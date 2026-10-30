package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "otp_rate_limits")
@Getter @Setter @NoArgsConstructor
public class OtpRateLimit {
    @Id @Column(name="limit_key", length=64) private String limitKey;
    @Column(name="window_started_at", nullable=false) private LocalDateTime windowStartedAt;
    @Column(name="send_count", nullable=false) private int sendCount;
    @Column(name="next_send_at") private LocalDateTime nextSendAt;
    @Column(name="verification_attempts", nullable=false) private int verificationAttempts;
    @Column(name="blocked_until") private LocalDateTime blockedUntil;
}
