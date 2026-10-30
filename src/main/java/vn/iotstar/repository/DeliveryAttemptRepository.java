package vn.iotstar.repository;
import org.springframework.data.jpa.repository.JpaRepository;import vn.iotstar.entity.DeliveryAttempt;import java.util.List;
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt,Long> {List<DeliveryAttempt> findByOrderOrderIdOrderByAttemptIdDesc(Long orderId);}
