import React, { useCallback, useEffect, useState } from 'react';
import {
  Ban,
  Bell,
  Check,
  Clock,
  MapPin,
  Package,
  RefreshCw,
  Search,
  Smartphone,
  Truck,
  Wallet,
  X,
} from 'lucide-react';
import { api } from '../services/api';
import CustomerOrderPaymentPanel, { PaymentOrder, paymentStateLabel } from '../components/CustomerOrderPaymentPanel';
import ShippingQuoteEditor from '../components/ShippingQuoteEditor';
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
  fullPaymentRequired?: boolean;
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

const deliveryPayReady = (order: { paymentState?: string | null; status?: string | null; deliveryHandler?: string | null; fullPaymentRequired?: boolean }) => {
  const pay = (order.paymentState || '').toUpperCase();
  return (pay === 'PAID' || pay === 'FULFILLED' || (pay === 'DEPOSIT_PAID' && order.deliveryHandler?.trim().toUpperCase() !== 'HANDOFF' && !order.fullPaymentRequired)) && order.status !== 'CANCELLED';
};

const deliveryDispatchReady = (order: {
  deliveryHandler?: string | null;
  fullPaymentRequired?: boolean;
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
  deliveryHandler?: string | null;
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
    if (order.deliveryHandler?.trim().toUpperCase() === 'HANDOFF') return 'External delivery requires full payment verified before dispatch.';
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

const pad2 = (n: number) => String(n).padStart(2, '0');

const todayYmd = () => {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
};

const localYmd = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
};

const isTodayOrder = (order: {
  createdAt?: string | null;
  requestedDeliveryAt?: string | null;
  deliveryScheduledAt?: string | null;
}) => {
  const today = todayYmd();
  return [order.createdAt, order.requestedDeliveryAt, order.deliveryScheduledAt].some((value) => localYmd(value) === today);
};

const productSummary = (lines?: OrderLine[] | null) => {
  const list = lines || [];
  if (!list.length) return '—';
  const first = `${list[0].productName} × ${list[0].qty}`;
  const extra = list.length - 1;
  return extra > 0 ? `${first} · နောက်ထပ် ${extra}` : first;
};

const productSummaryTitle = (lines?: OrderLine[] | null) =>
  (lines || []).map((l) => `${l.productName} × ${l.qty}`).join(', ');

type NextStep = {
  label: string;
  tone: 'now' | 'wait' | 'done' | 'alert';
  action: 'detail' | 'payment' | 'confirm' | 'none';
};

const nextStepTone = (tone: NextStep['tone']) => {
  if (tone === 'now') return 'bg-amber-50 text-amber-900';
  if (tone === 'alert') return 'bg-rose-50 text-rose-800';
  if (tone === 'done') return 'bg-emerald-50 text-emerald-800';
  return 'bg-slate-100 text-slate-600';
};

const staffNextStep = (order: {
  status?: string | null;
  orderType?: string | null;
  shippingState?: string | null;
  shippingRenegotiated?: boolean;
  fullPaymentRequired?: boolean;
  paymentState?: string | null;
  deliveryStatus?: string | null;
  customerReceiptState?: string | null;
  awaitingCustomerReceipt?: boolean;
  completedSaleId?: number | null;
}): NextStep => {
  const ship = (order.shippingState || 'LEGACY').toUpperCase();
  const pay = (order.paymentState || 'NONE').toUpperCase();
  const delivery = (order.deliveryStatus || 'PENDING').toUpperCase();
  const receipt = (order.customerReceiptState || '').toUpperCase();
  const isDelivery = order.orderType === 'DELIVERY';

  if (order.status === 'CANCELLED') return { label: 'ပယ်ဖျက်ပြီး', tone: 'done', action: 'none' };
  if (receipt === 'NOT_RECEIVED') return { label: 'ပစ္စည်းမရောက် — ပြန်စစ်ပါ', tone: 'alert', action: 'detail' };
  if (isDelivery && ['AWAITING_SHOP', 'NEEDS_QUOTE'].includes(ship)) {
    return {
      label: order.shippingRenegotiated ? 'ပို့ချိန် ပြန်ပို့ပါ' : 'ပို့ချိန် အတည်ပြုပါ',
      tone: 'now',
      action: 'detail',
    };
  }
  if (ship === 'QUOTED') return { label: 'ဖောက်သည် လက်ခံစောင့်', tone: 'wait', action: 'none' };
  if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) {
    return { label: 'ငွေလွှဲ စစ်ပါ', tone: 'now', action: 'payment' };
  }
  if (['REMAINDER_PROOF_SUBMITTED', 'REMAINDER_CHECKING'].includes(pay)) {
    return { label: 'ကျန်ငွေ စစ်ပါ', tone: 'now', action: 'payment' };
  }
  if (order.status === 'PENDING') return { label: 'အော်ဒါ လက်ခံပါ', tone: 'now', action: 'confirm' };
  if (pay === 'AWAITING_PAYMENT') return { label: 'ဖောက်သည် ငွေလွှဲစောင့်', tone: 'wait', action: 'none' };
  if (pay === 'DEPOSIT_PAID') {
    const atDoor = ['HANDED_TO_RIDER', 'OUT_FOR_DELIVERY', 'IN_TRANSIT', 'DELIVERED'].includes(delivery);
    if (isDelivery && !atDoor) return { label: 'ထုပ်ပိုး / ပို့ပါ', tone: 'now', action: 'detail' };
    return { label: 'ကျန်ငွေ လက်ခံပါ', tone: 'now', action: 'payment' };
  }
  if (pay === 'PAID' && !order.completedSaleId) return { label: 'ဘောင်ချာထုတ်ပါ', tone: 'now', action: 'payment' };
  if (order.awaitingCustomerReceipt) return { label: 'ဖောက်သည် လက်ခံစောင့်', tone: 'wait', action: 'none' };
  if (isDelivery && pay === 'FULFILLED' && receipt !== 'CONFIRMED' && delivery !== 'DELIVERED') {
    return { label: 'ပို့ဆောင်မှု ဆက်လုပ်ပါ', tone: 'now', action: 'detail' };
  }
  if (pay === 'FULFILLED' || receipt === 'CONFIRMED' || order.completedSaleId) {
    return { label: 'ပြီးပါပြီ', tone: 'done', action: 'none' };
  }
  return { label: 'အသေးစိတ် ကြည့်ပါ', tone: 'wait', action: 'detail' };
};

const isOpenOrder = (order: Parameters<typeof staffNextStep>[0]) => staffNextStep(order).tone !== 'done';

type DetailTab = 'overview' | 'quote' | 'delivery';

const staffDetailTab = (order: {
  status?: string | null;
  orderType?: string | null;
  shippingState?: string | null;
  shippingRenegotiated?: boolean;
  fullPaymentRequired?: boolean;
  paymentState?: string | null;
  deliveryStatus?: string | null;
  customerReceiptState?: string | null;
  awaitingCustomerReceipt?: boolean;
  completedSaleId?: number | null;
}): DetailTab => {
  if (order.orderType !== 'DELIVERY') return 'overview';
  const ship = (order.shippingState || 'LEGACY').toUpperCase();
  const receipt = (order.customerReceiptState || '').toUpperCase();
  const delivery = (order.deliveryStatus || 'PENDING').toUpperCase();
  const step = staffNextStep(order);
  if (['AWAITING_SHOP', 'NEEDS_QUOTE'].includes(ship)) return 'quote';
  if (ship === 'QUOTED') return 'quote';
  if (receipt === 'NOT_RECEIVED' || order.awaitingCustomerReceipt) return 'delivery';
  if (step.action === 'detail') return 'delivery';
  if (delivery !== 'PENDING' && receipt !== 'CONFIRMED') return 'delivery';
  return 'overview';
};

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
  const [tab, setTab] = useState<DetailTab>(() => staffDetailTab(order));
  const step = staffNextStep(order);
  const ship = (order.shippingState || '').toUpperCase();
  const quoteNeedsAction = isDelivery && ['AWAITING_SHOP', 'NEEDS_QUOTE', 'QUOTED'].includes(ship);
  const deliveryNeedsAction = isDelivery && !quoteNeedsAction && staffDetailTab(order) === 'delivery';

  useEffect(() => {
    setTab(staffDetailTab(order));
  }, [order.id]);

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
        className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
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

        {isDelivery && (
          <div className="flex gap-1 border-b px-5 pt-1">
            {([
              ['overview', 'အနှစ်ချုပ်', Package],
              ['quote', 'ပို့ချိန် / ပို့ခ', Clock],
              ['delivery', 'ပို့ဆောင်မှု', Truck],
            ] as const).map(([id, label, Icon]) => {
              const active = tab === id;
              const urgent = !active && ((id === 'quote' && quoteNeedsAction) || (id === 'delivery' && deliveryNeedsAction));
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => setTab(id)}
                  className={`inline-flex items-center gap-1.5 border-b-2 px-3 py-2 text-xs font-bold ${
                    active
                      ? 'border-indigo-600 text-indigo-700'
                      : urgent
                        ? 'border-transparent text-amber-800'
                        : 'border-transparent text-slate-500 hover:text-slate-800'
                  }`}
                >
                  <Icon size={13} />
                  {label}
                  {urgent ? <span className="rounded-full bg-amber-100 px-1.5 py-0.5 text-[9px]">လုပ်ရန်</span> : null}
                </button>
              );
            })}
          </div>
        )}

        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4">
          <button
            type="button"
            onClick={() => {
              if (step.action === 'payment') {
                onPayment();
                return;
              }
              if (isDelivery && ['AWAITING_SHOP', 'NEEDS_QUOTE', 'QUOTED'].includes(ship)) {
                setTab('quote');
                return;
              }
              if (step.action === 'detail' || step.action === 'confirm') {
                if (isDelivery && step.action === 'detail') setTab('delivery');
                else setTab('overview');
              }
            }}
            className={`w-full rounded-xl px-3 py-2 text-left text-sm font-semibold ${nextStepTone(step.tone)}`}
          >
            ယခုလုပ်ရန် · {step.label}
          </button>
          {tab === 'quote' && isDelivery && (
            <ShippingQuoteEditor order={order} onUpdated={onQuoteUpdated} />
          )}
          {tab === 'delivery' && (order.orderType || order.townshipName || order.deliveryAddress) && (
            <div className="rounded-xl border bg-indigo-50/40 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-700">
                <Truck size={15} className="text-indigo-600" />
                ပို့ဆောင်မှု
              </div>
              <div className="grid gap-2 text-sm sm:grid-cols-2">
                <div>
                  <span className="text-[11px] font-bold text-slate-400">အမျိုးအစား</span>
                  <div className="font-semibold text-slate-800">{orderTypeLabel(order.orderType)}</div>
                </div>
                <div>
                  <span className="text-[11px] font-bold text-slate-400">လိပ်စာအမျိုးအစား</span>
                  <div className="font-semibold text-slate-800">{locationModeLabel(order.deliveryLocationMode)}</div>
                </div>
                {order.townshipName && (
                  <div>
                    <span className="text-[11px] font-bold text-slate-400">မြို့နယ်</span>
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
                    <span className="text-[11px] font-bold text-slate-400">ပို့မည့်ပုံ</span>
                    <div className="font-semibold text-slate-800">{deliveryHandlerLabel(order.deliveryHandler)}</div>
                  </div>
                )}
                {order.deliveryAddress && (
                  <div className="sm:col-span-2">
                    <span className="text-[11px] font-bold text-slate-400">လိပ်စာ</span>
                    <div className="text-slate-700">{order.deliveryAddress}</div>
                  </div>
                )}
                {order.requestedDeliveryAt && (
                  <div>
                    <span className="text-[11px] font-bold text-slate-400">ဖောက်သည် တောင်းဆိုချိန်</span>
                    <div className="font-semibold text-slate-800">{new Date(order.requestedDeliveryAt).toLocaleString()}</div>
                  </div>
                )}
                {order.deliveryPhone && (
                  <div>
                    <span className="text-[11px] font-bold text-slate-400">ပို့ဖုန်း</span>
                    <div className="font-semibold text-slate-800">{order.deliveryPhone}</div>
                  </div>
                )}
                {order.deliveredAt && (
                  <div>
                    <span className="text-[11px] font-bold text-slate-400">ဆိုင်က ပို့ပြီးဟု မှတ်ချိန်</span>
                    <div className="text-slate-700">{new Date(order.deliveredAt).toLocaleString()}</div>
                  </div>
                )}
                {order.customerReceivedAt && (
                  <div>
                    <span className="text-[11px] font-bold text-slate-400">ဖောက်သည် အတည်ပြုချိန်</span>
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

          {tab === 'delivery' && isDelivery && (
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
                      ['PENDING', 'မပို့သေး'],
                      ['PACKING', 'ထုပ်ပိုးနေသည်'],
                      ['PACKED', 'ထုပ်ပိုးပြီး'],
                      ['HANDED_TO_RIDER', 'Rider ထံ အပ်ပြီး'],
                      ['OUT_FOR_DELIVERY', 'လာပို့နေပြီ'],
                      ['IN_TRANSIT', 'လမ်းမှာ'],
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

          {tab === 'overview' && (
            <>
              {isDelivery && (
                <div className="rounded-xl border bg-slate-50 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 space-y-1 text-sm">
                      <div className="font-bold text-slate-800">
                        {orderTypeLabel(order.orderType)}
                        <span className="ml-2 font-semibold text-indigo-700">
                          {deliveryStatusLabel(order.deliveryStatus, order.customerReceiptState)}
                        </span>
                      </div>
                      {(order.townshipName || order.deliveryAddress) && (
                        <div className="text-slate-600">
                          {order.townshipName}
                          {order.wardName ? ` · ${order.wardName}` : ''}
                          {order.deliveryAddress ? ` · ${order.deliveryAddress}` : ''}
                        </div>
                      )}
                      {order.requestedDeliveryAt && (
                        <div className="text-xs text-slate-500">
                          တောင်းဆိုချိန် {new Date(order.requestedDeliveryAt).toLocaleString()}
                          {order.deliveryScheduledAt
                            ? ` · ပို့ချိန် ${new Date(order.deliveryScheduledAt).toLocaleString()}`
                            : ''}
                        </div>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-2">
                    {quoteNeedsAction && (
                      <button
                        type="button"
                        onClick={() => setTab('quote')}
                        className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-[11px] font-bold text-amber-900"
                      >
                        ပို့ချိန် / ပို့ခ
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => setTab('delivery')}
                      className="rounded-lg border border-indigo-200 px-3 py-1.5 text-[11px] font-bold text-indigo-700"
                    >
                      ပို့ဆောင်မှု ပြင်မည်
                    </button>
                    </div>
                  </div>
                </div>
              )}
          <div>
            <div className="mb-2 flex items-center gap-2 text-sm font-bold text-slate-700">
              <Package size={15} className="text-indigo-600" />
              ပစ္စည်းစာရင်း
            </div>
            <div className="overflow-x-auto rounded-xl border">
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
            </>
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
                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white"
              >
                <Check size={14} />
                လက်ခံ
              </button>
            )}
            {order.status !== 'CANCELLED' && (
              <button
                type="button"
                onClick={onPayment}
                className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 px-3 py-2 text-xs font-bold text-indigo-700"
              >
                <Wallet size={14} />
                ငွေပေးချေမှု
              </button>
            )}
            {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
              <button
                type="button"
                onClick={onCancel}
                className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3 py-2 text-xs font-bold text-white"
              >
                <Ban size={14} />
                ပယ်ဖျက်
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="inline-flex items-center gap-1.5 rounded-lg border px-3 py-2 text-xs font-bold text-slate-600"
            >
              <X size={14} />
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
  const [search, setSearch] = useState('');
  const [workTab, setWorkTab] = useState<'OPEN' | 'TODAY' | 'DONE' | 'CONFIRM_TIME' | 'VERIFY_PAYMENT' | 'DELIVER' | 'RECEIPT' | 'RATINGS'>('OPEN');
  const [doneFrom, setDoneFrom] = useState(todayYmd);
  const [doneTo, setDoneTo] = useState(todayYmd);
  const [dismissedWork, setDismissedWork] = useState('');
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

  const patchStatus = async (id: number, status: string) => {
    if (status === 'CONFIRMED') {
      const order = rows.find((o) => o.id === id) || selected;
      const ship = (order?.shippingState || '').toUpperCase();
      if (order?.orderType === 'DELIVERY' && ship && !['LEGACY', 'ACCEPTED'].includes(ship)) {
        setPaymentOrder(null);
        setSelected(order);
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

  const tableColSpan = 7;
  const searching = search.trim().length > 0;

  const filteredRows = rows.filter((o) => {
    const ship = (o.shippingState || '').toUpperCase();
    const pay = (o.paymentState || '').toUpperCase();
    if (searching) return true;
    if (workTab === 'OPEN') return isOpenOrder(o);
    if (workTab === 'TODAY') return isTodayOrder(o);
    if (workTab === 'DONE') {
      if (isOpenOrder(o)) return false;
      const ymd = localYmd(o.createdAt);
      return ymd >= doneFrom && ymd <= doneTo;
    }
    if (workTab === 'CONFIRM_TIME') {
      return o.orderType === 'DELIVERY' && ship === 'AWAITING_SHOP' && o.status !== 'CANCELLED';
    }
    if (workTab === 'VERIFY_PAYMENT') {
      if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) return true;
      return pay === 'DEPOSIT_PAID' && o.orderType !== 'DELIVERY';
    }
    if (workTab === 'DELIVER') {
      if (!isOpenOrder(o)) return false;
      return pay === 'PAID' || pay === 'FULFILLED' || (o.orderType === 'DELIVERY' && pay === 'DEPOSIT_PAID');
    }
    if (workTab === 'RECEIPT') {
      return o.awaitingCustomerReceipt === true || (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED';
    }
    return isOpenOrder(o);
  });

  const normalizedSearch = search.trim().toLowerCase();
  const visibleRows = filteredRows.filter((o) => !normalizedSearch || [o.orderNo, o.customerName, o.customerPhone, o.deliveryPhone].some((value) => String(value || '').toLowerCase().includes(normalizedSearch))).sort((a, b) => {
    const urgent = (o: Order) => {
      const pay = (o.paymentState || '').toUpperCase();
      if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) return 0;
      if (o.orderType === 'DELIVERY' && (o.shippingState || '').toUpperCase() === 'AWAITING_SHOP') return 1;
      if (o.awaitingCustomerReceipt || (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED') return 2;
      if (o.status === 'PENDING') return 3;
      return 4;
    };
    return urgent(a) - urgent(b) || Number(b.id) - Number(a.id);
  });
  const tabCounts = {
    OPEN: rows.filter(isOpenOrder).length,
    TODAY: rows.filter(isTodayOrder).length,
    DONE: rows.filter((o) => !isOpenOrder(o) && localYmd(o.createdAt) >= doneFrom && localYmd(o.createdAt) <= doneTo).length,
    CONFIRM_TIME: rows.filter((o) => o.orderType === 'DELIVERY' && (o.shippingState || '').toUpperCase() === 'AWAITING_SHOP' && o.status !== 'CANCELLED').length,
    VERIFY_PAYMENT: rows.filter((o) => {
      const pay = (o.paymentState || '').toUpperCase();
      if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(pay)) return true;
      return pay === 'DEPOSIT_PAID' && o.orderType !== 'DELIVERY';
    }).length,
    DELIVER: rows.filter((o) => {
      if (!isOpenOrder(o)) return false;
      const pay = (o.paymentState || '').toUpperCase();
      return pay === 'PAID' || pay === 'FULFILLED' || (o.orderType === 'DELIVERY' && pay === 'DEPOSIT_PAID');
    }).length,
    RECEIPT: rows.filter((o) => o.awaitingCustomerReceipt === true || (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED').length,
    RATINGS: ratings.length,
  };

  const timeOrders = rows.filter((o) => o.orderType === 'DELIVERY' && (o.shippingState || '').toUpperCase() === 'AWAITING_SHOP' && o.status !== 'CANCELLED');
  const proofOrders = rows.filter((o) => ['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes((o.paymentState || '').toUpperCase()));
  const receiptOrders = rows.filter((o) => (o.customerReceiptState || '').toUpperCase() === 'NOT_RECEIVED');
  const workSignature = [
    timeOrders.map((o) => o.id).sort((a, b) => a - b).join(','),
    proofOrders.map((o) => o.id).sort((a, b) => a - b).join(','),
    receiptOrders.map((o) => o.id).sort((a, b) => a - b).join(','),
  ].join('|');
  const workParts = [
    timeOrders.length ? `ပို့ချိန် ${timeOrders.length}` : '',
    proofOrders.length ? `ငွေစစ်ရန် ${proofOrders.length}` : '',
    receiptOrders.length ? `ပစ္စည်းမရောက် ${receiptOrders.length}` : '',
  ].filter(Boolean);
  const showWorkBanner = workParts.length > 0 && dismissedWork !== workSignature;

  const openDetail = (order: Order) => {
    setPaymentOrder(null);
    setSelected(order);
  };
  const openPayment = (order: Order) => {
    setSelected(null);
    setPaymentOrder(order);
  };

  const scopeTabs: { id: typeof workTab; label: string; count: number; urgent?: boolean }[] = [
    { id: 'OPEN', label: 'လုပ်ရန်', count: tabCounts.OPEN, urgent: true },
    { id: 'TODAY', label: 'ဒီနေ့', count: tabCounts.TODAY },
    { id: 'DONE', label: 'ပြီးပါပြီ', count: tabCounts.DONE },
  ];
  const queueTabs: { id: typeof workTab; label: string; count: number; urgent?: boolean }[] = [
    { id: 'CONFIRM_TIME', label: 'ပို့ချိန်', count: tabCounts.CONFIRM_TIME, urgent: true },
    { id: 'VERIFY_PAYMENT', label: 'ငွေစစ်ရန်', count: tabCounts.VERIFY_PAYMENT, urgent: true },
    { id: 'DELIVER', label: 'ပို့ရန်', count: tabCounts.DELIVER },
    { id: 'RECEIPT', label: 'လက်ခံမှု', count: tabCounts.RECEIPT, urgent: true },
    { id: 'RATINGS', label: 'အဆင့်သတ်', count: tabCounts.RATINGS },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
            <Smartphone size={16} className="text-indigo-600" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-slate-800">ဖောက်သည် အော်ဒါများ</h2>
            <p className="text-xs text-slate-500">ပုံမှန် · လုပ်ရန်ကျန် · ရှာရင် အော်ဒါဟောင်းပါ ထွက်မည်</p>
          </div>
        </div>
        <button onClick={() => load()} className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border rounded-lg text-xs font-medium text-slate-600">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> ပြန်ဖတ်
        </button>
      </div>

      {showWorkBanner && (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2">
          <Bell size={14} className="shrink-0 text-amber-800" />
          <p className="min-w-0 flex-1 text-xs font-semibold text-amber-950">
            လုပ်ရန်ရှိသည် · {workParts.join(' · ')}
          </p>
          {timeOrders[0] && (
            <button type="button" onClick={() => { setWorkTab('CONFIRM_TIME'); openDetail(timeOrders[0]); }} className="rounded-md bg-white px-2 py-1 text-[11px] font-bold text-amber-900">
              ပို့ချိန် ဖွင့်
            </button>
          )}
          {proofOrders[0] && (
            <button type="button" onClick={() => { setWorkTab('VERIFY_PAYMENT'); openPayment(proofOrders[0]); }} className="rounded-md bg-white px-2 py-1 text-[11px] font-bold text-amber-900">
              ငွေစစ်ရန်
            </button>
          )}
          {receiptOrders[0] && (
            <button type="button" onClick={() => { setWorkTab('RECEIPT'); openDetail(receiptOrders[0]); }} className="rounded-md bg-white px-2 py-1 text-[11px] font-bold text-amber-900">
              မရောက်သေး
            </button>
          )}
          <button
            type="button"
            onClick={() => setDismissedWork(workSignature)}
            className="rounded-md p-1 text-amber-800 hover:bg-white/70"
            aria-label="ပိတ်မည်"
          >
            <X size={14} />
          </button>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-sm">
            <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="အော်ဒါနံပါတ် / နာမည် / ဖုန်း — အားလုံးထဲမှ ရှာမည်"
              className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-9 text-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="ရှာဖွေမှု ဖျက်မည်"
              >
                <X size={14} />
              </button>
            )}
          </div>
          {workTab === 'DONE' && !searching ? (
            <div className="flex flex-wrap items-center gap-2 text-xs font-semibold text-slate-600">
              <label className="flex items-center gap-1">
                စတင်
                <input type="date" value={doneFrom} onChange={(e) => setDoneFrom(e.target.value)} className="rounded-md border px-2 py-1" />
              </label>
              <label className="flex items-center gap-1">
                ပြီး
                <input type="date" value={doneTo} onChange={(e) => setDoneTo(e.target.value)} className="rounded-md border px-2 py-1" />
              </label>
            </div>
          ) : (
            <p className="text-xs font-semibold text-slate-500">
              {searching ? 'ရှာဖွေမှုသည် အော်ဒါအားလုံးထဲမှ ရှာသည်' : 'အရေးကြီးသော အော်ဒါများကို အပေါ်ဆုံးတွင် ပြထားသည်'}
            </p>
          )}
        </div>
      </div>

      <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
        <div className="flex flex-wrap gap-2">
          {scopeTabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setWorkTab(t.id)}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-bold ${
                workTab === t.id ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-200 bg-white text-slate-600'
              }`}
            >
              {t.label}
              <span
                className={`rounded-full px-1.5 py-0.5 text-[10px] ${
                  workTab === t.id
                    ? 'bg-white/20 text-white'
                    : t.urgent && t.count > 0
                      ? 'bg-amber-100 text-amber-800'
                      : 'bg-slate-100 text-slate-600'
                }`}
              >
                {t.count}
              </span>
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-2">
          {queueTabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setWorkTab(t.id)}
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-bold ${
                workTab === t.id ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-200 bg-white text-slate-600'
              }`}
            >
              {t.label}
              <span
                className={`rounded-full px-1.5 py-0.5 text-[10px] ${
                  workTab === t.id
                    ? 'bg-white/20 text-white'
                    : t.urgent && t.count > 0
                      ? 'bg-amber-100 text-amber-800'
                      : 'bg-slate-100 text-slate-600'
                }`}
              >
                {t.count}
              </span>
            </button>
          ))}
        </div>
      </div>

      {workTab === 'RATINGS' ? (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-slate-50 text-[11px] font-bold text-slate-500">
              <tr>
                <th className="px-4 py-2 text-left">ဖောက်သည်</th>
                <th className="px-4 py-2 text-left">အော်ဒါ</th>
                <th className="px-4 py-2">ပစ္စည်း</th>
                <th className="px-4 py-2">ပို့/ဝန်ဆောင်</th>
                <th className="px-4 py-2 text-left">မှတ်ချက်</th>
                <th className="px-4 py-2">ပြင်ဆင်ရန်</th>
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
      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full min-w-[720px] text-sm">
          <thead className="bg-slate-50 text-[11px] font-bold text-slate-500">
            <tr>
              <th className="px-4 py-2 text-left">အော်ဒါ</th>
              <th className="px-4 py-2 text-left">ဖောက်သည်</th>
              <th className="px-4 py-2 text-left">ပစ္စည်း</th>
              <th className="px-4 py-2 text-left">ရက်</th>
              <th className="px-4 py-2 text-right">ငွေ</th>
              <th className="px-4 py-2 text-left">ယခုလုပ်ရန်</th>
              <th className="px-4 py-2 text-right">လုပ်ဆောင်ချက်</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={tableColSpan} className="py-12 text-center text-slate-400">ဖတ်နေသည်…</td></tr>
            ) : visibleRows.length === 0 ? (
              <tr>
                <td colSpan={tableColSpan} className="py-12 text-center text-slate-400">
                  {searching
                    ? 'ရှာမတွေ့ပါ'
                    : workTab === 'TODAY'
                      ? 'ဒီနေ့တင် / ဒီနေ့ပို့ရမည့် အော်ဒါ မရှိပါ'
                      : workTab === 'DONE'
                        ? 'ဤရက်အတွင်း ပြီးသွားသော အော်ဒါ မရှိပါ'
                        : workTab === 'OPEN'
                          ? 'လုပ်ရန်ကျန် အော်ဒါ မရှိပါ'
                          : 'ဤစာရင်းတွင် အော်ဒါ မရှိပါ'}
                </td>
              </tr>
            ) : visibleRows.map((o) => {
              const step = staffNextStep(o);
              return (
              <tr
                key={o.id}
                className="border-t cursor-pointer transition-colors hover:bg-indigo-50/60"
                onClick={() => openDetail(o)}
                title="နှိပ်ပြီး အသေးစိတ်ကြည့်မည်"
              >
                <td className="px-4 py-3">
                  <div className="font-semibold text-indigo-700">{o.orderNo}</div>
                  <div className="text-[11px] text-slate-500">
                    {orderTypeLabel(o.orderType)}
                    {o.orderType === 'DELIVERY' ? ` · ${deliveryStatusLabel(o.deliveryStatus, o.customerReceiptState)}` : ''}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <b>{o.customerName}</b>
                  <div className="text-xs text-slate-500">{o.customerPhone}</div>
                </td>
                <td className="max-w-[200px] px-4 py-3 text-xs text-slate-600" title={productSummaryTitle(o.lines)}>
                  {productSummary(o.lines)}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-600">
                  <div>{o.createdAt ? new Date(o.createdAt).toLocaleString() : '—'}</div>
                  {(o.deliveryScheduledAt || o.requestedDeliveryAt) && (
                    <div className="text-[11px] text-indigo-600">
                      ပို့ {new Date(o.deliveryScheduledAt || o.requestedDeliveryAt || '').toLocaleString()}
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 text-right">
                  <div className="font-bold">{money(o.total)} Ks</div>
                  <div className="text-[11px] text-slate-500">{paymentStateLabel(o.paymentState)}</div>
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2 py-1 text-[11px] font-bold ${nextStepTone(step.tone)}`}>
                    {step.label}
                  </span>
                </td>
                <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                  {step.action === 'confirm' && (
                    <button
                      type="button"
                      onClick={() => patchStatus(o.id, 'CONFIRMED')}
                      className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2.5 py-1.5 text-[11px] font-bold text-white"
                    >
                      <Check size={12} />
                      လက်ခံ
                    </button>
                  )}
                  {step.action === 'payment' && (
                    <button
                      type="button"
                      onClick={() => openPayment(o)}
                      className="inline-flex items-center gap-1 rounded-lg bg-indigo-600 px-2.5 py-1.5 text-[11px] font-bold text-white"
                    >
                      <Wallet size={12} />
                      ငွေစစ်ရန်
                    </button>
                  )}
                  {step.action === 'detail' && (
                    <button
                      type="button"
                      onClick={() => openDetail(o)}
                      className="inline-flex items-center gap-1 rounded-lg border border-indigo-200 px-2.5 py-1.5 text-[11px] font-bold text-indigo-700"
                    >
                      ဖွင့်မည်
                    </button>
                  )}
                  {step.action === 'none' && (
                    <button
                      type="button"
                      onClick={() => openDetail(o)}
                      className="text-[11px] font-bold text-slate-500 hover:text-indigo-700"
                    >
                      ကြည့်မည်
                    </button>
                  )}
                </td>
              </tr>
              );
            })}
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
          onPayment={() => openPayment(selected)}
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
