package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.ChatMessage;
import vn.iotstar.service.ChatService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatDto.MessageRequest request) {
        log.info("Nhận tin nhắn WebSocket cho session: {}", request.getSessionId());

        ChatMessage saved = chatService.saveMessage(request);

        ChatDto.MessageResponse response = ChatDto.MessageResponse.builder()
                .messageId(saved.getMessageId())
                .sessionId(saved.getSession().getSessionId())
                .senderId(saved.getSender().getUserId())
                .senderName(saved.getSender().getFullName())
                .senderRole(saved.getSender().getRole().getRoleName())
                .content(saved.getContent())
                .sentAt(saved.getSentAt())
                .build();

        // Gửi tới phòng chat của phiên (Session)
        messagingTemplate.convertAndSend("/topic/chat/" + request.getSessionId(), response);

        // Gửi thông báo tin mới tới chi nhánh (nếu có branchId)
        if (request.getBranchId() != null) {
            messagingTemplate.convertAndSend("/topic/branch-chat/" + request.getBranchId(), response);
        }
    }
}
