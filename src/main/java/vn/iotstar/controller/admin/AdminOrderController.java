package vn.iotstar.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.OrderService;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final BranchService branchService;

    @GetMapping
    public String listOrders(
            @RequestParam(value = "branchId", required = false) Integer branchId,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            Model model) {

        OrderStatus status = null;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr)) {
            try {
                status = OrderStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception ignored) {}
        }

        Page<Order> orderPage = orderService.searchOrdersAdmin(
                branchId, status, keyword,
                PageRequest.of(page, size, Sort.by("orderId").descending()));

        model.addAttribute("orderPage", orderPage);
        model.addAttribute("branches", branchService.getAllBranches());
        model.addAttribute("selectedBranch", branchId);
        model.addAttribute("selectedStatus", statusStr != null ? statusStr.toUpperCase() : "ALL");
        model.addAttribute("keyword", keyword);

        return "admin/orders/list";
    }

    @GetMapping("/{id}")
    public String orderDetail(@PathVariable("id") Long id, Model model) {
        Order order = orderService.getOrderById(id).orElseThrow();
        model.addAttribute("order", order);
        model.addAttribute("allowedNextStatuses", orderService.allowedNextStatuses(order));
        model.addAttribute("statusChoices",orderService.allowedNextStatuses(order).stream().filter(s->s!=OrderStatus.DELIVERED && s!=OrderStatus.RETURNED && s!=OrderStatus.DELIVERY_FAILED).toList());
        model.addAttribute("deliveryAttempts",orderService.getDeliveryAttempts(id));
        model.addAttribute("returnReceipt",orderService.getReturnReceipt(id));
        return "admin/orders/detail";
    }

    @PostMapping("/{id}/status")
    public String updateOrderStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") OrderStatus newStatus,
            @RequestParam(value = "note", required = false) String note,
            RedirectAttributes redirectAttributes) {

        try { orderService.updateOrderStatus(id, newStatus, note); }
        catch (vn.iotstar.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",ex.getMessage());
            return "redirect:/admin/orders/" + id;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái đơn hàng thành công!");
        return "redirect:/admin/orders/" + id;
    }
}
