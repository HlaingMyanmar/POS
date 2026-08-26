import React, { useEffect, useRef, useState, useMemo } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { bookingService, api, serviceItemService } from '../services/api';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { shelfLocationService } from '../services/shelfLocationApiService';
import { staffService } from '../services/staffapiservice';
import { ApiResponse } from '../types';
import Swal from 'sweetalert2';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { getFromSession } from '../utils/storageHelper';
import { compressImageFile } from '../utils/imageCompression';
import { BriefcaseBusiness, Eye, Pencil, Plus, Printer, Trash2 } from 'lucide-react';

const DEVICE_TYPES = ['Phone', 'Laptop', 'Computer', 'Tablet', 'Printer', 'HDD', 'SSD', 'Storage', 'Other'];

const normalizeDeviceToken = (value: unknown) => String(value ?? '').toLowerCase().replace(/[^a-z0-9]/g, '');
const deviceAliases = (value: unknown) => {
  const token = normalizeDeviceToken(value);
  const groups = [
    ['phone', 'mobile', 'smartphone', 'iphone', 'android'],
    ['computer', 'desktop', 'pc'],
    ['laptop', 'notebook', 'macbook'],
    ['hdd', 'harddisk', 'harddrive', 'storage'],
    ['ssd', 'solidstate', 'solidstatedrive', 'storage'],
    ['printer', 'printing'],
    ['tablet', 'ipad'],
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
const servicesForDevice = (items: any[], deviceType: string, showAll: boolean) => {
  const sorted = [...items].sort((a, b) => Number(explicitlySupportsDevice(b, deviceType)) - Number(explicitlySupportsDevice(a, deviceType)));
  if (showAll || !deviceType) return sorted;
  const filtered = sorted.filter(item => supportedDeviceTokens(item).length === 0 || explicitlySupportsDevice(item, deviceType));
  return filtered.length ? filtered : sorted;
};
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
const BOOKING_STATUS_LABEL: Record<string, string> = {
  Pending: 'စောင့်ဆိုင်း', Confirmed: 'အတည်ပြု', IN_STORAGE: 'သိမ်းထားပြီး',
  Converted: 'Job ပြောင်းပြီး', Completed: 'ပြီးဆုံး', Cancelled: 'ပယ်ဖျက်',
};

interface PartRequest {
  partName: string;
  action: string;
  qty: number;
  notice: string;
  suggested?: boolean;
  confirmed?: boolean;
  sourceServiceId?: string;
  sourceServiceIds?: string[];
  sourceServiceName?: string;
}

interface DeviceEntry {
  deviceType: string;
  brand: string;
  model: string;
  serialNumber: string;
  color: string;
  accessories: string;
  problemDesc: string;
  deviceConditions: string;
  checklist: { name: string; description: string; status: string; notice: string }[];
  partRequests: PartRequest[];
}

const emptyDevice = (): DeviceEntry => ({
  deviceType: 'Phone', brand: '', model: '',
  serialNumber: '', color: '', accessories: '', problemDesc: '', deviceConditions: '', checklist: defaultChecklist(), partRequests: [],
});

const defaultChecklist = () => [{ name: '', description: '', status: 'Good', notice: '' }];
const parseChecklist = (value?: string) => { try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) && parsed.length ? parsed : defaultChecklist(); } catch { return defaultChecklist(); } };
const parsePartRequests = (value?: string): PartRequest[] => { try { const parsed = JSON.parse(value || '[]'); return Array.isArray(parsed) ? parsed : []; } catch { return []; } };
const defaultPartsForService = (service: any): string[] => {
  const parts = String(service?.defaultRequiredParts || '').split(/[\r\n,;]+/).map(part => part.trim()).filter(Boolean);
  return parts.filter((part, index) => parts.findIndex(candidate => candidate.toLocaleLowerCase() === part.toLocaleLowerCase()) === index);
};
const mergeSuggestedParts = (requests: PartRequest[], service: any): PartRequest[] => {
  const serviceId = String(service.id);
  const defaultPartNames = defaultPartsForService(service);
  const defaultPartKeys = new Set(defaultPartNames.map(part => part.toLocaleLowerCase()));
  const withSharedSources = requests.map(request => {
    if (!request.suggested || !defaultPartKeys.has(String(request.partName || '').trim().toLocaleLowerCase())) return request;
    const sourceServiceIds = Array.from(new Set([...(request.sourceServiceIds || (request.sourceServiceId ? [request.sourceServiceId] : [])), serviceId]));
    return { ...request, sourceServiceId: sourceServiceIds[0], sourceServiceIds };
  });
  const existingNames = new Set(withSharedSources.map(request => String(request.partName || '').trim().toLocaleLowerCase()).filter(Boolean));
  const suggestions = defaultPartNames
    .filter(partName => !existingNames.has(partName.toLocaleLowerCase()))
    .map(partName => ({
      partName,
      action: 'CHECK',
      qty: 1,
      notice: 'ဝန်ဆောင်မှုမှ အလိုအလျောက်အကြံပြု',
      suggested: true,
      confirmed: false,
      sourceServiceId: serviceId,
      sourceServiceIds: [serviceId],
      sourceServiceName: String(service.item || ''),
    }));
  return [...withSharedSources, ...suggestions];
};
const removeUnconfirmedSuggestions = (requests: PartRequest[], serviceId: string): PartRequest[] =>
  requests.flatMap(request => {
    if (!request.suggested) return [request];
    const sourceServiceIds = request.sourceServiceIds || (request.sourceServiceId ? [request.sourceServiceId] : []);
    if (!sourceServiceIds.includes(serviceId)) return [request];
    const remainingSources = sourceServiceIds.filter(id => id !== serviceId);
    return remainingSources.length ? [{ ...request, sourceServiceId: remainingSources[0], sourceServiceIds: remainingSources }] : [];
  });
const photoDeviceLabel = (attachmentType?: string) => {
  const tagged = String(attachmentType || '').match(/INTAKE_PHOTO_DEVICE_(\d+)/i);
  if (tagged) return `ပစ္စည်း ${tagged[1]}`;
  return 'လက်ခံဓာတ်ပုံ';
};

const emptyForm = {
  customerId: '', staffId: '',
  totalAmount: '', depositAmount: '', advancePaymentId: null as number | null, paymentMethodId: '', paymentAccountId: '', transactionNo: '', appointmentDate: '',
  shelfLocation: '', remark: '', signatureData: '',
  devices: [emptyDevice()] as DeviceEntry[],
  details: [] as { serviceId: string; serviceName: string; deviceIndex: number; qty: number; price: number }[],
  deviceInfos: defaultChecklist(),
  photoDeviceIndex: 0,
  photos: [] as { fileName: string; contentType: string; dataUrl: string; deviceIndex: number }[],
  existingPhotos: [] as any[],
};

/* ── CustomerCombo ────────────────────────────────────────────────────── */
const CustomerCombo: React.FC<{
  customers: any[]; value: string;
  onChange: (id: string) => void; onCreated: (c: any) => void;
  disabled?: boolean;
}> = ({ customers, value, onChange, onCreated, disabled }) => {
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
        readOnly={disabled}
        onChange={e => { if (disabled) return; setSearch(e.target.value); setOpen(true); onChange(''); }}
        onFocus={() => { if (!disabled) setOpen(true); }}
        placeholder="ဖောက်သည်အမည် သို့မဟုတ် ဖုန်း..."
        className={`min-h-11 w-full border rounded-xl px-3 py-2 text-base sm:text-sm focus:ring-2 focus:ring-indigo-500 ${disabled ? 'bg-slate-50 text-slate-600' : ''}`}
      />
      {!disabled && open && (
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
  inputClassName?: string;
  clearAfterSelect?: boolean;
}> = ({ value, options, placeholder, onChange, inputClassName, clearAfterSelect }) => {
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
        onChange={event => { setSearch(event.target.value); setOpen(true); if (!clearAfterSelect) onChange(''); }}
        placeholder={placeholder}
        className={inputClassName || 'w-full rounded-xl border border-amber-300 bg-white py-2 pl-3 pr-9 text-sm focus:ring-2 focus:ring-amber-400'} />
      {(search || value) && <button type="button" onClick={() => { setSearch(''); onChange(''); setOpen(true); }}
        className="absolute right-2 top-1/2 -translate-y-1/2 px-1 text-lg text-slate-400 hover:text-rose-500">×</button>}
    </div>
    {open && <div className="absolute z-[70] mt-1 max-h-60 w-full overflow-y-auto rounded-xl border border-amber-200 bg-white shadow-xl">
      {filtered.map(option => <button type="button" key={option.value}
        onClick={() => {
          onChange(option.value);
          setSearch(clearAfterSelect ? '' : option.label);
          setOpen(false);
        }}
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
  onChange: (idx: number, field: keyof DeviceEntry, val: any) => void;
  onRemove: (idx: number) => void;
  readOnly?: boolean;
}> = ({ index, device, total, onChange, onRemove, readOnly }) => (
  <div className="border rounded-xl p-4 bg-slate-50 space-y-3">
    <div className="flex items-center justify-between">
      <span className="text-xs font-bold text-indigo-600 uppercase tracking-wide">
        ပစ္စည်း {index + 1}
      </span>
      {total > 1 && !readOnly && (
        <button onClick={() => onRemove(index)}
          className="text-xs text-red-500 hover:text-red-700 font-medium px-2 py-0.5 border border-red-200 rounded-lg hover:bg-red-50">
          ဖယ်ရှားမည်
        </button>
      )}
    </div>

    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <div>
        <label className="block text-xs text-slate-500 mb-1">အမျိုးအစား</label>
        <select value={device.deviceType} disabled={readOnly}
          onChange={e => onChange(index, 'deviceType', e.target.value)}
          className="min-h-11 w-full border rounded-xl px-3 py-2 text-base sm:text-sm bg-white disabled:bg-slate-100">
          {DEVICE_TYPES.map(t => <option key={t}>{t}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">အမှတ်တံဆိပ် <span className="text-rose-500">*</span></label>
        <input value={device.brand} readOnly={readOnly}
          onChange={e => onChange(index, 'brand', e.target.value)}
          placeholder="Apple, Samsung, ASUS..."
          className="min-h-11 w-full border rounded-xl px-3 py-2 text-base sm:text-sm focus:ring-2 focus:ring-indigo-400 read-only:bg-slate-100" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">မော်ဒယ်</label>
        <input value={device.model} readOnly={readOnly}
          onChange={e => onChange(index, 'model', e.target.value)}
          placeholder="iPhone 14 Pro..."
          className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">Serial No</label>
        <input value={device.serialNumber} readOnly={readOnly}
          onChange={e => onChange(index, 'serialNumber', e.target.value)}
          placeholder="ရွေးချယ်ခွင့်"
          className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
      </div>
      <div>
        <label className="block text-xs text-slate-500 mb-1">အရောင်</label>
        <input value={device.color} readOnly={readOnly}
          onChange={e => onChange(index, 'color', e.target.value)}
          placeholder="မည်း၊ ဖြူ..."
          className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
      </div>
      <div className="sm:col-span-2 lg:col-span-3">
        <label className="block text-xs text-slate-500 mb-1">ပါပစ္စည်းများ</label>
        <input value={device.accessories} readOnly={readOnly}
          onChange={e => onChange(index, 'accessories', e.target.value)}
          placeholder="Charger, Case, SIM tray..."
          className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
      </div>
    </div>

    <div>
      <label className="block text-xs text-slate-500 mb-1">ပြဿနာဖော်ပြချက် <span className="text-rose-500">*</span></label>
      <textarea value={device.problemDesc} readOnly={readOnly}
        onChange={e => onChange(index, 'problemDesc', e.target.value)}
        placeholder="မျက်နှာပြင်ကွဲ၊ ဖွင့်မရ၊ အားသွင်းပလပ်ပျက်..."
        rows={2} className="w-full border rounded-xl px-3 py-2 text-sm resize-none focus:ring-2 focus:ring-indigo-400 read-only:bg-slate-100" />
    </div>

    <div>
      <label className="block text-xs text-slate-500 mb-1">ပစ္စည်းအခြေအနေ</label>
      <textarea value={device.deviceConditions} readOnly={readOnly}
        onChange={e => onChange(index, 'deviceConditions', e.target.value)}
        placeholder="ဥပမာ - ထိပ်ဘယ်ထောင့်တွင် ကွဲကြောင်းသေး၊ Charger မပါ..."
        rows={2} className="w-full border rounded-xl px-3 py-2 text-sm resize-none read-only:bg-slate-100" />
    </div>
    <div className="rounded-xl border bg-white p-3">
      <p className="mb-2 text-[10px] font-black uppercase text-slate-500">ဤပစ္စည်း၏ Condition Checklist</p>
      <div className="space-y-2">
        {device.checklist.map((info, checklistIndex) => (
          <div key={checklistIndex} className="grid grid-cols-1 items-center gap-2 rounded-lg border border-slate-100 bg-slate-50 p-2 text-xs sm:grid-cols-[1fr_0.8fr_1.2fr_auto]">
            <input value={info.name} readOnly={readOnly}
              onChange={event => onChange(index, 'checklist', device.checklist.map((current, currentIndex) => currentIndex === checklistIndex ? { ...current, name: event.target.value } : current))}
              placeholder="Device / အစိတ်အပိုင်း (ဥပမာ HDD)" className="min-w-0 rounded-lg border px-2 py-1.5 read-only:bg-slate-100" />
            <select value={info.status} disabled={readOnly}
              onChange={event => onChange(index, 'checklist', device.checklist.map((current, currentIndex) => currentIndex === checklistIndex ? { ...current, status: event.target.value } : current))}
              className="min-w-0 rounded-lg border px-2 py-1.5 disabled:bg-slate-100">
              <option value="Good">ကောင်း</option>
              <option value="Damaged">မကောင်း</option>
              <option value="Check Required">စစ်ဆေးရန်</option>
            </select>
            <input value={info.notice} readOnly={readOnly}
              onChange={event => onChange(index, 'checklist', device.checklist.map((current, currentIndex) => currentIndex === checklistIndex ? { ...current, notice: event.target.value } : current))}
              placeholder="မှတ်ချက် (ဥပမာ Bad Sector ရှိ)" className="min-w-0 rounded-lg border px-2 py-1.5 read-only:bg-slate-100" />
            {!readOnly && (
              <button type="button"
                onClick={() => onChange(index, 'checklist', device.checklist.filter((_, currentIndex) => currentIndex !== checklistIndex))}
                disabled={device.checklist.length === 1} title="ဖျက်ရန်"
                className="rounded-lg p-1.5 text-rose-500 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-30">
                <Trash2 size={15} />
              </button>
            )}
          </div>
        ))}
      </div>
      {!readOnly && (
        <button type="button"
          onClick={() => onChange(index, 'checklist', [...device.checklist, { name: '', description: '', status: 'Good', notice: '' }])}
          className="mt-2 rounded-lg border border-dashed border-indigo-300 px-3 py-1.5 text-xs font-semibold text-indigo-600 hover:bg-indigo-50">
          + Device / အစိတ်အပိုင်း ထည့်ရန်
        </button>
      )}

    </div>
  </div>
);

/* ── Main Page ────────────────────────────────────────────────────────── */
export default function BookingManagement() {
  const currentUser = useMemo(() => {
    try { return JSON.parse(getFromSession('sspd_user') || '{}') as { staffId?: number; name?: string; username?: string; roles?: string[]; permissions?: string[] }; }
    catch { return {}; }
  }, []);
  const canOverrideReceiver = (currentUser.roles || []).some((r) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(r))
    || (currentUser.permissions || []).includes('CAN_ACCESS_BOOKING_STAFF_OVERRIDE');
  const myStaffId = currentUser.staffId != null ? String(currentUser.staffId) : '';
  const [bookings, setBookings]   = useState<any[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [tab, setTab]             = useState<'waiting' | 'all' | 'converted'>('waiting');
  const [statusFilter, setStatusFilter] = useState<'all' | BookingStatus>('all');
  const [customers, setCustomers] = useState<any[]>([]);
  const [staffList, setStaffList] = useState<any[]>([]);
  const [shelves, setShelves]     = useState<any[]>([]);
  const [payMethods, setPayMethods] = useState<any[]>([]);
  const [serviceItems, setServiceItems] = useState<any[]>([]);
  const [search, setSearch]       = useState('');
  const [defaultDate]             = useState(getLocalToday);
  const [dateFrom, setDateFrom]   = useState(defaultDate);
  const [dateTo, setDateTo]       = useState(defaultDate);
  const [showModal, setShowModal] = useState(false);
  const [viewOnly, setViewOnly]   = useState(false);
  const [editId, setEditId]       = useState<number | null>(null);
  const [saving, setSaving]       = useState(false);
  const [form, setForm]           = useState({ ...emptyForm, devices: [emptyDevice()] });
  const [serviceTargetDeviceIndex, setServiceTargetDeviceIndex] = useState(0);
  const [showAllServices, setShowAllServices] = useState(false);
  const [printId, setPrintId]     = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [step, setStep]           = useState<'customer' | 'device' | 'review'>('customer');
  const PAGE_SIZE = 20;
  const serviceEstimate = useMemo(
    () => form.details.reduce((sum, d) => sum + (Number(d.price) || 0) * (Number(d.qty) || 1), 0),
    [form.details]
  );
  const serviceDeviceType = form.devices[serviceTargetDeviceIndex]?.deviceType ?? '';
  const selectableServiceItems = useMemo(() => servicesForDevice(serviceItems, serviceDeviceType, showAllServices), [serviceItems, serviceDeviceType, showAllServices]);
  const recommendedServiceCount = useMemo(() => serviceItems.filter(item => explicitlySupportsDevice(item, serviceDeviceType)).length, [serviceItems, serviceDeviceType]);

  useEffect(() => {
    if (serviceTargetDeviceIndex >= form.devices.length) setServiceTargetDeviceIndex(Math.max(0, form.devices.length - 1));
  }, [form.devices.length, serviceTargetDeviceIndex]);

  const load = async () => {
    const ignoresDateRange = tab === 'waiting' || search.trim().length > 0;
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
      const [customerRes, staffRes, shelfRes, payRes, svcRes] = await Promise.allSettled([
        api.get<any, any>('/v1/customers?size=999'),
        staffService.getAllActive(),
        shelfLocationService.getActive(),
        paymentMethodService.getAllActive(),
        serviceItemService.getActive(),
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
      if (payRes.status === 'fulfilled') setPayMethods(Array.isArray(payRes.value) ? payRes.value : []);
      if (svcRes.status === 'fulfilled') {
        const v = svcRes.value as any;
        setServiceItems(Array.isArray(v?.data) ? v.data : Array.isArray(v) ? v : []);
      }
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

  const closeModal = () => {
    setShowModal(false);
    setViewOnly(false);
  };

  const openNew = () => {
    setForm({ ...emptyForm, staffId: currentUser.staffId ? String(currentUser.staffId) : '', devices: [emptyDevice()] });
    setEditId(null);
    setViewOnly(false);
    setStep('customer');
    setShowModal(true);
  };

  const applyBookingToForm = (b: any) => {
    const devices: DeviceEntry[] = b.devices && b.devices.length > 0
      ? b.devices.map((d: any) => ({
          deviceType:       d.deviceType ?? 'Phone',
          brand:            d.brand ?? '',
          model:            d.model ?? '',
          serialNumber:     d.serialNumber ?? '',
          color:            d.color ?? '',
          accessories:      d.accessories ?? '',
          problemDesc:      d.problemDesc ?? '',
          deviceConditions: d.deviceConditions ?? '',
          checklist: parseChecklist(d.conditionChecklist),
          partRequests: parsePartRequests(d.partRequests),
        }))
      : [{
          deviceType:       b.deviceType ?? 'Phone',
          brand:            b.brand ?? '',
          model:            b.model ?? '',
          serialNumber:     b.serialNumber ?? '',
          color:            b.color ?? '',
          accessories:      b.accessories ?? '',
          problemDesc:      '',
          deviceConditions: '',
          checklist: defaultChecklist(),
          partRequests: [],
        }];

    setForm({
      customerId:    String(b.customerId ?? ''),
      staffId:       b.staffId ? String(b.staffId) : '',
      totalAmount:   b.totalAmount ? String(b.totalAmount) : '',
      depositAmount: b.depositAmount ? String(b.depositAmount) : '',
      advancePaymentId: b.advancePaymentId ?? null,
      paymentMethodId: b.paymentMethodId ? String(b.paymentMethodId) : '',
      paymentAccountId: b.paymentAccountId ? String(b.paymentAccountId) : '',
      transactionNo: b.transactionNo ?? '',
      appointmentDate: b.appointmentDate ? String(b.appointmentDate).slice(0, 16) : '',
      shelfLocation: b.shelfLocation ?? '',
      remark:        b.remark ?? '',
      signatureData: b.signatureData ?? '',
      devices,
      details: (b.details || []).map((d: any) => ({
        serviceId: String(d.serviceId), serviceName: d.serviceName || '', deviceIndex: Number.isInteger(d.deviceIndex) ? d.deviceIndex : 0, qty: d.qty || 1, price: Number(d.price || 0),
      })),
      deviceInfos: (b.deviceInfos && b.deviceInfos.length > 0) ? b.deviceInfos : defaultChecklist(),
      photoDeviceIndex: 0,
      photos: [],
      existingPhotos: Array.isArray(b.attachments) ? b.attachments : [],
    });
  };

  const openEdit = (b: any) => {
    applyBookingToForm(b);
    setViewOnly(false);
    setEditId(b.id);
    setStep('review');
    setShowModal(true);
  };

  const openView = async (id: number) => {
    try {
      const res = await bookingService.getById(id);
      const b = res.data;
      if (!res.success || !b) {
        void Swal.fire('Error', res.message || 'မှတ်တမ်းမတွေ့ပါ', 'error');
        return;
      }
      applyBookingToForm(b);
      setViewOnly(true);
      setEditId(b.id);
      setStep('review');
      setShowModal(true);
    } catch {
      void Swal.fire('Error', 'မှတ်တမ်းဖွင့်၍မရပါ', 'error');
    }
  };

  const updateDevice = <K extends keyof DeviceEntry>(idx: number, field: K, val: DeviceEntry[K]) => {
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
    const attachmentType = `INTAKE_PHOTO_DEVICE_${idx + 1}`;
    if (form.existingPhotos.some((photo: any) => photo.attachmentType === attachmentType)) {
      void Swal.fire('ပစ္စည်းကို ဖယ်ရှား၍မရသေးပါ', 'ဤပစ္စည်းနှင့်ချိတ်ထားသော လက်ခံဓာတ်ပုံများကို အရင်ဖယ်ရှားပါ။', 'warning');
      return;
    }
    setForm(prev => ({
      ...prev,
      devices: prev.devices.filter((_, i) => i !== idx),
      details: prev.details
        .filter(detail => detail.deviceIndex !== idx)
        .map(detail => detail.deviceIndex > idx ? { ...detail, deviceIndex: detail.deviceIndex - 1 } : detail),
      photos: prev.photos
        .filter(photo => photo.deviceIndex !== idx)
        .map(photo => photo.deviceIndex > idx ? { ...photo, deviceIndex: photo.deviceIndex - 1 } : photo),
      photoDeviceIndex: Math.max(0, Math.min(prev.photoDeviceIndex, prev.devices.length - 2)),
    }));
  };

  const handleSave = async () => {
    if (viewOnly) return;
    if (!form.customerId) { Swal.fire('Error', 'Customer ရွေးပါ', 'error'); return; }
    const hasEmptyBrand = form.devices.some(d => !d.brand.trim());
    const hasEmptyProblem = form.devices.some(d => !d.problemDesc.trim());
    if (hasEmptyBrand) { Swal.fire('Error', 'Device တိုင်းအတွက် Brand ဖြည့်ပါ', 'error'); return; }
    if (hasEmptyProblem) { Swal.fire('Error', 'Device တိုင်းအတွက် ပြဿနာဖော်ပြချက် ဖြည့်ပါ', 'error'); return; }

    if (Number(form.depositAmount || 0) > 0 && !form.paymentMethodId) {
      Swal.fire('Error', 'လက်ခံငွေအတွက် ငွေပေးချေနည်း ရွေးပါ', 'error'); return;
    }

    setSaving(true);
    try {
      const first = form.devices[0];
    const payload = {
      customerId:    Number(form.customerId),
      staffId:       form.staffId ? Number(form.staffId) : null,
      totalAmount:   serviceEstimate > 0 ? serviceEstimate : (form.totalAmount ? Number(form.totalAmount) : 0),
      depositAmount: form.depositAmount ? Number(form.depositAmount) : 0,
      paymentMethodId: form.paymentMethodId ? Number(form.paymentMethodId) : null,
      paymentAccountId: form.paymentAccountId ? Number(form.paymentAccountId) : null,
      transactionNo: form.transactionNo || null,
      appointmentDate: form.appointmentDate ? `${form.appointmentDate}:00` : null,
      shelfLocation: form.shelfLocation || null,
      remark:        form.remark || null,
      signatureData: form.signatureData || null,
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
        conditionChecklist: JSON.stringify(d.checklist || []),
        partRequests: JSON.stringify((d.partRequests || []).filter(request => request.partName.trim())),
      })),
      details: form.details.filter(d => d.serviceId).map(d => ({
        serviceId: Number(d.serviceId), deviceIndex: d.deviceIndex, qty: d.qty, price: d.price,
      })),
      deviceInfos: form.deviceInfos,
    };

    const res = editId
      ? await bookingService.update(editId, payload)
      : await bookingService.create(payload);

    if (res.success) {
      const bookingId = res.data?.id || editId;
      if (bookingId && form.photos.length > 0) {
        for (const photo of form.photos) {
          await bookingService.addAttachment(bookingId, { ...photo, attachmentType: 'INTAKE_PHOTO_DEVICE_' + (photo.deviceIndex + 1) });
        }
      }
      setShowModal(false);
      setStep('customer');
      load();
    }
      else await Swal.fire('သိမ်းဆည်း၍မရပါ', res.message || 'Booking သိမ်းဆည်းမှု မအောင်မြင်ပါ', 'error');
    } catch (error: any) {
      const message = error?.message || error?.error || error?.detail || 'Server နှင့်ချိတ်ဆက်၍မရပါ။ Backend ကို restart လုပ်ထားကြောင်း စစ်ဆေးပါ။';
      await Swal.fire('သိမ်းဆည်း၍မရပါ', String(message), 'error');
    } finally {
      setSaving(false);
    }
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
    const matchesConverted = tab !== 'converted' || booking.status === 'Converted' || booking.status === 'Completed';
    return matchesStatus && matchesWaiting && matchesConverted;
  };
  const filteredBookings = bookings.filter(matchesTab);
  const visibleBookings = filteredBookings.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const receiverChoices = (() => {
    if (canOverrideReceiver) return staffList;
    const mine = staffList.filter((s: any) => String(s.id) === myStaffId);
    if (myStaffId && !mine.some((s: any) => String(s.id) === myStaffId)) {
      mine.push({ id: Number(myStaffId) || myStaffId, name: currentUser.name || currentUser.username || 'ကျွန်ုပ်', role: '' });
    }
    const selected = staffList.find((s: any) => String(s.id) === String(form.staffId));
    if (selected && !mine.some((s: any) => String(s.id) === String(selected.id))) mine.push(selected);
    return mine;
  })();
  const totalPages = Math.ceil(filteredBookings.length / PAGE_SIZE);
  const counts = {
    waiting: bookings.filter(b => WAITING_STATUSES.includes(b.status)).length,
    all: bookings.length,
    converted: bookings.filter(b => b.status === 'Converted' || b.status === 'Completed').length,
  };
  const availableStatuses = tab === 'waiting'
    ? BOOKING_STATUSES.filter(status => WAITING_STATUSES.includes(status))
    : tab === 'converted' ? (['Converted', 'Completed'] as BookingStatus[]) : BOOKING_STATUSES;
  const tabDef: { key: 'waiting' | 'all' | 'converted'; label: string; note: string; count: number; active: string }[] = [
    { key: 'waiting', label: 'စောင့်ဆိုင်းဆဲ ⚡', note: 'ရက်မကန့်သတ်', count: counts.waiting, active: 'border-blue-500 bg-blue-50 text-blue-700' },
    { key: 'all', label: 'အားလုံး', note: 'ယနေ့ / ရက်ရွေးရန်', count: counts.all, active: 'border-slate-400 bg-slate-100 text-slate-700' },
    { key: 'converted', label: 'Job ပြောင်းပြီး ✓', note: 'ယနေ့ / ရက်ရွေးရန်', count: counts.converted, active: 'border-purple-500 bg-purple-50 text-purple-700' },
  ];

  return (
    <div>
      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
        {/* Workflow tabs */}
        <div className="flex gap-1.5 overflow-x-auto border-b bg-slate-50/60 px-3 pb-2 pt-3">
          {tabDef.map(item => (
            <button key={item.key} type="button" onClick={() => { setTab(item.key); setStatusFilter('all'); setPage(0); }}
              className={`min-h-14 flex items-center gap-1.5 whitespace-nowrap rounded-lg border-2 px-3.5 py-1.5 text-sm font-bold transition-all ${tab === item.key ? item.active : 'border-transparent text-slate-500 hover:bg-slate-100'}`}>
              <span className="text-left"><span className="block">{item.label}</span><span className="block text-[10px] font-medium opacity-70">{item.note}</span></span>
              <span className={`rounded-full px-1.5 py-0.5 text-xs font-bold ${tab === item.key ? 'bg-white/60' : 'bg-slate-200 text-slate-500'}`}>{item.count}</span>
            </button>
          ))}
        </div>

        {/* Toolbar: search + filters + button */}
        <div className="flex flex-col gap-2 px-3 py-2.5 border-b bg-slate-50/60 sm:flex-row sm:flex-wrap sm:items-center">
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Intake#၊ ဖောက်သည်၊ ပစ္စည်း ရှာပါ..."
            className="min-h-11 w-full border rounded-xl px-3 py-2 text-base sm:text-sm sm:flex-1 sm:min-w-44 focus:ring-2 focus:ring-indigo-300 bg-white" />
          <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
            <select value={statusFilter} onChange={e => { setStatusFilter(e.target.value as 'all' | BookingStatus); setPage(0); }}
              aria-label="အခြေအနေဖြင့် စစ်ထုတ်ရန်" className="min-h-11 border rounded-xl px-2.5 py-2 text-sm bg-white focus:ring-2 focus:ring-indigo-300">
              <option value="all">အခြေအနေအားလုံး</option>
              {availableStatuses.map(status => <option key={status} value={status}>{BOOKING_STATUS_LABEL[status] || status}</option>)}
            </select>
            <input type="date" value={dateFrom} disabled={tab === 'waiting'}
              title={tab === 'waiting' ? 'လုပ်စရာကျန်သော လက်ခံအားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : 'ရက်စွဲဖြင့် စစ်ထုတ်ရန်'}
              onChange={e => { setDateFrom(e.target.value); setPage(0); }}
              className="min-h-11 border rounded-xl px-2.5 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" />
            <input type="date" value={dateTo} disabled={tab === 'waiting'}
              title={tab === 'waiting' ? 'လုပ်စရာကျန်သော လက်ခံအားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်' : 'ရက်စွဲဖြင့် စစ်ထုတ်ရန်'}
              onChange={e => { setDateTo(e.target.value); setPage(0); }}
              className="min-h-11 col-span-2 border rounded-xl px-2.5 py-2 text-sm bg-white disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 sm:col-span-1" />
          </div>
          <button onClick={openNew}
            className="inline-flex min-h-11 w-full items-center justify-center gap-1.5 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-indigo-700 sm:ml-auto sm:w-auto">
            <Plus size={18} strokeWidth={2.5} /> ပစ္စည်းလက်ခံ
          </button>
        </div>
        <p className="border-b bg-white px-3 py-1.5 text-[11px] text-slate-500">
          {tab === 'waiting'
            ? 'လုပ်စရာကျန်သော လက်ခံအားလုံးကို ရက်မကန့်သတ်ဘဲ ပြထားသည်'
            : 'ရက်စွဲအတိုင်း ပြသည် (ရှာဖွေလျှင် ရက်မကန့်သတ်)'}
        </p>
        <div className="grid gap-3 bg-slate-100 p-3 md:hidden">
          {visibleBookings.map(b => {
            const dev       = b.devices?.[0];
            const devCount  = b.devices?.length ?? 0;
            const devStr    = [dev?.brand ?? b.brand, dev?.model ?? b.model].filter(Boolean).join(' ');
            const problem   = dev?.problemDesc ?? b.remark ?? '';
            const col       = STATUS_COLOR[b.status] ?? 'bg-slate-100 text-slate-600';
            const canConvert = b.status === 'Pending' || b.status === 'IN_STORAGE' || b.status === 'Confirmed';
            const canEdit   = b.status !== 'Converted' && b.status !== 'Cancelled' && b.status !== 'Completed';
            return (
              <article key={`mobile-${b.id}`} className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
                <header className="flex items-center justify-between gap-2 border-b px-4 py-3">
                  <span className="font-mono text-sm font-black text-indigo-700">{b.invoiceNo}</span>
                  <span className={`rounded-full px-2 py-1 text-[10px] font-black ${col}`}>{BOOKING_STATUS_LABEL[b.status] || b.status}</span>
                </header>
                <div className="space-y-3 p-4">
                  <div>
                    <p className="font-black text-slate-900">{b.customerName}<span className="ml-2 text-xs font-medium text-slate-400">{b.customerPhone}</span></p>
                    <p className="mt-1 font-semibold text-slate-700">{devStr || 'ပစ္စည်းအမည်မရှိ'}{devCount > 1 ? ` · ${devCount} ခု` : ''}</p>
                    <p className="mt-1 line-clamp-2 text-xs text-slate-500">{problem || 'ပြဿနာမဖော်ပြထားပါ'}</p>
                    <p className="mt-2 text-xs text-slate-400">{b.bookingDate?.slice(0, 10)} · ကန့် {b.shelfLocation || '—'} · {b.staffName || '—'}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <button onClick={() => setPrintId(b.id)} className="inline-flex min-h-11 items-center justify-center rounded-xl border px-3 text-xs font-bold"><Printer size={16}/></button>
                    {canConvert && (
                      <button onClick={() => handleConvertToJob(b.id)} className="inline-flex min-h-11 items-center gap-1 rounded-xl border border-emerald-300 bg-emerald-50 px-3 text-xs font-extrabold text-emerald-700">
                        <BriefcaseBusiness size={16} /> Job
                      </button>
                    )}
                    {!canEdit && (
                      <button onClick={() => openView(b.id)} className="inline-flex min-h-11 items-center gap-1 rounded-xl border border-slate-200 px-3 text-xs font-bold text-slate-700">
                        <Eye size={16} /> ကြည့်မည်
                      </button>
                    )}
                    {canEdit && (
                      <button onClick={() => openEdit(b)} className="inline-flex min-h-11 items-center gap-1 rounded-xl border border-blue-200 bg-blue-50 px-3 text-xs font-bold text-blue-700">
                        <Pencil size={16} /> ပြင်ဆင်မည်
                      </button>
                    )}
                    {canEdit && (
                      <button onClick={() => handleDelete(b.id)} className="inline-flex min-h-11 items-center justify-center rounded-xl border border-rose-200 px-3 text-xs font-bold text-rose-700">
                        <Trash2 size={16} />
                      </button>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
          {visibleBookings.length === 0 && <div className="rounded-2xl border-2 border-dashed bg-white py-16 text-center text-sm text-slate-400">မှတ်တမ်းမရှိသေးပါ</div>}
        </div>
        <div className="hidden overflow-x-auto md:block">
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
                const canConvert = b.status === 'Pending' || b.status === 'IN_STORAGE' || b.status === 'Confirmed';
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
                      <td className="px-3 py-3 max-w-[8rem] text-xs text-slate-600"><span className="block truncate" title={b.staffName || ''}>{b.staffName || '—'}</span></td>
                      <td className="px-3 py-3">
                        <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${col}`}>{BOOKING_STATUS_LABEL[b.status] || b.status}</span>
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
                          {!canEdit && (
                            <button onClick={() => openView(b.id)} title="ကြည့်ရှုရန်" aria-label="ကြည့်ရှုရန်"
                              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-slate-700 shadow-sm transition-all hover:border-indigo-400 hover:bg-indigo-600 hover:text-white hover:shadow-md focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-1">
                              <Eye size={17} strokeWidth={2.4} />
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
              {visibleBookings.length === 0 && (
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
            <span>စုစုပေါင်း {filteredBookings.length} မှတ်တမ်း</span>
            <div className="flex gap-2 items-center">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="min-h-11 px-4 py-2 border rounded-xl disabled:opacity-40 hover:bg-slate-50">← အရှေ့</button>
              <span>စာမျက်နှာ {page + 1} / {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
                className="min-h-11 px-4 py-2 border rounded-xl disabled:opacity-40 hover:bg-slate-50">နောက် →</button>
            </div>
          </div>
        )}
      </div>

      {/* ─── Intake Modal ─── */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-start justify-center overflow-hidden bg-black/50 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between bg-indigo-600 px-4 py-3 sm:rounded-t-2xl sm:px-6 sm:py-4">
              <div>
                <h2 className="text-lg font-bold text-white">
                  {viewOnly ? 'ကြည့်ရှုမည်' : editId ? 'ပြင်ဆင်မည်' : 'ပစ္စည်းလက်ခံ'}
                </h2>
                <p className="text-xs text-indigo-200 mt-0.5">{viewOnly ? 'လက်ခံမှတ်တမ်း (ကြည့်ရှုရန်သာ)' : 'ပစ္စည်းလက်ခံဖောင်'}</p>
              </div>
              <button onClick={closeModal} aria-label="ပိတ်ရန်" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-2xl text-white/90 hover:bg-white/15 hover:text-white">✕</button>
            </div>

            <div className="flex-1 space-y-5 overflow-y-auto overscroll-contain p-3 pb-4 sm:p-6 [&_input]:text-base [&_textarea]:text-base [&_select]:text-base sm:[&_input]:text-sm sm:[&_textarea]:text-sm sm:[&_select]:text-sm">
              <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs font-semibold text-slate-500">
                <button type="button" onClick={() => setStep('customer')} className={`min-h-10 shrink-0 rounded-full px-4 py-2 ${step === 'customer' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`}>၁. ဖောက်သည်</button>
                <button type="button" onClick={() => form.customerId && setStep('device')} className={`min-h-10 shrink-0 rounded-full px-4 py-2 ${step === 'device' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`} disabled={!form.customerId}>၂. ပစ္စည်း</button>
                <button type="button" onClick={() => form.devices.every(d => d.brand.trim() && d.problemDesc.trim()) && setStep('review')} className={`min-h-10 shrink-0 rounded-full px-4 py-2 ${step === 'review' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`} disabled={!form.customerId || !form.devices.every(d => d.brand.trim() && d.problemDesc.trim())}>၃. စစ်ဆေးသိမ်းမည်</button>
              </div>

              {step === 'customer' && (
                <section className="space-y-4">
                  <div>
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ဖောက်သည် *</p>
                    <CustomerCombo
                      customers={customers}
                      value={form.customerId}
                      disabled={viewOnly}
                      onChange={id => setForm(p => ({ ...p, customerId: id }))}
                      onCreated={(c) => {
                        setCustomers(prev => prev.some(x => x.id === c.id) ? prev : [c, ...prev]);
                      }}
                    />
                  </div>
                  {viewOnly ? null : (
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                    <p className="font-semibold text-slate-700">လုပ်ငန်းစဉ်</p>
                    <ul className="list-disc ml-5 mt-2 space-y-1">
                      <li>ဖောက်သည်ကို ရွေးပါ</li>
                      <li>ပစ္စည်းအချက်အလက်ကို ဖြည့်ပါ</li>
                      <li>ပြဿနာနှင့် အခြေအနေကို စစ်ဆေးပြီး သိမ်းဆည်းပါ</li>
                    </ul>
                  </div>
                  )}
                </section>
              )}

              {step === 'device' && (
                <section className="space-y-4">
                  <div className="mb-2 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wide">
                      ပစ္စည်း(များ) * &nbsp;
                      <span className="text-indigo-500 font-normal normal-case">
                        {viewOnly ? `(${form.devices.length} ခု)` : `(${form.devices.length} ခု → Job Order ${form.devices.length} ခု ဖန်တီးမည်)`}
                      </span>
                    </p>
                    {!viewOnly && (
                    <button onClick={addDevice} className="min-h-10 text-xs font-bold text-indigo-600 border border-indigo-200 px-3 py-2 rounded-xl hover:bg-indigo-50">
                      + ပစ္စည်းထပ်ထည့်
                    </button>
                    )}
                  </div>
                  <div className="space-y-3">
                    {form.devices.map((device, idx) => (
                      <DeviceCard key={idx} index={idx} device={device} total={form.devices.length} onChange={updateDevice} onRemove={removeDevice} readOnly={viewOnly} />
                    ))}
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
                      <select value={form.staffId} disabled={viewOnly || !canOverrideReceiver} onChange={e => setForm(p => ({ ...p, staffId: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:bg-slate-100">
                        {canOverrideReceiver && <option value="">— မရွေးထား —</option>}
                        {receiverChoices.map((s: any) => (
                          <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>
                        ))}
                      </select>
                      <p className="mt-1 text-[11px] text-slate-400">{canOverrideReceiver ? 'CAN_ACCESS_BOOKING_STAFF_OVERRIDE ရှိ၍ အခြားလက်ခံသူကို ရွေးနိုင်သည်။' : 'မိမိ Linked Staff ကိုသာ လက်ခံသူအဖြစ် သုံးသည်။'}</p>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ပစ္စည်းထားသည့်နေရာ</label>
                      <input
                        type="text"
                        list="booking-shelf-locations"
                        value={form.shelfLocation}
                        readOnly={viewOnly}
                        onChange={e => setForm(p => ({ ...p, shelfLocation: e.target.value }))}
                        placeholder="ဥပမာ - A-01၊ ရှေ့ကောင်တာ"
                        className="w-full border rounded-xl px-3 py-2 text-sm bg-white focus:ring-2 focus:ring-indigo-500 read-only:bg-slate-100"
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
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ခန့်မှန်းစုစုပေါင်းငွေ</label>
                      <input type="number" min={0}
                        value={serviceEstimate > 0 ? serviceEstimate : form.totalAmount}
                        readOnly={viewOnly || serviceEstimate > 0}
                        onChange={e => setForm(p => ({ ...p, totalAmount: e.target.value }))}
                        placeholder="0" className={`w-full border rounded-xl px-3 py-2 text-sm ${viewOnly || serviceEstimate > 0 ? 'bg-slate-50 text-slate-600' : ''}`} />
                      <p className="mt-1 text-[11px] text-slate-400">
                        {serviceEstimate > 0
                          ? `ဝန်ဆောင်မှုလိုင်းများမှ ပေါင်းသည် — ${serviceEstimate.toLocaleString()} Ks`
                          : 'ဝန်ဆောင်မှုမရွေးရသေးလျှင် ခန့်မှန်းငွေကို ဤနေရာတွင် ထည့်ပါ။'}
                      </p>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">မှတ်ချက်</label>
                      <input value={form.remark} readOnly={viewOnly} onChange={e => setForm(p => ({ ...p, remark: e.target.value }))}
                        placeholder="နောက်ထပ်မှတ်ချက်..." className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ချိန်းဆိုချိန်</label>
                      <input type="datetime-local" value={form.appointmentDate} readOnly={viewOnly} onChange={e => setForm(p => ({ ...p, appointmentDate: e.target.value }))}
                        className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">လက်ခံငွေ / Deposit</label>
                      <input type="number" min={0} value={form.depositAmount} disabled={viewOnly || Boolean(form.advancePaymentId)} title={form.advancePaymentId ? 'Transaction ရှိပြီးဖြစ်၍ တိုက်ရိုက်ပြင်မရပါ' : ''} onChange={e => setForm(p => ({ ...p, depositAmount: e.target.value }))}
                        placeholder="0" className="w-full border rounded-xl px-3 py-2 text-sm disabled:bg-slate-100 disabled:text-slate-500" />
                      {form.advancePaymentId && <p className="mt-1 text-[10px] text-amber-700">Deposit transaction ရှိပြီးဖြစ်၍ ပြင်မရပါ။ ထပ်လက်ခံ/Refund ကို Payment မှလုပ်ပါ။</p>}
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ငွေပေးချေနည်း</label>
                      <select value={form.paymentMethodId} disabled={viewOnly} onChange={e => { const method = payMethods.find((m: any) => String(m.id) === e.target.value); setForm(p => ({ ...p, paymentMethodId: e.target.value, paymentAccountId: method?.accountId ? String(method.accountId) : '' })); }}
                        className="w-full border rounded-xl px-3 py-2 text-sm bg-white disabled:bg-slate-100">
                        <option value="">— Deposit မရှိ —</option>
                        {payMethods.map((m: any) => <option key={m.id} value={m.id}>{m.methodName || m.name}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">Transaction / Reference No.</label>
                      <input value={form.transactionNo} readOnly={viewOnly} onChange={e => setForm(p => ({ ...p, transactionNo: e.target.value }))}
                        placeholder="KPay၊ Bank reference..." className="w-full border rounded-xl px-3 py-2 text-sm read-only:bg-slate-100" />
                    </div>
                  </section>

                  <section className="rounded-xl border p-4 space-y-3">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <p className="text-xs font-bold text-slate-600 uppercase tracking-wide">လုပ်ဆောင်ရန်နှင့် Part လိုအပ်ချက်</p>
                        <p className="mt-1 text-[11px] text-slate-400">တစ်ခုချင်းရွေးပြီး လိုသလောက်ထပ်ထည့်နိုင်ပါသည်။ Technician စစ်ပြီးမှ အတည်ပြုမည်။</p>
                      </div>
                      {!viewOnly && (
                      <div className="w-full space-y-2 sm:w-[30rem]">
                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-[11rem_1fr]">
                          <select value={serviceTargetDeviceIndex} onChange={event => { setServiceTargetDeviceIndex(Number(event.target.value)); setShowAllServices(false); }} className="w-full rounded-xl border border-indigo-200 bg-white px-3 py-2 text-sm">
                            {form.devices.map((device, index) => <option key={index} value={index}>ပစ္စည်း {index + 1} — {device.deviceType}</option>)}
                          </select>
                          <SearchableSelect
                            value=""
                            clearAfterSelect
                            placeholder={`+ ${serviceDeviceType || 'ပစ္စည်း'} ဝန်ဆောင်မှု ရှာပါ`}
                            inputClassName="w-full rounded-xl border border-indigo-200 bg-white py-2 pl-3 pr-9 text-sm focus:ring-2 focus:ring-indigo-400"
                            options={selectableServiceItems.map((item: any) => ({
                              value: String(item.id),
                              label: `${explicitlySupportsDevice(item, serviceDeviceType) ? '★ ' : ''}${item.serviceTypeName ? `[${item.serviceTypeName}] ` : ''}${item.item} — ${Number(item.price || 0).toLocaleString()} Ks`,
                              searchText: `${item.item || ''} ${item.serviceTypeName || ''} ${item.code || ''} ${item.supportedDeviceTypes || ''}`,
                            }))}
                            onChange={serviceId => {
                              const item = serviceItems.find((entry: any) => String(entry.id) === serviceId);
                              if (!item) return;
                              setForm(current => {
                                const targetIndex = Math.min(serviceTargetDeviceIndex, current.devices.length - 1);
                                const existingIndex = current.details.findIndex(detail => detail.serviceId === String(item.id) && detail.deviceIndex === targetIndex);
                                if (existingIndex < 0) return {
                                  ...current,
                                  details: [...current.details, { serviceId: String(item.id), serviceName: item.item, deviceIndex: targetIndex, qty: 1, price: Number(item.price || 0) }],
                                  devices: current.devices.map((device, index) => index === targetIndex ? { ...device, partRequests: mergeSuggestedParts(device.partRequests, item) } : device),
                                };
                                return { ...current, details: current.details.map((detail, index) => index === existingIndex ? { ...detail, qty: detail.qty + 1 } : detail) };
                              });
                            }}
                          />
                        </div>
                        <label className="flex items-center justify-between gap-3 text-[11px] text-slate-500">
                          <span>{recommendedServiceCount > 0 ? `★ ${serviceDeviceType} အတွက် အကြံပြု ${recommendedServiceCount} ခု` : `${serviceDeviceType} အတွက် သတ်မှတ်ထားသော service မရှိသေးပါ`}</span>
                          <span className="flex shrink-0 items-center gap-1"><input type="checkbox" checked={showAllServices} onChange={event => setShowAllServices(event.target.checked)} /> အားလုံးပြ</span>
                        </label>
                      </div>
                      )}
                    </div>

                    {form.details.length === 0 ? (
                      <div className="rounded-lg border border-dashed bg-slate-50 px-3 py-3 text-xs text-slate-500">
                        လုပ်ဆောင်ရန် မရွေးရသေးပါ။ မသေချာသေးလျှင် အလွတ်ထားနိုင်ပြီး Service Job ပြောင်းချိန်တွင် Technician က သတ်မှတ်နိုင်ပါသည်။
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <div className="hidden grid-cols-[8rem_1fr_7rem_5rem_7rem_2.5rem] gap-2 px-2 text-[10px] font-bold uppercase text-slate-400 sm:grid">
                          <span>ပစ္စည်း</span><span>လုပ်ဆောင်ရန်</span><span>အမျိုးအစား</span><span>အရေအတွက်</span><span>ခန့်မှန်းဈေး</span><span />
                        </div>
                        {form.details.map((d, i) => {
                          const selectedService = serviceItems.find((item: any) => String(item.id) === d.serviceId);
                          return (
                            <div key={`${d.serviceId}-${i}`} className="grid grid-cols-1 items-center gap-2 rounded-lg border bg-slate-50 p-2 text-sm sm:grid-cols-[8rem_1fr_7rem_5rem_7rem_2.5rem]">
                              <select value={d.deviceIndex} aria-label="ဝန်ဆောင်မှုပြုလုပ်မည့်ပစ္စည်း" disabled={viewOnly}
                                onChange={event => {
                                  const nextDeviceIndex = Number(event.target.value);
                                  setForm(current => ({
                                    ...current,
                                    details: current.details.map((detail, index) => index === i ? { ...detail, deviceIndex: nextDeviceIndex } : detail),
                                    devices: current.devices.map((device, deviceIndex) => {
                                      if (deviceIndex === d.deviceIndex) return { ...device, partRequests: removeUnconfirmedSuggestions(device.partRequests, d.serviceId) };
                                      if (deviceIndex === nextDeviceIndex && selectedService) return { ...device, partRequests: mergeSuggestedParts(device.partRequests, selectedService) };
                                      return device;
                                    }),
                                  }));
                                }}
                                className="min-w-0 rounded-lg border bg-white px-2 py-1.5 text-xs disabled:bg-slate-100">
                                {form.devices.map((device, deviceIndex) => <option key={deviceIndex} value={deviceIndex}>ပစ္စည်း {deviceIndex + 1} — {device.brand || device.deviceType}</option>)}
                              </select>
                              <span className="font-medium text-slate-700">{d.serviceName}</span>
                              <span className="w-fit rounded-full bg-indigo-50 px-2 py-1 text-[10px] font-semibold text-indigo-600">
                                {selectedService?.serviceTypeName || 'Service'}
                              </span>
                              <label className="flex items-center gap-2 text-xs text-slate-500 sm:block">
                                <span className="sm:hidden">အရေအတွက်</span>
                                <input type="number" min={1} value={d.qty} readOnly={viewOnly} className="w-16 border rounded px-2 py-1 text-xs bg-white read-only:bg-slate-100"
                                  onChange={e => setForm(p => ({ ...p, details: p.details.map((x, idx) => idx === i ? { ...x, qty: Number(e.target.value) || 1 } : x) }))} />
                              </label>
                              <label className="flex items-center gap-2 text-xs text-slate-500 sm:block">
                                <span className="sm:hidden">ခန့်မှန်းဈေး</span>
                                <input type="number" min={0} value={d.price} readOnly={viewOnly} className="w-24 border rounded px-2 py-1 text-xs bg-white read-only:bg-slate-100"
                                  onChange={e => setForm(p => ({ ...p, details: p.details.map((x, idx) => idx === i ? { ...x, price: Number(e.target.value) || 0 } : x) }))} />
                              </label>
                              {!viewOnly && (
                              <button type="button" title="ဖယ်ရှားရန်" className="w-fit rounded p-1.5 text-rose-500 hover:bg-rose-50"
                                onClick={() => setForm(current => ({
                                  ...current,
                                  details: current.details.filter((_, idx) => idx !== i),
                                  devices: current.devices.map((device, deviceIndex) => deviceIndex === d.deviceIndex ? { ...device, partRequests: removeUnconfirmedSuggestions(device.partRequests, d.serviceId) } : device),
                                }))}>
                                <Trash2 size={15} />
                              </button>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}

                    <div className="border-t pt-3">
                      <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="text-xs font-bold text-slate-600">Part လိုအပ်ချက်များ</p>
                          <p className="mt-1 text-[11px] text-slate-400">Booking တွင် လိုအပ်နိုင်သည့် Part ကိုသာ မှတ်တမ်းတင်ပါ။ Stock မထုတ်သေးပါ။</p>
                        </div>
                      </div>
                      <div className="space-y-3">
                        {form.devices.map((device, deviceIndex) => (
                          <div key={deviceIndex} className="rounded-xl border border-amber-100 bg-amber-50/40 p-3">
                            <div className="mb-2 flex items-center justify-between gap-2">
                              <span className="text-xs font-bold text-amber-800">ပစ္စည်း {deviceIndex + 1} — {device.brand || device.deviceType}</span>
                              {!viewOnly && (
                              <button type="button"
                                onClick={() => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: [...entry.partRequests, { partName: '', action: 'CHECK', qty: 1, notice: '' }] } : entry) }))}
                                className="rounded-lg border border-amber-300 bg-white px-2.5 py-1 text-[11px] font-bold text-amber-700 hover:bg-amber-50">
                                + Part လိုအပ်ချက်ထည့်ရန်
                              </button>
                              )}
                            </div>
                            {device.partRequests.length === 0 ? (
                              <p className="rounded-lg border border-dashed border-amber-200 px-3 py-2 text-[11px] text-amber-600">Part လိုအပ်ချက် မရှိသေးပါ</p>
                            ) : (
                              <div className="space-y-2">
                                {device.partRequests.map((request, requestIndex) => (
                                  <div key={requestIndex} className={`rounded-lg border bg-white p-2 ${request.suggested ? 'border-amber-300' : request.confirmed ? 'border-emerald-200' : ''}`}>
                                    {(request.suggested || request.confirmed) && (
                                      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                                        <span className={`rounded-full px-2 py-1 text-[10px] font-bold ${request.suggested ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>
                                          {request.suggested ? '★ Service မှ အကြံပြု Part' : '✓ Technician အတည်ပြုပြီး'}{request.sourceServiceName ? ` — ${request.sourceServiceName}` : ''}
                                        </span>
                                        {request.suggested && !viewOnly && (
                                          <button type="button"
                                            onClick={() => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.map((part, partIndex) => partIndex === requestIndex ? { ...part, suggested: false, confirmed: true } : part) } : entry) }))}
                                            className="rounded-lg bg-emerald-600 px-2.5 py-1 text-[10px] font-bold text-white hover:bg-emerald-700">
                                            Technician အတည်ပြု
                                          </button>
                                        )}
                                      </div>
                                    )}
                                    <div className="grid grid-cols-1 items-center gap-2 sm:grid-cols-[1fr_8rem_5rem_1.2fr_auto]">
                                      <input value={request.partName} placeholder="Part အမည် (ဥပမာ HDD)" readOnly={viewOnly}
                                        onChange={event => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.map((part, partIndex) => partIndex === requestIndex ? { ...part, partName: event.target.value } : part) } : entry) }))}
                                        className="min-w-0 rounded-lg border px-2 py-1.5 text-xs read-only:bg-slate-100" />
                                      <select value={request.action} disabled={viewOnly}
                                        onChange={event => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.map((part, partIndex) => partIndex === requestIndex ? { ...part, action: event.target.value } : part) } : entry) }))}
                                        className="rounded-lg border px-2 py-1.5 text-xs">
                                        <option value="CHECK">စစ်ဆေးရန်</option>
                                        <option value="REPLACE">လဲရန်</option>
                                        <option value="REPAIR">ပြင်ရန်</option>
                                      </select>
                                      <input type="number" min={1} value={request.qty} title="ခန့်မှန်း Qty" readOnly={viewOnly}
                                        onChange={event => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.map((part, partIndex) => partIndex === requestIndex ? { ...part, qty: Number(event.target.value) || 1 } : part) } : entry) }))}
                                        className="w-full rounded-lg border px-2 py-1.5 text-xs sm:w-20" />
                                      <input value={request.notice} placeholder="မှတ်ချက် (ဥပမာ SSD 512GB)" readOnly={viewOnly}
                                        onChange={event => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.map((part, partIndex) => partIndex === requestIndex ? { ...part, notice: event.target.value } : part) } : entry) }))}
                                        className="min-w-0 rounded-lg border px-2 py-1.5 text-xs" />
                                      {!viewOnly && (
                                      <button type="button" title="ဖယ်ရှားရန်"
                                        onClick={() => setForm(current => ({ ...current, devices: current.devices.map((entry, index) => index === deviceIndex ? { ...entry, partRequests: entry.partRequests.filter((_, partIndex) => partIndex !== requestIndex) } : entry) }))}
                                        className="w-fit rounded p-1.5 text-rose-500 hover:bg-rose-50">
                                        <Trash2 size={15} />
                                      </button>
                                      )}
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                      <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-[11px] leading-5 text-amber-700">
                        Part အတိအကျ၊ Serial၊ ရောင်းဈေးနှင့် Stock ထုတ်ခြင်းကို Technician စစ်ပြီး Service Job အဆင့်တွင် အတည်ပြုပါမည်။
                      </p>
                    </div>
                  </section>
                  <section className="rounded-xl border p-4 space-y-2">
                    <p className="text-xs font-bold text-slate-500 uppercase tracking-wide">လက်ခံဓာတ်ပုံ / လက်မှတ်</p>
                    {!viewOnly && (
                      <>
                        <div className="flex items-center gap-2">
                          <label className="text-xs font-semibold text-slate-600">ဓာတ်ပုံသည်</label>
                          <select value={form.photoDeviceIndex} onChange={event => setForm(current => ({ ...current, photoDeviceIndex: Number(event.target.value) }))} className="rounded-lg border px-2 py-1 text-xs">
                            {form.devices.map((device, index) => <option key={index} value={index}>ပစ္စည်း {index + 1} — {device.brand || device.deviceType}</option>)}
                          </select>
                        </div>
                        <input type="file" accept="image/*" multiple className="text-xs"
                          onChange={e => {
                            const files = Array.from(e.target.files ?? []) as File[];
                            files.forEach(async file => {
                              if (file.size > 10 * 1024 * 1024) { Swal.fire('Too large', file.name, 'warning'); return; }
                              const dataUrl = await compressImageFile(file);
                              setForm(p => ({ ...p, photos: [...p.photos, { fileName: file.name, contentType: 'image/jpeg', dataUrl, deviceIndex: p.photoDeviceIndex }] }));
                            });
                            e.target.value = '';
                          }} />
                      </>
                    )}
                    {form.existingPhotos.length > 0 ? (
                      <div className="flex flex-wrap gap-3 rounded-lg border bg-slate-50 p-2">
                        {form.existingPhotos.map((photo: any) => (
                          <div key={photo.id} className="relative">
                            <span className="absolute bottom-0 left-0 z-10 rounded-tr bg-slate-900/70 px-1 text-[8px] text-white">{photoDeviceLabel(photo.attachmentType)}</span>
                            <a href={photo.dataUrl} target="_blank" rel="noreferrer">
                              <img src={photo.dataUrl} alt={photo.fileName || 'Intake photo'} className={`object-cover rounded-lg border ${viewOnly ? 'h-28 w-28' : 'h-16 w-16'}`} />
                            </a>
                            {editId && !viewOnly && (
                              <button type="button" className="absolute -top-1 -right-1 bg-rose-500 text-white rounded-full w-4 h-4 text-[10px]" onClick={async () => { await bookingService.removeAttachment(editId, photo.id); setForm(current => ({ ...current, existingPhotos: current.existingPhotos.filter((x: any) => x.id !== photo.id) })); }}>×</button>
                            )}
                          </div>
                        ))}
                      </div>
                    ) : viewOnly ? (
                      <p className="rounded-lg border border-dashed bg-slate-50 px-3 py-2 text-xs text-slate-500">လက်ခံဓာတ်ပုံ မရှိပါ</p>
                    ) : null}
                    {!viewOnly && (
                      <div className="flex flex-wrap gap-2">
                        {form.photos.map((p, i) => (
                          <div key={i} className="relative">
                            <img src={p.dataUrl} alt="" className="h-16 w-16 object-cover rounded-lg border" />
                            <button className="absolute -top-1 -right-1 bg-rose-500 text-white rounded-full w-4 h-4 text-[10px]"
                              onClick={() => setForm(f => ({ ...f, photos: f.photos.filter((_, idx) => idx !== i) }))}>×</button>
                          </div>
                        ))}
                      </div>
                    )}
                    <label className="block text-[11px] text-slate-500 mt-2">ဖောက်သည်လက်မှတ် (ပုံ)</label>
                    {!viewOnly && (
                      <input type="file" accept="image/*" className="text-xs"
                        onChange={e => {
                          const file = e.target.files?.[0];
                          if (!file) return;
                          compressImageFile(file, 1000, 0.82).then(dataUrl => setForm(p => ({ ...p, signatureData: dataUrl })));
                        }} />
                    )}
                    {form.signatureData ? (
                      <a href={form.signatureData} target="_blank" rel="noreferrer">
                        <img src={form.signatureData} alt="signature" className={`${viewOnly ? 'h-24 max-w-xs' : 'h-16'} border rounded bg-white`} />
                      </a>
                    ) : viewOnly ? (
                      <p className="rounded-lg border border-dashed bg-slate-50 px-3 py-2 text-xs text-slate-500">လက်မှတ် မရှိပါ</p>
                    ) : null}
                  </section>
                </section>
              )}
            </div>
            <div className="flex shrink-0 flex-col-reverse gap-2 border-t bg-white px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-between sm:px-6">
              {step === 'customer' ? (
                <>
                  <button type="button" onClick={closeModal} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold text-slate-600 sm:w-auto">မလုပ်တော့ပါ</button>
                  <button type="button" onClick={() => form.customerId && setStep('device')} disabled={!form.customerId} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white disabled:opacity-50 sm:w-auto">{viewOnly ? 'ပစ္စည်းကြည့်မည်' : 'ရှေ့သို့'}</button>
                </>
              ) : step === 'device' ? (
                <>
                  <button type="button" onClick={() => setStep('customer')} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold text-slate-600 sm:w-auto">နောက်သို့</button>
                  <button type="button" onClick={() => form.devices.every(d => d.brand.trim() && d.problemDesc.trim()) && setStep('review')} disabled={!form.devices.every(d => d.brand.trim() && d.problemDesc.trim())} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white disabled:opacity-50 sm:w-auto">စစ်ဆေးသိမ်းမည်</button>
                </>
              ) : (
                <>
                  <button type="button" onClick={() => setStep('device')} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold text-slate-600 sm:w-auto">နောက်သို့</button>
                  <div className="flex w-full flex-col-reverse gap-2 sm:w-auto sm:flex-row">
                    <button type="button" onClick={closeModal} className="min-h-11 rounded-xl border px-5 text-sm font-medium text-slate-600">{viewOnly ? 'ပိတ်မည်' : 'မလုပ်တော့ပါ'}</button>
                    {!viewOnly && (
                      <button type="button" onClick={handleSave} disabled={saving} className="min-h-11 rounded-xl bg-indigo-600 px-6 text-sm font-bold text-white shadow disabled:cursor-wait disabled:opacity-60">{saving ? 'သိမ်းဆည်းနေသည်...' : editId ? 'ပြင်ဆင်မည်' : 'သိမ်းဆည်းမည်'}</button>
                    )}
                  </div>
                </>
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
