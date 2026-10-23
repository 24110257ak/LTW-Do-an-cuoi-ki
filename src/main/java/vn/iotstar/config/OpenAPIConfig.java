package vn.iotstar.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TechChain Store - Chuỗi Cửa Hàng Thiết Bị Điện Tử & Logistics",
                version = "v1.0",
                description = "Hệ thống RESTful API kết hợp xác thực bảo mật Bearer JWT. Tích hợp quản trị phân tán đa chi nhánh, giải thuật điều phối kho Smart Order Routing, tính toán giá vốn COGS & lợi nhuận gộp theo thời gian thực. Đồ án môn Lập trình Web - Nhóm 24 HCMUTE (GVHD: ThS. Nguyễn Hữu Trung).",
                contact = @Contact(
                        name = "Nhóm 24 HCMUTE - Mai Huy Nhật, Trần Vũ Anh Khoa, Đặng Nhật Phúc",
                        email = "24110257@student.hcmute.edu.vn",
                        url = "https://github.com/24110257ak/LTW-Do-an-cuoi-ki"
                ),
                license = @License(
                        name = "Apache License 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Server Cục Bộ (Local Development Server)")
        },
        security = {
                @SecurityRequirement(name = "BearerAuth")
        }
)
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Nhập chuỗi JWT Token sau khi gọi endpoint POST /api/auth/login. Ví dụ: eyJhbGciOiJIUzI1NiJ9..."
)
public class OpenAPIConfig {
}
