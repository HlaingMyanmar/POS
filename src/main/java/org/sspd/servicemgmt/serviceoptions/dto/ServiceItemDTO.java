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
    @JsonProperty("isActive")
    private boolean isActive;
    private Integer serviceTypeId;
    private String serviceTypeName;
    private Integer subServiceTypeId;
    private String subServiceTypeName;
}
