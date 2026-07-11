-- ============================================================
--  重建 furnace_metrics_1min 連續聚合：補上 avg_seed_lift
--
--  為什麼要這樣做：
--    SPC baseline 需要「每分鐘子群平均 × operation_mode」。原本直接對 furnace_metrics
--    原始表（單爐 7 天約 60 萬筆）做 time_bucket 聚合 + window function，
--    在 15.6GB 的 Docker VM 上會把 Postgres 後端 OOM 掉（signal 9），整個 DB 進 recovery。
--    furnace_metrics_1min 本來就存好了每分鐘平均，改讀它可把掃描量降到 ~1 萬筆。
--    但它缺 seed_lift（6 個 SPC 參數之一），而 TimescaleDB 的連續聚合不能 ALTER 加欄位，
--    只能重建。
--
--  用法：
--    docker exec -i twin-timescaledb psql -U twin -d furnace_db < scripts/spc-migrate-cagg.sql
--
--  注意：最後的 refresh 會把歷史資料重新物化，會跑幾分鐘（TimescaleDB 分批處理，不會 OOM）。
-- ============================================================

DROP MATERIALIZED VIEW IF EXISTS furnace_metrics_1min CASCADE;

CREATE MATERIALIZED VIEW furnace_metrics_1min
    WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', time)   AS bucket,
    furnace_id,
    ingot_no,
    operation_mode,
    AVG(diameter)                   AS avg_diameter,
    AVG(heater_temp)                AS avg_heater_temp,
    AVG(heater_power_sv)            AS avg_power,
    AVG(gr_mean)                    AS avg_gr,
    AVG(body_length)                AS avg_body_length,
    AVG(seed_lift)                  AS avg_seed_lift,   -- ← 新增
    AVG(residual_weight)            AS avg_residual_weight,
    COUNT(*)                        AS sample_count
FROM furnace_metrics
GROUP BY bucket, furnace_id, ingot_no, operation_mode
    WITH NO DATA;

-- 開啟即時聚合（real-time aggregation）。
-- TimescaleDB 較新版本的預設是 materialized_only = true，查詢只看「已物化」的資料，
-- 最新的一兩分鐘不會出現 → SPC 的製程階段會落後數位孿生（例如爐子已進 BODY，
-- 聚合表最後一筆還停在 HOLDING）。設成 false 後，查詢會把已物化資料與最新原始資料合併。
ALTER MATERIALIZED VIEW furnace_metrics_1min
    SET (timescaledb.materialized_only = false);

SELECT add_continuous_aggregate_policy('furnace_metrics_1min',
                                       start_offset      => INTERVAL '2 hours',
                                       end_offset        => INTERVAL '1 minute',
                                       schedule_interval => INTERVAL '1 minute',
                                       if_not_exists     => TRUE
       );

-- 重建後重新授權（DROP 會一併移除）。twin_app 不一定存在（應用實際是用 twin 連線），
-- 所以先檢查角色存在與否，避免整個腳本在這裡中斷。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'twin_app') THEN
        GRANT SELECT ON furnace_metrics_1min TO twin_app;
    END IF;
END
$$;

-- 把歷史資料物化（NULL, NULL = 全部區間，TimescaleDB 會分批做）
CALL refresh_continuous_aggregate('furnace_metrics_1min', NULL, NULL);

SELECT 'CAGG 重建完成，已含 avg_seed_lift' AS status,
       count(*) AS buckets
FROM furnace_metrics_1min;
