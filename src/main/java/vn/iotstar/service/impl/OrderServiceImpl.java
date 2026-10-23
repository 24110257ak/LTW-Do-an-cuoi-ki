package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.*;
import vn.iotstar.enums.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.*;
import vn.iotstar.service.CartService;
import vn.iotstar.service.CouponService;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.OrderService;
import vn.iotstar.util.OrderCodeUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final CouponService couponService;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final DeliveryProviderRepository providerRepository;
    private final ProductRepository productRepository;
    private final BranchInventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        // 0. Chống click đúp / đặt hàng trùng lặp qua Checkout Token Idempotency
        if (request.getCheckoutToken() != null && !request.getCheckoutToken().isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByUserUserIdAndCheckoutToken(userId, request.getCheckoutToken().trim());
            if (existingOrder.isPresent()) {
                log.info("Phát hiện checkoutToken trùng lặp: {}, trả về đơn hàng đã tồn tại #{}",
                        request.getCheckoutToken(), existingOrder.get().getOrderCode());
                return existingOrder.get();
            }
        }

        List<CartItem> cartItems = cartService.getCartItems(userId);
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống, không thể thanh toán!");
        }

        // 1. Tính tổng tiền hàng (subtotal)
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            BigDecimal lineTotal = item.getVariant().getEffectivePrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);
        }

        // 2. Xử lý chi nhánh phục vụ và phí giao hàng
        Branch assignedBranch = null;
        Branch pickupBranch = null;
        BigDecimal shippingFee = BigDecimal.ZERO;
        DeliveryProvider provider = null;

        if (request.getOrderType() == OrderType.PICKUP) {
            if (request.getPickupBranchId() == null) {
                throw new BadRequestException("Vui lòng chọn chi nhánh cửa hàng bạn muốn đến nhận!");
            }
            pickupBranch = branchRepository.findById(request.getPickupBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh", "id", request.getPickupBranchId()));
            assignedBranch = pickupBranch;
            shippingFee = BigDecimal.ZERO;
        } else {
            // DELIVERY
            if (request.getProviderId() != null) {
                provider = providerRepository.findById(request.getProviderId()).orElse(null);
                if (provider != null) {
                    shippingFee = provider.getBaseFee();
                }
            } else {
                shippingFee = BigDecimal.valueOf(30000); // Phí tiêu chuẩn
            }

            // Gán cho chi nhánh đầu tiên của chuỗi xử lý mặc định
            List<Branch> branches = branchRepository.findAllByIsActiveTrue();
            if (!branches.isEmpty()) {
                assignedBranch = branches.get(0);
            }
        }

        // 3. Xử lý mã giảm giá
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            Integer branchId = assignedBranch != null ? assignedBranch.getBranchId() : null;
            coupon = couponService.validateCoupon(request.getCouponCode(), subtotal, branchId);
            discountAmount = couponService.calculateDiscount(coupon, subtotal);
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }

        BigDecimal totalAmount = subtotal.add(shippingFee).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        // 4. Tạo thực thể Order
        String fullAddress = "";
        if (request.getOrderType() == OrderType.DELIVERY) {
            fullAddress = String.format("%s, %s, %s, %s",
                    request.getStreet() != null ? request.getStreet() : "",
                    request.getWard() != null ? request.getWard() : "",
                    request.getDistrict() != null ? request.getDistrict() : "",
                    request.getProvince() != null ? request.getProvince() : "");
        }

        Order order = Order.builder()
                .orderCode(OrderCodeUtils.generateOrderCode())
                .checkoutToken(request.getCheckoutToken() != null && !request.getCheckoutToken().isBlank() ? request.getCheckoutToken().trim() : null)
                .user(user)
                .branch(assignedBranch)
                .orderType(request.getOrderType())
                .receiverName(request.getReceiverName() != null ? request.getReceiverName() : user.getFullName())
                .receiverPhone(request.getReceiverPhone() != null ? request.getReceiverPhone() : user.getPhone())
                .shippingAddress(fullAddress)
                .provider(provider)
                .shippingFee(shippingFee)
                .pickupBranch(pickupBranch)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .coupon(coupon)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.NEW)
                .note(request.getNote())
                .build();

        order = orderRepository.save(order);

        // 5. Tạo OrderItems và cập nhật kho, sản phẩm
        // Sắp xếp các mục theo variantId tăng dần để triệt tiêu Deadlock khi nhiều khách cùng mua đồng thời
        List<CartItem> sortedCartItems = new ArrayList<>(cartItems);
        sortedCartItems.sort(Comparator.comparing(item -> item.getVariant().getVariantId()));

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .variant(variant)
                    .productName(product.getProductName())
                    .variantInfo(variant.getVariantInfo())
                    .unitPrice(variant.getEffectivePrice())
                    .quantity(cartItem.getQuantity())
                    .build();
            orderItems.add(orderItemRepository.save(orderItem));

            // Tăng số lượng đã bán của sản phẩm
            product.setTotalSold(product.getTotalSold() + cartItem.getQuantity());
            productRepository.save(product);

            // Giảm tồn kho tại chi nhánh qua InventoryService với khóa PESSIMISTIC_WRITE & ghi nhật ký StockLog
            if (assignedBranch != null) {
                inventoryService.adjustStock(
                        assignedBranch.getBranchId(),
                        variant.getVariantId(),
                        -cartItem.getQuantity(),
                        StockMovementType.SALE,
                        user,
                        order.getOrderCode(),
                        "Bán hàng đơn #" + order.getOrderCode()
                );
            }
        }
        order.setOrderItems(orderItems);

        // 6. Xóa giỏ hàng sau khi đặt thành công
        cartService.clearCart(userId);

        // 7. Tạo thông báo cho User và gửi WebSocket tới Admin/Staff
        Notification notif = Notification.builder()
                .user(user)
                .branch(assignedBranch)
                .type(NotificationType.NEW_ORDER)
                .title(String.format("Đặt hàng thành công #%s", order.getOrderCode()))
                .message(String.format("Đơn hàng #%s trị giá %,.0f đ đã được tiếp nhận và đang chờ xử lý.", order.getOrderCode(), order.getTotalAmount()))
                .refId(order.getOrderId())
                .build();
        notificationRepository.save(notif);

        try {
            messagingTemplate.convertAndSend("/topic/admin/orders", String.format(
                    "{\"orderCode\":\"%s\",\"totalAmount\":%s,\"customer\":\"%s\"}",
                    order.getOrderCode(), order.getTotalAmount(), user.getFullName()));
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo WebSocket đặt hàng mới: {}", e.getMessage());
        }

        return order;
    }

    @Override
    public Page<Order> getOrdersByUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserUserId(userId, pageable);
    }

    @Override
    public Optional<Order> getOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode);
    }

    @Override
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        if (!order.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đơn hàng này!");
        }

        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Đơn hàng đã được giao hoặc hoàn tất, không thể hủy!");
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(reason != null ? reason : "Khách hàng hủy đơn");
        orderRepository.save(order);

        // Hoàn lại lượt dùng mã giảm giá nếu có
        if (order.getCoupon() != null) {
            Coupon coupon = order.getCoupon();
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            couponRepository.save(coupon);
        }

        // Hoàn trả tồn kho và ghi nhận StockLog kiểm toán
        if (oldStatus != OrderStatus.CANCELLED && oldStatus != OrderStatus.RETURNED_REFUND && order.getBranch() != null) {
            List<OrderItem> items = orderItemRepository.findByOrderOrderId(order.getOrderId());
            for (OrderItem item : items) {
                try {
                    inventoryService.adjustStock(
                            order.getBranch().getBranchId(),
                            item.getVariant().getVariantId(),
                            item.getQuantity(),
                            StockMovementType.RETURN,
                            order.getUser(),
                            order.getOrderCode(),
                            "Hoàn kho khi khách hủy đơn #" + order.getOrderCode()
                    );
                } catch (Exception ex) {
                    log.error("Lỗi hoàn kho cho sản phẩm {}: {}", item.getVariant().getVariantId(), ex.getMessage());
                }
            }
        }
    }

    @Override
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Override
    public Page<Order> getOrdersByBranch(Integer branchId, Pageable pageable) {
        return orderRepository.findByBranchBranchId(branchId, pageable);
    }

    @Override
    public Page<Order> getOrdersByBranchAndStatus(Integer branchId, OrderStatus status, Pageable pageable) {
        if (status == null) {
            return orderRepository.findByBranchBranchId(branchId, pageable);
        }
        return orderRepository.findByBranchBranchIdAndStatus(branchId, status, pageable);
    }

    @Override
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        if (note != null && !note.isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + note);
        }
        orderRepository.save(order);

        // Nếu chuyển trạng thái sang CANCELLED hoặc RETURNED_REFUND thì hoàn trả tồn kho và ghi StockLog
        if ((newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.RETURNED_REFUND)
                && (oldStatus != OrderStatus.CANCELLED && oldStatus != OrderStatus.RETURNED_REFUND)
                && order.getBranch() != null) {
            if (order.getCoupon() != null) {
                Coupon coupon = order.getCoupon();
                coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
                couponRepository.save(coupon);
            }

            List<OrderItem> items = orderItemRepository.findByOrderOrderId(order.getOrderId());
            for (OrderItem item : items) {
                try {
                    inventoryService.adjustStock(
                            order.getBranch().getBranchId(),
                            item.getVariant().getVariantId(),
                            item.getQuantity(),
                            StockMovementType.RETURN,
                            null,
                            order.getOrderCode(),
                            "Hoàn kho cập nhật trạng thái " + newStatus.name() + " đơn #" + order.getOrderCode()
                    );
                } catch (Exception ex) {
                    log.error("Lỗi hoàn kho cho sản phẩm {}: {}", item.getVariant().getVariantId(), ex.getMessage());
                }
            }
        }

        // Gửi WebSocket thông báo cập nhật trạng thái đơn
        try {
            messagingTemplate.convertAndSend("/topic/order-status/" + order.getOrderCode(), String.format(
                    "{\"orderCode\":\"%s\",\"status\":\"%s\"}", order.getOrderCode(), newStatus.name()));
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo WebSocket cập nhật trạng thái: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void markAsPaid(Long orderId, String vnpayTxnRef) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setVnpayTxnRef(vnpayTxnRef);
        orderRepository.save(order);
    }

    @Override
    public BigDecimal getTotalRevenue() {
        return orderRepository.sumTotalRevenue();
    }

    @Override
    public BigDecimal getBranchRevenue(Integer branchId) {
        return orderRepository.sumRevenueByBranch(branchId);
    }

    @Override
    public long countOrdersByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    @Override
    public long countTotalOrders() {
        return orderRepository.count();
    }

    @Override
    public long countBranchOrders(Integer branchId) {
        return orderRepository.countByBranchBranchId(branchId);
    }

    @Override
    public long countBranchOrdersByStatus(Integer branchId, OrderStatus status) {
        return orderRepository.countByBranchBranchIdAndStatus(branchId, status);
    }

    @Override
    public List<Order> getRecentOrders() {
        return orderRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Override
    public List<Order> getRecentBranchOrders(Integer branchId) {
        return orderRepository.findTop10ByBranchBranchIdOrderByCreatedAtDesc(branchId);
    }

    @Override
    public Page<Order> searchOrdersAdmin(Integer branchId, OrderStatus status, String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return orderRepository.searchOrdersAdmin(branchId, status, cleanKeyword, pageable);
    }
}
