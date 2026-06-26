import { InvoicePage, PageDimensions, PrintLineItem, PrintOptions } from '../types/print.types';
import { getPrintConfig } from '../config/print.config';
import { getPageDimensions } from './pageConfig';

/**
 * Enterprise pagination engine.
 *
 * Determines how line items are distributed across pages.
 * Works for both browser-rendered React components and backend PDF generation.
 *
 * Algorithm:
 *  1. Fetch page dimension constants from pageConfig.
 *  2. Apply rowsOverride if the caller knows better (e.g. items with multi-line text).
 *  3. Fill page 1 up to rowsOnFirstPage.
 *  4. Fill subsequent pages up to rowsOnContinuationPage.
 *  5. Return an array of InvoicePage descriptors with correct flags.
 *
 * The algorithm intentionally uses WHOLE-ROW placement (no row splitting).
 * If a row would overflow a page it is moved to the next page entirely.
 */
export function paginateLineItems(
  items: PrintLineItem[],
  options: PrintOptions
): InvoicePage[] {
  const dims: PageDimensions = getPageDimensions(options.paperSize);

  // Thermal receipts are one continuous page — no pagination needed
  if (!isFinite(dims.rowsOnFirstPage)) {
    return [
      {
        pageNumber: 1,
        totalPages: 1,
        items: [...items],
        isFirst: true,
        isLast: true,
      },
    ];
  }

  const firstLimit = options.rowsOverride
    ? options.rowsOverride
    : dims.rowsOnFirstPage;

  const contLimit = options.rowsOverride
    ? options.rowsOverride
    : dims.rowsOnContinuationPage;

  const pages: InvoicePage[] = [];
  let idx = 0;
  let pageNum = 1;

  while (idx < items.length) {
    const limit = pageNum === 1 ? firstLimit : contLimit;
    const end = Math.min(idx + limit, items.length);
    pages.push({
      pageNumber: pageNum,
      totalPages: 0,         // filled in below
      items: items.slice(idx, end),
      isFirst: pageNum === 1,
      isLast: end >= items.length,
    });
    idx = end;
    pageNum++;
  }

  // Edge case: empty invoice still needs one page
  if (pages.length === 0) {
    pages.push({ pageNumber: 1, totalPages: 1, items: [], isFirst: true, isLast: true });
  }

  const total = pages.length;
  pages.forEach((p) => (p.totalPages = total));
  return pages;
}

/**
 * Measures the pixel height of a rendered table row.
 *
 * Pass a mounted <tr> element to get the actual browser-measured height.
 * Falls back to DEFAULT_ROW_HEIGHT_PX if the element is not yet mounted.
 */
export const DEFAULT_ROW_HEIGHT_PX = 33;

export function measureRowHeightPx(rowEl: HTMLTableRowElement | null): number {
  if (!rowEl) return DEFAULT_ROW_HEIGHT_PX;
  return rowEl.getBoundingClientRect().height || DEFAULT_ROW_HEIGHT_PX;
}

/**
 * Dynamically calculates how many rows fit in the available page body height.
 *
 * Used when row heights vary (e.g. multi-line product names, long serials).
 *
 * @param containerEl  The scroll container or page element to measure
 * @param headerPx     Estimated pixels consumed by header + info blocks
 * @param footerPx     Estimated pixels consumed by totals + footer
 * @param rowHeightPx  Average row height (from measureRowHeightPx)
 */
export function calcDynamicRowsPerPage(
  containerEl: HTMLElement | null,
  headerPx: number,
  footerPx: number,
  rowHeightPx: number
): number {
  const fallback = getPrintConfig('A4').rowsOnFirstPage;
  if (!containerEl) return fallback;
  const totalPx = containerEl.getBoundingClientRect().height;
  const available = totalPx - headerPx - footerPx - getPrintConfig('A4').safetyMarginPx;
  return Math.max(1, Math.floor(available / rowHeightPx));
}

/**
 * Formats the page indicator string: "Page 1 of 3".
 */
export function formatPageLabel(current: number, total: number): string {
  return `Page ${current} of ${total}`;
}
