package vn.iotstar.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@ToString(exclude = "product")
@EqualsAndHashCode(exclude = "product")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variant_id")
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "color", length = 100,
            columnDefinition = "nvarchar(100)")
    private String color;

    @Column(name = "storage", length = 50)
    private String storage;

    @Column(name = "ram", length = 50)
    private String ram;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "promo_price")
    private BigDecimal promoPrice;

    @Column(name = "sku", length = 100, nullable = false, unique = true)
    private String sku;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Giá hiệu lực (promo nếu có, ngược lại dùng price).
     */
    @Transient
    public BigDecimal getEffectivePrice() {
        return (promoPrice != null && promoPrice.compareTo(BigDecimal.ZERO) > 0)
                ? promoPrice : price;
    }

    /**
     * Chuỗi mô tả biến thể ngắn gọn, dùng cho OrderItem snapshot.
     */
    @Transient
    public String getVariantInfo() {
        StringBuilder sb = new StringBuilder();
        if (color   != null) sb.append(color);
        if (storage != null) sb.append(sb.length() > 0 ? " / " : "").append(storage);
        if (ram     != null) sb.append(sb.length() > 0 ? " / " : "").append(ram);
        return sb.toString();
    }
}
