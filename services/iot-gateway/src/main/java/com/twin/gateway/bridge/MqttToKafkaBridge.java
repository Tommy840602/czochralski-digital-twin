package com.twin.gateway.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twin.gateway.model.FurnaceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class MqttToKafkaBridge {

    private static final Logger log = LoggerFactory.getLogger(MqttToKafkaBridge.class);
    private static final String TOPIC_FURNACE_DATA  = "furnace-data";
    private static final String TOPIC_SENSOR_EVENTS = "sensor-events";
    private static final String TOPIC_ALARM_EVENTS  = "alarm-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqttToKafkaBridge(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<String> message) {
        String mqttTopic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload   = message.getPayload();
        log.debug("MQTT 收到 [{}]: {}B", mqttTopic, payload.length());

        try {
            if (mqttTopic == null) return;

            if (mqttTopic.equals("furnace/alarm")) {
                routeToKafka(TOPIC_ALARM_EVENTS, "alarm", payload);

            } else if (mqttTopic.startsWith("furnace/")) {
                String furnaceId = mqttTopic.replace("furnace/", "");
                FurnaceMessage msg = objectMapper.readValue(payload, FurnaceMessage.class);
                String key = msg.getIngotNo() != null ? msg.getIngotNo() : furnaceId;

                routeToKafka(TOPIC_FURNACE_DATA,  key, payload);
                routeToKafka(TOPIC_SENSOR_EVENTS, key, payload);

                log.info("→ Kafka furnaceId={} event={} Ø={} T={}°C",
                    furnaceId, msg.getEvent(), msg.getDiameter(), msg.getHeaterTemp());
            }
        } catch (Exception e) {
            log.error("處理失敗 topic={}: {}", mqttTopic, e.getMessage());
        }
    }

    private void routeToKafka(String kafkaTopic, String key, String payload) {
        kafkaTemplate.send(kafkaTopic, key, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) log.error("Kafka 失敗 {}: {}", kafkaTopic, ex.getMessage());
                else log.debug("Kafka ACK {} p={} o={}",
                    kafkaTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            });
    }
}
