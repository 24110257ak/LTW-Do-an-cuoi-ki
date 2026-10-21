package vn.iotstar.controller;

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
        Integer branchId = (Integer) session.getAttribute("staffBranchId");
        if (branchId == null) {
            String username = authentication != null ? authentication.getName().toLowerCase() : "";
            if (username.contains("q1")) branchId = 1;
            else if (username.contains("td")) branchId = 3;
            else if (username.contains("hn")) branchId = 2;
            else if (username.contains("dn")) branchId = 4;
            else branchId = 1;
            session.setAttribute("staffBranchId", branchId);
        }
        return branchId;
    }

    @PostMapping("/switch-branch")
    public String switchBranch(@RequestParam("branchId") Integer branchId,
                               HttpSession session,
                               @RequestHeader(value = "referer", required = false) String referer) {
        session.setAttribute("staffBranchId", branchId);
        return "redirect:" + (referer != null ? referer : "/staff/orders");
    }

    @GetMapping({"", "/dashboard"})
    public String staffDashboard(HttpSession session, Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(session, auth);
        Branch currentBranch = branchService.getBranchById(branchId).orElse(null);

        model.addAttribute("currentBranch", currentBranch);
        model.addAttribute("branches", branchService.getAllActiveBranches());
        model.addAttribute("newOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.NEW));
        model.addAttribute("confirmedOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.CONFIRMED));
        model.addAttribute("shippingOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.SHIPPING));
        model.addAttribute("deliveredOrdersCount", orderService.countBranchOrdersByStatus(branchId, OrderStatus.DELIVERED));
        model.addAttribute("totalUnits", inventoryService.countTotalStockUnits(branchId));
        model.addAttribute("lowStockCount", inventoryService.countLowStock(branchId, 5));
        model.addAttribute("outOfStockCount", inventoryService.countOutOfStock(branchId));
        model.addAttribute("recentOrders", orderService.getRecentBranchOrders(branchId));

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
        model.addAttribute("branches", branchService.getAllActiveBranches());
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", statusStr != null ? statusStr.toUpperCase() : "ALL");

        return "staff/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable("id") Long id, HttpSession session, Authentication auth, Model model) {
        Order order = orderService.getOrderById(id).orElseThrow();
        Integer branchId = getActiveBranchId(session, auth);

        model.addAttribute("order", order);
        model.addAttribute("currentBranch", branchService.getBranchById(branchId).orElse(null));
        model.addAttribute("branches", branchService.getAllActiveBranches());
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
        model.addAttribute("branches", branchService.getAllActiveBranches());
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
