package vn.iotstar.entity;

import lombok.Getter;

@Getter
public enum StockMovementType {
    SALE("Bán hàng"),
    RETURN("Hoàn hàng trả kho"),
    TRANSFER_OUT("Xuất chuyển kho"),
    TRANSFER_IN("Nhập chuyển kho"),
    ADJUSTMENT("Kiểm kê / Điều chỉnh kho");

    private final String displayName;

    StockMovementType(String displayName) {
        this.displayName = displayName;
    }
}

