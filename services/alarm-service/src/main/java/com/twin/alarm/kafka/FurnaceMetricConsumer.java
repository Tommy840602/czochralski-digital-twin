package com.twin.alarm.kafka;

import com.twin.alarm.service.SpcCheckService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FurnaceMetricConsumer {

    private static final Logger log = LoggerFactory.getLogger(FurnaceMetricConsumer.class);

    private final SpcCheckService spcCheckService;

    /**
     * CSV 欄位（從 iot-gateway processTelemetry 分析、對照 furnace_metrics 表）:
     * 0: datetime
     * 2: furnace_id
     * 3: ingot_id
     * 4: crown
     * 14: heater_temp
     * 17: diameter
     * 19: gr_mean
     * 20: heater_power_sv
     * 23: seed_lift
     * 24: body_length
     */
    @KafkaListener(topics = "furnace.raw", groupId = "alarm-service-spc")
    public void consume(String csvLine) {
        try {
            String[] fields = csvLine.split(",", -1);
            if (fields.length < 25) return;

            String furnaceId = trim(fields[2]);
            String ingotId = trim(fields[3]);

            Map<String, Double> values = new HashMap<>();
            putIfNumeric(values, "heaterTemp", fields[14]);
            putIfNumeric(values, "diameter", fields[17]);
            putIfNumeric(values, "grMean", fields[19]);
            putIfNumeric(values, "heaterPowerSv", fields[20]);
            putIfNumeric(values, "seedLift", fields[23]);
            putIfNumeric(values, "bodyLength", fields[24]);

            if (values.isEmpty() || furnaceId.isEmpty()) return;

            spcCheckService.checkAllParams(furnaceId, ingotId, Instant.now(), values);
        } catch (Exception e) {
            log.debug("Failed to parse furnace.raw record: {}", e.getMessage());
        }
    }

    private String trim(String s) { return s == null ? "" : s.trim(); }

    private void putIfNumeric(Map<String, Double> map, String key, String raw) {
        try {
            if (raw != null && !raw.isBlank()) {
                map.put(key, Double.parseDouble(raw.trim()));
            }
        } catch (NumberFormatException ignored) {}
    }
}