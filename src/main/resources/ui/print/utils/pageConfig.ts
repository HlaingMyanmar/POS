/**
 * pageConfig.ts
 * Backward-compatible adapter over print.config.ts.
 *
 * All row capacities are now derived from the physics formula in
 * print.config.ts — never hardcoded here.  Callers that already
 * use getPageDimensions() / printableWidthMm() / printableHeightMm()
 * continue to work without changes.
 */

import { getPrintConfig } from '../config/print.config';
import { PageDimensions, PaperSize } from '../types/print.types';

export function getPageDimensions(paper: PaperSize): PageDimensions {
  const cfg = getPrintConfig(paper);
  return {
    widthMm: cfg.pageWidthMm,
    heightMm: cfg.pageHeightMm,
    marginHorizMm: cfg.marginLeftMm,
    marginVertMm: cfg.marginTopMm,
    rowsOnFirstPage: cfg.rowsOnFirstPage,
    rowsOnContinuationPage: cfg.rowsOnContinuationPage,
    cssPageSize: cfg.cssPageSize,
  };
}

/** Returns the printable width in mm. */
export function printableWidthMm(paper: PaperSize): number {
  const cfg = getPrintConfig(paper);
  return cfg.pageWidthMm - cfg.marginLeftMm * 2;
}

/** Returns the printable height in mm (0 for thermal / auto). */
export function printableHeightMm(paper: PaperSize): number {
  const cfg = getPrintConfig(paper);
  if (cfg.pageHeightMm === 0) return 0;
  return cfg.pageHeightMm - cfg.marginTopMm * 2;
}
