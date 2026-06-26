import React from 'react';
import { InvoiceData, PrintOptions } from '../types/print.types';
import { getPrintConfig } from '../config/print.config';
import { usePagination } from '../hooks/usePagination';
import { InvoiceHeader } from './InvoiceHeader';
import { InvoiceInfoBlocks } from './InvoiceInfoBlocks';
import { InvoiceTable } from './InvoiceTable';
import { InvoiceSummary } from './InvoiceSummary';
import '../styles/print-base.css';

interface MultiPageInvoiceProps {
  data: InvoiceData;
  options: PrintOptions;
}

/**
 * Top-level React invoice renderer.
 *
 * Automatically paginates line items and renders one .invoice-page div per
 * page.  Works both in:
 *  - Browser preview: each .invoice-page has screen shadow + margin
 *  - @media print: pages flow with page-break-after: always
 *
 * Usage:
 *   <MultiPageInvoice data={saleData} options={{ paperSize: 'A4', design: 'STANDARD' }} />
 */
export const MultiPageInvoice = React.forwardRef<HTMLDivElement, MultiPageInvoiceProps>(
  ({ data, options }, ref) => {
    const pages = usePagination(data.lineItems, options);
    const { paperSize, showSerial, showPaymentHistory, showSignatures, sign1Label, sign2Label } = options;

    const cfg = getPrintConfig(paperSize);
    const pageClass = [
      'invoice-page',
      cfg.cssClassName,
      cfg.isThermal ? 'inv-thermal' : '',
    ].filter(Boolean).join(' ');

    return (
      <div ref={ref} className="invoice-print-root">
        {pages.map((page) => (
          <div key={page.pageNumber} className={pageClass}>
            {/* ── Header ── */}
            <InvoiceHeader
              data={data}
              paperSize={paperSize}
              currentPage={page.pageNumber}
              totalPages={page.totalPages}
            />

            {/* ── Body ── */}
            <div className="inv-body">
              {/* Info blocks on first page only */}
              {page.isFirst && <InvoiceInfoBlocks data={data} />}

              {/* Item rows for this page */}
              <InvoiceTable
                items={page.items}
                showSerial={showSerial ?? true}
              />

              {/* Totals + footer on last page only */}
              {page.isLast ? (
                <InvoiceSummary
                  data={data}
                  paperSize={paperSize}
                  showPaymentHistory={showPaymentHistory ?? true}
                  showSignatures={showSignatures ?? false}
                  sign1Label={sign1Label}
                  sign2Label={sign2Label}
                />
              ) : (
                /* Continuation indicator on non-last pages */
                <div className="inv-footer">
                  {data.invoiceNo}&nbsp;·&nbsp;Continued on next page&nbsp;·&nbsp;
                  Page {page.pageNumber} of {page.totalPages}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    );
  }
);

MultiPageInvoice.displayName = 'MultiPageInvoice';
