package vn.iotstar.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.iotstar.enums.OrderType;
import vn.iotstar.enums.PaymentMethod;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    private String checkoutToken;
    private Long buyNowVariantId;
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(10)
    private Integer buyNowQuantity;

    @NotNull(message = "Vui lòng chọn hình thức nhận hàng")
    private OrderType orderType; // DELIVERY hoặc PICKUP

    // Nhận tại nhà (DELIVERY)
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String district;
    private String ward;
    private String street;
    private Integer providerId;

    // Nhận tại cửa hàng (PICKUP)
    private Integer pickupBranchId;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod; // COD hoặc VNPAY

    private String couponCode;
    private String note;
}
