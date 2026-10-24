package vn.iotstar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SystemFixesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Admin Inventory Logs renders 200 without duplicate ORDER BY SQL error")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAdminInventoryLogsRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/admin/inventory/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/logs"))
                .andExpect(model().attributeExists("logsPage", "branches", "variants"));
    }

    @Test
    @DisplayName("Admin Inventory root path renders 200")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAdminInventoryRootPathRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/admin/inventory"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/inventory/logs"));
    }

    @Test
    @DisplayName("Shipper Orders renders 200 for shipper user")
    @WithMockUser(username = "shipper1", roles = {"SHIPPER"})
    void testShipperOrdersRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/shipper/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/orders"))
                .andExpect(model().attributeExists("orderPage", "selectedStatus"));
    }

    @Test
    @DisplayName("Shipper root path renders 200")
    @WithMockUser(username = "shipper1", roles = {"SHIPPER"})
    void testShipperRootPathRendersSuccessfully() throws Exception {
        mockMvc.perform(get("/shipper"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/orders"));
    }
}
