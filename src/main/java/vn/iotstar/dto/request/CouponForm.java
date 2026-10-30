package vn.iotstar.dto.request;

import lombok.Data;
import vn.iotstar.entity.Coupon;
import vn.iotstar.enums.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Admin input deliberately excludes the usage counter and creation timestamp. */
@Data
public class CouponForm {
    private Integer couponId;
    private String couponCode;
    private String description;
    private DiscountType discountType = DiscountType.PERCENT;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmt = BigDecimal.ZERO;
    private BigDecimal maxDiscount;
    private Integer usageLimit = 100;
    private LocalDateTime validFrom = LocalDateTime.now();
    private LocalDateTime validUntil = LocalDateTime.now().plusMonths(1);
    private Boolean isActive = true;

    public Coupon toEntity() {
        return Coupon.builder().couponId(couponId).couponCode(couponCode).description(description)
                .discountType(discountType).discountValue(discountValue).minOrderAmt(minOrderAmt)
                .maxDiscount(maxDiscount).usageLimit(usageLimit).validFrom(validFrom).validUntil(validUntil)
                .isActive(isActive).build();
    }
}
