import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { Printer, FileEdit, AlertTriangle, PackageCheck, RotateCcw, Plus } from 'lucide-react';
import { serviceJobService, serviceItemService } from '../services/api';
import { staffService } from '../services/staffapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { productService } from '../services/productapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { customerService } from '../services/customerapiservice';
import { customerPaymentService } from '../services/customerpaymentapiservice';
import { creditTermService } from '../services/credittermapiservice';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { PaymentTransactionDTO } from '../types';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { getFromSession } from '../utils/storageHelper';
import { compressImageFile } from '../utils/imageCompression';
import { isRepairTechnicianRole } from '../utils/staffRole';

/* ── Status config ─────────────────────────────────────────────── */
const SERVICE_DEVICE_TYPES = ['Phone', 'Laptop', 'Computer', 'Tablet', 'Printer', 'HDD', 'SSD', 'Storage', 'Other'];
const normalizeDeviceToken = (value: unknown) => String(value ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
const deviceAliases = (value: unknown) => {
  const token = normalizeDeviceToken(value);
  const groups = [
    ['phone', 'mobile', 'smartphone', 'iphone', 'android'], ['computer', 'desktop', 'pc'],
    ['laptop', 'notebook', 'macbook'], ['hdd', 'harddisk', 'harddrive', 'storage'],
    ['ssd', 'solidstate', 'solidstatedrive', 'storage'], ['printer', 'printing'], ['tablet', 'ipad'],
  ];
  return new Set([token, ...(groups.find(group => group.some(alias => token.includes(alias))) ?? [])].filter(Boolean));
};
const supportedDeviceTokens = (item: any) => String(item?.supportedDeviceTypes ?? '').split(/[,;\n]/).map(normalizeDeviceToken).filter(Boolean);
const explicitlySupportsDevice = (item: any, deviceType: string) => {
  const supported = supportedDeviceTokens(item);
  if (!supported.length) return false;
  const aliases = deviceAliases(deviceType);
  return supported.some(token => [...aliases].some(alias => token === alias || token.includes(alias) || alias.includes(token)));
};
const inferDeviceType = (explicitType: unknown, itemName: unknown) => {
  if (String(explicitType ?? '').trim()) return String(explicitType).trim();
  const normalizedName = normalizeDeviceToken(itemName);
  return SERVICE_DEVICE_TYPES.find(type => [...deviceAliases(type)].some(alias => normalizedName.includes(alias))) ?? '';
};
const servicesForDevice = (items: any[], deviceType: string, showAll: boolean) => {
  const ranked = [...items].sort((a, b) => Number(explicitlySupportsDevice(b, deviceType)) - Number(explicitlySupportsDevice(a, deviceType)));
  const filtered = showAll || !deviceType ? ranked : ranked.filter(item => supportedDeviceTokens(item).length === 0 || explicitlySupportsDevice(item, deviceType));
  return (filtered.length ? filtered : ranked).map(item => ({ ...item, _deviceRecommended: explicitlySupportsDevice(item, deviceType) }));
};
const STATUS_LIST = ['RECEIVED','INSPECTING','IN_PROGRESS','WAITING_PARTS','COMPLETED','DELIVERED','CANCELLED'] as const;
type JobStatus = typeof STATUS_LIST[number];

const STATUS_COLOR: Record<JobStatus, string> = {
  RECEIVED:    'bg-orange-100 text-orange-700',
  INSPECTING:  'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-purple-100 text-purple-700',
  WAITING_PARTS: 'bg-amber-100 text-amber-800',
  COMPLETED:   'bg-emerald-100 text-emerald-700',
  DELIVERED:   'bg-green-100 text-green-700',
  CANCELLED:   'bg-red-100 text-red-700',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  RECEIVED:    'လက်ခံပြီး',
  INSPECTING:  'စစ်ဆေးနေ',
  IN_PROGRESS: 'ပြင်ဆင်နေ',
  WAITING_PARTS: 'ပစ္စည်းစောင့်',
  COMPLETED:   'ပြီးစီး',
  DELIVERED:   'Closed / ပိတ်ပြီး',
  CANCELLED:   'ပယ်ဖျက်',
};

const ACTIVE_STATUSES    = ['RECEIVED', 'INSPECTING', 'IN_PROGRESS', 'WAITING_PARTS'];
const DONE_STATUSES      = ['COMPLETED'];
const ARCHIVED_STATUSES  = ['DELIVERED', 'CANCELLED'];
const NEXT_STATUS: Record<string, string[]> = {
  RECEIVED: ['INSPECTING', 'CANCELLED'],
  INSPECTING: ['IN_PROGRESS', 'WAITING_PARTS', 'CANCELLED'],
  WAITING_PARTS: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['WAITING_PARTS', 'COMPLETED', 'CANCELLED'],
  COMPLETED: ['DELIVERED'], DELIVERED: [], CANCELLED: [],
};
const selectableStatuses = (current: string) => [current, ...(NEXT_STATUS[current] || [])];
const LINE_CONFIRMATION_STATUS = [
  { value: 'RECOMMENDED', label: 'အကြံပြုထားသည်', cls: 'border-amber-200 bg-amber-50 text-amber-800' },
  { value: 'INSPECTING', label: 'စစ်ဆေးဆဲ', cls: 'border-sky-200 bg-sky-50 text-sky-800' },
  { value: 'CUSTOMER_APPROVED', label: 'Customer အတည်ပြုပြီး', cls: 'border-emerald-200 bg-emerald-50 text-emerald-800' },
  { value: 'CUSTOMER_REJECTED', label: 'Customer ငြင်းပယ်', cls: 'border-rose-200 bg-rose-50 text-rose-800' },
  { value: 'IN_PROGRESS', label: 'လုပ်ဆောင်ဆဲ', cls: 'border-indigo-200 bg-indigo-50 text-indigo-800' },
  { value: 'COMPLETED', label: 'ပြီးစီး', cls: 'border-slate-200 bg-slate-100 text-slate-700' },
] as const;
const lineConfirmationMeta = (value?: string) => LINE_CONFIRMATION_STATUS.find(item => item.value === value) || LINE_CONFIRMATION_STATUS[0];
const emptyServiceLine = () => ({ serviceItemId: '', serviceItemName: '', qty: 1, price: 0, warrantyMonths: 0, warrantyCovered: false, confirmationStatus: 'RECOMMENDED' });
type WorkTab = 'active' | 'payment' | 'handover' | 'closed' | 'all';
const needsPayment = (job: any) => job.status === 'COMPLETED' && (!job.paymentStatus || Number(job.dueAmount || 0) > 0);
const readyForHandover = (job: any) => job.status === 'COMPLETED' && Boolean(job.paymentStatus) && Number(job.dueAmount || 0) <= 0;
const countWorkQueue = (rows: any[]) => ({
  active: rows.filter((j: any) => ACTIVE_STATUSES.includes(j.status)).length,
  payment: rows.filter(needsPayment).length,
  handover: rows.filter(readyForHandover).length,
});
const nextActionLabel = (job: any) => {
  if (ACTIVE_STATUSES.includes(job.status)) return job.status === 'RECEIVED' ? 'စစ်ဆေးရန်' : job.status === 'INSPECTING' ? 'ပြင်ဆင်ရန်' : 'ဆက်လက်လုပ်ဆောင်ရန်';
  if (needsPayment(job)) return Number(job.dueAmount || 0) > 0 ? 'ကျန်ငွေကောက်ရန်' : 'ငွေရှင်းရန်';
  if (readyForHandover(job)) return 'ပစ္စည်းပေးအပ်ရန်';
  if (job.status === 'DELIVERED') return 'အလုပ်ပြီးပိတ်ထား';
  return job.status === 'CANCELLED' ? 'ပယ်ဖျက်ထား' : 'အသေးစိတ်စစ်ရန်';
};

const REWORK_RESOLUTION_LABEL: Record<string, string> = {
  SERVICE_ONLY: 'Service သာ', REPLACE_SAME: 'အစားထိုး', UPGRADE: 'Upgrade', REFUND: 'ငွေပြန်အမ်း',
};
const OLD_PART_DISPOSITION_LABEL: Record<string, string> = {
  REUSE: 'ပြန်သုံးနိုင် · Stock ပြန်ဝင်', QUARANTINE: 'စစ်ဆေးရန်သီးသန့်',
  DAMAGED: 'ပျက်စီး', SUPPLIER_RETURN: 'Supplier ထံပြန်ပို့',
};

const getLocalToday = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return [year, month, day].join('-');
};

/* ── Empty states ──────────────────────────────────────────────── */
const formatPartRequests = (value?: string | null) => {
  if (!value?.trim()) return '';
  try {
    const rows = JSON.parse(value);
    if (!Array.isArray(rows)) return value;
    return rows
      .filter((row: any) => String(row?.partName ?? '').trim())
      .map((row: any) => {
        const action = ({ REPLACE: 'လဲရန်', REPAIR: 'ပြုပြင်ရန်', CHECK: 'စစ်ဆေးရန်' } as Record<string, string>)[row.action] ?? row.action ?? '';
        const qty = Number(row.qty || 1);
        const notice = String(row.notice ?? '').trim();
        return `${row.partName}${action ? ` — ${action}` : ''} × ${qty}${notice ? ` (${notice})` : ''}`;
      })
      .join('\n');
  } catch {
    return value;
  }
};
const emptyForm = {
  customerId: '', assignedStaffId: '', helperStaffId: '',
  itemName: '', deviceType: '', serialNo: '', color: '', accessories: '', itemCondition: '', problemDesc: '', diagnosisNotes: '',
  deviceConditions: '', partRequests: '', estimatedCompletion: '', estimatedCost: '', remark: '',
  status: 'RECEIVED', holdReason: '', priority: 'NORMAL',
  lines: [] as { serviceItemId: string; serviceItemName: string; qty: number; price: number; warrantyMonths: number; warrantyCovered: boolean; confirmationStatus: string }[],
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
  resolutionMode: 'SERVICE_ONLY', originalPartId: '', oldPartDisposition: 'QUARANTINE',
  replacementProductId: '', replacementQty: '1', replacementSerialNumbers: '',
  warrantyCredit: '', refundAmount: '', refundPaymentMethodId: '', refundTransactionNo: '', replacementReason: '',
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
              <span className="font-medium text-slate-700">{item._deviceRecommended ? '★ ' : ''}{item[displayField]}</span>
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
  const currentUser = useMemo(() => {
    try { return JSON.parse(getFromSession('sspd_user') || '{}') as { staffId?: number; name?: string; username?: string; roles?: string[]; permissions?: string[] }; }
    catch { return {}; }
  }, []);
  const canAssignTechnician = (currentUser.permissions || []).includes('CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN');
  const myStaffId = currentUser.staffId != null ? String(currentUser.staffId) : '';
  const canPickOwnTechnician = Boolean(myStaffId);
  const technicianFieldDisabled = !canAssignTechnician && !canPickOwnTechnician;
  const [jobs, setJobs]           = useState<any[]>([]);
  const [workQueueCounts, setWorkQueueCounts] = useState({ active: 0, payment: 0, handover: 0 });
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [tab, setTab]             = useState<WorkTab>('active');
  const [statusFilter, setStatusFilter] = useState<'all' | JobStatus>('all');
  const [search, setSearch]       = useState('');
  const [defaultDate]             = useState(getLocalToday);
  const [dateFrom, setDateFrom]   = useState(defaultDate);
  const [dateTo, setDateTo]       = useState(defaultDate);
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
  const [showAllServices, setShowAllServices] = useState(false);

  const serviceFilterDeviceType = inferDeviceType(form.deviceType, form.itemName);
  const filteredServiceItems = useMemo(() => servicesForDevice(serviceItems, serviceFilterDeviceType, showAllServices), [serviceItems, serviceFilterDeviceType, showAllServices]);
  const recommendedServiceCount = useMemo(() => serviceItems.filter(item => explicitlySupportsDevice(item, serviceFilterDeviceType)).length, [serviceItems, serviceFilterDeviceType]);
  const [showSettle, setShowSettle] = useState(false);
  const [settleJob, setSettleJob]   = useState<any>(null);
  const [settleForm, setSettleForm] = useState(emptySettle);

  const [showCreditPay, setShowCreditPay] = useState(false);
  const [creditPayJob, setCreditPayJob]   = useState<any>(null);
  const [creditPayForm, setCreditPayForm] = useState({ paidAmount: '', paymentMethodId: '', paymentAccountId: '', transactionNo: '', payments: [] as PaymentTransactionDTO[] });

  const [printId, setPrintId]   = useState<number | null>(null);
  const [logJob, setLogJob]     = useState<any>(null);
  const [showRework, setShowRework] = useState(false);
  const [reworkParent, setReworkParent] = useState<any>(null);
  const [reworkForm, setReworkForm] = useState(emptyRework);
  const [reworkAvailableSerials, setReworkAvailableSerials] = useState<any[]>([]);
  const [reworkSerialSearch, setReworkSerialSearch] = useState('');
  const [reworkSerialLoading, setReworkSerialLoading] = useState(false);
  const [expandedJobFamilies, setExpandedJobFamilies] = useState<Record<string, boolean>>({});
  const PAGE_SIZE = 20;
  const workflowTab = tab === 'active' || tab === 'payment' || tab === 'handover';

  const load = async () => {
    const ignoresDateRange = workflowTab || search.trim().length > 0;
    const effectiveDateFrom = ignoresDateRange ? '' : dateFrom;
    const effectiveDateTo = ignoresDateRange ? '' : dateTo;
    const listIsUnrestrictedQueue = workflowTab && search.trim().length === 0;
    // Load the complete filtered working set so a main job and its linked
    // reworks cannot be separated by server-side pagination.
    if (listIsUnrestrictedQueue) {
      const res = await serviceJobService.getAll(0, 5000, search, effectiveDateFrom, effectiveDateTo);
      if (res.success) {
        const rows = res.data?.content ?? [];
        setJobs(rows);
        setTotal(res.data?.totalElements ?? 0);
        setWorkQueueCounts(countWorkQueue(rows));
      }
      return;
    }
    const [listRes, queueRes] = await Promise.all([
      serviceJobService.getAll(0, 5000, search, effectiveDateFrom, effectiveDateTo),
      serviceJobService.getAll(0, 5000, '', '', ''),
    ]);
    if (listRes.success) {
      setJobs(listRes.data?.content ?? []);
      setTotal(listRes.data?.totalElements ?? 0);
    }
    if (queueRes.success) {
      setWorkQueueCounts(countWorkQueue(queueRes.data?.content ?? []));
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

      if (staffRes.status === 'fulfilled') {
        const allStaff = Array.isArray(staffRes.value) ? staffRes.value : [];
        const rows = allStaff.filter((staff: any) => {
          const role = String(staff.role || '');
          return isRepairTechnicianRole(role);
        });
        const linked = allStaff.find((staff: any) => String(staff.id) === String(currentUser.staffId));
        if (linked && !rows.some((staff: any) => String(staff.id) === String(linked.id))) rows.push(linked);
        setStaffList(rows);
        if (linked && !editId) setForm((prev) => ({ ...prev, assignedStaffId: String(linked.id) }));
      }
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
  }, [page, tab, search, dateFrom, dateTo]);
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
    const matchesStatus = statusFilter === 'all' || j.status === statusFilter;
    const matchesWorkTab = tab === 'all'
      || (tab === 'active' && ACTIVE_STATUSES.includes(j.status))
      || (tab === 'payment' && needsPayment(j))
      || (tab === 'handover' && readyForHandover(j))
      || (tab === 'closed' && ARCHIVED_STATUSES.includes(j.status));
    return matchesStatus && matchesWorkTab;
  };

  // Keep every linked rework directly under its parent job. Orphaned children
  // (for example when the parent is on another page/filter) remain visible.
  const hierarchicalJobs = (() => {
    // Filter before building the tree. An active rework with a delivered
    // parent must become a visible root instead of being hidden with it.
    const visibleJobs = jobs.filter(matchesTab);
    const byParent = new Map<string, any[]>();
    visibleJobs.forEach(job => {
      if (!job.parentJobId) return;
      const key = String(job.parentJobId);
      byParent.set(key, [...(byParent.get(key) || []), job]);
    });
    const ids = new Set(visibleJobs.map(job => String(job.id)));
    const roots = visibleJobs.filter(job => !job.parentJobId || !ids.has(String(job.parentJobId)));
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
    const visibleJobs = jobs.filter(matchesTab);
    const ids = new Set(visibleJobs.map(job => String(job.id)));
    return visibleJobs.filter(job => !job.parentJobId || !ids.has(String(job.parentJobId))).length;
  })();

  const statusFilteredJobs = statusFilter === 'all'
    ? jobs
    : jobs.filter(j => j.status === statusFilter);
  const counts = {
    active: workQueueCounts.active,
    payment: workQueueCounts.payment,
    handover: workQueueCounts.handover,
    closed: statusFilteredJobs.filter(j => ARCHIVED_STATUSES.includes(j.status)).length,
    all: statusFilteredJobs.length,
  };
  const availableStatuses = STATUS_LIST;
  const technicianChoices = (() => {
    if (canAssignTechnician) return staffList;
    const mine = staffList.filter((s: any) => String(s.id) === myStaffId);
    if (myStaffId && !mine.some((s: any) => String(s.id) === myStaffId)) {
      mine.push({ id: Number(myStaffId) || myStaffId, name: currentUser.name || currentUser.username || 'ကျွန်ုပ်', role: '' });
    }
    return mine;
  })();

  /* ── Edit handlers ─────────────────────────────────────────── */
  const openEdit = (j: any) => {
    setForm({
      customerId:          String(j.customerId ?? ''),
      assignedStaffId:     canAssignTechnician
        ? (j.assignedStaffId ? String(j.assignedStaffId) : '')
        : myStaffId,
      helperStaffId:       j.helperStaffId ? String(j.helperStaffId) : '',
      serialNo:            j.serialNo ?? '',
      color:               j.color ?? '',
      accessories:         j.accessories ?? '',
      itemCondition:       j.itemCondition ?? '',
      itemName:            j.itemName ?? '',
      deviceType:          inferDeviceType(j.deviceType, j.itemName),
      problemDesc:         j.problemDesc ?? '',
      diagnosisNotes:      j.diagnosisNotes ?? '',
      deviceConditions:    j.deviceConditions ?? '',
      partRequests:        j.partRequests ?? '',
      estimatedCompletion: j.estimatedCompletion ? j.estimatedCompletion.slice(0, 16) : '',
      estimatedCost:       j.estimatedCost ? String(j.estimatedCost) : '',
      remark:              j.remark ?? '',
      status:              j.status ?? 'RECEIVED',
      holdReason:          j.holdReason ?? '',
      priority:            j.priority ?? 'NORMAL',
      lines: (j.lines ?? []).map((l: any) => ({
        serviceItemId:   l.serviceItemId ?? '',
        serviceItemName: l.serviceItemName ?? '',
        qty:             l.qty ?? 1,
        price:           Number(l.price ?? 0),
        warrantyMonths:  Number(l.warrantyMonths || 0),
        warrantyCovered: Boolean(l.warrantyCovered),
        confirmationStatus: l.confirmationStatus || 'RECOMMENDED',
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
    setShowAllServices(false);
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
      helperStaffId:       form.helperStaffId ? Number(form.helperStaffId) : null,
      serialNo:            form.serialNo || null,
      color:               form.color || null,
      accessories:         form.accessories || null,
      itemCondition:       form.itemCondition || null,
      itemName:            form.itemName || null,
      deviceType:          form.deviceType || inferDeviceType('', form.itemName) || null,
      problemDesc:         form.problemDesc || null,
      diagnosisNotes:      form.diagnosisNotes || null,
      deviceConditions:    form.deviceConditions || null,
      partRequests:        form.partRequests || null,
      estimatedCompletion: form.estimatedCompletion ? form.estimatedCompletion + ':00' : null,
      estimatedCost:       form.estimatedCost ? Number(form.estimatedCost) : null,
      remark:              form.remark || null,
      status:              form.status,
      holdReason:          form.holdReason || null,
      priority:            form.priority || 'NORMAL',
      lines:               form.lines.filter((l: any) => l.serviceItemId).map((l: any) => ({
        serviceItemId: Number(l.serviceItemId),
        qty: Number(l.qty || 1),
        price: Number(l.price || 0),
        warrantyMonths: Number(l.warrantyMonths || 0),
        warrantyCovered: Boolean(l.warrantyCovered),
        confirmationStatus: l.confirmationStatus || 'RECOMMENDED',
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
      helperStaffId:       form.helperStaffId ? Number(form.helperStaffId) : null,
      serialNo:            form.serialNo || null,
      color:               form.color || null,
      accessories:         form.accessories || null,
      itemCondition:       form.itemCondition || null,
      itemName:            form.itemName || null,
      deviceType:          form.deviceType || inferDeviceType('', form.itemName) || null,
      problemDesc:         form.problemDesc || null,
      diagnosisNotes:      form.diagnosisNotes || null,
      deviceConditions:    form.deviceConditions || null,
      partRequests:        form.partRequests || null,
      estimatedCompletion: form.estimatedCompletion ? form.estimatedCompletion + ':00' : null,
      estimatedCost:       form.estimatedCost ? Number(form.estimatedCost) : null,
      remark:              form.remark || null,
      status:              form.status,
      holdReason:          form.holdReason || null,
      priority:            form.priority || 'NORMAL',
      lines:               form.lines.filter((l: any) => l.serviceItemId).map((l: any) => ({
        serviceItemId: Number(l.serviceItemId),
        qty: Number(l.qty || 1),
        price: Number(l.price || 0),
        warrantyMonths: Number(l.warrantyMonths || 0),
        warrantyCovered: Boolean(l.warrantyCovered),
        confirmationStatus: l.confirmationStatus || 'RECOMMENDED',
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
    const res = await serviceJobService.update(editId, payload);
    if (!res.success) { Swal.fire('အမှား', res.message, 'error'); return; }

    if (form.status !== origStatus) {
      const statusRes = await serviceJobService.updateStatus(editId, form.status, form.status === 'WAITING_PARTS' ? form.holdReason : undefined);
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
    const confirmation = await Swal.fire({
      title: 'ပစ္စည်းပေးအပ်မှု အတည်ပြုရန်', input: 'text',
      inputLabel: 'ပစ္စည်းလက်ခံယူသူအမည် / ဖုန်း', inputPlaceholder: 'အမည် သို့မဟုတ် ဖုန်းနံပါတ်',
      icon: 'question', showCancelButton: true, confirmButtonText: 'ပေးအပ်ပြီး ပိတ်မည်', cancelButtonText: 'မလုပ်တော့',
      inputValidator: value => value?.trim() ? null : 'လက်ခံယူသူအမည် သို့မဟုတ် ဖုန်း ဖြည့်ပါ',
    });
    if (!confirmation.isConfirmed) return;
    const res = await serviceJobService.deliver(id);
    if (res.success) {
      await serviceJobService.notify(id, { channel: 'HANDOVER', note: `Delivered to ${String(confirmation.value).trim()}` });
      Swal.fire({ icon: 'success', title: 'ပစ္စည်းပေးအပ်ပြီး Job ပိတ်ထားပါသည်', timer: 1400, showConfirmButton: false });
      load();
    } else Swal.fire('အမှား', res.message, 'error');
  };
  const handleVoid = async (job: any) => {
    const { isConfirmed, value } = await Swal.fire({
      title: 'Settlement ပြန်ဖျက်မည်', input: 'text', inputPlaceholder: 'အကြောင်းရင်း *',
      icon: 'warning', showCancelButton: true, confirmButtonText: 'Void', confirmButtonColor: '#ef4444',
      inputValidator: (v) => v && v.trim() ? null : 'အကြောင်းရင်းလိုအပ်သည်',
    });
    if (!isConfirmed) return;
    const res = await serviceJobService.voidSettlement(job.id, value);
    if (res.success) { Swal.fire({ icon: 'success', title: 'Void ပြီး', timer: 1200, showConfirmButton: false }); load(); }
    else Swal.fire('အမှား', res.message, 'error');
  };

  const handleApplyCredit = async (job: any) => {
    const due = Number(job.dueAmount || 0);
    if (due <= 0) { Swal.fire('မရှိပါ', 'ကျန်ငွေမရှိပါ', 'info'); return; }
    const customer = customers.find((c: any) => c.id === job.customerId);
    const advance = Number(customer?.advanceBalance || 0);
    if (advance <= 0) { Swal.fire('Credit မရှိ', 'ဖောက်သည် advance မရှိပါ', 'info'); return; }
    const max = Math.min(due, advance);
    const { isConfirmed, value } = await Swal.fire({
      title: 'Customer credit သုံးမည်', input: 'number', inputValue: max,
      text: `Advance ${advance.toLocaleString()} · Due ${due.toLocaleString()}`,
      showCancelButton: true, confirmButtonText: 'သုံးမည်',
    });
    if (!isConfirmed) return;
    try {
      await customerPaymentService.applyCredit({
        customerId: job.customerId, serviceJobId: job.id, staffId: currentUser.staffId,
        amount: Number(value || max), reason: 'Service job credit apply',
      });
      Swal.fire({ icon: 'success', title: 'Credit သုံးပြီး', timer: 1200, showConfirmButton: false });
      load();
    } catch (e: any) { Swal.fire('အမှား', e.message || 'မသုံးနိုင်ပါ', 'error'); }
  };

  const handleApproveEstimate = async (job: any) => {
    const confirmation = await Swal.fire({
      title: 'Estimate အတည်ပြုချက်', text: `${Number(job.estimatedCost || 0).toLocaleString()} Ks ကို customer အတည်ပြုပါသလား?`,
      input: 'select', inputOptions: { PHONE: 'Phone', IN_PERSON: 'In person', MESSAGE: 'Message', SIGNATURE: 'Signature' },
      inputPlaceholder: 'အတည်ပြုသည့်နည်းလမ်း', showCancelButton: true, confirmButtonText: 'အတည်ပြုမည်',
      inputValidator: value => value ? null : 'အတည်ပြုသည့်နည်းလမ်းရွေးပါ',
    });
    if (!confirmation.isConfirmed) return;
    const noteResult = await Swal.fire({ title: 'အတည်ပြုချက်မှတ်ချက်', input: 'text', inputPlaceholder: 'Customer အမည် / မှတ်ချက်', showCancelButton: true, confirmButtonText: 'သိမ်းမည်' });
    if (!noteResult.isConfirmed) return;
    const res = await serviceJobService.approveEstimate(job.id);
    if (res.success) {
      await serviceJobService.notify(job.id, { channel: confirmation.value, note: `Estimate ${Number(job.estimatedCost || 0).toLocaleString()} Ks approved${noteResult.value ? ` — ${noteResult.value}` : ''}` });
      Swal.fire({ icon: 'success', title: 'Estimate အတည်ပြုပြီး', timer: 1000, showConfirmButton: false }); load();
    } else Swal.fire('အမှား', res.message, 'error');
  };
  const openLog = async (job: any) => {
    const res = await serviceJobService.getById(job.id);
    setLogJob(res.success ? res.data : job);
  };

  const openRework = (job: any) => {
    setReworkParent(job);
    setReworkForm({
      ...emptyRework,
      assignedStaffId: canAssignTechnician && job.assignedStaffId ? String(job.assignedStaffId) : myStaffId,
      problemDesc: job.problemDesc ?? '',
    });
    setReworkAvailableSerials([]);
    setReworkSerialSearch('');
    setShowRework(true);
  };

  const handleReworkProductChange = async (product: any | null) => {
    setReworkForm(p => ({ ...p, replacementProductId: product ? String(product.id) : '', replacementSerialNumbers: '' }));
    setReworkAvailableSerials([]);
    setReworkSerialSearch('');
    if (!product || !Boolean(product.hasSerial)) return;
    setReworkSerialLoading(true);
    try {
      const serials = await productSerialService.getByProductId(Number(product.id));
      setReworkAvailableSerials((serials || []).filter((serial: any) => String(serial.status).toLowerCase() === 'available'));
    } catch {
      Swal.fire('အမှား', 'Available Serial များ ရယူ၍မရပါ', 'error');
    } finally {
      setReworkSerialLoading(false);
    }
  };

  const toggleReworkSerial = (serialNumber: string) => {
    setReworkForm(p => {
      const selected = p.replacementSerialNumbers.split(',').map(v => v.trim()).filter(Boolean);
      const exists = selected.includes(serialNumber);
      if (!exists && selected.length >= Math.max(1, Number(p.replacementQty || 1))) return p;
      const next = exists ? selected.filter(v => v !== serialNumber) : [...selected, serialNumber];
      return { ...p, replacementSerialNumbers: next.join(',') };
    });
  };
  const handleCreateRework = async () => {
    if (!reworkParent) return;
    if (!reworkForm.problemDesc.trim()) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'ပြန်လာသည့်ပြဿနာကို ဖြည့်ပါ', 'warning'); return;
    }
    const partMode = reworkForm.resolutionMode !== 'SERVICE_ONLY';
    if (partMode && !reworkForm.originalPartId) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'မူလလဲခဲ့သည့်ပစ္စည်းကို ရွေးပါ', 'warning'); return;
    }
    if (['REPLACE_SAME', 'UPGRADE'].includes(reworkForm.resolutionMode) && !reworkForm.replacementProductId) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'အသစ်လဲပေးမည့်ပစ္စည်းကို ရွေးပါ', 'warning'); return;
    }
    const replacementProduct = products.find((product: any) => String(product.id) === reworkForm.replacementProductId);
    const selectedSerialCount = reworkForm.replacementSerialNumbers.split(',').map(v => v.trim()).filter(Boolean).length;
    if (['REPLACE_SAME', 'UPGRADE'].includes(reworkForm.resolutionMode) && Boolean(replacementProduct?.hasSerial)
        && selectedSerialCount !== Math.max(1, Number(reworkForm.replacementQty || 1))) {
      Swal.fire('Serial ရွေးရန်လိုအပ်သည်', `Qty ${reworkForm.replacementQty || 1} ခုအတွက် Available Serial ${reworkForm.replacementQty || 1} ခု ရွေးပါ`, 'warning'); return;
    }
    if (reworkForm.resolutionMode === 'REFUND' && (Number(reworkForm.refundAmount) <= 0 || !reworkForm.refundPaymentMethodId)) {
      Swal.fire('အချက်အလက်လိုအပ်ပါသည်', 'ပြန်အမ်းမည့်ငွေပမာဏကို ဖြည့်ပါ', 'warning'); return;
    }
    try {
      const res = await serviceJobService.rework(reworkParent.id, {
      reworkType: reworkForm.reworkType,
      problemDesc: reworkForm.problemDesc.trim(),
      assignedStaffId: reworkForm.assignedStaffId ? Number(reworkForm.assignedStaffId) : null,
      resolutionMode: reworkForm.resolutionMode,
      originalPartId: reworkForm.originalPartId ? Number(reworkForm.originalPartId) : null,
      oldPartDisposition: partMode ? reworkForm.oldPartDisposition : null,
      replacementProductId: reworkForm.replacementProductId ? Number(reworkForm.replacementProductId) : null,
      replacementQty: Number(reworkForm.replacementQty || 1),
      replacementSerialNumbers: reworkForm.replacementSerialNumbers.split(',').map(v => v.trim()).filter(Boolean),
      warrantyCredit: reworkForm.warrantyCredit ? Number(reworkForm.warrantyCredit) : null,
      refundAmount: reworkForm.refundAmount ? Number(reworkForm.refundAmount) : null,
      refundPaymentMethodId: reworkForm.refundPaymentMethodId ? Number(reworkForm.refundPaymentMethodId) : null,
      refundTransactionNo: reworkForm.refundTransactionNo.trim() || null,
      replacementReason: reworkForm.replacementReason.trim() || null,
      });
      if (res.success) {
        setShowRework(false);
        setTab('active');
        Swal.fire({ icon: 'success', title: 'Linked Job အသစ်ဖန်တီးပြီး', text: res.data?.jobNo ?? '', timer: 1800, showConfirmButton: false });
        load();
      } else Swal.fire('အမှား', res.message, 'error');
    } catch (error: any) {
      Swal.fire('Linked Job ဖန်တီး၍မရပါ', error?.message || 'Rework အချက်အလက်နှင့် stock/serial ကို ပြန်စစ်ပါ', 'error');
    }
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
    setShowAllServices(false);
    setForm({ ...emptyForm, assignedStaffId: currentUser.staffId ? String(currentUser.staffId) : '', lines: [], productParts: [] });
    setShowCreate(true);
  };

  /* ── Tabs config ───────────────────────────────────────────── */
  const tabDef: { key: WorkTab; label: string; note: string; count: number; active: string; inactive: string }[] = [
    { key: 'active', label: 'လုပ်ဆောင်ဆဲ', note: 'ရက်မကန့်သတ်', count: counts.active, active: 'border-blue-500 text-blue-700 bg-blue-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'payment', label: 'ငွေရှင်းရန်', note: 'ရက်မကန့်သတ်', count: counts.payment, active: 'border-rose-500 text-rose-700 bg-rose-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'handover', label: 'ပေးအပ်ရန်', note: 'ရက်မကန့်သတ်', count: counts.handover, active: 'border-emerald-500 text-emerald-700 bg-emerald-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'closed', label: 'ပိတ်ပြီး', note: 'ယနေ့ / ရက်ရွေးရန်', count: counts.closed, active: 'border-slate-500 text-slate-700 bg-slate-100', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
    { key: 'all', label: 'အားလုံး', note: 'ယနေ့ / ရက်ရွေးရန်', count: counts.all, active: 'border-purple-500 text-purple-700 bg-purple-50', inactive: 'border-transparent text-slate-500 hover:bg-slate-100' },
  ];

  return (
    <div>
      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
        {/* Status Tabs */}
        <div className="flex gap-1.5 px-3 pt-3 pb-2 overflow-x-auto bg-slate-50/60 border-b">
          {tabDef.map(t => (
            <button key={t.key} onClick={() => { setTab(t.key); setStatusFilter('all'); setPage(0); }}
              className={`min-h-14 px-3.5 py-1.5 rounded-xl text-sm font-bold border-2 whitespace-nowrap transition-all flex items-center gap-2 ${tab === t.key ? t.active : t.inactive}`}>
              <span className="text-left"><span className="block">{t.label}</span><span className="block text-[10px] font-medium opacity-70">{t.note}</span></span>
              <span className={`text-xs px-1.5 py-0.5 rounded-full font-bold ${tab === t.key ? 'bg-white/60' : 'bg-slate-200 text-slate-500'}`}>
                {t.count}
              </span>
            </button>
          ))}
        </div>

        {/* Filters */}
        <div className="flex flex-col gap-2 px-3 py-2 border-b bg-white sm:flex-row sm:flex-wrap sm:items-center">
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Job#, ဝယ်သူ၊ ပစ္စည်း ရှာရန်..."
            className="min-h-11 w-full border rounded-xl px-3 py-2 text-base sm:text-sm sm:flex-1 sm:min-w-48 focus:ring-2 focus:ring-purple-400" />
          <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
            <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value as 'all' | JobStatus); setPage(0); }}
              aria-label="အခြေအနေဖြင့် စစ်ထုတ်ရန်"
              className="min-h-11 border rounded-xl px-2.5 py-2 text-sm bg-white focus:ring-2 focus:ring-purple-400">
              <option value="all">အခြေအနေအားလုံး</option>
              {availableStatuses.map(status => <option key={status} value={status}>{STATUS_LABEL[status]}</option>)}
            </select>
            <input type="date" value={dateFrom} disabled={tab === 'active' || tab === 'payment' || tab === 'handover'}
              title={workflowTab ? 'လုပ်ဆောင်ရန်ကျန်သောအလုပ်အားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : 'ရက်စွဲဖြင့် စစ်ထုတ်ရန်'}
              onChange={e => { setDateFrom(e.target.value); setPage(0); }}
              className="min-h-11 border rounded-xl px-2.5 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" />
            <input type="date" value={dateTo} disabled={tab === 'active' || tab === 'payment' || tab === 'handover'}
              title={workflowTab ? 'လုပ်ဆောင်ရန်ကျန်သောအလုပ်အားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : 'ရက်စွဲဖြင့် စစ်ထုတ်ရန်'}
              onChange={e => { setDateTo(e.target.value); setPage(0); }}
              className="min-h-11 col-span-2 border rounded-xl px-2.5 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 sm:col-span-1" />
          </div>
          <button type="button" onClick={openCreate}
            className="inline-flex min-h-11 w-full items-center justify-center gap-1.5 rounded-xl bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 sm:ml-auto sm:w-auto">
            <Plus size={18} strokeWidth={2.5} /> ဝန်ဆောင်မှု Job အသစ်
          </button>
        </div>
        <p className="border-b bg-white px-3 py-1.5 text-[11px] text-slate-500">
          {workflowTab
            ? 'လုပ်ဆောင်ရန်ကျန်သောအလုပ်အားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်'
            : 'ရက်စွဲအတိုင်း ပြသည် (ရှာဖွေလျှင် ရက်မကန့်သတ်)'}
        </p>
        <div className="grid gap-3 bg-slate-100 p-3 md:hidden">
          {hierarchicalJobs.map(({ job: j, depth }) => {
            const balance = Number(j.dueAmount || 0);
            const actionTone = needsPayment(j) ? 'border-rose-300 bg-rose-50 text-rose-700' : readyForHandover(j) ? 'border-emerald-300 bg-emerald-50 text-emerald-700' : ACTIVE_STATUSES.includes(j.status) ? 'border-blue-300 bg-blue-50 text-blue-700' : 'border-slate-300 bg-slate-50 text-slate-700';
            return <article key={`mobile-${j.id}`} className={`overflow-hidden rounded-2xl border bg-white shadow-sm ${depth > 0 ? 'ml-4 border-amber-300' : 'border-slate-200'}`}>
              <header className="flex items-center justify-between gap-2 border-b px-4 py-3"><div><span className="font-mono text-sm font-black text-purple-700">{j.jobNo}</span>{depth > 0 && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-[9px] font-black text-amber-800">LINKED REWORK</span>}</div><span className={`rounded-full px-2 py-1 text-[10px] font-black ${STATUS_COLOR[j.status as JobStatus] || 'bg-slate-100 text-slate-700'}`}>{STATUS_LABEL[j.status as JobStatus] || j.status}</span></header>
              <div className="space-y-3 p-4"><div><p className="font-black text-slate-900">{j.customerName}<span className="ml-2 text-xs font-medium text-slate-400">{j.customerPhone}</span></p><p className="mt-1 font-semibold text-slate-700">{j.itemName || 'ပစ္စည်းအမည်မရှိ'}{j.serialNo ? <span className="ml-2 font-mono text-xs text-slate-400">SN: {j.serialNo}</span> : null}</p><p className="mt-1 line-clamp-2 text-xs text-slate-500">{j.problemDesc || 'ပြဿနာမဖော်ပြထားပါ'}</p></div>
                <div className={`rounded-xl border px-3 py-2 text-sm font-black ${actionTone}`}>နောက်လုပ်ရန် → {nextActionLabel(j)}</div>
                <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-50 p-2 text-xs"><div><span className="block text-slate-400">ကျသင့်ငွေ</span><b>{Number(j.netAmount ?? j.finalCost ?? j.estimatedCost ?? 0).toLocaleString()} Ks</b></div><div><span className="block text-slate-400">ကျန်ငွေ</span><b className={balance > 0 ? 'text-rose-700' : 'text-emerald-700'}>{balance.toLocaleString()} Ks</b></div></div>
                <div className="flex flex-wrap gap-2"><button onClick={() => setPrintId(j.id)} className="min-h-10 rounded-lg border px-3 text-xs font-bold"><Printer size={14}/></button>{j.status !== 'DELIVERED' && j.status !== 'CANCELLED' && <button onClick={() => openEdit(j)} className="min-h-10 rounded-lg border border-blue-200 px-3 text-xs font-bold text-blue-700">ပြင်ဆင်မည်</button>}{j.status === 'COMPLETED' && !j.paymentStatus && <button onClick={() => openSettle(j)} className="min-h-10 rounded-lg bg-rose-600 px-3 text-xs font-bold text-white">ငွေရှင်းမည်</button>}{balance > 0 && j.paymentStatus && <button onClick={() => openCreditPay(j)} className="min-h-10 rounded-lg bg-rose-600 px-3 text-xs font-bold text-white">အကြွေးဆပ်မည်</button>}{readyForHandover(j) && <button onClick={() => handleDeliver(j.id)} className="min-h-10 rounded-lg bg-emerald-600 px-3 text-xs font-bold text-white">ပစ္စည်းပေးအပ်မည်</button>}{j.status === 'DELIVERED' && <button onClick={() => openRework(j)} className="min-h-10 rounded-lg bg-amber-500 px-3 text-xs font-bold text-white">Warranty / Rework</button>}</div>
              </div>
            </article>;
          })}
          {hierarchicalJobs.length === 0 && <div className="rounded-2xl border-2 border-dashed bg-white py-16 text-center text-sm text-slate-400">ဤအပိုင်းတွင် Job မရှိသေးပါ</div>}
        </div>
        <div className="hidden overflow-x-auto md:block">
          <table className="w-full text-sm">
            <thead className="bg-purple-600">
              <tr>
                {['#', 'Job No', 'Intake #', 'ရက်စွဲ', 'ဖောက်သည်', 'ပစ္စည်း', 'ပြုပြင်သူ', 'အခြေအနေ', 'ခန့်မှန်းကုန်ကျ', 'အပြီးသတ်', 'လက်ကျန်', 'လုပ်ဆောင်ချက်'].map(h => (
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
                  <tr key={j.id} className={`transition-colors ${depth > 0 ? 'border-b border-amber-100 bg-amber-50/40 hover:bg-amber-50/80' : 'border-b-[10px] border-slate-100 bg-white shadow-[inset_0_-1px_0_#e2e8f0] hover:bg-slate-50'}`}>
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
                            {j.resolutionMode && <div className="space-y-1.5 rounded-lg border border-slate-200 bg-slate-50 p-2 text-[10px]">
                              <div className="flex flex-wrap gap-1">
                                <span className="rounded bg-purple-100 px-1.5 py-0.5 font-black text-purple-800">{REWORK_RESOLUTION_LABEL[j.resolutionMode] || j.resolutionMode}</span>
                                {j.oldPartDisposition && <span className={`rounded px-1.5 py-0.5 font-black ${j.oldPartDisposition === 'REUSE' ? 'bg-emerald-100 text-emerald-800' : j.oldPartDisposition === 'DAMAGED' ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'}`}>{OLD_PART_DISPOSITION_LABEL[j.oldPartDisposition] || j.oldPartDisposition}</span>}
                              </div>
                              {j.originalPartName && <p><b>အဟောင်း:</b> {j.originalPartName}{j.originalPartSerialNumbers?.length ? ` · ${j.originalPartSerialNumbers.join(', ')}` : ''}</p>}
                              {j.replacementProductName && <p><b>အသစ်:</b> {j.replacementProductName} × {j.replacementQty || 1}{j.replacementPartSerialNumbers?.length ? ` · ${j.replacementPartSerialNumbers.join(', ')}` : ''}</p>}
                              {j.resolutionMode === 'UPGRADE' && <div className="grid grid-cols-3 gap-1 text-center"><span>Credit<br/><b>{Number(j.warrantyCredit || 0).toLocaleString()}</b></span><span>အသစ်တန်ဖိုး<br/><b>{Number(j.replacementPrice || 0).toLocaleString()}</b></span><span>ကွာဟငွေ<br/><b className="text-blue-700">{Number(j.customerCharge || 0).toLocaleString()}</b></span></div>}
                              {j.resolutionMode === 'REFUND' && <p className="font-black text-rose-700">ပြန်အမ်းငွေ: {Number(j.refundAmount || 0).toLocaleString()} Ks</p>}
                            </div>}
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
                    <td className="px-3 py-3 max-w-[8rem] text-xs text-slate-600"><span className="block truncate" title={j.assignedStaffName || ''}>{j.assignedStaffName || '—'}</span></td>
                    <td className="px-3 py-3">
                      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${col}`}>
                        {STATUS_LABEL[j.status as JobStatus] ?? j.status}
                      </span>
                      <div className={`mt-1.5 rounded-lg px-2 py-1 text-[10px] font-black ${needsPayment(j) ? 'bg-rose-50 text-rose-700' : readyForHandover(j) ? 'bg-emerald-50 text-emerald-700' : ACTIVE_STATUSES.includes(j.status) ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-600'}`}>နောက်လုပ်ရန် → {nextActionLabel(j)}</div>
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
                        {j.paymentStatus && j.status === 'COMPLETED' && (
                          <button onClick={() => handleVoid(j)} className="px-2 py-1 text-xs border border-rose-200 rounded-lg text-rose-700 font-bold">Void</button>
                        )}
                        {Number(j.dueAmount) > 0 && (
                          <button onClick={() => handleApplyCredit(j)} className="px-2 py-1 text-xs border border-indigo-200 rounded-lg text-indigo-700 font-bold">Credit</button>
                        )}
                        {!j.estimateApproved && j.status !== 'DELIVERED' && j.status !== 'CANCELLED' && (
                          <button onClick={() => handleApproveEstimate(j)} className="px-2 py-1 text-xs border border-slate-200 rounded-lg font-bold">Estimate ✓</button>
                        )}
                        <button onClick={() => openLog(j)} className="px-2 py-1 text-xs border rounded-lg font-bold">မှတ်တမ်း</button>
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
        <div className="fixed inset-0 z-50 flex items-stretch justify-center overflow-hidden bg-black/50 p-0 sm:items-center sm:p-4">
          <div className="flex h-[100dvh] w-full max-w-3xl flex-col overflow-hidden bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:rounded-2xl">
            <div className="z-20 flex shrink-0 items-center justify-between gap-3 bg-amber-600 px-4 py-3 sm:px-6 sm:py-4">
              <div><h2 className="flex items-center gap-2 text-lg font-bold text-white"><RotateCcw size={20} /> Warranty / Rework</h2>
                <p className="text-xs text-amber-100 mt-0.5">မူလ Job: {reworkParent.jobNo} → Linked Job အသစ်</p></div>
              <button onClick={() => setShowRework(false)} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-2xl text-white/90 hover:bg-white/15 hover:text-white">✕</button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto overscroll-contain p-3 pb-24 [scrollbar-gutter:stable] [&_input]:text-base [&_textarea]:text-base sm:space-y-5 sm:p-6 sm:pb-24 sm:[&_input]:text-sm sm:[&_textarea]:text-sm">
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm">
                <p className="font-bold text-slate-800">{reworkParent.itemName}</p><p className="mt-1 text-xs text-slate-500">Customer: {reworkParent.customerName}</p>
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-500 uppercase mb-2">အလုပ်အမျိုးအစား *</label>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">{[
                  ['WARRANTY', 'Warranty', 'အာမခံဖြင့် ပြန်ပြင်'], ['REPLACEMENT', 'Replacement', 'ပစ္စည်းပါ လဲပေး'], ['ADDITIONAL', 'Additional', 'အခကြေးငွေရှိ အလုပ်အသစ်'],
                ].map(([value, title, note]) => <button key={value} type="button" onClick={() => setReworkForm(p => ({ ...p, reworkType: value }))}
                  className={`min-h-16 rounded-xl border-2 p-3 text-left transition-colors ${reworkForm.reworkType === value ? 'border-amber-500 bg-amber-50 ring-2 ring-amber-200' : 'border-slate-200'}`}>
                  <span className="block text-sm font-extrabold">{title}</span><span className="block mt-1 text-[11px] text-slate-500">{note}</span></button>)}</div>
              </div>
              <div><label className="block text-xs font-bold text-slate-500 mb-1">ပြန်လာသည့်ပြဿနာ *</label>
                <textarea value={reworkForm.problemDesc} onChange={e => setReworkForm(p => ({ ...p, problemDesc: e.target.value }))} rows={3}
                  placeholder="ချို့ယွင်းချက်နှင့် ဖောက်သည်တိုင်ကြားချက်..." className="w-full border rounded-xl px-3 py-2 text-sm resize-none" /></div>
              <div>
                <label className="block text-xs font-bold text-slate-500 mb-2">Part ဖြေရှင်းနည်း</label>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">{[
                  ['SERVICE_ONLY', 'Service သာ', 'Part မလဲပါ'], ['REPLACE_SAME', 'အစားထိုး', 'တန်ဖိုးတူ/အာမခံ'],
                  ['UPGRADE', 'Upgrade', 'ကွာဟငွေကောက်မည်'], ['REFUND', 'ငွေပြန်အမ်း', 'Warranty credit အတွင်း'],
                ].map(([value, title, note]) => <button key={value} type="button" onClick={() => setReworkForm(p => ({ ...p, resolutionMode: value }))}
                  className={`min-h-16 rounded-xl border-2 p-3 text-left transition-colors ${reworkForm.resolutionMode === value ? 'border-blue-500 bg-blue-50' : 'border-slate-200'}`}>
                  <span className="block text-sm font-bold">{title}</span><span className="text-[10px] text-slate-500">{note}</span></button>)}</div>
              </div>
              {reworkForm.resolutionMode !== 'SERVICE_ONLY' && <div className="space-y-4 rounded-xl border border-blue-200 bg-blue-50/40 p-3 sm:p-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div><label className="block text-xs font-bold text-slate-600 mb-1">မူလ Job မှ Part *</label><SearchableSelect
                    items={(reworkParent.productParts || []).map((part: any) => ({ ...part, name: `${part.productName} × ${part.qty}`, detail: `${Number(part.subtotal || 0).toLocaleString()} Ks · ${part.serialNumbers?.join?.(', ') || part.productCode || ''}` }))}
                    value={reworkForm.originalPartId} displayField="name" subField="detail" placeholder="Part အမည်၊ code၊ serial ဖြင့်ရှာပါ..."
                    onChange={part => setReworkForm(p => ({ ...p, originalPartId: part ? String(part.id) : '', warrantyCredit: part ? String(Number(part.unitPrice || 0) * Number(part.qty || 1) - Number(part.discountAmount || 0)) : '' }))} /></div>
                  <div><label className="block text-xs font-bold text-slate-600 mb-1">ပစ္စည်းဟောင်းအခြေအနေ *</label><SearchableSelect
                    items={[{ id: 'QUARANTINE', name: 'စစ်ဆေးရန် သီးသန့်ထား', detail: 'Quarantine' }, { id: 'DAMAGED', name: 'ပျက်စီး', detail: 'Damaged' }, { id: 'SUPPLIER_RETURN', name: 'Supplier ထံပြန်ပို့', detail: 'Supplier Return' }, { id: 'REUSE', name: 'ပြန်သုံးနိုင်', detail: 'Available stock ထဲပြန်ဝင်မည်' }]}
                    value={reworkForm.oldPartDisposition} displayField="name" subField="detail" placeholder="ပစ္စည်းဟောင်းအခြေအနေ ရှာပါ..."
                    onChange={item => setReworkForm(p => ({ ...p, oldPartDisposition: item ? String(item.id) : '' }))} /></div>
                </div>
                <div><label className="block text-xs font-bold text-slate-600 mb-1">Warranty Credit (အများဆုံး မူလ Part တန်ဖိုး)</label><input type="number" min="0" value={reworkForm.warrantyCredit} onChange={e => setReworkForm(p => ({ ...p, warrantyCredit: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm bg-white" /></div>
                {['REPLACE_SAME', 'UPGRADE'].includes(reworkForm.resolutionMode) && <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div><label className="block text-xs font-bold text-slate-600 mb-1">အသစ်လဲပေးမည့် Product *</label><SearchableSelect items={products.map((product: any) => ({ ...product, reworkDetail: `${product.code || product.barcode || ""} · ${Number(product.sellingPrice || 0).toLocaleString()} Ks · Stock ${product.stockQty ?? 0}` }))} value={reworkForm.replacementProductId} displayField="name" subField="reworkDetail" placeholder="Product၊ code၊ barcode ဖြင့်ရှာပါ..." onChange={product => void handleReworkProductChange(product)} /></div>
                  <div><label className="block text-xs font-bold text-slate-600 mb-1">Qty</label><input type="number" min="1" value={reworkForm.replacementQty} onChange={e => setReworkForm(p => ({ ...p, replacementQty: e.target.value, replacementSerialNumbers: p.replacementSerialNumbers.split(",").map(v => v.trim()).filter(Boolean).slice(0, Math.max(1, Number(e.target.value || 1))).join(",") }))} className="w-full border rounded-xl px-3 py-2 text-sm bg-white" /></div>
                  <div className="sm:col-span-2"><label className="block text-xs font-bold text-slate-600 mb-1">Replacement Serial ရွေးပါ</label>
                    {!reworkForm.replacementProductId ? <div className="rounded-xl border border-dashed bg-white p-3 text-xs text-slate-500">Product ကိုအရင်ရွေးပါ</div> :
                    !Boolean(products.find((product: any) => String(product.id) === reworkForm.replacementProductId)?.hasSerial) ? <div className="rounded-xl border bg-emerald-50 p-3 text-xs font-semibold text-emerald-700">ဤ Product သည် Serial မလိုပါ။ Stock Qty ကိုသာ စစ်ဆေးမည်။</div> :
                    <div className="rounded-xl border bg-white p-3 space-y-2">
                      <div className="flex flex-col gap-2 sm:flex-row sm:items-center"><input value={reworkSerialSearch} onChange={e => setReworkSerialSearch(e.target.value)} placeholder="Serial Number ရှာပါ..." className="min-w-0 flex-1 rounded-lg border px-3 py-2 text-sm" /><span className="whitespace-nowrap text-xs font-bold text-blue-700">ရွေးထား {reworkForm.replacementSerialNumbers.split(',').filter(Boolean).length}/{Math.max(1, Number(reworkForm.replacementQty || 1))}</span></div>
                      <div className="max-h-44 overflow-y-auto rounded-lg border divide-y">{reworkSerialLoading ? <div className="p-3 text-xs text-slate-500">Serial များရယူနေသည်...</div> : reworkAvailableSerials.filter((serial: any) => String(serial.serialNumber || '').toLowerCase().includes(reworkSerialSearch.toLowerCase())).length === 0 ? <div className="p-3 text-xs text-rose-600">Available Serial မရှိပါ</div> : reworkAvailableSerials.filter((serial: any) => String(serial.serialNumber || '').toLowerCase().includes(reworkSerialSearch.toLowerCase())).map((serial: any) => { const checked = reworkForm.replacementSerialNumbers.split(',').includes(serial.serialNumber); return <label key={serial.id || serial.serialNumber} className={`flex min-h-11 cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-blue-50 ${checked ? 'bg-blue-50' : ''}`}><input type="checkbox" checked={checked} onChange={() => toggleReworkSerial(serial.serialNumber)} /><span className="font-mono font-bold text-slate-700">{serial.serialNumber}</span><span className="ml-auto text-[10px] font-bold text-emerald-600">Available</span></label>; })}</div>
                    </div>}</div>
                </div>}
                {reworkForm.resolutionMode === 'REFUND' && <div><label className="block text-xs font-bold text-slate-600 mb-1">ပြန်အမ်းမည့်ငွေ *</label><input type="number" min="0" value={reworkForm.refundAmount} onChange={e => setReworkForm(p => ({ ...p, refundAmount: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm bg-white" /><div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3"><SearchableSelect items={payMethods.map((pm: any) => ({ ...pm, name: pm.methodName, detail: pm.accountName || "Cash / Bank" }))} value={reworkForm.refundPaymentMethodId} displayField="name" subField="detail" placeholder="ပြန်အမ်းမည့် Cash/Bank ရှာပါ..." onChange={pm => setReworkForm(p => ({ ...p, refundPaymentMethodId: pm ? String(pm.id) : "" }))} /><input value={reworkForm.refundTransactionNo} onChange={e => setReworkForm(p => ({ ...p, refundTransactionNo: e.target.value }))} placeholder="Transaction No. (optional)" className="w-full border rounded-xl px-3 py-2 text-sm bg-white" /></div><p className="mt-1 text-[11px] text-emerald-700">ငွေထွက် Payment Transaction နှင့် Refund Journal ကို အလိုအလျောက်ရေးမည်။</p></div>}
                <textarea value={reworkForm.replacementReason} onChange={e => setReworkForm(p => ({ ...p, replacementReason: e.target.value }))} rows={2} placeholder="လဲ/အမ်းရသည့်အကြောင်းရင်း..." className="w-full border rounded-xl px-3 py-2 text-sm bg-white resize-none" />
              </div>}
              <div><label className="block text-xs font-bold text-slate-500 mb-1">ပြန်လည်ပြုပြင်သူ</label><SearchableSelect items={technicianChoices.map((staff: any) => ({ ...staff, detail: staff.role || staff.phone || "" }))} value={reworkForm.assignedStaffId} displayField="name" subField="detail" placeholder="ပြုပြင်သူအမည်၊ role ဖြင့်ရှာပါ..." onChange={staff => setReworkForm(p => ({ ...p, assignedStaffId: staff ? String(staff.id) : "" }))} /></div>
              <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">Replacement part သည် Linked Job Part အဖြစ်ဝင်ပြီး settlement လုပ်ချိန်တွင်သာ stock ထွက်မည်။ ပစ္စည်းဟောင်းသည် stock ထပ်မလျော့ဘဲ disposition/serial audit မှတ်တမ်းဝင်မည်။</div>
              <div className="sticky bottom-0 z-30 -mx-3 -mb-24 flex flex-col-reverse gap-2 border-t bg-white/95 px-3 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] shadow-[0_-8px_24px_rgba(15,23,42,0.08)] backdrop-blur sm:-mx-6 sm:flex-row sm:justify-end sm:px-6"><button type="button" onClick={() => setShowRework(false)} className="min-h-11 rounded-xl border px-4 py-2 text-sm font-semibold">မလုပ်တော့ပါ</button><button type="button" onClick={handleCreateRework} className="min-h-11 rounded-xl bg-amber-600 px-5 py-2 text-sm font-bold text-white hover:bg-amber-700">Linked Job ဖန်တီးမည်</button></div>
            </div>
          </div>
        </div>
      )}
      {/* ─── Create Modal ─── */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-3xl sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between bg-indigo-600 px-4 py-3 sm:rounded-t-2xl sm:px-6 sm:py-4">
              <div>
                <h2 className="text-lg font-bold text-white">ဝန်ဆောင်မှု Job အသစ်</h2>
                <p className="text-xs text-indigo-200 mt-0.5">ဖောက်သည်၊ ပစ္စည်းအချက်အလက်၊ ဝန်ဆောင်မှုစာရင်းကို ထည့်ပါ</p>
              </div>
              <button onClick={() => setShowCreate(false)} aria-label="ပိတ်ရန်" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-2xl text-white/90 hover:bg-white/15 hover:text-white">✕</button>
            </div>

            <div className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain p-4 sm:p-6 [&_input]:text-base [&_textarea]:text-base [&_select]:text-base sm:[&_input]:text-sm sm:[&_textarea]:text-sm sm:[&_select]:text-sm">
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
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">ပြုပြင်သူ</label>
                  <select value={form.assignedStaffId} disabled={technicianFieldDisabled} onChange={e => setForm(p => ({ ...p, assignedStaffId: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100">
                    {canAssignTechnician && <option value="">— မရှိ —</option>}
                    {technicianChoices.map((s: any) => <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>)}
                  </select>
                  <p className="mt-1 text-[11px] text-slate-400">{canAssignTechnician ? 'CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN ရှိ၍ အခြားပြုပြင်သူကို ရွေးနိုင်သည်။' : 'မိမိ Linked Staff ကိုသာ ရွေးနိုင်သည်။'}</p>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">အကူပြုပြင်သူ</label>
                  <select value={form.helperStaffId} disabled={!canAssignTechnician} onChange={e => setForm(p => ({ ...p, helperStaffId: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100">
                    <option value="">— မရှိ —</option>
                    {staffList.filter((staff: any) => String(staff.id) !== form.assignedStaffId).map((staff: any) => <option key={staff.id} value={staff.id}>{staff.name}</option>)}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်း / ကိရိယာ အမည် *</label>
                <input value={form.itemName} onChange={e => setForm(p => ({ ...p, itemName: e.target.value }))}
                  placeholder="ဥပမာ - Apple iPhone 14 Pro"
                  className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအမျိုးအစား *</label>
                <select value={form.deviceType} onChange={e => { setForm(p => ({ ...p, deviceType: e.target.value })); setShowAllServices(false); }} className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                  <option value="">— ရွေးပါ —</option>
                  {SERVICE_DEVICE_TYPES.map(type => <option key={type} value={type}>{type}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div><label className="block text-xs text-slate-500 mb-1">Serial No.</label><input value={form.serialNo} onChange={e => setForm(p => ({ ...p, serialNo: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">အရောင်</label><input value={form.color} onChange={e => setForm(p => ({ ...p, color: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">ပါပစ္စည်းများ</label><input value={form.accessories} onChange={e => setForm(p => ({ ...p, accessories: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">လက်ခံချိန်အခြေအနေ</label><input value={form.itemCondition} onChange={e => setForm(p => ({ ...p, itemCondition: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
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
                    rows={3} placeholder="ပြုပြင်သူ တွေ့ရှိချက်..."
                    className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
                </div>
              </div>

              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအခြေအနေ</label>
                <textarea value={form.deviceConditions} onChange={e => setForm(p => ({ ...p, deviceConditions: e.target.value }))}
                  rows={2} placeholder="ပစ္စည်း၏ လက်ရှိ အခြေအနေ..."
                  className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
              </div>
              {formatPartRequests(form.partRequests) && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-3">
                  <div className="mb-1 text-xs font-bold text-amber-800">Booking မှ Part လိုအပ်ချက်</div>
                  <div className="whitespace-pre-line text-sm text-amber-950">{formatPartRequests(form.partRequests)}</div>
                  <div className="mt-1 text-[11px] text-amber-700">Technician စစ်ဆေးပြီးမှ အတည်ပြု Part စာရင်းထဲ ထည့်ပါ။</div>
                </div>
              )}

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
                    onClick={() => setForm(p => ({ ...p, lines: [...p.lines, emptyServiceLine()] }))}
                    className="text-xs text-indigo-600 hover:underline font-bold">+ ထည့်ရန်</button>
                </div>
                <div className="mb-2 flex items-center justify-between gap-3 text-[11px] text-slate-500">
                  <span>{serviceFilterDeviceType ? (recommendedServiceCount ? `★ ${serviceFilterDeviceType} အတွက် အကြံပြု ${recommendedServiceCount} ခု` : `${serviceFilterDeviceType} အတွက် သတ်မှတ်ထားသော service မရှိသေးပါ`) : 'ပစ္စည်းအမျိုးအစားရွေးပါ'}</span>
                  <label className="flex shrink-0 items-center gap-1"><input type="checkbox" checked={showAllServices} onChange={e => setShowAllServices(e.target.checked)} /> အားလုံးပြ</label>
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
                          <label className="block text-[10px] text-slate-500 mb-0.5">အတည်ပြုမှု အခြေအနေ</label>
                          <select value={line.confirmationStatus || 'RECOMMENDED'}
                            onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], confirmationStatus: e.target.value }; return { ...p, lines }; })}
                            className={`w-full rounded-lg border px-2.5 py-2 text-xs font-bold ${lineConfirmationMeta(line.confirmationStatus).cls}`}>
                            {LINE_CONFIRMATION_STATUS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
                          </select>
                        </div>
                        <div>
                          <label className="block text-[10px] text-slate-500 mb-0.5">ဝန်ဆောင်မှု အမျိုးအစား</label>
                          <SearchableSelect
                            items={filteredServiceItems}
                            value={line.serviceItemId}
                            displayField="item"
                            subField="code"
                            placeholder="ဝန်ဆောင်မှု ရှာရန်..."
                            onChange={(si) => {
                              setForm(p => {
                                const lines = [...p.lines];
                                if (si) {
                                  lines[li] = { ...lines[li], serviceItemId: String(si.id), serviceItemName: si.item ?? '', price: Number(si.price ?? 0), warrantyMonths: Number(si.warrantyMonths || 0), warrantyCovered: Boolean(si.focDefault) };
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
            </div>

              <div className="flex shrink-0 flex-col-reverse gap-2 border-t bg-slate-50 px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:gap-3 sm:px-6 sm:rounded-b-2xl">
                <button type="button" onClick={() => setShowCreate(false)} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold text-slate-600 sm:w-auto">ပယ်ဖျက်</button>
                <button type="button" onClick={handleCreate} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white sm:w-auto">သိမ်းဆည်းမည်</button>
              </div>
          </div>
        </div>
      )}

      {/* ─── Edit Modal ─── */}
      {showEdit && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden rounded-none bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-3xl sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between gap-3 bg-purple-600 px-4 py-3 sm:px-6 sm:py-4 sm:rounded-t-2xl">
              <div>
                <h2 className="text-lg font-bold text-white">ဝန်ဆောင်မှု ပြင်ဆင်ရန်</h2>
                <p className="text-xs text-purple-200 mt-0.5">{editJobNo}</p>
              </div>
              <button onClick={() => setShowEdit(false)} aria-label="ပိတ်ရန်" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-2xl text-white/90 hover:bg-white/15 hover:text-white">✕</button>
            </div>

            <div className="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain p-4 sm:p-6 [&_input]:text-base [&_textarea]:text-base [&_select]:text-base sm:[&_input]:text-sm sm:[&_textarea]:text-sm sm:[&_select]:text-sm">
              {/* Status + Technician */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">အခြေအနေ</label>
                  <select value={form.status} onChange={e => setForm(p => ({ ...p, status: e.target.value }))}
                    className="min-h-11 w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    {selectableStatuses(origStatus).map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
                  </select>
                  <p className="mt-1 text-[11px] text-slate-400">နောက်တစ်ဆင့်သာ ရွေးနိုင်သည်။ ပြီးစီး ရွေးရန် အရင် ပြင်ဆင်နေ ဖြစ်ရမည်။</p>
                  {form.status === 'WAITING_PARTS' && (
                    <input value={form.holdReason} placeholder="ပစ္စည်းစောင့်ရသည့်အကြောင်း" className="mt-2 min-h-11 w-full border rounded-xl px-3 py-2 text-sm"
                      onChange={e => setForm(p => ({ ...p, holdReason: e.target.value }))} />
                  )}
                  <label className="mt-3 block text-xs font-bold text-slate-500 uppercase mb-1">ဦးစားပေး</label>
                  <select value={form.priority} onChange={e => setForm(p => ({ ...p, priority: e.target.value }))}
                    className="min-h-11 w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    {['LOW','NORMAL','HIGH','URGENT'].map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                  <label className="mt-3 block text-xs font-bold text-slate-500 uppercase mb-1">အကူပြုပြင်သူ</label>
                  <select value={form.helperStaffId} disabled={!canAssignTechnician} onChange={e => setForm(p => ({ ...p, helperStaffId: e.target.value }))} className="min-h-11 w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100">
                    <option value="">— မရှိ —</option>
                    {staffList.filter((staff: any) => String(staff.id) !== form.assignedStaffId).map((staff: any) => <option key={staff.id} value={staff.id}>{staff.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase mb-1">ပြုပြင်သူ</label>
                  <select value={form.assignedStaffId} disabled={technicianFieldDisabled} onChange={e => setForm(p => ({ ...p, assignedStaffId: e.target.value }))}
                    className="min-h-11 w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100">
                    {canAssignTechnician && <option value="">— မရှိ —</option>}
                    {technicianChoices.map((s: any) => <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>)}
                  </select>
                  <p className="mt-1 text-[11px] text-slate-400">{canAssignTechnician ? 'CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN ရှိ၍ အခြားပြုပြင်သူကို ရွေးနိုင်သည်။' : 'မိမိ Linked Staff ကိုသာ ရွေးနိုင်သည်။'}</p>
                </div>
              </div>

              {/* Item Name */}
              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်း / ကိရိယာ အမည်</label>
                <input value={form.itemName} onChange={e => setForm(p => ({ ...p, itemName: e.target.value }))}
                  placeholder="ဥပမာ - Apple iPhone 14 Pro"
                  className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>              <div>
                <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအမျိုးအစား</label>
                <select value={form.deviceType} onChange={e => { setForm(p => ({ ...p, deviceType: e.target.value })); setShowAllServices(false); }} className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                  <option value="">— ရွေးပါ —</option>
                  {SERVICE_DEVICE_TYPES.map(type => <option key={type} value={type}>{type}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div><label className="block text-xs text-slate-500 mb-1">Serial No.</label><input value={form.serialNo} onChange={e => setForm(p => ({ ...p, serialNo: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">အရောင်</label><input value={form.color} onChange={e => setForm(p => ({ ...p, color: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">ပါပစ္စည်းများ</label><input value={form.accessories} onChange={e => setForm(p => ({ ...p, accessories: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
                <div><label className="block text-xs text-slate-500 mb-1">လက်ခံချိန်အခြေအနေ</label><input value={form.itemCondition} onChange={e => setForm(p => ({ ...p, itemCondition: e.target.value }))} className="w-full border rounded-xl px-3 py-2 text-sm" /></div>
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
                    rows={3} placeholder="ပြုပြင်သူ တွေ့ရှိချက်..."
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
              {formatPartRequests(form.partRequests) && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-3">
                  <div className="mb-1 text-xs font-bold text-amber-800">Booking မှ Part လိုအပ်ချက်</div>
                  <div className="whitespace-pre-line text-sm text-amber-950">{formatPartRequests(form.partRequests)}</div>
                  <div className="mt-1 text-[11px] text-amber-700">Technician စစ်ဆေးပြီးမှ အတည်ပြု Part စာရင်းထဲ ထည့်ပါ။</div>
                </div>
              )}

              {/* Est. Completion + Est. Cost */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4">
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
                    onClick={() => setForm(p => ({ ...p, lines: [...p.lines, emptyServiceLine()] }))}
                    className="text-xs text-indigo-600 hover:underline font-bold">+ ထည့်ရန်</button>
                </div>
                <div className="mb-2 flex items-center justify-between gap-3 text-[11px] text-slate-500">
                  <span>{serviceFilterDeviceType ? (recommendedServiceCount ? `★ ${serviceFilterDeviceType} အတွက် အကြံပြု ${recommendedServiceCount} ခု` : `${serviceFilterDeviceType} အတွက် သတ်မှတ်ထားသော service မရှိသေးပါ`) : 'ပစ္စည်းအမျိုးအစားရွေးပါ'}</span>
                  <label className="flex shrink-0 items-center gap-1"><input type="checkbox" checked={showAllServices} onChange={e => setShowAllServices(e.target.checked)} /> အားလုံးပြ</label>
                </div>
                {form.lines.length > 0 ? (
                  <div className="space-y-2">
                    {form.lines.map((line: any, li: number) => (
                      <div key={li} className="min-w-0 space-y-2 rounded-xl border bg-slate-50 p-3">
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
                          <label className="block text-[10px] text-slate-500 mb-0.5">အတည်ပြုမှု အခြေအနေ</label>
                          <select value={line.confirmationStatus || 'RECOMMENDED'}
                            onChange={e => setForm(p => { const lines = [...p.lines]; lines[li] = { ...lines[li], confirmationStatus: e.target.value }; return { ...p, lines }; })}
                            className={`w-full rounded-lg border px-2.5 py-2 text-xs font-bold ${lineConfirmationMeta(line.confirmationStatus).cls}`}>
                            {LINE_CONFIRMATION_STATUS.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}
                          </select>
                        </div>
                        <div>
                          <label className="block text-[10px] text-slate-500 mb-0.5">ဝန်ဆောင်မှု အမျိုးအစား</label>
                          <SearchableSelect
                            items={filteredServiceItems}
                            value={line.serviceItemId}
                            displayField="item"
                            subField="code"
                            placeholder="ဝန်ဆောင်မှု ရှာရန်..."
                            onChange={(si) => {
                              setForm(p => {
                                const lines = [...p.lines];
                                if (si) {
                                  lines[li] = { ...lines[li], serviceItemId: String(si.id), serviceItemName: si.item ?? '', price: Number(si.price ?? 0), warrantyMonths: Number(si.warrantyMonths || 0), warrantyCovered: Boolean(si.focDefault) };
                                } else {
                                  lines[li] = { ...lines[li], serviceItemId: '', serviceItemName: '', price: 0 };
                                }
                                return { ...p, lines };
                              });
                            }}
                          />
                        </div>
                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
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
                            စုစုပေါင်း: <span className={`font-bold ${line.confirmationStatus === 'CUSTOMER_REJECTED' ? 'text-rose-600' : line.warrantyCovered ? 'text-emerald-600' : 'text-slate-600'}`}>{line.confirmationStatus === 'CUSTOMER_REJECTED' ? 'ငြင်းပယ် — 0 Ks' : line.warrantyCovered ? 'FREE' : `${(Number(line.qty || 1) * Number(line.price || 0)).toLocaleString()} Ks`}</span>
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
                      <div key={pi} className="min-w-0 space-y-2 rounded-xl border bg-slate-50 p-3">
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
                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
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

            <div className="flex shrink-0 flex-col-reverse gap-2 border-t bg-slate-50 px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:gap-3 sm:px-6 sm:rounded-b-2xl">
              <button onClick={() => setShowEdit(false)}
                className="min-h-11 w-full rounded-xl border px-5 text-sm font-medium text-slate-600 hover:bg-slate-100 sm:w-auto">မလုပ်တော့</button>
              <button onClick={handleSave}
                className="min-h-11 w-full rounded-xl bg-purple-600 px-6 text-sm font-bold text-white shadow hover:bg-purple-700 sm:w-auto">
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

      {logJob && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 py-6 px-4">
          <div className="w-full max-w-2xl rounded-2xl bg-white shadow-2xl">
            <div className="flex items-center justify-between rounded-t-2xl bg-slate-800 px-5 py-3 text-white">
              <h3 className="font-bold">{logJob.jobNo} မှတ်တမ်း</h3>
              <button onClick={() => setLogJob(null)}>✕</button>
            </div>
            <div className="space-y-4 p-5 text-sm">
              {logJob.overdue && <p className="rounded-lg bg-rose-50 px-3 py-2 text-rose-700 font-bold">SLA ကျော်နေသည်</p>}
              <p className="text-xs text-slate-500">Priority: {logJob.priority || 'NORMAL'} · Technician time: {logJob.technicianMinutes || 0} min</p>
              <section>
                <p className="mb-2 text-xs font-black uppercase text-slate-500">ဝန်ဆောင်မှု အတည်ပြုမှု</p>
                <div className="space-y-1">
                  {(logJob.lines || []).map((line: any, index: number) => (
                    <div key={line.id || index} className="flex items-center justify-between gap-2 rounded-lg border px-3 py-2 text-xs">
                      <span className="font-semibold text-slate-700">{line.serviceItemName || `ဝန်ဆောင်မှု ${index + 1}`}</span>
                      <span className={`rounded-full border px-2 py-0.5 font-bold ${lineConfirmationMeta(line.confirmationStatus).cls}`}>{lineConfirmationMeta(line.confirmationStatus).label}</span>
                    </div>
                  ))}
                  {(!logJob.lines || logJob.lines.length === 0) && <p className="text-slate-400">ဝန်ဆောင်မှုမရှိသေးပါ</p>}
                </div>
              </section>
              <section>
                <p className="mb-2 text-xs font-black uppercase text-slate-500">Timeline</p>
                <div className="space-y-1">
                  {(logJob.activities || []).map((a: any) => (
                    <div key={a.id} className="rounded-lg border px-3 py-2">
                      <b>{a.eventType}</b> <span className="text-xs text-slate-400">{a.fromStatus} → {a.toStatus}</span>
                      <p className="text-xs text-slate-600">{a.note} · {a.actor} · {a.occurredAt ? new Date(a.occurredAt).toLocaleString() : ''}</p>
                    </div>
                  ))}
                  {(!logJob.activities || logJob.activities.length === 0) && <p className="text-slate-400">မှတ်တမ်းမရှိသေးပါ</p>}
                </div>
              </section>
              <section>
                <p className="mb-2 text-xs font-black uppercase text-slate-500">ဓာတ်ပုံ</p>
                <input type="file" accept="image/*" className="text-xs" onChange={async e => {
                  const file = e.target.files?.[0]; if (!file) return;
                  const dataUrl = await compressImageFile(file);
                  await serviceJobService.addAttachment(logJob.id, { attachmentType: 'PHOTO', fileName: file.name, contentType: 'image/jpeg', dataUrl });
                  openLog(logJob);
                }} />
                <div className="mt-2 flex flex-wrap gap-2">
                  {(logJob.attachments || []).map((a: any) => (
                    <div key={a.id} className="relative">
                      <img src={a.dataUrl} alt="" className="h-20 w-20 rounded-lg border object-cover" />
                      <button className="absolute -right-1 -top-1 h-4 w-4 rounded-full bg-rose-500 text-[10px] text-white"
                        onClick={async () => { await serviceJobService.removeAttachment(logJob.id, a.id); openLog(logJob); }}>×</button>
                    </div>
                  ))}
                </div>
              </section>
              <section>
                <p className="mb-2 text-xs font-black uppercase text-slate-500">ဖောက်သည် အကြောင်းကြား</p>
                <div className="flex gap-2">
                  <select id="notify-channel" className="rounded-lg border px-2 py-1 text-xs">
                    <option value="CALL">Call</option>
                    <option value="SMS">SMS</option>
                    <option value="NOTE">Note</option>
                  </select>
                  <input id="notify-note" placeholder="မှတ်ချက်" className="flex-1 rounded-lg border px-2 py-1 text-xs" />
                  <button className="rounded-lg bg-indigo-600 px-3 py-1 text-xs font-bold text-white" onClick={async () => {
                    const channel = (document.getElementById('notify-channel') as HTMLSelectElement).value;
                    const note = (document.getElementById('notify-note') as HTMLInputElement).value;
                    await serviceJobService.notify(logJob.id, { channel, note });
                    openLog(logJob);
                  }}>မှတ်မည်</button>
                </div>
                <div className="mt-2 space-y-1">
                  {(logJob.notifications || []).map((n: any) => (
                    <p key={n.id} className="text-xs text-slate-600">{n.channel} · {n.note} · {n.actor}</p>
                  ))}
                </div>
              </section>
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
