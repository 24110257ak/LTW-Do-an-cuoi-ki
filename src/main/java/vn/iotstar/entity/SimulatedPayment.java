package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.enums.SimulatedPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulated_payments", indexes = @Index(name = "ix_sim_payment_expiry", columnList = "status,expires_at"))
@org.hibernate.annotations.Check(constraints = "amount >= 0 AND expires_at > created_at AND status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED','REFUNDED')")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SimulatedPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @Column(nullable = false, unique = true, length = 64)
    private String reference;
    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SimulatedPaymentStatus status = SimulatedPaymentStatus.PENDING;
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
