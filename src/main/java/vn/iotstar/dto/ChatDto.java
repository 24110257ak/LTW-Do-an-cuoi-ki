package vn.iotstar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class ChatDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRequest {
        private Long sessionId;
        private Long senderId;
        private String senderName;
        private String content;
        private Integer branchId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private Long messageId;
        private Long sessionId;
        private Long senderId;
        private String senderName;
        private String senderRole;
        private String content;
        private LocalDateTime sentAt;
    }
}
