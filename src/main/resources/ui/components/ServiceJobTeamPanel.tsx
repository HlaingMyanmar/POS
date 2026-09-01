import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { serviceJobTeamService } from '../services/api';

type Assignment = {
  id: number; staffId: number; staffName: string; role: 'LEAD' | 'MEMBER' | 'HELPER';
  status: string; approvalStatus?: string; taskDescription?: string; completionNote?: string; assignedBy?: string;
  assignedAt?: string; workStartedAt?: string; accumulatedMinutes?: number; mine: boolean; logs?: any[];
};
type Handover = {
  id: number; fromAssignmentId: number; fromStaffName: string; toStaffId: number; toStaffName: string;
  role: string; completedWork?: string; remainingWork?: string; diagnosisNote?: string;
  status: string; requestedAt?: string; rejectionReason?: string; targetMine: boolean;
};
type Snapshot = { serviceJobId: number; jobNo: string; canComplete: boolean; completionBlockReason?: string; assignments: Assignment[]; handovers: Handover[] };

const currentStatuses = new Set(['PENDING', 'ACTIVE', 'PAUSED', 'COMPLETED']);
const statusStyle: Record<string, string> = {
  PENDING: 'bg-amber-100 text-amber-800', ACTIVE: 'bg-emerald-100 text-emerald-800',
  PAUSED: 'bg-blue-100 text-blue-800', COMPLETED: 'bg-indigo-100 text-indigo-800',
  HANDED_OVER: 'bg-purple-100 text-purple-800', REJECTED: 'bg-rose-100 text-rose-800',
  CANCELED: 'bg-slate-100 text-slate-600', ACCEPTED: 'bg-emerald-100 text-emerald-800',
};
const roleLabel: Record<string, string> = { LEAD: 'Lead', MEMBER: 'Team Member', HELPER: 'Helper' };
const input = 'w-full rounded-xl border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500';

export default function ServiceJobTeamPanel({ job, staff, canAssign, onClose, onChanged }:
  { job: any; staff: any[]; canAssign: boolean; onClose: () => void; onChanged?: () => void }) {
  const [data, setData] = useState<Snapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ staffId: '', role: 'MEMBER', taskDescription: '' });
  const [handover, setHandover] = useState({ fromAssignmentId: 0, toStaffId: '', completedWork: '', remainingWork: '', diagnosisNote: '' });

  const load = async () => {
    try { const res = await serviceJobTeamService.getTeam(job.id); setData(res.data); }
    catch (e: any) { void Swal.fire('မရပါ', e?.response?.data?.message || e?.message || 'Team data မရပါ', 'error'); }
  };
  useEffect(() => { void load(); }, [job.id]);
  const current = useMemo(() => (data?.assignments || []).filter(a => currentStatuses.has(a.status)), [data]);
  const history = useMemo(() => (data?.assignments || []).filter(a => !currentStatuses.has(a.status)), [data]);
  const act = async (fn: () => Promise<any>, success?: string) => {
    setBusy(true);
    try { await fn(); await load(); onChanged?.(); if (success) void Swal.fire({ icon: 'success', title: success, timer: 1100, showConfirmButton: false }); }
    catch (e: any) { void Swal.fire('မလုပ်နိုင်ပါ', e?.response?.data?.message || e?.message || 'လုပ်ဆောင်မှုမအောင်မြင်ပါ', 'error'); }
    finally { setBusy(false); }
  };
  const promptReason = async (title: string, required = false) => {
    const result = await Swal.fire({ title, input: 'textarea', inputPlaceholder: 'အကြောင်းပြချက် / မှတ်ချက်', showCancelButton: true,
      confirmButtonText: 'အတည်ပြု', cancelButtonText: 'မလုပ်တော့ပါ', inputValidator: required ? value => value?.trim() ? undefined : 'အချက်အလက်ဖြည့်ပါ' : undefined });
    return result.isConfirmed ? String(result.value || '') : null;
  };
  const add = () => act(async () => {
    if (!form.staffId) throw new Error('Technician ရွေးပါ');
    await serviceJobTeamService.assign(job.id, { ...form, staffId: Number(form.staffId) });
    setForm({ staffId: '', role: 'MEMBER', taskDescription: '' });
  }, 'Technician တာဝန်ပေးပြီးပါပြီ');
  const work = async (a: Assignment, action: string, needsNote = false) => {
    const note = needsNote ? await promptReason(action === 'COMPLETE' ? 'ပြီးစီးမှုမှတ်ချက်' : 'Work Note', true) : '';
    if (needsNote && note == null) return;
    await act(() => serviceJobTeamService.recordWork(job.id, a.id, action, note || undefined));
  };
  const openHandover = (a: Assignment) => setHandover({ fromAssignmentId: a.id, toStaffId: '', completedWork: '', remainingWork: a.taskDescription || '', diagnosisNote: '' });
  const sendHandover = () => act(async () => {
    if (!handover.toStaffId || !handover.remainingWork.trim()) throw new Error('လက်ခံမည့် Technician နှင့် ကျန်အလုပ်ဖြည့်ပါ');
    await serviceJobTeamService.requestHandover(job.id, { ...handover, toStaffId: Number(handover.toStaffId) });
    setHandover({ fromAssignmentId: 0, toStaffId: '', completedWork: '', remainingWork: '', diagnosisNote: '' });
  }, 'Hand Over တောင်းဆိုပြီးပါပြီ');

  return <div className="fixed inset-0 z-[70] flex items-stretch justify-center bg-black/55 sm:items-center sm:p-4">
    <div className="flex h-[100dvh] w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl sm:h-auto sm:max-h-[95dvh] sm:rounded-2xl">
      <header className="flex items-center justify-between bg-slate-900 px-4 py-3 text-white sm:px-6">
        <div><h2 className="text-lg font-black">Technician Team — {job.jobNo}</h2><p className="text-xs text-slate-300">Lead တစ်ယောက်၊ Members/Helpers အများအပြားနှင့် Hand Over history</p></div>
        <button onClick={onClose} className="h-10 w-10 rounded-full text-2xl hover:bg-white/10">×</button>
      </header>
      <main className="min-h-0 flex-1 space-y-5 overflow-y-auto p-4 sm:p-6">
        {!data ? <div className="py-16 text-center text-slate-500">Team data ရယူနေသည်...</div> : <>
          <div className={`rounded-xl border p-3 text-sm font-semibold ${data.canComplete ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
            {data.canComplete ? 'Technician tasks အားလုံးပြီးပါပြီ။ Job ကို COMPLETED ပြောင်းနိုင်ပါပြီ။' : data.completionBlockReason || 'Technician tasks မပြီးသေးပါ။'}
          </div>

          {canAssign && <section className="rounded-2xl border bg-slate-50 p-4">
            <h3 className="mb-3 font-black">Technician ထည့်ရန်</h3>
            <div className="grid gap-3 md:grid-cols-4">
              <select className={input} value={form.staffId} onChange={e => setForm({ ...form, staffId: e.target.value })}>
                <option value="">Technician ရွေးပါ</option>{staff.filter(s => !current.some(a => a.staffId === s.id)).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
              <select className={input} value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
                <option value="LEAD">Lead</option><option value="MEMBER">Team Member</option><option value="HELPER">Helper</option>
              </select>
              <input className={input} value={form.taskDescription} onChange={e => setForm({ ...form, taskDescription: e.target.value })} placeholder="တာဝန်/လုပ်ငန်းခွဲ" />
              <button disabled={busy} onClick={add} className="rounded-xl bg-indigo-600 px-4 py-2 font-bold text-white disabled:opacity-50">တာဝန်ပေးမည်</button>
            </div>
          </section>}

          <section><h3 className="mb-3 font-black">လက်ရှိ Team ({current.length})</h3>
            <div className="grid gap-3 lg:grid-cols-2">{current.map(a => <AssignmentCard key={a.id} a={a} canManage={canAssign} busy={busy}
              onApprove={() => act(() => serviceJobTeamService.approveAssignment(job.id, a.id), 'Helper approval ပြီးပါပြီ')}
              onAccept={() => act(() => serviceJobTeamService.acceptAssignment(job.id, a.id))}
              onReject={async () => { const reason = await promptReason('Assignment ငြင်းပယ်ရသည့်အကြောင်း'); if (reason != null) await act(() => serviceJobTeamService.rejectAssignment(job.id, a.id, reason)); }}
              onStart={() => work(a, 'START')} onPause={() => work(a, 'PAUSE')} onResume={() => work(a, 'RESUME')}
              onNote={() => work(a, 'NOTE', true)} onComplete={() => work(a, 'COMPLETE', true)} onHandover={() => openHandover(a)}
              onEdit={async () => { const note = await promptReason('တာဝန်/လုပ်ငန်းခွဲ ပြင်ရန်', true); if (note != null) await act(() => serviceJobTeamService.updateAssignment(job.id, a.id, { taskDescription: note })); }}
              onRemove={() => act(() => serviceJobTeamService.cancelAssignment(job.id, a.id))} />)}</div>
            {!current.length && <p className="rounded-xl border border-dashed p-6 text-center text-sm text-slate-500">လက်ရှိ Technician မရှိသေးပါ</p>}
          </section>

          {handover.fromAssignmentId > 0 && <section className="rounded-2xl border-2 border-purple-200 bg-purple-50 p-4">
            <div className="mb-3 flex justify-between"><h3 className="font-black text-purple-900">Hand Over Request</h3><button onClick={() => setHandover({ ...handover, fromAssignmentId: 0 })}>×</button></div>
            <div className="grid gap-3 md:grid-cols-2">
              <select className={input} value={handover.toStaffId} onChange={e => setHandover({ ...handover, toStaffId: e.target.value })}><option value="">လက်ခံမည့် Technician</option>{staff.filter(s => !current.some(a => a.staffId === s.id)).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
              <input className={input} value={handover.completedWork} onChange={e => setHandover({ ...handover, completedWork: e.target.value })} placeholder="လုပ်ပြီးသောအလုပ်" />
              <textarea className={input} value={handover.remainingWork} onChange={e => setHandover({ ...handover, remainingWork: e.target.value })} placeholder="ကျန်ရှိသောအလုပ် *" />
              <textarea className={input} value={handover.diagnosisNote} onChange={e => setHandover({ ...handover, diagnosisNote: e.target.value })} placeholder="Diagnosis / မှတ်ချက်" />
            </div><button disabled={busy} onClick={sendHandover} className="mt-3 rounded-xl bg-purple-700 px-5 py-2 font-bold text-white">Hand Over ပို့မည်</button>
          </section>}

          <section><h3 className="mb-3 font-black">Hand Over History</h3><div className="space-y-2">{(data.handovers || []).map(h => <div key={h.id} className="rounded-xl border p-3 text-sm">
            <div className="flex flex-wrap items-center gap-2"><b>{h.fromStaffName}</b><span>→</span><b>{h.toStaffName}</b><span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${statusStyle[h.status]}`}>{h.status}</span><span className="text-xs text-slate-400">{h.role}</span></div>
            <p className="mt-1 text-slate-600">ကျန်အလုပ်: {h.remainingWork}</p>{h.completedWork && <p className="text-slate-500">ပြီးခဲ့: {h.completedWork}</p>}
            {h.status === 'PENDING' && (h.targetMine || canAssign) && <div className="mt-2 flex gap-2"><button onClick={() => act(() => serviceJobTeamService.acceptHandover(job.id, h.id), 'Hand Over လက်ခံပြီးပါပြီ')} className="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white">လက်ခံ</button><button onClick={async () => { const reason = await promptReason('Hand Over ငြင်းပယ်ရသည့်အကြောင်း'); if (reason != null) await act(() => serviceJobTeamService.rejectHandover(job.id, h.id, reason)); }} className="rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-bold text-rose-700">ငြင်းပယ်</button></div>}
          </div>)}{!data.handovers?.length && <p className="text-sm text-slate-400">Hand Over history မရှိသေးပါ</p>}</div></section>

          {!!history.length && <section><h3 className="mb-3 font-black">Assignment History</h3><div className="space-y-2">{history.map(a => <AssignmentCard key={a.id} a={a} canManage={false} busy={busy} />)}</div></section>}
        </>}
      </main>
    </div>
  </div>;
}

function AssignmentCard({ a, canManage, busy, onApprove, onAccept, onReject, onStart, onPause, onResume, onNote, onComplete, onHandover, onEdit, onRemove }: any) {
  const actionable = a.mine || canManage;
  return <div className="rounded-2xl border bg-white p-4 shadow-sm">
    <div className="flex flex-wrap items-start justify-between gap-2"><div><div className="font-black">{a.staffName}</div><div className="text-xs text-slate-500">{roleLabel[a.role] || a.role}</div></div><span className={`rounded-full px-2.5 py-1 text-[10px] font-black ${statusStyle[a.status] || 'bg-slate-100'}`}>{a.status}</span></div>
    <p className="mt-3 min-h-6 text-sm text-slate-700">{a.taskDescription || 'တာဝန်အသေးစိတ် မသတ်မှတ်ရသေးပါ'}</p>
    <div className="mt-2 flex gap-3 text-xs text-slate-500"><span>လုပ်ချိန် {a.accumulatedMinutes || 0} မိနစ်</span>{a.workStartedAt && <span className="font-bold text-emerald-600">Timer running</span>}</div>
    {canManage && a.role === 'HELPER' && a.approvalStatus === 'PENDING' && <div className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs font-bold text-amber-800">Supervisor approval လိုအပ်နေသည်။ <Btn onClick={onApprove} color="bg-amber-600 text-white">Approve</Btn></div>}
    {actionable && <div className="mt-3 flex flex-wrap gap-2">
      {a.status === 'PENDING' && a.approvalStatus === 'APPROVED' && <><Btn onClick={onAccept} color="bg-emerald-600 text-white">လက်ခံ</Btn><Btn onClick={onReject}>ငြင်းပယ်</Btn></>}
      {a.status === 'PENDING' && a.approvalStatus === 'PENDING' && <span className="rounded-lg bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-800">Supervisor approval စောင့်နေသည်</span>}
      {a.status === 'ACTIVE' && !a.workStartedAt && <Btn onClick={onStart} color="bg-indigo-600 text-white">Start</Btn>}
      {a.status === 'ACTIVE' && a.workStartedAt && <Btn onClick={onPause}>Pause</Btn>}
      {a.status === 'PAUSED' && <Btn onClick={onResume} color="bg-indigo-600 text-white">Resume</Btn>}
      {['ACTIVE','PAUSED'].includes(a.status) && <><Btn onClick={onNote}>Note</Btn><Btn onClick={onComplete} color="bg-emerald-600 text-white">Task Complete</Btn><Btn onClick={onHandover} color="bg-purple-600 text-white">Hand Over</Btn></>}
      {canManage && currentStatuses.has(a.status) && <Btn onClick={onEdit}>တာဝန်ပြင်</Btn>}
      {canManage && a.role !== 'LEAD' && currentStatuses.has(a.status) && <Btn onClick={onRemove}>ဖယ်ရှား</Btn>}
    </div>}
    {!!a.logs?.length && <details className="mt-3"><summary className="cursor-pointer text-xs font-bold text-slate-500">Work Logs ({a.logs.length})</summary><div className="mt-2 space-y-1 border-l pl-3">{a.logs.map((l: any) => <p key={l.id} className="text-xs text-slate-500"><b>{l.action}</b> · {l.note || '—'} · {l.actor}</p>)}</div></details>}
  </div>;
}
function Btn({ children, color = 'border bg-white', ...props }: any) { return <button disabled={props.disabled} {...props} className={`rounded-lg px-3 py-1.5 text-xs font-bold disabled:opacity-50 ${color}`}>{children}</button>; }
