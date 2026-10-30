package vn.iotstar.controller.staff;

import vn.iotstar.exception.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Branch;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.OrderService;

import java.util.List;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final OrderService orderService;
    private final BranchService branchService;
    private final InventoryService inventoryService;

    private Integer getActiveBranchId(HttpSession session, Authentication authentication) {
        if (!branchService.isCurrentAdmin()) {
            session.removeAttribute("staffBranchId");
            return branchService.getStaffBranchId();
        }
        Integer selected = (Integer) session.getAttribute("staffBranchId");
        if (selected != null && branchService.getBranchById(selected)
                .filter(b -> Boolean.TRUE.equals(b.getIsActive())).isPresent()) return selected;
        return branchService.getAllActiveBranches().stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("Chưa có chi nhánh đang hoạt động"))
                .getBranchId();
    }

    @PostMapping("/switch-branch")
    @PreAuthorize("hasRole('ADMIN')")
    public String switchBranch(@RequestParam("branchId") Integer branchId, HttpSession session) {
        if (!branchService.isCurrentAdmin()) {
            throw new AccessDeniedException("Chỉ admin được đổi chi nhánh quản lý");
        }
        branchService.getBranchById(branchId).filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .orElseThrow(() -> new BadRequestException("Chi nhánh không tồn tại hoặc đã ngừng hoạt động"));
        session.setAttribute("staffBranchId", branchId);
        return "redirect:/staff/orders";
    }

    @GetMapping({"", "/dashboard"})
    public String staffDashboard(HttpSession session, Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(session, auth);
        Branch currentBranch = branchService.getBranchById(branchId).orElse(null);

        model.addAttribute("currentBranch", currentBranch);
        model.addAttribute("branches", branchService.isCurrentAdmin() ? branchService.getAllActiveBranches() : List.of());
        model.addAttribute("newOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.NEW));
        model.addAttribute("confirmedOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.CONFIRMED));
        model.addAttribute("shippingOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.SHIPPING));
        model.addAttribute("deliveredOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.DELIVERED));
        model.addAttribute("totalUnits", inventoryService.countTotalStockUnits(branchId));
        model.addAttribute("lowStockCount", inventoryService.countLowStock(branchId, 5));
        model.addAttribute("outOfStockCount", inventoryService.countOutOfStock(branchId));
        model.addAttribute("recentOrders", orderService.getRecentBranchOrders(branchId));

        model.addAttribute("attentionOrders",orderService.getReviewOrders());
        return "staff/dashboard";
    }

    @GetMapping("/orders")
    public String branchOrders(
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            HttpSession session, Authentication auth, Model model) {

        Integer branchId = getActiveBranchId(session, auth);
        Branch currentBranch = branchService.getBranchById(branchId).orElse(null);

        OrderStatus status = null;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr)) {
            try {
                status = OrderStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        Page<Order> orderPage = orderService.getOrdersByBranchAndStatus(
                branchId, status, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        model.addAttribute("currentBranch", currentBranch);
        model.addAttribute("branches", branchService.isCurrentAdmin() ? branchService.getAllActiveBranches() : List.of());
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", statusStr != null ? statusStr.toUpperCase() : "ALL");

        return "staff/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable("id") Long id, HttpSession session, Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(session, auth);
        Order order = orderService.getOrderById(id).orElseThrow();

        model.addAttribute("order", order);
        model.addAttribute("allowedNextStatuses", orderService.allowedNextStatuses(order));
        model.addAttribute("statusChoices",orderService.allowedNextStatuses(order).stream().filter(s->s!=OrderStatus.DELIVERED && s!=OrderStatus.RETURNED && s!=OrderStatus.DELIVERY_FAILED).toList());
        model.addAttribute("deliveryAttempts",orderService.getDeliveryAttempts(id));
        model.addAttribute("returnReceipt",orderService.getReturnReceipt(id));
        model.addAttribute("currentBranch", branchService.getBranchById(branchId).orElse(null));
        model.addAttribute("branches", branchService.isCurrentAdmin() ? branchService.getAllActiveBranches() : List.of());
        return "staff/order-detail";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") OrderStatus newStatus,
            @RequestParam(value = "note", required = false) String note,
            RedirectAttributes redirectAttributes) {

        orderService.updateOrderStatus(id, newStatus, note);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái đơn hàng thành công!");
        return "redirect:/staff/orders/" + id;
    }

    @GetMapping("/inventory")
    public String branchInventory(HttpSession session, Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(session, auth);
        Branch currentBranch = branchService.getBranchById(branchId).orElse(null);

        List<BranchInventory> inventoryList = inventoryService.getInventoryByBranch(branchId);

        model.addAttribute("currentBranch", currentBranch);
        model.addAttribute("branches", branchService.isCurrentAdmin() ? branchService.getAllActiveBranches() : List.of());
        model.addAttribute("inventoryList", inventoryList);
        return "staff/inventory";
    }

    @PostMapping("/inventory/update")
    public String updateInventory(
            @RequestParam("variantId") Long variantId,
            @RequestParam("quantity") Integer quantity,
            HttpSession session, Authentication auth,
            RedirectAttributes redirectAttributes) {

        Integer branchId = getActiveBranchId(session, auth);
        inventoryService.updateStock(branchId, variantId, quantity);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật số lượng tồn kho thành công!");
        return "redirect:/staff/inventory";
    }
}
