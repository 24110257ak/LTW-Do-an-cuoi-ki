package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long addressId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "receiver_name", length = 150, nullable = false,
            columnDefinition = "nvarchar(150) not null")
    private String receiverName;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "province", length = 100, nullable = false,
            columnDefinition = "nvarchar(100) not null")
    private String province;

    @Column(name = "district", length = 100, nullable = false,
            columnDefinition = "nvarchar(100) not null")
    private String district;

    @Column(name = "ward", length = 100, nullable = false,
            columnDefinition = "nvarchar(100) not null")
    private String ward;

    @Column(name = "street", length = 255, nullable = false,
            columnDefinition = "nvarchar(255) not null")
    private String street;

    @Column(name = "address_note", length = 255,
            columnDefinition = "nvarchar(255)")
    private String addressNote;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

