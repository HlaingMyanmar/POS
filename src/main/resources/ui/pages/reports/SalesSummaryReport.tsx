import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import { summaryReportService } from '../../services/api';
import { ShoppingCart, TrendingUp, Users, UserCircle } from 'lucide-react';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const fmt = (v: any) => Number(v ?? 0).toLocaleString();

export default function SalesSummaryReport() {
  const [data,    setData]    = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [from,    setFrom]    = useState('');
  const [to,      setTo]      = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await summaryReportService.sales(from || undefined, to || undefined);
      setData(res.data);
    } catch {}
    setLoading(false);
  }, [from, to]);

  useEffect(() => { load(); }, []);
  useDataEvents(['Sale', 'Return'], load);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center gap-3">
        <ShoppingCart size={22} className="text-indigo-500" />
        <h1 className="text-lg font-black text-slate-800">Sales Summary Report</h1>
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
        <button onClick={load} className="px-4 py-1.5 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700">Apply</button>
        {(from || to) && <button onClick={() => { setFrom(''); setTo(''); }} className="px-4 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">Clear</button>}
      </div>

      {loading && <div className="text-center py-10 text-slate-400">Loading...</div>}
      {!loading && data && (
        <>
          {/* KPI cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {[
              { label: 'Total Sales', value: fmt(data.totalCount) + ' txn', icon: <ShoppingCart size={18} />, color: 'text-indigo-600', bg: 'bg-indigo-50' },
              { label: 'Revenue', value: fmt(data.totalRevenue) + ' Ks', icon: <TrendingUp size={18} />, color: 'text-emerald-600', bg: 'bg-emerald-50' },
              { label: 'Discount Given', value: fmt(data.totalDiscount) + ' Ks', icon: <TrendingUp size={18} />, color: 'text-amber-600', bg: 'bg-amber-50' },
              { label: 'Outstanding AR', value: fmt(data.totalDue) + ' Ks', icon: <TrendingUp size={18} />, color: 'text-rose-600', bg: 'bg-rose-50' },
            ].map(k => (
              <div key={k.label} className="bg-white border border-slate-200 rounded-xl p-4">
                <div className={`w-8 h-8 rounded-lg ${k.bg} ${k.color} flex items-center justify-center mb-2`}>{k.icon}</div>
                <div className={`text-lg font-black ${k.color}`}>{k.value}</div>
                <div className="text-xs text-slate-500 mt-0.5">{k.label}</div>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Monthly breakdown */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><TrendingUp size={15} /> Monthly Breakdown</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">Month</th><th className="px-4 py-2 text-right">Txns</th><th className="px-4 py-2 text-right">Revenue (Ks)</th></tr></thead>
                <tbody>
                  {(data.monthly ?? []).map((m: any, i: number) => (
                    <tr key={m.label} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{MONTHS[m.month - 1]} {m.year}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(m.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-indigo-700">{fmt(m.amount)}</td>
                    </tr>
                  ))}
                  {(data.monthly ?? []).length === 0 && <tr><td colSpan={3} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>

            {/* By Staff */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><UserCircle size={15} /> By Staff</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">Staff</th><th className="px-4 py-2 text-right">Txns</th><th className="px-4 py-2 text-right">Revenue (Ks)</th></tr></thead>
                <tbody>
                  {(data.byStaff ?? []).map((s: any, i: number) => (
                    <tr key={s.staffId} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{s.staffName}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(s.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-emerald-700">{fmt(s.amount)}</td>
                    </tr>
                  ))}
                  {(data.byStaff ?? []).length === 0 && <tr><td colSpan={3} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>

            {/* Top Customers */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden lg:col-span-2">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><Users size={15} /> Top Customers</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">#</th><th className="px-4 py-2 text-left">Customer</th><th className="px-4 py-2 text-right">Purchases</th><th className="px-4 py-2 text-right">Total (Ks)</th></tr></thead>
                <tbody>
                  {(data.topCustomers ?? []).map((c: any, i: number) => (
                    <tr key={c.customerId} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 text-slate-400 text-xs font-bold">{i + 1}</td>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{c.customerName}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(c.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-indigo-700">{fmt(c.amount)}</td>
                    </tr>
                  ))}
                  {(data.topCustomers ?? []).length === 0 && <tr><td colSpan={4} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
