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

/**
 * RedisSink — 即時爐況寫入（彈性多爐版）
 * Key: furnace:{furnaceId}   → 動態，支援任意爐子
 * TTL: 60 秒（超過則視為離線）
 * 儲存核心感測器欄位供 WebSocket 即時推送使用
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
                String key = "furnace:" + r.getFurnaceId();   // 動態 key

                Map<String, String> fields = new HashMap<>();
                // 識別
                fields.put("furnaceId",       safe(r.getFurnaceId()));
                fields.put("ingotNo",         safe(r.getIngotNo()));
                fields.put("operationMode",   safe(r.getOperationMode()));
                fields.put("logTime",         safe(r.getLogTime()));
                fields.put("updatedAt",       safe(r.getReceivedAt()));

                // 核心感測器（前端即時顯示用）
                fields.put("diameter",        safeD(r.getDiameter()));
                fields.put("diameterTarget",  safeD(r.getDiameterTarget()));
                fields.put("heaterTemp",      safeD(r.getHeaterTemp()));
                fields.put("heaterPowerSv",   safeD(r.getHeaterPowerSv()));
                fields.put("grMean",          safeD(r.getGrMean()));
                fields.put("bodyLength",      safeD(r.getBodyLength()));
                fields.put("seedLift",        safeD(r.getSeedLift()));
                fields.put("residualWeight",  safeD(r.getResidualWeight()));
                fields.put("magnetPv",        safeD(r.getMagnetPv()));
                fields.put("argonFlowRate",   safeD(r.getArgonFlowRate()));
                fields.put("lowerChamberPress", safeD(r.getLowerChamberPress()));
                fields.put("crMean",          safeD(r.getCrMean()));
                fields.put("temp2",           safeD(r.getTemp2()));
                fields.put("temp4",           safeD(r.getTemp4()));
                fields.put("temp5",           safeD(r.getTemp5()));

                jedis.hset(key, fields);
                jedis.expire(key, 300);  // 300s TTL
            } catch (Exception e) {
                throw new IOException("Redis 寫入失敗 furnace=" + r.getFurnaceId()
                        + ": " + e.getMessage(), e);
            }
        }

        private String safe(String v)  { return v != null ? v : ""; }
        private String safeD(Double v) { return v != null ? String.valueOf(v) : ""; }

        @Override public void flush(boolean endOfInput) {}
        @Override public void close() { pool.close(); }
    }
}
