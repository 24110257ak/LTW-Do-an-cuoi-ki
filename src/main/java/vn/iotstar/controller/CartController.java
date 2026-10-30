package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.User;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.CartService;
import vn.iotstar.util.SecurityUtils;

@Controller
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public String viewCart(Model model) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/cart";
        }

        var items = cartService.getCartItems(user.getUserId());
        model.addAttribute("cartItems", items);
        var counts = items.stream().collect(java.util.stream.Collectors.groupingBy(i -> i.getVariant().getProduct().getProductId(), java.util.stream.Collectors.summingLong(vn.iotstar.entity.CartItem::getQuantity)));
        java.util.Map<Long,Integer> remaining = new java.util.HashMap<>();
        counts.forEach((id,count) -> remaining.put(id,vn.iotstar.service.PurchaseLimits.remaining(count)));
        model.addAttribute("quantityByProduct",counts);
        model.addAttribute("remainingByProduct",remaining);
        model.addAttribute("subtotal", cartService.getCartSubtotal(user.getUserId()));
        model.addAttribute("itemCount", cartService.getCartItemCount(user.getUserId()));
        return "customer/cart";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam Long variantId,
            @RequestParam(defaultValue = "1") int quantity,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/cart";
        }

        try {
            cartService.addToCart(user.getUserId(), variantId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm vào giỏ hàng thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/update")
    public String updateQuantity(
            @RequestParam Long cartItemId,
            @RequestParam int quantity,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        try {
            cartService.updateQuantity(user.getUserId(), cartItemId, quantity);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(
            @RequestParam Long cartItemId,
            RedirectAttributes redirectAttributes
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.removeFromCart(user.getUserId(), cartItemId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng!");
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(RedirectAttributes redirectAttributes) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.clearCart(user.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã làm trống giỏ hàng!");
        return "redirect:/cart";
    }

    @GetMapping("/limits")
    @ResponseBody
    public vn.iotstar.dto.response.CartLimitDto limits(@RequestParam Long variantId) {
        User user = SecurityUtils.getCurrentUser().orElseThrow(() -> new BadRequestException("Vui lòng đăng nhập"));
        return cartService.getLimits(user.getUserId(),variantId);
    }
}
