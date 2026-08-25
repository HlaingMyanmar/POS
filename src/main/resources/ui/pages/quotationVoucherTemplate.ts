import { QuotationDTO } from '../types';
import { buildCompanyContact, getCachedCompanySettings } from '../utils/companySettings';

const escapeHtml = (value: unknown) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const formatMoney = (value?: number | null) =>
  new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);

const formatDate = (value?: string) => {
  if (!value) return '-';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
};

export const buildQuotationVoucherHtml = (quotation: QuotationDTO) => {
  const settings = getCachedCompanySettings();
  const companyName = settings.companyName || 'Company';
  const subtotal = Number(quotation.totalAmount) || 0;
  const discount = Number(quotation.discountAmount) || 0;
  const netAmount = quotation.netAmount == null
    ? Math.max(0, subtotal - discount)
    : Number(quotation.netAmount) || 0;

  const itemRows = (quotation.details || []).map((item, index) => `
    <tr>
      <td class="cell-center cell-muted">${index + 1}</td>
      <td class="item-cell">${escapeHtml(item.productName || `Product #${item.productId}`)}</td>
      <td class="cell-number">${formatMoney(item.qty)}</td>
      <td class="cell-number">${formatMoney(item.unitPrice)}</td>
      <td class="cell-number cell-discount">${Number(item.discountAmount) > 0 ? `- ${formatMoney(item.discountAmount)}` : '-'}</td>
      <td class="cell-number cell-amount">${formatMoney(item.subtotal)}</td>
    </tr>
  `).join('');

  const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(quotation.quotationCode || 'Quotation')}</title>
  <style>
    @page { size: A4 portrait; margin: 0; }
    * { box-sizing: border-box; }
    html, body { margin: 0; padding: 0; background: #edf1f5; }
    body {
      color: #1e293b;
      font: 12px/1.5 Pyidaungsu, "Noto Sans Myanmar", "Segoe UI", Arial, sans-serif;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .page {
      width: 210mm;
      min-height: 297mm;
      margin: 18px auto;
      padding: 12mm 14mm 10mm;
      background: #fff;
      box-shadow: 0 14px 40px rgba(15, 23, 42, .14);
    }
    .header {
      display: grid;
      grid-template-columns: 1fr 220px;
      align-items: start;
      gap: 24px;
      padding-bottom: 16px;
      border-bottom: 2px solid #1e3a5f;
    }
    .brand { display: flex; align-items: center; gap: 13px; min-width: 0; }
    .logo { width: 62px; height: 62px; padding: 4px; object-fit: contain; border: 1px solid #dbe3ec; border-radius: 8px; }
    .company-name { margin: 0; color: #0f172a; font-size: 20px; font-weight: 800; line-height: 1.25; }
    .company-contact { max-width: 390px; margin-top: 5px; color: #64748b; font-size: 10px; }
    .document { text-align: right; }
    .document-label { color: #1d4ed8; font-size: 9px; font-weight: 800; letter-spacing: 1.4px; text-transform: uppercase; }
    .document-title { margin-top: 2px; color: #0f172a; font-size: 25px; font-weight: 900; line-height: 1.1; }
    .document-title-mm { color: #64748b; font-size: 11px; font-weight: 600; }
    .document-code { margin-top: 8px; color: #0f766e; font-size: 14px; font-weight: 800; }
    .details {
      display: grid;
      grid-template-columns: 1.1fr .9fr;
      gap: 12px;
      margin: 14px 0;
    }
    .detail-card { min-height: 92px; padding: 11px 13px; border: 1px solid #dbe3ec; border-radius: 6px; background: #fbfcfe; }
    .section-label { margin-bottom: 7px; color: #1d4ed8; font-size: 9px; font-weight: 800; letter-spacing: .8px; text-transform: uppercase; }
    .customer-name { color: #0f172a; font-size: 15px; font-weight: 800; }
    .meta-row { display: flex; justify-content: space-between; gap: 12px; padding: 3px 0; }
    .meta-key { color: #64748b; }
    .meta-value { font-weight: 700; text-align: right; }
    .status { display: inline-block; padding: 2px 9px; border-radius: 99px; background: #dbeafe; color: #1d4ed8; font-size: 9px; font-weight: 800; text-transform: uppercase; }
    .items { overflow: hidden; border: 1px solid #cbd5e1; border-radius: 6px; }
    table { width: 100%; border-collapse: collapse; table-layout: fixed; }
    thead { display: table-header-group; }
    th { padding: 8px 7px; background: #1e3a5f; color: #fff; font-size: 9px; font-weight: 800; letter-spacing: .35px; text-align: right; text-transform: uppercase; }
    th.item-heading { text-align: left; }
    td { padding: 8px 7px; border-bottom: 1px solid #e2e8f0; vertical-align: top; }
    tbody tr:nth-child(even) td { background: #f8fafc; }
    tbody tr:last-child td { border-bottom: 0; }
    tr { break-inside: avoid; page-break-inside: avoid; }
    .item-cell { color: #0f172a; font-weight: 700; overflow-wrap: anywhere; }
    .cell-center { text-align: center; }
    .cell-number { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
    .cell-muted { color: #64748b; }
    .cell-discount { color: #b45309; }
    .cell-amount { color: #0f172a; font-weight: 800; }
    .summary-wrap { display: flex; justify-content: flex-end; margin-top: 12px; break-inside: avoid; page-break-inside: avoid; }
    .summary { width: 285px; overflow: hidden; border: 1px solid #cbd5e1; border-radius: 6px; }
    .summary-row { display: flex; justify-content: space-between; gap: 16px; padding: 7px 11px; border-bottom: 1px solid #e2e8f0; }
    .summary-row:last-child { border: 0; }
    .summary-label { color: #64748b; }
    .summary-value { font-weight: 700; font-variant-numeric: tabular-nums; }
    .summary-total { padding: 10px 11px; background: #1d4ed8; color: #fff; font-size: 14px; }
    .summary-total .summary-label { color: #dbeafe; font-weight: 700; }
    .notes { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 14px; break-inside: avoid; page-break-inside: avoid; }
    .note { min-height: 68px; padding: 9px 11px; border: 1px solid #dbe3ec; border-radius: 6px; background: #fbfcfe; }
    .note-text { color: #475569; white-space: pre-wrap; overflow-wrap: anywhere; }
    .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 70px; margin-top: 42px; break-inside: avoid; page-break-inside: avoid; }
    .signature { padding-top: 7px; border-top: 1px solid #94a3b8; color: #64748b; font-size: 10px; text-align: center; }
    .footer { margin-top: 18px; padding-top: 8px; border-top: 1px dashed #cbd5e1; color: #64748b; font-size: 9px; text-align: center; }
    .print-button { position: fixed; top: 18px; right: 18px; padding: 10px 16px; border: 0; border-radius: 7px; background: #1d4ed8; color: #fff; font-weight: 700; cursor: pointer; }
    @media print {
      html, body { background: #fff; }
      .page { width: auto; min-height: 297mm; margin: 0; box-shadow: none; }
      .print-button { display: none; }
      .header, .details, .summary-wrap, .notes, .signatures, .footer { break-inside: avoid; page-break-inside: avoid; }
    }
  </style>
</head>
<body>
  <button class="print-button" onclick="window.print()">Print / Save PDF</button>
  <main class="page">
    <header class="header">
      <div class="brand">
        ${settings.logoBase64 ? `<img class="logo" src="${escapeHtml(settings.logoBase64)}" alt="Company logo" />` : ''}
        <div><h1 class="company-name">${escapeHtml(companyName)}</h1><div class="company-contact">${escapeHtml(buildCompanyContact(settings))}</div></div>
      </div>
      <div class="document">
        <div class="document-label">Official Price Proposal</div>
        <div class="document-title">QUOTATION</div>
        <div class="document-title-mm">ဈေးနှုန်းကမ်းလှမ်းလွှာ</div>
        <div class="document-code">${escapeHtml(quotation.quotationCode || `#${quotation.id || '-'}`)}</div>
      </div>
    </header>
    <section class="details">
      <div class="detail-card"><div class="section-label">Quotation For / သို့</div><div class="customer-name">${escapeHtml(quotation.customerName || 'Walk-in Customer')}</div></div>
      <div class="detail-card">
        <div class="meta-row"><span class="meta-key">Issue Date</span><span class="meta-value">${formatDate(quotation.quotationDate)}</span></div>
        <div class="meta-row"><span class="meta-key">Valid Until</span><span class="meta-value">${formatDate(quotation.validUntil)}</span></div>
        <div class="meta-row"><span class="meta-key">Status</span><span class="meta-value status">${escapeHtml(quotation.status || 'Draft')}</span></div>
      </div>
    </section>
    <section class="items"><table><thead><tr><th style="width:6%">#</th><th class="item-heading" style="width:37%">Description</th><th style="width:10%">Qty</th><th style="width:17%">Unit Price</th><th style="width:13%">Discount</th><th style="width:17%">Amount</th></tr></thead><tbody>${itemRows || '<tr><td colspan="6" class="cell-center cell-muted" style="padding:22px">No quotation items</td></tr>'}</tbody></table></section>
    <section class="summary-wrap"><div class="summary">
      <div class="summary-row"><span class="summary-label">Subtotal</span><span class="summary-value">${formatMoney(subtotal)} MMK</span></div>
      <div class="summary-row"><span class="summary-label">Discount</span><span class="summary-value">${discount > 0 ? `- ${formatMoney(discount)}` : formatMoney(0)} MMK</span></div>
      <div class="summary-row summary-total"><span class="summary-label">Net Amount</span><span class="summary-value">${formatMoney(netAmount)} MMK</span></div>
    </div></section>
    ${quotation.terms || quotation.remark ? `<section class="notes">${quotation.terms ? `<div class="note"><div class="section-label">Terms &amp; Conditions</div><div class="note-text">${escapeHtml(quotation.terms)}</div></div>` : ''}${quotation.remark ? `<div class="note"><div class="section-label">Remark</div><div class="note-text">${escapeHtml(quotation.remark)}</div></div>` : ''}</section>` : ''}
    <section class="signatures"><div class="signature">Prepared By / ပြင်ဆင်သူ</div><div class="signature">Customer Acceptance / ဝယ်ယူသူ အတည်ပြုချက်</div></section>
    <footer class="footer">${escapeHtml(companyName)} &nbsp;•&nbsp; ${escapeHtml(settings.footerNote || 'Thank you for your business')} &nbsp;•&nbsp; Valid until ${formatDate(quotation.validUntil)}</footer>
  </main>
</body></html>`;

  return { html, popupSize: 'width=980,height=900' };
};
