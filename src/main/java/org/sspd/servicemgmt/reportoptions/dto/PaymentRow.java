package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentRow {
    private String PAY_DATE;
    private String PAY_METHOD;
    private String PAY_AMOUNT;
}
