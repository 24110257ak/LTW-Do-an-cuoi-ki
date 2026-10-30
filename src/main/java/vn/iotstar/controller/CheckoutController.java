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
import vn.iotstar.service.OrderService;
import vn.iotstar.util.SecurityUtils;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final vn.iotstar.service.CheckoutPricingService pricingService;
    private final OrderService orderService;
    private final BranchService branchService;
    private final DeliveryProviderRepository providerRepository;
    private final UserAddressRepository addressRepository;
    private final vn.iotstar.service.UserService userService;

    @GetMapping
    public String checkoutPage(@RequestParam(required = false) Long buyNowVariantId,
                               @RequestParam(required = false) Integer buyNowQuantity, Model model,
                               RedirectAttributes redirect) {
        User user = SecurityUtils.getCurrentUser().flatMap(account -> userService.findById(account.getUserId())).orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/checkout";
        }

        // Chuẩn bị form request với thông tin mặc định
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setBuyNowVariantId(buyNowVariantId);
        checkoutRequest.setBuyNowQuantity(buyNowQuantity);
        checkoutRequest.setCheckoutToken(UUID.randomUUID().toString());
        checkoutRequest.setProvince("TP. Hồ Chí Minh");
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

        try {
            var items = pricingService.itemsForCheckout(user.getUserId(),checkoutRequest);
            if (items.isEmpty()) return "redirect:/cart";
            model.addAttribute("checkoutRequest",checkoutRequest);
            populate(model,user,checkoutRequest);
        } catch (BadRequestException ex) {
            redirect.addFlashAttribute("errorMessage",ex.getMessage());
            return "redirect:/products";
        }
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

        if (request.getCheckoutToken() == null || request.getCheckoutToken().isBlank()) {
            request.setCheckoutToken(UUID.randomUUID().toString());
        }

        if (bindingResult.hasErrors()) {
            populate(model,user,request);
            return "customer/checkout";
        }

        try {
            Order order = orderService.placeOrder(user.getUserId(), request);
            if (order.getPaymentMethod() == PaymentMethod.VNPAY && Boolean.TRUE.equals(order.getPaymentSimulated()))
                return "redirect:" + order.getPaymentSimulationUrl();
            return "redirect:/checkout/success/" + order.getOrderCode();
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:" + location(request);
        }
    }

    @GetMapping("/success/{orderCode}")
    public String orderSuccess(@PathVariable String orderCode, Model model) {
        Order order = orderService.getOrderByCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "mã", orderCode));

        User user = SecurityUtils.getCurrentUser().orElseThrow(() -> new BadRequestException("Vui lòng đăng nhập"));
        if (!order.getUser().getUserId().equals(user.getUserId())) throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền xem đơn này");
        model.addAttribute("order", order);
        return "customer/order-success";
    }

    @GetMapping("/pricing")
    @ResponseBody
    public vn.iotstar.dto.response.CheckoutPricingDto preview(@ModelAttribute CheckoutRequest request) {
        User user = SecurityUtils.getCurrentUser().orElseThrow(() -> new BadRequestException("Vui lòng đăng nhập"));
        return pricingService.preview(user.getUserId(), request);
    }

    private void addPricing(Model model, Long userId, CheckoutRequest request) {
        try { model.addAttribute("pricing", pricingService.preview(userId, request)); }
        catch (BadRequestException ex) { model.addAttribute("pricingError", ex.getMessage()); }
    }

    @PostMapping("/buy-now")
    public String buyNow(@RequestParam Long variantId, @RequestParam(defaultValue="1") int quantity) {
        if (SecurityUtils.getCurrentUser().isEmpty()) return "redirect:/auth/login";
        if (quantity < 1 || quantity > vn.iotstar.service.PurchaseLimits.MAX_PER_PRODUCT)
            throw new BadRequestException("Số lượng mua ngay phải từ 1 đến 10");
        return "redirect:/checkout?buyNowVariantId=" + variantId + "&buyNowQuantity=" + quantity;
    }

    private String location(CheckoutRequest request) {
        return request.getBuyNowVariantId() == null ? "/checkout" : "/checkout?buyNowVariantId=" + request.getBuyNowVariantId() + "&buyNowQuantity=" + request.getBuyNowQuantity();
    }

    private void populate(Model model, User user, CheckoutRequest request) {
        var items = pricingService.itemsForCheckout(user.getUserId(),request);
        model.addAttribute("cartItems",items);
        model.addAttribute("branches",branchService.getAllActiveBranches());
        model.addAttribute("providers",providerRepository.findAllByIsActiveTrue());
        var savedAddresses = addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(user.getUserId());
        model.addAttribute("savedAddresses", savedAddresses);
        // Chỉ đánh dấu địa chỉ trùng form; không ghi đè thông tin khi form bị lỗi xác thực.
        model.addAttribute("selectedSavedAddressId", savedAddresses.stream().filter(address ->
                java.util.Objects.equals(address.getReceiverName(), request.getReceiverName())
                && java.util.Objects.equals(address.getPhone(), request.getReceiverPhone())
                && java.util.Objects.equals(address.getProvince(), request.getProvince())
                && java.util.Objects.equals(address.getDistrict(), request.getDistrict())
                && java.util.Objects.equals(address.getWard(), request.getWard())
                && java.util.Objects.equals(address.getStreet(), request.getStreet()))
                .map(UserAddress::getAddressId).findFirst().orElse(null));
        model.addAttribute("buyNow",request.getBuyNowVariantId() != null);
        addPricing(model,user.getUserId(),request);
    }
}
