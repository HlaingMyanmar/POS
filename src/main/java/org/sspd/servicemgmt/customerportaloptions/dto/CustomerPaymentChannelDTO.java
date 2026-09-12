package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerPaymentChannelDTO {
    private Integer id;
    private String methodName;
    private String payeeName;
    private String payeeAccountNo;
    private String payeeHint;
}
