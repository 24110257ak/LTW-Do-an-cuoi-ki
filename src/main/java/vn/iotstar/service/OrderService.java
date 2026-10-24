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
    Order placeOrder(Long userId, CheckoutRequest request);
    Page<Order> getOrdersByUser(Long userId, Pageable pageable);
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
