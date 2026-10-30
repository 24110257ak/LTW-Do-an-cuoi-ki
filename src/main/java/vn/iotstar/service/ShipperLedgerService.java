package vn.iotstar.service;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import vn.iotstar.entity.Order;import vn.iotstar.enums.*;import vn.iotstar.repository.OrderRepository;import java.math.BigDecimal;import java.util.List;
@Service @RequiredArgsConstructor
public class ShipperLedgerService {
 private final OperatorService operators;private final OrderRepository orders;
 public record Summary(BigDecimal toCollect,BigDecimal collected,BigDecimal pendingRemittance,BigDecimal remitted,List<Order> entries,java.util.Map<String,BigDecimal> batches){}
 @Transactional(readOnly=true) public Summary currentLedger(){
  var actor=operators.shipper();var entries=orders.findByShipperUserIdAndPaymentMethodOrderByCreatedAtDesc(actor.getUserId(),PaymentMethod.COD);
  var batches=new java.util.LinkedHashMap<String,BigDecimal>();
  BigDecimal due=BigDecimal.ZERO,collected=BigDecimal.ZERO,pending=BigDecimal.ZERO,remitted=BigDecimal.ZERO;
  for(var order:entries){
   if(order.getStatus()==OrderStatus.SHIPPING)due=due.add(order.getTotalAmount());
   if(Boolean.TRUE.equals(order.getCodCollected())){BigDecimal amount=order.getActualCollectedAmount()==null?order.getTotalAmount():order.getActualCollectedAmount();collected=collected.add(amount);if(Boolean.TRUE.equals(order.getCodReconciled())){remitted=remitted.add(amount);batches.merge(order.getCodTransactionId()==null?"Đợt cũ chưa có mã":order.getCodTransactionId(),amount,BigDecimal::add);}else pending=pending.add(amount);}
  }
  return new Summary(due,collected,pending,remitted,entries,java.util.Collections.unmodifiableMap(batches));
 }
}
