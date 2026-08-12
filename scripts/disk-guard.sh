#!/usr/bin/env bash
# ============================================================
#  disk-guard.sh — 每日磁碟清理 + 超標告警
#
#  背景：這套系統每次 CI 部署都 --build，Docker 的 image / build cache
#        會持續累積。上線約 3 週後 160GB 磁碟塞滿，Kafka 和 MongoDB
#        首當其衝（一直在寫檔）被拖垮，整個資料流靜靜地斷掉。
#        這支腳本讓磁碟不會累積到爆。
#
#  做三件事：
#    1. 清掉沒用到的 Docker image / 停止的容器 / build cache
#       （正在跑的服務用到的 image 不會被動到）
#    2. 清掉 7 天前的 log
#    3. 清完若使用率仍 > THRESHOLD%，發 Slack 告警
#
#  安裝成每日排程（在伺服器上跑一次）：
#    ( crontab -l 2>/dev/null; \
#      echo "17 4 * * * cd $HOME/czochralski-digital-twin && ./scripts/disk-guard.sh >> /tmp/disk-guard.log 2>&1" \
#    ) | crontab -
#    → 每天 04:17 跑，輸出寫到 /tmp/disk-guard.log
#
#  手動跑：./scripts/disk-guard.sh
# ============================================================
set -uo pipefail
cd "$(dirname "$0")/.."

THRESHOLD=80   # 使用率超過這個百分比就告警

echo "════════ disk-guard $(date '+%F %T') ════════"

before=$(df -h / | awk 'NR==2{print $5}')
echo "清理前：$before"

# ── 1. Docker 清理（安全：不碰正在用的 image）──
docker system prune -af  >/dev/null 2>&1 || true
docker builder prune -af >/dev/null 2>&1 || true

# ── 2. 舊 log ──
# 容器 log 已由 compose 的 max-size:50m / max-file:3 控制，這裡清系統 journal
sudo journalctl --vacuum-time=7d >/dev/null 2>&1 || true

after_pct=$(df / | awk 'NR==2{print $5}' | tr -d '%')
after=$(df -h / | awk 'NR==2{print $5}')
echo "清理後：$after"

# ── 3. 仍超標 → Slack ──
# 注意：$after 來自 df -h，已經含 '%'（例如 "35%"），後面不要再加 %，否則變 %%。
if [ "$after_pct" -gt "$THRESHOLD" ]; then
    echo "⚠ 使用率 ${after} 超過 ${THRESHOLD}%，發送 Slack 告警"

    # 從 .env.prod 撈 webhook（單獨撈，不要 source —— 裡面的 bcrypt hash 有 $ 會爆）
    WEBHOOK=$(grep -E '^SLACK_WEBHOOK_URL=' .env.prod | cut -d= -f2- | tr -d '"'"'"'')

    if [[ "$WEBHOOK" == https://hooks.slack.com/* ]]; then
        # 佔用前幾大的目錄，方便判斷是誰吃掉的
        BIG=$(df -h / | awk 'NR==2{print "已用 "$3" / "$2" ("$5")"}')
        DOCKER_USE=$(docker system df --format '{{.Type}} {{.Size}}' 2>/dev/null | tr '\n' ' ')
        TEXT="🚨 *磁碟告警* — twin.tommy-huang.dev\\n根分割區清理後仍達 ${after}（門檻 ${THRESHOLD}%）\\n${BIG}\\nDocker: ${DOCKER_USE}\\n可能要考慮擴大磁碟，或檢查是誰在狂寫。"
        curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
            -d "{\"text\":\"${TEXT}\"}" "$WEBHOOK" >/dev/null \
            && echo "  ✓ 已送出" || echo "  ✗ Slack 送出失敗"
    else
        echo "  （SLACK_WEBHOOK_URL 沒設或格式不對，略過告警）"
    fi
else
    echo "✓ 使用率 ${after} 在門檻內"
fi

echo "════════ 完成 ════════"
