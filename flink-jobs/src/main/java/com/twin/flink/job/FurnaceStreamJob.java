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
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
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

public class FurnaceStreamJob {

    static final String KAFKA = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "twin-kafka:9093");
    static final String MONGO = System.getenv().getOrDefault("MONGO_URI", "mongodb://twin:twin_secret@twin-mongodb:27017/twin_db?authSource=admin");
    static final String RHOST = System.getenv().getOrDefault("REDIS_HOST", "twin-redis");
    static final int    RPORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // Checkpoint 關閉，避免目錄權限問題
        env.setParallelism(1);

        KafkaSource<FurnaceReading> source = KafkaSource.<FurnaceReading>builder()
            .setBootstrapServers(KAFKA)
            .setTopics("furnace-data")
            .setGroupId("flink-furnace-job")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new FurnaceDeserializer())
            .build();

        DataStream<FurnaceReading> stream = env
            .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka-furnace-data")
            .filter(new FilterFunction<FurnaceReading>() {
                @Override
                public boolean filter(FurnaceReading r) {
                    return r != null && r.getFurnaceId() != null;
                }
            });

        stream.sinkTo(new TimescaleDbSink()).name("sink-timescaledb");
        stream.sinkTo(new RedisSink(RHOST, RPORT)).name("sink-redis");
        stream.sinkTo(new MongoDbSink(MONGO)).name("sink-mongodb");

        KeyedStream<FurnaceReading, String> keyed = stream.keyBy(
            new org.apache.flink.api.java.functions.KeySelector<FurnaceReading, String>() {
                @Override
                public String getKey(FurnaceReading r) throws Exception {
                    return r.getFurnaceId();
                }
            });

        CEP.pattern(keyed, DiameterDriftPattern.build())
            .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                @Override
                public void processMatch(Map<String, List<FurnaceReading>> m,
                        Context c, Collector<AlarmEvent> out) throws Exception {
                    FurnaceReading r = m.get("drift").get(0);
                    out.collect(new AlarmEvent("DiameterDrift", r.getFurnaceId(), r.getIngotNo(),
                        "WARN", "Diameter偏離target", Instant.now().toString(),
                        r.getDiameter(), r.getHeaterTemp(), r.getEvent(), r.getOperationMode()));
                }
            }).print("ALARM-DIAM");

        CEP.pattern(keyed, HeaterOverheatPattern.build())
            .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                @Override
                public void processMatch(Map<String, List<FurnaceReading>> m,
                        Context c, Collector<AlarmEvent> out) throws Exception {
                    FurnaceReading r = m.get("overheat").get(0);
                    out.collect(new AlarmEvent("HeaterOverheat", r.getFurnaceId(), r.getIngotNo(),
                        "CRITICAL", "Heater超過1450°C", Instant.now().toString(),
                        r.getDiameter(), r.getHeaterTemp(), r.getEvent(), r.getOperationMode()));
                }
            }).print("ALARM-HEAT");

        CEP.pattern(keyed, NgDisconnectPattern.build())
            .process(new PatternProcessFunction<FurnaceReading, AlarmEvent>() {
                @Override
                public void processMatch(Map<String, List<FurnaceReading>> m,
                        Context c, Collector<AlarmEvent> out) throws Exception {
                    FurnaceReading r = m.get("ng").get(0);
                    out.collect(new AlarmEvent("NgDisconnect", r.getFurnaceId(), r.getIngotNo(),
                        "CRITICAL", "Event=6偵測", Instant.now().toString(),
                        r.getDiameter(), r.getHeaterTemp(), r.getEvent(), r.getOperationMode()));
                }
            }).print("ALARM-NG");

        env.execute("FurnaceStreamJob");
    }
}
