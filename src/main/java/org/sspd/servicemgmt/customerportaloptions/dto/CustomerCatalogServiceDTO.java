package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerCatalogServiceDTO {
    private Integer id;
    private String name;
    private String serviceTypeName;
    private BigDecimal price;
    private Integer warrantyMonths;
    private String description;
}
