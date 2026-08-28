package org.sspd.servicemgmt.customeroptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "customer")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String address;

    @Column(name = "credit_hold")
    private Boolean creditHold = Boolean.FALSE;

    @Column(name = "credit_hold_reason", columnDefinition = "TEXT")
    private String creditHoldReason;

    @Column(name = "blacklisted")
    private Boolean blacklisted = Boolean.FALSE;

    @Column(name = "blacklist_reason", columnDefinition = "TEXT")
    private String blacklistReason;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private java.math.BigDecimal longitude;

    @Column(name = "location_accuracy", precision = 8, scale = 2)
    private java.math.BigDecimal locationAccuracy;

    @Column(name = "location_captured_at")
    private java.time.LocalDateTime locationCapturedAt;

    @Column(name = "location_captured_by", length = 120)
    private String locationCapturedBy;

    @Column(name = "location_source", length = 20)
    private String locationSource;

    @Builder.Default
    @Column(name = "advance_balance", precision = 15, scale = 2, nullable = false)
    private java.math.BigDecimal advanceBalance = java.math.BigDecimal.ZERO;
}
