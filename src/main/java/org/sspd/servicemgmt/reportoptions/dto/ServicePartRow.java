package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServicePartRow {
    private Integer ROW_NO;
    private String PRODUCT_NAME;
    private String SERIAL_INFO;
    private Integer QTY;
    private String UNIT_PRICE;
    private String SUBTOTAL;
}
