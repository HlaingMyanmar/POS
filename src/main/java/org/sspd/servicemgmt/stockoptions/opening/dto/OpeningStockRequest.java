package org.sspd.servicemgmt.stockoptions.opening.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningStockRequest {
    private Integer productId;
    private Integer qty;
    private String reason;
}
