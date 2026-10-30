package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Coupon;
import vn.iotstar.enums.DiscountType;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.CouponRepository;
import vn.iotstar.service.CouponService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public Optional<Coupon> findByCode(String code) {
        if (code == null) return Optional.empty();
        return couponRepository.findByCouponCode(code.trim().toUpperCase());
    }

    @Override
    @Transactional(readOnly = true)
    public Coupon validateCoupon(String code, BigDecimal orderAmount, Integer branchId) {
        Coupon coupon = couponRepository.findByCouponCode(normalize(code))
                .orElseThrow(() -> new BadRequestException("Mã giảm giá không tồn tại!"));
        return validate(coupon, orderAmount, branchId);
    }

    private Coupon validate(Coupon coupon, BigDecimal orderAmount, Integer branchId) {
        vn.iotstar.service.CouponRules.validate(coupon);
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new BadRequestException("Mã giảm giá hiện đang bị tạm khóa!");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || !now.isBefore(coupon.getValidUntil())) {
            throw new BadRequestException("Mã giảm giá chưa đến đợt áp dụng hoặc đã hết hạn!");
        }

        if (coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new BadRequestException("Mã giảm giá đã hết lượt sử dụng!");
        }

        if (orderAmount != null && orderAmount.compareTo(coupon.getMinOrderAmt()) < 0) {
            throw new BadRequestException(String.format("Đơn hàng phải tối thiểu %,.0f VNĐ để áp dụng mã này!", coupon.getMinOrderAmt()));
        }

        if (coupon.getBranch() != null && !coupon.getBranch().getBranchId().equals(branchId)) {
            throw new BadRequestException(String.format("Mã giảm giá chỉ áp dụng cho chi nhánh: %s!", coupon.getBranch().getBranchName()));
        }

        return coupon;
    }

    private String normalize(String code) {
        if (code == null || code.isBlank()) throw new BadRequestException("Vui lòng nhập mã giảm giá");
        return code.strip().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Coupon reserveCoupon(String code, BigDecimal amount, Integer branchId) {
        Coupon coupon = couponRepository.findByCodeForUpdate(normalize(code))
                .orElseThrow(() -> new BadRequestException("Mã giảm giá không tồn tại"));
        entityManager.refresh(coupon);
        validate(coupon, amount, branchId);
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void releaseCoupon(Integer id) {
        Coupon coupon = couponRepository.findForUpdate(id).orElseThrow(() -> new BadRequestException("Mã giảm giá không còn tồn tại"));
        entityManager.refresh(coupon);
        if (coupon.getUsedCount() <= 0) throw new BadRequestException("Bộ đếm coupon không khớp; cần đối soát trước khi trả lượt");
        coupon.setUsedCount(coupon.getUsedCount() - 1);
        couponRepository.save(coupon);
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        if (coupon == null || orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        vn.iotstar.service.CouponRules.validate(coupon);
        BigDecimal discount = BigDecimal.ZERO;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && coupon.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                discount = discount.min(coupon.getMaxDiscount());
            }
        } else if (coupon.getDiscountType() == DiscountType.AMOUNT) {
            discount = coupon.getDiscountValue();
            if (coupon.getMaxDiscount() != null) discount = discount.min(coupon.getMaxDiscount());
        }

        return discount.min(orderAmount);
    }

    @Override
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @Override
    public Optional<Coupon> getCouponById(Integer id) {
        return couponRepository.findById(id);
    }

    @Override
    @Transactional
    public Coupon saveCoupon(Coupon coupon) {
        coupon.setCouponCode(normalize(coupon.getCouponCode()));
        Coupon existing = coupon.getCouponId() == null ? null : couponRepository.findForUpdate(coupon.getCouponId())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", coupon.getCouponId()));
        coupon.setUsedCount(existing == null ? 0 : existing.getUsedCount());
        if (existing != null) coupon.setCreatedAt(existing.getCreatedAt());
        couponRepository.findByCouponCode(coupon.getCouponCode()).ifPresent(other -> {
            if (!other.getCouponId().equals(coupon.getCouponId())) throw new BadRequestException("Mã giảm giá đã tồn tại");
        });
        vn.iotstar.service.CouponRules.validate(coupon);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void toggleCouponStatus(Integer id) {
        Coupon coupon = couponRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mã giảm giá", "id", id));
        coupon.setIsActive(coupon.getIsActive() == null || !coupon.getIsActive());
        couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(Integer id) {
        couponRepository.deleteById(id);
    }
}
