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

    @Column(name = "catalog_price", precision = 15, scale = 2)
    private BigDecimal catalogPrice;

    @Column(name = "estimated_price", precision = 15, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "approved_price", precision = 15, scale = 2)
    private BigDecimal approvedPrice;

    @Column(name = "billed_price", precision = 15, scale = 2)
    private BigDecimal billedPrice;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "min_price", precision = 15, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 15, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "price_change_reason", length = 500)
    private String priceChangeReason;

    @Builder.Default
    @Column(name = "price_override_approved")
    private Boolean priceOverrideApproved = Boolean.FALSE;

    @Column(name = "price_override_approved_by", length = 120)
    private String priceOverrideApprovedBy;

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

    public BigDecimal chargeUnitPrice() {
        if (!isBillable() || Boolean.TRUE.equals(warrantyCovered)) return BigDecimal.ZERO;
        if (billedPrice != null) return billedPrice;
        if (getConfirmationStatus().isCustomerConfirmed() && approvedPrice != null) return approvedPrice;
        if (estimatedPrice != null) return estimatedPrice;
        if (price != null) return price;
        return catalogPrice != null ? catalogPrice : BigDecimal.ZERO;
    }

    public BigDecimal estimateUnitPrice() {
        if (estimatedPrice != null) return estimatedPrice;
        if (price != null) return price;
        return catalogPrice != null ? catalogPrice : BigDecimal.ZERO;
    }

    public void refreshCharge() {
        BigDecimal unit = chargeUnitPrice();
        this.price = unit;
        int q = qty != null ? qty : 1;
        this.subtotal = unit.multiply(BigDecimal.valueOf(q));
    }
}
