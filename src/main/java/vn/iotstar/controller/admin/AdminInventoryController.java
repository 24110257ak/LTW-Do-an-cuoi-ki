package vn.iotstar.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.StockLog;
import vn.iotstar.entity.StockMovementType;
import vn.iotstar.entity.User;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.InventoryService;

@Slf4j
@Controller
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;
    private final BranchService branchService;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @GetMapping("/logs")
    public String viewLogs(@RequestParam(value = "branchId", required = false) Integer branchId,
                           @RequestParam(value = "movementType", required = false) StockMovementType movementType,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "size", defaultValue = "20") int size,
                           Model model) {
        Page<StockLog> logs = inventoryService.getStockLogsPaged(
                branchId,
                movementType,
                PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        model.addAttribute("logsPage", logs);
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("variants", variantRepository.findAll());
        model.addAttribute("selectedBranchId", branchId);
        model.addAttribute("selectedType", movementType);
        model.addAttribute("allTypes", StockMovementType.values());
        return "admin/inventory/logs";
    }

    @PostMapping("/adjust")
    public String adjustStock(@RequestParam("branchId") Integer branchId,
                              @RequestParam("variantId") Long variantId,
                              @RequestParam("delta") Integer delta,
                              @RequestParam("reason") String reason,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            User actor = authentication != null
                    ? userRepository.findByUsername(authentication.getName()).orElse(null)
                    : null;

            inventoryService.adminAdjustStock(branchId, variantId, delta, reason, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Điều chỉnh tồn kho thành công và đã ghi sổ nhật ký!");
        } catch (Exception e) {
            log.error("Lỗi khi điều chỉnh tồn kho: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/inventory/logs";
    }
}

