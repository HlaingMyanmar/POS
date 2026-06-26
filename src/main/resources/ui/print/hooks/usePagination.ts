import { useMemo } from 'react';
import { InvoicePage, PrintLineItem, PrintOptions } from '../types/print.types';
import { paginateLineItems } from '../utils/paginationEngine';

/**
 * Memoised pagination hook.
 *
 * Re-computes pages only when items array reference or options change.
 *
 * Usage:
 *   const pages = usePagination(lineItems, printOptions);
 *   pages.forEach(page => <InvoicePage key={page.pageNumber} page={page} />)
 */
export function usePagination(
  items: PrintLineItem[],
  options: PrintOptions
): InvoicePage[] {
  return useMemo(
    () => paginateLineItems(items, options),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items, options.paperSize, options.rowsOverride]
  );
}
