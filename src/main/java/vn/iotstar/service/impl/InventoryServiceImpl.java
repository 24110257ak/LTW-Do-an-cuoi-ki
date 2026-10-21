package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.entity.ProductVariant;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BranchInventoryRepository;
import vn.iotstar.repository.BranchRepository;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.service.InventoryService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final BranchInventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository variantRepository;

    @Override
    public List<BranchInventory> getInventoryByBranch(Integer branchId) {
        return inventoryRepository.findByBranchBranchId(branchId);
    }

    @Override
    public Optional<BranchInventory> getInventoryItem(Integer branchId, Long variantId) {
        return inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchId, variantId);
    }

    @Override
    @Transactional
    public void updateStock(Integer branchId, Long variantId, Integer newQuantity) {
        BranchInventory inventory = inventoryRepository
                .findByBranchBranchIdAndVariantVariantId(branchId, variantId)
                .orElseGet(() -> {
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh", "id", branchId));
                    ProductVariant variant = variantRepository.findById(variantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Biến thể", "id", variantId));
                    return BranchInventory.builder()
                            .branch(branch)
                            .variant(variant)
                            .quantity(0)
                            .build();
                });

        inventory.setQuantity(Math.max(0, newQuantity != null ? newQuantity : 0));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);
    }

    @Override
    public List<BranchInventory> getStockDistribution(Long variantId) {
        return inventoryRepository.findByVariantVariantId(variantId);
    }

    @Override
    public long countLowStock(Integer branchId, int threshold) {
        List<BranchInventory> list = inventoryRepository.findByBranchBranchId(branchId);
        return list.stream().filter(i -> i.getQuantity() > 0 && i.getQuantity() <= threshold).count();
    }

    @Override
    public long countOutOfStock(Integer branchId) {
        List<BranchInventory> list = inventoryRepository.findByBranchBranchId(branchId);
        return list.stream().filter(i -> i.getQuantity() == 0).count();
    }

    @Override
    public long countTotalStockUnits(Integer branchId) {
        List<BranchInventory> list = inventoryRepository.findByBranchBranchId(branchId);
        return list.stream().mapToLong(BranchInventory::getQuantity).sum();
    }
}
