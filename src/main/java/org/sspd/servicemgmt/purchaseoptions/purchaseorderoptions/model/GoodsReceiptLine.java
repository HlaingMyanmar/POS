package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "goods_receipt_lines")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GoodsReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;
    private Integer poDetailId;
    private Integer productId;
    private String productName;
    private Integer orderedQty;
    private Integer acceptedQty;
    private Integer damagedQty;
    private Integer rejectedQty;
    private BigDecimal poUnitCost;
    private BigDecimal invoiceUnitCost;
    private BigDecimal priceVariance;
}
