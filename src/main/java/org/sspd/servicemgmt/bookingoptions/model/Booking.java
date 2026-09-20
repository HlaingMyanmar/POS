package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "booking_no", nullable = false, unique = true, length = 20)
    private String bookingNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "booking_datetime")
    private LocalDateTime appointmentDate;

    /** Authoritative outdoor service day when an arrival window is selected. */
    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "arrival_window_id")
    private Integer arrivalWindowId;

    /** Customer preference inside the window; not a guaranteed arrival time. */
    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    @Column(name = "preferred_anytime", nullable = false)
    @Builder.Default
    private Boolean preferredAnytime = true;

    @Column(name = "customer_preference_note", columnDefinition = "TEXT")
    private String customerPreferenceNote;

    @Column(name = "complaint_note", columnDefinition = "TEXT")
    private String complaintNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(length = 20)
    private String source;

    @Column(name = "requested_service_name", length = 200)
    private String requestedServiceName;
    @Column(name = "request_type", length = 30)
    private String requestType;
    @Column(name = "device_category", length = 80)
    private String deviceCategory;
    @Column(name = "device_name", length = 200)
    private String deviceName;
    @Column(name = "requested_service_mode", length = 20)
    private String requestedServiceMode;

    @Column(name = "service_address", columnDefinition = "TEXT")
    private String serviceAddress;
    @Column(length = 20)
    private String urgency;
    @Column(name = "contact_preference", length = 20)
    private String contactPreference;

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slot ASC")
    private List<BookingRequestPhoto> requestPhotos = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BookingItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (bookingDate == null) bookingDate = LocalDate.now();
        if (status == null) status = BookingStatus.CONFIRMED;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
