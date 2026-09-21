package org.sspd.servicemgmt.servicebookingsettingsoptions.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_booking_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBookingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "outdoor_transportation_notice", columnDefinition = "TEXT")
    private String outdoorTransportationNotice;

    @Column(name = "outdoor_transportation_fee", precision = 15, scale = 2)
    private BigDecimal outdoorTransportationFee;

    /** Max device photos allowed per booking intake item (slots 1..N). */
    @Column(name = "max_photos_per_item", nullable = false)
    @Builder.Default
    private Integer maxPhotosPerItem = 50;

    /** Shown in customer app when a booking is canceled/rejected by the shop. */
    @Column(name = "booking_rejection_message", columnDefinition = "TEXT")
    private String bookingRejectionMessage;

    /** When false, customer app cannot request ONSITE / outdoor service. */
    @Column(name = "outdoor_booking_enabled", nullable = false)
    @Builder.Default
    private Boolean outdoorBookingEnabled = true;

    /** Reason shown when outdoor booking is disabled. */
    @Column(name = "outdoor_booking_disabled_reason", columnDefinition = "TEXT")
    private String outdoorBookingDisabledReason;

    @Column(name = "max_advance_booking_days", nullable = false)
    @Builder.Default
    private Integer maxAdvanceBookingDays = 7;

    @Column(name = "min_notice_hours", nullable = false)
    @Builder.Default
    private Integer minNoticeHours = 2;

    @Column(name = "allow_same_day_booking", nullable = false)
    @Builder.Default
    private Boolean allowSameDayBooking = true;

    @Column(name = "allow_emergency_request", nullable = false)
    @Builder.Default
    private Boolean allowEmergencyRequest = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
