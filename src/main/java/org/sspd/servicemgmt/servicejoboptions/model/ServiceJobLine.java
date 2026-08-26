package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;

import java.math.BigDecimal;

@Entity
@Table(name = "service_job_lines")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJobLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_job_id", nullable = false)
    private ServiceJob serviceJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_item_id", nullable = false)
    private ServiceItem serviceItem;

    @Column(name = "qty")
    private Integer qty;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Builder.Default
    @Column(name = "warranty_covered")
    private Boolean warrantyCovered = Boolean.FALSE;

    @Getter(AccessLevel.NONE)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", length = 30)
    private ServiceLineConfirmationStatus confirmationStatus = ServiceLineConfirmationStatus.RECOMMENDED;

    public ServiceLineConfirmationStatus getConfirmationStatus() {
        return confirmationStatus != null ? confirmationStatus : ServiceLineConfirmationStatus.RECOMMENDED;
    }

    public boolean isBillable() {
        return getConfirmationStatus().isBillable();
    }
}
