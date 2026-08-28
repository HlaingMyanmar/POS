package org.sspd.servicemgmt.technicianvisitoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "technician_visits",
        indexes = {
                @Index(name = "idx_tv_staff_status", columnList = "staff_id,status"),
                @Index(name = "idx_tv_job", columnList = "service_job_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_job_id", nullable = false)
    private ServiceJob serviceJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TechnicianVisitStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime endedAt;
    private LocalDateTime lastMovedAt;

    @Column(precision = 10, scale = 7)
    private BigDecimal startLatitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal startLongitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal arriveLatitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal arriveLongitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal endLatitude;
    @Column(precision = 10, scale = 7)
    private BigDecimal endLongitude;

    @Column(length = 500)
    private String cancelReason;
}
