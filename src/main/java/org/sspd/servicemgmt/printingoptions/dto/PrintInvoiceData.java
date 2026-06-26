package org.sspd.servicemgmt.printingoptions.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Fully assembled invoice data passed to Thymeleaf templates and the PDF engine.
 */
@Data
@Builder
public class PrintInvoiceData {

    // ── Company ──────────────────────────────────────────────────────────────
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private String companyEmail;
    private String logoBase64;
    private String footerNote;
    private String headerColor;

    // ── Invoice meta ─────────────────────────────────────────────────────────
    private String documentType; // SALE | BOOKING | SERVICE_JOB | SERVICE_DONE | PURCHASE
    private String invoiceTitle;
    private String invoiceNo;
    private String invoiceDate;
    private String dueDate;
    private String paymentStatus;
    private String creditStatus;

    // ── Parties ──────────────────────────────────────────────────────────────
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private String cashierName;

    // ── Line items ───────────────────────────────────────────────────────────
    private List<PrintLineItem> lineItems;

    // ── Payment history ──────────────────────────────────────────────────────
    private List<PaymentEntry> payments;

    // ── Totals ───────────────────────────────────────────────────────────────
    private String subtotal;
    private String discount;
    private String netAmount;
    private String paid;
    private String balanceDue;

    // ── Extra fields (service voucher / booking) ──────────────────────────────
    private String remark;
    private String customerNotice;

    // ── Service Job / Intake Sheet ────────────────────────────────────────────
    private String itemName;
    private String problemDesc;
    private String accessories;
    private String estimatedCost;
    private List<ConditionRow> deviceConditionRows;

    // ── Booking / Device Intake Receipt ──────────────────────────────────────
    private boolean bookingReceipt; // true → show device info layout, hide items table & financials
    private String deviceType;
    private String brand;
    private String model;
    private String serialNo;
    private String color;
    private List<DeviceRow> deviceRows; // multiple devices per booking

    @Data
    @Builder
    public static class DeviceRow {
        private String deviceType;
        private String brand;
        private String model;
        private String serialNo;
        private String color;
        private String accessories;
        private String problemDesc;
    }

    @Data
    @Builder
    public static class ConditionRow {
        private String component;
        private String status;
    }

    // ── Pagination (filled by HtmlPdfService before rendering) ───────────────
    /** Current page number (1-based) */
    private int currentPage;
    /** Total page count */
    private int totalPages;
    /** Subset of lineItems to render on this page */
    private List<PrintLineItem> pageItems;
    /** True when this is the last page (show totals + footer) */
    private boolean lastPage;
    /** True when this is the first page (show header + customer info) */
    private boolean firstPage;

    // ── Config ────────────────────────────────────────────────────────────────
    private PrintPageConfig pageConfig;
    private boolean showLogo;
    private boolean showSerial;
    private boolean showPaymentHistory;
    private boolean showSignatures;
    private boolean showQrCode;
    private String qrCodeBase64;
    private String sign1Label;
    private String sign2Label;

    // ── Typography ───────────────────────────────────────────────────────────
    private String  headerFontFamily;      private Integer headerFontSizePx;
    private String  infoFontFamily;        private Integer infoFontSizePx;
    private String  tableHeaderFontFamily; private Integer tableHeaderFontSizePx;
    private String  tableDataFontFamily;   private Integer tableDataFontSizePx;
    private String  footerFontFamily;      private Integer footerFontSizePx;
    private String  noticeFontFamily;      private Integer noticeFontSizePx;

    // ── Nested: payment history row ─────────────────────────────────────────
    @Data
    @Builder
    public static class PaymentEntry {
        private String date;
        private String method;
        private String amount;
    }
}
