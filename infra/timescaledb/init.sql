-- ============================================================
--  TimescaleDB 初始化 — 長晶爐時序數據
--  Czochralski Digital Twin
-- ============================================================

-- 啟用 TimescaleDB 擴充
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- ─────────────────────────────────────────
-- 長晶爐主指標 Hypertable
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS furnace_metrics (
    time                TIMESTAMPTZ     NOT NULL,
    ingot_no            VARCHAR(20)     NOT NULL,          -- C225C34E / C226654E
    furnace_id          VARCHAR(10)     NOT NULL,          -- C1 / C2
    operation_mode      VARCHAR(20),                       -- NECK4 / BODY / ...
    sop                 VARCHAR(50),
    event               SMALLINT        NOT NULL DEFAULT 1, -- 1=OK, 6=NG

    -- 直徑相關
    diameter            DOUBLE PRECISION,                  -- mm
    d_mean              DOUBLE PRECISION,
    diameter_target     DOUBLE PRECISION,

    -- 加熱器
    heater_temp         DOUBLE PRECISION,                  -- °C
    heater_temp_target  DOUBLE PRECISION,
    heater_power_sv     DOUBLE PRECISION,                  -- kW
    ht_mean             DOUBLE PRECISION,

    -- 生長相關
    gr_mean             DOUBLE PRECISION,                  -- 生長速率 mm/min
    seed_lift           DOUBLE PRECISION,
    seed_lift_sp        DOUBLE PRECISION,
    seed_lift_target    DOUBLE PRECISION,
    body_length         DOUBLE PRECISION,                  -- mm
    neck_length_accum   DOUBLE PRECISION,

    -- 磁場 / 溫度感測
    magnet_pv           DOUBLE PRECISION,
    temp2               DOUBLE PRECISION,
    temp4               DOUBLE PRECISION,
    temp5               DOUBLE PRECISION,
    temp29              DOUBLE PRECISION,

    -- 坩堝
    crucible_rotation_sp DOUBLE PRECISION,
    cr_mean             DOUBLE PRECISION,
    residual_weight     DOUBLE PRECISION,                  -- kg

    -- PID
    bp_mean             DOUBLE PRECISION,
    bpu60mean           DOUBLE PRECISION,
    pidsl_ddmean        DOUBLE PRECISION,
    pidsl_temp1         DOUBLE PRECISION,

    created_at          TIMESTAMPTZ     DEFAULT NOW()
);

-- 轉換為 Hypertable，以 1 天為 chunk
SELECT create_hypertable(
    'furnace_metrics',
    'time',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

-- 壓縮設定（7天後壓縮）
ALTER TABLE furnace_metrics SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'furnace_id, ingot_no',
    timescaledb.compress_orderby = 'time DESC'
);

SELECT add_compression_policy('furnace_metrics', INTERVAL '7 days', if_not_exists => TRUE);

-- 資料保留 90 天
SELECT add_retention_policy('furnace_metrics', INTERVAL '90 days', if_not_exists => TRUE);

-- ─────────────────────────────────────────
-- 索引
-- ─────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_furnace_metrics_furnace_id
    ON furnace_metrics (furnace_id, time DESC);

CREATE INDEX IF NOT EXISTS idx_furnace_metrics_ingot_no
    ON furnace_metrics (ingot_no, time DESC);

CREATE INDEX IF NOT EXISTS idx_furnace_metrics_event
    ON furnace_metrics (event, time DESC)
    WHERE event != 1;

-- ─────────────────────────────────────────
-- 連續聚合 View：每分鐘平均 (for Grafana)
-- ─────────────────────────────────────────
CREATE MATERIALIZED VIEW IF NOT EXISTS furnace_metrics_1min
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', time) AS bucket,
    furnace_id,
    ingot_no,
    AVG(diameter)        AS avg_diameter,
    AVG(heater_temp)     AS avg_heater_temp,
    AVG(heater_power_sv) AS avg_power,
    AVG(gr_mean)         AS avg_gr,
    MAX(event)           AS max_event
FROM furnace_metrics
GROUP BY bucket, furnace_id, ingot_no
WITH NO DATA;

SELECT add_continuous_aggregate_policy('furnace_metrics_1min',
    start_offset => INTERVAL '1 hour',
    end_offset   => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => TRUE
);

-- ─────────────────────────────────────────
-- 爐子基本資料表
-- ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS furnace_info (
    furnace_id   VARCHAR(10)  PRIMARY KEY,
    furnace_name VARCHAR(50),
    location     VARCHAR(100),
    status       VARCHAR(20)  DEFAULT 'idle',
    created_at   TIMESTAMPTZ  DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  DEFAULT NOW()
);

INSERT INTO furnace_info (furnace_id, furnace_name, location, status)
VALUES
    ('C1', '長晶爐 C1', 'Zone-A Row-1', 'running'),
    ('C2', '長晶爐 C2', 'Zone-A Row-2', 'running')
ON CONFLICT (furnace_id) DO NOTHING;

-- ─────────────────────────────────────────
-- 應用帳號 (Spring Boot 使用)
-- ─────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'twin_app') THEN
        CREATE ROLE twin_app LOGIN PASSWORD 'twin_app_secret';
    END IF;
END $$;

GRANT SELECT, INSERT, UPDATE ON furnace_metrics TO twin_app;
GRANT SELECT ON furnace_metrics_1min TO twin_app;
GRANT SELECT, UPDATE ON furnace_info TO twin_app;
GRANT USAGE ON SCHEMA public TO twin_app;

\echo '✅ TimescaleDB 初始化完成'
