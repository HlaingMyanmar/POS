import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import { ChevronLeft, ChevronRight, Camera, Eye, Home, MapPin, PackagePlus, Pencil, Plus, Printer, Search, Trash2, Wrench, X, ZoomIn } from 'lucide-react';
import { bookingService, resolveAssetUrl } from '../services/api';
import { customerService } from '../services/customerapiservice';
import { useDataEvents } from '../hooks/useDataEvents';
import { useWebsocket } from '../hooks/useWebsocket';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { getFromSession } from '../utils/storageHelper';
import { compressImageFile } from '../utils/imageCompression';
import PortaledCombobox from '../components/PortaledCombobox';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';

type BookingStatus = 'CONFIRMED' | 'ARRIVED' | 'CANCELED';
type ItemPhoto = { id?: number; slot?: number; fileName?: string; contentType?: string; dataUrl?: string; imagePath?: string; thumbnailPath?: string };
type Item = { id?: number; itemName: string; deviceType: string; serialNo: string; color: string; accessories: string; problemDesc: string; itemCondition: string; noticed: string; convertedJobId?: number | null; photos?: ItemPhoto[] };
type Booking = { id: number; bookingNo: string; customerId: number; customerName: string; customerPhone?: string; bookingDate: string; appointmentDate?: string; complaintNote?: string; status: BookingStatus; remark?: string; items: Item[]; linkedJobs: any[]; unconvertedItemCount: number; fullyConverted: boolean };
type Form = { customerId: string; bookingDate: string; appointmentDate: string; complaintNote: string; remark: string };
type NewCustomerForm = { name: string; phone: string; address: string };

const localDate = () => {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
};
const emptyForm = (): Form => ({ customerId: '', bookingDate: localDate(), appointmentDate: '', complaintNote: '', remark: '' });
const emptyNewCustomer = (): NewCustomerForm => ({ name: '', phone: '', address: '' });
const emptyItem = (): Item => ({ itemName: '', deviceType: '', serialNo: '', color: '', accessories: '', problemDesc: '', itemCondition: '', noticed: '', photos: [] });
const input = 'w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100';
const status: Record<BookingStatus, { label: string; style: string }> = {
  CONFIRMED: { label: 'အတည်ပြုပြီး', style: 'bg-blue-100 text-blue-700' },
  ARRIVED: { label: 'ပစ္စည်းလက်ခံပြီး', style: 'bg-amber-100 text-amber-700' },
  CANCELED: { label: 'ပယ်ဖျက်ထား', style: 'bg-rose-100 text-rose-700' },
};
const dateText = (value?: string) => {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString();
};
const photoUrl = (photo?: ItemPhoto | null) => resolveAssetUrl(photo?.thumbnailPath || photo?.imagePath || photo?.dataUrl);
const Label = ({ text, children, required = false }: { text: string; children: React.ReactNode; required?: boolean }) => (
  <label className="block"><span className="mb-1 block text-sm font-medium text-slate-700">{text}{required && <b className="text-rose-500"> *</b>}</span>{children}</label>
);
const Modal = ({ title, close, children, wide = false, elevated = false }: { title: string; close: () => void; children: React.ReactNode; wide?: boolean; elevated?: boolean }) => (
  <div className={'fixed inset-0 flex items-center justify-center bg-slate-950/55 p-4 ' + (elevated ? 'z-[60]' : 'z-50')}>
    <div className={'max-h-[92vh] w-full overflow-y-auto rounded-2xl bg-white shadow-2xl ' + (wide ? 'max-w-5xl' : 'max-w-2xl')}>
      <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-white px-5 py-4"><h2 className="text-lg font-bold">{title}</h2><button onClick={close} className="rounded-lg p-2 hover:bg-slate-100"><X size={20} /></button></div>
      {children}
    </div>
  </div>
);

const PhotoLightbox = ({ photos, index, onClose, onChange }: { photos: ItemPhoto[]; index: number; onClose: () => void; onChange: (next: number) => void }) => {
  const photo = photos[index];
  if (!photoUrl(photo)) return null;
  const hasPrev = index > 0;
  const hasNext = index < photos.length - 1;
  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/85 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="flex max-h-[92vh] max-w-[95vw] flex-col items-center gap-3" onClick={e => e.stopPropagation()}>
        {photos.length > 1 && <div className="text-sm font-medium text-white/80">{index + 1} / {photos.length}</div>}
        <img src={resolveAssetUrl(photo?.imagePath || photo?.dataUrl)} alt={photo?.fileName || `Device photo ${photo?.slot || index + 1}`} className="max-h-[78vh] max-w-[95vw] rounded-xl object-contain shadow-2xl" />
        {photo.fileName && <div className="max-w-[95vw] truncate text-xs text-white/70">{photo.fileName}</div>}
        <div className="flex items-center gap-3">
          {hasPrev && <button type="button" onClick={() => onChange(index - 1)} className="flex items-center gap-1 rounded-full border border-white/30 bg-white/10 px-4 py-2 text-sm text-white hover:bg-white/20"><ChevronLeft size={18} /> ယခင်</button>}
          <button type="button" onClick={onClose} className="rounded-full border border-white/30 bg-white/10 px-5 py-2 text-sm font-semibold text-white hover:bg-white/20">ပိတ်ရန်</button>
          {hasNext && <button type="button" onClick={() => onChange(index + 1)} className="flex items-center gap-1 rounded-full border border-white/30 bg-white/10 px-4 py-2 text-sm text-white hover:bg-white/20">နောက် <ChevronRight size={18} /></button>}
        </div>
      </div>
    </div>
  );
};

const DevicePhotoSlots = ({ photos, onChange, readOnly = false }: { photos: ItemPhoto[]; onChange?: (next: ItemPhoto[]) => void; readOnly?: boolean }) => {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const slots = [1, 2, 3];
  const filledPhotos = useMemo(
    () => photos.filter(p => photoUrl(p)).sort((a, b) => (a.slot || 0) - (b.slot || 0)),
    [photos],
  );
  const bySlot = (slot: number) => photos.find(p => p.slot === slot);
  const openLightbox = (slot: number) => {
    const idx = filledPhotos.findIndex(p => p.slot === slot);
    if (idx >= 0) setLightboxIndex(idx);
  };
  const upload = async (slot: number, file?: File | null) => {
    if (!file || readOnly || !onChange) return;
    try {
      const dataUrl = await compressImageFile(file);
      const next = photos.filter(p => p.slot !== slot);
      next.push({ slot, fileName: file.name, contentType: file.type || 'image/jpeg', dataUrl });
      onChange(next.sort((a, b) => (a.slot || 0) - (b.slot || 0)));
    } catch (e: any) {
      Swal.fire('ပုံတင်၍မရပါ', e?.message || 'Photo upload failed', 'error');
    }
  };
  const remove = (slot: number) => onChange?.(photos.filter(p => p.slot !== slot));
  return (
    <div>
      <div className="mb-2 text-sm font-medium text-slate-700">Device Photos <span className="text-xs text-slate-400">(အများဆုံး ၃ ပုံ)</span></div>
      <div className="grid grid-cols-3 gap-3">
        {slots.map(slot => {
          const photo = bySlot(slot);
          return (
            <div key={slot} className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-2">
              <div className="mb-2 text-center text-[11px] font-semibold text-slate-500">ပုံ {slot}</div>
              {photoUrl(photo) ? (
                <div className="space-y-2">
                  <button type="button" onClick={() => openLightbox(slot)} className="group relative block w-full overflow-hidden rounded-lg border" title="ပုံကြီးကြည့်ရန်">
                    <img src={photoUrl(photo)} alt={photo.fileName || `Device photo ${slot}`} className="h-24 w-full object-cover transition group-hover:opacity-90" />
                    <span className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/0 transition group-hover:bg-black/35">
                      <ZoomIn size={22} className="text-white opacity-0 drop-shadow transition group-hover:opacity-100" />
                    </span>
                  </button>
                  {!readOnly && <button type="button" onClick={() => remove(slot)} className="w-full rounded-lg border border-rose-200 px-2 py-1 text-xs font-semibold text-rose-600">ဖယ်ရှား</button>}
                </div>
              ) : readOnly ? (
                <div className="flex h-24 items-center justify-center rounded-lg border bg-white text-xs text-slate-400">မရှိ</div>
              ) : (
                <label className="flex h-24 cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border bg-white text-xs font-semibold text-blue-700 hover:bg-blue-50">
                  <Camera size={18} />
                  <span>ရွေးပါ</span>
                  <input type="file" accept="image/*" className="hidden" onChange={e => { void upload(slot, e.target.files?.[0]); e.currentTarget.value = ''; }} />
                </label>
              )}
            </div>
          );
        })}
      </div>
      {lightboxIndex !== null && (
        <PhotoLightbox
          photos={filledPhotos}
          index={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onChange={setLightboxIndex}
        />
      )}
    </div>
  );
};

export default function BookingManagement() {
  const user = useMemo(() => { try { return JSON.parse(getFromSession('sspd_user') || '{}'); } catch { return {}; } }, []);
  const admin = (user.roles || []).some((r: string) => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR');
  const can = (name: string) => admin || (user.permissions || []).includes(name);
  const [rows, setRows] = useState<Booking[]>([]);
  const [customers, setCustomers] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [detail, setDetail] = useState<Booking | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<Form>(emptyForm());
  const [itemsOpen, setItemsOpen] = useState(false);
  const [items, setItems] = useState<Item[]>([emptyItem()]);
  const [saving, setSaving] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [creatingCustomer, setCreatingCustomer] = useState(false);
  const [newCustomer, setNewCustomer] = useState<NewCustomerForm>(emptyNewCustomer());
  const [printBookingId, setPrintBookingId] = useState<number | null>(null);
  const detailIdRef = useRef<number | null>(null);

  useEffect(() => {
    detailIdRef.current = detail?.id ?? null;
  }, [detail?.id]);

  const customerItems = useMemo(
    () => customers.map(c => ({
      id: Number(c.id),
      label: c.name || '',
      sub: c.phone || undefined,
      searchText: `${c.name || ''} ${c.phone || ''}`,
    })),
    [customers],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res: any = await bookingService.getAll(page, 20, search.trim(), from, to);
      setRows(res?.data?.content || []); setPages(Number(res?.data?.totalPages || 0)); setTotal(Number(res?.data?.totalElements || 0));
    } catch (e: any) { Swal.fire('မရရှိပါ', e?.message || 'Booking စာရင်းမရပါ။', 'error'); }
    finally { setLoading(false); }
  }, [page, search, from, to]);
  const loadCustomers = useCallback(async () => {
    try {
      const list = await customerService.getAll();
      setCustomers(Array.isArray(list) ? list : []);
    } catch {
      setCustomers([]);
    }
  }, []);
  const getDetail = async (id: number) => { const res: any = await bookingService.getById(id); setDetail(res.data); return res.data as Booking; };
  const refreshBooking = useCallback(() => {
    void load();
    const openId = detailIdRef.current;
    if (openId) void getDetail(openId);
  }, [load]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { void loadCustomers(); }, [loadCustomers]);
  useRefreshOnTabActivate(() => { refreshBooking(); void loadCustomers(); });
  useDataEvents(['Booking', 'Service Job'], refreshBooking);
  useWebsocket('/topic/booking', refreshBooking);
  useWebsocket('/topic/service-jobs', refreshBooking);
  useDataEvents(['Customer'], loadCustomers);

  const openCreate = () => { void loadCustomers(); setEditId(null); setForm(emptyForm()); setShowAddCustomer(false); setNewCustomer(emptyNewCustomer()); setFormOpen(true); };
  const openEdit = (b: Booking) => {
    void loadCustomers();
    setEditId(b.id); setForm({ customerId: String(b.customerId), bookingDate: b.bookingDate, appointmentDate: b.appointmentDate?.slice(0, 16) || '', complaintNote: b.complaintNote || '', remark: b.remark || '' }); setShowAddCustomer(false); setNewCustomer(emptyNewCustomer()); setFormOpen(true);
  };
  const createCustomer = async () => {
    const name = newCustomer.name.trim();
    const phone = newCustomer.phone.trim();
    const address = newCustomer.address.trim();
    if (!name) return void Swal.fire('လိုအပ်ပါသည်', 'Customer အမည် ထည့်ပါ။', 'warning');
    setCreatingCustomer(true);
    try {
      const created = await customerService.create({ name, phone, address });
      if (created?.id) {
        setCustomers(prev => prev.some(c => c.id === created.id) ? prev : [created, ...prev]);
        setForm(f => ({ ...f, customerId: String(created.id) }));
      }
      setShowAddCustomer(false);
      setNewCustomer(emptyNewCustomer());
      Swal.fire({ icon: 'success', title: 'Customer ဖန်တီးပြီးပါပြီ', timer: 1200, showConfirmButton: false });
    } catch (x: any) {
      Swal.fire('မအောင်မြင်ပါ', x?.message || 'Customer ဖန်တီး၍မရပါ။', 'error');
    } finally {
      setCreatingCustomer(false);
    }
  };
  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.customerId) return void Swal.fire('လိုအပ်ပါသည်', 'Customer ရွေးပါ။', 'warning');
    setSaving(true);
    try {
      const body = { customerId: Number(form.customerId), bookingDate: form.bookingDate, appointmentDate: form.appointmentDate || null, complaintNote: form.complaintNote.trim() || null, remark: form.remark.trim() || null };
      if (editId) {
        await bookingService.update(editId, body);
        setFormOpen(false);
        await load();
        if (detail?.id === editId) await getDetail(editId);
      } else {
        const res: any = await bookingService.create(body);
        const createdId = Number(res?.data?.id);
        setFormOpen(false);
        await load();
        if (createdId) await getDetail(createdId);
      }
      Swal.fire({ icon: 'success', title: editId ? 'ပြင်ဆင်ပြီးပါပြီ' : 'Booking ဖန်တီးပြီးပါပြီ', timer: 1200, showConfirmButton: false });
    } catch (x: any) { Swal.fire('မအောင်မြင်ပါ', x?.message || 'သိမ်းမရပါ။', 'error'); } finally { setSaving(false); }
  };
  const action = async (title: string, text: string, task: () => Promise<any>, done: string) => {
    const q = await Swal.fire({ title, text, icon: 'question', showCancelButton: true, confirmButtonText: 'ဆက်လုပ်မည်', cancelButtonText: 'မလုပ်တော့ပါ' });
    if (!q.isConfirmed) return;
    setSaving(true);
    try { await task(); if (detail) await getDetail(detail.id); await load(); Swal.fire({ icon: 'success', title: done, timer: 1300, showConfirmButton: false }); }
    catch (x: any) { Swal.fire('မအောင်မြင်ပါ', x?.message || 'လုပ်ဆောင်မရပါ။', 'error'); } finally { setSaving(false); }
  };
  const receive = async () => {
    if (!detail || items.some(x => !x.itemName.trim())) return void Swal.fire('လိုအပ်ပါသည်', 'ပစ္စည်းတိုင်းတွင် အမည်ထည့်ပါ။', 'warning');
    setSaving(true);
    try {
      await bookingService.addItems(detail.id, items.map(x => ({
        itemName: x.itemName.trim(),
        deviceType: x.deviceType.trim() || null,
        serialNo: x.serialNo.trim() || null,
        color: x.color.trim() || null,
        accessories: x.accessories.trim() || null,
        itemCondition: x.itemCondition.trim() || null,
        noticed: x.noticed.trim() || null,
        problemDesc: x.problemDesc.trim() || detail.complaintNote || null,
        photos: (x.photos || []).filter(p => p.dataUrl).map(p => ({
          slot: p.slot,
          fileName: p.fileName || null,
          contentType: p.contentType || 'image/jpeg',
          dataUrl: p.dataUrl,
        })),
      })));
      setItemsOpen(false); setItems([emptyItem()]);
      const updated = await getDetail(detail.id);
      await load();
      Swal.fire({ icon: 'success', title: 'ပစ္စည်းလက်ခံပြီးပါပြီ', timer: 1200, showConfirmButton: false });
      if (updated?.items?.length) setPrintBookingId(updated.id);
    } catch (x: any) { Swal.fire('မအောင်မြင်ပါ', x?.message || 'ပစ္စည်းလက်ခံမရပါ။', 'error'); } finally { setSaving(false); }
  };

  return <div className="space-y-5 p-4 md:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold">Booking Management</h1><p className="text-sm text-slate-500">Outdoor ချိန်းဆိုမှုနှင့် ဆိုင်အပ်ပစ္စည်း လက်ခံမှု</p></div>{can('CAN_ACCESS_BOOKING_CREATE') && <button onClick={openCreate} className="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 font-semibold text-white"><Plus size={18} /> Booking အသစ်</button>}</div>
    <div className="grid gap-3 rounded-2xl border bg-white p-4 md:grid-cols-[1fr_170px_170px]"><div className="relative"><Search size={18} className="absolute left-3 top-3 text-slate-400" /><input className={input + ' pl-10'} placeholder="Booking No, Customer, Phone, Complaint" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} /></div><input className={input} type="date" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} /><input className={input} type="date" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} /></div>
    <div className="overflow-hidden rounded-2xl border bg-white shadow-sm"><div className="overflow-x-auto"><table className="min-w-full text-sm"><thead className="bg-slate-50 text-left text-xs uppercase text-slate-500"><tr><th className="px-4 py-3">Booking နံပါတ်</th><th className="px-4 py-3">ဖောက်သည်</th><th className="px-4 py-3">ချိန်းဆိုချိန်</th><th className="px-4 py-3">အခြေအနေ</th><th className="px-4 py-3">တိုင်ပင်ချက်</th><th className="px-4 py-3 text-right">လုပ်ဆောင်ချက်</th></tr></thead><tbody className="divide-y">
      {loading ? <tr><td colSpan={6} className="py-12 text-center">Loading...</td></tr> : !rows.length ? <tr><td colSpan={6} className="py-12 text-center text-slate-500">Booking မရှိသေးပါ။</td></tr> : rows.map(b => <tr key={b.id} className="hover:bg-slate-50"><td className="px-4 py-3 font-semibold text-blue-700">{b.bookingNo}</td><td className="px-4 py-3"><b>{b.customerName}</b><div className="text-xs text-slate-500">{b.customerPhone}</div></td><td className="px-4 py-3">{dateText(b.appointmentDate || b.bookingDate)}</td><td className="px-4 py-3"><span className={'rounded-full px-2.5 py-1 text-xs font-semibold ' + status[b.status].style}>{status[b.status].label}</span></td><td className="max-w-xs truncate px-4 py-3">{b.complaintNote || '—'}</td><td className="px-4 py-3"><div className="flex justify-end gap-1"><button onClick={() => getDetail(b.id)} className="rounded-lg p-2 text-blue-600 hover:bg-blue-50"><Eye size={17} /></button>{can('CAN_ACCESS_BOOKING_UPDATE') && b.status !== 'CANCELED' && !b.fullyConverted && <button onClick={() => openEdit(b)} className="rounded-lg p-2 text-amber-600 hover:bg-amber-50"><Pencil size={17} /></button>}{can('CAN_ACCESS_BOOKING_DELETE') && b.status === 'CONFIRMED' && <button onClick={() => action('Booking ဖျက်မည်လား?', b.bookingNo, () => bookingService.remove(b.id), 'ဖျက်ပြီးပါပြီ')} className="rounded-lg p-2 text-rose-600 hover:bg-rose-50"><Trash2 size={17} /></button>}</div></td></tr>)}
    </tbody></table></div><div className="flex items-center justify-between border-t px-4 py-3 text-sm"><span>စုစုပေါင်း {total}</span><div className="flex items-center gap-2"><button disabled={!page} onClick={() => setPage(p => p - 1)} className="rounded-lg border p-2 disabled:opacity-40"><ChevronLeft size={17} /></button><span>{pages ? page + 1 : 0} / {pages}</span><button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} className="rounded-lg border p-2 disabled:opacity-40"><ChevronRight size={17} /></button></div></div></div>


    {showAddCustomer && <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/55 p-4"><div className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl"><div className="flex items-center justify-between border-b px-5 py-4"><h3 className="text-lg font-bold">Customer အသစ်</h3><button type="button" disabled={creatingCustomer} onClick={() => { setShowAddCustomer(false); setNewCustomer(emptyNewCustomer()); }} className="rounded-lg p-2 hover:bg-slate-100 disabled:opacity-50"><X size={20} /></button></div><div className="space-y-3 p-5"><Label text="အမည်" required><input className={input} value={newCustomer.name} onChange={e => setNewCustomer({ ...newCustomer, name: e.target.value })} placeholder="Customer အမည်" /></Label><Label text="ဖုန်း"><input className={input} value={newCustomer.phone} onChange={e => setNewCustomer({ ...newCustomer, phone: e.target.value })} placeholder="ဖုန်းနံပါတ်" /></Label><Label text="လိပ်စာ"><textarea rows={3} className={input} value={newCustomer.address} onChange={e => setNewCustomer({ ...newCustomer, address: e.target.value })} placeholder="လိပ်စာ" /></Label><div className="flex justify-end gap-2 border-t pt-4"><button type="button" disabled={creatingCustomer} onClick={() => { setShowAddCustomer(false); setNewCustomer(emptyNewCustomer()); }} className="rounded-xl border px-4 py-2.5">မလုပ်တော့ပါ</button><button type="button" disabled={creatingCustomer} onClick={() => void createCustomer()} className="rounded-xl bg-blue-600 px-5 py-2.5 font-semibold text-white disabled:opacity-50">{creatingCustomer ? 'သိမ်းနေသည်...' : 'သိမ်းမည်'}</button></div></div></div></div>}

    {detail && <Modal title={detail.bookingNo + ' အသေးစိတ်'} close={() => setDetail(null)} wide><div className="space-y-5 p-5">
      <div className="grid gap-3 rounded-xl bg-slate-50 p-4 md:grid-cols-4"><div><small>Customer</small><div className="font-semibold">{detail.customerName}</div><small>{detail.customerPhone}</small></div><div><small>Appointment</small><div className="font-semibold">{dateText(detail.appointmentDate || detail.bookingDate)}</div></div><div><small>Status</small><div><span className={'rounded-full px-2.5 py-1 text-xs font-semibold ' + status[detail.status].style}>{status[detail.status].label}</span></div></div><div><small>Next action</small><div className="font-semibold text-blue-700">{detail.status === 'CONFIRMED' ? detail.linkedJobs.length ? 'Outdoor Job ပြောင်းပြီး' : 'Outdoor ပြောင်း / ပစ္စည်းလက်ခံ' : detail.status === 'ARRIVED' ? detail.unconvertedItemCount ? 'Indoor Job ပြောင်းရန်' : 'Items အားလုံးပြောင်းပြီး' : 'လုပ်ဆောင်၍မရ'}</div></div></div>
      <div><h3 className="font-semibold">Complaint</h3><p className="whitespace-pre-wrap text-sm text-slate-600">{detail.complaintNote || '—'}</p></div>
      <div className="flex flex-wrap gap-2">
        {can('CAN_ACCESS_BOOKING_CONVERT_JOB') && detail.status === 'CONFIRMED' && !detail.linkedJobs.length && <button disabled={saving} onClick={() => action('Outdoor Job ပြောင်းမည်လား?', 'OUTDOOR ServiceJob တစ်ခုဖန်တီးပါမည်။', () => bookingService.convertOutdoor(detail.id), 'Outdoor Job ဖန်တီးပြီးပါပြီ')} className="flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 font-semibold text-white"><MapPin size={17} /> Outdoor Job ပြောင်း</button>}
        {can('CAN_ACCESS_BOOKING_UPDATE') && detail.status === 'CONFIRMED' && !detail.linkedJobs.length && <button onClick={() => { setItems([emptyItem()]); setItemsOpen(true); }} className="flex items-center gap-2 rounded-xl bg-amber-500 px-4 py-2.5 font-semibold text-white"><PackagePlus size={17} /> ပစ္စည်းလက်ခံ</button>}
        {!!detail.items.length && <button onClick={() => setPrintBookingId(detail.id)} className="flex items-center gap-2 rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-2.5 font-semibold text-indigo-800 hover:bg-indigo-100"><Printer size={17} /> လက်ခံ Voucher</button>}
        {can('CAN_ACCESS_BOOKING_CONVERT_JOB') && detail.status === 'ARRIVED' && detail.unconvertedItemCount > 0 && <button disabled={saving} onClick={() => action('Indoor Jobs ပြောင်းမည်လား?', 'မပြောင်းရသေးသော ပစ္စည်းတစ်ခုလျှင် Job တစ်ခုဖန်တီးပါမည်။', () => bookingService.convertIndoor(detail.id), 'Indoor Jobs ဖန်တီးပြီးပါပြီ')} className="flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 font-semibold text-white"><Home size={17} /> Indoor Job ပြောင်း</button>}
        {can('CAN_ACCESS_BOOKING_UPDATE') && detail.status !== 'CANCELED' && !detail.linkedJobs.length && <button disabled={saving} onClick={() => action('Booking ပယ်ဖျက်မည်လား?', 'Job ပြောင်းပြီးပါက Cancel မရပါ။', () => bookingService.updateStatus(detail.id, 'CANCELED'), 'ပယ်ဖျက်ပြီးပါပြီ')} className="rounded-xl border border-rose-300 px-4 py-2.5 font-semibold text-rose-600">Cancel</button>}
        {can('CAN_ACCESS_BOOKING_UPDATE') && detail.status !== 'CANCELED' && !detail.fullyConverted && <button onClick={() => openEdit(detail)} className="flex items-center gap-2 rounded-xl border px-4 py-2.5"><Pencil size={17} /> ပြင်ဆင်ရန်</button>}
      </div>
      <section><h3 className="mb-2 flex items-center gap-2 font-semibold"><PackagePlus size={18} /> လက်ခံပစ္စည်း ({detail.items.length})</h3>{!detail.items.length ? <div className="rounded-xl border border-dashed p-5 text-center text-sm text-slate-500">ဆိုင်အပ်ပစ္စည်း မရှိသေးပါ။</div> : <div className="grid gap-3 md:grid-cols-2">{detail.items.map((x, i) => <div key={x.id || i} className="rounded-xl border p-4"><div className="flex justify-between gap-2"><div><b>{i + 1}. {x.itemName}</b><div className="text-xs text-slate-500">{x.deviceType || 'Device type မရှိ'} {x.serialNo ? '• S/N ' + x.serialNo : ''}</div></div>{x.convertedJobId ? <span className="h-fit rounded-full bg-emerald-100 px-2 py-1 text-xs text-emerald-700">Job #{x.convertedJobId}</span> : can('CAN_ACCESS_BOOKING_UPDATE') && <button onClick={() => action('ပစ္စည်းဖယ်မည်လား?', x.itemName, () => bookingService.removeItem(detail.id, x.id!), 'ဖယ်ပြီးပါပြီ')} className="text-rose-600"><Trash2 size={16} /></button>}</div>{x.noticed && <p className="mt-2 text-sm text-amber-800"><span className="font-semibold">Noticed:</span> {x.noticed}</p>}<p className="mt-2 text-sm text-slate-600">{x.problemDesc || detail.complaintNote || 'ပြဿနာဖော်ပြချက်မရှိ'}</p>{!!x.photos?.length && <div className="mt-3"><DevicePhotoSlots photos={x.photos || []} readOnly /></div>}</div>)}</div>}</section>
      <section><h3 className="mb-2 flex items-center gap-2 font-semibold"><Wrench size={18} /> Linked Jobs ({detail.linkedJobs.length})</h3>{!detail.linkedJobs.length ? <div className="rounded-xl border border-dashed p-5 text-center text-sm text-slate-500">Service Job မပြောင်းရသေးပါ။</div> : <div className="overflow-x-auto rounded-xl border"><table className="min-w-full text-sm"><thead className="bg-slate-50 text-left"><tr><th className="px-3 py-2">Job No</th><th>Mode</th><th>Item</th><th>Status</th></tr></thead><tbody className="divide-y">{detail.linkedJobs.map(j => <tr key={j.id}><td className="px-3 py-2 font-semibold text-blue-700">{j.jobNo}</td><td>{j.serviceMode}</td><td>{j.itemName}</td><td>{j.status === 'CANCELLED' ? <span className="rounded-full bg-rose-100 px-2 py-1 text-xs font-semibold text-rose-700">Job ပယ်ဖျက်ထား</span> : j.status}</td></tr>)}</tbody></table></div>}</section>
    </div></Modal>}

    {formOpen && <Modal title={editId ? 'Booking ပြင်ဆင်ရန်' : 'Booking အသစ်'} close={() => setFormOpen(false)} elevated><form onSubmit={save} className="grid gap-4 p-5 md:grid-cols-2"><div className="md:col-span-2"><div className="mb-1 flex items-center justify-between"><span className="text-sm font-medium text-slate-700">Customer <b className="text-rose-500">*</b></span><button type="button" onClick={() => setShowAddCustomer(true)} className="inline-flex items-center gap-1 rounded-lg border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50"><Plus size={12} /> အသစ်</button></div><PortaledCombobox items={customerItems} value={Number(form.customerId) || 0} placeholder="အမည် / ဖုန်းနံပါတ်ဖြင့်ရှာပါ..." inputClassName={input} onChange={id => setForm({ ...form, customerId: id ? String(id) : '' })} /></div><Label text="Booking Date" required><input type="date" className={input} value={form.bookingDate} onChange={e => setForm({ ...form, bookingDate: e.target.value })} /></Label><Label text="Appointment Time"><input type="datetime-local" className={input} value={form.appointmentDate} onChange={e => setForm({ ...form, appointmentDate: e.target.value })} /></Label><div className="md:col-span-2"><Label text="Customer Complaint"><textarea rows={4} className={input} value={form.complaintNote} onChange={e => setForm({ ...form, complaintNote: e.target.value })} /></Label></div><div className="md:col-span-2"><Label text="Remark"><textarea rows={2} className={input} value={form.remark} onChange={e => setForm({ ...form, remark: e.target.value })} /></Label></div><div className="flex justify-end gap-2 md:col-span-2"><button type="button" onClick={() => setFormOpen(false)} className="rounded-xl border px-4 py-2.5">မလုပ်တော့ပါ</button><button disabled={saving} className="rounded-xl bg-blue-600 px-5 py-2.5 font-semibold text-white disabled:opacity-50">သိမ်းမည်</button></div></form></Modal>}

    {itemsOpen && detail && <Modal title="ဆိုင်အပ်ပစ္စည်း လက်ခံရန်" close={() => setItemsOpen(false)} wide elevated><div className="space-y-4 p-5"><div className="rounded-xl bg-blue-50 p-3 text-sm text-blue-800">ပစ္စည်းတစ်ခုစီသည် Indoor convert လုပ်ချိန်တွင် Service Job တစ်ခုစီဖြစ်လာပါမည်။</div>{items.map((x, i) => <div key={i} className="relative grid gap-3 rounded-xl border p-4 pt-6 md:grid-cols-3"><span className="absolute -top-3 left-3 rounded-full bg-slate-800 px-2 py-1 text-xs text-white">ပစ္စည်း {i + 1}</span>{items.length > 1 && <button onClick={() => setItems(a => a.filter((_, n) => n !== i))} className="absolute right-2 top-2 text-rose-600"><Trash2 size={16} /></button>}<Label text="ပစ္စည်းအမည်" required><input className={input} value={x.itemName} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, itemName: e.target.value } : v))} /></Label><Label text="Device Type"><input className={input} value={x.deviceType} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, deviceType: e.target.value } : v))} /></Label><Label text="Serial No"><input className={input} value={x.serialNo} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, serialNo: e.target.value } : v))} /></Label><Label text="Color"><input className={input} value={x.color} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, color: e.target.value } : v))} /></Label><Label text="Accessories"><input className={input} value={x.accessories} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, accessories: e.target.value } : v))} /></Label><Label text="လက်ခံချိန် Condition"><input className={input} value={x.itemCondition} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, itemCondition: e.target.value } : v))} /></Label><div className="md:col-span-3"><Label text="Noticed"><textarea className={input} rows={2} placeholder="လက်ခံစဉ် သတိထားမိသော အချက်အလက်" value={x.noticed} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, noticed: e.target.value } : v))} /></Label></div><div className="md:col-span-3"><Label text="Problem Description"><textarea className={input} rows={2} placeholder={detail.complaintNote || 'Booking complaint ကို fallback သုံးပါမည်'} value={x.problemDesc} onChange={e => setItems(a => a.map((v, n) => n === i ? { ...v, problemDesc: e.target.value } : v))} /></Label></div><div className="md:col-span-3"><DevicePhotoSlots photos={x.photos || []} onChange={photos => setItems(a => a.map((v, n) => n === i ? { ...v, photos } : v))} /></div></div>)}<button onClick={() => setItems(a => [...a, emptyItem()])} className="flex items-center gap-2 rounded-xl border border-dashed border-blue-400 px-4 py-2.5 font-semibold text-blue-700"><Plus size={17} /> နောက်ထပ်ပစ္စည်း</button><div className="flex justify-end gap-2 border-t pt-4"><button onClick={() => setItemsOpen(false)} className="rounded-xl border px-4 py-2.5">မလုပ်တော့ပါ</button><button disabled={saving} onClick={receive} className="rounded-xl bg-amber-500 px-5 py-2.5 font-semibold text-white disabled:opacity-50">ပစ္စည်းလက်ခံမည်</button></div></div></Modal>}

    {printBookingId && (
      <InvoicePrintPreview
        documentType="BOOKING"
        documentId={printBookingId}
        title="လက်ခံ Voucher"
        defaultPaper="POS_80MM"
        onClose={() => setPrintBookingId(null)}
      />
    )}
  </div>;
}
