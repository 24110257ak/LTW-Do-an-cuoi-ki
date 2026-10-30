package vn.iotstar.entity;
import jakarta.persistence.*;import lombok.*;import com.fasterxml.jackson.annotation.JsonIgnore;import java.time.LocalDateTime;import java.math.BigDecimal;
@Entity @Table(name="payment_refunds") @Getter @Setter @NoArgsConstructor
public class PaymentRefund {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long refundId;
 @OneToOne @JoinColumn(name="order_id",nullable=false,unique=true) @JsonIgnore private Order order;
 @ManyToOne @JoinColumn(name="actor_id",nullable=false) private User actor;
 @Column(nullable=false,precision=18,scale=0) private BigDecimal amount;
 @Column(nullable=false,length=100,unique=true) private String reference;
 @Column(nullable=false) private LocalDateTime refundedAt;
 @Column(nullable=false,length=500,columnDefinition="nvarchar(500) not null") private String reason;
}
