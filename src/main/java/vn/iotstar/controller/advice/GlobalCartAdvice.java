package vn.iotstar.controller.advice;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.iotstar.entity.User;
import vn.iotstar.service.CartService;
import vn.iotstar.util.SecurityUtils;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalCartAdvice {

    private final CartService cartService;

    @ModelAttribute
    public void populateCartData(Model model, HttpSession session) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user != null) {
            int count = cartService.getCartItemCount(user.getUserId());
            session.setAttribute("cartCount", count);
            model.addAttribute("cartCount", count);
        } else {
            session.removeAttribute("cartCount");
            model.addAttribute("cartCount", 0);
        }
    }
}
