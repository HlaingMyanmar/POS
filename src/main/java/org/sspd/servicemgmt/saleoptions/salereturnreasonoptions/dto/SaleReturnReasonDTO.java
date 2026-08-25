package org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.dto;

import lombok.Data;

@Data
public class SaleReturnReasonDTO {
    private Integer id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
}
