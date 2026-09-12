package org.sspd.servicemgmt.adminqueryoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminQueryDefinitionDTO {
    private String id;
    private String name;
    private String description;
    private String category;
}
