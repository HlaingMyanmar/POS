package org.sspd.servicemgmt.printingoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintLineItem {
    private int rowNo;
    private String productName;
    private String serialInfo;
    private String warrantyLabel;
    private int qty;
    private String unitPrice;
    private String subtotal;
    /** Optional: discount per line, shown when non-zero */
    private String discount;
}
