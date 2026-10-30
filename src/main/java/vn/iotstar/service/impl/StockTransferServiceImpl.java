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
    private final ProductRepository productRepository;
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
        StockTransfer transfer = transferRepository.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));

        if (transfer.getStatus() == TransferStatus.APPROVED) return;
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new IllegalStateException("Chỉ phiếu ở trạng thái 'Chờ duyệt' mới có thể duyệt");
        }

        // Sắp xếp items theo variantId tăng dần để triệt tiêu Deadlock khi nhiều lệnh chuyển kho diễn ra cùng lúc
        List<StockTransferItem> sortedItems = transfer.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                .toList();

        lockProducts(transfer);

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
        StockTransfer transfer = transferRepository.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));

        if (transfer.getStatus() == TransferStatus.IN_TRANSIT) return;
        if (transfer.getStatus() != TransferStatus.APPROVED) {
            throw new IllegalStateException("Phiếu chuyển kho chưa được duyệt xuất kho");
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transferRepository.save(transfer);
    }

    @Override
    @Transactional
    public void completeTransfer(Long transferId, User actor) {
        StockTransfer transfer = transferRepository.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));

        if (transfer.getStatus() == TransferStatus.COMPLETED) return;
        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("Hàng chưa xuất hoặc phiếu đã kết thúc");
        }

        // Sắp xếp items theo variantId tăng dần
        List<StockTransferItem> sortedItems = transfer.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                .toList();

        lockProducts(transfer);

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
        StockTransfer transfer = transferRepository.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));

        if (Set.of(TransferStatus.CANCELLED, TransferStatus.RETURNING, TransferStatus.RETURNED).contains(transfer.getStatus())) return;
        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new IllegalStateException("Không thể hủy phiếu đã hoàn tất hoặc đã bị hủy");
        }

        // Hàng chưa rời kho mới được cộng lại ngay; hàng trên xe phải xác nhận nhận hoàn.
        if (transfer.getStatus() == TransferStatus.APPROVED) {
            List<StockTransferItem> sortedItems = transfer.getItems().stream()
                    .sorted(Comparator.comparing(item -> item.getVariant().getVariantId()))
                    .toList();

            lockProducts(transfer);

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

        transfer.setStatus(transfer.getStatus() == TransferStatus.IN_TRANSIT ? TransferStatus.RETURNING : TransferStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            transfer.setNote(transfer.getNote() != null ? transfer.getNote() + " | Lý do hủy: " + reason : "Lý do hủy: " + reason);
        }
        transferRepository.save(transfer);
    }

    @Override
    @Transactional
    public void confirmReturnedTransfer(Long transferId, Map<Long, Integer> received, User actor, String note) {
        StockTransfer transfer = transferRepository.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu chuyển kho", "id", transferId));
        if (transfer.getStatus() == TransferStatus.RETURNED) return;
        if (transfer.getStatus() != TransferStatus.RETURNING || transfer.getReturnedAt() != null)
            throw new IllegalStateException("Chỉ được nhận hoàn phiếu đang chuyển hoàn về kho nguồn");
        var items = transfer.getItems().stream().sorted(Comparator.comparing(i -> i.getVariant().getVariantId())).toList();
        if (received == null || received.size() != items.size()) throw new IllegalArgumentException("Phải kiểm đếm tất cả mặt hàng trong phiếu");
        for (var item : items) {
            Integer qty = received.get(item.getVariant().getVariantId());
            if (qty == null || qty < 0 || qty > item.getQuantity()) throw new IllegalArgumentException("Số lượng thực nhận không hợp lệ");
            if (qty < item.getQuantity() && (note == null || note.isBlank()))
                throw new IllegalArgumentException("Cần ghi chú chênh lệch khi số lượng nhận hoàn thiếu");
        }
        lockProducts(transfer);

        for (var item : items) {
            int qty = received.get(item.getVariant().getVariantId());
            if (qty > 0) inventoryService.adjustStock(transfer.getSourceBranch().getBranchId(), item.getVariant().getVariantId(), qty,
                    StockMovementType.TRANSFER_IN, actor, transfer.getTransferCode(), "Nhận hoàn thực tế " + qty + "/" + item.getQuantity() + ": " + (note == null ? "" : note));
            item.setReturnedQuantity(qty);
        }
        transfer.setReturnedAt(LocalDateTime.now());
        transfer.setStatus(TransferStatus.RETURNED);
        if (note != null && !note.isBlank()) transfer.setNote((transfer.getNote() == null ? "" : transfer.getNote() + " | ") + "Nhận hoàn: " + note);
        transferRepository.save(transfer);
    }

    private void lockProducts(StockTransfer transfer) {
        transfer.getItems().stream().map(i -> i.getVariant().getProduct().getProductId()).distinct().sorted()
                .forEach(id -> productRepository.findForCheckout(id).orElseThrow());
    }
}

