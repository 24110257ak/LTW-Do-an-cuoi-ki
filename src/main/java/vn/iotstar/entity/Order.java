package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.enums.OrderType;
import vn.iotstar.enums.PaymentMethod;
import vn.iotstar.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@ToString(exclude = {"orderItems"})
@EqualsAndHashCode(exclude = {"orderItems"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code", length = 30, nullable = false, unique = true)
    private String orderCode;

    // SQL migration enforces a filtered unique index so legacy NULL tokens remain valid.
    @Column(name = "checkout_token", length = 64)
    private String checkoutToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 20, nullable = false)
    @Builder.Default
    private OrderType orderType = OrderType.DELIVERY;

    // --- Thông tin giao hàng (DELIVERY) ---
    @Column(name = "receiver_name", length = 150,
            columnDefinition = "nvarchar(150)")
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "shipping_address", length = 500,
            columnDefinition = "nvarchar(500)")
    private String shippingAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private DeliveryProvider provider;

    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    // --- Nhận tại cửa hàng (PICKUP) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_branch_id")
    private Branch pickupBranch;

    // --- Tài chính & Quản trị Lợi nhuận (P&L) ---
    @Column(name = "subtotal", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Tổng giá vốn của đơn hàng (COGS) */
    @Column(name = "total_cost", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** Lợi nhuận gộp của đơn hàng (Gross Profit = Subtotal - Discount - TotalCost) */
    @Column(name = "gross_profit", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal grossProfit = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // --- Thanh toán ---
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, nullable = false)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20, nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_simulated", nullable = false)
    @Builder.Default
    private Boolean paymentSimulated = false;

    @Transient
    public String getPaymentSimulationUrl() {
        return Boolean.TRUE.equals(paymentSimulated) && paymentMethod == PaymentMethod.VNPAY ? "/checkout/vnpay-sim/" + orderCode : null;
    }

    @Column(name = "vnpay_txn_ref", length = 100)
    private String vnpayTxnRef;

    // --- Quản trị đối soát tiền mặt COD (COD Cash Reconciliation) ---
    @Column(name = "cod_collected", nullable = false)
    @Builder.Default
    private Boolean codCollected = false;

    @Column(name = "cod_reconciled", nullable = false)
    @Builder.Default
    private Boolean codReconciled = false;

    @Column(name = "cod_reconciled_at")
    private LocalDateTime codReconciledAt;

    @Column(name = "cod_transaction_id", length = 100)
    private String codTransactionId;

    // --- Trạng thái đơn hàng ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id")
    private User shipper;

    @Column(name = "note", length = 500,
            columnDefinition = "nvarchar(500)")
    private String note;

    @Column(name = "cancelled_reason", length = 300,
            columnDefinition = "nvarchar(300)")
    private String cancelledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "stock_released", nullable = false)
    @Builder.Default
    private Boolean stockReleased = false;

    @Column(name = "coupon_released", nullable = false)
    @Builder.Default
    private Boolean couponReleased = false;

    @Column(name="hold_expires_at") private LocalDateTime holdExpiresAt;
    @Column(name="hold_reminder_sent_at") private LocalDateTime holdReminderSentAt;
    @Column(name="no_show_at") private LocalDateTime noShowAt;
    @Column(name="cancellation_requested_at") private LocalDateTime cancellationRequestedAt;
    @Column(name="cancellation_request_reason",length=500,columnDefinition="nvarchar(500)") private String cancellationRequestReason;
    @Column(name="manual_review_required",nullable=false) @Builder.Default private Boolean manualReviewRequired=false;
    @Column(name="actual_collected_amount",precision=18,scale=0) private BigDecimal actualCollectedAmount;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems;

    @OneToOne(mappedBy="order",fetch=FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnore private PaymentRefund paymentRefund;

    @Transient public boolean getHasUnconfirmedCost() {return orderItems!=null && orderItems.stream().anyMatch(i->!Boolean.TRUE.equals(i.getCostPriceConfirmed()));}

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
