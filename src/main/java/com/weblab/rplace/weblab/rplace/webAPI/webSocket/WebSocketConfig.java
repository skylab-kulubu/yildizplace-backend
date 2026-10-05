package com.weblab.rplace.weblab.rplace.webAPI.webSocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
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

    /**
     * Clients only listen. PixelController broadcasts every accepted pixel and fill through the
     * broker channel, and nothing here handles a client's message: pixels go over HTTP, where
     * login, cooldown and bans apply. Without this rule the simple broker relays a client's SEND
     * (or a MESSAGE frame a client sends) to every subscriber of /topic/**, so anyone who can open
     * the socket could paint on every live canvas. CONNECT, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT and
     * heartbeats pass; a refused frame gets an ERROR and the connection closes.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (SimpMessageType.MESSAGE.equals(SimpMessageHeaderAccessor.getMessageType(message.getHeaders()))) {
                    throw new MessageDeliveryException(message, "Clients cannot send messages on this socket");
                }
                return message;
            }
        });
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        return new ThreadPoolTaskScheduler();
    }
}
