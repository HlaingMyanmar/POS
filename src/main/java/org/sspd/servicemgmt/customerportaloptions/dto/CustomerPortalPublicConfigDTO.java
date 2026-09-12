package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPortalPublicConfigDTO {
    private String googleClientId;
    private boolean googleEnabled;
}
