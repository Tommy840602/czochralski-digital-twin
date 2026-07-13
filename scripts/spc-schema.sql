-- baseline 以 (furnace_id, param_name, operation_mode) 為唯一鍵：
-- 各製程階段（MELT/NECK4/CROWN/BODY/HOLDING…）的分佈本質不同，必須分開建管制界。
CREATE TABLE IF NOT EXISTS spc_baseline (
                                            id SERIAL PRIMARY KEY,
                                            furnace_id VARCHAR(16) NOT NULL,
    param_name VARCHAR(32) NOT NULL,
    operation_mode VARCHAR(30) NOT NULL,
    mean DOUBLE PRECISION NOT NULL,
    std_dev DOUBLE PRECISION NOT NULL,
    ucl_3sigma DOUBLE PRECISION NOT NULL,
    lcl_3sigma DOUBLE PRECISION NOT NULL,
    ucl_2sigma DOUBLE PRECISION NOT NULL,
    lcl_2sigma DOUBLE PRECISION NOT NULL,
    ucl_1sigma DOUBLE PRECISION NOT NULL,
    lcl_1sigma DOUBLE PRECISION NOT NULL,
    sigma_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sample_size INTEGER NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(furnace_id, param_name, operation_mode)
    );

CREATE TABLE IF NOT EXISTS spc_violation (
                                             id BIGSERIAL,
                                             ts TIMESTAMPTZ NOT NULL,
                                             furnace_id VARCHAR(16) NOT NULL,
    ingot_id VARCHAR(32),
    param_name VARCHAR(32) NOT NULL,
    rule_id INTEGER NOT NULL,
    rule_name VARCHAR(96) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    mean DOUBLE PRECISION NOT NULL,
    std_dev DOUBLE PRECISION NOT NULL,
    ucl_3sigma DOUBLE PRECISION,
    lcl_3sigma DOUBLE PRECISION,
    severity VARCHAR(8) NOT NULL,
    PRIMARY KEY(id, ts)
    );
SELECT create_hypertable('spc_violation', 'ts', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_spc_violation_furnace_param ON spc_violation(furnace_id, param_name, ts DESC);

CREATE TABLE IF NOT EXISTS equipment_oee (
                                             id BIGSERIAL,
                                             ts TIMESTAMPTZ NOT NULL,
                                             furnace_id VARCHAR(16) NOT NULL,
    availability DOUBLE PRECISION NOT NULL,
    performance DOUBLE PRECISION NOT NULL,
    quality DOUBLE PRECISION NOT NULL,
    oee DOUBLE PRECISION NOT NULL,
    running_seconds INTEGER NOT NULL,
    idle_seconds INTEGER NOT NULL,
    down_seconds INTEGER NOT NULL,
    PRIMARY KEY(id, ts)
    );
SELECT create_hypertable('equipment_oee', 'ts', if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_equipment_oee_furnace ON equipment_oee(furnace_id, ts DESC);

-- ─────────────────────────────────────────────────────────────
--  oee_target — 每台爐子的 OEE 目標值
--
--  ⚠ 這張表原本「只存在於 JPA entity」（OeeTarget.java），從來沒有任何 SQL 建過它，
--    而 alarm-service 是 ddl-auto: none，不會自動建表。
--    全新機器部署時，OEE 頁面會直接 500：relation "oee_target" does not exist。
--    （筆電上是某次手動建的，所以一直沒發現。）
--
--  OeeService.calculate() 會先 findById(furnaceId)，查不到就直接拋例外——
--  沒有預設值可以退回，所以「每一台爐子都必須有一筆」。
--
--  三個目標值怎麼用：
--    target_gr_mean        → Performance = BODY 階段平均拉速 / 這個值（封頂 100%）
--    target_length_mm      → Quality     = 達到這個長度的比例
--    quality_threshold_pct → 良品判定門檻
--    target_cycle_hours    → 週期時間目標
--
--  下面是通用預設值。等資料累積幾天後，建議用實際分佈重新校準：
--
--    SELECT furnace_id,
--           percentile_cont(0.9) WITHIN GROUP (ORDER BY gr_mean)     AS gr_p90,
--           percentile_cont(0.9) WITHIN GROUP (ORDER BY body_length) AS len_p90
--    FROM furnace_metrics
--    WHERE operation_mode = 'BODY' AND time > NOW() - INTERVAL '7 days'
--    GROUP BY furnace_id;
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS oee_target (
    furnace_id            VARCHAR(16) PRIMARY KEY,
    target_length_mm      DOUBLE PRECISION NOT NULL,
    target_cycle_hours    DOUBLE PRECISION NOT NULL,
    target_gr_mean        DOUBLE PRECISION NOT NULL,
    quality_threshold_pct DOUBLE PRECISION NOT NULL
);

INSERT INTO oee_target (furnace_id, target_length_mm, target_cycle_hours, target_gr_mean, quality_threshold_pct)
SELECT furnace_id, 2000.0, 40.0, 0.8, 95.0
FROM furnace_registry
ON CONFLICT (furnace_id) DO NOTHING;