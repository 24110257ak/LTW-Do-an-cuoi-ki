package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);
    List<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId);
    Page<Order> findByUserUserId(Long userId, Pageable pageable);
    Page<Order> findByBranchBranchId(Integer branchId, Pageable pageable);
    Page<Order> findByBranchBranchIdAndStatus(Integer branchId, OrderStatus status, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByShipperUserId(Long shipperId, Pageable pageable);
    boolean existsByOrderCode(String orderCode);
}

