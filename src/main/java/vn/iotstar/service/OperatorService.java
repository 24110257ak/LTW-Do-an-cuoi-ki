package vn.iotstar.service;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.security.access.AccessDeniedException;import org.springframework.security.authentication.AnonymousAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;import vn.iotstar.entity.*;import vn.iotstar.repository.UserRepository;
@Service @RequiredArgsConstructor
public class OperatorService {
 private final UserRepository users;private final BranchService branches;
 public User current() {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth==null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)throw new AccessDeniedException("Vui lòng đăng nhập");
  var user=users.findByUsername(auth.getName()).orElseThrow(()->new AccessDeniedException("Tài khoản không tồn tại"));
  if(!Boolean.TRUE.equals(user.getIsActive()))throw new AccessDeniedException("Tài khoản đã bị khóa");return user;
 }
 public User admin() {var user=current();if(!"ADMIN".equals(user.getRole().getRoleName()))throw new AccessDeniedException("Chỉ admin được sửa giá vốn");return user;}
 public User warehouse(Order order) {var user=current();if(!java.util.Set.of("ADMIN","STAFF").contains(user.getRole().getRoleName()))throw new AccessDeniedException("Chỉ nhân viên kho hoặc admin được thực hiện");branches.checkBranchAccess(order.getBranch()==null?null:order.getBranch().getBranchId());return user;}
 public User shipper() {var user=current();if(!"SHIPPER".equals(user.getRole().getRoleName()))throw new AccessDeniedException("Tài khoản không phải tài xế");if(user.getBranch()!=null&&!Boolean.TRUE.equals(user.getBranch().getIsActive()))throw new AccessDeniedException("Chi nhánh tài xế đã ngừng hoạt động");return user;}
 public void shipperScope(User actor,Order order) {if(order.getBranch()==null||!Boolean.TRUE.equals(order.getBranch().getIsActive())||(actor.getBranch()!=null&&!actor.getBranch().getBranchId().equals(order.getBranch().getBranchId())))throw new AccessDeniedException("Đơn không thuộc phạm vi giao của tài xế");}
}
