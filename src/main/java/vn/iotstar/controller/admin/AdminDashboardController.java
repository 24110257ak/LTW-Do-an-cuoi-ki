package vn.iotstar.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.iotstar.entity.Branch;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.OrderService;
import vn.iotstar.service.ProductService;
import vn.iotstar.service.UserService;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final OrderService orderService;
    private final vn.iotstar.service.FinancialReportService financialReports;
    private final ProductService productService;
    private final UserService userService;
    private final BranchService branchService;

    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model,
            @RequestParam(value = "fromMonth", defaultValue = "") String fromMonth,
            @RequestParam(value = "toMonth", defaultValue = "") String toMonth,
            @RequestParam(value = "allTime", defaultValue = "false") boolean allTime) {
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
        Map<OrderStatus, Long> orderStatusCounts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            orderStatusCounts.put(status, orderService.countOrdersByStatus(status));
        }
        model.addAttribute("newOrdersCount", orderStatusCounts.get(OrderStatus.NEW));
        model.addAttribute("confirmedOrdersCount", orderStatusCounts.get(OrderStatus.CONFIRMED));
        model.addAttribute("shippingOrdersCount", orderStatusCounts.get(OrderStatus.SHIPPING));
        model.addAttribute("deliveredOrdersCount", orderStatusCounts.get(OrderStatus.DELIVERED));
        model.addAttribute("cancelledOrdersCount", orderStatusCounts.get(OrderStatus.CANCELLED));

        // Chỉ lọc biểu đồ tròn theo ngày tạo đơn; các KPI giữ thống kê toàn hệ thống.
        if (allTime) {
            fromMonth = "";
            toMonth = "";
        } else if (fromMonth.isBlank() && toMonth.isBlank()) {
            // Mở dashboard lần đầu: tự chọn tháng hiện tại theo giờ Việt Nam.
            String currentMonth = YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
            fromMonth = currentMonth;
            toMonth = currentMonth;
        }
        model.addAttribute("fromMonth", fromMonth);
        model.addAttribute("toMonth", toMonth);
        model.addAttribute("statusPeriodCaption", "Tất cả thời gian");
        if (!fromMonth.isBlank() || !toMonth.isBlank()) {
            try {
                if (!fromMonth.matches("\\d{4}-\\d{2}") || !toMonth.matches("\\d{4}-\\d{2}")) {
                    throw new DateTimeException("Invalid month format");
                }
                YearMonth from = YearMonth.parse(fromMonth);
                YearMonth to = YearMonth.parse(toMonth);
                if (from.getYear() < 1 || to.getYear() > 9998 || from.isAfter(to)) {
                    throw new DateTimeException("Invalid month range");
                }
                orderStatusCounts = orderService.getOrderStatusCounts(
                        from.atDay(1).atStartOfDay(), to.plusMonths(1).atDay(1).atStartOfDay());
                var monthFormat = DateTimeFormatter.ofPattern("MM/yyyy");
                model.addAttribute("statusPeriodCaption", from.equals(to) ? "Tháng " + from.format(monthFormat)
                        : from.format(monthFormat) + " – " + to.format(monthFormat));
            } catch (DateTimeException ex) {
                model.addAttribute("statusFilterError", "Chọn đủ hai tháng hợp lệ; tháng bắt đầu phải trước hoặc bằng tháng kết thúc. Đang hiển thị tất cả thời gian.");
            }
        }
        model.addAttribute("orderStatusCounts", orderStatusCounts);

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

        model.addAttribute("financialSummary", financialReports.getFinancialOverview());
        var today = java.time.LocalDate.now();
        model.addAttribute("dailyRevenue", financialReports.getCompletedDays(today.minusDays(29), today));
        model.addAttribute("attentionOrders",orderService.getReviewOrders());
        return "admin/dashboard";
    }
}
