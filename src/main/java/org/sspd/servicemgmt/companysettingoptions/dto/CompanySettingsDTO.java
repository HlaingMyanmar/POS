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
    /** Data URL (audio/mpeg, audio/wav, audio/ogg, …) for order alert sound. */
    private String notificationSoundBase64;
    private String voucherConfigJson;
    private String salePrefix;
    private Integer saleDigits;
    private String purchasePrefix;
    private Integer purchaseDigits;
    private String bookingPrefix;
    private Integer bookingDigits;
    private String poPrefix;
    private Integer poDigits;
    private String purchaseReturnPrefix;
    private Integer purchaseReturnDigits;
    private java.math.BigDecimal poFinalApprovalThreshold;
    private Boolean serviceSupervisorApprovalRequired;
    private Boolean serviceAllowDeliveryWithDue;
    /** 1–100: pickup orders must pre-transfer this percent as deposit. */
    private java.math.BigDecimal pickupDepositPercent;

    /** SMTP host for outbound mail (e.g. smtp.gmail.com). */
    private String mailSmtpHost;
    private Integer mailSmtpPort;
    private String mailSmtpUsername;
    /** Write-only: blank keeps existing password. Never returned from GET. */
    private String mailSmtpPassword;
    private String mailSmtpFrom;
    private Boolean mailSmtpAuth;
    private Boolean mailSmtpStartTls;
    /** Read-only: true when host+username+password are configured. */
    private Boolean mailSmtpConfigured;
}
