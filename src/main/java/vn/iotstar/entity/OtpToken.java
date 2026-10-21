package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.enums.OtpPurpose;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;       // null nếu chưa tạo account

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "otp_code", length = 10, nullable = false)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 30, nullable = false)
    private OtpPurpose purpose;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public boolean isValid() {
        return !Boolean.TRUE.equals(isUsed) && LocalDateTime.now().isBefore(expiredAt);
    }
}

