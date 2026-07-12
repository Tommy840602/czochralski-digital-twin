-- ============================================================
--  TimescaleDB 初始化 — 長晶爐數位孿生（彈性多爐版）
--  支援任意數量爐子，透過 furnace_registry 動態管理
-- ============================================================

-- ─────────────────────────────────────────
-- 0. auth-service 的資料庫
--
--    compose 只透過 POSTGRES_DB 建了 furnace_db，authdb 沒人建。
--    auth-service 連 jdbc:.../authdb，資料庫不存在就會無限重啟——
--    部署到全新機器時踩過這個坑，這裡補上。
--
--    Postgres 的 CREATE DATABASE 沒有 IF NOT EXISTS，用 \gexec 做冪等。
--    （\gexec 是 psql 的 meta-command，docker-entrypoint 就是用 psql 跑這支檔案）
-- ─────────────────────────────────────────
SELECT 'CREATE DATABASE authdb OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'authdb')\gexec

CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- ─────────────────────────────────────────
-- 1. 爐子登錄表（彈性支援 N 台爐子）
--    新增爐子只需 INSERT 一筆，無需改任何程式碼
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS furnace_registry (
                                                furnace_id      VARCHAR(20)  PRIMARY KEY,        -- 爐子識別碼，等同 CSV PULLER 欄位
    display_name    VARCHAR(50),                     -- 顯示名稱，可中文
    location        VARCHAR(100),                    -- 位置（廠區/區域）
    zone            VARCHAR(20),                     -- 區域代碼，例如 Zone-A
    status          VARCHAR(20)  DEFAULT 'idle'      -- running / idle / maintenance / offline
    CHECK (status IN ('running','idle','maintenance','offline')),
    description     TEXT,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  DEFAULT NOW()
    );

COMMENT ON TABLE furnace_registry IS '爐子主登錄表，所有爐子資訊的唯一真相來源';
COMMENT ON COLUMN furnace_registry.furnace_id IS '等同 CSV PULLER 欄位值，如 D1 / D3 / DB / F7 / FA';

-- ─────────────────────────────────────────
-- 1.5 爐子種子資料  ★ 少了這段，整套系統會「安靜地」不動 ★
--
--     FurnaceStreamJob 的 RegistryFilter 啟動時會從這張表載入爐子清單，
--     然後 filter 掉所有不在清單裡的 Kafka 訊息：
--
--         return knownFurnaces.contains(r.getFurnaceId());
--
--     表是空的 → 清單是空集合 → 每一筆資料都被丟掉。
--     TimescaleDB / Redis / MongoDB 全部零筆，前端顯示「0 爐」、3D 場景空白，
--     但**沒有任何錯誤訊息**——Flink job 是 RUNNING 的，只是什麼都沒做。
--
--     部署到全新機器時踩過這個坑，非常難查。
--
--     ⚠ 新增爐子時，這裡加一筆 + datapipe 掛一個 simulator 就好，不用改程式碼。
--        改完要重推 Flink job（RegistryFilter 只在 open() 時讀一次）。
-- ─────────────────────────────────────────
INSERT INTO furnace_registry (furnace_id, display_name, location, zone, status, description) VALUES
    ('D1', '長晶爐 D1', 'Fab-A / 1F', 'Zone-A', 'idle', 'CZ 單晶矽長晶爐'),
    ('D3', '長晶爐 D3', 'Fab-A / 1F', 'Zone-A', 'idle', 'CZ 單晶矽長晶爐'),
    ('DB', '長晶爐 DB', 'Fab-A / 2F', 'Zone-B', 'idle', 'CZ 單晶矽長晶爐'),
    ('F7', '長晶爐 F7', 'Fab-B / 1F', 'Zone-C', 'idle', 'CZ 單晶矽長晶爐'),
    ('FA', '長晶爐 FA', 'Fab-B / 2F', 'Zone-C', 'idle', 'CZ 單晶矽長晶爐')
ON CONFLICT (furnace_id) DO NOTHING;

-- ─────────────────────────────────────────
-- 2. 長晶爐主指標 Hypertable
--    furnace_id 透過 FK 對應 furnace_registry
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS furnace_metrics (
                                               time                    TIMESTAMPTZ      NOT NULL,   -- LogTime
                                               furnace_id              VARCHAR(20)      NOT NULL
    REFERENCES furnace_registry(furnace_id) ON DELETE CASCADE,
    ingot_no                VARCHAR(30),                 -- INGOT_NO
    operation_mode          VARCHAR(30),                 -- Operation Mode
    sop                     VARCHAR(100),                -- SOP

-- 直徑相關
    diameter                DOUBLE PRECISION,            -- Diameter (mm)
    d_mean                  DOUBLE PRECISION,            -- D_mean
    diameter_target         DOUBLE PRECISION,            -- Diameter target

-- 溫度感測
    heater_temp             DOUBLE PRECISION,            -- Heater temp (°C)
    heater_temp_target      DOUBLE PRECISION,            -- Heater temp target
    heater_power_sv         DOUBLE PRECISION,            -- Heater Power SV (kW)
    ht_mean                 DOUBLE PRECISION,            -- HTmean
    temp2                   DOUBLE PRECISION,            -- temp2
    temp4                   DOUBLE PRECISION,            -- temp4
    temp5                   DOUBLE PRECISION,            -- temp5
    temp9                   DOUBLE PRECISION,            -- temp9
    temp29                  DOUBLE PRECISION,            -- temp29

-- 生長速率
    gr_mean                 DOUBLE PRECISION,            -- GR_mean (mm/min)
    body_length             DOUBLE PRECISION,            -- Body length (mm)
    neck_length_accum       DOUBLE PRECISION,            -- Neck Length Accum

-- 晶種提升
    seed_lift               DOUBLE PRECISION,            -- Seed lift
    seed_lift_sp            DOUBLE PRECISION,            -- Seed Lift SP
    seed_lift_target        DOUBLE PRECISION,            -- Seed lift target

-- 坩堝
    crucible_rotation_sp    DOUBLE PRECISION,            -- Crucible rotation SP
    cr_mean                 DOUBLE PRECISION,            -- CRmean
    crucible_lift           DOUBLE PRECISION,            -- Crucible Lift
    crucible_lift_ratio     DOUBLE PRECISION,            -- Crucible lift ratio
    crucible_position       DOUBLE PRECISION,            -- Crucible position
    crucible_pos_calibrated DOUBLE PRECISION,            -- Crucible Position Calibrated
    ctpfl_pul               DOUBLE PRECISION,            -- CTPFL_PUL

-- 磁場
    magnet_pv               DOUBLE PRECISION,            -- MAGNET PV

-- 壓力/氣流
    argon_flow_rate         DOUBLE PRECISION,            -- Argon gas flow rate
    lower_chamber_press     DOUBLE PRECISION,            -- Lower chamber press
    lower_chamber_press_sp  DOUBLE PRECISION,            -- Lower chamber press SP
    thro_valve_open         DOUBLE PRECISION,            -- Thro Valve Open
    bp_mean                 DOUBLE PRECISION,            -- BPmean
    bpu60mean               DOUBLE PRECISION,            -- BPU60mean
    btpl_bpul1              DOUBLE PRECISION,            -- BTPL_BPUL1
    btpl_bpll1              DOUBLE PRECISION,            -- BTPL_BPLL1

-- PID
    pidsl_ddmean            DOUBLE PRECISION,            -- PIDSL_dDmean
    pidsl_temp1             DOUBLE PRECISION,            -- PIDSL_temp1

-- 其他
    residual_weight         DOUBLE PRECISION,            -- Residual Weight (kg)
    seed_rotation_sp        DOUBLE PRECISION,            -- Seed rotation SP
    countb                  INTEGER,                     -- countb

    created_at              TIMESTAMPTZ      DEFAULT NOW()
    );

-- 轉換為 Hypertable，以 1 天為 chunk
SELECT create_hypertable(
               'furnace_metrics', 'time',
               chunk_time_interval => INTERVAL '1 day',
               if_not_exists => TRUE
       );

-- 壓縮（7 天後）
ALTER TABLE furnace_metrics SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'furnace_id, ingot_no',
    timescaledb.compress_orderby   = 'time DESC'
    );
SELECT add_compression_policy('furnace_metrics', INTERVAL '7 days', if_not_exists => TRUE);

-- 保留 365 天
SELECT add_retention_policy('furnace_metrics', INTERVAL '365 days', if_not_exists => TRUE);

-- ─────────────────────────────────────────
-- 3. 索引
-- ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_fm_furnace_time
    ON furnace_metrics (furnace_id, time DESC);

CREATE INDEX IF NOT EXISTS idx_fm_ingot_time
    ON furnace_metrics (ingot_no, time DESC);

CREATE INDEX IF NOT EXISTS idx_fm_mode_time
    ON furnace_metrics (operation_mode, time DESC);

-- ─────────────────────────────────────────
-- 4. 連續聚合 View（每分鐘，供 Grafana 使用）
-- ─────────────────────────────────────────
CREATE MATERIALIZED VIEW IF NOT EXISTS furnace_metrics_1min
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
    AVG(residual_weight)            AS avg_residual_weight,
    COUNT(*)                        AS sample_count
FROM furnace_metrics
GROUP BY bucket, furnace_id, ingot_no, operation_mode
    WITH NO DATA;

SELECT add_continuous_aggregate_policy('furnace_metrics_1min',
                                       start_offset      => INTERVAL '2 hours',
                                       end_offset        => INTERVAL '1 minute',
                                       schedule_interval => INTERVAL '1 minute',
                                       if_not_exists     => TRUE
       );

-- ─────────────────────────────────────────
-- 5. 每小時聚合（供長期趨勢分析）
-- ─────────────────────────────────────────
CREATE MATERIALIZED VIEW IF NOT EXISTS furnace_metrics_1hour
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time)     AS bucket,
    furnace_id,
    ingot_no,
    AVG(diameter)                   AS avg_diameter,
    MAX(diameter)                   AS max_diameter,
    MIN(diameter)                   AS min_diameter,
    AVG(heater_temp)                AS avg_heater_temp,
    MAX(heater_temp)                AS max_heater_temp,
    AVG(gr_mean)                    AS avg_gr,
    AVG(body_length)                AS avg_body_length,
    AVG(heater_power_sv)            AS avg_power,
    COUNT(*)                        AS sample_count
FROM furnace_metrics
GROUP BY bucket, furnace_id, ingot_no
    WITH NO DATA;

SELECT add_continuous_aggregatereplication_policy('furnace_metrics_1hour',
                                                  start_offset      => INTERVAL '2 days',
                                                  end_offset        => INTERVAL '1 hour',
                                                  schedule_interval => INTERVAL '1 hour',
                                                  if_not_exists     => TRUE
       ) WHERE FALSE;  -- placeholder, actual call below

SELECT add_continuous_aggregate_policy('furnace_metrics_1hour',
                                       start_offset      => INTERVAL '2 days',
                                       end_offset        => INTERVAL '1 hour',
                                       schedule_interval => INTERVAL '1 hour',
                                       if_not_exists     => TRUE
       );

-- ─────────────────────────────────────────
-- 6. 應用帳號
-- ─────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'twin_app') THEN
CREATE ROLE twin_app LOGIN PASSWORD 'twin_app_secret';
END IF;
END $$;

GRANT SELECT, INSERT, UPDATE ON furnace_registry    TO twin_app;
GRANT SELECT, INSERT          ON furnace_metrics     TO twin_app;
GRANT SELECT                  ON furnace_metrics_1min  TO twin_app;
GRANT SELECT                  ON furnace_metrics_1hour TO twin_app;
GRANT USAGE ON SCHEMA public  TO twin_app;

-- ─────────────────────────────────────────
-- 7. 便利 View：各爐子最新狀態
--    供 API GET /api/furnaces 快速查詢
-- ─────────────────────────────────────────
CREATE OR REPLACE VIEW furnace_latest AS
SELECT DISTINCT ON (m.furnace_id)
    r.furnace_id,
    r.display_name,
    r.location,
    r.zone,
    r.status,
    m.time            AS last_log_time,
    m.ingot_no,
    m.operation_mode,
    m.diameter,
    m.heater_temp,
    m.gr_mean,
    m.body_length,
    m.heater_power_sv,
    m.residual_weight
FROM furnace_registry  r
    LEFT JOIN furnace_metrics m ON m.furnace_id = r.furnace_id
ORDER BY m.furnace_id, m.time DESC;

COMMENT ON VIEW furnace_latest IS '各爐子最新一筆數據，JOIN registry 取顯示名稱與狀態';

\echo '✅ TimescaleDB 初始化完成'
