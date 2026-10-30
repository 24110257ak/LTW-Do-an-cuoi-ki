package vn.iotstar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.*;
import vn.iotstar.entity.OtpRateLimit;
import vn.iotstar.enums.OtpPurpose;
import vn.iotstar.exception.*;
import vn.iotstar.repository.OtpRateLimitRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Durable counters commit separately, including when the caller rolls back a wrong OTP. */
@Service @RequiredArgsConstructor
public class OtpRateLimitService {
    private final OtpRateLimitRepository limits;
    private final PlatformTransactionManager transactions;
    @Value("${app.otp.resend-seconds:60}") private int resendSeconds;
    @Value("${app.otp.email-sends-per-hour:5}") private int emailSends;
    @Value("${app.otp.ip-sends-per-hour:20}") private int ipSends;
    @Value("${app.otp.max-attempts:5}") private int maxAttempts;
    @Value("${app.otp.lock-minutes:15}") private int lockMinutes;

    public static String emailKey(String email, OtpPurpose purpose) { return key("email:"+purpose+":"+email); }
    public static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public String normalize(String email) {
        if(email==null || email.isBlank() || email.length()>255) throw new BadRequestException("Email không hợp lệ");
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private OtpRateLimit row(String key, LocalDateTime now) {
        return limits.lock(key).orElseGet(() -> {
            var row=new OtpRateLimit();row.setLimitKey(key);row.setWindowStartedAt(now);
            return limits.saveAndFlush(row);
        });
    }
    private <T> T separateTransaction(Supplier<T> work) {
        var tx=new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // A competing first insert retries in a fresh transaction, under the unique key.
        for(int i=0;i<3;i++) {
            try { return tx.execute(status -> work.get()); }
            catch(DataIntegrityViolationException ex) { if(i==2) throw ex; }
        }
        throw new IllegalStateException();
    }
    private void checkLock(OtpRateLimit row, LocalDateTime now) {
        if(row.getBlockedUntil()!=null) {
            if(now.isBefore(row.getBlockedUntil())) throw new OtpRateLimitException("Đã hết lượt thử OTP. Tạm khóa đến "+row.getBlockedUntil()+"; gửi lại mã không bỏ khóa.");
            row.setBlockedUntil(null);row.setVerificationAttempts(0);
        }
    }
    public void beforeSend(String email, OtpPurpose purpose) {
        String emailKey=emailKey(normalize(email),purpose);
        String ip = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest().getRemoteAddr() : null; // Never trust arbitrary forwarded headers.
        separateTransaction(() -> {
            var now=LocalDateTime.now();var keys=new TreeSet<String>();keys.add(emailKey);
            if(ip!=null) keys.add(key("ip:"+purpose+":"+ip));
            var rows=new HashMap<String,OtpRateLimit>();for(var k:keys)rows.put(k,row(k,now));
            checkLock(rows.get(emailKey),now);
            for(var entry:rows.entrySet()) {
                var row=entry.getValue();boolean emailScope=entry.getKey().equals(emailKey);
                if(!now.isBefore(row.getWindowStartedAt().plusHours(1))) {row.setWindowStartedAt(now);row.setSendCount(0);}
                if(emailScope && row.getNextSendAt()!=null && now.isBefore(row.getNextSendAt()))
                    throw new OtpRateLimitException("Vui lòng chờ ít nhất "+resendSeconds+" giây giữa hai lần gửi OTP.");
                if(row.getSendCount()>=(emailScope?emailSends:ipSends)) throw new OtpRateLimitException("Đã đạt giới hạn gửi OTP trong một giờ. Vui lòng thử lại sau.");
            }
            rows.forEach((k,row)->{row.setSendCount(row.getSendCount()+1);if(k.equals(emailKey))row.setNextSendAt(now.plusSeconds(resendSeconds));});
            return true;
        });
    }
    public void beforeVerification(String email, OtpPurpose purpose) {
        separateTransaction(() -> {
            var now=LocalDateTime.now();var row=row(emailKey(normalize(email),purpose),now);checkLock(row,now);
            row.setVerificationAttempts(row.getVerificationAttempts()+1);
            if(row.getVerificationAttempts()>=maxAttempts) row.setBlockedUntil(now.plusMinutes(lockMinutes));
            return true;
        });
    }
    public void successfulVerification(String email, OtpPurpose purpose) {
        separateTransaction(() -> {var row=row(emailKey(normalize(email),purpose),LocalDateTime.now());
            row.setVerificationAttempts(0);row.setBlockedUntil(null);return true;});
    }
}
