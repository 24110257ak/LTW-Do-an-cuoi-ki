package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.CartItem;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderService {
    List<OrderStatus> allowedNextStatuses(Order order);
    List<Order> getReviewOrders();
    Order refundSimulatedOrder(Long id,String reason);
    Order claimOrder(Long orderId);
    List<Order> getClaimableOrders();
    Order confirmDelivery(Long orderId, BigDecimal collectedAmount, String recipient, String note);
    Order inspectReturn(Long orderId, vn.iotstar.dto.request.ReturnInspectionForm form);
    List<vn.iotstar.entity.DeliveryAttempt> getDeliveryAttempts(Long orderId);
    vn.iotstar.entity.OrderReturnReceipt getReturnReceipt(Long orderId);
    void processExpiredHold(Long orderId);
    void remindHold(Long orderId);
    void recordNoShow(Long orderId);
    void setPickupDeadline(Long orderId, int hours);
    void requestCancellation(Long orderId, String reason);
    Order placeOrder(Long userId, CheckoutRequest request);
    Page<Order> getOrdersByUser(Long userId, Pageable pageable);
    Page<Order> getOrdersByUser(Long userId, OrderStatus status, Pageable pageable);
    /** Customer history tabs group delivery/return progress without changing stored statuses. */
    Page<Order> getCustomerOrderHistory(Long userId, OrderStatus tab, Pageable pageable);
    default Page<Order> getOrdersByUserAndStatus(Long userId, OrderStatus status, Pageable pageable) {
        return getOrdersByUser(userId, status, pageable);
    }
    Optional<Order> getOrderByCode(String orderCode);
    Optional<Order> getOrderById(Long orderId);
    void cancelOrder(Long userId, Long orderId, String reason);
    Page<Order> getAllOrders(Pageable pageable);
    Page<Order> getOrdersByBranch(Integer branchId, Pageable pageable);
    Page<Order> getOrdersByBranchAndStatus(Integer branchId, OrderStatus status, Pageable pageable);
    Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable);
    Page<Order> getOrdersByShipper(Long shipperId, OrderStatus status, Pageable pageable);
    void updateOrderStatus(Long orderId, OrderStatus newStatus, String note);
    void markAsPaid(Long orderId, String vnpayTxnRef);

    // Dashboard & Admin
    BigDecimal getTotalRevenue();
    BigDecimal getBranchRevenue(Integer branchId);
    long countOrdersByStatus(OrderStatus status);
    java.util.Map<OrderStatus, Long> getOrderStatusCounts(java.time.LocalDateTime start, java.time.LocalDateTime end);
    long countTotalOrders();
    long countBranchOrders(Integer branchId);
    long countBranchOrdersByStatus(Integer branchId, OrderStatus status);
    List<Order> getRecentOrders();
    List<Order> getRecentBranchOrders(Integer branchId);
    Page<Order> searchOrdersAdmin(Integer branchId, OrderStatus status, String keyword, Pageable pageable);

    // Logistics thông minh & Điểu phối đơn hàng (Smart Order Routing)
    Branch resolveBestFulfillmentBranch(String province, String district, List<CartItem> cartItems);
    BigDecimal calculateEstimatedShippingFee(String province, String district, BigDecimal subtotal, Integer providerId);

    // Vận hành giao hàng & Đối soát tiền mặt COD (COD Cash Reconciliation)
    Order shipperConfirmDelivered(Long orderId, Long shipperUserId, String note);
    Order reportDeliveryFailed(Long orderId, String note);
    Order confirmReturnedToWarehouse(Long orderId, Long warehouseStaffUserId, String conditionNote);
    Order reconcileCodPayment(Long orderId, Long accountantUserId, String transactionRef);
    List<Order> getPendingCodReconciliationOrders();
}
