package vn.iotstar.service;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.transaction.annotation.Transactional;import vn.iotstar.entity.*;import vn.iotstar.repository.*;import vn.iotstar.exception.BadRequestException;import java.math.BigDecimal;import java.time.LocalDateTime;import java.util.List;
@Service @RequiredArgsConstructor
public class ProductCostService {
 private final ProductRepository products;private final ProductVariantRepository variants;private final CostPriceAuditRepository audits;private final OperatorService operators;
 private void validate(BigDecimal cost,boolean confirmZero,String reason) {
  if(cost==null||cost.signum()<0||cost.stripTrailingZeros().scale()>0||cost.precision()-cost.scale()>18)throw new BadRequestException("Giá vốn phải là số nguyên VND không âm, tối đa 18 chữ số");
  if(cost.signum()==0&&!confirmZero)throw new BadRequestException("Giá vốn 0 cần xác nhận rõ đã kiểm tra, không phải chưa khai báo");
  if(reason==null||reason.isBlank()||reason.length()>500)throw new BadRequestException("Cần lý do cập nhật giá vốn, tối đa 500 ký tự");
 }
 @Transactional @PreAuthorize("hasRole('ADMIN')")
 public ProductVariant saveVariant(Long productId,ProductVariant incoming,BigDecimal declaredCost,boolean confirmZero,String reason) {
  var actor=operators.admin();var product=products.findForCheckout(productId).orElseThrow();incoming.setProduct(product);
  ProductVariant old=incoming.getVariantId()==null?null:variants.findForCheckout(incoming.getVariantId()).orElseThrow();
  if(old!=null&&!old.getProduct().getProductId().equals(productId))throw new BadRequestException("Biến thể không thuộc sản phẩm");
  BigDecimal oldCost=old==null?BigDecimal.ZERO:old.getCostPrice();boolean confirmed=old!=null&&Boolean.TRUE.equals(old.getCostPriceConfirmed());
  incoming.setCostPrice(oldCost);incoming.setCostPriceConfirmed(confirmed); // Ignore bound cost metadata.
  if(declaredCost!=null)validate(declaredCost,confirmZero,old==null&&(reason==null||reason.isBlank())?"Khai báo giá vốn ban đầu":reason);
  var saved=variants.save(incoming);
  if(declaredCost!=null)apply(saved,declaredCost,actor,reason==null||reason.isBlank()?"Khai báo giá vốn ban đầu":reason);
  return saved;
 }
 @Transactional @PreAuthorize("hasRole('ADMIN')")
 public void update(Long productId,Long variantId,BigDecimal cost,boolean confirmZero,String reason) {
  var actor=operators.admin();validate(cost,confirmZero,reason);products.findForCheckout(productId).orElseThrow();
  var variant=variants.findForCheckout(variantId).orElseThrow();
  if(!variant.getProduct().getProductId().equals(productId))throw new BadRequestException("Biến thể không thuộc sản phẩm");apply(variant,cost,actor,reason);
 }
 private void apply(ProductVariant variant,BigDecimal cost,User actor,String reason) {
  var audit=new CostPriceAudit();audit.setVariant(variant);audit.setActor(actor);audit.setOldCost(variant.getCostPrice());audit.setNewCost(cost);audit.setPreviouslyConfirmed(Boolean.TRUE.equals(variant.getCostPriceConfirmed()));audit.setChangedAt(LocalDateTime.now());audit.setReason(reason.trim());audits.save(audit);
  variant.setCostPrice(cost);variant.setCostPriceConfirmed(true);
 }
 @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')") public List<CostPriceAudit> history(Long productId){operators.admin();return audits.findTop50ByVariantProductProductIdOrderByAuditIdDesc(productId);}
}
