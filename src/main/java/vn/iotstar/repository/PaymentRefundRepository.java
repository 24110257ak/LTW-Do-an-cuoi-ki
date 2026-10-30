package vn.iotstar.repository;
import org.springframework.data.jpa.repository.JpaRepository;import vn.iotstar.entity.PaymentRefund;import java.util.Optional;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,Long>{Optional<PaymentRefund> findByOrderOrderId(Long id);}
