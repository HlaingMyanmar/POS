import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import { summaryReportService } from '../../services/api';
import { Wrench, BarChart2 } from 'lucide-react';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const fmt = (v: any) => Number(v ?? 0).toLocaleString();

const STATUS_COLOR: Record<string, string> = {
  RECEIVED:    'bg-slate-100 text-slate-600',
  INSPECTING:  'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-purple-100 text-purple-700',
  COMPLETED:   'bg-emerald-100 text-emerald-700',
  DELIVERED:   'bg-green-100 text-green-700',
  CANCELLED:   'bg-red-100 text-red-700',
};

export default function ServiceSummaryReport() {
  const [data,    setData]    = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [from,    setFrom]    = useState('');
  const [to,      setTo]      = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await summaryReportService.service(from || undefined, to || undefined);
      setData(res.data);
    } catch {}
    setLoading(false);
  }, [from, to]);

  useEffect(() => { load(); }, []);
  useDataEvents(['Service Job', 'Booking'], load);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Wrench size={22} className="text-emerald-500" />
        <h1 className="text-lg font-black text-slate-800">Service Job Summary Report</h1>
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
        <button onClick={load} className="px-4 py-1.5 bg-emerald-600 text-white text-sm rounded-lg hover:bg-emerald-700">Apply</button>
        {(from || to) && <button onClick={() => { setFrom(''); setTo(''); }} className="px-4 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg">Clear</button>}
      </div>

      {loading && <div className="text-center py-10 text-slate-400">Loading...</div>}
      {!loading && data && (
        <>
          {/* Total Jobs KPI */}
          <div className="bg-white border border-slate-200 rounded-xl p-4 inline-flex items-center gap-4">
            <div className="w-12 h-12 bg-emerald-50 rounded-xl flex items-center justify-center">
              <Wrench size={22} className="text-emerald-600" />
            </div>
            <div>
              <div className="text-2xl font-black text-emerald-700">{fmt(data.totalJobs)}</div>
              <div className="text-xs text-slate-500">Total Service Jobs</div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* By Status */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><BarChart2 size={15} /> By Status</div>
              <div className="p-4 space-y-2">
                {(data.byStatus ?? []).map((s: any) => {
                  const total = data.totalJobs || 1;
                  const pct = Math.round((Number(s.count) / total) * 100);
                  return (
                    <div key={s.status} className="flex items-center gap-3">
                      <span className={`text-[10px] font-black px-2 py-0.5 rounded-full w-28 text-center ${STATUS_COLOR[s.status] ?? 'bg-slate-100 text-slate-600'}`}>
                        {s.status.replace(/_/g, ' ')}
                      </span>
                      <div className="flex-1 bg-slate-100 rounded-full h-2">
                        <div className="bg-emerald-500 h-2 rounded-full" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="text-xs font-bold text-slate-600 w-12 text-right">{fmt(s.count)}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Monthly */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700">Monthly Breakdown</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">Month</th><th className="px-4 py-2 text-right">Jobs</th><th className="px-4 py-2 text-right">Revenue (Ks)</th></tr></thead>
                <tbody>
                  {(data.monthly ?? []).map((m: any, i: number) => (
                    <tr key={m.label} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{MONTHS[m.month - 1]} {m.year}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(m.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-emerald-700">{fmt(m.amount)}</td>
                    </tr>
                  ))}
                  {(data.monthly ?? []).length === 0 && <tr><td colSpan={3} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
