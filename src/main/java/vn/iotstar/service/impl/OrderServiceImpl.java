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
import java.time.LocalDateTime;
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

        // 2. Xử lý chi nhánh phục vụ và phí giao hàng (Smart Order Routing & Logistics)
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
            // DELIVERY: Ưu tiên chọn nhà vận chuyển nếu khách chọn
            if (request.getProviderId() != null) {
                provider = providerRepository.findById(request.getProviderId()).orElse(null);
            }
            shippingFee = calculateEstimatedShippingFee(request.getProvince(), request.getDistrict(), subtotal, request.getProviderId());

            // Smart Order Routing: Định tuyến chi nhánh gần nhất và có đủ hàng phục vụ
            assignedBranch = resolveBestFulfillmentBranch(request.getProvince(), request.getDistrict(), cartItems);
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
                .totalCost(BigDecimal.ZERO)
                .grossProfit(BigDecimal.ZERO)
                .codCollected(false)
                .codReconciled(false)
                .coupon(coupon)
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.NEW)
                .note(request.getNote())
                .build();

        order = orderRepository.save(order);

        // 5. Tạo OrderItems, tính COGS (Giá vốn hàng bán) & Lợi nhuận gộp, cập nhật tồn kho
        // Sắp xếp các mục theo variantId tăng dần để triệt tiêu Deadlock khi nhiều khách cùng mua đồng thời
        List<CartItem> sortedCartItems = new ArrayList<>(cartItems);
        sortedCartItems.sort(Comparator.comparing(item -> item.getVariant().getVariantId()));

        BigDecimal totalCost = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant variant = cartItem.getVariant();
            Product product = variant.getProduct();

            BigDecimal costPrice = variant.getCostPrice() != null ? variant.getCostPrice() : BigDecimal.ZERO;
            BigDecimal lineCost = costPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalCost = totalCost.add(lineCost);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .variant(variant)
                    .productName(product.getProductName())
                    .variantInfo(variant.getVariantInfo())
                    .unitPrice(variant.getEffectivePrice())
                    .costPrice(costPrice)
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
        order.setTotalCost(totalCost);
        // Lợi nhuận gộp = Doanh thu thuần (Subtotal - Discount) - Tổng giá vốn (Total Cost)
        BigDecimal grossProfit = subtotal.subtract(discountAmount).subtract(totalCost);
        order.setGrossProfit(grossProfit);
        orderRepository.save(order);

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
    public Branch resolveBestFulfillmentBranch(String province, String district, List<CartItem> cartItems) {
        List<Branch> activeBranches = branchRepository.findAllByIsActiveTrue();
        if (activeBranches.isEmpty()) {
            return null;
        }

        // Lọc các chi nhánh có đủ tồn kho cho toàn bộ danh mục sản phẩm trong giỏ
        List<Branch> capableBranches = new ArrayList<>();
        for (Branch branch : activeBranches) {
            boolean hasAll = true;
            for (CartItem item : cartItems) {
                Optional<BranchInventory> invOpt = inventoryRepository.findByBranchBranchIdAndVariantVariantId(
                        branch.getBranchId(), item.getVariant().getVariantId());
                if (invOpt.isEmpty() || invOpt.get().getQuantity() < item.getQuantity()) {
                    hasAll = false;
                    break;
                }
            }
            if (hasAll) {
                capableBranches.add(branch);
            }
        }

        if (!capableBranches.isEmpty()) {
            // Ưu tiên 1: Cùng Quận/Huyện
            if (district != null && !district.isBlank()) {
                for (Branch b : capableBranches) {
                    if (b.getDistrict() != null && district.toLowerCase().contains(b.getDistrict().toLowerCase())) {
                        log.info("Smart Order Routing: Khớp chi nhánh cấp Quận [{}] cho đơn hàng", b.getBranchName());
                        return b;
                    }
                }
            }
            // Ưu tiên 2: Cùng Tỉnh/Thành
            if (province != null && !province.isBlank()) {
                for (Branch b : capableBranches) {
                    if (b.getProvince() != null && province.toLowerCase().contains(b.getProvince().toLowerCase())) {
                        log.info("Smart Order Routing: Khớp chi nhánh cấp Tỉnh [{}] cho đơn hàng", b.getBranchName());
                        return b;
                    }
                }
            }
            // Ưu tiên 3: Chi nhánh có tổng tồn kho các mặt hàng này dồi dào nhất
            capableBranches.sort((b1, b2) -> {
                int stock1 = cartItems.stream().mapToInt(ci -> inventoryRepository.findByBranchBranchIdAndVariantVariantId(b1.getBranchId(), ci.getVariant().getVariantId()).map(BranchInventory::getQuantity).orElse(0)).sum();
                int stock2 = cartItems.stream().mapToInt(ci -> inventoryRepository.findByBranchBranchIdAndVariantVariantId(b2.getBranchId(), ci.getVariant().getVariantId()).map(BranchInventory::getQuantity).orElse(0)).sum();
                return Integer.compare(stock2, stock1);
            });
            log.info("Smart Order Routing: Chọn chi nhánh tồn kho dồi dào nhất [{}]", capableBranches.get(0).getBranchName());
            return capableBranches.get(0);
        }

        // Dự phòng nếu không có chi nhánh nào có đủ 100% giỏ: Chọn chi nhánh đầu tiên của hệ thống
        log.warn("Smart Order Routing: Không có chi nhánh nào có đủ 100% mặt hàng trong giỏ, gán chi nhánh trung tâm: {}", activeBranches.get(0).getBranchName());
        return activeBranches.get(0);
    }

    @Override
    public BigDecimal calculateEstimatedShippingFee(String province, String district, BigDecimal subtotal, Integer providerId) {
        // Chính sách 1: Miễn phí vận chuyển toàn quốc cho đơn hàng từ 5 triệu đồng trở lên
        if (subtotal != null && subtotal.compareTo(BigDecimal.valueOf(5000000)) >= 0) {
            return BigDecimal.ZERO;
        }

        // Chính sách 2: Áp dụng đơn giá cơ bản của đơn vị vận chuyển nếu khách chọn
        if (providerId != null) {
            Optional<DeliveryProvider> providerOpt = providerRepository.findById(providerId);
            if (providerOpt.isPresent() && providerOpt.get().getBaseFee() != null) {
                return providerOpt.get().getBaseFee();
            }
        }

        // Chính sách 3: Phân vùng địa lý thông minh
        if (province == null || province.isBlank()) {
            return BigDecimal.valueOf(30000);
        }

        String prov = province.toLowerCase();
        if (prov.contains("hồ chí minh") || prov.contains("tp.hcm") || prov.contains("hcm") || prov.contains("sài gòn")) {
            if (district != null) {
                String dist = district.toLowerCase();
                // Các quận nội thành TP.HCM (gần kho trung tâm): 25.000 đ
                if (dist.contains("1") || dist.contains("3") || dist.contains("5") || dist.contains("10")
                        || dist.contains("bình thạnh") || dist.contains("phú nhuận") || dist.contains("gò vấp")
                        || dist.contains("tân bình") || dist.contains("tân phú") || dist.contains("thủ đức")) {
                    return BigDecimal.valueOf(25000);
                }
            }
            return BigDecimal.valueOf(30000); // Ngoại thành TP.HCM
        }

        // Các tỉnh thành phố khác trên toàn quốc: 40.000 đ
        return BigDecimal.valueOf(40000);
    }

    @Override
    @Transactional
    public Order shipperConfirmDelivered(Long orderId, Long shipperUserId, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        if (order.getStatus() == OrderStatus.DELIVERED) {
            return order;
        }

        order.setStatus(OrderStatus.DELIVERED);
        if (shipperUserId != null) {
            userRepository.findById(shipperUserId).ifPresent(order::setShipper);
        }

        // Nếu là đơn hàng COD: Shipper đã thu tiền mặt từ khách hàng
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setCodCollected(true);
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        if (note != null && !note.isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Shipper xác nhận: " + note);
        }

        Order saved = orderRepository.save(order);

        try {
            messagingTemplate.convertAndSend("/topic/order-status/" + saved.getOrderCode(), String.format(
                    "{\"orderCode\":\"%s\",\"status\":\"%s\"}", saved.getOrderCode(), OrderStatus.DELIVERED.name()));
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo WebSocket giao hàng thành công: {}", e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional
    public Order reportDeliveryFailed(Long orderId, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        order.setStatus(OrderStatus.DELIVERY_FAILED);
        String reason = note != null ? note : "Khách hàng không nghe máy / hẹn lại";
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Giao không thành công: " + reason);

        // Chú ý: Chưa hoàn kho tại bước này (Reverse Logistics Restock Safety Guard)
        // vì hàng hóa vẫn đang nằm trên thùng xe shipper, tránh ghi sai lệch số liệu tồn vật lý.
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order confirmReturnedToWarehouse(Long orderId, Long warehouseStaffUserId, String conditionNote) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.RETURNED);
        order.setCodCollected(false);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        String condition = (conditionNote != null && !conditionNote.isBlank()) ? conditionNote : "Nguyên vẹn tem mác";
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Thủ kho nhận hoàn kho: " + condition);

        User staff = warehouseStaffUserId != null ? userRepository.findById(warehouseStaffUserId).orElse(null) : null;

        // Chỉ nhập lại tồn kho vật lý sau khi thủ kho đã kiểm đếm thực tế
        if (oldStatus != OrderStatus.RETURNED && oldStatus != OrderStatus.RETURNED_REFUND && order.getBranch() != null) {
            List<OrderItem> items = orderItemRepository.findByOrderOrderId(order.getOrderId());
            for (OrderItem item : items) {
                try {
                    inventoryService.adjustStock(
                            order.getBranch().getBranchId(),
                            item.getVariant().getVariantId(),
                            item.getQuantity(),
                            StockMovementType.RETURN,
                            staff,
                            order.getOrderCode(),
                            "Nhập kho hàng hoàn đơn #" + order.getOrderCode()
                    );
                } catch (Exception ex) {
                    log.error("Lỗi hoàn kho cho biến thể {}: {}", item.getVariant().getVariantId(), ex.getMessage());
                }
            }
        }

        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order reconcileCodPayment(Long orderId, Long accountantUserId, String transactionRef) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));

        if (order.getPaymentMethod() != PaymentMethod.COD) {
            throw new BadRequestException("Đơn hàng này không thuộc diện thanh toán khi nhận hàng (COD)!");
        }
        if (!Boolean.TRUE.equals(order.getCodCollected())) {
            throw new BadRequestException("Đơn hàng chưa được shipper xác nhận thu tiền COD!");
        }
        if (Boolean.TRUE.equals(order.getCodReconciled())) {
            throw new BadRequestException("Đơn hàng này đã được kế toán đối soát nộp quỹ trước đó!");
        }

        String txnId = (transactionRef != null && !transactionRef.isBlank())
                ? transactionRef.trim()
                : "COD-REC-" + order.getOrderCode() + "-" + (System.currentTimeMillis() % 100000);

        order.setCodReconciled(true);
        order.setCodReconciledAt(LocalDateTime.now());
        order.setCodTransactionId(txnId);

        log.info("Kế toán đối soát thành công đơn COD #{}, số tiền {}, mã chứng từ: {}",
                order.getOrderCode(), order.getTotalAmount(), txnId);

        return orderRepository.save(order);
    }

    @Override
    public List<Order> getPendingCodReconciliationOrders() {
        return orderRepository.findByCodCollectedTrueAndCodReconciledFalseOrderByCreatedAtAsc();
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
        if (oldStatus != OrderStatus.CANCELLED && oldStatus != OrderStatus.RETURNED_REFUND && oldStatus != OrderStatus.RETURNED && order.getBranch() != null) {
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

        // Đồng bộ trạng thái COD nếu giao hàng thành công
        if (newStatus == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setCodCollected(true);
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        orderRepository.save(order);

        // Nếu chuyển trạng thái sang CANCELLED, RETURNED hoặc RETURNED_REFUND thì hoàn trả tồn kho và ghi StockLog
        boolean isRestockStatus = (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.RETURNED_REFUND || newStatus == OrderStatus.RETURNED);
        boolean wasRestocked = (oldStatus == OrderStatus.CANCELLED || oldStatus == OrderStatus.RETURNED_REFUND || oldStatus == OrderStatus.RETURNED);

        if (isRestockStatus && !wasRestocked && order.getBranch() != null) {
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
