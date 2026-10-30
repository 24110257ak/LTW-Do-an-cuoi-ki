package vn.iotstar.entity;
import jakarta.persistence.*;import lombok.*;import com.fasterxml.jackson.annotation.JsonIgnore;import java.time.LocalDateTime;import java.math.BigDecimal;import vn.iotstar.enums.OrderStatus;
@Entity @Table(name="delivery_attempts") @Getter @Setter @NoArgsConstructor
public class DeliveryAttempt {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long attemptId;
 @ManyToOne @JoinColumn(name="order_id",nullable=false) @JsonIgnore private Order order;
 @ManyToOne @JoinColumn(name="actor_id",nullable=false) private User actor;
 @Column(nullable=false) private LocalDateTime attemptedAt;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private OrderStatus outcome;
 @Column(precision=18,scale=0) private BigDecimal collectedAmount;
 @Column(length=150,columnDefinition="nvarchar(150)") private String recipientName;
 @Column(length=500,columnDefinition="nvarchar(500)") private String reason;
}
