package org.sspd.servicemgmt.serviceoptions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceItemDTO {
    private Integer id;
    private String code;
    private String item;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer warrantyMonths;
    private Integer durationMinutes;
    private String description;
    private Boolean focDefault;
    private BigDecimal taxRate;
    private String skillRequired;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal commissionPercent;
    private String supportedDeviceTypes;
    private String defaultRequiredParts;
    @JsonProperty("isActive")
    private boolean isActive;
    private Integer serviceTypeId;
    private String serviceTypeName;
    private Integer subServiceTypeId;
    private String subServiceTypeName;
}
