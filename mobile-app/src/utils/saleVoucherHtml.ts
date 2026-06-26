import { LOGO_BASE64 } from '../assets/logoBase64';

export type PrintFormat = 'A4' | 'A5' | 'POS';

export interface VoucherData {
  voucherNo?: string;
  saleDate?: string;
  customerName?: string;
  customerPhone?: string;
  staffName?: string;
  paymentMethodName?: string;
  paymentStatus?: string;
  items: { name: string; qty: number; unitPrice: number; subtotal: number; serialNumbers?: string[]; serialWarranties?: Record<string, string>; warrantyMonths?: number; warrantyExpiryDate?: string }[];
  totalAmount?: number;
  discountAmount?: number;
  netAmount?: number;
  paidAmount?: number;
  dueAmount?: number;
  remark?: string;
}

const esc = (v?: string | number | null) =>
  String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const money = (v?: number | null) =>
  Number(v || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtDate = (v?: string) => {
  if (!v) return new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  const d = new Date(v);
  if (isNaN(d.getTime())) return v;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
};

const HEADER_COLOR = '#1e40af';
const COMPANY_NAME = 'SSPD IT Solution Center';
const COMPANY_CONTACT = 'Phone: 09-252425319';

const fmtWarranty = (item: VoucherData['items'][0]) => {
  const m = Number(item.warrantyMonths) || 0;
  if (m <= 0) return '';
  if (m % 12 === 0) { const y = m / 12; return `${y} နှစ်`; }
  return `${m} လ`;
};

function buildPosHtml(d: VoucherData): string {
  const rows = d.items.map((item, i) => {
    const war = fmtWarranty(item);
    return `
      <tr>
        <td>${i + 1}</td>
        <td>
          <div>${esc(item.name)}</div>
          ${item.serialWarranties
            ? (item.serialNumbers ?? []).map(sn => {
                const w = item.serialWarranties![sn];
                return `<div class="muted">S/N: ${esc(sn)}${w ? ` &#128737; ${esc(w)}` : ''}</div>`;
              }).join('')
            : (item.serialNumbers?.length ? `<div class="muted">S/N: ${esc(item.serialNumbers.join(', '))}</div>` : '')}
          ${!item.serialWarranties && war ? `<div class="muted" style="color:#0891b2;">&#128737; ${esc(war)}</div>` : ''}
        </td>
        <td class="num">${item.qty}</td>
        <td class="num">${money(item.unitPrice)}</td>
        <td class="num">${money(item.subtotal)}</td>
      </tr>
    `;
  }).join('');

  return `<!doctype html><html><head><meta charset="utf-8"/>
  <style>
    @page{size:80mm auto;margin:3mm}
    *{box-sizing:border-box}
    body{margin:0;font-family:'Segoe UI',Arial,sans-serif;width:80mm;color:#111827;font-size:10px;line-height:1.35}
    .wrap{width:100%}
    .center{text-align:center}
    .muted{color:#6b7280;font-size:9px}
    .title{font-size:13px;font-weight:700;margin-bottom:2px}
    .line{border-top:1px dashed #9ca3af;margin:6px 0}
    table{width:100%;border-collapse:collapse}
    th,td{padding:2px 0;vertical-align:top}
    th{text-align:left;border-bottom:1px dashed #9ca3af;font-weight:700}
    .num{text-align:right;white-space:nowrap}
    .summary td{padding:1px 0}
    .footer{margin-top:8px;text-align:center;font-size:9px;color:#4b5563}
  </style></head><body>
  <div class="wrap">
    <div class="center">
      <img src="${LOGO_BASE64}" alt="logo" style="max-height:48px;max-width:100%;margin-bottom:4px;" />
      <div class="title">${esc(COMPANY_NAME)}</div>
      <div>Sales Receipt</div>
      <div class="muted">${esc(COMPANY_CONTACT)}</div>
    </div>
    <div class="line"></div>
    <div>Invoice: <b>${esc(d.voucherNo)}</b></div>
    <div>Date: ${esc(fmtDate(d.saleDate))}</div>
    <div>Customer: ${esc(d.customerName)}</div>
    <div>Staff: ${esc(d.staffName)}</div>
    ${d.paymentMethodName ? `<div>Payment: ${esc(d.paymentMethodName)}</div>` : ''}
    <div class="line"></div>
    <table>
      <thead><tr><th style="width:8%">#</th><th>Item</th><th style="width:10%" class="num">Qty</th><th style="width:18%" class="num">Price</th><th style="width:18%" class="num">Sub</th></tr></thead>
      <tbody>${rows || '<tr><td colspan="5" class="muted">No items</td></tr>'}</tbody>
    </table>
    <div class="line"></div>
    <table class="summary">
      <tr><td>Subtotal</td><td class="num">${money(d.totalAmount)}</td></tr>
      ${(d.discountAmount ?? 0) > 0 ? `<tr><td>Discount</td><td class="num">− ${money(d.discountAmount)}</td></tr>` : ''}
      <tr><td><b>Net Amount</b></td><td class="num"><b>${money(d.netAmount)}</b></td></tr>
      <tr><td>Paid</td><td class="num">${money(d.paidAmount)}</td></tr>
      <tr><td><b>Balance Due</b></td><td class="num"><b>${money(d.dueAmount)}</b></td></tr>
    </table>
    ${d.remark ? `<div class="line"></div><div><b>Remark:</b> ${esc(d.remark)}</div>` : ''}
    <div class="line"></div>
    <div class="footer">Thank you for your purchase!</div>
  </div>
</body></html>`;
}

function buildStandardHtml(d: VoucherData, paper: 'A4' | 'A5'): string {
  const compact = paper === 'A5';
  const rows = d.items.map((item, i) => {
    const war = fmtWarranty(item);
    return `
      <tr>
        <td class="center">${i + 1}</td>
        <td>
          <div>${esc(item.name)}</div>
          ${item.serialWarranties
            ? (item.serialNumbers ?? []).map(sn => {
                const w = item.serialWarranties![sn];
                return `<div class="item-sn">S/N: ${esc(sn)}${w ? ` &nbsp;&#128737; ${esc(w)}` : ''}</div>`;
              }).join('')
            : (item.serialNumbers?.length ? `<div class="item-sn">S/N: ${esc(item.serialNumbers.join(', '))}</div>` : '')}
          ${!item.serialWarranties && war ? `<div class="item-sn" style="color:#0891b2;">&#128737; ${esc(war)}</div>` : ''}
        </td>
        <td class="num">${item.qty}</td>
        <td class="num">${money(item.unitPrice)}</td>
        <td class="num">${money(item.subtotal)}</td>
      </tr>
    `;
  }).join('');

  const badgeClass = d.paymentStatus === 'Partial' || d.paymentStatus === 'PARTIAL' ? ' partial'
    : d.paymentStatus === 'Pending' || d.paymentStatus === 'PENDING' ? ' pending' : '';

  return `<!doctype html><html><head><meta charset="utf-8"/>
  <style>
    @page{size:${paper} portrait;margin:${compact ? '8mm' : '10mm'}}
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:'Segoe UI',Arial,sans-serif;color:#1e293b;font-size:${compact ? '11px' : '12px'};line-height:1.5;background:#fff}
    .header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;padding:${compact ? '14px 16px 12px' : '18px 20px 14px'};background:${HEADER_COLOR};border-radius:10px 10px 0 0}
    .logo-brand{display:flex;align-items:center;gap:10px}
    .brand-name{font-size:${compact ? '18px' : '22px'};font-weight:800;color:#fff}
    .brand-sub{margin-top:3px;font-size:10px;color:#94a3b8;max-width:300px;line-height:1.4}
    .inv-box{text-align:right;flex-shrink:0}
    .inv-label{font-size:9px;text-transform:uppercase;letter-spacing:1px;color:#64748b}
    .inv-code{font-size:${compact ? '15px' : '18px'};font-weight:800;color:#fff;margin-top:2px}
    .inv-date{font-size:10px;color:#94a3b8;margin-top:2px}
    .body-wrap{border:1px solid #e2e8f0;border-top:none;border-radius:0 0 10px 10px;padding:${compact ? '12px 16px' : '14px 20px'}}
    .blocks{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:14px;padding-bottom:14px;border-bottom:1px dashed #e2e8f0}
    .block{padding:${compact ? '8px 10px' : '10px 12px'};background:#f8fafc;border-radius:7px;border:1px solid #e2e8f0}
    .block-title{font-size:9px;text-transform:uppercase;letter-spacing:.8px;color:#64748b;margin-bottom:6px;padding-bottom:4px;border-bottom:1px solid #e2e8f0}
    .block-row{display:flex;justify-content:space-between;gap:8px;margin-top:4px}
    .bl{color:#64748b;font-size:10px}
    .bv{font-weight:600;font-size:11px;text-align:right}
    .badge{display:inline-block;padding:1px 7px;border-radius:20px;font-size:10px;font-weight:600;background:#dcfce7;color:#166534}
    .badge.partial{background:#fef9c3;color:#854d0e}
    .badge.pending{background:#f1f5f9;color:#475569}
    .section-lbl{font-size:9.5px;text-transform:uppercase;letter-spacing:.7px;color:#64748b;font-weight:700;margin-bottom:6px;margin-top:14px}
    .table-wrap{border:1px solid #e2e8f0;border-radius:7px;overflow:hidden;margin-bottom:12px}
    table{width:100%;border-collapse:collapse}
    th{background:#f1f5f9;color:#475569;font-size:9.5px;text-transform:uppercase;letter-spacing:.6px;padding:${compact ? '5px 7px' : '6px 8px'};border-bottom:1px solid #e2e8f0;font-weight:700}
    td{padding:${compact ? '5px 7px' : '6px 8px'};border-bottom:1px solid #f1f5f9;vertical-align:top}
    tr:last-child td{border-bottom:none}
    tr:nth-child(even) td{background:#fafbfd}
    .num{text-align:right;white-space:nowrap}
    .center{text-align:center}
    .item-sn{font-size:9.5px;color:#64748b;margin-top:1px}
    .bottom-area{display:flex;justify-content:flex-end;align-items:flex-start;gap:16px}
    .summary-box{width:${compact ? '210px' : '250px'};border:1px solid #e2e8f0;border-radius:7px;overflow:hidden}
    .s-row{display:flex;justify-content:space-between;align-items:center;padding:${compact ? '5px 10px' : '6px 12px'};border-bottom:1px solid #f1f5f9;font-size:12px}
    .s-row:last-child{border-bottom:none}
    .s-row.hl{background:${HEADER_COLOR};color:#fff;font-weight:700}
    .s-row.sub-hl{background:#f1f5f9;font-weight:600}
    .s-val{font-weight:600}
    .footer-bar{margin-top:12px;padding-top:8px;border-top:1px dashed #e2e8f0;text-align:center;font-size:9.5px;color:#94a3b8}
    .signatures{margin-top:24px;display:grid;grid-template-columns:1fr 1fr;gap:24px}
    .sign{padding-top:30px;border-top:1px solid #cbd5e1;text-align:center;font-size:10px;color:#64748b}
  </style></head><body>
  <div class="header">
    <div class="logo-brand">
      <img src="${LOGO_BASE64}" alt="logo" style="height:${compact ? '40px' : '48px'};object-fit:contain;background:#fff;border-radius:6px;padding:3px;" />
      <div>
        <div class="brand-name">${esc(COMPANY_NAME)}</div>
        <div class="brand-sub">${esc(COMPANY_CONTACT)}</div>
      </div>
    </div>
    <div class="inv-box">
      <div class="inv-label">Sales Invoice</div>
      <div class="inv-code">${esc(d.voucherNo)}</div>
      <div class="inv-date">${esc(fmtDate(d.saleDate))}</div>
    </div>
  </div>
  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">Customer</div>
        <div class="block-row"><span class="bl">Name</span><span class="bv">${esc(d.customerName)}</span></div>
        ${d.customerPhone ? `<div class="block-row"><span class="bl">Phone</span><span class="bv">${esc(d.customerPhone)}</span></div>` : ''}
      </div>
      <div class="block">
        <div class="block-title">Sale Info</div>
        <div class="block-row"><span class="bl">Staff</span><span class="bv">${esc(d.staffName)}</span></div>
        ${d.paymentMethodName ? `<div class="block-row"><span class="bl">Payment</span><span class="bv">${esc(d.paymentMethodName)}</span></div>` : ''}
        <div class="block-row"><span class="bl">Status</span><span class="bv"><span class="badge${badgeClass}">${esc(d.paymentStatus ?? 'PAID')}</span></span></div>
      </div>
    </div>
    <div class="section-lbl">Items</div>
    <div class="table-wrap"><table>
      <thead><tr>
        <th class="center" style="width:5%">#</th>
        <th style="width:45%">Item</th>
        <th class="num" style="width:10%">Qty</th>
        <th class="num" style="width:20%">Unit Price</th>
        <th class="num" style="width:20%">Total</th>
      </tr></thead>
      <tbody>${rows || '<tr><td colspan="5" style="text-align:center;padding:12px;color:#94a3b8;">No items</td></tr>'}</tbody>
    </table></div>
    <div class="bottom-area">
      <div class="summary-box">
        <div class="s-row"><span>Subtotal</span><span class="s-val">${money(d.totalAmount)} Ks</span></div>
        ${(d.discountAmount ?? 0) > 0 ? `<div class="s-row"><span>Discount</span><span class="s-val">− ${money(d.discountAmount)} Ks</span></div>` : ''}
        <div class="s-row sub-hl"><span>Net Amount</span><span class="s-val">${money(d.netAmount)} Ks</span></div>
        <div class="s-row"><span>Paid</span><span class="s-val">${money(d.paidAmount)} Ks</span></div>
        <div class="s-row hl"><span>Balance Due</span><span class="s-val">${money(d.dueAmount)} Ks</span></div>
      </div>
    </div>
    ${d.remark ? `<div class="section-lbl">Remark</div><div style="border:1px solid #e2e8f0;border-radius:7px;padding:8px 10px;font-size:11px;color:#475569;margin-top:4px">${esc(d.remark)}</div>` : ''}
    <div class="signatures">
      <div class="sign">Staff Signature</div>
      <div class="sign">Customer Signature</div>
    </div>
    <div class="footer-bar">Thank you for your purchase! · ${esc(COMPANY_NAME)}</div>
  </div>
</body></html>`;
}

export function buildSaleVoucherHtml(data: VoucherData, format: PrintFormat): string {
  if (format === 'POS') return buildPosHtml(data);
  return buildStandardHtml(data, format);
}
