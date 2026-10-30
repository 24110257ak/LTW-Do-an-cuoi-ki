package vn.iotstar.dto.response;

public record CartLimitDto(int maxQuantity, long inCartQuantity, int maxAdditionalQuantity, String message) {}
