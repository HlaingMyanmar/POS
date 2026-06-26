import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import { summaryReportService } from '../../services/api';
import { Users, TrendingUp, Wrench, XCircle, RefreshCw, AlertCircle } from 'lucide-react';

const fmt = (v: any) => Number(v ?? 0).toLocaleString();
const pct = (v: any) => `${Number(v ?? 0).toFixed(1)}%`;

interface StaffPerformanceDTO {
  staffId: number;
  staffName: string;
  staffRole: string;
  salesCount: number;
  salesAmount: number;
  serviceJobsCount: number;
  completedJobsCount: number;
  cancelledJobsCount: number;
  reworkJobsCount: number;
  inProgressJobsCount: number;
  serviceJobsAmount: number;
  completionRate: number;
}

function perfBadge(rate: number) {
  if (rate >= 90) return { label: 'Excellent', cls: 'bg-emerald-100 text-emerald-700' };
  if (rate >= 75) return { label: 'Good',      cls: 'bg-blue-100 text-blue-700' };
  if (rate >= 50) return { label: 'Average',   cls: 'bg-yellow-100 text-yellow-700' };
  return              { label: 'Poor',         cls: 'bg-red-100 text-red-700' };
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
function firstOfMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
}

export default function StaffPerformanceReport() {
  const [data,    setData]    = useState<StaffPerformanceDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [from,    setFrom]    = useState(firstOfMonth());
  const [to,      setTo]      = useState(today());
  const [sortKey, setSortKey] = useState<keyof StaffPerformanceDTO>('completionRate');
  const [sortAsc, setSortAsc] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await summaryReportService.staffPerformance(from || undefined, to || undefined);
      setData(res.data ?? []);
    } catch {}
    setLoading(false);
  }, [from, to]);

  useEffect(() => { load(); }, []);
  useDataEvents(['Sale', 'Service Job'], load);

  function toggleSort(key: keyof StaffPerformanceDTO) {
    if (sortKey === key) setSortAsc(v => !v);
    else { setSortKey(key); setSortAsc(false); }
  }

  const sorted = [...data].sort((a, b) => {
    const av = a[sortKey] as any;
    const bv = b[sortKey] as any;
    const cmp = typeof av === 'string' ? av.localeCompare(bv) : (av ?? 0) - (bv ?? 0);
    return sortAsc ? cmp : -cmp;
  });

  const totalJobs      = data.reduce((s, r) => s + (r.serviceJobsCount ?? 0), 0);
  const totalCompleted = data.reduce((s, r) => s + (r.completedJobsCount ?? 0), 0);
  const totalRevenue   = data.reduce((s, r) => s + (r.serviceJobsAmount ?? 0), 0);
  const totalReworks   = data.reduce((s, r) => s + (r.reworkJobsCount ?? 0), 0);
  const overallRate    = totalJobs > 0 ? (totalCompleted / totalJobs * 100).toFixed(1) : '0.0';

  const Th = ({ label, col }: { label: string; col: keyof StaffPerformanceDTO }) => (
    <th
      className="px-3 py-2 text-left text-xs font-bold text-slate-500 uppercase cursor-pointer select-none hover:text-slate-700 whitespace-nowrap"
      onClick={() => toggleSort(col)}
    >
      {label} {sortKey === col ? (sortAsc ? '↑' : '↓') : ''}
    </th>
  );

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Users size={22} className="text-violet-500" />
        <h1 className="text-lg font-black text-slate-800">Staff Performance Report</h1>
      </div>

      {/* Filter */}
      <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-200 rounded-xl p-4">
        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">From</label>
          <input type="date" value={from} onChange={e => setFrom(e.target.value)} className="border rounded-lg px-3 py-1.5 text-sm" />
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-500 mb-1">To</label>
          <input type="date" value={to} onChange={e => setTo(e.target.value)} className="border rounded-lg px-3 py-1.5 text-sm" />
        </div>
        <button onClick={load} className="px-4 py-1.5 bg-violet-600 text-white text-sm rounded-lg hover:bg-violet-700">Apply</button>
        <button onClick={() => { setFrom(firstOfMonth()); setTo(today()); }} className="px-4 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">Reset</button>
      </div>

      {/* KPI summary row */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <KpiCard icon={<Wrench size={18} className="text-violet-500" />} label="Total Jobs"       value={fmt(totalJobs)}       color="text-violet-600" />
        <KpiCard icon={<TrendingUp size={18} className="text-emerald-500" />} label="Completion Rate" value={`${overallRate}%`}  color="text-emerald-600" />
        <KpiCard icon={<RefreshCw size={18} className="text-yellow-500" />} label="Total Reworks"    value={fmt(totalReworks)}    color="text-yellow-600" />
        <KpiCard icon={<XCircle size={18} className="text-blue-500" />} label="Total Revenue"    value={`${fmt(totalRevenue)} Ks`} color="text-blue-600" />
      </div>

      {loading && <div className="text-center py-10 text-slate-400">Loading...</div>}

      {!loading && data.length === 0 && (
        <div className="flex items-center gap-2 text-slate-400 py-10 justify-center">
          <AlertCircle size={18} /> No data for the selected period.
        </div>
      )}

      {!loading && data.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-bold text-slate-500 uppercase">#</th>
                  <Th label="Staff"         col="staffName" />
                  <Th label="Role"          col="staffRole" />
                  <Th label="Total Jobs"    col="serviceJobsCount" />
                  <Th label="Completed"     col="completedJobsCount" />
                  <Th label="In Progress"   col="inProgressJobsCount" />
                  <Th label="Cancelled"     col="cancelledJobsCount" />
                  <Th label="Reworks"       col="reworkJobsCount" />
                  <Th label="Revenue"       col="serviceJobsAmount" />
                  <Th label="Completion %"  col="completionRate" />
                  <th className="px-3 py-2 text-left text-xs font-bold text-slate-500 uppercase">Performance</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((r, i) => {
                  const badge = perfBadge(r.completionRate ?? 0);
                  const hasJobs = r.serviceJobsCount > 0;
                  return (
                    <tr key={r.staffId} className="border-b border-slate-100 hover:bg-slate-50">
                      <td className="px-3 py-2.5 text-slate-400 text-xs">{i + 1}</td>
                      <td className="px-3 py-2.5">
                        <div className="flex items-center gap-2">
                          <div className="w-7 h-7 rounded-full bg-violet-100 flex items-center justify-center text-violet-700 font-black text-xs">
                            {(r.staffName || '?')[0].toUpperCase()}
                          </div>
                          <span className="font-semibold text-slate-800">{r.staffName}</span>
                        </div>
                      </td>
                      <td className="px-3 py-2.5">
                        <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-semibold">{r.staffRole || '—'}</span>
                      </td>
                      <td className="px-3 py-2.5 font-semibold text-slate-700">{hasJobs ? r.serviceJobsCount : '—'}</td>
                      <td className="px-3 py-2.5 text-emerald-600 font-semibold">{hasJobs ? r.completedJobsCount : '—'}</td>
                      <td className="px-3 py-2.5 text-purple-600 font-semibold">{hasJobs ? r.inProgressJobsCount : '—'}</td>
                      <td className="px-3 py-2.5 text-red-500 font-semibold">{hasJobs ? r.cancelledJobsCount : '—'}</td>
                      <td className="px-3 py-2.5 text-yellow-600 font-semibold">{hasJobs ? r.reworkJobsCount : '—'}</td>
                      <td className="px-3 py-2.5 font-bold text-slate-800">{fmt(r.serviceJobsAmount)} Ks</td>
                      <td className="px-3 py-2.5">
                        {hasJobs ? (
                          <div className="flex items-center gap-2">
                            <div className="w-20 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                              <div
                                className="h-full rounded-full bg-emerald-500"
                                style={{ width: `${Math.min(r.completionRate ?? 0, 100)}%` }}
                              />
                            </div>
                            <span className="text-xs font-semibold text-slate-700">{pct(r.completionRate)}</span>
                          </div>
                        ) : '—'}
                      </td>
                      <td className="px-3 py-2.5">
                        {hasJobs ? (
                          <span className={`px-2 py-0.5 rounded-full text-xs font-bold ${badge.cls}`}>{badge.label}</span>
                        ) : (
                          <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-400">No Jobs</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function KpiCard({ icon, label, value, color }: { icon: React.ReactNode; label: string; value: string; color: string }) {
  return (
    <div className="bg-white border border-slate-200 rounded-xl p-4 flex items-center gap-3">
      <div className="w-10 h-10 rounded-xl bg-slate-50 flex items-center justify-center">{icon}</div>
      <div>
        <p className="text-xs font-semibold text-slate-500 uppercase">{label}</p>
        <p className={`text-lg font-black ${color}`}>{value}</p>
      </div>
    </div>
  );
}
