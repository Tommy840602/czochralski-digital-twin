package com.twin.furnace.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FurnaceWebSocketHandler {

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate   redis;

    private static final String[] FURNACES = {"C1", "C2"};

    @Scheduled(fixedDelay = 2000)
    public void pushFurnaceState() {
        for (String id : FURNACES) {
            try {
                Map<Object, Object> data = redis.opsForHash().entries("furnace:" + id);
                if (!data.isEmpty()) {
                    messaging.convertAndSend("/topic/furnace/" + id, data);
                    log.debug("推送 爐{} state", id);
                }
            } catch (Exception e) {
                log.warn("推送 爐{} 失敗: {}", id, e.getMessage());
            }
        }
    }
}
