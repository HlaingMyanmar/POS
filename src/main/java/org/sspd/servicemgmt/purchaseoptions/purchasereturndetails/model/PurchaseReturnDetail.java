package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model.PurchaseReturnReason;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_return_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseReturnDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private PurchaseReturn purchaseReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer qty;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    private BigDecimal subtotal;

    @Builder.Default
    @Column(name = "allocated_shipping_cost", precision = 18, scale = 2, nullable = false)
    private BigDecimal allocatedShippingCost = BigDecimal.ZERO;

    @Column(name = "serial_number")
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reason_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private PurchaseReturnReason reason;

    @Builder.Default
    @Column(name = "quarantined_qty", nullable = false)
    private Integer quarantinedQty = 0;

    @Builder.Default
    @Column(name = "dispatched_qty", nullable = false)
    private Integer dispatchedQty = 0;
}
