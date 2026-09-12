import React, { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { customerPortalService } from '../../services/customerPortalApi';

export type { DeliverySelection } from './CheckoutFlow';

export function CustomerShippingQuote({ order, onUpdated }: { order: any; onUpdated: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (order.orderType !== 'DELIVERY' || !order.shippingState || order.shippingState === 'LEGACY') return null;
  const awaiting = order.shippingState === 'AWAITING_SHOP' || order.shippingState === 'NEEDS_QUOTE';
  const quoted = order.shippingState === 'QUOTED';
  const handoff = order.deliveryHandler === 'HANDOFF';
  const own = order.deliveryHandler === 'OWN';
  const when = (iso?: string) => iso ? new Date(iso).toLocaleString() : '';
  const timeChanged = order.requestedDeliveryAt && order.deliveryScheduledAt
    && new Date(order.requestedDeliveryAt).getTime() !== new Date(order.deliveryScheduledAt).getTime();
  return (
    <div className="my-2 space-y-2 rounded border p-3 text-sm">
      {awaiting && (
        <div className="flex items-center gap-2 font-bold">
          <Loader2 className="h-4 w-4 animate-spin" />
          ဆိုင်မှ ပို့ချိန် အတည်ပြုရန် စောင့်ဆိုင်းပါ
        </div>
      )}
      {!awaiting && (
        <b>
          {order.shippingState === 'ACCEPTED'
            ? (handoff ? 'အပြင်ပို့ အပ်မည် — ဆိုင်ပို့ခ မကောက်' : 'ပို့ချိန် / ပို့ခ လက်ခံပြီး')
            : timeChanged
              ? 'ဆိုင်က လုပ်ငန်းအဆင်ပြေသည့် အချိန် ပို့ထားသည် — လက်ခံမလား ညှိနှိုင်းအုံးမလား'
              : 'ဆိုင်က တောင်းဆိုချိန် အဆင်ပြေသည် — လက်ခံမလား'}
        </b>
      )}
      {order.requestedDeliveryAt && (
        <p className="text-xs">သင်တောင်းဆိုချိန်: {when(order.requestedDeliveryAt)}</p>
      )}
      {order.deliveryScheduledAt && quoted && (
        <p className="text-xs">ဆိုင်ပို့ချိန်: <b>{when(order.deliveryScheduledAt)}</b></p>
      )}
      {handoff && quoted && (
        <p className="rounded bg-amber-50 p-2 text-xs text-amber-900">
          ဆိုင်က အပြင်ပို့ဆောင်သူသို့ အပ်မည်။ ဆိုင်ဘောင်ချာတွင် ပို့ခ မပါပါ။
        </p>
      )}
      {quoted && (
        <p>
          {own ? 'ဆိုင်ကပို့မည် · ' : ''}
          ပို့ခ {Number(order.deliveryCharge || 0).toLocaleString()} Ks · စုစုပေါင်း {Number(order.total || 0).toLocaleString()} Ks
        </p>
      )}
      {order.shippingState === 'ACCEPTED' && (
        <p>
          ပို့ခ {Number(order.deliveryCharge || 0).toLocaleString()} Ks · စုစုပေါင်း {Number(order.total || 0).toLocaleString()} Ks — ငွေလွှဲပါ
        </p>
      )}
      {order.shippingWeightKg != null && <p>စုစုပေါင်း {order.shippingWeightKg} kg</p>}
      <p>{order.shippingReason}</p>
      {quoted && ['PENDING', 'CONFIRMED'].includes(order.status) && [true, false].map(accept => (
        <button
          key={String(accept)}
          type="button"
          disabled={busy}
          className="mr-2 rounded border p-2"
          onClick={async () => {
            setBusy(true);
            try {
              await customerPortalService.decideShipping(order.id, { version: order.shippingVersion, accept });
              onUpdated();
            } catch (e: any) {
              setError(e.message);
            } finally {
              setBusy(false);
            }
          }}
        >
          {accept ? 'လက်ခံမည်' : 'ညှိနှိုင်းအုံးမည်'}
        </button>
      ))}
      {busy && <p className="flex items-center gap-2 text-xs"><Loader2 className="h-3 w-3 animate-spin" /> ခဏစောင့်ပါ…</p>}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
