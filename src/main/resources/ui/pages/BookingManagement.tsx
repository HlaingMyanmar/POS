import React, { useEffect, useRef, useState, useMemo } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { bookingService, api } from '../services/api';
import { shelfLocationService } from '../services/shelfLocationApiService';
import { staffService } from '../services/staffapiservice';
import { ApiResponse } from '../types';
import Swal from 'sweetalert2';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { getFromSession } from '../utils/storageHelper';
import { BriefcaseBusiness, Pencil, Printer, Trash2 } from 'lucide-react';

const DEVICE_TYPES = ['Phone', 'Laptop', 'Computer', 'Tablet', 'Printer', 'Other'];

const BOOKING_STATUSES = ['Pending', 'Confirmed', 'IN_STORAGE', 'Converted', 'Completed', 'Cancelled'] as const;
type BookingStatus = typeof BOOKING_STATUSES[number];
const WAITING_STATUSES: BookingStatus[] = ['Pending', 'Confirmed', 'IN_STORAGE'];

const getLocalToday = () => {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
};

const STATUS_COLOR: Record<string, string> = {
  Pending:     'bg-amber-100 text-amber-700',
  Confirmed:   'bg-blue-100 text-blue-700',
  IN_STORAGE:  'bg-teal-100 text-teal-700',
  Converted:   'bg-purple-100 text-purple-700',
  Completed:   'bg-emerald-100 text-emerald-700',
  Cancelled:   'bg-red-100 text-red-700',
};

interface DeviceEntry {
  deviceType: string;
  brand: string;
  model: string;
  serialNumber: string;
  color: string;
  problemDesc: string;
  deviceConditions: string;
}

const emptyDevice = (): DeviceEntry => ({
  deviceType: 'Phone', brand: '', model: '',
  serialNumber: '', color: '', problemDesc: '', deviceConditions: '',
});

const emptyForm = {
  customerId: '', staffId: '',
  totalAmount: '', shelfLocation: '', remark: '',
  devices: [emptyDevice()] as DeviceEntry[],
};

/* ── CustomerCombo ────────────────────────────────────────────────────── */
const CustomerCombo: React.FC<{
  customers: any[]; value: string;
  onChange: (id: string) => void; onCreated: (c: any) => void;
}> = ({ customers, value, onChange, onCreated }) => {
  const [search, setSearch]   = useState('');
  const [open, setOpen]       = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [qForm, setQForm]     = useState({ name: '', phone: '' });
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const c = customers.find(c => String(c.id) === String(value));
    setSearch(c ? c.name : '');
  }, [value, customers]);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const filtered = customers
    .filter(c => c.name.toLowerCase().includes(search.toLowerCase()) || (c.phone || '').includes(search))
    .slice(0, 20);

  const select = (c: any) => {
    onChange(String(c.id)); setSearch(c.name); setOpen(false); setShowAdd(false);
  };

  const handleAdd = async () => {
    if (!qForm.name.trim() || !qForm.phone.trim()) {
      Swal.fire('Error', 'Name and Phone required', 'error'); return;
    }
    try {
      const res = await api.post<any, ApiResponse<any>>('/v1/customers', { ...qForm, address: '-' });
      if (res.success) { onCreated(res.data); select(res.data); setQForm({ name: '', phone: '' }); }
      else Swal.fire('Error', res.message, 'error');
    } catch { Swal.fire('Error', 'Failed to create customer', 'error'); }
  };

  return (
    <div ref={ref} className="relative">
      <input
        value={search}
        onChange={e => { setSearch(e.target.value); setOpen(true); onChange(''); }}
        onFocus={() => setOpen(true)}
        placeholder="Customer name or phone..."
        className="w-full border rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
      />
      {open && (
        <div className="absolute z-50 top-full left-0 right-0 mt-1 bg-white border rounded-xl shadow-xl max-h-56 overflow-y-auto">
          {filtered.map(c => (
            <div key={c.id} onClick={() => select(c)}
              className="px-3 py-2.5 text-sm cursor-pointer hover:bg-indigo-50 flex justify-between items-center">
              <span className="font-semibold text-slate-800">{c.name}</span>
              <span className="text-xs text-slate-400">{c.phone}</span>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="px-3 py-2 text-sm text-slate-400 italic">Customer မတွေ့ပါ</div>
          )}
          {!showAdd && (
            <div
              onClick={() => { setShowAdd(true); setQForm({ name: search, phone: '' }); }}
              className="px-3 py-2 text-sm text-indigo-600 font-bold cursor-pointer hover:bg-indigo-50 border-t">
              + Customer အသစ်ထည့်
            </div>
          )}
          {showAdd && (
            <div className="p-3 border-t bg-slate-50 space-y-2">
              <p className="text-xs font-bold text-slate-600">Customer အသစ်</p>
              <input placeholder="Name *" value={qForm.name}
                onChange={e => setQForm(p => ({ ...p, name: e.target.value }))}
                className="w-full border rounded-lg px-2 py-1.5 text-sm" />
              <input placeholder="Phone *" value={qForm.phone}
                onChange={e => setQForm(p => ({ ...p, phone: e.target.value }))}
                className="w-full border rounded-lg px-2 py-1.5 text-sm" />
              <button onClick={handleAdd}
                className="w-full py-1.5 text-xs bg-indigo-600 text-white rounded-lg font-bold hover:bg-indigo-700">
                Save Customer
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

/* ── SearchableSelect ─────────────────────────────────────────────────── */
const SearchableSelect: React.FC<{
  value: string;
  options: { value: string; label: string; searchText?: string }[];
  placeholder: string;
  onChange: (value: string) => void;
}> = ({ value, options, placeholder, onChange }) => {
  const selected = options.find(option => option.value === value);
  const [search, setSearch] = useState(selected?.label || '');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => setSearch(selected?.label || ''), [value, selected?.label]);
  useEffect(() => {
    const close = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  // When the input is merely displaying the selected label, opening it must
  // show every choice. Filtering starts only after the user actually types.
  const showingSelectedLabel = Boolean(selected && search === selected.label);
  const needle = showingSelectedLabel ? '' : search.trim().toLowerCase();
  const filtered = options.filter(option =>
    `${option.label} ${option.searchText || ''}`.toLowerCase().includes(needle)
  ).slice(0, 30);

  return <div ref={ref} className="relative">
    <div className="relative">
      <input value={search} onFocus={() => setOpen(true)}
        onChange={event => { setSearch(event.target.value); setOpen(true); onChange(''); }}
        placeholder={placeholder}
        className="w-full rounded-xl border border-amber-300 bg-white py-2 pl-3 pr-9 text-sm focus:ring-2 focus:ring-amber-400" />
      {(search || value) && <button type="button" onClick={() => { setSearch(''); onChange(''); setOpen(true); }}
        className="absolute right-2 top-1/2 -translate-y-1/2 px-1 text-lg text-slate-400 hover:text-rose-500">×</button>}
    </div>
    {open && <div className="absolute z-[70] mt-1 max-h-60 w-full overflow-y-auto rounded-xl border border-amber-200 bg-white shadow-xl">
      {filtered.map(option => <button type="button" key={option.value}
        onClick={() => { onChange(option.value); setSearch(option.label); setOpen(false); }}
        className={`block w-full border-b border-slate-100 px-3 py-2.5 text-left text-sm last:border-0 hover:bg-amber-50 ${option.value === value ? 'bg-amber-50 font-bold text-amber-900' : 'text-slate-700'}`}>
        {option.label}
      </button>)}
      {filtered.length === 0 && <p className="px-3 py-3 text-sm text-slate-400">ရှာဖွေမှုနှင့် ကိုက်ညီသည့်အချက်အလက် မတွေ့ပါ</p>}
    </div>}
  </div>;
};

/* ── DeviceCard ───────────────────────────────────────────────────────── */
const DeviceCard: React.FC<{
  index: number;
  device: DeviceEntry;
  total: number;
  onChange: (idx: number, field: keyof DeviceEntry, val: string) => void;
  onRemove: (idx: number) => void;
}> = ({ index, device, total, onChange, onRemove }) => (
  <div className="border rounded-xl p-4 bg-slate-50 space-y-3">
    <div className="flex items-center justify-between">
      <span className="text-xs font-bold text-indigo-600 uppercase tracking-wide">
        ပစ္စည်း {index + 1}
      </span>
      {total > 1 && (
        <button onClick={() => onRemove(index)}
          className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-0.5 border border-red-200 rounded-lg hover:bg-red-50">
          ဖယ်ရှားမည်
        </button>
      )}
    </div>

    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
      <div>
        <label className="block text-xs text-slate-500 mb-1">အမျိုးအစား</label>
        <select value={device.deviceType}
          onChange={e => onChange(index, 'deviceType', e.target.value)}
          className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
          {DEVICE_TYPES.map(t => <option key={t}>{t}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">အမှတ်တံဆိပ် <span className="text-rose-500">*</span></label>
        <input value={device.brand}
          onChange={e => onChange(index, 'brand', e.target.value)}
          placeholder="Apple, Samsung, ASUS..."
          className="w-full border rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-400" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">မော်ဒယ်</label>
        <input value={device.model}
          onChange={e => onChange(index, 'model', e.target.value)}
          placeholder="iPhone 14 Pro..."
          className="w-full border rounded-xl px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">Serial No</label>
        <input value={device.serialNumber}
          onChange={e => onChange(index, 'serialNumber', e.target.value)}
          placeholder="ရွေးချယ်ခွင့်"
          className="w-full border rounded-xl px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">အရောင်</label>
        <input value={device.color}
          onChange={e => onChange(index, 'color', e.target.value)}
          placeholder="မည်း၊ ဖြူ..."
          className="w-full border rounded-xl px-3 py-2 text-sm" />
      </div>
    </div>

    <div>
      <label className="block text-xs text-slate-500 mb-1">ပြဿနာဖော်ပြချက် <span className="text-rose-500">*</span></label>
      <textarea value={device.problemDesc}
        onChange={e => onChange(index, 'problemDesc', e.target.value)}
        placeholder="မျက်နှာပြင်ကွဲ၊ ဖွင့်မရ၊ အားသွင်းပလပ်ပျက်..."
        rows={2} className="w-full border rounded-xl px-3 py-2 text-sm resize-none focus:ring-2 focus:ring-indigo-400" />
    </div>

    <div>
      <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအခြေအနေ</label>
      <textarea value={device.deviceConditions}
        onChange={e => onChange(index, 'deviceConditions', e.target.value)}
        placeholder="ဥပမာ - ထိပ်ဘယ်ထောင့်တွင် ကွဲကြောင်းသေး၊ Charger မပါ..."
        rows={2} className="w-full border rounded-xl px-3 py-2 text-sm resize-none" />
    </div>
  </div>
);

/* ── Main Page ────────────────────────────────────────────────────────── */
export default function BookingManagement() {
  const currentUser = useMemo(() => {
    try { return JSON.parse(getFromSession('sspd_user') || '{}') as { staffId?: number; roles?: string[]; permissions?: string[] }; }
    catch { return {}; }
  }, []);
  const canOverrideReceiver = (currentUser.roles || []).some((r) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(r))
    || (currentUser.permissions || []).includes('CAN_ACCESS_BOOKING_STAFF_OVERRIDE');
  const [bookings, setBookings]   = useState<any[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [tab, setTab]             = useState<'waiting' | 'all' | 'converted'>('waiting');
  const [statusFilter, setStatusFilter] = useState<'all' | BookingStatus>('all');
  const [customers, setCustomers] = useState<any[]>([]);
  const [staffList, setStaffList] = useState<any[]>([]);
  const [shelves, setShelves]     = useState<any[]>([]);
  const [search, setSearch]       = useState('');
  const [defaultDate]             = useState(getLocalToday);
  const [dateFrom, setDateFrom]   = useState(defaultDate);
  const [dateTo, setDateTo]       = useState(defaultDate);
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId]       = useState<number | null>(null);
  const [form, setForm]           = useState({ ...emptyForm, devices: [emptyDevice()] });
  const [printId, setPrintId]     = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [step, setStep]           = useState<'customer' | 'device' | 'review'>('customer');
  const PAGE_SIZE = 20;

  const load = async () => {
    const globalSearch = tab === 'all' && search.trim().length > 0;
    const ignoresDateRange = tab !== 'all' || globalSearch;
    const effectiveDateFrom = ignoresDateRange ? '' : dateFrom;
    const effectiveDateTo = ignoresDateRange ? '' : dateTo;
    const res = await bookingService.getAll(0, 5000, search, effectiveDateFrom, effectiveDateTo);
    if (res.success) {
      setBookings(res.data?.content ?? []);
      setTotal(res.data?.totalElements ?? 0);
    }
  };

  const loadReferenceData = async () => {
    try {
      const [customerRes, staffRes, shelfRes] = await Promise.allSettled([
        api.get<any, any>('/v1/customers?size=999'),
        staffService.getAllActive(),
        shelfLocationService.getActive(),
      ]);

      if (customerRes.status === 'fulfilled') {
        const payload = customerRes.value as any;
        setCustomers(payload?.data?.content ?? payload?.data ?? payload ?? []);
      }
      if (staffRes.status === 'fulfilled') {
        const staffRows = Array.isArray(staffRes.value) ? staffRes.value : [];
        setStaffList(staffRows);
        const linkedStaff = staffRows.find((staff: any) => staff.id === currentUser.staffId);
        if (linkedStaff && !editId) setForm((prev) => ({ ...prev, staffId: String(linkedStaff.id) }));
      }
      if (shelfRes.status === 'fulfilled') setShelves(Array.isArray(shelfRes.value) ? shelfRes.value : []);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    void load();
    void loadReferenceData();
  }, [tab, search, dateFrom, dateTo]);
  useRefreshOnTabActivate(() => {
    void load();
    void loadReferenceData();
  });
  useDataEvents(['Booking', 'Customer', 'Staff', 'Service', 'Service Job'], () => {
    void load();
    void loadReferenceData();
  });

  const openNew = () => {
    setForm({ ...emptyForm, staffId: currentUser.staffId ? String(currentUser.staffId) : '', devices: [emptyDevice()] });
    setEditId(null);
    setStep('customer');
    setShowModal(true);
  };

  const openEdit = (b: any) => {
    const devices: DeviceEntry[] = b.devices && b.devices.length > 0
      ? b.devices.map((d: any) => ({
          deviceType:       d.deviceType ?? 'Phone',
          brand:            d.brand ?? '',
          model:            d.model ?? '',
          serialNumber:     d.serialNumber ?? '',
          color:            d.color ?? '',
          problemDesc:      d.problemDesc ?? '',
          deviceConditions: d.deviceConditions ?? '',
        }))
      : [{
          deviceType:       b.deviceType ?? 'Phone',
          brand:            b.brand ?? '',
          model:            b.model ?? '',
          serialNumber:     b.serialNumber ?? '',
          color:            b.color ?? '',
          problemDesc:      '',
          deviceConditions: '',
        }];

    setForm({
      customerId:    String(b.customerId ?? ''),
      staffId:       b.staffId ? String(b.staffId) : '',
      totalAmount:   b.totalAmount ? String(b.totalAmount) : '',
      shelfLocation: b.shelfLocation ?? '',
      remark:        b.remark ?? '',
      devices,
    });
    setEditId(b.id);
    setStep('review');
    setShowModal(true);
  };

  const updateDevice = (idx: number, field: keyof DeviceEntry, val: string) => {
    setForm(prev => {
      const devices = [...prev.devices];
      devices[idx] = { ...devices[idx], [field]: val };
      return { ...prev, devices };
    });
  };

  const addDevice = () => {
    setForm(prev => ({ ...prev, devices: [...prev.devices, emptyDevice()] }));
  };

  const removeDevice = (idx: number) => {
    setForm(prev => ({ ...prev, devices: prev.devices.filter((_, i) => i !== idx) }));
  };

  const handleSave = async () => {
    if (!form.customerId) { Swal.fire('Error', 'Customer ရွေးပါ', 'error'); return; }
    const hasEmptyBrand = form.devices.some(d => !d.brand.trim());
    const hasEmptyProblem = form.devices.some(d => !d.problemDesc.trim());
    if (hasEmptyBrand) { Swal.fire('Error', 'Device တိုင်းအတွက် Brand ဖြည့်ပါ', 'error'); return; }
    if (hasEmptyProblem) { Swal.fire('Error', 'Device တိုင်းအတွက် ပြဿနာဖော်ပြချက် ဖြည့်ပါ', 'error'); return; }

    // Use first device's fields as the booking-level device info (legacy compat)
    const first = form.devices[0];
    const payload = {
      customerId:    Number(form.customerId),
      staffId:       form.staffId ? Number(form.staffId) : null,
      totalAmount:   form.totalAmount ? Number(form.totalAmount) : 0,
      shelfLocation: form.shelfLocation || null,
      remark:        form.remark || null,
      deviceType:    first.deviceType,
      brand:         first.brand,
      model:         first.model,
      serialNumber:  first.serialNumber || null,
      color:         first.color || null,
      accessories:   first.accessories || null,
      devices: form.devices.map(d => ({
        deviceType:       d.deviceType,
        brand:            d.brand,
        model:            d.model,
        serialNumber:     d.serialNumber || null,
        color:            d.color || null,
        accessories:      d.accessories || null,
        problemDesc:      d.problemDesc || null,
        deviceConditions: d.deviceConditions || null,
      })),
    };

    const res = editId
      ? await bookingService.update(editId, payload)
      : await bookingService.create(payload);

    if (res.success) {
      setShowModal(false);
      setStep('customer');
      load();
    }
    else Swal.fire('Error', res.message, 'error');
  };

  const handleConvertToJob = async (id: number) => {
    const b = bookings.find(b => b.id === id);
    const deviceCount = Math.max(1, b?.devices?.length ?? 0);
    const { isConfirmed } = await Swal.fire({
      title: 'Service Job သို့ပြောင်းမည်',
      text: `Device ${deviceCount} ခုအတွက် Job Order ${deviceCount} ခု ဖန်တီးမည်။ ပြောင်းပြီးနောက် Intake ကို မပြင်နိုင်တော့ပါ`,
      icon: 'question', showCancelButton: true,
      confirmButtonText: 'ပြောင်းမည်', cancelButtonText: 'မပြောင်းဘူး',
    });
    if (!isConfirmed) return;

    const res = await bookingService.convertToJob(id);
    if (res.success) {
      const createdCount = Array.isArray(res.data) ? res.data.length : deviceCount;
      Swal.fire({ icon: 'success', title: `Service Job ${createdCount} ခု ဖန်တီးပြီး`, timer: 1500, showConfirmButton: false });
      load();
    } else Swal.fire('Error', res.message, 'error');
  };

  const handleDelete = async (id: number) => {
    const { isConfirmed } = await Swal.fire({
      title: 'ဖျက်မည်လား?', icon: 'warning', showCancelButton: true,
      confirmButtonText: 'ဖျက်', cancelButtonText: 'မဖျက်ဘူး', confirmButtonColor: '#ef4444',
    });
    if (!isConfirmed) return;
    const res = await bookingService.remove(id);
    if (res.success) load();
  };

  const matchesTab = (booking: any) => {
    const matchesStatus = statusFilter === 'all' || booking.status === statusFilter;
    const matchesWaiting = tab !== 'waiting' || WAITING_STATUSES.includes(booking.status);
    const matchesConverted = tab !== 'converted' || booking.status === 'Converted';
    return matchesStatus && matchesWaiting && matchesConverted;
  };
  const filteredBookings = bookings.filter(matchesTab);
  const visibleBookings = filteredBookings.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.ceil(filteredBookings.length / PAGE_SIZE);
  const counts = {
    waiting: bookings.filter(b => WAITING_STATUSES.includes(b.status)).length,
    all: bookings.length,
    converted: bookings.filter(b => b.status === 'Converted').length,
  };
  const availableStatuses = tab === 'waiting'
    ? BOOKING_STATUSES.filter(status => WAITING_STATUSES.includes(status))
    : tab === 'converted' ? (['Converted'] as BookingStatus[]) : BOOKING_STATUSES;
  const tabDef = [
    { key: 'waiting' as const, label: 'စောင့်ဆိုင်းဆဲ ⚡', count: counts.waiting, active: 'border-blue-500 bg-blue-50 text-blue-700' },
    { key: 'all' as const, label: 'အားလုံး', count: counts.all, active: 'border-slate-400 bg-slate-100 text-slate-700' },
    { key: 'converted' as const, label: 'Job ပြောင်းပြီး ✓', count: counts.converted, active: 'border-purple-500 bg-purple-50 text-purple-700' },
  ];

  return (
    <div>
      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
        {/* Workflow tabs */}
        <div className="flex gap-1.5 overflow-x-auto border-b bg-slate-50/60 px-3 pb-2 pt-3">
          {tabDef.map(item => (
            <button key={item.key} type="button" onClick={() => { setTab(item.key); setStatusFilter('all'); setPage(0); }}
              className={`flex items-center gap-1.5 whitespace-nowrap rounded-lg border-2 px-3.5 py-1.5 text-sm font-bold transition-all ${tab === item.key ? item.active : 'border-transparent text-slate-500 hover:bg-slate-100'}`}>
              {item.label}
              <span className={`rounded-full px-1.5 py-0.5 text-xs font-bold ${tab === item.key ? 'bg-white/60' : 'bg-slate-200 text-slate-500'}`}>{item.count}</span>
            </button>
          ))}
        </div>

        {/* Toolbar: search + filters + button */}
        <div className="flex flex-wrap items-center gap-2 px-3 py-2.5 border-b bg-slate-50/60">
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Intake#၊ ဖောက်သည်၊ ပစ္စည်း ရှာပါ..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-44 focus:ring-2 focus:ring-indigo-300 bg-white" />
          <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value as 'all' | BookingStatus); setPage(0); }}
            aria-label="အခြေအနေဖြင့် စစ်ထုတ်ရန်" className="border rounded-lg px-2.5 py-1.5 text-sm bg-white focus:ring-2 focus:ring-indigo-300">
            <option value="all">အခြေအနေအားလုံး</option>
            {availableStatuses.map(status => <option key={status} value={status}>{status}</option>)}
          </select>
          <input type="date" value={dateFrom} disabled={tab !== 'all'}
            title={tab !== 'all' ? 'ဤ tab တွင် မှတ်တမ်းအားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : undefined}
            onChange={e => { setDateFrom(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" />
          <input type="date" value={dateTo} disabled={tab !== 'all'}
            title={tab !== 'all' ? 'ဤ tab တွင် မှတ်တမ်းအားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : undefined}
            onChange={e => { setDateTo(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" />
          <button onClick={openNew}
            className="ml-auto px-4 py-1.5 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 shadow-sm whitespace-nowrap">
            + ပစ္စည်းလက်ခံ
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-purple-600">
              <tr>
                {['#', 'လက်ခံနံပါတ်', 'ရက်စွဲ', 'ဖောက်သည်', 'ပစ္စည်း', 'ပြဿနာ', 'ခန့်မှန်းကုန်ကျ', 'ကန့်နေရာ', 'လက်ခံသူ', 'အခြေအနေ', 'လုပ်ဆောင်ချက်'].map(h => (
                  <th key={h} className="text-left px-3 py-3.5 text-[13px] font-extrabold text-white tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {visibleBookings.map((b, i) => {
                const dev       = b.devices?.[0];
                const devCount  = b.devices?.length ?? 0;
                const devStr    = [dev?.brand ?? b.brand, dev?.model ?? b.model].filter(Boolean).join(' ');
                const devType   = dev?.deviceType ?? b.deviceType;
                const problem   = dev?.problemDesc ?? b.remark ?? '';
                const col       = STATUS_COLOR[b.status] ?? 'bg-slate-100 text-slate-600';
                const canConvert = b.status === 'Pending' || b.status === 'IN_STORAGE';
                const canEdit   = b.status !== 'Converted' && b.status !== 'Cancelled' && b.status !== 'Completed';
                const isExpanded = expandedId === b.id;
                return (
                  <React.Fragment key={b.id}>
                    <tr className={`hover:bg-slate-50 transition-colors ${devCount > 1 ? 'cursor-pointer' : ''}`}
                        onClick={() => devCount > 1 && setExpandedId(isExpanded ? null : b.id)}>
                      <td className="px-3 py-3 text-xs text-slate-400">{page * PAGE_SIZE + i + 1}</td>
                      <td className="px-3 py-3">
                        <span className="font-mono text-xs font-bold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-lg">{b.invoiceNo}</span>
                      </td>
                      <td className="px-3 py-3 text-xs text-slate-500 whitespace-nowrap">{b.bookingDate?.slice(0, 10)}</td>
                      <td className="px-3 py-3">
                        <div className="font-semibold text-slate-800">{b.customerName}</div>
                        <div className="text-xs text-slate-400">{b.customerPhone}</div>
                      </td>
                      <td className="px-3 py-3">
                        <div className="font-medium text-slate-700">{devStr || '—'}</div>
                        {devType && <div className="text-xs text-slate-400">{devType}</div>}
                        {devCount > 1 && (
                          <span className="text-xs font-bold text-indigo-500">
                            {isExpanded ? '▾' : '▸'} {devCount} ခု
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-3 max-w-36">
                        <p className="text-xs text-slate-600 truncate" title={problem}>{problem || '—'}</p>
                      </td>
                      <td className="px-3 py-3 text-sm font-semibold text-slate-700">
                        {b.totalAmount && Number(b.totalAmount) > 0 ? Number(b.totalAmount).toLocaleString() + ' Ks' : '—'}
                      </td>
                      <td className="px-3 py-3 text-xs text-slate-500">{b.shelfLocation || '—'}</td>
                      <td className="px-3 py-3 text-xs text-slate-600">{b.staffName || '—'}</td>
                      <td className="px-3 py-3">
                        <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${col}`}>{b.status}</span>
                      </td>
                      <td className="px-3 py-3" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center gap-1.5 flex-nowrap">
                          <button onClick={() => setPrintId(b.id)} title="လက်ခံဖြတ်ပိုင်း ပရင့်ထုတ်ရန်" aria-label="လက်ခံဖြတ်ပိုင်း ပရင့်ထုတ်ရန်"
                            className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-violet-200 bg-violet-50 text-violet-700 shadow-sm transition-all hover:border-violet-400 hover:bg-violet-600 hover:text-white hover:shadow-md focus:outline-none focus:ring-2 focus:ring-violet-400 focus:ring-offset-1">
                            <Printer size={18} strokeWidth={2.4} />
                          </button>
                          {canConvert && (
                            <button onClick={() => handleConvertToJob(b.id)} title="Service Job အဖြစ်ပြောင်းရန်" aria-label="Service Job အဖြစ်ပြောင်းရန်"
                              className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-emerald-300 bg-emerald-50 px-2.5 text-xs font-extrabold text-emerald-700 shadow-sm whitespace-nowrap transition-all hover:border-emerald-600 hover:bg-emerald-600 hover:text-white hover:shadow-md focus:outline-none focus:ring-2 focus:ring-emerald-400 focus:ring-offset-1">
                              <BriefcaseBusiness size={17} strokeWidth={2.4} />
                              <span>Job</span>
                            </button>
                          )}
                          {canEdit && (
                            <button onClick={() => openEdit(b)} title="ပြင်ဆင်ရန်" aria-label="ပြင်ဆင်ရန်"
                              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-blue-200 bg-blue-50 text-blue-700 shadow-sm transition-all hover:border-blue-500 hover:bg-blue-600 hover:text-white hover:shadow-md focus:outline-none focus:ring-2 focus:ring-blue-400 focus:ring-offset-1">
                              <Pencil size={17} strokeWidth={2.5} />
                            </button>
                          )}
                          {canEdit && (
                            <button onClick={() => handleDelete(b.id)} title="ဖျက်ရန်" aria-label="ဖျက်ရန်"
                              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-rose-200 bg-rose-50 text-rose-700 shadow-sm transition-all hover:border-rose-500 hover:bg-rose-600 hover:text-white hover:shadow-md focus:outline-none focus:ring-2 focus:ring-rose-400 focus:ring-offset-1">
                              <Trash2 size={17} strokeWidth={2.4} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                    {/* Expanded device list */}
                    {isExpanded && devCount > 1 && (
                      <tr>
                        <td colSpan={11} className="px-6 py-3 bg-indigo-50/50">
                          <div className="grid gap-2">
                            {b.devices.map((d: any, di: number) => (
                              <div key={di} className="flex items-start gap-4 bg-white rounded-lg px-4 py-2.5 border border-indigo-100 text-xs">
                                <span className="font-bold text-indigo-500 shrink-0">#{di + 1}</span>
                                <div className="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-x-4 gap-y-1">
                                  <div>
                                    <span className="text-slate-400">ပစ္စည်း: </span>
                                    <span className="font-semibold text-slate-700">{[d.brand, d.model].filter(Boolean).join(' ') || '—'}</span>
                                  </div>
                                  <div>
                                    <span className="text-slate-400">အမျိုးအစား: </span>
                                    <span className="text-slate-600">{d.deviceType || '—'}</span>
                                  </div>
                                  <div>
                                    <span className="text-slate-400">Serial: </span>
                                    <span className="font-mono text-slate-600">{d.serialNumber || '—'}</span>
                                  </div>
                                  <div>
                                    <span className="text-slate-400">အရောင်: </span>
                                    <span className="text-slate-600">{d.color || '—'}</span>
                                  </div>
                                </div>
                                {d.problemDesc && (
                                  <div className="text-amber-700 shrink-0 max-w-48 truncate" title={d.problemDesc}>
                                    {d.problemDesc}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
              {bookings.length === 0 && (
                <tr>
                  <td colSpan={11} className="py-16 text-center">
                    <div className="text-5xl mb-3">📋</div>
                    <p className="text-sm text-slate-400">မှတ်တမ်းမရှိသေးပါ</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="px-4 py-3 border-t flex items-center justify-between text-xs text-slate-500">
            <span>စုစုပေါင်း {total} မှတ်တမ်း</span>
            <div className="flex gap-2 items-center">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-40 hover:bg-slate-50">← အရှေ့</button>
              <span>စာမျက်နှာ {page + 1} / {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-40 hover:bg-slate-50">နောက် →</button>
            </div>
          </div>
        )}
      </div>

      {/* ─── Intake Modal ─── */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center overflow-y-auto py-6 px-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl">
            <div className="flex items-center justify-between px-6 py-4 bg-indigo-600 rounded-t-2xl">
              <div>
                <h2 className="text-lg font-bold text-white">
                  {editId ? '✏ ပြင်ဆင်မည်' : '+ ပစ္စည်းလက်ခံ'}
                </h2>
                <p className="text-xs text-indigo-200 mt-0.5">ပစ္စည်းလက်ခံဖောင်</p>
              </div>
              <button onClick={() => setShowModal(false)} className="text-white/70 hover:text-white text-xl leading-none">✕</button>
            </div>

            <div className="p-6 space-y-5">
              <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <button type="button" onClick={() => setStep('customer')} className={`px-3 py-1.5 rounded-full ${step === 'customer' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`}>1. Customer</button>
                <button type="button" onClick={() => form.customerId && setStep('device')} className={`px-3 py-1.5 rounded-full ${step === 'device' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`} disabled={!form.customerId}>2. Device</button>
                <button type="button" onClick={() => form.devices.every(d => d.brand.trim() && d.problemDesc.trim()) && setStep('review')} className={`px-3 py-1.5 rounded-full ${step === 'review' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`} disabled={!form.customerId || !form.devices.every(d => d.brand.trim() && d.problemDesc.trim())}>3. Review</button>
              </div>

              {step === 'customer' && (
                <section className="space-y-4">
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ဖောက်သည် *</p>
                    <CustomerCombo
                      customers={customers}
                      value={form.customerId}
                      onChange={id => setForm(p => ({ ...p, customerId: id }))}
                      onCreated={(c) => {
                        setCustomers(prev => prev.some(x => x.id === c.id) ? prev : [c, ...prev]);
                      }}
                    />
                  </div>
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                    <p className="font-semibold text-slate-700">လုပ်ငန်းစဉ်</p>
                    <ul className="list-disc ml-5 mt-2 space-y-1">
                      <li>ဖောက်သည်ကို ရွေးပါ</li>
                      <li>ပစ္စည်းအချက်အလက်ကို ဖြည့်ပါ</li>
                      <li>ပြဿနာနှင့် အခြေအနေကို review လုပ်ပြီး save လုပ်ပါ</li>
                    </ul>
                  </div>
                  <div className="flex justify-end">
                    <button type="button" onClick={() => form.customerId && setStep('device')} disabled={!form.customerId} className="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold disabled:opacity-50">Next</button>
                  </div>
                </section>
              )}

              {step === 'device' && (
                <section className="space-y-4">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wide">
                      ပစ္စည်း(များ) * &nbsp;
                      <span className="text-indigo-500 font-normal normal-case">
                        ({form.devices.length} ခု → Job Order {form.devices.length} ခု ဖန်တီးမည်)
                      </span>
                    </p>
                    <button onClick={addDevice} className="text-xs font-bold text-indigo-600 border border-indigo-200 px-3 py-1 rounded-lg hover:bg-indigo-50">
                      + ပစ္စည်းထပ်ထည့်
                    </button>
                  </div>
                  <div className="space-y-3">
                    {form.devices.map((device, idx) => (
                      <DeviceCard key={idx} index={idx} device={device} total={form.devices.length} onChange={updateDevice} onRemove={removeDevice} />
                    ))}
                  </div>
                  <div className="flex justify-between">
                    <button type="button" onClick={() => setStep('customer')} className="px-4 py-2 rounded-lg border text-sm font-semibold text-slate-600">Back</button>
                    <button type="button" onClick={() => form.devices.every(d => d.brand.trim() && d.problemDesc.trim()) && setStep('review')} disabled={!form.devices.every(d => d.brand.trim() && d.problemDesc.trim())} className="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold disabled:opacity-50">Review</button>
                  </div>
                </section>
              )}

              {step === 'review' && (
                <section className="space-y-4">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-bold text-slate-500 uppercase tracking-wide">Review</p>
                      <span className="text-xs text-slate-500">{form.devices.length} device(s)</span>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                      <div>
                        <p className="text-slate-500">ဖောက်သည်</p>
                        <p className="font-semibold text-slate-800">{customers.find(c => String(c.id) === String(form.customerId))?.name || '—'}</p>
                      </div>
                      <div>
                        <p className="text-slate-500">ဝန်ထမ်း</p>
                        <p className="font-semibold text-slate-800">{staffList.find(s => String(s.id) === String(form.staffId))?.name || '—'}</p>
                      </div>
                      <div>
                        <p className="text-slate-500">ပစ္စည်းထားသည့်နေရာ</p>
                        <p className="font-semibold text-slate-800">{form.shelfLocation || '— မသတ်မှတ်ရသေး —'}</p>
                      </div>
                    </div>
                    <div className="space-y-2">
                      {form.devices.map((device, idx) => (
                        <div key={idx} className="rounded-lg border border-slate-200 bg-white p-3">
                          <p className="font-semibold text-slate-800">{idx + 1}. {device.brand || 'Brand not set'}</p>
                          <p className="text-xs text-slate-500 mt-1">{device.problemDesc || 'Problem not entered'}</p>
                        </div>
                      ))}
                    </div>
                  </div>

                  <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ဝန်ထမ်း</label>
                      <select value={form.staffId} disabled={!canOverrideReceiver} onChange={e => setForm(p => ({ ...p, staffId: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                        <option value="">— မရွေးထား —</option>
                        {staffList.map((s: any) => (
                          <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ပစ္စည်းထားသည့်နေရာ</label>
                      <input
                        type="text"
                        list="booking-shelf-locations"
                        value={form.shelfLocation}
                        onChange={e => setForm(p => ({ ...p, shelfLocation: e.target.value }))}
                        placeholder="ဥပမာ - A-01၊ ရှေ့ကောင်တာ"
                        className="w-full border rounded-xl px-3 py-2 text-sm bg-white focus:ring-2 focus:ring-indigo-500"
                      />
                      <datalist id="booking-shelf-locations">
                        {shelves.map((s: any) => (
                          <option key={s.id ?? s.code} value={s.code} label={s.label ? `${s.code} — ${s.label}` : s.code} />
                        ))}
                      </datalist>
                      <p className="mt-1 text-[11px] text-slate-400">စာရင်းထဲမှရွေးနိုင်သလို နေရာအသစ်ကိုလည်း ရိုက်ထည့်နိုင်သည်။</p>
                    </div>
                  </section>

                  <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">စုစုပေါင်းငွေ</label>
                      <input type="number" min={0} value={form.totalAmount} onChange={e => setForm(p => ({ ...p, totalAmount: e.target.value }))}
                        placeholder="0" className="w-full border rounded-xl px-3 py-2 text-sm" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">မှတ်ချက်</label>
                      <input value={form.remark} onChange={e => setForm(p => ({ ...p, remark: e.target.value }))}
                        placeholder="နောက်ထပ်မှတ်ချက်..." className="w-full border rounded-xl px-3 py-2 text-sm" />
                    </div>
                  </section>

                  <div className="flex justify-between gap-2 pt-2">
                    <button type="button" onClick={() => setStep('device')} className="px-4 py-2 rounded-lg border text-sm font-semibold text-slate-600">Back</button>
                    <div className="flex gap-2">
                      <button onClick={() => setShowModal(false)} className="px-5 py-2 text-sm border rounded-xl text-slate-600 hover:bg-slate-100 font-medium">မလုပ်တော့ပါ</button>
                      <button onClick={handleSave} className="px-6 py-2 text-sm bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow">{editId ? 'ပြင်ဆင်မည်' : 'သိမ်းဆည်းမည်'}</button>
                    </div>
                  </div>
                </section>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Print Preview */}
      {printId && (
        <InvoicePrintPreview
          documentType="BOOKING"
          documentId={printId}
          title="Intake Receipt"
          onClose={() => setPrintId(null)}
        />
      )}
    </div>
  );
}
