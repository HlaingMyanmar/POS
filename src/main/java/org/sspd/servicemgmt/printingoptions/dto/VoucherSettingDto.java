package org.sspd.servicemgmt.printingoptions.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for {@code VoucherSetting}.
 *
 * <p>{@code rowsOnFirstPage} and {@code rowsOnContinuationPage} are
 * <em>derived</em> (computed from the physics formula) — they are never
 * stored in the database. They are included here so the admin UI can display
 * live "rows per page" feedback without a separate API call.
 *
 * <p>All component-height and margin fields are nullable. A null value means
 * "use the system default from {@code PrintLayoutConfigRegistry}".
 */
public record VoucherSettingDto(
        Long    id,
        String  documentType,   // SALE | SERVICE_JOB | BOOKING | PURCHASE

        // ── Paper ──────────────────────────────────────────────────────────
        String  paperSize,      // A4 | A5 | POS_80MM | POS_58MM

        // ── Margins (mm) ───────────────────────────────────────────────────
        Integer marginTopMm,
        Integer marginBottomMm,
        Integer marginLeftMm,
        Integer marginRightMm,

        // ── Component heights (px) ─────────────────────────────────────────
        Integer headerHeightPx,
        Integer contHeaderHeightPx,
        Integer infoBlocksHeightPx,
        Integer tableHeaderHeightPx,
        Integer rowHeightPx,
        Integer totalsAreaHeightPx,
        Integer footerHeightPx,
        Integer safetyMarginPx,

        // ── Display toggles ────────────────────────────────────────────────
        Boolean showLogo,
        Boolean showQrCode,
        Boolean showSignatures,
        Boolean showPaymentHistory,
        Boolean showSerial,

        // ── Labels & content ───────────────────────────────────────────────
        String  sign1Label,
        String  sign2Label,
        String  voucherTitle,
        String  footerNote,
        String  customerNotice,

        // ── Typography ─────────────────────────────────────────────────────
        String  headerFontFamily,      Integer headerFontSizePx,
        String  infoFontFamily,        Integer infoFontSizePx,
        String  tableHeaderFontFamily, Integer tableHeaderFontSizePx,
        String  tableDataFontFamily,   Integer tableDataFontSizePx,
        String  footerFontFamily,      Integer footerFontSizePx,
        String  noticeFontFamily,      Integer noticeFontSizePx,

        // ── Derived (computed, never stored) ───────────────────────────────
        Integer rowsOnFirstPage,
        Integer rowsOnContinuationPage,

        // ── Audit ──────────────────────────────────────────────────────────
        LocalDateTime updatedAt,
        String        updatedBy
) {}
