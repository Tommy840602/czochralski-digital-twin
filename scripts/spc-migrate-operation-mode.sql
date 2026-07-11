-- ============================================================
--  SPC 遷移：baseline 改為「每個 operation_mode 各一組」
--  可重複執行（idempotent）
--
--  背景：
--    原本 (furnace_id, param_name) 一組 baseline，把 MELT / NECK4 /
--    CROWN / BODY / HOLDING 全部混在一起算 mean/σ。各階段的溫度與直徑
--    本質不同，混合後製程不在統計管制狀態，管制界沒有意義，
--    導致 Rule 1~8 大量誤報。
--
--  用法：
--    docker exec -i twin-timescaledb psql -U twin -d furnace_db \
--      < scripts/spc-migrate-operation-mode.sql
-- ============================================================

-- 1) 補上 sigma_multiplier（entity 有、但舊 spc-schema.sql 漏了，屬 schema 漂移）
ALTER TABLE spc_baseline
    ADD COLUMN IF NOT EXISTS sigma_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.0;

-- 2) 新增 operation_mode
ALTER TABLE spc_baseline
    ADD COLUMN IF NOT EXISTS operation_mode VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';

-- 3) 唯一鍵改為含 operation_mode
--    （舊約束名為 Postgres 自動產生的 spc_baseline_furnace_id_param_name_key）
ALTER TABLE spc_baseline
    DROP CONSTRAINT IF EXISTS spc_baseline_furnace_id_param_name_key;

DROP INDEX IF EXISTS uq_spc_baseline_furnace_param_mode;
CREATE UNIQUE INDEX uq_spc_baseline_furnace_param_mode
    ON spc_baseline (furnace_id, param_name, operation_mode);

-- 4) 清掉舊 baseline
--    舊資料是「混合所有 mode」算出來的，統計上無效；
--    且 σ 的尺度定義也變了（規則改用每分鐘子群平均評估），
--    必須全部重算，不能沿用。
DELETE FROM spc_baseline;

-- 5) 舊的違規紀錄同樣是用錯誤管制界產生的，一併清掉以免統計數字失真。
--    （如果你想保留歷史，把下面這行註解掉即可）
TRUNCATE TABLE spc_violation;

-- 6) baseline / backfill 的查詢會依 (爐, 階段, 時間) 掃資料，補一個對應索引。
--    沒有它，每次重算都要對該爐 7 天的百萬筆做全掃。
CREATE INDEX IF NOT EXISTS idx_fm_furnace_mode_time
    ON furnace_metrics (furnace_id, operation_mode, time DESC);

SELECT 'SPC migration done. 請呼叫 baseline rebuild 重算各 mode 的 baseline。' AS status;
