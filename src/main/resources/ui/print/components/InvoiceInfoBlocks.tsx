import React from 'react';
import { InvoiceData } from '../types/print.types';

interface InvoiceInfoBlocksProps {
  data: InvoiceData;
}

function badgeClass(status?: string): string {
  if (!status) return 'inv-badge';
  const s = status.toLowerCase();
  if (s === 'partial') return 'inv-badge inv-badge--partial';
  if (s === 'pending' || s === 'unpaid') return 'inv-badge inv-badge--neutral';
  return 'inv-badge';
}

/**
 * The "Bill To" and "Invoice Details" info blocks shown on the first page.
 */
export const InvoiceInfoBlocks: React.FC<InvoiceInfoBlocksProps> = ({ data }) => (
  <div className="inv-info-grid">
    {/* Bill To */}
    <div className="inv-block">
      <div className="inv-block__title">Bill To</div>
      <div className="inv-block__row">
        <span className="inv-block__label">Customer</span>
        <span className="inv-block__value">{data.customerName || '—'}</span>
      </div>
      {data.customerPhone && (
        <div className="inv-block__row">
          <span className="inv-block__label">Phone</span>
          <span className="inv-block__value">{data.customerPhone}</span>
        </div>
      )}
      <div className="inv-block__row">
        <span className="inv-block__label">Status</span>
        <span className="inv-block__value">
          <span className={badgeClass(data.paymentStatus)}>
            {data.paymentStatus || '—'}
          </span>
        </span>
      </div>
    </div>

    {/* Invoice Details */}
    <div className="inv-block">
      <div className="inv-block__title">Invoice Details</div>
      <div className="inv-block__row">
        <span className="inv-block__label">Cashier</span>
        <span className="inv-block__value">{data.cashierName || '—'}</span>
      </div>
      <div className="inv-block__row">
        <span className="inv-block__label">Date</span>
        <span className="inv-block__value">{data.invoiceDate}</span>
      </div>
      {data.dueDate && (
        <div className="inv-block__row">
          <span className="inv-block__label">Due Date</span>
          <span className="inv-block__value">{data.dueDate}</span>
        </div>
      )}
    </div>
  </div>
);
