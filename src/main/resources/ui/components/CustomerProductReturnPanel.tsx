import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import Swal from 'sweetalert2';

export type ProductReturn = {
  id: number;
  returnNo: string;
  orderId?: number;
  orderNo?: string;
  saleId?: number;
  customerName?: string;
  status: string;
  deliveryStatus?: string;
  deliveryNote?: string;
  inventoryApplied?: boolean;
  accountingPosted?: boolean;
  reason?: string;
  customerNote?: string;
  reviewNote?: string;
  inspectNote?: string;
  refundAmount?: number;
  refundReference?: string;
  lines?: {
    id: number;
    productId: number;
    productName: string;
    qty: number;
    unitPrice: number;
    subtotal: number;
    serialNumber?: string;
    disposition?: string;
    replacementProductId?: number;
    replacementSerialNumber?: string;
  }[];
  photos?: { id: number; imageType?: string; image?: string }[];
};

type Method = { id: number; methodName?: string };

const statusLabel: Record<string, string> = {
  REQUESTED: 'တောင်းဆို',
  APPROVED: 'လက်ခံ',
  REJECTED: 'ငြင်းပယ်',
  RETURNED: 'ပစ္စည်းပြန်ရ',
  INSPECTING: 'စစ်ဆေးဆဲ',
  REFUNDED: 'ငွေပြန်အမ်း',
  REPLACED: 'အစားထိုး',
  CLOSED: 'ပိတ်',
};

const deliveryNext: Record<string, string> = {
  PICKUP_REQUESTED: 'PICKED_UP',
  PICKED_UP: 'RETURN_IN_TRANSIT',
  RETURN_IN_TRANSIT: 'RECEIVED_BY_SHOP',
};

export const CustomerProductReturnPanel: React.FC<{
  rows: ProductReturn[];
  onReload: () => Promise<void>;
}> = ({ rows, onReload }) => {
  const [selected, setSelected] = useState<ProductReturn | null>(null);
  const [methods, setMethods] = useState<Method[]>([]);
  const [note, setNote] = useState('');
  const [outcome, setOutcome] = useState<'REFUNDED' | 'REPLACED' | 'CLOSED'>('REFUNDED');
  const [refundAmount, setRefundAmount] = useState('');
  const [methodId, setMethodId] = useState<number | ''>('');
  const [reference, setReference] = useState('');
  const [disp, setDisp] = useState<Record<number, string>>({});
  const [replacementSerial, setReplacementSerial] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState<'ACTIVE' | 'ALL' | 'DONE'>('ACTIVE');
  const [query, setQuery] = useState('');
  const activeStatuses = ['REQUESTED', 'APPROVED', 'RETURNED', 'INSPECTING'];
  const visibleRows = rows.filter((r) => {
    const q = query.trim().toLowerCase();
    const matches = !q || [r.returnNo, r.orderNo, r.customerName].some((v) => (v || '').toLowerCase().includes(q));
    return matches && (filter === 'ALL' || (filter === 'ACTIVE' ? activeStatuses.includes(r.status) : !activeStatuses.includes(r.status)));
  });

  useEffect(() => {
    api.get<any>('/v1/payment-methods/active').then((r) => setMethods(r.data || [])).catch(() => setMethods([]));
  }, []);

  const open = async (id: number) => {
    const res = await api.get<any>(`/v1/customer-order-returns/${id}?photos=true`);
    const row = res.data as ProductReturn;
    setSelected(row);
    setNote('');
    setOutcome('REFUNDED');
    const max = (row.lines || []).reduce((s, l) => s + Number(l.subtotal || 0), 0);
    setRefundAmount(String(max));
    setMethodId(methods[0]?.id || '');
    setReference('');
    const next: Record<number, string> = {};
    (row.lines || []).forEach((l) => { next[l.id] = l.disposition || 'SELLABLE'; });
    setDisp(next);
    const replacement: Record<number, string> = {};
    (row.lines || []).forEach((l) => { replacement[l.id] = l.replacementSerialNumber || ''; });
    setReplacementSerial(replacement);
  };

  const review = async (action: string, extra?: Record<string, unknown>) => {
    if (!selected) return;
    setBusy(true);
    try {
      await api.post(`/v1/customer-order-returns/${selected.id}/review`, { action, note, ...extra });
      await onReload();
      await open(selected.id);
      Swal.fire({ icon: 'success', title: 'သိမ်းပြီး', toast: true, timer: 1400, showConfirmButton: false, position: 'top-end' });
    } catch (e: any) {
      Swal.fire('မရပါ', e?.response?.data?.message || e?.message || 'Update failed', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="rounded-xl border bg-white p-4">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div><h3 className="font-black text-slate-900">Product return workflow</h3><p className="mt-1 text-xs text-slate-500">Review request → Return delivery → Shop receipt → Inspection → Refund or replacement</p></div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search return, order or customer" className="min-w-64 rounded-lg border px-3 py-2 text-sm" />
            <div className="flex rounded-lg bg-slate-100 p-1">
              {([['ACTIVE','Action needed'],['ALL','All'],['DONE','Completed']] as const).map(([id,label]) => (
                <button key={id} type="button" onClick={() => setFilter(id)} className={`rounded-md px-3 py-1.5 text-xs font-bold ${filter === id ? 'bg-white text-indigo-700 shadow' : 'text-slate-500'}`}>{label}</button>
              ))}
            </div>
          </div>
        </div>
      </div>
      <div className="grid gap-4 xl:grid-cols-[380px_minmax(0,1fr)]">
      <div className="max-h-[72vh] overflow-auto bg-white border rounded-xl">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2 text-left">Return</th>
              <th className="px-3 py-2 text-left">Customer</th>
              <th className="px-3 py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {visibleRows.length === 0 ? (
              <tr><td colSpan={3} className="py-10 text-center text-slate-400">ပြန်ပို့ တောင်းဆိုချက် မရှိသေးပါ</td></tr>
            ) : visibleRows.map((r) => (
              <tr key={r.id} className={`border-t cursor-pointer hover:bg-indigo-50/60 ${selected?.id === r.id ? 'bg-indigo-100 ring-2 ring-inset ring-indigo-400' : ''}`} onClick={() => void open(r.id)}>
                <td className="px-3 py-2 font-semibold text-indigo-700">{r.returnNo}<div className="text-[10px] text-slate-500">{r.orderNo || `Sale #${r.saleId}`}</div></td>
                <td className="px-3 py-2">{r.customerName}</td>
                <td className="px-3 py-2 text-center text-[11px] font-bold">{statusLabel[r.status] || r.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="min-w-0 bg-white border rounded-xl p-5 space-y-4">
        {!selected ? <p className="text-sm text-slate-400">ဘယ်ဘက်မှ return ရွေးပါ။ ငွေပြန်အမ်း (payment refund) နှင့် သီးခြားဖြစ်သည်။</p> : (
          <>
            <div className="flex justify-between gap-2">
              <div>
                <h3 className="font-black">{selected.returnNo} · {statusLabel[selected.status] || selected.status}</h3>
                <p className="text-xs text-slate-500">{selected.customerName} · {selected.orderNo || `Sale #${selected.saleId}`}</p>
              </div>
              <button type="button" className="text-xs" onClick={() => setSelected(null)}>ပိတ်</button>
            </div>
            <p className="text-sm"><b>အကြောင်းရင်း:</b> {selected.reason}</p>
            {selected.customerNote && <p className="text-xs text-slate-600">{selected.customerNote}</p>}
            <div className="rounded-lg bg-indigo-50 p-2 text-xs">
              Return delivery: <b>{selected.deliveryStatus || 'PICKUP_REQUESTED'}</b>
              {selected.deliveryNote ? <span> · {selected.deliveryNote}</span> : null}
            </div>
            {(selected.lines || []).map((l) => (
              <div key={l.id} className="rounded-lg border p-2 text-xs flex justify-between gap-2">
                <div>
                  <b>{l.productName}</b> × {l.qty}
                  {l.serialNumber ? ` · ${l.serialNumber}` : ''}
                  <div>{Number(l.subtotal).toLocaleString()} Ks</div>
                </div>
                {['RETURNED', 'INSPECTING'].includes(selected.status) && (
                  <select value={disp[l.id] || 'SELLABLE'} onChange={(e) => setDisp({ ...disp, [l.id]: e.target.value })} className="rounded border px-1">
                    <option value="SELLABLE">Sellable / return to stock</option>
                    <option value="DAMAGED">Damaged</option>
                    <option value="QUARANTINE">Quarantine / inspect separately</option>
                  </select>
                )}
                {l.disposition && !['RETURNED', 'INSPECTING'].includes(selected.status) && <span>{l.disposition}</span>}
                {selected.status === 'INSPECTING' && outcome === 'REPLACED' && (
                  <input value={replacementSerial[l.id] || ''}
                    onChange={(e) => setReplacementSerial({ ...replacementSerial, [l.id]: e.target.value })}
                    className="rounded border px-2 py-1"
                    placeholder={l.serialNumber ? 'Replacement serial' : 'Non-serial replacement'}
                    disabled={!l.serialNumber} />
                )}
              </div>
            ))}
            <div className="flex flex-wrap gap-2">
              {(selected.photos || []).map((p) => p.image ? <img key={p.id} src={p.image} alt="" className="h-20 w-20 rounded object-cover border" /> : null)}
            </div>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Decision, delivery or inspection note" className="w-full rounded-lg border p-2 text-sm" rows={2} />
            {selected.status === 'REQUESTED' && (
              <div className="flex gap-2">
                <button disabled={busy} type="button" onClick={() => void review('APPROVE')} className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-bold text-white">လက်ခံ</button>
                <button disabled={busy} type="button" onClick={() => void review('REJECT')} className="rounded-lg bg-rose-600 px-3 py-2 text-xs font-bold text-white">ငြင်းပယ်</button>
              </div>
            )}
            {selected.status === 'APPROVED' && deliveryNext[selected.deliveryStatus || 'PICKUP_REQUESTED'] && (
              <button disabled={busy} type="button" onClick={() => void review('DELIVERY', {
                deliveryStatus: deliveryNext[selected.deliveryStatus || 'PICKUP_REQUESTED'],
              })} className="rounded-lg border border-indigo-600 px-3 py-2 text-xs font-bold text-indigo-700">
                {deliveryNext[selected.deliveryStatus || 'PICKUP_REQUESTED']}
              </button>
            )}
            {selected.status === 'APPROVED' && selected.deliveryStatus === 'RECEIVED_BY_SHOP' && (
              <button disabled={busy} type="button" onClick={() => void review('RECEIVED')} className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-bold text-white">ပစ္စည်း ပြန်လက်ခံပြီး</button>
            )}
            {selected.status === 'RETURNED' && (
              <button disabled={busy} type="button" onClick={() => void review('INSPECT')} className="rounded-lg border px-3 py-2 text-xs font-bold">စစ်ဆေးမည်</button>
            )}
            {selected.status === 'INSPECTING' && (
              <div className="space-y-2 rounded-lg bg-slate-50 p-2">
                <div className="flex flex-wrap gap-2">
                  {(['REFUNDED', 'REPLACED', 'CLOSED'] as const).map((o) => (
                    <button key={o} type="button" onClick={() => setOutcome(o)} className={`rounded-full px-3 py-1 text-[11px] font-bold border ${outcome === o ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white'}`}>{o}</button>
                  ))}
                </div>
                {outcome === 'REFUNDED' && (
                  <div className="grid gap-2 sm:grid-cols-3">
                    <input value={refundAmount} onChange={(e) => setRefundAmount(e.target.value)} className="rounded border p-2 text-sm" placeholder="Refund amount" />
                    <select value={methodId} onChange={(e) => setMethodId(e.target.value ? Number(e.target.value) : '')} className="rounded border p-2 text-sm">
                      <option value="">Select refund channel</option>
                      {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                    </select>
                    <input value={reference} onChange={(e) => setReference(e.target.value)} className="rounded border p-2 text-sm" placeholder="Transaction reference" />
                  </div>
                )}
                <button
                  disabled={busy}
                  type="button"
                  onClick={() => void review('COMPLETE', {
                    outcome,
                    refundAmount: outcome === 'REFUNDED' ? Number(refundAmount) : undefined,
                    paymentMethodId: outcome === 'REFUNDED' ? methodId || undefined : undefined,
                    transactionNo: outcome === 'REFUNDED' ? reference : undefined,
                    lines: (selected.lines || []).map((l) => ({
                      id: l.id,
                      disposition: disp[l.id] || 'SELLABLE',
                      replacementProductId: outcome === 'REPLACED' ? l.productId : undefined,
                      replacementSerialNumber: outcome === 'REPLACED' && l.serialNumber
                        ? replacementSerial[l.id] : undefined,
                    })),
                  })}
                  className="rounded-lg bg-slate-900 px-3 py-2 text-xs font-bold text-white"
                >
                  အပြီးသတ်
                </button>
              </div>
            )}
          </>
        )}
      </div>
      </div>
    </div>
  );
};
