package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerCatalogServiceDTO {
    private Integer id;
    private String name;
    private String serviceTypeName;
    private BigDecimal price;
    private BigDecimal normalPrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    /** FIXED | STARTING_FROM | INSPECTION_REQUIRED */
    private String priceType;
    private Integer warrantyMonths;
    private String description;
}
