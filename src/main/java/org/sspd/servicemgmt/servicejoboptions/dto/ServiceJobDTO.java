package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkType;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ServiceJobDTO {
    private Integer id;
    private String jobNo;
    private Integer customerId;
    private String customerName;
    private Integer assignedStaffId;
    private String assignedStaffName;
    private String itemName;
    private String itemCondition;
    private String deviceConditions;
    private String problemDesc;
    private String diagnosisNotes;
    private BigDecimal estimatedCost;
    private BigDecimal finalCost;
    private BigDecimal discountAmount;
    private Boolean foc;
    private BigDecimal netAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private LocalDate dueDate;
    private String paymentStatus;
    private String creditStatus;
    private String receivedDate;
    private String estimatedCompletion;
    private String completedDate;
    private String deliveredDate;
    private ServiceJobStatus status;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private Integer bookingId;
    private String bookingNo;
    private Integer saleId;
    private String customerPhone;
    private String color;
    private String serialNo;
    private String accessories;
    private Integer shelfLocationId;
    private String shelfLocationCode;
    private String shelfLocationLabel;
    private boolean rework;
    private Integer parentJobId;
    private String parentJobNo;
    private ReworkType reworkType;
    private String replacementItemName;
    private String replacementSerialNo;
    private String replacementReason;
    private String remark;
    private List<ServiceJobLineDTO> lines;
    private List<ServiceJobPartDTO> productParts;
}
