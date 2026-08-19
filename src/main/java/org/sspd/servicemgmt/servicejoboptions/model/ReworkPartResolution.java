package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rework_part_resolutions", indexes = {
    @Index(name = "idx_rework_resolution_job", columnList = "rework_job_id"),
    @Index(name = "idx_rework_resolution_original_part", columnList = "original_part_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReworkPartResolution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rework_job_id", nullable = false)
    private ServiceJob reworkJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_part_id")
    private ServiceJobPart originalPart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_product_id")
    private Product replacementProduct;

    @Enumerated(EnumType.STRING) @Column(name = "resolution_mode", nullable = false, length = 20)
    private ReworkResolutionMode resolutionMode;

    @Enumerated(EnumType.STRING) @Column(name = "old_part_disposition", length = 25)
    private OldPartDisposition oldPartDisposition;

    @Column(name = "old_serial_numbers", columnDefinition = "TEXT")
    private String oldSerialNumbers;
    @Column(name = "replacement_serial_numbers", columnDefinition = "TEXT")
    private String replacementSerialNumbers;
    @Column(name = "replacement_qty")
    private Integer replacementQty;
    @Column(name = "original_credit", precision = 15, scale = 2)
    private BigDecimal originalCredit;
    @Column(name = "replacement_price", precision = 15, scale = 2)
    private BigDecimal replacementPrice;
    @Column(name = "customer_charge", precision = 15, scale = 2)
    private BigDecimal customerCharge;
    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
