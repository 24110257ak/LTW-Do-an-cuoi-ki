package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.*;
import vn.iotstar.dto.request.UserAddressForm;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.ProductRepository;
import vn.iotstar.repository.UserAddressRepository;
import vn.iotstar.repository.ViewedProductRepository;
import vn.iotstar.repository.WishlistRepository;
import vn.iotstar.service.OrderService;
import vn.iotstar.service.ReviewService;
import vn.iotstar.service.UserService;
import vn.iotstar.service.UserAddressService;
import vn.iotstar.util.SecurityUtils;

import java.util.Collections;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final vn.iotstar.service.WishlistService wishlistService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final UserAddressRepository addressRepository;
    private final UserAddressService addressService;
    private final WishlistRepository wishlistRepository;
    private final ViewedProductRepository viewedProductRepository;
    private final ProductRepository productRepository;

    @GetMapping("/profile")
    public String profilePage(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        model.addAttribute("user", userService.findById(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", user.getUserId())));
        model.addAttribute("addresses", addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(user.getUserId()));
        return "customer/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        try {
            userService.updateProfile(user.getUserId(), fullName, phone, null);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @GetMapping("/profile/address/new")
    public String newAddress(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        User current = userService.findById(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", user.getUserId()));
        var form = new UserAddressForm();
        form.setReceiverName(current.getFullName());
        form.setPhone(current.getPhone());
        model.addAttribute("addressForm", form);
        return addressFormPage(model, null, false);
    }

    @GetMapping("/profile/address/{addressId}/edit")
    public String editAddress(@PathVariable Long addressId, Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        var address = addressService.getOwnedAddress(user.getUserId(), addressId);
        model.addAttribute("addressForm", UserAddressForm.from(address));
        return addressFormPage(model, addressId, Boolean.TRUE.equals(address.getIsDefault()));
    }

    @PostMapping("/profile/address/add")
    public String addAddress(@Valid @ModelAttribute("addressForm") UserAddressForm form, BindingResult errors,
                             Model model, RedirectAttributes redirect) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        if (errors.hasErrors()) return addressFormPage(model, null, false);
        addressService.add(user.getUserId(), form);
        redirect.addFlashAttribute("successMessage", "Đã thêm địa chỉ giao hàng mới!");
        return "redirect:/profile#saved-addresses";
    }

    @PostMapping("/profile/address/{addressId}/edit")
    public String saveAddress(@PathVariable Long addressId,
                              @Valid @ModelAttribute("addressForm") UserAddressForm form, BindingResult errors,
                              Model model, RedirectAttributes redirect) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        var address = addressService.getOwnedAddress(user.getUserId(), addressId);
        if (errors.hasErrors()) return addressFormPage(model, addressId, Boolean.TRUE.equals(address.getIsDefault()));
        addressService.update(user.getUserId(), addressId, form);
        redirect.addFlashAttribute("successMessage", "Đã cập nhật thông tin địa chỉ!");
        return "redirect:/profile#saved-addresses";
    }

    @PostMapping("/profile/address/{addressId}/default")
    public String defaultAddress(@PathVariable Long addressId, RedirectAttributes redirect) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        addressService.makeDefault(user.getUserId(), addressId);
        redirect.addFlashAttribute("successMessage", "Đã đặt địa chỉ nhận hàng mặc định!");
        return "redirect:/profile#saved-addresses";
    }

    @PostMapping("/profile/address/delete")
    public String deleteAddress(@RequestParam Long addressId, RedirectAttributes redirect) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";
        addressService.delete(user.getUserId(), addressId);
        redirect.addFlashAttribute("successMessage", "Đã xóa địa chỉ. Nếu còn địa chỉ khác, hệ thống luôn giữ một địa chỉ mặc định.");
        return "redirect:/profile#saved-addresses";
    }

    private String addressFormPage(Model model, Long addressId, boolean isDefault) {
        model.addAttribute("pageTitle", addressId == null ? "Thêm địa chỉ" : "Sửa địa chỉ");
        model.addAttribute("editingAddressId", addressId);
        model.addAttribute("addressWasDefault", isDefault);
        return "customer/address-form";
    }

    @GetMapping("/orders")
    public String orderHistory(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        // Old direct links to intermediate statuses select their customer-facing tab.
        if (status != null) {
            status = switch (status) {
                case DELIVERY_FAILED -> OrderStatus.SHIPPING;
                case RETURNING, RETURNED -> OrderStatus.RETURNED_REFUND;
                default -> status;
            };
        }
        Page<Order> orderPage = orderService.getCustomerOrderHistory(user.getUserId(), status,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                        Sort.by(Sort.Direction.DESC, "createdAt", "orderId")));
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "customer/order-history";
    }

    @GetMapping("/orders/{orderCode}")
    public String orderDetail(@PathVariable String orderCode, Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        Order order = orderService.getOrderByCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng", "mã", orderCode));

        if (!order.getUser().getUserId().equals(user.getUserId())) {
            throw new BadRequestException("Bạn không có quyền truy cập đơn hàng này!");
        }

        model.addAttribute("order", order);
        return "customer/order-detail";
    }

    @PostMapping("/orders/{orderId}/cancel")
    public String cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        try {
            orderService.cancelOrder(user.getUserId(), orderId, reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn hàng thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders";
    }

    @PostMapping("/orders/review")
    public String submitReview(
            @RequestParam Long orderItemId,
            @RequestParam byte stars,
            @RequestParam String content,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        try {
            reviewService.addReview(user.getUserId(), orderItemId, stars, content, null);
            redirectAttributes.addFlashAttribute("successMessage", "Cảm ơn bạn đã đánh giá sản phẩm!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/orders";
    }

    @GetMapping("/wishlist")
    public String wishlistPage(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        model.addAttribute("wishlistItems", wishlistRepository.findByUserUserIdOrderByAddedAtDesc(user.getUserId()));
        return "customer/wishlist";
    }

    @PostMapping("/wishlist/toggle")
    public String toggleWishlist(@RequestParam Long productId, RedirectAttributes redirectAttributes) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        wishlistService.setLiked(productId, !wishlistService.likedIds().contains(productId));
        return "redirect:/wishlist";
    }
    @PostMapping("/wishlist/set")
    public String setWishlist(@RequestParam Long productId,@RequestParam boolean liked,@RequestParam(defaultValue="wishlist")String place,RedirectAttributes redirect) {
        User user=SecurityUtils.getCurrentUser().orElse(null);if(user==null)return "redirect:/auth/login";
        try {wishlistService.setLiked(productId,liked);redirect.addFlashAttribute("successMessage",liked?"Đã lưu sản phẩm yêu thích":"Đã bỏ thích sản phẩm");}
        catch(BadRequestException ex){redirect.addFlashAttribute("errorMessage",ex.getMessage());}
        if("home".equals(place))return "redirect:/";
        if("list".equals(place))return "redirect:/products";
        if("detail".equals(place))return "redirect:/products/"+productRepository.findById(productId).orElseThrow().getSlug();
        return "redirect:/wishlist";
    }
}
