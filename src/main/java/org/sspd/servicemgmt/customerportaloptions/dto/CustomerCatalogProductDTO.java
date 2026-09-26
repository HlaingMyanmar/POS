package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerCatalogProductDTO {
    private Integer id;
    private String name;
    private String productCode;
    private Integer categoryId;
    private String categoryName;
    private Integer parentCategoryId;
    private String parentCategoryName;
    private String brandName;
    private String productType;
    private BigDecimal sellingPrice;
    private Integer warrantyMonths;
    private String warrantyTerms;
    private String remark;
    private String specifications;
    private Integer reviewCount;
    private Double reviewRating;
    private boolean inStock;
    private Integer stockQty;
    private String thumbnailUrl;
    private List<String> photoUrls = new ArrayList<>();
    private List<CustomerCatalogVideoDTO> videos = new ArrayList<>();
}
