package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPortalBrandingDTO {
    private String companyName;
    private String taglineMm;
    /** Prefer loading via logoUrl — base64 may be omitted for large logos. */
    private String logoBase64;
    private boolean hasLogo;
    /** Relative API path, e.g. /api/v1/customer-portal/branding/logo */
    private String logoUrl;
    /** Pickup orders: percent of cart items the customer must transfer as deposit. */
    private java.math.BigDecimal pickupDepositPercent;
    /** When false, customer app only offers pickup. */
    private boolean deliveryEnabled;
}
