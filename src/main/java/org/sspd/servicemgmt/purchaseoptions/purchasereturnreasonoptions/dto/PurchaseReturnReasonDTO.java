package org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.dto;

import lombok.Data;

@Data
public class PurchaseReturnReasonDTO {
    private Integer id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
}
