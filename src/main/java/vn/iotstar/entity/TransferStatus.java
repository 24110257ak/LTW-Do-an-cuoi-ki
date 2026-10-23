package vn.iotstar.entity;

import lombok.Getter;

@Getter
public enum TransferStatus {
    PENDING("Chờ duyệt"),
    APPROVED("Đã duyệt"),
    IN_TRANSIT("Đang vận chuyển"),
    COMPLETED("Đã hoàn tất"),
    CANCELLED("Đã hủy");

    private final String displayName;

    TransferStatus(String displayName) {
        this.displayName = displayName;
    }
}

