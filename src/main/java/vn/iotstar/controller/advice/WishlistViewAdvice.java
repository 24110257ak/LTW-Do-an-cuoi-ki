package vn.iotstar.controller.advice;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Controller;import org.springframework.web.bind.annotation.*;import vn.iotstar.service.WishlistService;import java.util.Set;
@ControllerAdvice(annotations=Controller.class) @RequiredArgsConstructor
public class WishlistViewAdvice {
 @ModelAttribute("canUseWishlist") public boolean canUseWishlist(){return vn.iotstar.util.SecurityUtils.getCurrentUser().map(u->u.getRole()!=null&&"CUSTOMER".equals(u.getRole().getRoleName())).orElse(true);}
private final WishlistService wishlist;@ModelAttribute("likedProductIds") public Set<Long> likedIds(){return wishlist.likedIds();}}
