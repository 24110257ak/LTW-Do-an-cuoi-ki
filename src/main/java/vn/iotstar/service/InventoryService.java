package vn.iotstar.service;

import vn.iotstar.entity.BranchInventory;

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
}
