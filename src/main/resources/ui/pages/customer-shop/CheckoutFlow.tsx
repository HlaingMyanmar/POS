import React, { useEffect, useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import {
  CartLine,
  CustomerSession,
  DeliveryQuote,
  customerPortalService,
} from '../../services/customerPortalApi';
import {
  deliveryScheduleError,
  earliestOpeningInput,
  scheduleHint,
  type DeliveryHours,
} from './deliverySchedule';

export { deliveryScheduleError } from './deliverySchedule';

export type DeliverySelection = {
  orderType: 'PICKUP' | 'DELIVERY';
  deliveryLocationMode: 'PROFILE' | 'OTHER';
  paymentChoice: 'TRANSFER' | 'PAY_ON_COLLECTION';
  requestedDeliveryAt?: string;
  regionId?: number;
  townshipId?: number;
  wardId?: number;
  deliveryPhone?: string;
  deliveryAddress?: string;
};

type Region = {
  id: number;
  name?: string;
  townships?: Township[];
};
type Township = {
  id: number;
  name?: string;
  deliveryCharge?: number;
  wards?: Ward[];
};
type Ward = { id: number; name?: string; deliveryCharge?: number };

type Step = 'CART' | 'FULFILLMENT' | 'SCHEDULE' | 'PAYMENT' | 'CONFIRM';

const money = (n?: number | null) => `${Number(n || 0).toLocaleString()} Ks`;

const stepsFor = (orderType: string): Step[] =>
  orderType === 'DELIVERY'
    ? ['CART', 'FULFILLMENT', 'SCHEDULE', 'CONFIRM']
    : ['CART', 'FULFILLMENT', 'PAYMENT', 'CONFIRM'];

const stepLabels = (orderType: string) =>
  orderType === 'DELIVERY'
    ? ['ပစ္စည်း', 'ပို့ဆောင်', 'ပို့ချိန်', 'အော်ဒါတင်']
    : ['ပစ္စည်း', 'ပို့ဆောင်', 'ငွေပေးချေ', 'အော်ဒါတင်'];

function toLocalInput(iso?: string) {
  if (!iso) return '';
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}

function toApiDateTime(local: string) {
  if (!local) return undefined;
  return local.length === 16 ? `${local}:00` : local;
}

export function CheckoutFlow({
  cart,
  persistCart,
  session,
  value,
  onChange,
  orderNote,
  onNoteChange,
  placing,
  pickupDepositPercent,
  deliveryEnabled = true,
  deliveryHours,
  onPlace,
  onNeedAuth,
  onBrowse,
}: {
  cart: CartLine[];
  persistCart: (next: CartLine[]) => void;
  session: CustomerSession | null;
  value: DeliverySelection;
  onChange: (next: DeliverySelection) => void;
  orderNote: string;
  onNoteChange: (note: string) => void;
  placing: boolean;
  pickupDepositPercent: number;
  deliveryEnabled?: boolean;
  deliveryHours?: DeliveryHours;
  onPlace: (promoCode?: string) => void;
  onNeedAuth: () => void;
  onBrowse: () => void;
}) {
  const [step, setStep] = useState<Step>('CART');
  const [locations, setLocations] = useState<Region[]>([]);
  const [quote, setQuote] = useState<DeliveryQuote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState('');
  const [promoCode, setPromoCode] = useState('');
  const [promoDiscount, setPromoDiscount] = useState(0);
  const [promoBusy, setPromoBusy] = useState(false);
  const [promoError, setPromoError] = useState('');
  const [appliedPromo, setAppliedPromo] = useState<string | undefined>();

  const itemsTotal = cart.reduce((n, l) => n + Number(l.product.sellingPrice || 0) * l.qty, 0);
  const depositPct = Math.min(100, Math.max(1, pickupDepositPercent || 30));
  const quotedFee = value.orderType === 'DELIVERY' && quote?.deliveryCharge != null
    ? Number(quote.deliveryCharge)
    : 0;
  const billedItems = Math.max(0, itemsTotal - promoDiscount);
  const needsDeposit = value.orderType === 'PICKUP' || (value.orderType === 'DELIVERY' && value.paymentChoice === 'PAY_ON_COLLECTION');
  const orderDeposit = needsDeposit
    ? Math.round(billedItems * depositPct) / 100
    : 0;
  const remainingAfterDeposit = needsDeposit
    ? Math.max(0, billedItems + quotedFee - orderDeposit)
    : 0;

  const region = locations.find(r => r.id === value.regionId);
  const township = region?.townships?.find(t => t.id === value.townshipId);
  const ward = township?.wards?.find(w => w.id === value.wardId);
  const grandTotal = billedItems + quotedFee;
  const fulfillmentReady = value.orderType !== 'DELIVERY' || (
    !!value.townshipId
    && (value.deliveryLocationMode === 'PROFILE'
      ? !!session?.address
      : !!(value.deliveryPhone?.trim() && value.deliveryAddress?.trim()))
  );
  const quoteReady = value.orderType !== 'DELIVERY'
    || (!quoteLoading && !quoteError && quote?.deliveryCharge != null);
  const scheduleError = value.orderType === 'DELIVERY' ? deliveryScheduleError(value.requestedDeliveryAt, deliveryHours) : '';
  const scheduleReady = value.orderType !== 'DELIVERY' || !scheduleError;

  useEffect(() => {
    customerPortalService.deliveryLocations()
      .then(r => setLocations(r.data || []))
      .catch(() => setLocations([]));
  }, []);

  useEffect(() => {
    setPromoDiscount(0);
    setAppliedPromo(undefined);
    setPromoError('');
  }, [cart]);

  useEffect(() => {
    if (!cart.length) setStep('CART');
  }, [cart.length]);

  useEffect(() => {
    if (!deliveryEnabled && value.orderType !== 'PICKUP') {
      onChange({ ...value, orderType: 'PICKUP', paymentChoice: 'TRANSFER' });
    }
  }, [deliveryEnabled]);

  useEffect(() => {
    if (value.orderType === 'PICKUP' && value.paymentChoice !== 'TRANSFER') {
      onChange({ ...value, paymentChoice: 'TRANSFER' });
    }
    const allowed = stepsFor(value.orderType);
    setStep(prev => (prev === 'CART' || allowed.includes(prev) ? prev : 'FULFILLMENT'));
  }, [value.orderType]);

  const linesKey = useMemo(
    () => JSON.stringify(cart.map(l => ({ productId: l.product.id, qty: l.qty }))),
    [cart]
  );

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setQuote(null);
    setQuoteError('');
    if (value.orderType !== 'DELIVERY' || !value.townshipId || !session || !cart.length) {
      setQuoteLoading(false);
      return;
    }
    setQuoteLoading(true);
    const timer = window.setTimeout(() => {
      customerPortalService.deliveryQuote({
        orderType: 'DELIVERY',
        townshipId: value.townshipId,
        wardId: value.wardId,
        lines: JSON.parse(linesKey),
      }, controller.signal)
        .then(r => { if (active) setQuote(r.data || null); })
        .catch(e => { if (active) setQuoteError(e?.message || 'ပို့ခတွက်မရပါ'); })
        .finally(() => { if (active) setQuoteLoading(false); });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [linesKey, value.orderType, value.townshipId, value.wardId, session, cart.length]);

  const path = stepsFor(value.orderType);
  const idx = Math.max(0, path.indexOf(step));
  const goNext = () => {
    if (step === 'CART' && !session) { onNeedAuth(); return; }
    if (idx < path.length - 1) setStep(path[idx + 1]);
  };
  const goBack = () => { if (idx > 0) setStep(path[idx - 1]); };

  if (!cart.length) {
    return (
      <div className="rounded-2xl border border-dashed py-12 text-center text-sm text-slate-400">
        ဘာမှ မထည့်ရသေးပါ
        <button type="button" onClick={onBrowse} className="mt-3 block w-full text-indigo-600 underline">ပစ္စည်းများ ကြည့်မည်</button>
      </div>
    );
  }

  return (
    <div className="relative space-y-3">
      {placing && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 rounded-2xl bg-white/80">
          <Loader2 className="h-10 w-10 animate-spin text-indigo-600" />
          <p className="text-sm font-bold text-indigo-800">အော်ဒါတင်နေသည် — ခဏစောင့်ပါ</p>
        </div>
      )}
      <ol className="grid grid-cols-4 gap-1 text-center text-[10px] font-semibold text-slate-500">
        {stepLabels(value.orderType).map((label, i) => (
          <li key={label} className={`rounded-lg py-1.5 ${i <= idx ? 'bg-indigo-600 text-white' : 'bg-slate-100'}`}>{label}</li>
        ))}
      </ol>

      {step === 'CART' && (
        <>
          {cart.map(line => (
            <div key={line.product.id} className="flex items-center justify-between gap-3 rounded-xl border bg-white p-3">
              <div>
                <div className="font-bold">{line.product.name}</div>
                <div className="text-xs text-indigo-600">{money(Number(line.product.sellingPrice || 0) * line.qty)}</div>
              </div>
              <div className="flex items-center gap-2">
                <button type="button" className="rounded-lg border px-2 py-1" onClick={() => persistCart(line.qty <= 1 ? cart.filter(l => l.product.id !== line.product.id) : cart.map(l => l.product.id === line.product.id ? { ...l, qty: l.qty - 1 } : l))}>−</button>
                <span className="w-6 text-center text-sm font-bold">{line.qty}</span>
                <button type="button" className="rounded-lg border px-2 py-1" onClick={() => persistCart(cart.map(l => l.product.id === line.product.id ? { ...l, qty: l.qty + 1 } : l))}>+</button>
              </div>
            </div>
          ))}
          <div className="flex gap-2">
            <input
              value={promoCode}
              onChange={(e) => setPromoCode(e.target.value.toUpperCase())}
              placeholder="Promo code"
              className="flex-1 rounded-xl border px-3 py-2 text-sm"
            />
            <button
              type="button"
              disabled={promoBusy || !promoCode.trim()}
              className="rounded-xl border px-3 py-2 text-sm font-bold"
              onClick={async () => {
                setPromoBusy(true); setPromoError('');
                try {
                  const res = await customerPortalService.validatePromo({
                    code: promoCode.trim(),
                    lines: cart.map((l) => ({ productId: l.product.id, qty: l.qty })),
                  });
                  const amount = Number(res.data?.discountAmount || 0);
                  setPromoDiscount(amount);
                  setAppliedPromo(res.data?.promoCode || promoCode.trim());
                } catch (e: any) {
                  setPromoDiscount(0);
                  setAppliedPromo(undefined);
                  setPromoError(e?.response?.data?.message || e?.message || 'Promo မရပါ');
                } finally { setPromoBusy(false); }
              }}
            >သုံးမည်</button>
          </div>
          {promoError && <p className="text-xs text-rose-600">{promoError}</p>}
          {promoDiscount > 0 && <p className="text-xs text-emerald-700">လျှော့ {money(promoDiscount)}</p>}
          <Totals itemsTotal={itemsTotal} />
          <Nav caption={money(itemsTotal)} next="ပို့ဆောင်ပုံ ရွေးမည်" onNext={goNext} />
        </>
      )}

      {step === 'FULFILLMENT' && (
        <>
          <div className="space-y-2 rounded-xl border bg-white p-3">
            <p className="text-sm font-bold">ဆိုင်လာယူ / ပို့ဆောင်</p>
            <div className={`grid gap-2 ${deliveryEnabled ? 'grid-cols-2' : 'grid-cols-1'}`}>
              <Choice selected={value.orderType === 'PICKUP'} onClick={() => onChange({ ...value, orderType: 'PICKUP', paymentChoice: 'TRANSFER' })}>ဆိုင်လာယူ</Choice>
              {deliveryEnabled && (
                <Choice selected={value.orderType === 'DELIVERY'} onClick={() => onChange({ ...value, orderType: 'DELIVERY' })}>သွားပို့</Choice>
              )}
            </div>
            {!deliveryEnabled && (
              <p className="rounded-lg bg-amber-50 p-2 text-xs text-amber-900">သွားပို့ ယာယီပိတ်ထားသည်။ ဆိုင်မှာလာယူ + စရံကြိုလွှဲ သာ ရနိုင်သည်။</p>
            )}
            {value.orderType === 'DELIVERY' && (
              <div className="space-y-2">
                <select aria-label="တိုင်း/ပြည်နယ်" className="w-full rounded-xl border p-2" value={value.regionId || ''} onChange={e => onChange({ ...value, regionId: Number(e.target.value) || undefined, townshipId: undefined, wardId: undefined })}>
                  <option value="">တိုင်း / ပြည်နယ် ရွေးပါ</option>
                  {locations.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
                <select aria-label="မြို့နယ်" className="w-full rounded-xl border p-2" value={value.townshipId || ''} onChange={e => onChange({ ...value, townshipId: Number(e.target.value) || undefined, wardId: undefined })}>
                  <option value="">မြို့နယ် ရွေးပါ</option>
                  {(region?.townships || []).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
                <select aria-label="ရပ်ကွက်" className="w-full rounded-xl border p-2" value={value.wardId || ''} onChange={e => onChange({ ...value, wardId: Number(e.target.value) || undefined })}>
                  <option value="">ရပ်ကွက် ရွေးပါ</option>
                  {(township?.wards || []).map(w => (
                    <option key={w.id} value={w.id}>
                      {w.name}{w.deliveryCharge != null ? ` · ${money(w.deliveryCharge)}` : ''}
                    </option>
                  ))}
                </select>
                <div className="grid grid-cols-2 gap-2">
                  <Choice selected={value.deliveryLocationMode === 'PROFILE'} onClick={() => onChange({ ...value, deliveryLocationMode: 'PROFILE' })}>Profile လိပ်စာ</Choice>
                  <Choice selected={value.deliveryLocationMode === 'OTHER'} onClick={() => onChange({ ...value, deliveryLocationMode: 'OTHER' })}>အခြားနေရာ</Choice>
                </div>
                {value.deliveryLocationMode === 'PROFILE' ? (
                  <p className="text-xs text-slate-600">
                    {session?.address || 'Profile လိပ်စာ မရှိသေးပါ — အကောင့်တွင် ဖြည့်ပါ'}
                    {session?.phone ? ` · ${session.phone}` : ''}
                  </p>
                ) : (
                  <>
                    {(township || ward) && (
                      <p className="rounded-lg bg-indigo-50 px-3 py-2 text-xs text-indigo-900">
                        ပို့မည့်ဒေသ · {[township?.name, ward?.name].filter(Boolean).join(' · ')} — အောက်တွင် အိမ်နံပါတ်နဲ့ လမ်းအမည်သာ ဖြည့်ပါ။
                      </p>
                    )}
                    <input value={value.deliveryPhone || ''} onChange={e => onChange({ ...value, deliveryPhone: e.target.value })} placeholder="လက်ခံမည့်သူ ဖုန်း" className="w-full rounded-xl border px-3 py-2 text-sm" />
                    <textarea
                      value={value.deliveryAddress || ''}
                      onChange={e => onChange({ ...value, deliveryAddress: e.target.value })}
                      placeholder="ဥပမာ — အမှတ် ၁၂၊ ဗိုလ်ချုပ်လမ်း၊ ၃ လွှာ"
                      aria-label="အသေးစိတ်လိပ်စာ (အိမ်နံပါတ်၊ လမ်းအမည်)"
                      rows={2}
                      className="w-full rounded-xl border px-3 py-2 text-sm"
                    />
                  </>
                )}
                <p className="text-sm">
                  {quoteLoading ? 'ပို့ခတွက်နေသည်…'
                    : quote?.deliveryCharge != null ? `ပို့ခ ${money(quote.deliveryCharge)}`
                    : township ? 'ပို့ခ စောင့်နေသည်' : 'မြို့နယ် ရွေးပါ'}
                </p>
                {quote?.weightKg != null && <p className="text-xs text-slate-500">{quote.weightKg} kg{quote.baseCharge != null ? ` · အခြေခံ ${money(quote.baseCharge)}` : ''}{quote.extraCharge != null ? ` + အလေးချိန်ကျော်ခ ${money(quote.extraCharge)}` : ''}</p>}
                {quoteError && <p role="alert" className="text-xs text-rose-600">{quoteError}</p>}
                <p className="rounded-lg bg-slate-50 p-2 text-xs text-slate-600">
                  ပြထားသော ပို့ခသည် <b>ဆိုင်ကပို့ရင်</b> ကောက်မည့် ခန့်မှန်းဈေး ဖြစ်သည်။ ဆိုင်က အပြင်ပို့ (Royal / Grab စသည်) အပ်ရန် ဆုံးဖြတ်ရင် ဆိုင်ဘောင်ချာတွင် ပို့ခ မထည့်ပါ — အပြင်ပို့ခကို သူတို့နှင့် သီးခြားရှင်းပါမည်။
                </p>
              </div>
            )}
          </div>
          <Nav
            caption={value.orderType === 'DELIVERY' ? (quoteLoading ? 'တွက်နေသည်' : quote?.deliveryCharge != null ? money(quotedFee) : 'မြို့နယ် ရွေးပါ') : 'ပို့ခ မလိုပါ'}
            back
            onBack={goBack}
            next={value.orderType === 'DELIVERY' ? 'ပို့ချိန် ရွေးမည်' : 'ငွေပေးချေနည်း ရွေးမည်'}
            onNext={goNext}
            enabled={fulfillmentReady && quoteReady}
          />
        </>
      )}

      {step === 'SCHEDULE' && (
        <>
          <div className="space-y-2 rounded-xl border bg-white p-3">
            <p className="text-sm font-bold">ပို့မည့် ရက်နှင့် အချိန်</p>
            <p className="text-xs text-slate-600">ဤအချိန်ကို ဆိုင်က အတည်ပြုပြီးမှ ငွေပေးချေနည်း ရွေးပါမည်။ ဆိုင်က ကိုယ်တိုင်ပို့ သို့မဟုတ် အပြင်ပို့ အပ်မည်ကိုလည်း အတည်ပြုပါမည်။</p>
            <input
              aria-label="ပို့မည့်အချိန်"
              type="datetime-local"
              min={earliestOpeningInput(deliveryHours)}
              step={1800}
              className="w-full rounded-xl border p-2"
              value={toLocalInput(value.requestedDeliveryAt)}
              onChange={e => onChange({ ...value, requestedDeliveryAt: toApiDateTime(e.target.value) })}
            />
            <p className={`text-xs ${scheduleError ? 'text-rose-600' : 'text-slate-500'}`}>{scheduleError || scheduleHint(deliveryHours)}</p>
          </div>
          <Nav
            caption={value.requestedDeliveryAt ? new Date(value.requestedDeliveryAt).toLocaleString() : 'ရက်ချိန်း ရွေးပါ'}
            back
            onBack={goBack}
            next="အော်ဒါ အတည်ပြုမည်"
            onNext={goNext}
            enabled={scheduleReady}
          />
        </>
      )}

      {step === 'CONFIRM' && (
        <>
          <div className="space-y-2 rounded-xl border bg-white p-3 text-sm">
            <Row label="ပစ္စည်းဖိုး" value={money(itemsTotal)} />
            {value.orderType === 'DELIVERY' && (
              <Row label="ပို့ဆောင်ခ (ခန့်မှန်း)" value={quote?.deliveryCharge != null ? money(quotedFee) : 'မြို့နယ် ရွေးပါ'} />
            )}
            <Row label="စုစုပေါင်း" value={money(grandTotal)} strong />
            {value.orderType === 'DELIVERY' && value.requestedDeliveryAt && (
              <Row label="တောင်းဆို ပို့ချိန်" value={new Date(value.requestedDeliveryAt).toLocaleString()} />
            )}
            {value.orderType === 'DELIVERY' && (
              <p className="text-xs text-slate-500">အော်ဒါတင်ပြီး ဆိုင်က ပို့ချိန်နှင့် ကိုယ်တိုင်ပို့ / အပြင်ပို့ အပ်မည်ကို အတည်ပြုပါမည်။ ပြီးမှ ငွေပေးချေနည်း ရွေးပါ။ စရံသည် ကြိုလွှဲဖြစ်သည်။</p>
            )}
            {value.orderType === 'PICKUP' && needsDeposit && (
              <div className="text-xs text-slate-600">
                <div className="flex justify-between"><span>စရံ ကြိုလွှဲ</span><span>{money(orderDeposit)}</span></div>
                <div className="flex justify-between"><span>ကျန် (ဆိုင်တွင်)</span><span>{money(remainingAfterDeposit)}</span></div>
                <p className="mt-2 rounded-lg bg-amber-50 p-2 text-amber-900">စရံလွှဲပြီးမှ အော်ဒါ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။</p>
              </div>
            )}
            <p className="text-xs text-slate-500">
              {value.orderType === 'DELIVERY'
                ? `${ward?.name || ''} · ${value.deliveryLocationMode === 'PROFILE' ? (session?.address || 'Profile') : value.deliveryAddress}`
                : 'ဆိုင်မှာ လာယူမည်'}
            </p>
            <textarea value={orderNote} onChange={e => onNoteChange(e.target.value)} placeholder="မှတ်ချက် (optional)" rows={2} className="w-full rounded-xl border bg-white px-3 py-2.5 text-sm" />
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={goBack} className="rounded-xl border px-4 py-3 text-sm font-semibold">နောက်သို့</button>
            <button
              type="button"
              disabled={placing || !fulfillmentReady || !quoteReady || !scheduleReady}
              onClick={() => onPlace(appliedPromo)}
              className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-indigo-600 py-3 text-sm font-bold text-white disabled:opacity-60"
            >
              {placing ? <Loader2 className="h-5 w-5 animate-spin" /> : 'အော်ဒါတင်မည်'}
            </button>
          </div>
        </>
      )}

      {step === 'PAYMENT' && (
        <>
          <div className="space-y-2 rounded-xl border bg-white p-3">
            <p className="text-sm font-bold">ငွေပေးချေမှု</p>
            <p className="text-sm text-slate-600">ဆိုင်လာယူ — စရံ {depositPct}% ကို ငွေလွှဲရပါမည်။ ကျန်ငွေကို ဆိုင်တွင် ပေးပါ။</p>
            {needsDeposit && (
              <div className="text-xs text-slate-600">
                <div className="flex justify-between"><span>စရံ ကြိုလွှဲ</span><span>{money(orderDeposit)}</span></div>
                <div className="flex justify-between"><span>ကျန် (လက်ခံချိန်)</span><span>{money(remainingAfterDeposit)}</span></div>
                <p className="mt-2 rounded-lg bg-amber-50 p-2 text-amber-900">စရံလွှဲပြီးမှ အော်ဒါ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။</p>
              </div>
            )}
          </div>
          <Nav caption={`စရံ ${depositPct}%`} back onBack={goBack} next="အော်ဒါ အတည်ပြုမည်" onNext={goNext} enabled />
        </>
      )}
    </div>
  );
}

function Choice({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick} className={`rounded-xl border px-3 py-2 text-sm font-semibold ${selected ? 'border-indigo-500 bg-indigo-50 text-indigo-800' : 'bg-white'}`}>
      {children}
    </button>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between gap-2 ${strong ? 'font-black text-indigo-700' : ''}`}>
      <span>{label}</span><span>{value}</span>
    </div>
  );
}

function Totals({ itemsTotal }: { itemsTotal: number }) {
  return (
    <div className="flex justify-between font-black">
      <span>ပစ္စည်းဖိုး</span>
      <span className="text-indigo-600">{money(itemsTotal)}</span>
    </div>
  );
}

function Nav({
  caption, next, onNext, enabled = true, back, onBack,
}: {
  caption: string; next: string; onNext: () => void; enabled?: boolean; back?: boolean; onBack?: () => void;
}) {
  return (
    <div className="flex items-center gap-2">
      {back && <button type="button" onClick={onBack} className="rounded-xl border px-4 py-3 text-sm font-semibold">နောက်သို့</button>}
      <div className="flex-1 text-sm font-bold text-indigo-700">{caption}</div>
      <button type="button" disabled={!enabled} onClick={onNext} className="rounded-xl bg-indigo-600 px-4 py-3 text-sm font-bold text-white disabled:opacity-40">{next}</button>
    </div>
  );
}
