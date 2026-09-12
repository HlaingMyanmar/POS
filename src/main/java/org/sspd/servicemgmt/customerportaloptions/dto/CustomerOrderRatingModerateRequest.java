package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerOrderRatingModerateRequest {
    private boolean hidden;
    private String reason;
}
