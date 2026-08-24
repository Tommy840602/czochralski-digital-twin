#!/usr/bin/env bash
# ============================================================
#  flink-watchdog.sh — 確保「隨時剛好有一個」Flink job 在跑
#
#  為什麼需要它：
#    FurnaceStreamJob 關了 checkpointing（env.getCheckpointConfig()
#    .disableCheckpointing()），所以 JobManager / TaskManager 一重啟，
#    job 就永久消失，而且沒有任何東西會自動把它推回來。
#    重啟的原因很多：CI 部署、短暫 OOM、Docker 重啟、機器重開……
#    每次都要人工重推。這支腳本把那件事自動化。
#
#  它會分辨三種狀態，對症下藥：
#    A. 剛好 1 個 RUNNING        → 正常，什麼都不做（最常見）
#    B. 0 個 RUNNING             → job 消失了，重推一個
#    C. /jobs 打不通（非 200）    → JobManager 殭屍（Metaspace OOM 等），
#                                   先重啟 jobmanager+taskmanager，再推
#    D. >1 個 RUNNING            → 重複 job（會造成資料重複寫入），
#                                   取消到只剩一個
#
#  安裝成排程（伺服器上跑一次，每 2 分鐘檢查）：
#    ( crontab -l 2>/dev/null; \
#      echo "*/2 * * * * cd $HOME/czochralski-digital-twin && ./scripts/flink-watchdog.sh >> /tmp/flink-watchdog.log 2>&1" \
#    ) | crontab -
#
#  手動跑：./scripts/flink-watchdog.sh
# ============================================================
set -uo pipefail
cd "$(dirname "$0")/.."

COMPOSE="docker compose --env-file .env.prod -f docker-compose.prod.yml"
JM="twin-flink-jobmanager"

log() { echo "[$(date '+%F %T')] $*"; }

slack() {
    local msg="$1"
    local hook
    hook=$(grep -E '^SLACK_WEBHOOK_URL=' .env.prod | cut -d= -f2- | tr -d '"'"'"'')
    [[ "$hook" == https://hooks.slack.com/* ]] || return 0
    curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
        -d "{\"text\":\"$msg\"}" "$hook" >/dev/null 2>&1 || true
}

# 取消一個 job（帶重試，因為殭屍剛重啟時 API 可能還沒 ready）
cancel_job() {
    docker exec "$JM" curl -s -X PATCH "localhost:8081/jobs/$1?mode=cancel" >/dev/null 2>&1 || true
}

submit_job() {
    $COMPOSE up -d --force-recreate --no-deps flink-job-submitter >/dev/null 2>&1
    sleep 30
}

# 目前有幾個 RUNNING job（乾淨計數：grep -o 逐一匹配 | wc -l，永遠是單一數字）。
# ⚠ 不要用 `grep -oc ... || echo 0`——找不到時 grep 印 0、|| 又補一個 0 → "0\n0"
#   → 整數比較 [ "0\n0" -ge 1 ] 直接噴 "integer expression expected"（踩過）。
count_running() {
    docker exec "$JM" curl -s -m 8 localhost:8081/jobs 2>/dev/null \
        | grep -o '"status":"RUNNING"' | wc -l | tr -d ' '
}

# 目前有幾個 TaskManager 註冊到 JobManager。
# 這是最近一次故障的盲點：TM 掉線後不會自己重連，JobManager 看到 0 個 TM、
# job 拿不到 slot → 無限 RESTARTING，watchdog 卻只會徒勞重推 job（不重啟 TM）。
count_taskmanagers() {
    docker exec "$JM" curl -s -m 8 localhost:8081/taskmanagers 2>/dev/null \
        | grep -o '"id"' | wc -l | tr -d ' '
}

# ── 讀取目前 job 狀態 ──
# 打得通 → RESP 是 JSON；打不通（殭屍 / 沒起來）→ 空字串
RESP=$(docker exec "$JM" curl -s -m 8 localhost:8081/jobs 2>/dev/null || echo "")
RUNNING_IDS=$(echo "$RESP" | grep -oE '"id":"[a-f0-9]{32}","status":"RUNNING"' | grep -oE '[a-f0-9]{32}')
RUNNING_CNT=$(echo -n "$RUNNING_IDS" | grep -c . || true)

# ── C: JobManager 打不通（殭屍 / 掛了）──
if ! echo "$RESP" | grep -q '"jobs"'; then
    log "JobManager /jobs 無回應 → 疑似 Metaspace 殭屍，重啟 jobmanager+taskmanager"
    $COMPOSE up -d --force-recreate flink-jobmanager flink-taskmanager >/dev/null 2>&1
    sleep 45
    submit_job
    NOW=$(count_running)
    log "重啟後 RUNNING job 數：$NOW"
    slack "🔧 *Flink watchdog* — JobManager 殭屍已重啟並重推 job（現有 ${NOW} 個 RUNNING）。twin.tommy-huang.dev"
    exit 0
fi

# ── C2: TaskManager 沒註冊（最近一次崩 3 天的病灶）──
# JM 活著、能回 /jobs，但沒有任何 TM 註冊 → job 永遠拿不到 slot、無限 RESTARTING。
# 只重推 job 沒用（watchdog 舊版就是這樣徒勞了 3 天），必須把 TM 重啟、讓它重新註冊。
TM_CNT=$(count_taskmanagers)
if [ "${TM_CNT:-0}" -eq 0 ]; then
    log "JobManager 上註冊的 TaskManager = 0 → TM 掉線，重啟 jobmanager+taskmanager 讓它重新註冊"
    $COMPOSE up -d --force-recreate flink-jobmanager flink-taskmanager >/dev/null 2>&1
    sleep 45
    # 重啟後把殘留的 RESTARTING/CREATED job 清掉，再推一個乾淨的
    for id in $(docker exec "$JM" curl -s localhost:8081/jobs 2>/dev/null \
                  | grep -oE '"id":"[a-f0-9]{32}","status":"(RESTARTING|CREATED|FAILING)"' \
                  | grep -oE '[a-f0-9]{32}'); do
        cancel_job "$id"
    done
    submit_job
    NOW=$(count_running); TM=$(count_taskmanagers)
    log "重啟後：TM=$TM，RUNNING job=$NOW"
    if [ "${NOW:-0}" -ge 1 ]; then
        slack "🔧 *Flink watchdog* — TaskManager 曾掉線，已重啟並重推 job（TM=${TM}，RUNNING=${NOW}）。twin.tommy-huang.dev"
    else
        slack "🚨 *Flink watchdog* — TaskManager 掉線、重啟後 job 仍起不來（TM=${TM}），需人工介入！twin.tommy-huang.dev"
    fi
    exit 0
fi

# ── A: 正好一個，正常 ──
if [ "$RUNNING_CNT" -eq 1 ]; then
    exit 0
fi

# ── D: 太多，取消到剩一個 ──
if [ "$RUNNING_CNT" -gt 1 ]; then
    log "偵測到 $RUNNING_CNT 個 RUNNING job（重複寫入風險），取消多餘的"
    KEEP=$(echo "$RUNNING_IDS" | head -1)
    for id in $RUNNING_IDS; do
        [ "$id" = "$KEEP" ] && continue
        log "  取消 $id"
        cancel_job "$id"
    done
    slack "⚠️ *Flink watchdog* — 偵測到 ${RUNNING_CNT} 個重複 job，已取消到剩 1 個。twin.tommy-huang.dev"
    exit 0
fi

# ── B: 一個都沒有，重推 ──
log "沒有 RUNNING job → 重推一個"
# 先把殘留的 CANCELED/FAILED 之外的活躍 job（RESTARTING/CREATED）清掉，避免搶 slot
for id in $(echo "$RESP" | grep -oE '"id":"[a-f0-9]{32}","status":"(RESTARTING|CREATED|FAILING)"' | grep -oE '[a-f0-9]{32}'); do
    cancel_job "$id"
done
submit_job
NOW=$(count_running)
log "重推後 RUNNING job 數：$NOW"
if [ "${NOW:-0}" -ge 1 ]; then
    slack "🔧 *Flink watchdog* — 偵測到 job 消失，已自動重推（現有 ${NOW} 個 RUNNING）。twin.tommy-huang.dev"
else
    slack "🚨 *Flink watchdog* — job 消失且「重推失敗」，需要人工介入！twin.tommy-huang.dev"
fi
