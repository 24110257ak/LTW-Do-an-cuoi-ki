package vn.iotstar.repository;
import org.springframework.data.jpa.repository.JpaRepository;import vn.iotstar.entity.CostPriceAudit;import java.util.List;
public interface CostPriceAuditRepository extends JpaRepository<CostPriceAudit,Long> {List<CostPriceAudit> findTop50ByVariantProductProductIdOrderByAuditIdDesc(Long productId);}
