package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.iotstar.entity.Branch;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.OrderService;
import vn.iotstar.service.ProductService;
import vn.iotstar.service.UserService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final BranchService branchService;

    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        // Thống kê tổng quan
        BigDecimal totalRevenue = orderService.getTotalRevenue();
        long totalOrders = orderService.countTotalOrders();
        long totalProducts = productService.countActiveProducts();
        long totalUsers = userService.countUsers();

        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("totalUsers", totalUsers);

        // Thống kê theo trạng thái đơn hàng
        model.addAttribute("newOrdersCount", orderService.countOrdersByStatus(OrderStatus.NEW));
        model.addAttribute("confirmedOrdersCount", orderService.countOrdersByStatus(OrderStatus.CONFIRMED));
        model.addAttribute("shippingOrdersCount", orderService.countOrdersByStatus(OrderStatus.SHIPPING));
        model.addAttribute("deliveredOrdersCount", orderService.countOrdersByStatus(OrderStatus.DELIVERED));
        model.addAttribute("cancelledOrdersCount", orderService.countOrdersByStatus(OrderStatus.CANCELLED));

        // Thống kê doanh thu theo từng chi nhánh
        List<Branch> branches = branchService.getAllBranches();
        List<String> branchNames = new ArrayList<>();
        List<BigDecimal> branchRevenues = new ArrayList<>();
        for (Branch b : branches) {
            branchNames.add(b.getBranchName());
            BigDecimal rev = orderService.getBranchRevenue(b.getBranchId());
            branchRevenues.add(rev != null ? rev : BigDecimal.ZERO);
        }
        model.addAttribute("branchNames", branchNames);
        model.addAttribute("branchRevenues", branchRevenues);

        // Danh sách đơn hàng gần đây và sản phẩm bán chạy nhất
        model.addAttribute("recentOrders", orderService.getRecentOrders());
        model.addAttribute("bestSellers", productService.getBestSellerProducts());

        return "admin/dashboard";
    }
}
