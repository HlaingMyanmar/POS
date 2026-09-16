import React, { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import {
  CustomerOrder,
  CustomerPaymentChannel,
  customerPortalService,
} from '../../services/customerPortalApi';
import { CustomerShippingQuote } from './DeliveryCheckout';

const money = (n?: number | null) => `${Number(n || 0).toLocaleString()} Ks`;

const paymentLabel = (state?: string, choice?: string, shipping?: string) => {
  if (choice === 'PENDING') return shipping === 'ACCEPTED' ? 'ငွေပေးချေနည်း ရွေးရန်' : 'ဆိုင်အတည်ပြု စောင့်';
  switch (state) {
    case 'AWAITING_PAYMENT': return 'ငွေလွှဲရန်';
    case 'AWAITING_COLLECTION': return 'လက်ခံချိန်ပေး';
    case 'PROOF_SUBMITTED':
    case 'CHECKING':
    case 'REVIEW':
    case 'LATE_REVIEW': return 'စစ်ဆေးနေသည်';
    case 'NONE': return 'စောင့်ဆိုင်း';
    case 'DEPOSIT_PAID': return 'စရံရရှိပြီး';
    case 'PAID':
    case 'FULFILLED': return 'ပေးပြီး';
    case 'EXPIRED': return 'အချိန်ကုန် · အထောက်အထားတင်နိုင်';
    case 'REJECTED': return 'ငြင်းပယ်';
    case 'REFUND_REQUIRED': return 'ငွေပြန်အမ်းရန်';
    case 'REFUNDED': return 'ငွေပြန်အမ်းပြီး';
    case 'FORFEITED': return 'စရံသိမ်းပြီး';
    default: return choice === 'PAY_ON_COLLECTION' ? 'လက်ခံချိန်ပေး' : (state || '—');
  }
};

const openPdfBlob = async (blob: Blob, fallbackName: string) => {
  if (blob.type && blob.type.includes('json')) {
    const text = await blob.text();
    const parsed = JSON.parse(text);
    throw new Error(parsed.message || 'Invoice မရနိုင်သေးပါ');
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fallbackName;
  a.target = '_blank';
  a.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
};

const needsAttention = (order: CustomerOrder) => {
  const shipping = (order.shippingState || '').toUpperCase();
  if (order.orderType === 'DELIVERY' && shipping && !['LEGACY', 'ACCEPTED'].includes(shipping)) return true;
  if (['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes((order.paymentState || '').toUpperCase())) return true;
  if ((order.paymentState || 'NONE') === 'NONE' && order.status === 'PENDING') return true;
  if (order.awaitingCustomerReceipt || (order.customerReceiptState || '') === 'NOT_RECEIVED') return true;
  return ['AWAITING_PAYMENT', 'EXPIRED', 'REJECTED'].includes((order.paymentState || '').toUpperCase());
};

const fulfillmentShort = (order: CustomerOrder) => {
  if (order.orderType === 'PICKUP') return 'ဆိုင်လာယူ';
  if (order.orderType === 'DELIVERY') return 'သွားပို့';
  return '';
};

const productSummary = (order: CustomerOrder) => {
  const lines = order.lines || [];
  if (!lines.length) return 'ပစ္စည်းစာရင်း မရှိပါ';
  const first = lines[0].productName || 'ပစ္စည်း';
  const extra = lines.length - 1;
  return extra > 0 ? `${first} · နောက်ထပ် ${extra} မျိုး` : first;
};

export const CustomerOrderCard: React.FC<{
  order: CustomerOrder;
  onUpdated: (next?: CustomerOrder) => void;
  onReorder?: (order: CustomerOrder) => void;
}> = ({ order, onUpdated, onReorder }) => {
  const attention = needsAttention(order);
  const [open, setOpen] = useState(attention);
  useEffect(() => { if (attention) setOpen(true); }, [attention]);
  const items = Number(order.itemsTotal ?? (Number(order.total || 0) - Number(order.deliveryCharge || 0)));
  const delivery = Number(order.quotedDeliveryCharge ?? order.deliveryCharge ?? 0);
  return (
    <div className={`mb-2 rounded-xl border bg-white p-3 ${attention ? 'border-amber-300' : ''}`}>
      <button type="button" className="flex w-full items-start justify-between gap-2 text-left" onClick={() => setOpen(v => !v)}>
        <div className="min-w-0">
          <b className="text-indigo-700">{order.orderNo}</b>
          <div className="truncate text-xs text-slate-500">
            {[fulfillmentShort(order), productSummary(order)].filter(Boolean).join(' · ')}
          </div>
        </div>
        <div className="shrink-0 text-right">
          <span className="text-[10px] font-black text-slate-500">{paymentLabel(order.paymentState, order.paymentChoice, order.shippingState)}</span>
          <div className="text-sm font-bold">
            {order.shippingState === 'NEEDS_QUOTE' ? 'ပို့ခကျန်' : money(order.total)}
          </div>
          <div className="text-[10px] text-slate-400">{open ? 'ပိတ်မည်' : 'အသေးစိတ်'}</div>
        </div>
      </button>
      {open && (
        <div className="mt-3 space-y-2 border-t pt-3 text-sm">
          <div className="flex justify-between text-xs"><span>ပစ္စည်းဖိုး</span><span>{money(items)}</span></div>
          {order.orderType === 'DELIVERY' && (
            <div className="flex justify-between text-xs">
              <span>{order.deliveryHandler === 'HANDOFF' ? 'ဆိုင်ပို့ခ' : 'ပို့ခ'}</span>
              <span>
                {order.deliveryHandler === 'HANDOFF'
                  ? '၀ (အပြင်ပို့ — ဘောင်ချာမထည့်)'
                  : order.shippingState === 'NEEDS_QUOTE' ? 'ဆိုင်မှတွက်မည်' : money(delivery)}
              </span>
            </div>
          )}
          {order.depositAmount != null && (
            <>
              <div className="flex justify-between text-xs"><span>စရံ {Number(order.depositPercent || 0)}%</span><span>{money(order.depositAmount)}</span></div>
              {order.remainingAmount != null && <div className="flex justify-between text-xs"><span>ကျန် (လက်ခံချိန်)</span><span>{money(order.remainingAmount)}</span></div>}
              <p className="rounded-lg bg-amber-50 p-2 text-[11px] text-amber-900">စရံလွှဲပြီးမှ အော်ဒါ ပယ်ဖျက်ပါက စရံငွေ ဆုံးရှုံးမည်။ ပြန်အမ်းမည် မဟုတ်ပါ။</p>
            </>
          )}
          <div className="flex justify-between font-bold"><span>စုစုပေါင်း</span><span>{money(order.total)}</span></div>
          {order.orderType === 'DELIVERY' && (
            <>
              <p className="text-xs text-slate-500">
                {[order.wardName, order.townshipName].filter(Boolean).join(' · ')}
                {order.deliveryAddress ? ` · ${order.deliveryAddress}` : ''}
                {order.deliveryPhone ? ` · ${order.deliveryPhone}` : ''}
                {order.requestedDeliveryAt ? ` · တောင်းဆို ${new Date(order.requestedDeliveryAt).toLocaleString()}` : ''}
                {order.deliveryScheduledAt ? ` · အတည်ပြု ${new Date(order.deliveryScheduledAt).toLocaleString()}` : ''}
              </p>
              <p className="text-xs font-semibold text-slate-700">
                ပို့ဆောင်မှု · {order.deliveryStatus === 'PACKING' ? 'ထုပ်ပိုးနေသည်'
                  : order.deliveryStatus === 'PACKED' ? 'ထုပ်ပိုးပြီး'
                  : order.deliveryStatus === 'HANDED_TO_RIDER' ? 'Rider ထံ အပ်ပြီး'
                  : order.deliveryStatus === 'OUT_FOR_DELIVERY' ? 'လာပို့နေပြီ'
                  : order.deliveryStatus === 'IN_TRANSIT' ? 'လမ်းမှာ'
                  : order.deliveryStatus === 'DELIVERED' ? 'ဆိုင်က ရောက်သည်ဟု မှတ်ထား'
                  : 'မပို့သေး'}
              </p>
              {(order.deliveryMilestones || []).length > 0 && (
                <div className="space-y-1 rounded-lg bg-slate-50 p-2">
                  {[...(order.deliveryMilestones || [])].reverse().slice(0, 8).map((row) => (
                    <p key={row.id || `${row.toStatus}-${row.at}`} className="text-[11px] text-slate-600">
                      <b>{row.toStatus}</b>
                      {row.actor ? ` · ${row.actor}` : ''}
                      {row.at ? ` · ${new Date(row.at).toLocaleString()}` : ''}
                      {row.note ? ` — ${row.note}` : ''}
                    </p>
                  ))}
                </div>
              )}
            </>
          )}
          <CustomerShippingQuote order={order} onUpdated={() => onUpdated()} />
          <OrderReceiptPanel order={order} onUpdated={onUpdated} />
          <OrderPaymentPanel order={order} onUpdated={onUpdated} onReorder={onReorder} />
          <OrderReturnRatePanel order={order} />
        </div>
      )}
    </div>
  );
};

function OrderReceiptPanel({
  order,
  onUpdated,
}: {
  order: CustomerOrder;
  onUpdated: (next?: CustomerOrder) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const receipt = (order.customerReceiptState || 'NONE').toUpperCase();
  const show = order.awaitingCustomerReceipt || receipt === 'CONFIRMED' || receipt === 'NOT_RECEIVED';
  if (!show) return null;
  const send = async (received: boolean) => {
    setBusy(true); setError('');
    try {
      const res = await customerPortalService.confirmReceipt(order.id, received);
      onUpdated(res.data);
    } catch (e: any) {
      setError(e.response?.data?.message || e.message || 'အတည်ပြုမရပါ');
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className={`rounded-xl border p-3 text-sm ${receipt === 'CONFIRMED' ? 'border-emerald-200 bg-emerald-50' : receipt === 'NOT_RECEIVED' ? 'border-rose-200 bg-rose-50' : 'border-amber-200 bg-amber-50'}`}>
      <p className="font-bold">
        {receipt === 'CONFIRMED' ? 'ပစ္စည်း လက်ထဲ ရောက်ကြောင်း အတည်ပြုပြီး' : receipt === 'NOT_RECEIVED' ? 'ပစ္စည်း မရောက်သေးဟု ပြောထားသည်' : 'ပစ္စည်း လက်ထဲ ရောက်ပါသလား?'}
      </p>
      <p className="mt-1 text-xs">
        {receipt === 'CONFIRMED' ? 'ဆိုင်သို့ အသိပေးပြီးပါပြီ။' : 'ဆိုင်က ပို့ပြီးဟု မှတ်ထားနိုင်ပါတယ်။ လက်ထဲ အမှန်တကယ် ရောက်မှသာ အတည်ပြုပါ။'}
      </p>
      {error && <p className="mt-2 text-xs text-rose-700">{error}</p>}
      {receipt !== 'CONFIRMED' && (
        <div className="mt-2 grid grid-cols-2 gap-2">
          <button type="button" disabled={busy} onClick={() => void send(true)} className="rounded-lg bg-emerald-600 py-2 text-xs font-bold text-white disabled:opacity-50">ရောက်ပါပြီ</button>
          <button type="button" disabled={busy} onClick={() => void send(false)} className="rounded-lg border border-rose-300 py-2 text-xs font-bold text-rose-700 disabled:opacity-50">မရောက်သေး</button>
        </div>
      )}
    </div>
  );
}

function OrderPaymentPanel({
  order,
  onUpdated,
  onReorder,
}: {
  order: CustomerOrder;
  onUpdated: (next?: CustomerOrder) => void;
  onReorder?: (order: CustomerOrder) => void;
}) {
  const [channels, setChannels] = useState<CustomerPaymentChannel[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [reference, setReference] = useState('');
  const [proofs, setProofs] = useState<{ deposit?: any; remainder?: any }>({});
  const [amount, setAmount] = useState(String(order.depositAmount ?? order.total ?? ''));
  const [file, setFile] = useState<File | null>(null);
  const pendingChoice = order.paymentChoice === 'PENDING';
  useEffect(() => {
    if (!order.latestProofId && !order.collectionProofId) { setProofs({}); return; }
    customerPortalService.paymentProofs(order.id).then(r => setProofs(r.data || {})).catch(() => setProofs({}));
  }, [order.id, order.latestProofId, order.collectionProofId, order.paymentState]);
    const transfer = !pendingChoice && ((order.paymentChoice || 'TRANSFER') === 'TRANSFER' || Number(order.depositAmount || 0) > 0);
    const canUpload = transfer && ['AWAITING_PAYMENT', 'EXPIRED', 'REJECTED'].includes(order.paymentState || '');
  const invoiceReady = !!order.completedSaleId || !!order.completedSale?.id;
  const receiptReady = ['DEPOSIT_PAID', 'PAID', 'FULFILLED'].includes(order.paymentState || '');
  const deposit = Number(order.depositAmount || 0);
  const remainder = Number(order.remainingAmount || 0);
  const depositConfirmed = ['DEPOSIT_PAID', 'REMAINDER_PROOF_SUBMITTED', 'REMAINDER_CHECKING', 'PAID', 'FULFILLED'].includes(order.paymentState || '');
  const remainderSubmitted = ['REMAINDER_PROOF_SUBMITTED', 'REMAINDER_CHECKING'].includes(order.paymentState || '');
  const remainderConfirmed = ['PAID', 'FULFILLED'].includes(order.paymentState || '') && Number(order.collectionAmount || 0) > 0;
  const paidSoFar = remainderConfirmed ? deposit + Number(order.collectionAmount || 0) : depositConfirmed ? deposit : 0;
  const balanceDue = Math.max(0, Number(order.total || 0) - paidSoFar);

  useEffect(() => {
    if (!transfer) return;
    customerPortalService.paymentChannels()
      .then(r => setChannels(r.data || []))
      .catch(() => setChannels([]));
  }, [transfer, order.id]);

  const choose = async (id: number) => {
    setBusy(true); setError('');
    try {
      const res = await customerPortalService.choosePaymentChannel(order.id, id);
      if (res.data) onUpdated(res.data);
    } catch (e: any) {
      setError(e?.message || 'Channel ရွေးမရပါ');
    } finally { setBusy(false); }
  };

  const choosePay = async (choice: 'TRANSFER' | 'PAY_ON_COLLECTION') => {
    setBusy(true); setError('');
    try {
      const res = await customerPortalService.choosePayment(order.id, choice);
      if (res.data) onUpdated(res.data);
    } catch (e: any) {
      setError(e?.message || 'ငွေပေးချေနည်း ရွေးမရပါ');
    } finally { setBusy(false); }
  };

  const submit = async () => {
    if (!file || !order.paymentMethodId) return;
    setBusy(true); setError('');
    try {
      const fd = new FormData();
      fd.append('image', file);
      fd.append('amount', amount);
      fd.append('paymentMethodId', String(order.paymentMethodId));
      if (reference.trim()) fd.append('reference', reference.trim());
      const res = await customerPortalService.submitPaymentProof(order.id, fd);
      if (res.data) onUpdated(res.data);
      setFile(null);
    } catch (e: any) {
      setError(e?.message || 'အထောက်အထား တင်မရပါ');
    } finally { setBusy(false); }
  };

  const downloadReceipt = async () => {
    setBusy(true); setError('');
    try {
      const blob = await customerPortalService.downloadPaymentReceipt(order.id);
      await openPdfBlob(blob, `payment-receipt-${order.orderNo}.pdf`);
    } catch (e: any) {
      setError(e?.message || 'ငွေလက်ခံပြေစာ မရနိုင်သေးပါ');
    } finally { setBusy(false); }
  };

  const shareReceipt = async () => {
    setBusy(true); setError('');
    try {
      const blob = await customerPortalService.downloadPaymentReceipt(order.id);
      const file = new File([blob], `payment-receipt-${order.orderNo}.pdf`, { type: 'application/pdf' });
      const nav = navigator as Navigator & { share?: (data: ShareData & { files?: File[] }) => Promise<void>; canShare?: (data: { files?: File[] }) => boolean };
      if (nav.share && (!nav.canShare || nav.canShare({ files: [file] }))) {
        await nav.share({ title: `ငွေလက်ခံပြေစာ ${order.orderNo}`, files: [file] });
      } else {
        await openPdfBlob(blob, `payment-receipt-${order.orderNo}.pdf`);
      }
    } catch (e: any) {
      if (e?.name !== 'AbortError') setError(e?.message || 'ငွေလက်ခံပြေစာ မပို့နိုင်ပါ');
    } finally { setBusy(false); }
  };

  const downloadInvoice = async () => {
    setBusy(true); setError('');
    try {
      const blob = await invoiceBlob();
      await openPdfBlob(blob, `invoice-${order.orderNo}.pdf`);
    } catch (e: any) {
      setError(e?.message || 'Invoice မရနိုင်သေးပါ');
    } finally { setBusy(false); }
  };

  const shareInvoice = async () => {
    setBusy(true); setError('');
    try {
      const blob = await invoiceBlob();
      const file = new File([blob], `invoice-${order.orderNo}.pdf`, { type: 'application/pdf' });
      const nav = navigator as Navigator & { share?: (data: ShareData & { files?: File[] }) => Promise<void>; canShare?: (data: { files?: File[] }) => boolean };
      if (nav.share && (!nav.canShare || nav.canShare({ files: [file] }))) {
        await nav.share({ title: `Invoice ${order.orderNo}`, files: [file] });
      } else {
        await openPdfBlob(blob, `invoice-${order.orderNo}.pdf`);
      }
    } catch (e: any) {
      if (e?.name !== 'AbortError') setError(e?.message || 'Invoice မပို့နိုင်ပါ');
    } finally { setBusy(false); }
  };

  const invoiceBlob = async () => {
    const saleId = order.completedSaleId || order.completedSale?.id;
    return saleId && !order.completedSaleId
      ? customerPortalService.downloadPurchaseInvoice(saleId)
      : customerPortalService.downloadOrderInvoice(order.id);
  };

  return (
    <div className="space-y-2 rounded-lg border p-2">
      <p className="text-xs font-bold">ငွေပေးချေမှု — {paymentLabel(order.paymentState, order.paymentChoice, order.shippingState)}</p>
      {deposit > 0 && (
        <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-3 text-xs">
          <p className="font-bold text-slate-800">ငွေပေးချေမှု အကျဉ်းချုပ်</p>
          <div className="flex justify-between rounded-lg bg-emerald-50 p-2 text-emerald-900"><span>စရံ {Number(order.depositPercent || 0)}%</span><b>{money(deposit)} · {depositConfirmed ? 'အတည်ပြုပြီး ✓' : 'ပေးရန်'}</b></div>
          <div className="flex justify-between rounded-lg bg-amber-50 p-2 text-amber-900"><span>ကျန်ငွေ</span><b>{money(remainder)} · {remainderConfirmed ? 'အတည်ပြုပြီး ✓' : remainderSubmitted ? 'လွှဲပုံတင်ပြီး' : 'ပေးရန်ကျန်'}</b></div>
          <div className="flex justify-between border-t pt-2"><span>ပေးပြီးစုစုပေါင်း</span><b>{money(paidSoFar)}</b></div>
          <div className="flex justify-between"><span>ပေးရန်ကျန်</span><b className={balanceDue > 0 ? 'text-amber-700' : 'text-emerald-700'}>{money(balanceDue)}</b></div>
        </div>
      )}
      {pendingChoice && order.shippingState !== 'ACCEPTED' && (
        <p className="flex items-center gap-2 text-xs text-slate-600">
          <Loader2 className="h-3 w-3 animate-spin" /> ဆိုင်က ပို့ချိန် အတည်ပြုရန် စောင့်ဆိုင်းပါ။
        </p>
      )}
      {pendingChoice && order.shippingState === 'ACCEPTED' && (
        <div className="space-y-2 rounded-lg bg-amber-50 p-2">
          <p className="text-xs font-semibold text-amber-900">ပို့ချိန် လက်ခံပြီးပါပြီ။ ငွေပေးချေနည်း ရွေးပါ။</p>
          {(order.deliveryHandler === 'HANDOFF' || order.fullPaymentRequired) && <p className="text-xs">{order.deliveryHandler === 'HANDOFF' ? 'အခြား delivery နဲ့ ပို့မယ့် order ဖြစ်လို့' : 'ဆိုင်က ဒီ order ကို ငွေအပြည့် ကြိုတောင်းထားလို့'} စရံပေးပြီး ကျန်ငွေကို ပစ္စည်းရောက်မှ ရှင်းလို့မရပါ။ ငွေအပြည့်အကြေ ကြိုလွှဲရပါမယ်။</p>}
          <div className="flex flex-wrap gap-1">
            <button type="button" disabled={busy} onClick={() => void choosePay('TRANSFER')} className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white disabled:opacity-40">
              ငွေအပြည့် လွှဲမည်
            </button>
            <button type="button" hidden={order.deliveryHandler === 'HANDOFF' || order.fullPaymentRequired} disabled={busy} onClick={() => void choosePay('PAY_ON_COLLECTION')} className="rounded-lg border border-indigo-600 px-3 py-1.5 text-xs font-bold text-indigo-700 disabled:opacity-40">
              လက်ခံချိန် ရှင်းမည် (စရံကြို)
            </button>
          </div>
        </div>
      )}
      {!pendingChoice && (
        <p className="text-xs text-slate-600">{transfer ? 'ဘဏ် / Wallet ငွေလွှဲ' : 'ပစ္စည်းလက်ခံချိန် ငွေရှင်း'}</p>
      )}
      {(proofs.deposit?.image || proofs.remainder?.image) && (
        <div className="rounded-xl border border-indigo-100 bg-indigo-50/40 p-3">
          <p className="mb-2 text-xs font-bold text-indigo-900">သင်တင်ထားသော ငွေလွှဲပုံများ</p>
          <div className="grid grid-cols-2 gap-2">
            {([['deposit', 'ပထမပုံ · စရံ'], ['remainder', 'ဒုတိယပုံ · ကျန်ငွေ']] as const).map(([key, label]) => {
              const item=proofs[key]; return <div key={key} className="rounded-lg bg-white p-2">
                <p className="mb-1 text-[11px] font-bold">{label}</p>
                {item?.image ? <><img src={item.image} alt={label} className="h-32 w-full rounded object-contain"/><p className="mt-1 text-[10px] text-slate-500">{money(item.amount)} · {item.reference || 'Reference မရှိ'}</p></> : <p className="py-8 text-center text-[11px] text-slate-400">မတင်ရသေးပါ</p>}
              </div>;
            })}
          </div>
        </div>
      )}
      {order.paymentState === 'DEPOSIT_PAID' && Number(order.remainingAmount || 0) > 0 && (
        <p className="rounded-lg bg-emerald-50 p-2 text-xs font-semibold text-emerald-800">
          {order.orderType === 'DELIVERY'
            ? `စရံရရှိပြီး — ဆိုင်က ပို့ပါမည်။ ပစ္စည်းရောက်မှ ကျန် ${money(order.remainingAmount)} ပေးချေပါ။`
            : `စရံရရှိပြီး — ဆိုင်မှာ လက်ခံချိန် ကျန် ${money(order.remainingAmount)} ပေးချေပါ။`}
        </p>
      )}
      {['PROOF_SUBMITTED', 'CHECKING', 'REVIEW', 'LATE_REVIEW'].includes(order.paymentState || '') && (
        <p className="flex items-center gap-2 rounded-lg bg-sky-50 p-2 text-xs font-semibold text-sky-800">
          <Loader2 className="h-4 w-4 animate-spin" /> {order.paymentState === 'LATE_REVIEW'
            ? 'နောက်ကျ အထောက်အထား ရပါပြီ။ ဆိုင်က stock ပြန်စစ်ပြီး ဆက်ရောင်းမလား / ငွေပြန်အမ်းမလား ဆုံးဖြတ်ပါမည်။'
            : 'ဆိုင်မှ ချက်ချင်း အတည်ပြုနေသည် — ခဏစောင့်ပါ။ ငွေလက်ခံပြေစာ မကြာမီ ရပါမည်။'}
        </p>
      )}
      {order.orderType === 'DELIVERY' && order.shippingState === 'ACCEPTED' && (
        <p className="rounded-lg bg-emerald-50 p-2 text-xs font-semibold text-emerald-800">
          {order.deliveryHandler === 'HANDOFF'
            ? `ဆိုင်ပို့ခ ၀ · စုစုပေါင်း ${money(order.total)} — ငွေလွှဲပါ`
            : `ပို့ခ ${money(order.deliveryCharge)} · စုစုပေါင်း ${money(order.total)} — ငွေလွှဲပါ`}
        </p>
      )}
      {transfer && canUpload && (
        <>
          <div className="flex flex-wrap gap-1">
            {channels.map(ch => (
              <button
                key={ch.id}
                type="button"
                disabled={busy}
                onClick={() => void choose(ch.id)}
                className={`rounded-lg border px-2 py-1 text-xs ${order.paymentMethodId === ch.id ? 'border-indigo-500 bg-indigo-50' : ''}`}
              >
                {ch.methodName}
              </button>
            ))}
          </div>
          {(order.payeeName || order.payeeAccountNo || order.paymentInstructions) && (
            <div className="whitespace-pre-wrap rounded-lg bg-slate-50 p-2 text-xs">
              {order.paymentMethodName && <div>Channel: {order.paymentMethodName}</div>}
              {order.payeeName && <div>အကောင့်အမည်: {order.payeeName}</div>}
              {order.payeeAccountNo && <div>အကောင့်နံပါတ်: {order.payeeAccountNo}</div>}
              {order.paymentInstructions}
            </div>
          )}
        </>
      )}
      {order.paymentReviewNote && <p className="text-xs text-slate-500">{order.paymentReviewNote}</p>}
      {order.paymentState === 'EXPIRED' && (
        <div className="space-y-2 rounded-lg bg-rose-50 p-2">
          <p className="text-xs font-semibold text-rose-800">
            Hold အချိန်ကုန်ပါပြီ။ ပစ္စည်းဖယ်ထားမှု ပြန်လွှတ်ပြီးပါပြီ။ ငွေလွှဲပြီးသားဆို အထောက်အထားတင်ပါ — ဆိုင်က stock ပြန်စစ်မည်။ မလွှဲရသေးရင် ပြန်မှာယူပါ။
          </p>
          {onReorder && (
            <button type="button" onClick={() => onReorder(order)} className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white">
              ပြန်မှာယူမည်
            </button>
          )}
        </div>
      )}
      {canUpload && order.paymentMethodId && (
        <div className="space-y-1">
          <input value={reference} onChange={e => setReference(e.target.value)} placeholder="Transaction no (မထည့်လည်းရ)" className="w-full rounded-lg border px-2 py-1 text-xs" />
          <input value={amount} onChange={e => setAmount(e.target.value)} placeholder="လွှဲထားသည့်ငွေ" className="w-full rounded-lg border px-2 py-1 text-xs" />
          <input type="file" accept="image/jpeg,image/png" onChange={e => setFile(e.target.files?.[0] || null)} className="text-xs" />
          <button type="button" disabled={busy || !file || !(Number(amount) > 0)} onClick={() => void submit()} className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white disabled:opacity-40">
            {busy ? <Loader2 className="inline h-3 w-3 animate-spin" /> : 'ပြေစာတင်မည်'}
          </button>
        </div>
      )}
      {receiptReady && (
        <div className="space-y-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3">
          <p className="text-xs font-bold text-emerald-900">
            {order.paymentState === 'DEPOSIT_PAID' ? 'စရံငွေ လက်ခံပြေစာ' : 'ငွေလက်ခံပြေစာ'}
          </p>
          <p className="text-xs text-emerald-800">
            {order.paymentState === 'DEPOSIT_PAID'
              ? 'ဆိုင်က စရံငွေ အတည်ပြုပြီးပါပြီ။ ပြေစာ ဖွင့် / ပို့နိုင်ပါသည်။'
              : order.paymentState === 'FULFILLED'
                ? 'ငွေဝင်မှု အတည်ပြုစာ။ Sale ဘောင်ချာကို အောက်တွင် သီးခြား ဖွင့်နိုင်ပါသည်။'
                : 'ဆိုင်က ငွေဝင်မှု အတည်ပြုပြီးပါပြီ။ Sale ဘောင်ချာသည် ဘောင်ချာထုတ်ပြီးမှ ရပါမည်။'}
          </p>
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={busy} onClick={() => void downloadReceipt()} className="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-40">
              {busy ? <Loader2 className="inline h-3 w-3 animate-spin" /> : 'ပြေစာ ဖွင့်'}
            </button>
            <button type="button" disabled={busy} onClick={() => void shareReceipt()} className="rounded-lg border border-emerald-700 px-3 py-1.5 text-xs font-semibold text-emerald-800 disabled:opacity-40">
              Send
            </button>
          </div>
        </div>
      )}
      {invoiceReady && deposit > 0 && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs">
          <p className="mb-2 font-bold text-emerald-900">Sale ငွေပေးချေမှု</p>
          <div className="flex justify-between py-1"><span>စရံ · {order.paymentMethodName || 'Channel'}</span><b>{money(deposit)} ✓</b></div>
          <div className="flex justify-between py-1"><span>ကျန်ငွေ · {order.collectionPaymentMethodName || 'Channel'}</span><b>{money(order.collectionAmount ?? remainder)} ✓</b></div>
          <div className="mt-1 flex justify-between border-t border-emerald-200 pt-2"><span>ပေးပြီးစုစုပေါင်း</span><b>{money(order.total)}</b></div>
          <div className="flex justify-between text-emerald-800"><span>ပေးရန်ကျန်</span><b>{money(0)} ✓</b></div>
        </div>
      )}
      {invoiceReady && (
        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={busy} onClick={() => void downloadInvoice()} className="rounded-lg border px-3 py-1.5 text-xs font-semibold">
            Invoice ဖွင့်
          </button>
          <button type="button" disabled={busy} onClick={() => void shareInvoice()} className="rounded-lg border px-3 py-1.5 text-xs font-semibold">
            Send
          </button>
        </div>
      )}
      {error && <p role="alert" className="text-xs text-rose-600">{error}</p>}
    </div>
  );
}

function OrderReturnRatePanel({ order }: { order: CustomerOrder }) {
  const saleReady = !!(order.completedSaleId || order.completedSale?.id);
  const receiptOk = (order.customerReceiptState || '').toUpperCase() === 'CONFIRMED';
  const [reason, setReason] = useState('');
  const [qty, setQty] = useState<Record<number, number>>({});
  const [productRating, setProductRating] = useState(order.rating?.productRating || 5);
  const [serviceRating, setServiceRating] = useState(order.rating?.serviceRating || 5);
  const [review, setReview] = useState(order.rating?.comment || order.rating?.review || '');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [returns, setReturns] = useState<any[]>([]);
  useEffect(() => {
    if (!saleReady) return;
    customerPortalService.myReturns(order.id).then((r) => setReturns(r.data || [])).catch(() => setReturns([]));
  }, [order.id, saleReady]);
  const canRate = receiptOk && (order.canRate || order.canEditRating);
  const submitReturn = async () => {
    const lines = (order.lines || [])
      .filter((l) => l.productId && (qty[l.productId] || 0) > 0)
      .map((l) => ({ productId: l.productId as number, qty: qty[l.productId] }));
    if (!reason.trim() || !lines.length) { setMsg('အကြောင်းရင်းနှင့် ပစ္စည်းရွေးပါ'); return; }
    setBusy(true); setMsg('');
    try {
      await customerPortalService.submitReturn(order.id, { reason: reason.trim(), lines, saleId: order.completedSaleId }, files);
      setMsg('ပြန်ပို့ တောင်းဆိုပြီးပါပြီ');
      const r = await customerPortalService.myReturns(order.id);
      setReturns(r.data || []);
    } catch (e: any) {
      setMsg(e?.response?.data?.message || e?.message || 'မတင်နိုင်ပါ');
    } finally { setBusy(false); }
  };
  const submitRate = async () => {
    setBusy(true); setMsg('');
    try {
      await customerPortalService.rateOrder(order.id, {
        productRating,
        serviceRating,
        comment: review.trim() || undefined,
      });
      setMsg(order.canEditRating ? 'အဆင့် ပြင်ပြီးပါပြီ' : 'အဆင့်သတ်မှတ်ပြီးပါပြီ');
    } catch (e: any) {
      setMsg(e?.response?.data?.message || e?.message || 'မတင်နိုင်ပါ');
    } finally { setBusy(false); }
  };
  if (!saleReady && !receiptOk && !order.rating) return null;
  const serviceLabel = (order.orderType || '').toUpperCase() === 'PICKUP' ? 'ဆိုင်ဝန်ဆောင်မှု' : 'ပို့ဆောင်မှု';
  return (
    <div className="space-y-2 rounded-lg border p-2">
      <p className="text-xs font-bold">ပစ္စည်းပြန်ပို့ · အဆင့်သတ်မှတ်</p>
      {saleReady && (
        <>
          <p className="text-[11px] text-slate-500">ငွေစရံပြန်အမ်းနှင့် မရောပါ။ Sale voucher ရှိမှ တောင်းနိုင်သည်။</p>
          {returns.map((r) => (
            <p key={r.id} className="text-[11px]">{r.returnNo} · {r.status} · {r.reason}</p>
          ))}
          {(order.lines || []).map((l) => (
            <label key={l.productId} className="flex items-center justify-between gap-2 text-xs">
              <span>{l.productName}</span>
              <input type="number" min={0} max={l.qty} value={qty[l.productId || 0] || 0} onChange={(e) => setQty({ ...qty, [l.productId || 0]: Number(e.target.value) })} className="w-16 rounded border px-1" />
            </label>
          ))}
          <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="ပြန်ပို့ အကြောင်းရင်း" className="w-full rounded border p-2 text-xs" />
          <input type="file" accept="image/jpeg,image/png" multiple onChange={(e) => setFiles(Array.from(e.target.files || []))} className="text-xs" />
          <button type="button" disabled={busy} onClick={() => void submitReturn()} className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-bold text-white">ပြန်ပို့ တောင်းမည်</button>
        </>
      )}
      {order.rating && (
        <p className="text-[11px] text-slate-600">
          ပစ္စည်း {order.rating.productRating}/5 · {serviceLabel} {order.rating.serviceRating}/5
          {order.rating.comment || order.rating.review ? ` — ${order.rating.comment || order.rating.review}` : ''}
          {order.canEditRating ? ' (ပြင်နိုင်သေးသည်)' : ''}
        </p>
      )}
      {!receiptOk && !order.rating && (
        <p className="text-[11px] text-slate-500">လက်ခံအတည်ပြုပြီးမှ အဆင့်ပေးနိုင်သည်။</p>
      )}
      {canRate && (
        <>
          <div className="flex items-center gap-2 text-xs">
            <span>ပစ္စည်း</span>
            <select value={productRating} onChange={(e) => setProductRating(Number(e.target.value))} className="rounded border px-1">
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
            <span>{serviceLabel}</span>
            <select value={serviceRating} onChange={(e) => setServiceRating(Number(e.target.value))} className="rounded border px-1">
              {[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </div>
          <input value={review} onChange={(e) => setReview(e.target.value)} placeholder="မှတ်ချက် (optional)" className="w-full rounded border p-2 text-xs" />
          <button type="button" disabled={busy} onClick={() => void submitRate()} className="rounded-lg border px-3 py-1.5 text-xs font-bold">
            {order.canEditRating ? 'အဆင့် ပြင်မည်' : 'အဆင့်ပေးမည်'}
          </button>
        </>
      )}
      {msg && <p className="text-xs text-indigo-700">{msg}</p>}
    </div>
  );
}


