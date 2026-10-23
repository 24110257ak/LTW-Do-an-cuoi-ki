package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.StockLog;
import vn.iotstar.entity.StockMovementType;

import java.util.List;

@Repository
public interface StockLogRepository extends JpaRepository<StockLog, Long> {

    List<StockLog> findByBranchBranchIdOrderByCreatedAtDesc(Integer branchId);

    List<StockLog> findByVariantVariantIdOrderByCreatedAtDesc(Long variantId);

    List<StockLog> findByBranchBranchIdAndVariantVariantIdOrderByCreatedAtDesc(Integer branchId, Long variantId);

    Page<StockLog> findByBranchBranchIdOrderByCreatedAtDesc(Integer branchId, Pageable pageable);

    Page<StockLog> findByMovementTypeOrderByCreatedAtDesc(StockMovementType movementType, Pageable pageable);

    Page<StockLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
