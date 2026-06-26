package org.sspd.servicemgmt.printingoptions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest.DocumentType;

import java.time.LocalDateTime;

/**
 * One row per {@link DocumentType} — stores every configurable print parameter
 * that an admin can change through the UI without touching code or redeploying.
 *
 * <h3>Nullability contract</h3>
 * Component-height fields ({@code headerHeightPx}, etc.) are nullable.
 * A null value means "use the hardcoded default from
 * {@code PrintLayoutConfigRegistry}". This lets admins override only the
 * values they care about while keeping everything else at the system default.
 *
 * <h3>Physics</h3>
 * {@code rowsOnFirstPage} and {@code rowsOnContinuationPage} are <em>never</em>
 * stored — they are derived by {@code DynamicPrintConfigService} from the
 * physical measurements every time they are needed.
 */
@Entity
@Table(
    name = "voucher_settings",
    uniqueConstraints = @UniqueConstraint(name = "uq_voucher_setting_type", columnNames = "document_type")
)
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class VoucherSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Identity ──────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    // ── Paper ─────────────────────────────────────────────────────────────────

    /** A4 | A5 | POS_80MM | POS_58MM */
    @Column(name = "paper_size", nullable = false, length = 10)
    private String paperSize;

    // ── Margins (mm) — null = use registry default ────────────────────────────

    @Column(name = "margin_top_mm")    private Integer marginTopMm;
    @Column(name = "margin_bottom_mm") private Integer marginBottomMm;
    @Column(name = "margin_left_mm")   private Integer marginLeftMm;
    @Column(name = "margin_right_mm")  private Integer marginRightMm;

    // ── Component heights (px at 96 dpi) — null = use registry default ────────
    // These values drive the physics formula for rows-per-page.
    // Measure from the rendered Thymeleaf template at 1× zoom when changing.

    @Column(name = "header_height_px")       private Integer headerHeightPx;
    @Column(name = "cont_header_height_px")  private Integer contHeaderHeightPx;
    @Column(name = "info_blocks_height_px")  private Integer infoBlocksHeightPx;
    @Column(name = "table_header_height_px") private Integer tableHeaderHeightPx;
    @Column(name = "row_height_px")          private Integer rowHeightPx;
    @Column(name = "totals_area_height_px")  private Integer totalsAreaHeightPx;
    @Column(name = "footer_height_px")       private Integer footerHeightPx;
    @Column(name = "safety_margin_px")       private Integer safetyMarginPx;

    // ── Display toggles ───────────────────────────────────────────────────────

    @Builder.Default @Column(name = "show_logo")             private Boolean showLogo            = true;
    @Builder.Default @Column(name = "show_qr_code")          private Boolean showQrCode          = false;
    @Builder.Default @Column(name = "show_signatures")        private Boolean showSignatures      = false;
    @Builder.Default @Column(name = "show_payment_history")  private Boolean showPaymentHistory  = true;
    @Builder.Default @Column(name = "show_serial")           private Boolean showSerial          = true;

    // ── Signature labels ──────────────────────────────────────────────────────

    @Column(name = "sign1_label", length = 60) private String sign1Label;
    @Column(name = "sign2_label", length = 60) private String sign2Label;

    // ── Voucher content ───────────────────────────────────────────────────────

    @Column(name = "voucher_title",    length = 80)   private String voucherTitle;
    @Column(name = "footer_note",      length = 500)  private String footerNote;
    @Column(name = "customer_notice",  length = 1000) private String customerNotice;

    // ── Typography — per-section font settings ────────────────────────────────
    // null = use CSS default; set only the sections the admin wants to override

    @Column(name = "header_font_family",       length = 100) private String  headerFontFamily;
    @Column(name = "header_font_size_px")                    private Integer headerFontSizePx;

    @Column(name = "info_font_family",         length = 100) private String  infoFontFamily;
    @Column(name = "info_font_size_px")                      private Integer infoFontSizePx;

    @Column(name = "table_header_font_family", length = 100) private String  tableHeaderFontFamily;
    @Column(name = "table_header_font_size_px")              private Integer tableHeaderFontSizePx;

    @Column(name = "table_data_font_family",   length = 100) private String  tableDataFontFamily;
    @Column(name = "table_data_font_size_px")                private Integer tableDataFontSizePx;

    @Column(name = "footer_font_family",       length = 100) private String  footerFontFamily;
    @Column(name = "footer_font_size_px")                    private Integer footerFontSizePx;

    @Column(name = "notice_font_family",       length = 100) private String  noticeFontFamily;
    @Column(name = "notice_font_size_px")                    private Integer noticeFontSizePx;

    // ── Audit ─────────────────────────────────────────────────────────────────

    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 80) private String updatedBy;
}
