package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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

    // Chưa xác thực OTP thì không tạo users/carts. Không giữ mật khẩu gốc trong RAM.
    private final Map<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();

    @Value("${app.mail.pending-registration-minutes:30}")
    private int pendingRegistrationMinutes;

    private record PendingRegistration(String username, String email, String encodedPassword,
                                       String fullName, String phone, LocalDateTime expiresAt) {
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void clearExpiredRegistrations() {
        LocalDateTime now = LocalDateTime.now();
        pendingRegistrations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void checkRegistrationAvailable(String username, String email, String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Tên đăng nhập đã được sử dụng!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Địa chỉ email đã được sử dụng!");
        }
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Số điện thoại đã được đăng ký!");
        }
    }

    @Override
    @Transactional
    public UserSummaryResponse register(RegisterRequest request) {
        clearExpiredRegistrations();
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;
        checkRegistrationAvailable(username, email, phone);

        PendingRegistration pending = new PendingRegistration(username, email,
                passwordEncoder.encode(request.getPassword()), request.getFullName().trim(), phone,
                LocalDateTime.now().plusMinutes(pendingRegistrationMinutes));
        otpService.generateAndSendOtp(email, OtpPurpose.REGISTER);
        PendingRegistration previous = pendingRegistrations.put(email, pending);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    if (previous == null) pendingRegistrations.remove(pending.email(), pending);
                    else pendingRegistrations.replace(pending.email(), pending, previous);
                }
            }
        });


        return UserSummaryResponse.builder()
                .username(username).email(email).fullName(pending.fullName()).phone(phone)
                .role("CUSTOMER").isActive(true).isVerified(false).build();
    }

    @Override
    @Transactional
    public boolean verifyAccount(String email, String otpCode) {
        email = normalizeEmail(email);
        clearExpiredRegistrations();
        boolean verified = otpService.verifyOtp(email, otpCode, OtpPurpose.REGISTER);
        if (verified) {
            PendingRegistration pending = pendingRegistrations.get(email);
            if (pending != null) {
                // Kiểm tra lại: username/phone có thể đã được người khác đăng ký trong lúc chờ OTP.
                checkRegistrationAvailable(pending.username(), email, pending.phone());
                Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                        .orElseGet(() -> roleRepository.save(Role.builder().roleName("CUSTOMER").build()));
                User user = userRepository.save(User.builder()
                        .username(pending.username()).email(email).password(pending.encodedPassword())
                        .fullName(pending.fullName()).phone(pending.phone()).role(customerRole)
                        .isActive(true).isVerified(true).build());
                cartRepository.save(Cart.builder().user(user).build());
                // Chỉ bỏ dữ liệu tạm sau khi cả user, cart và OTP được commit thành công.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pendingRegistrations.remove(pending.email(), pending);
                    }
                });
            } else {
                // Tương thích tài khoản chưa xác thực được tạo bởi phiên bản cũ.
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new BadRequestException(
                                "Phiên đăng ký đã hết hạn hoặc ứng dụng đã khởi động lại. Vui lòng đăng ký lại."));
                user.setIsVerified(true);
                userRepository.save(user);
            }
        }
        return verified;
    }

    @Override
    public void resendOtp(String email) {
        email = normalizeEmail(email);
        clearExpiredRegistrations();
        if (pendingRegistrations.containsKey(email)) {
            otpService.generateAndSendOtp(email, OtpPurpose.REGISTER);
            return;
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Phiên đăng ký không còn hiệu lực. Vui lòng đăng ký lại."));
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
