package vn.iotstar.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import vn.iotstar.repository.SimulatedPaymentRepository;
import vn.iotstar.service.PaymentSimulationService;
import java.time.LocalDateTime;

@Configuration @EnableScheduling @Profile("!test") @RequiredArgsConstructor @Slf4j
public class PaymentSimulationExpiryJob {
    private final SimulatedPaymentRepository payments;
    private final PaymentSimulationService simulator;
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expirePending() {
        for (Long id : payments.findExpiredIds(LocalDateTime.now())) {
            try { simulator.expire(id); }
            catch (RuntimeException ex) { log.warn("Chưa giải phóng được đơn thanh toán mô phỏng {}: {}", id, ex.getMessage()); }
        }
    }
}
