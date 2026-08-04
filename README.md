# Czochralski Digital Twin

以事件驅動架構實作的長晶爐數位孿生平台，整合即時資料採集、串流處理、微服務、時序資料庫、3D 視覺化、告警與可觀測性。

專案目前以多台 Czochralski 長晶爐為監控對象，將 CSV／設備資料轉換為 MQTT 與 Kafka 事件，經 Apache Flink 處理後寫入 TimescaleDB、MongoDB、Redis 與 Elasticsearch，再由 Spring Boot API 提供即時爐況、歷史趨勢、告警與 WebSocket 資料給前端使用。

## 系統架構

```text
CSV / OPC-UA / Equipment Data
             │
             ▼
      Python Datapipe
             │ MQTT
             ▼
     Eclipse Mosquitto
             │
             ▼
      IoT Gateway Service
             │ Kafka
             ▼
   Apache Kafka / Flink CEP
       │        │        │
       │        │        └── Alarm Events
       │        └─────────── Sensor Events
       └──────────────────── Furnace Metrics
             │
   ┌─────────┼──────────┬──────────────┐
   ▼         ▼          ▼              ▼
TimescaleDB MongoDB    Redis       Elasticsearch
   │         │          │              │
   └─────────┴──────┬───┴──────────────┘
                    ▼
         Spring Boot Microservices
                    │
       REST / WebSocket / SSE / OAuth2
                    │
                    ▼
      Vue + Vite + Three.js + ECharts
```

## 核心功能

- 多台長晶爐即時狀態監控
- CSV／設備資料模擬與 MQTT 推播
- MQTT 到 Kafka 的事件橋接
- Kafka 分區與同一晶棒資料順序保證
- Apache Flink 即時串流處理與 CEP 異常偵測
- TimescaleDB 長晶製程時序資料儲存
- MongoDB 感測器原始資料與告警事件儲存
- Redis 即時爐況快取與狀態發布
- Spring Cloud Gateway、Eureka 與 Config Server
- JWT／OAuth2 登入與角色權限控制
- WebSocket／STOMP 即時資料更新
- Three.js 數位孿生 3D 視覺化
- ECharts／Chart.js 製程趨勢與 KPI 圖表
- Slack 告警通知
- Prometheus、Grafana 與 ELK 可觀測性
- Docker Compose 本機與正式環境部署
- Caddy HTTPS、反向代理與管理介面保護

## 技術棧

### Backend

| 技術 | 用途 |
|---|---|
| Java 17 | 微服務與 Flink Job 編譯目標 |
| Spring Boot / Spring Cloud | API、服務發現、集中設定、Gateway |
| Spring Security | JWT、OAuth2、RBAC |
| Apache Kafka | 事件串流與服務解耦 |
| Apache Flink 2.2 | 串流運算、狀態處理與 CEP |
| MQTT / Eclipse Mosquitto | 設備與邊緣資料傳輸 |
| Python 3.11 | CSV 資料管線與設備資料模擬 |

### Frontend

| 技術 | 用途 |
|---|---|
| Vue | SPA 與操作介面 |
| Vite | 前端建置工具 |
| Pinia | 狀態管理 |
| Vue Router | 路由與權限導向 |
| Three.js | 長晶爐 3D 數位孿生 |
| ECharts / Chart.js | KPI、時序趨勢與製程圖表 |
| Axios | REST API Client |
| STOMP / SockJS | WebSocket 即時通訊 |

### Data and Observability

| 技術 | 用途 |
|---|---|
| TimescaleDB | 長晶製程時序資料與 hypertable |
| MongoDB | 感測器原始資料與告警訊息 |
| Redis | 即時狀態快取與 Pub/Sub |
| Elasticsearch / Logstash / Kibana | 集中式日誌與查詢 |
| Prometheus | 指標收集與告警規則 |
| Grafana | 系統與服務監控 Dashboard |

## 微服務

| Service | Port | 職責 |
|---|---:|---|
| Eureka Server | 8761 | 服務註冊與發現 |
| Config Server | 8888 | 集中式設定管理 |
| API Gateway | 8085 | API 路由、認證與跨服務入口 |
| IoT Gateway | 8090 | MQTT 訊息轉換與 Kafka Producer |
| Furnace Service | 8091 | 爐況、歷史資料與 WebSocket |
| Alarm Service | 8092 | 告警消費、查詢與 Slack 通知 |
| Twin State Service | 8093 | Redis 即時狀態與 SSE |
| Auth Service | 依 Compose 設定 | 登入、JWT、OAuth2 與使用者管理 |

## Kafka Topics

| Topic | Partition Key | 用途 |
|---|---|---|
| `furnace-data` | `INGOT_NO` | 長晶爐即時製程資料 |
| `sensor-events` | `INGOT_NO` | 感測器原始事件 |
| `alarm-events` | `furnace_id` | 製程異常與告警事件 |

同一晶棒的資料以 `INGOT_NO` 作為 Partition Key，使事件在同一 Kafka Partition 中依序處理。

## 資料儲存策略

| 資料類型 | 儲存位置 |
|---|---|
| 長晶爐時序資料 | TimescaleDB `furnace_metrics` hypertable |
| 爐台與晶棒基本資料 | TimescaleDB relational tables |
| 感測器原始資料 | MongoDB `sensor_data` |
| 告警事件 | MongoDB `alarm_messages` |
| 即時爐況 | Redis `furnace:{id}` |
| 系統與應用日誌 | Elasticsearch `system_logs-*` |
| Flink 狀態 | Flink checkpoint volume |

## 專案目錄

```text
czochralski-digital-twin/
├── datapipe/                    # Python 資料產生與 MQTT 推播
├── flink-jobs/                  # Flink 串流處理與 CEP Jobs
├── frontend/                    # Vue、Three.js、ECharts 前端
├── services/
│   ├── eureka-server/
│   ├── config-server/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── iot-gateway/
│   ├── furnace-service/
│   ├── alarm-service/
│   └── twin-state-service/
├── infra/
│   ├── mosquitto/
│   ├── kafka/
│   ├── flink/
│   ├── timescaledb/
│   ├── mongodb/
│   ├── logstash/
│   ├── prometheus/
│   └── grafana/
├── scripts/                     # 啟動、部署與資源檢查腳本
├── docker-compose.yml           # 本機開發環境
├── docker-compose.prod.yml      # 正式環境
├── Caddyfile                    # HTTPS 與反向代理
├── .env.example                 # 環境變數範例
└── DEPLOY.md                    # Hetzner 正式部署文件
```

## 效能測試

正式站的唯讀 k6 基線、breakpoint 結果與重現方式請參考
[load-tests/README.md](load-tests/README.md)。高壓 profile 會在錯誤率或延遲超標時自動停止。

## 快速啟動

### 系統需求

- Docker Engine 24+
- Docker Compose Plugin 2.20+
- Java 17
- Maven 3.9+
- Python 3.11+
- Node.js 20.19+ 或 22.12+
- 建議至少 16 GB RAM

### 1. 取得專案

```bash
git clone https://github.com/Tommy840602/czochralski-digital-twin.git
cd czochralski-digital-twin
```

### 2. 建立環境變數

```bash
cp .env.example .env
```

請修改 `.env` 中的資料庫密碼、JWT Secret、OAuth Client Secret、Slack Webhook 與其他敏感設定。

不要將 `.env`、私鑰、Token 或正式環境帳密提交到 Git。

### 3. 編譯 Java 服務

各服務 Dockerfile 會複製本機已產生的 JAR，因此修改 Java 程式後必須先執行 Maven Build。

```bash
cd services
mvn clean package -DskipTests
cd ..

cd flink-jobs
mvn clean package -DskipTests
cd ..
```

### 4. 啟動基礎設施與服務

```bash
docker compose up -d --build
```

也可以使用專案腳本：

```bash
chmod +x scripts/start.sh
./scripts/start.sh up
```

### 5. 查看狀態

```bash
docker compose ps
./scripts/start.sh status
```

### 6. 查看日誌

```bash
docker compose logs -f api-gateway
docker compose logs -f furnace-service
docker compose logs -f flink-jobmanager
```

### 7. 停止服務

```bash
docker compose down
```

清除 Volume 與所有持久化資料：

```bash
docker compose down -v
```

此操作會刪除 TimescaleDB、MongoDB、Kafka、Redis、Grafana 與其他 Volume 中的資料。

## 前端開發

```bash
cd frontend
npm install
npm run dev
```

正式建置：

```bash
npm run lint
npm run build
```

## 常用管理介面

本機環境的實際 Port 以 `docker-compose.yml` 為準。

| 介面 | 預設位置 |
|---|---|
| Kafka UI | `http://localhost:8080` |
| Schema Registry | `http://localhost:8081` |
| Flink UI | `http://localhost:8083` |
| Eureka | `http://localhost:8761` |
| Config Server | `http://localhost:8888` |
| API Gateway | `http://localhost:8085` |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Kibana | `http://localhost:5601` |

## 驗證 Kafka Topics

```bash
docker exec twin-kafka kafka-topics \
  --bootstrap-server localhost:9093 \
  --list
```

預期包含：

```text
furnace-data
sensor-events
alarm-events
```

## 驗證 TimescaleDB

```bash
docker exec -it twin-timescaledb \
  psql -U twin -d furnace_db -c "\dt"
```

## 驗證 MongoDB

請從 `.env` 取得帳號密碼，不要將正式密碼直接寫在指令歷史或 README。

```bash
docker exec -it twin-mongodb \
  mongosh --authenticationDatabase admin twin_db
```

## 正式環境部署

正式環境使用：

- Ubuntu 24.04
- Docker Compose
- Caddy
- Let's Encrypt TLS
- UFW Firewall
- 僅開放 `22`、`80`、`443`
- 資料庫與 Middleware 僅允許 Docker Internal Network 存取
- Grafana、Kafka UI、Flink UI、Prometheus 與 Kibana 透過 HTTPS 子網域存取
- 管理介面使用登入機制或 Reverse Proxy Basic Authentication

部署細節、DNS、Cloudflare、Swap、記憶體限制、備份與 SSH Tunnel 請參考 [DEPLOY.md](DEPLOY.md)。

### 可選服務 Profile

正式環境預設可關閉高記憶體或非必要服務：

```bash
# 啟用 ELK
docker compose --env-file .env.prod \
  -f docker-compose.prod.yml \
  --profile elk up -d

# 啟用額外管理服務
docker compose --env-file .env.prod \
  -f docker-compose.prod.yml \
  --profile extras up -d
```

## 安全原則

- 正式環境不得直接暴露 Kafka、Redis、MongoDB、TimescaleDB、Elasticsearch 或 Config Server Port
- 所有 Secret 必須由環境變數、Secret Manager 或部署平台注入
- JWT Secret 應使用高熵隨機值並定期輪替
- OAuth Callback URL 必須與正式網域一致
- 管理介面必須啟用認證
- 正式環境僅允許 HTTPS
- 使用 UFW 或雲端 Firewall 限制入站連線
- 資料庫維運應使用 SSH Tunnel 或私有網路
- Docker Log 必須設定 Rotation，避免磁碟耗盡
- 建議定期備份 TimescaleDB、MongoDB、Grafana 與必要 Volume

## 可觀測性

平台提供以下監控層：

- Spring Boot Actuator 指標
- Kafka JMX Exporter
- Node Exporter 主機指標
- Prometheus Metrics 與 Alert Rules
- Grafana Dashboard
- Elasticsearch、Logstash 與 Kibana 日誌查詢
- Docker Container Health Check
- Docker JSON Log Rotation

## 開發注意事項

1. Java 程式修改後，先執行 `mvn package`，再執行 `docker compose build`。
2. Flink Job 必須產生 Fat JAR。
3. Flink 2.x Sink 使用 SinkV2 API。
4. TimescaleDB 存取使用 JPA。
5. Slack 通知集中由 Alarm Service 發送。
6. Config Server 引用的環境變數必須同時注入 Config Server Container。
7. 前端以 JavaScript 為主，不新增 TypeScript 檔案。
8. 正式環境資料層不可發布 Host Port。

## License

此專案目前未指定開源授權。未經作者同意，不得將程式碼用於商業散布或再授權。
