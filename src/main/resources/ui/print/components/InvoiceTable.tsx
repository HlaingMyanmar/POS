import React from 'react';
import { PrintLineItem } from '../types/print.types';

interface InvoiceTableProps {
  items: PrintLineItem[];
  showSerial?: boolean;
}

/**
 * Product / service line items table.
 *
 * Designed so every <tr> carries `break-inside: avoid` — this prevents
 * a row from being torn apart between two printed pages when the browser
 * (or Flying Saucer) calculates page breaks.
 */
export const InvoiceTable: React.FC<InvoiceTableProps> = ({
  items,
  showSerial = true,
}) => {
  const colItem   = showSerial ? '38%' : '62%';
  const colSerial = '22%';

  return (
    <div className="inv-table-wrap">
      <table className="inv-table">
        <thead>
          <tr>
            <th className="col-center" style={{ width: '5%' }}>#</th>
            <th style={{ width: colItem }}>Item / Description</th>
            {showSerial && <th style={{ width: colSerial }}>Serial / Info</th>}
            <th className="col-center" style={{ width: '7%' }}>Qty</th>
            <th className="col-num"    style={{ width: '14%' }}>Unit Price</th>
            <th className="col-num"    style={{ width: '14%' }}>Amount</th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr>
              <td
                colSpan={showSerial ? 6 : 5}
                className="col-center"
                style={{ padding: '16px', color: '#94a3b8' }}
              >
                No items
              </td>
            </tr>
          ) : (
            items.map((item) => (
              <tr key={item.rowNo}>
                <td className="col-center">{item.rowNo}</td>
                <td>
                  <div>{item.productName}</div>
                  {item.warrantyLabel && (
                    <div className="inv-item-sub">Warranty: {item.warrantyLabel}</div>
                  )}
                </td>
                {showSerial && <td>{item.serialInfo || '—'}</td>}
                <td className="col-center">{item.qty}</td>
                <td className="col-num">{item.unitPrice}</td>
                <td className="col-num">{item.subtotal}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};
