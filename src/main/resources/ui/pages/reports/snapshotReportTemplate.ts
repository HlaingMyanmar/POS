import { CompanySettings, getCachedCompanySettings } from '../../utils/companySettings';

const esc = (v?: string | number | null) =>
  String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const money = (v: any) =>
  Number(v ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtDate = (v?: string) => {
  if (!v) return '-';
  const d = new Date(v);
  return isNaN(d.getTime()) ? v : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

const statusBadge = (status?: string) => {
  if (!status) return '';
  const s = status.toLowerCase();
  const color = s.includes('paid') || s.includes('complete') || s.includes('deliver')
    ? '#065f46' : s.includes('partial') || s.includes('progress')
    ? '#92400e' : s.includes('due') || s.includes('pending') || s.includes('cancel')
    ? '#991b1b' : '#374151';
  const bg = s.includes('paid') || s.includes('complete') || s.includes('deliver')
    ? '#d1fae5' : s.includes('partial') || s.includes('progress')
    ? '#fef3c7' : s.includes('due') || s.includes('pending') || s.includes('cancel')
    ? '#fee2e2' : '#f3f4f6';
  return `<span style="display:inline-block;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:700;background:${bg};color:${color}">${esc(status)}</span>`;
};

const sectionHeader = (title: string, count?: number) =>
  `<div class="sec-hdr">${esc(title)}${count !== undefined ? ` <span class="sec-count">(${count})</span>` : ''}</div>`;

const tableWrap = (html: string) => `<div class="tbl-wrap">${html}</div>`;

export interface SnapshotData {
  periodLabel: string;
  dateFrom: string;
  dateTo: string;
  summary: {
    saleCount: number;
    totalIncome: number;
    totalExpenses: number;
    netProfit: number;
    netSaleRevenue: number;
    serviceRevenue: number;
    otherIncome: number;
    purchaseAmount: number;
    netPurchaseCost: number;
  };
  sales: any[];
  purchases: any[];
  serviceJobs: any[];
  bookings: any[];
  expenses: any[];
  incomes: any[];
  products: any[];
}

export const buildSnapshotReportHtml = (
  data: SnapshotData,
  settings?: CompanySettings
): string => {
  const cs = settings ?? getCachedCompanySettings();
  const company = esc(cs.companyName || 'Company');
  const address = esc(cs.companyAddress || '');
  const phone   = esc(cs.companyPhone || '');
  const logo    = cs.logoBase64 ? `<img src="${cs.logoBase64}" style="height:56px;width:auto;object-fit:contain" />` : '';
  const now     = new Date().toLocaleString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });

  const salesRows = data.sales.map((s, i) => `
    <tr>
      <td>${i + 1}</td>
      <td class="mono">${esc(s.saleCode || `#${s.id}`)}</td>
      <td>${esc(s.customerName || '-')}</td>
      <td>${esc(s.staffName || '-')}</td>
      <td>${fmtDate(s.saleDate)}</td>
      <td class="num">${money(s.netAmount ?? s.totalAmount)}</td>
      <td>${statusBadge(s.paymentStatus)}</td>
    </tr>`).join('');

  const purchaseRows = data.purchases.map((p, i) => `
    <tr>
      <td>${i + 1}</td>
      <td class="mono">${esc(p.purchaseCode || `#${p.id}`)}</td>
      <td>${esc(p.supplierName || '-')}</td>
      <td>${esc(p.staffName || '-')}</td>
      <td>${fmtDate(p.purchaseDate)}</td>
      <td class="num">${money(p.netAmount ?? p.totalAmount)}</td>
      <td>${statusBadge(p.paymentStatus)}</td>
    </tr>`).join('');

  const jobRows = data.serviceJobs.map((j, i) => `
    <tr>
      <td>${i + 1}</td>
      <td class="mono">${esc(j.jobNo || `#${j.id}`)}</td>
      <td>${esc(j.customerName || '-')}</td>
      <td>${esc(j.itemName || '-')}</td>
      <td>${esc(j.assignedStaffName || '-')}</td>
      <td>${statusBadge(j.status)}</td>
      <td class="num">${money(j.netAmount ?? j.finalCost ?? 0)}</td>
    </tr>`).join('');

  const bookingRows = data.bookings.map((b, i) => `
    <tr>
      <td>${i + 1}</td>
      <td class="mono">${esc(b.bookingNo || `#${b.id}`)}</td>
      <td>${esc(b.customerName || '-')}</td>
      <td>${esc(b.itemName || b.deviceModel || '-')}</td>
      <td>${fmtDate(b.receivedDate || b.bookingDate || b.createdAt)}</td>
      <td>${esc(b.assignedStaffName || b.technicianName || '-')}</td>
      <td>${statusBadge(b.status)}</td>
    </tr>`).join('');

  const expInc = [
    ...data.expenses.map(e => ({ ...e, _type: 'Expense' })),
    ...data.incomes.map(i => ({ ...i, _type: 'Income' })),
  ].sort((a, b) => new Date(a.expenseDate || a.incomeDate || 0).getTime() - new Date(b.expenseDate || b.incomeDate || 0).getTime());

  const expIncRows = expInc.map((r, i) => {
    const isExp = r._type === 'Expense';
    return `<tr>
      <td>${i + 1}</td>
      <td class="mono">${esc(r.expenseCode || r.incomeCode || `#${r.id}`)}</td>
      <td>${fmtDate(r.expenseDate || r.incomeDate)}</td>
      <td>${esc(r.description || r.accountName || '-')}</td>
      <td>${esc(r.staffName || '-')}</td>
      <td class="num ${isExp ? 'red' : 'grn'}">${isExp ? '−' : '+'}${money(r.amount)}</td>
      <td><span style="font-size:10px;font-weight:700;color:${isExp ? '#991b1b' : '#065f46'}">${r._type}</span></td>
    </tr>`;
  }).join('');

  const productRows = data.products.map((p, i) => {
    const low = (p.currentStock ?? p.stockQty ?? 0) <= (p.minStockLevel ?? 0) && (p.minStockLevel ?? 0) > 0;
    return `<tr ${low ? 'style="background:#fff7ed"' : ''}>
      <td>${i + 1}</td>
      <td class="mono">${esc(p.productCode)}</td>
      <td>${esc(p.name)}</td>
      <td>${esc(p.categoryName || '-')}</td>
      <td class="num ${low ? 'red' : ''}">${(p.currentStock ?? p.stockQty ?? 0).toLocaleString()}</td>
      <td>${esc(p.unitName || '-')}</td>
      ${low ? '<td><span style="font-size:9px;font-weight:800;color:#b45309;background:#fef3c7;padding:2px 5px;border-radius:3px">LOW</span></td>' : '<td></td>'}
    </tr>`;
  }).join('');

  const d = data.summary;

  return `<!DOCTYPE html>
<html lang="my">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Daily Snapshot — ${esc(data.periodLabel)}</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"Noto Sans Myanmar","Myanmar Text","Padauk",Arial,sans-serif;font-size:12px;color:#111;background:#fff;padding:20px}
@media print{
  body{padding:0}
  .no-print{display:none!important}
  .page-section{page-break-inside:avoid}
  @page{size:A4;margin:15mm 12mm}
}
.print-btn{display:flex;align-items:center;gap:8px;margin-bottom:16px}
.print-btn button{padding:6px 16px;background:#4f46e5;color:#fff;border:none;border-radius:6px;font-size:12px;cursor:pointer;font-weight:700}
.print-btn button:hover{background:#4338ca}
/* Header */
.header{display:flex;align-items:flex-start;justify-content:space-between;border-bottom:2px solid #1e1b4b;padding-bottom:12px;margin-bottom:16px}
.header-left{display:flex;align-items:center;gap:12px}
.company-name{font-size:18px;font-weight:900;color:#1e1b4b}
.company-sub{font-size:11px;color:#475569;margin-top:2px}
.header-right{text-align:right;font-size:10px;color:#64748b}
/* Title bar */
.title-bar{background:#1e1b4b;color:#fff;text-align:center;padding:8px;border-radius:6px;margin-bottom:16px}
.title-bar h1{font-size:14px;font-weight:900;letter-spacing:.05em}
.title-bar p{font-size:11px;opacity:.8;margin-top:2px}
/* Summary cards */
.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:16px}
.card{border-radius:8px;padding:10px 12px;border:1px solid #e2e8f0}
.card-label{font-size:9px;font-weight:800;text-transform:uppercase;letter-spacing:.06em;color:#64748b}
.card-value{font-size:16px;font-weight:900;margin-top:3px}
.card-sub{font-size:10px;color:#94a3b8;margin-top:1px}
.c-income{background:#eff6ff;border-color:#bfdbfe}.c-income .card-value{color:#1d4ed8}
.c-expense{background:#fff1f2;border-color:#fecdd3}.c-expense .card-value{color:#be123c}
.c-profit-pos{background:#f0fdf4;border-color:#bbf7d0}.c-profit-pos .card-value{color:#15803d}
.c-profit-neg{background:#fff7ed;border-color:#fed7aa}.c-profit-neg .card-value{color:#c2410c}
.c-neutral{background:#f8fafc;border-color:#e2e8f0}.c-neutral .card-value{color:#334155}
/* Sections */
.page-section{margin-bottom:16px}
.sec-hdr{background:#334155;color:#fff;padding:6px 12px;font-size:11px;font-weight:900;text-transform:uppercase;letter-spacing:.05em;border-radius:4px 4px 0 0}
.sec-count{font-weight:500;opacity:.7;font-size:10px}
/* Tables */
.tbl-wrap{border:1px solid #e2e8f0;border-radius:0 0 4px 4px;overflow:hidden}
table{width:100%;border-collapse:collapse;font-size:11px}
thead tr{background:#f8fafc}
th{padding:6px 8px;text-align:left;font-size:9px;font-weight:800;text-transform:uppercase;color:#64748b;border-bottom:1px solid #e2e8f0;white-space:nowrap}
td{padding:5px 8px;border-bottom:1px solid #f1f5f9;vertical-align:middle}
tr:last-child td{border-bottom:none}
tr:hover{background:#f8fafc}
.mono{font-family:monospace;font-weight:700;color:#1e1b4b}
.num{text-align:right;font-weight:600;font-variant-numeric:tabular-nums}
.red{color:#dc2626}.grn{color:#16a34a}
.empty{text-align:center;padding:20px;color:#94a3b8;font-style:italic;font-size:11px}
/* Mini summary row */
.summary-2col{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:16px}
.sum-box{background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;padding:10px 12px}
.sum-box-label{font-size:9px;font-weight:800;text-transform:uppercase;color:#64748b;margin-bottom:6px}
.sum-row{display:flex;justify-content:space-between;font-size:11px;padding:2px 0}
.sum-row span:last-child{font-weight:700}
</style>
</head>
<body>
<div class="no-print print-btn">
  <button onclick="window.print()">🖨️ Print / Save as PDF</button>
  <span style="font-size:11px;color:#64748b">Save as PDF: Print dialog → Destination → Save as PDF</span>
</div>

<div class="header">
  <div class="header-left">
    ${logo}
    <div>
      <div class="company-name">${company}</div>
      <div class="company-sub">${address}${phone ? ` · ${phone}` : ''}</div>
    </div>
  </div>
  <div class="header-right">
    <div>Generated: ${esc(now)}</div>
  </div>
</div>

<div class="title-bar">
  <h1>တစ်နေ့တာ Snapshot Report</h1>
  <p>Period: ${esc(data.periodLabel)}&nbsp;&nbsp;|&nbsp;&nbsp;${esc(data.dateFrom)} to ${esc(data.dateTo)}</p>
</div>

<!-- SUMMARY CARDS -->
<div class="cards">
  <div class="card c-income">
    <div class="card-label">Total Income</div>
    <div class="card-value">${money(d.totalIncome)}</div>
    <div class="card-sub">Sales: ${money(d.netSaleRevenue)} · Svc: ${money(d.serviceRevenue)}</div>
  </div>
  <div class="card c-expense">
    <div class="card-label">Total Expenses</div>
    <div class="card-value">${money(d.totalExpenses)}</div>
    <div class="card-sub">Purchase: ${money(d.netPurchaseCost)}</div>
  </div>
  <div class="card ${d.netProfit >= 0 ? 'c-profit-pos' : 'c-profit-neg'}">
    <div class="card-label">Net Profit</div>
    <div class="card-value">${d.netProfit >= 0 ? '' : '−'}${money(Math.abs(d.netProfit))}</div>
    <div class="card-sub">${d.netProfit >= 0 ? 'Profit' : 'Loss'}</div>
  </div>
  <div class="card c-neutral">
    <div class="card-label">Sales Vouchers</div>
    <div class="card-value">${d.saleCount}</div>
    <div class="card-sub">Transactions</div>
  </div>
</div>

<div class="summary-2col">
  <div class="sum-box">
    <div class="sum-box-label">Income Breakdown</div>
    <div class="sum-row"><span>Net Sales Revenue</span><span>${money(d.netSaleRevenue)} Ks</span></div>
    <div class="sum-row"><span>Service Revenue</span><span>${money(d.serviceRevenue)} Ks</span></div>
    <div class="sum-row"><span>Other Income</span><span>${money(d.otherIncome)} Ks</span></div>
    <div class="sum-row" style="border-top:1px solid #e2e8f0;margin-top:4px;padding-top:4px"><span style="font-weight:800">Total Income</span><span style="color:#1d4ed8">${money(d.totalIncome)} Ks</span></div>
  </div>
  <div class="sum-box">
    <div class="sum-box-label">Expense Breakdown</div>
    <div class="sum-row"><span>Net Purchase Cost</span><span>${money(d.netPurchaseCost)} Ks</span></div>
    <div class="sum-row"><span>Expenses</span><span>${money(d.totalExpenses)} Ks</span></div>
    <div class="sum-row" style="border-top:1px solid #e2e8f0;margin-top:4px;padding-top:4px"><span style="font-weight:800">Net Profit</span><span style="color:${d.netProfit >= 0 ? '#15803d' : '#be123c'}">${money(d.netProfit)} Ks</span></div>
  </div>
</div>

<!-- SALES -->
<div class="page-section">
  ${sectionHeader('ရောင်းချမှုများ / Sales', data.sales.length)}
  ${tableWrap(data.sales.length === 0 ? `<p class="empty">ရောင်းချမှု မရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Customer</th><th>Staff</th><th>Date</th><th style="text-align:right">Amount (Ks)</th><th>Status</th></tr></thead>
    <tbody>${salesRows}</tbody>
    <tfoot><tr style="background:#eff6ff"><td colspan="5" style="text-align:right;font-weight:800;font-size:11px">Total (${data.sales.length} vouchers)</td><td class="num" style="font-weight:900;color:#1d4ed8">${money(data.sales.reduce((s, r) => s + (r.netAmount ?? r.totalAmount ?? 0), 0))}</td><td></td></tr></tfoot>
  </table>`)}
</div>

<!-- PURCHASES -->
<div class="page-section">
  ${sectionHeader('ဝယ်ယူမှုများ / Purchases', data.purchases.length)}
  ${tableWrap(data.purchases.length === 0 ? `<p class="empty">ဝယ်ယူမှု မရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Supplier</th><th>Staff</th><th>Date</th><th style="text-align:right">Amount (Ks)</th><th>Status</th></tr></thead>
    <tbody>${purchaseRows}</tbody>
    <tfoot><tr style="background:#faf5ff"><td colspan="5" style="text-align:right;font-weight:800;font-size:11px">Total (${data.purchases.length} vouchers)</td><td class="num" style="font-weight:900;color:#7e22ce">${money(data.purchases.reduce((s, r) => s + (r.netAmount ?? r.totalAmount ?? 0), 0))}</td><td></td></tr></tfoot>
  </table>`)}
</div>

<!-- SERVICE JOBS -->
<div class="page-section">
  ${sectionHeader('ဝန်ဆောင်မှုလုပ်ငန်းများ / Service Jobs', data.serviceJobs.length)}
  ${tableWrap(data.serviceJobs.length === 0 ? `<p class="empty">ဝန်ဆောင်မှုလုပ်ငန်း မရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Job No</th><th>Customer</th><th>Item</th><th>Staff</th><th>Status</th><th style="text-align:right">Amount (Ks)</th></tr></thead>
    <tbody>${jobRows}</tbody>
  </table>`)}
</div>

<!-- BOOKINGS -->
<div class="page-section">
  ${sectionHeader('ပစ္စည်းလက်ခံ / Bookings', data.bookings.length)}
  ${tableWrap(data.bookings.length === 0 ? `<p class="empty">ပစ္စည်းလက်ခံ မရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Booking No</th><th>Customer</th><th>Item / Device</th><th>Date In</th><th>Staff</th><th>Status</th></tr></thead>
    <tbody>${bookingRows}</tbody>
  </table>`)}
</div>

<!-- INCOME & EXPENSES -->
<div class="page-section">
  ${sectionHeader('ဝင်ငွေ / ထွက်ငွေ (Income & Expenses)', expInc.length)}
  ${tableWrap(expInc.length === 0 ? `<p class="empty">ဝင်ငွေ/ထွက်ငွေ မရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Date</th><th>Description</th><th>Staff</th><th style="text-align:right">Amount (Ks)</th><th>Type</th></tr></thead>
    <tbody>${expIncRows}</tbody>
  </table>`)}
</div>

<!-- PRODUCT STOCK -->
<div class="page-section">
  ${sectionHeader('ပစ္စည်းလက်ကျန် / Product Stock', data.products.length)}
  ${tableWrap(data.products.length === 0 ? `<p class="empty">ပစ္စည်းမရှိပါ</p>` : `
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Product Name</th><th>Category</th><th style="text-align:right">Stock</th><th>Unit</th><th>Alert</th></tr></thead>
    <tbody>${productRows}</tbody>
  </table>`)}
</div>

<div style="margin-top:20px;text-align:center;font-size:10px;color:#94a3b8;border-top:1px solid #e2e8f0;padding-top:10px">
  ${esc(cs.footerNote || 'Thank you')} &nbsp;·&nbsp; ${company} &nbsp;·&nbsp; Generated ${esc(now)}
</div>
</body>
</html>`;
};
