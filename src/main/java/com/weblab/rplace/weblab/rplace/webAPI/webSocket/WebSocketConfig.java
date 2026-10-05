package com.weblab.rplace.weblab.rplace.webAPI.webSocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    /**
     * The same origins as /api/** (place.cors.allowed-origins, CorsConfig): in production the
     * https frontend alone, the dev profile adds the frontend's dev server. A browser on any
     * other origin gets 403 at the handshake and at SockJS's /info (cross-site WebSocket
     * hijacking). A client that sends no Origin (not a browser) is not affected.
     */
    public WebSocketConfig(@Value("${place.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/rplace")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{5000, 5000})
                .setTaskScheduler(heartBeatScheduler());
        registry.setApplicationDestinationPrefixes("/app"); 
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        return new ThreadPoolTaskScheduler();
    }
}
