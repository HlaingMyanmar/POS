package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCatalogOptionDTO {
    private Integer id;
    private String name;
    private Integer parentId;
    private String parentName;
}
