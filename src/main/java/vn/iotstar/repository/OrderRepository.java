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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Order e where e.orderId = :id")
    Optional<Order> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Order> findByOrderCode(String orderCode);
    Optional<Order> findByCheckoutToken(String checkoutToken);
    Optional<Order> findByUserUserIdAndCheckoutToken(Long userId, String checkoutToken);
    List<Order> findByUserUserIdOrderByCreatedAtDesc(Long userId);
    Page<Order> findByUserUserId(Long userId, Pageable pageable);
    Page<Order> findByUserUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
    Page<Order> findByUserUserIdAndStatusIn(Long userId, List<OrderStatus> statuses, Pageable pageable);
    Page<Order> findByBranchBranchId(Integer branchId, Pageable pageable);
    Page<Order> findByBranchBranchIdAndStatus(Integer branchId, OrderStatus status, Pageable pageable);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByShipperUserId(Long shipperId, Pageable pageable);
    Page<Order> findByShipperUserIdAndStatus(Long shipperId, OrderStatus status, Pageable pageable);
    long countByShipperUserId(Long shipperId);
    long countByShipperUserIdAndStatus(Long shipperId, OrderStatus status);
    boolean existsByOrderCode(String orderCode);

    long countByStatus(OrderStatus status);

    interface StatusCount {
        OrderStatus getStatus();
        long getTotal();
    }

    // Khoảng nửa mở: lấy cả tháng cuối, không lấy đầu tháng kế tiếp.
    @Query("select o.status as status, count(o) as total from Order o left join o.branch b " +
           "where o.createdAt >= :start and o.createdAt < :end " +
           "and (:branchId is null or b.branchId = :branchId) group by o.status")
    List<StatusCount> countStatusesCreatedBetween(
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end,
            @Param("branchId") Integer branchId);
    long countByBranchBranchId(Integer branchId);
    long countByBranchBranchIdAndStatus(Integer branchId, OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.subtotal - o.discountAmount), 0) FROM Order o WHERE o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.subtotal - o.discountAmount), 0) FROM Order o WHERE o.branch.branchId = :branchId AND o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumRevenueByBranch(@Param("branchId") Integer branchId);

    @Query("SELECT COALESCE(SUM(o.grossProfit), 0) FROM Order o WHERE o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumTotalGrossProfit();

    @Query("SELECT COALESCE(SUM(o.grossProfit), 0) FROM Order o WHERE o.branch.branchId = :branchId AND o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumGrossProfitByBranch(@Param("branchId") Integer branchId);

    @Query("SELECT COALESCE(SUM(o.totalCost), 0) FROM Order o WHERE o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumTotalCost();

    @Query("SELECT COALESCE(SUM(o.totalCost), 0) FROM Order o WHERE o.branch.branchId = :branchId AND o.status = vn.iotstar.enums.OrderStatus.DELIVERED")
    BigDecimal sumTotalCostByBranch(@Param("branchId") Integer branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentMethod = vn.iotstar.enums.PaymentMethod.COD AND o.codCollected = true AND o.codReconciled = false")
    BigDecimal sumPendingCodReconciliationAmount();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentMethod = vn.iotstar.enums.PaymentMethod.COD AND o.codReconciled = true")
    BigDecimal sumReconciledCodAmount();

    List<Order> findByCodCollectedTrueAndCodReconciledFalseOrderByCreatedAtAsc();

    List<Order> findTop10ByOrderByCreatedAtDesc();
    List<Order> findTop10ByBranchBranchIdOrderByCreatedAtDesc(Integer branchId);

    @Query("select o from Order o where o.status = vn.iotstar.enums.OrderStatus.DELIVERED and o.completedAt >= :start and o.completedAt < :end order by o.completedAt")
    List<Order> findCompletedBetween(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

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

    @Query("select coalesce(sum(o.totalAmount),0) from Order o where o.status in (vn.iotstar.enums.OrderStatus.NEW,vn.iotstar.enums.OrderStatus.CONFIRMED,vn.iotstar.enums.OrderStatus.READY_FOR_PICKUP,vn.iotstar.enums.OrderStatus.SHIPPING,vn.iotstar.enums.OrderStatus.DELIVERY_FAILED,vn.iotstar.enums.OrderStatus.RETURNING)")
    BigDecimal sumProcessingOrderValue();

    @Query("select coalesce(sum(o.totalAmount),0) from Order o where o.paymentStatus = vn.iotstar.enums.PaymentStatus.PAID and o.paymentSimulated = false")
    BigDecimal sumConfirmedPayments();
    @Query("select coalesce(sum(o.totalAmount),0) from Order o where o.paymentStatus = vn.iotstar.enums.PaymentStatus.PAID and o.paymentSimulated = true")
    BigDecimal sumSimulatedPayments();

    @Query("select o from Order o where o.orderType=vn.iotstar.enums.OrderType.DELIVERY and o.status=vn.iotstar.enums.OrderStatus.SHIPPING and o.shipper is null and o.branch.isActive=true and (:branchId is null or o.branch.branchId=:branchId) and (o.paymentMethod=vn.iotstar.enums.PaymentMethod.COD or o.paymentStatus=vn.iotstar.enums.PaymentStatus.PAID) order by o.orderId")
    List<Order> findClaimable(@Param("branchId") Integer branchId, Pageable pageable);
    @Query("select o.orderId from Order o where o.holdExpiresAt<=:before and o.status in (vn.iotstar.enums.OrderStatus.NEW,vn.iotstar.enums.OrderStatus.READY_FOR_PICKUP) and o.noShowAt is null")
    List<Long> findExpiredHoldIds(@Param("before") java.time.LocalDateTime before, Pageable pageable);
    @Query("select o.orderId from Order o where o.orderType=vn.iotstar.enums.OrderType.PICKUP and o.status=vn.iotstar.enums.OrderStatus.READY_FOR_PICKUP and o.holdReminderSentAt is null and o.holdExpiresAt>:now and o.holdExpiresAt<=:soon")
    List<Long> findReminderIds(@Param("now") java.time.LocalDateTime now,@Param("soon") java.time.LocalDateTime soon, Pageable pageable);
    List<Order> findByShipperUserIdAndPaymentMethodOrderByCreatedAtDesc(Long shipperId, vn.iotstar.enums.PaymentMethod method);
    @Query("select count(distinct i.order.orderId) from OrderItem i where i.order.status=vn.iotstar.enums.OrderStatus.DELIVERED and i.costPriceConfirmed=false")
    long countDeliveredWithUnconfirmedCost();
    List<Order> findByManualReviewRequiredTrueOrderByUpdatedAtAsc(Pageable pageable);
    List<Order> findByBranchBranchIdAndManualReviewRequiredTrueOrderByUpdatedAtAsc(Integer branchId,Pageable pageable);
}
