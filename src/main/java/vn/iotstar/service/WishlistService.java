package vn.iotstar.service;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.security.access.AccessDeniedException;import vn.iotstar.entity.*;import vn.iotstar.repository.*;import vn.iotstar.util.SecurityUtils;import vn.iotstar.exception.BadRequestException;import java.util.*;
@Service @RequiredArgsConstructor
public class WishlistService {
 private final WishlistRepository wishlist;private final UserRepository users;private final ProductRepository products;private final OperatorService operators;
 @Transactional public boolean setLiked(Long productId,boolean liked){
  var actor=operators.current();if(!"CUSTOMER".equals(actor.getRole().getRoleName()))throw new AccessDeniedException("Chức năng yêu thích dành cho khách hàng");
  var owner=users.findForCheckout(actor.getUserId()).orElseThrow();
  var existing=wishlist.findByUserUserIdAndProductProductId(owner.getUserId(),productId);
  if(!liked){existing.ifPresent(wishlist::delete);return false;}
  if(existing.isPresent())return true;
  var product=products.findById(productId).filter(p->Boolean.TRUE.equals(p.getIsActive())).orElseThrow(()->new BadRequestException("Sản phẩm không còn kinh doanh"));
  wishlist.save(Wishlist.builder().user(owner).product(product).build());return true;
 }
 @Transactional(readOnly=true) public Set<Long> likedIds(){
  var account=SecurityUtils.getCurrentUser().flatMap(u->users.findById(u.getUserId())).orElse(null);
  if(account==null||!Boolean.TRUE.equals(account.getIsActive())||!"CUSTOMER".equals(account.getRole().getRoleName()))return Set.of();
  return new HashSet<>(wishlist.findLikedProductIds(account.getUserId()));
 }
}
