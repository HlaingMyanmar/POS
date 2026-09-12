package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerPortalAuthResponse {
    private String accessToken;
    private Integer customerId;
    private String name;
    private String phone;
    private String address;
    private String email;
    private boolean needsProfile;
    private boolean resetSent;
    private boolean creditAllowed;
    private BigDecimal creditLimit;
    private Integer creditDays;
    private boolean creditHold;
    private String creditStatusReason;
}
