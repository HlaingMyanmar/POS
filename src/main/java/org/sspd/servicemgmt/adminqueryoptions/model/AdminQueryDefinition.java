package org.sspd.servicemgmt.adminqueryoptions.model;

import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryDefinitionDTO;

public record AdminQueryDefinition(
        String id,
        String name,
        String description,
        String category,
        String sql
) {
    public AdminQueryDefinitionDTO toDto() {
        return new AdminQueryDefinitionDTO(id, name, description, category);
    }
}
