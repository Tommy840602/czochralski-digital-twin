# Flink Jobs — 子模組說明

- **Flink 2.2.1，Java 17（與 Spring Boot 服務用同一個 JDK，不再需要雙版本）**
- 打 fat JAR 用 maven-shade-plugin，Main Class = `com.twin.flink.job.FurnaceStreamJob`
- `scope=provided`：flink-streaming-java, flink-clients（Flink cluster 已有）
- **Flink 2.x 重要變更：**
  - SinkFunction / SinkV1 已移除，一律用 SinkV2（`org.apache.flink.api.connector.sink2`）
  - DataSet API 已移除，只用 DataStream API
  - 設定檔改為 `config.yaml`（不再支援 `flink-conf.yaml`）
- CEP Pattern 在 `cep/` 下，Sink 在 `sink/` 下
- 提交指令：`flink run -c com.twin.flink.job.FurnaceStreamJob target/flink-jobs-1.0.0-SNAPSHOT.jar`
- Kafka Connector 版本：`flink-connector-kafka:3.4.0-2.0`
