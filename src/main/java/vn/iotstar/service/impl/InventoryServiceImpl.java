package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BranchInventoryRepository;
import vn.iotstar.repository.BranchRepository;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.repository.StockLogRepository;
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
    private final StockLogRepository stockLogRepository;

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

        int finalQuantity = Math.max(0, newQuantity != null ? newQuantity : 0);
        int delta = finalQuantity - inventory.getQuantity();
        inventory.setQuantity(finalQuantity);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        if (delta != 0) {
            StockLog log = StockLog.builder()
                    .branch(inventory.getBranch())
                    .variant(inventory.getVariant())
                    .movementType(StockMovementType.ADJUSTMENT)
                    .quantityChange(delta)
                    .quantityAfter(finalQuantity)
                    .note("Cập nhật số lượng trực tiếp")
                    .build();
            stockLogRepository.save(log);
        }
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

    @Override
    @Transactional
    public void adjustStock(Integer branchId, Long variantId, int delta, StockMovementType movementType,
                            User actor, String referenceCode, String note) {
        if (delta == 0) {
            throw new IllegalArgumentException("Số lượng biến động không được bằng 0");
        }

        // Lấy và khóa dòng tồn kho bằng PESSIMISTIC_WRITE để chống race condition
        BranchInventory inventory = inventoryRepository
                .findByBranchAndVariantForUpdate(branchId, variantId)
                .orElseGet(() -> {
                    if (delta < 0) {
                        throw new IllegalStateException("Kho chi nhánh chưa có sản phẩm này để xuất");
                    }
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh", "id", branchId));
                    ProductVariant variant = variantRepository.findById(variantId)
                            .orElseThrow(() -> new ResourceNotFoundException("Biến thể", "id", variantId));
                    BranchInventory created = BranchInventory.builder()
                            .branch(branch)
                            .variant(variant)
                            .quantity(0)
                            .build();
                    return inventoryRepository.saveAndFlush(created);
                });

        long newQuantity = (long) inventory.getQuantity() + delta;
        if (newQuantity < 0) {
            String variantName = inventory.getVariant() != null && inventory.getVariant().getProduct() != null
                    ? inventory.getVariant().getProduct().getProductName()
                    : ("Mã biến thể " + variantId);
            throw new IllegalStateException(String.format("Sản phẩm %s còn %d, không đủ số lượng yêu cầu (%d)",
                    variantName, inventory.getQuantity(), Math.abs(delta)));
        }

        if (newQuantity > Integer.MAX_VALUE) {
            throw new IllegalStateException("Số lượng tồn kho vượt quá giới hạn tối đa");
        }

        inventory.setQuantity((int) newQuantity);
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        // Ghi nhật ký biến động kho (Stock Audit Log)
        StockLog log = StockLog.builder()
                .branch(inventory.getBranch())
                .variant(inventory.getVariant())
                .movementType(movementType)
                .quantityChange(delta)
                .quantityAfter((int) newQuantity)
                .performedBy(actor)
                .referenceCode(referenceCode)
                .note(note)
                .build();
        stockLogRepository.save(log);
    }

    @Override
    @Transactional
    public void adminAdjustStock(Integer branchId, Long variantId, int delta, String reason, User actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Vui lòng ghi rõ lý do kiểm kê / điều chỉnh tồn kho");
        }
        adjustStock(branchId, variantId, delta, StockMovementType.ADJUSTMENT, actor, null, reason.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableQuantity(Integer branchId, Long variantId) {
        return inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchId, variantId)
                .map(BranchInventory::getQuantity)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLog> getStockLogsByBranch(Integer branchId) {
        return stockLogRepository.findByBranchBranchIdOrderByCreatedAtDesc(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLog> getStockLogsByVariant(Long variantId) {
        return stockLogRepository.findByVariantVariantIdOrderByCreatedAtDesc(variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockLog> getStockLogsPaged(Integer branchId, StockMovementType movementType, Pageable pageable) {
        if (branchId != null) {
            return stockLogRepository.findByBranchBranchIdOrderByCreatedAtDesc(branchId, pageable);
        }
        if (movementType != null) {
            return stockLogRepository.findByMovementTypeOrderByCreatedAtDesc(movementType, pageable);
        }
        return stockLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
