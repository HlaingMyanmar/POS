package org.sspd.servicemgmt.serviceoptions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ServiceTypeDTO {
    private Integer id;
    private String name;
    private String description;
    @JsonProperty("isActive")
    private boolean isActive;
}
