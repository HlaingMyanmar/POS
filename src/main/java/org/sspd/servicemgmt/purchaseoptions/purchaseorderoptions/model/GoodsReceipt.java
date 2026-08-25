package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_receipts", indexes = {
        @Index(name = "idx_grn_po", columnList = "purchase_order_id"),
        @Index(name = "idx_grn_purchase", columnList = "purchase_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GoodsReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "grn_code", nullable = false, unique = true)
    private String grnCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;
    @Column(name = "purchase_id")
    private Integer purchaseId;
    @Column(name = "supplier_invoice_no")
    private String supplierInvoiceNo;
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
    @Column(name = "received_by")
    private String receivedBy;
    @Column(name = "match_status", nullable = false)
    private String matchStatus;
    @Column(name = "variance_reason", length = 500)
    private String varianceReason;
    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GoodsReceiptLine> lines = new ArrayList<>();
}
