# 長晶爐數位孿生 — 基礎設施

Czochralski Crystal Growth Furnace Digital Twin — Infrastructure Layer

## 目錄結構

```
czochralski-digital-twin/
├── docker-compose.yml              # 主設定檔
├── scripts/
│   └── start.sh                   # 一鍵啟動腳本
└── infra/
    ├── mosquitto/config/           # MQTT Broker 設定
    ├── kafka/                      # JMX Exporter
    ├── flink/                      # Flink 設定
    ├── timescaledb/init.sql        # 長晶爐 Hypertable Schema
    ├── mongodb/init.js             # sensor_data + alarm_messages
    ├── logstash/pipeline/          # Log Pipeline
    ├── prometheus/                 # Scrape 設定 + Alert Rules
    └── grafana/provisioning/       # Datasource + Dashboard
```

## 服務清單

| 服務             | 說明                      | Port           |
|-----------------|---------------------------|----------------|
| Mosquitto       | MQTT Broker               | 1883, 9001(WS) |
| Kafka           | 訊息佇列                   | 9092           |
| Schema Registry | Avro Schema 管理           | 8081           |
| Kafka UI        | Kafka 管理介面              | 8080           |
| Flink           | 串流處理                   | 8082 (Web UI)  |
| TimescaleDB     | 長晶爐時序數據              | 5432           |
| MongoDB         | 感測器數據 + Alarms         | 27017          |
| Redis           | 即時快取 + Pub/Sub          | 6379           |
| Elasticsearch   | 系統 Log                  | 9200           |
| Kibana          | Log 分析 UI               | 5601           |
| Logstash        | Log Pipeline              | 5044, 5000     |
| Prometheus      | 指標收集                   | 9090           |
| Grafana         | 監控 Dashboard            | 3000           |
| Node Exporter   | 主機指標                   | 9100 (host)    |

## 快速啟動

### 需求
- Docker Engine >= 24.0
- Docker Compose Plugin >= 2.20
- 可用記憶體 >= 8GB
- 磁碟空間 >= 10GB

### 步驟

```bash
# 1. 給腳本執行權限
chmod +x scripts/start.sh

# 2. 一鍵啟動 (依序等待各服務健康)
./scripts/start.sh up

# 3. 查看狀態
./scripts/start.sh status

# 4. 查看特定服務 Log
./scripts/start.sh logs kafka
./scripts/start.sh logs flink-jobmanager

# 5. 停止
./scripts/start.sh down

# 6. 清除所有資料 (慎用)
./scripts/start.sh reset
```

### 驗證 Kafka Topics

```bash
docker exec twin-kafka kafka-topics \
  --bootstrap-server localhost:9093 --list
# 應看到: furnace-data / sensor-events / alarm-events
```

### 驗證 TimescaleDB

```bash
docker exec -it twin-timescaledb \
  psql -U twin -d furnace_db -c "\dt"
# 應看到: furnace_metrics / furnace_info
```

### 驗證 MongoDB

```bash
docker exec -it twin-mongodb \
  mongosh -u twin -p twin_secret --authenticationDatabase admin twin_db \
  --eval "show collections"
# 應看到: sensor_data / alarm_messages / flink_state
```

## 帳號密碼

| 服務          | 帳號       | 密碼              |
|-------------|-----------|------------------|
| TimescaleDB | twin      | twin_secret       |
| MongoDB     | twin      | twin_secret       |
| Grafana     | admin     | twin_admin        |

## Kafka Topics 說明

| Topic          | Partition | 用途                     | Flink Consumer    |
|---------------|-----------|--------------------------|-------------------|
| furnace-data  | 3         | 長晶爐即時數據 (每10s)    | FurnaceStreamJob  |
| sensor-events | 3         | 感測器原始數據            | SensorJob         |
| alarm-events  | 1         | 告警事件 → Slack          | AlarmService      |

## Partition Key
所有 Topic 均以 `INGOT_NO`（如 C225C34E / C226654E）作為 Partition Key，
確保同一爐子的數據按序處理。

