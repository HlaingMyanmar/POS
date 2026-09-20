import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Landmark, Loader2, RefreshCw } from 'lucide-react';
import Swal from 'sweetalert2';
import { cashDrawerService } from '../services/cashdrawerapiservice';
import { CashDrawerMovementDTO, CashDrawerSessionDTO } from '../types';
import { getFromSession } from '../utils/storageHelper';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const money = (value?: number | null) => Number(value || 0).toLocaleString();
const when = (value?: string | null) => {
  if (!value) return '—';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
};

const CashDrawerManagement: React.FC = () => {
  const currentUser = useMemo(() => {
    try {
      return JSON.parse(getFromSession('sspd_user') || '{}') as { username?: string; permissions?: string[]; roles?: string[] };
    } catch {
      return {};
    }
  }, []);
  const isAdmin = (currentUser.roles || []).some(role => role === 'ADMINISTRATOR' || role === 'ROLE_ADMINISTRATOR');
  const canManage = isAdmin || (currentUser.permissions || []).includes('CAN_ACCESS_CASH_DRAWER_MANAGE');
  const username = currentUser.username || '';

  const [sessions, setSessions] = useState<CashDrawerSessionDTO[]>([]);
  const [movements, setMovements] = useState<CashDrawerMovementDTO[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [openingCash, setOpeningCash] = useState('0');
  const [openNote, setOpenNote] = useState('');
  const [countedCash, setCountedCash] = useState('0');
  const [closeNote, setCloseNote] = useState('');
  const [moveAmount, setMoveAmount] = useState('');
  const [moveReason, setMoveReason] = useState('');

  const myOpen = sessions.find(s => s.status === 'OPEN' && s.openedBy === username) || null;
  const selected = sessions.find(s => s.id === selectedId) || myOpen || sessions[0] || null;

  const load = useCallback(async () => {
    try {
      const rows = await cashDrawerService.getAll();
      setSessions(rows);
      setSelectedId(prev => {
        const mine = rows.find(s => s.status === 'OPEN' && s.openedBy === username);
        if (mine) return mine.id;
        if (prev && rows.some(s => s.id === prev)) return prev;
        return rows[0]?.id ?? null;
      });
    } catch (error: any) {
      Swal.fire('အမှား', error?.message || 'Cash drawer စာရင်း မယူနိုင်ပါ', 'error');
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => { void load(); }, [load]);
  useRefreshOnTabActivate(load);

  useEffect(() => {
    if (!selectedId) {
      setMovements([]);
      return;
    }
    let cancelled = false;
    cashDrawerService.movements(selectedId)
      .then(rows => { if (!cancelled) setMovements(rows); })
      .catch(() => { if (!cancelled) setMovements([]); });
    return () => { cancelled = true; };
  }, [selectedId]);

  const run = async (fn: () => Promise<unknown>, success: string) => {
    setSaving(true);
    try {
      await fn();
      Swal.fire({ icon: 'success', title: success, toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      await load();
    } catch (error: any) {
      Swal.fire('အမှား', error?.message || 'Operation failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleOpen = () => run(
    () => cashDrawerService.open(Number(openingCash || 0), openNote.trim() || undefined),
    'Drawer ဖွင့်ပြီး'
  );

  const handleClose = () => {
    if (!myOpen) return;
    return run(
      () => cashDrawerService.close(myOpen.id, Number(countedCash || 0), closeNote.trim() || undefined),
      'Drawer ပိတ်ပြီး'
    );
  };

  const handleMove = (type: 'in' | 'out') => {
    if (!myOpen) return;
    const amount = Number(moveAmount);
    if (!(amount > 0) || !moveReason.trim()) {
      Swal.fire('အမှား', 'ပမာဏနှင့် အကြောင်းရင်း ထည့်ပါ', 'error');
      return;
    }
    return run(async () => {
      if (type === 'in') await cashDrawerService.cashIn(myOpen.id, amount, moveReason.trim());
      else await cashDrawerService.cashOut(myOpen.id, amount, moveReason.trim());
      setMoveAmount('');
      setMoveReason('');
    }, type === 'in' ? 'Cash in မှတ်ပြီး' : 'Cash out မှတ်ပြီး');
  };

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-black text-slate-800 flex items-center gap-2">
            <Landmark className="text-amber-500" size={22} />
            Cash Drawer
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            ငွေသားနဲ့ Sale / Service Job ရှင်းမယ်ဆို သင်ကိုယ်တိုင် drawer အရင်ဖွင့်ပါ။
          </p>
        </div>
        <button type="button" onClick={() => void load()}
          className="px-3 py-2 rounded-xl border border-slate-200 text-xs font-bold text-slate-600 hover:bg-slate-50 flex items-center gap-1.5">
          <RefreshCw size={13} /> ပြန်ဖတ်မည်
        </button>
      </div>

      {loading ? (
        <div className="flex justify-center py-16 text-slate-400"><Loader2 className="animate-spin" /></div>
      ) : (
        <>
          <div className={`rounded-2xl border px-4 py-3 ${myOpen ? 'border-emerald-200 bg-emerald-50' : 'border-amber-200 bg-amber-50'}`}>
            {myOpen ? (
              <div className="text-sm">
                <p className="font-bold text-emerald-800">သင့် drawer ဖွင့်ထားသည် (#{myOpen.id})</p>
                <p className="text-xs text-emerald-700 mt-1">
                  Opening {money(myOpen.openingCash)} · Sales {money(myOpen.cashSales)} · Refunds {money(myOpen.cashRefunds)} ·
                  In {money(myOpen.cashIn)} · Out {money(myOpen.cashOut)}
                </p>
              </div>
            ) : (
              <p className="text-sm font-bold text-amber-800">သင့်အကောင့်အတွက် ဖွင့်ထားသော drawer မရှိပါ။ ငွေသားမရှင်းမီ ဖွင့်ပါ။</p>
            )}
          </div>

          {canManage && !myOpen && (
            <section className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
              <h2 className="text-sm font-black text-slate-800">Drawer ဖွင့်မည်</h2>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <label className="text-xs font-bold text-slate-500">အစပိုင်းငွေ (Ks)
                  <input type="number" min={0} value={openingCash} onChange={e => setOpeningCash(e.target.value)}
                    className="mt-1 w-full border rounded-xl px-3 py-2 text-sm font-bold text-slate-800" />
                </label>
                <label className="text-xs font-bold text-slate-500 sm:col-span-2">မှတ်ချက်
                  <input value={openNote} onChange={e => setOpenNote(e.target.value)} placeholder="Morning open"
                    className="mt-1 w-full border rounded-xl px-3 py-2 text-sm" />
                </label>
              </div>
              <button type="button" disabled={saving} onClick={() => void handleOpen()}
                className="px-4 py-2 rounded-xl bg-amber-500 text-white text-sm font-bold hover:bg-amber-600 disabled:opacity-50">
                Drawer ဖွင့်မည်
              </button>
            </section>
          )}

          {canManage && myOpen && (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <section className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
                <h2 className="text-sm font-black text-slate-800">Cash in / Cash out</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label className="text-xs font-bold text-slate-500">ပမာဏ
                    <input type="number" min={0.01} value={moveAmount} onChange={e => setMoveAmount(e.target.value)}
                      className="mt-1 w-full border rounded-xl px-3 py-2 text-sm font-bold" />
                  </label>
                  <label className="text-xs font-bold text-slate-500">အကြောင်းရင်း
                    <input value={moveReason} onChange={e => setMoveReason(e.target.value)}
                      className="mt-1 w-full border rounded-xl px-3 py-2 text-sm" />
                  </label>
                </div>
                <div className="flex gap-2">
                  <button type="button" disabled={saving} onClick={() => void handleMove('in')}
                    className="px-4 py-2 rounded-xl bg-emerald-600 text-white text-xs font-bold disabled:opacity-50">Cash in</button>
                  <button type="button" disabled={saving} onClick={() => void handleMove('out')}
                    className="px-4 py-2 rounded-xl bg-rose-600 text-white text-xs font-bold disabled:opacity-50">Cash out</button>
                </div>
              </section>
              <section className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3">
                <h2 className="text-sm font-black text-slate-800">Drawer ပိတ်မည်</h2>
                <label className="text-xs font-bold text-slate-500">ရေတွက်ထားသောငွေ (Ks)
                  <input type="number" min={0} value={countedCash} onChange={e => setCountedCash(e.target.value)}
                    className="mt-1 w-full border rounded-xl px-3 py-2 text-sm font-bold" />
                </label>
                <label className="text-xs font-bold text-slate-500">မှတ်ချက်
                  <input value={closeNote} onChange={e => setCloseNote(e.target.value)}
                    className="mt-1 w-full border rounded-xl px-3 py-2 text-sm" />
                </label>
                <button type="button" disabled={saving} onClick={() => void handleClose()}
                  className="px-4 py-2 rounded-xl bg-slate-800 text-white text-sm font-bold hover:bg-slate-900 disabled:opacity-50">
                  Drawer ပိတ်မည်
                </button>
              </section>
            </div>
          )}

          <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
            <section className="rounded-2xl border border-slate-200 bg-white overflow-hidden">
              <div className="px-4 py-3 border-b text-sm font-black text-slate-800">Sessions</div>
              <div className="overflow-auto max-h-[28rem]">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <th className="text-left px-3 py-2">#</th>
                      <th className="text-left px-3 py-2">User</th>
                      <th className="text-left px-3 py-2">Status</th>
                      <th className="text-right px-3 py-2">Opening</th>
                      <th className="text-right px-3 py-2">Sales</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.map(session => (
                      <tr key={session.id}
                        onClick={() => setSelectedId(session.id)}
                        className={`cursor-pointer border-t ${selected?.id === session.id ? 'bg-amber-50' : 'hover:bg-slate-50'}`}>
                        <td className="px-3 py-2 font-bold">{session.id}</td>
                        <td className="px-3 py-2">{session.openedBy}</td>
                        <td className="px-3 py-2">
                          <span className={`px-2 py-0.5 rounded-full font-bold ${session.status === 'OPEN' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>
                            {session.status}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-right">{money(session.openingCash)}</td>
                        <td className="px-3 py-2 text-right">{money(session.cashSales)}</td>
                      </tr>
                    ))}
                    {sessions.length === 0 && (
                      <tr><td colSpan={5} className="px-3 py-8 text-center text-slate-400">မှတ်တမ်းမရှိပါ</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white overflow-hidden">
              <div className="px-4 py-3 border-b text-sm font-black text-slate-800">
                Movements {selected ? `#${selected.id}` : ''}
              </div>
              <div className="overflow-auto max-h-[28rem]">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <th className="text-left px-3 py-2">Time</th>
                      <th className="text-left px-3 py-2">Type</th>
                      <th className="text-right px-3 py-2">Amount</th>
                      <th className="text-left px-3 py-2">Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movements.map(move => (
                      <tr key={move.id} className="border-t">
                        <td className="px-3 py-2 whitespace-nowrap">{when(move.createdAt)}</td>
                        <td className="px-3 py-2 font-bold">{move.type}</td>
                        <td className="px-3 py-2 text-right">{money(move.amount)}</td>
                        <td className="px-3 py-2">{move.reason}{move.reversed ? ' (reversed)' : ''}</td>
                      </tr>
                    ))}
                    {selected && movements.length === 0 && (
                      <tr><td colSpan={4} className="px-3 py-8 text-center text-slate-400">Movement မရှိပါ</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
};

export default CashDrawerManagement;
