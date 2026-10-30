package vn.iotstar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_providers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class DeliveryProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_id")
    private Integer providerId;

    @Column(name = "provider_name", length = 150, nullable = false, unique = true,
            columnDefinition = "nvarchar(150) not null")
    private String providerName;

    @Column(name = "base_fee", nullable = false, precision = 18, scale = 0)
    @Builder.Default
    private BigDecimal baseFee = BigDecimal.ZERO;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}

