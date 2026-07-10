package com.twin.flink.sink;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class MongoDbSink implements Sink<FurnaceReading> {
    private final String uri;

    public MongoDbSink(String uri) { this.uri = uri; }

    @Override
    public SinkWriter<FurnaceReading> createWriter(WriterInitContext ctx) throws IOException {
        return new MongoWriter(uri);
    }

    static class MongoWriter implements SinkWriter<FurnaceReading> {
        private final MongoClient client;
        private final MongoCollection<Document> col;

        MongoWriter(String uri) {
            client = MongoClients.create(uri);
            col = client.getDatabase("twin_db").getCollection("sensor_data");
        }

        // log_time 格式："2026-06-09 05:49:31"（無時區，視為 UTC）
        private static final DateTimeFormatter LOG_TIME_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        /** 把字串 log_time 轉成 BSON date；解析失敗則退回接收時間，避免丟資料。 */
        private static Date parseLogTime(String s) {
            if (s != null && !s.isEmpty()) {
                try {
                    return Date.from(LocalDateTime.parse(s, LOG_TIME_FMT)
                            .toInstant(ZoneOffset.UTC));
                } catch (Exception ignore) {
                    // fall through
                }
            }
            return Date.from(Instant.now());
        }

        @Override
        public void write(FurnaceReading r, Context ctx) throws IOException {
            col.insertOne(new Document()
                .append("log_time",       parseLogTime(r.getLogTime()))      // date，非字串
                .append("furnace_id",     r.getFurnaceId())
                .append("ingot_no",       r.getIngotNo())
                .append("operation_mode", r.getOperationMode())
                .append("event",          r.getEvent() != null ? r.getEvent() : 0)  // int，非 null
                .append("diameter",       r.getDiameter())
                .append("heater_temp",    r.getHeaterTemp())
                .append("gr_mean",        r.getGrMean())
                .append("body_length",    r.getBodyLength())
                .append("received_at",    Date.from(Instant.now())));         // date，非字串
        }

        @Override public void flush(boolean endOfInput) throws IOException {}
        @Override public void close() throws Exception { client.close(); }
    }
}
