package vn.iotstar.controller;

import jakarta.servlet.http.HttpSession;
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
    public String viewCart(Model model, HttpSession session) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/cart";
        }

        int count = cartService.getCartItemCount(user.getUserId());
        session.setAttribute("cartCount", count);
        model.addAttribute("cartItems", cartService.getCartItems(user.getUserId()));
        model.addAttribute("subtotal", cartService.getCartSubtotal(user.getUserId()));
        model.addAttribute("itemCount", count);
        return "customer/cart";
    }

    @PostMapping("/add")
    public String addToCart(
            @RequestParam Long variantId,
            @RequestParam(defaultValue = "1") int quantity,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login?redirect=/cart";
        }

        try {
            cartService.addToCart(user.getUserId(), variantId, quantity);
            session.setAttribute("cartCount", cartService.getCartItemCount(user.getUserId()));
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
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        try {
            cartService.updateQuantity(user.getUserId(), cartItemId, quantity);
            session.setAttribute("cartCount", cartService.getCartItemCount(user.getUserId()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(
            @RequestParam Long cartItemId,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.removeFromCart(user.getUserId(), cartItemId);
        session.setAttribute("cartCount", cartService.getCartItemCount(user.getUserId()));
        redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm khỏi giỏ hàng!");
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(RedirectAttributes redirectAttributes, HttpSession session) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            return "redirect:/auth/login";
        }

        cartService.clearCart(user.getUserId());
        session.setAttribute("cartCount", 0);
        redirectAttributes.addFlashAttribute("successMessage", "Đã làm trống giỏ hàng!");
        return "redirect:/cart";
    }
}
