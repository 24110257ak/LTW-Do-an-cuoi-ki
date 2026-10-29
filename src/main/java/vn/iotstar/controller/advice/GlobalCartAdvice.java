package vn.iotstar.controller.advice;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.iotstar.entity.User;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.CartService;
import vn.iotstar.util.SecurityUtils;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalCartAdvice {

    private final CartService cartService;
    private final UserRepository userRepository;

    @ModelAttribute
    public void populateCartData(Model model, HttpSession session) {
        User user = SecurityUtils.getCurrentUser().orElse(null);
        if (user == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) && auth.getName() != null) {
                user = userRepository.findByUsername(auth.getName()).orElse(null);
            }
        }

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
