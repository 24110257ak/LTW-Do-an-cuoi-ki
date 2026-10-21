package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.ChatMessage;
import vn.iotstar.entity.ChatSession;
import vn.iotstar.entity.User;
import vn.iotstar.enums.ChatSessionStatus;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BranchRepository;
import vn.iotstar.repository.ChatMessageRepository;
import vn.iotstar.repository.ChatSessionRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.ChatService;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public ChatSession getOrCreateSession(Long customerId, Integer branchId) {
        return sessionRepository.findByCustomerUserIdAndBranchBranchIdAndStatus(customerId, branchId, ChatSessionStatus.OPEN)
                .orElseGet(() -> {
                    User customer = userRepository.findById(customerId)
                            .orElseThrow(() -> new ResourceNotFoundException("Khách hàng", "id", customerId));
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh", "id", branchId));
                    ChatSession newSession = ChatSession.builder()
                            .customer(customer)
                            .branch(branch)
                            .status(ChatSessionStatus.OPEN)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return sessionRepository.save(newSession);
                });
    }

    @Override
    @Transactional
    public ChatMessage saveMessage(ChatDto.MessageRequest request) {
        ChatSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Phiên chat", "id", request.getSessionId()));
        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new ResourceNotFoundException("Người gửi", "id", request.getSenderId()));

        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sender(sender)
                .content(request.getContent().trim())
                .sentAt(LocalDateTime.now())
                .build();

        return messageRepository.save(message);
    }

    @Override
    public List<ChatMessage> getMessages(Long sessionId) {
        return messageRepository.findBySessionSessionIdOrderBySentAtAsc(sessionId);
    }

    @Override
    public List<ChatSession> getOpenSessionsForBranch(Integer branchId) {
        return sessionRepository.findByBranchBranchIdAndStatusOrderByCreatedAtDesc(branchId, ChatSessionStatus.OPEN);
    }
}
