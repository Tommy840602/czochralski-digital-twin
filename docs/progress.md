# 開發進度

## Part 8 — Docker 基礎設施
- [x] docker-compose.yml（17 個服務）
- [x] mosquitto.conf
- [x] kafka-jmx-config.yml
- [x] flink-conf.yaml
- [x] timescaledb/init.sql（hypertable + 壓縮 + 保留政策）
- [x] mongodb/init.js（sensor_data + alarm_messages）
- [x] logstash/pipeline/logstash.conf
- [x] prometheus.yml + alerts.yml
- [x] grafana datasources.yml
- [x] scripts/start.sh

## Part 4 — DB Schema（含在 Part 8）
- [x] TimescaleDB hypertable
- [x] MongoDB collections + index
- [ ] TimescaleDB migrations (Flyway)
- [ ] ES index template

## Part 1 — Python Datapipe
- [ ] csv_reader.py
- [ ] event_emitter.py（Event=6 結束發 alarm）
- [ ] opcua_server.py
- [ ] mqtt/publisher.py
- [ ] config/settings.yaml
- [ ] Dockerfile

## Part 2 — Spring Boot IoT Gateway
- [x] pom.xml（全部 7 個子模組）
- [x] EurekaServerApplication.java + application.yml
- [x] ConfigServerApplication.java + configs/
- [x] ApiGatewayApplication.java + application.yml
- [x] IotGatewayApplication.java + application.yml
- [ ] MqttToKafkaBridge.java
- [ ] MqttConfig.java
- [ ] KafkaProducerConfig.java
- [ ] FurnaceKafkaProducer.java
- [ ] FurnaceMessage.java（Avro schema）

## Part 3 — Flink Jobs
- [x] flink-jobs/pom.xml（shade plugin）
- [ ] FurnaceStreamJob.java
- [ ] SensorJob.java
- [ ] DiameterDriftPattern.java
- [ ] HeaterOverheatPattern.java
- [ ] NgDisconnectPattern.java
- [ ] TimescaleDbSink.java
- [ ] MongoDbSink.java
- [ ] RedisSink.java
- [ ] ElasticsearchSink.java
- [ ] FurnaceReading.java
- [ ] AlarmEvent.java

## Part 5 — 微服務實作
- [x] FurnaceServiceApplication.java
- [x] AlarmServiceApplication.java
- [x] TwinStateServiceApplication.java
- [x] 所有 application.yml
- [ ] FurnaceController.java
- [ ] FurnaceService.java
- [ ] FurnaceMetricsRepository.java
- [ ] FurnaceWebSocketHandler.java
- [ ] AlarmEventConsumer.java（@KafkaListener）
- [ ] SlackNotifier.java（Webhook + Retry）
- [ ] AlarmController.java
- [ ] AlarmRepository.java
- [ ] RedisStateStore.java
- [ ] TwinStateController.java（SSE）

## Part 6 — Three.js 前端
- [ ] vite.config.js
- [ ] package.json
- [ ] main.jsx
- [ ] App.jsx
- [ ] FurnaceScene.jsx（Three.js Canvas）
- [ ] FurnaceModel.jsx（GLTFLoader furnace.glb × 2）
- [ ] FurnaceOverlay.jsx
- [ ] Dashboard.jsx
- [ ] StatusPanel.jsx
- [ ] AlarmList.jsx
- [ ] TemperatureChart.jsx（Recharts）
- [ ] DiameterChart.jsx
- [ ] useFurnaceWebSocket.js（STOMP）
- [ ] useAlarms.js（SSE）
- [ ] furnaceStore.js（Zustand）
- [ ] alarmStore.js

## Part 7 — 監控
- [x] prometheus.yml + alerts.yml
- [x] grafana datasources.yml
- [ ] grafana/dashboards/furnace-kpi.json
- [ ] grafana/dashboards/kafka-flink.json
- [ ] elasticsearch/templates/system_logs.json
