package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.StockLog;
import vn.iotstar.entity.StockMovementType;

import java.util.List;

@Repository
public interface StockLogRepository extends JpaRepository<StockLog, Long> {

    @Query("select l from StockLog l where l.branch.branchId = :branchId order by l.createdAt desc, l.logId desc")
    List<StockLog> findByBranchBranchIdOrderByCreatedAtDesc(@Param("branchId") Integer branchId);

    @Query("select l from StockLog l where l.variant.variantId = :variantId order by l.createdAt desc, l.logId desc")
    List<StockLog> findByVariantVariantIdOrderByCreatedAtDesc(@Param("variantId") Long variantId);

    @Query("select l from StockLog l where l.branch.branchId = :branchId and l.variant.variantId = :variantId order by l.createdAt desc, l.logId desc")
    List<StockLog> findByBranchBranchIdAndVariantVariantIdOrderByCreatedAtDesc(@Param("branchId") Integer branchId, @Param("variantId") Long variantId);

    Page<StockLog> findByBranchBranchId(Integer branchId, Pageable pageable);

    Page<StockLog> findByMovementType(StockMovementType movementType, Pageable pageable);

    Page<StockLog> findByBranchBranchIdAndMovementType(Integer branchId, StockMovementType movementType, Pageable pageable);
}
