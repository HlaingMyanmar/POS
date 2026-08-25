import { PurchaseReturnDTO } from '../types';
import { buildCompanyContact, CompanySettings, getCachedCompanySettings } from '../utils/companySettings';

const escapeHtml = (v?: string | number | null) =>
  String(v ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const money = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const fmtDate = (v?: string) => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('en-GB', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

export const buildPurchaseReturnVoucherHtml = ({
  row,
  settings,
  preview = false,
}: {
  row: PurchaseReturnDTO;
  settings?: CompanySettings;
  preview?: boolean;
}): { html: string; popupSize: string } => {
  const cs = settings ?? getCachedCompanySettings();
  const companyName = cs.companyName || 'Company';
  const companyContact = buildCompanyContact(cs);
  const footerNote = cs.footerNote || 'Thank you';
  const logoSrc = cs.logoBase64 || '/img/logo.png';
  const total = Number(row.totalReturnAmount) || 0;
  const refund = Number(row.refundAmount ?? row.totalReturnAmount) || 0;

  const itemRows = (row.details || []).map((d, idx) => `
    <tr>
      <td class="center">${idx + 1}</td>
      <td>
        <div>${escapeHtml(d.productName || `Product #${d.productId}`)}</div>
        ${d.serialNumbers?.length ? `<div class="item-sn">SN: ${escapeHtml(d.serialNumbers.join(', '))}</div>` : ''}
      </td>
      <td class="num">${Number(d.qty) || 0}</td>
      <td class="num">${money(Number(d.unitPrice) || 0)}</td>
      <td class="num">${money(Number(d.subtotal) || 0)}</td>
    </tr>
  `).join('');

  const html = `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <style>
    @page { size: A4 portrait; margin: 0; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: Pyidaungsu, 'Segoe UI', Arial, sans-serif; color: #111827; font-size: 12px; line-height: 1.42; background: #fff; padding: 10mm; }
    .header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding: 12px 16px 10px; border: 1px solid #d1d5db; border-bottom: 3px solid #e11d48; }
    .brand-name { font-size: 19px; font-weight: 800; }
    .brand-sub { margin-top: 4px; font-size: 10px; color: #6b7280; max-width: 360px; }
    .inv-box { text-align: right; min-width: 170px; border-left: 1px solid #d1d5db; padding-left: 14px; }
    .inv-label { font-size: 9px; text-transform: uppercase; letter-spacing: 1px; color: #6b7280; }
    .inv-code { font-size: 18px; font-weight: 800; margin-top: 3px; }
    .body-wrap { border: 1px solid #d1d5db; border-top: none; padding: 14px 16px 12px; }
    .blocks { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; }
    .block { padding: 8px 10px; border: 1px solid #d1d5db; border-left: 3px solid #e11d48; }
    .block-title { font-size: 9px; text-transform: uppercase; font-weight: 700; margin-bottom: 6px; border-bottom: 1px solid #e5e7eb; padding-bottom: 4px; }
    .block-row { display: flex; justify-content: space-between; gap: 8px; margin-top: 4px; }
    .bl { color: #6b7280; font-size: 10px; }
    .bv { font-weight: 700; font-size: 11px; text-align: right; }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f3f4f6; font-size: 9px; text-transform: uppercase; padding: 6px 7px; border-bottom: 1px solid #d1d5db; }
    td { padding: 5px 7px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
    .num { text-align: right; white-space: nowrap; }
    .center { text-align: center; }
    .item-sn { font-size: 9.5px; color: #64748b; margin-top: 1px; }
    .summary-box { width: 245px; margin-left: auto; border: 1px solid #d1d5db; }
    .s-row { display: flex; justify-content: space-between; padding: 5px 10px; border-bottom: 1px solid #e5e7eb; font-size: 11px; }
    .s-row:last-child { border-bottom: none; background: #e11d48; color: #fff; font-weight: 700; }
    .remark-box { margin-top: 12px; border: 1px solid #d1d5db; padding: 7px 9px; font-size: 11px; color: #4b5563; }
    .footer-bar { margin-top: 10px; padding-top: 6px; border-top: 1px dashed #d1d5db; text-align: center; font-size: 9px; color: #6b7280; }
  </style>
</head>
<body>
  <div class="header">
    <div style="display:flex;align-items:center;gap:12px;">
      ${logoSrc ? `<img src="${logoSrc}" alt="logo" style="max-height:50px;max-width:80px;border-radius:6px;padding:4px;" />` : ''}
      <div>
        <div class="brand-name">${escapeHtml(companyName)}</div>
        <div class="brand-sub">${escapeHtml(companyContact)}</div>
      </div>
    </div>
    <div class="inv-box">
      <div class="inv-label">Purchase Return</div>
      <div class="inv-code">${escapeHtml(row.returnNo || `#${row.id}`)}</div>
      <div class="inv-date" style="font-size:10px;color:#6b7280;margin-top:2px">${escapeHtml(fmtDate(row.returnDate))}</div>
    </div>
  </div>
  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">Supplier</div>
        <div class="block-row"><span class="bl">Name</span><span class="bv">${escapeHtml(row.supplierName || '-')}</span></div>
        <div class="block-row"><span class="bl">Purchase</span><span class="bv">${escapeHtml(row.purchaseCode || (row.purchaseId ? `#${row.purchaseId}` : '-'))}</span></div>
      </div>
      <div class="block">
        <div class="block-title">Return</div>
        <div class="block-row"><span class="bl">Status</span><span class="bv">${escapeHtml(row.status || 'CONFIRMED')}</span></div>
        <div class="block-row"><span class="bl">Date</span><span class="bv">${escapeHtml(fmtDate(row.returnDate))}</span></div>
      </div>
    </div>
    <table>
      <thead>
        <tr>
          <th style="width:5%" class="center">#</th>
          <th style="width:45%">Item</th>
          <th style="width:10%" class="num">Qty</th>
          <th style="width:20%" class="num">Unit</th>
          <th style="width:20%" class="num">Amount</th>
        </tr>
      </thead>
      <tbody>${itemRows || '<tr><td colspan="5" class="center" style="padding:16px;color:#94a3b8;">No items</td></tr>'}</tbody>
    </table>
    <div class="summary-box" style="margin-top:12px">
      <div class="s-row"><span>Return Total</span><span>${money(total)}</span></div>
      <div class="s-row"><span>Refund</span><span>${money(refund)}</span></div>
    </div>
    ${row.reason ? `<div class="remark-box"><b>Reason:</b> ${escapeHtml(row.reason)}</div>` : ''}
    <div class="footer-bar">${escapeHtml(companyName)} | ${escapeHtml(footerNote)}</div>
  </div>
  ${preview ? '' : `<script>window.onload=function(){setTimeout(function(){window.print();window.close();},120);};</script>`}
</body>
</html>`;

  return { html, popupSize: 'width=980,height=860' };
};
