package vn.iotstar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.User;
import vn.iotstar.service.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/chat/api")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chat;
    private final BranchService branches;
    public record Bootstrap(Long userId, String fullName, String role, Integer branchId,
                            List<ChatDto.BranchOption> branches, String csrfHeader, String csrfToken) {}

    @GetMapping("/bootstrap")
    public Bootstrap bootstrap(Principal principal, CsrfToken csrf) {
        User user = chat.requireUser(principal.getName());
        boolean staff = "STAFF".equals(user.getRole().getRoleName());
        Integer branchId = staff ? branches.getStaffBranchId() : null;
        var options = branches.getAllActiveBranches().stream()
                .filter(b -> !staff || b.getBranchId().equals(branchId))
                .map(b -> new ChatDto.BranchOption(b.getBranchId(), b.getBranchName())).toList();
        return new Bootstrap(user.getUserId(), user.getFullName(), user.getRole().getRoleName(), branchId,
                options, csrf.getHeaderName(), csrf.getToken());
    }
    @PostMapping("/sessions")
    public ChatDto.SessionResponse open(Principal principal, @Valid @RequestBody ChatDto.OpenRequest request) {
        return chat.openSession(principal.getName(), request.getBranchId());
    }
    @GetMapping("/sessions")
    public ChatDto.SessionPage sessions(Principal principal, @RequestParam(required = false) Integer branchId,
                                      @RequestParam(defaultValue = "0") int page) {
        return chat.listSessions(principal.getName(), branchId, page);
    }
    @GetMapping("/conversations")
    public ChatDto.ConversationPage conversations(Principal principal, @RequestParam(required = false) Integer branchId,
                                                 @RequestParam(defaultValue = "0") int page) {
        return chat.listConversations(principal.getName(), branchId, page);
    }
    @GetMapping("/sessions/{id}/conversation")
    public ChatDto.SessionResponse conversation(Principal principal, @PathVariable Long id) {
        return chat.getConversation(principal.getName(), id);
    }
    @GetMapping("/sessions/{id}")
    public ChatDto.SessionResponse session(Principal principal, @PathVariable Long id) {
        return chat.getSession(principal.getName(), id);
    }
    @GetMapping("/sessions/{id}/messages")
    public ChatDto.MessagePage messages(Principal principal, @PathVariable Long id, @RequestParam(required = false) Long beforeId, @RequestParam(required = false) Long afterId) {
        return chat.getMessages(principal.getName(), id, beforeId, afterId);
    }
    @GetMapping("/sessions/{id}/conversation/messages")
    public ChatDto.MessagePage conversationMessages(Principal principal, @PathVariable Long id,
            @RequestParam(required = false) Long beforeId, @RequestParam(required = false) Long afterId) {
        return chat.getConversationMessages(principal.getName(), id, beforeId, afterId);
    }
    @PostMapping("/messages")
    public ChatDto.MessageResponse send(Principal principal, @Valid @RequestBody ChatDto.MessageRequest request) {
        return chat.sendMessage(principal.getName(), request);
    }
    @PostMapping("/sessions/{id}/claim")
    public ChatDto.SessionResponse claim(Principal principal, @PathVariable Long id) { return chat.claimSession(principal.getName(), id); }
    @PostMapping("/sessions/{id}/close")
    public ChatDto.SessionResponse close(Principal principal, @PathVariable Long id) { return chat.closeSession(principal.getName(), id); }
    @PostMapping("/sessions/{id}/read")
    public void read(Principal principal, @PathVariable Long id, @Valid @RequestBody ChatDto.ReadRequest request) {
        chat.markRead(principal.getName(), id, request.messageId());
    }
}
