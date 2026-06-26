// ─── Types ────────────────────────────────────────────────────────────────────
export * from './types/print.types';

// ─── Config ───────────────────────────────────────────────────────────────────
export {
  PRINT_CONFIG,
  getPrintConfig,
  debugPrintConfig,
} from './config/print.config';
export type { RawPaperConfig, CompiledPaperConfig } from './config/print.config';

// ─── Utilities ────────────────────────────────────────────────────────────────
export { getPageDimensions, printableWidthMm, printableHeightMm } from './utils/pageConfig';
export {
  paginateLineItems,
  measureRowHeightPx,
  calcDynamicRowsPerPage,
  formatPageLabel,
  DEFAULT_ROW_HEIGHT_PX,
} from './utils/paginationEngine';
export { printQueue } from './utils/printQueue';
export {
  fetchHtmlPreview,
  fetchPdfObjectUrl,
  buildSalePdfUrl,
  buildServiceJobPdfUrl,
  buildBookingPdfUrl,
} from './utils/htmlPdfClient';

// ─── Hooks ────────────────────────────────────────────────────────────────────
export { useHtmlPreview, usePdfDownload, usePrintQueue, useIframePrint } from './hooks/usePrint';
export { usePagination } from './hooks/usePagination';

// ─── Components ───────────────────────────────────────────────────────────────
export { MultiPageInvoice }    from './components/MultiPageInvoice';
export { InvoiceHeader }       from './components/InvoiceHeader';
export { InvoiceInfoBlocks }   from './components/InvoiceInfoBlocks';
export { InvoiceTable }        from './components/InvoiceTable';
export { InvoiceSummary }      from './components/InvoiceSummary';
export { PrintButton }         from './components/PrintButton';
export { InvoicePrintPreview } from './components/InvoicePrintPreview';
