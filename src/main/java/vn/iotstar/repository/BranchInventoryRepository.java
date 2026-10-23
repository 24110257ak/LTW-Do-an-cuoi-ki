package vn.iotstar.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.BranchInventory;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventory, Long> {
    List<BranchInventory> findByBranchBranchId(Integer branchId);
    List<BranchInventory> findByVariantVariantId(Long variantId);
    Optional<BranchInventory> findByBranchBranchIdAndVariantVariantId(Integer branchId, Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bi FROM BranchInventory bi WHERE bi.branch.branchId = :branchId AND bi.variant.variantId = :variantId")
    Optional<BranchInventory> findByBranchAndVariantForUpdate(@Param("branchId") Integer branchId, @Param("variantId") Long variantId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.variant.product.productId = :productId AND bi.branch.branchId = :branchId")
    List<BranchInventory> findByProductAndBranch(@Param("productId") Long productId, @Param("branchId") Integer branchId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.variant.product.productId = :productId AND bi.quantity > 0")
    List<BranchInventory> findAvailableBranchesForProduct(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(bi.quantity), 0) FROM BranchInventory bi WHERE bi.variant.variantId = :variantId")
    Integer sumQuantityByVariantId(@Param("variantId") Long variantId);

    @Query("SELECT bi FROM BranchInventory bi WHERE bi.quantity <= bi.safetyStock")
    List<BranchInventory> findLowStockAlerts();

    List<BranchInventory> findByBranchBranchIdAndQuantityLessThanEqual(Integer branchId, Integer threshold);
    List<BranchInventory> findByQuantityLessThanEqual(Integer threshold);
    List<BranchInventory> findByVariantVariantIdAndQuantityGreaterThanOrderByQuantityDesc(Long variantId, Integer threshold);
}
