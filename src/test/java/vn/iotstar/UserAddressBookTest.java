package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.UserAddressForm;
import vn.iotstar.entity.UserAddress;
import vn.iotstar.enums.OrderType;
import vn.iotstar.repository.UserAddressRepository;
import vn.iotstar.service.UserAddressService;
import vn.iotstar.service.UserService;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user_address_book;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAddressBookTest extends CommerceTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserAddressService addressService;
    @Autowired UserAddressRepository addressRepository;
    @Autowired UserService userService;
    @BeforeEach void setup() { fixtures(); }

    UserAddressForm form(String street, boolean makeDefault) {
        var form = new UserAddressForm();
        form.setReceiverName("Người nhận");
        form.setPhone("0901000001");
        form.setProvince("TP. Hồ Chí Minh");
        form.setDistrict("Quận 1");
        form.setWard("Phường Bến Nghé");
        form.setStreet(street);
        form.setAddressNote("Nhà riêng");
        form.setIsDefault(makeDefault);
        return form;
    }

    MockHttpServletRequestBuilder fields(MockHttpServletRequestBuilder request, String street, boolean makeDefault) {
        return request.param("receiverName", "Người nhận").param("phone", "0901000001")
                .param("province", "TP. Hồ Chí Minh").param("district", "Quận 1")
                .param("ward", "Phường Bến Nghé").param("street", street)
                .param("addressNote", "Nhà riêng").param("isDefault", String.valueOf(makeDefault));
    }

    List<UserAddress> addresses() {
        em.flush();
        return addressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(customer.getUserId());
    }

    void onlyDefault(Long id) {
        var saved = addresses();
        assertEquals(1L, saved.stream().filter(address -> Boolean.TRUE.equals(address.getIsDefault())).count());
        assertTrue(addressRepository.findById(id).orElseThrow().getIsDefault());
    }

    @Test void firstAddressBecomesDefaultAndAddingAnotherRespectsTheCheckbox() throws Exception {
        mvc.perform(fields(post("/profile/address/add"), "Địa chỉ đầu", false).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/profile#saved-addresses"));
        Long first = addresses().get(0).getAddressId();
        onlyDefault(first);
        mvc.perform(fields(post("/profile/address/add"), "Địa chỉ hai", false).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        onlyDefault(first);
        mvc.perform(fields(post("/profile/address/add"), "Địa chỉ ba", true).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var newest = addresses().stream().filter(address -> address.getStreet().equals("Địa chỉ ba")).findFirst().orElseThrow();
        onlyDefault(newest.getAddressId());
    }

    @Test void editChangesRecipientDetailsButCannotChangeOwnerOrLoseTheDefault() throws Exception {
        var saved = addressService.add(customer.getUserId(), form("Địa chỉ cũ", false));
        em.flush(); em.refresh(saved);
        var createdAt = saved.getCreatedAt();
        mvc.perform(fields(post("/profile/address/" + saved.getAddressId() + "/edit"), "  Địa chỉ mới  ", false)
                        .param("userId", admin.getUserId().toString()).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        em.flush(); em.refresh(saved);
        assertEquals("Địa chỉ mới", saved.getStreet());
        assertEquals("Nhà riêng", saved.getAddressNote());
        assertEquals(customer.getUserId(), saved.getUser().getUserId());
        assertEquals(createdAt, saved.getCreatedAt());
        onlyDefault(saved.getAddressId());
    }

    @Test void changingDefaultIsReflectedInCheckoutAndDoesNotChangeAnotherAccount() throws Exception {
        addressService.add(customer.getUserId(), form("Địa chỉ đầu", false));
        var second = addressService.add(customer.getUserId(), form("Địa chỉ hai", false));
        var other = addressService.add(admin.getUserId(), form("Địa chỉ của Admin", true));
        mvc.perform(post("/profile/address/" + second.getAddressId() + "/default").with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        onlyDefault(second.getAddressId());
        assertTrue(other.getIsDefault());
        mvc.perform(get("/checkout").with(user(customer)))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedSavedAddressId", second.getAddressId()))
                .andExpect(model().attribute("checkoutRequest", hasProperty("street", equalTo("Địa chỉ hai"))));
    }

    @Test void deletingDefaultPromotesRemainingAndDeletingLastLeavesAnEmptyBook() throws Exception {
        var first = addressService.add(customer.getUserId(), form("Địa chỉ đầu", false));
        var second = addressService.add(customer.getUserId(), form("Địa chỉ hai", false));
        mvc.perform(post("/profile/address/delete").param("addressId", first.getAddressId().toString()).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(addressRepository.existsById(first.getAddressId()));
        onlyDefault(second.getAddressId());
        mvc.perform(post("/profile/address/delete").param("addressId", second.getAddressId().toString()).with(user(customer)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(addresses().isEmpty());
    }

    @Test void deletingNonDefaultKeepsExistingDefault() {
        var first = addressService.add(customer.getUserId(), form("Địa chỉ đầu", false));
        var second = addressService.add(customer.getUserId(), form("Địa chỉ hai", false));
        addressService.delete(customer.getUserId(), second.getAddressId());
        onlyDefault(first.getAddressId());
    }

    @Test void foreignAddressesCannotBeReadEditedDeletedOrMadeDefault() throws Exception {
        var other = addressService.add(admin.getUserId(), form("Địa chỉ riêng", true));
        long id = other.getAddressId();
        mvc.perform(get("/profile/address/" + id + "/edit").with(user(customer))).andExpect(status().isNotFound());
        mvc.perform(fields(post("/profile/address/" + id + "/edit"), "Địa chỉ bị sửa", true).with(user(customer)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/profile/address/" + id + "/default").with(user(customer)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/profile/address/delete").param("addressId", String.valueOf(id)).with(user(customer)).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals("Địa chỉ riêng", other.getStreet());
        assertTrue(other.getIsDefault());
    }

    @Test void mutationsRequireCsrfAndAddressFieldsAreValidatedWithoutLosingEdits() throws Exception {
        var saved = addressService.add(customer.getUserId(), form("Địa chỉ đầu", false));
        mvc.perform(post("/profile/address/" + saved.getAddressId() + "/default").with(user(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/profile/address/delete").param("addressId", saved.getAddressId().toString()).with(user(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(fields(post("/profile/address/add"), " ", true).with(user(customer)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("customer/address-form"))
                .andExpect(model().attributeHasFieldErrors("addressForm", "street"));
        mvc.perform(fields(post("/profile/address/" + saved.getAddressId() + "/edit"), "x".repeat(256), true).with(user(customer)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("customer/address-form"))
                .andExpect(model().attributeHasFieldErrors("addressForm", "street"));
        assertEquals("Địa chỉ đầu", saved.getStreet());
        onlyDefault(saved.getAddressId());
    }

    @Test void profileAndFormsRenderActionsPrefillAndFreshPersonalInformation() throws Exception {
        var saved = addressService.add(customer.getUserId(), form("Địa chỉ đầu", false));
        mvc.perform(get("/profile").with(user(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sửa thông tin")))
                .andExpect(content().string(containsString("id=\"deleteAddressModal\"")))
                .andExpect(content().string(containsString("action=\"/profile/address/delete\"")));
        mvc.perform(get("/profile/address/" + saved.getAddressId() + "/edit").with(user(customer)))
                .andExpect(status().isOk()).andExpect(view().name("customer/address-form"))
                .andExpect(model().attribute("addressForm", hasProperty("street", equalTo("Địa chỉ đầu"))))
                .andExpect(content().string(containsString("id=\"province\"")));
        // Detached principal mimics an existing login session after a profile update.
        em.detach(customer);
        userService.updateProfile(customer.getUserId(), "Tên hồ sơ mới", "0901777777", null);
        mvc.perform(get("/profile").with(user(customer))).andExpect(status().isOk())
                .andExpect(model().attribute("user", hasProperty("fullName", equalTo("Tên hồ sơ mới"))));
        mvc.perform(get("/profile/address/new").with(user(customer))).andExpect(status().isOk())
                .andExpect(model().attribute("addressForm", hasProperty("receiverName", equalTo("Tên hồ sơ mới"))));
    }

    @Test void editingAndDeletingAddressNeverRewriteAnExistingOrdersDeliverySnapshot() {
        var saved = addressService.add(customer.getUserId(), form("Địa chỉ ban đầu", false));
        var request = request();
        request.setOrderType(OrderType.DELIVERY);
        request.setReceiverName(saved.getReceiverName()); request.setReceiverPhone(saved.getPhone());
        request.setProvince(saved.getProvince()); request.setDistrict(saved.getDistrict());
        request.setWard(saved.getWard()); request.setStreet(saved.getStreet());
        var order = orders.placeOrder(customer.getUserId(), request);
        var shipping = order.getShippingAddress();
        addressService.update(customer.getUserId(), saved.getAddressId(), form("Địa chỉ mới", true));
        addressService.delete(customer.getUserId(), saved.getAddressId());
        em.flush(); em.refresh(order);
        assertEquals(shipping, order.getShippingAddress());
        assertEquals("Người nhận", order.getReceiverName());
        assertEquals("0901000001", order.getReceiverPhone());
    }
}
