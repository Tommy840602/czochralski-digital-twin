package com.twin.flink.sink;

import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RedisSink implements Sink<FurnaceReading> {
    private final String host;
    private final int    port;

    public RedisSink(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public SinkWriter<FurnaceReading> createWriter(WriterInitContext ctx) throws IOException {
        return new RedisWriter(host, port);
    }

    static class RedisWriter implements SinkWriter<FurnaceReading> {
        private final JedisPool pool;

        RedisWriter(String host, int port) {
            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(4);
            this.pool = new JedisPool(cfg, host, port, 2000);
        }

        @Override
        public void write(FurnaceReading r, Context ctx) throws IOException {
            try (Jedis jedis = pool.getResource()) {
                String key = "furnace:" + r.getFurnaceId();
                Map<String, String> fields = new HashMap<>();
                fields.put("furnaceId",     r.getFurnaceId() != null ? r.getFurnaceId() : "");
                fields.put("ingotNo",       r.getIngotNo() != null ? r.getIngotNo() : "");
                fields.put("event",         String.valueOf(r.getEvent() != null ? r.getEvent() : 1));
                fields.put("operationMode", r.getOperationMode() != null ? r.getOperationMode() : "");
                fields.put("diameter",      r.getDiameter() != null ? String.valueOf(r.getDiameter()) : "");
                fields.put("heaterTemp",    r.getHeaterTemp() != null ? String.valueOf(r.getHeaterTemp()) : "");
                fields.put("grMean",        r.getGrMean() != null ? String.valueOf(r.getGrMean()) : "");
                fields.put("bodyLength",    r.getBodyLength() != null ? String.valueOf(r.getBodyLength()) : "");
                fields.put("updatedAt",     r.getReceivedAt() != null ? r.getReceivedAt() : "");
                jedis.hset(key, fields);
                jedis.expire(key, 60);
            }
        }

        @Override public void flush(boolean endOfInput) throws IOException {}
        @Override public void close() throws Exception { pool.close(); }
    }
}
