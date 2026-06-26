package org.sspd.servicemgmt.setupoptions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetupStatusDTO {
    private boolean complete;
    private boolean hasPaymentMethods;
    private boolean companyConfigured;
}
