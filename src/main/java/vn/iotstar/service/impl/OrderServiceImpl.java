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
import vn.iotstar.service.OrderService;
import vn.iotstar.util.OrderCodeUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

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
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
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

            // Giảm tồn kho tại chi nhánh được gán nếu có
            if (assignedBranch != null) {
                inventoryRepository.findByBranchBranchIdAndVariantVariantId(assignedBranch.getBranchId(), variant.getVariantId())
                        .ifPresent(inv -> {
                            inv.setQuantity(Math.max(0, inv.getQuantity() - cartItem.getQuantity()));
                            inventoryRepository.save(inv);
                        });
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
            // Gửi WebSocket Realtime tới kênh /topic/orders
            messagingTemplate.convertAndSend("/topic/orders", String.format(
                    "{\"orderCode\":\"%s\",\"totalAmount\":%.0f,\"receiverName\":\"%s\"}",
                    order.getOrderCode(), order.getTotalAmount(), order.getReceiverName()));
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo WebSocket: {}", e.getMessage());
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

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(reason != null ? reason : "Khách hàng hủy đơn");
        orderRepository.save(order);
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
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        order.setStatus(newStatus);
        if (note != null && !note.isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + note);
        }
        orderRepository.save(order);
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
}
