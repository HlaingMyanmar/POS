import { CustomerPaymentDTO, SaleDTO } from '../types';
import { buildCompanyContact, CompanySettings, getCachedCompanySettings } from '../utils/companySettings';
import { DEFAULT_SALE_CONFIG, parseVoucherConfig, VoucherSaleConfig } from '../utils/voucherTemplateConfig';

export type VoucherDesign = 'POS' | 'STANDARD';
export type VoucherPaper = '58mm' | '80mm' | 'A5' | 'A4';

export const COMPANY_NAME = 'SSPD IT Solution Center';
export const INVOICE_TITLE = 'Sales Invoice';
export const COMPANY_CONTACT = 'Phone: 09-252425319 | Address: No. ၃၈/ခ ၊ ၅၆ ရပ်ကွက် ၊ လှော်ကားလမ်းမပေါ် ၊ တောင်ဒဂုံမြို့နယ် ၊ ရန်ကုန်မြို့။';

const escapeHtml = (v?: string | number | null) => String(v ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

const money = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const fmtDate = (v?: string) => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('en-GB', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

type BuildSaleVoucherHtmlInput = {
  sale: SaleDTO;
  payments: CustomerPaymentDTO[];
  design: VoucherDesign;
  paper: VoucherPaper;
  isSerialProduct: (productId: number) => boolean;
  settings?: CompanySettings;
  cfg?: Partial<VoucherSaleConfig>;
  preview?: boolean;
};

const fmtWarrantyDuration = (months: number): string => {
  if (months <= 0) return '';
  if (months % 12 === 0) { const y = months / 12; return `${y} Year${y > 1 ? 's' : ''}`; }
  return `${months} Month${months > 1 ? 's' : ''}`;
};

const formatWarrantyPreview = (detail: any) => {
  const warrantyTerms = String(detail?.warrantyTerms || '').trim();
  const warrantyMonths = Number(detail?.warrantyMonths) || 0;
  const warrantyExpiry = detail?.warrantyExpiryDate;
  const parts: string[] = [];

  if (warrantyTerms) parts.push(warrantyTerms);
  else { const d = fmtWarrantyDuration(warrantyMonths); if (d) parts.push(d); }

  if (warrantyExpiry) parts.push(`Exp: ${fmtDate(warrantyExpiry)}`);
  return parts.join(' · ');
};

export const buildSaleVoucherHtml = ({
  sale,
  payments,
  design,
  paper,
  isSerialProduct,
  settings,
  cfg: cfgOverride,
  preview = false
}: BuildSaleVoucherHtmlInput) => {
  const isPosDesign = design === 'POS';
  const resolvedPaper = isPosDesign
    ? (paper === '58mm' ? '58mm' : '80mm')
    : (paper === 'A5' ? 'A5' : 'A4');

  const cs = settings ?? getCachedCompanySettings();
  const baseCfg = parseVoucherConfig(cs.voucherConfigJson).sale;
  const cfg: VoucherSaleConfig = { ...baseCfg, ...cfgOverride };

  const companyName = cs.companyName || COMPANY_NAME;
  const invoiceTitle = cfg.title || cs.invoiceTitle || INVOICE_TITLE;
  const companyContact = buildCompanyContact(cs) || COMPANY_CONTACT;
  const footerNote = cs.footerNote || 'Thank you';
  const logoSrc = cs.logoBase64 || '/img/logo.png';
  const headerColor = cfg.headerColor || DEFAULT_SALE_CONFIG.headerColor;

  const raw = sale as any;

  // Try every known field name variant, then fall back to calculating from details
  const detailsSum = (sale.details || []).reduce((s, d) => s + (Number(d.subtotal) || Number(d.qty) * Number(d.unitPrice) || 0), 0);
  const hasCustomVoucherPrice = (sale.details || []).some((d) => d.customVoucherPrice != null && Number(d.customVoucherPrice) > 0);
  const voucherDetailsSum = (sale.details || []).reduce((s, d) => {
    const customPrice = Number(d.customVoucherPrice);
    return s + (customPrice > 0 ? (Number(d.qty) || 0) * customPrice : (Number(d.subtotal) || (Number(d.qty) || 0) * (Number(d.unitPrice) || 0)));
  }, 0);
  const lineDiscountTotal = (sale.details || []).reduce((s, d) => s + (Number(d.discountAmount) || 0), 0);

  const total    = Number(raw.totalAmount    ?? raw.total        ?? raw.grandTotal    ?? raw.saleTotal    ?? raw.totalPrice)    || detailsSum;
  const discount = Number(raw.discountAmount ?? raw.discount     ?? raw.totalDiscount ?? raw.discountTotal ?? raw.discAmt)       || 0;
  const net      = Number(raw.netAmount      ?? raw.net          ?? raw.netTotal      ?? raw.subTotal      ?? raw.netSale       ?? raw.finalAmount ?? raw.payable) || (total - discount) || 0;
  const paid     = Number(raw.paidAmount     ?? raw.paid         ?? raw.totalPaid     ?? raw.amountPaid    ?? raw.paidTotal)     || 0;
  const due      = Number(raw.dueAmount      ?? raw.due          ?? raw.balance       ?? raw.dueBalance    ?? raw.remaining      ?? raw.outstanding ?? raw.balanceDue) || (net - paid) || 0;
  // These values are presentation-only. Actual payment and revenue stay on the sale's net amount.
  const voucherTotal = hasCustomVoucherPrice ? voucherDetailsSum : total;
  const voucherDiscount = hasCustomVoucherPrice ? discount + lineDiscountTotal : discount;
  const voucherNet = hasCustomVoucherPrice ? Math.max(0, voucherDetailsSum - voucherDiscount) : net;
  const voucherPaid = hasCustomVoucherPrice ? voucherNet : paid;
  const voucherDue = hasCustomVoucherPrice ? 0 : due;

  const itemRows = (sale.details || []).map((d, idx) => {
    const name = escapeHtml(d.productName || `Product #${d.productId}`);
    const productCode = escapeHtml((d as any).productCode || '');
    const qty = Number(d.qty) || 0;
    const customPrice = Number(d.customVoucherPrice);
    const useCustomVoucherPrice = customPrice > 0;
    const unit = money(useCustomVoucherPrice ? customPrice : Number(d.unitPrice) || 0);
    const sub = money(useCustomVoucherPrice ? qty * customPrice : Number(d.subtotal) || 0);
    const serialText = isSerialProduct(d.productId)
      ? (d.serialNumbers?.length ? d.serialNumbers.join(', ') : '-')
      : 'Qty Only';
    const warrantyLabel = formatWarrantyPreview(d as any);
    const hasWarranty = Boolean(warrantyLabel);
    if (isPosDesign) {
      return `
        <tr>
          <td>${idx + 1}</td>
          <td>
            <div>${name}</div>
            <div class="muted">SN: ${escapeHtml(serialText)}</div>
            ${hasWarranty ? `<div class="muted">WAR: ${escapeHtml(warrantyLabel)}</div>` : ''}
          </td>
          <td class="num">${qty}</td>
          <td class="num">${unit}</td>
          <td class="num">${sub}</td>
        </tr>
      `;
    }
    return `
      <tr>
        <td class="center">${idx + 1}</td>
        <td>
          <div>${name}</div>
          ${hasWarranty ? `<div class="item-sn" style="color:#0891b2;font-size:10px;margin-top:2px;">🛡 ${escapeHtml(warrantyLabel)}</div>` : ''}
        </td>
        ${cfg.showSerial ? `<td>${escapeHtml(serialText)}</td>` : ''}
        <td class="num">${qty}</td>
        <td class="num">${unit}</td>
        <td class="num">${sub}</td>
      </tr>
    `;
  }).join('');

  const paymentRows = payments.map((p) => `
    <tr>
      <td>${escapeHtml(fmtDate(p.paymentDate))}</td>
      <td>${escapeHtml(p.paymentMethodName || '-')}</td>
      <td class="num">${money(Number(p.amount) || 0)}</td>
    </tr>
  `).join('');

  const posHtml = `
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title></title>
  <style>
    @page { size: ${resolvedPaper} auto; margin: 0; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 3mm;
      font-family: 'Segoe UI', Arial, sans-serif;
      width: ${resolvedPaper};
      color: #111827;
      font-size: 10px;
      line-height: 1.35;
    }
    .wrap { width: 100%; }
    .center { text-align: center; }
    .muted { color: #6b7280; font-size: 9px; }
    .title { font-size: 13px; font-weight: 700; margin-bottom: 2px; }
    .section { margin-top: 8px; }
    .line { border-top: 1px dashed #9ca3af; margin: 6px 0; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 2px 0; vertical-align: top; }
    th { text-align: left; border-bottom: 1px dashed #9ca3af; font-weight: 700; }
    .num { text-align: right; white-space: nowrap; }
    .summary td { padding: 1px 0; }
    .footer { margin-top: 8px; text-align: center; font-size: 9px; color: #4b5563; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="center">
      ${logoSrc ? `<img src="${logoSrc}" alt="logo" style="max-height:48px;max-width:100%;margin-bottom:4px;" />` : ''}
      <div class="title">${escapeHtml(companyName)}</div>
      <div>${escapeHtml(invoiceTitle)}</div>
      <div class="muted">${escapeHtml(companyContact)}</div>
      <div class="muted">${escapeHtml(resolvedPaper)} POS Receipt</div>
    </div>
    <div class="line"></div>

    <div>Invoice: <b>${escapeHtml(sale.saleCode || `#${sale.id}`)}</b></div>
    <div>Date: ${escapeHtml(fmtDate(sale.saleDate))}</div>
    <div>Customer: ${escapeHtml(sale.customerName || '-')}</div>
    <div>Staff: ${escapeHtml(sale.staffName || '-')}</div>
    ${sale.dueDate ? `<div>Due Date: ${escapeHtml(sale.dueDate)}</div>` : ''}

    <div class="section">
      <table>
        <thead>
          <tr>
            <th style="width:8%">#</th>
            <th style="width:48%">Item</th>
            <th style="width:10%" class="num">Qty</th>
            <th style="width:17%" class="num">Price</th>
            <th style="width:17%" class="num">Sub</th>
          </tr>
        </thead>
        <tbody>
          ${itemRows || '<tr><td colspan="5" class="muted">No items</td></tr>'}
        </tbody>
      </table>
    </div>

    <div class="line"></div>
    <table class="summary">
      <tr><td>${hasCustomVoucherPrice ? 'Voucher Total' : 'Total'}</td><td class="num">${money(voucherTotal)}</td></tr>
      <tr><td>Discount</td><td class="num">${money(voucherDiscount)}</td></tr>
      <tr><td><b>${hasCustomVoucherPrice ? 'Actual Net' : 'Net'}</b></td><td class="num"><b>${money(voucherNet)}</b></td></tr>
      <tr><td>Paid</td><td class="num">${money(voucherPaid)}</td></tr>
      <tr><td><b>Due</b></td><td class="num"><b>${money(voucherDue)}</b></td></tr>
    </table>

    ${paymentRows ? `
    <div class="section">
      <div><b>Payment History</b></div>
      <table>
        <thead>
          <tr><th>Date</th><th>Method</th><th class="num">Amount</th></tr>
        </thead>
        <tbody>${paymentRows}</tbody>
      </table>
    </div>
    ` : ''}

    ${sale.remark ? `<div class="section"><b>Remark:</b> ${escapeHtml(sale.remark)}</div>` : ''}
    ${cfg.customerNotice ? `
    <div class="line"></div>
    <div class="section" style="font-size:9px;color:#374151;white-space:pre-wrap;line-height:1.5;">${escapeHtml(cfg.customerNotice)}</div>
    ` : ''}
    <div class="line"></div>
    <div class="footer">${escapeHtml(footerNote)}</div>
  </div>
  ${preview ? '' : `
  <script>
    window.onload = function() {
      setTimeout(function() {
        window.print();
        window.close();
      }, 100);
    };
  </script>
  `}
</body>
</html>`;

  const standardCompact = resolvedPaper === 'A5';
  const standardHtml = `
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title></title>
  <style>
    @page { size: ${resolvedPaper} portrait; margin: 0; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Segoe UI', 'Arial', sans-serif;
      color: #1e293b;
      font-size: ${standardCompact ? '11px' : '12px'};
      line-height: 1.5;
      background: #fff;
      padding: ${standardCompact ? '8mm' : '10mm'};
    }

    /* ── Header ── */
    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      padding: ${standardCompact ? '14px 16px 12px' : '18px 20px 14px'};
      background: ${headerColor};
      border-radius: 10px 10px 0 0;
    }
    .brand-name {
      font-size: ${standardCompact ? '18px' : '22px'};
      font-weight: 800;
      color: #fff;
      letter-spacing: 0.2px;
    }
    .brand-sub {
      margin-top: 3px;
      font-size: 10px;
      color: #94a3b8;
      max-width: 340px;
      line-height: 1.4;
    }
    .inv-box {
      text-align: right;
      flex-shrink: 0;
    }
    .inv-label {
      font-size: 9px;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: #64748b;
    }
    .inv-code {
      font-size: ${standardCompact ? '15px' : '18px'};
      font-weight: 800;
      color: #fff;
      margin-top: 2px;
    }
    .inv-date {
      font-size: 10px;
      color: #94a3b8;
      margin-top: 2px;
    }

    /* ── Body ── */
    .body-wrap {
      border: 1px solid #e2e8f0;
      border-top: none;
      border-radius: 0 0 10px 10px;
      padding: ${standardCompact ? '12px 16px' : '14px 20px'};
    }
    /* On page 2+, body-wrap top border may look orphaned; reset header radius too */
    @media print {
      .header { border-radius: 10px; }
      .body-wrap { border-top: 1px solid #e2e8f0; border-radius: 10px; margin-top: 6px; }
    }

    /* ── Info blocks — single box, left / right ── */
    .blocks {
      display: grid;
      grid-template-columns: 1fr 1fr;
      border: 1px solid #e2e8f0;
      border-top: 3px solid ${headerColor};
      border-radius: 7px;
      overflow: hidden;
      margin-bottom: 14px;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .block {
      padding: ${standardCompact ? '9px 11px' : '11px 13px'};
      background: #fff;
    }
    .block:first-child { border-right: 1px solid #e2e8f0; }
    .block-title {
      font-size: 9px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: ${headerColor};
      margin-bottom: 7px;
      padding-bottom: 5px;
      border-bottom: 1px solid #e2e8f0;
    }
    .block-row { display: flex; justify-content: space-between; gap: 8px; margin-top: 4px; }
    .block-row:first-of-type { margin-top: 0; }
    .bl { color: #64748b; font-size: 10px; }
    .bv { font-weight: 600; font-size: 11px; text-align: right; }

    /* ── Status badge ── */
    .badge {
      display: inline-block;
      padding: 1px 7px;
      border-radius: 20px;
      font-size: 10px;
      font-weight: 600;
      background: #dcfce7;
      color: #166534;
    }
    .badge.partial { background: #fef9c3; color: #854d0e; }
    .badge.pending { background: #f1f5f9; color: #475569; }

    /* ── Items table ── */
    .table-wrap {
      border: 1px solid #e2e8f0;
      border-radius: 7px;
      overflow: visible;
      margin-bottom: 12px;
    }
    table { width: 100%; border-collapse: collapse; }
    thead { display: table-header-group; }
    tbody { display: table-row-group; }
    th {
      background: #f1f5f9;
      color: #475569;
      font-size: 9.5px;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      padding: ${standardCompact ? '5px 7px' : '6px 8px'};
      border-bottom: 1px solid #e2e8f0;
      font-weight: 700;
    }
    td {
      padding: ${standardCompact ? '5px 7px' : '6px 8px'};
      border-bottom: 1px solid #f1f5f9;
      vertical-align: top;
    }
    tbody tr { break-inside: avoid; page-break-inside: avoid; }
    tr:last-child td { border-bottom: none; }
    tr:nth-child(even) td { background: #fafbfd; }
    .num { text-align: right; white-space: nowrap; }
    .center { text-align: center; }
    .item-sn { font-size: 9.5px; color: #64748b; margin-top: 1px; }

    /* ── Summary ── */
    .bottom-area {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .payment-section { flex: 1; }
    .section-label {
      font-size: 9.5px;
      text-transform: uppercase;
      letter-spacing: 0.7px;
      color: #64748b;
      font-weight: 700;
      margin-bottom: 6px;
    }
    .summary-box {
      width: ${standardCompact ? '210px' : '250px'};
      flex-shrink: 0;
      border: 1px solid #e2e8f0;
      border-radius: 7px;
      overflow: hidden;
    }
    .s-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: ${standardCompact ? '5px 10px' : '6px 12px'};
      border-bottom: 1px solid #f1f5f9;
      font-size: 12px;
    }
    .s-row:last-child { border-bottom: none; }
    .s-row.highlight {
      background: ${headerColor};
      color: #fff;
      font-weight: 700;
    }
    .s-row.sub-highlight {
      background: #f1f5f9;
      font-weight: 600;
    }
    .s-label { color: inherit; }
    .s-val { font-weight: 600; }
    .s-row.highlight .s-val { color: #f0fdf4; }

    /* ── Payment history ── */
    .pay-table { width: 100%; border-collapse: collapse; font-size: 11px; }
    .pay-table th { background: #f1f5f9; color: #475569; font-size: 9.5px; text-transform: uppercase; letter-spacing: 0.5px; padding: 4px 7px; border-bottom: 1px solid #e2e8f0; font-weight: 700; }
    .pay-table td { padding: 4px 7px; border-bottom: 1px solid #f1f5f9; }
    .pay-table tr:last-child td { border-bottom: none; }

    /* ── Remark + Signatures ── */
    .remark-box {
      margin-top: 12px;
      border: 1px solid #e2e8f0;
      border-radius: 7px;
      padding: 8px 10px;
      min-height: 34px;
      font-size: 11px;
      color: #475569;
      white-space: pre-wrap;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .notice-box {
      margin-top: 10px;
      border: 1px dashed #cbd5e1;
      border-radius: 7px;
      padding: 8px 12px;
      font-size: 10.5px;
      color: #475569;
      white-space: pre-wrap;
      line-height: 1.6;
      background: #f8fafc;
    }
    .signatures {
      margin-top: ${standardCompact ? '18px' : '24px'};
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
      break-inside: avoid;
      page-break-inside: avoid;
    }
    .sign {
      padding-top: 30px;
      border-top: 1px solid #cbd5e1;
      text-align: center;
      font-size: 10px;
      color: #64748b;
      letter-spacing: 0.3px;
    }
    .footer-bar {
      margin-top: 12px;
      padding-top: 8px;
      border-top: 1px dashed #e2e8f0;
      text-align: center;
      font-size: 9.5px;
      color: #94a3b8;
      letter-spacing: 0.4px;
    }
  </style>
</head>
<body>
  <div class="header">
    <div style="display:flex;align-items:center;gap:12px;">
      ${logoSrc ? `<img src="${logoSrc}" alt="logo" style="max-height:50px;max-width:80px;background:#fff;border-radius:6px;padding:4px;" />` : ''}
      <div>
        <div class="brand-name">${escapeHtml(companyName)}</div>
        <div class="brand-sub">${escapeHtml(companyContact)}</div>
      </div>
    </div>
    <div class="inv-box">
      <div class="inv-label">${escapeHtml(invoiceTitle)}</div>
      <div class="inv-code">${escapeHtml(sale.saleCode || `#${sale.id}`)}</div>
      <div class="inv-date">${escapeHtml(fmtDate(sale.saleDate))}</div>
    </div>
  </div>

  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">${escapeHtml(cfg.secBillTo)}</div>
        <div class="block-row">
          <span class="bl">Customer</span>
          <span class="bv">${escapeHtml(sale.customerName || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Payment</span>
          <span class="bv">
            <span class="badge${sale.paymentStatus === 'Partial' ? ' partial' : sale.paymentStatus === 'Pending' ? ' pending' : ''}">${escapeHtml(sale.paymentStatus || '-')}</span>
          </span>
        </div>
        <div class="block-row">
          <span class="bl">Credit</span>
          <span class="bv">${escapeHtml(sale.creditStatus || '-')}</span>
        </div>
      </div>
      <div class="block">
        <div class="block-title">${escapeHtml(cfg.secPreparedBy)}</div>
        <div class="block-row">
          <span class="bl">Staff</span>
          <span class="bv">${escapeHtml(sale.staffName || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Sale Date</span>
          <span class="bv">${escapeHtml(fmtDate(sale.saleDate))}</span>
        </div>
        <div class="block-row">
          <span class="bl">Due Date</span>
          <span class="bv">${escapeHtml(sale.dueDate || '-')}</span>
        </div>
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th style="width:5%" class="center">#</th>
            <th style="${cfg.showSerial ? 'width:36%' : 'width:60%'}">${escapeHtml(cfg.colItem)}</th>
            ${cfg.showSerial ? `<th style="width:24%">${escapeHtml(cfg.colSerial)}</th>` : ''}
            <th style="width:7%" class="center">${escapeHtml(cfg.colQty)}</th>
            <th style="width:14%" class="num">${escapeHtml(cfg.colUnitPrice)}</th>
            <th style="width:14%" class="num">${escapeHtml(cfg.colAmount)}</th>
          </tr>
        </thead>
        <tbody>
          ${itemRows || '<tr><td colspan="6" class="center" style="padding:16px;color:#94a3b8;">No items</td></tr>'}
        </tbody>
      </table>
    </div>

    <div class="bottom-area">
      <div class="payment-section">
        ${cfg.showPaymentHistory && paymentRows ? `
          <div class="section-label">${escapeHtml(cfg.secPaymentHistory)}</div>
          <div class="table-wrap" style="margin-bottom:0;">
            <table class="pay-table">
              <thead><tr><th>Date</th><th>Method</th><th class="num">Amount</th></tr></thead>
              <tbody>${paymentRows}</tbody>
            </table>
          </div>
        ` : '<div style="color:#94a3b8;font-size:11px;">No payment records.</div>'}
      </div>
      <div class="summary-box">
        <div class="s-row"><span class="s-label">${hasCustomVoucherPrice ? 'Voucher Total' : 'Total'}</span><span class="s-val">${money(voucherTotal)}</span></div>
        <div class="s-row"><span class="s-label">Discount</span><span class="s-val">${money(voucherDiscount)}</span></div>
        <div class="s-row sub-highlight"><span class="s-label">${hasCustomVoucherPrice ? 'Actual Net Amount' : 'Net Amount'}</span><span class="s-val">${money(voucherNet)}</span></div>
        <div class="s-row"><span class="s-label">Paid</span><span class="s-val">${money(voucherPaid)}</span></div>
        <div class="s-row highlight"><span class="s-label">Balance Due</span><span class="s-val">${money(voucherDue)}</span></div>
      </div>
    </div>

    ${sale.remark ? `
      <div class="section-label" style="margin-top:12px;">${escapeHtml(cfg.secRemark)}</div>
      <div class="remark-box">${escapeHtml(sale.remark)}</div>
    ` : ''}

    ${cfg.customerNotice ? `
      <div class="notice-box">${escapeHtml(cfg.customerNotice)}</div>
    ` : ''}

    ${cfg.showSignatures ? `
    <div class="signatures">
      <div class="sign">${escapeHtml(cfg.sign1)}</div>
      <div class="sign">${escapeHtml(cfg.sign2)}</div>
    </div>
    ` : ''}

    <div class="footer-bar">
      ${escapeHtml(companyName)} &nbsp;·&nbsp; ${escapeHtml(footerNote)} &nbsp;·&nbsp; ${escapeHtml(fmtDate(sale.saleDate))}
    </div>
  </div>

  ${preview ? '' : `
  <script>
    window.onload = function() {
      setTimeout(function() {
        window.print();
        window.close();
      }, 120);
    };
  </script>
  `}
</body>
</html>`;

  return {
    html: isPosDesign ? posHtml : standardHtml,
    popupSize: isPosDesign ? 'width=420,height=760' : 'width=980,height=860'
  };
};
