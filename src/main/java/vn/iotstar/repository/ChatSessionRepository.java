package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.ChatSession;
import vn.iotstar.enums.ChatSessionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByCustomerUserIdOrderByCreatedAtDesc(Long customerId);
    List<ChatSession> findByBranchBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, ChatSessionStatus status);
    Optional<ChatSession> findByCustomerUserIdAndBranchBranchIdAndStatus(Long customerId, Integer branchId, ChatSessionStatus status);
}
