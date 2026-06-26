import React from 'react';
import { InvoiceData, PaperSize } from '../types/print.types';

interface InvoiceHeaderProps {
  data: InvoiceData;
  paperSize: PaperSize;
  currentPage: number;
  totalPages: number;
}

/**
 * Full header for the first page — company branding + invoice meta.
 * Continuation pages render a compact header instead.
 */
export const InvoiceHeader: React.FC<InvoiceHeaderProps> = ({
  data,
  paperSize,
  currentPage,
  totalPages,
}) => {
  const isCompact = paperSize === 'A5';

  if (currentPage > 1) {
    return (
      <div className="inv-cont-header">
        <span className="inv-cont-title">
          {data.invoiceTitle} — {data.invoiceNo}
        </span>
        <span className="inv-cont-page">
          Page {currentPage} of {totalPages}
        </span>
      </div>
    );
  }

  return (
    <div className={`inv-header${isCompact ? ' inv-header--compact' : ''}`}
         style={data.headerColor ? { backgroundColor: data.headerColor } : undefined}>
      {/* Left: logo + company */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {data.logoBase64 && (
          <img
            src={data.logoBase64}
            alt="logo"
            className="inv-logo"
          />
        )}
        <div>
          <div className={`inv-company-name${isCompact ? ' inv-company-name--sm' : ''}`}>
            {data.companyName}
          </div>
          <div className="inv-company-sub">
            {[data.companyAddress, data.companyPhone, data.companyEmail]
              .filter(Boolean)
              .join(' | ')}
          </div>
        </div>
      </div>

      {/* Right: invoice meta */}
      <div style={{ textAlign: 'right', flexShrink: 0 }}>
        <div className="inv-meta-label">{data.invoiceTitle}</div>
        <div className="inv-meta-number">{data.invoiceNo}</div>
        <div className="inv-meta-date">{data.invoiceDate}</div>
      </div>
    </div>
  );
};
