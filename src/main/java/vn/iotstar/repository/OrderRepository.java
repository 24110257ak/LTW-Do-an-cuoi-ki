package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;

import java.math.BigDecimal;
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

    long countByStatus(OrderStatus status);
    long countByBranchBranchId(Integer branchId);
    long countByBranchBranchIdAndStatus(Integer branchId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status != vn.iotstar.enums.OrderStatus.CANCELLED")
    BigDecimal sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.branch.branchId = :branchId AND o.status != vn.iotstar.enums.OrderStatus.CANCELLED")
    BigDecimal sumRevenueByBranch(@Param("branchId") Integer branchId);

    List<Order> findTop10ByOrderByCreatedAtDesc();
    List<Order> findTop10ByBranchBranchIdOrderByCreatedAtDesc(Integer branchId);

    @Query("SELECT o FROM Order o WHERE " +
           "(:branchId IS NULL OR o.branch.branchId = :branchId) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(o.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(o.receiverPhone) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Order> searchOrdersAdmin(
            @Param("branchId") Integer branchId,
            @Param("status") OrderStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
