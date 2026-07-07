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

        @Override
        public void write(FurnaceReading r, Context ctx) throws IOException {
            col.insertOne(new Document()
                .append("log_time",       r.getLogTime())
                .append("furnace_id",     r.getFurnaceId())
                .append("ingot_no",       r.getIngotNo())
                .append("operation_mode", r.getOperationMode())
                .append("event",          r.getEvent())
                .append("diameter",       r.getDiameter())
                .append("heater_temp",    r.getHeaterTemp())
                .append("gr_mean",        r.getGrMean())
                .append("body_length",    r.getBodyLength())
                .append("received_at",    Instant.now().toString()));
        }

        @Override public void flush(boolean endOfInput) throws IOException {}
        @Override public void close() throws Exception { client.close(); }
    }
}
