package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.*;
import vn.iotstar.enums.ChatSessionStatus;
import vn.iotstar.exception.*;
import vn.iotstar.repository.*;
import vn.iotstar.service.ChatService;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {
    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final BranchRepository branches;
    private final ApplicationEventPublisher events;

    @Override
    public User requireUser(String username) {
        if (username == null) throw new AccessDeniedException("Vui lòng đăng nhập để chat");
        User user = users.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không tồn tại"));
        if (!user.isEnabled() || !user.isAccountNonLocked()) throw new AccessDeniedException("Tài khoản không được phép chat");
        if (!Set.of("CUSTOMER", "STAFF", "ADMIN").contains(user.getRole().getRoleName())) {
            throw new AccessDeniedException("Vai trò không được phép truy cập chat");
        }
        return user;
    }

    private boolean customer(User user) { return "CUSTOMER".equals(user.getRole().getRoleName()); }
    private boolean admin(User user) { return "ADMIN".equals(user.getRole().getRoleName()); }

    private void staffBranch(User user, Integer branchId) {
        if (admin(user)) return;
        if (!"STAFF".equals(user.getRole().getRoleName()) || user.getBranch() == null
                || !Boolean.TRUE.equals(user.getBranch().getIsActive())
                || !Objects.equals(user.getBranch().getBranchId(), branchId)) {
            throw new AccessDeniedException("Bạn không có quyền tư vấn tại chi nhánh này");
        }
    }
    private ChatSession find(Long id, boolean lock) {
        return (lock ? sessions.findForUpdate(id) : sessions.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Phiên chat", "id", id));
    }
    private void access(User user, ChatSession session) {
        if (customer(user)) {
            if (!user.getUserId().equals(session.getCustomer().getUserId()))
                throw new AccessDeniedException("Bạn không có quyền xem cuộc trò chuyện này");
        } else staffBranch(user, session.getBranch().getBranchId());
    }
    private void writable(ChatSession session) {
        if (session.getStatus() != ChatSessionStatus.OPEN) throw new BadRequestException("Phiên đã kết thúc. Vui lòng tạo phiên tư vấn mới.");
        if (!Boolean.TRUE.equals(session.getBranch().getIsActive())) throw new BadRequestException("Chi nhánh đã ngừng hoạt động");
    }
    private ChatDto.SessionResponse response(ChatSession session, User user) {
        long unread = customer(user)
                ? messages.countUnread(session.getSessionId(), session.getCustomerLastReadId(), user.getUserId())
                : messages.countStaffUnread(session.getSessionId(), session.getStaffLastReadId());
        User staff = session.getStaff();
        return new ChatDto.SessionResponse(session.getSessionId(), session.getCustomer().getUserId(),
                session.getCustomer().getFullName(), session.getBranch().getBranchId(), session.getBranch().getBranchName(),
                staff == null ? null : staff.getUserId(), staff == null ? null : staff.getFullName(),
                session.getStatus().name(), session.getLastMessageAt(), unread);
    }
    private ChatDto.MessageResponse response(ChatMessage message) {
        return ChatDto.MessageResponse.builder().messageId(message.getMessageId()).sessionId(message.getSession().getSessionId())
                .senderId(message.getSender().getUserId()).senderName(message.getSender().getFullName())
                .senderRole(message.getSender().getRole().getRoleName()).content(message.getContent())
                .sentAt(message.getSentAt()).clientMessageId(message.getClientMessageId()).build();
    }

    @Override @Transactional
    public ChatDto.SessionResponse openSession(String username, Integer branchId) {
        User user = requireUser(username);
        if (!customer(user)) throw new AccessDeniedException("Chỉ khách hàng tạo phiên tư vấn");
        // Serialize concurrent open requests for the same customer (including across application instances).
        users.findChatCustomerForUpdate(user.getUserId()).orElseThrow();
        Branch branch = branches.findById(branchId)
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .orElseThrow(() -> new BadRequestException("Chi nhánh không tồn tại hoặc đã ngừng hoạt động"));
        ChatSession session = sessions.findFirstByCustomerUserIdAndBranchBranchIdAndStatusOrderByCreatedAtDesc(
                user.getUserId(), branchId, ChatSessionStatus.OPEN).orElseGet(() -> {
                    ChatSession created = sessions.save(ChatSession.builder().customer(user).branch(branch).build());
                    events.publishEvent(new ChatDto.Event("SESSION", created.getSessionId(), null));
                    return created;
                });
        return response(session, user);
    }
    @Override
    public ChatDto.SessionPage listSessions(String username, Integer branchId, int page) {
        User user = requireUser(username);
        if (page < 0 || page > 10000) throw new BadRequestException("Trang không hợp lệ");
        var paging = PageRequest.of(page, 30);
        var result = customer(user) ? sessions.findByCustomerUserIdOrderByLastMessageAtDescSessionIdDesc(user.getUserId(), paging)
                : listForOperator(user, branchId, paging);
        return new ChatDto.SessionPage(result.getContent().stream().map(s -> response(s, user)).toList(), result.hasNext(), page);
    }
    private org.springframework.data.domain.Page<ChatSession> listForOperator(User user, Integer branchId, PageRequest paging) {
        return sessions.findByBranchBranchIdOrderByLastMessageAtDescSessionIdDesc(operatorBranch(user, branchId), paging);
    }
    private Integer operatorBranch(User user, Integer branchId) {
        if (!admin(user)) {
            if (branchId == null && user.getBranch() != null) branchId = user.getBranch().getBranchId();
            staffBranch(user, branchId);
        }
        if (branchId == null) throw new BadRequestException("Vui lòng chọn chi nhánh");
        return branchId;
    }
    @Override
    public ChatDto.ConversationPage listConversations(String username, Integer branchId, int page) {
        User user = requireUser(username);
        if (page < 0 || page > 10000) throw new BadRequestException("Trang không hợp lệ");
        Integer scope = operatorBranch(user, branchId);
        var result = sessions.findConversations(scope, ChatSessionStatus.OPEN, PageRequest.of(page, 30));
        var conversations = result.getContent().stream()
                .map(row -> conversationResponse(find(row.getSessionId(), false), row.getLastMessageAt())).toList();
        return new ChatDto.ConversationPage(conversations, result.hasNext(), page);
    }
    @Override
    public ChatDto.SessionResponse getConversation(String username, Long sessionId) {
        User user = requireUser(username);
        ChatSession anchor = find(sessionId, false);
        staffBranch(user, anchor.getBranch().getBranchId());
        Long customerId = anchor.getCustomer().getUserId();
        Integer branchId = anchor.getBranch().getBranchId();
        ChatSession latest = sessions.findFirstByCustomerUserIdAndBranchBranchIdAndStatusOrderByCreatedAtDesc(
                customerId, branchId, ChatSessionStatus.OPEN).orElseGet(() ->
                sessions.findFirstByCustomerUserIdAndBranchBranchIdOrderBySessionIdDesc(customerId, branchId).orElseThrow());
        return conversationResponse(latest, sessions.conversationActivity(customerId, branchId));
    }
    private ChatDto.SessionResponse conversationResponse(ChatSession session, LocalDateTime activity) {
        User staff = session.getStaff();
        Long customerId = session.getCustomer().getUserId();
        Integer branchId = session.getBranch().getBranchId();
        return new ChatDto.SessionResponse(session.getSessionId(), customerId, session.getCustomer().getFullName(),
                branchId, session.getBranch().getBranchName(), staff == null ? null : staff.getUserId(),
                staff == null ? null : staff.getFullName(), session.getStatus().name(), activity,
                messages.countConversationStaffUnread(customerId, branchId));
    }
    @Override
    public ChatDto.SessionResponse getSession(String username, Long sessionId) {
        User user = requireUser(username); ChatSession session = find(sessionId, false);
        access(user, session); return response(session, user);
    }
    @Override
    public ChatDto.MessagePage getMessages(String username, Long sessionId, Long beforeId, Long afterId) {
        User user = requireUser(username); ChatSession session = find(sessionId, false); access(user, session);
        validateMessageCursors(beforeId, afterId);
        List<ChatMessage> list;
        if (afterId != null) {
            list = messages.since(sessionId, afterId, PageRequest.of(0, 51));
        } else if (beforeId != null) {
            list = messages.findBySessionSessionIdAndMessageIdLessThanOrderByMessageIdDesc(
                    sessionId, beforeId, PageRequest.of(0, 51));
        } else {
            list = messages.findBySessionSessionIdOrderByMessageIdDesc(
                    sessionId, PageRequest.of(0, 51));
        }
        return messagePage(list, afterId);
    }
    @Override
    public ChatDto.MessagePage getConversationMessages(String username, Long sessionId, Long beforeId, Long afterId) {
        User user = requireUser(username);
        ChatSession session = find(sessionId, false);
        access(user, session);
        validateMessageCursors(beforeId, afterId);
        Long customerId = session.getCustomer().getUserId();
        Integer branchId = session.getBranch().getBranchId();
        List<ChatMessage> list;
        if (afterId != null) {
            list = messages.conversationSince(customerId, branchId, afterId, PageRequest.of(0, 51));
        } else if (beforeId != null) {
            list = messages.findBySessionCustomerUserIdAndSessionBranchBranchIdAndMessageIdLessThanOrderByMessageIdDesc(
                    customerId, branchId, beforeId, PageRequest.of(0, 51));
        } else {
            list = messages.findBySessionCustomerUserIdAndSessionBranchBranchIdOrderByMessageIdDesc(
                    customerId, branchId, PageRequest.of(0, 51));
        }
        return messagePage(list, afterId);
    }
    private void validateMessageCursors(Long beforeId, Long afterId) {
        if (afterId != null && (afterId < 0 || beforeId != null)) throw new BadRequestException("Mốc tải tin không hợp lệ");
        if (beforeId != null && beforeId <= 0) throw new BadRequestException("Mốc tin nhắn không hợp lệ");
    }
    private ChatDto.MessagePage messagePage(List<ChatMessage> selected, Long afterId) {
        var result = new ArrayList<>(selected);
        boolean more = result.size() > 50;
        if (more) result.remove(result.size() - 1);
        if (afterId == null) Collections.reverse(result);
        return new ChatDto.MessagePage(result.stream().map(this::response).toList(), more,
                result.isEmpty() ? null : result.get(0).getMessageId(), result.isEmpty() ? afterId : result.get(result.size() - 1).getMessageId());
    }
    @Override @Transactional
    public ChatDto.MessageResponse sendMessage(String username, ChatDto.MessageRequest request) {
        User user = requireUser(username);
        if (request == null || request.getSessionId() == null || request.getContent() == null
                || request.getContent().isBlank() || request.getContent().length() > 2000
                || request.getClientMessageId() == null || !request.getClientMessageId().matches("[A-Za-z0-9_-]{1,64}"))
            throw new BadRequestException("Tin nhắn cần có nội dung 1–2000 ký tự và mã gửi hợp lệ");
        ChatSession session = find(request.getSessionId(), true); access(user, session);
        // Retry after a lost response must return the committed message even if the session has since closed.
        var previous = messages.findBySessionSessionIdAndClientMessageId(session.getSessionId(), request.getClientMessageId());
        if (previous.isPresent()) {
            ChatMessage old = previous.get();
            if (!old.getSender().getUserId().equals(user.getUserId()) || !old.getContent().equals(request.getContent().strip()))
                throw new BadRequestException("Mã gửi đã được sử dụng cho tin nhắn khác");
            return response(old);
        }
        writable(session);
        // One operator claims the conversation. A stale assignment after a branch transfer can be claimed again.
        if (!customer(user)) {
            claim(user, session);
        }
        ChatMessage saved = messages.saveAndFlush(ChatMessage.builder().session(session).sender(user)
                .content(request.getContent().strip()).clientMessageId(request.getClientMessageId()).build());
        session.setLastMessageAt(saved.getSentAt());
        if (!customer(user)) session.setStaffLastReadId(saved.getMessageId());
        sessions.save(session);
        ChatDto.MessageResponse dto = response(saved);
        events.publishEvent(new ChatDto.Event("MESSAGE", session.getSessionId(), dto));
        return dto;
    }
    private void claim(User user, ChatSession session) {
        User assigned = session.getStaff();
        boolean validAssignment = assigned != null && assigned.isEnabled()
                && ("ADMIN".equals(assigned.getRole().getRoleName())
                    || ("STAFF".equals(assigned.getRole().getRoleName()) && assigned.getBranch() != null
                        && Objects.equals(assigned.getBranch().getBranchId(), session.getBranch().getBranchId())));
        if (!admin(user) && validAssignment && !assigned.getUserId().equals(user.getUserId()))
            throw new BadRequestException("Phiên này đã được nhân viên khác tiếp nhận");
        session.setStaff(user);
    }
    @Override @Transactional
    public ChatDto.SessionResponse claimSession(String username, Long sessionId) {
        User user = requireUser(username); ChatSession session = find(sessionId, true);
        access(user, session); staffBranch(user, session.getBranch().getBranchId()); writable(session); claim(user, session);
        sessions.save(session); events.publishEvent(new ChatDto.Event("SESSION", sessionId, null));
        return response(session, user);
    }
    @Override @Transactional
    public ChatDto.SessionResponse closeSession(String username, Long sessionId) {
        User user = requireUser(username); ChatSession session = find(sessionId, true); access(user, session);
        if (!customer(user)) { staffBranch(user, session.getBranch().getBranchId()); claim(user, session); }
        if (session.getStatus() != ChatSessionStatus.CLOSED) {
            session.setStatus(ChatSessionStatus.CLOSED); session.setClosedAt(LocalDateTime.now());
            sessions.save(session); events.publishEvent(new ChatDto.Event("SESSION", sessionId, null));
        }
        return response(session, user);
    }
    @Override @Transactional
    public void markRead(String username, Long sessionId, Long messageId) {
        User user = requireUser(username); ChatSession session = find(sessionId, true); access(user, session);
        if (messageId == null || messageId < 0 || (messageId > 0 && !messages.existsBySessionSessionIdAndMessageId(sessionId, messageId)))
            throw new BadRequestException("Tin nhắn không thuộc phiên này");
        if (customer(user)) session.setCustomerLastReadId(Math.max(session.getCustomerLastReadId(), messageId));
        else session.setStaffLastReadId(Math.max(session.getStaffLastReadId(), messageId));
        sessions.save(session);
    }
}
