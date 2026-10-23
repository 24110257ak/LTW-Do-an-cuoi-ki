package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.entity.StockLog;
import vn.iotstar.entity.StockMovementType;
import vn.iotstar.entity.User;

import java.util.List;
import java.util.Optional;

public interface InventoryService {
    List<BranchInventory> getInventoryByBranch(Integer branchId);
    Optional<BranchInventory> getInventoryItem(Integer branchId, Long variantId);
    void updateStock(Integer branchId, Long variantId, Integer newQuantity);
    List<BranchInventory> getStockDistribution(Long variantId);
    long countLowStock(Integer branchId, int threshold);
    long countOutOfStock(Integer branchId);
    long countTotalStockUnits(Integer branchId);

    // ---- Các phương thức nâng cao phục vụ kiểm toán kho & khóa bi quan ----
    void adjustStock(Integer branchId, Long variantId, int delta, StockMovementType movementType, User actor, String referenceCode, String note);
    void adminAdjustStock(Integer branchId, Long variantId, int delta, String reason, User actor);
    int getAvailableQuantity(Integer branchId, Long variantId);
    List<StockLog> getStockLogsByBranch(Integer branchId);
    List<StockLog> getStockLogsByVariant(Long variantId);
    Page<StockLog> getStockLogsPaged(Integer branchId, StockMovementType movementType, Pageable pageable);
}
