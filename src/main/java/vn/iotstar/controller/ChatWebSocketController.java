package vn.iotstar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.service.ChatService;
import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatService chat;
    @MessageMapping("/chat.sendMessage")
    public void send(Principal principal, @Valid @Payload ChatDto.MessageRequest request) {
        // Principal comes from the authenticated handshake, never from STOMP native headers.
        chat.sendMessage(principal == null ? null : principal.getName(), request);
    }
    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/chat-errors", broadcast = false)
    public Map<String, String> error(Exception exception) {
        return Map.of("message", "Không gửi được tin nhắn. Vui lòng tải lại phiên và kiểm tra quyền truy cập.");
    }
}
