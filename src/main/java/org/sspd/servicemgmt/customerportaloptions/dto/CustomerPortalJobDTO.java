package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalJobDTO {
    private Integer id;
    private String jobNo;
    private String status;
    private String itemName;
    private String deviceType;
    private String problemDesc;
    private String serviceMode;
    private Integer bookingId;
    private String bookingNo;
    private LocalDateTime receivedDate;
    private LocalDateTime appointmentDate;
    private LocalDateTime workStartedAt;
    private LocalDateTime completedDate;
    private LocalDateTime deliveredDate;
    private String assignedStaffName;
    private String helperStaffName;
    private boolean hasHelper;
    private List<CrewMember> technicians = new ArrayList<>();
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal laborNetAmount;
    private BigDecimal partsNetAmount;
    private String paymentStatus;
    private List<ServiceLine> services = new ArrayList<>();
    private List<PartLine> parts = new ArrayList<>();

    @Data
    public static class CrewMember {
        private String name;
        private String role;
        private String roleLabel;
    }

    @Data
    public static class ServiceLine {
        private String name;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal price;
        private BigDecimal discountAmount;
        private BigDecimal subtotal;
        private Integer warrantyMonths;
        private Boolean warrantyCovered;
    }

    @Data
    public static class PartLine {
        private String productName;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal price;
        private BigDecimal discountAmount;
        private BigDecimal subtotal;
        private Integer warrantyMonths;
        private Boolean warrantyCovered;
        private String serialNumber;
    }
}
