package vn.iotstar.service;

import vn.iotstar.entity.Coupon;

import java.math.BigDecimal;
import java.util.Optional;

public interface CouponService {
    Optional<Coupon> findByCode(String code);
    Coupon validateCoupon(String code, BigDecimal orderAmount, Integer branchId);
    BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount);
}
