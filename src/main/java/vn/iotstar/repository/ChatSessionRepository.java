package vn.iotstar.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.iotstar.entity.ChatSession;
import vn.iotstar.enums.ChatSessionStatus;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findFirstByCustomerUserIdAndBranchBranchIdAndStatusOrderByCreatedAtDesc(
            Long customerId, Integer branchId, ChatSessionStatus status);
    Page<ChatSession> findByCustomerUserIdOrderByLastMessageAtDescSessionIdDesc(Long customerId, Pageable pageable);
    Page<ChatSession> findByBranchBranchIdOrderByLastMessageAtDescSessionIdDesc(Integer branchId, Pageable pageable);
    interface ConversationRow {
        Long getCustomerId();
        Long getSessionId();
        java.time.LocalDateTime getLastMessageAt();
    }
    // Page distinct customers, not individual sessions. Keep an empty OPEN session as the reply target
    // when the same customer already has messages in an earlier consultation.
    @Query(value = "select s.customer.userId as customerId, " +
            "coalesce(max(case when s.status = :openStatus then s.sessionId else null end), max(s.sessionId)) as sessionId, " +
            "max(s.lastMessageAt) as lastMessageAt from ChatSession s where s.branch.branchId = :branchId " +
            "and exists (select m.messageId from ChatMessage m where m.session.customer.userId = s.customer.userId " +
            "and m.session.branch.branchId = :branchId) group by s.customer.userId " +
            "order by max(s.lastMessageAt) desc, s.customer.userId desc",
            countQuery = "select count(distinct s.customer.userId) from ChatSession s where s.branch.branchId = :branchId " +
            "and exists (select m.messageId from ChatMessage m where m.session.customer.userId = s.customer.userId " +
            "and m.session.branch.branchId = :branchId)")
    Page<ConversationRow> findConversations(@Param("branchId") Integer branchId,
            @Param("openStatus") ChatSessionStatus openStatus, Pageable pageable);
    Optional<ChatSession> findFirstByCustomerUserIdAndBranchBranchIdOrderBySessionIdDesc(Long customerId, Integer branchId);
    @Query("select max(s.lastMessageAt) from ChatSession s where s.customer.userId = :customerId and s.branch.branchId = :branchId")
    java.time.LocalDateTime conversationActivity(@Param("customerId") Long customerId, @Param("branchId") Integer branchId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSession s where s.sessionId = :id")
    Optional<ChatSession> findForUpdate(@Param("id") Long id);
}
