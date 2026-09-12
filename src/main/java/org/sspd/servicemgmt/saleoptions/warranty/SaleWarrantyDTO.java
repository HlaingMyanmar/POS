package org.sspd.servicemgmt.saleoptions.warranty;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SaleWarrantyDTO {
    private Integer saleDetailId;
    private Integer saleId;
    private String saleCode;
    private LocalDateTime saleDate;
    private Integer customerId;
    private String customerName;
    private Integer productId;
    private String productName;
    private String serialNumber;
    private Integer qty;
    private Integer warrantyMonths;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private String status;
    private Long daysRemaining;
}
