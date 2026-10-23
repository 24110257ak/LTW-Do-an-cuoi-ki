package vn.iotstar.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@ToString(exclude = "order")
@EqualsAndHashCode(exclude = "order")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** Snapshot tên sản phẩm tại thời điểm mua */
    @Column(name = "product_name", length = 300, nullable = false,
            columnDefinition = "nvarchar(300) not null")
    private String productName;

    /** Snapshot thông tin biến thể tại thời điểm mua */
    @Column(name = "variant_info", length = 200,
            columnDefinition = "nvarchar(200)")
    private String variantInfo;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    /** Snapshot giá vốn tại thời điểm mua (phục vụ tính lợi nhuận gộp lịch sử) */
    @Column(name = "cost_price", nullable = false)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Transient
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Transient
    public BigDecimal getLineProfit() {
        return (unitPrice.subtract(costPrice != null ? costPrice : BigDecimal.ZERO))
                .multiply(BigDecimal.valueOf(quantity));
    }
}
