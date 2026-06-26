package org.sspd.servicemgmt.stockoptions.productserialoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductSerialDTO {

    private Integer id;

    private String serialNumber;

    private SerialStatus status;

    // Product Info
    private Integer productId;
    private String productName;
    private Integer warrantyMonths;
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;

    private Integer purchaseId;
    private String purchaseCode;
    private String supplierName;
    private LocalDateTime purchaseDate;

    private String condition;
    private String photoBase64;
}
