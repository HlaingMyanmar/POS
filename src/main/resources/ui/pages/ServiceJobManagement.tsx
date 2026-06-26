import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { Printer, FileEdit, AlertTriangle } from 'lucide-react';
import { serviceJobService, serviceItemService } from '../services/api';
import { staffService } from '../services/staffapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { productService } from '../services/productapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { customerService } from '../services/customerapiservice';
import { creditTermService } from '../services/credittermapiservice';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { PaymentTransactionDTO } from '../types';
import Swal from 'sweetalert2';

/* ── Status config ─────────────────────────────────────────────── */
const STATUS_LIST = ['RECEIVED','INSPECTING','IN_PROGRESS','COMPLETED','DELIVERED','CANCELLED'] as const;
type JobStatus = typeof STATUS_LIST[number];

const STATUS_COLOR: Record<JobStatus, string> = {
  RECEIVED:    'bg-orange-100 text-orange-700',
  INSPECTING:  'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-purple-100 text-purple-700',
  COMPLETED:   'bg-emerald-100 text-emerald-700',
  DELIVERED:   'bg-green-100 text-green-700',
  CANCELLED:   'bg-red-100 text-red-700',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  RECEIVED:    'လက်ခံပြီး',
  INSPECTING:  'စစ်ဆေးနေ',
  IN_PROGRESS: 'ပြင်ဆင်နေ',
  COMPLETED:   'ပြီးစီး',
  DELIVERED:   'ပေးပို့ပြီး',
  CANCELLED:   'ပယ်ဖျက်',
};

const ACTIVE_STATUSES    = ['RECEIVED', 'INSPECTING', 'IN_PROGRESS'];
const DONE_STATUSES      = ['COMPLETED'];
const ARCHIVED_STATUSES  = ['DELIVERED', 'CANCELLED'];

/* ── Empty states ──────────────────────────────────────────────── */
const emptyForm = {
  customerId: '', assignedStaffId: '',
  itemName: '', problemDesc: '', diagnosisNotes: '',
  deviceConditions: '', estimatedCompletion: '', estimatedCost: '', remark: '',
  status: 'RECEIVED',
  lines: [] as { serviceItemId: string; serviceItemName: string; qty: number; price: number }[],
  productParts: [] as { productId: string; productName: string; qty: number; unitPrice: number; discountAmount: number; hasSerial: boolean; serialNumbers: string[]; availableSerials: any[] }[],
};

const emptySettle = {
  finalCost: '', discountAmount: '0', foc: false,
  paidAmount: '', dueDate: '',
  paymentMethodId: '', paymentAccountId: '', transactionNo: '',
  payments: [] as PaymentTransactionDTO[],
};

const normalizePayments = (payments: PaymentTransactionDTO[]) =>
  payments
    .map((p) => ({ ...p, paymentMethodId: Number(p.paymentMethodId) || 0, amount: Number(p.amount) || 0, transactionNo: p.transactionNo?.trim() || undefined }))
    .filter((p) => p.paymentMethodId > 0 && p.amount > 0);
const paymentTotal = (payments: PaymentTransactionDTO[]) => normalizePayments(payments).reduce((sum, p) => sum + (Number(p.amount) || 0), 0);

/* ── SearchableSelect ─────────────────────────────────────────── */
const SearchableSelect: React.FC<{
  items: any[];
  value: string;
  displayField?: string;
  subField?: string;
  placeholder?: string;
  onChange: (item: any | null) => void;
}> = ({ items, value, displayField = 'name', subField, placeholder = 'Search...', onChange }) => {
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const safeItems = Array.isArray(items) ? items : [];

  useEffect(() => {
    if (value) {
      const item = safeItems.find(i => i && String(i.id) === String(value));
      setSearch(item ? (item[displayField] ?? '') : '');
    } else {
      setSearch('');
    }
  }, [value, items, displayField]);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const filtered = safeItems.filter(i => {
    if (!i) return false;
    const txt = (search || '').toLowerCase();
    const name = (i[displayField] ?? '').toLowerCase();
    const sub = subField ? (i[subField] ?? '').toLowerCase() : '';
    return name.includes(txt) || sub.includes(txt);
  }).slice(0, 30);

  return (
    <div ref={ref} className="relative">
      <input
        value={search}
        onChange={e => { setSearch(e.target.value); setOpen(true); if (!e.target.value) onChange(null); }}
        onFocus={() => setOpen(true)}
        placeholder={placeholder}
        className="w-full border rounded-lg px-2 py-1.5 text-xs focus:ring-2 focus:ring-indigo-400"
      />
      {open && (
        <div className="absolute z-50 top-full left-0 right-0 mt-0.5 bg-white border rounded-lg shadow-xl max-h-44 overflow-y-auto">
          {filtered.map(item => (
            <div key={item.id}
              onClick={() => { onChange(item); setSearch(item[displayField]); setOpen(false); }}
              className="px-2.5 py-2 text-xs cursor-pointer hover:bg-indigo-50 flex justify-between items-center">
              <span className="font-medium text-slate-700">{item[displayField]}</span>
              {subField && item[subField] && (
                <span className="text-[10px] text-slate-400 ml-2">{item[subField]}</span>
              )}
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="px-2.5 py-2 text-xs text-slate-400 italic">ရှာမတွေ့ပါ</div>
          )}
        </div>
      )}
    </div>
  );
};

/* ── Main Page ─────────────────────────────────────────────────── */
export default function ServiceJobManagement() {
  const [jobs, setJobs]           = useState<any[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [tab, setTab]             = useState<'active' | 'done' | 'archived' | 'all' | 'credit'>('active');
  const [search, setSearch]       = useState('');
  const [dateFrom, setDateFrom]   = useState('');
  const [dateTo, setDateTo]       = useState('');
  const [staffList, setStaffList] = useState<any[]>([]);
  const [serviceItems, setServiceItems] = useState<any[]>([]);
  const [products, setProducts]   = useState<any[]>([]);
  const [payMethods, setPayMethods] = useState<any[]>([]);
  const [customers, setCustomers] = useState<any[]>([]);
  const [creditTerms, setCreditTerms] = useState<any[]>([]);

  const [showEdit, setShowEdit]   = useState(false);
  const [editId, setEditId]       = useState<number | null>(null);
  const [editJobNo, setEditJobNo] = useState('');
  const [origStatus, setOrigStatus] = useState('');
  const [form, setForm]           = useState(emptyForm);

  const [showSettle, setShowSettle] = useState(false);
  const [settleJob, setSettleJob]   = useState<any>(null);
  const [settleForm, setSettleForm] = useState(emptySettle);

  const [showCreditPay, setShowCreditPay] = useState(false);
  const [creditPayJob, setCreditPayJob]   = useState<any>(null);
  const [creditPayForm, setCreditPayForm] = useState({ paidAmount: '', paymentMethodId: '', paymentAccountId: '', transactionNo: '', payments: [] as PaymentTransactionDTO[] });

  const [printId, setPrintId]   = useState<number | null>(null);
  const PAGE_SIZE = 20;

  const load = async () => {
    const res = await serviceJobService.getAll(page, PAGE_SIZE, search, dateFrom, dateTo);
    if (res.success) {
      setJobs(res.data?.content ?? []);
      setTotal(res.data?.totalElements ?? 0);
    }
  };

  useEffect(() => { load(); }, [page, search, dateFrom, dateTo]);
  useDataEvents(['Service Job', 'Booking'], load);

  useEffect(() => {
    staffService.getAllActive()
      .then(list => setStaffList(list ?? []))
      .catch(() => {});
    serviceItemService.getActive()
      .then((r: any) => setServiceItems(Array.isArray(r?.data) ? r.data : Array.isArray(r) ? r : []))
      .catch(() => {});
    productService.getAll()
      .then((r: any) => setProducts(Array.isArray(r) ? r : []))
      .catch(() => {});
    paymentMethodService.getAllActive().then(setPayMethods).catch(() => {});
    customerService.getAll().then(setCustomers).catch(() => {});
    creditTermService.getAll().then(t => setCreditTerms(Array.isArray(t) ? t : [])).catch(() => {});
  }, []);

  /* ── Filtering ─────────────────────────────────────────────── */
  const filteredJobs = jobs.filter(j => {
    if (tab === 'active')   return ACTIVE_STATUSES.includes(j.status);
    if (tab === 'done')     return DONE_STATUSES.includes(j.status);
    if (tab === 'archived') return ARCHIVED_STATUSES.includes(j.status);
    if (tab === 'credit')   return Number(j.dueAmount) > 0;
    return true;
  });

  const counts = {
    active:   jobs.filter(j => ACTIVE_STATUSES.includes(j.status)).length,
    done:     jobs.filter(j => DONE_STATUSES.includes(j.status)).length,
    archived: jobs.filter(j => ARCHIVED_STATUSES.includes(j.status)).length,
    all:      jobs.length,
    credit:   jobs.filter(j => Number(j.dueAmount) > 0).length,
  };

  /* ── Edit handlers ─────────────────────────────────────────── */
  const openEdit = (j: any) => {
    setForm({
      customerId:          String(j.customerId ?? ''),
      assignedStaffId:     j.assignedStaffId ? String(j.assignedStaffId) : '',
      itemName:            j.itemName ?? '',
      problemDesc:         j.problemDesc ?? '',
      diagnosisNotes:      j.diagnosisNotes ?? '',
      deviceConditions:    j.deviceConditions ?? '',
      estimatedCompletion: j.estimatedCompletion ? j.estimatedCompletion.slice(0, 16) : '',
      estimatedCost:       j.estimatedCost ? String(j.estimatedCost) : '',
      remark:              j.remark ?? '',
      status:              j.status ?? 'RECEIVED',
      lines: (j.lines ?? []).map((l: any) => ({
        serviceItemId:   l.serviceItemId ?? '',
        serviceItemName: l.serviceItemName ?? '',
        qty:             l.qty ?? 1,
        price:           Number(l.price ?? 0),
      })),
      productParts: (j.productParts ?? []).map((p: any) => {
        const prod = products.find((pr: any) => String(pr.id) === String(p.productId));
        // detect serial: either product.hasSerial or already has serial numbers saved
        const hs = !!(prod?.hasSerial || (Array.isArray(p.serialNumbers) && p.serialNumbers.length > 0));
        return {
          productId:      p.productId ? String(p.productId) : '',
          productName:    p.productName ?? '',
          qty:            p.qty ?? 1,
          unitPrice:      Number(p.unitPrice ?? 0),
          discountAmount: Number(p.discountAmount ?? 0),
          hasSerial:      hs,
          serialNumbers:  Array.isArray(p.serialNumbers) ? p.serialNumbers : [],
          availableSerials: [] as any[],
        };
      }),
    });
    setEditId(j.id);
    setEditJobNo(j.jobNo ?? '');
    setOrigStatus(j.status ?? 'RECEIVED');
    setShowEdit(true);
    // Fetch available serials for serial-tracked parts
    (j.productParts ?? []).forEach((p: any, idx: number) => {
      if (!p.productId) return;
      const prod = products.find((pr: any) => String(pr.id) === String(p.productId));
      const isSerial = !!(prod?.hasSerial || (Array.isArray(p.serialNumbers) && p.serialNumbers.length > 0));
      if (isSerial) {
        productSerialService.getByProductId(Number(p.productId)).then(serials => {
          setForm(prev => {
            const pp = [...prev.productParts];
            if (pp[idx]) {
              pp[idx] = { ...pp[idx], hasSerial: true, availableSerials: serials ?? [] };
            }
            return { ...prev, productParts: pp };
          });
        }).catch(() => {});
      }
    });
  };

  const handleSave = async () => {
    if (!editId) return;
    const serialMismatch = form.productParts.find(p => p.hasSerial && p.productId && p.serialNumbers.length !== p.qty);
    if (serialMismatch) {
      Swal.fire('Serial Number လိုအပ်ပါ', `"${serialMismatch.productName}" အတွက် serial number ${serialMismatch.qty} ခု လိုအပ်သော်လည်း ${serialMismatch.serialNumbers.length} ခု ရွေးထားပါသည်`, 'warning');
      return;
    }
    const payload = {
      customerId:          form.customerId ? Number(form.customerId) : undefined,
      assignedStaffId:     form.assignedStaffId ? Number(form.assignedStaffId) : null,
      itemName:            form.itemName || null,
      problemDesc:         form.problemDesc || null,
      diagnosisNotes:      form.diagnosisNotes || null,
      deviceConditions:    form.deviceConditions || null,
      estimatedCompletion: form.estimatedCompletion ? form.estimatedCompletion + ':00' : null,
      estimatedCost:       form.estimatedCost ? Number(form.estimatedCost) : null,
      remark:              form.remark || null,
      status:              form.status,
      lines:               form.lines.filter((l: any) => l.serviceItemId),
      productParts:        form.productParts.filter((p: any) => p.productId).map(p => ({
        productId: Number(p.productId),
        qty: p.qty,
        unitPrice: p.unitPrice,
        discountAmount: p.discountAmount || 0,
        serialNumbers: p.hasSerial ? p.serialNumbers : [],
      })),
    };
    const res = await serviceJobService.update(editId, payload);
    if (!res.success) { Swal.fire('အမှား', res.message, 'error'); return; }

    if (form.status !== origStatus) {
      const statusRes = await serviceJobService.updateStatus(editId, form.status);
      if (!statusRes.success) { Swal.fire('အမှား', statusRes.message, 'error'); load(); return; }
    }
    setShowEdit(false);
    load();
  };

  /* ── Settle handlers ───────────────────────────────────────── */
  const openSettle = (j: any) => {
    const estCost = j.finalCost && Number(j.finalCost) > 0 ? j.finalCost : (j.estimatedCost ?? '');
    setSettleJob(j);
    setSettleForm({
      ...emptySettle,
      finalCost:  estCost ? String(estCost) : '',
      paidAmount: estCost ? String(estCost) : '',
    });
    setShowSettle(true);
  };

  const handleSettle = async () => {
    if (!settleJob) return;
    const settlePayments = normalizePayments(settleForm.payments || []);
    const paid = settleForm.foc ? 0 : (settlePayments.length > 0 ? paymentTotal(settleForm.payments || []) : Number(settleForm.paidAmount || 0));
    if (!settleForm.foc && paid > 0 && !settleForm.paymentMethodId && settlePayments.length === 0) {
      Swal.fire('အမှား', 'ငွေပေးချေနည်း ရွေးပါ', 'error'); return;
    }
    const dto = {
      finalCost:        settleForm.foc ? 0 : Number(settleForm.finalCost || 0),
      discountAmount:   Number(settleForm.discountAmount || 0),
      foc:              settleForm.foc,
      paidAmount:       paid,
      dueDate:          settleForm.dueDate || null,
      paymentMethodId:  paid > 0 ? (settlePayments[0]?.paymentMethodId || (settleForm.paymentMethodId ? Number(settleForm.paymentMethodId) : null)) : null,
      paymentAccountId: settleForm.paymentAccountId ? Number(settleForm.paymentAccountId) : null,
      transactionNo:    settleForm.transactionNo || null,
      payments:         settlePayments.length > 0 ? settlePayments : undefined,
    };
    const res = await serviceJobService.settle(settleJob.id, dto);
    if (res.success) {
      setShowSettle(false);
      Swal.fire({ icon: 'success', title: 'Settle လုပ်ပြီး', timer: 1500, showConfirmButton: false });
      load();
    } else Swal.fire('အမှား', res.message, 'error');
  };

  /* ── Credit Pay handlers ────────────────────────────────────── */
  const openCreditPay = (j: any) => {
    setCreditPayJob(j);
    const due = Number(j.dueAmount) || 0;
    setCreditPayForm({ paidAmount: due > 0 ? String(due) : '', paymentMethodId: '', paymentAccountId: '', transactionNo: '', payments: [] });
    setShowCreditPay(true);
  };

  const handleCreditPay = async () => {
    if (!creditPayJob) return;
    const creditPayments = normalizePayments(creditPayForm.payments || []);
    const paid = creditPayments.length > 0 ? paymentTotal(creditPayForm.payments || []) : Number(creditPayForm.paidAmount || 0);
    if (paid <= 0) { Swal.fire('အမှား', 'ပေးချေမည့် ပမာဏ ထည့်ပါ', 'error'); return; }
    if (!creditPayForm.paymentMethodId && creditPayments.length === 0) { Swal.fire('အမှား', 'ငွေပေးချေနည်း ရွေးပါ', 'error'); return; }
    const dto = {
      paidAmount: paid,
      paymentMethodId: creditPayments[0]?.paymentMethodId || Number(creditPayForm.paymentMethodId),
      paymentAccountId: creditPayForm.paymentAccountId ? Number(creditPayForm.paymentAccountId) : null,
      transactionNo: creditPayForm.transactionNo || null,
      payments: creditPayments.length > 0 ? creditPayments : undefined,
    };
    const res = await serviceJobService.payDue(creditPayJob.id, dto);
    if (res.success) {
      setShowCreditPay(false);
      Swal.fire({ icon: 'success', title: 'အကြွေးဆပ်ပြီး', timer: 1500, showConfirmButton: false });
      load();
    } else Swal.fire('အမှား', res.message, 'error');
  };

  const cpPaid = Number(creditPayForm.paidAmount || 0);
  const cpDue = creditPayJob ? Number(creditPayJob.dueAmount || 0) : 0;
  const cpRemaining = Math.max(0, cpDue - cpPaid);
  const cpSelectedPM = payMethods.find(m => String(m.id) === creditPayForm.paymentMethodId);
  const cpRequiresTxn = cpSelectedPM && /bank|kpay|wave|aya|kbz|mpu/i.test(cpSelectedPM.methodName);

  /* ── Deliver ───────────────────────────────────────────────── */
  const handleDeliver = async (id: number) => {
    const { isConfirmed } = await Swal.fire({
      title: 'ပစ္စည်းပြန်ပေးပြီးကြောင်း အတည်ပြုမည်',
      text: 'Delivered အဖြစ်မှတ်မည်',
      icon: 'question', showCancelButton: true,
      confirmButtonText: 'အတည်ပြု', cancelButtonText: 'မလုပ်တော့',
    });
    if (!isConfirmed) return;
    const res = await serviceJobService.deliver(id);
    if (res.success) {
      Swal.fire({ icon: 'success', title: 'ပေးပို့ပြီး!', timer: 1200, showConfirmButton: false });
      load();
    } else Swal.fire('အမှား', res.message, 'error');
  };

  /* ── Delete ────────────────────────────────────────────────── */
  const handleDelete = async (id: number) => {
    const { isConfirmed } = await Swal.fire({
      title: 'ဖျက်မည်လား?', icon: 'warning', showCancelButton: true,
      confirmButtonText: 'ဖျက်', cancelButtonText: 'မဖျက်ဘူး', confirmButtonColor: '#ef4444',
    });
    if (!isConfirmed) return;
    const res = await serviceJobService.remove(id);
    if (res.success) load();
  };

  /* ── Settle calculations ───────────────────────────────────── */
  const sFinalCost = Number(settleForm.finalCost || 0);
  const sDiscount  = Number(settleForm.discountAmount || 0);
  const sNetAmt    = settleForm.foc ? 0 : Math.max(0, sFinalCost - sDiscount);
  const sPaid      = settleForm.foc ? 0 : Number(settleForm.paidAmount || 0);
  const sBalance   = Math.max(0, sNetAmt - sPaid);
  const selectedPM = payMethods.find(m => String(m.id) === settleForm.paymentMethodId);
  const requiresTxn = selectedPM && /bank|kpay|wave|aya|kbz|mpu/i.test(selectedPM.methodName);

  const settleCustomer = settleJob ? customers.find(c => c.id === settleJob.customerId) : null;
  const settleTerm = settleJob ? creditTerms.find(t => t.customerId === settleJob.customerId) : null;
  const isCreditHold = Boolean(settleCustomer?.creditHold);
  const isBlacklisted = Boolean(settleCustomer?.blacklisted);
  const creditAllowed = Boolean(settleTerm?.creditAllowed);
  const creditLimit = Number(settleTerm?.creditLimit) || 0;

  const customerJobOutstanding = useMemo(() => {
    if (!settleJob) return 0;
    return jobs.filter(j => j.customerId === settleJob.customerId && j.id !== settleJob.id)
      .reduce((sum, j) => sum + (Number(j.dueAmount) || 0), 0);
  }, [jobs, settleJob]);
  const projectedOutstanding = customerJobOutstanding + sBalance;
  const limitExceeded = sBalance > 0 && creditLimit > 0 && projectedOutstanding > creditLimit;
  const limitNear = sBalance > 0 && creditLimit > 0 && !limitExceeded && projectedOutstanding >= (creditLimit * 0.8);

  const totalPages = Math.ceil(total / PAGE_SIZE);

  /* ── Tabs config ───────────────────────────────────────────── */
  const tabDef: { key: typeof tab; label: string; count: number; active: string; inactive: string }[] = [
    { key: 'active',   label: 'ဝန်ဆောင်မှု အမှာစာ ⚡', count: counts.active,   active: 'border-blue-500 text-blue-700 bg-blue-50',    inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'done',     label: 'ပြင်ဆင်ပြီး 🔧',        count: counts.done,     active: 'border-emerald-500 text-emerald-700 bg-emerald-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'archived', label: 'ပေးပို့ပြီး / ပိတ်ပြီး ✓', count: counts.archived, active: 'border-green-500 text-green-700 bg-green-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'all',      label: 'အားလုံး',                count: counts.all,      active: 'border-slate-400 text-slate-700 bg-slate-100', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'credit',   label: 'အကြွေးကျန် 💳',         count: counts.credit,   active: 'border-rose-500 text-rose-700 bg-rose-50',    inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
  ];

  return (
    <div>
      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
        {/* Status Tabs */}
        <div className="flex gap-1.5 px-3 pt-3 pb-2 overflow-x-auto bg-slate-50/60 border-b">
          {tabDef.map(t => (
            <button key={t.key} onClick={() => { setTab(t.key); setPage(0); }}
              className={`px-3.5 py-1.5 rounded-lg text-sm font-bold border-2 whitespace-nowrap transition-all flex items-center gap-1.5 ${tab === t.key ? t.active : t.inactive}`}>
              {t.label}
              <span className={`text-xs px-1.5 py-0.5 rounded-full font-bold ${tab === t.key ? 'bg-white/60' : 'bg-slate-200 text-slate-500'}`}>
                {t.count}
              </span>
            </button>
          ))}
        </div>

        {/* Filters */}
        <div className="flex flex-wrap items-center gap-2 px-3 py-2 border-b bg-white">
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Job#, ဝယ်သူ, ပစ္စည်း ရှာရန်..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-48 focus:ring-2 focus:ring-purple-400" />
          <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white" />
          <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white" />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-purple-600">
              <tr>
                {['#', 'Job No', 'Intake #', 'ရက်စွဲ', 'ဖောက်သည်', 'ပစ္စည်း', 'ကျွမ်းကျင်သူ', 'အခြေအနေ', 'ခန့်မှန်းကုန်ကျ', 'အပြီးသတ်', 'လက်ကျန်', 'လုပ်ဆောင်ချက်'].map(h => (
                  <th key={h} className="text-left px-3 py-3.5 text-[13px] font-extrabold text-white tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredJobs.map((j, i) => {
                const col        = STATUS_COLOR[j.status as JobStatus] ?? 'bg-slate-100 text-slate-600';
                const isCompleted = j.status === 'COMPLETED';
                const canEdit    = j.status !== 'DELIVERED' && j.status !== 'CANCELLED';
                const canDelete  = j.status === 'RECEIVED' || j.status === 'INSPECTING';
                const balance    = Number(j.dueAmount ?? 0);
                return (
                  <tr key={j.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-3 py-3 text-xs text-slate-400">{page * PAGE_SIZE + i + 1}</td>
                    <td className="px-3 py-3">
                      <span className="font-mono text-xs font-bold text-purple-600 bg-purple-50 px-2 py-0.5 rounded-lg">{j.jobNo}</span>
                    </td>
                    <td className="px-3 py-3">
                      {j.bookingNo && (
                        <span className="font-mono text-xs text-indigo-500 bg-indigo-50 px-1.5 py-0.5 rounded">{j.bookingNo}</span>
                      )}
                    </td>
                    <td className="px-3 py-3 text-xs text-slate-500 whitespace-nowrap">{j.receivedDate?.slice(0, 10)}</td>
                    <td className="px-3 py-3">
                      <div className="font-semibold text-slate-800">{j.customerName}</div>
                      <div className="text-xs text-slate-400">{j.customerPhone}</div>
                    </td>
                    <td className="px-3 py-3">
                      <div className="font-medium text-slate-700">{j.itemName || '—'}</div>
                      {j.problemDesc && (
                        <p className="text-xs text-slate-400 truncate max-w-32" title={j.problemDesc}>{j.problemDesc}</p>
                      )}
                    </td>
                    <td className="px-3 py-3 text-xs text-slate-600">{j.assignedStaffName || '—'}</td>
                    <td className="px-3 py-3">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${col}`}>
                        {STATUS_LABEL[j.status as JobStatus] ?? j.status}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-xs text-slate-600">
                      {j.estimatedCost ? Number(j.estimatedCost).toLocaleString() : '—'}
                    </td>
                    <td className="px-3 py-3 text-sm font-semibold text-slate-700">
                      {j.finalCost !== null && j.finalCost !== undefined ? Number(j.finalCost).toLocaleString() : '—'}
                    </td>
                    <td className="px-3 py-3">
                      {isCompleted && balance > 0 && (
                        <span className="text-xs font-bold text-red-600">{balance.toLocaleString()} Ks</span>
                      )}
                      {isCompleted && balance === 0 && j.paymentStatus === 'Paid' && (
                        <span className="text-xs font-bold text-emerald-600">ပေးပြီး ✓</span>
                      )}
                      {isCompleted && balance === 0 && j.paymentStatus !== 'Paid' && j.finalCost !== null && (
                        <span className="text-xs font-semibold text-emerald-500">ရှင်းပြီး</span>
                      )}
                      {!isCompleted && <span className="text-xs text-slate-300">—</span>}
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex items-center gap-1 flex-wrap">
                        <button onClick={() => setPrintId(j.id)} title="Print Invoice"
                          className="px-2 py-1 text-xs border rounded-lg text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 transition-colors">
                          <Printer size={14} />
                        </button>
                        {canEdit && (
                          <button onClick={() => openEdit(j)} title="Edit"
                            className="px-2 py-1 text-xs border rounded-lg text-slate-500 hover:text-blue-600 hover:bg-blue-50 transition-colors">
                            <FileEdit size={14} />
                          </button>
                        )}
                        {isCompleted && !j.paymentStatus && (
                          <button onClick={() => openSettle(j)} title="ငွေရှင်းရန်"
                            className="px-2 py-1 text-xs border border-amber-200 rounded-lg text-amber-700 hover:bg-amber-50 font-bold transition-colors whitespace-nowrap">
                            💰 ရှင်းမယ်
                          </button>
                        )}
                        {Number(j.dueAmount) > 0 && j.paymentStatus && (
                          <button onClick={() => openCreditPay(j)} title="အကြွေးဆပ်ရန်"
                            className="px-2 py-1 text-xs border border-rose-200 rounded-lg text-rose-700 hover:bg-rose-50 font-bold transition-colors whitespace-nowrap">
                            💳 အကြွေးဆပ်
                          </button>
                        )}
                        {isCompleted && (
                          <button onClick={() => handleDeliver(j.id)} title="ပေးပို့ပြီးမှတ်ရန်"
                            className="px-2 py-1 text-xs border border-green-200 rounded-lg text-green-700 hover:bg-green-50 font-bold transition-colors whitespace-nowrap">
                            ✓ ပေးပို့ပြီး
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
              {filteredJobs.length === 0 && (
                <tr>
                  <td colSpan={12} className="py-16 text-center">
                    <div className="text-5xl mb-3">🔧</div>
                    <p className="text-sm text-slate-400">Job မရှိသေးပါ</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="px-4 py-3 border-t flex items-center justify-between text-xs text-slate-500">
            <span>စုစုပေါင်း {total}</span>
            <div className="flex gap-2 items-center">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-40 hover:bg-slate-50">← နောက်</button>
              <span>စာမျက်နှာ {page + 1} / {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-40 hover:bg-slate-50">ရှေ့ →</button>
            </div>
          </div>
        )}
      </div>

      {/* ─── Edit Modal ─── */}
      {showEdit && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center overflow-y-auto py-6 px-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl">
            <div className="flex items-center justify-between px-6 py-4 bg-purple-600 rounded-t-2xl">
              <div>
                <h2 className="text-lg font-bold text-white">✏ ဝန်ဆောင်မှု ပြင်ဆင်ရန်</h2>
                <p className="text-xs text-purple-200 mt-0.5">{editJobNo}</p>
              </div>
              <button onClick={() => setShowEdit(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="p-6 space-y-5">
              {/* Status + Technician */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">အခြေအနေ</label>
                  <select value={form.status} onChange={e => setForm(p => ({ ...p, status: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    {STATUS_LIST.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">ကျွမ်းကျင်သူ</label>
                  <select value={form.assignedStaffId} onChange={e => setForm(p => ({ ...p, assignedStaffId: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    <option value="">— မရှိ —</option>
                    {staffList.map((s: any) => <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>)}
                  </select>
                </div>
              </div>

              {/* Item Name */}
              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်း / ကိရိယာ အမည်</label>
                <input value={form.itemName} onChange={e => setForm(p => ({ ...p, itemName: e.target.value }))}
                  placeholder="ဥပမာ - Apple iPhone 14 Pro"
                  className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>

              {/* Problem + Diagnosis */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-500 mb-1">ပြဿနာ ဖော်ပြချက်</label>
                  <textarea value={form.problemDesc} onChange={e => setForm(p => ({ ...p, problemDesc: e.target.value }))}
                    rows={3} placeholder="ဝယ်သူ၏ တိုင်ကြားချက်..."
                    className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 mb-1">စစ်ဆေးတွေ့ရှိချက်</label>
                  <textarea value={form.diagnosisNotes} onChange={e => setForm(p => ({ ...p, diagnosisNotes: e.target.value }))}
                    rows={3} placeholder="ကျွမ်းကျင်သူ တွေ့ရှိချက်..."
                    className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
                </div>
              </div>

              {/* Device Condition */}
              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအခြေအနေ</label>
                <textarea value={form.deviceConditions} onChange={e => setForm(p => ({ ...p, deviceConditions: e.target.value }))}
                  rows={2} placeholder="ပစ္စည်း၏ လက်ရှိ အခြေအနေ..."
                  className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
              </div>

              {/* Est. Completion + Est. Cost */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs text-slate-500 mb-1">ခန့်မှန်းပြီးစီးရက်</label>
                  <input type="datetime-local" value={form.estimatedCompletion}
                    onChange={e => setForm(p => ({ ...p, estimatedCompletion: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 mb-1">ခန့်မှန်းကုန်ကျစရိတ် (Ks)</label>
                  <input type="number" min={0} value={form.estimatedCost}
                    onChange={e => setForm(p => ({ ...p, estimatedCost: e.target.value }))}
                    placeholder="0" className="w-full border rounded-xl px-3 py-2 text-sm" />
                </div>
              </div>

              {/* Service Lines */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-slate-500 uppercase">🔧 ဝန်ဆောင်မှု / လုပ်ခ</label>
                  <button type="button"
                    onClick={() => setForm(p => ({ ...p, lines: [...p.lines, { serviceItemId: '', serviceItemName: '', qty: 1, price: 0 }] }))}
                    className="text-xs text-indigo-600 hover:underline font-bold">+ ထည့်ရန်</button>
                </div>
                {form.lines.length > 0 ? (
                  <div className="space-y-2">
                    {form.lines.map((line: any, li: number) => (
                      <div key={li} className="border rounded-xl p-3 bg-slate-50 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] font-bold text-slate-400 uppercase">ဝန်ဆောင်မှု #{li + 1}</span>
                          <button type="button"
                            onClick={() => setForm(p => ({ ...p, lines: p.lines.filter((_: any, idx: number) => idx !== li) }))}
                            className="text-xs text-red-400 hover:text-red-600 font-bold px-1.5 py-0.5 border border-red-200 rounded hover:bg-red-50">ဖယ်ရန်</button>
                        </div>
                        <div>
                          <label className="block text-[10px] text-slate-500 mb-0.5">ဝန်ဆောင်မှု အမျိုးအစား</label>
                          <SearchableSelect
                            items={serviceItems}
                            value={line.serviceItemId}
                            displayField="item"
                            subField="code"
                            placeholder="ဝန်ဆောင်မှု ရှာရန်..."
                            onChange={(si) => {
                              setForm(p => {
                                const lines = [...p.lines];
                                if (si) {
                                  lines[li] = { ...lines[li], serviceItemId: String(si.id), serviceItemName: si.item ?? '', price: Number(si.price ?? 0) };
                                } else {
                                  lines[li] = { ...lines[li], serviceItemId: '', serviceItemName: '', price: 0 };
                                }
                                return { ...p, lines };
                              });
                            }}
                          />
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                          <div>
                            <label className="block text-[10px] text-slate-500 mb-0.5">အရေအတွက်</label>
                            <input type="number" min={1} value={line.qty}
                              onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], qty: Number(e.target.value) }; return { ...p, lines }; })}
                              className="w-full border rounded-lg px-2 py-1.5 text-xs text-center" />
                          </div>
                          <div>
                            <label className="block text-[10px] text-slate-500 mb-0.5">ဈေးနှုန်း (Ks)</label>
                            <input type="number" min={0} value={line.price}
                              onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], price: Number(e.target.value) }; return { ...p, lines }; })}
                              className="w-full border rounded-lg px-2 py-1.5 text-xs text-right" />
                          </div>
                        </div>
                        {line.serviceItemName && (
                          <div className="text-[10px] text-slate-400">
                            စုစုပေါင်း: <span className="font-bold text-slate-600">{(Number(line.qty || 1) * Number(line.price || 0)).toLocaleString()} Ks</span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">ဝန်ဆောင်မှု မရှိသေးပါ</p>
                )}
              </div>

              {/* Product Parts */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-slate-500 uppercase">📦 အသုံးပြုပစ္စည်းများ</label>
                  <button type="button"
                    onClick={() => setForm(p => ({ ...p, productParts: [...p.productParts, { productId: '', productName: '', qty: 1, unitPrice: 0, discountAmount: 0, hasSerial: false, serialNumbers: [], availableSerials: [] }] }))}
                    className="text-xs text-indigo-600 hover:underline font-bold">+ ထည့်ရန်</button>
                </div>
                {form.productParts.length > 0 ? (
                  <div className="space-y-2">
                    {form.productParts.map((part: any, pi: number) => (
                      <div key={pi} className="border rounded-xl p-3 bg-slate-50 space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] font-bold text-slate-400 uppercase">ပစ္စည်း #{pi + 1}</span>
                          <button type="button"
                            onClick={() => setForm(p => ({ ...p, productParts: p.productParts.filter((_: any, idx: number) => idx !== pi) }))}
                            className="text-xs text-red-400 hover:text-red-600 font-bold px-1.5 py-0.5 border border-red-200 rounded hover:bg-red-50">ဖယ်ရန်</button>
                        </div>
                        <div>
                          <label className="block text-[10px] text-slate-500 mb-0.5">ပစ္စည်း</label>
                          <SearchableSelect
                            items={products}
                            value={part.productId}
                            displayField="name"
                            subField="productCode"
                            placeholder="ပစ္စည်း ရှာရန်..."
                            onChange={(prod) => {
                              if (prod) {
                                const hs = !!prod.hasSerial;
                                setForm(p => {
                                  const pp = [...p.productParts];
                                  pp[pi] = { ...pp[pi], productId: String(prod.id), productName: prod.name ?? '', unitPrice: Number(prod.sellingPrice ?? prod.price ?? 0), discountAmount: 0, hasSerial: hs, serialNumbers: [], availableSerials: [] };
                                  return { ...p, productParts: pp };
                                });
                                if (hs) {
                                  productSerialService.getByProductId(Number(prod.id)).then(serials => {
                                    setForm(p => {
                                      const pp = [...p.productParts];
                                      if (pp[pi]) pp[pi] = { ...pp[pi], availableSerials: serials ?? [] };
                                      return { ...p, productParts: pp };
                                    });
                                  }).catch(() => {});
                                }
                              } else {
                                setForm(p => {
                                  const pp = [...p.productParts];
                                  pp[pi] = { ...pp[pi], productId: '', productName: '', unitPrice: 0, discountAmount: 0, hasSerial: false, serialNumbers: [], availableSerials: [] };
                                  return { ...p, productParts: pp };
                                });
                              }
                            }}
                          />
                        </div>
                        <div className="grid grid-cols-3 gap-2">
                          <div>
                            <label className="block text-[10px] text-slate-500 mb-0.5">အရေအတွက်</label>
                            <input type="number" min={1} value={part.qty}
                              onChange={e => {
                                const newQty = Number(e.target.value);
                                setForm(p => {
                                  const pp = [...p.productParts];
                                  const trimmed = pp[pi].hasSerial && pp[pi].serialNumbers.length > newQty
                                    ? pp[pi].serialNumbers.slice(0, newQty)
                                    : pp[pi].serialNumbers;
                                  pp[pi] = { ...pp[pi], qty: newQty, serialNumbers: trimmed };
                                  return { ...p, productParts: pp };
                                });
                              }}
                              className="w-full border rounded-lg px-2 py-1.5 text-xs text-center" />
                          </div>
                          <div>
                            <label className="block text-[10px] text-slate-500 mb-0.5">တစ်ခုဈေး (Ks)</label>
                            <input type="number" min={0} value={part.unitPrice}
                              onChange={e => setForm(p => { const pp = [...p.productParts]; pp[pi] = { ...pp[pi], unitPrice: Number(e.target.value) }; return { ...p, productParts: pp }; })}
                              className="w-full border rounded-lg px-2 py-1.5 text-xs text-right" />
                          </div>
                          <div>
                            <label className="block text-[10px] text-slate-500 mb-0.5">လျှော့ဈေး (Ks)</label>
                            <input type="number" min={0} value={part.discountAmount}
                              onChange={e => setForm(p => { const pp = [...p.productParts]; pp[pi] = { ...pp[pi], discountAmount: Number(e.target.value) }; return { ...p, productParts: pp }; })}
                              className="w-full border rounded-lg px-2 py-1.5 text-xs text-right" />
                          </div>
                        </div>
                        {/* Serial Number Picker */}
                        {part.hasSerial && part.productId && (
                          <div className="mt-1">
                            <label className="block text-[10px] font-bold text-amber-600 mb-1">
                              Serial Numbers ({part.serialNumbers.length}/{part.qty})
                              {part.serialNumbers.length !== part.qty && (
                                <span className="text-red-500 ml-1">— {part.qty - part.serialNumbers.length} remaining</span>
                              )}
                            </label>
                            {part.serialNumbers.length > 0 && (
                              <div className="flex flex-wrap gap-1 mb-1.5">
                                {part.serialNumbers.map((sn: string, si: number) => (
                                  <span key={si} className="inline-flex items-center gap-1 bg-amber-50 border border-amber-200 text-amber-800 text-[10px] font-mono px-2 py-0.5 rounded-full">
                                    {sn}
                                    <button type="button"
                                      onClick={() => setForm(p => {
                                        const pp = [...p.productParts];
                                        pp[pi] = { ...pp[pi], serialNumbers: pp[pi].serialNumbers.filter((_: string, idx: number) => idx !== si) };
                                        return { ...p, productParts: pp };
                                      })}
                                      className="text-amber-500 hover:text-red-600 font-bold leading-none">x</button>
                                  </span>
                                ))}
                              </div>
                            )}
                            {part.serialNumbers.length < part.qty && (
                              <div className="max-h-28 overflow-y-auto border rounded-lg bg-white">
                                {(part.availableSerials || []).length === 0 ? (
                                  <div className="px-2 py-2 text-[10px] text-slate-400 italic">Available serial မရှိပါ</div>
                                ) : (
                                  (part.availableSerials || [])
                                    .filter((s: any) => s.status === 'Available' && !part.serialNumbers.includes(s.serialNumber))
                                    .map((s: any) => (
                                      <div key={s.id}
                                        onClick={() => {
                                          if (part.serialNumbers.length >= part.qty) return;
                                          setForm(p => {
                                            const pp = [...p.productParts];
                                            pp[pi] = { ...pp[pi], serialNumbers: [...pp[pi].serialNumbers, s.serialNumber] };
                                            return { ...p, productParts: pp };
                                          });
                                        }}
                                        className="px-2.5 py-1.5 text-[11px] font-mono cursor-pointer hover:bg-amber-50 border-b last:border-b-0 flex items-center justify-between">
                                        <span>{s.serialNumber}</span>
                                        <span className="text-[9px] text-emerald-500 font-bold">+ ရွေးပါ</span>
                                      </div>
                                    ))
                                )}
                              </div>
                            )}
                          </div>
                        )}
                        {part.productName && (() => {
                          const gross = Number(part.qty || 1) * Number(part.unitPrice || 0);
                          const disc = Number(part.discountAmount || 0);
                          const sub = Math.max(0, gross - disc);
                          return (
                            <div className="text-[10px] text-slate-400 flex items-center gap-2">
                              <span>စုစုပေါင်း: <span className="font-bold text-slate-600">{sub.toLocaleString()} Ks</span></span>
                              {disc > 0 && <span className="text-red-400 font-medium">(- {disc.toLocaleString()} Ks လျှော့)</span>}
                            </div>
                          );
                        })()}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">ပစ္စည်း မရှိသေးပါ</p>
                )}
              </div>

              {/* Remark */}
              <div>
                <label className="block text-xs text-slate-500 mb-1">မှတ်ချက်</label>
                <input value={form.remark} onChange={e => setForm(p => ({ ...p, remark: e.target.value }))}
                  placeholder="မှတ်ချက်..." className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>
            </div>

            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl">
              <button onClick={() => setShowEdit(false)}
                className="px-5 py-2 text-sm border rounded-xl text-slate-600 hover:bg-slate-100 font-medium">မလုပ်တော့</button>
              <button onClick={handleSave}
                className="px-6 py-2 text-sm bg-purple-600 text-white rounded-xl font-bold hover:bg-purple-700 shadow">
                သိမ်းမယ်
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─── Settle Modal ─── */}
      {showSettle && settleJob && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
            <div className="px-6 py-4 bg-amber-500 rounded-t-2xl flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white">💰 ငွေရှင်းရန်</h2>
                <p className="text-xs text-amber-100 mt-0.5">{settleJob.jobNo} · {settleJob.customerName}</p>
              </div>
              <button onClick={() => setShowSettle(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="p-6 space-y-4">
              {/* Device */}
              <div className="bg-slate-50 rounded-xl px-4 py-3">
                <span className="text-xs text-slate-500">ပစ္စည်း: </span>
                <span className="text-sm font-bold text-slate-800">{settleJob.itemName || '—'}</span>
              </div>

              {/* FOC */}
              <label className="flex items-center gap-3 cursor-pointer">
                <input type="checkbox" checked={settleForm.foc}
                  onChange={e => setSettleForm(p => ({
                    ...p, foc: e.target.checked,
                    paidAmount: e.target.checked ? '0' : p.paidAmount,
                    paymentMethodId: e.target.checked ? '' : p.paymentMethodId,
                  }))}
                  className="w-4 h-4 accent-amber-500" />
                <span className="text-sm font-bold text-slate-700">FOC (အခမဲ့)</span>
              </label>

              {!settleForm.foc && (
                <>
                  {/* Final Cost + Discount */}
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs text-slate-500 mb-1">အပြီးသတ်ကုန်ကျစရိတ် (Ks)</label>
                      <input type="number" min={0} value={settleForm.finalCost}
                        onChange={e => setSettleForm(p => ({ ...p, finalCost: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm font-bold focus:ring-2 focus:ring-amber-400" />
                    </div>
                    <div>
                      <label className="block text-xs text-slate-500 mb-1">လျှော့ဈေး (Ks)</label>
                      <input type="number" min={0} value={settleForm.discountAmount}
                        onChange={e => setSettleForm(p => ({ ...p, discountAmount: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm" />
                    </div>
                  </div>

                  {/* Net Amount */}
                  <div className="bg-slate-100 rounded-xl px-4 py-3 flex justify-between items-center">
                    <span className="text-sm text-slate-600 font-medium">အသားတင်ပမာဏ</span>
                    <span className="text-base font-bold text-slate-800">{sNetAmt.toLocaleString()} Ks</span>
                  </div>

                  {/* Paid Amount — Full / Credit quick buttons */}
                  <div className="rounded-xl border-2 border-amber-300 bg-amber-50 px-4 py-3 space-y-3">
                    <label className="block text-[11px] font-bold text-amber-700 uppercase tracking-wide">ပေးချေပမာဏ (Ks)</label>
                    <div className="flex gap-2">
                      <input
                        type="number" min={0}
                        value={settleForm.paidAmount}
                        onChange={e => {
                          const val = e.target.value;
                          setSettleForm(p => ({
                            ...p, paidAmount: val,
                            ...(Number(val) === 0 ? { paymentMethodId: '', transactionNo: '' } : {}),
                          }));
                        }}
                        className="flex-1 w-0 px-3 py-2.5 rounded-lg border-2 border-amber-300 bg-white text-lg font-bold text-amber-900 focus:outline-none focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
                      />
                      <button type="button"
                        onClick={() => setSettleForm(p => ({ ...p, paidAmount: sNetAmt > 0 ? String(sNetAmt) : '0' }))}
                        className="px-3 py-2 rounded-lg bg-amber-500 text-white text-xs font-bold hover:bg-amber-600 shrink-0">အပြည့်</button>
                      <button type="button"
                        onClick={() => setSettleForm(p => ({ ...p, paidAmount: '0', paymentMethodId: '', transactionNo: '' }))}
                        className="px-3 py-2 rounded-lg bg-sky-500 text-white text-xs font-bold hover:bg-sky-600 shrink-0">အကြွေး</button>
                    </div>

                    {/* Payment Method */}
                    <div>
                      <label className="block text-[10px] font-semibold text-amber-700 uppercase mb-1">ငွေပေးချေနည်း</label>
                      <select
                        value={settleForm.paymentMethodId}
                        onChange={e => {
                          const m = payMethods.find(m => String(m.id) === e.target.value);
                          setSettleForm(p => ({
                            ...p, paymentMethodId: e.target.value,
                            paymentAccountId: m?.accountId ? String(m.accountId) : '',
                          }));
                        }}
                        disabled={sPaid <= 0}
                        className="w-full border border-amber-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-amber-500 disabled:opacity-40 disabled:cursor-not-allowed">
                        <option value="">— ရွေးပါ —</option>
                        {payMethods.map(m => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                      </select>
                      {sPaid <= 0 && <p className="text-[10px] text-amber-600 mt-0.5">အကြွေးရောင်း — ငွေပေးချေမှုမရှိပါ</p>}
                    </div>

                    <SplitPaymentEditor
                      methods={payMethods}
                      payments={settleForm.payments || []}
                      onChange={(next) => {
                        setSettleForm(p => ({
                          ...p,
                          payments: next,
                          paidAmount: paymentTotal(next) > 0 ? String(paymentTotal(next)) : p.paidAmount
                        }));
                      }}
                      label="Split Payment"
                    />

                    {/* Transaction No (bank/kpay/wave) */}
                    {requiresTxn && sPaid > 0 && (
                      <div>
                        <label className="block text-[10px] font-semibold text-amber-700 uppercase mb-1">ငွေလွှဲနံပါတ်</label>
                        <input
                          value={settleForm.transactionNo}
                          onChange={e => setSettleForm(p => ({ ...p, transactionNo: e.target.value }))}
                          placeholder="ဘဏ်/KPay ငွေလွှဲအမှတ်..."
                          className="w-full border border-amber-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-amber-500"
                        />
                      </div>
                    )}

                    {/* Paid / Balance summary */}
                    <div className="border-t border-amber-200 pt-2 space-y-1 text-sm">
                      <div className="flex justify-between text-slate-600">
                        <span>ပေးပြီး</span>
                        <span className="font-semibold text-amber-700">{sPaid.toLocaleString()} Ks</span>
                      </div>
                      <div className={`flex justify-between font-bold text-base ${sBalance > 0 ? 'text-red-700' : 'text-emerald-700'}`}>
                        <span>{sBalance > 0 ? 'အကြွေးကျန်' : 'အပြည့်ပေးပြီး ✓'}</span>
                        <span>{sBalance.toLocaleString()} Ks</span>
                      </div>
                    </div>
                  </div>

                  {/* Credit Due Date */}
                  {sBalance > 0 && (
                    <div>
                      <label className="block text-xs font-semibold text-slate-600 mb-1.5">အကြွေးသတ်မှတ်ရက် <span className="text-slate-400 font-normal">(အကြွေးသာ)</span></label>
                      <input type="date" value={settleForm.dueDate}
                        onChange={e => setSettleForm(p => ({ ...p, dueDate: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-indigo-400" />
                    </div>
                  )}

                  {/* Credit status warnings */}
                  {sBalance > 0 && isBlacklisted && (
                    <div className="rounded-lg border border-slate-300 bg-slate-800 px-4 py-3 flex items-center gap-2">
                      <AlertTriangle size={15} className="text-white flex-shrink-0" />
                      <p className="text-sm font-semibold text-white">Blacklist — လက်ငင်းသာရှင်းနိုင်ပါသည်</p>
                    </div>
                  )}
                  {sBalance > 0 && !isBlacklisted && isCreditHold && (
                    <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 flex items-center gap-2">
                      <AlertTriangle size={15} className="text-rose-600 flex-shrink-0" />
                      <p className="text-sm font-semibold text-rose-700">Credit Hold — ဤ customer အတွက် credit ပိတ်ထားပါသည်</p>
                    </div>
                  )}
                  {sBalance > 0 && !isBlacklisted && !isCreditHold && limitExceeded && (
                    <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 flex items-center gap-2">
                      <AlertTriangle size={15} className="text-rose-600 flex-shrink-0" />
                      <p className="text-sm font-semibold text-rose-700">အကြွေးကန့်သတ်ကျော်လွန် — {creditLimit.toLocaleString()} Ks (လက်ကျန်: {projectedOutstanding.toLocaleString()} Ks)</p>
                    </div>
                  )}
                  {sBalance > 0 && !isBlacklisted && !isCreditHold && limitNear && (
                    <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 flex items-center gap-2">
                      <AlertTriangle size={15} className="text-amber-600 flex-shrink-0" />
                      <p className="text-sm font-semibold text-amber-700">အကြွေးကန့်သတ်နီးပါးပြည့်ပြီ — {creditLimit.toLocaleString()} Ks (လက်ကျန်: {projectedOutstanding.toLocaleString()} Ks)</p>
                    </div>
                  )}
                  {sBalance > 0 && !isBlacklisted && !isCreditHold && !limitExceeded && creditAllowed && creditLimit > 0 && !limitNear && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 flex items-center gap-2">
                      <span className="text-sm font-semibold text-emerald-700">အကြွေးအဆင်ပြေ — ကန့်သတ်: {creditLimit.toLocaleString()} Ks | လက်ကျန်: {projectedOutstanding.toLocaleString()} Ks</span>
                    </div>
                  )}
                </>
              )}

              {settleForm.foc && (
                <div className="bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3 text-emerald-700 text-sm font-bold">
                  ✓ FOC — ငွေပေးချေစရာမလိုပါ
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl">
              <button onClick={() => setShowSettle(false)}
                className="px-5 py-2 text-sm border rounded-xl text-slate-600 hover:bg-slate-100 font-medium">မလုပ်တော့</button>
              <button onClick={handleSettle}
                className="px-6 py-2 text-sm bg-amber-500 text-white rounded-xl font-bold hover:bg-amber-600 shadow">
                အတည်ပြုမယ်
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─── Credit Pay Modal ─── */}
      {showCreditPay && creditPayJob && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
            <div className="px-6 py-4 bg-rose-500 rounded-t-2xl flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white">💳 အကြွေးဆပ်</h2>
                <p className="text-xs text-rose-100 mt-0.5">{creditPayJob.jobNo} · {creditPayJob.customerName}</p>
              </div>
              <button onClick={() => setShowCreditPay(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="p-6 space-y-4">
              {/* Outstanding info */}
              <div className="bg-rose-50 border border-rose-200 rounded-xl px-4 py-3 space-y-1">
                <div className="flex justify-between text-sm">
                  <span className="text-slate-600">အသားတင်ပမာဏ</span>
                  <span className="font-semibold text-slate-800">{Number(creditPayJob.netAmount || 0).toLocaleString()} Ks</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-slate-600">ပေးပြီးသား</span>
                  <span className="font-semibold text-emerald-700">{Number(creditPayJob.paidAmount || 0).toLocaleString()} Ks</span>
                </div>
                <div className="flex justify-between text-base font-bold border-t border-rose-200 pt-1.5">
                  <span className="text-rose-700">ပေးရန်ကျန်</span>
                  <span className="text-rose-700">{cpDue.toLocaleString()} Ks</span>
                </div>
              </div>

              {/* Pay Amount */}
              <div className="rounded-xl border-2 border-rose-300 bg-rose-50 px-4 py-3 space-y-3">
                <label className="block text-[11px] font-bold text-rose-700 uppercase tracking-wide">ပေးချေပမာဏ (Ks)</label>
                <div className="flex gap-2">
                  <input
                    type="number" min={0} max={cpDue}
                    value={creditPayForm.paidAmount}
                    onChange={e => setCreditPayForm(p => ({ ...p, paidAmount: e.target.value }))}
                    className="flex-1 w-0 px-3 py-2.5 rounded-lg border-2 border-rose-300 bg-white text-lg font-bold text-rose-900 focus:outline-none focus:border-rose-500 focus:ring-2 focus:ring-rose-200"
                  />
                  <button type="button"
                    onClick={() => setCreditPayForm(p => ({ ...p, paidAmount: cpDue > 0 ? String(cpDue) : '0' }))}
                    className="px-3 py-2 rounded-lg bg-rose-500 text-white text-xs font-bold hover:bg-rose-600 shrink-0">အပြည့်</button>
                </div>

                {/* Payment Method */}
                <div>
                  <label className="block text-[10px] font-semibold text-rose-700 uppercase mb-1">ငွေပေးချေနည်း *</label>
                  <select
                    value={creditPayForm.paymentMethodId}
                    onChange={e => {
                      const m = payMethods.find(m => String(m.id) === e.target.value);
                      setCreditPayForm(p => ({
                        ...p, paymentMethodId: e.target.value,
                        paymentAccountId: m?.accountId ? String(m.accountId) : '',
                      }));
                    }}
                    className="w-full border border-rose-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-rose-500">
                    <option value="">— ရွေးပါ —</option>
                    {payMethods.map(m => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                  </select>
                </div>

                <SplitPaymentEditor
                  methods={payMethods}
                  payments={creditPayForm.payments || []}
                  onChange={(next) => {
                    setCreditPayForm(p => ({
                      ...p,
                      payments: next,
                      paidAmount: paymentTotal(next) > 0 ? String(paymentTotal(next)) : p.paidAmount
                    }));
                  }}
                  label="Split Payment"
                />

                {/* Transaction No */}
                {cpRequiresTxn && (
                  <div>
                    <label className="block text-[10px] font-semibold text-rose-700 uppercase mb-1">ငွေလွှဲနံပါတ်</label>
                    <input
                      value={creditPayForm.transactionNo}
                      onChange={e => setCreditPayForm(p => ({ ...p, transactionNo: e.target.value }))}
                      placeholder="ဘဏ်/KPay ငွေလွှဲအမှတ်..."
                      className="w-full border border-rose-300 rounded-lg px-3 py-2 text-sm bg-white focus:outline-none focus:border-rose-500"
                    />
                  </div>
                )}

                {/* Summary */}
                <div className="border-t border-rose-200 pt-2 space-y-1 text-sm">
                  <div className="flex justify-between text-slate-600">
                    <span>ယခုပေးချေမည်</span>
                    <span className="font-semibold text-rose-700">{cpPaid.toLocaleString()} Ks</span>
                  </div>
                  <div className={`flex justify-between font-bold text-base ${cpRemaining > 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
                    <span>{cpRemaining > 0 ? 'ကျန်ပေးရန်' : 'အပြည့်ရှင်းပြီး ✓'}</span>
                    <span>{cpRemaining.toLocaleString()} Ks</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl">
              <button onClick={() => setShowCreditPay(false)}
                className="px-5 py-2 text-sm border rounded-xl text-slate-600 hover:bg-slate-100 font-medium">မလုပ်တော့</button>
              <button onClick={handleCreditPay}
                className="px-6 py-2 text-sm bg-rose-500 text-white rounded-xl font-bold hover:bg-rose-600 shadow">
                ငွေပေးချေမယ်
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Print Preview */}
      {printId && (
        <InvoicePrintPreview
          documentType="SERVICE_DONE"
          documentId={printId}
          title="ဝန်ဆောင်မှုပြေစာ"
          onClose={() => setPrintId(null)}
        />
      )}
    </div>
  );
}
