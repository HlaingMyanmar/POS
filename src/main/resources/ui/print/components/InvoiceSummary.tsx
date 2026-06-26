import React from 'react';
import { InvoiceData, PaymentEntry, PaperSize } from '../types/print.types';

interface InvoiceSummaryProps {
  data: InvoiceData;
  paperSize: PaperSize;
  showPaymentHistory?: boolean;
  showSignatures?: boolean;
  sign1Label?: string;
  sign2Label?: string;
}

/**
 * The totals + payment history + signatures + footer block.
 * Only rendered on the last page of a multi-page invoice.
 */
export const InvoiceSummary: React.FC<InvoiceSummaryProps> = ({
  data,
  paperSize,
  showPaymentHistory = true,
  showSignatures = false,
  sign1Label = 'Prepared By',
  sign2Label = 'Received By',
}) => {
  const isCompact = paperSize === 'A5';
  const hasPayments = showPaymentHistory && (data.payments?.length ?? 0) > 0;

  return (
    <>
      {/* ── Totals + Payment History ── */}
      <div className="inv-bottom">
        {/* Payment history */}
        <div className="inv-payment-section">
          {hasPayments && (
            <>
              <div className="inv-section-label">Payment History</div>
              <div className="inv-table-wrap" style={{ marginBottom: 0 }}>
                <table className="inv-pay-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Method</th>
                      <th className="col-num">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(data.payments as PaymentEntry[]).map((p, i) => (
                      <tr key={i}>
                        <td>{p.date}</td>
                        <td>{p.method}</td>
                        <td className="col-num">{p.amount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>

        {/* Summary box */}
        <div className={`inv-summary-box${isCompact ? ' inv-summary-box--sm' : ''}`}>
          <div className="inv-summary-row">
            <span className="inv-summary-row__label">Subtotal</span>
            <span className="inv-summary-row__value">{data.subtotal}</span>
          </div>
          <div className="inv-summary-row">
            <span className="inv-summary-row__label">Discount</span>
            <span className="inv-summary-row__value">{data.discount}</span>
          </div>
          <div className="inv-summary-row inv-summary-row--sub">
            <span className="inv-summary-row__label">Net Amount</span>
            <span className="inv-summary-row__value">{data.netAmount}</span>
          </div>
          <div className="inv-summary-row">
            <span className="inv-summary-row__label">Paid</span>
            <span className="inv-summary-row__value">{data.paid}</span>
          </div>
          <div className="inv-summary-row inv-summary-row--highlight">
            <span className="inv-summary-row__label">Balance Due</span>
            <span className="inv-summary-row__value">{data.balanceDue}</span>
          </div>
        </div>
      </div>

      {/* ── Remark ── */}
      {data.remark && (
        <>
          <div className="inv-section-label" style={{ marginTop: 14 }}>Remarks</div>
          <div className="inv-remark">{data.remark}</div>
        </>
      )}

      {/* ── Customer notice ── */}
      {data.customerNotice && (
        <div className="inv-notice">{data.customerNotice}</div>
      )}

      {/* ── Signatures ── */}
      {showSignatures && (
        <div className="inv-signatures">
          <div className="inv-sign">
            <div className="inv-sign__line">{sign1Label}</div>
          </div>
          <div className="inv-sign">
            <div className="inv-sign__line">{sign2Label}</div>
          </div>
        </div>
      )}

      {/* ── Footer bar ── */}
      <div className="inv-footer">
        {data.companyName}
        {data.footerNote && <>&nbsp;·&nbsp;{data.footerNote}</>}
      </div>
    </>
  );
};
