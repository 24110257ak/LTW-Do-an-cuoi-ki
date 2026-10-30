package vn.iotstar.repository;
import org.springframework.data.jpa.repository.JpaRepository;import vn.iotstar.entity.OrderReturnReceipt;import java.util.Optional;
public interface OrderReturnReceiptRepository extends JpaRepository<OrderReturnReceipt,Long> {Optional<OrderReturnReceipt> findByOrderOrderId(Long orderId);}
