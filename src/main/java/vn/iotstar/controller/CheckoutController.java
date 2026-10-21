package vn.iotstar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.*;
import vn.iotstar.enums.OrderType;
import vn.iotstar.enums.PaymentMethod;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.DeliveryProviderRepository;
import vn.iotstar.repository.UserAddressRepository;
import vn.iotstar.service.BranchService;
import vn.iotstar.service.CartService;
import vn.iotstar.service.OrderService;
import vn.iotstar.util.SecurityUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CartService cartService;
    private final OrderService orderService;
    private final BranchService branchService;
    private final DeliveryProviderRepository providerRepository;
    private final UserAddressRepository addressRepository;

    @GetMapping
    public String checkoutPage(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/checkout";
        }

        List<CartItem> cartItems = cartService.getCartItems(user.getUserId());
        if (cartItems.isEmpty()) {
            return "redirect:/cart";
        }

        BigDecimal subtotal = cartService.getCartSubtotal(user.getUserId());

        // Chuẩn bị form request với thông tin mặc định
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setOrderType(OrderType.DELIVERY);
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setReceiverName(user.getFullName());
        checkoutRequest.setReceiverPhone(user.getPhone());

        // Lấy địa chỉ mặc định của user nếu có
        addressRepository.findByUserUserIdAndIsDefaultTrue(user.getUserId()).ifPresent(addr -> {
            checkoutRequest.setReceiverName(addr.getReceiverName());
            checkoutRequest.setReceiverPhone(addr.getPhone());
            checkoutRequest.setProvince(addr.getProvince());
            checkoutRequest.setDistrict(addr.getDistrict());
            checkoutRequest.setWard(addr.getWard());
            checkoutRequest.setStreet(addr.getStreet());
        });

        model.addAttribute("checkoutRequest", checkoutRequest);
        model.addAttribute("cartItems", cartItems);
        model.addAttribute("subtotal", subtotal);
        model.addAttribute("branches", branchService.getAllActiveBranches());
        model.addAttribute("providers", providerRepository.findAllByIsActiveTrue());
        model.addAttribute("savedAddresses", addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(user.getUserId()));

        return "customer/checkout";
    }

    @PostMapping("/place-order")
    public String processCheckout(
            @Valid @ModelAttribute("checkoutRequest") CheckoutRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("cartItems", cartService.getCartItems(user.getUserId()));
            model.addAttribute("subtotal", cartService.getCartSubtotal(user.getUserId()));
            model.addAttribute("branches", branchService.getAllActiveBranches());
            model.addAttribute("providers", providerRepository.findAllByIsActiveTrue());
            return "customer/checkout";
        }

        try {
            Order order = orderService.placeOrder(user.getUserId(), request);
            return "redirect:/checkout/success/" + order.getOrderCode();
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/checkout";
        }
    }

    @GetMapping("/success/{orderCode}")
    public String orderSuccess(@PathVariable String orderCode, Model model) {
        Order order = orderService.getOrderByCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "mã", orderCode));

        model.addAttribute("order", order);
        return "customer/order-success";
    }
}
