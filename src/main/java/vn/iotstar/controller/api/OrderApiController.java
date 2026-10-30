package vn.iotstar.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.dto.response.ApiResponse;
import vn.iotstar.entity.Order;
import vn.iotstar.entity.User;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.service.OrderService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Đơn hàng & Logistics (Order & Logistics API)", description = "Các API đặt hàng, tra cứu lịch sử, xác nhận giao hàng và đối soát tiền COD")
public class OrderApiController {

    private final OrderService orderService;
    private final vn.iotstar.service.PaymentSimulationService simulator;

    @PostMapping("/checkout")
    @Operation(summary = "Tạo đơn hàng mới (Checkout)", description = "Áp dụng định tuyến thông minh (Smart Routing), tính giá vốn COGS và chống đặt trùng")
    public ResponseEntity<ApiResponse<Order>> checkout(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CheckoutRequest request
    ) {
        if (currentUser == null) {
            throw new BadRequestException("Vui lòng đăng nhập trước khi đặt hàng!");
        }
        log.info("API checkout from user: {}", currentUser.getUsername());
        Order order = orderService.placeOrder(currentUser.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đặt hàng thành công", order));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Lấy lịch sử đơn hàng của người dùng hiện tại", description = "Phân trang theo thời gian tạo mới nhất")
    public ResponseEntity<ApiResponse<Page<Order>>> getMyOrders(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (currentUser == null) {
            throw new BadRequestException("Vui lòng đăng nhập!");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = orderService.getOrdersByUser(currentUser.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử đơn hàng thành công", orders));
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Xem chi tiết đơn hàng qua mã đơn", description = "Người dùng tra cứu đơn hàng cá nhân hoặc quản trị viên tra cứu")
    public ResponseEntity<ApiResponse<Order>> getOrderByCode(
            @AuthenticationPrincipal User currentUser,
            @PathVariable String orderCode
    ) {
        Order order = orderService.getOrderByCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "code", orderCode));

        // Kiểm tra quyền truy cập: chỉ chủ đơn hoặc STAFF/ADMIN
        boolean isOwner = currentUser != null && order.getUser().getUserId().equals(currentUser.getUserId());
        boolean isStaffOrAdmin = currentUser != null && currentUser.getRole() != null &&
                ("ADMIN".equals(currentUser.getRole().getRoleName()) || "STAFF".equals(currentUser.getRole().getRoleName()));

        if (!isOwner && !isStaffOrAdmin) {
            throw new BadRequestException("Bạn không có quyền xem thông tin đơn hàng này!");
        }

        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin đơn hàng thành công", order));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Khách hàng hủy đơn", description = "Chỉ được hủy khi trạng thái còn là Chờ xác nhận hoặc Đã xác nhận")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "Khách hàng yêu cầu hủy qua API") String reason
    ) {
        if (currentUser == null) {
            throw new BadRequestException("Vui lòng đăng nhập!");
        }
        orderService.cancelOrder(currentUser.getUserId(), orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công và đã hoàn lại tồn kho", null));
    }

    @PutMapping("/{orderId}/shipper-deliver")
    @PreAuthorize("hasAnyRole('SHIPPER', 'ADMIN', 'STAFF')")
    @Operation(summary = "Shipper xác nhận giao hàng & thu tiền COD", description = "Cập nhật đơn hàng thành DELIVERED, tự động đánh dấu codCollected = true nếu là COD")
    public ResponseEntity<ApiResponse<Order>> shipperDeliver(
            @RequestParam java.math.BigDecimal collectedAmount,
            @RequestParam String recipient,
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "Giao hàng thành công") String note
    ) {
        Long shipperId = currentUser != null ? currentUser.getUserId() : null;
        Order delivered = orderService.confirmDelivery(orderId, collectedAmount, recipient, note);
        return ResponseEntity.ok(ApiResponse.ok("Shipper xác nhận giao hàng và thu COD thành công", delivered));
    }

    @PutMapping("/{orderId}/reconcile-cod")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Kế toán chi nhánh đối soát nộp quỹ COD", description = "Xác nhận tiền mặt COD đã nộp vào tài khoản / quỹ chi nhánh, sinh mã chứng từ đối soát")
    public ResponseEntity<ApiResponse<Order>> reconcileCod(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @RequestParam(required = false) String transactionRef
    ) {
        Long accountantId = currentUser != null ? currentUser.getUserId() : null;
        Order reconciled = orderService.reconcileCodPayment(orderId, accountantId, transactionRef);
        return ResponseEntity.ok(ApiResponse.ok("Đối soát quỹ COD thành công", reconciled));
    }

    @GetMapping("/pending-cod-reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Danh sách đơn hàng COD đang chờ đối soát nộp quỹ", description = "Dành cho bộ phận kế toán kho và thủ quỹ đối chiếu dòng tiền mặt")
    public ResponseEntity<ApiResponse<List<Order>>> getPendingCodOrders() {
        List<Order> orders = orderService.getPendingCodReconciliationOrders();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đơn chờ đối soát COD thành công", orders));
    }

    @GetMapping("/{orderCode}/payment-simulation")
    public ResponseEntity<ApiResponse<vn.iotstar.dto.response.PaymentSimulationDto>> paymentSimulation(
            @AuthenticationPrincipal User user, @PathVariable String orderCode) {
        if (user == null) throw new BadRequestException("Vui lòng đăng nhập");
        var payment = simulator.view(user.getUserId(),orderCode);
        return ResponseEntity.ok(ApiResponse.ok("VNPay mô phỏng - không thu tiền thật",vn.iotstar.dto.response.PaymentSimulationDto.from(payment)));
    }

    @PostMapping("/{orderCode}/payment-simulation")
    public ResponseEntity<ApiResponse<vn.iotstar.dto.response.PaymentSimulationDto>> simulatePayment(
            @AuthenticationPrincipal User user, @PathVariable String orderCode,
            @RequestParam vn.iotstar.service.PaymentSimulationService.Outcome outcome) {
        if (user == null) throw new BadRequestException("Vui lòng đăng nhập");
        var payment = simulator.settle(user.getUserId(),orderCode,outcome);
        return ResponseEntity.ok(ApiResponse.ok("VNPay mô phỏng - không thu tiền thật",vn.iotstar.dto.response.PaymentSimulationDto.from(payment)));
    }
}
