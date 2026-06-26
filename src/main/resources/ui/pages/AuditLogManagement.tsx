import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { AuditLogDTO } from '../types';
import { api } from '../services/api';
import {
  RefreshCw, Search, X, Shield, Monitor, Smartphone,
  LogIn, Plus, Pencil, Trash2, Zap, ChevronDown, ChevronRight,
  Clock, Globe,
} from 'lucide-react';

/* ── helpers ─────────────────────────────────────────────────── */
const fmtDate = (d?: string) => {
  if (!d) return '-';
  try { return new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }); }
  catch { return '-'; }
};
const fmtTime = (d?: string) => {
  if (!d) return '';
  try { return new Date(d).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', second: '2-digit' }); }
  catch { return ''; }
};
const dateKey = (d?: string) => d ? d.slice(0, 10) : 'unknown';

/* ── action config ────────────────────────────────────────────── */
const ACTION_CFG: Record<string, { color: string; bg: string; icon: React.ReactNode; dot: string }> = {
  LOGIN:  { color: 'text-blue-700',   bg: 'bg-blue-50 border-blue-200',   icon: <LogIn  size={11}/>, dot: 'bg-blue-500'   },
  CREATE: { color: 'text-emerald-700',bg: 'bg-emerald-50 border-emerald-200', icon: <Plus   size={11}/>, dot: 'bg-emerald-500'},
  UPDATE: { color: 'text-amber-700',  bg: 'bg-amber-50 border-amber-200', icon: <Pencil size={11}/>, dot: 'bg-amber-500'  },
  DELETE: { color: 'text-rose-700',   bg: 'bg-rose-50 border-rose-200',   icon: <Trash2 size={11}/>, dot: 'bg-rose-500'   },
  ACTION: { color: 'text-violet-700', bg: 'bg-violet-50 border-violet-200',icon: <Zap    size={11}/>, dot: 'bg-violet-500' },
};
const getCfg = (a: string) => ACTION_CFG[a] ?? { color: 'text-slate-600', bg: 'bg-slate-100 border-slate-200', icon: null, dot: 'bg-slate-400' };

const ALL_ACTIONS = ['LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'ACTION'];

/* ── types ────────────────────────────────────────────────────── */
interface PagedResponse { content: AuditLogDTO[]; totalElements: number; totalPages: number; number: number; }

/* ── session group ────────────────────────────────────────────── */
interface SessionGroup { key: string; actor: string; actorRole?: string; date: string; events: AuditLogDTO[]; }

function buildGroups(logs: AuditLogDTO[]): SessionGroup[] {
  const map = new Map<string, SessionGroup>();
  logs.forEach(log => {
    const k = `${log.actor}__${dateKey(log.createdAt)}`;
    if (!map.has(k)) {
      map.set(k, { key: k, actor: log.actor, actorRole: log.actorRole, date: dateKey(log.createdAt), events: [] });
    }
    map.get(k)!.events.push(log);
  });
  // sort events within each group oldest→newest
  map.forEach(g => g.events.sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? '')));
  // sort groups newest first
  return [...map.values()].sort((a, b) => b.date.localeCompare(a.date));
}

/* ── session card ─────────────────────────────────────────────── */
function SessionCard({ group }: { group: SessionGroup }) {
  const [open, setOpen] = useState(false);
  const initial = (group.actor || 'U')[0].toUpperCase();

  const loginCount  = group.events.filter(e => e.action === 'LOGIN').length;
  const actionCount = group.events.filter(e => e.action !== 'LOGIN').length;

  return (
    <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
      {/* Header */}
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-slate-50 transition-colors text-left"
      >
        {/* Avatar */}
        <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-[11px] font-black text-indigo-700 shrink-0">
          {initial}
        </div>

        {/* Name + role */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-bold text-slate-800">{group.actor}</span>
            {group.actorRole && (
              <span className="text-[9px] font-black uppercase tracking-wider px-1.5 py-0.5 bg-indigo-50 text-indigo-600 rounded-full border border-indigo-100">
                {group.actorRole}
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 mt-0.5">
            <span className="text-[11px] text-slate-500 font-medium">
              {new Date(group.date).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })}
            </span>
            <span className="text-[10px] text-slate-400">
              {loginCount > 0 && <span className="text-blue-500 font-bold">{loginCount} login{loginCount > 1 ? 's' : ''}</span>}
              {loginCount > 0 && actionCount > 0 && <span className="mx-1">·</span>}
              {actionCount > 0 && <span>{actionCount} action{actionCount > 1 ? 's' : ''}</span>}
            </span>
          </div>
        </div>

        {/* Event count badge */}
        <span className="shrink-0 px-2 py-0.5 bg-slate-100 text-slate-600 rounded-full text-[10px] font-black">
          {group.events.length} events
        </span>

        {/* Chevron */}
        <span className="shrink-0 text-slate-400">
          {open ? <ChevronDown size={16}/> : <ChevronRight size={16}/>}
        </span>
      </button>

      {/* Timeline */}
      {open && (
        <div className="border-t border-slate-100 px-4 py-3 space-y-0">
          {group.events.map((ev, idx) => {
            const cfg = getCfg(ev.action);
            const isLast = idx === group.events.length - 1;
            return (
              <div key={ev.id} className="flex gap-3 relative">
                {/* Vertical line */}
                {!isLast && (
                  <div className="absolute left-[13px] top-5 bottom-0 w-px bg-slate-200" />
                )}

                {/* Dot */}
                <div className={`w-[26px] h-[26px] rounded-full ${cfg.dot} flex items-center justify-center shrink-0 mt-1 z-10 shadow-sm`}>
                  <span className="text-white">{cfg.icon}</span>
                </div>

                {/* Content */}
                <div className={`flex-1 pb-3 ${isLast ? '' : ''}`}>
                  <div className="flex items-start justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className={`text-[10px] font-black px-2 py-0.5 rounded-full border ${cfg.bg} ${cfg.color}`}>
                        {ev.action}
                      </span>
                      <span className="text-xs font-semibold text-slate-700">{ev.module}</span>
                      {ev.resourceId && (
                        <span className="text-[10px] font-mono text-indigo-600 bg-indigo-50 px-1.5 py-0.5 rounded">
                          {ev.resourceId}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {ev.deviceType && (
                        <span className="flex items-center gap-1 text-[10px] text-slate-400">
                          {ev.deviceType === 'MOBILE'
                            ? <Smartphone size={10} className="text-indigo-400"/>
                            : <Monitor    size={10} className="text-slate-400"/>}
                        </span>
                      )}
                      {ev.ipAddress && (
                        <span className="flex items-center gap-1 text-[10px] text-slate-400 font-mono">
                          <Globe size={10}/>{ev.ipAddress}
                        </span>
                      )}
                      <span className="flex items-center gap-1 text-[10px] text-slate-400">
                        <Clock size={10}/>{fmtTime(ev.createdAt)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/* ── main component ───────────────────────────────────────────── */
const AuditLogManagement: React.FC = () => {
  const [data, setData]         = useState<PagedResponse | null>(null);
  const [loading, setLoading]   = useState(true);
  const [page, setPage]         = useState(0);
  const [actor, setActor]       = useState('');
  const [action, setAction]     = useState('');
  const [module, setModule]     = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo]     = useState('');

  const fetchData = useCallback(async (pg = page) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(pg), size: '200', actor, action, module, dateFrom, dateTo });
      const res = await api.get<any>(`/v1/audit-logs?${params}`);
      setData(res.data ?? null);
    } catch (e) {
      console.error('Failed to load audit logs', e);
    } finally {
      setLoading(false);
    }
  }, [page, actor, action, module, dateFrom, dateTo]);

  useEffect(() => { void fetchData(); }, [fetchData]);
  useDataEvents(['Sale', 'Service Job', 'Booking', 'Product', 'Purchase', 'Expense', 'Income'], () => void fetchData());

  const clearFilters = () => { setActor(''); setAction(''); setModule(''); setDateFrom(''); setDateTo(''); setPage(0); };
  const hasFilters = actor || action || module || dateFrom || dateTo;

  const groups = useMemo(() => buildGroups(data?.content ?? []), [data]);

  return (
    <div className="w-full max-w-none space-y-4">

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
              <Shield size={16} className="text-indigo-600" />
            </div>
            <h2 className="text-xl font-bold text-slate-800">Audit Logs</h2>
          </div>
          <p className="text-xs text-slate-500 mt-0.5 ml-10">ဘယ် Account မှ ဘာလုပ်သွားလဲ — Session Timeline</p>
        </div>
        <button onClick={() => fetchData(page)}
          className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 space-y-3">
        <div className="flex flex-col lg:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="text" value={actor} onChange={e => { setActor(e.target.value); setPage(0); }}
              placeholder="Search by username..."
              className="w-full pl-8 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
          </div>
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="text" value={module} onChange={e => { setModule(e.target.value); setPage(0); }}
              placeholder="Filter by module..."
              className="w-full pl-8 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
          </div>
          <div className="flex gap-2 items-center">
            <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setPage(0); }}
              className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-600 focus:outline-none" />
            <span className="text-slate-300 text-xs">—</span>
            <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setPage(0); }}
              className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-600 focus:outline-none" />
          </div>
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          <span className="text-[10px] text-slate-400 font-bold uppercase">Action:</span>
          {['', ...ALL_ACTIONS].map(a => (
            <button key={a} onClick={() => { setAction(a); setPage(0); }}
              className={`px-2.5 py-1 rounded-full text-[11px] font-bold border transition-all ${action === a
                ? 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-slate-500 border-slate-200 hover:bg-slate-50'}`}>
              {a === '' ? 'All' : a}
            </button>
          ))}
          {hasFilters && (
            <button onClick={clearFilters} className="flex items-center gap-1 px-2 py-1 text-xs text-slate-500 hover:text-rose-500">
              <X size={12} /> Clear
            </button>
          )}
          <span className="ml-auto text-[11px] text-slate-400">
            {data?.totalElements ?? 0} record(s) · {groups.length} session group(s)
          </span>
        </div>
      </div>

      {/* Groups */}
      {loading ? (
        <div className="py-16 text-center text-slate-400 text-sm">Loading...</div>
      ) : groups.length === 0 ? (
        <div className="py-16 text-center text-slate-400 text-sm">
          <Shield size={40} className="mx-auto mb-3 text-slate-200" />
          No audit records found.
        </div>
      ) : (
        <div className="space-y-2">
          {groups.map(g => <SessionCard key={g.key} group={g} />)}
        </div>
      )}

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
            className="px-3 py-1.5 text-xs font-medium bg-white border border-slate-200 rounded-lg disabled:opacity-40 hover:bg-slate-50">← Prev</button>
          {Array.from({ length: Math.min(data.totalPages, 10) }, (_, i) => (
            <button key={i} onClick={() => setPage(i)}
              className={`w-8 h-8 text-xs font-bold rounded-lg border ${page === i ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>
              {i + 1}
            </button>
          ))}
          <button onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))} disabled={page >= data.totalPages - 1}
            className="px-3 py-1.5 text-xs font-medium bg-white border border-slate-200 rounded-lg disabled:opacity-40 hover:bg-slate-50">Next →</button>
        </div>
      )}
    </div>
  );
};

export default AuditLogManagement;
