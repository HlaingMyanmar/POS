package org.sspd.servicemgmt.printingoptions.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of all supported paper-size layout configurations.
 *
 * <h3>This is the SINGLE source of truth for all print layout values.</h3>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  To change footer position   → adjust footerHeightPx               │
 * │  To change rows per page     → adjust rowHeightPx or safetyMarginPx│
 * │  To change page margins      → adjust marginTopMm / marginBottomMm  │
 * │  To add a new paper size     → add a new entry in REGISTRY          │
 * │                                                                     │
 * │  Zero other files need to change.                                   │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>How row capacity is calculated</h3>
 * <pre>
 *   printableHeightPx = (pageHeightMm − marginTopMm − marginBottomMm) × 3.7795
 *
 *   rowsOnFirstPage =
 *     (printableHeightPx − headerHeightPx − infoBlocksHeightPx
 *      − tableHeaderHeightPx − totalsAreaHeightPx
 *      − footerHeightPx − safetyMarginPx)
 *     ÷ rowHeightPx
 *
 *   rowsOnContinuationPage =
 *     (printableHeightPx − contHeaderHeightPx
 *      − tableHeaderHeightPx − footerHeightPx − safetyMarginPx)
 *     ÷ rowHeightPx
 * </pre>
 *
 * Component heights were measured from the rendered Thymeleaf templates
 * at 96 dpi / 100 % zoom.  Re-measure when template markup changes.
 */
@Component
public class PrintLayoutConfigRegistry {

    // ─────────────────────────────────────────────────────────────────────────
    //  Raw layout definitions — EDIT ONLY HERE
    // ─────────────────────────────────────────────────────────────────────────

    private static final Map<String, PrintLayoutConfig> REGISTRY;

    static {
        Map<String, PrintLayoutConfig> m = new LinkedHashMap<>();

        // ── A4 ────────────────────────────────────────────────────────────────
        //  Physical:  210 × 297 mm,  10 mm margins all sides
        //  Printable: 190 × 277 mm  →  718 × 1047 px
        //
        //  First-page layout heights (px):
        //    Header band          82   (logo + company name + invoice meta)
        //    Info blocks          96   (Bill To card + Invoice Details card)
        //    Table header         28   (<thead>)
        //    Totals area         130   (summary box + payment history)
        //    Footer bar           30
        //    Safety margin        20
        //    ─────────────────────────
        //    Reserved            386 px
        //    Available for rows  661 px
        //    Row height           33 px
        //    Rows on first page   20
        //
        //  Continuation-page layout heights (px):
        //    Cont. header         42
        //    Table header         28
        //    Footer bar           30
        //    Safety margin        20
        //    ─────────────────────────
        //    Reserved            120 px
        //    Available for rows  927 px
        //    Rows on cont. page   28
        // ─────────────────────────────────────────────────────────────────────
        m.put("A4", PrintLayoutConfig.builder()
                .name("A4")
                .pageWidthMm(210).pageHeightMm(297)
                .marginTopMm(10).marginBottomMm(10)
                .marginLeftMm(10).marginRightMm(10)
                // component heights (measured from rendered template)
                .headerHeightPx(82)
                .infoBlocksHeightPx(96)
                .contHeaderHeightPx(42)
                .tableHeaderHeightPx(28)
                .rowHeightPx(33)
                .totalsAreaHeightPx(130)
                .footerHeightPx(30)
                .safetyMarginPx(20)
                // CSS
                .cssPageSize("A4 portrait")
                .cssClassName("invoice-page--a4")
                .build());

        // ── A5 ────────────────────────────────────────────────────────────────
        //  Physical:  148 × 210 mm,  8 mm margins all sides
        //  Printable: 132 × 194 mm  →  499 × 733 px
        //
        //  First-page available: 733 − 64 − 74 − 24 − 105 − 26 − 16 = 424 px
        //  Rows on first page:  424 ÷ 29 = 14
        //
        //  Continuation available: 733 − 36 − 24 − 26 − 16 = 631 px
        //  Rows on cont. page:  631 ÷ 29 = 21
        // ─────────────────────────────────────────────────────────────────────
        m.put("A5", PrintLayoutConfig.builder()
                .name("A5")
                .pageWidthMm(148).pageHeightMm(210)
                .marginTopMm(8).marginBottomMm(8)
                .marginLeftMm(8).marginRightMm(8)
                .headerHeightPx(64)
                .infoBlocksHeightPx(74)
                .contHeaderHeightPx(36)
                .tableHeaderHeightPx(24)
                .rowHeightPx(29)
                .totalsAreaHeightPx(105)
                .footerHeightPx(26)
                .safetyMarginPx(16)
                .cssPageSize("A5 portrait")
                .cssClassName("invoice-page--a5")
                .build());

        // ── 80 mm thermal ─────────────────────────────────────────────────────
        //  Continuous roll — no pagination.
        m.put("POS_80MM", PrintLayoutConfig.builder()
                .name("POS_80MM")
                .pageWidthMm(80).pageHeightMm(0)  // 0 = auto / roll
                .marginTopMm(3).marginBottomMm(3)
                .marginLeftMm(3).marginRightMm(3)
                // thermal: all heights irrelevant (isThermal() → rows = MAX_INT)
                .headerHeightPx(0).infoBlocksHeightPx(0)
                .contHeaderHeightPx(0).tableHeaderHeightPx(0)
                .rowHeightPx(0).totalsAreaHeightPx(0)
                .footerHeightPx(0).safetyMarginPx(0)
                .cssPageSize("80mm auto")
                .cssClassName("invoice-page--pos-80mm")
                .build());

        // ── 58 mm thermal ─────────────────────────────────────────────────────
        m.put("POS_58MM", PrintLayoutConfig.builder()
                .name("POS_58MM")
                .pageWidthMm(58).pageHeightMm(0)
                .marginTopMm(2).marginBottomMm(2)
                .marginLeftMm(2).marginRightMm(2)
                .headerHeightPx(0).infoBlocksHeightPx(0)
                .contHeaderHeightPx(0).tableHeaderHeightPx(0)
                .rowHeightPx(0).totalsAreaHeightPx(0)
                .footerHeightPx(0).safetyMarginPx(0)
                .cssPageSize("58mm auto")
                .cssClassName("invoice-page--pos-58mm")
                .build());

        REGISTRY = Collections.unmodifiableMap(m);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the layout config for {@code paperSize}.
     * Falls back to A4 if the key is unknown or null.
     */
    public PrintLayoutConfig get(String paperSize) {
        if (paperSize == null) return REGISTRY.get("A4");
        PrintLayoutConfig cfg = REGISTRY.get(paperSize.toUpperCase().replace("-", "_"));
        return cfg != null ? cfg : REGISTRY.get("A4");
    }

    /** Returns a read-only view of all registered configurations. */
    public Map<String, PrintLayoutConfig> getAll() {
        return REGISTRY;
    }

    /**
     * Convenience: expose the A4 config row calculation for integration tests
     * without spinning up the full Spring context.
     */
    public static PrintLayoutConfig defaultA4() {
        PrintLayoutConfigRegistry r = new PrintLayoutConfigRegistry();
        return r.get("A4");
    }
}
