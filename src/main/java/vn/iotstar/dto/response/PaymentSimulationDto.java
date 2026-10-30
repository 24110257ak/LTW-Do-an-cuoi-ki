package vn.iotstar.dto.response;

import vn.iotstar.entity.SimulatedPayment;
import vn.iotstar.enums.SimulatedPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentSimulationDto(String orderCode, String reference, BigDecimal amount,
                                   SimulatedPaymentStatus status, LocalDateTime expiresAt,
                                   LocalDateTime settledAt, boolean simulated) {
    public static PaymentSimulationDto from(SimulatedPayment p) {
        return new PaymentSimulationDto(p.getOrder().getOrderCode(),p.getReference(),p.getAmount(),p.getStatus(),p.getExpiresAt(),p.getSettledAt(),true);
    }
}
