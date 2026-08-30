package org.sspd.servicemgmt.stockoptions.lotoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.model.Warehouse;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_detail_id", unique = true)
    private PurchaseDetail purchaseDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(length = 100)
    private String batchNumber;
    private LocalDate expiryDate;
    @Column(length = 120)
    private String warehouseName;

    @Column(nullable = false)
    private Integer receivedQty;
    @Column(nullable = false)
    private Integer remainingQty;
    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "source_type", length = 20)
    private String sourceType;
    @Column(name = "source_id")
    private Integer sourceId;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
