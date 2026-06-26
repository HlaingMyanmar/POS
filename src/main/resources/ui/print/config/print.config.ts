/**
 * print.config.ts — Frontend mirror of PrintLayoutConfigRegistry.java
 *
 * This is the SINGLE source of truth for all frontend print layout values.
 * Row capacities are derived from physical measurements (physics formula),
 * never hardcoded — matching the backend registry exactly.
 *
 * Formula (mirrors PrintLayoutConfig.java):
 *
 *   printableHeightPx = (pageHeightMm − marginTopMm − marginBottomMm) × MM_TO_PX
 *
 *   rowsOnFirstPage =
 *     floor( (printableHeightPx − headerHeightPx − infoBlocksHeightPx
 *             − tableHeaderHeightPx − totalsAreaHeightPx
 *             − footerHeightPx − safetyMarginPx) / rowHeightPx )
 *
 *   rowsOnContinuationPage =
 *     floor( (printableHeightPx − contHeaderHeightPx
 *             − tableHeaderHeightPx − footerHeightPx − safetyMarginPx)
 *            / rowHeightPx )
 *
 * To change row capacity → adjust the raw config value (not the formula).
 * To add a paper size    → add a RAW_CONFIGS entry.
 */

import type { PaperSize } from '../types/print.types';

/** 1 mm in CSS pixels at 96 dpi — matches MM_TO_PX in PrintLayoutConfig.java */
const MM_TO_PX = 3.7795;

// ─── Raw config shape ────────────────────────────────────────────────────────

export interface RawPaperConfig {
  readonly name: string;
  readonly pageWidthMm: number;
  /** 0 = auto height (thermal roll, no pagination) */
  readonly pageHeightMm: number;
  readonly marginTopMm: number;
  readonly marginBottomMm: number;
  readonly marginLeftMm: number;
  readonly marginRightMm: number;
  /** Full header band height in px — first page only */
  readonly headerHeightPx: number;
  /** Compact header height in px — continuation pages */
  readonly contHeaderHeightPx: number;
  /** Info blocks (Bill To + Invoice Details) height in px */
  readonly infoBlocksHeightPx: number;
  /** Table <thead> row height in px */
  readonly tableHeaderHeightPx: number;
  /** Single data row height in px */
  readonly rowHeightPx: number;
  /** Totals area height in px (summary box + payment history) */
  readonly totalsAreaHeightPx: number;
  /** Footer bar height in px */
  readonly footerHeightPx: number;
  /** Anti-overflow safety buffer in px */
  readonly safetyMarginPx: number;
  /** CSS @page size string */
  readonly cssPageSize: string;
  /** CSS class name applied to the page wrapper div */
  readonly cssClassName: string;
}

// ─── Compiled config shape (adds derived fields) ─────────────────────────────

export interface CompiledPaperConfig extends RawPaperConfig {
  /** True when pageHeightMm === 0 (thermal roll) */
  readonly isThermal: boolean;
  /** Printable height in px (0 for thermal) */
  readonly printableHeightPx: number;
  /** Max rows on page 1 (Infinity for thermal) */
  readonly rowsOnFirstPage: number;
  /** Max rows on continuation pages (Infinity for thermal) */
  readonly rowsOnContinuationPage: number;
}

// ─── Compiler ────────────────────────────────────────────────────────────────

function compilePaperConfig(raw: RawPaperConfig): CompiledPaperConfig {
  const isThermal = raw.pageHeightMm <= 0;

  if (isThermal) {
    return { ...raw, isThermal: true, printableHeightPx: 0, rowsOnFirstPage: Infinity, rowsOnContinuationPage: Infinity };
  }

  const printableHeightPx = Math.round(
    (raw.pageHeightMm - raw.marginTopMm - raw.marginBottomMm) * MM_TO_PX
  );

  const firstPageBody =
    printableHeightPx
    - raw.headerHeightPx
    - raw.infoBlocksHeightPx
    - raw.tableHeaderHeightPx
    - raw.totalsAreaHeightPx
    - raw.footerHeightPx
    - raw.safetyMarginPx;

  const contPageBody =
    printableHeightPx
    - raw.contHeaderHeightPx
    - raw.tableHeaderHeightPx
    - raw.footerHeightPx
    - raw.safetyMarginPx;

  return {
    ...raw,
    isThermal: false,
    printableHeightPx,
    rowsOnFirstPage: Math.max(1, Math.floor(firstPageBody / raw.rowHeightPx)),
    rowsOnContinuationPage: Math.max(1, Math.floor(contPageBody / raw.rowHeightPx)),
  };
}

// ─── Raw definitions — EDIT ONLY HERE ────────────────────────────────────────
// Values must mirror PrintLayoutConfigRegistry.java exactly.
// Heights are measured from the rendered Thymeleaf templates at 96 dpi / 100%.

const RAW_CONFIGS: Record<PaperSize, RawPaperConfig> = {

  // ── A4 ──────────────────────────────────────────────────────────────────────
  //  Physical: 210 × 297 mm  |  Printable: 190 × 277 mm  →  718 × 1047 px
  //  Reserved first page: 82+96+28+130+30+20 = 386 px  →  661 px available
  //  rowsOnFirstPage = floor(661 / 33) = 20
  //  Reserved cont page: 42+28+30+20 = 120 px  →  927 px available
  //  rowsOnContinuationPage = floor(927 / 33) = 28
  A4: {
    name: 'A4',
    pageWidthMm: 210,       pageHeightMm: 297,
    marginTopMm: 10,        marginBottomMm: 10,
    marginLeftMm: 10,       marginRightMm: 10,
    headerHeightPx: 82,
    contHeaderHeightPx: 42,
    infoBlocksHeightPx: 96,
    tableHeaderHeightPx: 28,
    rowHeightPx: 33,
    totalsAreaHeightPx: 130,
    footerHeightPx: 30,
    safetyMarginPx: 20,
    cssPageSize: 'A4 portrait',
    cssClassName: 'invoice-page--a4',
  },

  // ── A5 ──────────────────────────────────────────────────────────────────────
  //  Physical: 148 × 210 mm  |  Printable: 132 × 194 mm  →  499 × 733 px
  //  Reserved first page: 64+74+24+105+26+16 = 309 px  →  424 px available
  //  rowsOnFirstPage = floor(424 / 29) = 14
  //  Reserved cont page: 36+24+26+16 = 102 px  →  631 px available
  //  rowsOnContinuationPage = floor(631 / 29) = 21
  A5: {
    name: 'A5',
    pageWidthMm: 148,       pageHeightMm: 210,
    marginTopMm: 8,         marginBottomMm: 8,
    marginLeftMm: 8,        marginRightMm: 8,
    headerHeightPx: 64,
    contHeaderHeightPx: 36,
    infoBlocksHeightPx: 74,
    tableHeaderHeightPx: 24,
    rowHeightPx: 29,
    totalsAreaHeightPx: 105,
    footerHeightPx: 26,
    safetyMarginPx: 16,
    cssPageSize: 'A5 portrait',
    cssClassName: 'invoice-page--a5',
  },

  // ── 80 mm thermal ───────────────────────────────────────────────────────────
  //  Continuous roll — no pagination. All heights are 0 (isThermal = true).
  POS_80MM: {
    name: 'POS_80MM',
    pageWidthMm: 80,        pageHeightMm: 0,
    marginTopMm: 3,         marginBottomMm: 3,
    marginLeftMm: 3,        marginRightMm: 3,
    headerHeightPx: 0,      contHeaderHeightPx: 0,
    infoBlocksHeightPx: 0,  tableHeaderHeightPx: 0,
    rowHeightPx: 0,         totalsAreaHeightPx: 0,
    footerHeightPx: 0,      safetyMarginPx: 0,
    cssPageSize: '80mm auto',
    cssClassName: 'invoice-page--pos-80mm',
  },

  // ── 58 mm thermal ───────────────────────────────────────────────────────────
  POS_58MM: {
    name: 'POS_58MM',
    pageWidthMm: 58,        pageHeightMm: 0,
    marginTopMm: 2,         marginBottomMm: 2,
    marginLeftMm: 2,        marginRightMm: 2,
    headerHeightPx: 0,      contHeaderHeightPx: 0,
    infoBlocksHeightPx: 0,  tableHeaderHeightPx: 0,
    rowHeightPx: 0,         totalsAreaHeightPx: 0,
    footerHeightPx: 0,      safetyMarginPx: 0,
    cssPageSize: '58mm auto',
    cssClassName: 'invoice-page--pos-58mm',
  },
};

// ─── Compiled registry ───────────────────────────────────────────────────────

export const PRINT_CONFIG: Record<PaperSize, CompiledPaperConfig> = {
  A4:       compilePaperConfig(RAW_CONFIGS.A4),
  A5:       compilePaperConfig(RAW_CONFIGS.A5),
  POS_80MM: compilePaperConfig(RAW_CONFIGS.POS_80MM),
  POS_58MM: compilePaperConfig(RAW_CONFIGS.POS_58MM),
};

// ─── Public API ──────────────────────────────────────────────────────────────

/** Returns the compiled config for a paper size. Falls back to A4. */
export function getPrintConfig(paper: PaperSize | string): CompiledPaperConfig {
  return (PRINT_CONFIG as Record<string, CompiledPaperConfig>)[paper] ?? PRINT_CONFIG.A4;
}

/** Logs derived row capacity values to the browser console (dev only). */
export function debugPrintConfig(paper: PaperSize | string): void {
  const cfg = getPrintConfig(paper);
  console.group(`[PrintConfig] ${cfg.name}`);
  console.log('printableHeightPx    :', cfg.printableHeightPx);
  console.log('rowsOnFirstPage      :', cfg.rowsOnFirstPage);
  console.log('rowsOnContinuationPage:', cfg.rowsOnContinuationPage);
  console.log('isThermal            :', cfg.isThermal);
  console.groupEnd();
}
