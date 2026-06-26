package org.sspd.servicemgmt.printingoptions.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable layout configuration for one paper size.
 *
 * <h3>Design contract</h3>
 * <ul>
 *   <li>All physical dimensions are in <b>millimetres</b>.</li>
 *   <li>All component heights are in <b>pixels at 96 dpi</b>
 *       (the browser and Flying Saucer default resolution).</li>
 *   <li>{@link #getRowsOnFirstPage()} and {@link #getRowsOnContinuationPage()}
 *       are <em>derived</em> — they recalculate automatically whenever a raw
 *       dimension changes.  Never hardcode these anywhere else.</li>
 * </ul>
 *
 * <h3>Changing the layout</h3>
 * <pre>
 *   ┌─ Edit PrintLayoutConfigRegistry ─────────────────────────────────┐
 *   │  Change rowHeightPx    → row capacity recalculates everywhere    │
 *   │  Change footerHeightPx → footer position recalculates            │
 *   │  Change marginTopMm    → printable area recalculates             │
 *   └──────────────────────────────────────────────────────────────────┘
 *   No other class needs to change.
 * </pre>
 */
@Getter
@Builder
@ToString
public final class PrintLayoutConfig {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Canonical name, e.g. "A4", "A5", "POS_80MM". */
    private final String name;

    // ── Physical dimensions (millimetres) ─────────────────────────────────────

    private final float pageWidthMm;
    /** 0 = thermal roll (auto / continuous height). */
    private final float pageHeightMm;

    private final float marginTopMm;
    private final float marginBottomMm;
    private final float marginLeftMm;
    private final float marginRightMm;

    // ── Component heights (pixels at 96 dpi) ─────────────────────────────────
    //   Measure these once from the rendered template at 1× zoom.

    /** Full header band — company logo + name + invoice meta. First page only. */
    private final int headerHeightPx;

    /** "Bill To" + "Invoice Details" card row. First page only. */
    private final int infoBlocksHeightPx;

    /** Compact continuation header shown on page 2 onwards. */
    private final int contHeaderHeightPx;

    /** Table {@code <thead>} height. */
    private final int tableHeaderHeightPx;

    /** Average height of one {@code <tbody>} data row (single-line product name). */
    private final int rowHeightPx;

    /** Totals summary box + payment history table. Last page only. */
    private final int totalsAreaHeightPx;

    /** "Company · Thank you · Page X of Y" footer bar. Every page. */
    private final int footerHeightPx;

    /** Extra whitespace buffer to prevent accidental overflow onto a new page. */
    private final int safetyMarginPx;

    // ── CSS helpers ───────────────────────────────────────────────────────────

    /** CSS {@code @page { size: … }} value, e.g. {@code "A4 portrait"}. */
    private final String cssPageSize;

    /** BEM modifier class appended to {@code .invoice-page}, e.g. {@code "invoice-page--a4"}. */
    private final String cssClassName;

    // ── Derived row capacities (calculated, not stored) ───────────────────────

    /**
     * Maximum data rows that fit on the <em>first</em> page.
     * Subtracts: full header + info blocks + table header + totals area + footer + safety.
     */
    public int getRowsOnFirstPage() {
        if (isThermal()) return Integer.MAX_VALUE;
        int body = printableHeightPx()
                - headerHeightPx
                - infoBlocksHeightPx
                - tableHeaderHeightPx
                - totalsAreaHeightPx
                - footerHeightPx
                - safetyMarginPx;
        return Math.max(1, body / rowHeightPx);
    }

    /**
     * Maximum data rows that fit on <em>continuation</em> pages (page 2+).
     * Subtracts: compact continuation header + table header + footer + safety.
     */
    public int getRowsOnContinuationPage() {
        if (isThermal()) return Integer.MAX_VALUE;
        int body = printableHeightPx()
                - contHeaderHeightPx
                - tableHeaderHeightPx
                - footerHeightPx
                - safetyMarginPx;
        return Math.max(1, body / rowHeightPx);
    }

    /** Printable width in pixels. */
    public int printableWidthPx() {
        return mmToPx(pageWidthMm - marginLeftMm - marginRightMm);
    }

    /** Printable height in pixels. Returns 0 for thermal (auto-height) paper. */
    public int printableHeightPx() {
        if (pageHeightMm <= 0) return 0;
        return mmToPx(pageHeightMm - marginTopMm - marginBottomMm);
    }

    /** {@code true} when this is a thermal receipt roll (continuous, no fixed height). */
    public boolean isThermal() {
        return pageHeightMm <= 0;
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    /** 1 mm at 96 dpi ≈ 3.7795 px */
    public static final float MM_TO_PX = 3.7795f;

    private static int mmToPx(float mm) {
        return Math.round(mm * MM_TO_PX);
    }
}
