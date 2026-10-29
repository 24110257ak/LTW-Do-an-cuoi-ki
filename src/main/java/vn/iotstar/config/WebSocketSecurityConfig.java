package vn.iotstar.config;

import org.springframework.context.annotation.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages
                .simpTypeMatchers(SimpMessageType.DISCONNECT, SimpMessageType.UNSUBSCRIBE, SimpMessageType.HEARTBEAT).permitAll()
                .nullDestMatcher().authenticated()
                .simpDestMatchers("/app/chat.sendMessage").hasAnyRole("CUSTOMER", "STAFF", "ADMIN")
                .simpSubscribeDestMatchers("/user/queue/chat", "/user/queue/chat-errors").hasAnyRole("CUSTOMER", "STAFF", "ADMIN")
                .simpSubscribeDestMatchers("/topic/admin/orders").hasRole("ADMIN")
                // No client may subscribe to another user's resolved queue, a chat topic, or broadcast its own messages.
                .anyMessage().denyAll().build();
    }
}
