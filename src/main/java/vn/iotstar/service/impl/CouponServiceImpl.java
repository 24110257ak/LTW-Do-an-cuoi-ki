package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.iotstar.entity.Coupon;
import vn.iotstar.enums.DiscountType;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.CouponRepository;
import vn.iotstar.service.CouponService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    public Optional<Coupon> findByCode(String code) {
        if (code == null) return Optional.empty();
        return couponRepository.findByCouponCode(code.trim().toUpperCase());
    }

    @Override
    public Coupon validateCoupon(String code, BigDecimal orderAmount, Integer branchId) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Vui lòng nhập mã giảm giá!");
        }

        Coupon coupon = couponRepository.findByCouponCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Mã giảm giá không tồn tại!"));

        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new BadRequestException("Mã giảm giá hiện đang bị tạm khóa!");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidUntil())) {
            throw new BadRequestException("Mã giảm giá chưa đến đợt áp dụng hoặc đã hết hạn!");
        }

        if (coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("Mã giảm giá đã hết lượt sử dụng!");
        }

        if (orderAmount != null && orderAmount.compareTo(coupon.getMinOrderAmt()) < 0) {
            throw new BadRequestException(String.format("Đơn hàng phải tối thiểu %,.0f VNĐ để áp dụng mã này!", coupon.getMinOrderAmt()));
        }

        if (coupon.getBranch() != null && branchId != null && !coupon.getBranch().getBranchId().equals(branchId)) {
            throw new BadRequestException(String.format("Mã giảm giá chỉ áp dụng cho chi nhánh: %s!", coupon.getBranch().getBranchName()));
        }

        return coupon;
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        if (coupon == null || orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        } else if (coupon.getDiscountType() == DiscountType.AMOUNT) {
            discount = coupon.getDiscountValue();
        }

        return discount.min(orderAmount);
    }
}
