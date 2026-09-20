const DAY_KEYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export type DeliveryWeekday = {
  day?: string;
  open?: boolean;
  opensAt?: string;
  closesAt?: string;
};

export type DeliveryClosedDate = {
  date?: string;
  reason?: string | null;
};

export type DeliveryHours = {
  opensAt?: string;
  closesAt?: string;
  days?: string;
  weekdays?: DeliveryWeekday[];
  closedDates?: DeliveryClosedDate[];
  minLeadDays?: number;
};

function timeMinutes(value: string | undefined, fallback: string) {
  const [h, m] = (value || fallback).slice(0, 5).split(':').map(Number);
  return h * 60 + m;
}

function sliceTime(value: string | undefined, fallback: string) {
  const text = (value || fallback).slice(0, 5);
  return text.length === 5 ? text : fallback;
}

function windowFor(date: Date, hours: DeliveryHours) {
  const day = DAY_KEYS[date.getDay()];
  const row = (hours.weekdays || []).find(item => item.day === day);
  const allowed = (hours.days || DAY_KEYS.slice(1).concat(DAY_KEYS[0]).join(',')).split(',').filter(Boolean);
  const open = row ? row.open !== false : (allowed.length ? allowed.includes(day) : true);
  return {
    open,
    opensAt: sliceTime(row?.opensAt || hours.opensAt, '09:00'),
    closesAt: sliceTime(row?.closesAt || hours.closesAt, '18:00'),
  };
}

function closedReason(date: Date, hours: DeliveryHours) {
  const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  const hit = (hours.closedDates || []).find(item => String(item.date || '').slice(0, 10) === key);
  if (!hit) return '';
  return hit.reason ? `ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည် — ${hit.reason}` : 'ရွေးထားသောနေ့ ပိတ်ရက်ဖြစ်သည်';
}

function leadMessage(minLeadDays: number) {
  if (minLeadDays <= 0) return 'ပို့မည့်ရက်ကို ယနေ့မှ ရွေးပါ';
  if (minLeadDays === 1) return 'ပို့မည့်ရက်ကို အနည်းဆုံး မနက်ဖြန်မှ ရွေးပါ';
  return `ပို့မည့်ရက်ကို အနည်းဆုံး ${minLeadDays} ရက် ကြိုရွေးပါ`;
}

export function earliestOpeningInput(hours: DeliveryHours = {}) {
  const minLead = hours.minLeadDays == null ? 1 : Math.max(0, Number(hours.minLeadDays) || 0);
  const date = new Date();
  date.setDate(date.getDate() + minLead);
  for (let i = 0; i < 60; i++) {
    if (!closedReason(date, hours) && windowFor(date, hours).open) break;
    date.setDate(date.getDate() + 1);
  }
  const window = windowFor(date, hours);
  const minutes = timeMinutes(window.opensAt, '09:00');
  date.setHours(Math.floor(minutes / 60), minutes % 60, 0, 0);
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}

export function scheduleHint(hours: DeliveryHours = {}) {
  const rows = hours.weekdays || [];
  const open = rows.filter(row => row.open !== false);
  if (open.length && new Set(open.map(row => `${sliceTime(row.opensAt, '09:00')}-${sliceTime(row.closesAt, '18:00')}`)).size > 1) {
    return 'ရွေးချယ်နိုင်ချိန် · နေ့အလိုက် ဆိုင်ဖွင့်ချိန်အတွင်း';
  }
  const sample = open[0];
  const opensAt = sliceTime(sample?.opensAt || hours.opensAt, '09:00');
  const closesAt = sliceTime(sample?.closesAt || hours.closesAt, '18:00');
  return `ရွေးချယ်နိုင်ချိန် · ${opensAt} မှ ${closesAt} မတိုင်မီ`;
}

export function deliveryScheduleError(value?: string, hours: DeliveryHours = {}) {
  if (!value) return 'ပို့မည့် ရက်နှင့် အချိန် ရွေးပါ';
  const requested = new Date(value);
  if (Number.isNaN(requested.getTime())) return 'ပို့မည့် ရက်နှင့် အချိန် မှန်ကန်စွာ ရွေးပါ';
  const minLead = hours.minLeadDays == null ? 1 : Math.max(0, Number(hours.minLeadDays) || 0);
  const earliest = new Date();
  earliest.setDate(earliest.getDate() + minLead);
  earliest.setHours(0, 0, 0, 0);
  if (requested < earliest) return leadMessage(minLead);
  const closed = closedReason(requested, hours);
  if (closed) return closed;
  const window = windowFor(requested, hours);
  if (!window.open) return 'ရွေးထားသောနေ့တွင် delivery ပိတ်ထားပါသည်';
  const minutes = requested.getHours() * 60 + requested.getMinutes();
  const open = timeMinutes(window.opensAt, '09:00');
  const close = timeMinutes(window.closesAt, '18:00');
  if (minutes < open || minutes >= close) {
    return `ပို့မည့်အချိန်ကို ${window.opensAt} မှ ${window.closesAt} မတိုင်မီ ရွေးပါ`;
  }
  return '';
}
