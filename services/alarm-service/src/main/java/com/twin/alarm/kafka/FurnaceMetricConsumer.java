package com.twin.alarm.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * JSON 訊息格式（來自 iot-gateway MqttToKafkaBridge，furnace-data topic）：
     * {
     *   "furnaceId": "D1",
     *   "ingotNo": "D126654E",
     *   "heaterTemp": 1259.505,
     *   "diameter": 105.5,
     *   "grMean": 1.046128,
     *   "heaterPowerSv": 53.11406,
     *   "seedLift": 1.082042,
     *   "bodyLength": 105.0925,
     *   ...
     * }
     */
    @KafkaListener(topics = "furnace-data", groupId = "alarm-service-spc")
    public void consume(String jsonPayload) {
        try {
            JsonNode node = objectMapper.readTree(jsonPayload);

            String furnaceId = textOrEmpty(node, "furnaceId");
            String ingotId = textOrEmpty(node, "ingotNo");
            String operationMode = textOrEmpty(node, "operationMode");

            if (furnaceId.isEmpty()) return;

            Map<String, Double> values = new HashMap<>();
            putIfNumeric(values, node, "heaterTemp");
            putIfNumeric(values, node, "diameter");
            putIfNumeric(values, node, "grMean");
            putIfNumeric(values, node, "heaterPowerSv");
            putIfNumeric(values, node, "seedLift");
            putIfNumeric(values, node, "bodyLength");

            if (values.isEmpty()) return;

            spcCheckService.checkAllParams(furnaceId, ingotId, operationMode, Instant.now(), values);
        } catch (Exception e) {
            log.debug("Failed to parse furnace-data record: {}", e.getMessage());
        }
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? "" : v.asText();
    }

    private void putIfNumeric(Map<String, Double> map, JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v != null && v.isNumber()) {
            map.put(field, v.asDouble());
        }
    }
}