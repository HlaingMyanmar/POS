package org.sspd.servicemgmt.serviceoptions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SubServiceTypeDTO {
    private Integer id;
    private String name;
    private String description;
    @JsonProperty("isActive")
    private boolean isActive;
    private Integer serviceTypeId;
    private String serviceTypeName;
}
