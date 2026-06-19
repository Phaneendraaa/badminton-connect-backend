package com.app.badminton_backend.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP over SockJS.
 *
 * Connection endpoint: /ws (with SockJS fallback for environments that don't
 * support native WebSocket — e.g. older devices, some corporate proxies).
 *
 * Frontend should use:
 *   new SockJS('http://10.0.2.2:8082/ws')  ← Android emulator
 *   new SockJS('http://localhost:8082/ws')  ← iOS simulator / browser
 *
 * Topics:
 *   /topic/match/{matchId}  ← broadcast channel per match
 *
 * Application destinations (client → server):
 *   /app/chat.send  ← @MessageMapping handled in MatchChatController
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for /topic/** outbound channels
        registry.enableSimpleBroker("/topic");
        // Prefix for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
