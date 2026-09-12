import React from 'react';

const field = 'min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-base outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-100 sm:text-sm';

function normalizeHandoverStatus(status: any) {
  return String(status || '').toUpperCase();
}

function canActOnHandover(item: any, canAssign: boolean, myStaffId?: string | number) {
  if (canAssign) return true;
  if (item.targetMine) return true;
  if (myStaffId != null && myStaffId !== '' && item.toStaffId != null) return String(item.toStaffId) === String(myStaffId);
  return false;
}

function pendingForMe(handovers: any[] | undefined, myPendingHandovers: any[] | undefined, canAssign: boolean, myStaffId?: string | number) {
  if (myPendingHandovers?.length) return myPendingHandovers;
  return (handovers || []).filter((item: any) => normalizeHandoverStatus(item.status) === 'PENDING' && canActOnHandover(item, canAssign, myStaffId));
}

export default function ServiceJobHandoverPanel(props: any) {
  return <><PendingHandoverBanner {...props} /><HandoverActions {...props} /><HandoverRequest {...props} /><HandoverHistory {...props} /></>;
}

function PendingHandoverBanner({ handovers, myPendingHandovers, canAssign, myStaffId, busy, accept, reject }: any) {
  const pending = pendingForMe(handovers, myPendingHandovers, canAssign, myStaffId);
  if (!pending.length) return null;
  return (
    <section className="rounded-2xl border-2 border-amber-300 bg-amber-50 p-4 shadow-sm">
      <h3 className="font-black text-amber-950">Hand Over လက်ခံရန် စောင့်နေသည်</h3>
      <p className="mt-1 text-xs text-amber-800">Technician Assignment panel အောက်သို့ scroll မလုပ်ဘဲ ဒီမှာ တိုက်ရိုက် လက်ခံ/ငြင်းပယ် လုပ်နိုင်ပါသည်။</p>
      <div className="mt-4 space-y-3">{pending.map((item: any) =>
        <div key={item.id} className="rounded-2xl border border-amber-200 bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div><b>{item.fromStaffName} → {item.toStaffName}</b><p className="mt-1 text-xs text-slate-500">{item.role}{item.requestedAt ? ` · ${new Date(item.requestedAt).toLocaleString()}` : ''}</p></div>
            <HandoverBadge status={item.status} />
          </div>
          {item.completedWork && <p className="mt-3 text-sm text-slate-700"><b>လုပ်ပြီးသောအလုပ်:</b> {item.completedWork}</p>}
          <p className="mt-2 text-sm text-slate-700"><b>ကျန်ရှိသောအလုပ်:</b> {item.remainingWork}</p>
          {item.diagnosisNote && <p className="mt-2 text-sm text-slate-700"><b>Diagnosis / မှတ်ချက်:</b> {item.diagnosisNote}</p>}
          <div className="mt-3 flex gap-2">
            <button disabled={busy} onClick={() => accept(item)} className="min-h-10 rounded-lg bg-emerald-600 px-4 text-xs font-bold text-white disabled:opacity-50">Hand Over လက်ခံ</button>
            <button disabled={busy} onClick={() => reject(item)} className="min-h-10 rounded-lg border border-rose-300 px-4 text-xs font-bold text-rose-700 disabled:opacity-50">ငြင်းပယ်</button>
          </div>
        </div>)}</div>
    </section>
  );
}

function HandoverActions(p: any) {
  const rows = p.current.filter((a: any) => (a.mine || p.canAssign) && ['ACTIVE', 'PAUSED'].includes(a.status));
  if (!rows.length) return null;
  return <section className="rounded-2xl border border-purple-200 bg-purple-50 p-4"><h3 className="font-black">Service Hand Over</h3><div className="mt-3 flex flex-wrap gap-2">{rows.map((a: any) => <HandoverButton key={a.id} item={a} {...p} />)}</div></section>;
}

function HandoverButton({ item, setDraft, busy }: any) {
  const open = () => setDraft({
    fromAssignmentId: item.id,
    toStaffId: '',
    completedWork: '',
    remainingWork: item.taskDescription || '',
    diagnosisNote: '',
  });
  return <button disabled={busy} onClick={open} className="min-h-10 rounded-xl bg-purple-700 px-4 text-xs font-bold text-white disabled:opacity-50">
    {item.staffName} — Hand Over
  </button>;
}

function HandoverRequest({ draft, setDraft, available, send, busy }: any) {
  if (!draft) return null;
  const update = (name: string, value: string) => setDraft({ ...draft, [name]: value });
  return <section className="rounded-2xl border-2 border-purple-300 bg-white p-4 shadow-sm">
    <div className="flex items-center justify-between gap-3"><div><h3 className="font-black text-purple-950">Hand Over Request</h3><p className="mt-1 text-xs text-slate-500">လက်ခံမည့် Technician နှင့် ကျန်ရှိသောအလုပ်ကို ဖြည့်ပါ။</p></div><button onClick={() => setDraft(null)} aria-label="Hand Over form ပိတ်ရန်" className="h-10 w-10 rounded-full text-xl text-slate-500 hover:bg-slate-100">×</button></div>
    <div className="mt-4 grid gap-3 md:grid-cols-2">
      <select className={field} value={draft.toStaffId} onChange={e => update('toStaffId', e.target.value)}><option value="">လက်ခံမည့် Technician *</option>{available.map((person: any) => <option key={person.id} value={person.id}>{person.name}{person.role ? ` (${person.role})` : ''}</option>)}</select>
      <input className={field} value={draft.completedWork} onChange={e => update('completedWork', e.target.value)} placeholder="လုပ်ပြီးသောအလုပ်" />
      <textarea className={`${field} min-h-24 md:col-span-2`} value={draft.remainingWork} onChange={e => update('remainingWork', e.target.value)} placeholder="ကျန်ရှိသောအလုပ် *" />
      <textarea className={`${field} min-h-20 md:col-span-2`} value={draft.diagnosisNote} onChange={e => update('diagnosisNote', e.target.value)} placeholder="Diagnosis / မှတ်ချက်" />
    </div>
    <div className="mt-4 flex justify-end gap-2"><button onClick={() => setDraft(null)} className="min-h-11 rounded-xl border px-4 text-sm font-bold text-slate-600">မလုပ်တော့ပါ</button><button disabled={busy} onClick={send} className="min-h-11 rounded-xl bg-purple-700 px-5 text-sm font-black text-white disabled:opacity-50">Hand Over ပို့မည်</button></div>
  </section>;
}

function HandoverHistory({ handovers, myPendingHandovers, canAssign, myStaffId, busy, accept, reject }: any) {
  if (!handovers?.length) return null;
  const pendingOthers = (handovers || []).filter((item: any) =>
    normalizeHandoverStatus(item.status) === 'PENDING' && !pendingForMe([item], undefined, canAssign, myStaffId).length
  );
  return <section>
    {pendingOthers.length > 0 && (
      <div className="mb-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
        Hand Over request {pendingOthers.length} ခု ရှိသော်လည်း အခြား Technician အတွက် ဖြစ်နေပါသည်။
      </div>
    )}
    <h3 className="mb-3 font-black">Hand Over History</h3><div className="space-y-3">{handovers.map((item: any) =>
    <div key={item.id} className="rounded-2xl border bg-white p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2"><div><b>{item.fromStaffName} → {item.toStaffName}</b><p className="mt-1 text-xs text-slate-500">{item.role}{item.requestedAt ? ` · ${new Date(item.requestedAt).toLocaleString()}` : ''}</p></div><HandoverBadge status={item.status} /></div>
      {item.completedWork && <p className="mt-3 text-sm text-slate-700"><b>လုပ်ပြီးသောအလုပ်:</b> {item.completedWork}</p>}
      <p className="mt-2 text-sm text-slate-700"><b>ကျန်ရှိသောအလုပ်:</b> {item.remainingWork}</p>
      {item.diagnosisNote && <p className="mt-2 text-sm text-slate-700"><b>Diagnosis / မှတ်ချက်:</b> {item.diagnosisNote}</p>}
      {item.rejectionReason && <p className="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-xs font-bold text-rose-700">ငြင်းပယ်ရသည့်အကြောင်း: {item.rejectionReason}</p>}
      {normalizeHandoverStatus(item.status) === 'PENDING' && canActOnHandover(item, canAssign, myStaffId) && <div className="mt-3 flex gap-2"><button disabled={busy} onClick={() => accept(item)} className="min-h-9 rounded-lg bg-emerald-600 px-3 text-xs font-bold text-white disabled:opacity-50">လက်ခံ</button><button disabled={busy} onClick={() => reject(item)} className="min-h-9 rounded-lg border border-rose-300 px-3 text-xs font-bold text-rose-700 disabled:opacity-50">ငြင်းပယ်</button></div>}
    </div>)}</div></section>;
}

function HandoverBadge({ status }: any) {
  const color = status === 'ACCEPTED' ? 'bg-emerald-100 text-emerald-800' : status === 'REJECTED' ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800';
  const text = status === 'ACCEPTED' ? 'လက်ခံပြီး' : status === 'REJECTED' ? 'ငြင်းပယ်ပြီး' : 'စောင့်ဆိုင်းနေ';
  return <span className={`rounded-full px-2.5 py-1 text-[10px] font-black ${color}`}>{text}</span>;
}
