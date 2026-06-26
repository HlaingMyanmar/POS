import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { salesRankingService } from '../services/api';
import { Trophy, TrendingUp, BarChart2, Calendar, Package } from 'lucide-react';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

const MEDAL: Record<number, string> = { 0: '🥇', 1: '🥈', 2: '🥉' };

export default function SalesRankingPage() {
  const [tab,       setTab]       = useState<'products' | 'monthly'>('products');
  const [products,  setProducts]  = useState<any[]>([]);
  const [monthly,   setMonthly]   = useState<any[]>([]);
  const [loading,   setLoading]   = useState(false);
  const [from,      setFrom]      = useState('');
  const [to,        setTo]        = useState('');

  const loadProducts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await salesRankingService.topProducts(from || undefined, to || undefined);
      setProducts(res.data ?? []);
    } catch {}
    setLoading(false);
  }, [from, to]);

  const loadMonthly = useCallback(async () => {
    setLoading(true);
    try {
      const res = await salesRankingService.monthly();
      setMonthly(res.data ?? []);
    } catch {}
    setLoading(false);
  }, []);

  useEffect(() => {
    if (tab === 'products') loadProducts();
    else loadMonthly();
  }, [tab, loadProducts, loadMonthly]);
  useDataEvents(['Sale'], () => { if (tab === 'products') loadProducts(); else loadMonthly(); });

  const maxQty = products[0]?.totalQty ?? 1;
  const maxMonthAmt = monthly[0] ? Math.max(...monthly.map(m => Number(m.totalAmount))) : 1;

  return (
    <div className="p-4 md:p-6 space-y-4">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Trophy size={22} className="text-amber-500" />
        <h1 className="text-lg font-black text-slate-800">Sales Ranking</h1>
      </div>

      {/* Tab Switcher */}
      <div className="flex gap-2">
        <button
          onClick={() => setTab('products')}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
            tab === 'products' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
          }`}
        >
          <Package size={15} /> Top Products
        </button>
        <button
          onClick={() => setTab('monthly')}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
            tab === 'monthly' ? 'bg-indigo-600 text-white' : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
          }`}
        >
          <Calendar size={15} /> Monthly Trend
        </button>
      </div>

      {/* Product Ranking */}
      {tab === 'products' && (
        <>
          {/* Date filter */}
          <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-200 rounded-xl p-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">From</label>
              <input type="date" value={from} onChange={e => setFrom(e.target.value)}
                className="border rounded-lg px-3 py-1.5 text-sm" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">To</label>
              <input type="date" value={to} onChange={e => setTo(e.target.value)}
                className="border rounded-lg px-3 py-1.5 text-sm" />
            </div>
            <button onClick={loadProducts}
              className="px-4 py-1.5 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700">
              Apply
            </button>
            {(from || to) && (
              <button onClick={() => { setFrom(''); setTo(''); }}
                className="px-4 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">
                Clear
              </button>
            )}
          </div>

          {loading ? (
            <div className="text-center py-12 text-slate-400">Loading...</div>
          ) : products.length === 0 ? (
            <div className="text-center py-12 text-slate-400">No sales data found</div>
          ) : (
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left">#</th>
                    <th className="px-4 py-3 text-left">Product</th>
                    <th className="px-4 py-3 text-right">Qty Sold</th>
                    <th className="px-4 py-3 text-right">Revenue</th>
                    <th className="px-4 py-3 text-left hidden md:table-cell">Bar</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((p, i) => {
                    const pct = Math.round((p.totalQty / maxQty) * 100);
                    return (
                      <tr key={p.productId} className={`border-t ${i < 3 ? 'bg-amber-50/40' : 'hover:bg-slate-50'}`}>
                        <td className="px-4 py-3 font-black text-slate-500 text-base w-10">
                          {MEDAL[i] ?? <span className="text-xs text-slate-400">{i + 1}</span>}
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-semibold text-slate-800">{p.productName}</div>
                          <div className="text-xs text-slate-400">{p.productCode}</div>
                        </td>
                        <td className="px-4 py-3 text-right font-black text-indigo-700">{Number(p.totalQty).toLocaleString()}</td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-700">{Number(p.totalAmount).toLocaleString()} Ks</td>
                        <td className="px-4 py-3 hidden md:table-cell w-40">
                          <div className="bg-slate-100 rounded-full h-2">
                            <div className="bg-indigo-500 h-2 rounded-full transition-all" style={{ width: `${pct}%` }} />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Monthly Trend */}
      {tab === 'monthly' && (
        loading ? (
          <div className="text-center py-12 text-slate-400">Loading...</div>
        ) : monthly.length === 0 ? (
          <div className="text-center py-12 text-slate-400">No monthly data</div>
        ) : (
          <div className="space-y-3">
            {/* Summary cards — top 3 months */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {monthly.slice(0, 3).map((m, i) => (
                <div key={m.monthLabel} className={`rounded-xl p-4 border ${i === 0 ? 'bg-amber-50 border-amber-200' : 'bg-white border-slate-200'}`}>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-lg">{MEDAL[i] ?? ''}</span>
                    <span className="text-sm font-black text-slate-700">{MONTHS[m.month - 1]} {m.year}</span>
                  </div>
                  <div className="text-xl font-black text-indigo-700">{Number(m.totalAmount).toLocaleString()} <span className="text-xs font-normal text-slate-400">Ks</span></div>
                  <div className="text-xs text-slate-500 mt-0.5">{Number(m.totalQty).toLocaleString()} units sold</div>
                </div>
              ))}
            </div>

            {/* Full table */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr>
                    <th className="px-4 py-3 text-left">#</th>
                    <th className="px-4 py-3 text-left">Month</th>
                    <th className="px-4 py-3 text-right">Units Sold</th>
                    <th className="px-4 py-3 text-right">Revenue</th>
                    <th className="px-4 py-3 text-left hidden md:table-cell">Bar</th>
                  </tr>
                </thead>
                <tbody>
                  {monthly.map((m, i) => {
                    const pct = Math.round((Number(m.totalAmount) / maxMonthAmt) * 100);
                    return (
                      <tr key={m.monthLabel} className={`border-t ${i < 3 ? 'bg-amber-50/30' : 'hover:bg-slate-50'}`}>
                        <td className="px-4 py-3 text-base w-10">
                          {MEDAL[i] ?? <span className="text-xs text-slate-400">{i + 1}</span>}
                        </td>
                        <td className="px-4 py-3 font-semibold text-slate-800">
                          {MONTHS[m.month - 1]} {m.year}
                        </td>
                        <td className="px-4 py-3 text-right font-bold text-indigo-700">{Number(m.totalQty).toLocaleString()}</td>
                        <td className="px-4 py-3 text-right font-bold text-slate-700">{Number(m.totalAmount).toLocaleString()} Ks</td>
                        <td className="px-4 py-3 hidden md:table-cell w-40">
                          <div className="bg-slate-100 rounded-full h-2">
                            <div className="bg-emerald-500 h-2 rounded-full transition-all" style={{ width: `${pct}%` }} />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )
      )}
    </div>
  );
}
