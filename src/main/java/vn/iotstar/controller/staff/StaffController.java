package vn.iotstar.controller.staff;

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
import vn.iotstar.entity.User;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.OrderService;
import vn.iotstar.service.UserService;

import java.util.List;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final OrderService orderService;
    private final BranchService branchService;
    private final InventoryService inventoryService;
    private final UserService userService; // Đã thêm UserService để query Database

    // HÀM MỚI: Bắt buộc lấy ID chi nhánh gốc từ Database của nhân viên
    private Integer getActiveBranchId(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản nhân viên!"));

        // Nếu tài khoản được admin gắn đúng branch_id, lấy chính xác ID đó
        if (user.getBranch() != null) {
            return user.getBranch().getBranchId();
        }
        
        // Fallback an toàn (tránh sập web) nếu admin quên gắn chi nhánh
        return 1; 
    }

    // VÔ HIỆU HÓA HOÀN TOÀN TÍNH NĂNG CHUYỂN CHI NHÁNH
    @PostMapping("/switch-branch")
    public String switchBranch(@RequestParam("branchId") Integer branchId,
                               RedirectAttributes redirectAttributes,
                               @RequestHeader(value = "referer", required = false) String referer) {
        // Cố tình bắn request lên cũng không cho đổi, đá ngược về trang cũ
        redirectAttributes.addFlashAttribute("errorMessage", "Cảnh báo bảo mật: Bạn không có quyền xem dữ liệu của chi nhánh khác!");
        return "redirect:" + (referer != null ? referer : "/staff/orders");
    }

    @GetMapping({"", "/dashboard"})
    public String staffDashboard(Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(auth); // Gọi hàm mới an toàn
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
            Authentication auth, Model model) {

        Integer branchId = getActiveBranchId(auth);
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
    public String orderDetail(@PathVariable("id") Long id, Authentication auth, Model model) {
        Order order = orderService.getOrderById(id).orElseThrow();
        Integer branchId = getActiveBranchId(auth);

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
    public String branchInventory(Authentication auth, Model model) {
        Integer branchId = getActiveBranchId(auth);
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
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        Integer branchId = getActiveBranchId(auth);
        inventoryService.updateStock(branchId, variantId, quantity);

        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật số lượng tồn kho thành công!");
        return "redirect:/staff/inventory";
    }
}