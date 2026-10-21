package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.otp-expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${spring.mail.username:no-reply@electronics.store.vn}")
    private String fromEmail;

    @Override
    @Transactional
    public String generateAndSendOtp(String email, OtpPurpose purpose) {
        // Vô hiệu hóa các OTP cũ của email này với cùng mục đích
        otpTokenRepository.invalidateOldTokens(email, purpose);

        // Sinh mã OTP 6 chữ số ngẫu nhiên an toàn
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        String otpCode = String.valueOf(code);

        // Lưu vào database
        OtpToken token = OtpToken.builder()
                .email(email)
                .otpCode(otpCode)
                .purpose(purpose)
                .isUsed(false)
                .expiredAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .user(userRepository.findByEmail(email).orElse(null))
                .build();
        otpTokenRepository.save(token);

        // Gửi email qua JavaMailSender (kèm fallback log ra console nếu chưa cấu hình SMTP thật)
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
            log.info("Đã gửi OTP thành công tới email: {}", email);
        } catch (Exception e) {
            log.warn("Không thể gửi email OTP (chưa cấu hình SMTP thực tế): {}. [MÃ OTP TEST DÀNH CHO PHÁT TRIỂN: {}]", e.getMessage(), otpCode);
        }

        return otpCode;
    }

    @Override
    @Transactional
    public boolean verifyOtp(String email, String otpCode, OtpPurpose purpose) {
        OtpToken token = otpTokenRepository.findTopByEmailAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy mã OTP hợp lệ hoặc mã đã hết hạn!"));

        if (token.getExpiredAt().isBefore(LocalDateTime.now())) {
            token.setIsUsed(true);
            otpTokenRepository.save(token);
            throw new BadRequestException("Mã OTP đã hết hiệu lực. Vui lòng yêu cầu mã mới!");
        }

        if (!token.getOtpCode().equals(otpCode.trim())) {
            throw new BadRequestException("Mã OTP không chính xác. Vui lòng kiểm tra lại!");
        }

        token.setIsUsed(true);
        otpTokenRepository.save(token);
        return true;
    }
}

