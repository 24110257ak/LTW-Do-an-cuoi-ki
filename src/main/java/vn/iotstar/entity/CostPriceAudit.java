package vn.iotstar.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;import java.math.BigDecimal;
@Entity @Table(name="cost_price_audits") @Getter @Setter @NoArgsConstructor
public class CostPriceAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long auditId;
 @ManyToOne @JoinColumn(name="variant_id",nullable=false) private ProductVariant variant;
 @ManyToOne @JoinColumn(name="actor_id",nullable=false) private User actor;
 @Column(nullable=false,precision=18,scale=0) private BigDecimal oldCost;
 @Column(nullable=false,precision=18,scale=0) private BigDecimal newCost;
 @Column(nullable=false) private boolean previouslyConfirmed;
 @Column(nullable=false) private LocalDateTime changedAt;
 @Column(nullable=false,length=500,columnDefinition="nvarchar(500) not null") private String reason;
}
