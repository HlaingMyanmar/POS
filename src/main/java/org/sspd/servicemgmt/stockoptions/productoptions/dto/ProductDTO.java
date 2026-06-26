package org.sspd.servicemgmt.stockoptions.productoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType;

import java.math.BigDecimal;

@Data
public class ProductDTO {
    private Integer id;
    private String productCode;
    private String name;
    private ProductType productType;
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private String remark;

    // Category အချက်အလက်
    private Integer categoryId;
    private String categoryName;

    // Brand အချက်အလက်
    private Integer brandId;
    private String brandName;

    // Unit အချက်အလက်
    private Integer unitId;
    private String unitName;

    private Boolean hasSerial;
    private Integer stockQty;
    private Integer availableSerialCount;
    private Integer unlinkedQty;
    private Integer reorderLevel;
    private Integer shortageQty;
    private Integer warrantyMonths;
    private String warrantyTerms;
    private String photoBase64;
}
