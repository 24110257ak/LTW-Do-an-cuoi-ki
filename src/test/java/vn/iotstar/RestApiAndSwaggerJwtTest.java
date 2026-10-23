package vn.iotstar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.LoginRequest;
import vn.iotstar.entity.Category;
import vn.iotstar.entity.Role;
import vn.iotstar.entity.User;
import vn.iotstar.repository.CategoryRepository;
import vn.iotstar.repository.RoleRepository;
import vn.iotstar.repository.UserRepository;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RestApiAndSwaggerJwtTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;

    @BeforeEach
    void setUp() {
        Role roleAdmin = roleRepository.save(Role.builder().roleName("ADMIN").build());
        roleRepository.save(Role.builder().roleName("CUSTOMER").build());

        adminUser = userRepository.save(User.builder()
                .username("admin_api_test")
                .password(passwordEncoder.encode("AdminPass123!"))
                .fullName("Quản Trị Viên API")
                .email("admin_api@techchain.vn")
                .phone("0909090909")
                .role(roleAdmin)
                .isActive(true)
                .isVerified(true)
                .build());

        categoryRepository.save(Category.builder()
                .categoryName("Điện Thoại Thông Minh")
                .slug("dien-thoai-thong-minh-" + System.currentTimeMillis())
                .isActive(true)
                .build());
    }

    @Test
    @DisplayName("1. POST /api/auth/login: Xác thực thành công cấp phát Bearer JWT Token")
    void testAuthLoginAndReceiveJwt() throws Exception {
        LoginRequest req = new LoginRequest("admin_api_test", "AdminPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("admin_api_test"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("2. GET /api/auth/me: Truy cập endpoint yêu cầu bảo mật với Header Authorization: Bearer JWT")
    void testAccessProtectedEndpointWithJwt() throws Exception {
        // Đăng nhập lấy JWT
        LoginRequest req = new LoginRequest("admin_api_test", "AdminPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        String jsonResponse = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(jsonResponse).path("data").path("token").asText();
        assertNotNull(token);

        // Gọi endpoint bảo mật với token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("admin_api_test"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("3. GET /api/categories: Endpoint công khai truy vấn danh mục không cần Token")
    void testPublicCategoryApi() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("4. GET /api/admin/financial/overview: Truy vấn báo cáo tài chính COGS & Lợi nhuận bằng quyền ADMIN")
    void testFinancialOverviewApiWithAdminRole() throws Exception {
        LoginRequest req = new LoginRequest("admin_api_test", "AdminPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/financial/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRevenue").exists())
                .andExpect(jsonPath("$.data.totalCost").exists())
                .andExpect(jsonPath("$.data.totalGrossProfit").exists());
    }

    @Test
    @DisplayName("5. GET /api-docs: Swagger OpenAPI 3.0 tự động sinh tài liệu chuẩn xác kèm Security Bearer")
    void testOpenApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", notNullValue()))
                .andExpect(jsonPath("$.info.title", containsString("TechChain Store")))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"));
    }
}
