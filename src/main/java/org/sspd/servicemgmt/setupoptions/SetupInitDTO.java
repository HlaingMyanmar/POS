package org.sspd.servicemgmt.setupoptions;

import lombok.Data;
import java.util.List;

@Data
public class SetupInitDTO {
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private String companyEmail;
    private List<String> paymentMethods;
}
