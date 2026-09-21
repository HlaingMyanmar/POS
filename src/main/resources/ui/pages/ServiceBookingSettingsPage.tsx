import React, { useEffect, useState } from 'react';
import { Ban, Calendar, Camera, Clock, Info, MapPinOff, Plus, RefreshCw, Save, Trash2, Truck } from 'lucide-react';
import Swal from 'sweetalert2';
import { serviceBookingSettingsService } from '../services/api';

interface ServiceBookingSettings {
  outdoorTransportationNotice: string;
  outdoorTransportationFee: number | null;
  maxPhotosPerItem: number;
  bookingRejectionMessage: string;
  outdoorBookingEnabled: boolean;
  outdoorBookingDisabledReason: string;
  maxAdvanceBookingDays: number;
  minNoticeHours: number;
  allowSameDayBooking: boolean;
  allowEmergencyRequest: boolean;
}

interface WeekdayHours {
  id?: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  active: boolean;
  displayOrder: number;
}

interface ArrivalWindow {
  id?: number;
  name: string;
  startTime: string;
  endTime: string;
  maxCapacity: number;
  active: boolean;
  displayOrder: number;
}

interface DateException {
  id?: number;
  exceptionDate: string;
  closed: boolean;
  opensAt: string;
  closesAt: string;
  reason: string;
  disabledArrivalWindowIds: number[];
}

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

const defaultSettings: ServiceBookingSettings = {
  outdoorTransportationNotice: '',
  outdoorTransportationFee: null,
  maxPhotosPerItem: 50,
  bookingRejectionMessage: '',
  outdoorBookingEnabled: true,
  outdoorBookingDisabledReason: '',
  maxAdvanceBookingDays: 7,
  minNoticeHours: 2,
  allowSameDayBooking: true,
  allowEmergencyRequest: true,
};

const toTimeInput = (v?: string | null) => {
  if (!v) return '';
  return v.length >= 5 ? v.slice(0, 5) : v;
};

const ServiceBookingSettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<ServiceBookingSettings>(defaultSettings);
  const [feeInput, setFeeInput] = useState('');
  const [photosInput, setPhotosInput] = useState('50');
  const [advanceInput, setAdvanceInput] = useState('7');
  const [noticeInput, setNoticeInput] = useState('2');
  const [weekdayHours, setWeekdayHours] = useState<WeekdayHours[]>([]);
  const [windows, setWindows] = useState<ArrivalWindow[]>([]);
  const [exceptions, setExceptions] = useState<DateException[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const applyLoaded = (data: any) => {
    const next: ServiceBookingSettings = {
      outdoorTransportationNotice: data.outdoorTransportationNotice ?? '',
      outdoorTransportationFee: data.outdoorTransportationFee ?? null,
      maxPhotosPerItem: Number(data.maxPhotosPerItem) > 0 ? Number(data.maxPhotosPerItem) : 50,
      bookingRejectionMessage: data.bookingRejectionMessage ?? '',
      outdoorBookingEnabled: data.outdoorBookingEnabled !== false,
      outdoorBookingDisabledReason: data.outdoorBookingDisabledReason ?? '',
      maxAdvanceBookingDays: Number(data.maxAdvanceBookingDays) >= 0 ? Number(data.maxAdvanceBookingDays) : 7,
      minNoticeHours: Number(data.minNoticeHours) >= 0 ? Number(data.minNoticeHours) : 2,
      allowSameDayBooking: data.allowSameDayBooking !== false,
      allowEmergencyRequest: data.allowEmergencyRequest !== false,
    };
    setSettings(next);
    setFeeInput(next.outdoorTransportationFee != null ? String(next.outdoorTransportationFee) : '');
    setPhotosInput(String(next.maxPhotosPerItem));
    setAdvanceInput(String(next.maxAdvanceBookingDays));
    setNoticeInput(String(next.minNoticeHours));
  };

  const load = async () => {
    setLoading(true);
    try {
      const [settingsRes, hoursRes, windowsRes, exRes] = await Promise.all([
        serviceBookingSettingsService.getSettings(),
        serviceBookingSettingsService.listWeekdayHours(),
        serviceBookingSettingsService.listArrivalWindows(),
        serviceBookingSettingsService.listDateExceptions(),
      ]);
      if (settingsRes.success && settingsRes.data) applyLoaded(settingsRes.data);
      if (hoursRes.success && hoursRes.data) {
        setWeekdayHours(hoursRes.data.map((h: any) => ({
          id: h.id,
          dayOfWeek: h.dayOfWeek,
          startTime: toTimeInput(h.startTime),
          endTime: toTimeInput(h.endTime),
          active: h.active !== false,
          displayOrder: h.displayOrder ?? 0,
        })));
      }
      if (windowsRes.success && windowsRes.data) {
        setWindows(windowsRes.data.map((w: any) => ({
          id: w.id,
          name: w.name ?? '',
          startTime: toTimeInput(w.startTime),
          endTime: toTimeInput(w.endTime),
          maxCapacity: w.maxCapacity ?? 1,
          active: w.active !== false,
          displayOrder: w.displayOrder ?? 0,
        })));
      }
      if (exRes.success && exRes.data) {
        setExceptions(exRes.data.map((e: any) => ({
          id: e.id,
          exceptionDate: e.exceptionDate ?? '',
          closed: !!e.closed,
          opensAt: toTimeInput(e.opensAt),
          closesAt: toTimeInput(e.closesAt),
          reason: e.reason ?? '',
          disabledArrivalWindowIds: Array.isArray(e.disabledArrivalWindowIds) ? e.disabledArrivalWindowIds : [],
        })));
      }
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Settings ဖတ်မရပါ', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      const trimmedFee = feeInput.trim();
      let fee: number | null = null;
      if (trimmedFee !== '') {
        fee = Number(trimmedFee);
        if (!Number.isFinite(fee) || fee < 0) {
          Swal.fire('Error', 'သယ်ယူခသည် ၀ သို့မဟုတ် အပေါင်းကိန်းသာ ထည့်ပါ', 'error');
          setSaving(false);
          return;
        }
      }
      const maxPhotos = Number(photosInput.trim());
      if (!Number.isInteger(maxPhotos) || maxPhotos < 1 || maxPhotos > 100) {
        Swal.fire('Error', 'ပစ္စည်းတစ်ခုလျှင် ဓာတ်ပုံအရေအတွက်သည် ၁ မှ ၁၀၀ အတွင်း ဖြစ်ရမည်', 'error');
        setSaving(false);
        return;
      }
      const advance = Number(advanceInput.trim());
      const notice = Number(noticeInput.trim());
      if (!Number.isInteger(advance) || advance < 0 || advance > 90) {
        Swal.fire('Error', 'ကြိုတင်ရက်သည် ၀ မှ ၉၀ အတွင်း ဖြစ်ရမည်', 'error');
        setSaving(false);
        return;
      }
      if (!Number.isInteger(notice) || notice < 0 || notice > 168) {
        Swal.fire('Error', 'အနည်းဆုံး ကြိုတင်နာရီသည် ၀ မှ ၁၆၈ အတွင်း ဖြစ်ရမည်', 'error');
        setSaving(false);
        return;
      }
      if (!settings.outdoorBookingEnabled && !settings.outdoorBookingDisabledReason.trim()) {
        Swal.fire('Error', 'Outdoor booking ပိတ်ထားလျှင် ပိတ်ရခြင်းအကြောင်းအရင်း ထည့်ပါ', 'error');
        setSaving(false);
        return;
      }
      const payload = {
        outdoorTransportationNotice: settings.outdoorTransportationNotice.trim(),
        outdoorTransportationFee: fee,
        maxPhotosPerItem: maxPhotos,
        bookingRejectionMessage: settings.bookingRejectionMessage.trim(),
        outdoorBookingEnabled: settings.outdoorBookingEnabled,
        outdoorBookingDisabledReason: settings.outdoorBookingDisabledReason.trim(),
        maxAdvanceBookingDays: advance,
        minNoticeHours: notice,
        allowSameDayBooking: settings.allowSameDayBooking,
        allowEmergencyRequest: settings.allowEmergencyRequest,
      };
      const res = await serviceBookingSettingsService.saveSettings(payload);
      if (res.success && res.data) {
        applyLoaded(res.data);
        Swal.fire('Saved', 'Service & Booking Settings သိမ်းပြီးပါပြီ', 'success');
      } else {
        Swal.fire('Error', res.message || 'သိမ်းမရပါ', 'error');
      }
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'သိမ်းမရပါ', 'error');
    } finally {
      setSaving(false);
    }
  };

  const saveWeekday = async (row: WeekdayHours) => {
    try {
      const body = {
        dayOfWeek: row.dayOfWeek,
        startTime: row.startTime.length === 5 ? `${row.startTime}:00` : row.startTime,
        endTime: row.endTime.length === 5 ? `${row.endTime}:00` : row.endTime,
        active: row.active,
        displayOrder: row.displayOrder,
      };
      const res = row.id
        ? await serviceBookingSettingsService.updateWeekdayHours(row.id, body)
        : await serviceBookingSettingsService.createWeekdayHours(body);
      if (!res.success) throw new Error(res.message || 'Failed');
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Weekday hours သိမ်းမရပါ', 'error');
    }
  };

  const deleteWeekday = async (id?: number) => {
    if (!id) return;
    const ok = await Swal.fire({ title: 'ဖျက်မလား?', icon: 'warning', showCancelButton: true });
    if (!ok.isConfirmed) return;
    try {
      await serviceBookingSettingsService.deleteWeekdayHours(id);
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'ဖျက်မရပါ', 'error');
    }
  };

  const saveWindow = async (row: ArrivalWindow) => {
    try {
      const body = {
        name: row.name.trim(),
        startTime: row.startTime.length === 5 ? `${row.startTime}:00` : row.startTime,
        endTime: row.endTime.length === 5 ? `${row.endTime}:00` : row.endTime,
        maxCapacity: Number(row.maxCapacity),
        active: row.active,
        displayOrder: row.displayOrder,
      };
      const res = row.id
        ? await serviceBookingSettingsService.updateArrivalWindow(row.id, body)
        : await serviceBookingSettingsService.createArrivalWindow(body);
      if (!res.success) throw new Error(res.message || 'Failed');
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Arrival window သိမ်းမရပါ', 'error');
    }
  };

  const deleteWindow = async (id?: number) => {
    if (!id) return;
    const ok = await Swal.fire({
      title: 'ဖျက်မလား?',
      text: 'Booking များက ကိုးကားနေလျှင် ဖျက်မရပါ — active=false သုံးပါ',
      icon: 'warning',
      showCancelButton: true,
    });
    if (!ok.isConfirmed) return;
    try {
      await serviceBookingSettingsService.deleteArrivalWindow(id);
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'ဖျက်မရပါ', 'error');
    }
  };

  const saveException = async (row: DateException) => {
    try {
      const body = {
        exceptionDate: row.exceptionDate,
        closed: row.closed,
        opensAt: row.closed || !row.opensAt ? null : (row.opensAt.length === 5 ? `${row.opensAt}:00` : row.opensAt),
        closesAt: row.closed || !row.closesAt ? null : (row.closesAt.length === 5 ? `${row.closesAt}:00` : row.closesAt),
        reason: row.reason.trim() || null,
        disabledArrivalWindowIds: row.disabledArrivalWindowIds,
      };
      const res = row.id
        ? await serviceBookingSettingsService.updateDateException(row.id, body)
        : await serviceBookingSettingsService.createDateException(body);
      if (!res.success) throw new Error(res.message || 'Failed');
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Date exception သိမ်းမရပါ', 'error');
    }
  };

  const deleteException = async (id?: number) => {
    if (!id) return;
    const ok = await Swal.fire({ title: 'ဖျက်မလား?', icon: 'warning', showCancelButton: true });
    if (!ok.isConfirmed) return;
    try {
      await serviceBookingSettingsService.deleteDateException(id);
      await load();
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'ဖျက်မရပါ', 'error');
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-400">
        <RefreshCw size={22} className="mr-2 animate-spin" /> Settings ဖတ်နေသည်...
      </div>
    );
  }

  const input = 'rounded-lg border border-slate-200 px-2 py-1.5 text-sm outline-none focus:border-slate-400';

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Service & Booking Settings</h1>
          <p className="mt-1 text-sm text-slate-500">
            Customer app booking / outdoor availability နှင့် ဆိုင်လက်ခံ booking သတ်မှတ်ချက်များ
          </p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 hover:bg-slate-50"
        >
          <RefreshCw size={16} /> Reload
        </button>
      </div>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Calendar size={18} className="text-emerald-600" />
          <h2 className="text-lg font-semibold">Booking Rules</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-slate-700">အများဆုံး ကြိုတင်ရက်</label>
            <input type="number" min={0} max={90} value={advanceInput} onChange={(e) => setAdvanceInput(e.target.value)} className={`mt-1 w-full ${input}`} />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700">အနည်းဆုံး ကြိုတင်နာရီ</label>
            <input type="number" min={0} max={168} value={noticeInput} onChange={(e) => setNoticeInput(e.target.value)} className={`mt-1 w-full ${input}`} />
          </div>
          <label className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
            <input type="checkbox" className="mt-1" checked={settings.allowSameDayBooking} onChange={(e) => setSettings((s) => ({ ...s, allowSameDayBooking: e.target.checked }))} />
            <span className="text-sm"><b>ယနေ့စာရင်းသွင်းခွင့်</b><br /><span className="text-xs text-slate-500">Same-day outdoor / shop booking</span></span>
          </label>
          <label className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
            <input type="checkbox" className="mt-1" checked={settings.allowEmergencyRequest} onChange={(e) => setSettings((s) => ({ ...s, allowEmergencyRequest: e.target.checked }))} />
            <span className="text-sm"><b>အရေးပေါ် request</b><br /><span className="text-xs text-slate-500">EMERGENCY က min-notice / same-day ကို ဖြေလျှော့</span></span>
          </label>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Clock size={18} className="text-blue-600" />
            <h2 className="text-lg font-semibold">Weekly Business Hours</h2>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-lg border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700"
            onClick={() => setWeekdayHours((rows) => [...rows, { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '18:00', active: true, displayOrder: rows.length + 1 }])}
          >
            <Plus size={12} /> Period
          </button>
        </div>
        <p className="mb-3 text-xs text-slate-500">တစ်နေ့လျှင် period အများအပြား ထားနိုင်သည် (ဥပမာ နံနက် / ညနေ)။</p>
        <div className="space-y-2">
          {weekdayHours.map((row, idx) => (
            <div key={row.id ?? `new-${idx}`} className="grid items-center gap-2 rounded-lg border border-slate-100 bg-slate-50 p-2 md:grid-cols-6">
              <select value={row.dayOfWeek} onChange={(e) => setWeekdayHours((rows) => rows.map((r, i) => i === idx ? { ...r, dayOfWeek: e.target.value } : r))} className={input}>
                {DAYS.map((d) => <option key={d} value={d}>{d}</option>)}
              </select>
              <input type="time" value={row.startTime} onChange={(e) => setWeekdayHours((rows) => rows.map((r, i) => i === idx ? { ...r, startTime: e.target.value } : r))} className={input} />
              <input type="time" value={row.endTime} onChange={(e) => setWeekdayHours((rows) => rows.map((r, i) => i === idx ? { ...r, endTime: e.target.value } : r))} className={input} />
              <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={row.active} onChange={(e) => setWeekdayHours((rows) => rows.map((r, i) => i === idx ? { ...r, active: e.target.checked } : r))} /> Active</label>
              <button type="button" onClick={() => void saveWeekday(row)} className="rounded-lg bg-blue-600 px-2 py-1.5 text-xs font-semibold text-white">Save</button>
              <button type="button" onClick={() => void deleteWeekday(row.id)} className="inline-flex justify-center rounded-lg border border-rose-200 px-2 py-1.5 text-rose-600"><Trash2 size={14} /></button>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Clock size={18} className="text-violet-600" />
            <h2 className="text-lg font-semibold">Outdoor Arrival Windows</h2>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-lg border border-violet-200 px-2 py-1 text-xs font-semibold text-violet-700"
            onClick={() => setWindows((rows) => [...rows, { name: '', startTime: '09:00', endTime: '12:00', maxCapacity: 2, active: true, displayOrder: rows.length + 1 }])}
          >
            <Plus size={12} /> Window
          </button>
        </div>
        <p className="mb-3 text-xs text-slate-500">Capacity = customer booking request အရေအတွက် (technician မဟုတ်)။</p>
        <div className="space-y-2">
          {windows.map((row, idx) => (
            <div key={row.id ?? `w-${idx}`} className="grid items-center gap-2 rounded-lg border border-slate-100 bg-slate-50 p-2 md:grid-cols-7">
              <input value={row.name} placeholder="Name" onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, name: e.target.value } : r))} className={input} />
              <input type="time" value={row.startTime} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, startTime: e.target.value } : r))} className={input} />
              <input type="time" value={row.endTime} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, endTime: e.target.value } : r))} className={input} />
              <input type="number" min={1} value={row.maxCapacity} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, maxCapacity: Number(e.target.value) } : r))} className={input} />
              <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={row.active} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, active: e.target.checked } : r))} /> Active</label>
              <button type="button" onClick={() => void saveWindow(row)} className="rounded-lg bg-violet-600 px-2 py-1.5 text-xs font-semibold text-white">Save</button>
              <button type="button" onClick={() => void deleteWindow(row.id)} className="inline-flex justify-center rounded-lg border border-rose-200 px-2 py-1.5 text-rose-600"><Trash2 size={14} /></button>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Ban size={18} className="text-amber-600" />
            <h2 className="text-lg font-semibold">Special Date Exceptions</h2>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-lg border border-amber-200 px-2 py-1 text-xs font-semibold text-amber-800"
            onClick={() => setExceptions((rows) => [...rows, { exceptionDate: '', closed: true, opensAt: '', closesAt: '', reason: '', disabledArrivalWindowIds: [] }])}
          >
            <Plus size={12} /> Exception
          </button>
        </div>
        <div className="space-y-3">
          {exceptions.map((row, idx) => (
            <div key={row.id ?? `ex-${idx}`} className="space-y-2 rounded-lg border border-slate-100 bg-slate-50 p-3">
              <div className="grid gap-2 md:grid-cols-5">
                <input type="date" value={row.exceptionDate} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, exceptionDate: e.target.value } : r))} className={input} />
                <label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={row.closed} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, closed: e.target.checked, opensAt: '', closesAt: '' } : r))} /> Closed all day</label>
                <input type="time" disabled={row.closed} value={row.opensAt} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, opensAt: e.target.value } : r))} className={input} />
                <input type="time" disabled={row.closed} value={row.closesAt} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, closesAt: e.target.value } : r))} className={input} />
                <input value={row.reason} placeholder="Reason" onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, reason: e.target.value } : r))} className={input} />
              </div>
              {!row.closed && (
                <div className="flex flex-wrap gap-2 text-xs">
                  <span className="text-slate-500">Disable windows:</span>
                  {windows.filter((w) => w.id).map((w) => {
                    const checked = row.disabledArrivalWindowIds.includes(w.id!);
                    return (
                      <label key={w.id} className="inline-flex items-center gap-1 rounded border border-slate-200 bg-white px-2 py-1">
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => setExceptions((rows) => rows.map((r, i) => {
                            if (i !== idx) return r;
                            const ids = new Set(r.disabledArrivalWindowIds);
                            if (e.target.checked) ids.add(w.id!);
                            else ids.delete(w.id!);
                            return { ...r, disabledArrivalWindowIds: [...ids] };
                          }))}
                        />
                        {w.name}
                      </label>
                    );
                  })}
                </div>
              )}
              <div className="flex gap-2">
                <button type="button" onClick={() => void saveException(row)} className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white">Save</button>
                <button type="button" onClick={() => void deleteException(row.id)} className="inline-flex items-center gap-1 rounded-lg border border-rose-200 px-3 py-1.5 text-xs text-rose-600"><Trash2 size={12} /> Delete</button>
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Ban size={18} className="text-rose-600" />
          <h2 className="text-lg font-semibold">Booking ငြင်းပယ် စာသား</h2>
        </div>
        <textarea
          value={settings.bookingRejectionMessage}
          onChange={(e) => setSettings((s) => ({ ...s, bookingRejectionMessage: e.target.value }))}
          rows={4}
          maxLength={4000}
          className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800 outline-none focus:border-rose-400 focus:ring-2 focus:ring-rose-100"
        />
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <MapPinOff size={18} className="text-violet-600" />
          <h2 className="text-lg font-semibold">Outdoor / အိမ်အရောက် Booking</h2>
        </div>
        <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3">
          <input
            type="checkbox"
            className="mt-1 h-4 w-4 rounded border-slate-300 text-violet-600 focus:ring-violet-500"
            checked={settings.outdoorBookingEnabled}
            onChange={(e) => setSettings((s) => ({ ...s, outdoorBookingEnabled: e.target.checked }))}
          />
          <span>
            <span className="block text-sm font-semibold text-slate-800">Outdoor booking ဖွင့်ထားသည်</span>
            <span className="mt-0.5 block text-xs text-slate-500">ပိတ်ထားလျှင် customer app မှာ အိမ်အရောက် ရွေး၍ မရပါ။</span>
          </span>
        </label>
        {!settings.outdoorBookingEnabled && (
          <div className="mt-4">
            <label className="block text-sm font-medium text-slate-700">ပိတ်ရခြင်းအကြောင်းအရင်း *</label>
            <textarea
              value={settings.outdoorBookingDisabledReason}
              onChange={(e) => setSettings((s) => ({ ...s, outdoorBookingDisabledReason: e.target.value }))}
              rows={3}
              maxLength={2000}
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-violet-400"
            />
          </div>
        )}
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Truck size={18} className="text-amber-600" />
          <h2 className="text-lg font-semibold">အိမ်အရောက် သယ်ယူခ အသိပေးချက်</h2>
        </div>
        <textarea
          value={settings.outdoorTransportationNotice}
          onChange={(e) => setSettings((s) => ({ ...s, outdoorTransportationNotice: e.target.value }))}
          rows={5}
          maxLength={4000}
          className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-amber-400"
        />
        <label className="mt-4 block text-sm font-medium text-slate-700">သတ်မှတ်သယ်ယူခ (optional)</label>
        <input type="number" min={0} step="1" value={feeInput} onChange={(e) => setFeeInput(e.target.value)} className="mt-1 w-full max-w-xs rounded-lg border border-slate-200 px-3 py-2 text-sm" />
        <div className="mt-4 flex items-start gap-2 rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <Info size={16} className="mt-0.5 shrink-0" />
          <span>ဤစာသားသည် Customer Portal branding API မှတစ်ဆင့် customer app သို့ ပို့ပါသည်။</span>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Camera size={18} className="text-sky-600" />
          <h2 className="text-lg font-semibold">Booking Item ဓာတ်ပုံ ကန့်သတ်ချက်</h2>
        </div>
        <input type="number" min={1} max={100} value={photosInput} onChange={(e) => setPhotosInput(e.target.value)} className="w-full max-w-xs rounded-lg border border-slate-200 px-3 py-2 text-sm" />
      </section>

      <div className="flex justify-end">
        <button
          type="button"
          disabled={saving}
          onClick={() => void save()}
          className="inline-flex items-center gap-2 rounded-lg bg-slate-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-60"
        >
          {saving ? <RefreshCw size={16} className="animate-spin" /> : <Save size={16} />}
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </div>
    </div>
  );
};

export default ServiceBookingSettingsPage;
