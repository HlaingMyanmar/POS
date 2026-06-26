package org.sspd.servicemgmt.printingoptions.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Page dimension constants for the PDF engine.
 * All values are in millimetres unless noted.
 */
@Data
@Builder
public class PrintPageConfig {

    public enum PaperSize { A4, A5, POS_58MM, POS_80MM }

    private PaperSize paperSize;

    /** Page width in mm */
    private float pageWidthMm;
    /** Page height in mm (0 = auto / thermal roll) */
    private float pageHeightMm;

    /** Left + right margin in mm */
    private float marginHorizMm;
    /** Top + bottom margin in mm */
    private float marginVertMm;

    /**
     * Estimated number of product rows that fit on the first page
     * (after header, info-blocks, and totals area are subtracted).
     */
    private int rowsOnFirstPage;

    /**
     * Estimated rows that fit on subsequent (continuation) pages,
     * which have a smaller header and no totals block.
     */
    private int rowsOnContinuationPage;

    // ── factory helpers ─────────────────────────────────────────────────────

    public static PrintPageConfig a4() {
        return PrintPageConfig.builder()
                .paperSize(PaperSize.A4)
                .pageWidthMm(210).pageHeightMm(297)
                .marginHorizMm(10).marginVertMm(10)
                .rowsOnFirstPage(22)
                .rowsOnContinuationPage(32)
                .build();
    }

    public static PrintPageConfig a5() {
        return PrintPageConfig.builder()
                .paperSize(PaperSize.A5)
                .pageWidthMm(148).pageHeightMm(210)
                .marginHorizMm(8).marginVertMm(8)
                .rowsOnFirstPage(14)
                .rowsOnContinuationPage(22)
                .build();
    }

    public static PrintPageConfig pos80mm() {
        return PrintPageConfig.builder()
                .paperSize(PaperSize.POS_80MM)
                .pageWidthMm(80).pageHeightMm(0)
                .marginHorizMm(3).marginVertMm(3)
                .rowsOnFirstPage(Integer.MAX_VALUE)
                .rowsOnContinuationPage(Integer.MAX_VALUE)
                .build();
    }

    public static PrintPageConfig pos58mm() {
        return PrintPageConfig.builder()
                .paperSize(PaperSize.POS_58MM)
                .pageWidthMm(58).pageHeightMm(0)
                .marginHorizMm(2).marginVertMm(2)
                .rowsOnFirstPage(Integer.MAX_VALUE)
                .rowsOnContinuationPage(Integer.MAX_VALUE)
                .build();
    }
}
