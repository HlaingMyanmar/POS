package org.sspd.servicemgmt.bookingoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_date",     columnList = "booking_date"),
    @Index(name = "idx_booking_status",   columnList = "status"),
    @Index(name = "idx_booking_customer", columnList = "customer_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 20)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "invoice_file_path", length = 255)
    private String invoiceFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private BookingStatus status = BookingStatus.Pending;

    @Column(columnDefinition = "TEXT")
    private String remark;

    // Device information fields
    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "accessories", columnDefinition = "TEXT")
    private String accessories;

    @Column(name = "shelf_location", length = 100)
    private String shelfLocation;

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingDetail> details = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingDeviceInfo> deviceInfos = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingDevice> devices = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (bookingDate == null) bookingDate = LocalDateTime.now();
    }
}
