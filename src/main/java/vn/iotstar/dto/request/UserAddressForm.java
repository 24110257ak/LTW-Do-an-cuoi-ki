package vn.iotstar.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import vn.iotstar.entity.UserAddress;

@Data
public class UserAddressForm {
    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự.")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
    @Pattern(regexp = "\\s*\\+?[0-9][0-9 ().-]{7,19}\\s*", message = "Số điện thoại không hợp lệ.")
    private String phone;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành phố.")
    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự.")
    private String province;

    @NotBlank(message = "Vui lòng chọn quận/huyện.")
    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự.")
    private String district;

    @NotBlank(message = "Vui lòng chọn phường/xã.")
    @Size(max = 100, message = "Phường/xã tối đa 100 ký tự.")
    private String ward;

    @NotBlank(message = "Vui lòng nhập số nhà và tên đường.")
    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự.")
    private String street;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự.")
    private String addressNote;
    private Boolean isDefault = false;

    public static UserAddressForm from(UserAddress address) {
        var form = new UserAddressForm();
        form.setReceiverName(address.getReceiverName());
        form.setPhone(address.getPhone());
        form.setProvince(address.getProvince());
        form.setDistrict(address.getDistrict());
        form.setWard(address.getWard());
        form.setStreet(address.getStreet());
        form.setAddressNote(address.getAddressNote());
        form.setIsDefault(address.getIsDefault());
        return form;
    }
}
