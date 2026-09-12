import React from 'react';

export default function ServiceJobFinalCheckPanel({ snapshot, lead, canAssign, busy, leadCheck, approve, returnWork }: any) {
  const checked = !!snapshot.leadFinalCheckStatus;
  const mayLeadCheck = snapshot.canComplete && !checked && (lead?.mine || canAssign);
  const pendingSupervisor = checked && snapshot.supervisorApprovalRequired !== false && !snapshot.finalApprovalStatus;
  return <section className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-4">
    <h3 className="font-black text-emerald-950">Final Completion</h3>
    <div className="mt-3 grid gap-2 text-sm">
      <Step ok={snapshot.canComplete} text="Member/Helper နှင့် Lead Assignment အားလုံး Completed" />
      <Step ok={checked} text="Lead Technician Final Check" />
      {snapshot.supervisorApprovalRequired !== false && <Step ok={snapshot.finalApprovalStatus} text={pendingSupervisor ? 'Supervisor အတည်ပြုချက်စောင့်နေ' : 'Supervisor Approval'} />}
    </div>
    {snapshot.finalReturnReason && !checked && <p className="mt-3 rounded-xl bg-rose-50 px-3 py-2 text-xs font-bold text-rose-700">ပြန်ပြင်ရန်: {snapshot.finalReturnReason}</p>}
    {checked && <p className="mt-3 text-xs text-emerald-800">{snapshot.leadFinalCheckedBy || 'Lead Technician'} · {snapshot.leadFinalCheckNote || 'Final check completed'}</p>}
    <div className="mt-4 flex flex-wrap gap-2">
      {mayLeadCheck && <button disabled={busy} onClick={leadCheck} className="min-h-10 rounded-xl bg-emerald-600 px-4 text-xs font-bold text-white disabled:opacity-50">Lead Final Check တင်မည်</button>}
      {pendingSupervisor && canAssign && <><button disabled={busy} onClick={approve} className="min-h-10 rounded-xl bg-indigo-600 px-4 text-xs font-bold text-white disabled:opacity-50">Supervisor အတည်ပြုမည်</button><button disabled={busy} onClick={returnWork} className="min-h-10 rounded-xl border border-rose-300 bg-white px-4 text-xs font-bold text-rose-700 disabled:opacity-50">ပြန်ပြင်ရန်</button></>}
    </div>
  </section>;
}

function Step({ ok, text }: any) {
  return <div className="flex items-center gap-2"><span className={`flex h-6 w-6 items-center justify-center rounded-full font-black ${ok ? 'bg-emerald-600 text-white' : 'bg-white text-slate-400'}`}>{ok ? '✓' : '·'}</span><span className={ok ? 'font-bold text-emerald-800' : 'text-slate-600'}>{text}</span></div>;
}
