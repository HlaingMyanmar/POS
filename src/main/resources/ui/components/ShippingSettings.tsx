import React, { useEffect, useState } from 'react';
import { Loader2, Scale, Save } from 'lucide-react';
import { api } from '../services/api';

const fieldClass =
  'mt-1.5 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-800 outline-none transition focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-50';

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
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
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
                    await api.put(url, form);
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
