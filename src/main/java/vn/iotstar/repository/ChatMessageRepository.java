package vn.iotstar.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.iotstar.entity.ChatMessage;
import java.util.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findBySessionSessionIdAndClientMessageId(Long sessionId, String clientMessageId);

    List<ChatMessage> findBySessionSessionIdOrderByMessageIdDesc(Long sessionId, Pageable pageable);
    List<ChatMessage> findBySessionSessionIdAndMessageIdLessThanOrderByMessageIdDesc(Long sessionId, Long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m where m.session.sessionId = :sessionId " +
           "and (:beforeId is null or m.messageId < :beforeId) order by m.messageId desc")
    List<ChatMessage> history(@Param("sessionId") Long sessionId, @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("select count(m) from ChatMessage m where m.session.sessionId = :sessionId " +
           "and m.messageId > :readId and m.sender.userId <> :viewerId")
    long countUnread(@Param("sessionId") Long sessionId, @Param("readId") Long readId, @Param("viewerId") Long viewerId);

    @Query("select count(m) from ChatMessage m where m.session.sessionId = :sessionId " +
           "and m.messageId > :readId and m.sender.userId = m.session.customer.userId")
    long countStaffUnread(@Param("sessionId") Long sessionId, @Param("readId") Long readId);

    @Query("select m from ChatMessage m where m.session.sessionId = :sessionId and m.messageId > :afterId order by m.messageId asc")
    List<ChatMessage> since(@Param("sessionId") Long sessionId, @Param("afterId") Long afterId, Pageable pageable);

    // A conversation spans every consultation session of the same customer and branch.
    List<ChatMessage> findBySessionCustomerUserIdAndSessionBranchBranchIdOrderByMessageIdDesc(Long customerId, Integer branchId, Pageable pageable);
    List<ChatMessage> findBySessionCustomerUserIdAndSessionBranchBranchIdAndMessageIdLessThanOrderByMessageIdDesc(Long customerId, Integer branchId, Long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m where m.session.customer.userId = :customerId " +
           "and m.session.branch.branchId = :branchId " +
           "and (:beforeId is null or m.messageId < :beforeId) order by m.messageId desc")
    List<ChatMessage> conversationHistory(@Param("customerId") Long customerId, @Param("branchId") Integer branchId,
                                         @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("select m from ChatMessage m where m.session.customer.userId = :customerId " +
           "and m.session.branch.branchId = :branchId and m.messageId > :afterId order by m.messageId asc")
    List<ChatMessage> conversationSince(@Param("customerId") Long customerId, @Param("branchId") Integer branchId,
                                        @Param("afterId") Long afterId, Pageable pageable);

    @Query("select count(m) from ChatMessage m where m.session.customer.userId = :customerId " +
           "and m.session.branch.branchId = :branchId and m.sender.userId = m.session.customer.userId " +
           "and m.messageId > m.session.staffLastReadId")
    long countConversationStaffUnread(@Param("customerId") Long customerId, @Param("branchId") Integer branchId);

    boolean existsBySessionSessionIdAndMessageId(Long sessionId, Long messageId);
}
