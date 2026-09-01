package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 15)
    private AdjustmentType adjustmentType;

    @Column(name = "qty_change", nullable = false)
    private Integer qtyChange;

    @Column(name = "qty_before", nullable = false)
    private Integer qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private Integer qtyAfter;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "serial_numbers", columnDefinition = "TEXT")
    private String serialNumbers;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}