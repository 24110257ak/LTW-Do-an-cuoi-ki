package vn.iotstar.enums;

import lombok.Getter;

@Getter
public enum SimulatedPaymentStatus {
    PENDING("Chờ thanh toán mô phỏng"), SUCCESS("Thanh toán mô phỏng thành công"),
    FAILED("Thanh toán mô phỏng thất bại"), CANCELLED("Đã hủy thanh toán mô phỏng"), EXPIRED("Hết thời gian thanh toán"), REFUNDED("Đã hoàn tiền mô phỏng");
    private final String displayName;
    SimulatedPaymentStatus(String displayName) { this.displayName = displayName; }
}
