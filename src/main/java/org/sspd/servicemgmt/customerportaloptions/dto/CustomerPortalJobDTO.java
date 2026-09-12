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
    private LocalDateTime receivedDate;
    private LocalDateTime completedDate;
    private LocalDateTime deliveredDate;
    private BigDecimal netAmount;
    private String paymentStatus;
    private List<ServiceLine> services = new ArrayList<>();
    private List<PartLine> parts = new ArrayList<>();

    @Data
    public static class ServiceLine {
        private String name;
        private Integer qty;
        private Integer warrantyMonths;
        private Boolean warrantyCovered;
    }

    @Data
    public static class PartLine {
        private String productName;
        private Integer qty;
        private Boolean warrantyCovered;
    }
}
