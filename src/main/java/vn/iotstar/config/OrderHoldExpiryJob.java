package vn.iotstar.config;
import lombok.RequiredArgsConstructor;import lombok.extern.slf4j.Slf4j;import org.springframework.context.annotation.*;import org.springframework.scheduling.annotation.*;import org.springframework.data.domain.PageRequest;import vn.iotstar.repository.OrderRepository;import vn.iotstar.service.OrderService;import java.time.LocalDateTime;
@Configuration @EnableScheduling @Profile("!test") @RequiredArgsConstructor @Slf4j
public class OrderHoldExpiryJob {
 private final OrderRepository orders;private final OrderService operations;
 @Scheduled(fixedDelay=60000,initialDelay=60000) public void process() {
  var now=LocalDateTime.now();
  for(var id:orders.findReminderIds(now,now.plusHours(6),PageRequest.of(0,200)))try{operations.remindHold(id);}catch(RuntimeException ex){log.warn("Nhắc nhận hàng {} chưa hoàn tất: {}",id,ex.getMessage());}
  for(var id:orders.findExpiredHoldIds(now,PageRequest.of(0,200)))try{operations.processExpiredHold(id);}catch(RuntimeException ex){log.warn("Xử lý hạn giữ hàng {} chưa hoàn tất: {}",id,ex.getMessage());}
 }
}
