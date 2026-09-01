import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { serviceJobTeamService } from '../services/api';
import { useWebsocket } from '../hooks/useWebsocket';

type Assignment = {
  id: number; staffId: number; staffName: string; role: 'LEAD' | 'MEMBER' | 'HELPER';
  status: string; approvalStatus?: string; taskDescription?: string; workStartedAt?: string;
  accumulatedMinutes?: number; mine: boolean;
};
type Snapshot = { canComplete: boolean; completionBlockReason?: string; assignments: Assignment[] };
type MemberDraft = { key: number; staffId: string; taskDescription: string };
const live = new Set(['PENDING', 'ACTIVE', 'PAUSED', 'COMPLETED']);
const field = 'min-h-11 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-base outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100 sm:text-sm';
const roleText: Record<string, string> = { LEAD: 'အဓိကပြုပြင်သူ', MEMBER: 'အဖွဲ့ဝင်', HELPER: 'အကူပြုပြင်သူ' };
const statusText: Record<string, string> = { PENDING: 'လက်ခံရန်စောင့်နေ', ACTIVE: 'လက်ခံပြီး', PAUSED: 'ခေတ္တရပ်ထား', COMPLETED: 'ပြီးစီး', REJECTED: 'ငြင်းပယ်', CANCELED: 'ပယ်ဖျက်', HANDED_OVER: 'လွှဲပြောင်းပြီး' };
const statusColor: Record<string, string> = { PENDING: 'bg-amber-100 text-amber-800', ACTIVE: 'bg-emerald-100 text-emerald-800', PAUSED: 'bg-blue-100 text-blue-800', COMPLETED: 'bg-indigo-100 text-indigo-800', REJECTED: 'bg-rose-100 text-rose-800', CANCELED: 'bg-slate-100 text-slate-600', HANDED_OVER: 'bg-purple-100 text-purple-800' };
const newMember = (key: number): MemberDraft => ({ key, staffId: '', taskDescription: '' });
const friendlyReason = (reason?: string) => reason === 'A current lead technician is required'
  ? 'အဓိကပြုပြင်သူ မသတ်မှတ်ရသေးပါ။'
  : reason?.startsWith('Complete all technician assignments first:')
    ? 'Technician တစ်ဦးချင်းစီ၏ လုပ်ငန်းကို ပြီးစီးအဖြစ် သတ်မှတ်ရန်လိုအပ်ပါသည်။'
    : reason || 'Technician လုပ်ငန်းများ မပြီးသေးပါ။';

export default function TechnicianAssignmentPanel({ job, staff, canAssign, onClose, onChanged }:
  { job: any; staff: any[]; canAssign: boolean; onClose: () => void; onChanged?: () => void }) {
  const [data, setData] = useState<Snapshot | null>(null);
  const [busy, setBusy] = useState(false);
  const [view, setView] = useState<'setup' | 'workspace'>('workspace');
  const [step, setStep] = useState(0);
  const [mode, setMode] = useState<'SOLO' | 'TEAM' | ''>('');
  const [lead, setLead] = useState({ staffId: '', taskDescription: 'စစ်ဆေးမှုနှင့် ပြုပြင်မှုကို တာဝန်ယူရန်' });
  const [members, setMembers] = useState<MemberDraft[]>([newMember(1)]);
  const [nextKey, setNextKey] = useState(2);
  const [add, setAdd] = useState({ staffId: '', role: 'MEMBER' as 'MEMBER' | 'HELPER', taskDescription: '' });

  const load = async () => {
    try {
      const response = await serviceJobTeamService.getTeam(job.id);
      const snapshot = response.data as Snapshot;
      setData(snapshot);
      if (canAssign && !snapshot.assignments.some(item => live.has(item.status))) setView('setup');
    } catch (error: any) {
      void Swal.fire('မရပါ', error?.response?.data?.message || error?.message || 'Assignment အချက်အလက် မရပါ', 'error');
    }
  };
  useEffect(() => { void load(); }, [job.id]);
  useWebsocket('/topic/service-jobs', () => {
    void load();
    onChanged?.();
  });
  const current = useMemo(() => (data?.assignments || []).filter(item => live.has(item.status)), [data]);
  const history = useMemo(() => (data?.assignments || []).filter(item => !live.has(item.status)), [data]);
  const leadAssignment = current.find(item => item.role === 'LEAD');
  const unfinished = current.filter(item => item.status !== 'COMPLETED');
  const used = new Set(current.map(item => String(item.staffId)));
  const available = staff.filter(person => !used.has(String(person.id)));

  const run = async (action: () => Promise<any>, success?: string) => {
    setBusy(true);
    try {
      await action(); await load(); onChanged?.();
      if (success) void Swal.fire({ icon: 'success', title: success, timer: 1100, showConfirmButton: false });
    } catch (error: any) {
      void Swal.fire('မလုပ်နိုင်ပါ', error?.response?.data?.message || error?.message || 'လုပ်ဆောင်မှု မအောင်မြင်ပါ', 'error');
    } finally { setBusy(false); }
  };
  const askNote = async (title: string, required = false) => {
    const result = await Swal.fire({ title, input: 'textarea', inputPlaceholder: 'အကြောင်းပြချက် / မှတ်ချက်', showCancelButton: true, confirmButtonText: 'အတည်ပြု', cancelButtonText: 'မလုပ်တော့ပါ', inputValidator: required ? value => value?.trim() ? undefined : 'အချက်အလက်ဖြည့်ပါ' : undefined });
    return result.isConfirmed ? String(result.value || '') : null;
  };
  const reset = () => {
    setStep(0); setMode(''); setLead({ staffId: '', taskDescription: 'စစ်ဆေးမှုနှင့် ပြုပြင်မှုကို တာဝန်ယူရန်' });
    setMembers([newMember(1)]); setNextKey(2);
  };
  const goNext = () => {
    if (step === 0 && !mode) return void Swal.fire('လုပ်ငန်းပုံစံရွေးပါ', 'တစ်ယောက်တာဝန်ယူ သို့မဟုတ် အဖွဲ့လိုက်လုပ်ဆောင် ကိုရွေးပါ။', 'warning');
    if (step === 1) {
      if (!lead.staffId) return void Swal.fire('အဓိကပြုပြင်သူရွေးပါ', 'Lead Technician တစ်ဦး မဖြစ်မနေလိုအပ်ပါသည်။', 'warning');
      if (!lead.taskDescription.trim()) return void Swal.fire('လုပ်ငန်းတာဝန်ဖြည့်ပါ', 'Lead Technician ၏ တာဝန်ကိုရေးပါ။', 'warning');
      const chosen = members.filter(item => item.staffId || item.taskDescription.trim());
      if (mode === 'TEAM' && (!chosen.length || chosen.some(item => !item.staffId || !item.taskDescription.trim()))) return void Swal.fire('အဖွဲ့ဝင်တာဝန် မပြည့်စုံပါ', 'အဖွဲ့ဝင်အနည်းဆုံးတစ်ဦးနှင့် တစ်ဦးချင်းလုပ်ငန်းတာဝန် ဖြည့်ပါ။', 'warning');
      const ids = [lead.staffId, ...chosen.map(item => item.staffId)];
      if (new Set(ids).size !== ids.length) return void Swal.fire('Technician ထပ်နေပါသည်', 'Technician တစ်ဦးကို Role နှစ်ခုတွင် မရွေးနိုင်ပါ။', 'warning');
    }
    setStep(value => Math.min(2, value + 1));
  };
  const submit = async () => {
    const rows = [{ staffId: Number(lead.staffId), role: 'LEAD', taskDescription: lead.taskDescription.trim() },
      ...(mode === 'TEAM' ? members.filter(item => item.staffId).map(item => ({ staffId: Number(item.staffId), role: 'MEMBER', taskDescription: item.taskDescription.trim() })) : [])];
    setBusy(true);
    try {
      for (const row of rows) await serviceJobTeamService.assign(job.id, row);
      await load(); setView('workspace'); reset(); onChanged?.();
      void Swal.fire({ icon: 'success', title: 'Technician တာဝန်ပေးပို့ပြီးပါပြီ', text: 'Technician များ၏ လက်ခံမှုကို စောင့်ပါ။', timer: 1600, showConfirmButton: false });
    } catch (error: any) {
      await load(); setView('workspace');
      void Swal.fire('တာဝန်ပေးမှု မပြည့်စုံပါ', error?.response?.data?.message || error?.message || 'Assignment မဖန်တီးနိုင်ပါ', 'error');
    } finally { setBusy(false); }
  };
  const work = async (assignment: Assignment, action: string, needsNote = false) => {
    const text = needsNote ? await askNote(action === 'COMPLETE' ? 'ပြီးစီးမှုမှတ်ချက်' : 'လုပ်ငန်းမှတ်ချက်', true) : '';
    if (needsNote && text == null) return;
    await run(() => serviceJobTeamService.recordWork(job.id, assignment.id, action, text || undefined));
  };

  return <div className="fixed inset-0 z-[70] flex items-stretch justify-center bg-black/55 sm:items-center sm:p-4"><div className="flex h-[100dvh] w-full max-w-5xl flex-col overflow-hidden bg-white shadow-2xl sm:h-auto sm:max-h-[95dvh] sm:rounded-2xl">
    <header className="flex items-center justify-between gap-3 bg-slate-900 px-4 py-3 text-white sm:px-6"><div className="min-w-0"><h2 className="truncate text-lg font-black">Technician Assignment</h2><p className="truncate text-xs text-slate-300">{job.jobNo} · {job.itemName || job.deviceType || 'ဝန်ဆောင်မှု Job'}</p></div><button onClick={onClose} aria-label="ပိတ်ရန်" className="flex h-11 w-11 items-center justify-center rounded-full text-2xl hover:bg-white/10">×</button></header>
    <main className="min-h-0 flex-1 overflow-y-auto overscroll-contain p-4 sm:p-6">{!data ? <div className="py-16 text-center text-slate-500">Assignment အချက်အလက် ရယူနေသည်...</div> : view === 'setup' && canAssign ? <>
      <Steps step={step} />
      {step === 0 && <ModeStep mode={mode} setMode={setMode} />}
      {step === 1 && <PeopleStep mode={mode} lead={lead} setLead={setLead} members={members} setMembers={setMembers} available={available} addMember={() => { setMembers(value => [...value, newMember(nextKey)]); setNextKey(value => value + 1); }} />}
      {step === 2 && <ReviewStep mode={mode} lead={lead} members={members} available={available} />}
      <div className="mt-7 flex flex-col-reverse gap-3 border-t pt-4 sm:flex-row sm:justify-between"><button onClick={() => step === 0 ? (current.length ? setView('workspace') : onClose()) : setStep(value => value - 1)} className="min-h-11 rounded-xl border px-5 text-sm font-bold text-slate-600">{step === 0 ? 'မလုပ်တော့ပါ' : 'အရင်အဆင့်'}</button>{step < 2 ? <button onClick={goNext} className="min-h-11 rounded-xl bg-indigo-600 px-6 text-sm font-black text-white">နောက်တစ်ဆင့်</button> : <button disabled={busy} onClick={submit} className="min-h-11 rounded-xl bg-indigo-600 px-6 text-sm font-black text-white disabled:opacity-50">{busy ? 'ပေးပို့နေသည်...' : 'တာဝန်ပေးပို့မည်'}</button>}</div>
    </> : <div className="space-y-6">
      <section className="rounded-2xl border bg-slate-50 p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">လက်ရှိ Technician Assignment</p><h3 className="mt-1 text-lg font-black">{leadAssignment?.staffName || 'Lead Technician မရှိသေးပါ'}</h3><p className="mt-1 text-sm text-slate-600">{leadAssignment ? `${current.length} ဦးတာဝန်ပေးထားသည် · ${unfinished.length} ဦးမပြီးသေး` : 'Job မပြီးစီးမီ အဓိကပြုပြင်သူတစ်ဦး သတ်မှတ်ပါ။'}</p></div>{canAssign && !leadAssignment && <button onClick={() => { reset(); setView('setup'); }} className="min-h-11 rounded-xl bg-indigo-600 px-4 text-sm font-black text-white">တာဝန်ပေးရန်</button>}</div></section>
      <section><h3 className="font-black">Job ပြီးစီးရန် လိုအပ်ချက်များ</h3><div className="mt-3 space-y-2 rounded-2xl border p-4"><Check ok={!!leadAssignment} text="Lead Technician သတ်မှတ်ပြီး" /><Check ok={!!current.length && !unfinished.length} text={unfinished.length ? `Technician Assignment ${unfinished.length} ခု မပြီးသေး` : 'Technician Assignment အားလုံးပြီးစီး'} /><Check ok={!!job.finalApprovalStatus} text={job.finalApprovalStatus ? 'Supervisor Final Check အတည်ပြုပြီး' : 'Supervisor Final Check မလုပ်ရသေး'} />{!data.canComplete && <p className="mt-2 rounded-xl bg-amber-50 px-3 py-2 text-xs font-bold text-amber-800">{friendlyReason(data.completionBlockReason)}</p>}</div></section>
      {canAssign && leadAssignment && <section className="rounded-2xl border border-indigo-100 bg-indigo-50/50 p-4"><h3 className="font-black text-indigo-950">အဖွဲ့ဝင် သို့မဟုတ် Helper ထည့်ရန်</h3><p className="mt-1 text-xs text-indigo-700">Helper Request သည် Supervisor approval ရပြီးမှ လက်ခံနိုင်ပါမည်။</p><div className="mt-3 grid gap-3 md:grid-cols-4"><select className={field} value={add.role} onChange={event => setAdd({ ...add, role: event.target.value as any })}><option value="MEMBER">Team Member</option><option value="HELPER">Helper Request</option></select><People value={add.staffId} list={available} placeholder="Technician ရွေးပါ" onChange={(value: string) => setAdd({ ...add, staffId: value })} /><input className={field} value={add.taskDescription} onChange={event => setAdd({ ...add, taskDescription: event.target.value })} placeholder="လုပ်ငန်းတာဝန် *" /><button disabled={busy} onClick={() => run(async () => { if (!add.staffId) throw new Error('Technician ရွေးပါ'); if (!add.taskDescription.trim()) throw new Error('လုပ်ငန်းတာဝန် ဖြည့်ပါ'); await serviceJobTeamService.assign(job.id, { ...add, staffId: Number(add.staffId) }); setAdd({ staffId: '', role: 'MEMBER', taskDescription: '' }); }, add.role === 'HELPER' ? 'Helper Request ပို့ပြီးပါပြီ' : 'အဖွဲ့ဝင် ထည့်ပြီးပါပြီ')} className="min-h-11 rounded-xl bg-indigo-600 px-4 text-sm font-bold text-white">{add.role === 'HELPER' ? 'Request ပို့ရန်' : 'အဖွဲ့ဝင်ထည့်ရန်'}</button></div></section>}
      <section><h3 className="mb-3 font-black">လက်ရှိအဖွဲ့ ({current.length})</h3><div className="grid gap-3 lg:grid-cols-2">{current.map(assignment => <AssignmentCard key={assignment.id} a={assignment} manage={canAssign} approve={() => run(() => serviceJobTeamService.approveAssignment(job.id, assignment.id), 'Helper Request အတည်ပြုပြီးပါပြီ')} accept={() => run(() => serviceJobTeamService.acceptAssignment(job.id, assignment.id), 'တာဝန်လက်ခံပြီးပါပြီ')} reject={async () => { const reason = await askNote('Assignment ငြင်းပယ်ရသည့်အကြောင်း'); if (reason != null) await run(() => serviceJobTeamService.rejectAssignment(job.id, assignment.id, reason)); }} start={() => work(assignment, 'START')} pause={() => work(assignment, 'PAUSE')} resume={() => work(assignment, 'RESUME')} addNote={() => work(assignment, 'NOTE', true)} complete={() => work(assignment, 'COMPLETE', true)} edit={async () => { const text = await askNote('လုပ်ငန်းတာဝန် ပြင်ရန်', true); if (text != null) await run(() => serviceJobTeamService.updateAssignment(job.id, assignment.id, { taskDescription: text })); }} remove={() => run(() => serviceJobTeamService.cancelAssignment(job.id, assignment.id), 'Assignment ဖယ်ရှားပြီးပါပြီ')} />)}</div></section>
      {!!history.length && <section><h3 className="mb-3 font-black">Assignment မှတ်တမ်း</h3><div className="grid gap-3 lg:grid-cols-2">{history.map(assignment => <AssignmentCard key={assignment.id} a={assignment} manage={false} />)}</div></section>}
    </div>}</main>
  </div></div>;
}

function Steps({ step }: { step: number }) { return <div className="mb-6 grid grid-cols-3 gap-2">{['လုပ်ငန်းပုံစံ', 'လူနှင့်တာဝန်', 'ပြန်လည်စစ်ဆေး'].map((label, index) => <div key={label} className="text-center"><div className={`mx-auto flex h-9 w-9 items-center justify-center rounded-full text-sm font-black ${index < step ? 'bg-emerald-500 text-white' : index === step ? 'bg-indigo-600 text-white ring-4 ring-indigo-100' : 'bg-slate-100 text-slate-400'}`}>{index < step ? '✓' : index + 1}</div><p className={`mt-2 truncate text-xs font-bold ${index === step ? 'text-indigo-700' : 'text-slate-500'}`}>{label}</p></div>)}</div>; }
function ModeStep({ mode, setMode }: any) { return <section><h3 className="text-lg font-black">ဘယ်လိုလုပ်ဆောင်မလဲ?</h3><p className="mt-1 text-sm text-slate-500">ဒီ Job အတွက် လုပ်ငန်းပုံစံတစ်ခုရွေးပါ။</p><div className="mt-5 grid gap-4 sm:grid-cols-2"><Mode chosen={mode === 'SOLO'} onClick={() => setMode('SOLO')} icon="👤" title="တစ်ယောက်တာဝန်ယူ" text="Lead Technician တစ်ယောက်က လုပ်ငန်းအားလုံးကို တာဝန်ယူမည်။" /><Mode chosen={mode === 'TEAM'} onClick={() => setMode('TEAM')} icon="👥" title="အဖွဲ့လိုက်လုပ်ဆောင်" text="Lead Technician နှင့် Team Members များကို လုပ်ငန်းခွဲပေးမည်။" /></div></section>; }
function PeopleStep({ mode, lead, setLead, members, setMembers, available, addMember }: any) { return <section className="space-y-5"><div><h3 className="text-lg font-black">လူနှင့်လုပ်ငန်း သတ်မှတ်ပါ</h3><p className="mt-1 text-sm text-slate-500">Lead Technician တစ်ဦး မဖြစ်မနေလိုအပ်ပါသည်။</p></div><div className="rounded-2xl border-2 border-indigo-200 bg-indigo-50/60 p-4"><div className="mb-3 flex justify-between"><h4 className="font-black text-indigo-900">Lead Technician</h4><span className="rounded-full bg-indigo-600 px-2.5 py-1 text-[10px] font-black text-white">တာဝန်ပိုင်ရှင်</span></div><div className="grid gap-3 sm:grid-cols-2"><People value={lead.staffId} list={available} placeholder="အဓိကပြုပြင်သူ ရွေးပါ *" onChange={(value: string) => setLead({ ...lead, staffId: value })} /><input className={field} value={lead.taskDescription} onChange={event => setLead({ ...lead, taskDescription: event.target.value })} placeholder="Lead ၏ လုပ်ငန်းတာဝန် *" /></div></div>{mode === 'TEAM' && <div className="space-y-3"><div className="flex items-center justify-between"><div><h4 className="font-black">Team Members</h4><p className="text-xs text-slate-500">တစ်ဦးချင်းလုပ်ငန်းခွဲကိုရေးပါ။</p></div><button onClick={addMember} className="min-h-11 rounded-xl border border-indigo-200 px-3 text-xs font-bold text-indigo-700">+ အဖွဲ့ဝင်ထည့်ရန်</button></div>{members.map((item: MemberDraft, index: number) => <div key={item.key} className="grid gap-3 rounded-2xl border bg-slate-50 p-4 sm:grid-cols-[1fr_1.4fr_auto]"><People value={item.staffId} list={available} placeholder={`အဖွဲ့ဝင် ${index + 1} ရွေးပါ *`} onChange={(value: string) => setMembers((rows: MemberDraft[]) => rows.map(row => row.key === item.key ? { ...row, staffId: value } : row))} /><input className={field} value={item.taskDescription} onChange={event => setMembers((rows: MemberDraft[]) => rows.map(row => row.key === item.key ? { ...row, taskDescription: event.target.value } : row))} placeholder="လုပ်ငန်းခွဲ *" /><button disabled={members.length === 1} onClick={() => setMembers((rows: MemberDraft[]) => rows.filter(row => row.key !== item.key))} className="min-h-11 rounded-xl border border-rose-200 px-3 text-xs font-bold text-rose-600 disabled:opacity-40">ဖယ်ရှား</button></div>)}</div>}</section>; }
function ReviewStep({ mode, lead, members, available }: any) { return <section><h3 className="text-lg font-black">တာဝန်ပေးမှု ပြန်လည်စစ်ဆေးပါ</h3><p className="mt-1 text-sm text-slate-500">ပေးပို့ပြီးနောက် Technician များက မိမိတာဝန်ကို လက်ခံရပါမည်။</p><div className="mt-5 overflow-hidden rounded-2xl border"><div className="flex justify-between bg-slate-50 px-4 py-3 text-sm"><b className="text-slate-600">လုပ်ငန်းပုံစံ</b><b>{mode === 'SOLO' ? 'တစ်ယောက်တာဝန်ယူ' : 'အဖွဲ့လိုက်လုပ်ဆောင်'}</b></div><Review label="အဓိကပြုပြင်သူ" name={available.find((person: any) => String(person.id) === lead.staffId)?.name || '—'} task={lead.taskDescription} />{mode === 'TEAM' && members.filter((item: MemberDraft) => item.staffId).map((item: MemberDraft) => <Review key={item.key} label="အဖွဲ့ဝင်" name={available.find((person: any) => String(person.id) === item.staffId)?.name || '—'} task={item.taskDescription} />)}</div></section>; }
function Mode({ chosen, onClick, icon, title, text }: any) { return <button onClick={onClick} className={`min-h-40 rounded-2xl border-2 p-5 text-left ${chosen ? 'border-indigo-600 bg-indigo-50 ring-4 ring-indigo-100' : 'border-slate-200 hover:border-indigo-300'}`}><span className="text-3xl">{icon}</span><b className="mt-3 block">{title}</b><span className="mt-1 block text-sm text-slate-600">{text}</span></button>; }
function People({ value, list, placeholder, onChange }: any) { return <select className={field} value={value} onChange={event => onChange(event.target.value)}><option value="">{placeholder}</option>{list.map((person: any) => <option key={person.id} value={person.id}>{person.name}{person.role ? ` (${person.role})` : ''}</option>)}</select>; }
function Review({ label, name, task }: any) { return <div className="grid gap-1 border-t px-4 py-3 sm:grid-cols-[10rem_1fr]"><b className="text-xs text-slate-500">{label}</b><div><b>{name}</b><p className="mt-1 text-sm text-slate-600">{task}</p></div></div>; }
function Check({ ok, text }: any) { return <div className="flex items-center gap-3 text-sm"><span className={`flex h-6 w-6 items-center justify-center rounded-full font-black ${ok ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>{ok ? '✓' : '!'}</span><span className={ok ? 'font-semibold text-slate-700' : 'font-bold text-rose-700'}>{text}</span></div>; }
function Badge({ status }: any) { return <span className={`rounded-full px-2.5 py-1 text-[10px] font-black ${statusColor[status] || 'bg-slate-100 text-slate-600'}`}>{statusText[status] || status}</span>; }
function Btn({ children, color = 'border bg-white', ...props }: any) { return <button {...props} className={`min-h-9 rounded-lg px-3 py-1.5 text-xs font-bold ${color}`}>{children}</button>; }
function AssignmentCard({ a, manage, approve, accept, reject, start, pause, resume, addNote, complete, edit, remove }: any) { const actionable = a.mine || manage; return <div className={`rounded-2xl border bg-white p-4 shadow-sm ${a.role === 'LEAD' ? 'border-indigo-300 ring-2 ring-indigo-50' : ''}`}><div className="flex justify-between gap-2"><div><b>{a.staffName}</b><p className="text-xs text-slate-500">{roleText[a.role] || a.role}{a.role === 'LEAD' ? ' · တာဝန်ပိုင်ရှင်' : ''}</p></div><Badge status={a.status} /></div><p className="mt-3 text-sm text-slate-700">{a.taskDescription || 'တာဝန်အသေးစိတ် မသတ်မှတ်ရသေးပါ'}</p><p className="mt-2 text-xs text-slate-500">လုပ်ချိန် {a.accumulatedMinutes || 0} မိနစ် {a.workStartedAt && '· Timer လည်နေသည်'}</p>{manage && a.role === 'HELPER' && a.approvalStatus === 'PENDING' && <div className="mt-3 flex items-center justify-between rounded-lg bg-amber-50 px-3 py-2 text-xs font-bold text-amber-800"><span>Supervisor approval လိုအပ်နေသည်။</span><Btn onClick={approve} color="bg-amber-600 text-white">အတည်ပြု</Btn></div>}{actionable && <div className="mt-3 flex flex-wrap gap-2">{a.status === 'PENDING' && a.approvalStatus !== 'PENDING' && <><Btn onClick={accept} color="bg-emerald-600 text-white">လက်ခံ</Btn><Btn onClick={reject}>ငြင်းပယ်</Btn></>}{a.status === 'PENDING' && a.approvalStatus === 'PENDING' && <span className="rounded-lg bg-amber-50 px-3 py-1.5 text-xs font-bold text-amber-800">Supervisor approval စောင့်နေသည်</span>}{a.status === 'ACTIVE' && !a.workStartedAt && <Btn onClick={start} color="bg-indigo-600 text-white">စတင်</Btn>}{a.status === 'ACTIVE' && a.workStartedAt && <Btn onClick={pause}>ခေတ္တရပ်</Btn>}{a.status === 'PAUSED' && <Btn onClick={resume} color="bg-indigo-600 text-white">ပြန်စတင်</Btn>}{['ACTIVE', 'PAUSED'].includes(a.status) && <><Btn onClick={addNote}>မှတ်ချက်</Btn><Btn onClick={complete} color="bg-emerald-600 text-white">လုပ်ငန်းပြီးစီး</Btn></>}{manage && live.has(a.status) && <Btn onClick={edit}>တာဝန်ပြင်</Btn>}{manage && a.role !== 'LEAD' && live.has(a.status) && <Btn onClick={remove}>ဖယ်ရှား</Btn>}</div>}</div>; }
