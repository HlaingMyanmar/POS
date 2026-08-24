package org.sspd.servicemgmt.quotationoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

@Data
public class QuotationDTO {
    private Integer id;
    private String quotationCode;
    private Integer customerId;
    private String customerName;
    private LocalDateTime quotationDate;
    private LocalDate validUntil;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;
    private String terms;
    private String remark;
    private Integer convertedSaleId;
    private String convertedBy;
    private LocalDateTime convertedAt;
    private List<SaleDetailDTO> details;
}
