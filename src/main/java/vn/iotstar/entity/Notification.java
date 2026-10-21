package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.iotstar.enums.NotificationType;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notif_id")
    private Long notifId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 300, nullable = false,
            columnDefinition = "nvarchar(300) not null")
    private String title;

    @Column(name = "message", length = 1000, nullable = false,
            columnDefinition = "nvarchar(1000) not null")
    private String message;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
