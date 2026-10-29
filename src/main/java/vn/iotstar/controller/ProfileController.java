package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.*;
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
import vn.iotstar.util.SecurityUtils;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final UserAddressRepository addressRepository;
    private final WishlistRepository wishlistRepository;
    private final ViewedProductRepository viewedProductRepository;
    private final ProductRepository productRepository;

    @GetMapping("/profile")
    public String profilePage(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        model.addAttribute("user", user);
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

    @PostMapping("/profile/address/add")
    public String addAddress(
            @RequestParam String receiverName,
            @RequestParam String phone,
            @RequestParam String province,
            @RequestParam String district,
            @RequestParam String ward,
            @RequestParam String street,
            @RequestParam(required = false) String addressNote,
            @RequestParam(required = false, defaultValue = "false") boolean isDefault,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) return "redirect:/auth/login";

        if (isDefault) {
            // Hủy mặc định các địa chỉ cũ
            List<UserAddress> addresses = addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(user.getUserId());
            addresses.forEach(a -> a.setIsDefault(false));
            addressRepository.saveAll(addresses);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .receiverName(receiverName)
                .phone(phone)
                .province(province)
                .district(district)
                .ward(ward)
                .street(street)
                .addressNote(addressNote)
                .isDefault(isDefault)
                .build();
        addressRepository.save(address);

        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm địa chỉ giao hàng mới!");
        return "redirect:/profile";
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

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = (status != null) ? orderService.getOrdersByUserAndStatus(user.getUserId(), status, pageRequest) : orderService.getOrdersByUser(user.getUserId(), pageRequest);
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

        if (wishlistRepository.existsByUserUserIdAndProductProductId(user.getUserId(), productId)) {
            wishlistRepository.deleteByUserUserIdAndProductProductId(user.getUserId(), productId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi danh sách yêu thích!");
        } else {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "id", productId));
            Wishlist wl = Wishlist.builder().user(user).product(product).build();
            wishlistRepository.save(wl);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu sản phẩm vào danh sách yêu thích!");
        }
        return "redirect:/wishlist";
    }
}
