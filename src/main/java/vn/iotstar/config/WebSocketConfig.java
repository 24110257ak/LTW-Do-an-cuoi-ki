package vn.iotstar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint kết nối WebSocket từ client với SockJS fallback
        registry.addEndpoint("/ws")
                .addInterceptors(new CsrfTokenHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix cho các kênh broadcast và queue nhận tin nhắn từ Server
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix cho các tin nhắn từ Client gửi lên Server
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix cho tin nhắn gửi riêng cho từng User
        registry.setUserDestinationPrefix("/user");
    }
}

