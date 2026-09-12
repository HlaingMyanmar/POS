package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomerOrderRatingRequest {
    private Integer productRating;
    private Integer serviceRating;
    /** Fallback when product/service ratings are omitted. */
    private Integer rating;
    private Integer saleId;
    private String comment;
    private String review;
    private List<Line> products;

    @Data
    public static class Line {
        private Integer productId;
        private Integer rating;
    }
}
