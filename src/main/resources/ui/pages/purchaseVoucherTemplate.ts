import { PurchaseDTO } from '../types';
import { buildCompanyContact, CompanySettings, getCachedCompanySettings } from '../utils/companySettings';
import { DEFAULT_PURCHASE_CONFIG, parseVoucherConfig, VoucherPurchaseConfig } from '../utils/voucherTemplateConfig';

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

type BuildPurchaseVoucherHtmlInput = {
  purchase: PurchaseDTO;
  settings?: CompanySettings;
  cfg?: Partial<VoucherPurchaseConfig>;
  preview?: boolean;
};

export const buildPurchaseVoucherHtml = ({
  purchase,
  settings,
  cfg: cfgOverride,
  preview = false,
}: BuildPurchaseVoucherHtmlInput): { html: string; popupSize: string } => {
  const cs = settings ?? getCachedCompanySettings();
  const baseCfg = parseVoucherConfig(cs.voucherConfigJson).purchase;
  const cfg: VoucherPurchaseConfig = { ...baseCfg, ...cfgOverride };

  const companyName = cs.companyName || 'Company';
  const companyContact = buildCompanyContact(cs);
  const footerNote = cs.footerNote || 'Thank you';
  const logoSrc = cs.logoBase64 || '/img/logo.png';
  const headerColor = cfg.headerColor || DEFAULT_PURCHASE_CONFIG.headerColor;

  const itemRows = (purchase.details || []).map((d, idx) => {
    const name = escapeHtml(d.productName || `Product #${d.productId}`);
    const qty = Number(d.qty) || 0;
    const cost = money(Number(d.unitCost) || 0);
    const sub = money(Number(d.subtotal) || 0);
    const serials = d.serialNumbers?.length ? d.serialNumbers.join(', ') : '-';
    return `
      <tr>
        <td class="center">${idx + 1}</td>
        <td>
          <div>${name}</div>
          ${d.serialNumbers?.length ? `<div class="item-sn">SN: ${escapeHtml(serials)}</div>` : ''}
          ${(() => {
            const terms = String(d.warrantyTerms || '').trim();
            if (terms) return `<div class="item-sn">Warranty: ${escapeHtml(terms)}</div>`;
            const m = Number(d.warrantyMonths) || 0;
            if (!m) return '';
            const label = m % 12 === 0 ? `${m/12} Year${m/12>1?'s':''}` : `${m} Month${m>1?'s':''}`;
            return `<div class="item-sn">Warranty: ${label}</div>`;
          })()}
        </td>
        <td class="num">${qty}</td>
        <td class="num">${cost}</td>
        <td class="num">${sub}</td>
      </tr>
    `;
  }).join('');

  const num = (v?: number | null) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  };
  const subtotal = num(purchase.totalAmount);
  const discount = num(purchase.discountAmount);
  const tax = num(purchase.taxAmount);
  const otherCharges = num(purchase.otherCharges);
  const net = purchase.netAmount != null
    ? num(purchase.netAmount)
    : Math.max(0, subtotal - discount + tax + otherCharges);
  const paid = num(purchase.paidAmount);
  const due = purchase.dueAmount != null
    ? Math.max(0, num(purchase.dueAmount))
    : Math.max(0, net - paid);

  const summaryRows = [
    `<div class="s-row"><span>Subtotal</span><span class="s-val">${money(subtotal)}</span></div>`,
    discount > 0 ? `<div class="s-row"><span>Discount</span><span class="s-val">- ${money(discount)}</span></div>` : '',
    tax > 0 ? `<div class="s-row"><span>Tax / VAT</span><span class="s-val">${money(tax)}</span></div>` : '',
    otherCharges > 0 ? `<div class="s-row"><span>Other Charges</span><span class="s-val">${money(otherCharges)}</span></div>` : '',
    `<div class="s-row sub-highlight"><span>Net Amount</span><span class="s-val">${money(net)}</span></div>`,
    `<div class="s-row"><span>Paid</span><span class="s-val">${money(paid)}</span></div>`,
    `<div class="s-row highlight"><span>Balance Due</span><span class="s-val">${money(due)}</span></div>`,
  ].filter(Boolean).join('');

  const signaturesHtml = cfg.showSignatures ? `
    <div class="signatures">
      <div class="sign">${escapeHtml(cfg.sign1)}</div>
      <div class="sign">${escapeHtml(cfg.sign2)}</div>
    </div>
  ` : '';

  const html = `
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <title></title>
  <style>
    @page { size: A4 portrait; margin: 0; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: Pyidaungsu, 'Segoe UI', Arial, sans-serif; color: #111827; font-size: 12px; line-height: 1.42; background: #fff; padding: 10mm; }

    .header {
      display: flex; justify-content: space-between; align-items: flex-start; gap: 16px;
      padding: 12px 16px 10px; background: #fff; border: 1px solid #d1d5db; border-bottom: 3px solid ${headerColor};
    }
    .brand-name { font-size: 19px; font-weight: 800; color: #111827; line-height: 1.15; }
    .brand-sub { margin-top: 4px; font-size: 10px; color: #6b7280; max-width: 360px; line-height: 1.45; }
    .inv-box { text-align: right; flex-shrink: 0; min-width: 170px; border-left: 1px solid #d1d5db; padding-left: 14px; }
    .inv-label { font-size: 9px; text-transform: uppercase; letter-spacing: 1px; color: #6b7280; }
    .inv-code { font-size: 18px; font-weight: 800; color: #111827; margin-top: 3px; }
    .inv-date { font-size: 10px; color: #6b7280; margin-top: 2px; }

    .body-wrap { border: 1px solid #d1d5db; border-top: none; padding: 14px 16px 12px; }

    .blocks { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid #d1d5db; }
    .block { padding: 8px 10px; background: #fff; border-radius: 3px; border: 1px solid #d1d5db; border-left: 3px solid ${headerColor}; }
    .block-title { font-size: 9px; text-transform: uppercase; letter-spacing: 0.6px; color: #111827; margin-bottom: 6px; padding-bottom: 4px; border-bottom: 1px solid #e5e7eb; font-weight: 700; }
    .block-row { display: flex; justify-content: space-between; gap: 8px; margin-top: 4px; }
    .block-row:first-of-type { margin-top: 0; }
    .bl { color: #6b7280; font-size: 10px; }
    .bv { font-weight: 700; font-size: 11px; text-align: right; }

    .badge { display: inline-block; padding: 1px 7px; border-radius: 20px; font-size: 10px; font-weight: 600; background: #dcfce7; color: #166534; }
    .badge.partial { background: #fef9c3; color: #854d0e; }
    .badge.pending { background: #f1f5f9; color: #475569; }

    .table-wrap { border: 1px solid #d1d5db; border-radius: 3px; overflow: visible; margin-bottom: 12px; }
    table { width: 100%; border-collapse: collapse; table-layout: fixed; }
    thead { display: table-header-group; }
    tfoot { display: table-footer-group; }
    th { background: #f3f4f6; color: #111827; font-size: 9px; text-transform: uppercase; letter-spacing: 0.4px; padding: 6px 7px; border-bottom: 1px solid #d1d5db; font-weight: 700; }
    td { padding: 5px 7px; border-bottom: 1px solid #e5e7eb; vertical-align: top; }
    tr:last-child td { border-bottom: none; }
    tr:nth-child(even) td { background: #fff; }
    tr { break-inside: avoid; page-break-inside: avoid; }
    .num { text-align: right; white-space: nowrap; }
    .center { text-align: center; }
    .item-sn { font-size: 9.5px; color: #64748b; margin-top: 1px; }

    .bottom-area { display: flex; justify-content: flex-end; align-items: flex-start; gap: 16px; break-inside: avoid; page-break-inside: avoid; }
    .summary-box { width: 245px; flex-shrink: 0; border: 1px solid #d1d5db; border-radius: 3px; overflow: hidden; }
    .s-row { display: flex; justify-content: space-between; align-items: center; padding: 5px 10px; border-bottom: 1px solid #e5e7eb; font-size: 11px; }
    .s-row:last-child { border-bottom: none; }
    .s-row.highlight { background: ${headerColor}; color: #fff; font-weight: 700; }
    .s-row.sub-highlight { background: #f1f5f9; font-weight: 600; }
    .s-val { font-weight: 600; }

    .remark-box { margin-top: 12px; border: 1px solid #d1d5db; border-radius: 3px; padding: 7px 9px; min-height: 34px; font-size: 11px; color: #4b5563; break-inside: avoid; page-break-inside: avoid; }
    .section-label { font-size: 9px; text-transform: uppercase; letter-spacing: 0.6px; color: #111827; font-weight: 700; margin-bottom: 6px; }
    .signatures { margin-top: 22px; display: grid; grid-template-columns: 1fr 1fr; gap: 28px; break-inside: avoid; page-break-inside: avoid; }
    .sign { padding-top: 28px; border-top: 1px solid #9ca3af; text-align: center; font-size: 10px; color: #6b7280; }
    .footer-bar { margin-top: 10px; padding-top: 6px; border-top: 1px dashed #d1d5db; text-align: center; font-size: 9px; color: #6b7280; break-inside: avoid; page-break-inside: avoid; }
    @media print {
      body { margin: 0 !important; }
      .header, .blocks, .bottom-area, .summary-box, .remark-box, .signatures, .footer-bar {
        break-inside: avoid;
        page-break-inside: avoid;
      }
      thead { display: table-header-group; }
      tr { break-inside: avoid; page-break-inside: avoid; }
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
      <div class="inv-label">${escapeHtml(cfg.title)}</div>
      <div class="inv-code">${escapeHtml(purchase.purchaseCode || `#${purchase.id}`)}</div>
      <div class="inv-date">${escapeHtml(fmtDate(purchase.purchaseDate))}</div>
    </div>
  </div>

  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">${escapeHtml(cfg.secSupplier)}</div>
        <div class="block-row">
          <span class="bl">Supplier</span>
          <span class="bv">${escapeHtml(purchase.supplierName || '-')}</span>
        </div>
        ${purchase.poCode ? `<div class="block-row"><span class="bl">PO</span><span class="bv">${escapeHtml(purchase.poCode)}</span></div>` : ''}
        ${purchase.supplierInvoiceNo ? `<div class="block-row"><span class="bl">Supplier Inv</span><span class="bv">${escapeHtml(purchase.supplierInvoiceNo)}</span></div>` : ''}
        <div class="block-row">
          <span class="bl">Payment</span>
          <span class="bv">
            <span class="badge${purchase.paymentStatus === 'Partial' ? ' partial' : purchase.paymentStatus === 'Pending' ? ' pending' : ''}">${escapeHtml(purchase.paymentStatus || '-')}</span>
          </span>
        </div>
      </div>
      <div class="block">
        <div class="block-title">${escapeHtml(cfg.secPreparedBy)}</div>
        <div class="block-row">
          <span class="bl">Staff</span>
          <span class="bv">${escapeHtml(purchase.staffName || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Date</span>
          <span class="bv">${escapeHtml(fmtDate(purchase.purchaseDate))}</span>
        </div>
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th style="width:5%" class="center">#</th>
            <th style="width:45%">${escapeHtml(cfg.colItem)}</th>
            <th style="width:10%" class="num">${escapeHtml(cfg.colQty)}</th>
            <th style="width:20%" class="num">${escapeHtml(cfg.colUnitCost)}</th>
            <th style="width:20%" class="num">${escapeHtml(cfg.colAmount)}</th>
          </tr>
        </thead>
        <tbody>
          ${itemRows || '<tr><td colspan="5" class="center" style="padding:16px;color:#94a3b8;">No items</td></tr>'}
        </tbody>
      </table>
    </div>

    <div class="bottom-area">
      <div class="summary-box">
        ${summaryRows}
      </div>
    </div>

    ${purchase.remark ? `
      <div class="section-label" style="margin-top:12px;">${escapeHtml(cfg.secRemark)}</div>
      <div class="remark-box">${escapeHtml(purchase.remark)}</div>
    ` : ''}

    ${signaturesHtml}

    <div class="footer-bar">
      ${escapeHtml(companyName)} | ${escapeHtml(footerNote)} | ${escapeHtml(fmtDate(purchase.purchaseDate))}
    </div>
  </div>

  ${preview ? '' : `
  <script>
    window.onload = function() {
      setTimeout(function() { window.print(); window.close(); }, 120);
    };
  </script>
  `}
</body>
</html>`;

  return { html, popupSize: 'width=980,height=860' };
};
