package vn.iotstar.dto.response;

import java.math.BigDecimal;

public record CheckoutPricingDto(BigDecimal subtotal, BigDecimal shippingFee,
                                 BigDecimal discountAmount, BigDecimal totalAmount) {}
