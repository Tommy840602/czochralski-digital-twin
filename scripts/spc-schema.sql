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