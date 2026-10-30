package vn.iotstar.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import vn.iotstar.dto.request.UserAddressForm;
import vn.iotstar.entity.User;
import vn.iotstar.entity.UserAddress;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.UserAddressRepository;
import vn.iotstar.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAddressService {
    private final UserAddressRepository addresses;
    private final UserRepository users;

    public UserAddress getOwnedAddress(Long userId, Long addressId) {
        return addresses.findByAddressIdAndUserUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Địa chỉ", "id", addressId));
    }

    // Khóa tài khoản trước mọi lần ghi để hai yêu cầu đồng thời không tạo hai mặc định.
    private User lockOwner(Long userId) {
        return users.findForAddressUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
    }

    @Transactional
    public UserAddress add(Long userId, @Valid UserAddressForm form) {
        User owner = lockOwner(userId);
        var current = new ArrayList<>(addresses.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId));
        var address = UserAddress.builder().user(owner).isDefault(false).build();
        apply(address, form);
        addresses.save(address);
        current.add(address);
        UserAddress preferred = Boolean.TRUE.equals(form.getIsDefault()) ? address : existingDefault(current, address);
        setOnlyDefault(current, preferred);
        return address;
    }

    @Transactional
    public void update(Long userId, Long addressId, @Valid UserAddressForm form) {
        lockOwner(userId);
        UserAddress address = getOwnedAddress(userId, addressId);
        var current = addresses.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
        boolean keepDefault = Boolean.TRUE.equals(address.getIsDefault());
        apply(address, form);
        UserAddress preferred = keepDefault || Boolean.TRUE.equals(form.getIsDefault())
                ? address : existingDefault(current, address);
        setOnlyDefault(current, preferred);
    }

    @Transactional
    public void makeDefault(Long userId, Long addressId) {
        lockOwner(userId);
        UserAddress address = getOwnedAddress(userId, addressId);
        setOnlyDefault(addresses.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId), address);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        lockOwner(userId);
        UserAddress address = getOwnedAddress(userId, addressId);
        var remaining = new ArrayList<>(addresses.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId));
        remaining.removeIf(item -> item.getAddressId().equals(addressId));
        addresses.delete(address);
        addresses.flush();
        if (!remaining.isEmpty()) setOnlyDefault(remaining, existingDefault(remaining, remaining.get(0)));
    }

    private UserAddress existingDefault(List<UserAddress> current, UserAddress fallback) {
        return current.stream().filter(address -> Boolean.TRUE.equals(address.getIsDefault())).findFirst().orElse(fallback);
    }

    private void setOnlyDefault(List<UserAddress> current, UserAddress preferred) {
        // Cùng một giao dịch: bỏ mặc định cũ trước, sau đó đặt địa chỉ được chọn.
        current.forEach(address -> address.setIsDefault(false));
        addresses.saveAllAndFlush(current);
        preferred.setIsDefault(true);
        addresses.save(preferred);
    }

    private void apply(UserAddress address, UserAddressForm form) {
        address.setReceiverName(form.getReceiverName().trim());
        address.setPhone(form.getPhone().trim());
        address.setProvince(form.getProvince().trim());
        address.setDistrict(form.getDistrict().trim());
        address.setWard(form.getWard().trim());
        address.setStreet(form.getStreet().trim());
        address.setAddressNote(form.getAddressNote() == null ? null : form.getAddressNote().trim());
    }
}
