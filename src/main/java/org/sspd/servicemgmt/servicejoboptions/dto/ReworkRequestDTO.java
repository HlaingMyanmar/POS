package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkType;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkResolutionMode;
import org.sspd.servicemgmt.servicejoboptions.model.OldPartDisposition;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ReworkRequestDTO {
    private ReworkType reworkType;
    private String problemDesc;
    private Integer assignedStaffId;
    private String replacementItemName;
    private String replacementSerialNo;
    private String replacementReason;
    private ReworkResolutionMode resolutionMode = ReworkResolutionMode.SERVICE_ONLY;
    private Integer originalPartId;
    private OldPartDisposition oldPartDisposition;
    private Integer replacementProductId;
    private Integer replacementQty;
    private List<String> replacementSerialNumbers;
    private BigDecimal warrantyCredit;
    private BigDecimal refundAmount;
    private Integer refundPaymentMethodId;
    private String refundTransactionNo;
}
