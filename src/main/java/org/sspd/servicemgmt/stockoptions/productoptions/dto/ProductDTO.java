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
    private BigDecimal lastPurchaseCost;
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
    private Integer quarantinedQty;
    private Integer availableSerialCount;
    private Integer unlinkedQty;
    private Integer reorderLevel;
    private Integer shortageQty;
    private Integer warrantyMonths;
    private String warrantyTerms;
    private String photoBase64;
    private String imagePath;
    private String thumbnailPath;
    private String imageMimeType;
    private String originalFileName;
    private Integer imageWidth;
    private Integer imageHeight;
    private Boolean archived;
    private Integer openingQty;
    private String openingBatch;
    private java.time.LocalDate openingExpiry;
    private String shelfLocation;
    private java.util.List<ProductPhotoDTO> photos = new java.util.ArrayList<>();
}
