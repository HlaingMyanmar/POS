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
const SECTIONS = [
  ['general', 'General'], ['schedule', 'Schedule'], ['outdoor', 'Outdoor'],
  ['exceptions', 'Exceptions'], ['customer-app', 'Customer App'],
] as const;
const addButton = 'inline-flex items-center gap-1.5 rounded-lg border border-blue-200 px-3 py-2 text-sm font-medium text-blue-700 hover:bg-blue-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500';
const actionButton = 'inline-flex items-center justify-center gap-1.5 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600';
const inputClass = 'w-full min-w-0 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100';
const errorText = 'mt-1 text-sm text-rose-700';
const friendlyError = (error: unknown, fallback: string) => {
  const message = error instanceof Error ? error.message : '';
  if (!message || /HTTP\s*\d|SQL|exception|stack|java\.|database/i.test(message)) return fallback;
  return message;
};

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
  const [savedSettings, setSavedSettings] = useState<ServiceBookingSettings | null>(null);
  const [savedRows, setSavedRows] = useState({ hours: [] as WeekdayHours[], windows: [] as ArrivalWindow[], exceptions: [] as DateException[] });
  const [busyRow, setBusyRow] = useState('');
  const [rowError, setRowError] = useState<Record<string, string>>({});
  const [feedback, setFeedback] = useState('');

  const currentSettings = { ...settings, outdoorTransportationFee: feeInput, maxPhotosPerItem: photosInput, maxAdvanceBookingDays: advanceInput, minNoticeHours: noticeInput };
  const loadedSettings = savedSettings && {
    ...savedSettings,
    outdoorTransportationFee: savedSettings.outdoorTransportationFee == null ? '' : String(savedSettings.outdoorTransportationFee),
    maxPhotosPerItem: String(savedSettings.maxPhotosPerItem),
    maxAdvanceBookingDays: String(savedSettings.maxAdvanceBookingDays),
    minNoticeHours: String(savedSettings.minNoticeHours),
  };
  const settingsDirty = !!loadedSettings && JSON.stringify(currentSettings) !== JSON.stringify(loadedSettings);
  const rowsDirty = JSON.stringify(weekdayHours) !== JSON.stringify(savedRows.hours)
    || JSON.stringify(windows) !== JSON.stringify(savedRows.windows)
    || JSON.stringify(exceptions) !== JSON.stringify(savedRows.exceptions);
  const anyDirty = settingsDirty || rowsDirty;
  const savedHour = (row: WeekdayHours) => savedRows.hours.find((item) => item.id === row.id);
  const savedWindow = (row: ArrivalWindow) => savedRows.windows.find((item) => item.id === row.id);
  const savedException = (row: DateException) => savedRows.exceptions.find((item) => item.id === row.id);
  const rowChanged = (row: WeekdayHours | ArrivalWindow | DateException, saved?: WeekdayHours | ArrivalWindow | DateException) =>
    !saved || JSON.stringify(row) !== JSON.stringify(saved);
  const markSaved = (message: string) => { setFeedback(message); window.setTimeout(() => setFeedback(''), 4000); };
  const discard = () => {
    if (savedSettings) applyLoaded(savedSettings);
    setWeekdayHours(savedRows.hours.map((row) => ({ ...row })));
    setWindows(savedRows.windows.map((row) => ({ ...row })));
    setExceptions(savedRows.exceptions.map((row) => ({ ...row, disabledArrivalWindowIds: [...row.disabledArrivalWindowIds] })));
    setRowError({});
  };

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
    setSavedSettings(next);
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
        const hours = hoursRes.data.map((h: any) => ({
          id: h.id,
          dayOfWeek: h.dayOfWeek,
          startTime: toTimeInput(h.startTime),
          endTime: toTimeInput(h.endTime),
          active: h.active !== false,
          displayOrder: h.displayOrder ?? 0,
        }));
        setWeekdayHours(hours);
        setSavedRows((previous) => ({ ...previous, hours }));
      }
      if (windowsRes.success && windowsRes.data) {
        const loadedWindows = windowsRes.data.map((w: any) => ({
          id: w.id,
          name: w.name ?? '',
          startTime: toTimeInput(w.startTime),
          endTime: toTimeInput(w.endTime),
          maxCapacity: w.maxCapacity ?? 1,
          active: w.active !== false,
          displayOrder: w.displayOrder ?? 0,
        }));
        setWindows(loadedWindows);
        setSavedRows((previous) => ({ ...previous, windows: loadedWindows }));
      }
      if (exRes.success && exRes.data) {
        const loadedExceptions = exRes.data.map((e: any) => ({
          id: e.id,
          exceptionDate: e.exceptionDate ?? '',
          closed: !!e.closed,
          opensAt: toTimeInput(e.opensAt),
          closesAt: toTimeInput(e.closesAt),
          reason: e.reason ?? '',
          disabledArrivalWindowIds: Array.isArray(e.disabledArrivalWindowIds) ? e.disabledArrivalWindowIds : [],
        }));
        setExceptions(loadedExceptions);
        setSavedRows((previous) => ({ ...previous, exceptions: loadedExceptions }));
      }
    } catch (e: any) {
      Swal.fire('Error', friendlyError(e, 'Settings မဖတ်နိုင်ပါ။ ပြန်ကြိုးစားပါ။'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!anyDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [anyDirty]);

  const reload = async () => {
    if (anyDirty) {
      const decision = await Swal.fire({ title: 'Unsaved changes ရှိနေပါသည်', text: 'Reload လုပ်ပါက မသိမ်းရသေးသော ပြင်ဆင်ချက်များ ပျောက်သွားပါမည်။', icon: 'warning', showCancelButton: true, confirmButtonText: 'Reload', cancelButtonText: 'မလုပ်တော့ပါ' });
      if (!decision.isConfirmed) return;
    }
    await load();
  };

  const save = async () => {
    if (!settingsDirty || saving) return;
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
      if (!photosInput.trim() || !Number.isInteger(maxPhotos) || maxPhotos < 1 || maxPhotos > 100) {
        Swal.fire('Error', 'ပစ္စည်းတစ်ခုလျှင် ဓာတ်ပုံအရေအတွက်သည် ၁ မှ ၁၀၀ အတွင်း ဖြစ်ရမည်', 'error');
        setSaving(false);
        return;
      }
      const advance = Number(advanceInput.trim());
      const notice = Number(noticeInput.trim());
      if (!advanceInput.trim() || !Number.isInteger(advance) || advance < 0 || advance > 90) {
        Swal.fire('Error', 'ကြိုတင်ရက်သည် ၀ မှ ၉၀ အတွင်း ဖြစ်ရမည်', 'error');
        setSaving(false);
        return;
      }
      if (!noticeInput.trim() || !Number.isInteger(notice) || notice < 0 || notice > 168) {
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
        markSaved('Settings saved');
      } else {
        Swal.fire('Error', friendlyError(new Error(res.message || ''), 'Settings မသိမ်းနိုင်ပါ။ ပြန်ကြိုးစားပါ။'), 'error');
      }
    } catch (e: any) {
      Swal.fire('Error', friendlyError(e, 'Settings မသိမ်းနိုင်ပါ။ ပြန်ကြိုးစားပါ။'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const saveWeekday = async (row: WeekdayHours, key: string) => {
    if (!row.startTime || !row.endTime || row.startTime >= row.endTime) {
      setRowError((errors) => ({ ...errors, [key]: 'Start time သည် End time ထက်စောရပါမည်။' }));
      return;
    }
    setBusyRow(key);
    setRowError((errors) => ({ ...errors, [key]: '' }));
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
      if (!res.success || !res.data) throw new Error(res.message || 'Failed');
      const updated: WeekdayHours = { ...row, id: res.data.id, startTime: toTimeInput(res.data.startTime), endTime: toTimeInput(res.data.endTime) };
      setWeekdayHours((rows) => rows.map((item) => item === row ? updated : item));
      setSavedRows((previous) => ({ ...previous, hours: row.id ? previous.hours.map((item) => item.id === row.id ? updated : item) : [...previous.hours, updated] }));
      markSaved('Business hours saved');
    } catch (e: any) {
      setRowError((errors) => ({ ...errors, [key]: friendlyError(e, 'Business hours မသိမ်းနိုင်ပါ။') }));
    } finally {
      setBusyRow('');
    }
  };

  const deleteWeekday = async (id?: number) => {
    if (!id) return;
    const ok = await Swal.fire({ title: 'ဖျက်မလား?', icon: 'warning', showCancelButton: true });
    if (!ok.isConfirmed) return;
    try {
      const result = await serviceBookingSettingsService.deleteWeekdayHours(id);
      if (!result.success) throw new Error(result.message || 'Failed');
      setWeekdayHours((rows) => rows.filter((row) => row.id !== id));
      setSavedRows((previous) => ({ ...previous, hours: previous.hours.filter((row) => row.id !== id) }));
      markSaved('Period deleted');
    } catch (e: any) {
      Swal.fire('Error', friendlyError(e, 'Period မဖျက်နိုင်ပါ။'), 'error');
    }
  };

  const saveWindow = async (row: ArrivalWindow, key: string) => {
    const error = !row.name.trim() ? 'Window name ထည့်ပါ။'
      : row.name.trim().length > 80 ? 'Window name သည် စာလုံး ၈၀ ထက်မပိုရပါ။'
      : !row.startTime || !row.endTime || row.startTime >= row.endTime ? 'Start time သည် End time ထက်စောရပါမည်။'
      : !Number.isInteger(Number(row.maxCapacity)) || Number(row.maxCapacity) < 1 || Number(row.maxCapacity) > 100 ? 'Capacity သည် ၁ မှ ၁၀၀ အတွင်းဖြစ်ရပါမည်။' : '';
    if (error) { setRowError((errors) => ({ ...errors, [key]: error })); return; }
    setBusyRow(key);
    setRowError((errors) => ({ ...errors, [key]: '' }));
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
      if (!res.success || !res.data) throw new Error(res.message || 'Failed');
      const updated: ArrivalWindow = { ...row, id: res.data.id, name: res.data.name, startTime: toTimeInput(res.data.startTime), endTime: toTimeInput(res.data.endTime), maxCapacity: res.data.maxCapacity };
      setWindows((rows) => rows.map((item) => item === row ? updated : item));
      setSavedRows((previous) => ({ ...previous, windows: row.id ? previous.windows.map((item) => item.id === row.id ? updated : item) : [...previous.windows, updated] }));
      markSaved('Arrival window saved');
    } catch (e: any) {
      setRowError((errors) => ({ ...errors, [key]: friendlyError(e, 'Arrival window မသိမ်းနိုင်ပါ။') }));
    } finally {
      setBusyRow('');
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
      const result = await serviceBookingSettingsService.deleteArrivalWindow(id);
      if (!result.success) throw new Error(result.message || 'Failed');
      setWindows((rows) => rows.filter((row) => row.id !== id));
      setSavedRows((previous) => ({ ...previous, windows: previous.windows.filter((row) => row.id !== id) }));
      markSaved('Arrival window deleted');
    } catch (e: any) {
      Swal.fire('Error', friendlyError(e, 'Arrival window မဖျက်နိုင်ပါ။ Active ကိုပိတ်၍ သိမ်းနိုင်ပါသည်။'), 'error');
    }
  };

  const saveException = async (row: DateException, key: string) => {
    const error = !row.exceptionDate ? 'Date ရွေးပါ။'
      : !row.closed && (!!row.opensAt !== !!row.closesAt) ? 'Start နှင့် End နှစ်ခုလုံးထည့်ပါ။'
      : !row.closed && row.opensAt && row.closesAt && row.opensAt >= row.closesAt ? 'Start time သည် End time ထက်စောရပါမည်။'
      : row.reason.length > 500 ? 'Reason သည် စာလုံး ၅၀၀ ထက်မပိုရပါ။' : '';
    if (error) { setRowError((errors) => ({ ...errors, [key]: error })); return; }
    setBusyRow(key);
    setRowError((errors) => ({ ...errors, [key]: '' }));
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
      if (!res.success || !res.data) throw new Error(res.message || 'Failed');
      const updated: DateException = { ...row, id: res.data.id, opensAt: toTimeInput(res.data.opensAt), closesAt: toTimeInput(res.data.closesAt), disabledArrivalWindowIds: [...row.disabledArrivalWindowIds] };
      setExceptions((rows) => rows.map((item) => item === row ? updated : item));
      setSavedRows((previous) => ({ ...previous, exceptions: row.id ? previous.exceptions.map((item) => item.id === row.id ? updated : item) : [...previous.exceptions, updated] }));
      markSaved('Date exception saved');
    } catch (e: any) {
      setRowError((errors) => ({ ...errors, [key]: friendlyError(e, 'Date exception မသိမ်းနိုင်ပါ။') }));
    } finally {
      setBusyRow('');
    }
  };

  const deleteException = async (id?: number) => {
    if (!id) return;
    const ok = await Swal.fire({ title: 'ဖျက်မလား?', icon: 'warning', showCancelButton: true });
    if (!ok.isConfirmed) return;
    try {
      const result = await serviceBookingSettingsService.deleteDateException(id);
      if (!result.success) throw new Error(result.message || 'Failed');
      setExceptions((rows) => rows.filter((row) => row.id !== id));
      setSavedRows((previous) => ({ ...previous, exceptions: previous.exceptions.filter((row) => row.id !== id) }));
      markSaved('Date exception deleted');
    } catch (e: any) {
      Swal.fire('Error', friendlyError(e, 'Date exception မဖျက်နိုင်ပါ။'), 'error');
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-400">
        <RefreshCw size={22} className="mr-2 animate-spin" /> Settings ဖတ်နေသည်...
      </div>
    );
  }

  const input = inputClass;

  return (
    <div className="w-full max-w-[1360px] space-y-6 p-4 pb-24 md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Service & Booking Settings</h1>
          <p className="mt-1 text-sm text-slate-600">Booking availability နှင့် Customer App အတွက် ဆိုင်၏ သတ်မှတ်ချက်များ</p>
        </div>
        <button
          type="button"
          onClick={() => void reload()}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 hover:bg-slate-50"
        >
          <RefreshCw size={16} /> Reload
        </button>
      </div>

      <div className="grid gap-6 lg:grid-cols-[190px_minmax(0,1fr)]">
      <nav aria-label="Settings sections" className="flex gap-1 overflow-x-auto rounded-xl border border-slate-200 bg-white p-2 lg:sticky lg:top-4 lg:h-fit lg:flex-col">
        {SECTIONS.map(([id, label]) => <a key={id} href={`#${id}`} className="shrink-0 rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-blue-50 hover:text-blue-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500">{label}</a>)}
      </nav>
      <main className="min-w-0 space-y-6">
      <section id="general" className="scroll-mt-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Calendar size={18} className="text-blue-600" />
          <h2 className="text-lg font-semibold">General · Booking Rules</h2>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label htmlFor="booking-ahead" className="block text-sm font-medium text-slate-700">Booking ahead</label>
            <div className="mt-1 flex max-w-xs items-center gap-3"><input id="booking-ahead" type="number" min={0} max={90} value={advanceInput} onChange={(e) => setAdvanceInput(e.target.value)} className={input} /><span className="text-sm text-slate-600">days</span></div>
            <p className="mt-1 text-sm text-slate-500">ကြိုတင် booking လုပ်နိုင်သည့် အများဆုံးရက် (၀–၉၀)</p>
          </div>
          <div>
            <label htmlFor="booking-notice" className="block text-sm font-medium text-slate-700">Minimum notice</label>
            <div className="mt-1 flex max-w-xs items-center gap-3"><input id="booking-notice" type="number" min={0} max={168} value={noticeInput} onChange={(e) => setNoticeInput(e.target.value)} className={input} /><span className="text-sm text-slate-600">hours</span></div>
            <p className="mt-1 text-sm text-slate-500">Booking မတိုင်မီ လိုအပ်သော ကြိုတင်ချိန် (၀–၁၆၈)</p>
          </div>
          <label className="flex cursor-pointer items-center justify-between gap-3 border-t border-slate-100 py-4 md:col-span-2">
            <span className="text-sm"><b>Same-day booking</b><br /><span className="text-sm text-slate-500">ယနေ့အတွက် ဆိုင်လာအပ်ခြင်း / Outdoor booking ခွင့်ပြုရန်</span></span>
            <span className="flex shrink-0 items-center gap-2"><span className="text-sm font-medium">{settings.allowSameDayBooking ? 'ON' : 'OFF'}</span><input type="checkbox" role="switch" className="h-5 w-5 accent-blue-600" checked={settings.allowSameDayBooking} onChange={(e) => setSettings((s) => ({ ...s, allowSameDayBooking: e.target.checked }))} /></span>
          </label>
          <label className="flex cursor-pointer items-center justify-between gap-3 border-t border-slate-100 py-4 md:col-span-2">
            <span className="text-sm"><b>Emergency request</b><br /><span className="text-sm text-slate-500">EMERGENCY အတွက် minimum notice / same-day ကန့်သတ်ချက် ဖြေလျှော့ရန်</span></span>
            <span className="flex shrink-0 items-center gap-2"><span className="text-sm font-medium">{settings.allowEmergencyRequest ? 'ON' : 'OFF'}</span><input type="checkbox" role="switch" className="h-5 w-5 accent-blue-600" checked={settings.allowEmergencyRequest} onChange={(e) => setSettings((s) => ({ ...s, allowEmergencyRequest: e.target.checked }))} /></span>
          </label>
        </div>
      </section>

      <section id="schedule" className="scroll-mt-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Clock size={18} className="text-blue-600" />
            <h2 className="text-lg font-semibold">Weekly Business Hours</h2>
          </div>
          <button
            type="button"
            className={addButton}
            onClick={() => setWeekdayHours((rows) => [...rows, { dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '18:00', active: true, displayOrder: rows.length + 1 }])}
          >
            <Plus size={16} /> Add period
          </button>
        </div>
        <p className="mb-4 text-sm text-slate-600">တစ်နေ့လျှင် period အများအပြားထားနိုင်ပါသည်။ Row တစ်ခုချင်းစီကို သီးသန့်သိမ်းပါ။</p>
        <div className="space-y-4">
          {DAYS.map((day) => {
            const rows = weekdayHours.map((row, idx) => ({ row, idx })).filter(({ row }) => row.dayOfWeek === day)
              .sort((a, b) => a.row.startTime.localeCompare(b.row.startTime) || a.row.displayOrder - b.row.displayOrder);
            return <div key={day} className="border-t border-slate-100 pt-3">
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-semibold capitalize text-slate-800">{day.toLowerCase()}</h3>
                <button type="button" className="text-sm font-medium text-blue-700 hover:underline" onClick={() => setWeekdayHours((all) => [...all, { dayOfWeek: day, startTime: '09:00', endTime: '18:00', active: true, displayOrder: all.length + 1 }])}>+ Add period</button>
              </div>
              {rows.length === 0 && <p className="text-sm text-slate-500">Closed · period မရှိပါ</p>}
              <div className="space-y-2">{rows.map(({ row, idx }) => {
                const key = `h-${row.id ?? idx}`;
                return <div key={key} className="rounded-lg bg-slate-50 p-3">
                  <div className="grid items-end gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto_auto_auto]">
                    <label className="text-sm text-slate-600">Start<input aria-describedby={rowError[key] ? `${key}-error` : undefined} type="time" value={row.startTime} onChange={(e) => setWeekdayHours((all) => all.map((r, i) => i === idx ? { ...r, startTime: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
                    <label className="text-sm text-slate-600">End<input type="time" value={row.endTime} onChange={(e) => setWeekdayHours((all) => all.map((r, i) => i === idx ? { ...r, endTime: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
                    <label className="flex h-10 items-center gap-2 text-sm"><input type="checkbox" checked={row.active} onChange={(e) => setWeekdayHours((all) => all.map((r, i) => i === idx ? { ...r, active: e.target.checked } : r))} />{row.active ? 'Active' : 'Inactive'}</label>
                    <button type="button" disabled={busyRow !== '' || !rowChanged(row, savedHour(row))} onClick={() => void saveWeekday(row, key)} className={actionButton}>{busyRow === key ? 'Saving...' : 'Save'}</button>
                    <button type="button" title="Delete" aria-label={`Delete ${day} period`} onClick={() => row.id ? void deleteWeekday(row.id) : setWeekdayHours((all) => all.filter((_, i) => i !== idx))} className="rounded-lg p-2 text-rose-600 hover:bg-rose-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-500"><Trash2 size={17} /></button>
                  </div>
                  {rowError[key] && <p id={`${key}-error`} role="alert" className={errorText}>{rowError[key]}</p>}
                </div>;
              })}</div>
            </div>;
          })}
        </div>
      </section>

      <section id="outdoor" className="scroll-mt-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Clock size={18} className="text-blue-600" />
            <h2 className="text-lg font-semibold">Outdoor Arrival Windows</h2>
          </div>
          <button
            type="button"
            className={addButton}
            onClick={() => setWindows((rows) => [...rows, { name: '', startTime: '09:00', endTime: '12:00', maxCapacity: 2, active: true, displayOrder: rows.length + 1 }])}
          >
            <Plus size={16} /> Add window
          </button>
        </div>
        <p className="mb-4 text-sm text-slate-600">Capacity = ဤ arrival window အတွက် လက်ခံနိုင်သည့် customer booking အများဆုံး (technician အရေအတွက် မဟုတ်)။</p>
        <div className="mb-2 hidden grid-cols-[minmax(120px,1.5fr)_repeat(2,minmax(105px,1fr))_minmax(90px,0.7fr)_80px_65px_40px] gap-2 px-2 text-sm font-medium text-slate-600 lg:grid"><span>Window</span><span>Start</span><span>End</span><span>Capacity</span><span>Status</span><span>Save</span><span>Action</span></div>
        <div className="space-y-2">
          {windows.map((row, idx) => (
            <div key={row.id ?? `w-${idx}`} className="rounded-lg border border-slate-100 bg-slate-50 p-2">
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(120px,1.5fr)_repeat(2,minmax(105px,1fr))_minmax(90px,0.7fr)_80px_65px_40px] lg:items-center">
                <label className="min-w-0 text-sm text-slate-600"><span className="lg:sr-only">Window</span><input aria-label="Window name" aria-describedby={rowError[`w-${row.id ?? idx}`] ? `w-${row.id ?? idx}-error` : undefined} value={row.name} maxLength={80} placeholder="Window name" onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, name: e.target.value } : r))} className={input} /></label>
                <label className="min-w-0 text-sm text-slate-600"><span className="lg:sr-only">Start</span><input aria-label="Start time" type="time" value={row.startTime} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, startTime: e.target.value } : r))} className={input} /></label>
                <label className="min-w-0 text-sm text-slate-600"><span className="lg:sr-only">End</span><input aria-label="End time" type="time" value={row.endTime} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, endTime: e.target.value } : r))} className={input} /></label>
                <label className="min-w-0 text-sm text-slate-600"><span className="lg:sr-only">Capacity</span><input aria-label="Capacity" type="number" min={1} max={100} value={row.maxCapacity} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, maxCapacity: Number(e.target.value) } : r))} className={input} /></label>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={row.active} onChange={(e) => setWindows((rows) => rows.map((r, i) => i === idx ? { ...r, active: e.target.checked } : r))} />{row.active ? 'Active' : 'Inactive'}</label>
                <button type="button" disabled={busyRow !== '' || !rowChanged(row, savedWindow(row))} onClick={() => void saveWindow(row, `w-${row.id ?? idx}`)} className={actionButton}>{busyRow === `w-${row.id ?? idx}` ? 'Saving...' : 'Save'}</button>
                <button type="button" title="Delete" aria-label={`Delete ${row.name || 'window'}`} onClick={() => row.id ? void deleteWindow(row.id) : setWindows((rows) => rows.filter((_, i) => i !== idx))} className="w-fit rounded-lg p-2 text-rose-600 hover:bg-rose-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-500"><Trash2 size={17} /></button>
              </div>
              {rowError[`w-${row.id ?? idx}`] && <p id={`w-${row.id ?? idx}-error`} role="alert" className={errorText}>{rowError[`w-${row.id ?? idx}`]}</p>}
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center gap-2 text-slate-800"><MapPinOff size={18} className="text-blue-600" /><h2 className="text-lg font-semibold">Outdoor booking availability</h2></div>
        <label className="flex cursor-pointer items-center justify-between gap-3 border-y border-slate-100 py-4">
          <span><span className="block text-sm font-semibold text-slate-800">Outdoor booking enabled</span><span className="block text-sm text-slate-500">ပိတ်ထားပါက Customer App မှ Outdoor ရွေး၍မရပါ။</span></span>
          <span className="flex shrink-0 items-center gap-2"><span className="text-sm font-medium">{settings.outdoorBookingEnabled ? 'ON' : 'OFF'}</span><input type="checkbox" role="switch" className="h-5 w-5 accent-blue-600" checked={settings.outdoorBookingEnabled} onChange={(e) => setSettings((s) => ({ ...s, outdoorBookingEnabled: e.target.checked }))} /></span>
        </label>
        {!settings.outdoorBookingEnabled && <div className="mt-4"><label htmlFor="outdoor-disabled-reason" className="block text-sm font-medium text-slate-700">Disabled reason · Shown in Customer App *</label><textarea id="outdoor-disabled-reason" value={settings.outdoorBookingDisabledReason} onChange={(e) => setSettings((s) => ({ ...s, outdoorBookingDisabledReason: e.target.value }))} rows={3} maxLength={2000} className={`mt-1 ${input}`} /></div>}
      </section>

      <section id="exceptions" className="scroll-mt-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-slate-800">
            <Ban size={18} className="text-amber-600" />
            <h2 className="text-lg font-semibold">Special Date Exceptions</h2>
          </div>
          <button
            type="button"
            className={addButton}
            onClick={() => setExceptions((rows) => [...rows, { exceptionDate: '', closed: true, opensAt: '', closesAt: '', reason: '', disabledArrivalWindowIds: [] }])}
          >
            <Plus size={16} /> Add exception
          </button>
        </div>
        <div className="space-y-3">
          {exceptions.map((row, idx) => (
            <div key={row.id ?? `ex-${idx}`} className="space-y-3 border-t border-slate-100 py-4">
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[150px_125px_105px_105px_minmax(150px,1fr)]">
                <label className="text-sm text-slate-600">Date<input type="date" value={row.exceptionDate} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, exceptionDate: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
                <label className="flex items-center gap-2 text-sm"><input type="checkbox" role="switch" checked={row.closed} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, closed: e.target.checked, opensAt: '', closesAt: '' } : r))} />Closed all day {row.closed ? 'ON' : 'OFF'}</label>
                {row.closed ? <span className="self-center text-sm text-slate-500 sm:col-span-2">All day closed · hours မလိုပါ</span> : <>
                  <label className="text-sm text-slate-600">Start<input type="time" value={row.opensAt} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, opensAt: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
                  <label className="text-sm text-slate-600">End<input type="time" value={row.closesAt} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, closesAt: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
                </>}
                <label className="text-sm text-slate-600">Reason<input value={row.reason} maxLength={500} onChange={(e) => setExceptions((rows) => rows.map((r, i) => i === idx ? { ...r, reason: e.target.value } : r))} className={`mt-1 ${input}`} /></label>
              </div>
              {!row.closed && (
                <div className="flex flex-wrap gap-2 text-sm">
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
              <div className="flex items-center gap-2">
                <button type="button" disabled={busyRow !== '' || !rowChanged(row, savedException(row))} onClick={() => void saveException(row, `ex-${row.id ?? idx}`)} className={actionButton}>{busyRow === `ex-${row.id ?? idx}` ? 'Saving...' : 'Save'}</button>
                <button type="button" title="Delete" aria-label={`Delete exception ${row.exceptionDate}`} onClick={() => row.id ? void deleteException(row.id) : setExceptions((rows) => rows.filter((_, i) => i !== idx))} className="rounded-lg p-2 text-rose-600 hover:bg-rose-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-rose-500"><Trash2 size={17} /></button>
              </div>
              {rowError[`ex-${row.id ?? idx}`] && <p role="alert" className={errorText}>{rowError[`ex-${row.id ?? idx}`]}</p>}
            </div>
          ))}
        </div>
      </section>

      <section id="customer-app" className="scroll-mt-5 rounded-xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">
        <div className="mb-4 flex items-center gap-2 text-slate-800"><Camera size={18} className="text-blue-600" /><h2 className="text-lg font-semibold">Customer App</h2></div>
        <p className="mb-5 text-sm text-slate-600">ဖောက်သည်မြင်နိုင်သော စာသားနှင့် booking photo ကန့်သတ်ချက်များ</p>
        <div className="space-y-6 divide-y divide-slate-100">
        <div>
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Ban size={18} className="text-rose-600" />
          <label htmlFor="booking-rejection" className="text-sm font-semibold">Booking closed / rejected message · Shown in Customer App</label>
        </div>
        <textarea
          id="booking-rejection"
          value={settings.bookingRejectionMessage}
          onChange={(e) => setSettings((s) => ({ ...s, bookingRejectionMessage: e.target.value }))}
          rows={4}
          maxLength={4000}
          className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800 outline-none focus:border-rose-400 focus:ring-2 focus:ring-rose-100"
        />
        </div>

      <div className="pt-6">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Truck size={18} className="text-amber-600" />
          <label htmlFor="transport-notice" className="text-sm font-semibold">Outdoor description · Shown in Customer App</label>
        </div>
        <textarea
          id="transport-notice"
          value={settings.outdoorTransportationNotice}
          onChange={(e) => setSettings((s) => ({ ...s, outdoorTransportationNotice: e.target.value }))}
          rows={5}
          maxLength={4000}
          className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-amber-400"
        />
        <label htmlFor="transport-fee" className="mt-4 block text-sm font-medium text-slate-700">Outdoor transportation charge (optional)</label>
        <div className="mt-1 flex max-w-xs items-center gap-3"><input id="transport-fee" type="number" min={0} step="1" value={feeInput} onChange={(e) => setFeeInput(e.target.value)} className={input} /><span className="text-sm text-slate-600">Ks</span></div>
        <p className="mt-1 text-sm text-slate-500">မသတ်မှတ်ထားပါက ကွက်လပ်ထားပါ။</p>
        <div className="mt-4 flex items-start gap-2 text-sm text-slate-600">
          <Info size={16} className="mt-0.5 shrink-0" />
          <span>ဤစာသားသည် Customer Portal branding API မှတစ်ဆင့် customer app သို့ ပို့ပါသည်။</span>
        </div>
      </div>

      <div className="pt-6">
        <div className="mb-4 flex items-center gap-2 text-slate-800">
          <Camera size={18} className="text-sky-600" />
          <label htmlFor="photos-limit" className="text-sm font-semibold">Maximum booking item photos</label>
        </div>
        <div className="flex max-w-xs items-center gap-3"><input id="photos-limit" type="number" min={1} max={100} value={photosInput} onChange={(e) => setPhotosInput(e.target.value)} className={input} /><span className="text-sm text-slate-600">photos</span></div>
        <p className="mt-1 text-sm text-slate-500">ပစ္စည်းတစ်ခုလျှင် ၁ မှ ၁၀၀ ပုံအထိ (staff intake)</p>
      </div>
      </div>
      </section>

      </main>
      </div>
      {feedback && <div role="status" className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-emerald-800">{feedback}</div>}
      {anyDirty && <div className="sticky bottom-2 z-20 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-white px-4 py-3 shadow-lg">
        <span className="text-sm font-medium text-amber-900">Unsaved changes {rowsDirty && '· Row changes ကို သီးသန့် Save လုပ်ပါ'}</span>
        <div className="flex gap-2"><button type="button" onClick={discard} className="rounded-lg border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50">Discard</button>
        {settingsDirty && <button
          type="button"
          disabled={saving || !settingsDirty}
          onClick={() => void save()}
          className={actionButton}
        >
          {saving ? <RefreshCw size={16} className="animate-spin" /> : <Save size={16} />}
          {saving ? 'Saving...' : 'Save settings'}
        </button>}
        </div>
      </div>}
    </div>
  );
};

export default ServiceBookingSettingsPage;
