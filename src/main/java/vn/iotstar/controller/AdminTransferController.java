package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.*;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.StockTransferService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin/transfers")
@RequiredArgsConstructor
public class AdminTransferController {

    private final StockTransferService stockTransferService;
    private final BranchService branchService;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String listTransfers(@RequestParam(value = "branchId", required = false) Integer branchId,
                                @RequestParam(value = "status", required = false) TransferStatus status,
                                Model model) {
        List<StockTransfer> transfers;
        if (branchId != null) {
            transfers = stockTransferService.getTransfersByBranch(branchId);
        } else if (status != null) {
            transfers = stockTransferService.getTransfersByStatus(status);
        } else {
            transfers = stockTransferService.getAllTransfers();
        }

        model.addAttribute("transfers", transfers);
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("selectedBranchId", branchId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("allStatuses", TransferStatus.values());
        return "admin/transfers/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("variants", variantRepository.findAll());
        return "admin/transfers/form";
    }

    @PostMapping("/create")
    public String createTransfer(@RequestParam("sourceBranchId") Integer sourceBranchId,
                                 @RequestParam("destinationBranchId") Integer destinationBranchId,
                                 @RequestParam("variantId") Long variantId,
                                 @RequestParam("quantity") Integer quantity,
                                 @RequestParam(value = "note", required = false) String note,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;

            Map<Long, Integer> items = new HashMap<>();
            items.put(variantId, quantity);

            StockTransfer transfer = stockTransferService.createTransfer(sourceBranchId, destinationBranchId, items, actor, note);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo phiếu điều chuyển #" + transfer.getTransferCode() + " thành công!");
            return "redirect:/admin/transfers";
        } catch (Exception e) {
            log.error("Lỗi khi tạo phiếu điều chuyển: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/transfers/create";
        }
    }

    @PostMapping("/{id}/approve")
    public String approveTransfer(@PathVariable("id") Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;
            stockTransferService.approveTransfer(id, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Duyệt xuất kho phiếu điều chuyển thành công (đã trừ kho nguồn)!");
        } catch (Exception e) {
            log.error("Lỗi khi duyệt phiếu chuyển kho #{}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }

    @PostMapping("/{id}/ship")
    public String shipTransfer(@PathVariable("id") Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;
            stockTransferService.shipTransfer(id, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đang vận chuyển thành công!");
        } catch (Exception e) {
            log.error("Lỗi khi chuyển trạng thái vận chuyển #{}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }

    @PostMapping("/{id}/complete")
    public String completeTransfer(@PathVariable("id") Long id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;
            stockTransferService.completeTransfer(id, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Xác nhận nhận hàng thành công (đã cộng kho chi nhánh đích)!");
        } catch (Exception e) {
            log.error("Lỗi khi hoàn tất phiếu điều chuyển #{}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }

    @PostMapping("/{id}/cancel")
    public String cancelTransfer(@PathVariable("id") Long id,
                                 @RequestParam(value = "reason", required = false) String reason,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;
            stockTransferService.cancelTransfer(id, actor, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Hủy phiếu điều chuyển thành công (nếu đã trừ kho nguồn thì đã được hoàn lại)!");
        } catch (Exception e) {
            log.error("Lỗi khi hủy phiếu điều chuyển #{}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/transfers";
    }
}
