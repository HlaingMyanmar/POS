import React, { useEffect, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { bookingService, api } from '../services/api';
import { shelfLocationService } from '../services/shelfLocationApiService';
import { staffService } from '../services/staffapiservice';
import { ApiResponse } from '../types';
import Swal from 'sweetalert2';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';

const DEVICE_TYPES = ['Phone', 'Laptop', 'Computer', 'Tablet', 'Printer', 'Other'];

const STATUS_COLOR: Record<string, string> = {
  Pending:     'bg-amber-100 text-amber-700',
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
  const [bookings, setBookings]   = useState<any[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [customers, setCustomers] = useState<any[]>([]);
  const [staffList, setStaffList] = useState<any[]>([]);
  const [shelves, setShelves]     = useState<any[]>([]);
  const [search, setSearch]       = useState('');
  const [dateFrom, setDateFrom]   = useState('');
  const [dateTo, setDateTo]       = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId]       = useState<number | null>(null);
  const [form, setForm]           = useState({ ...emptyForm, devices: [emptyDevice()] });
  const [printId, setPrintId]     = useState<number | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const PAGE_SIZE = 20;

  const load = async () => {
    const res = await bookingService.getAll(page, PAGE_SIZE, search, dateFrom, dateTo);
    if (res.success) {
      setBookings(res.data?.content ?? []);
      setTotal(res.data?.totalElements ?? 0);
    }
  };

  useEffect(() => { load(); }, [page, search, dateFrom, dateTo]);
  useDataEvents(['Booking'], load);

  useEffect(() => {
    api.get<any, any>('/v1/customers?size=999')
      .then((r: any) => setCustomers(r.data?.content ?? r.data ?? []))
      .catch(() => {});
    staffService.getAllActive()
      .then(list => setStaffList(list ?? []))
      .catch(() => {});
    shelfLocationService.getActive().then(setShelves).catch(() => {});
  }, []);

  const openNew = () => {
    setForm({ ...emptyForm, devices: [emptyDevice()] });
    setEditId(null);
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
    if (hasEmptyBrand) { Swal.fire('Error', 'Device တိုင်းအတွက် Brand ဖြည့်ပါ', 'error'); return; }

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
      devices: form.devices.map(d => ({
        deviceType:       d.deviceType,
        brand:            d.brand,
        model:            d.model,
        serialNumber:     d.serialNumber || null,
        color:            d.color || null,
        problemDesc:      d.problemDesc || null,
        deviceConditions: d.deviceConditions || null,
      })),
    };

    const res = editId
      ? await bookingService.update(editId, payload)
      : await bookingService.create(payload);

    if (res.success) { setShowModal(false); load(); }
    else Swal.fire('Error', res.message, 'error');
  };

  const handleConvertToJob = async (id: number) => {
    const b = bookings.find(b => b.id === id);
    const deviceCount = b?.devices?.length ?? 1;
    const { isConfirmed } = await Swal.fire({
      title: 'Service Job သို့ပြောင်းမည်',
      text: `Device ${deviceCount} ခုအတွက် Job Order ${deviceCount} ခု ဖန်တီးမည်။ ပြောင်းပြီးနောက် Intake ကို မပြင်နိုင်တော့ပါ`,
      icon: 'question', showCancelButton: true,
      confirmButtonText: 'ပြောင်းမည်', cancelButtonText: 'မပြောင်းဘူး',
    });
    if (!isConfirmed) return;

    const res = await bookingService.convertToJob(id);
    if (res.success) {
      Swal.fire({ icon: 'success', title: `Service Job ${deviceCount} ခု ဖန်တီးပြီး`, timer: 1500, showConfirmButton: false });
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

  const totalPages = Math.ceil(total / PAGE_SIZE);

  return (
    <div>
      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
        {/* Toolbar: search + filters + button */}
        <div className="flex flex-wrap items-center gap-2 px-3 py-2.5 border-b bg-slate-50/60">
          <input value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}
            placeholder="Intake#၊ ဖောက်သည်၊ ပစ္စည်း ရှာပါ..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-44 focus:ring-2 focus:ring-indigo-300 bg-white" />
          <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white" />
          <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setPage(0); }}
            className="border rounded-lg px-2.5 py-1.5 text-sm bg-white" />
          <button onClick={openNew}
            className="ml-auto px-4 py-1.5 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 shadow-sm whitespace-nowrap">
            + ပစ္စည်းလက်ခံ
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-purple-600">
              <tr>
                {['#', 'လက်ခံနံပါတ်', 'ရက်စွဲ', 'ဖောက်သည်', 'ပစ္စည်း', 'ပြဿနာ', 'ခန့်မှန်းကုန်ကျ', 'ကန့်နေရာ', 'ကျွမ်းကျင်သူ', 'အခြေအနေ', 'လုပ်ဆောင်ချက်'].map(h => (
                  <th key={h} className="text-left px-3 py-3.5 text-[13px] font-extrabold text-white tracking-wide whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {bookings.map((b, i) => {
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
                        <div className="flex items-center gap-1 flex-nowrap">
                          <button onClick={() => setPrintId(b.id)} title="Print Receipt"
                            className="px-2 py-1 text-xs border rounded-lg text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 transition-colors">
                            🖨
                          </button>
                          {canConvert && (
                            <button onClick={() => handleConvertToJob(b.id)} title="Convert to Service Job"
                              className="px-2 py-1 text-xs border border-emerald-200 rounded-lg text-emerald-700 hover:bg-emerald-50 font-bold whitespace-nowrap transition-colors">
                              → Job
                            </button>
                          )}
                          {canEdit && (
                            <button onClick={() => openEdit(b)} title="Edit"
                              className="px-2 py-1 text-xs border rounded-lg text-slate-500 hover:text-blue-600 hover:bg-blue-50 transition-colors">
                              ✏
                            </button>
                          )}
                          {canEdit && (
                            <button onClick={() => handleDelete(b.id)} title="Delete"
                              className="px-2 py-1 text-xs border rounded-lg text-slate-500 hover:text-red-600 hover:bg-red-50 transition-colors">
                              🗑
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
              {/* Customer */}
              <section>
                <p className="text-xs font-bold text-slate-500 uppercase tracking-wide mb-2">ဖောက်သည် *</p>
                <CustomerCombo
                  customers={customers}
                  value={form.customerId}
                  onChange={id => setForm(p => ({ ...p, customerId: id }))}
                  onCreated={c => setCustomers(prev => [...prev, c])}
                />
              </section>

              {/* Devices */}
              <section>
                <div className="flex items-center justify-between mb-2">
                  <p className="text-xs font-bold text-slate-500 uppercase tracking-wide">
                    ပစ္စည်း(များ) * &nbsp;
                    <span className="text-indigo-500 font-normal normal-case">
                      ({form.devices.length} ခု → Job Order {form.devices.length} ခု ဖန်တီးမည်)
                    </span>
                  </p>
                  <button onClick={addDevice}
                    className="text-xs font-bold text-indigo-600 border border-indigo-200 px-3 py-1 rounded-lg hover:bg-indigo-50">
                    + ပစ္စည်းထပ်ထည့်
                  </button>
                </div>
                <div className="space-y-3">
                  {form.devices.map((device, idx) => (
                    <DeviceCard
                      key={idx}
                      index={idx}
                      device={device}
                      total={form.devices.length}
                      onChange={updateDevice}
                      onRemove={removeDevice}
                    />
                  ))}
                </div>
              </section>

              {/* Cost / Shelf / Staff */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs text-slate-500 mb-1">ခန့်မှန်းကုန်ကျ (Ks)</label>
                  <input type="number" min={0} value={form.totalAmount}
                    onChange={e => setForm(p => ({ ...p, totalAmount: e.target.value }))}
                    placeholder="0"
                    className="w-full border rounded-xl px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-400" />
                </div>
                <div>
                  <label className="block text-xs text-slate-500 mb-1">ကဏ်တည်နေရာ</label>
                  <select value={form.shelfLocation}
                    onChange={e => setForm(p => ({ ...p, shelfLocation: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    <option value="">— ရွေးပါ —</option>
                    {shelves.map((s: any) => (
                      <option key={s.id} value={s.code}>
                        {s.label ? `${s.code} - ${s.label}` : s.code}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-slate-500 mb-1">နည်းပညာဆရာ</label>
                  <select value={form.staffId}
                    onChange={e => setForm(p => ({ ...p, staffId: e.target.value }))}
                    className="w-full border rounded-xl px-3 py-2 text-sm bg-white">
                    <option value="">— မရှိ —</option>
                    {staffList.map((s: any) => (
                      <option key={s.id} value={s.id}>
                        {s.name}{s.role ? ` (${s.role})` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Remark */}
              <div>
                <label className="block text-xs text-slate-500 mb-1">မှတ်ချက်</label>
                <input value={form.remark} onChange={e => setForm(p => ({ ...p, remark: e.target.value }))}
                  placeholder="နောက်ထပ်မှတ်ချက်..."
                  className="w-full border rounded-xl px-3 py-2 text-sm" />
              </div>
            </div>

            <div className="flex justify-end gap-3 px-6 py-4 border-t bg-slate-50 rounded-b-2xl">
              <button onClick={() => setShowModal(false)}
                className="px-5 py-2 text-sm border rounded-xl text-slate-600 hover:bg-slate-100 font-medium">
                မလုပ်တော့ပါ
              </button>
              <button onClick={handleSave}
                className="px-6 py-2 text-sm bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow">
                {editId ? 'ပြင်ဆင်မည်' : 'သိမ်းဆည်းမည်'}
              </button>
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
