package com.twin.flink.job;

import com.twin.flink.cep.DiameterDriftPattern;
import com.twin.flink.cep.HeaterOverheatPattern;
import com.twin.flink.cep.NgDisconnectPattern;
import com.twin.flink.model.AlarmEvent;
import com.twin.flink.model.FurnaceReading;
import com.twin.flink.schema.FurnaceDeserializer;
import com.twin.flink.sink.MongoDbSink;
import com.twin.flink.sink.RedisSink;
import com.twin.flink.sink.TimescaleDbSink;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * FurnaceStreamJob — Flink 串流處理（彈性多爐版）
 *
 * 彈性設計：
 *   - Kafka source 從 furnace-data topic 消費，keyBy(furnaceId)
 *   - furnaceId 直接來自 Kafka message（PULLER 欄位），不寫死
 *   - 新增爐子後 datapipe 自動發送到同一 topic，Flink 自動處理
 *
 * Sink：
 *   - TimescaleDbSink  → furnace_metrics（44 個感測器欄位）
 *   - RedisSink        → furnace:{furnaceId} Hash（即時爐況）
 *   - MongoDbSink      → sensor_data（原始數據備份）
 *
 * CEP Pattern（每台爐子獨立觸發）：
 *   - DiameterDrift    → 直徑偏離 target > 5mm，連續 3 次
 *   - HeaterOverheat   → 爐溫 > 1450°C，連續 3 次
 *   - NgDisconnect     → 保留相容舊格式（event=6）
 */
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.functions.OpenContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

public class FurnaceStreamJob {

    static final String KAFKA = System.getenv().getOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "twin-kafka:9093");
    static final String MONGO = System.getenv().getOrDefault(
            "MONGO_URI", "mongodb://twin:twin_secret@twin-mongodb:27017/twin_db?authSource=admin");
    static final String RHOST = System.getenv().getOrDefault("REDIS_HOST",  "twin-redis");
    static final int    RPORT = Integer.parseInt(
            System.getenv().getOrDefault("REDIS_PORT", "6379"));
    /** 生產環境的 Redis 有 requirepass；本機開發沒有，留空即可 */
    static final String RPASS = System.getenv().getOrDefault("REDIS_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        // 容錯：task 失敗時自動重試（最多重試很多次，每次間隔10秒），
        // 避免單一異常資料（如未知 furnace_id）造成整個 job 直接死掉
        org.apache.flink.configuration.Configuration conf = new org.apache.flink.configuration.Configuration();
        conf.set(org.apache.flink.configuration.RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        conf.set(org.apache.flink.configuration.RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 2147483647);
        conf.set(org.apache.flink.configuration.RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY,
                java.time.Duration.ofSeconds(10));
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(conf);
        env.setParallelism(1);
        // 關閉 checkpoint，使用記憶體 state backend
        env.getCheckpointConfig().disableCheckpointing();

        // ── Source：Kafka furnace-data topic ──────────────────────────────
        KafkaSource<FurnaceReading> source = KafkaSource.<FurnaceReading>builder()
                .setBootstrapServers(KAFKA)
                .setTopics("furnace-data")
                .setGroupId("flink-furnace-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new FurnaceDeserializer())
                .build();

        DataStream<FurnaceReading> stream = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka-furnace-data")
                .filter((FilterFunction<FurnaceReading>) r ->
                        r != null && r.getFurnaceId() != null && !r.getFurnaceId().isBlank())
                .filter(new RegistryFilter());

        // ── Sink：TimescaleDB（全 44 欄） ──────────────────────────────────
        stream.sinkTo(new TimescaleDbSink()).name("sink-timescaledb");

        // ── Sink：Redis（即時爐況，TTL=60s） ──────────────────────────────
        stream.sinkTo(new RedisSink(RHOST, RPORT, RPASS)).name("sink-redis");

        // ── Sink：MongoDB（原始備份） ─────────────────────────────────────
        stream.sinkTo(new MongoDbSink(MONGO)).name("sink-mongodb");

        // ── KeyBy furnaceId（支援任意數量爐子） ───────────────────────────
        KeyedStream<FurnaceReading, String> keyed = stream.keyBy(
                (KeySelector<FurnaceReading, String>) FurnaceReading::getFurnaceId);

        // ── CEP：直徑偏離 ────────────────────────────────────────────────
        CEP.pattern(keyed, DiameterDriftPattern.build())
                .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                    @Override
                    public void processMatch(Map<String, List<FurnaceReading>> m,
                                             Context c, Collector<AlarmEvent> out) {
                        FurnaceReading r = m.get("drift").get(0);
                        out.collect(new AlarmEvent(
                                "DiameterDrift", r.getFurnaceId(), r.getIngotNo(),
                                "WARN",
                                String.format("[%s] 直徑偏離 target %.1f mm (target %.1f mm)",
                                        r.getFurnaceId(),
                                        r.getDiameter() != null ? r.getDiameter() : 0.0,
                                        r.getDiameterTarget() != null ? r.getDiameterTarget() : 0.0),
                                Instant.now().toString(),
                                r.getDiameter(), r.getHeaterTemp(),
                                r.getOperationMode()
                        ));
                    }
                }).print("ALARM-DIAM");

        // ── CEP：爐溫過高 ────────────────────────────────────────────────
        CEP.pattern(keyed, HeaterOverheatPattern.build())
                .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                    @Override
                    public void processMatch(Map<String, List<FurnaceReading>> m,
                                             Context c, Collector<AlarmEvent> out) {
                        FurnaceReading r = m.get("overheat").get(0);
                        out.collect(new AlarmEvent(
                                "HeaterOverheat", r.getFurnaceId(), r.getIngotNo(),
                                "CRITICAL",
                                String.format("[%s] 爐溫過高 %.1f°C (閾值 1450°C)",
                                        r.getFurnaceId(),
                                        r.getHeaterTemp() != null ? r.getHeaterTemp() : 0.0),
                                Instant.now().toString(),
                                r.getDiameter(), r.getHeaterTemp(),
                                r.getOperationMode()
                        ));
                    }
                }).print("ALARM-HEAT");

        // ── CEP：NgDisconnect（相容舊格式） ──────────────────────────────
        CEP.pattern(keyed, NgDisconnectPattern.build())
                .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                    @Override
                    public void processMatch(Map<String, List<FurnaceReading>> m,
                                             Context c, Collector<AlarmEvent> out) {
                        FurnaceReading r = m.get("ng").get(0);
                        out.collect(new AlarmEvent(
                                "NgDisconnect", r.getFurnaceId(), r.getIngotNo(),
                                "CRITICAL",
                                String.format("[%s] NG 事件偵測 (event=6)", r.getFurnaceId()),
                                Instant.now().toString(),
                                r.getDiameter(), r.getHeaterTemp(),
                                r.getOperationMode()
                        ));
                    }
                }).print("ALARM-NG");

        env.execute("FurnaceStreamJob-v2");
    }
    /**
     * 動態過濾：只保留存在於 furnace_registry 的爐子資料。
     * 啟動時從 DB 載入清單，彈性支援新增爐子無需改程式碼。
     */
    public static class RegistryFilter extends RichFilterFunction<FurnaceReading> {
        private transient Set<String> knownFurnaces;

        @Override
        public void open(OpenContext openContext) throws Exception {
            super.open(openContext);
            knownFurnaces = new HashSet<>();
            String url  = System.getenv().getOrDefault(
                    "TIMESCALE_URL", "jdbc:postgresql://twin-timescaledb:5432/furnace_db");
            String user = System.getenv().getOrDefault("TIMESCALE_USER", "twin");
            String pass = System.getenv().getOrDefault("TIMESCALE_PASSWORD", "twin_secret");
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 PreparedStatement ps = conn.prepareStatement("SELECT furnace_id FROM furnace_registry");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    knownFurnaces.add(rs.getString("furnace_id"));
                }
            }
        }

        @Override
        public boolean filter(FurnaceReading r) {
            return knownFurnaces.contains(r.getFurnaceId());
        }
    }
}