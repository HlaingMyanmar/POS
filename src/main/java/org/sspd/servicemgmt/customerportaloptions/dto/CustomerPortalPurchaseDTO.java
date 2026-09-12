package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalPurchaseDTO {
    private Integer id;
    private String saleCode;
    private LocalDateTime saleDate;
    private BigDecimal netAmount;
    private String paymentStatus;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private Integer productId;
        private String productName;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private Integer warrantyMonths;
        private LocalDate warrantyStartDate;
        private LocalDate warrantyExpiryDate;
        private String warrantyStatus;
        private Long warrantyDaysRemaining;
        private String serialNumber;
    }
}
