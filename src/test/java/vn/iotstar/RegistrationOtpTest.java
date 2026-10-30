package vn.iotstar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import vn.iotstar.dto.request.RegisterRequest;
import vn.iotstar.entity.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.*;
import vn.iotstar.service.UserService;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registration_otp;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@ActiveProfiles("test")
class RegistrationOtpTest {
    private static final AtomicLong PHONE = new AtomicLong(901000000);
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired CartRepository carts;
    @Autowired RoleRepository roles;
    @Autowired OtpTokenRepository tokens;
    @Autowired OtpRateLimitRepository rates;
    @Autowired PasswordEncoder encoder;

    RegisterRequest request() {
        String username = "otp_" + UUID.randomUUID().toString().replace("-", "");
        return RegisterRequest.builder().username(username).email(username + "@example.test")
                .fullName("Người đăng ký").phone("0" + PHONE.incrementAndGet()).password("Password@123").build();
    }

    OtpToken otp(String email) {
        return tokens.findAll().stream().filter(t -> t.getEmail().equals(email) && !t.getIsUsed())
                .findFirst().orElseThrow();
    }

    @Test void registrationOnlyCreatesOtpUntilVerificationThenCreatesOneUserAndCart() {
        var req = request();
        String email = req.getEmail();
        long userCount = users.count(), cartCount = carts.count();
        req.setEmail("  " + email.toUpperCase() + "  ");
        var response = service.register(req);
        assertNull(response.getUserId());
        assertFalse(response.getIsVerified());
        assertEquals(email, response.getEmail());
        assertEquals(userCount, users.count());
        assertEquals(cartCount, carts.count());
        assertNull(otp(email).getUser());

        // Dữ liệu chờ được sao chép, không giữ RegisterRequest/mật khẩu gốc.
        req.setPassword("Changed@123");
        assertTrue(service.verifyAccount(" " + email.toUpperCase() + " ", otp(email).getOtpCode()));
        User user = users.findByEmail(email).orElseThrow();
        assertTrue(user.getIsVerified());
        assertTrue(encoder.matches("Password@123", user.getPassword()));
        assertEquals(userCount + 1, users.count());
        assertEquals(cartCount + 1, carts.count());
        assertTrue(carts.findByUserUserId(user.getUserId()).isPresent());
        assertThrows(BadRequestException.class, () -> service.verifyAccount(email, "123456"));
    }

    @Test void wrongOtpDoesNotCreateAccountAndCorrectOtpCanStillBeUsed() {
        var req = request(); service.register(req);
        assertThrows(BadRequestException.class, () -> service.verifyAccount(req.getEmail(), "000000"));
        assertFalse(users.existsByEmail(req.getEmail()));
        assertTrue(service.verifyAccount(req.getEmail(), otp(req.getEmail()).getOtpCode()));
    }

    @Test void resendWorksForPendingRegistrationAndInvalidatesPreviousToken() {
        var req = request(); service.register(req);
        Long oldId = otp(req.getEmail()).getTokenId();
        allowResend(req.getEmail());
        service.resendOtp(req.getEmail().toUpperCase());
        assertTrue(tokens.findById(oldId).orElseThrow().getIsUsed());
        assertFalse(users.existsByEmail(req.getEmail()));
        assertTrue(service.verifyAccount(req.getEmail(), otp(req.getEmail()).getOtpCode()));
        assertThrows(BadRequestException.class, () -> service.resendOtp(req.getEmail()));
    }

    @Test void expiredOtpDoesNotCreateAccountAndResendAllowsRetry() {
        var req = request(); service.register(req);
        var token = otp(req.getEmail());
        token.setExpiredAt(LocalDateTime.now().minusMinutes(1)); tokens.save(token);
        assertThrows(BadRequestException.class, () -> service.verifyAccount(req.getEmail(), token.getOtpCode()));
        assertFalse(users.existsByEmail(req.getEmail()));
        allowResend(req.getEmail());
        service.resendOtp(req.getEmail());
        assertTrue(service.verifyAccount(req.getEmail(), otp(req.getEmail()).getOtpCode()));
    }

    @Test void expiredPendingRegistrationRequiresNewRegistration() {
        var target = AopTestUtils.getUltimateTargetObject(service);
        ReflectionTestUtils.setField(target, "pendingRegistrationMinutes", 0);
        var req = request();
        try {
            service.register(req);
            var error = assertThrows(BadRequestException.class,
                    () -> service.verifyAccount(req.getEmail(), otp(req.getEmail()).getOtpCode()));
            assertTrue(error.getMessage().contains("đăng ký lại"));
            assertFalse(users.existsByEmail(req.getEmail()));
            assertThrows(BadRequestException.class, () -> service.resendOtp(req.getEmail()));
        } finally {
            ReflectionTestUtils.setField(target, "pendingRegistrationMinutes", 30);
        }
    }

    @Test void verificationRechecksUsernameAndRollsBackOtpOnConflict() {
        var req = request(); service.register(req);
        Role role = roles.findByRoleName("CUSTOMER").orElseGet(() -> roles.save(Role.builder().roleName("CUSTOMER").build()));
        users.save(User.builder().username(req.getUsername()).email("taken_" + req.getEmail())
                .password("encoded").phone("1" + req.getPhone()).fullName("Other user").role(role).build());
        var token = otp(req.getEmail());
        assertThrows(BadRequestException.class, () -> service.verifyAccount(req.getEmail(), token.getOtpCode()));
        assertFalse(users.existsByEmail(req.getEmail()));
        assertFalse(tokens.findById(token.getTokenId()).orElseThrow().getIsUsed());
    }

    @Test void existingUnverifiedAccountCanStillResendAndVerifyWithoutDuplicateCart() {
        var req = request();
        Role role = roles.findByRoleName("CUSTOMER").orElseGet(() -> roles.save(Role.builder().roleName("CUSTOMER").build()));
        User user = users.save(User.builder().username(req.getUsername()).email(req.getEmail())
                .password("encoded").phone(req.getPhone()).fullName("Legacy").role(role).build());
        carts.save(Cart.builder().user(user).build());
        long count = carts.count();
        allowResend(req.getEmail());
        service.resendOtp(req.getEmail());
        assertTrue(service.verifyAccount(req.getEmail(), otp(req.getEmail()).getOtpCode()));
        assertTrue(users.findById(user.getUserId()).orElseThrow().getIsVerified());
        assertEquals(count, carts.count());
    }

    @Test void concurrentVerificationCreatesOnlyOneAccountAndCart() throws Exception {
        var req = request(); service.register(req);
        String code = otp(req.getEmail()).getOtpCode();
        long userCount = users.count(), cartCount = carts.count();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> verify = () -> {
            start.await();
            try { return service.verifyAccount(req.getEmail(), code); }
            catch (BadRequestException ex) { return false; }
        };
        try {
            var first = pool.submit(verify); var second = pool.submit(verify); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(userCount + 1, users.count());
            assertEquals(cartCount + 1, carts.count());
        } finally { pool.shutdownNow(); }
    }
    private void allowResend(String email) {
        rates.findById(vn.iotstar.service.OtpRateLimitService.emailKey(email,vn.iotstar.enums.OtpPurpose.REGISTER)).ifPresent(row->{row.setNextSendAt(LocalDateTime.now().minusSeconds(1));rates.save(row);});
    }
    @Test void blockedDuplicateRegistrationKeepsTheOriginalPendingCredentials() {
        var original=request();service.register(original);String code=otp(original.getEmail()).getOtpCode();
        var duplicate=request();duplicate.setEmail(original.getEmail());
        assertThrows(vn.iotstar.exception.OtpRateLimitException.class,()->service.register(duplicate));
        service.verifyAccount(original.getEmail(),code);var saved=users.findByEmail(original.getEmail()).orElseThrow();
        assertEquals(original.getUsername(),saved.getUsername());assertTrue(encoder.matches(original.getPassword(),saved.getPassword()));
    }
}
