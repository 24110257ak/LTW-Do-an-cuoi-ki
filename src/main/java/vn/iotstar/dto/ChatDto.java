package vn.iotstar.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class ChatDto {
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class OpenRequest { @NotNull private Integer branchId; }

    // Identity and branch are resolved on the server, never accepted from the client.
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageRequest {
        @NotNull private Long sessionId;
        @NotBlank @Size(max = 2000) private String content;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") private String clientMessageId;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageResponse {
        private Long messageId;
        private Long sessionId;
        private Long senderId;
        private String senderName;
        private String senderRole;
        private String content;
        private LocalDateTime sentAt;
        private String clientMessageId;
    }
    public record SessionResponse(Long sessionId, Long customerId, String customerName,
            Integer branchId, String branchName, Long staffId, String staffName,
            String status, LocalDateTime lastMessageAt, long unreadCount) {}
    public record MessagePage(List<MessageResponse> messages, boolean hasMore, Long nextBeforeId, Long nextAfterId) {}
    public record SessionPage(List<SessionResponse> sessions, boolean hasMore, int page) {}
    public record ConversationPage(List<SessionResponse> conversations, boolean hasMore, int page) {}
    public record BranchOption(Integer branchId, String branchName) {}
    public record ReadRequest(@NotNull @PositiveOrZero Long messageId) {}
    public record Event(String type, Long sessionId, MessageResponse message) {}
}
