import React, { useEffect, useState } from 'react';
import { Loader2, Scale, Save } from 'lucide-react';
import { api } from '../services/api';

const fieldClass =
  'mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-800 outline-none transition focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-50';

const WEEKDAYS = [
  ['MONDAY', 'တနင်္လာ'],
  ['TUESDAY', 'အင်္ဂါ'],
  ['WEDNESDAY', 'ဗုဒ္ဓဟူး'],
  ['THURSDAY', 'ကြာသပတေး'],
  ['FRIDAY', 'သောကြာ'],
  ['SATURDAY', 'စနေ'],
  ['SUNDAY', 'တနင်္ဂနွေ'],
] as const;

function sliceTime(value?: string | null, fallback = '09:00') {
  const text = String(value || fallback);
  return text.length >= 5 ? text.slice(0, 5) : fallback;
}

function weekdayRows(form: any) {
  const existing = Array.isArray(form?.weekdayHours) ? form.weekdayHours : [];
  const byDay = Object.fromEntries(existing.filter(Boolean).map((row: any) => [row.day, row]));
  const openDays = String(form?.deliveryDays || '').split(',').filter(Boolean);
  return WEEKDAYS.map(([day]) => {
    const row = byDay[day];
    return {
      day,
      open: row ? row.open !== false : openDays.includes(day) || openDays.length === 0,
      opensAt: sliceTime(row?.opensAt || form?.opensAt, '09:00'),
      closesAt: sliceTime(row?.closesAt || form?.closesAt, '18:00'),
    };
  });
}

function DeliveryHoursEditor({ form, setForm }: { form: any; setForm: (next: any) => void }) {
  const rows = weekdayRows(form);
  const closedDates: { date?: string; reason?: string }[] = Array.isArray(form.closedDates) ? form.closedDates : [];
  const minLeadDays = form.minLeadDays == null ? 1 : Number(form.minLeadDays);
  const patchHours = (nextRows: typeof rows) => {
    const open = nextRows.filter(row => row.open);
    setForm({
      ...form,
      weekdayHours: nextRows,
      deliveryDays: open.map(row => row.day).join(','),
      opensAt: open[0]?.opensAt || form.opensAt || '09:00',
      closesAt: open[0]?.closesAt || form.closesAt || '18:00',
    });
  };
  return (
    <div className="space-y-3 rounded-xl border border-indigo-100 bg-indigo-50/40 p-3">
      <p className="text-sm font-black text-slate-800">နေ့စဉ် Order လက်ခံမည့်အချိန်</p>
      <p className="text-xs text-slate-500">နေ့တစ်နေ့ချင်း ဖွင့်/ပိတ်နှင့် အချိန် သတ်မှတ်ပါ။ Customer Web နှင့် App က ဤအချိန်အတွင်းသာ ရွေးနိုင်သည်။</p>
      <div className="grid grid-cols-2 gap-3">
        <label className="text-xs font-bold text-slate-600">ပုံသေ စချိန်
          <input type="time" className={fieldClass} value={sliceTime(form.opensAt, '09:00')} onChange={e => setForm({ ...form, opensAt: e.target.value })}/>
        </label>
        <label className="text-xs font-bold text-slate-600">ပုံသေ ဆုံးချိန်
          <input type="time" className={fieldClass} value={sliceTime(form.closesAt, '18:00')} onChange={e => setForm({ ...form, closesAt: e.target.value })}/>
        </label>
      </div>
      <button
        type="button"
        className="rounded-lg border border-indigo-200 bg-white px-2.5 py-1.5 text-xs font-bold text-indigo-700"
        onClick={() => patchHours(rows.map(row => row.open ? { ...row, opensAt: sliceTime(form.opensAt, '09:00'), closesAt: sliceTime(form.closesAt, '18:00') } : row))}
      >
        ဖွင့်ရက်အားလုံးကို ပုံသေအချိန်သို့ ကူးထည့်
      </button>
      <div className="space-y-2">
        {rows.map((row, index) => {
          const label = WEEKDAYS[index][1];
          return (
            <div key={row.day} className="flex flex-wrap items-center gap-2 rounded-lg border border-white bg-white/80 p-2">
              <label className="flex min-w-[7.5rem] items-center gap-2 text-xs font-bold text-slate-700">
                <input
                  type="checkbox"
                  checked={row.open}
                  onChange={e => patchHours(rows.map(item => item.day === row.day ? { ...item, open: e.target.checked } : item))}
                />
                {label}
              </label>
              {row.open ? (
                <>
                  <input type="time" className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1.5 text-sm font-semibold" value={row.opensAt} onChange={e => patchHours(rows.map(item => item.day === row.day ? { ...item, opensAt: e.target.value } : item))}/>
                  <span className="text-xs text-slate-400">—</span>
                  <input type="time" className="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1.5 text-sm font-semibold" value={row.closesAt} onChange={e => patchHours(rows.map(item => item.day === row.day ? { ...item, closesAt: e.target.value } : item))}/>
                </>
              ) : (
                <span className="text-xs font-semibold text-rose-600">ပိတ်ရက်</span>
              )}
            </div>
          );
        })}
      </div>
      <label className="block text-xs font-bold text-slate-600">
        အနည်းဆုံး ကြိုမှာရက်
        <input
          type="number"
          min={0}
          max={30}
          className={fieldClass}
          value={minLeadDays}
          onChange={e => setForm({ ...form, minLeadDays: e.target.value === '' ? 1 : Number(e.target.value) })}
        />
      </label>
      <p className="text-xs text-slate-500">၀ = ယနေ့ ရွေးနိုင်သည်။ ၁ = မနက်ဖြန်မှ။</p>
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <p className="text-xs font-black uppercase tracking-wide text-slate-500">အထူးပိတ်ရက်</p>
          <button
            type="button"
            className="text-xs font-bold text-indigo-700"
            onClick={() => setForm({ ...form, closedDates: [...closedDates, { date: '', reason: '' }] })}
          >
            ရက်ထည့်
          </button>
        </div>
        {closedDates.length === 0 && <p className="text-xs text-slate-400">ရက်စွဲအလိုက် ပိတ်ရက် မရှိသေးပါ။</p>}
        {closedDates.map((item, index) => (
          <div key={index} className="grid gap-2 sm:grid-cols-[9rem_1fr_auto]">
            <input
              type="date"
              className={fieldClass}
              value={String(item.date || '').slice(0, 10)}
              onChange={e => setForm({
                ...form,
                closedDates: closedDates.map((row, i) => i === index ? { ...row, date: e.target.value } : row),
              })}
            />
            <input
              className={fieldClass}
              placeholder="အကြောင်းပြချက်"
              value={item.reason || ''}
              onChange={e => setForm({
                ...form,
                closedDates: closedDates.map((row, i) => i === index ? { ...row, reason: e.target.value } : row),
              })}
            />
            <button
              type="button"
              className="rounded-lg border px-2 text-xs font-bold text-rose-600"
              onClick={() => setForm({ ...form, closedDates: closedDates.filter((_, i) => i !== index) })}
            >
              ဖျက်
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

function ShippingEditor({ productId, policy = false }: { productId?: number; policy?: boolean }) {
  const [form, setForm] = useState<any>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const url = policy ? '/v1/customer-orders/delivery-policy' : '/v1/products/' + productId + '/shipping';

  useEffect(() => {
    let active = true;
    setForm(null);
    setMessage('');
    if (!policy && !productId) return;
    api
      .get<any>(url)
      .then((r) => {
        if (active) setForm(r.data);
      })
      .catch((e) => {
        if (active) setMessage(e.message);
      });
    return () => {
      active = false;
    };
  }, [url, policy, productId]);

  if (!policy && !productId) {
    return (
      <p className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500">
        ပစ္စည်းကို အရင်သိမ်းပြီး ပို့ဆောင်ရေးအချက်အလက် ဖြည့်ပါ။
      </p>
    );
  }

  const fields = policy
    ? [
        ['includedKg', 'အခြေခံပို့ခတွင် ပါဝင်သော kg'],
        ['extraPerKg', 'ကျော်လွန် ၁ kg ပို့ခ (ကျပ်)'],
        ['maxAutoKg', 'အလိုအလျောက်တွက်နိုင်သော အများဆုံး kg'],
        ['maxAutoQty', 'အော်ဒါတစ်ခု အများဆုံး အရေအတွက်'],
      ]
    : [
        ['weightKg', 'ထုပ်ပိုးပြီး အလေးချိန် (kg)'],
        ['maxAutoQty', 'ပစ္စည်းတစ်မျိုး အများဆုံး အရေအတွက် (optional)'],
      ];

  return (
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-start gap-3 border-b border-slate-100 px-5 py-4">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
          <Scale size={18} />
        </div>
        <div className="min-w-0">
          <h3 className="text-sm font-black uppercase tracking-wide text-slate-800">
            {policy ? 'Weight & quote policy' : 'ပစ္စည်း ပို့ဆောင်ရေးအချက်အလက်'}
          </h3>
          <p className="mt-1 text-xs leading-relaxed text-slate-500">
            မဖြည့်ရသေးခြင်း၊ အရွယ်ကြီးခြင်း၊ သတ်မှတ်ချက်ကျော်ခြင်းကို ဆိုင်မှ ပို့ခတွက်ပေးရမည်။ ကျော်လွန်အလေးချိန်ကို ၁ kg အပြည့်သို့ အပေါ်တင်တွက်ပါမည်။
          </p>
        </div>
      </div>

      <div className="space-y-4 p-5">
        {form && (
          <>
            <div className={`grid gap-3 ${policy ? 'sm:grid-cols-2 xl:grid-cols-4' : 'sm:grid-cols-3'}`}>
              {!policy && (
                <label className="block text-[11px] font-black uppercase tracking-wider text-slate-400">
                  ပို့ဆောင်ရေးအမျိုးအစား
                  <select
                    className={fieldClass}
                    value={form.shippingClass}
                    onChange={(e) => setForm({ ...form, shippingClass: e.target.value })}
                  >
                    <option value="STANDARD">ပုံမှန်</option>
                    <option value="BULKY">အရွယ်ကြီး</option>
                    <option value="MANUAL">ဆိုင်မှတွက်ရန်</option>
                  </select>
                </label>
              )}
              {fields.map(([key, label]) => (
                <label key={key} className="block text-[11px] font-black uppercase tracking-wider text-slate-400">
                  {label}
                  <input
                    className={fieldClass}
                    type="number"
                    min={0}
                    step={key.includes('Qty') ? '1' : key === 'extraPerKg' ? '0.01' : '0.001'}
                    value={form[key] ?? ''}
                    onChange={(e) =>
                      setForm({ ...form, [key]: e.target.value === '' ? null : Number(e.target.value) })
                    }
                  />
                </label>
              ))}
            </div>
            {policy && (
              <DeliveryHoursEditor form={form} setForm={setForm} />
            )}
            {policy && (
              <label className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-3">
                <input
                  type="checkbox"
                  className="mt-1 h-4 w-4"
                  checked={form.deliveryEnabled !== false}
                  onChange={(e) => setForm({ ...form, deliveryEnabled: e.target.checked })}
                />
                <span>
                  <span className="block text-sm font-bold text-slate-800">Customer app / shop တွင် သွားပို့ ဖွင့်မည်</span>
                  <span className="text-xs text-slate-500">ပိတ်ထားရင် Customer က ဆိုင်မှာလာယူ + စရံကြိုလွှဲ သာ ရွေးနိုင်သည်။</span>
                </span>
              </label>
            )}
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                disabled={busy}
                className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-xs font-black uppercase tracking-wide text-white shadow-sm transition hover:bg-indigo-700 disabled:opacity-50"
                onClick={async () => {
                  setBusy(true);
                  setMessage('');
                  try {
                    await api.put(url, {
                      ...form,
                      weekdayHours: policy ? weekdayRows(form) : undefined,
                      closedDates: policy ? (Array.isArray(form.closedDates) ? form.closedDates.filter((d: any) => d?.date) : []) : undefined,
                      minLeadDays: policy ? Number(form.minLeadDays ?? 1) : undefined,
                    });
                    setMessage('သိမ်းပြီးပါပြီ');
                  } catch (e: any) {
                    setMessage(e.message || 'သိမ်းမရပါ');
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                {busy ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                သိမ်းမည်
              </button>
              {message && (
                <p
                  role="status"
                  className={`text-xs font-semibold ${message.includes('မရ') ? 'text-rose-600' : 'text-emerald-600'}`}
                >
                  {message}
                </p>
              )}
            </div>
          </>
        )}
        {!form && !message && (
          <div className="flex items-center gap-2 py-4 text-sm text-slate-400">
            <Loader2 size={16} className="animate-spin" /> Loading policy…
          </div>
        )}
        {!form && message && (
          <p role="status" className="text-sm font-semibold text-rose-600">
            {message}
          </p>
        )}
      </div>
    </section>
  );
}

export const DeliveryPolicyEditor = () => <ShippingEditor policy />;
export const ProductShippingEditor = ({ productId }: { productId?: number }) => (
  <ShippingEditor productId={productId} />
);
