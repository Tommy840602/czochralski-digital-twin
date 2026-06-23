package com.twin.furnace.websocket;

import com.twin.furnace.dto.FurnaceLatestDto;
import com.twin.furnace.service.FurnaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class FurnaceWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(FurnaceWebSocketHandler.class);
    private final SimpMessagingTemplate messaging;
    private final FurnaceService furnaceService;

    public FurnaceWebSocketHandler(SimpMessagingTemplate m, FurnaceService s) {
        this.messaging = m; this.furnaceService = s;
    }

    @Scheduled(fixedDelay = 2000)
    public void pushAllFurnaces() {
        try {
            List<FurnaceLatestDto> all = furnaceService.getAllLatest();
            for (FurnaceLatestDto dto : all) {
                try { messaging.convertAndSend("/topic/furnace/" + dto.getFurnaceId(), dto); }
                catch (Exception e) { log.warn("推送失敗 {}: {}", dto.getFurnaceId(), e.getMessage()); }
            }
            try { messaging.convertAndSend("/topic/furnaces/all", all); }
            catch (Exception e) { log.warn("推送 all 失敗: {}", e.getMessage()); }
        } catch (Exception e) {
            log.error("pushAllFurnaces tick failed, will retry next interval", e);
        }
    }
}
