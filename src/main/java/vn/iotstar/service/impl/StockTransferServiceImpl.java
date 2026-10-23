package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.*;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.StockTransferService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transferRepository;
    private final StockTransferItemRepository transferItemRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public StockTransfer createTransfer(Integer sourceBranchId, Integer destinationBranchId,
                                         Map<Long, Integer> variantQuantities, User actor, String note) {
        if (sourceBranchId.equals(destinationBranchId)) {
            throw new IllegalArgumentException("Chi nhánh nguồn và chi nhánh đích phải khác nhau");
        }
        if (variantQuantities == null || variantQuantities.isEmpty()) {
            throw new IllegalArgumentException("Phiếu chuyển kho phải có ít nhất một sản phẩm");
        }

        Branch sourceBranch = branchRepository.findById(sourceBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh nguồn", "id", sourceBranchId));
        Branch destinationBranch = branchRepository.findById(destinationBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Chi nhánh đích", "id", destinationBranchId));

        String code = "TRF-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        StockTransfer transfer = StockTransfer.builder()
                .transferCode(code)
                .sourceBranch(sourceBranch)
                .destinationBranch(destinationBranch)
                .status(TransferStatus.PENDING)
                .createdBy(actor)
                .note(note)
                .items(new ArrayList<>())
                .build();

        for (Map.Entry<Long, Integer> entry : variantQuantities.entrySet()) {
            Long variantId = entry.getKey();
            Integer qty = entry.getValue();

            if (qty == null || qty <= 0) {
                throw new IllegalArgumentException("Số lượng chuyển kho phải lớn hơn 0");
            }

            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Biến thể", "id", variantId));

            // Kiểm tra sơ bộ tồn kho hiện có tại chi nhánh nguồn
            int available = inventoryService.getAvailableQuantity(sourceBranchId, variantId);
            if (available < qty) {
                String name = variant.getProduct() != null ? variant.getProduct().getProductName() : ("Mã " + variantId);
                throw new IllegalStateException(String.format("Chi nhánh nguồn không đủ hàng: %s còn %d, yêu cầu %d",
                        name, available, qty));
            }

            StockTransferItem item = StockTransferItem.builder()
                    .transfer(transfer)
                    .variant(variant)
                    .quantity(qty)
                    .build();
            transfer.getItems().add(item);
        }

        return transferRepository.save(transfer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByBranch(Integer branchId) {
        return transferRepository.findBySourceBranchBranchIdOrDestinationBranchBranchIdOrderByCreatedAtDesc(branchId, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getAllTransfers() {
        return transferRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockTransfer> getTransfersByStatus(TransferStatus status) {
        return transferRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransfer getTransferById(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));
    }

    @Override
    @Transactional
    public void approveTransfer(Long transferId, User actor) {
        StockTransfer transfer = getTransferById(transferId);

        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Chỉ phiếu ở trạng thái 'Chờ duyệt' mới có thể duyệt");
        }

        // Sắp xếp items theo variantId tăng dần để triệt tiêu Deadlock khi nhiều lệnh chuyển kho diễn ra cùng lúc
        List<StockTransferItem> sortedItems = transfer.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                .toList();

        // Khóa và trừ tồn kho tại chi nhánh nguồn
        for (StockTransferItem item : sortedItems) {
            inventoryService.adjustStock(
                    transfer.getSourceBranch().getBranchId(),
                    item.getVariant().getVariantId(),
                    -item.getQuantity(),
                    StockMovementType.TRANSFER_OUT,
                    actor,
                    transfer.getTransferCode(),
                    "Duyệt phiếu xuất chuyển kho"
            );
        }

        transfer.setStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(actor);
        transferRepository.save(transfer);
    }

    @Override
    @Transactional
    public void shipTransfer(Long transferId, User actor) {
        StockTransfer transfer = getTransferById(transferId);

        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Phiếu chuyển kho chưa được duyệt xuất kho");
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transferRepository.save(transfer);
    }

    @Override
    @Transactional
    public void completeTransfer(Long transferId, User actor) {
        StockTransfer transfer = getTransferById(transferId);

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT && transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Hàng chưa xuất hoặc phiếu đã kết thúc");
        }

        // Sắp xếp items theo variantId tăng dần
        List<StockTransferItem> sortedItems = transfer.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                .toList();

        // Cộng tồn kho tại chi nhánh đích
        for (StockTransferItem item : sortedItems) {
            inventoryService.adjustStock(
                    transfer.getDestinationBranch().getBranchId(),
                    item.getVariant().getVariantId(),
                    item.getQuantity(),
                    StockMovementType.TRANSFER_IN,
                    actor,
                    transfer.getTransferCode(),
                    "Nhập kho nhận hàng chuyển chi nhánh"
            );
        }

        transfer.setStatus(TransferStatus.COMPLETED);
        transferRepository.save(transfer);
    }

    @Override
    @Transactional
    public void cancelTransfer(Long transferId, User actor, String reason) {
        StockTransfer transfer = getTransferById(transferId);

        if (transfer.getStatus() == TransferStatus.COMPLETED || transfer.getStatus() == TransferStatus.CANCELLED) {
            throw new IllegalStateException("Không thể hủy phiếu đã hoàn tất hoặc đã bị hủy");
        }

        // Nếu phiếu đã duyệt hoặc đang giao (đã trừ kho nguồn), cần hoàn trả lại kho nguồn
        if (transfer.getStatus() == TransferStatus.APPROVED || transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            List<StockTransferItem> sortedItems = transfer.getItems().stream()
                    .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                    .toList();

            for (StockTransferItem item : sortedItems) {
                inventoryService.adjustStock(
                        transfer.getSourceBranch().getBranchId(),
                        item.getVariant().getVariantId(),
                        item.getQuantity(),
                        StockMovementType.TRANSFER_IN,
                        actor,
                        transfer.getTransferCode(),
                        "Hoàn kho nguồn do hủy phiếu chuyển: " + (reason != null ? reason : "")
                );
            }
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            transfer.setNote(transfer.getNote() != null ? transfer.getNote() + " | Lý do hủy: " + reason : "Lý do hủy: " + reason);
        }
        transferRepository.save(transfer);
    }
}
