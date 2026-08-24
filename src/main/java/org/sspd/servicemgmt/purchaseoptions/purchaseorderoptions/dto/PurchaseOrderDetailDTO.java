package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseOrderDetailDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private Integer qty;
    private Integer receivedQty;
    private BigDecimal unitCost;
    private BigDecimal subtotal;

    // ── Receive-time optional inputs (per line) ──
    private List<Integer> itemWarranties;
    private Integer warrantyMonths;
    private List<String> serialNumbers;
    private List<String> serialConditions;
    private List<String> serialPhotos;
}
