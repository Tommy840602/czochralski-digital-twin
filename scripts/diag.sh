#!/usr/bin/env bash
# ============================================================
#  診斷腳本：把狀態全部寫進 scripts/diag-output.txt
#  Claude 可以直接讀那個檔，你不用複製貼上。
#
#  用法（在專案根目錄）：
#      bash scripts/diag.sh            # 只看狀態（快）
#      bash scripts/diag.sh --build    # 順便重新 mvn 編譯
# ============================================================

set +e
cd "$(dirname "$0")/.." || exit 1
OUT="scripts/diag-output.txt"
: > "$OUT"

section() { echo -e "\n\n===== $1 =====" >> "$OUT"; }

AUTH='--header=X-User-Perms:SPC_VIEW --header=X-User-Name:admin --header=X-User-Id:2 --header=X-User-Roles:ADMIN'

if [ "$1" = "--build" ]; then
  section "0. mvn 編譯 alarm-service"
  (cd services/alarm-service && mvn clean package -DskipTests 2>&1) \
      | grep -vE "Downloading|Downloaded|Progress" | tail -40 >> "$OUT"
fi

section "1. 容器狀態 / jar 時間"
docker ps -a --filter name=twin-alarm-service --format "{{.Names}}  {{.Status}}" 2>&1 >> "$OUT"
docker exec twin-alarm-service sh -c 'ls -la /app/app.jar' 2>&1 >> "$OUT"

section "2. 重算是否還在進行中"
docker exec twin-api-gateway sh -c \
  "wget -qO- $AUTH 'http://alarm-service:8092/spc/baseline/rebuild/status?furnaceId=D1'" 2>&1 >> "$OUT"
echo "" >> "$OUT"

section "3. baseline 建了幾組"
docker exec -i twin-timescaledb psql -U twin -d furnace_db -c \
  "SELECT furnace_id, operation_mode, count(*) AS params, max(sample_size) AS max_samples
   FROM spc_baseline GROUP BY furnace_id, operation_mode ORDER BY 1,2;" 2>&1 >> "$OUT"

section "4. 違規統計（依 rule）"
docker exec -i twin-timescaledb psql -U twin -d furnace_db -c \
  "SELECT rule_id, severity, count(*) FROM spc_violation
   WHERE furnace_id='D1' GROUP BY rule_id, severity ORDER BY rule_id;" 2>&1 >> "$OUT"

section "5. 重算 / 回填 執行紀錄"
docker logs twin-alarm-service 2>&1 \
  | grep -iE "Baseline rebuild done|Backfilled|Failed to rebuild|Backfill failed|rebuild failed|Not enough" \
  | tail -30 >> "$OUT"

section "6. 最近的錯誤"
docker logs twin-alarm-service 2>&1 \
  | grep -iE "ERROR|Exception|Caused by" | grep -v ConfigServerConfigDataLoader \
  | tail -20 >> "$OUT"

section "7. 最後 15 行原始 log"
docker logs twin-alarm-service --tail 15 2>&1 >> "$OUT"

section "8. TimescaleDB 是不是被殺掉了（OOM / crash）"
docker logs twin-timescaledb 2>&1 \
  | grep -iE "terminated by signal|out of memory|server process|crash|restart|FATAL|shutting down|received (fast|smart|immediate)" \
  | tail -25 >> "$OUT"

section "9. TimescaleDB 容器狀態與記憶體"
docker ps -a --filter name=twin-timescaledb --format "{{.Status}}" 2>&1 >> "$OUT"
docker stats --no-stream --format "{{.Name}}  CPU={{.CPUPerc}}  MEM={{.MemUsage}}" \
  twin-timescaledb twin-alarm-service 2>&1 >> "$OUT"
echo "--- 主機可用記憶體 ---" >> "$OUT"
docker info --format 'Docker 總記憶體: {{.MemTotal}}' 2>&1 >> "$OUT"

section "10. furnace_metrics 資料量 / work_mem"
docker exec -i twin-timescaledb psql -U twin -d furnace_db -c \
  "SHOW work_mem; SHOW shared_buffers;" 2>&1 >> "$OUT"

echo "完成 → $OUT"
