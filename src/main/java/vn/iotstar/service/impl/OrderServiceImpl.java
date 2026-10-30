package vn.iotstar.service.impl;

import vn.iotstar.service.BranchService;
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

    private final vn.iotstar.service.OperatorService operators;
    private final OrderReturnReceiptRepository returnReceipts;
    private final PaymentRefundRepository refunds;
    private final DeliveryAttemptRepository deliveryAttempts;
    @org.springframework.beans.factory.annotation.Value("${app.orders.unprocessed-cod-hours:2}") private int unprocessedHours;
    @org.springframework.beans.factory.annotation.Value("${app.orders.pickup-hold-hours:48}") private int pickupHours;
    private final BranchService branchService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SimulatedPaymentRepository simulatedPayments;
    private final CartService cartService;
    private final vn.iotstar.service.CheckoutPricingService pricingService;
    private final CouponService couponService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest request) {
        if (request == null) throw new BadRequestException("Thiếu thông tin đặt hàng");
        User user = userRepository.findForCheckout(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        if (!Boolean.TRUE.equals(user.getIsActive())) throw new BadRequestException("Tài khoản đã bị khóa");

        // 0. Chống click đúp / đặt hàng trùng lặp qua Checkout Token Idempotency
        if (request.getCheckoutToken() != null && !request.getCheckoutToken().isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByUserUserIdAndCheckoutToken(userId, request.getCheckoutToken().trim());
            if (existingOrder.isPresent()) {
                log.info("Phát hiện checkoutToken trùng lặp: {}, trả về đơn hàng đã tồn tại #{}",
                        request.getCheckoutToken(), existingOrder.get().getOrderCode());
                return existingOrder.get();
            }
        }

        var quote = pricingService.prepareOrder(userId, request);
        List<CartItem> cartItems = quote.items();
        Branch assignedBranch = quote.branch();
        Branch pickupBranch = request.getOrderType() == OrderType.PICKUP ? assignedBranch : null;
        DeliveryProvider provider = quote.provider();
        Coupon coupon = quote.coupon();
        BigDecimal subtotal = quote.pricing().subtotal();
        BigDecimal shippingFee = quote.pricing().shippingFee();
        BigDecimal discountAmount = quote.pricing().discountAmount();
        BigDecimal totalAmount = quote.pricing().totalAmount();

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
                .paymentSimulated(request.getPaymentMethod() == PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PENDING)
                .status(OrderStatus.NEW)
                .note(request.getNote())
                .build();

        if (order.getPaymentMethod() == PaymentMethod.COD) order.setHoldExpiresAt(LocalDateTime.now().plusHours(unprocessedHours));
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
                    .costPriceConfirmed(Boolean.TRUE.equals(variant.getCostPriceConfirmed()))
                    .quantity(cartItem.getQuantity())
                    .build();
            orderItems.add(orderItemRepository.save(orderItem));

            // Tăng số lượng đã bán của sản phẩm
            product.setTotalSold(product.getTotalSold() + cartItem.getQuantity());
            productRepository.save(product);

            // Giảm tồn kho tại chi nhánh qua InventoryService với khóa PESSIMISTIC_WRITE & ghi nhật ký StockLog
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
        order.setOrderItems(orderItems);
        order.setTotalCost(totalCost);
        // Lợi nhuận gộp = Doanh thu thuần (Subtotal - Discount) - Tổng giá vốn (Total Cost)
        BigDecimal grossProfit = subtotal.subtract(discountAmount).subtract(totalCost);
        order.setGrossProfit(grossProfit);
        orderRepository.save(order);

        if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
            var now = LocalDateTime.now();
            simulatedPayments.save(SimulatedPayment.builder().order(order).amount(order.getTotalAmount())
                    .reference("SIM-VNPAY-" + java.util.UUID.randomUUID()).createdAt(now)
                    .expiresAt(now.plusMinutes(vn.iotstar.service.PaymentSimulationService.HOLD_MINUTES)).build());
        }

        // 6. Xóa giỏ hàng sau khi đặt thành công
        if (request.getBuyNowVariantId() == null) cartService.clearCart(userId);

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
    public Branch resolveBestFulfillmentBranch(String province, String district, List<CartItem> items) {
        return pricingService.resolveBranch(province, district, items);
    }

    @Override
    public BigDecimal calculateEstimatedShippingFee(String province, String district, BigDecimal subtotal, Integer providerId) {
        return pricingService.shippingFee(province, district, subtotal, providerId);
    }

    @Override
    @Transactional
    public Order shipperConfirmDelivered(Long orderId, Long shipperUserId, String note) {
        throw new BadRequestException("Cần xác nhận người nhận và số tiền thực thu qua form giao hàng");
    }

    @Override @Transactional
    public Order confirmDelivery(Long orderId, BigDecimal amount, String recipient, String note) {
        Order order=locked(orderId);User actor=deliveryActor(order);
        if(order.getStatus()==OrderStatus.DELIVERED)return order;
        requireTransition(order,OrderStatus.DELIVERED);
        BigDecimal expected=order.getPaymentMethod()==PaymentMethod.COD?order.getTotalAmount():BigDecimal.ZERO;
        if(amount==null || amount.signum()<0 || amount.stripTrailingZeros().scale()>0 || amount.compareTo(expected)!=0)
            throw new BadRequestException("Số tiền thực thu phải đúng số phải thu: "+expected.toPlainString()+" VND. Nếu chưa thu đủ, báo giao thất bại.");
        if(recipient==null || recipient.trim().length()<2 || recipient.length()>150)throw new BadRequestException("Cần tên người thực nhận từ 2 đến 150 ký tự");
        if(note!=null&&note.length()>500)throw new BadRequestException("Ghi chú tối đa 500 ký tự");
        order.setActualCollectedAmount(amount);complete(order);order.setHoldExpiresAt(null);order.setManualReviewRequired(false);
        saveAttempt(order,actor,OrderStatus.DELIVERED,amount,recipient.trim(),note);
        notifyOrder(order,"Đã giao hàng","Đơn đã giao. Thu COD và nộp quỹ là hai bước riêng.");
        return orderRepository.save(order);
    }

    @Override @Transactional
    public Order reportDeliveryFailed(Long orderId,String note) {
        Order order=locked(orderId);User actor=deliveryActor(order);
        if(order.getStatus()==OrderStatus.DELIVERY_FAILED)return order;
        requireTransition(order,OrderStatus.DELIVERY_FAILED);
        if(note==null||note.isBlank()||note.length()>500)throw new BadRequestException("Cần lý do giao thất bại, tối đa 500 ký tự");
        order.setStatus(OrderStatus.DELIVERY_FAILED);
        saveAttempt(order,actor,OrderStatus.DELIVERY_FAILED,BigDecimal.ZERO,null,note.trim());
        // Hàng vẫn ở tài xế: chưa cộng kho tại bước giao thất bại.
        return orderRepository.save(order);
    }

    @Override @Transactional
    public Order confirmReturnedToWarehouse(Long orderId,Long actorId,String note) {
        throw new BadRequestException("Phải lập phiếu kiểm đếm tốt/hỏng/thiếu theo từng dòng hàng");
    }

    @Override @Transactional
    public Order inspectReturn(Long orderId,vn.iotstar.dto.request.ReturnInspectionForm form) {
        Order order=locked(orderId);User actor=operators.warehouse(order);
        if(order.getStatus()==OrderStatus.RETURNED && returnReceipts.findByOrderOrderId(orderId).isPresent())return order;
        if(order.getStatus()!=OrderStatus.RETURNING || Boolean.TRUE.equals(order.getStockReleased()))throw new BadRequestException("Chỉ kiểm đếm đơn đang chuyển hoàn và chưa nhập lại kho");
        if(form==null || form.getLines()==null || form.getLines().isEmpty() || form.getNote()!=null&&form.getNote().length()>500)throw new BadRequestException("Phiếu nhận hoàn không hợp lệ");
        var items=orderItemRepository.findByOrderOrderId(orderId);
        if(items.isEmpty())throw new BadRequestException("Đơn không có chi tiết để kiểm đếm");
        java.util.Map<Long,vn.iotstar.dto.request.ReturnInspectionForm.Line> received=new java.util.HashMap<>();
        for(var line:form.getLines()) {
            if(line==null || line.getOrderItemId()==null || received.putIfAbsent(line.getOrderItemId(),line)!=null)throw new BadRequestException("Dòng kiểm đếm bị thiếu mã hoặc bị lặp");
        }
        if(received.size()!=items.size())throw new BadRequestException("Phải kiểm đếm đủ các dòng của đơn hàng");
        for(var item:items) {
            var line=received.get(item.getOrderItemId());
            if(line==null || line.getGood()==null || line.getDamaged()==null || line.getMissing()==null || line.getGood()<0 || line.getDamaged()<0 || line.getMissing()<0
                || (long)line.getGood()+line.getDamaged()+line.getMissing()!=item.getQuantity())throw new BadRequestException("Tổng tốt + hỏng + thiếu phải đúng số đã xuất, không được âm");
            if((line.getDamaged()>0||line.getMissing()>0)&&(line.getReason()==null||line.getReason().isBlank()) || line.getReason()!=null&&line.getReason().length()>500)throw new BadRequestException("Cần lý do chênh lệch tối đa 500 ký tự khi có hàng hỏng/thiếu");
        }
        items.stream().map(i->i.getVariant().getProduct().getProductId()).distinct().sorted().forEach(id->productRepository.findForCheckout(id).orElseThrow());
        var receipt=new OrderReturnReceipt();receipt.setOrder(order);receipt.setInspector(actor);receipt.setInspectedAt(LocalDateTime.now());receipt.setNote(form.getNote());
        for(var item:items.stream().sorted(Comparator.comparing(i->i.getVariant().getVariantId())).toList()) {
            var input=received.get(item.getOrderItemId());var line=new OrderReturnLine();line.setReceipt(receipt);line.setItem(item);line.setGoodQuantity(input.getGood());line.setDamagedQuantity(input.getDamaged());line.setMissingQuantity(input.getMissing());line.setDiscrepancyNote(input.getReason());receipt.getLines().add(line);
            if(input.getGood()>0)inventoryService.adjustStock(order.getBranch().getBranchId(),item.getVariant().getVariantId(),input.getGood(),StockMovementType.RETURN,actor,order.getOrderCode(),"Nhận hoàn bán được; hỏng="+input.getDamaged()+", thiếu="+input.getMissing());
            var product=productRepository.findForCheckout(item.getVariant().getProduct().getProductId()).orElseThrow();product.setTotalSold(Math.max(0,product.getTotalSold()-item.getQuantity()));
        }
        returnReceipts.save(receipt);order.setStockReleased(true);
        if(!Boolean.TRUE.equals(order.getCouponReleased())) {if(order.getCoupon()!=null)couponService.releaseCoupon(order.getCoupon().getCouponId());order.setCouponReleased(true);}
        order.setStatus(OrderStatus.RETURNED);order.setHoldExpiresAt(null);
        if(order.getPaymentStatus()==PaymentStatus.PAID)order.setManualReviewRequired(true); // No fictitious refund.
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order reconcileCodPayment(Long orderId, Long accountantUserId, String transactionRef) {
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));
        branchService.checkStaffBranchAccess(order.getBranch() == null ? null : order.getBranch().getBranchId());

        User accountant=operators.warehouse(order);
        if(accountantUserId!=null&&!accountantUserId.equals(accountant.getUserId()))throw new org.springframework.security.access.AccessDeniedException("Sai người đối soát");
        if(transactionRef!=null&&transactionRef.length()>100)throw new BadRequestException("Mã chứng từ tối đa 100 ký tự");
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
        var orders = orderRepository.findByCodCollectedTrueAndCodReconciledFalseOrderByCreatedAtAsc();
        if (!branchService.isCurrentStaff()) return orders;
        Integer branchId = branchService.getStaffBranchId();
        return orders.stream().filter(o -> o.getBranch() != null && branchId.equals(o.getBranch().getBranchId())).toList();
    }

    @Override
    public Page<Order> getOrdersByUser(Long userId, Pageable pageable) {
        return getOrdersByUser(userId, null, pageable);
    }

    @Override
    public Page<Order> getOrdersByUser(Long userId, OrderStatus status, Pageable pageable) {
        return status == null ? orderRepository.findByUserUserId(userId, pageable)
                : orderRepository.findByUserUserIdAndStatus(userId, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getCustomerOrderHistory(Long userId, OrderStatus tab, Pageable pageable) {
        if (tab == null) return orderRepository.findByUserUserId(userId, pageable);
        // Filter the complete group in SQL before counting and paginating.
        List<OrderStatus> statuses = switch (tab) {
            case SHIPPING, DELIVERY_FAILED -> List.of(OrderStatus.SHIPPING, OrderStatus.DELIVERY_FAILED);
            case RETURNING, RETURNED, RETURNED_REFUND ->
                    List.of(OrderStatus.RETURNING, OrderStatus.RETURNED, OrderStatus.RETURNED_REFUND);
            default -> List.of(tab);
        };
        return orderRepository.findByUserUserIdAndStatusIn(userId, statuses, pageable);
    }

    @Override
    public Optional<Order> getOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode).map(order -> {
            branchService.checkStaffBranchAccess(order.getBranch() == null ? null : order.getBranch().getBranchId());
            return order;
        });
    }

    @Override
    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId).map(order -> {
            branchService.checkStaffBranchAccess(order.getBranch() == null ? null : order.getBranch().getBranchId());
            return order;
        });
    }

    @Override
    @Transactional
    public void cancelOrder(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));
        branchService.checkStaffBranchAccess(order.getBranch() == null ? null : order.getBranch().getBranchId());

        if (!order.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền thao tác trên đơn hàng này!");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) return;
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Đơn hàng đã được giao hoặc hoàn tất, không thể hủy!");
        }

        requireTransition(order, OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(reason == null ? "Khách hàng hủy đơn" : reason);
        releaseResources(order);
        orderRepository.save(order);
    }

    @Override
    public Page<Order> getAllOrders(Pageable pageable) {
        if (branchService.isCurrentStaff()) return getOrdersByBranch(branchService.getStaffBranchId(), pageable);
        return orderRepository.findAll(pageable);
    }

    @Override
    public Page<Order> getOrdersByBranch(Integer branchId, Pageable pageable) {
        branchService.checkStaffBranchAccess(branchId);
        return orderRepository.findByBranchBranchId(branchId, pageable);
    }

    @Override
    public Page<Order> getOrdersByBranchAndStatus(Integer branchId, OrderStatus status, Pageable pageable) {
        branchService.checkStaffBranchAccess(branchId);
        if (status == null) {
            return orderRepository.findByBranchBranchId(branchId, pageable);
        }
        return orderRepository.findByBranchBranchIdAndStatus(branchId, status, pageable);
    }

    @Override
    public Page<Order> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        if (branchService.isCurrentStaff()) return getOrdersByBranchAndStatus(branchService.getStaffBranchId(), status, pageable);
        return orderRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getOrdersByShipper(Long shipperId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findByShipperUserIdAndStatus(shipperId, status, pageable);
        }
        return orderRepository.findByShipperUserId(shipperId, pageable);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus, String note) {
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "id", orderId));
        branchService.checkBranchAccess(order.getBranch() == null ? null : order.getBranch().getBranchId());

        if (order.getStatus() == newStatus) return;
        if(newStatus==OrderStatus.RETURNED)throw new BadRequestException("Dùng phiếu kiểm đếm nhận hoàn theo từng dòng hàng");
        if(newStatus==OrderStatus.DELIVERED)throw new BadRequestException("Dùng form xác nhận giao hàng và số tiền thực thu");
        if(newStatus==OrderStatus.DELIVERY_FAILED)throw new BadRequestException("Dùng form báo giao thất bại để lưu từng lần giao");
        requireTransition(order, newStatus);
        if (newStatus == OrderStatus.DELIVERED) complete(order);
        else order.setStatus(newStatus);
        if (note != null && !note.isBlank()) order.setNote((order.getNote() == null ? "" : order.getNote() + " | ") + note);
        if (newStatus == OrderStatus.CANCELLED) {releaseResources(order);order.setHoldExpiresAt(null);order.setManualReviewRequired(false);}
        else if(newStatus==OrderStatus.READY_FOR_PICKUP) {order.setHoldExpiresAt(LocalDateTime.now().plusHours(pickupHours));order.setHoldReminderSentAt(null);}
        else if(newStatus==OrderStatus.CONFIRMED)order.setHoldExpiresAt(null);
        orderRepository.save(order);

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
        throw new BadRequestException("Vui lòng thanh toán qua phiên VNPay mô phỏng của chính đơn hàng; không ghi nhận thủ công");
    }

    @Override
    public BigDecimal getTotalRevenue() {
        if (branchService.isCurrentStaff()) return getBranchRevenue(branchService.getStaffBranchId());
        return orderRepository.sumTotalRevenue();
    }

    @Override
    public BigDecimal getBranchRevenue(Integer branchId) {
        branchService.checkStaffBranchAccess(branchId);
        return orderRepository.sumRevenueByBranch(branchId);
    }

    @Override
    public long countOrdersByStatus(OrderStatus status) {
        if (branchService.isCurrentStaff()) return countBranchOrdersByStatus(branchService.getStaffBranchId(), status);
        return orderRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<OrderStatus, Long> getOrderStatusCounts(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BadRequestException("Khoảng thời gian thống kê không hợp lệ");
        }
        Integer branchId = branchService.isCurrentStaff() ? branchService.getStaffBranchId() : null;
        if (branchId != null) branchService.checkStaffBranchAccess(branchId);
        var counts = new java.util.EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) counts.put(status, 0L);
        orderRepository.countStatusesCreatedBetween(start, end, branchId)
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    @Override
    public long countTotalOrders() {
        if (branchService.isCurrentStaff()) return countBranchOrders(branchService.getStaffBranchId());
        return orderRepository.count();
    }

    @Override
    public long countBranchOrders(Integer branchId) {
        branchService.checkStaffBranchAccess(branchId);
        return orderRepository.countByBranchBranchId(branchId);
    }

    @Override
    public long countBranchOrdersByStatus(Integer branchId, OrderStatus status) {
        branchService.checkStaffBranchAccess(branchId);
        return orderRepository.countByBranchBranchIdAndStatus(branchId, status);
    }

    @Override
    public List<Order> getRecentOrders() {
        if (branchService.isCurrentStaff()) return getRecentBranchOrders(branchService.getStaffBranchId());
        return orderRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Override
    public List<Order> getRecentBranchOrders(Integer branchId) {
        branchService.checkStaffBranchAccess(branchId);
        return orderRepository.findTop10ByBranchBranchIdOrderByCreatedAtDesc(branchId);
    }

    @Override
    public Page<Order> searchOrdersAdmin(Integer branchId, OrderStatus status, String keyword, Pageable pageable) {
        branchService.checkStaffBranchAccess(branchId);
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return orderRepository.searchOrdersAdmin(branchId, status, cleanKeyword, pageable);
    }

    private void requireTransition(Order order, OrderStatus next) {
        if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
            if (next == OrderStatus.CANCELLED && order.getPaymentStatus() == PaymentStatus.PAID)
                throw new BadRequestException("Đơn đã thanh toán mô phỏng; dùng luồng hoàn tiền có kiểm soát nếu đủ điều kiện");
            if (next != OrderStatus.CANCELLED && order.getPaymentStatus() != PaymentStatus.PAID)
                throw new BadRequestException("Cần hoàn tất thanh toán mô phỏng trước khi xử lý đơn");
        }
        if (!allowedNextStatuses(order).contains(next))
            throw new BadRequestException("Không được chuyển trạng thái " + order.getStatus() + " → " + next);
    }

    @Override
    public List<OrderStatus> allowedNextStatuses(Order order) {
        List<OrderStatus> next = switch (order.getStatus()) {
            case NEW -> List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> List.of(order.getOrderType() == OrderType.PICKUP ? OrderStatus.READY_FOR_PICKUP : OrderStatus.SHIPPING, OrderStatus.CANCELLED);
            case READY_FOR_PICKUP -> List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
            case SHIPPING -> List.of(OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED);
            case DELIVERY_FAILED -> List.of(OrderStatus.SHIPPING, OrderStatus.RETURNING);
            case RETURNING -> List.of(OrderStatus.RETURNED);
            default -> List.of();
        };
        if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
            return next.stream().filter(status -> order.getPaymentStatus() == PaymentStatus.PAID
                    ? status != OrderStatus.CANCELLED : status == OrderStatus.CANCELLED).toList();
        }
        return next;
    }

    private void complete(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.COD && order.getPaymentStatus() != PaymentStatus.PAID)
            throw new BadRequestException("Đơn thanh toán trực tuyến chưa được xác nhận thanh toán");
        order.setStatus(OrderStatus.DELIVERED);
        if (order.getCompletedAt() == null) order.setCompletedAt(LocalDateTime.now());
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setCodCollected(true);
            order.setPaymentStatus(PaymentStatus.PAID);
        }
    }

    private void checkAssignedShipper(Order order, Long suppliedId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHIPPER"))) {
            var actor = userRepository.findByUsername(auth.getName()).orElseThrow(() -> new BadRequestException("Tài xế không tồn tại"));
            if (order.getShipper() == null || !actor.getUserId().equals(order.getShipper().getUserId())
                    || suppliedId != null && !actor.getUserId().equals(suppliedId))
                throw new org.springframework.security.access.AccessDeniedException("Đơn chưa được giao cho tài xế này");
            if (order.getOrderType() != OrderType.DELIVERY) throw new BadRequestException("Tài xế không xử lý đơn nhận tại quầy");
        }
    }

    private void releaseResources(Order order) {
        if (order.getBranch() == null) throw new BadRequestException("Đơn không có chi nhánh phục vụ; cần đối soát trước khi hoàn kho");
        var items = new ArrayList<>(orderItemRepository.findByOrderOrderId(order.getOrderId()));
        items.stream().map(i -> i.getVariant().getProduct().getProductId()).distinct().sorted()
                .forEach(id -> productRepository.findForCheckout(id).orElseThrow());
        if (!Boolean.TRUE.equals(order.getCouponReleased())) {
            if (order.getCoupon() != null) couponService.releaseCoupon(order.getCoupon().getCouponId());
            order.setCouponReleased(true);
        }
        if (!Boolean.TRUE.equals(order.getStockReleased())) {
            items.sort(Comparator.comparing(i -> i.getVariant().getVariantId()));
            for (var item : items) {
                inventoryService.adjustStock(order.getBranch().getBranchId(), item.getVariant().getVariantId(), item.getQuantity(),
                        StockMovementType.RETURN, null, order.getOrderCode(), "Nhận lại hàng đơn " + order.getOrderCode());
                var product = productRepository.findForCheckout(item.getVariant().getProduct().getProductId()).orElseThrow();
                product.setTotalSold(Math.max(0, product.getTotalSold() - item.getQuantity()));
            }
            order.setStockReleased(true);
        }
    }

    private Order locked(Long id){return orderRepository.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Đơn hàng","id",id));}
    private User deliveryActor(Order order) {
        User actor=operators.current();
        if("SHIPPER".equals(actor.getRole().getRoleName())) {
            operators.shipperScope(actor,order);
            if(order.getOrderType()!=OrderType.DELIVERY || order.getShipper()==null || !order.getShipper().getUserId().equals(actor.getUserId()))throw new org.springframework.security.access.AccessDeniedException("Đơn chưa giao cho tài xế này");
        } else operators.warehouse(order);
        return actor;
    }
    private void saveAttempt(Order order,User actor,OrderStatus outcome,BigDecimal cash,String recipient,String reason) {
        var attempt=new DeliveryAttempt();attempt.setOrder(order);attempt.setActor(actor);attempt.setOutcome(outcome);attempt.setAttemptedAt(LocalDateTime.now());attempt.setCollectedAmount(cash);attempt.setRecipientName(recipient);attempt.setReason(reason);deliveryAttempts.save(attempt);
    }
    @Override @Transactional
    public Order claimOrder(Long id) {
        User actor=operators.shipper();Order order=locked(id);operators.shipperScope(actor,order);
        if(order.getOrderType()!=OrderType.DELIVERY)throw new BadRequestException("Tài xế chỉ nhận đơn giao tận nơi");
        if(order.getShipper()!=null) {
            if(order.getShipper().getUserId().equals(actor.getUserId()))return order;
            throw new BadRequestException("Đơn đã có tài xế khác tiếp nhận");
        }
        if(order.getStatus()!=OrderStatus.SHIPPING || order.getPaymentMethod()==PaymentMethod.VNPAY&&order.getPaymentStatus()!=PaymentStatus.PAID)throw new BadRequestException("Đơn chưa đủ điều kiện tiếp nhận");
        order.setShipper(actor);return orderRepository.save(order);
    }
    @Override @Transactional(readOnly=true)
    public List<Order> getClaimableOrders() {
        var actor=operators.shipper();return orderRepository.findClaimable(actor.getBranch()==null?null:actor.getBranch().getBranchId(),org.springframework.data.domain.PageRequest.of(0,20));
    }
    private void checkHistoryAccess(Order order) {
        var actor=operators.current();
        if("SHIPPER".equals(actor.getRole().getRoleName()))deliveryActor(order);
        else if(!order.getUser().getUserId().equals(actor.getUserId()))operators.warehouse(order);
    }
    @Override @Transactional(readOnly=true) public List<DeliveryAttempt> getDeliveryAttempts(Long id) {var order=orderRepository.findById(id).orElseThrow();checkHistoryAccess(order);return deliveryAttempts.findByOrderOrderIdOrderByAttemptIdDesc(id);}
    @Override @Transactional(readOnly=true) public OrderReturnReceipt getReturnReceipt(Long id) {var order=orderRepository.findById(id).orElseThrow();checkHistoryAccess(order);return returnReceipts.findByOrderOrderId(id).orElse(null);}
    @Override @Transactional public void requestCancellation(Long id,String reason) {
        var actor=operators.current();var order=locked(id);
        if(!order.getUser().getUserId().equals(actor.getUserId()))throw new org.springframework.security.access.AccessDeniedException("Không phải đơn của bạn");
        if(order.getStatus()!=OrderStatus.READY_FOR_PICKUP || order.getOrderType()!=OrderType.PICKUP)throw new BadRequestException("Chỉ gửi yêu cầu hỗ trợ cho đơn đang chờ nhận tại quầy");
        if(reason==null||reason.isBlank()||reason.length()>500)throw new BadRequestException("Cần lý do từ 1 đến 500 ký tự");
        if(order.getCancellationRequestedAt()!=null)return;
        order.setCancellationRequestedAt(LocalDateTime.now());order.setCancellationRequestReason(reason.trim());order.setManualReviewRequired(true);
        notifyOrder(order,"Đã tiếp nhận yêu cầu hỗ trợ","Chi nhánh sẽ xử lý yêu cầu hủy/không đến nhận. Đơn và khoản thanh toán chưa tự thay đổi.");
    }
    @Override @Transactional public void setPickupDeadline(Long id,int hours) {
        var order=locked(id);operators.warehouse(order);
        if(order.getOrderType()!=OrderType.PICKUP || order.getStatus()!=OrderStatus.READY_FOR_PICKUP || hours<1 || hours>168)throw new BadRequestException("Chỉ đặt hạn 1–168 giờ cho đơn chờ nhận tại quầy");
        order.setHoldExpiresAt(LocalDateTime.now().plusHours(hours));order.setHoldReminderSentAt(null);order.setNoShowAt(null);if(order.getCancellationRequestedAt()==null)order.setManualReviewRequired(false);
        notifyOrder(order,"Hẹn nhận tại cửa hàng","Vui lòng nhận hàng trước "+order.getHoldExpiresAt());
    }
    @Override @Transactional public void recordNoShow(Long id) {var order=locked(id);operators.warehouse(order);if(order.getOrderType()!=OrderType.PICKUP||order.getStatus()!=OrderStatus.READY_FOR_PICKUP||order.getHoldExpiresAt()==null||LocalDateTime.now().isBefore(order.getHoldExpiresAt()))throw new BadRequestException("Đơn chưa đến hạn xử lý không đến nhận");processExpiredHold(id);}
    @Override @Transactional public void processExpiredHold(Long id) {
        var order=locked(id);var now=LocalDateTime.now();
        if(order.getHoldExpiresAt()==null||now.isBefore(order.getHoldExpiresAt())||order.getNoShowAt()!=null)return;
        boolean newCod=order.getStatus()==OrderStatus.NEW && order.getPaymentMethod()==PaymentMethod.COD;
        boolean pickup=order.getStatus()==OrderStatus.READY_FOR_PICKUP && order.getOrderType()==OrderType.PICKUP;
        if(!newCod&&!pickup)return;
        if(pickup)order.setNoShowAt(now);
        if(order.getPaymentStatus()==PaymentStatus.PAID || order.getPaymentMethod()!=PaymentMethod.COD) {
            order.setManualReviewRequired(true);notifyOrder(order,"Cần liên hệ chi nhánh","Đã quá hẹn nhận hàng. Đơn đã thanh toán được giữ để xử lý và hoàn tiền có kiểm soát; chưa tự hủy.");return;
        }
        requireTransition(order,OrderStatus.CANCELLED);order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(pickup?"Khách không đến nhận sau hạn giữ hàng":"Đơn COD mới quá hạn chưa được xử lý");releaseResources(order);order.setHoldExpiresAt(null);
        notifyOrder(order,"Đơn đã hết hạn giữ hàng",order.getCancelledReason());
    }
    @Override @Transactional public void remindHold(Long id) {
        var order=locked(id);var now=LocalDateTime.now();
        if(order.getStatus()!=OrderStatus.READY_FOR_PICKUP||order.getOrderType()!=OrderType.PICKUP||order.getHoldExpiresAt()==null||!now.isBefore(order.getHoldExpiresAt())||order.getHoldExpiresAt().isAfter(now.plusHours(6))||order.getHoldReminderSentAt()!=null)return;
        order.setHoldReminderSentAt(now);notifyOrder(order,"Sắp đến hạn nhận hàng","Vui lòng đến nhận đơn "+order.getOrderCode()+" trước "+order.getHoldExpiresAt());
    }
    private void notifyOrder(Order order,String title,String text) {
        notificationRepository.save(Notification.builder().user(order.getUser()).branch(order.getBranch()).type(NotificationType.SYSTEM).title(title).message(text).refId(order.getOrderId()).build());
    }
    @Override @Transactional(readOnly=true) public List<Order> getReviewOrders(){
        var page=org.springframework.data.domain.PageRequest.of(0,30);
        return branchService.isCurrentStaff()?orderRepository.findByBranchBranchIdAndManualReviewRequiredTrueOrderByUpdatedAtAsc(branchService.getStaffBranchId(),page):orderRepository.findByManualReviewRequiredTrueOrderByUpdatedAtAsc(page);
    }
    @Override @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Order refundSimulatedOrder(Long id,String reason) {
        var actor=operators.admin();var order=locked(id);
        if(refunds.findByOrderOrderId(id).isPresent())return order;
        if(reason==null||reason.isBlank()||reason.length()>500)throw new BadRequestException("Cần lý do hoàn tiền tối đa 500 ký tự");
        boolean pickup=order.getOrderType()==OrderType.PICKUP&&order.getStatus()==OrderStatus.READY_FOR_PICKUP;
        boolean returned=order.getStatus()==OrderStatus.RETURNED&&returnReceipts.findByOrderOrderId(id).isPresent();
        if(!pickup&&!returned)throw new BadRequestException("Chỉ hoàn tiền đơn chờ nhận tại quầy hoặc hàng đã kiểm đếm nhận hoàn");
        if(order.getPaymentMethod()!=PaymentMethod.VNPAY||!Boolean.TRUE.equals(order.getPaymentSimulated())||order.getPaymentStatus()!=PaymentStatus.PAID)throw new BadRequestException("Luồng này chỉ hoàn tiền VNPay mô phỏng đã thanh toán; không thay thế giao dịch ngân hàng hoặc trả tiền COD");
        var payment=simulatedPayments.findForUpdate(id).orElseThrow(()->new BadRequestException("Không có phiên thanh toán gốc"));
        if(payment.getStatus()!=SimulatedPaymentStatus.SUCCESS||payment.getAmount().compareTo(order.getTotalAmount())!=0)throw new BadRequestException("Phiên gốc không khớp để hoàn tiền");
        if(pickup)releaseResources(order); // Goods have never left the pickup store.
        var refund=new PaymentRefund();refund.setOrder(order);refund.setActor(actor);refund.setAmount(order.getTotalAmount());refund.setReference("SIM-REFUND-"+java.util.UUID.randomUUID());refund.setRefundedAt(LocalDateTime.now());refund.setReason(reason.trim());refunds.save(refund);order.setPaymentRefund(refund);
        payment.setStatus(SimulatedPaymentStatus.REFUNDED);order.setPaymentStatus(PaymentStatus.REFUNDED);order.setStatus(OrderStatus.RETURNED_REFUND);order.setManualReviewRequired(false);order.setHoldExpiresAt(null);
        notifyOrder(order,"Đã hoàn tiền mô phỏng","Hoàn "+order.getTotalAmount().toPlainString()+" VND mô phỏng; mã "+refund.getReference()+". Không phát sinh chuyển tiền ngân hàng.");
        return orderRepository.save(order);
    }
}
