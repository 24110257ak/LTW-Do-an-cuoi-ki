package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Integer> {
    Optional<Coupon> findByCouponCode(String couponCode);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Coupon c where c.couponCode = :code")
    Optional<Coupon> findByCodeForUpdate(@org.springframework.data.repository.query.Param("code") String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Coupon c where c.couponId = :id")
    Optional<Coupon> findForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);
    List<Coupon> findAllByIsActiveTrueAndValidUntilAfter(LocalDateTime now);
    boolean existsByCouponCode(String couponCode);
}

