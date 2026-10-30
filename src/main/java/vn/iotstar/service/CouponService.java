package vn.iotstar.service;

import vn.iotstar.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CouponService {
    Optional<Coupon> findByCode(String code);
    Coupon validateCoupon(String code, BigDecimal orderAmount, Integer branchId);
    Coupon reserveCoupon(String code, BigDecimal orderAmount, Integer branchId);
    void releaseCoupon(Integer couponId);
    BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount);

    // Admin methods
    List<Coupon> getAllCoupons();
    Optional<Coupon> getCouponById(Integer id);
    Coupon saveCoupon(Coupon coupon);
    void toggleCouponStatus(Integer id);
    void deleteCoupon(Integer id);
}
