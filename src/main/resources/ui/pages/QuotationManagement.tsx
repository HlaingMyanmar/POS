import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, CheckCircle2, Plus, Printer, RefreshCw, Save, Search, Send, Trash2, X } from 'lucide-react';
import Swal from 'sweetalert2';
import { useNavigate } from 'react-router-dom';
import { quotationApiService } from '../services/quotationapiservice';
import { customerService } from '../services/customerapiservice';
import { productService } from '../services/productapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { warehouseApiService, WarehouseDTO } from '../services/warehouseapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { AppRoute, CustomerDTO, PaymentMethodDTO, ProductDTO, ProductSerialDTO, QuotationDTO, SaleDetailDTO, SerialStatus } from '../types';
import { getFromSession } from '../utils/storageHelper';
import { buildQuotationVoucherHtml } from './quotationVoucherTemplate';

const money = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);
const plusDays = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d.toISOString().slice(0, 10);
};
const currentStaffId = () => {
  try {
    const user = JSON.parse(getFromSession('sspd_user') || '{}');
    return Number(user.staffId) || undefined;
  } catch {
    return undefined;
  }
};

type LineForm = SaleDetailDTO & { productSearch: string; serialNumbers: string[] };

const emptyLine = (): LineForm => ({ productId: 0, qty: 1, unitPrice: 0, discountAmount: 0, subtotal: 0, productSearch: '', serialNumbers: [] });

const statusClass = (status?: string) => {
  const s = (status || '').toUpperCase();
  if (s === 'DRAFT') return 'bg-slate-100 text-slate-700';
  if (s === 'SENT') return 'bg-sky-100 text-sky-700';
  if (s === 'ACCEPTED') return 'bg-emerald-100 text-emerald-700';
  if (s === 'CONVERTED_TO_SALE') return 'bg-indigo-100 text-indigo-700';
  if (s === 'REJECTED' || s === 'CANCELLED' || s === 'EXPIRED') return 'bg-rose-100 text-rose-700';
  return 'bg-slate-100 text-slate-600';
};

const QuotationManagement: React.FC = () => {
  const navigate = useNavigate();
  const [rows, setRows] = useState<QuotationDTO[]>([]);
  const [customers, setCustomers] = useState<CustomerDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [warehouses, setWarehouses] = useState<WarehouseDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [convertRow, setConvertRow] = useState<QuotationDTO | null>(null);

  const [customerId, setCustomerId] = useState(0);
  const [validUntil, setValidUntil] = useState(plusDays(30));
  const [discount, setDiscount] = useState('0');
  const [terms, setTerms] = useState('');
  const [remark, setRemark] = useState('');
  const [details, setDetails] = useState<LineForm[]>([emptyLine()]);

  const [paidAmount, setPaidAmount] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState(0);
  const [warehouseName, setWarehouseName] = useState('Main');
  const [convertSerials, setConvertSerials] = useState<Record<number, string>>({});
  const [availableSerials,setAvailableSerials]=useState<ProductSerialDTO[]>([]);

  useEffect(()=>{if(!convertRow)return;let active=true;productSerialService.getAll().then(list=>{if(!active)return;const available=(list||[]).filter(s=>s.status===SerialStatus.AVAILABLE);setAvailableSerials(available);const auto:Record<number,string>={};(convertRow.details||[]).forEach(d=>{const p=products.find(x=>x.id===d.productId);if(p?.hasSerial)auto[d.productId]=available.filter(s=>s.productId===d.productId).slice(0,Number(d.qty)||0).map(s=>s.serialNumber).join(',');});setConvertSerials(auto);}).catch(()=>{setAvailableSerials([]);setConvertSerials({});});return()=>{active=false;};},[convertRow,products]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [quotes, cus, prods, pay, wh] = await Promise.all([
        quotationApiService.getAll(),
        customerService.getAll(),
        productService.getAll(),
        paymentMethodService.getAllActive(),
        warehouseApiService.list(true).catch(() => [])
      ]);
      setRows(quotes || []);
      setCustomers(cus || []);
      setProducts(prods || []);
      setMethods(pay || []);
      setWarehouses(wh || []);
      if (wh?.length && !warehouseName) setWarehouseName(wh[0].name);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load quotations', 'error');
    } finally {
      setLoading(false);
    }
  }, [warehouseName]);

  useEffect(() => { load(); }, [load]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => [r.quotationCode, r.customerName, r.status].join(' ').toLowerCase().includes(q));
  }, [rows, search]);

  const lineTotal = details.reduce((s, d) => s + Math.max(0, (Number(d.qty) || 0) * (Number(d.unitPrice) || 0) - (Number(d.discountAmount) || 0)), 0);
  const disc = Number(discount) || 0;
  const net = Math.max(0, lineTotal - disc);

  const resetForm = () => {
    setEditingId(null);
    setCustomerId(0);
    setValidUntil(plusDays(30));
    setDiscount('0');
    setTerms('');
    setRemark('');
    setDetails([emptyLine()]);
  };

  const openCreate = () => { resetForm(); setShowForm(true); };
  const openEdit = (row: QuotationDTO) => {
    setEditingId(row.id || null);
    setCustomerId(row.customerId);
    setValidUntil((row.validUntil || plusDays(30)).slice(0, 10));
    setDiscount(String(row.discountAmount || 0));
    setTerms(row.terms || '');
    setRemark(row.remark || '');
    setDetails((row.details || []).map((d) => ({
      ...d,
      productSearch: d.productName || '',
      serialNumbers: d.serialNumbers || []
    })));
    setShowForm(true);
  };

  const save = async () => {
    if (!customerId) return Swal.fire('Validation', 'Customer is required', 'warning');
    if (details.some((d) => !d.productId || !d.qty || d.qty <= 0)) return Swal.fire('Validation', 'Each line needs a product and qty', 'warning');
    setSaving(true);
    try {
      const payload: QuotationDTO = {
        customerId,
        validUntil,
        discountAmount: disc,
        terms: terms.trim() || undefined,
        remark: remark.trim() || undefined,
        details: details.map((d) => ({
          productId: d.productId,
          qty: Number(d.qty),
          unitPrice: Number(d.unitPrice) || 0,
          discountAmount: Number(d.discountAmount) || 0,
          subtotal: Math.max(0, (Number(d.qty) || 0) * (Number(d.unitPrice) || 0) - (Number(d.discountAmount) || 0))
        }))
      };
      if (editingId) await quotationApiService.update(editingId, payload);
      else await quotationApiService.create(payload);
      setShowForm(false);
      resetForm();
      await load();
      Swal.fire({ icon: 'success', title: 'Quotation saved', toast: true, timer: 1200, position: 'top-end', showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save quotation', 'error');
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = async (id: number, status: string) => {
    try {
      await quotationApiService.changeStatus(id, status);
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to update status', 'error');
    }
  };

  const convert = async () => {
    if (!convertRow?.id) return;
    const serialDetails = (convertRow.details || []).map((d) => {
      const product = products.find((p) => p.id === d.productId);
      const raw = convertSerials[d.productId || 0] || '';
      const serials = raw.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean);
      return { ...d, serialNumbers: product?.hasSerial ? serials : [] };
    });
    if (serialDetails.some((d) => {
      const product = products.find((p) => p.id === d.productId);
      return product?.hasSerial && (d.serialNumbers || []).length !== Number(d.qty);
    })) {
      return Swal.fire('Validation', 'Serial count must match qty for serial products', 'warning');
    }
    setSaving(true);
    try {
      const paid = Number(paidAmount) || 0;
      const sale = await quotationApiService.convertToSale(convertRow.id, {
        customerId: convertRow.customerId,
        staffId: currentStaffId() || 0,
        paidAmount: paid,
        paymentMethodId: paid > 0 ? paymentMethodId : undefined,
        warehouseName: warehouseName || undefined,
        details: serialDetails
      });
      setConvertRow(null);
      await load();
      Swal.fire({ icon: 'success', title: `Converted to ${sale.saleCode || 'sale'}`, toast: true, timer: 1600, position: 'top-end', showConfirmButton: false });
      navigate(AppRoute.SALES);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to convert quotation', 'error');
    } finally {
      setSaving(false);
    }
  };

  if (showForm) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <button onClick={() => setShowForm(false)} className="inline-flex items-center gap-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg px-3 py-1.5">
            <ArrowLeft size={16} /> ပြန်မည်
          </button>
          <h2 className="text-xl font-bold text-slate-800">{editingId ? 'Quotation ပြင်ဆင်' : 'Quotation အသစ်'}</h2>
          <button onClick={save} disabled={saving} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60">
            <Save size={14} /> သိမ်းမည်
          </button>
        </div>
        <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600">ဖောက်သည်</label>
              <select value={customerId} onChange={(e) => setCustomerId(Number(e.target.value) || 0)} className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm">
                <option value={0}>- ရွေးပါ -</option>
                {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600">သက်တမ်းကုန်ရက်</label>
              <input type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600">Discount</label>
              <input type="number" min="0" step="0.01" value={discount} onChange={(e) => setDiscount(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="overflow-auto border border-slate-200 rounded-lg">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500 uppercase"><tr><th className="px-3 py-2 text-left">ပစ္စည်း</th><th className="px-3 py-2">Qty</th><th className="px-3 py-2">ဈေး</th><th className="px-3 py-2">Disc</th><th className="px-3 py-2"></th></tr></thead>
              <tbody>
                {details.map((d, idx) => (
                  <tr key={idx} className="border-t border-slate-100">
                    <td className="px-3 py-2">
                      <input value={d.productSearch} onChange={(e) => {
                        const next = [...details];
                        next[idx] = { ...d, productSearch: e.target.value };
                        setDetails(next);
                      }} placeholder="ပစ္စည်းရှာပါ" className="w-full rounded border border-slate-200 px-2 py-1.5 text-xs" />
                      {d.productSearch && (
                        <div className="mt-1 max-h-32 overflow-auto rounded border border-slate-200 bg-white">
                          {products.filter((p) => p.name.toLowerCase().includes(d.productSearch.toLowerCase()) || (p.productCode || '').toLowerCase().includes(d.productSearch.toLowerCase())).slice(0, 8).map((p) => (
                            <button key={p.id} type="button" className="block w-full px-2 py-1 text-left text-xs hover:bg-indigo-50" onClick={() => {
                              const next = [...details];
                              next[idx] = { ...d, productId: p.id, productSearch: p.name, unitPrice: Number(p.sellingPrice) || 0 };
                              setDetails(next);
                            }}>{p.name} · {money(Number(p.sellingPrice) || 0)}</button>
                          ))}
                        </div>
                      )}
                    </td>
                    <td className="px-3 py-2 w-24"><input type="number" min="1" value={d.qty} onChange={(e) => { const next = [...details]; next[idx] = { ...d, qty: Number(e.target.value) || 0 }; setDetails(next); }} className="w-full rounded border border-slate-200 px-2 py-1.5 text-xs" /></td>
                    <td className="px-3 py-2 w-28"><input type="number" min="0" step="0.01" value={d.unitPrice} onChange={(e) => { const next = [...details]; next[idx] = { ...d, unitPrice: Number(e.target.value) || 0 }; setDetails(next); }} className="w-full rounded border border-slate-200 px-2 py-1.5 text-xs" /></td>
                    <td className="px-3 py-2 w-24"><input type="number" min="0" step="0.01" value={d.discountAmount || 0} onChange={(e) => { const next = [...details]; next[idx] = { ...d, discountAmount: Number(e.target.value) || 0 }; setDetails(next); }} className="w-full rounded border border-slate-200 px-2 py-1.5 text-xs" /></td>
                    <td className="px-3 py-2 w-10"><button type="button" onClick={() => setDetails(details.filter((_, i) => i !== idx).length ? details.filter((_, i) => i !== idx) : [emptyLine()])}><Trash2 size={14} className="text-rose-500" /></button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="button" onClick={() => setDetails([...details, emptyLine()])} className="text-xs font-semibold text-indigo-700">+ လိုင်းထပ်ထည့်</button>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <textarea value={terms} onChange={(e) => setTerms(e.target.value)} rows={2} placeholder="Terms" className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm" />
            <textarea value={remark} onChange={(e) => setRemark(e.target.value)} rows={2} placeholder="Remark" className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm" />
          </div>
          <p className="text-right text-sm font-bold text-slate-800">Net {money(net)}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">ဈေးနှုန်းကမ်းလှမ်းချက်</h1>
          <p className="text-sm text-slate-500">Draft → Send → Accept → Sale အဖြစ်ပြောင်း</p>
        </div>
        <div className="flex gap-2">
          <button onClick={load} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh</button>
          <button onClick={openCreate} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white"><Plus size={14} /> အသစ်</button>
        </div>
      </div>
      <div className="relative">
        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="ရှာရန်" className="w-full rounded-lg border border-slate-200 bg-white py-2 pl-9 pr-3 text-sm" />
      </div>
      <div className="overflow-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[860px] text-sm">
          <thead className="bg-slate-50 text-xs uppercase text-slate-500">
            <tr><th className="px-3 py-2 text-left">Code</th><th className="px-3 py-2 text-left">Customer</th><th className="px-3 py-2 text-left">Valid</th><th className="px-3 py-2 text-right">Net</th><th className="px-3 py-2 text-left">Status</th><th className="px-3 py-2 text-right">Actions</th></tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {filtered.map((row) => (
              <tr key={row.id}>
                <td className="px-3 py-2 font-semibold">{row.quotationCode}</td>
                <td className="px-3 py-2">{row.customerName}</td>
                <td className="px-3 py-2">{row.validUntil || '-'}</td>
                <td className="px-3 py-2 text-right">{money(Number(row.netAmount) || 0)}</td>
                <td className="px-3 py-2"><span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${statusClass(row.status)}`}>{row.status}</span></td>
                <td className="px-3 py-2 text-right space-x-1">
                  <button className="rounded bg-indigo-50 px-2 py-1 text-[11px] font-semibold text-indigo-700" onClick={()=>{const {html,popupSize}=buildQuotationVoucherHtml(row);const w=window.open('','_blank',popupSize);if(w){w.document.write(html);w.document.close();}}}><Printer size={11} className="inline" /> Voucher</button>
                  {row.status === 'DRAFT' && <button className="rounded bg-slate-100 px-2 py-1 text-[11px] font-semibold" onClick={() => openEdit(row)}>Edit</button>}
                  {row.status === 'DRAFT' && <button className="rounded bg-sky-50 px-2 py-1 text-[11px] font-semibold text-sky-700" onClick={() => changeStatus(row.id!, 'SENT')}><Send size={11} className="inline" /> Send</button>}
                  {row.status === 'SENT' && <button className="rounded bg-emerald-50 px-2 py-1 text-[11px] font-semibold text-emerald-700" onClick={() => changeStatus(row.id!, 'ACCEPTED')}><CheckCircle2 size={11} className="inline" /> Accept</button>}
                  {row.status === 'ACCEPTED' && <button className="rounded bg-indigo-50 px-2 py-1 text-[11px] font-semibold text-indigo-700" onClick={() => { setConvertSerials({}); setAvailableSerials([]); setConvertRow(row); setPaidAmount(''); setPaymentMethodId(methods[0]?.id || 0); }}>Convert</button>}
                  {row.status === 'DRAFT' || row.status === 'SENT' ? <button className="rounded bg-rose-50 px-2 py-1 text-[11px] font-semibold text-rose-700" onClick={() => changeStatus(row.id!, 'CANCELLED')}>Cancel</button> : null}
                </td>
              </tr>
            ))}
            {!filtered.length && <tr><td colSpan={6} className="px-3 py-8 text-center text-slate-400">{loading ? 'Loading...' : 'No quotations'}</td></tr>}
          </tbody>
        </table>
      </div>

      {convertRow && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-lg rounded-xl bg-white p-5 shadow-xl">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="font-bold text-slate-800">Convert {convertRow.quotationCode}</h3>
              <button onClick={() => setConvertRow(null)}><X size={16} /></button>
            </div>
            <div className="space-y-3 text-sm">
              <div>
                <label className="text-xs font-semibold text-slate-600">Warehouse</label>
                <select value={warehouseName} onChange={(e) => setWarehouseName(e.target.value)} className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                  {(warehouses.length ? warehouses : [{ name: 'Main' } as WarehouseDTO]).map((w) => <option key={w.name} value={w.name}>{w.name}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <input type="number" min="0" step="0.01" value={paidAmount} onChange={(e) => setPaidAmount(e.target.value)} placeholder="Paid amount" className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2" />
                <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value) || 0)} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                  <option value={0}>Method</option>
                  {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                </select>
              </div>
              <div className="max-h-80 space-y-2 overflow-y-auto rounded-lg border border-slate-200 p-2">{(convertRow.details||[]).map(d=>{const product=products.find(p=>p.id===d.productId);const pool=availableSerials.filter(s=>s.productId===d.productId);const selected=(convertSerials[d.productId]||'').split(',').filter(Boolean);return <div key={d.productId} className="rounded-lg bg-slate-50 p-2"><div className="flex justify-between text-xs"><b>{d.productName||product?.name||`Item #${d.productId}`}</b><span>Qty {d.qty} · {money(d.unitPrice)}</span></div>{product?.hasSerial?<><select multiple value={selected} size={Math.min(4,Math.max(2,pool.length))} onChange={e=>{const values=Array.from(e.currentTarget.selectedOptions).map(o=>o.value).slice(0,Number(d.qty)||0);setConvertSerials(p=>({...p,[d.productId]:values.join(',')}));}} className="mt-2 w-full rounded border border-violet-200 bg-white px-2 py-1 font-mono text-xs">{pool.map(s=><option key={s.id} value={s.serialNumber}>{s.serialNumber}</option>)}</select><p className={`mt-1 text-[10px] ${selected.length===Number(d.qty)?'text-emerald-600':'text-rose-600'}`}>Auto selected {selected.length}/{d.qty} · Available {pool.length}</p></>:<p className="mt-1 text-[10px] text-slate-400">Non-serial item · Qty only</p>}</div>;})}</div>
              <button onClick={convert} disabled={saving} className="w-full rounded-lg bg-indigo-600 py-2 font-semibold text-white disabled:opacity-60">Sale အဖြစ်ပြောင်းမည်</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default QuotationManagement;
