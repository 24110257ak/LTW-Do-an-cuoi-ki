package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;

import java.util.Optional;

public interface OrderService {
    Order placeOrder(Long userId, CheckoutRequest request);
    Page<Order> getOrdersByUser(Long userId, Pageable pageable);
    Optional<Order> getOrderByCode(String orderCode);
    Optional<Order> getOrderById(Long orderId);
    void cancelOrder(Long userId, Long orderId, String reason);
    Page<Order> getAllOrders(Pageable pageable);
    Page<Order> getOrdersByBranch(Integer branchId, Pageable pageable);
    Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable);
    void updateOrderStatus(Long orderId, OrderStatus newStatus, String note);
    void markAsPaid(Long orderId, String vnpayTxnRef);
}
