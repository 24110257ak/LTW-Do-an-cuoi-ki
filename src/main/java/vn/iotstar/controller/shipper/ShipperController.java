package vn.iotstar.controller.shipper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Order;
import vn.iotstar.entity.User;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.repository.OrderRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.OrderService;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/shipper")
@RequiredArgsConstructor
public class ShipperController {

    private final OrderService orderService;
    private final vn.iotstar.service.ShipperLedgerService ledger;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private User getCurrentShipper(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    @GetMapping({"", "/", "/orders"})
    public String shipperOrders(
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication,
            Model model) {

        User shipper = getCurrentShipper(authentication);
        Long shipperId = shipper != null ? shipper.getUserId() : null;

        OrderStatus filterStatus = null;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr)) {
            try {
                filterStatus = OrderStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception ignored) {
            }
        }

        Page<Order> orderPage = Page.empty();
        long countTotal = 0;
        long countShipping = 0;
        long countDelivered = 0;
        long countFailed = 0;

        if (shipperId != null) {
            orderPage = orderService.getOrdersByShipper(
                    shipperId,
                    filterStatus,
                    PageRequest.of(Math.max(0, page), Math.max(1,Math.min(size,100)), Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            countTotal = orderRepository.countByShipperUserId(shipperId);
            countShipping = orderRepository.countByShipperUserIdAndStatus(shipperId, OrderStatus.SHIPPING);
            countDelivered = orderRepository.countByShipperUserIdAndStatus(shipperId, OrderStatus.DELIVERED);
            countFailed = orderRepository.countByShipperUserIdAndStatus(shipperId, OrderStatus.DELIVERY_FAILED);
        }

        // Đơn hàng đang SHIPPING chưa có tài xế nhận (sẵn sàng tiếp nhận)
        List<Order> unassignedOrders = shipperId == null ? List.of() : orderService.getClaimableOrders();

        model.addAttribute("cashLedger", shipperId == null ? null : ledger.currentLedger());
        java.util.Map<Long,List<vn.iotstar.entity.DeliveryAttempt>> attempts=new java.util.HashMap<>();
        for(var order:orderPage)attempts.put(order.getOrderId(),orderService.getDeliveryAttempts(order.getOrderId()));
        model.addAttribute("deliveryAttemptsByOrder",attempts);
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("shipper", shipper);
        model.addAttribute("selectedStatus", statusStr != null ? statusStr.toUpperCase() : "ALL");
        model.addAttribute("countTotal", countTotal);
        model.addAttribute("countShipping", countShipping);
        model.addAttribute("countDelivered", countDelivered);
        model.addAttribute("countFailed", countFailed);
        model.addAttribute("unassignedOrders", unassignedOrders);

        return "shipper/orders";
    }

    @PostMapping("/orders/{id}/deliver")
    public String confirmDelivered(@PathVariable("id") Long id,
                                   @RequestParam java.math.BigDecimal collectedAmount,
                                   @RequestParam String recipient,
                                   @RequestParam(value = "note", required = false) String note,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User shipper = getCurrentShipper(authentication);
            orderService.confirmDelivery(id, collectedAmount, recipient, note);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ghi nhận giao hàng và số tiền thực thu. COD đã thu vẫn cần đối soát nộp quỹ riêng.");
        } catch (Exception e) {
            log.error("Lỗi xác nhận giao hàng: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/shipper/orders";
    }

    @PostMapping("/orders/{id}/fail")
    public String reportFailed(@PathVariable("id") Long id,
                               @RequestParam(value = "reason", required = false) String reason,
                               RedirectAttributes redirectAttributes) {
        try {
            orderService.reportDeliveryFailed(id, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái: Giao hàng không thành công.");
        } catch (Exception e) {
            log.error("Lỗi báo cáo giao thất bại: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/shipper/orders";
    }

    @PostMapping("/orders/{id}/claim")
    public String claimOrder(@PathVariable("id") Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.claimOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã tiếp nhận đơn hàng #" + order.getOrderCode() + " thành công!");
        } catch (Exception e) {
            log.error("Lỗi tiếp nhận đơn hàng: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/shipper/orders";
    }
}
