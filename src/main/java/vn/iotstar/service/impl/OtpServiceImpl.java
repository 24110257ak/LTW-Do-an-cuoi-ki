package vn.iotstar.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.OtpToken;
import vn.iotstar.enums.OtpPurpose;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.OtpTokenRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.OtpService;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final vn.iotstar.service.OtpRateLimitService rateLimits;
    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.otp-expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.mail.terminal-mode:true}")
    private boolean terminalMode;

    @Value("${spring.mail.username:your_email@gmail.com}")
    private String fromEmail;

    public OtpServiceImpl(OtpTokenRepository otpTokenRepository,
                          UserRepository userRepository,
                          ObjectProvider<JavaMailSender> mailSenderProvider,
                          vn.iotstar.service.OtpRateLimitService rateLimits) {
        this.rateLimits = rateLimits;
        this.otpTokenRepository = otpTokenRepository;
        this.userRepository = userRepository;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    @Transactional
    public String generateAndSendOtp(String email, OtpPurpose purpose) {
        email = rateLimits.normalize(email);
        rateLimits.beforeSend(email, purpose);
        // 1. Vô hiệu hóa các OTP cũ của email này với cùng mục đích
        otpTokenRepository.invalidateOldTokens(email, purpose);

        // 2. Sinh mã OTP 6 chữ số ngẫu nhiên an toàn
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        String otpCode = String.valueOf(code);

        // 3. Lưu vào database
        OtpToken token = OtpToken.builder()
                .email(email)
                .otpCode(otpCode)
                .purpose(purpose)
                .isUsed(false)
                .expiredAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .user(userRepository.findByEmail(email).orElse(null))
                .build();
        otpTokenRepository.save(token);

        // 4. Kiểm tra chế độ gửi: Terminal Mode (mặc định) vs Real SMTP
        boolean isDummyConfig = fromEmail == null || fromEmail.contains("your_email") || fromEmail.contains("example.com");

        if (terminalMode || isDummyConfig) {
            // Chế độ in Terminal: Nhanh 0ms, không phụ thuộc tài khoản Google, không nghẽn mạng
            printOtpToTerminal(email, otpCode, purpose, "DEV / TERMINAL MODE (Chế độ phát triển & test)");
            return otpCode;
        }

        // Nếu người dùng cấu hình SMTP thật và tắt terminalMode: thử gửi qua JavaMailSender
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(email);
                message.setSubject("Mã xác thực OTP - Chuỗi Cửa Hàng Điện Tử ESC");
                message.setText(String.format(
                        "Xin chào,\n\nMã xác thực OTP của bạn cho mục đích [%s] là: %s\n" +
                        "Mã có hiệu lực trong vòng %d phút.\n\n" +
                        "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.\n" +
                        "Trân trọng,\nĐội ngũ ESC.",
                        purpose.name(), otpCode, otpExpiryMinutes
                ));
                mailSender.send(message);
                log.info("Đã gửi email OTP thành công tới: {}", email);
            } catch (Exception e) {
                // Fallback nếu kết nối Google SMTP thất bại (sai pass, chặn 2FA, mất mạng...)
                log.warn("Gửi email SMTP thất bại ({}). Kích hoạt Terminal Fallback!", e.getMessage());
                printOtpToTerminal(email, otpCode, purpose, "FALLBACK (Lỗi kết nối SMTP Google)");
            }
        } else {
            printOtpToTerminal(email, otpCode, purpose, "TERMINAL (Không tìm thấy bean JavaMailSender)");
        }

        return otpCode;
    }

    private void printOtpToTerminal(String email, String otpCode, OtpPurpose purpose, String modeNote) {
        String border = "================================================================================";
        System.out.println("\n" + border);
        System.out.println("                 [ESC.STORE] THÔNG BÁO MÃ XÁC THỰC OTP");
        System.out.println(border);
        System.out.printf("  Chế độ             : %s\n", modeNote);
        System.out.printf("  Mục đích (Purpose) : %s\n", purpose.name());
        System.out.printf("  Gửi tới (Email)    : %s\n", email);
        System.out.printf("  Thời hạn hiệu lực  : %d phút\n", otpExpiryMinutes);
        System.out.println("  ----------------------------------------------------------------------------");
        System.out.printf("  >>> MÃ OTP XÁC THỰC: [  %s  ] <<<\n", otpCode);
        System.out.println("  ----------------------------------------------------------------------------");
        System.out.println("  (Sử dụng mã OTP trên để xác nhận tài khoản / đặt lại mật khẩu trực tiếp)");
        System.out.println(border + "\n");

        log.info("📧 [OTP NOTIFICATION] To: {} | Purpose: {} | OTP CODE: [{}] (Valid for {} mins)",
                email, purpose.name(), otpCode, otpExpiryMinutes);
    }

    @Override
    @Transactional
    public boolean verifyOtp(String email, String otpCode, OtpPurpose purpose) {
        email = rateLimits.normalize(email);
        rateLimits.beforeVerification(email, purpose);
        OtpToken token = otpTokenRepository.findTopByEmailAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy mã OTP hợp lệ hoặc mã đã hết hạn!"));

        if (!LocalDateTime.now().isBefore(token.getExpiredAt())) {
            token.setIsUsed(true);
            otpTokenRepository.save(token);
            throw new BadRequestException("Mã OTP đã hết hiệu lực. Vui lòng yêu cầu mã mới!");
        }

        if (otpCode == null || !token.getOtpCode().equals(otpCode.trim())) {
            throw new BadRequestException("Mã OTP không chính xác. Vui lòng kiểm tra lại!");
        }

        token.setIsUsed(true);
        otpTokenRepository.save(token);
        rateLimits.successfulVerification(email, purpose);
        return true;
    }
}
