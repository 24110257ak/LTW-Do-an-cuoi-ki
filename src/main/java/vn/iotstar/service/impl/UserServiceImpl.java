package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.util.List;
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

    // BỘ NHỚ TẠM (RAM) LƯU TRỮ THÔNG TIN ĐĂNG KÝ CHƯA XÁC THỰC OTP
    private final Map<String, RegisterRequest> pendingRegistrations = new ConcurrentHashMap<>();

    @Override
    public UserSummaryResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Tên đăng nhập đã được sử dụng!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Địa chỉ email đã được sử dụng!");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Số điện thoại đã được đăng ký!");
        }

        // 1. CHỈ LƯU TẠM VÀO BỘ NHỚ RAM, TUYỆT ĐỐI KHÔNG LƯU VÀO DATABASE
        pendingRegistrations.put(email, request);

        // 2. Tạo và gửi mã OTP qua email (5 phút)
        otpService.generateAndSendOtp(email, OtpPurpose.REGISTER);

        // 3. Trả về thông tin giả định (chưa có ID) để API không bị lỗi
        return UserSummaryResponse.builder()
                .userId(null) // Chưa lưu DB nên chưa có ID
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
        boolean verified = otpService.verifyOtp(email, otpCode, OtpPurpose.REGISTER);
        if (verified) {
            // Kiểm tra xem user có đang nằm trong bộ nhớ tạm (đăng ký mới) không
            RegisterRequest pendingRequest = pendingRegistrations.get(email);
            
            if (pendingRequest != null) {
                // LÚC NÀY MỚI LƯU VÀO DATABASE VÌ OTP ĐÃ ĐÚNG
                Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                        .orElseGet(() -> roleRepository.save(Role.builder().roleName("CUSTOMER").build()));

                User user = User.builder()
                        .username(pendingRequest.getUsername().trim())
                        .email(email)
                        .password(passwordEncoder.encode(pendingRequest.getPassword()))
                        .fullName(pendingRequest.getFullName().trim())
                        .phone(pendingRequest.getPhone() != null ? pendingRequest.getPhone().trim() : null)
                        .role(customerRole)
                        .isActive(true)
                        .isVerified(true) // Đã verify OTP thành công
                        .build();

                user = userRepository.save(user);

                // Tạo giỏ hàng cho user
                Cart cart = Cart.builder().user(user).build();
                cartRepository.save(cart);

                // Xóa rác khỏi bộ nhớ tạm
                pendingRegistrations.remove(email);
            } else {
                // Fallback: Xử lý cho luồng tài khoản cũ (đã có trong DB nhưng chưa verify)
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "email", email));
                user.setIsVerified(true);
                userRepository.save(user);
            }
        }
        return verified;
    }

    @Override
    public void resendOtp(String email) {
        // Ưu tiên kiểm tra trong RAM trước (tài khoản đang chờ xác thực)
        if (pendingRegistrations.containsKey(email)) {
            otpService.generateAndSendOtp(email, OtpPurpose.REGISTER);
            return;
        }

        // Nếu không có trong RAM, kiểm tra trong DB
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "email", email));
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            throw new BadRequestException("Tài khoản của bạn đã được xác thực trước đó!");
        }
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.REGISTER);
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

    @Override
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<User> searchUsersAdmin(Integer roleId, String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return userRepository.searchUsersAdmin(roleId, cleanKeyword, pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
        user.setIsActive(user.getIsActive() == null || !user.getIsActive());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateUserRole(Long userId, Integer roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vai trò", "id", roleId));
        user.setRole(role);
        userRepository.save(user);
    }

    @Override
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    public long countUsers() {
        return userRepository.count();
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