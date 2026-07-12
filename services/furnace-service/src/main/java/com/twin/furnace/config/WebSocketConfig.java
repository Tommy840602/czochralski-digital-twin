package com.twin.furnace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 允許連進 STOMP endpoint 的來源。
     *
     * ⚠ 這裡原本寫死 localhost，正式站的瀏覽器送 Origin: https://<網域> 一律被擋，
     *    SockJS 的握手直接 403 → 前端永遠顯示 OFFLINE、0 爐。
     *
     *    注意 /ws 是由前端 nginx「直接」代理到 furnace-service，不經過 api-gateway，
     *    所以 gateway 那邊的 CORS 白名單管不到這裡，得各自設定。
     *
     *    用 allowedOriginPatterns（不是 setAllowedOrigins），才能支援萬用字元，
     *    且與 SockJS 的 credentials 相容。
     *
     *    逗號分隔，Spring 會自動綁成 String[]。
     */
    @Value("${app.ws.allowed-origins:http://localhost:5173,http://localhost:3001,http://localhost}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
