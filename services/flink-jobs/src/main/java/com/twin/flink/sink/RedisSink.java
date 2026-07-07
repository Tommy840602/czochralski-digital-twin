package com.twin.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twin.flink.model.FurnaceReading;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RedisSink — 即時爐況寫入（彈性多爐版）
 * Key: furnace:{furnaceId}
 * TTL: 300 秒
 * 以反射(Jackson)一次寫入 FurnaceReading 所有欄位 → 新增感測器零改動。
 */
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

        private static final ObjectMapper M = new ObjectMapper();
        private final JedisPool pool;

        RedisWriter(String host, int port) {
            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(8);
            cfg.setMaxIdle(4);
            pool = new JedisPool(cfg, host, port, 2000);
        }

        @Override
        public void write(FurnaceReading r, Context ctx) throws IOException {
            if (r.getFurnaceId() == null) return;
            try (Jedis jedis = pool.getResource()) {
                String key = "furnace:" + r.getFurnaceId();

                // 反射：FurnaceReading 全欄位 → Map（key = @JsonProperty 名）
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = M.convertValue(r, Map.class);

                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    Object v = e.getValue();
                    fields.put(e.getKey(), v == null ? "" : String.valueOf(v));
                }
                // 前端用 updatedAt 判斷離線；以 receivedAt 當別名
                fields.put("updatedAt", r.getReceivedAt() != null ? r.getReceivedAt() : "");

                jedis.hset(key, fields);
                jedis.expire(key, 300);
            } catch (Exception e) {
                throw new IOException("Redis 寫入失敗 furnace=" + r.getFurnaceId()
                        + ": " + e.getMessage(), e);
            }
        }

        @Override public void flush(boolean endOfInput) {}
        @Override public void close() { pool.close(); }
    }
}
