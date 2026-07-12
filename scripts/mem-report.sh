#!/usr/bin/env bash
# ============================================================
#  mem-report.sh — 量「真實」記憶體用量，決定 16GB 夠不夠
#
#  背景：docker-compose.prod.yml 裡的 mem_limit 是「上限」不是「預留」。
#        上限總和 14.5GB 不代表真的會用到 14.5GB。
#        這支腳本量的是實際 RSS，用數字取代猜測。
#
#  用法（在伺服器上，系統穩定跑滿 10 分鐘後執行）：
#      ./scripts/mem-report.sh
#
#  輸出同時寫到 scripts/mem-report-output.txt（已 gitignore）。
# ============================================================
set -uo pipefail

cd "$(dirname "$0")/.."
OUT="scripts/mem-report-output.txt"
COMPOSE="docker compose --env-file .env.prod -f docker-compose.prod.yml"

exec > >(tee "$OUT") 2>&1

echo "════════════════════════════════════════════════════════════"
echo " 記憶體實測  $(date '+%F %T')"
echo "════════════════════════════════════════════════════════════"
echo
echo "── 1. 主機整體 ─────────────────────────────────────────────"
free -h
echo
echo "swap 使用量（若 Used 持續 > 0，代表記憶體已經不夠）："
swapon --show 2>/dev/null || echo "  （沒有 swap —— 建議加 4GB，見 DEPLOY.md）"
echo

echo "── 2. 各容器實際用量 vs 上限 ───────────────────────────────"
echo
docker stats --no-stream --format \
  '{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.CPUPerc}}' \
  | sort -t$'\t' -k2 -h -r \
  | awk -F'\t' 'BEGIN{printf "%-26s %-22s %8s %8s\n","CONTAINER","MEM (used / limit)","MEM%","CPU%"; print "---------------------------------------------------------------------"}
               {printf "%-26s %-22s %8s %8s\n",$1,$2,$3,$4}'
echo

echo "── 3. 加總 ─────────────────────────────────────────────────"
docker stats --no-stream --format '{{.MemUsage}}' \
  | awk -F' / ' '
    function toMiB(s,   v,u) {
      v = s; u = s
      gsub(/[A-Za-z]+$/,"",v); gsub(/^[0-9.]+/,"",u)
      if (u ~ /^GiB/) return v*1024
      if (u ~ /^MiB/) return v
      if (u ~ /^KiB/) return v/1024
      if (u ~ /^B/)   return v/1048576
      return 0
    }
    { used += toMiB($1); lim += toMiB($2) }
    END {
      printf "  實際用量總計 : %8.0f MiB  (%.2f GB)\n", used, used/1024
      printf "  上限總和     : %8.0f MiB  (%.2f GB)\n", lim,  lim/1024
      printf "  實際/上限    : %8.1f %%\n", used/lim*100
    }'
echo

echo "── 4. 有沒有被 OOM killer 殺過 ─────────────────────────────"
if dmesg 2>/dev/null | grep -qi 'killed process'; then
  echo "  ⚠ 有！以下是紀錄："
  dmesg | grep -i 'killed process' | tail -5
else
  echo "  ✓ 沒有（dmesg 無 'Killed process' 紀錄）"
fi
echo
echo "  容器被 OOM 殺掉的紀錄（OOMKilled=true 就是中獎了）："
docker ps -a --format '{{.Names}}' | while read -r c; do
  oom=$(docker inspect -f '{{.State.OOMKilled}}' "$c" 2>/dev/null)
  [ "$oom" = "true" ] && echo "    ⚠ $c  OOMKilled"
done
echo "    （上面沒列出任何東西 = 全部正常）"
echo

echo "── 5. 重啟次數（頻繁重啟常常就是記憶體不足）────────────────"
docker ps -a --format '{{.Names}}' | while read -r c; do
  n=$(docker inspect -f '{{.RestartCount}}' "$c" 2>/dev/null)
  [ "${n:-0}" -gt 2 ] && echo "    ⚠ $c  重啟 $n 次"
done
echo "    （上面沒列出任何東西 = 全部穩定）"
echo

echo "════════════════════════════════════════════════════════════"
echo " 怎麼讀這份報告"
echo "════════════════════════════════════════════════════════════"
cat <<'EOF'

  看第 1 段的 free -h「available」欄：

    > 4 GB  → 很寬裕。可以把 ELK 開回來試試：
                docker compose --env-file .env.prod \
                  -f docker-compose.prod.yml --profile elk up -d

    2～4 GB → 剛好。維持現狀，不要再加東西。

    < 2 GB  → 危險。Postgres 隨時可能被 OOM killer 殺掉。
              先加 swap；還是不夠就得升級到 32GB（CX53）。

  第 4 段只要出現任何一行 ⚠，就別再猶豫了，直接升級。
  記憶體不足造成的資料庫損毀，省下的那點錢不值得。

EOF
echo "報告已存到 $OUT"
