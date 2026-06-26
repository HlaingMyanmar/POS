package org.sspd.servicemgmt.creditoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.creditoptions.model.AlertType;

import java.time.LocalDateTime;

@Data
public class CreditAlertDTO {
    private Integer id;
    private Integer customerId;
    private Integer saleId;
    private AlertType alertType;
    private LocalDateTime alertDate;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    private String customerName;
    private String saleCode;
}
