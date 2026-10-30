package vn.iotstar.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.iotstar.entity.SimulatedPayment;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface SimulatedPaymentRepository extends JpaRepository<SimulatedPayment, Long> {
    Optional<SimulatedPayment> findByOrderOrderId(Long orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SimulatedPayment p where p.order.orderId = :orderId")
    Optional<SimulatedPayment> findForUpdate(@Param("orderId") Long orderId);
    @Query("select p.paymentId from SimulatedPayment p where p.status = vn.iotstar.enums.SimulatedPaymentStatus.PENDING and p.expiresAt <= :now")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now);
}
