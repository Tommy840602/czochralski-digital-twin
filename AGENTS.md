# 長晶爐數位孿生 — AGENTS.md

## 專案定位
Event-Driven 大數據數位孿生：兩台長晶爐(C1/C2)即時監控。
OPC-UA/MQTT 採集 → Kafka → Flink CEP → 多儲存層 → Spring Boot API → Three.js 3D 視覺化。

## 關鍵規則

- **全專案統一 Java 17（Spring Boot 25 runtime，Flink 2.2 預設 Java 17）**
- **前端只用 .js / .jsx，禁止 .ts / .tsx**
- **不要自動加 TypeScript**，package.json 不加 typescript 依賴
- **Flink Job 必須打 fat JAR**（maven-shade-plugin），不是 Spring Boot jar
- **Flink 2.x Sink 一律用 SinkV2**，SinkFunction/SinkV1 已移除
- **Flink 設定檔用 config.yaml**，不是舊版 flink-conf.yaml
- 所有 Spring Boot 服務都要加 `@EnableDiscoveryClient` 對接 Eureka
- TimescaleDB 用 JPA，不用 R2DBC
- Slack 通知只在 `AlarmService` 裡觸發，其他服務不直接呼叫

## 版本矩陣

| 元件 | 版本 | Java |
|---|---|---|
| Spring Boot | 4.0.6 | 25（compile target 17+） |
| Spring Cloud | 2025.1.0 (Oakwood) | — |
| Apache Flink | **2.2.1** | **17**（與 Spring Boot 同一 JDK） |
| Kafka Connector | 3.4.0-2.0 | — |
| Python Datapipe | 3.11 | — |
| Three.js | r165 | — |

## 架構速查

```
datapipe/             Python — CSV → MQTT (Part 1)
flink-jobs/           Java 17 — Flink 2.2 CEP (Part 3)  ← 不再是 Java 11
services/
  eureka-server/      port 8761
  config-server/      port 8888
  api-gateway/        port 8085  Spring Cloud Gateway
  iot-gateway/        port 8090  MQTT → Kafka Bridge
  furnace-service/    port 8091  TimescaleDB + WebSocket
  alarm-service/      port 8092  MongoDB + Slack
  twin-state-service/ port 8093  Redis SSE
frontend/             React 18 + Three.js 
infra/                Docker Compose 17 個服務 
```

## 服務啟動順序
Docker infra → eureka → config → iot-gateway → furnace/alarm/twin-state → api-gateway

## Kafka Topics
| Topic | Partition Key | Consumer |
|---|---|---|
| furnace-data | INGOT_NO | FurnaceStreamJob (Flink) |
| sensor-events | INGOT_NO | SensorJob (Flink) |
| alarm-events | furnace_id | AlarmService |

## 資料欄位
CSV 關鍵欄位：`LogTime, Diameter, Heater temp, GR_mean, Seed lift, Body length, Heater Power SV, INGOT_NO, Event`
- `Event=1` → OK 爐 (C225C34E)
- `Event=6` → NG 爐 (C226654E)，CSV 結束時發 Slack

## DB 對應
| 資料 | 儲存 |
|---|---|
| 長晶爐時序 | TimescaleDB `furnace_metrics` hypertable |
| 感測器原始 | MongoDB `sensor_data` |
| 告警訊息 | MongoDB `alarm_messages` |
| 即時爐況 | Redis Hash `furnace:{id}` TTL=60s |
| 系統 Log | Elasticsearch `system_logs-*` |

## 詳細文件
@docs/architecture.md
@docs/api-spec.md
@docs/data-schema.md
@docs/progress.md
