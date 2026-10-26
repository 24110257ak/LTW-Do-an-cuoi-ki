package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.config.security.JwtService;
import vn.iotstar.dto.request.RegisterRequest;
import vn.iotstar.dto.response.AuthResponse;
import vn.iotstar.dto.response.UserSummaryResponse;
import vn.iotstar.entity.Cart;
import vn.iotstar.entity.Role;
import vn.iotstar.entity.User;
import vn.iotstar.enums.OtpPurpose;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.CartRepository;
import vn.iotstar.repository.RoleRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.OtpService;
import vn.iotstar.service.UserService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // LƯU TRỮ TẠM VÀO RAM: Key = email, Value = Dữ liệu người dùng nhập
    private final Map<String, RegisterRequest> pendingRegistrations = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public UserSummaryResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // 1. Kiểm tra xem Email, SĐT, Username đã có trong DB chưa
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Tên đăng nhập đã được sử dụng!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Địa chỉ email đã được sử dụng!");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Số điện thoại đã được đăng ký!");
        }

        // 2. KHÔNG LƯU DATABASE. CHỈ LƯU NHÁP VÀO RAM
        pendingRegistrations.put(email, request);

        // 3. Gọi hàm tạo OTP (OTP vẫn lưu vào DB an toàn nhờ file OtpServiceImpl)
        otpService.generateAndSendOtp(email, OtpPurpose.REGISTER);

        // 4. Trả về response ảo để Controller không bị lỗi (chưa có userId)
        return UserSummaryResponse.builder()
                .username(request.getUsername().trim())
                .email(email)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .role("CUSTOMER")
                .isActive(true)
                .isVerified(false)
                .build();
    }

    @Override
    @Transactional
    public boolean verifyAccount(String email, String otpCode) {
        String lowerEmail = email.trim().toLowerCase();
        
        // Xác thực mã OTP
        boolean verified = otpService.verifyOtp(lowerEmail, otpCode, OtpPurpose.REGISTER);
        if (verified) {
            // 1. Lấy dữ liệu nháp từ RAM ra
            RegisterRequest request = pendingRegistrations.get(lowerEmail);
            
            if (request != null) {
                // Kiểm tra lại lần cuối để tránh xung đột (Race condition)
                if (userRepository.existsByEmail(lowerEmail)) {
                    pendingRegistrations.remove(lowerEmail);
                    throw new BadRequestException("Email đã được sử dụng!");
                }

                // 2. BÂY GIỜ MỚI LƯU NGƯỜI DÙNG XUỐNG DATABASE
                Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                        .orElseGet(() -> roleRepository.save(Role.builder().roleName("CUSTOMER").build()));

                User user = User.builder()
                        .username(request.getUsername().trim())
                        .email(lowerEmail)
                        .fullName(request.getFullName().trim())
                        .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                        .password(passwordEncoder.encode(request.getPassword()))
                        .role(customerRole)
                        .isActive(true)
                        .isVerified(true) // Đã kích hoạt OTP thành công
                        .build();

                user = userRepository.save(user);

                // Khởi tạo giỏ hàng cho user mới
                Cart cart = Cart.builder().user(user).build();
                cartRepository.save(cart);

                // Xóa rác trong RAM
                pendingRegistrations.remove(lowerEmail);
            } else {
                // Fallback: Xử lý an toàn nếu user đăng ký bằng code cũ trước khi đổi logic
                User user = userRepository.findByEmail(lowerEmail)
                        .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "email", lowerEmail));
                if (!user.getIsVerified()) {
                    user.setIsVerified(true);
                    userRepository.save(user);
                }
            }
        }
        return verified;
    }

    @Override
    public void resendOtp(String email) {
        String lowerEmail = email.trim().toLowerCase();
        // Cho phép gửi lại OTP nếu email có trong RAM nháp HOẶC đã có trong DB
        if (pendingRegistrations.containsKey(lowerEmail) || userRepository.existsByEmail(lowerEmail)) {
            otpService.generateAndSendOtp(lowerEmail, OtpPurpose.REGISTER);
        } else {
            throw new ResourceNotFoundException("Người dùng", "email", lowerEmail);
        }
    }

    @Override
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "email", email));
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.FORGOT_PASSWORD);
    }

    @Override
    @Transactional
    public boolean resetPassword(String email, String otpCode, String newPassword) {
        boolean verified = otpService.verifyOtp(email, otpCode, OtpPurpose.FORGOT_PASSWORD);
        if (verified) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "email", email));
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
        }
        return verified;
    }

    @Override
    public AuthResponse authenticateAndGetToken(String usernameOrEmail, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(usernameOrEmail, password)
        );

        User user = (User) auth.getPrincipal();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa bởi Quản trị viên!");
        }
        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new BadRequestException("Tài khoản chưa được kích hoạt qua mã OTP Email. Vui lòng xác thực tài khoản!");
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().getRoleName())
                .build();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không chính xác!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, String fullName, String phone, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone.trim());
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            user.setAvatarUrl(avatarUrl.trim());
        }
        userRepository.save(user);
    }

    private UserSummaryResponse toSummaryResponse(User user) {
        return UserSummaryResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() != null ? user.getRole().getRoleName() : null)
                .isActive(user.getIsActive())
                .isVerified(user.getIsVerified())
                .build();
    }
}