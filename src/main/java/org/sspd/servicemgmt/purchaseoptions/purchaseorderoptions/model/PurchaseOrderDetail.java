package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // မှာယူထားသော အရေအတွက်
    private Integer qty;

    // လက်ခံပြီးသော အရေအတွက်
    @Builder.Default
    @Column(name = "received_qty")
    private Integer receivedQty = 0;

    private BigDecimal unitCost;
    private BigDecimal subtotal;
}
