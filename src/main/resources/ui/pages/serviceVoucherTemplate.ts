import { ServiceJobDTO } from '../types';
import { buildCompanyContact, CompanySettings, getCachedCompanySettings } from '../utils/companySettings';
import { DEFAULT_SERVICE_CONFIG, parseVoucherConfig, VoucherServiceConfig } from '../utils/voucherTemplateConfig';

const escapeHtml = (v?: string | number | null) =>
  String(v ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const money = (v?: number | null) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v) || 0);

const lineConfirmationLabel = (value?: string) => {
  switch (value) {
    case 'INSPECTING': return 'စစ်ဆေးဆဲ';
    case 'CUSTOMER_APPROVED': return 'Customer အတည်ပြုပြီး';
    case 'CUSTOMER_REJECTED': return 'Customer ငြင်းပယ်';
    case 'IN_PROGRESS': return 'လုပ်ဆောင်ဆဲ';
    case 'COMPLETED': return 'ပြီးစီး';
    default: return 'အကြံပြုထားသည်';
  }
};

const fmtDate = (v?: string) => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('en-GB', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

export type ServiceVoucherPaper = 'A4' | 'A5';
export type ServiceVoucherMode = 'intake' | 'done';

type BuildServiceVoucherHtmlInput = {
  job: ServiceJobDTO;
  settings?: CompanySettings;
  cfg?: Partial<VoucherServiceConfig>;
  preview?: boolean;
  paper?: ServiceVoucherPaper;
  mode?: ServiceVoucherMode;
};

export const buildServiceVoucherHtml = ({
  job,
  settings,
  cfg: cfgOverride,
  preview = false,
  paper = 'A4',
  mode = 'intake',
}: BuildServiceVoucherHtmlInput): { html: string; popupSize: string } => {
  const cs = settings ?? getCachedCompanySettings();
  const baseCfg = parseVoucherConfig(cs.voucherConfigJson).service;
  const cfg: VoucherServiceConfig = { ...baseCfg, ...cfgOverride };

  const companyName = cs.companyName || 'Company';
  const companyContact = buildCompanyContact(cs);
  const footerNote = cs.footerNote || 'Thank you';
  const logoSrc = cs.logoBase64 || '/img/logo.png';
  const headerColor = cfg.headerColor || DEFAULT_SERVICE_CONFIG.headerColor;
  const isA5 = paper === 'A5';
  const isDone = mode === 'done';
  const compact = isDone && isA5;
  const voucherTitle = isDone ? 'Service Completion Receipt' : (cfg.title || 'Service Job Receipt');

  const serviceLines = Array.isArray(job.lines) ? job.lines : [];
  const partLines = Array.isArray(job.productParts) ? job.productParts : [];

  const fmtWarranty = (l: any) => {
    const terms = String(l?.warrantyTerms || '').trim();
    if (terms) return `${terms} Warranty`;
    const m = Number(l.warrantyMonths) || 0;
    if (m <= 0) return '';
    if (m % 12 === 0) { const y = m / 12; return `${y} Year${y > 1 ? 's' : ''} Warranty`; }
    return `${m} Month${m > 1 ? 's' : ''} Warranty`;
  };

  const serviceRowsHtml = serviceLines.length
    ? serviceLines.map((l: any, i: number) => {
        const warrantyTxt = fmtWarranty(l);
        return `
          <tr>
            <td class="center">${i + 1}</td>
            <td>
              <div>${escapeHtml(l.serviceItemName || l.serviceName || l.item || '-')}</div>
              ${l.confirmationStatus ? `<div style="font-size:10px;color:#64748b;margin-top:2px;">${escapeHtml(lineConfirmationLabel(l.confirmationStatus))}</div>` : ''}
              ${warrantyTxt ? `<div style="font-size:10px;color:#0891b2;margin-top:2px;">🛡 ${escapeHtml(warrantyTxt)}</div>` : ''}
            </td>
            <td class="num">${Number(l.qty) || 1}</td>
            <td class="num">${money(l.price)}</td>
            <td class="num">${money(l.subtotal ?? (Number(l.qty || 1) * Number(l.price || 0)))}</td>
          </tr>
        `;
      }).join('')
    : '<tr><td colspan="5" class="center" style="padding:12px;color:#94a3b8;">No services recorded</td></tr>';

  const condLabel = (t?: string) => {
    if (!t) return '-';
    if (t === 'NEW') return 'New';
    if (t === 'SECOND') return 'Used';
    if (t === 'SECOND_NEW') return 'Like New';
    return t;
  };

  const partRowsHtml = partLines.length
    ? partLines.map((p: any, i: number) => {
        const serialStr = Array.isArray(p.serialNumbers) ? p.serialNumbers.join(', ') : (p.serialNo || '');
        return `
        <tr>
          <td class="center">${i + 1}</td>
          <td>${escapeHtml(p.productName || p.name || '-')}</td>
          <td style="font-size:${compact ? '7px' : isA5 ? '8.5px' : '9px'};color:#64748b;">${escapeHtml(serialStr)}</td>
          <td class="num">${Number(p.qty) || 1}</td>
          <td class="num">${money(p.unitPrice ?? p.price)}</td>
          <td class="num">${money(p.subtotal ?? (Number(p.qty || 1) * Number(p.unitPrice ?? p.price ?? 0)))}</td>
        </tr>
      `;}).join('')
    : '<tr><td colspan="6" class="center" style="padding:12px;color:#94a3b8;">No parts used</td></tr>';

  const finalCost = Number(job.finalCost) || 0;
  const discount = Number(job.discountAmount) || 0;
  const net = Number(job.netAmount) || (finalCost - discount) || 0;
  const paid = Number(job.paidAmount) || 0;
  const due = Number(job.dueAmount) || (net - paid) || 0;

  const statusBadgeClass = job.paymentStatus === 'Partial' ? ' partial'
    : job.paymentStatus === 'Pending' ? ' pending' : '';

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
    @page { size: ${paper} portrait; margin: 0; }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', Arial, sans-serif; color: #1e293b; font-size: ${isA5 ? '7px' : '12px'}; line-height: ${isA5 ? '1.3' : '1.5'}; background: #fff; padding: ${compact ? '6mm' : isA5 ? '7mm' : '10mm'}; }

    .header {
      display: flex; justify-content: space-between; align-items: flex-start; gap: ${compact ? '8px' : isA5 ? '8px' : '16px'};
      padding: ${compact ? '9px 12px 8px' : isA5 ? '10px 12px 8px' : '18px 20px 14px'}; background: ${headerColor}; border-radius: 10px 10px 0 0;
    }
    .brand-name { font-size: ${compact ? '11px' : isA5 ? '13px' : '22px'}; font-weight: 800; color: #fff; }
    .brand-sub { margin-top: 1px; font-size: ${compact ? '7px' : isA5 ? '7.5px' : '10px'}; color: #94a3b8; max-width: ${compact ? '200px' : isA5 ? '240px' : '340px'}; line-height: 1.3; }
    .inv-box { text-align: right; flex-shrink: 0; }
    .inv-label { font-size: ${compact ? '7px' : isA5 ? '7.5px' : '9px'}; text-transform: uppercase; letter-spacing: 0.8px; color: #94a3b8; }
    .inv-code { font-size: ${compact ? '11px' : isA5 ? '12px' : '18px'}; font-weight: 800; color: #fff; margin-top: 1px; }
    .inv-date { font-size: ${compact ? '7px' : isA5 ? '7.5px' : '10px'}; color: #94a3b8; margin-top: 1px; }

    .body-wrap { border: 1px solid #e2e8f0; border-top: none; border-radius: 0 0 10px 10px; padding: ${compact ? '8px 10px' : isA5 ? '8px 10px' : '14px 20px'}; }

    .blocks { display: grid; grid-template-columns: 1fr 1fr; border: 1px solid #e2e8f0; border-top: 3px solid ${headerColor}; border-radius: 6px; overflow: hidden; margin-bottom: ${compact ? '8px' : isA5 ? '8px' : '14px'}; }
    .block { padding: ${compact ? '6px 8px' : isA5 ? '6px 8px' : '10px 12px'}; background: #fff; }
    .block:first-child { border-right: 1px solid #e2e8f0; }
    .block-title { font-size: ${compact ? '7px' : isA5 ? '7.5px' : '9px'}; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; color: ${headerColor}; margin-bottom: 3px; padding-bottom: 2px; border-bottom: 1px solid #e2e8f0; }
    .block-row { display: flex; justify-content: space-between; gap: 4px; margin-top: ${compact ? '1px' : isA5 ? '2px' : '3px'}; }
    .block-row:first-of-type { margin-top: 0; }
    .bl { color: #64748b; font-size: ${compact ? '7.5px' : isA5 ? '8px' : '10px'}; }
    .bv { font-weight: 600; font-size: ${compact ? '8px' : isA5 ? '8.5px' : '11px'}; text-align: right; max-width: ${compact ? '110px' : isA5 ? '130px' : '180px'}; word-break: break-word; }

    .badge { display: inline-block; padding: 0px 4px; border-radius: 20px; font-size: ${compact ? '7.5px' : isA5 ? '8px' : '10px'}; font-weight: 600; background: #dcfce7; color: #166534; }
    .badge.partial { background: #fef9c3; color: #854d0e; }
    .badge.pending { background: #f1f5f9; color: #475569; }

    .section-label { font-size: ${compact ? '7px' : isA5 ? '7.5px' : '9px'}; text-transform: uppercase; letter-spacing: 0.6px; color: #64748b; font-weight: 700; margin-bottom: 3px; margin-top: ${compact ? '6px' : isA5 ? '8px' : '14px'}; }
    .table-wrap { border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: ${compact ? '5px' : isA5 ? '5px' : '8px'}; }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f1f5f9; color: #475569; font-size: ${compact ? '7.5px' : isA5 ? '7.5px' : '9.5px'}; text-transform: uppercase; letter-spacing: 0.3px; padding: ${compact ? '2px 3px' : isA5 ? '3px 4px' : '6px 8px'}; border-bottom: 1px solid #e2e8f0; font-weight: 700; }
    td { padding: ${compact ? '2px 3px' : isA5 ? '3px 4px' : '6px 8px'}; border-bottom: 1px solid #f1f5f9; vertical-align: top; }
    tr:last-child td { border-bottom: none; }
    tr:nth-child(even) td { background: #fafbfd; }
    .num { text-align: right; white-space: nowrap; }
    .center { text-align: center; }

    .diagnosis-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 5px; padding: ${compact ? '3px 5px' : isA5 ? '4px 6px' : '10px 12px'}; font-size: ${compact ? '8px' : isA5 ? '8.5px' : '11px'}; color: #475569; white-space: pre-wrap; min-height: 20px; }
    .cond-badge { display:inline-block; padding:0px 4px; border-radius:20px; font-size:${compact ? '7.5px' : isA5 ? '8px' : '10px'}; font-weight:600; }
    .cond-new         { background:#dcfce7; color:#166534; }
    .cond-second      { background:#fef9c3; color:#854d0e; }
    .cond-second_new  { background:#ede9fe; color:#5b21b6; }

    .bottom-area { display: flex; justify-content: flex-end; align-items: flex-start; gap: 10px; margin-top: ${compact ? '7px' : isA5 ? '7px' : '12px'}; }
    .summary-box { width: ${compact ? '180px' : isA5 ? '185px' : '250px'}; flex-shrink: 0; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; }
    .s-row { display: flex; justify-content: space-between; align-items: center; padding: ${compact ? '2px 6px' : isA5 ? '3px 7px' : '6px 12px'}; border-bottom: 1px solid #f1f5f9; font-size: ${compact ? '8px' : isA5 ? '8.5px' : '12px'}; }
    .s-row:last-child { border-bottom: none; }
    .s-row.highlight { background: ${headerColor}; color: #fff; font-weight: 700; }
    .s-row.sub-highlight { background: #f1f5f9; font-weight: 600; }
    .s-val { font-weight: 600; }

    .remark-box { margin-top: ${compact ? '4px' : isA5 ? '5px' : '8px'}; border: 1px solid #e2e8f0; border-radius: 5px; padding: ${compact ? '3px 5px' : isA5 ? '4px 6px' : '8px 10px'}; font-size: ${compact ? '8px' : isA5 ? '8.5px' : '11px'}; color: #475569; }
    .signatures { margin-top: ${compact ? '10px' : isA5 ? '12px' : '24px'}; display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .sign { padding-top: ${compact ? '18px' : isA5 ? '22px' : '28px'}; border-top: 1px solid #cbd5e1; text-align: center; font-size: ${compact ? '8px' : isA5 ? '8px' : '10px'}; color: #64748b; }
    .notice-box { margin-top: ${compact ? '4px' : isA5 ? '5px' : '8px'}; border: 1px dashed #cbd5e1; border-radius: 5px; padding: ${compact ? '3px 5px' : isA5 ? '4px 6px' : '8px 12px'}; font-size: ${compact ? '7.5px' : isA5 ? '8px' : '10.5px'}; color: #475569; white-space: pre-wrap; line-height: 1.5; background: #f8fafc; }
    .footer-bar { margin-top: ${compact ? '5px' : isA5 ? '6px' : '10px'}; padding-top: 4px; border-top: 1px dashed #e2e8f0; text-align: center; font-size: ${compact ? '7.5px' : isA5 ? '8px' : '9px'}; color: #94a3b8; }
    ${isDone ? `.done-stamp { display:inline-block; border:1.5px solid #16a34a; border-radius:4px; color:#16a34a; font-size:${compact ? '7px' : '10px'}; font-weight:800; letter-spacing:0.8px; padding:1px 5px; margin-left:5px; vertical-align:middle; text-transform:uppercase; }` : ''}
  </style>
</head>
<body>
  <div class="header">
    <div style="display:flex;align-items:center;gap:${isA5 ? '8px' : '12px'};">
      ${logoSrc ? `<img src="${logoSrc}" alt="logo" style="max-height:${isA5 ? '40px' : '50px'};max-width:${isA5 ? '60px' : '80px'};background:#fff;border-radius:6px;padding:3px;" />` : ''}
      <div>
        <div class="brand-name">${escapeHtml(companyName)}</div>
        <div class="brand-sub">${escapeHtml(companyContact)}</div>
      </div>
    </div>
    <div class="inv-box">
      <div class="inv-label">${escapeHtml(voucherTitle)}</div>
      <div class="inv-code">${escapeHtml(job.jobNo || `#${job.id}`)}${isDone ? ' <span class="done-stamp">Done</span>' : ''}</div>
      <div class="inv-date">${escapeHtml(fmtDate(isDone ? (job as any).completedDate || job.receivedDate : job.receivedDate))}</div>
    </div>
  </div>

  <div class="body-wrap">
    <div class="blocks">
      <div class="block">
        <div class="block-title">${escapeHtml(cfg.secDevice)}</div>
        <div class="block-row">
          <span class="bl">Customer</span>
          <span class="bv">${escapeHtml(job.customerName || '-')}</span>
        </div>
        ${job.customerPhone ? `
        <div class="block-row">
          <span class="bl">Phone</span>
          <span class="bv">${escapeHtml(job.customerPhone)}</span>
        </div>` : ''}
        <div class="block-row">
          <span class="bl">Device</span>
          <span class="bv">${escapeHtml(job.itemName || '-')}</span>
        </div>
        ${job.color || job.serialNo || job.accessories ? `
        <div class="block-row">
          <span class="bl">Color</span>
          <span class="bv">${escapeHtml(job.color || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Serial No</span>
          <span class="bv">${escapeHtml(job.serialNo || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Accessories</span>
          <span class="bv">${escapeHtml(job.accessories || '-')}</span>
        </div>` : ''}
        ${job.itemCondition ? `
        <div class="block-row">
          <span class="bl">Condition</span>
          <span class="bv">${escapeHtml(job.itemCondition)}</span>
        </div>` : ''}
      </div>
      <div class="block">
        <div class="block-title">Technician / Status</div>
        <div class="block-row">
          <span class="bl">Technician</span>
          <span class="bv">${escapeHtml(job.assignedStaffName || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Status</span>
          <span class="bv">${escapeHtml(job.status || '-')}</span>
        </div>
        <div class="block-row">
          <span class="bl">Received</span>
          <span class="bv">${escapeHtml(fmtDate(job.receivedDate))}</span>
        </div>
        ${isDone ? `
        <div class="block-row">
          <span class="bl">Completed</span>
          <span class="bv">${escapeHtml(fmtDate((job as any).completedDate || (job as any).updatedAt))}</span>
        </div>` : `
        <div class="block-row">
          <span class="bl">Est. Cost</span>
          <span class="bv">${money(job.estimatedCost)}</span>
        </div>`}
        ${isDone ? `
        <div class="block-row">
          <span class="bl">Payment</span>
          <span class="bv"><span class="badge${statusBadgeClass}">${escapeHtml(job.paymentStatus || '-')}</span></span>
        </div>` : ''}
        ${job.bookingNo ? `
        <div class="block-row">
          <span class="bl">Booking Ref</span>
          <span class="bv">${escapeHtml(job.bookingNo)}</span>
        </div>` : ''}
      </div>
    </div>

    ${job.diagnosisNotes ? `
      <div class="section-label">${escapeHtml(cfg.secDiagnosis)}</div>
      <div class="diagnosis-box">${escapeHtml(job.diagnosisNotes)}</div>
    ` : ''}

    ${isDone ? `
    <div class="section-label">${escapeHtml(cfg.secServices)}</div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th style="width:5%" class="center">#</th>
            <th style="width:${isA5 ? '47%' : '45%'}">Service</th>
            <th style="width:8%" class="num">Qty</th>
            <th style="width:${isA5 ? '20%' : '22%'}" class="num">Unit Price</th>
            <th style="width:20%" class="num">Subtotal</th>
          </tr>
        </thead>
        <tbody>${serviceRowsHtml}</tbody>
      </table>
    </div>

    <div class="section-label">${escapeHtml(cfg.secParts)}</div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th style="width:5%" class="center">#</th>
            <th style="width:${isA5 ? '30%' : '32%'}">Part</th>
            <th style="width:${isA5 ? '22%' : '20%'}">Serial No</th>
            <th style="width:8%" class="num">Qty</th>
            <th style="width:${isA5 ? '17%' : '18%'}" class="num">Unit Price</th>
            <th style="width:18%" class="num">Subtotal</th>
          </tr>
        </thead>
        <tbody>${partRowsHtml}</tbody>
      </table>
    </div>

    <div class="bottom-area">
      <div class="summary-box">
        <div class="s-row"><span>${escapeHtml(cfg.secPayment)}</span></div>
        <div class="s-row"><span>Estimated</span><span class="s-val">${money(job.estimatedCost)}</span></div>
        <div class="s-row"><span>Final Cost</span><span class="s-val">${money(finalCost)}</span></div>
        <div class="s-row"><span>Discount</span><span class="s-val">${money(discount)}</span></div>
        <div class="s-row sub-highlight"><span>Net Amount</span><span class="s-val">${money(net)}</span></div>
        <div class="s-row"><span>Paid</span><span class="s-val">${money(paid)}</span></div>
        <div class="s-row highlight"><span>Balance Due</span><span class="s-val">${money(due)}</span></div>
      </div>
    </div>
    ` : `
    <div class="bottom-area">
      <div class="summary-box">
        <div class="s-row"><span>Estimated Cost</span><span class="s-val">${money(job.estimatedCost)}</span></div>
      </div>
    </div>
    `}

    ${job.remark ? `
      <div class="section-label">${escapeHtml(cfg.secRemark)}</div>
      <div class="remark-box">${escapeHtml(job.remark)}</div>
    ` : ''}

    ${cfg.customerNotice ? `
      <div class="notice-box">${escapeHtml(cfg.customerNotice)}</div>
    ` : ''}

    ${signaturesHtml}

    <div class="footer-bar">
      ${escapeHtml(companyName)} &nbsp;·&nbsp; ${escapeHtml(footerNote)} &nbsp;·&nbsp; ${escapeHtml(fmtDate(job.receivedDate))}
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

  return { html, popupSize: 'width=980,height=960' };
};
