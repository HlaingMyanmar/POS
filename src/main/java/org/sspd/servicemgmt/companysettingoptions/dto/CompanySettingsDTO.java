package org.sspd.servicemgmt.companysettingoptions.dto;

import lombok.Data;

@Data
public class CompanySettingsDTO {
    private Integer id;
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private String companyEmail;
    private String invoiceTitle;
    private String footerNote;
    private String taglineMm;
    private String logoBase64;
    private String voucherConfigJson;
    private String salePrefix;
    private Integer saleDigits;
    private String purchasePrefix;
    private Integer purchaseDigits;
    private String bookingPrefix;
    private Integer bookingDigits;
}
