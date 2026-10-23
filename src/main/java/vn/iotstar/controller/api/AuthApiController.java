package vn.iotstar.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.dto.request.LoginRequest;
import vn.iotstar.dto.request.RegisterRequest;
import vn.iotstar.dto.response.ApiResponse;
import vn.iotstar.dto.response.AuthResponse;
import vn.iotstar.dto.response.UserSummaryResponse;
import vn.iotstar.entity.User;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.UserService;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Xác thực & Bảo mật (Auth API)", description = "Các endpoint đăng nhập cấp phát Bearer JWT, đăng ký và truy xuất hồ sơ tài khoản")
public class AuthApiController {

    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực tài khoản bằng username/email và mật khẩu, trả về JWT Token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("API login request for user: {}", request.getUsernameOrEmail());
        AuthResponse response = userService.authenticateAndGetToken(request.getUsernameOrEmail(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", response));
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới", description = "Đăng ký tài khoản khách hàng mới và gửi OTP kích hoạt qua Email")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("API register request for username: {}", request.getUsername());
        UserSummaryResponse summary = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đăng ký thành công! Vui lòng kiểm tra email để xác thực OTP.", summary));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "Yêu cầu Bearer JWT token trong Header Authorization")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new BadRequestException("Chưa xác thực hoặc token không hợp lệ!");
        }

        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng hiện tại"));

        UserSummaryResponse summary = UserSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() != null ? user.getRole().getRoleName() : "CUSTOMER")
                .isActive(user.getIsActive())
                .isVerified(user.getIsVerified())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin tài khoản thành công", summary));
    }
}
