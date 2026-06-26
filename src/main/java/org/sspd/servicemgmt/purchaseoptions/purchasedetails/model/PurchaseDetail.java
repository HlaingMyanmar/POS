package org.sspd.servicemgmt.purchaseoptions.purchasedetails.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_details")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer qty;
    private BigDecimal unitCost;
    private BigDecimal subtotal;

    @Builder.Default
    @Column(name = "warranty_months")
    private Integer warrantyMonths = 0;

    @Builder.Default
    @OneToMany(mappedBy = "purchaseDetail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseDetailWarranty> warrantyItems = new ArrayList<>();
}