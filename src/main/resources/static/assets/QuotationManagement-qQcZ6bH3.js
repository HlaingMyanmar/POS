import{u as be,r as o,j as e}from"./react-vendor-BRHkPwyY.js";import{S as b}from"./alerts-vendor-CH_YVA8w.js";import{f as N,j as ge,k as fe,S as ye,A as ve,i as Ne}from"./index-B9qo04FS.js";import{c as je}from"./customerapiservice-BFr5ZXTh.js";import{p as we}from"./productapiservice-DDXTvzxd.js";import{p as Se}from"./paymentmethodapiservice-BYkpCE0x.js";import{p as Ce}from"./productserialapiservice-DQv47uQq.js";import{am as ke,ar as Ae,ag as Ie,a8 as $e,ai as Ee,ae as De,aC as Pe,aB as ze,ab as Te,X as Me}from"./icons-vendor-Cv2DV0ct.js";const w={getAll:async()=>(await N.get("/v1/quotations")).data??[],getById:async a=>(await N.get(`/v1/quotations/${a}`)).data,create:async a=>(await N.post("/v1/quotations",a)).data,update:async(a,i)=>(await N.put(`/v1/quotations/${a}`,i)).data,changeStatus:async(a,i)=>(await N.patch(`/v1/quotations/${a}/status?status=${encodeURIComponent(i)}`)).data,convertToSale:async(a,i)=>(await N.post(`/v1/quotations/${a}/convert-to-sale`,i||{})).data},m=a=>String(a??"").replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;"),f=a=>new Intl.NumberFormat("en-US",{minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(a)||0),O=a=>{if(!a)return"-";const i=new Date(a);return Number.isNaN(i.getTime())?a:i.toLocaleDateString("en-GB",{day:"2-digit",month:"short",year:"numeric"})},qe=a=>{const i=fe(),y=i.companyName||"Company",S=Number(a.totalAmount)||0,j=Number(a.discountAmount)||0,g=a.netAmount==null?Math.max(0,S-j):Number(a.netAmount)||0,D=(a.details||[]).map((u,C)=>`
    <tr>
      <td class="cell-center cell-muted">${C+1}</td>
      <td class="item-cell">${m(u.productName||`Product #${u.productId}`)}</td>
      <td class="cell-number">${f(u.qty)}</td>
      <td class="cell-number">${f(u.unitPrice)}</td>
      <td class="cell-number cell-discount">${Number(u.discountAmount)>0?`- ${f(u.discountAmount)}`:"-"}</td>
      <td class="cell-number cell-amount">${f(u.subtotal)}</td>
    </tr>
  `).join("");return{html:`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>${m(a.quotationCode||"Quotation")}</title>
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
        ${i.logoBase64?`<img class="logo" src="${m(i.logoBase64)}" alt="Company logo" />`:""}
        <div><h1 class="company-name">${m(y)}</h1><div class="company-contact">${m(ge(i))}</div></div>
      </div>
      <div class="document">
        <div class="document-label">Official Price Proposal</div>
        <div class="document-title">QUOTATION</div>
        <div class="document-title-mm">ဈေးနှုန်းကမ်းလှမ်းလွှာ</div>
        <div class="document-code">${m(a.quotationCode||`#${a.id||"-"}`)}</div>
      </div>
    </header>
    <section class="details">
      <div class="detail-card"><div class="section-label">Quotation For / သို့</div><div class="customer-name">${m(a.customerName||"Walk-in Customer")}</div></div>
      <div class="detail-card">
        <div class="meta-row"><span class="meta-key">Issue Date</span><span class="meta-value">${O(a.quotationDate)}</span></div>
        <div class="meta-row"><span class="meta-key">Valid Until</span><span class="meta-value">${O(a.validUntil)}</span></div>
        <div class="meta-row"><span class="meta-key">Status</span><span class="meta-value status">${m(a.status||"Draft")}</span></div>
      </div>
    </section>
    <section class="items"><table><thead><tr><th style="width:6%">#</th><th class="item-heading" style="width:37%">Description</th><th style="width:10%">Qty</th><th style="width:17%">Unit Price</th><th style="width:13%">Discount</th><th style="width:17%">Amount</th></tr></thead><tbody>${D||'<tr><td colspan="6" class="cell-center cell-muted" style="padding:22px">No quotation items</td></tr>'}</tbody></table></section>
    <section class="summary-wrap"><div class="summary">
      <div class="summary-row"><span class="summary-label">Subtotal</span><span class="summary-value">${f(S)} MMK</span></div>
      <div class="summary-row"><span class="summary-label">Discount</span><span class="summary-value">${j>0?`- ${f(j)}`:f(0)} MMK</span></div>
      <div class="summary-row summary-total"><span class="summary-label">Net Amount</span><span class="summary-value">${f(g)} MMK</span></div>
    </div></section>
    ${a.terms||a.remark?`<section class="notes">${a.terms?`<div class="note"><div class="section-label">Terms &amp; Conditions</div><div class="note-text">${m(a.terms)}</div></div>`:""}${a.remark?`<div class="note"><div class="section-label">Remark</div><div class="note-text">${m(a.remark)}</div></div>`:""}</section>`:""}
    <section class="signatures"><div class="signature">Prepared By / ပြင်ဆင်သူ</div><div class="signature">Customer Acceptance / ဝယ်ယူသူ အတည်ပြုချက်</div></section>
    <footer class="footer">${m(y)} &nbsp;•&nbsp; ${m(i.footerNote||"Thank you for your business")} &nbsp;•&nbsp; Valid until ${O(a.validUntil)}</footer>
  </main>
</body></html>`,popupSize:"width=980,height=900"}},$=a=>new Intl.NumberFormat("en-US",{minimumFractionDigits:2,maximumFractionDigits:2}).format(a||0),_=a=>{const i=new Date;return i.setDate(i.getDate()+a),i.toISOString().slice(0,10)},Fe=()=>{try{const a=JSON.parse(Ne("sspd_user")||"{}");return Number(a.staffId)||void 0}catch{return}},E=()=>({productId:0,qty:1,unitPrice:0,discountAmount:0,subtotal:0,productSearch:"",serialNumbers:[]}),Le=a=>{const i=(a||"").toUpperCase();return i==="DRAFT"?"bg-slate-100 text-slate-700":i==="SENT"?"bg-sky-100 text-sky-700":i==="ACCEPTED"?"bg-emerald-100 text-emerald-700":i==="CONVERTED_TO_SALE"?"bg-indigo-100 text-indigo-700":i==="REJECTED"||i==="CANCELLED"||i==="EXPIRED"?"bg-rose-100 text-rose-700":"bg-slate-100 text-slate-600"},He=()=>{const a=be(),[i,y]=o.useState([]),[S,j]=o.useState([]),[g,D]=o.useState([]),[P,u]=o.useState([]),[C,K]=o.useState(!0),[H,k]=o.useState(!1),[z,oe]=o.useState(""),[le,A]=o.useState(!1),[T,J]=o.useState(null),[d,M]=o.useState(null),[q,F]=o.useState(0),[X,L]=o.useState(_(30)),[G,R]=o.useState("0"),[W,U]=o.useState(""),[Y,Q]=o.useState(""),[c,p]=o.useState([E()]),[Z,ee]=o.useState(""),[te,se]=o.useState(0),[ae,I]=o.useState({}),[de,B]=o.useState([]);o.useEffect(()=>{if(!d)return;let t=!0;return Ce.getAll().then(s=>{if(!t)return;const r=(s||[]).filter(l=>l.status===ye.AVAILABLE);B(r);const n={};(d.details||[]).forEach(l=>{const x=g.find(h=>h.id===l.productId);x!=null&&x.hasSerial&&(n[l.productId]=r.filter(h=>h.productId===l.productId).slice(0,Number(l.qty)||0).map(h=>h.serialNumber).join(","))}),I(n)}).catch(()=>{B([]),I({})}),()=>{t=!1}},[d,g]);const v=o.useCallback(async()=>{K(!0);try{const[t,s,r,n]=await Promise.all([w.getAll(),je.getAll(),we.getAll(),Se.getAllActive()]);y(t||[]),j(s||[]),D(r||[]),u(n||[])}catch(t){b.fire("Error",(t==null?void 0:t.message)||"Failed to load quotations","error")}finally{K(!1)}},[]);o.useEffect(()=>{v()},[v]);const re=o.useMemo(()=>{const t=z.trim().toLowerCase();return t?i.filter(s=>[s.quotationCode,s.customerName,s.status].join(" ").toLowerCase().includes(t)):i},[i,z]),ce=c.reduce((t,s)=>t+Math.max(0,(Number(s.qty)||0)*(Number(s.unitPrice)||0)-(Number(s.discountAmount)||0)),0),ne=Number(G)||0,me=Math.max(0,ce-ne),ie=()=>{J(null),F(0),L(_(30)),R("0"),U(""),Q(""),p([E()])},ue=()=>{ie(),A(!0)},pe=t=>{J(t.id||null),F(t.customerId),L((t.validUntil||_(30)).slice(0,10)),R(String(t.discountAmount||0)),U(t.terms||""),Q(t.remark||""),p((t.details||[]).map(s=>({...s,productSearch:s.productName||"",serialNumbers:s.serialNumbers||[]}))),A(!0)},xe=async()=>{if(!q)return b.fire("Validation","Customer is required","warning");if(c.some(t=>!t.productId||!t.qty||t.qty<=0))return b.fire("Validation","Each line needs a product and qty","warning");k(!0);try{const t={customerId:q,validUntil:X,discountAmount:ne,terms:W.trim()||void 0,remark:Y.trim()||void 0,details:c.map(s=>({productId:s.productId,qty:Number(s.qty),unitPrice:Number(s.unitPrice)||0,discountAmount:Number(s.discountAmount)||0,subtotal:Math.max(0,(Number(s.qty)||0)*(Number(s.unitPrice)||0)-(Number(s.discountAmount)||0))}))};T?await w.update(T,t):await w.create(t),A(!1),ie(),await v(),b.fire({icon:"success",title:"Quotation saved",toast:!0,timer:1200,position:"top-end",showConfirmButton:!1})}catch(t){b.fire("Error",(t==null?void 0:t.message)||"Failed to save quotation","error")}finally{k(!1)}},V=async(t,s)=>{try{await w.changeStatus(t,s),await v()}catch(r){b.fire("Error",(r==null?void 0:r.message)||"Failed to update status","error")}},he=async()=>{if(!(d!=null&&d.id))return;const t=(d.details||[]).map(s=>{const r=g.find(x=>x.id===s.productId),l=(ae[s.productId||0]||"").split(",").map(x=>x.trim().toUpperCase()).filter(Boolean);return{...s,serialNumbers:r!=null&&r.hasSerial?l:[]}});if(t.some(s=>{const r=g.find(n=>n.id===s.productId);return(r==null?void 0:r.hasSerial)&&(s.serialNumbers||[]).length!==Number(s.qty)}))return b.fire("Validation","Serial count must match qty for serial products","warning");k(!0);try{const s=Number(Z)||0,r=await w.convertToSale(d.id,{customerId:d.customerId,staffId:Fe()||0,paidAmount:s,paymentMethodId:s>0?te:void 0,warehouseName:"Main",details:t});M(null),await v(),b.fire({icon:"success",title:`Converted to ${r.saleCode||"sale"}`,toast:!0,timer:1600,position:"top-end",showConfirmButton:!1}),a(ve.SALES)}catch(s){b.fire("Error",(s==null?void 0:s.message)||"Failed to convert quotation","error")}finally{k(!1)}};return le?e.jsxs("div",{className:"space-y-4",children:[e.jsxs("div",{className:"flex items-center justify-between",children:[e.jsxs("button",{onClick:()=>A(!1),className:"inline-flex items-center gap-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg px-3 py-1.5",children:[e.jsx(ke,{size:16})," ပြန်မည်"]}),e.jsx("h2",{className:"text-xl font-bold text-slate-800",children:T?"Quotation ပြင်ဆင်":"Quotation အသစ်"}),e.jsxs("button",{onClick:xe,disabled:H,className:"inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60",children:[e.jsx(Ae,{size:14})," သိမ်းမည်"]})]}),e.jsxs("div",{className:"bg-white rounded-xl border border-slate-200 p-5 space-y-4",children:[e.jsxs("div",{className:"grid grid-cols-1 md:grid-cols-3 gap-3",children:[e.jsxs("div",{children:[e.jsx("label",{className:"text-xs font-semibold text-slate-600",children:"ဖောက်သည်"}),e.jsxs("select",{value:q,onChange:t=>F(Number(t.target.value)||0),className:"mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm",children:[e.jsx("option",{value:0,children:"- ရွေးပါ -"}),S.map(t=>e.jsx("option",{value:t.id,children:t.name},t.id))]})]}),e.jsxs("div",{children:[e.jsx("label",{className:"text-xs font-semibold text-slate-600",children:"သက်တမ်းကုန်ရက်"}),e.jsx("input",{type:"date",value:X,onChange:t=>L(t.target.value),className:"mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm"})]}),e.jsxs("div",{children:[e.jsx("label",{className:"text-xs font-semibold text-slate-600",children:"Discount"}),e.jsx("input",{type:"number",min:"0",step:"0.01",value:G,onChange:t=>R(t.target.value),className:"mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm"})]})]}),e.jsx("div",{className:"overflow-auto border border-slate-200 rounded-lg",children:e.jsxs("table",{className:"w-full text-sm",children:[e.jsx("thead",{className:"bg-slate-50 text-xs text-slate-500 uppercase",children:e.jsxs("tr",{children:[e.jsx("th",{className:"px-3 py-2 text-left",children:"ပစ္စည်း"}),e.jsx("th",{className:"px-3 py-2",children:"Qty"}),e.jsx("th",{className:"px-3 py-2",children:"ဈေး"}),e.jsx("th",{className:"px-3 py-2",children:"Disc"}),e.jsx("th",{className:"px-3 py-2"})]})}),e.jsx("tbody",{children:c.map((t,s)=>e.jsxs("tr",{className:"border-t border-slate-100",children:[e.jsxs("td",{className:"px-3 py-2",children:[e.jsx("input",{value:t.productSearch,onChange:r=>{const n=[...c];n[s]={...t,productSearch:r.target.value},p(n)},placeholder:"ပစ္စည်းရှာပါ",className:"w-full rounded border border-slate-200 px-2 py-1.5 text-xs"}),t.productSearch&&e.jsx("div",{className:"mt-1 max-h-32 overflow-auto rounded border border-slate-200 bg-white",children:g.filter(r=>r.name.toLowerCase().includes(t.productSearch.toLowerCase())||(r.productCode||"").toLowerCase().includes(t.productSearch.toLowerCase())).slice(0,8).map(r=>e.jsxs("button",{type:"button",className:"block w-full px-2 py-1 text-left text-xs hover:bg-indigo-50",onClick:()=>{const n=[...c];n[s]={...t,productId:r.id,productSearch:r.name,unitPrice:Number(r.sellingPrice)||0},p(n)},children:[r.name," · ",$(Number(r.sellingPrice)||0)]},r.id))})]}),e.jsx("td",{className:"px-3 py-2 w-24",children:e.jsx("input",{type:"number",min:"1",value:t.qty,onChange:r=>{const n=[...c];n[s]={...t,qty:Number(r.target.value)||0},p(n)},className:"w-full rounded border border-slate-200 px-2 py-1.5 text-xs"})}),e.jsx("td",{className:"px-3 py-2 w-28",children:e.jsx("input",{type:"number",min:"0",step:"0.01",value:t.unitPrice,onChange:r=>{const n=[...c];n[s]={...t,unitPrice:Number(r.target.value)||0},p(n)},className:"w-full rounded border border-slate-200 px-2 py-1.5 text-xs"})}),e.jsx("td",{className:"px-3 py-2 w-24",children:e.jsx("input",{type:"number",min:"0",step:"0.01",value:t.discountAmount||0,onChange:r=>{const n=[...c];n[s]={...t,discountAmount:Number(r.target.value)||0},p(n)},className:"w-full rounded border border-slate-200 px-2 py-1.5 text-xs"})}),e.jsx("td",{className:"px-3 py-2 w-10",children:e.jsx("button",{type:"button",onClick:()=>p(c.filter((r,n)=>n!==s).length?c.filter((r,n)=>n!==s):[E()]),children:e.jsx(Ie,{size:14,className:"text-rose-500"})})})]},s))})]})}),e.jsx("button",{type:"button",onClick:()=>p([...c,E()]),className:"text-xs font-semibold text-indigo-700",children:"+ လိုင်းထပ်ထည့်"}),e.jsxs("div",{className:"grid grid-cols-1 md:grid-cols-2 gap-3",children:[e.jsx("textarea",{value:W,onChange:t=>U(t.target.value),rows:2,placeholder:"Terms",className:"rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm"}),e.jsx("textarea",{value:Y,onChange:t=>Q(t.target.value),rows:2,placeholder:"Remark",className:"rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm"})]}),e.jsxs("p",{className:"text-right text-sm font-bold text-slate-800",children:["Net ",$(me)]})]})]}):e.jsxs("div",{className:"space-y-4",children:[e.jsxs("div",{className:"flex flex-wrap items-center justify-between gap-3",children:[e.jsxs("div",{children:[e.jsx("h1",{className:"text-2xl font-bold text-slate-800",children:"ဈေးနှုန်းကမ်းလှမ်းချက်"}),e.jsx("p",{className:"text-sm text-slate-500",children:"Draft → Send → Accept → Sale အဖြစ်ပြောင်း"})]}),e.jsxs("div",{className:"flex gap-2",children:[e.jsxs("button",{onClick:v,className:"inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600",children:[e.jsx($e,{size:14,className:C?"animate-spin":""})," Refresh"]}),e.jsxs("button",{onClick:ue,className:"inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white",children:[e.jsx(Ee,{size:14})," အသစ်"]})]})]}),e.jsxs("div",{className:"relative",children:[e.jsx(De,{size:14,className:"absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"}),e.jsx("input",{value:z,onChange:t=>oe(t.target.value),placeholder:"ရှာရန်",className:"w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm"})]}),e.jsx("div",{className:"overflow-auto rounded-xl border border-slate-200 bg-white",children:e.jsxs("table",{className:"w-full min-w-[860px] text-sm",children:[e.jsx("thead",{className:"bg-slate-50 text-xs uppercase text-slate-500",children:e.jsxs("tr",{children:[e.jsx("th",{className:"px-3 py-2 text-left",children:"Code"}),e.jsx("th",{className:"px-3 py-2 text-left",children:"Customer"}),e.jsx("th",{className:"px-3 py-2 text-left",children:"Valid"}),e.jsx("th",{className:"px-3 py-2 text-right",children:"Net"}),e.jsx("th",{className:"px-3 py-2 text-left",children:"Status"}),e.jsx("th",{className:"px-3 py-2 text-right",children:"Actions"})]})}),e.jsxs("tbody",{className:"divide-y divide-slate-100",children:[re.map(t=>e.jsxs("tr",{children:[e.jsx("td",{className:"px-3 py-2 font-semibold",children:t.quotationCode}),e.jsx("td",{className:"px-3 py-2",children:t.customerName}),e.jsx("td",{className:"px-3 py-2",children:t.validUntil||"-"}),e.jsx("td",{className:"px-3 py-2 text-right",children:$(Number(t.netAmount)||0)}),e.jsx("td",{className:"px-3 py-2",children:e.jsx("span",{className:`rounded-full px-2 py-0.5 text-[10px] font-bold ${Le(t.status)}`,children:t.status})}),e.jsxs("td",{className:"px-3 py-2 text-right space-x-1",children:[e.jsxs("button",{className:"rounded bg-indigo-50 px-2 py-1 text-[11px] font-semibold text-indigo-700",onClick:()=>{const{html:s,popupSize:r}=qe(t),n=window.open("","_blank",r);n&&(n.document.write(s),n.document.close())},children:[e.jsx(Pe,{size:11,className:"inline"})," Voucher"]}),t.status==="DRAFT"&&e.jsx("button",{className:"rounded bg-slate-100 px-2 py-1 text-[11px] font-semibold",onClick:()=>pe(t),children:"Edit"}),t.status==="DRAFT"&&e.jsxs("button",{className:"rounded bg-sky-50 px-2 py-1 text-[11px] font-semibold text-sky-700",onClick:()=>V(t.id,"SENT"),children:[e.jsx(ze,{size:11,className:"inline"})," Send"]}),t.status==="SENT"&&e.jsxs("button",{className:"rounded bg-emerald-50 px-2 py-1 text-[11px] font-semibold text-emerald-700",onClick:()=>V(t.id,"ACCEPTED"),children:[e.jsx(Te,{size:11,className:"inline"})," Accept"]}),t.status==="ACCEPTED"&&e.jsx("button",{className:"rounded bg-indigo-50 px-2 py-1 text-[11px] font-semibold text-indigo-700",onClick:()=>{var s;I({}),B([]),M(t),ee(""),se(((s=P[0])==null?void 0:s.id)||0)},children:"Convert"}),t.status==="DRAFT"||t.status==="SENT"?e.jsx("button",{className:"rounded bg-rose-50 px-2 py-1 text-[11px] font-semibold text-rose-700",onClick:()=>V(t.id,"CANCELLED"),children:"Cancel"}):null]})]},t.id)),!re.length&&e.jsx("tr",{children:e.jsx("td",{colSpan:6,className:"px-3 py-8 text-center text-slate-400",children:C?"Loading...":"No quotations"})})]})]})}),d&&e.jsx("div",{className:"fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4",children:e.jsxs("div",{className:"w-full max-w-lg rounded-xl bg-white p-5 shadow-xl",children:[e.jsxs("div",{className:"mb-3 flex items-center justify-between",children:[e.jsxs("h3",{className:"font-bold text-slate-800",children:["Convert ",d.quotationCode]}),e.jsx("button",{onClick:()=>M(null),children:e.jsx(Me,{size:16})})]}),e.jsxs("div",{className:"space-y-3 text-sm",children:[e.jsxs("div",{className:"grid grid-cols-2 gap-2",children:[e.jsx("input",{type:"number",min:"0",step:"0.01",value:Z,onChange:t=>ee(t.target.value),placeholder:"Paid amount",className:"rounded-lg border border-slate-200 bg-slate-50 px-3 py-2"}),e.jsxs("select",{value:te,onChange:t=>se(Number(t.target.value)||0),className:"rounded-lg border border-slate-200 bg-slate-50 px-3 py-2",children:[e.jsx("option",{value:0,children:"Method"}),P.map(t=>e.jsx("option",{value:t.id,children:t.methodName},t.id))]})]}),e.jsx("div",{className:"max-h-80 space-y-2 overflow-y-auto rounded-lg border border-slate-200 p-2",children:(d.details||[]).map(t=>{const s=g.find(l=>l.id===t.productId),r=de.filter(l=>l.productId===t.productId),n=(ae[t.productId]||"").split(",").filter(Boolean);return e.jsxs("div",{className:"rounded-lg bg-slate-50 p-2",children:[e.jsxs("div",{className:"flex justify-between text-xs",children:[e.jsx("b",{children:t.productName||(s==null?void 0:s.name)||`Item #${t.productId}`}),e.jsxs("span",{children:["Qty ",t.qty," · ",$(t.unitPrice)]})]}),s!=null&&s.hasSerial?e.jsxs(e.Fragment,{children:[e.jsx("select",{multiple:!0,value:n,size:Math.min(4,Math.max(2,r.length)),onChange:l=>{const x=Array.from(l.currentTarget.selectedOptions).map(h=>h.value).slice(0,Number(t.qty)||0);I(h=>({...h,[t.productId]:x.join(",")}))},className:"mt-2 w-full rounded border border-violet-200 bg-white px-2 py-1 font-mono text-xs",children:r.map(l=>e.jsx("option",{value:l.serialNumber,children:l.serialNumber},l.id))}),e.jsxs("p",{className:`mt-1 text-[10px] ${n.length===Number(t.qty)?"text-emerald-600":"text-rose-600"}`,children:["Auto selected ",n.length,"/",t.qty," · Available ",r.length]})]}):e.jsx("p",{className:"mt-1 text-[10px] text-slate-400",children:"Non-serial item · Qty only"})]},t.productId)})}),e.jsx("button",{onClick:he,disabled:H,className:"w-full rounded-lg bg-indigo-600 py-2 font-semibold text-white disabled:opacity-60",children:"Sale အဖြစ်ပြောင်းမည်"})]})]})})]})};export{He as default};
