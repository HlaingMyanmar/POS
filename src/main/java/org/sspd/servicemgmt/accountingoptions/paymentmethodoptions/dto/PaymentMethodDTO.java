package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.dto;

import lombok.Data;

@Data
public class PaymentMethodDTO {
    private Integer id;
    private String methodName;
    private boolean active;

    // COA အချက်အလက်များ
    private Integer accountId;
    private String accountName;

    /** Customer-facing transfer destination (separate from COA). */
    private String payeeName;
    private String payeeAccountNo;
    private String payeeHint;
    private Boolean showOnCustomerApp;
}