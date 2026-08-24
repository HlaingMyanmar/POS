package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseOrderDTO {
    private Integer id;
    private String poCode;
    private Integer supplierId;
    private String supplierName;
    private Integer staffId;
    private String staffName;
    private LocalDateTime orderDate;
    private LocalDate expectedDate;
    private String status;
    private BigDecimal totalAmount;
    private String remark;
    private List<PurchaseOrderDetailDTO> details;
}
