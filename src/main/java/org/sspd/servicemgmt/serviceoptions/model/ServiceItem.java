package org.sspd.servicemgmt.serviceoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "services")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "item", nullable = false, unique = true, length = 100)
    private String item;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "warranty_months")
    private Integer warrantyMonths = 0;

    @Builder.Default
    @Column(name = "duration_minutes")
    private Integer durationMinutes = 0;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "foc_default")
    private Boolean focDefault = Boolean.FALSE;

    @Builder.Default
    @Column(name = "tax_rate", precision = 7, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "skill_required", length = 120)
    private String skillRequired;

    @Column(name = "min_price", precision = 15, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 15, scale = 2)
    private BigDecimal maxPrice;

    @Builder.Default
    @Column(name = "commission_percent", precision = 7, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    @Column(name = "supported_device_types", columnDefinition = "TEXT")
    private String supportedDeviceTypes;

    @Column(name = "default_required_parts", columnDefinition = "TEXT")
    private String defaultRequiredParts;

    @Column(name = "is_active")
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_service_type_id")
    private SubServiceType subServiceType;
}
