package vn.iotstar.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    NEW("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    READY_FOR_PICKUP("Sẵn sàng nhận tại cửa hàng"),
    SHIPPING("Đang giao hàng"),
    DELIVERY_FAILED("Giao không thành công"),
    RETURNING("Đang chuyển hoàn về kho"),
    RETURNED("Đã hoàn về kho"),
    DELIVERED("Đã giao thành công / Đã nhận"),
    CANCELLED("Đã hủy"),
    RETURNED_REFUND("Đã hoàn tiền");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }
}
