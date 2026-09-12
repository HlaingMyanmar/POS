package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerAppAccountDTO {
    private Integer id;
    private Integer customerId;
    private String customerName;
    private String customerPhone;
    private String phone;
    private String email;
    private boolean hasPassword;
    private boolean hasGoogle;
    private boolean profileComplete;
    private boolean enabled;
    private LocalDateTime lastLoginAt;
    private Integer loginCount;
    private boolean hasUsedApp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
