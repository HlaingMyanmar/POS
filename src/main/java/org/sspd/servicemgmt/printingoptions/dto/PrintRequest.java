package org.sspd.servicemgmt.printingoptions.dto;

import lombok.Data;

/**
 * Inbound request body for the /print/pdf endpoint.
 */
@Data
public class PrintRequest {

    public enum DocumentType { SALE, BOOKING, SERVICE_JOB, SERVICE_DONE, PURCHASE }

    private DocumentType documentType;
    private Integer documentId;

    /** A4 | A5 | POS_58MM | POS_80MM */
    private String paperSize;

    private boolean showLogo = true;
    private boolean showSerial = true;
    private boolean showPaymentHistory = true;
    private boolean showSignatures = false;
    private boolean showQrCode = false;
    private String sign1Label = "Prepared By";
    private String sign2Label = "Received By";

    private String  headerFontFamily;
    private Integer headerFontSizePx;
    private String  infoFontFamily;
    private Integer infoFontSizePx;
    private String  tableHeaderFontFamily;
    private Integer tableHeaderFontSizePx;
    private String  tableDataFontFamily;
    private Integer tableDataFontSizePx;
    private String  footerFontFamily;
    private Integer footerFontSizePx;
    private String  noticeFontFamily;
    private Integer noticeFontSizePx;
}
