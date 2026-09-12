package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPromoCodeDTO {
    private Integer id;
    private String code;
    private String name;
    private String description;
    private boolean active = true;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;
    private Integer usageLimit;
    private Integer perCustomerLimit;
    private long usedCount;
    private List<Integer> productIds = new ArrayList<>();
    private List<Integer> categoryIds = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
