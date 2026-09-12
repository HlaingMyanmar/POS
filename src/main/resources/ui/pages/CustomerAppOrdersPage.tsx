import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  MapPin,
  Package,
  RefreshCw,
  Smartphone,
  Truck,
  X,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import CustomerOrderPaymentPanel, { PaymentOrder, paymentStateLabel } from '../components/CustomerOrderPaymentPanel';
import ShippingQuoteEditor from '../components/ShippingQuoteEditor';
import { CustomerProductReturnPanel, ProductReturn } from '../components/CustomerProductReturnPanel';
import { AppRoute } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { useCustomerOrderLiveSync } from '../hooks/useCustomerOrderLiveSync';
import Swal from 'sweetalert2';

type OrderLine = {
  productId: number;
  productName: string;
  productCode?: string | null;
  qty: number;
  unitPrice: number;
  subtotal: number;
  hasSerial?: boolean | null;
};

type Order = PaymentOrder & {
  id: number;
  orderNo: string;
  customerName?: string;
  customerPhone?: string;
  status: string;
  note?: string;
  total?: number;
  createdAt?: string;
  lines?: OrderLine[];
  orderLatitude?: number | null;
  orderLongitude?: number | null;
  orderLocationAccuracy?: number | null;
  orderLocationAt?: string | null;
  orderLocationSource?: string | null;
  profileLatitude?: number | null;
  profileLongitude?: number | null;
  orderType?: string | null;
  deliveryLocationMode?: string | null;
  deliveryAddress?: string | null;
  deliveryPhone?: string | null;
  townshipId?: number | null;
  townshipName?: string | null;
  wardName?: string | null;
  deliveryCharge?: number | null;
  itemsTotal?: number | null;
  deliveryStatus?: string | null;
  deliveryCurrentLocation?: string | null;
  deliveryScheduledAt?: string | null;
  requestedDeliveryAt?: string | null;
  shippingState?: string | null;
  shippingVersion?: number | null;
  shippingRenegotiated?: boolean;
  deliveryPersonPhone?: string | null;
  deliveredAt?: string | null;
  customerReceiptState?: string | null;
  customerReceivedAt?: string | null;
  customerReceiptNote?: string | null;
  awaitingCustomerReceipt?: boolean;
  completedSaleId?: number | null;
  deliveryMilestones?: DeliveryMilestone[];
};

type DeliveryMilestone = {
  id?: number;
  fromStatus?: string | null;
  toStatus?: string | null;
  actor?: string | null;
  actorType?: string | null;
  note?: string | null;
  at?: string | null;
};

type DeliverySavePayload = {
  deliveryStatus?: string;
  deliveryCurrentLocation?: string;
  deliveryScheduledAt?: string | null;
  deliveryPersonPhone?: string;
  note?: string;
};

const deliveryHandlerLabel = (h?: string | null) =>
  h === 'HANDOFF' ? 'အပြင်ပို့ အပ် (ဆိုင်ပို့ခ မကောက် / ဘောင်ချာမထည့်)'
    : h === 'OWN' ? 'ဆိုင်ကပို့ (ပို့ခ ကောက် / ဘောင်ချာထည့်)'
    : 'မဆုံးဖြတ်ရသေး';

const statusStyle = (s: string) => {
  if (s === 'PENDING') return 'bg-amber-100 text-amber-800';
  if (s === 'CONFIRMED') return 'bg-emerald-100 text-emerald-800';
  if (s === 'CANCELLED') return 'bg-rose-100 text-rose-800';
  return 'bg-slate-100 text-slate-600';
};

const orderStatusLabel = (s?: string | null) => {
  if (s === 'PENDING') return 'စောင့်ဆိုင်း';
  if (s === 'CONFIRMED') return 'လက်ခံပြီး';
  if (s === 'CANCELLED') return 'ပယ်ဖျက်ပြီး';
  return s || '—';
};

const paymentStateStyle = (s?: string | null) => {
  const key = (s || 'NONE').toUpperCase();
  if (['PAID', 'FULFILLED'].includes(key)) return 'bg-emerald-100 text-emerald-800';
  if (key === 'DEPOSIT_PAID') return 'bg-amber-100 text-amber-900';
  if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(key)) return 'bg-sky-100 text-sky-800';
  if (['EXPIRED', 'REJECTED', 'FORFEITED'].includes(key)) return 'bg-rose-100 text-rose-800';
  if (key === 'REFUND_REQUIRED') return 'bg-violet-100 text-violet-800';
  return 'bg-slate-100 text-slate-600';
};

const deliveryStatusStyle = (s?: string | null, receipt?: string | null) => {
  if (receipt === 'CONFIRMED') return 'bg-emerald-100 text-emerald-800';
  if (receipt === 'NOT_RECEIVED') return 'bg-rose-100 text-rose-800';
  if (s === 'PENDING') return 'bg-amber-100 text-amber-800';
  if (s === 'PACKING') return 'bg-orange-100 text-orange-800';
  if (s === 'PACKED') return 'bg-amber-100 text-amber-800';
  if (s === 'HANDED_TO_RIDER') return 'bg-violet-100 text-violet-800';
  if (s === 'OUT_FOR_DELIVERY') return 'bg-sky-100 text-sky-800';
  if (s === 'IN_TRANSIT') return 'bg-indigo-100 text-indigo-800';
  if (s === 'DELIVERED') return 'bg-amber-100 text-amber-800';
  return 'bg-slate-100 text-slate-500';
};

const orderTypeLabel = (t?: string | null) => {
  if (t === 'DELIVERY') return 'သွားပို့';
  if (t === 'PICKUP') return 'ဆိုင်လာယူ';
  return t || '—';
};

const deliveryStatusLabel = (s?: string | null, receipt?: string | null) => {
  if (receipt === 'CONFIRMED') return 'ဖောက်သည် လက်ခံပြီး';
  if (receipt === 'NOT_RECEIVED') return 'ဖောက်သည် မရောက်သေး';
  if (s === 'PENDING') return 'မပို့သေး';
  if (s === 'PACKING') return 'ထုပ်ပိုးနေသည်';
  if (s === 'PACKED') return 'ထုပ်ပိုးပြီး';
  if (s === 'HANDED_TO_RIDER') return 'Rider ထံ အပ်ပြီး';
  if (s === 'OUT_FOR_DELIVERY') return 'လာပို့နေပြီ';
  if (s === 'IN_TRANSIT') return 'လမ်းမှာ';
  if (s === 'DELIVERED') return 'ဆိုင်က ရောက်သည်ဟု မှတ်ထား';
  return s || '—';
};

const DELIVERY_STEPS = [
  'PENDING',
  'PACKING',
  'PACKED',
  'HANDED_TO_RIDER',
  'OUT_FOR_DELIVERY',
  'IN_TRANSIT',
  'DELIVERED',
] as const;

const deliveryStepsFor = (order: { deliveryHandler?: string | null }) =>
  order.deliveryHandler?.trim().toUpperCase() === 'HANDOFF' ? DELIVERY_STEPS.slice(0, 4) : DELIVERY_STEPS;

const deliveryPayReady = (order: { paymentState?: string | null; status?: string | null }) => {
  const pay = (order.paymentState || '').toUpperCase();
  return (pay === 'PAID' || pay === 'FULFILLED' || pay === 'DEPOSIT_PAID') && order.status !== 'CANCELLED';
};

const deliveryDispatchReady = (order: {
  paymentState?: string | null;
  status?: string | null;
  shippingState?: string | null;
  completedSaleId?: number | null;
}) => {
  const ship = (order.shippingState || 'LEGACY').trim().toUpperCase() || 'LEGACY';
  const orderOk = order.status === 'CONFIRMED' || order.completedSaleId != null;
  return deliveryPayReady(order) && orderOk && (ship === 'LEGACY' || ship === 'ACCEPTED');
};

const deliveryBlockedReason = (order: {
  paymentState?: string | null;
  status?: string | null;
  shippingState?: string | null;
  completedSaleId?: number | null;
  customerReceiptState?: string | null;
  deliveryStatus?: string | null;
}) => {
  if (order.status === 'CANCELLED') return 'ပယ်ဖျက်ပြီး အော်ဒါကို ပို့ဆောင်မှု ပြင်မရပါ။';
  if (order.customerReceiptState === 'CONFIRMED') return 'ဖောက်သည် လက်ခံပြီးသား — ပို့ဆောင်မှု အဆင့် ပြန်မချနိုင်ပါ။';
  if ((order.deliveryStatus || 'PENDING').toUpperCase() !== 'PENDING') return null;
  if (deliveryDispatchReady(order)) return null;
  const ship = (order.shippingState || 'LEGACY').trim().toUpperCase() || 'LEGACY';
  if (!['LEGACY', 'ACCEPTED'].includes(ship)) {
    return 'ပို့ချိန်/ပို့ခ အဆိုပြုချက်ကို Customer ဆီ အရင်ပို့ပြီး သူလက်ခံမှသာ ထုပ်ပိုး/ပို့နိုင်သည်။';
  }
  if (!deliveryPayReady(order)) {
    return 'စရံရပြီး သို့မဟုတ် ငွေအပြည့်ရပြီးမှသာ ထုပ်ပိုး/ပို့ဆောင်မှု စနိုင်သည်။ ကျန်ငွေကို ပို့ရောက်ချိန် ချေနိုင်သည်။';
  }
  if (order.status !== 'CONFIRMED' && order.completedSaleId == null) {
    return 'အော်ဒါကို လက်ခံ · Stock ဖယ်ထားပြီးမှ ပို့ဆောင်မှု စနိုင်သည်။';
  }
  return null;
};

const allowedDeliveryStatuses = (order: {
  deliveryHandler?: string | null;
  deliveryStatus?: string | null;
  paymentState?: string | null;
  status?: string | null;
  shippingState?: string | null;
  completedSaleId?: number | null;
  customerReceiptState?: string | null;
}) => {
  const current = (order.deliveryStatus || 'PENDING').toUpperCase();
  if (order.status === 'CANCELLED' || order.customerReceiptState === 'CONFIRMED') return [current];
  const idx = DELIVERY_STEPS.indexOf(current as (typeof DELIVERY_STEPS)[number]);
  const at = idx < 0 ? 0 : idx;
  const next: string[] = [DELIVERY_STEPS[at]];
  if (at > 0) next.push(DELIVERY_STEPS[at - 1]);
  if (at < DELIVERY_STEPS.length - 1) {
    if (at > 0 || deliveryDispatchReady(order)) next.push(DELIVERY_STEPS[at + 1]);
  }
  return Array.from(new Set(next)).filter(step => step === current || deliveryStepsFor(order).some(value => value === step));
};

const locationModeLabel = (m?: string | null) => {
  if (m === 'PROFILE') return 'Profile လိပ်စာ';
  if (m === 'OTHER') return 'အခြား လိပ်စာ';
  return m || '—';
};

const money = (n?: number | null) => Number(n || 0).toLocaleString();

const mapsUrl = (lat: number, lng: number) =>
  `https://www.google.com/maps?q=${encodeURIComponent(`${lat},${lng}`)}`;

const fmtCoord = (n: number) => n.toFixed(5);

const toDatetimeLocal = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const fromDatetimeLocal = (value: string) => {
  if (!value.trim()) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
};

const OrderGpsCell: React.FC<{ order: Order }> = ({ order }) => {
  const lat = order.orderLatitude;
  const lng = order.orderLongitude;
  if (lat == null || lng == null) {
    return <span className="text-xs text-slate-400">GPS မရှိ</span>;
  }
  const accuracy =
    order.orderLocationAccuracy != null
      ? ` ±${Math.round(order.orderLocationAccuracy)}m`
      : '';
  const profileHint =
    order.profileLatitude != null && order.profileLongitude != null
      ? `Profile: ${fmtCoord(Number(order.profileLatitude))}, ${fmtCoord(Number(order.profileLongitude))}`
      : null;
  return (
    <div className="space-y-0.5 text-xs text-slate-600">
      <a
        href={mapsUrl(Number(lat), Number(lng))}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1 font-semibold text-indigo-600 hover:underline"
        title="Google Maps တွင် ဖွင့်မည်"
        onClick={(e) => e.stopPropagation()}
      >
        <MapPin size={12} />
        {fmtCoord(Number(lat))}, {fmtCoord(Number(lng))}
        {accuracy}
      </a>
      {order.orderLocationAt && (
        <div className="text-[10px] text-slate-400">{new Date(order.orderLocationAt).toLocaleString()}</div>
      )}
      {profileHint && <div className="text-[10px] text-slate-400">{profileHint}</div>}
    </div>
  );
};

const OrderDetailModal: React.FC<{
  order: Order;
  onClose: () => void;
  onConfirm: () => void;
  onPayment: () => void;
  onCancel: () => void;
  onDeliverySave: (payload: DeliverySavePayload) => void | Promise<void>;
  onQuoteUpdated: (order: Order) => void;
}> = ({ order, onClose, onConfirm, onPayment, onCancel, onDeliverySave, onQuoteUpdated }) => {
  const lines = order.lines || [];
  const isDelivery = order.orderType === 'DELIVERY';

  const [deliveryStatus, setDeliveryStatus] = useState(order.deliveryStatus || 'PENDING');
  const [deliveryCurrentLocation, setDeliveryCurrentLocation] = useState(order.deliveryCurrentLocation || '');
  const [deliveryScheduledAt, setDeliveryScheduledAt] = useState(toDatetimeLocal(order.deliveryScheduledAt));
  const [deliveryPersonPhone, setDeliveryPersonPhone] = useState(order.deliveryPersonPhone || '');
  const [deliveryNote, setDeliveryNote] = useState('');
  const [savingDelivery, setSavingDelivery] = useState(false);

  useEffect(() => {
    setDeliveryStatus(order.deliveryStatus || 'PENDING');
    setDeliveryCurrentLocation(order.deliveryCurrentLocation || '');
    setDeliveryScheduledAt(toDatetimeLocal(order.deliveryScheduledAt));
    setDeliveryPersonPhone(order.deliveryPersonPhone || '');
    setDeliveryNote('');
  }, [order]);

  const handleDeliverySave = async () => {
    setSavingDelivery(true);
    try {
      await onDeliverySave({
        deliveryStatus,
        deliveryCurrentLocation,
        deliveryScheduledAt: fromDatetimeLocal(deliveryScheduledAt),
        deliveryPersonPhone,
        note: deliveryNote.trim() || undefined,
      });
      setDeliveryNote('');
    } finally {
      setSavingDelivery(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-4"
      onClick={onClose}
    >
      <div
        className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3 border-b px-5 py-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-lg font-bold text-slate-800">{order.orderNo}</h3>
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${statusStyle(order.status)}`}>
                {orderStatusLabel(order.status)}
              </span>
              <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${paymentStateStyle(order.paymentState)}`}>
                {paymentStateLabel(order.paymentState)}
              </span>
              {order.orderType && (
                <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-black text-indigo-700">
                  {orderTypeLabel(order.orderType)}
                </span>
              )}
            </div>
            <p className="mt-1 text-sm text-slate-600">
              <b>{order.customerName}</b>
              {order.customerPhone ? <span className="text-slate-400"> · {order.customerPhone}</span> : null}
            </p>
            {order.createdAt && (
              <p className="text-xs text-slate-400">{new Date(order.createdAt).toLocaleString()}</p>
            )}
          </div>
          <button type="button" onClick={onClose} className="rounded-lg p-2 hover:bg-slate-100">
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
          {isDelivery && order.requestedDeliveryAt && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <div className="text-[11px] font-bold uppercase text-amber-700">Customer တောင်းဆိုချိန်</div>
              <div className="text-base font-black text-amber-950">
                {new Date(order.requestedDeliveryAt).toLocaleString()}
              </div>
              {order.deliveryScheduledAt && (
                <div className="mt-1 text-sm text-amber-900">
                  ဆိုင်အဆိုပြုချိန်: <b>{new Date(order.deliveryScheduledAt).toLocaleString()}</b>
                </div>
              )}
            </div>
          )}
          {isDelivery && (
            <ShippingQuoteEditor order={order} onUpdated={onQuoteUpdated} />
          )}
          {(order.orderType || order.townshipName || order.deliveryAddress) && (
            <div className="rounded-xl border bg-indigo-50/40 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-700">
                <Truck size={15} className="text-indigo-600" />
                Delivery info
              </div>
              <div className="grid gap-2 text-sm sm:grid-cols-2">
                <div>
                  <span className="text-[11px] font-bold uppercase text-slate-400">Type</span>
                  <div className="font-semibold text-slate-800">{orderTypeLabel(order.orderType)}</div>
                </div>
                <div>
                  <span className="text-[11px] font-bold uppercase text-slate-400">Location mode</span>
                  <div className="font-semibold text-slate-800">{locationModeLabel(order.deliveryLocationMode)}</div>
                </div>
                {order.townshipName && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">Township</span>
                    <div className="font-semibold text-slate-800">
                      {order.townshipName}
                      {order.wardName ? ` · ${order.wardName}` : ''}
                      {order.deliveryCharge != null && (
                        <span className="ml-1 text-indigo-700">({money(order.deliveryCharge)} Ks)</span>
                      )}
                    </div>
                  </div>
                )}
                {order.orderType === 'DELIVERY' && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">ပို့မည့်ပုံ</span>
                    <div className="font-semibold text-slate-800">{deliveryHandlerLabel(order.deliveryHandler)}</div>
                  </div>
                )}
                {order.deliveryAddress && (
                  <div className="sm:col-span-2">
                    <span className="text-[11px] font-bold uppercase text-slate-400">Address</span>
                    <div className="text-slate-700">{order.deliveryAddress}</div>
                  </div>
                )}
                {order.requestedDeliveryAt && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">Customer တောင်းဆိုချိန်</span>
                    <div className="font-semibold text-slate-800">{new Date(order.requestedDeliveryAt).toLocaleString()}</div>
                  </div>
                )}
                {order.deliveryPhone && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">ပို့ဖုန်း</span>
                    <div className="font-semibold text-slate-800">{order.deliveryPhone}</div>
                  </div>
                )}
                {order.deliveredAt && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">ဆိုင်က ပို့ပြီးဟု မှတ်ချိန်</span>
                    <div className="text-slate-700">{new Date(order.deliveredAt).toLocaleString()}</div>
                  </div>
                )}
                {order.customerReceivedAt && (
                  <div>
                    <span className="text-[11px] font-bold uppercase text-slate-400">ဖောက်သည် အတည်ပြုချိန်</span>
                    <div className="font-semibold text-emerald-800">{new Date(order.customerReceivedAt).toLocaleString()}</div>
                  </div>
                )}
              </div>
              {order.customerReceiptState === 'CONFIRMED' && (
                <p className="mt-3 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-emerald-800">ဖောက်သည်က ပစ္စည်းလက်ထဲ ရောက်ကြောင်း အတည်ပြုပြီးပါပြီ။</p>
              )}
              {order.customerReceiptState === 'NOT_RECEIVED' && (
                <p className="mt-3 rounded-lg bg-rose-50 p-3 text-sm font-semibold text-rose-800">
                  ဖောက်သည်က ပစ္စည်း မရောက်သေးပါဟု ပြောပါသည်။
                  {order.customerReceiptNote ? ` — ${order.customerReceiptNote}` : ''}
                </p>
              )}
              {order.awaitingCustomerReceipt && order.customerReceiptState !== 'NOT_RECEIVED' && (
                <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">ဆိုင်က ပို့ပြီးနိုင်ပါတယ်။ ဖောက်သည် app မှ လက်ထဲရောက်ကြောင်း အတည်ပြုရန် စောင့်နေသည်။</p>
              )}
            </div>
          )}

          {isDelivery && (
            <div className="rounded-xl border p-4">
              <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-700">
                <Truck size={15} className="text-indigo-600" />
                ပို့ဆောင်မှု အဆင့်
              </div>
              {deliveryBlockedReason(order) && (
                <p className={`mb-3 rounded-lg p-2 text-xs font-semibold ${order.status === 'CANCELLED' ? 'bg-rose-50 text-rose-800' : 'bg-amber-50 text-amber-900'}`}>
                  {deliveryBlockedReason(order)}
                </p>
              )}
              <div className="mb-3 flex flex-wrap gap-1">
                {deliveryStepsFor(order).map((step) => {
                  const current = (order.deliveryStatus || 'PENDING').toUpperCase();
                  const at = DELIVERY_STEPS.indexOf(current as (typeof DELIVERY_STEPS)[number]);
                  const idx = DELIVERY_STEPS.indexOf(step);
                  const done = at >= idx;
                  return (
                    <span
                      key={step}
                      className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${done ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-500'}`}
                    >
                      {deliveryStatusLabel(step)}
                    </span>
                  );
                })}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block sm:col-span-2">
                  <span className="mb-1 block text-[11px] font-bold uppercase text-slate-400">ပို့အဆင့်</span>
                  <select
                    value={deliveryStatus}
                    onChange={(e) => setDeliveryStatus(e.target.value)}
                    disabled={order.status === 'CANCELLED' || order.customerReceiptState === 'CONFIRMED'}
                    className="w-full rounded-lg border px-3 py-2 text-sm disabled:bg-slate-50"
                  >
                    {([
                      ['PENDING', 'မပို့သေး (PENDING)'],
                      ['PACKING', 'ထုပ်ပိုးနေသည် (PACKING)'],
                      ['PACKED', 'ထုပ်ပိုးပြီး (PACKED)'],
                      ['HANDED_TO_RIDER', 'Rider ထံ အပ်ပြီး (HANDED_TO_RIDER)'],
                      ['OUT_FOR_DELIVERY', 'လာပို့နေပြီ (OUT_FOR_DELIVERY)'],
                      ['IN_TRANSIT', 'လမ်းမှာ (IN_TRANSIT)'],
                      ['DELIVERED', 'ဆိုင်က ပို့ပြီး (ဖောက်သည်အတည်ပြု စောင့်)'],
                    ] as const).filter(([value]) => deliveryStepsFor(order).some(step => step === value) || value === order.deliveryStatus).map(([value, label]) => (
                      <option key={value} value={value} disabled={!allowedDeliveryStatuses(order).includes(value)}>
                        {label}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block sm:col-span-2">
                  <span className="mb-1 block text-[11px] font-bold uppercase text-slate-400">လက်ရှိ တည်နေရာ</span>
                  <input
                    type="text"
                    value={deliveryCurrentLocation}
                    onChange={(e) => setDeliveryCurrentLocation(e.target.value)}
                    className="w-full rounded-lg border px-3 py-2 text-sm"
                    placeholder="လက်ရှိ တည်နေရာ"
                  />
                </label>
                <label className="block">
                  <span className="mb-1 block text-[11px] font-bold uppercase text-slate-400">ပို့ချိန်</span>
                  <input
                    type="datetime-local"
                    value={deliveryScheduledAt}
                    onChange={(e) => setDeliveryScheduledAt(e.target.value)}
                    className="w-full rounded-lg border px-3 py-2 text-sm"
                  />
                </label>
                <label className="block">
                  <span className="mb-1 block text-[11px] font-bold uppercase text-slate-400">ပို့သူ ဖုန်း</span>
                  <input
                    type="text"
                    value={deliveryPersonPhone}
                    onChange={(e) => setDeliveryPersonPhone(e.target.value)}
                    className="w-full rounded-lg border px-3 py-2 text-sm"
                    placeholder="09xxxxxxxxx"
                  />
                </label>
                <label className="block sm:col-span-2">
                  <span className="mb-1 block text-[11px] font-bold uppercase text-slate-400">မှတ်ချက် (ဤအဆင့်)</span>
                  <input
                    type="text"
                    value={deliveryNote}
                    onChange={(e) => setDeliveryNote(e.target.value)}
                    maxLength={500}
                    className="w-full rounded-lg border px-3 py-2 text-sm"
                    placeholder="ဥပမာ — rider နာမည်၊ box အရေအတွက်"
                  />
                </label>
              </div>
              {(order.deliveryMilestones || []).length > 0 && (
                <div className="mt-3 space-y-1 rounded-lg bg-slate-50 p-3">
                  <div className="text-[11px] font-bold uppercase text-slate-400">အဆင့်မှတ်တမ်း</div>
                  {(order.deliveryMilestones || []).slice().reverse().map((row) => (
                    <div key={row.id || `${row.toStatus}-${row.at}`} className="text-xs text-slate-700">
                      <span className="font-semibold">{deliveryStatusLabel(row.toStatus)}</span>
                      {row.actor ? <span className="text-slate-500"> · {row.actor}</span> : null}
                      {row.at ? <span className="text-slate-400"> · {new Date(row.at).toLocaleString()}</span> : null}
                      {row.note ? <div className="text-slate-500">{row.note}</div> : null}
                    </div>
                  ))}
                </div>
              )}
              <button
                type="button"
                disabled={savingDelivery || order.status === 'CANCELLED'}
                onClick={() => void handleDeliverySave()}
                className="mt-3 rounded-lg bg-indigo-600 px-3 py-2 text-xs font-bold text-white disabled:opacity-50"
              >
                {savingDelivery ? 'သိမ်းနေသည်…' : 'ပို့ဆောင်မှု သိမ်းမည်'}
              </button>
            </div>
          )}

          <div>
            <div className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-700">
              <Package size={15} className="text-indigo-600" />
              ပစ္စည်းစာရင်း
            </div>
            <div className="overflow-hidden rounded-xl border">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
                  <tr>
                    <th className="px-3 py-2 text-left">ပစ္စည်း</th>
                    <th className="px-3 py-2 text-left">အမျိုးအစား</th>
                    <th className="px-3 py-2 text-right">အရေအတွက်</th>
                    <th className="px-3 py-2 text-right">တစ်ခုချင်း</th>
                    <th className="px-3 py-2 text-right">စုစုပေါင်း</th>
                  </tr>
                </thead>
                <tbody>
                  {lines.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-3 py-8 text-center text-slate-400">
                        ပစ္စည်း မရှိပါ
                      </td>
                    </tr>
                  ) : (
                    lines.map((line, idx) => {
                      const serial = line.hasSerial !== false;
                      return (
                        <tr key={`${line.productId}-${idx}`} className="border-t">
                          <td className="px-3 py-3">
                            <div className="font-semibold text-slate-800">{line.productName}</div>
                            {line.productCode && (
                              <div className="text-[11px] text-slate-400">{line.productCode}</div>
                            )}
                          </td>
                          <td className="px-3 py-3">
                            {serial ? (
                              <span className="rounded-md bg-violet-50 px-2 py-1 text-[10px] font-bold text-violet-700">
                                Serial ပစ္စည်း
                              </span>
                            ) : (
                              <span className="rounded-md bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-600">
                                အရေအတွက် ပစ္စည်း
                              </span>
                            )}
                            <div className="mt-1 text-[10px] text-slate-400">
                              {serial
                                ? `ရောင်းချစဉ် serial ${line.qty} ခု ရွေးရမည်`
                                : 'Qty only — serial မလိုပါ'}
                            </div>
                          </td>
                          <td className="px-3 py-3 text-right font-bold">{line.qty}</td>
                          <td className="px-3 py-3 text-right">{money(line.unitPrice)}</td>
                          <td className="px-3 py-3 text-right font-bold text-indigo-700">
                            {money(line.subtotal)}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {(order.note || order.orderLatitude != null) && (
            <div className="grid gap-3 sm:grid-cols-2">
              {order.note && (
                <div className="rounded-xl border bg-slate-50 px-3 py-2">
                  <div className="text-[11px] font-bold uppercase text-slate-400">မှတ်ချက်</div>
                  <div className="text-sm text-slate-700">{order.note}</div>
                </div>
              )}
              {order.orderLatitude != null && order.orderLongitude != null && (
                <div className="rounded-xl border bg-slate-50 px-3 py-2">
                  <div className="text-[11px] font-bold uppercase text-slate-400">အော်ဒါ GPS</div>
                  <OrderGpsCell order={order} />
                </div>
              )}
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t px-5 py-4">
          <div>
            {order.depositAmount != null ? (
              <div className="space-y-0.5">
                <div className="text-[11px] text-slate-400">
                စရံ {order.depositPercent ?? 0}% · {money(order.depositAmount)} ကြိုလွှဲ
                  {order.remainingAmount ? ` · ကျန် ${money(order.remainingAmount)} ပို့ရောက်ချိန်` : ''}
                </div>
                <div className="text-[11px] font-semibold text-amber-800">စရံလွှဲပြီးမှ ပယ်ဖျက်ပါက စရံ ဆုံးရှုံးမည်</div>
                <div className="text-xl font-black text-indigo-700">{money(order.total)} Ks</div>
              </div>
            ) : order.itemsTotal != null && order.deliveryCharge != null && order.deliveryCharge > 0 ? (
              <div className="space-y-0.5">
                <div className="text-[11px] text-slate-400">
                  ပစ္စည်း {money(order.itemsTotal)} + ပို့ခ {money(order.deliveryCharge)}
                </div>
                <div className="text-xl font-black text-indigo-700">{money(order.total)} Ks</div>
              </div>
            ) : (
              <>
                <div className="text-[11px] uppercase text-slate-400">စုစုပေါင်း</div>
                <div className="text-xl font-black text-indigo-700">{money(order.total)} Ks</div>
              </>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {order.status === 'PENDING' && (
              <button
                type="button"
                onClick={onConfirm}
                className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white"
              >
                လက်ခံ · Stock ဖယ်ထား
              </button>
            )}
            {order.status !== 'CANCELLED' && (
              <button
                type="button"
                onClick={onPayment}
                className="rounded-lg border border-indigo-200 px-3 py-2 text-xs font-bold text-indigo-700"
              >
                ငွေပေးချေမှု
              </button>
            )}
            {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
              <button
                type="button"
                onClick={onCancel}
                className="rounded-lg bg-rose-600 px-3 py-2 text-xs font-bold text-white"
              >
                ပယ်ဖျက်
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border px-3 py-2 text-xs font-bold text-slate-600"
            >
              ပိတ်မည်
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const CustomerAppOrdersPage: React.FC = () => {
  const [rows, setRows] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Order | null>(null);
  const [paymentOrder, setPaymentOrder] = useState<Order | null>(null);
  const [workTab, setWorkTab] = useState<'ALL' | 'CONFIRM_TIME' | 'RENEGOTIATED' | 'VERIFY_PAYMENT' | 'DELIVER' | 'RECEIPT' | 'RETURNS' | 'RATINGS'>('ALL');
  const [returns, setReturns] = useState<ProductReturn[]>([]);
  const [ratings, setRatings] = useState<{
    id: number;
    orderNo?: string;
    orderType?: string;
    customerName?: string;
    rating: number;
    productRating?: number;
    serviceRating?: number;
    comment?: string;
    review?: string;
    hidden?: boolean;
    hideReason?: string;
  }[]>([]);
  const proofAlertOpen = useRef(false);
  const timeAlertOpen = useRef(false);
  const receiptAlertOpen = useRef(false);

  const load = useCallback(async (opts?: { silent?: boolean; orderId?: number }) => {
    if (!opts?.silent) setLoading(true);
    try {
      const res = await api.get<any>('/v1/customer-orders');
      let merged: Order[] = res.data ?? [];
      if (opts?.orderId) {
        try {
          const one = await api.get<any>('/v1/customer-orders/' + opts.orderId);
          const detail = one.data as Order | undefined;
          if (detail?.id) {
            merged = merged.some((o) => o.id === detail.id)
              ? merged.map((o) => (o.id === detail.id ? { ...o, ...detail } : o))
              : [detail, ...merged];
          }
        } catch {
          /* keep list */
        }
      }
      setRows(merged);
      setSelected((prev) => (prev ? merged.find((o) => o.id === prev.id) ?? prev : null));
      setPaymentOrder((prev) => (prev ? merged.find((o) => o.id === prev.id) ?? prev : null));
      try {
        const ret = await api.get<any>('/v1/customer-order-returns');
        setReturns(ret.data ?? []);
      } catch { setReturns([]); }
      try {
        const rate = await api.get<any>('/v1/customer-order-returns/ratings');
        setRatings(rate.data ?? []);
      } catch { setRatings([]); }
    } catch (e) {
      console.error(e);
    } finally {
      if (!opts?.silent) setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useRefreshOnTabActivate(() => { void load({ silent: true }); });
  useCustomerOrderLiveSync((orderId) => load({ silent: true, orderId }));

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (timeAlertOpen.current) return;
      const awaiting = rows.filter((o) => o.orderType === 'DELIVERY' && o.shippingState === 'AWAITING_SHOP');
      if (!awaiting.length) return;
      timeAlertOpen.current = true;
      void Swal.fire({
        icon: 'info',
        title: 'ပို့ချိန် အတည်ပြုရန်',
        text: `${awaiting.map((o) => o.orderNo).join(', ')} — Customer တောင်းဆိုချိန်ကို ချက်ချင်း အတည်ပြုပါ။ အဆင်မပြေရင် လုပ်ငန်းအချိန် ပို့ပါ။`,
        confirmButtonText: 'ဖွင့်မည်',
      }).then((res) => {
        timeAlertOpen.current = false;
        if (res.isConfirmed && awaiting[0]) setPaymentOrder(awaiting[0]);
      });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [rows]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (proofAlertOpen.current) return;
      const pending = rows.filter((o) => o.paymentState === 'PROOF_SUBMITTED');
      if (!pending.length) return;
      proofAlertOpen.current = true;
      void Swal.fire({
        icon: 'warning',
        title: 'ငွေလွှဲ ချက်ချင်း အတည်ပြုရန်',
        text: `${pending.map((o) => o.orderNo).join(', ')} — ငွေလွှဲအချက်အလက် ရောက်ပြီးပါပြီ။ ချက်ချင်း အတည်ပြုမည် နှိပ်ပါ။`,
        confirmButtonText: 'ငွေပေးချေမှု ဖွင့်မည်',
      }).then((res) => {
        proofAlertOpen.current = false;
        if (res.isConfirmed && pending[0]) setPaymentOrder(pending[0]);
      });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [rows]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (receiptAlertOpen.current) return;
      const missing = rows.filter((o) => (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED');
      if (!missing.length) return;
      receiptAlertOpen.current = true;
      void Swal.fire({
        icon: 'warning',
        title: 'ပစ္စည်း မရောက်သေး',
        text: `${missing.map((o) => o.orderNo).join(', ')} — ဖောက်သည်က လက်ထဲ မရောက်သေးဟု ပြောပါသည်။ ပို့ဆောင်မှု ပြန်စစ်ပါ။`,
        confirmButtonText: 'ဖွင့်မည်',
      }).then((res) => {
        receiptAlertOpen.current = false;
        if (res.isConfirmed && missing[0]) setSelected(missing[0]);
      });
    }, 8000);
    return () => window.clearInterval(timer);
  }, [rows]);

  const patchStatus = async (id: number, status: string) => {
    if (status === 'CONFIRMED') {
      const order = rows.find((o) => o.id === id) || selected;
      const ship = (order?.shippingState || '').toUpperCase();
      if (order?.orderType === 'DELIVERY' && ship && !['LEGACY', 'ACCEPTED'].includes(ship)) {
        setPaymentOrder(order);
        return;
      }
      const collectionOnly = order?.paymentChoice === 'PAY_ON_COLLECTION' && !(Number(order?.depositAmount) > 0);
      try {
        const res = await api.post<any>(`/v1/customer-orders/${id}/reserve`, {
          holdMinutes: collectionOnly ? 1440 : 15,
          deliveryHandler: order?.orderType === 'DELIVERY' ? (order.deliveryHandler || undefined) : undefined,
        });
        await load();
        setPaymentOrder(res.data || order);
      } catch (e: any) {
        await Swal.fire({
          icon: 'error',
          title: 'Order လက်ခံမရပါ',
          text: e.response?.data?.message || e.message || 'Stock ဖယ်မရပါ — ငွေပေးချေမှု panel မှ ဆက်လုပ်ပါ',
        });
        if (order) setPaymentOrder(order);
      }
      return;
    }
    if (status === 'CANCELLED') {
      const order = rows.find(o => o.id === id) || selected;
      const hasDeposit = Number(order?.depositAmount || 0) > 0;
      const paidish = ['PAID', 'DEPOSIT_PAID', 'PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW', 'AWAITING_PAYMENT'].includes(
        (order?.paymentState || '').toUpperCase()
      );
      const res = await Swal.fire({
        icon: 'warning',
        title: 'အော်ဒါ ပယ်ဖျက်မည်လား',
        text: hasDeposit && paidish
          ? 'ငွေလွှဲပြီး/စစ်ဆေးဆဲ အော်ဒါကို ဒီခလုတ်မှ ပယ်ဖျက်မရပါ။ Payment panel မှ စရံသိမ်း သို့မဟုတ် ပြန်အမ်း (ပမာဏ / Channel / reference) မှတ်ပါ။'
          : hasDeposit
            ? 'စရံမလွှဲရသေးပါ။ ပယ်ဖျက်ရင် hold ပြန်လွှတ်ပါမည်။ စရံလွှဲပြီးမှ ပယ်ဖျက်ချင်ရင် Payment panel မှ သိမ်း/ပြန်အမ်း ရွေးပါ။'
            : 'ဤအော်ဒါကို ပယ်ဖျက်မည်လား။',
        showCancelButton: true,
        confirmButtonText: 'ပယ်ဖျက်မည်',
        cancelButtonText: 'မလုပ်တော့ပါ',
        confirmButtonColor: '#dc2626',
      });
      if (!res.isConfirmed) return;
    }
    await api.patch(`/v1/customer-orders/${id}/status?status=${status}`);
    await load();
    setSelected((prev) => (prev?.id === id ? { ...prev, status } : prev));
  };

  const patchDelivery = async (id: number, payload: DeliverySavePayload) => {
    const res = await api.patch<any>(`/v1/customer-orders/${id}/delivery`, payload);
    const updated = res.data as Order | undefined;
    await load();
    if (updated) {
      setSelected(updated);
    } else {
      setSelected((prev) => (prev?.id === id ? { ...prev, ...payload } : prev));
    }
  };

  const tableColSpan = 10;

  const filteredRows = rows.filter((o) => {
    const ship = (o.shippingState || '').toUpperCase();
    const pay = (o.paymentState || '').toUpperCase();
    if (workTab === 'CONFIRM_TIME') {
      return o.orderType === 'DELIVERY' && ship === 'AWAITING_SHOP' && o.status !== 'CANCELLED';
    }
    if (workTab === 'RENEGOTIATED') {
      return o.orderType === 'DELIVERY' && ship === 'AWAITING_SHOP' && o.shippingRenegotiated === true && o.status !== 'CANCELLED';
    }
    if (workTab === 'VERIFY_PAYMENT') {
      if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) return true;
      return pay === 'DEPOSIT_PAID' && o.orderType !== 'DELIVERY';
    }
    if (workTab === 'DELIVER') {
      return pay === 'PAID' || pay === 'FULFILLED' || (o.orderType === 'DELIVERY' && pay === 'DEPOSIT_PAID');
    }
    if (workTab === 'RECEIPT') {
      return o.awaitingCustomerReceipt === true || (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED';
    }
    return true;
  });

  const tabCounts = {
    ALL: rows.length,
    CONFIRM_TIME: rows.filter((o) => o.orderType === 'DELIVERY' && (o.shippingState || '').toUpperCase() === 'AWAITING_SHOP' && o.status !== 'CANCELLED').length,
    RENEGOTIATED: rows.filter((o) => o.orderType === 'DELIVERY' && (o.shippingState || '').toUpperCase() === 'AWAITING_SHOP' && o.shippingRenegotiated === true && o.status !== 'CANCELLED').length,
    VERIFY_PAYMENT: rows.filter((o) => {
      const pay = (o.paymentState || '').toUpperCase();
      if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) return true;
      return pay === 'DEPOSIT_PAID' && o.orderType !== 'DELIVERY';
    }).length,
    DELIVER: rows.filter((o) => {
      const pay = (o.paymentState || '').toUpperCase();
      return pay === 'PAID' || pay === 'FULFILLED' || (o.orderType === 'DELIVERY' && pay === 'DEPOSIT_PAID');
    }).length,
    RECEIPT: rows.filter((o) => o.awaitingCustomerReceipt === true || (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED').length,
    RETURNS: returns.filter((r) => ['REQUESTED', 'APPROVED', 'RETURNED', 'INSPECTING'].includes(r.status)).length,
    RATINGS: ratings.length,
  };

  const workTabs: { id: typeof workTab; label: string }[] = [
    { id: 'ALL', label: `အားလုံး (${tabCounts.ALL})` },
    { id: 'CONFIRM_TIME', label: `ပို့ချိန်အတည်ပြုရန် (${tabCounts.CONFIRM_TIME})` },
    { id: 'RENEGOTIATED', label: `Customer ပြန်ညှိထားသည် (${tabCounts.RENEGOTIATED})` },
    { id: 'VERIFY_PAYMENT', label: `ငွေစစ်ရန် (${tabCounts.VERIFY_PAYMENT})` },
    { id: 'DELIVER', label: `ပို့ရန် (${tabCounts.DELIVER})` },
    { id: 'RECEIPT', label: `ဖောက်သည်လက်ခံ (${tabCounts.RECEIPT})` },
    { id: 'RETURNS', label: `ပစ္စည်းပြန်ပို့ (${tabCounts.RETURNS})` },
    { id: 'RATINGS', label: `အဆင့်သတ် (${tabCounts.RATINGS})` },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
            <Smartphone size={16} className="text-indigo-600" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-slate-800">Customer App Orders</h2>
            <p className="text-xs text-slate-500">
              ဖောက်သည် app သို့မဟုတ်{' '}
              <Link to={AppRoute.CUSTOMER_SHOP} className="font-bold text-indigo-600">ဝဘ်ဆိုင်</Link>
              {' '}မှ တင်သော ပစ္စည်းအော်ဒါများ —{' '}
              <Link to={AppRoute.DELIVERY_CHARGES} className="font-bold text-indigo-600">Delivery Charges</Link>
              {' '}သတ်မှတ် · row နှိပ်ပြီး item အသေးစိတ် ကြည့်ပါ
            </p>
          </div>
        </div>
        <button onClick={() => load()} className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border rounded-lg text-xs font-medium text-slate-600">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      <div className="flex flex-wrap gap-2">
        {workTabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setWorkTab(t.id)}
            className={`rounded-full px-3 py-1.5 text-xs font-bold border ${
              workTab === t.id ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {workTab === 'RETURNS' ? (
        <CustomerProductReturnPanel rows={returns} onReload={async () => { await load({ silent: true }); }} />
      ) : workTab === 'RATINGS' ? (
        <div className="bg-white border rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2 text-left">Customer</th>
                <th className="px-4 py-2 text-left">Order</th>
                <th className="px-4 py-2">ပစ္စည်း</th>
                <th className="px-4 py-2">ပို့/ဝန်ဆောင်</th>
                <th className="px-4 py-2 text-left">မှတ်ချက်</th>
                <th className="px-4 py-2">Moderate</th>
              </tr>
            </thead>
            <tbody>
              {ratings.length === 0 ? (
                <tr><td colSpan={6} className="py-12 text-center text-slate-400">အဆင့်သတ်မှတ်ချက် မရှိသေးပါ</td></tr>
              ) : ratings.map((r) => (
                <tr key={r.id} className={`border-t ${r.hidden ? 'bg-slate-50 text-slate-400' : ''}`}>
                  <td className="px-4 py-3">{r.customerName}</td>
                  <td className="px-4 py-3">{r.orderNo || '—'}{r.hidden ? ' · ဖျောက်ထား' : ''}</td>
                  <td className="px-4 py-3 text-center font-black">{r.productRating ?? r.rating} / 5</td>
                  <td className="px-4 py-3 text-center font-black">{r.serviceRating ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {r.comment || r.review || '—'}
                    {r.hideReason ? <span className="block text-[11px] text-rose-600">{r.hideReason}</span> : null}
                  </td>
                  <td className="px-4 py-3 text-center">
                    <button
                      type="button"
                      className="rounded border px-2 py-1 text-[11px] font-bold"
                      onClick={async () => {
                        const nextHidden = !r.hidden;
                        const reason = nextHidden
                          ? window.prompt('ဖျောက်ရသည့် အကြောင်းရင်း (optional)') || undefined
                          : undefined;
                        await api.post(`/v1/customer-order-returns/ratings/${r.id}/moderate`, {
                          hidden: nextHidden,
                          reason,
                        });
                        await load({ silent: true });
                      }}
                    >
                      {r.hidden ? 'ပြန်ပြ' : 'ဖျောက်'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
      <div className="bg-white border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
            <tr>
              <th className="px-4 py-2 text-left">အော်ဒါနံပါတ်</th>
              <th className="px-4 py-2 text-left">ဖောက်သည်</th>
              <th className="px-4 py-2 text-left">အမျိုးအစား</th>
              <th className="px-4 py-2 text-left">ပို့ဆောင်မှု</th>
              <th className="px-4 py-2 text-left">ပစ္စည်း</th>
              <th className="px-4 py-2 text-left">GPS</th>
              <th className="px-4 py-2 text-right">စုစုပေါင်း</th>
              <th className="px-4 py-2">အခြေအနေ</th>
              <th className="px-4 py-2">ငွေပေးချေမှု</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={tableColSpan} className="py-12 text-center text-slate-400">ဖတ်နေသည်…</td></tr>
            ) : filteredRows.length === 0 ? (
              <tr><td colSpan={tableColSpan} className="py-12 text-center text-slate-400">ဤ tab တွင် အော်ဒါ မရှိပါ</td></tr>
            ) : filteredRows.map((o) => (
              <tr
                key={o.id}
                className="border-t cursor-pointer transition-colors hover:bg-indigo-50/60"
                onClick={() => setSelected(o)}
                title="နှိပ်ပြီး item အသေးစိတ်ကြည့်မည်"
              >
                <td className="px-4 py-3 font-semibold text-indigo-700">{o.orderNo}</td>
                <td className="px-4 py-3">
                  <b>{o.customerName}</b>
                  <div className="text-xs text-slate-500">{o.customerPhone}</div>
                </td>
                <td className="px-4 py-3">
                  <span
                    className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                      o.orderType === 'DELIVERY'
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {orderTypeLabel(o.orderType)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {o.orderType === 'DELIVERY' ? (
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${deliveryStatusStyle(o.deliveryStatus, o.customerReceiptState)}`}>
                      {deliveryStatusLabel(o.deliveryStatus, o.customerReceiptState)}
                    </span>
                  ) : (
                    <span className="text-xs text-slate-400">—</span>
                  )}
                </td>
                <td className="px-4 py-3 text-xs text-slate-600">
                  {(o.lines || []).map((l) => `${l.productName} × ${l.qty}`).join(', ') || '—'}
                </td>
                <td className="px-4 py-3"><OrderGpsCell order={o} /></td>
                <td className="px-4 py-3 text-right font-bold">{money(o.total)}</td>
                <td className="px-4 py-3 text-center">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${statusStyle(o.status)}`}>
                    {orderStatusLabel(o.status)}
                  </span>
                </td>
                <td className="px-4 py-3 text-center">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${paymentStateStyle(o.paymentState)}`}>
                    {paymentStateLabel(o.paymentState)}
                  </span>
                </td>
                <td className="px-4 py-3 text-right space-x-1" onClick={(e) => e.stopPropagation()}>
                  <button onClick={()=>setPaymentOrder(o)} className="rounded-lg border border-indigo-200 px-2 py-1 text-[11px] font-bold text-indigo-700">ငွေပေးချေမှု</button>
                  {o.status === 'PENDING' && (
                    <button
                      onClick={() => patchStatus(o.id, 'CONFIRMED')}
                      className="rounded-lg bg-emerald-600 px-2 py-1 text-[11px] font-bold text-white"
                    >
                      လက်ခံ · Stock ဖယ်ထား
                    </button>
                  )}
                  {(o.status === 'PENDING' || o.status === 'CONFIRMED') && (
                    <button
                      onClick={() => patchStatus(o.id, 'CANCELLED')}
                      className="rounded-lg bg-rose-600 px-2 py-1 text-[11px] font-bold text-white"
                    >
                      ပယ်ဖျက်
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      )}

      {paymentOrder && <CustomerOrderPaymentPanel order={paymentOrder} onClose={()=>setPaymentOrder(null)} onUpdated={updated=>{setPaymentOrder(updated);setSelected(prev=>prev?.id===updated.id?updated:prev);void load();}} />}
      {selected && (
        <OrderDetailModal
          order={selected}
          onClose={() => setSelected(null)}
          onConfirm={() => void patchStatus(selected.id, 'CONFIRMED')}
          onPayment={() => setPaymentOrder(selected)}
          onCancel={() => void patchStatus(selected.id, 'CANCELLED')}
          onDeliverySave={(payload) => patchDelivery(selected.id, payload)}
          onQuoteUpdated={(updated) => {
            const next = { ...selected, ...updated };
            setSelected(next);
            setRows((prev) => prev.map((row) => (row.id === next.id ? { ...row, ...updated } : row)));
          }}
        />
      )}
    </div>
  );
};

export default CustomerAppOrdersPage;
