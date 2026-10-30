package vn.iotstar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.enums.*;
import vn.iotstar.exception.*;
import vn.iotstar.repository.*;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class PaymentSimulationService {
    public static final int HOLD_MINUTES = 15;
    public enum Outcome { SUCCESS, FAILED, CANCELLED }
    private final jakarta.persistence.EntityManager entityManager;
    private final OrderRepository orders;
    private final SimulatedPaymentRepository payments;
    private final OrderService orderService;

    @Transactional
    public SimulatedPayment view(Long userId, String orderCode) {
        var payment = owned(userId, orderCode);
        expireIfNecessary(payment);
        return payment;
    }

    @Transactional
    public SimulatedPayment settle(Long userId, String orderCode, Outcome outcome) {
        if (outcome == null) throw new BadRequestException("Vui lòng chọn kết quả thanh toán mô phỏng");
        var payment = owned(userId, orderCode);
        expireIfNecessary(payment);
        if (payment.getStatus() != SimulatedPaymentStatus.PENDING) return payment;
        var order = payment.getOrder();
        if (order.getStatus() != OrderStatus.NEW || order.getPaymentStatus() != PaymentStatus.PENDING)
            throw new BadRequestException("Đơn không còn ở trạng thái chờ thanh toán");
        if (outcome == Outcome.SUCCESS) {
            payment.setStatus(SimulatedPaymentStatus.SUCCESS);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setVnpayTxnRef(payment.getReference());
        } else {
            payment.setStatus(outcome == Outcome.FAILED ? SimulatedPaymentStatus.FAILED : SimulatedPaymentStatus.CANCELLED);
            orderService.cancelOrder(userId, order.getOrderId(), "VNPay mô phỏng: " + payment.getStatus());
        }
        payment.setSettledAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return payment;
    }

    @Transactional
    public void expire(Long paymentId) {
        var existing = payments.findById(paymentId).orElse(null);
        if (existing == null) return;
        var order = orders.findForUpdate(existing.getOrder().getOrderId()).orElseThrow();
        entityManager.refresh(order);
        var payment = payments.findForUpdate(order.getOrderId()).orElseThrow();
        entityManager.refresh(payment);
        if (!Boolean.TRUE.equals(order.getPaymentSimulated()) || order.getPaymentMethod() != PaymentMethod.VNPAY
                || payment.getAmount().compareTo(order.getTotalAmount()) != 0)
            throw new BadRequestException("Phiên thanh toán mô phỏng không khớp đơn hàng");
        expireIfNecessary(payment);
    }

    private SimulatedPayment owned(Long userId, String code) {
        var existing = orders.findByOrderCode(code).orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "mã", code));
        var order = orders.findForUpdate(existing.getOrderId()).orElseThrow();
        entityManager.refresh(order);
        if (userId == null || !order.getUser().getUserId().equals(userId)) throw new AccessDeniedException("Bạn không có quyền thanh toán đơn này");
        if (order.getPaymentMethod() != PaymentMethod.VNPAY || !Boolean.TRUE.equals(order.getPaymentSimulated()))
            throw new BadRequestException("Đơn này không dùng VNPay mô phỏng");
        var payment = payments.findForUpdate(order.getOrderId()).orElseThrow(() -> new BadRequestException("Không có phiên thanh toán mô phỏng"));
        entityManager.refresh(payment);
        if (payment.getAmount().compareTo(order.getTotalAmount()) != 0)
            throw new BadRequestException("Số tiền phiên thanh toán không khớp đơn hàng; cần kiểm tra lại");
        return payment;
    }

    private void expireIfNecessary(SimulatedPayment payment) {
        if (payment.getStatus() != SimulatedPaymentStatus.PENDING) return;
        var order = payment.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLED) {
            payment.setStatus(SimulatedPaymentStatus.CANCELLED);
            payment.setSettledAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        } else if (!LocalDateTime.now().isBefore(payment.getExpiresAt())) {
            orderService.cancelOrder(order.getUser().getUserId(), order.getOrderId(), "Hết hạn giữ hàng VNPay mô phỏng");
            payment.setStatus(SimulatedPaymentStatus.EXPIRED);
            payment.setSettledAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        }
    }
}
