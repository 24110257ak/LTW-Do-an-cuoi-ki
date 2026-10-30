package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.enums.*;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phuc_integration;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PhucIntegrationTest extends CommerceTestSupport {
    @Autowired MockMvc mvc;
    @BeforeEach void setup() { fixtures(); }

    @Test void providerPageRendersRealEntityFieldsAndCanCreateProvider() throws Exception {
        persist(DeliveryProvider.builder().providerName("Test Carrier").baseFee(new BigDecimal("25000")).build());
        mvc.perform(get("/admin/providers").with(user(admin)))
                .andExpect(status().isOk()).andExpect(view().name("admin/providers/list"))
                .andExpect(content().string(containsString("Test Carrier")))
                .andExpect(content().string(not(containsString("name=\"providerCode\""))))
                .andExpect(content().string(not(containsString("name=\"hotline\""))));
        mvc.perform(post("/admin/providers/save").with(user(admin)).with(csrf())
                        .param("providerName", "New Carrier").param("baseFee", "30000"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/providers"));
    }

    @Test void categoryPageRendersBothImagesAndIconsAndSavesImageUrl() throws Exception {
        persist(Category.builder().categoryName("Image Category").slug("image-category").imageUrl("/uploads/categories/test.png").build());
        persist(Category.builder().categoryName("Icon Category").slug("icon-category").imageUrl("bi bi-phone").build());
        mvc.perform(get("/admin/categories").with(user(admin)))
                .andExpect(status().isOk()).andExpect(view().name("admin/categories/list"))
                .andExpect(content().string(containsString("src=\"/uploads/categories/test.png\"")))
                .andExpect(content().string(containsString("bi bi-phone")))
                .andExpect(content().string(containsString("name=\"imageUrl\"")));
        mvc.perform(post("/admin/categories/save").with(user(admin)).with(csrf())
                        .param("categoryName", "New Category").param("slug", "new-category")
                        .param("imageUrl", "/uploads/categories/new.png"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/categories"));
    }

    @Test void checkoutKeepsSavedAddressInSelectsAndPricingIntegration() throws Exception {
        var defaultAddress = persist(UserAddress.builder().user(customer).receiverName("Người nhận").phone("0901000001")
                .province("TP. Hồ Chí Minh").district("Quận 1").ward("Phường Bến Nghé").street("12 Lê Lợi").isDefault(true).build());
        var otherAddress = persist(UserAddress.builder().user(customer).receiverName("Người nhận thứ hai").phone("0901000002")
                .province("Hà Nội").district("Quận Ba Đình").ward("Phường Phúc Xá").street("20 Phố Huế").build());
        persist(UserAddress.builder().user(admin).receiverName("Địa chỉ riêng tài khoản khác").phone("0901999999")
                .province("Hà Nội").district("Quận Ba Đình").ward("Phường Phúc Xá").street("Địa chỉ bí mật").build());
        var result = mvc.perform(get("/checkout").with(user(customer)))
                .andExpect(status().isOk()).andExpect(view().name("customer/checkout"))
                .andExpect(model().attribute("selectedSavedAddressId", defaultAddress.getAddressId()))
                .andExpect(content().string(containsString("id=\"savedAddressSelect\"")))
                .andExpect(content().string(containsString("data-receiver-name=\"Người nhận thứ hai\"")))
                .andExpect(content().string(not(containsString("Địa chỉ riêng tài khoản khác"))))
                .andExpect(content().string(not(containsString("techchain-theme"))))
                .andExpect(content().string(containsString("value=\"Phường Bến Nghé\"")))
                .andExpect(content().string(containsString("/js/checkout-address.js")))
                .andExpect(content().string(containsString("/js/checkout.js")))
                .andExpect(content().string(containsString("name=\"checkoutToken\"")))
                .andReturn();
        @SuppressWarnings("unchecked")
        var addresses = (List<UserAddress>) result.getModelAndView().getModel().get("savedAddresses");
        assertEquals(2, addresses.size());
        assertTrue(addresses.stream().anyMatch(address -> address.getAddressId().equals(otherAddress.getAddressId())));
    }

    @Test void checkoutValidationKeepsEditedAddressInsteadOfRestoringTheDefault() throws Exception {
        persist(UserAddress.builder().user(customer).receiverName("Người nhận").phone("0901000001")
                .province("TP. Hồ Chí Minh").district("Quận 1").ward("Phường Bến Nghé").street("12 Lê Lợi").isDefault(true).build());
        mvc.perform(post("/checkout/place-order").with(user(customer)).with(csrf())
                        .param("orderType", "DELIVERY").param("receiverName", "Người nhận đã sửa")
                        .param("receiverPhone", "0901222222").param("province", "Hà Nội")
                        .param("district", "Quận Ba Đình").param("ward", "Phường Phúc Xá")
                        .param("street", "999 Địa chỉ mới"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/checkout"))
                .andExpect(model().attributeDoesNotExist("selectedSavedAddressId"))
                .andExpect(content().string(containsString("value=\"999 Địa chỉ mới\"")))
                .andExpect(model().attribute("checkoutRequest", hasProperty("receiverName", equalTo("Người nhận đã sửa"))));
    }

    @Test void apiAddressNamesMatchLegacyBranchesAndShippingFeesWithoutChangingAmounts() {
        inventory.adjustStock(destination.getBranchId(), variant.getVariantId(), 5, StockMovementType.ADJUSTMENT, admin, "TEST", "Stock");
        assertEquals(destination.getBranchId(), pricing.resolveBranch("Thành phố Hồ Chí Minh", "Quận 3", carts.getCartItems(customer.getUserId())).getBranchId());
        destination.setDistrict("TP. Thủ Đức"); em.flush();
        assertEquals(destination.getBranchId(), pricing.resolveBranch("Thành phố Hồ Chí Minh", "Thành phố Thủ Đức", carts.getCartItems(customer.getUserId())).getBranchId());
        assertEquals(new BigDecimal("25000"), pricing.shippingFee("Thành phố Hồ Chí Minh", "Thành phố Thủ Đức", new BigDecimal("2000000"), null));
        assertEquals(new BigDecimal("30000"), pricing.shippingFee("TP.HCM", "Quận 12", new BigDecimal("2000000"), null));
        assertEquals(BigDecimal.ZERO, pricing.shippingFee("Hà Nội", "Quận Ba Đình", new BigDecimal("5000000"), null));
    }
}
