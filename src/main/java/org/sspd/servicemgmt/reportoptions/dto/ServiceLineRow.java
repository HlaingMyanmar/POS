package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServiceLineRow {
    private Integer ROW_NO;
    private String SERVICE_NAME;
    private Integer QTY;
    private String PRICE;
    private String SUBTOTAL;
}
