import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import { summaryReportService } from '../../services/api';
import { Truck, TrendingDown } from 'lucide-react';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const fmt = (v: any) => Number(v ?? 0).toLocaleString();

export default function PurchaseSummaryReport() {
  const [data,    setData]    = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [from,    setFrom]    = useState('');
  const [to,      setTo]      = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await summaryReportService.purchase(from || undefined, to || undefined);
      setData(res.data);
    } catch {}
    setLoading(false);
  }, [from, to]);

  useEffect(() => { load(); }, []);
  useDataEvents(['Purchase', 'Purchase Return'], load);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Truck size={22} className="text-violet-500" />
        <h1 className="text-lg font-black text-slate-800">Purchase Summary Report</h1>
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
        {(from || to) && <button onClick={() => { setFrom(''); setTo(''); }} className="px-4 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg">Clear</button>}
      </div>

      {loading && <div className="text-center py-10 text-slate-400">Loading...</div>}
      {!loading && data && (
        <>
          {/* KPI cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {[
              { label: 'Total Purchases', value: fmt(data.totalCount) + ' txn',  color: 'text-violet-600', bg: 'bg-violet-50' },
              { label: 'Total Spent',     value: fmt(data.totalAmount) + ' Ks',  color: 'text-rose-600',   bg: 'bg-rose-50'   },
              { label: 'Outstanding AP',  value: fmt(data.totalDue) + ' Ks',     color: 'text-amber-600',  bg: 'bg-amber-50'  },
            ].map(k => (
              <div key={k.label} className="bg-white border border-slate-200 rounded-xl p-4">
                <div className={`text-2xl font-black ${k.color}`}>{k.value}</div>
                <div className="text-xs text-slate-500 mt-1">{k.label}</div>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Monthly */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><TrendingDown size={15} /> Monthly Breakdown</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">Month</th><th className="px-4 py-2 text-right">Txns</th><th className="px-4 py-2 text-right">Amount (Ks)</th></tr></thead>
                <tbody>
                  {(data.monthly ?? []).map((m: any, i: number) => (
                    <tr key={m.label} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{MONTHS[m.month - 1]} {m.year}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(m.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-violet-700">{fmt(m.amount)}</td>
                    </tr>
                  ))}
                  {(data.monthly ?? []).length === 0 && <tr><td colSpan={3} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>

            {/* By Supplier */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <div className="px-4 py-3 border-b font-semibold text-sm text-slate-700 flex items-center gap-2"><Truck size={15} /> By Supplier</div>
              <table className="w-full text-sm">
                <thead><tr><th className="px-4 py-2 text-left">Supplier</th><th className="px-4 py-2 text-right">Orders</th><th className="px-4 py-2 text-right">Amount (Ks)</th></tr></thead>
                <tbody>
                  {(data.bySupplier ?? []).map((s: any, i: number) => (
                    <tr key={s.supplierId} className={`border-t ${i % 2 === 0 ? '' : 'bg-slate-50'}`}>
                      <td className="px-4 py-2.5 font-semibold text-slate-700">{s.supplierName}</td>
                      <td className="px-4 py-2.5 text-right text-slate-500">{fmt(s.count)}</td>
                      <td className="px-4 py-2.5 text-right font-bold text-rose-700">{fmt(s.amount)}</td>
                    </tr>
                  ))}
                  {(data.bySupplier ?? []).length === 0 && <tr><td colSpan={3} className="px-4 py-6 text-center text-slate-400">No data</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
