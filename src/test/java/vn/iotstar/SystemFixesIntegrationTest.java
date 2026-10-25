package vn.iotstar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.service.CloudinaryService;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SystemFixesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CloudinaryService cloudinaryService;

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

    @Test
    @DisplayName("Static upload resource /uploads/products/iphone-16-pro-max.png is publicly accessible")
    void testUploadsStaticResourceAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/uploads/products/iphone-16-pro-max.png"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Dynamic upload saves file locally and allows deletion when Cloudinary has dummy credentials")
    void testDynamicFileUploadAndDeletion() {
        MockMultipartFile testFile = new MockMultipartFile(
                "file",
                "test-image.png",
                "image/png",
                "fake image content bytes".getBytes()
        );

        String savedPath = cloudinaryService.uploadFile(testFile, "test-unit");
        assertTrue(savedPath.startsWith("/uploads/test-unit/"));
        assertTrue(savedPath.endsWith(".png"));

        java.nio.file.Path localFilePath = Paths.get(savedPath.substring(1));
        assertTrue(Files.exists(localFilePath));

        // Test delete
        cloudinaryService.deleteFile(savedPath);
        assertFalse(Files.exists(localFilePath));
    }
}
