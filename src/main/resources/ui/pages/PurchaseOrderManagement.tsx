import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { purchaseOrderApiService, type PurchaseOrderPage } from '../services/purchaseorderapiservice';
import { supplierService } from '../services/supplierapiservice';
import { staffService } from '../services/staffapiservice';
import { productService } from '../services/productapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { PurchaseOrderDTO, SupplierDTO, StaffDTO, ProductDTO, PaymentMethodDTO, PaymentTransactionDTO } from '../types';
import { Plus, Trash2, Save, X, RefreshCw, Eye, FileText, Search, Calendar, Loader2, Ban, PackageCheck } from 'lucide-react';
import Swal from 'sweetalert2';

type PODetailForm = {
  detailId?: number;
  productId: number;
  productSearch: string;
  qty: number;
  receivedQty: number;
  unitCost: number;
  subtotal: number;
};

const money = (n: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(n || 0);
const dateInput = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const statusBadge: Record<string, string> = {
  OPEN: 'bg-sky-100 text-sky-700 border border-sky-200',
  PARTIAL: 'bg-amber-100 text-amber-700 border border-amber-200',
  RECEIVED: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
  CANCELLED: 'bg-slate-200 text-slate-500 border border-slate-300 line-through'
};
const statusLabel: Record<string, string> = {
  OPEN: 'အော်ဒါဖွင့်',
  PARTIAL: 'တစ်ဝက်ရရှိ',
  RECEIVED: 'ရရှိပြီး',
  CANCELLED: 'ပယ်ဖျက်'
};

const emptyRow = (): PODetailForm => ({ productId: 0, productSearch: '', qty: 1, receivedQty: 0, unitCost: 0, subtotal: 0 });

const PurchaseOrderManagement: React.FC = () => {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<PurchaseOrderDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [viewOrder, setViewOrder] = useState<PurchaseOrderDTO | null>(null);

  // form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [suppliers, setSuppliers] = useState<SupplierDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [supplierId, setSupplierId] = useState(0);
  const [staffId, setStaffId] = useState(0);
  const [orderDate, setOrderDate] = useState(dateInput(new Date()));
  const [expectedDate, setExpectedDate] = useState('');
  const [remarkText, setRemarkText] = useState('');
  const [rows, setRows] = useState<PODetailForm[]>([emptyRow()]);
  const [saving, setSaving] = useState(false);

  // receive state
  const [receiveOrder, setReceiveOrder] = useState<PurchaseOrderDTO | null>(null);
  const [receiveLines, setReceiveLines] = useState<{ detailId: number; productName: string; pending: number; qty: number; unitCost: number; hasSerial: boolean }[]>([]);
  const [receivePaidAmount, setReceivePaidAmount] = useState<number>(0);
  const [receiveMethodId, setReceiveMethodId] = useState(0);
  const [receiving, setReceiving] = useState(false);

  const fetchOrders = useCallback(async (pg: number, size: number, search: string) => {
    setLoading(true);
    try {
      const result: PurchaseOrderPage = await purchaseOrderApiService.getAllPaged(pg, size, search);
      setOrders(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
    } catch (e) {
      console.error('Failed to load purchase orders', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const t = setTimeout(() => { setPage(0); setDebouncedSearch(searchTerm.trim()); }, 400);
    return () => clearTimeout(t);
  }, [searchTerm]);

  useEffect(() => { fetchOrders(page, pageSize, debouncedSearch); }, [fetchOrders, page, pageSize, debouncedSearch]);

  useEffect(() => {
    (async () => {
      try {
        const [supRes, staffRes, prodRes, payRes] = await Promise.all([
          supplierService.getAll(),
          staffService.getAll(),
          productService.getAll(),
          paymentMethodService.getAllActive()
        ]);
        setSuppliers(supRes);
        setStaffs(staffRes);
        setProducts(prodRes);
        setPaymentMethods(payRes);
        const me = JSON.parse(sessionStorage.getItem('sspd_user') || '{}') as { staffId?: number };
        const linked = staffRes.find((s) => s.id === me.staffId);
        setStaffId((prev) => prev || linked?.id || staffRes[0]?.id || 0);
      } catch (e) {
        console.error('Failed to load master data', e);
      }
    })();
  }, []);

  const totalAmount = rows.reduce((sum, r) => sum + r.subtotal, 0);
  const formValid = supplierId > 0 && staffId > 0 && rows.every((r) => r.productId > 0 && r.qty > 0 && r.unitCost >= 0);

  const handleOpenForm = () => {
    setEditingId(null);
    setSupplierId(0);
    setStaffId(staffId);
    setOrderDate(dateInput(new Date()));
    setExpectedDate('');
    setRemarkText('');
    setRows([emptyRow()]);
    setShowForm(true);
  };

  const handleEdit = (po: PurchaseOrderDTO) => {
    if ((po.status || 'OPEN').toUpperCase() !== 'OPEN') {
      Swal.fire({ icon: 'warning', title: 'ပြင်ဆင်မရပါ', text: 'OPEN အခြေအနေရှိမှ ပြင်ဆင်နိုင်ပါသည်။' });
      return;
    }
    setEditingId(po.id!);
    setSupplierId(po.supplierId);
    setStaffId(po.staffId);
    setOrderDate(po.orderDate ? po.orderDate.slice(0, 10) : dateInput(new Date()));
    setExpectedDate(po.expectedDate ? po.expectedDate.slice(0, 10) : '');
    setRemarkText(po.remark || '');
    setRows((po.details || []).map((d) => ({
      detailId: d.id,
      productId: d.productId,
      productSearch: d.productName ? `${d.productName}` : '',
      qty: d.qty,
      receivedQty: d.receivedQty || 0,
      unitCost: d.unitCost,
      subtotal: d.subtotal
    })));
    setShowForm(true);
  };

  const handleRowChange = (idx: number, patch: Partial<PODetailForm>) => {
    setRows((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], ...patch };
      next[idx].subtotal = (Number(next[idx].qty) || 0) * (Number(next[idx].unitCost) || 0);
      return next;
    });
  };

  const handleSave = async () => {
    if (!formValid || saving) return;
    setSaving(true);
    try {
      const dto: PurchaseOrderDTO = {
        id: editingId ?? undefined,
        supplierId,
        staffId,
        orderDate: `${orderDate}T00:00:00`,
        expectedDate: expectedDate ? `${expectedDate}T00:00:00` : undefined,
        remark: remarkText.trim() || undefined,
        totalAmount,
        details: rows.map((r) => ({
          id: r.detailId,
          productId: r.productId,
          qty: Number(r.qty),
          unitCost: Number(r.unitCost),
          subtotal: r.subtotal
        }))
      };
      if (editingId) await purchaseOrderApiService.update(editingId, dto);
      else await purchaseOrderApiService.create(dto);
      Swal.fire({ icon: 'success', title: editingId ? 'Update ပြီး' : 'အော်ဒါ ဖန်တီးပြီး', timer: 1600, showConfirmButton: false });
      setShowForm(false);
      fetchOrders(page, pageSize, debouncedSearch);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to save purchase order' });
    } finally {
      setSaving(false);
    }
  };

  const handleCancelPO = async (po: PurchaseOrderDTO) => {
    const res = await Swal.fire({
      icon: 'warning', title: 'အော်ဒါ ပယ်ဖျက်မည်လား?',
      html: `<b>${po.poCode || `#${po.id}`}</b>`,
      showCancelButton: true, confirmButtonText: 'ပယ်ဖျက်', cancelButtonText: 'မလုပ်တော့', confirmButtonColor: '#dc2626'
    });
    if (!res.isConfirmed) return;
    try {
      await purchaseOrderApiService.cancel(po.id!);
      Swal.fire({ icon: 'success', title: 'ပယ်ဖျက်ပြီး', timer: 1400, showConfirmButton: false });
      fetchOrders(page, pageSize, debouncedSearch);
      if (viewOrder?.id === po.id) setViewOrder(null);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to cancel order' });
    }
  };

  const openReceive = async (po: PurchaseOrderDTO) => {
    try {
      const full = await purchaseOrderApiService.getById(po.id!);
      const lines = (full.details || []).map((d) => {
        const prod = products.find((p) => p.id === d.productId);
        return {
          detailId: d.id!,
          productName: d.productName || prod?.name || `#${d.productId}`,
          pending: Math.max(0, d.qty - (d.receivedQty || 0)),
          qty: Math.max(0, d.qty - (d.receivedQty || 0)),
          unitCost: d.unitCost,
          hasSerial: prod ? prod.hasSerial !== false : true
        };
      }).filter((l) => l.pending > 0);
      if (lines.length === 0) {
        Swal.fire({ icon: 'info', title: 'ပြီးစီးပြီး', text: 'ဤအော်ဒါ၏ ပစ္စည်းအားလုံး လက်ခံရရှိပြီးဖြစ်သည်။' });
        return;
      }
      setReceiveLines(lines.map((l) => ({ ...l, qty: l.pending })));
      setReceivePaidAmount(0);
      setReceiveMethodId(0);
      setReceiveOrder(full);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to load order' });
    }
  };

  const receiveTotal = receiveLines.reduce((s, l) => s + Math.max(0, Number(l.qty) || 0) * Number(l.unitCost || 0), 0);
  const serialMissing = receiveLines.some((l) => l.hasSerial && l.qty > 0);

  const handleReceive = async () => {
    if (!receiveOrder || receiving) return;
    if (serialMissing) {
      Swal.fire({
        icon: 'warning', title: 'Serial လိုအပ်သည်',
        text: 'Serial ပစ္စည်းများပါသော အော်ဒါကို Purchase စာမျက်နှာမှ Serial များနှင့်အတူ ဝယ်ယူမှုသိမ်းရန် လိုအပ်သည်။ (ဒီ modal မှ serial-less products သာ လက်ခံနိုင်သည်)'
      });
      return;
    }
    const lines = receiveLines.filter((l) => Number(l.qty) > 0).map((l) => ({
      detailId: l.detailId,
      qty: Math.floor(Number(l.qty))
    }));
    if (lines.length === 0) {
      Swal.fire({ icon: 'warning', title: 'Quantities ထည့်ပါ', text: 'လက်ခံရရှိမည့် အရေအတွက်ကို ထည့်ပါ။' });
      return;
    }
    const payments: PaymentTransactionDTO[] =
      receivePaidAmount > 0 && receiveMethodId > 0
        ? [{ paymentMethodId: receiveMethodId, amount: receivePaidAmount }]
        : [];
    setReceiving(true);
    try {
      const created = await purchaseOrderApiService.receive(receiveOrder.id!, {
        staffId,
        lines,
        dueDate: undefined,
        discountAmount: 0,
        taxAmount: 0,
        otherCharges: 0,
        remark: `Received from PO ${receiveOrder.poCode || receiveOrder.id}`,
        paymentMethodId: payments.length > 0 ? payments[0].paymentMethodId : undefined,
        transactionNo: undefined,
        payments: payments.length > 0 ? payments : undefined
      });
      Swal.fire({
        icon: 'success', title: 'Goods Received!',
        html: `Voucher <b>${created?.purchase?.purchaseCode || `#${created?.purchase?.id ?? '-'}`}</b> ဖန်တီးပြီး။ Stock + Journal update ဖြစ်သည်။`
      });
      setReceiveOrder(null);
      fetchOrders(page, pageSize, debouncedSearch);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Receive failed', text: e.message || 'Failed to receive goods' });
    } finally {
      setReceiving(false);
    }
  };

  return (
    <div className="w-full max-w-none space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-black text-slate-800"><FileText size={22} className="text-indigo-500" /> အဝယ်အော်ဒါ (Purchase Orders)</h2>
          <p className="text-xs text-slate-500 mt-0.5">Supplier ဆီ အရင်မှာယူ — ပစ္စည်းရောက်မှ ဝယ်ယူမှုဘောင်ချာ ဖန်တီးမည်</p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => fetchOrders(page, pageSize, debouncedSearch)} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> ပြန်ဖတ်ရန်
          </button>
          <button onClick={handleOpenForm} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-bold text-white hover:bg-indigo-700">
            <Plus size={16} /> အော်ဒါအသစ်
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="PO Code / Supplier ရှာပါ..."
            className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm font-medium outline-none focus:border-indigo-400 focus:bg-white"
          />
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-auto max-h-[62vh] custom-scrollbar">
          {loading ? (
            <div className="p-10 text-center text-slate-400">ဖတ်နေသည်...</div>
          ) : (
            <table className="w-full min-w-[900px] table-auto text-sm">
              <thead className="sticky top-0 z-10 bg-slate-50">
                <tr className="text-[11px] font-bold uppercase tracking-wide text-slate-500">
                  <th className="px-4 py-3 text-left">PO No</th>
                  <th className="px-4 py-3 text-left">ပေးသွင်းသူ</th>
                  <th className="px-4 py-3 text-left">အော်ဒါရက်</th>
                  <th className="px-4 py-3 text-left">ရောက်မည့်ရက်</th>
                  <th className="px-4 py-3 text-right">စုစုပေါင်း</th>
                  <th className="px-4 py-3 text-center">အခြေအနေ</th>
                  <th className="px-4 py-3 text-right">လုပ်ဆောင်ချက်</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.length === 0 ? (
                  <tr><td colSpan={7} className="px-4 py-10 text-center text-slate-400">အဝယ်အော်ဒါ မရှိသေးပါ။</td></tr>
                ) : orders.map((po) => {
                  const st = (po.status || 'OPEN').toUpperCase();
                  return (
                    <tr key={po.id} className={`hover:bg-slate-50/80 ${st === 'CANCELLED' ? 'opacity-60' : ''}`}>
                      <td className="px-4 py-3 font-mono text-xs font-bold text-slate-800">{po.poCode || `#${po.id}`}</td>
                      <td className="px-4 py-3 font-semibold text-slate-700">{po.supplierName || '-'}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">{po.orderDate ? new Date(po.orderDate).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">{po.expectedDate ? new Date(po.expectedDate).toLocaleDateString() : '-'}</td>
                      <td className="px-4 py-3 text-right font-semibold tabular-nums">{money(po.totalAmount)}</td>
                      <td className="px-4 py-3 text-center">
                        <span className={`inline-flex min-w-[86px] justify-center rounded-md px-2.5 py-1 text-[10px] font-black ${statusBadge[st] || ''}`}>{statusLabel[st] || st}</span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="inline-flex items-center justify-end gap-1.5 whitespace-nowrap">
                          {(st === 'OPEN' || st === 'PARTIAL') && (
                            <button onClick={() => openReceive(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-emerald-200 bg-emerald-600 px-2.5 text-xs font-bold text-white hover:bg-emerald-700" title="ပစ္စည်းလက်ခံမည်">
                              <PackageCheck size={14} /> Receive
                            </button>
                          )}
                          <button onClick={() => setViewOrder(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-indigo-200 px-2.5 text-xs font-bold text-indigo-700 hover:bg-indigo-50">
                            <Eye size={14} /> ကြည့်မည်
                          </button>
                          {st === 'OPEN' && (
                            <>
                              <button onClick={() => handleEdit(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-amber-200 px-2.5 text-xs font-bold text-amber-700 hover:bg-amber-50">ပြင်</button>
                              <button onClick={() => handleCancelPO(po)} className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-300 px-2.5 text-xs font-bold text-slate-500 hover:bg-slate-100"><Ban size={14} /></button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
        {!loading && totalPages > 0 && (
          <div className="flex items-center justify-between gap-3 border-t border-slate-100 px-4 py-3">
            <p className="text-xs text-slate-500">{page * pageSize + 1} မှ {Math.min((page + 1) * pageSize, totalElements)} / {totalElements.toLocaleString()} ခု</p>
            <div className="flex items-center gap-1.5">
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }} className="rounded-md border border-slate-200 px-2 py-1 text-xs">
                {[10, 20, 50, 100].map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
              <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="rounded-md border border-slate-200 px-3 py-1 text-xs font-bold disabled:opacity-40">နောက်ပြန်</button>
              <span className="text-xs font-bold text-slate-600">{page + 1}/{totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} className="rounded-md border border-slate-200 px-3 py-1 text-xs font-bold disabled:opacity-40">ရှေ့သို့</button>
            </div>
          </div>
        )}
      </div>

      {/* Create/Edit Form Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/60 p-4">
          <div className="w-full max-w-4xl rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Purchase Order</p>
                <h3 className="font-black text-slate-800 text-sm mt-0.5">{editingId ? 'အော်ဒါ ပြင်ဆင်' : 'အဝယ်အော်ဒါ အသစ်ဖန်တီး'}</h3>
              </div>
              <button onClick={() => setShowForm(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="space-y-4 p-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ပေးသွင်းသူ *</span>
                  <select value={supplierId} onChange={(e) => setSupplierId(Number(e.target.value))} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none">
                    <option value={0}>ရွေးပါ...</option>
                    {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}{s.phone ? ` (${s.phone})` : ''}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">တာဝန်ခံ *</span>
                  <select value={staffId} onChange={(e) => setStaffId(Number(e.target.value))} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none">
                    <option value={0}>ရွေးပါ...</option>
                    {staffs.filter((s) => s.active !== false).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5">
                  <span className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-slate-400"><Calendar size={11} /> အော်ဒါရက်</span>
                  <input type="date" value={orderDate} onChange={(e) => setOrderDate(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
                </label>
                <label className="space-y-1.5">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ရောက်မည့်ရက်</span>
                  <input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-2.5 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
                </label>
              </div>

              <div className="overflow-hidden rounded-xl border border-slate-200">
                <table className="w-full min-w-[680px] text-left text-sm">
                  <thead><tr className="bg-slate-50 text-[10px] font-black uppercase tracking-wide text-slate-500">
                    <th className="px-3 py-2.5">ပစ္စည်း</th>
                    <th className="px-3 py-2.5 w-24 text-right">အရေအတွက်</th>
                    <th className="px-3 py-2.5 w-32 text-right">ခန့်မှန်းဈေး</th>
                    <th className="px-3 py-2.5 w-32 text-right">စုစုပေါင်း</th>
                    <th className="w-12"></th>
                  </tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row, idx) => (
                      <tr key={idx}>
                        <td className="px-3 py-2">
                          <select
                            value={row.productId}
                            onChange={(e) => {
                              const pid = Number(e.target.value);
                              const prod = products.find((p) => p.id === pid);
                              handleRowChange(idx, { productId: pid, unitCost: prod ? Number(prod.costPrice ?? 0) || row.unitCost : row.unitCost });
                            }}
                            className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-xs focus:border-indigo-400 focus:outline-none"
                          >
                            <option value={0}>ပစ္စည်းရွေးပါ...</option>
                            {products.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.productCode})</option>)}
                          </select>
                        </td>
                        <td className="px-3 py-2">
                          <input type="number" min="1" value={row.qty || ''} onChange={(e) => handleRowChange(idx, { qty: parseInt(e.target.value) || 0 })} className="w-full rounded border border-slate-200 px-2 py-1.5 text-right text-xs focus:border-indigo-400 focus:outline-none" />
                        </td>
                        <td className="px-3 py-2">
                          <input type="number" min="0" step="0.01" value={row.unitCost || ''} onChange={(e) => handleRowChange(idx, { unitCost: parseFloat(e.target.value) || 0 })} placeholder="0.00" className="w-full rounded border border-slate-200 px-2 py-1.5 text-right text-xs focus:border-indigo-400 focus:outline-none" />
                        </td>
                        <td className="px-3 py-2 text-right font-bold tabular-nums text-slate-700">{money(row.subtotal)}</td>
                        <td className="px-3 py-2 text-center">
                          <button onClick={() => { if (rows.length > 1) setRows(rows.filter((_, i) => i !== idx)); }} className="rounded-md p-1.5 text-rose-500 hover:bg-rose-50"><Trash2 size={15} /></button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="border-t border-slate-100 p-2">
                  <button onClick={() => setRows([...rows, emptyRow()])} className="inline-flex items-center gap-1.5 rounded-lg border border-dashed border-indigo-300 px-3 py-1.5 text-xs font-bold text-indigo-600 hover:bg-indigo-50">
                    <Plus size={13} /> Row ထပ်ထည့်
                  </button>
                </div>
              </div>

              <label className="block space-y-1.5">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">မှတ်ချက်</span>
                <textarea value={remarkText} onChange={(e) => setRemarkText(e.target.value)} rows={2} placeholder="Optional notes..." className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm focus:border-indigo-400 focus:bg-white focus:outline-none" />
              </label>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3">
                <div className="text-sm"><span className="text-slate-500">စုစုပေါင်း:</span> <b className="ml-1 text-lg text-indigo-700">{money(totalAmount)}</b></div>
                <div className="flex gap-2">
                  <button onClick={() => setShowForm(false)} className="rounded-lg px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-100">မလုပ်တော့ပါ</button>
                  <button onClick={handleSave} disabled={!formValid || saving} className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-700 disabled:bg-slate-300 disabled:cursor-not-allowed">
                    <Save size={16} /> {saving ? 'သိမ်းနေသည်...' : editingId ? 'Update မည်' : 'သိမ်းမည်'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* View Modal */}
      {viewOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-slate-800">PO: {viewOrder.poCode || `#${viewOrder.id}`}</h3>
                <span className={`rounded-md px-2 py-0.5 text-[10px] font-black ${statusBadge[(viewOrder.status || 'OPEN').toUpperCase()] || ''}`}>
                  {statusLabel[(viewOrder.status || 'OPEN').toUpperCase()] || viewOrder.status}
                </span>
              </div>
              <button onClick={() => setViewOrder(null)} className="rounded-lg p-1.5 text-slate-400 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                <p className="text-slate-600"><span className="text-slate-500">ပေးသွင်းသူ:</span> {viewOrder.supplierName}</p>
                <p className="text-slate-600"><span className="text-slate-500">တာဝန်ခံ:</span> {viewOrder.staffName || '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">အော်ဒါရက်:</span> {viewOrder.orderDate ? new Date(viewOrder.orderDate).toLocaleDateString() : '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">ရောက်မည့်ရက်:</span> {viewOrder.expectedDate ? new Date(viewOrder.expectedDate).toLocaleDateString() : '-'}</p>
                <p className="text-slate-600"><span className="text-slate-500">စုစုပေါင်း:</span> <b>{money(viewOrder.totalAmount)}</b></p>
              </div>
              {viewOrder.remark && <p className="text-sm text-slate-600"><span className="text-slate-500">မှတ်ချက်:</span> {viewOrder.remark}</p>}
              <table className="w-full text-left text-sm border-collapse">
                <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500">
                  <th className="border-b px-3 py-2">ပစ္စည်း</th>
                  <th className="border-b px-3 py-2 text-right">မှာယူ</th>
                  <th className="border-b px-3 py-2 text-right">ရရှိ</th>
                  <th className="border-b px-3 py-2 text-right">ဈေး</th>
                  <th className="border-b px-3 py-2 text-right">စုစုပေါင်း</th>
                </tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {(viewOrder.details || []).map((d, i) => (
                    <tr key={i}>
                      <td className="px-3 py-2">{d.productName || d.productId}</td>
                      <td className="px-3 py-2 text-right">{d.qty}</td>
                      <td className={`px-3 py-2 text-right font-bold ${(d.receivedQty || 0) >= d.qty ? 'text-emerald-600' : 'text-amber-600'}`}>{d.receivedQty || 0}</td>
                      <td className="px-3 py-2 text-right">{money(d.unitCost)}</td>
                      <td className="px-3 py-2 text-right font-medium">{money(d.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 p-4">
              {(viewOrder.status || 'OPEN').toUpperCase() === 'OPEN' && (
                <>
                  <button onClick={() => { handleCancelPO(viewOrder); }} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-100"><Ban size={13} /> ပယ်ဖျက်</button>
                  <button onClick={() => { handleEdit(viewOrder); setViewOrder(null); }} className="rounded-lg border border-amber-200 px-3 py-1.5 text-xs font-bold text-amber-700 hover:bg-amber-50">ပြင်ဆင်</button>
                </>
              )}
              {(viewOrder.status || 'OPEN').toUpperCase() !== 'RECEIVED' && (viewOrder.status || '').toUpperCase() !== 'CANCELLED' && (
                <button onClick={() => { openReceive(viewOrder); setViewOrder(null); }} className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-1.5 text-xs font-bold text-white hover:bg-emerald-700"><PackageCheck size={13} /> Receive</button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Receive Modal */}
      {receiveOrder && (() => {
        const paidAmt = Number(receivePaidAmount) || 0;
        const due = Math.max(0, receiveTotal - paidAmt);
        return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[92vh] w-full max-w-xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-emerald-500">Goods Receipt</p>
                <h3 className="flex items-center gap-2 text-sm font-black text-slate-800"><PackageCheck size={16} className="text-emerald-500" /> {receiveOrder.poCode || `#${receiveOrder.id}`} — ပစ္စည်းလက်ခံ</h3>
              </div>
              <button onClick={() => setReceiveOrder(null)} className="rounded-lg p-1.5 text-slate-400 hover:text-slate-700"><X size={18} /></button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <table className="w-full text-left text-sm">
                <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500">
                  <th className="border-b px-3 py-2">ပစ္စည်း</th>
                  <th className="border-b px-3 py-2 text-right">ကျန်</th>
                  <th className="border-b px-3 py-2 text-right">လက်ခံ</th>
                  <th className="border-b px-3 py-2 text-right">ဈေး</th>
                </tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {receiveLines.map((line, idx) => (
                    <tr key={line.detailId}>
                      <td className="px-3 py-2">
                        {line.productName}
                        {line.hasSerial && line.qty > 0 && (
                          <span className="ml-1.5 inline-block rounded bg-violet-100 px-1.5 py-0.5 text-[9px] font-black text-violet-700">SERIAL</span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-right text-slate-500">{line.pending}</td>
                      <td className="px-3 py-2 text-right">
                        <input
                          type="number"
                          min="0"
                          max={line.pending}
                          value={line.qty || ''}
                          onChange={(e) => setReceiveLines((prev) => prev.map((l, i) => i === idx ? { ...l, qty: Math.min(l.pending, Math.max(0, parseFloat(e.target.value) || 0)) } : l))}
                          className="w-20 rounded border border-slate-200 px-2 py-1 text-right text-xs font-bold focus:border-emerald-400 focus:outline-none"
                        />
                      </td>
                      <td className="px-3 py-2 text-right text-xs text-slate-600">{money(line.unitCost)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {serialMissing && (
                <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-semibold text-amber-700">
                  ⚠ Serial ပစ္စည်းပါသော line များကို ဤနေရာမှ လက်ခံ၍ မရပါ — Purchase စာမျက်နှာမှ Serial များနှင့်အတူ သိမ်းပါ။
                </p>
              )}

              <div className="space-y-3 rounded-xl border border-slate-100 bg-slate-50/60 p-3">
                <div className="flex items-center justify-between text-sm"><span className="text-slate-500">Receive စုစုပေါင်း</span><b className="text-indigo-700">{money(receiveTotal)}</b></div>
                <div className="grid grid-cols-2 gap-2">
                  <label className="space-y-1 block">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Payment Method</span>
                    <select value={receiveMethodId} onChange={(e) => setReceiveMethodId(Number(e.target.value))} className="w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs focus:border-indigo-400 focus:outline-none">
                      <option value={0}>Credit (အကြွေး)</option>
                      {paymentMethods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                    </select>
                  </label>
                  <label className="space-y-1 block">
                    <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Advance Paid</span>
                    <input type="number" min="0" max={receiveTotal || undefined} value={receivePaidAmount || ''} onChange={(e) => setReceivePaidAmount(Math.min(receiveTotal, Math.max(0, parseFloat(e.target.value) || 0)))} placeholder="0.00" className="w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs font-bold text-emerald-600 focus:border-indigo-400 focus:outline-none" />
                  </label>
                </div>
                <div className="flex items-center justify-between text-sm border-t border-slate-200 pt-2">
                  <span className="text-slate-500">ကျန်ငွေ (အကြွေး)</span>
                  <b className={due > 0 ? 'text-rose-600' : 'text-emerald-600'}>{money(due)}</b>
                </div>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2 border-t border-slate-100 p-4">
              <button onClick={() => setReceiveOrder(null)} className="rounded-lg px-4 py-2 text-sm font-bold text-slate-600 hover:bg-slate-100">မလုပ်တော့ပါ</button>
              <button onClick={handleReceive} disabled={receiving || serialMissing} className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-emerald-700 disabled:bg-slate-300 disabled:cursor-not-allowed">
                {receiving ? <Loader2 size={16} className="animate-spin" /> : <PackageCheck size={16} />}
                {receiving ? 'Processing...' : 'လက်ခံ + Voucher ဖန်တီး'}
              </button>
            </div>
          </div>
        </div>
        );
      })()}
    </div>
  );
};

export default PurchaseOrderManagement;
