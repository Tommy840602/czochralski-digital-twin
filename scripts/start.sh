#!/usr/bin/env bash
# ============================================================
#  長晶爐數位孿生 — 基礎設施啟動腳本
#  用法: ./scripts/start.sh [up|down|status|logs]
# ============================================================
set -euo pipefail

COMPOSE_FILE="$(dirname "$0")/../docker-compose.yml"
PROJECT_NAME="czochralski-twin"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[TWIN]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERR]${NC} $*"; }

wait_healthy() {
    local svc="$1"
    local max="${2:-60}"
    local i=0
    log "等待 $svc 健康..."
    while [ $i -lt $max ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "twin-${svc}" 2>/dev/null || echo "unknown")
        if [ "$status" = "healthy" ]; then
            ok "$svc 已就緒"
            return 0
        fi
        sleep 2; i=$((i+2))
        echo -n "."
    done
    echo
    err "$svc 啟動超時 (${max}s)"
    return 1
}

download_jmx_agent() {
    local jar="./infra/kafka/jmx_prometheus_javaagent.jar"
    if [ ! -f "$jar" ]; then
        log "下載 JMX Prometheus Agent..."
        curl -sL "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar" \
            -o "$jar"
        ok "JMX Agent 下載完成"
    fi
}

cmd_up() {
    log "=== 啟動 長晶爐數位孿生 基礎設施 ==="

    # 確認必要檔案
    download_jmx_agent

    # Phase 1: 基礎服務
    log "[Phase 1] 啟動 MQTT + ZooKeeper..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d mosquitto zookeeper
    wait_healthy "mosquitto" 30
    wait_healthy "zookeeper" 30

    # Phase 2: Kafka
    log "[Phase 2] 啟動 Kafka..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d kafka
    wait_healthy "kafka" 90

    # Phase 3: Schema Registry + Init
    log "[Phase 3] 啟動 Schema Registry 並建立 Topics..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d schema-registry kafka-init
    wait_healthy "schema-registry" 60
    sleep 5

    # Phase 4: 資料庫
    log "[Phase 4] 啟動 資料庫層..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d timescaledb mongodb redis
    wait_healthy "timescaledb" 60
    wait_healthy "mongodb" 60
    wait_healthy "redis" 30

    # Phase 5: Elasticsearch
    log "[Phase 5] 啟動 Elasticsearch (需要較長時間)..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d elasticsearch
    wait_healthy "elasticsearch" 120

    # Phase 6: Kibana + Logstash
    log "[Phase 6] 啟動 Kibana + Logstash..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d kibana logstash

    # Phase 7: Flink
    log "[Phase 7] 啟動 Flink..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d flink-jobmanager
    wait_healthy "flink-jobmanager" 60
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d flink-taskmanager

    # Phase 8: 監控
    log "[Phase 8] 啟動 Prometheus + Grafana..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d prometheus
    wait_healthy "prometheus" 30
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" up -d grafana kafka-ui
    wait_healthy "grafana" 60

    echo
    ok "=== 所有基礎設施已就緒 ==="
    echo
    cmd_status
    echo
    echo -e "${CYAN}服務入口:${NC}"
    echo "  Kafka UI      : http://localhost:8080"
    echo "  Flink Web UI  : http://localhost:8082"
    echo "  Grafana       : http://localhost:3000  (admin / twin_admin)"
    echo "  Kibana        : http://localhost:5601"
    echo "  Prometheus    : http://localhost:9090"
    echo "  Schema Reg.   : http://localhost:8081"
    echo "  TimescaleDB   : localhost:5432  (twin / twin_secret)"
    echo "  MongoDB       : localhost:27017 (twin / twin_secret)"
    echo "  Redis         : localhost:6379"
    echo "  MQTT Broker   : localhost:1883"
}

cmd_down() {
    log "停止所有服務..."
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" down
    ok "完成"
}

cmd_status() {
    log "=== 服務狀態 ==="
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
}

cmd_logs() {
    local svc="${2:-}"
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" logs -f --tail=100 $svc
}

cmd_reset() {
    warn "⚠ 這將刪除所有 Volume (資料不可恢復)！"
    read -rp "確認重置? [y/N] " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" down -v
        ok "已清除所有 Volume"
    else
        log "取消"
    fi
}

case "${1:-up}" in
    up)     cmd_up ;;
    down)   cmd_down ;;
    status) cmd_status ;;
    logs)   cmd_logs "$@" ;;
    reset)  cmd_reset ;;
    *)
        echo "用法: $0 [up|down|status|logs [service]|reset]"
        exit 1
        ;;
esac
