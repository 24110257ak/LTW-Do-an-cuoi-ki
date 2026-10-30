package vn.iotstar.service;

import vn.iotstar.entity.Coupon;
import vn.iotstar.enums.DiscountType;
import vn.iotstar.exception.BadRequestException;
import java.math.BigDecimal;

public final class CouponRules {
    private CouponRules() {}

    public static void validate(Coupon c) {
        if (c.getCouponCode() == null || c.getCouponCode().isBlank() || c.getCouponCode().length() > 50)
            fail("Mã giảm giá phải có từ 1 đến 50 ký tự");
        if (c.getDiscountType() == null || c.getDiscountValue() == null || c.getDiscountValue().signum() <= 0)
            fail("Giá trị giảm giá phải lớn hơn 0");
        if (c.getDiscountType() == DiscountType.PERCENT && c.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0)
            fail("Phần trăm giảm giá không được vượt quá 100");
        if (c.getMinOrderAmt() == null || c.getMinOrderAmt().signum() < 0) fail("Đơn tối thiểu không được âm");
        if (c.getMaxDiscount() != null && c.getMaxDiscount().signum() <= 0) fail("Mức giảm tối đa phải lớn hơn 0");
        if (c.getValidFrom() == null || c.getValidUntil() == null || !c.getValidFrom().isBefore(c.getValidUntil()))
            fail("Thời điểm bắt đầu phải trước thời điểm hết hạn");
        for (var amount : new BigDecimal[]{c.getDiscountValue(), c.getMinOrderAmt(), c.getMaxDiscount()}) {
            if (amount != null && amount.stripTrailingZeros().scale() > 0)
                fail("Cấu hình coupon hiện dùng số nguyên (VND hoặc phần trăm nguyên)");
        }
        if (c.getUsageLimit() == null || c.getUsageLimit() <= 0 || c.getUsedCount() == null
                || c.getUsedCount() < 0 || c.getUsedCount() > c.getUsageLimit()) fail("Giới hạn hoặc bộ đếm lượt dùng không hợp lệ");
    }

    private static void fail(String message) { throw new BadRequestException(message); }
}
