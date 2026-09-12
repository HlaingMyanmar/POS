import ShippingQuoteEditor from './ShippingQuoteEditor';
import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../services/api';
import { staffService } from '../services/staffapiservice';
import { StaffDTO } from '../types';
import { getFromSession } from '../utils/storageHelper';

export type PaymentOrder = {
  id: number; orderNo: string; status: string; total?: number;
  paymentState?: string; paymentChoice?: string; paymentMethodId?: number;
  paymentMethodName?: string | null;
  payeeName?: string | null;
  payeeAccountNo?: string | null;
  paymentInstructions?: string; reservationExpiresAtEpochMillis?: number;
  paymentReviewNote?: string; paymentVerifiedBy?: string; latestProofId?: number;
  completedSaleId?: number; lines?: { productId: number; productName: string; qty: number; hasSerial?: boolean | null }[];
  orderType?: string | null;
  deliveryCharge?: number | null;
  quotedDeliveryCharge?: number | null;
  shippingState?: string;
  shippingVersion?: number;
  shippingWeightKg?: number;
  shippingReason?: string;
  deliveryHandler?: string | null;
  deliveryStatus?: string | null;
  requestedDeliveryAt?: string | null;
  deliveryScheduledAt?: string | null;
  itemsTotal?: number | null;
  depositPercent?: number | null;
  depositAmount?: number | null;
  remainingAmount?: number | null;
  collectionPaymentMethodId?: number | null;
  collectionPaymentMethodName?: string | null;
  collectionAmount?: number | null;
  collectionProofId?: number | null;
  collectionReference?: string | null;
  settlementAction?: string | null;
  settlementAmount?: number | null;
  settlementKeptAmount?: number | null;
  settlementPaymentMethodName?: string | null;
  settlementReference?: string | null;
  settlementRecordedBy?: string | null;
};

type PayMethod = {
  id: number;
  methodName: string;
  payeeName?: string | null;
  payeeAccountNo?: string | null;
  payeeHint?: string | null;
};

const ks = (n: number) => `${Number(n || 0).toLocaleString()} Ks`;

export const PAYMENT_STATE_LABELS: Record<string, string> = {
  NONE: 'မအတည်ပြုရသေး',
  AWAITING_PAYMENT: 'ငွေလွှဲရန် စောင့်ဆိုင်းဆဲ',
  AWAITING_COLLECTION: 'လက်ခံချိန် ငွေရှင်းရန်',
  PROOF_SUBMITTED: 'ငွေလွှဲအချက်အလက် ရောက်ပြီး — စစ်ဆေးရန်',
  REMAINDER_PROOF_SUBMITTED: 'ကျန်ငွေလွှဲအထောက်အထား ရောက်ပြီး',
  REMAINDER_CHECKING: 'ကျန်ငွေလွှဲ စစ်ဆေးနေသည်',
  CHECKING: 'စစ်ဆေးနေသည်',
  REVIEW: 'ငွေလွှဲ စစ်ဆေးဆဲ',
  LATE_REVIEW: 'နောက်ကျငွေလွှဲ စစ်ဆေးရန်',
  EXPIRED: 'သတ်မှတ်ချိန်ကုန်ပြီ',
  REJECTED: 'အထောက်အထား ပယ်ချထား',
  DEPOSIT_PAID: 'စရံရရှိပြီး',
  PAID: 'ငွေအပြည့် ရရှိပြီး',
  FULFILLED: 'ဘောင်ချာထုတ်ပြီး',
  REFUND_REQUIRED: 'ငွေပြန်အမ်းရန်',
  REFUNDED: 'ငွေပြန်အမ်းပြီး',
  FORFEITED: 'စရံသိမ်းပြီး ပယ်ဖျက်',
};

export function paymentStateLabel(state?: string | null) {
  const key = (state || 'NONE').toUpperCase();
  return PAYMENT_STATE_LABELS[key] || key;
}

function paymentStep(order: PaymentOrder, state: string) {
  const ship = (order.shippingState || 'LEGACY').toUpperCase();
  const delivery = (order.deliveryStatus || 'PENDING').toUpperCase();
  const isDelivery = order.orderType === 'DELIVERY';
  const needsQuote = isDelivery && !['LEGACY', 'ACCEPTED'].includes(ship);
  if (order.completedSaleId || state === 'FULFILLED') {
    return { title: 'ပြီးပါပြီ', hint: 'ဘောင်ချာထုတ်ပြီးပါပြီ။', tone: 'done' as const };
  }
  if (needsQuote && ['AWAITING_SHOP', 'NEEDS_QUOTE'].includes(ship)) {
    return { title: 'ယခုလုပ်ရန် · ပို့ချိန် ပို့ပါ', hint: 'Customer တောင်းဆိုချိန် အဆင်ပြေရင် အဆိုပြုချက် ပို့ပါ။ မပြေရင် အချိန်ပြောင်းပြီး ပို့ပါ။', tone: 'now' as const };
  }
  if (ship === 'QUOTED') {
    return { title: 'စောင့်ဆိုင်း · Customer လက်ခံရန်', hint: 'အဆိုပြုချက် ပို့ပြီးပါပြီ။ Customer app မှ လက်ခံမှ ငွေလွှဲစနိုင်သည်။', tone: 'wait' as const };
  }
  if (order.paymentChoice === 'PENDING') {
    return { title: 'စောင့်ဆိုင်း · ငွေပေးချေနည်း', hint: 'Customer က အပြည့်လွှဲ သို့မဟုတ် စရံကြို (လက်ခံချိန်ရှင်း) ရွေးပါမည်။', tone: 'wait' as const };
  }
  if (['NONE', 'EXPIRED', 'REJECTED'].includes(state)) {
    return { title: 'ယခုလုပ်ရန် · Order လက်ခံပါ', hint: 'Stock ဖယ်ထားပြီးမှ Customer ငွေလွှဲနိုင်သည်။', tone: 'now' as const };
  }
  if (state === 'AWAITING_PAYMENT') {
    return { title: 'စောင့်ဆိုင်း · စရံ / ငွေလွှဲ', hint: 'Customer က app မှ Channel ရွေးပြီး ငွေလွှဲအထောက်အထား တင်ပါမည်။', tone: 'wait' as const };
  }
  if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(state)) {
    return { title: 'ယခုလုပ်ရန် · ငွေဝင်မှု အတည်ပြုပါ', hint: 'ပြေစာပုံနှင့် ပမာဏ တိုက်စစ်ပြီး အတည်ပြုပါ။', tone: 'now' as const };
  }
  if (state === 'DEPOSIT_PAID') {
    const atDoor = ['HANDED_TO_RIDER', 'OUT_FOR_DELIVERY', 'IN_TRANSIT', 'DELIVERED'].includes(delivery);
    if (isDelivery && !atDoor) {
      return { title: 'ယခုလုပ်ရန် · ပစ္စည်း ပို့ပါ', hint: 'စရံရပြီးပါပြီ။ ထုပ်ပိုး / Rider အပ်ပြီး ပို့ပါ။ ပစ္စည်းရောက်မှ ကျန်ငွေ လက်ခံမှတ်ပါ။', tone: 'now' as const };
    }
    return { title: 'ယခုလုပ်ရန် · ကျန်ငွေ လက်ခံပါ', hint: isDelivery ? 'ပစ္စည်းရောက်ပါပြီ။ ကျန်ငွေ Channel ရွေးပြီး မှတ်ပါ။' : 'ဆိုင်မှာ ကျန်ငွေ လက်ခံမှတ်ပါ။', tone: 'now' as const };
  }
  if (state === 'PAID') {
    return { title: 'ယခုလုပ်ရန် · ဘောင်ချာထုတ်ပါ', hint: 'ငွေအပြည့်ရပါပြီ။ Staff ရွေးပြီး အရောင်း အပြီးသတ်ပါ။', tone: 'now' as const };
  }
  if (state === 'AWAITING_COLLECTION') {
    return { title: 'လက်ခံချိန် ငွေရှင်း', hint: 'ပစ္စည်းပေးချိန် ငွေကောက်ပါ။', tone: 'now' as const };
  }
  return { title: paymentStateLabel(state), hint: '', tone: 'wait' as const };
}

export default function CustomerOrderPaymentPanel({
  order, onClose, onUpdated,
}: {
  order: PaymentOrder;
  onClose: () => void;
  onUpdated: (order: any) => void;
}) {
  const [methods, setMethods] = useState<PayMethod[]>([]);
  const [method, setMethod] = useState(String(order.paymentMethodId || ''));
  const [minutes, setMinutes] = useState(
    order.paymentChoice === 'PAY_ON_COLLECTION' && !(Number(order.depositAmount) > 0) ? '1440' : '15'
  );
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [refundRef, setRefundRef] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [staffId, setStaffId] = useState<number | null>(null);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [serials, setSerials] = useState<Record<number, string>>({});
  const [proofs, setProofs] = useState<{ deposit?: any; remainder?: any }>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const quoted = Number(order.quotedDeliveryCharge ?? order.deliveryCharge ?? 0);
  const [handler, setHandler] = useState(order.deliveryHandler || (order.orderType === 'DELIVERY' ? '' : 'OWN'));
  useEffect(() => {
    setHandler(order.deliveryHandler || 'OWN');
  }, [order.deliveryHandler, order.shippingVersion]);
  const state = order.paymentState || 'NONE';
  const billedDelivery = handler === 'HANDOFF' ? 0 : quoted;
  const billedTotal = Number(order.itemsTotal ?? (Number(order.total || 0) - Number(order.deliveryCharge || 0))) + billedDelivery;
  const depositAmount = Number(order.depositAmount ?? 0);
  const hasDeposit = depositAmount > 0;
  const expectedTransfer = hasDeposit ? depositAmount : billedTotal;
  const remainingAtShop = hasDeposit
    ? Number(order.remainingAmount ?? Math.max(0, billedTotal - depositAmount))
    : 0;
  const remainderAtDoor = order.orderType === 'DELIVERY'
    && ['HANDED_TO_RIDER', 'OUT_FOR_DELIVERY', 'IN_TRANSIT', 'DELIVERED'].includes((order.deliveryStatus || '').toUpperCase());
  const canCollectRemainder = hasDeposit && state === 'DEPOSIT_PAID' && remainingAtShop > 0
    && (order.orderType !== 'DELIVERY' || remainderAtDoor);
  const collectionOnly = order.paymentChoice === 'PAY_ON_COLLECTION' && !hasDeposit;
  const receivedConfirmed = Number(order.collectionAmount || 0) > 0
    ? expectedTransfer + Number(order.collectionAmount)
    : state === 'DEPOSIT_PAID' || (hasDeposit && state === 'REFUND_REQUIRED')
      ? expectedTransfer
      : state === 'PAID' ? billedTotal
        : Number(proofs.remainder?.amount || proofs.deposit?.amount || 0) || (hasDeposit ? expectedTransfer : billedTotal);
  const canSettle = ['DEPOSIT_PAID', 'PAID', 'REFUND_REQUIRED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(state)
    && !order.completedSaleId;
  useEffect(() => {
    if (state === 'DEPOSIT_PAID' && remainingAtShop > 0) {
      setAmount(String(remainingAtShop));
    }
  }, [state, remainingAtShop]);

  useEffect(() => {
    let active = true;
    api.get<any>('/v1/payment-methods/active').then((r) => {
      if (active) setMethods(r.data || []);
    }).catch(() => {
      if (active) setError('Payment methods ဖတ်မရပါ။');
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    staffService.getAllActive().then((rows) => {
      if (!active) return;
      const list = (rows || []).filter((s) => s.active !== false);
      setStaffs(list);
      try {
        const user = JSON.parse(getFromSession('sspd_user') || '{}') as { staffId?: number };
        const linked = list.find((s) => s.id === Number(user.staffId));
        if (linked) {
          setStaffId((prev) => prev ?? linked.id);
        }
      } catch {
        /* ignore */
      }
    }).catch(() => {
      if (active) setError((prev) => prev || 'Staff စာရင်း ဖတ်မရပါ။');
    });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setProofs({});
    if (order.latestProofId || order.collectionProofId) {
      api.get<any>('/v1/customer-orders/' + order.id + '/payment-proofs').then((r) => {
        if (active) setProofs(r.data || {});
      }).catch(() => {
        if (active) setError('အထောက်အထား ဖတ်မရပါ။');
      });
    }
    return () => { active = false; };
  }, [order.id, order.latestProofId, order.collectionProofId, state]);

  const run = async (path: string, body: any) => {
    setBusy(true);
    setError('');
    try {
      const r = await api.post<any>('/v1/customer-orders/' + order.id + '/' + path, body);
      onUpdated(r.data);
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || 'မအောင်မြင်ပါ');
    } finally {
      setBusy(false);
    }
  };

  const review = (action: string) => run('payment-review', { action, note, amount: Number(amount) });
  const step = paymentStep(order, state);
  const stepTone = step.tone === 'now' ? 'border-amber-300 bg-amber-50 text-amber-950'
    : step.tone === 'done' ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
    : 'border-sky-200 bg-sky-50 text-sky-950';
  const collectedRemainder = Number(order.collectionAmount ?? 0) > 0;

  const confirmNow = async () => {
    setBusy(true);
    setError('');
    try {
      const pay = Number(amount) > 0 ? Number(amount) : expectedTransfer;
      await api.post('/v1/customer-orders/' + order.id + '/payment-review', { action: 'START_CHECK' });
      const approved = await api.post<any>('/v1/customer-orders/' + order.id + '/payment-review', {
        action: 'APPROVE',
        amount: pay,
        note: note || undefined,
      });
      const remaining = Number(order.remainingAmount ?? Math.max(0, billedTotal - depositAmount));
      const needsSerial = (order.lines || []).some((l) => l.hasSerial);
      const sid = staffId ?? staffs[0]?.id ?? null;
      if (hasDeposit && remaining > 0) {
        onUpdated(approved.data);
      } else if (!needsSerial && sid) {
        const done = await api.post<any>('/v1/customer-orders/' + order.id + '/fulfill', {
          staffId: sid,
          serialNumbers: {},
        });
        onUpdated(done.data);
      } else {
        onUpdated(approved.data);
      }
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || 'မအောင်မြင်ပါ');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] bg-slate-950/50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label="Order payment">
      <section className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-auto">
        <div className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b bg-white px-5 py-4">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-wide text-slate-400">
              {order.orderType === 'DELIVERY' ? 'သွားပို့ အော်ဒါ' : 'ဆိုင်လာယူ'} · ငွေပေးချေမှု
            </p>
            <h2 className="text-xl font-black text-slate-900">{order.orderNo}</h2>
          </div>
          <button disabled={busy} onClick={onClose} className="rounded-lg px-3 py-2 text-slate-500 hover:bg-slate-100" aria-label="Close">✕</button>
        </div>

        <div className="space-y-4 p-5">
          <div className={`rounded-2xl border p-4 ${stepTone}`}>
            <p className="text-base font-black">{step.title}</p>
            {step.hint ? <p className="mt-1 text-sm leading-relaxed opacity-90">{step.hint}</p> : null}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm">
            <div className="flex justify-between py-1"><span className="text-slate-500">ပစ္စည်း</span><b>{ks(Number(order.itemsTotal ?? 0))}</b></div>
            {order.orderType === 'DELIVERY' && (
              <div className="flex justify-between py-1">
                <span className="text-slate-500">ဆိုင်ပို့ခ</span>
                <b>{handler === 'HANDOFF' ? '၀ (အပြင်ပို့)' : ks(billedDelivery)}</b>
              </div>
            )}
            <div className="flex justify-between border-t py-1.5"><span className="text-slate-500">စုစုပေါင်း</span><b className="text-indigo-700">{ks(billedTotal)}</b></div>
            {hasDeposit && (
              <>
                <div className="flex justify-between py-1 text-emerald-800">
                  <span>စရံ {Number(order.depositPercent ?? 0)}%</span>
                  <b>{ks(depositAmount)}{['DEPOSIT_PAID', 'PAID', 'FULFILLED'].includes(state) ? ' ✓' : ''}</b>
                </div>
                <div className="flex justify-between py-1 text-amber-800">
                  <span>ကျန်ငွေ</span>
                  <b>{ks(remainingAtShop)}{collectedRemainder || ['PAID', 'FULFILLED'].includes(state) ? ' ✓' : ' · ပို့ရောက်ချိန်'}</b>
                </div>
              </>
            )}
            {!hasDeposit && (
              <div className="flex justify-between py-1">
                <span className="text-slate-500">ယခုကောက်ရန်</span>
                <b>{ks(expectedTransfer)}</b>
              </div>
            )}
          </div>

          {error && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-semibold text-rose-800">{error}</p>}
          {order.status === 'CANCELLED' && order.paymentState === 'EXPIRED' && (
            <p className="rounded-xl bg-rose-50 p-3 text-sm font-semibold text-rose-800">
              Hold ကုန်၍ stock ပြန်လွှတ်ပြီးပါပြီ။ ငွေလွှဲပြီးသားဆို အထောက်အထားတင်နိုင်သေးသည်။
            </p>
          )}
          {order.reservationExpiresAtEpochMillis && state === 'AWAITING_PAYMENT' && (
            <p className="text-xs text-slate-500">ဖယ်ထားချိန် ကုန်မည့်အချိန် · {new Date(order.reservationExpiresAtEpochMillis).toLocaleString()}</p>
          )}
          {order.paymentReviewNote && <p className="rounded-xl bg-slate-50 p-3 text-sm text-slate-700">{order.paymentReviewNote}</p>}

          <ShippingQuoteEditor order={order} onUpdated={onUpdated} />
          {order.paymentChoice === 'PENDING' && order.shippingState === 'ACCEPTED' && (
            <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Customer လက်ခံပြီး — ငွေပေးချေနည်း ရွေးရန် စောင့်ဆိုင်းပါ။</p>
          )}

          <fieldset disabled={busy || (!!order.shippingState && !['LEGACY','ACCEPTED'].includes(order.shippingState))} className="space-y-3 disabled:opacity-60">
          {order.status !== 'CANCELLED' && ['NONE', 'EXPIRED', 'REJECTED'].includes(state)
            && (order.orderType !== 'DELIVERY' || order.shippingState === 'ACCEPTED')
            && order.paymentChoice !== 'PENDING' && (
            <>
              {collectionOnly ? (
                <>
                  <p className="text-sm text-slate-600">လက်ခံချိန် ငွေရှင်း — Customer က Channel မရွေးပါ။ ဆိုင်က Ledger Channel (optional) ရွေးနိုင်သည်။</p>
                  <label className="block text-sm">
                    Ledger Channel (optional)
                    <select value={method} onChange={(e) => setMethod(e.target.value)} className="w-full border rounded-lg p-2">
                      <option value="">နောက်မှ သတ်မှတ်မည်</option>
                      {methods.map((m) => (
                        <option key={m.id} value={m.id}>{m.methodName}</option>
                      ))}
                    </select>
                  </label>
                </>
              ) : (
                <p className="text-sm text-emerald-800 bg-emerald-50 rounded-xl p-3">
                  Customer က app ထဲမှာ KBZPay / WavePay စသည့် Channel ကိုယ်တိုင် ရွေးပြီး ငွေလွှဲ + screenshot တင်မည်။ ဆိုင်က Channel မရွေးရပါ။
                </p>
              )}
              {order.orderType === 'DELIVERY' && (
                <div className="rounded-xl border p-3 space-y-2">
                  <p className="text-sm font-bold text-slate-800">ပို့ဆောင်သူ — ငွေမကောက်ခင် ဆုံးဖြတ်ပါ</p>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      disabled={order.shippingState === 'ACCEPTED'} onClick={() => setHandler('OWN')}
                      className={`rounded-lg border p-3 text-sm font-bold ${handler === 'OWN' ? 'border-indigo-500 bg-indigo-50 text-indigo-800' : 'text-slate-600'}`}
                    >
                      ဆိုင်ကပို့<br />
                      <span className="text-xs font-semibold">ပို့ခ {quoted.toLocaleString()} Ks</span>
                    </button>
                    <button
                      type="button"
                      disabled={order.shippingState === 'ACCEPTED'} onClick={() => setHandler('HANDOFF')}
                      className={`rounded-lg border p-3 text-sm font-bold ${handler === 'HANDOFF' ? 'border-indigo-500 bg-indigo-50 text-indigo-800' : 'text-slate-600'}`}
                    >
                      အပြင်ပို့ အပ်<br />
                      <span className="text-xs font-semibold">ဆိုင်ပို့ခ ၀</span>
                    </button>
                  </div>
                </div>
              )}
              <label className="block text-sm">
                ပစ္စည်းဖယ်ထားမည့် မိနစ်
                <input type="number" min="1" max={collectionOnly ? 1440 : 60} value={minutes} onChange={(e) => setMinutes(e.target.value)} className="w-full border rounded-lg p-2" />
              </label>
              <button
                disabled={order.orderType === 'DELIVERY' && !handler}
                onClick={() => void run('reserve', {
                  paymentMethodId: collectionOnly && method ? Number(method) : null,
                  holdMinutes: Number(minutes),
                  deliveryHandler: order.orderType === 'DELIVERY' ? handler : null,
                })}
                className="w-full rounded-xl bg-indigo-600 text-white p-3 font-bold disabled:opacity-40"
              >
                Stock ဖယ်ထားပြီး Order လက်ခံမည်
              </button>
            </>
          )}
          {order.paymentMethodName && (
            <p className="text-sm text-slate-600">Customer Channel: <b>{order.paymentMethodName}</b>
              {(order.payeeName || order.payeeAccountNo) ? ` · ${[order.payeeName, order.payeeAccountNo].filter(Boolean).join(' · ')}` : ''}
            </p>
          )}
          {(proofs.deposit?.image || proofs.remainder?.image) && (
            <div className="rounded-xl border p-3">
              <p className="mb-3 text-sm font-bold text-slate-800">ငွေလွှဲအထောက်အထားများ · စရံနှင့် ကျန်ငွေကို သီးခြားစစ်ပါ</p>
              <div className="grid gap-3 md:grid-cols-2">
                {([['deposit', 'ပထမပုံ · စရံလွှဲ', depositAmount], ['remainder', 'ဒုတိယပုံ · ကျန်ငွေလွှဲ', remainingAtShop]] as const).map(([key, label, due]) => {
                  const item = proofs[key];
                  return <div key={key} className="rounded-xl bg-slate-50 p-3">
                    <p className="mb-2 text-xs font-bold text-indigo-700">{label}</p>
                    {item?.image ? <>
                      <img src={item.image} alt={label} className="max-h-72 w-full rounded-lg bg-white object-contain" />
                      <div className="mt-2 space-y-1 text-xs"><p>သတ်မှတ်ငွေ <b>{ks(due)}</b></p><p>တင်ထားသည့်ငွေ <b>{ks(Number(item.amount || 0))}</b></p><p>Reference <b>{item.reference || '—'}</b></p></div>
                    </> : <p className="text-xs text-slate-500">မတင်ရသေးပါ</p>}
                  </div>;
                })}
              </div>
            </div>
          )}
          {state === 'PROOF_SUBMITTED' && (
            <button disabled={busy} onClick={() => void confirmNow()} className="w-full rounded-xl bg-emerald-600 text-white p-3 font-bold disabled:opacity-60">
              {busy ? 'အတည်ပြုနေသည်…' : 'ငွေဝင်မှု အတည်ပြုမည်'}
            </button>
          )}
          {state === 'REMAINDER_PROOF_SUBMITTED' && (
            <button disabled={busy} onClick={() => void run('payment-review', { action: 'START_CHECK' })} className="w-full rounded-xl bg-amber-600 text-white p-3 font-bold disabled:opacity-60">
              ကျန်ငွေလွှဲအထောက်အထား စတင်စစ်မည်
            </button>
          )}
          {state === 'REMAINDER_CHECKING' && (
            <div className="space-y-2">
              <button disabled={busy} onClick={() => void run('payment-review', { action: 'APPROVE', amount: remainingAtShop, note: note || undefined })} className="w-full rounded-xl bg-emerald-600 text-white p-3 font-bold">
                ကျန်ငွေဝင်မှု အတည်ပြုမည်
              </button>
              <button disabled={busy || !note.trim()} onClick={() => void run('payment-review', { action: 'REJECT', note })} className="w-full rounded-xl border border-rose-300 p-3 font-bold text-rose-700 disabled:opacity-40">
                အထောက်အထား ပယ်ချမည်
              </button>
            </div>
          )}
          {['CHECKING', 'REVIEW', 'LATE_REVIEW', 'AWAITING_COLLECTION'].includes(state) && (
            <>
              <label className="block text-sm">
                {hasDeposit ? 'လက်ခံရရှိ စရံငွေ (Ks)' : 'အမှန်တကယ် လက်ခံရရှိငွေ (Ks)'}
                <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full border rounded-lg p-2" />
              </label>
              <p className="text-xs text-slate-500">လွှဲရမည့်ပမာဏ {ks(expectedTransfer)}</p>
              <button disabled={!amount || Number(amount) !== Number(expectedTransfer)} onClick={() => void review('APPROVE')} className="w-full bg-emerald-600 text-white rounded-xl p-3 font-bold disabled:opacity-40">
                ငွေဝင်မှု အတည်ပြုမည်
              </button>
            </>
          )}
          {state === 'DEPOSIT_PAID' && remainingAtShop > 0 && (
            <div className="space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-4">
              <p className="font-black text-amber-950">ကျန် {ks(remainingAtShop)}</p>
              {order.orderType === 'DELIVERY' && !remainderAtDoor && (
                <p className="text-sm text-amber-900">အရင် အော်ဒါအသေးစိတ်မှ ပို့ဆောင်မှုကို Rider အပ် / ရောက်ပြီး သိမ်းပါ။ ပြီးမှ ဤနေရာတွင် ကျန်ငွေ မှတ်ပါ။</p>
              )}
              <label className="block text-sm">
                ကျန်ငွေ Channel
                <select value={method} onChange={(e) => setMethod(e.target.value)} disabled={!canCollectRemainder} className="w-full border rounded-lg p-2 bg-white disabled:opacity-50">
                  <option value="">ရွေးပါ</option>
                  {methods.filter((m) => m.methodName?.trim().toUpperCase() === 'CASH').map((m) => (
                    <option key={m.id} value={m.id}>{m.methodName}</option>
                  ))}
                </select>
              </label>
              <p className="text-xs text-amber-800">Online payment ဆို Customer ဘက်မှ အထောက်အထားတင်ပြီးနောက် ဤ panel တွင် စစ်ဆေးအတည်ပြုပါ။</p>
              <label className="block text-sm">
                လက်ခံရရှိ ကျန်ငွေ
                <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} disabled={!canCollectRemainder} className="w-full border rounded-lg p-2 bg-white disabled:opacity-50" />
              </label>
              <button
                disabled={!canCollectRemainder || !method || Number(amount) !== remainingAtShop}
                onClick={() => void run('payment-review', {
                  action: 'COLLECT_REMAINDER',
                  amount: remainingAtShop,
                  paymentMethodId: Number(method),
                  note: note || undefined,
                })}
                className="w-full rounded-xl bg-emerald-600 text-white p-3 font-bold disabled:opacity-40"
              >
                {canCollectRemainder ? 'Cash ကျန်ငွေ လက်ခံမှတ်မည်' : 'ပို့ပြီးမှ မှတ်မည်'}
              </button>
            </div>
          )}
          {state === 'PAID' && (
            <div className="space-y-3 rounded-2xl border p-4">
              <p className="font-black">အရောင်းဘောင်ချာထုတ်မည်</p>
              {hasDeposit && remainingAtShop > 0 && (
                <p className="text-xs rounded-lg bg-slate-50 p-2">
                  စရံ {ks(depositAmount)}
                  {order.paymentMethodName ? ` · ${order.paymentMethodName}` : ''}
                  {' '} / ကျန် {ks(remainingAtShop)}
                  {order.collectionPaymentMethodName ? ` · ${order.collectionPaymentMethodName}` : ''}
                </p>
              )}
              <StaffNameSearch staffs={staffs} value={staffId} onChange={setStaffId} />
              {(order.lines || []).filter((l) => l.hasSerial).map((l) => (
                <label key={l.productId} className="block text-sm">
                  {l.productName} — Serial {l.qty} ခု
                  <textarea value={serials[l.productId] || ''} onChange={(e) => setSerials({ ...serials, [l.productId]: e.target.value })} className="w-full border rounded-lg p-2" />
                </label>
              ))}
              <button
                onClick={() => void run('fulfill', {
                  staffId,
                  serialNumbers: Object.fromEntries(
                    Object.entries<string>(serials).map(([id, value]) => [id, value.split(/\r?\n/).map((v) => v.trim()).filter(Boolean)]),
                  ),
                })}
                className="w-full rounded-xl p-3 bg-indigo-600 text-white font-bold"
              >
                အရောင်း အပြီးသတ်မည်
              </button>
            </div>
          )}
          {order.completedSaleId && (
            <div className="space-y-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm">
              <p className="font-bold text-emerald-800">Sale #{order.completedSaleId} — ဘောင်ချာထုတ်ပြီး</p>
              {hasDeposit && <>
                <div className="flex justify-between"><span>စရံ · {order.paymentMethodName || 'Channel'}</span><b>{ks(depositAmount)} ✓</b></div>
                <div className="flex justify-between"><span>ကျန်ငွေ · {order.collectionPaymentMethodName || 'Channel'}</span><b>{ks(order.collectionAmount ?? remainingAtShop)} ✓</b></div>
                <div className="flex justify-between border-t border-emerald-200 pt-2"><span>ပေးပြီးစုစုပေါင်း</span><b>{ks(billedTotal)}</b></div>
                <div className="flex justify-between text-emerald-800"><span>ပေးရန်ကျန်</span><b>{ks(0)} ✓</b></div>
              </>}
            </div>
          )}

          {(['CHECKING', 'REVIEW', 'LATE_REVIEW', 'PAID', 'DEPOSIT_PAID', 'REFUND_REQUIRED'].includes(state) || (canSettle && receivedConfirmed > 0)) && (
            <details className="rounded-xl border border-slate-200 p-3 text-sm">
              <summary className="cursor-pointer font-bold text-slate-600">ပြန်အမ်း / စရံသိမ်း</summary>
              <div className="mt-3 space-y-3">
                <label className="block text-sm">
                  အကြောင်းရင်း
                  <textarea maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} className="w-full border rounded-lg p-2" />
                </label>
                <div className="flex flex-wrap gap-2">
                  {['CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(state) && (
                    <button disabled={!note.trim()} onClick={() => void review('REJECT')} className="border rounded-lg p-2 text-rose-700">အထောက်အထား ပယ်ချမည်</button>
                  )}
                  {state !== 'REFUND_REQUIRED' && canSettle && (
                    <button disabled={!note.trim()} onClick={() => void review('REFUND_REQUIRED')} className="border rounded-lg p-2 text-amber-700">ပြန်အမ်းရန် တန်းစီမည်</button>
                  )}
                </div>
                {canSettle && receivedConfirmed > 0 && (
                  <div className="space-y-2 rounded-xl bg-rose-50 p-3">
                    {hasDeposit && state === 'DEPOSIT_PAID' && (
                      <button disabled={!note.trim()} onClick={() => void review('FORFEIT_DEPOSIT')} className="w-full rounded-lg border bg-white p-3 text-rose-800 font-bold disabled:opacity-40">
                        စရံသိမ်းပြီး ပယ်ဖျက်မည်
                      </button>
                    )}
                    <select value={method} onChange={(e) => setMethod(e.target.value)} className="w-full border rounded-lg p-2 bg-white">
                      <option value="">ပြန်အမ်း Channel</option>
                      {methods.map((m) => (
                        <option key={m.id} value={m.id}>{m.methodName}</option>
                      ))}
                    </select>
                    <input type="number" min="0.01" step="0.01" max={receivedConfirmed} value={refundAmount} onChange={(e) => setRefundAmount(e.target.value)} placeholder="ပြန်အမ်းပမာဏ" className="w-full border rounded-lg p-2 bg-white" />
                    <input value={refundRef} onChange={(e) => setRefundRef(e.target.value)} maxLength={120} placeholder="Transaction reference" className="w-full border rounded-lg p-2 bg-white" />
                    <button
                      disabled={!note.trim() || !method || !refundRef.trim() || !(Number(refundAmount) > 0) || Number(refundAmount) > receivedConfirmed}
                      onClick={() => void run('payment-review', {
                        action: 'REFUNDED',
                        note,
                        amount: Number(refundAmount),
                        paymentMethodId: Number(method),
                        transactionNo: refundRef.trim(),
                      })}
                      className="w-full rounded-lg bg-indigo-700 text-white p-3 font-bold disabled:opacity-40"
                    >
                      ပြန်အမ်းမှတ်မည်
                    </button>
                  </div>
                )}
              </div>
            </details>
          )}
          {(state === 'REFUNDED' || state === 'FORFEITED') && (
            <p className="rounded-xl bg-slate-50 p-3 text-sm">
              {state === 'FORFEITED' ? 'စရံသိမ်း' : 'ပြန်အမ်း'}
              {order.settlementAmount != null ? ` · ပြန် ${Number(order.settlementAmount).toLocaleString()} Ks` : ''}
              {order.settlementKeptAmount != null ? ` · သိမ်း ${Number(order.settlementKeptAmount).toLocaleString()} Ks` : ''}
            </p>
          )}
          </fieldset>
        </div>
      </section>
    </div>
  );
}

function staffLabel(staff: StaffDTO) {
  return [staff.name, staff.role, staff.phone].filter(Boolean).join(' · ');
}

function StaffNameSearch({
  staffs,
  value,
  onChange,
}: {
  staffs: StaffDTO[];
  value: number | null;
  onChange: (id: number | null) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const selected = staffs.find((s) => s.id === value) ?? null;
  const shown = selected ? staffLabel(selected) : '';

  useEffect(() => {
    if (!open) setQuery(shown);
  }, [shown, open]);

  useLayoutEffect(() => {
    if (!open || !inputRef.current) {
      setMenuPos(null);
      return;
    }
    const place = () => {
      if (!inputRef.current) return;
      const rect = inputRef.current.getBoundingClientRect();
      const width = Math.min(Math.max(rect.width, 280), window.innerWidth - 16);
      const spaceBelow = window.innerHeight - rect.bottom - 8;
      const openUp = spaceBelow < 180 && rect.top > spaceBelow;
      const height = Math.min(240, openUp ? Math.max(120, rect.top - 8) : Math.max(120, spaceBelow));
      setMenuPos({
        top: openUp ? Math.max(8, rect.top - height - 4) : rect.bottom + 4,
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
        width,
      });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, query]);

  const needle = query.trim().toLowerCase();
  const filtered = useMemo(
    () =>
      staffs.filter((s) => {
        if (!needle) return true;
        return `${s.name} ${s.role || ''} ${s.phone || ''} ${s.id}`.toLowerCase().includes(needle);
      }).slice(0, 40),
    [staffs, needle]
  );

  return (
    <label className="block text-sm">
      Staff နာမည် ရွေးပါ (လိုအပ်ပါက)
      <div className="relative mt-1">
        <input
          ref={inputRef}
          value={open ? query : shown}
          onChange={(e) => {
            const next = e.target.value;
            setQuery(next);
            setOpen(true);
            if (!next.trim()) onChange(null);
          }}
          onFocus={() => {
            setQuery('');
            setOpen(true);
          }}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          placeholder="နာမည် ရိုက်ရှာပါ"
          className="w-full rounded-lg border p-2"
          autoComplete="off"
        />
        {value != null && (
          <button
            type="button"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
            aria-label="Staff ရှင်းမည်"
            onMouseDown={(e) => {
              e.preventDefault();
              onChange(null);
              setQuery('');
            }}
          >
            ✕
          </button>
        )}
      </div>
      {open && menuPos && createPortal(
        <div
          style={{ position: 'fixed', top: menuPos.top, left: menuPos.left, width: menuPos.width, zIndex: 200 }}
          className="max-h-60 overflow-auto rounded-lg border border-slate-200 bg-white shadow-xl"
        >
          {filtered.length > 0 ? (
            filtered.map((staff) => (
              <button
                key={staff.id}
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  onChange(staff.id);
                  setQuery(staffLabel(staff));
                  setOpen(false);
                }}
                className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${value === staff.id ? 'bg-indigo-50' : ''}`}
              >
                <p className="text-sm font-semibold text-slate-800">{staff.name}</p>
                <p className="text-[11px] text-slate-400">
                  {[staff.role, staff.phone].filter(Boolean).join(' · ') || 'Staff'}
                </p>
              </button>
            ))
          ) : (
            <p className="px-3 py-2.5 text-xs text-slate-400">နာမည် မတွေ့ပါ</p>
          )}
        </div>,
        document.body
      )}
    </label>
  );
}



