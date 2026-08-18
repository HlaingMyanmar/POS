import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { Printer, FileEdit, AlertTriangle, PackageCheck, RotateCcw } from 'lucide-react';
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
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

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
  DELIVERED:   'Closed / ပိတ်ပြီး',
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
  lines: [] as { serviceItemId: string; serviceItemName: string; qty: number; price: number; warrantyCovered: boolean }[],
  productParts: [] as { productId: string; productName: string; qty: number; unitPrice: number; discountAmount: number; warrantyCovered: boolean; hasSerial: boolean; serialNumbers: string[]; availableSerials: any[] }[],
};

const emptySettle = {
  finalCost: '', discountAmount: '0', foc: false,
  paidAmount: '', dueDate: '',
  paymentMethodId: '', paymentAccountId: '', transactionNo: '',
  payments: [] as PaymentTransactionDTO[],
};

const emptyRework = {
  reworkType: 'WARRANTY', problemDesc: '', assignedStaffId: '',
  replacementItemName: '', replacementSerialNo: '', replacementReason: '',
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

/* ── CustomerPicker ───────────────────────────────────────────── */
const CustomerPicker: React.FC<{
  customers: any[];
  value: string;
  onChange: (id: string) => void;
  onCreated: (customer: any) => void;
}> = ({ customers, value, onChange, onCreated }) => {
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [qForm, setQForm] = useState({ name: '', phone: '', address: '' });
  const [creating, setCreating] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const safeCustomers = Array.isArray(customers) ? customers : [];

  useEffect(() => {
    const c = safeCustomers.find(item => String(item.id) === String(value));
    setSearch(c ? (c.name ?? '') : '');
  }, [value, safeCustomers]);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const filtered = safeCustomers.filter(c => {
    if (!c) return false;
    const txt = (search || '').toLowerCase();
    const label = [c.name, c.phone, c.address].filter(Boolean).join(' ').toLowerCase();
    return label.includes(txt);
  }).slice(0, 20);

  const select = (c: any) => {
    onChange(String(c.id));
    setSearch(c.name ?? '');
    setOpen(false);
    setShowAdd(false);
  };

  const handleAdd = async () => {
    const name = qForm.name.trim();
    if (!name) {
      Swal.fire('အမှား', 'ဖောက်သည်အမည်ထည့်ပါ', 'warning');
      return;
    }
    setCreating(true);
    try {
      const created = await customerService.create({
        name,
        phone: qForm.phone.trim(),
        address: qForm.address.trim(),
      });
      if (created?.id) {
        onCreated(created);
        select(created);
        setQForm({ name: '', phone: '', address: '' });
      }
    } catch (e: any) {
      Swal.fire('အမှား', e?.message || 'ဖောက်သည်အသစ်ဖန်တီးမရပါ', 'error');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div ref={ref} className="relative">
      <input
        value={search}
        onChange={e => { setSearch(e.target.value); setOpen(true); onChange(''); }}
        onFocus={() => setOpen(true)}
        placeholder="ဖောက်သည်ရှာရန်..."
        className="w-full border rounded-xl px-3 py-2 text-sm bg-white"
      />
      {open && (
        <div className="absolute z-50 top-full left-0 right-0 mt-1 bg-white border rounded-xl shadow-xl max-h-56 overflow-y-auto">
          {filtered.map(c => (
            <div key={c.id} onClick={() => select(c)} className="px-3 py-2.5 text-sm cursor-pointer hover:bg-indigo-50 flex justify-between items-center">
              <span className="font-semibold text-slate-800">{c.name}</span>
              <span className="text-xs text-slate-400">{c.phone || ''}</span>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="px-3 py-2 text-sm text-slate-400 italic">ဖောက်သည်မတွေ့ပါ</div>
          )}
          {!showAdd && (
            <div onClick={() => { setShowAdd(true); setQForm({ name: search.trim(), phone: '', address: '' }); }} className="px-3 py-2 text-sm text-indigo-600 font-bold cursor-pointer hover:bg-indigo-50 border-t">
              + ဖောက်သည်အသစ်ထည့်
            </div>
          )}
          {showAdd && (
            <div className="p-3 border-t bg-slate-50 space-y-2">
              <p className="text-xs font-bold text-slate-600">ဖောက်သည်အသစ်</p>
              <input placeholder="အမည် *" value={qForm.name} onChange={e => setQForm(p => ({ ...p, name: e.target.value }))} className="w-full border rounded-lg px-2 py-1.5 text-sm" />
              <input placeholder="ဖုန်း" value={qForm.phone} onChange={e => setQForm(p => ({ ...p, phone: e.target.value }))} className="w-full border rounded-lg px-2 py-1.5 text-sm" />
              <input placeholder="လိပ်စာ" value={qForm.address} onChange={e => setQForm(p => ({ ...p, address: e.target.value }))} className="w-full border rounded-lg px-2 py-1.5 text-sm" />
              <button onClick={handleAdd} disabled={creating} className="w-full py-1.5 text-xs bg-indigo-600 text-white rounded-lg font-bold hover:bg-indigo-700 disabled:opacity-60">
                {creating ? 'လုပ်နေပါသည်...' : 'Save Customer'}
              </button>
            </div>
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
  const [showCreate, setShowCreate] = useState(false);
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
  const [showRework, setShowRework] = useState(false);
  const [reworkParent, setReworkParent] = useState<any>(null);
  const [reworkForm, setReworkForm] = useState(emptyRework);
  const [expandedJobFamilies, setExpandedJobFamilies] = useState<Record<string, boolean>>({});
  const PAGE_SIZE = 20;

  const load = async () => {
    // Load the complete filtered working set so a main job and its linked
    // reworks cannot be separated by server-side pagination.
    const res = await serviceJobService.getAll(0, 5000, search, dateFrom, dateTo);
    if (res.success) {
      setJobs(res.data?.content ?? []);
      setTotal(res.data?.totalElements ?? 0);
    }
  };

  const loadReferenceData = async () => {
    try {
      const [staffRes, serviceItemRes, productRes, payMethodRes, customerRes, creditTermRes] = await Promise.allSettled([
        staffService.getAllActive(),
        serviceItemService.getActive(),
        productService.getAll(),
        paymentMethodService.getAllActive(),
        customerService.getAll(),
        creditTermService.getAll(),
      ]);

      if (staffRes.status === 'fulfilled') setStaffList(Array.isArray(staffRes.value) ? staffRes.value : []);
      if (serviceItemRes.status === 'fulfilled') {
        const data = serviceItemRes.value as any;
        setServiceItems(Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : []);
      }
      if (productRes.status === 'fulfilled') {
        const data = productRes.value as any;
        setProducts(Array.isArray(data) ? data : []);
      }
      if (payMethodRes.status === 'fulfilled') setPayMethods(Array.isArray(payMethodRes.value) ? payMethodRes.value : []);
      if (customerRes.status === 'fulfilled') setCustomers(Array.isArray(customerRes.value) ? customerRes.value : []);
      if (creditTermRes.status === 'fulfilled') setCreditTerms(Array.isArray(creditTermRes.value) ? creditTermRes.value : []);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    void load();
    void loadReferenceData();
  }, [page, search, dateFrom, dateTo]);
  useRefreshOnTabActivate(() => {
    void load();
    void loadReferenceData();
  });
  useDataEvents(['Service Job', 'Booking', 'Customer', 'Staff', 'Service', 'Product', 'Payment', 'Credit'], () => {
    void load();
    void loadReferenceData();
  });

  /* ── Filtering ─────────────────────────────────────────────── */
  const matchesTab = (j: any) => {
    if (tab === 'active')   return ACTIVE_STATUSES.includes(j.status);
    if (tab === 'done')     return DONE_STATUSES.includes(j.status);
    if (tab === 'archived') return ARCHIVED_STATUSES.includes(j.status);
    if (tab === 'credit')   return Number(j.dueAmount) > 0;
    return true;
  };

  // Keep every linked rework directly under its parent job. Orphaned children
  // (for example when the parent is on another page/filter) remain visible.
  const hierarchicalJobs = (() => {
    const byParent = new Map<string, any[]>();
    jobs.forEach(job => {
      if (!job.parentJobId) return;
      const key = String(job.parentJobId);
      byParent.set(key, [...(byParent.get(key) || []), job]);
    });
    const ids = new Set(jobs.map(job => String(job.id)));
    const roots = jobs.filter(job => !job.parentJobId || !ids.has(String(job.parentJobId))).filter(matchesTab);
    const pagedRoots = roots.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
    const result: { job: any; depth: number; childCount: number; lineage: string[]; family: any }[] = [];
    const descendants = (job: any): any[] => (byParent.get(String(job.id)) || []).flatMap(child => [child, ...descendants(child)]);
    const append = (job: any, depth: number, lineage: string[], family: any) => {
      const children = (byParent.get(String(job.id)) || []).slice().sort((a, b) => String(a.receivedDate).localeCompare(String(b.receivedDate)));
      result.push({ job, depth, childCount: children.length, lineage, family });
      // Expanding the main job reveals its entire descendant chain, e.g.
      // SJ-000001 → SJ-000002 → SJ-000003, with depth-based indentation.
      if (depth > 0 || expandedJobFamilies[String(job.id)]) children.forEach(child => append(child, depth + 1, [...lineage, child.jobNo], family));
    };
    pagedRoots.forEach(job => {
      const linked = descendants(job);
      const originalCost = Number(job.netAmount ?? job.finalCost ?? 0);
      const extraCharge = linked.reduce((sum, child) => sum + Number(child.netAmount ?? child.finalCost ?? 0), 0);
      append(job, 0, [job.jobNo], {
        linkedCount: linked.length,
        warrantyCount: linked.filter(child => child.reworkType === 'WARRANTY').length,
        additionalCount: linked.filter(child => child.reworkType === 'ADDITIONAL').length,
        replacementCount: linked.filter(child => child.reworkType === 'REPLACEMENT').length,
        originalCost, extraCharge, total: originalCost + extraCharge,
      });
    });
    return result;
  })();

  const visibleRootCount = (() => {
    const ids = new Set(jobs.map(job => String(job.id)));
    return jobs.filter(job => (!job.parentJobId || !ids.has(String(job.parentJobId))) && matchesTab(job)).length;
  })();

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
        warrantyCovered: Boolean(l.warrantyCovered),
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
          warrantyCovered: Boolean(p.warrantyCovered),
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

  const handleCreate = async () => {
    const serialMismatch = form.productParts.find(p => p.hasSerial && p.productId && p.serialNumbers.length !== p.qty);
    if (serialMismatch) {
      Swal.fire('Serial Number လိုအပ်ပါ', `"${serialMismatch.productName}" အတွက် serial number ${serialMismatch.qty} ခု လိုအပ်သော်လည်း ${serialMismatch.serialNumbers.length} ခု ရွေးထားပါသည်`, 'warning');
      return;
    }
    if (!form.customerId) {
      Swal.fire('အမှား', 'ဖောက်သည်ရွေးပါ', 'error');
      return;
    }
    if (!form.itemName?.trim()) {
      Swal.fire('အမှား', 'ပစ္စည်း / ကိရိယာ အမည် ထည့်ပါ', 'error');
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
      lines:               form.lines.filter((l: any) => l.serviceItemId).map((l: any) => ({
        serviceItemId: Number(l.serviceItemId),
        qty: Number(l.qty || 1),
        warrantyMonths: 0,
        warrantyCovered: Boolean(l.warrantyCovered),
      })),
      productParts:        form.productParts.filter((p: any) => p.productId).map(p => ({
        productId: Number(p.productId),
        qty: p.qty,
        unitPrice: p.unitPrice,
        discountAmount: p.discountAmount || 0,
        serialNumbers: p.hasSerial ? p.serialNumbers : [],
        warrantyCovered: Boolean(p.warrantyCovered),
      })),
    };

    const res = await serviceJobService.create(payload);
    if (!res.success) { Swal.fire('အမှား', res.message, 'error'); return; }

    setShowCreate(false);
    setForm(emptyForm);
    load();
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
        warrantyCovered: Boolean(p.warrantyCovered),
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
      text: 'ပစ္စည်းပေးအပ်ပြီး Closed အဖြစ်မှတ်မည်',
      icon: 'question', showCancelButton: true,
      confirmButtonText: 'Closed လုပ်မည်', cancelButtonText: 'မလုပ်တော့',
    });
    if (!isConfirmed) return;
    const res = await serviceJobService.deliver(id);
    if (res.success) {
      Swal.fire({ icon: 'success', title: 'Job ပိတ်ပြီး!', timer: 1200, showConfirmButton: false });
      load();
    } else Swal.fire('အမှား', res.message, 'error');
  };

  const openRework = (job: any) => {
    setReworkParent(job);
    setReworkForm({
      ...emptyRework,
      assignedStaffId: job.assignedStaffId ? String(job.assignedStaffId) : '',
      problemDesc: job.problemDesc ?? '',
    });
    setShowRework(true);
  };

  const handleCreateRework = async () => {
    if (!reworkParent) return;
    if (!reworkForm.problemDesc.trim()) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'ပြန်လာသည့်ပြဿနာကို ဖြည့်ပါ', 'warning'); return;
    }
    if (reworkForm.reworkType === 'REPLACEMENT' && !reworkForm.replacementItemName.trim()) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'လဲပေးသည့်ပစ္စည်းအမည်ကို ဖြည့်ပါ', 'warning'); return;
    }
    const res = await serviceJobService.rework(reworkParent.id, {
      reworkType: reworkForm.reworkType,
      problemDesc: reworkForm.problemDesc.trim(),
      assignedStaffId: reworkForm.assignedStaffId ? Number(reworkForm.assignedStaffId) : null,
      replacementItemName: reworkForm.reworkType === 'REPLACEMENT' ? reworkForm.replacementItemName.trim() : null,
      replacementSerialNo: reworkForm.reworkType === 'REPLACEMENT' ? reworkForm.replacementSerialNo.trim() || null : null,
      replacementReason: reworkForm.reworkType === 'REPLACEMENT' ? reworkForm.replacementReason.trim() || null : null,
    });
    if (res.success) {
      setShowRework(false);
      setTab('active');
      Swal.fire({ icon: 'success', title: 'Linked Job အသစ်ဖန်တီးပြီး', text: res.data?.jobNo ?? '', timer: 1800, showConfirmButton: false });
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

  const totalPages = Math.ceil(visibleRootCount / PAGE_SIZE);

  const openCreate = () => {
    setForm({ ...emptyForm, lines: [], productParts: [] });
    setShowCreate(true);
  };

  /* ── Tabs config ───────────────────────────────────────────── */
  const tabDef: { key: typeof tab; label: string; count: number; active: string; inactive: string }[] = [
    { key: 'active',   label: 'ဝန်ဆောင်မှု အမှာစာ ⚡', count: counts.active,   active: 'border-blue-500 text-blue-700 bg-blue-50',    inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'done',     label: 'ပြင်ဆင်ပြီး 🔧',        count: counts.done,     active: 'border-emerald-500 text-emerald-700 bg-emerald-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'archived', label: 'Closed / ပိတ်ပြီး ✓', count: counts.archived, active: 'border-green-500 text-green-700 bg-green-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
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
          <button type="button" onClick={openCreate}
            className="ml-auto px-3.5 py-1.5 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700">
            + Service Job အသစ်
          </button>
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
              {hierarchicalJobs.map(({ job: j, depth, childCount, lineage, family }, i) => {
                const col        = STATUS_COLOR[j.status as JobStatus] ?? 'bg-slate-100 text-slate-600';
                const isCompleted = j.status === 'COMPLETED' || j.status === 'DELIVERED';
                const canEdit    = j.status !== 'DELIVERED' && j.status !== 'CANCELLED';
                const canDelete  = j.status === 'RECEIVED' || j.status === 'INSPECTING';
                const balance    = Number(j.dueAmount ?? 0);
                return (
                  <tr key={j.id} className={`transition-colors ${depth > 0 ? 'bg-amber-50/40 hover:bg-amber-50/80' : 'bg-white hover:bg-slate-50'}`}>
                    <td className="px-3 py-3 text-xs text-slate-400">{depth === 0 ? page * PAGE_SIZE + i + 1 : '↳'}</td>
                    <td className="px-3 py-3" style={{ paddingLeft: `${12 + Math.min(depth, 3) * 28}px` }}>
                      {depth > 0 && <div className="mb-1 flex items-center gap-1 text-[10px] font-black uppercase tracking-wider text-amber-700"><span className="h-px w-5 bg-amber-400"/> Rework sub job</div>}
                      {j.rework ? (
                        <div className={`min-w-[158px] overflow-hidden rounded-xl border-2 bg-white shadow-sm ${j.reworkType === 'REPLACEMENT' ? 'border-rose-300' : j.reworkType === 'WARRANTY' ? 'border-amber-300' : 'border-blue-300'}`}>
                          <div className="flex items-center gap-1.5 border-b border-slate-100 px-2.5 py-2">
                            <RotateCcw size={15} strokeWidth={2.5} className={j.reworkType === 'REPLACEMENT' ? 'text-rose-600' : j.reworkType === 'WARRANTY' ? 'text-amber-600' : 'text-blue-600'} />
                            <span className="font-mono text-sm font-black tracking-wide text-slate-900">{j.jobNo}</span>
                            <span className="ml-auto rounded-full bg-slate-100 px-1.5 py-0.5 text-[9px] font-black text-slate-500">STEP {depth}</span>
                          </div>
                          <div className="space-y-1.5 px-2.5 py-2">
                            <span className={`inline-flex rounded-md px-2 py-1 text-[10px] font-black tracking-wide ${j.reworkType === 'REPLACEMENT' ? 'bg-rose-100 text-rose-800' : j.reworkType === 'WARRANTY' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800'}`}>
                              {j.reworkType === 'REPLACEMENT' ? 'ပစ္စည်းလဲပေးခြင်း' : j.reworkType === 'WARRANTY' ? 'အာမခံပြန်ပြင်ခြင်း' : 'ထပ်ဆောင်းပြဿနာ'}
                            </span>
                            <div className="flex items-center gap-1 text-[10px] font-semibold text-slate-500">
                              <span>မူလ Job</span>
                              <span className="text-slate-400">→</span>
                              <span className="font-mono text-xs font-extrabold text-purple-700">{j.parentJobNo || `#${j.parentJobId}`}</span>
                            </div>
                            <div className="max-w-[230px] truncate rounded-md bg-slate-50 px-2 py-1 font-mono text-[9px] font-bold text-slate-500" title={lineage.join(' → ')}>{lineage.join(' → ')}</div>
                            {childCount > 0 && <div className="rounded-md bg-amber-50 px-2 py-1 text-[10px] font-black text-amber-700">↳ နောက်ထပ် Linked Job {childCount} ခု</div>}
                          </div>
                        </div>
                      ) : (
                        <div className="min-w-[260px] space-y-2">
                          <div className="flex items-center gap-2"><span className="font-mono text-xs font-bold text-purple-600 bg-purple-50 px-2 py-0.5 rounded-lg">{j.jobNo}</span><span className="rounded-md bg-purple-600 px-2 py-0.5 text-[9px] font-black text-white">MAIN JOB</span>
                          {family.linkedCount > 0 && <button type="button"
                            onClick={() => setExpandedJobFamilies(prev => ({ ...prev, [String(j.id)]: !prev[String(j.id)] }))}
                            title={expandedJobFamilies[String(j.id)] ? 'Linked Jobs ဖျောက်ရန်' : 'Linked Jobs ပြရန်'}
                            className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-100 px-2 py-1 text-[10px] font-black text-amber-800 hover:bg-amber-200">
                            <span className={`transition-transform ${expandedJobFamilies[String(j.id)] ? 'rotate-90' : ''}`}>▶</span>
                            ↻ Rework History {family.linkedCount} ခု
                            <span className="font-semibold">{expandedJobFamilies[String(j.id)] ? 'Hide' : 'Show'}</span>
                          </button>}</div>
                          {family.linkedCount > 0 && <div className="grid grid-cols-3 gap-1 rounded-lg border border-slate-100 bg-slate-50 p-1.5 text-center">
                            <div><p className="text-[8px] font-bold text-slate-400">ORIGINAL</p><b className="text-[10px] text-slate-700">{family.originalCost.toLocaleString()}</b></div>
                            <div><p className="text-[8px] font-bold text-slate-400">EXTRA</p><b className="text-[10px] text-blue-700">{family.extraCharge.toLocaleString()}</b></div>
                            <div><p className="text-[8px] font-bold text-slate-400">FAMILY TOTAL</p><b className="text-[10px] text-purple-700">{family.total.toLocaleString()}</b></div>
                          </div>}
                          {family.linkedCount > 0 && <div className="flex flex-wrap gap-1 text-[8px] font-black"><span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-800">Warranty {family.warrantyCount}</span><span className="rounded bg-blue-100 px-1.5 py-0.5 text-blue-800">Additional {family.additionalCount}</span><span className="rounded bg-rose-100 px-1.5 py-0.5 text-rose-800">Replacement {family.replacementCount}</span></div>}
                        </div>
                      )}
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
                      {j.reworkType === 'REPLACEMENT' && j.replacementItemName && (
                        <p className="mt-1 text-xs font-semibold text-rose-600">လဲပေးရန် → {j.replacementItemName}{j.replacementSerialNo ? ` (${j.replacementSerialNo})` : ''}</p>
                      )}
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
                      {j.finalCost !== null && j.finalCost !== undefined
                        ? (j.rework && Number(j.finalCost) === 0 ? <span className="font-black text-emerald-600">FREE</span> : `${Number(j.finalCost).toLocaleString()} Ks`)
                        : '—'}
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
                          <button onClick={() => handleDeliver(j.id)} title="ပစ္စည်းပေးအပ်ပြီး Job ပိတ်ရန်"
                            className="px-2 py-1 text-xs border border-green-200 rounded-lg text-green-700 hover:bg-green-50 font-bold transition-colors whitespace-nowrap">
                            ✓ Closed
                          </button>
                        )}
                        {j.status === 'DELIVERED' && (
                          <button onClick={() => openRework(j)} title="Warranty/Rework Return အတွက် Linked Job အသစ်ဖန်တီးရန်"
                            className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-amber-300 rounded-lg bg-amber-50 text-amber-800 hover:bg-amber-600 hover:text-white font-bold transition-colors whitespace-nowrap">
                            <RotateCcw size={14} /> Return
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
              {hierarchicalJobs.length === 0 && (
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
            <span>Main Job စုစုပေါင်း {visibleRootCount}{total > jobs.length ? ` (ရရှိနိုင်သော data ${jobs.length}/${total})` : ''}</span>
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

      {/* ─── Warranty / Rework / Replacement linked job ─── */}
      {showRework && reworkParent && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center overflow-y-auto py-6 px-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 bg-amber-600">
              <div>
                <h2 className="flex items-center gap-2 text-lg font-bold text-white"><RotateCcw size={20} /> Warranty / Rework Return</h2>
                <p className="text-xs text-amber-100 mt-0.5">မူလ Job: {reworkParent.jobNo} → New Linked Job</p>
              </div>
              <button onClick={() => setShowRework(false)} className="text-white/80 hover:text-white text-xl">✕</button>
            </div>
            <div className="p-6 space-y-4">
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm">
                <p className="font-bold text-slate-800">{reworkParent.itemName}</p>
                <p className="mt-1 text-xs text-slate-500">Customer: {reworkParent.customerName}</p>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase mb-2">Return အမျိုးအစား *</label>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  {[
                    ['WARRANTY', 'Warranty', 'အာမခံဖြင့် ပြန်ပြင်'],
                    ['REPLACEMENT', 'Replacement', 'ပစ္စည်းအသစ် လဲပေး'],
                    ['ADDITIONAL', 'Additional', 'ပြဿနာအသစ် / အခကြေးငွေရှိ'],
                  ].map(([value, title, note]) => (
                    <button key={value} type="button" onClick={() => setReworkForm(p => ({ ...p, reworkType: value }))}
                      className={`rounded-xl border-2 p-3 text-left transition-all ${reworkForm.reworkType === value ? 'border-amber-500 bg-amber-50 ring-2 ring-amber-200' : 'border-slate-200 hover:border-amber-300'}`}>
                      <span className="block text-sm font-extrabold text-slate-800">{title}</span>
                      <span className="block mt-1 text-[11px] leading-4 text-slate-500">{note}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-500 mb-1">ပြန်လာသည့်ပြဿနာ *</label>
                <textarea value={reworkForm.problemDesc} onChange={e => setReworkForm(p => ({ ...p, problemDesc: e.target.value }))}
                  rows={3} placeholder="ချို့ယွင်းချက်နှင့် ဖောက်သည်တိုင်ကြားချက်..." className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
              </div>

              {reworkForm.reworkType === 'REPLACEMENT' && (
                <div className="rounded-xl border border-rose-200 bg-rose-50/50 p-4 space-y-3">
                  <div className="flex items-center gap-2 font-bold text-rose-700"><PackageCheck size={18} /> လဲပေးမည့်ပစ္စည်း</div>
                  <input value={reworkForm.replacementItemName} onChange={e => setReworkForm(p => ({ ...p, replacementItemName: e.target.value }))}
                    placeholder="ပစ္စည်းအသစ် အမည် / Model *" className="w-full border rounded-xl px-3 py-2 text-sm bg-white" />
                  <input value={reworkForm.replacementSerialNo} onChange={e => setReworkForm(p => ({ ...p, replacementSerialNo: e.target.value }))}
                    placeholder="Serial Number (ရှိလျှင်)" className="w-full border rounded-xl px-3 py-2 text-sm bg-white" />
                  <textarea value={reworkForm.replacementReason} onChange={e => setReworkForm(p => ({ ...p, replacementReason: e.target.value }))}
                    rows={2} placeholder="လဲပေးရသည့်အကြောင်းရင်း..." className="w-full border rounded-xl px-3 py-2 text-sm bg-white resize-none" />
                </div>
              )}

              <div>
                <label className="block text-xs font-bold text-slate-500 mb-1">တာဝန်ခံကျွမ်းကျင်သူ</label>
                <select value={reworkForm.assignedStaffId} onChange={e => setReworkForm(p => ({ ...p, assignedStaffId: e.target.value }))}
                  className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                  <option value="">— မူလတာဝန်ခံ / မရွေးထား —</option>
                  {staffList.map((s: any) => <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>)}
                </select>
              </div>

              <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
                Linked Job အသစ်သည် <b>လက်ခံပြီး</b> အခြေအနေမှ စတင်မည်။ ပြီးလျှင် <b>Complete</b>၊ ဖောက်သည်ကို ပေးပြီးလျှင် <b>Closed</b> လုပ်နိုင်သည်။
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setShowRework(false)} className="px-4 py-2 border rounded-xl text-sm font-semibold text-slate-600">မလုပ်တော့ပါ</button>
                <button type="button" onClick={handleCreateRework} className="px-5 py-2 rounded-xl bg-amber-600 text-white text-sm font-bold hover:bg-amber-700">New Linked Job ဖန်တီးမည်</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ─── Create Modal ─── */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center overflow-y-auto py-6 px-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl">
            <div className="flex items-center justify-between px-6 py-4 bg-indigo-600 rounded-t-2xl">
              <div>
                <h2 className="text-lg font-bold text-white">➕ ဝန်ဆောင်မှု Job အသစ်</h2>
                <p className="text-xs text-indigo-200 mt-0.5">ဖောက်သည်၊ ပစ္စည်းအချက်အလက်၊ ဝန်ဆောင်မှုစာရင်းကို ထည့်ပါ</p>
              </div>
              <button onClick={() => setShowCreate(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="p-6 space-y-5">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">ဖောက်သည်</label>
                  <CustomerPicker
                    customers={customers}
                    value={form.customerId}
                    onChange={(id) => setForm(p => ({ ...p, customerId: id }))}
                    onCreated={(c) => setCustomers(prev => prev.some(item => item.id === c.id) ? prev : [c, ...prev])}
                  />
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

              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်း / ကိရိယာ အမည် *</label>
                <input value={form.itemName} onChange={e => setForm(p => ({ ...p, itemName: e.target.value }))}
                  placeholder="ဥပမာ - Apple iPhone 14 Pro"
                  className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>

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

              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအခြေအနေ</label>
                <textarea value={form.deviceConditions} onChange={e => setForm(p => ({ ...p, deviceConditions: e.target.value }))}
                  rows={2} placeholder="ပစ္စည်း၏ လက်ရှိ အခြေအနေ..."
                  className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
              </div>

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

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-slate-500 uppercase">🔧 ဝန်ဆောင်မှု / လုပ်ခ</label>
                  <button type="button"
                    onClick={() => setForm(p => ({ ...p, lines: [...p.lines, { serviceItemId: '', serviceItemName: '', qty: 1, price: 0, warrantyCovered: false }] }))}
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
                        <label className={`flex items-center gap-2 rounded-lg border px-2.5 py-2 text-xs font-bold ${line.warrantyCovered ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-500'}`}>
                          <input type="checkbox" checked={Boolean(line.warrantyCovered)}
                            onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], warrantyCovered: e.target.checked }; return { ...p, lines }; })} />
                          Warranty အကျုံးဝင် — အခမဲ့
                        </label>
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
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">ဝန်ဆောင်မှု မရှိသေးပါ</p>
                )}
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 border rounded-lg text-sm font-semibold text-slate-600">ပယ်ဖျက်</button>
                <button type="button" onClick={handleCreate} className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-semibold">Create Service Job</button>
              </div>
            </div>
          </div>
        </div>
      )}

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
                    onClick={() => setForm(p => ({ ...p, lines: [...p.lines, { serviceItemId: '', serviceItemName: '', qty: 1, price: 0, warrantyCovered: false }] }))}
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
                        <label className={`flex items-center gap-2 rounded-lg border px-2.5 py-2 text-xs font-bold ${line.warrantyCovered ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-500'}`}>
                          <input type="checkbox" checked={Boolean(line.warrantyCovered)}
                            onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], warrantyCovered: e.target.checked }; return { ...p, lines }; })} />
                          Warranty အကျုံးဝင် — အခမဲ့
                        </label>
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
                            စုစုပေါင်း: <span className={`font-bold ${line.warrantyCovered ? 'text-emerald-600' : 'text-slate-600'}`}>{line.warrantyCovered ? 'FREE' : `${(Number(line.qty || 1) * Number(line.price || 0)).toLocaleString()} Ks`}</span>
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
                    onClick={() => setForm(p => ({ ...p, productParts: [...p.productParts, { productId: '', productName: '', qty: 1, unitPrice: 0, discountAmount: 0, warrantyCovered: false, hasSerial: false, serialNumbers: [], availableSerials: [] }] }))}
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
                        <label className={`flex items-center gap-2 rounded-lg border px-2.5 py-2 text-xs font-bold ${part.warrantyCovered ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-500'}`}>
                          <input type="checkbox" checked={Boolean(part.warrantyCovered)}
                            onChange={e => setForm(p => { const pp = [...p.productParts]; pp[pi] = { ...pp[pi], warrantyCovered: e.target.checked }; return { ...p, productParts: pp }; })} />
                          Warranty အကျုံးဝင် — ပစ္စည်းဖိုးအခမဲ့
                        </label>
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
                          const sub = part.warrantyCovered ? 0 : Math.max(0, gross - disc);
                          return (
                            <div className="text-[10px] text-slate-400 flex items-center gap-2">
                              <span>စုစုပေါင်း: <span className={`font-bold ${part.warrantyCovered ? 'text-emerald-600' : 'text-slate-600'}`}>{part.warrantyCovered ? 'FREE' : `${sub.toLocaleString()} Ks`}</span></span>
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden rounded-none bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-md sm:rounded-2xl">
            <div className="px-6 py-4 bg-amber-500 rounded-t-2xl flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white">💰 ငွေရှင်းရန်</h2>
                <p className="text-xs text-amber-100 mt-0.5">{settleJob.jobNo} · {settleJob.customerName}</p>
              </div>
              <button onClick={() => setShowSettle(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto overscroll-contain p-4 sm:p-6">
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
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
                    <div className="flex flex-col gap-2 sm:flex-row">
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
                        className="w-full min-w-0 flex-1 rounded-lg border-2 border-amber-300 bg-white px-3 py-2.5 text-lg font-bold text-amber-900 focus:border-amber-500 focus:outline-none focus:ring-2 focus:ring-amber-200 sm:w-0"
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

            <div className="grid shrink-0 grid-cols-2 gap-3 border-t bg-slate-50 px-4 py-3 sm:flex sm:justify-end sm:rounded-b-2xl sm:px-6 sm:py-4">
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
