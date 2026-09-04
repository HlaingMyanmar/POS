import React from 'react';
import { PrintLineItem } from '../types/print.types';

interface InvoiceTableProps {
  items: PrintLineItem[];
  showSerial?: boolean;
  showWarranty?: boolean;
  showLineDiscount?: boolean;
}

function serialCell(item: PrintLineItem): string {
  const sn = (item.serialInfo || '').trim().replace(/^SN\s*:\s*/i, '');
  return sn ? `SN:${sn}` : '—';
}

function warrantyCell(item: PrintLineItem): string {
  const war = (item.warrantyLabel || '').trim().replace(/\s+/g, '');
  return war || '—';
}

function discountCell(item: PrintLineItem): string {
  const disc = (item.discount || '').trim();
  if (item.foc || !disc || disc === '0' || disc === '0.00') return '—';
  return disc;
}

/**
 * Columns: # · Service / Part · Serial · Warranty · Dis · Qty · Unit Price · Amount
 */
export const InvoiceTable: React.FC<InvoiceTableProps> = ({
  items,
  showSerial = true,
  showWarranty = true,
  showLineDiscount = true,
}) => {
  const colSpan =
    1 + 1 + (showSerial ? 1 : 0) + (showWarranty ? 1 : 0) + (showLineDiscount ? 1 : 0) + 1 + 1 + 1;

  return (
    <div className="inv-table-wrap">
      <table className="inv-table">
        <thead>
          <tr>
            <th className="col-center" style={{ width: '4%' }}>#</th>
            <th>Service / Part</th>
            {showSerial && <th style={{ width: '16%' }}>Serial</th>}
            {showWarranty && <th style={{ width: '10%' }}>Warranty</th>}
            {showLineDiscount && <th className="col-num" style={{ width: '10%' }}>Dis</th>}
            <th className="col-center" style={{ width: '7%' }}>Qty</th>
            <th className="col-num" style={{ width: '12%' }}>Unit Price</th>
            <th className="col-num" style={{ width: '12%' }}>Amount</th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr>
              <td colSpan={colSpan} className="col-center" style={{ padding: '16px', color: '#94a3b8' }}>
                No items
              </td>
            </tr>
          ) : (
            items.map((item) => (
              <tr key={item.rowNo}>
                <td className="col-center">{item.rowNo}</td>
                <td>
                  <div>{item.productName}</div>
                  {item.foc && (
                    <div className="inv-item-sub" style={{ color: '#047857', fontWeight: 700 }}>FOC</div>
                  )}
                </td>
                {showSerial && <td className="inv-item-sub">{serialCell(item)}</td>}
                {showWarranty && <td className="inv-item-sub">{warrantyCell(item)}</td>}
                {showLineDiscount && <td className="col-num inv-item-sub">{discountCell(item)}</td>}
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
