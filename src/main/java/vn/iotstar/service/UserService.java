package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.dto.request.RegisterRequest;
import vn.iotstar.dto.response.AuthResponse;
import vn.iotstar.dto.response.UserSummaryResponse;
import vn.iotstar.entity.Role;
import vn.iotstar.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    UserSummaryResponse register(RegisterRequest request);
    boolean verifyAccount(String email, String otpCode);
    void resendOtp(String email);
    void forgotPassword(String email);
    boolean resetPassword(String email, String otpCode, String newPassword);
    AuthResponse authenticateAndGetToken(String usernameOrEmail, String password);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    void changePassword(Long userId, String oldPassword, String newPassword);
    void updateProfile(Long userId, String fullName, String phone, String avatarUrl);

    // Admin methods
    Page<User> getAllUsers(Pageable pageable);
    Page<User> searchUsersAdmin(Integer roleId, String keyword, Pageable pageable);
    void toggleUserStatus(Long userId);
    void updateUserRole(Long userId, Integer roleId);
    List<Role> getAllRoles();
    long countUsers();
}
