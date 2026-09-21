import{r as w,R as ke,j as e}from"./react-vendor-BRHkPwyY.js";import{k as fe,I as Ce,G as De,E as Ae}from"./index-B9qo04FS.js";import{s as Pe}from"./saleapiservice-CtG7filb.js";import{p as Te}from"./purchaseapiservice-BGVzO1GV.js";import{e as Re,i as ze}from"./incomeapiservice-GmPO5H5b.js";import{p as Ee}from"./productapiservice-DDXTvzxd.js";import{e as pe,a8 as me,bc as Ie,b6 as Le,b5 as he,aB as Ke,at as Oe,g as Me,W as ue,o as ge,T as be,aW as Be,S as Je,w as Fe,P as Ue,az as Ye,$ as We}from"./icons-vendor-Cv2DV0ct.js";import"./alerts-vendor-CH_YVA8w.js";const x=s=>String(s??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;"),h=s=>Number(s??0).toLocaleString("en-US",{minimumFractionDigits:2,maximumFractionDigits:2}),I=s=>{if(!s)return"-";const r=new Date(s);return isNaN(r.getTime())?s:r.toLocaleDateString("en-GB",{day:"2-digit",month:"short",year:"numeric"})},L=s=>{if(!s)return"";const r=s.toLowerCase(),d=r.includes("paid")||r.includes("complete")||r.includes("deliver")?"#065f46":r.includes("partial")||r.includes("progress")?"#92400e":r.includes("due")||r.includes("pending")||r.includes("cancel")?"#991b1b":"#374151";return`<span style="display:inline-block;padding:2px 6px;border-radius:4px;font-size:10px;font-weight:700;background:${r.includes("paid")||r.includes("complete")||r.includes("deliver")?"#d1fae5":r.includes("partial")||r.includes("progress")?"#fef3c7":r.includes("due")||r.includes("pending")||r.includes("cancel")?"#fee2e2":"#f3f4f6"};color:${d}">${x(s)}</span>`},C=(s,r)=>`<div class="sec-hdr">${x(s)}${r!==void 0?` <span class="sec-count">(${r})</span>`:""}</div>`,D=s=>`<div class="tbl-wrap">${s}</div>`,_e=(s,r)=>{const d=r??fe(),g=x(d.companyName||"Company"),f=x(d.companyAddress||""),j=x(d.companyPhone||""),y=d.logoBase64?`<img src="${d.logoBase64}" style="height:56px;width:auto;object-fit:contain" />`:"",S=new Date().toLocaleString("en-GB",{day:"2-digit",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit"}),o=s.sales.map((a,p)=>`
    <tr>
      <td>${p+1}</td>
      <td class="mono">${x(a.saleCode||`#${a.id}`)}</td>
      <td>${x(a.customerName||"-")}</td>
      <td>${x(a.staffName||"-")}</td>
      <td>${I(a.saleDate)}</td>
      <td class="num">${h(a.netAmount??a.totalAmount)}</td>
      <td>${L(a.paymentStatus)}</td>
    </tr>`).join(""),B=s.purchases.map((a,p)=>`
    <tr>
      <td>${p+1}</td>
      <td class="mono">${x(a.purchaseCode||`#${a.id}`)}</td>
      <td>${x(a.supplierName||"-")}</td>
      <td>${x(a.staffName||"-")}</td>
      <td>${I(a.purchaseDate)}</td>
      <td class="num">${h(a.netAmount??a.totalAmount)}</td>
      <td>${L(a.paymentStatus)}</td>
    </tr>`).join(""),T=s.serviceJobs.map((a,p)=>`
    <tr>
      <td>${p+1}</td>
      <td class="mono">${x(a.jobNo||`#${a.id}`)}</td>
      <td>${x(a.customerName||"-")}</td>
      <td>${x(a.itemName||"-")}</td>
      <td>${x(a.assignedStaffName||"-")}</td>
      <td>${L(a.status)}</td>
      <td class="num">${h(a.netAmount??a.finalCost??0)}</td>
    </tr>`).join(""),R=s.bookings.map((a,p)=>`
    <tr>
      <td>${p+1}</td>
      <td class="mono">${x(a.bookingNo||`#${a.id}`)}</td>
      <td>${x(a.customerName||"-")}</td>
      <td>${x(a.itemName||a.deviceModel||"-")}</td>
      <td>${I(a.receivedDate||a.bookingDate||a.createdAt)}</td>
      <td>${x(a.assignedStaffName||a.technicianName||"-")}</td>
      <td>${L(a.status)}</td>
    </tr>`).join(""),k=[...s.expenses.map(a=>({...a,_type:"Expense"})),...s.incomes.map(a=>({...a,_type:"Income"}))].sort((a,p)=>new Date(a.expenseDate||a.incomeDate||0).getTime()-new Date(p.expenseDate||p.incomeDate||0).getTime()),J=k.map((a,p)=>{const $=a._type==="Expense";return`<tr>
      <td>${p+1}</td>
      <td class="mono">${x(a.expenseCode||a.incomeCode||`#${a.id}`)}</td>
      <td>${I(a.expenseDate||a.incomeDate)}</td>
      <td>${x(a.description||a.accountName||"-")}</td>
      <td>${x(a.staffName||"-")}</td>
      <td class="num ${$?"red":"grn"}">${$?"−":"+"}${h(a.amount)}</td>
      <td><span style="font-size:10px;font-weight:700;color:${$?"#991b1b":"#065f46"}">${a._type}</span></td>
    </tr>`}).join(""),z=s.products.map((a,p)=>{const $=(a.currentStock??a.stockQty??0)<=(a.minStockLevel??0)&&(a.minStockLevel??0)>0;return`<tr ${$?'style="background:#fff7ed"':""}>
      <td>${p+1}</td>
      <td class="mono">${x(a.productCode)}</td>
      <td>${x(a.name)}</td>
      <td>${x(a.categoryName||"-")}</td>
      <td class="num ${$?"red":""}">${(a.currentStock??a.stockQty??0).toLocaleString()}</td>
      <td>${x(a.unitName||"-")}</td>
      ${$?'<td><span style="font-size:9px;font-weight:800;color:#b45309;background:#fef3c7;padding:2px 5px;border-radius:3px">LOW</span></td>':"<td></td>"}
    </tr>`}).join(""),m=s.summary;return`<!DOCTYPE html>
<html lang="my">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Daily Snapshot — ${x(s.periodLabel)}</title>
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
    ${y}
    <div>
      <div class="company-name">${g}</div>
      <div class="company-sub">${f}${j?` · ${j}`:""}</div>
    </div>
  </div>
  <div class="header-right">
    <div>Generated: ${x(S)}</div>
  </div>
</div>

<div class="title-bar">
  <h1>တစ်နေ့တာ Snapshot Report</h1>
  <p>Period: ${x(s.periodLabel)}&nbsp;&nbsp;|&nbsp;&nbsp;${x(s.dateFrom)} to ${x(s.dateTo)}</p>
</div>

<!-- SUMMARY CARDS -->
<div class="cards">
  <div class="card c-income">
    <div class="card-label">Total Income</div>
    <div class="card-value">${h(m.totalIncome)}</div>
    <div class="card-sub">Sales: ${h(m.netSaleRevenue)} · Svc: ${h(m.serviceRevenue)}</div>
  </div>
  <div class="card c-expense">
    <div class="card-label">Total Expenses</div>
    <div class="card-value">${h(m.totalExpenses)}</div>
    <div class="card-sub">Purchase: ${h(m.netPurchaseCost)}</div>
  </div>
  <div class="card ${m.netProfit>=0?"c-profit-pos":"c-profit-neg"}">
    <div class="card-label">Net Profit</div>
    <div class="card-value">${m.netProfit>=0?"":"−"}${h(Math.abs(m.netProfit))}</div>
    <div class="card-sub">${m.netProfit>=0?"Profit":"Loss"}</div>
  </div>
  <div class="card c-neutral">
    <div class="card-label">Sales Vouchers</div>
    <div class="card-value">${m.saleCount}</div>
    <div class="card-sub">Transactions</div>
  </div>
</div>

<div class="summary-2col">
  <div class="sum-box">
    <div class="sum-box-label">Income Breakdown</div>
    <div class="sum-row"><span>Net Sales Revenue</span><span>${h(m.netSaleRevenue)} Ks</span></div>
    <div class="sum-row"><span>Service Revenue</span><span>${h(m.serviceRevenue)} Ks</span></div>
    <div class="sum-row"><span>Other Income</span><span>${h(m.otherIncome)} Ks</span></div>
    <div class="sum-row" style="border-top:1px solid #e2e8f0;margin-top:4px;padding-top:4px"><span style="font-weight:800">Total Income</span><span style="color:#1d4ed8">${h(m.totalIncome)} Ks</span></div>
  </div>
  <div class="sum-box">
    <div class="sum-box-label">Expense Breakdown</div>
    <div class="sum-row"><span>Net Purchase Cost</span><span>${h(m.netPurchaseCost)} Ks</span></div>
    <div class="sum-row"><span>Expenses</span><span>${h(m.totalExpenses)} Ks</span></div>
    <div class="sum-row" style="border-top:1px solid #e2e8f0;margin-top:4px;padding-top:4px"><span style="font-weight:800">Net Profit</span><span style="color:${m.netProfit>=0?"#15803d":"#be123c"}">${h(m.netProfit)} Ks</span></div>
  </div>
</div>

<!-- SALES -->
<div class="page-section">
  ${C("ရောင်းချမှုများ / Sales",s.sales.length)}
  ${D(s.sales.length===0?'<p class="empty">ရောင်းချမှု မရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Customer</th><th>Staff</th><th>Date</th><th style="text-align:right">Amount (Ks)</th><th>Status</th></tr></thead>
    <tbody>${o}</tbody>
    <tfoot><tr style="background:#eff6ff"><td colspan="5" style="text-align:right;font-weight:800;font-size:11px">Total (${s.sales.length} vouchers)</td><td class="num" style="font-weight:900;color:#1d4ed8">${h(s.sales.reduce((a,p)=>a+(p.netAmount??p.totalAmount??0),0))}</td><td></td></tr></tfoot>
  </table>`)}
</div>

<!-- PURCHASES -->
<div class="page-section">
  ${C("ဝယ်ယူမှုများ / Purchases",s.purchases.length)}
  ${D(s.purchases.length===0?'<p class="empty">ဝယ်ယူမှု မရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Supplier</th><th>Staff</th><th>Date</th><th style="text-align:right">Amount (Ks)</th><th>Status</th></tr></thead>
    <tbody>${B}</tbody>
    <tfoot><tr style="background:#faf5ff"><td colspan="5" style="text-align:right;font-weight:800;font-size:11px">Total (${s.purchases.length} vouchers)</td><td class="num" style="font-weight:900;color:#7e22ce">${h(s.purchases.reduce((a,p)=>a+(p.netAmount??p.totalAmount??0),0))}</td><td></td></tr></tfoot>
  </table>`)}
</div>

<!-- SERVICE JOBS -->
<div class="page-section">
  ${C("ဝန်ဆောင်မှုလုပ်ငန်းများ / Service Jobs",s.serviceJobs.length)}
  ${D(s.serviceJobs.length===0?'<p class="empty">ဝန်ဆောင်မှုလုပ်ငန်း မရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Job No</th><th>Customer</th><th>Item</th><th>Staff</th><th>Status</th><th style="text-align:right">Amount (Ks)</th></tr></thead>
    <tbody>${T}</tbody>
  </table>`)}
</div>

<!-- BOOKINGS -->
<div class="page-section">
  ${C("ပစ္စည်းလက်ခံ / Bookings",s.bookings.length)}
  ${D(s.bookings.length===0?'<p class="empty">ပစ္စည်းလက်ခံ မရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Booking No</th><th>Customer</th><th>Item / Device</th><th>Date In</th><th>Staff</th><th>Status</th></tr></thead>
    <tbody>${R}</tbody>
  </table>`)}
</div>

<!-- INCOME & EXPENSES -->
<div class="page-section">
  ${C("ဝင်ငွေ / ထွက်ငွေ (Income & Expenses)",k.length)}
  ${D(k.length===0?'<p class="empty">ဝင်ငွေ/ထွက်ငွေ မရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Date</th><th>Description</th><th>Staff</th><th style="text-align:right">Amount (Ks)</th><th>Type</th></tr></thead>
    <tbody>${J}</tbody>
  </table>`)}
</div>

<!-- PRODUCT STOCK -->
<div class="page-section">
  ${C("ပစ္စည်းလက်ကျန် / Product Stock",s.products.length)}
  ${D(s.products.length===0?'<p class="empty">ပစ္စည်းမရှိပါ</p>':`
  <table>
    <thead><tr><th>#</th><th>Code</th><th>Product Name</th><th>Category</th><th style="text-align:right">Stock</th><th>Unit</th><th>Alert</th></tr></thead>
    <tbody>${z}</tbody>
  </table>`)}
</div>

<div style="margin-top:20px;text-align:center;font-size:10px;color:#94a3b8;border-top:1px solid #e2e8f0;padding-top:10px">
  ${x(d.footerNote||"Thank you")} &nbsp;·&nbsp; ${g} &nbsp;·&nbsp; Generated ${x(S)}
</div>
</body>
</html>`},M=s=>`${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,"0")}-${String(s.getDate()).padStart(2,"0")}`,Y=()=>M(new Date),He=()=>{const s=new Date,r=s.getDay(),d=new Date(s);return d.setDate(s.getDate()-(r===0?6:r-1)),{from:M(d),to:M(s)}},Ge=()=>{const s=new Date;return{from:`${s.getFullYear()}-${String(s.getMonth()+1).padStart(2,"0")}-01`,to:M(s)}},Qe=()=>{const s=new Date().getFullYear();return{from:`${s}-01-01`,to:`${s}-12-31`}},u=s=>Number(s??0).toLocaleString("en-US",{minimumFractionDigits:2,maximumFractionDigits:2});function A({icon:s,title:r,count:d,children:g,accentClass:f="bg-slate-700"}){const[j,y]=w.useState(!0);return e.jsxs("div",{className:"bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden",children:[e.jsxs("button",{type:"button",onClick:()=>y(S=>!S),className:`w-full flex items-center justify-between gap-3 px-4 py-3 text-white ${f} hover:opacity-90`,children:[e.jsxs("div",{className:"flex items-center gap-2",children:[s,e.jsx("span",{className:"text-sm font-black uppercase tracking-wide",children:r}),d!==void 0&&e.jsx("span",{className:"ml-1 px-2 py-0.5 rounded-full bg-white/20 text-xs font-bold",children:d})]}),j?e.jsx(Ye,{size:16}):e.jsx(We,{size:16})]}),j&&e.jsx("div",{className:"overflow-auto",children:g})]})}function K({label:s,value:r,sub:d,colorClass:g}){return e.jsxs("div",{className:`rounded-xl border p-4 ${g}`,children:[e.jsx("p",{className:"text-[10px] font-black uppercase tracking-widest text-current opacity-60",children:s}),e.jsx("p",{className:"text-xl font-black mt-1",children:r}),d&&e.jsx("p",{className:"text-[10px] opacity-60 mt-0.5",children:d})]})}const l=({children:s,right:r})=>e.jsx("th",{className:`px-3 py-2 text-[10px] font-black uppercase tracking-wider text-slate-500 bg-slate-50 border-b border-slate-200 ${r?"text-right":"text-left"} whitespace-nowrap`,children:s}),i=({children:s,right:r,mono:d,muted:g})=>e.jsx("td",{className:`px-3 py-2 text-sm border-b border-slate-100 ${r?"text-right":""} ${d?"font-mono font-bold text-indigo-700":""} ${g?"text-slate-400":"text-slate-700"}`,children:s}),O=s=>{if(!s)return e.jsx("span",{className:"text-slate-400 text-xs",children:"—"});const r=s.toLowerCase(),d=r.includes("paid")||r.includes("complete")||r.includes("deliver")?"bg-emerald-100 text-emerald-700":r.includes("partial")||r.includes("progress")?"bg-amber-100 text-amber-700":r.includes("due")||r.includes("pending")||r.includes("cancel")?"bg-rose-100 text-rose-700":"bg-slate-100 text-slate-600";return e.jsx("span",{className:`inline-block px-2 py-0.5 rounded text-[10px] font-bold ${d}`,children:s})},P=({cols:s})=>e.jsx("tr",{children:e.jsx("td",{colSpan:s,className:"px-4 py-8 text-center text-slate-400 text-sm italic",children:"မရှိပါ"})}),nt=()=>{const[s,r]=w.useState("TODAY"),[d,g]=w.useState(Y()),[f,j]=w.useState(Y()),[y,S]=w.useState(!1),[o,B]=w.useState(null),[T,R]=w.useState(!1),k=w.useRef(!1),J=t=>{if(r(t),t==="TODAY"){const n=Y();g(n),j(n)}else if(t==="WEEK"){const n=He();g(n.from),j(n.to)}else if(t==="MONTH"){const n=Ge();g(n.from),j(n.to)}else if(t==="YEAR"){const n=Qe();g(n.from),j(n.to)}},z=(t,n)=>t.filter(c=>{const v=(c[n]||"").slice(0,10);return(!d||v>=d)&&(!f||v<=f)}),m=w.useCallback(async(t,n)=>{var c,v,_,H,G,Q,V,q,X;S(!0);try{const[Z,ee,te,F,U,se,ae,ne]=await Promise.allSettled([Ce.daily(t,n),Pe.getAllPaged(0,500,"",t,n),Te.getAllPaged(0,500,"",t,n),De.getAll(0,500,"",t,n),Ae.getAll(0,500,"",t,n),Re.getAll(),ze.getAll(),Ee.getAll()]),N=Z.status==="fulfilled"?((c=Z.value)==null?void 0:c.data)??{}:{},oe=ee.status==="fulfilled"?((v=ee.value)==null?void 0:v.content)??[]:[],re=te.status==="fulfilled"?((_=te.value)==null?void 0:_.content)??[]:[],le=F.status==="fulfilled"?((G=(H=F.value)==null?void 0:H.data)==null?void 0:G.content)??((Q=F.value)==null?void 0:Q.content)??[]:[],ie=U.status==="fulfilled"?((q=(V=U.value)==null?void 0:V.data)==null?void 0:q.content)??((X=U.value)==null?void 0:X.content)??[]:[],ce=se.status==="fulfilled"?se.value??[]:[],de=ae.status==="fulfilled"?ae.value??[]:[],xe=ne.status==="fulfilled"?ne.value??[]:[],we=z(Array.isArray(ce)?ce:[],"expenseDate"),Se=z(Array.isArray(de)?de:[],"incomeDate");B({summary:{saleCount:Number(N.saleCount??0),totalIncome:Number(N.totalIncome??0),totalExpenses:Number(N.totalExpenses??0),netProfit:Number(N.netProfit??0),netSaleRevenue:Number(N.netSaleRevenue??0),serviceRevenue:Number(N.serviceRevenue??0),otherIncome:Number(N.otherIncome??0),purchaseAmount:Number(N.purchaseAmount??0),netPurchaseCost:Number(N.netPurchaseCost??0)},sales:Array.isArray(oe)?oe:[],purchases:Array.isArray(re)?re:[],serviceJobs:Array.isArray(le)?le:[],bookings:Array.isArray(ie)?ie:[],expenses:we,incomes:Se,products:Array.isArray(xe)?xe:[]})}finally{S(!1)}},[]);ke.useEffect(()=>{k.current||(k.current=!0,m(d,f))},[]);const a=()=>m(d,f),p=()=>s==="TODAY"?`Today (${d})`:s==="WEEK"?`This Week (${d} ~ ${f})`:s==="MONTH"?`This Month (${d} ~ ${f})`:s==="YEAR"?`This Year (${d} ~ ${f})`:`${d} ~ ${f}`,$=()=>{if(!o)return;const t={periodLabel:p(),dateFrom:d,dateTo:f,summary:o.summary,sales:o.sales,purchases:o.purchases,serviceJobs:o.serviceJobs,bookings:o.bookings,expenses:o.expenses,incomes:o.incomes,products:o.products},n=_e(t,fe()),c=window.open("","_blank","width=900,height=700");c&&(c.document.write(n),c.document.close(),setTimeout(()=>c.print(),600))},W=()=>{if(!o)return"";const t=o.summary;return[`📊 Daily Snapshot — ${p()}`,"━━━━━━━━━━━━━━━━━━━━━━",`💰 Total Income   : ${u(t.totalIncome)} Ks`,`📦 Net Purchase   : ${u(t.netPurchaseCost)} Ks`,`💸 Expenses       : ${u(t.totalExpenses)} Ks`,`📈 Net Profit     : ${u(t.netProfit)} Ks`,"━━━━━━━━━━━━━━━━━━━━━━",`🛒 Sales          : ${o.sales.length} vouchers`,`🏭 Purchases      : ${o.purchases.length} vouchers`,`🔧 Service Jobs   : ${o.serviceJobs.length}`,`📥 Bookings       : ${o.bookings.length}`,`📦 Products       : ${o.products.length} items`,...o.products.filter(c=>(c.currentStock??c.stockQty??0)<=(c.minStockLevel??0)&&(c.minStockLevel??0)>0).length>0?[`⚠️  Low Stock items: ${o.products.filter(c=>(c.currentStock??c.stockQty??0)<=(c.minStockLevel??0)&&(c.minStockLevel??0)>0).length}`]:[]].join(`
`)},ve=async()=>{const t=W();if(typeof navigator.share=="function")try{await navigator.share({title:`Daily Snapshot — ${p()}`,text:t})}catch{}else await E()},E=async()=>{var n;const t=W();try{if((n=navigator.clipboard)!=null&&n.writeText)await navigator.clipboard.writeText(t);else{const c=document.createElement("textarea");c.value=t,c.style.position="fixed",c.style.opacity="0",document.body.appendChild(c),c.focus(),c.select(),document.execCommand("copy"),document.body.removeChild(c)}R(!0),setTimeout(()=>R(!1),2e3)}catch{}return t},je=async()=>{const t=await E(),n=`https://t.me/share/url?url=&text=${encodeURIComponent(t)}`;window.open(n,"_blank","noopener,noreferrer")},ye=async()=>{const t=await E();window.location.href=`viber://forward?text=${encodeURIComponent(t)}`},$e=async()=>{await E()},b=o==null?void 0:o.summary,Ne=[{key:"TODAY",label:"Today"},{key:"WEEK",label:"This Week"},{key:"MONTH",label:"This Month"},{key:"YEAR",label:"This Year"}];return e.jsxs("div",{className:"w-full max-w-none space-y-5",children:[e.jsxs("div",{className:"flex flex-col sm:flex-row sm:items-center justify-between gap-3",children:[e.jsxs("div",{className:"flex items-center gap-3",children:[e.jsx("div",{className:"w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center",children:e.jsx(pe,{size:20,className:"text-white"})}),e.jsxs("div",{children:[e.jsx("h2",{className:"text-xl font-black text-slate-800",children:"တစ်နေ့တာ Snapshot Report"}),e.jsx("p",{className:"text-xs text-slate-500 mt-0.5",children:"Sales · Purchases · Services · Bookings · Income/Expense · Stock"})]})]}),e.jsxs("div",{className:"flex items-center gap-2 flex-wrap",children:[e.jsxs("button",{onClick:a,disabled:y,className:"inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50",children:[e.jsx(me,{size:13,className:y?"animate-spin":""}),"Refresh"]}),e.jsxs("button",{onClick:$e,disabled:!o,className:"inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40",children:[T?e.jsx(Ie,{size:13,className:"text-emerald-500"}):e.jsx(Le,{size:13}),T?"Copied!":"Copy Text"]}),typeof navigator.share=="function"&&e.jsxs("button",{onClick:ve,disabled:!o,className:"inline-flex items-center gap-1.5 px-3 py-1.5 bg-sky-600 text-white rounded-lg text-xs font-bold hover:bg-sky-700 disabled:opacity-40",children:[e.jsx(he,{size:13}),"Share"]}),e.jsxs("button",{onClick:je,disabled:!o,className:"inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#229ED9] text-white rounded-lg text-xs font-bold hover:bg-[#168ac1] disabled:opacity-40",children:[e.jsx(Ke,{size:13}),"Telegram"]}),e.jsxs("button",{onClick:ye,disabled:!o,className:"inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#7360F2] text-white rounded-lg text-xs font-bold hover:bg-[#5d4bd6] disabled:opacity-40",children:[e.jsx(he,{size:13}),"Viber"]}),e.jsxs("button",{onClick:$,disabled:!o,className:"inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700 disabled:opacity-40",children:[e.jsx(Oe,{size:13}),"PDF ထုတ်မည်"]})]})]}),e.jsx("div",{className:"bg-white rounded-xl border border-slate-200 shadow-sm p-4",children:e.jsxs("div",{className:"flex flex-col lg:flex-row lg:items-center gap-3",children:[e.jsxs("div",{className:"flex items-center gap-1 p-1 bg-slate-100 rounded-lg flex-shrink-0",children:[Ne.map(({key:t,label:n})=>e.jsx("button",{onClick:()=>{J(t)},className:`px-3 py-1.5 rounded-md text-xs font-bold transition-colors ${s===t?"bg-white text-indigo-700 shadow-sm":"text-slate-500 hover:text-indigo-700"}`,children:n},t)),e.jsx("button",{onClick:()=>r("CUSTOM"),className:`px-3 py-1.5 rounded-md text-xs font-bold transition-colors ${s==="CUSTOM"?"bg-white text-indigo-700 shadow-sm":"text-slate-500 hover:text-indigo-700"}`,children:"Custom"})]}),e.jsxs("div",{className:"flex items-center gap-2",children:[e.jsx("input",{type:"date",value:d,onChange:t=>{g(t.target.value),r("CUSTOM")},className:"px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"}),e.jsx("span",{className:"text-slate-400 text-xs",children:"—"}),e.jsx("input",{type:"date",value:f,onChange:t=>{j(t.target.value),r("CUSTOM")},className:"px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"}),e.jsx("button",{onClick:a,className:"px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700",children:"Load"})]}),o&&e.jsx("span",{className:"text-xs text-slate-400 ml-auto",children:p()})]})}),y&&e.jsxs("div",{className:"flex items-center justify-center h-32 text-slate-400 text-sm",children:[e.jsx(me,{size:18,className:"animate-spin mr-2"})," ဖတ်နေသည်..."]}),!y&&o&&e.jsxs(e.Fragment,{children:[e.jsxs("div",{className:"grid grid-cols-2 lg:grid-cols-4 gap-3",children:[e.jsx(K,{label:"Total Income",value:`${u(b.totalIncome)} Ks`,sub:`Sales: ${u(b.netSaleRevenue)}`,colorClass:"bg-blue-50 border-blue-200 text-blue-800"}),e.jsx(K,{label:"Total Expenses",value:`${u(b.totalExpenses)} Ks`,sub:`Purchase: ${u(b.netPurchaseCost)}`,colorClass:"bg-rose-50 border-rose-200 text-rose-800"}),e.jsx(K,{label:"Net Profit",value:`${u(b.netProfit)} Ks`,sub:b.netProfit>=0?"Profit":"Loss",colorClass:b.netProfit>=0?"bg-emerald-50 border-emerald-200 text-emerald-800":"bg-orange-50 border-orange-200 text-orange-800"}),e.jsx(K,{label:"Sales Vouchers",value:String(o.sales.length),sub:`Service Jobs: ${o.serviceJobs.length}`,colorClass:"bg-violet-50 border-violet-200 text-violet-800"})]}),e.jsxs("div",{className:"grid grid-cols-1 sm:grid-cols-2 gap-4",children:[e.jsxs("div",{className:"bg-white rounded-xl border border-slate-200 p-4 space-y-2",children:[e.jsx("p",{className:"text-[10px] font-black uppercase tracking-widest text-slate-400",children:"Income Breakdown"}),[{label:"Net Sales Revenue",value:b.netSaleRevenue,icon:e.jsx(Me,{size:13,className:"text-indigo-500"})},{label:"Service Revenue",value:b.serviceRevenue,icon:e.jsx(ue,{size:13,className:"text-emerald-500"})},{label:"Other Income",value:b.otherIncome,icon:e.jsx(ge,{size:13,className:"text-amber-500"})}].map(t=>e.jsxs("div",{className:"flex items-center justify-between",children:[e.jsxs("div",{className:"flex items-center gap-1.5 text-xs text-slate-600",children:[t.icon,t.label]}),e.jsxs("span",{className:"text-xs font-bold text-slate-800",children:[u(t.value)," Ks"]})]},t.label)),e.jsxs("div",{className:"flex items-center justify-between border-t border-slate-100 pt-2",children:[e.jsx("span",{className:"text-xs font-black text-slate-700",children:"Total Income"}),e.jsxs("span",{className:"text-sm font-black text-blue-700",children:[u(b.totalIncome)," Ks"]})]})]}),e.jsxs("div",{className:"bg-white rounded-xl border border-slate-200 p-4 space-y-2",children:[e.jsx("p",{className:"text-[10px] font-black uppercase tracking-widest text-slate-400",children:"Expense Breakdown"}),[{label:"Net Purchase Cost",value:b.netPurchaseCost,icon:e.jsx(be,{size:13,className:"text-violet-500"})},{label:"Expenses",value:b.totalExpenses,icon:e.jsx(Be,{size:13,className:"text-rose-500"})}].map(t=>e.jsxs("div",{className:"flex items-center justify-between",children:[e.jsxs("div",{className:"flex items-center gap-1.5 text-xs text-slate-600",children:[t.icon,t.label]}),e.jsxs("span",{className:"text-xs font-bold text-slate-800",children:[u(t.value)," Ks"]})]},t.label)),e.jsxs("div",{className:"flex items-center justify-between border-t border-slate-100 pt-2",children:[e.jsx("span",{className:"text-xs font-black text-slate-700",children:"Net Profit"}),e.jsxs("span",{className:`text-sm font-black ${b.netProfit>=0?"text-emerald-700":"text-rose-700"}`,children:[u(b.netProfit)," Ks"]})]})]})]}),e.jsx(A,{icon:e.jsx(Je,{size:15}),title:"ရောင်းချမှုများ / Sales",count:o.sales.length,accentClass:"bg-indigo-700",children:e.jsxs("table",{className:"w-full min-w-[600px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Code"}),e.jsx(l,{children:"Customer"}),e.jsx(l,{children:"Staff"}),e.jsx(l,{children:"Date"}),e.jsx(l,{right:!0,children:"Amount (Ks)"}),e.jsx(l,{children:"Status"})]})}),e.jsx("tbody",{children:o.sales.length===0?e.jsx(P,{cols:7}):o.sales.map((t,n)=>e.jsxs("tr",{className:"hover:bg-slate-50",children:[e.jsx(i,{muted:!0,children:n+1}),e.jsx(i,{mono:!0,children:t.saleCode||`#${t.id}`}),e.jsx(i,{children:t.customerName||"—"}),e.jsx(i,{children:t.staffName||"—"}),e.jsx(i,{muted:!0,children:t.saleDate?new Date(t.saleDate).toLocaleDateString():"—"}),e.jsx(i,{right:!0,children:u(t.netAmount??t.totalAmount)}),e.jsx(i,{children:O(t.paymentStatus)})]},t.id))}),o.sales.length>0&&e.jsx("tfoot",{children:e.jsxs("tr",{className:"bg-indigo-50",children:[e.jsxs("td",{colSpan:5,className:"px-3 py-2 text-right text-xs font-black text-slate-600",children:["Total (",o.sales.length,")"]}),e.jsx("td",{className:"px-3 py-2 text-right text-sm font-black text-indigo-700",children:u(o.sales.reduce((t,n)=>t+(n.netAmount??n.totalAmount??0),0))}),e.jsx("td",{})]})})]})}),e.jsx(A,{icon:e.jsx(be,{size:15}),title:"ဝယ်ယူမှုများ / Purchases",count:o.purchases.length,accentClass:"bg-violet-700",children:e.jsxs("table",{className:"w-full min-w-[600px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Code"}),e.jsx(l,{children:"Supplier"}),e.jsx(l,{children:"Staff"}),e.jsx(l,{children:"Date"}),e.jsx(l,{right:!0,children:"Amount (Ks)"}),e.jsx(l,{children:"Status"})]})}),e.jsx("tbody",{children:o.purchases.length===0?e.jsx(P,{cols:7}):o.purchases.map((t,n)=>e.jsxs("tr",{className:"hover:bg-slate-50",children:[e.jsx(i,{muted:!0,children:n+1}),e.jsx(i,{mono:!0,children:t.purchaseCode||`#${t.id}`}),e.jsx(i,{children:t.supplierName||"—"}),e.jsx(i,{children:t.staffName||"—"}),e.jsx(i,{muted:!0,children:t.purchaseDate?new Date(t.purchaseDate).toLocaleDateString():"—"}),e.jsx(i,{right:!0,children:u(t.netAmount??t.totalAmount)}),e.jsx(i,{children:O(t.paymentStatus)})]},t.id))}),o.purchases.length>0&&e.jsx("tfoot",{children:e.jsxs("tr",{className:"bg-violet-50",children:[e.jsxs("td",{colSpan:5,className:"px-3 py-2 text-right text-xs font-black text-slate-600",children:["Total (",o.purchases.length,")"]}),e.jsx("td",{className:"px-3 py-2 text-right text-sm font-black text-violet-700",children:u(o.purchases.reduce((t,n)=>t+(n.netAmount??n.totalAmount??0),0))}),e.jsx("td",{})]})})]})}),e.jsx(A,{icon:e.jsx(ue,{size:15}),title:"ဝန်ဆောင်မှုလုပ်ငန်းများ / Service Jobs",count:o.serviceJobs.length,accentClass:"bg-emerald-700",children:e.jsxs("table",{className:"w-full min-w-[640px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Job No"}),e.jsx(l,{children:"Customer"}),e.jsx(l,{children:"Item"}),e.jsx(l,{children:"Staff"}),e.jsx(l,{children:"Status"}),e.jsx(l,{right:!0,children:"Amount (Ks)"})]})}),e.jsx("tbody",{children:o.serviceJobs.length===0?e.jsx(P,{cols:7}):o.serviceJobs.map((t,n)=>e.jsxs("tr",{className:"hover:bg-slate-50",children:[e.jsx(i,{muted:!0,children:n+1}),e.jsx(i,{mono:!0,children:t.jobNo||`#${t.id}`}),e.jsx(i,{children:t.customerName||"—"}),e.jsx(i,{children:t.itemName||"—"}),e.jsx(i,{children:t.assignedStaffName||"—"}),e.jsx(i,{children:O(t.status)}),e.jsx(i,{right:!0,children:u(t.netAmount??t.finalCost??0)})]},t.id))})]})}),e.jsx(A,{icon:e.jsx(Fe,{size:15}),title:"ပစ္စည်းလက်ခံ / Bookings",count:o.bookings.length,accentClass:"bg-sky-700",children:e.jsxs("table",{className:"w-full min-w-[680px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Booking No"}),e.jsx(l,{children:"Customer"}),e.jsx(l,{children:"Item / Device"}),e.jsx(l,{children:"Date In"}),e.jsx(l,{children:"Staff"}),e.jsx(l,{children:"Status"})]})}),e.jsx("tbody",{children:o.bookings.length===0?e.jsx(P,{cols:7}):o.bookings.map((t,n)=>e.jsxs("tr",{className:"hover:bg-slate-50",children:[e.jsx(i,{muted:!0,children:n+1}),e.jsx(i,{mono:!0,children:t.bookingNo||`#${t.id}`}),e.jsx(i,{children:t.customerName||"—"}),e.jsx(i,{children:t.itemName||t.deviceModel||"—"}),e.jsx(i,{muted:!0,children:t.receivedDate||t.bookingDate?new Date(t.receivedDate||t.bookingDate).toLocaleDateString():"—"}),e.jsx(i,{children:t.assignedStaffName||t.technicianName||"—"}),e.jsx(i,{children:O(t.status)})]},t.id))})]})}),e.jsx(A,{icon:e.jsx(ge,{size:15}),title:"ဝင်ငွေ / ထွက်ငွေ (Income & Expenses)",count:o.expenses.length+o.incomes.length,accentClass:"bg-amber-700",children:(()=>{const t=[...o.expenses.map(n=>({...n,_type:"Expense"})),...o.incomes.map(n=>({...n,_type:"Income"}))].sort((n,c)=>new Date(n.expenseDate||n.incomeDate||0).getTime()-new Date(c.expenseDate||c.incomeDate||0).getTime());return e.jsxs("table",{className:"w-full min-w-[560px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Code"}),e.jsx(l,{children:"Date"}),e.jsx(l,{children:"Description"}),e.jsx(l,{children:"Staff"}),e.jsx(l,{right:!0,children:"Amount (Ks)"}),e.jsx(l,{children:"Type"})]})}),e.jsx("tbody",{children:t.length===0?e.jsx(P,{cols:7}):t.map((n,c)=>{const v=n._type==="Expense";return e.jsxs("tr",{className:"hover:bg-slate-50",children:[e.jsx(i,{muted:!0,children:c+1}),e.jsx(i,{mono:!0,children:n.expenseCode||n.incomeCode||`#${n.id}`}),e.jsx(i,{muted:!0,children:n.expenseDate||n.incomeDate?new Date(n.expenseDate||n.incomeDate).toLocaleDateString():"—"}),e.jsx(i,{children:n.description||n.accountName||"—"}),e.jsx(i,{children:n.staffName||"—"}),e.jsxs("td",{className:`px-3 py-2 text-sm border-b border-slate-100 text-right font-bold ${v?"text-rose-600":"text-emerald-600"}`,children:[v?"−":"+",u(n.amount)]}),e.jsx(i,{children:e.jsx("span",{className:`inline-block px-2 py-0.5 rounded text-[10px] font-bold ${v?"bg-rose-100 text-rose-700":"bg-emerald-100 text-emerald-700"}`,children:n._type})})]},`${n._type}-${n.id}`)})})]})})()}),e.jsx(A,{icon:e.jsx(Ue,{size:15}),title:"ပစ္စည်းလက်ကျန် / Product Stock",count:o.products.length,accentClass:"bg-slate-700",children:e.jsxs("table",{className:"w-full min-w-[560px]",children:[e.jsx("thead",{children:e.jsxs("tr",{children:[e.jsx(l,{children:"#"}),e.jsx(l,{children:"Code"}),e.jsx(l,{children:"Product Name"}),e.jsx(l,{children:"Category"}),e.jsx(l,{right:!0,children:"Stock"}),e.jsx(l,{children:"Unit"}),e.jsx(l,{children:"Alert"})]})}),e.jsx("tbody",{children:o.products.length===0?e.jsx(P,{cols:7}):o.products.map((t,n)=>{const c=t.currentStock??t.stockQty??0,v=c<=(t.minStockLevel??0)&&(t.minStockLevel??0)>0;return e.jsxs("tr",{className:`hover:bg-slate-50 ${v?"bg-amber-50/60":""}`,children:[e.jsx(i,{muted:!0,children:n+1}),e.jsx(i,{mono:!0,children:t.productCode}),e.jsx(i,{children:t.name}),e.jsx(i,{muted:!0,children:t.categoryName||"—"}),e.jsx("td",{className:`px-3 py-2 text-sm border-b border-slate-100 text-right font-bold ${v?"text-amber-700":"text-slate-700"}`,children:c.toLocaleString()}),e.jsx(i,{muted:!0,children:t.unitName||"—"}),e.jsx(i,{children:v&&e.jsx("span",{className:"inline-block px-2 py-0.5 rounded text-[10px] font-black bg-amber-100 text-amber-700",children:"LOW"})})]},t.id)})})]})})]}),!y&&!o&&e.jsxs("div",{className:"flex flex-col items-center justify-center h-48 text-slate-400 gap-3",children:[e.jsx(pe,{size:36,className:"opacity-30"}),e.jsx("p",{className:"text-sm",children:'Period ရွေးပြီး "Load" နှိပ်ပါ'})]})]})};export{nt as default};
