import React,{useState} from 'react';
import {api} from '../services/api';

function toLocal(iso?: string | null) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso).slice(0, 16);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function sameSlot(a?: string | null, b?: string) {
  if (!a || !b) return false;
  return toLocal(a) === (b.length >= 16 ? b.slice(0, 16) : b);
}

export default function ShippingQuoteEditor({order,onUpdated}:{order:any;onUpdated:(o:any)=>void}) {
 const [amount,setAmount]=useState(String(order.quotedDeliveryCharge??order.deliveryCharge??''));
 const [handler,setHandler]=useState(order.deliveryHandler||'OWN');
 const [reason,setReason]=useState('');
 const requestedLocal = toLocal(order.requestedDeliveryAt);
 const [scheduledAt,setScheduledAt]=useState(toLocal(order.deliveryScheduledAt || order.requestedDeliveryAt));
 const [busy,setBusy]=useState(false);const [error,setError]=useState('');
 if(order.orderType!=='DELIVERY')return null;
 const handoff = handler==='HANDOFF';
 const canQuote = (order.paymentState||'NONE')==='NONE'
   && ['AWAITING_SHOP','QUOTED','NEEDS_QUOTE'].includes(order.shippingState||'')
   && ['PENDING','CONFIRMED'].includes(order.status);
 const send = async (when: string, note: string) => {
  setBusy(true);setError('');
  try{
    const r=await api.post<any>('/v1/customer-orders/'+order.id+'/shipping-quote',{
      amount:Number(amount),handler,reason:note,version:order.shippingVersion??0,
      scheduledAt: when.length===16 ? when+':00' : when
    });
    onUpdated(r.data);
  } catch(e:any){setError(e.message);} finally{setBusy(false);}
 };
 return <div className="border rounded-xl p-3 space-y-2">
  <b>Customer တောင်းဆိုချိန် — ချက်ချင်း အတည်ပြုပါ</b>
  <p className="text-xs text-slate-600">အဆင်ပြေရင် အတည်ပြုပါ။ မပြေရင် လုပ်ငန်းအဆင်ပြေသည့် အချိန် ရွေးပြီး ပို့ပါ။ Customer က လက်ခံမလား / ညှိနှိုင်းအုံးမလား ရွေးပါမည်။</p>
  {order.requestedDeliveryAt && (
    <p className="text-sm">Customer တောင်းဆိုချိန်: <b>{new Date(order.requestedDeliveryAt).toLocaleString()}</b></p>
  )}
  <p>{order.shippingWeightKg!=null?order.shippingWeightKg+' kg':'အလေးချိန် မပြည့်စုံသေးပါ'} · ပစ္စည်း {order.lines?.reduce((s:number,l:any)=>s+l.qty,0)} ခု</p>
  {canQuote && <>
   <select aria-label="ပို့ဆောင်သူ" className="border p-2 w-full" value={handler} onChange={e=>{
    const next=e.target.value;setHandler(next);if(next==='HANDOFF')setAmount('0');
   }}>
    <option value="OWN">ဆိုင်မှပို့ — ပို့ခ ကောက် / ဘောင်ချာထည့်</option>
    <option value="HANDOFF">အပြင်ပို့ အပ် — ဆိုင်ပို့ခ ၀ / ဘောင်ချာမထည့်</option>
   </select>
   <input aria-label="နောက်ဆုံးပို့ခ" type="number" min="0" step="0.01" className="border p-2 w-full" value={amount} onChange={e=>setAmount(e.target.value)} placeholder="ဆိုင်ကောက်မည့် ပို့ခ (ကျပ်)" disabled={handoff}/>
   <button type="button" disabled={busy||amount===''||!requestedLocal} className="w-full rounded-lg bg-emerald-600 p-2 font-bold text-white disabled:opacity-40" onClick={()=>void send(requestedLocal, reason.trim() || 'တောင်းဆိုချိန် အဆင်ပြေသည်။')}>
     တောင်းဆိုချိန် အဆင်ပြေ — ပို့မည်
   </button>
   <label className="block text-xs font-bold text-slate-600">
     လုပ်ငန်းဘက် အဆင်ပြေသည့် အချိန်
     <input aria-label="ပို့ချိန်" type="datetime-local" className="mt-1 w-full border p-2" value={scheduledAt} onChange={e=>setScheduledAt(e.target.value)}/>
   </label>
   <textarea aria-label="ပို့ခအကြောင်းပြချက်" className="border p-2 w-full" maxLength={1000} value={reason} onChange={e=>setReason(e.target.value)} placeholder="ဥပမာ — ထိုအချိန် ပို့မရ။ မနက် ၁၀ နာရီ ပို့နိုင်သည်။"/>
   <button type="button" disabled={busy||amount===''||!scheduledAt||sameSlot(order.requestedDeliveryAt, scheduledAt)&&!reason.trim()} className="w-full rounded-lg border p-2 font-bold disabled:opacity-40" onClick={()=>void send(scheduledAt, reason.trim() || 'လုပ်ငန်းအဆင်ပြေသည့် ပို့ချိန် ပို့လိုက်ပါသည်။')}>
     Customer ဆီ အဆိုပြုချက်ပို့မည်
   </button>
  </>}
  {order.shippingState==='QUOTED' && (
    <p className="text-xs text-amber-800">Customer လက်ခံမလား / ညှိနှိုင်းအုံးမလား စောင့်ဆိုင်းပါ။</p>
  )}
  {order.shippingState==='ACCEPTED' && (
    <p className="text-xs text-emerald-800">Customer လက်ခံပြီး — ပို့ခပြပြီး ငွေလွှဲနေသည်။</p>
  )}
  {error&&<p role="alert">{error}</p>}
 </div>;
}
