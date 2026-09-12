package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPromoValidateRequest {
    private String code;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private Integer productId;
        private Integer qty;
    }
}
