
import React, { useState, useEffect } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell, PieChart, Pie, Legend
} from 'recharts';
import { TrendingUp, TrendingDown, RefreshCw, FileText } from 'lucide-react';
import { profitLossService } from '../services/profitlossapiservice';
import { ProfitLossDTO, ProfitLossLineItem } from '../types';

const today      = () => new Date().toISOString().slice(0, 10);
const firstOfYear = () => `${new Date().getFullYear()}-01-01`;

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const fmtK = (v: number) =>
  v >= 1_000_000
    ? `${(v / 1_000_000).toFixed(1)}M`
    : v >= 1_000
    ? `${(v / 1_000).toFixed(0)}K`
    : String(v);

// ── Stat card ─────────────────────────────────────────────────────────────────
const Kard = ({
  label, value, sub, accent
}: { label: string; value: string; sub?: string; accent: string }) => (
  <div className={`rounded-lg border px-4 py-3 ${accent}`}>
    <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-1">{label}</p>
    <p className="text-lg font-bold tabular-nums text-slate-800">{value}</p>
    {sub && <p className="text-[10px] text-slate-400 mt-0.5">{sub}</p>}
  </div>
);

// ── Tooltip ───────────────────────────────────────────────────────────────────
const BarTip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-slate-200 rounded-lg shadow-lg px-3 py-2 text-xs">
      <p className="font-bold text-slate-600 mb-1">{label}</p>
      {payload.map((p: any) => (
        <p key={p.name} style={{ color: p.fill }}>
          <strong>Ks {fmt(p.value)}</strong>
        </p>
      ))}
    </div>
  );
};

// ── Main ──────────────────────────────────────────────────────────────────────
const ProfitLossReport: React.FC = () => {
  const [from, setFrom]       = useState(firstOfYear());
  const [to, setTo]           = useState(today());
  const [data, setData]       = useState<ProfitLossDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const fetchReport = async () => {
    if (!from || !to) return;
    setLoading(true);
    setError('');
    try {
      setData(await profitLossService.getReport(from, to));
    } catch (e: any) {
      setError(e?.message || 'Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void fetchReport(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Return'], fetchReport);

  const isProfit = (data?.netProfit ?? 0) >= 0;

  // Bar chart: key figures
  const barData = data
    ? [
        { name: 'Net Revenue',    value: data.netRevenue,    fill: '#6366f1' },
        { name: 'Net Purchases',  value: data.netPurchases,  fill: '#f59e0b' },
        { name: 'Gross Profit',   value: data.grossProfit,   fill: '#10b981' },
        { name: 'Expenses',       value: data.totalExpenses, fill: '#f43f5e' },
        { name: 'Net Profit',     value: Math.abs(data.netProfit), fill: isProfit ? '#6366f1' : '#94a3b8' },
      ]
    : [];

  // Pie: expense + other income breakdown
  const pieData = data
    ? [
        ...data.otherIncomeItems.map(i => ({ name: i.accountName, value: i.amount })),
        ...data.expenseItems.map(i => ({ name: i.accountName, value: i.amount })),
      ]
    : [];

  const PIE_COLORS = ['#10b981','#34d399','#6ee7b7','#f43f5e','#fb7185','#fda4af','#6366f1','#818cf8','#f59e0b'];

  return (
    <div className="p-4 space-y-4">

      {/* ── Top bar ────────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
            <FileText size={16} />
          </div>
          <div>
            <h1 className="text-sm font-bold text-slate-800">Profit &amp; Loss Report</h1>
            <p className="text-[10px] text-slate-400">Revenue · COGS · Other Income · Expenses</p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 items-end">
          <div className="flex flex-col gap-0.5">
            <label className="text-[10px] font-semibold text-slate-500">From</label>
            <input type="date" value={from} onChange={e => setFrom(e.target.value)}
              className="border border-slate-300 rounded px-2.5 py-1 text-xs text-slate-700 focus:outline-none focus:border-indigo-400" />
          </div>
          <div className="flex flex-col gap-0.5">
            <label className="text-[10px] font-semibold text-slate-500">To</label>
            <input type="date" value={to} onChange={e => setTo(e.target.value)}
              className="border border-slate-300 rounded px-2.5 py-1 text-xs text-slate-700 focus:outline-none focus:border-indigo-400" />
          </div>
          <button onClick={fetchReport} disabled={loading}
            className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold px-3 py-1.5 rounded disabled:opacity-60">
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
            {loading ? 'Loading...' : 'Generate'}
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg px-4 py-2">{error}</div>
      )}

      {!data && !loading && (
        <div className="text-center text-slate-400 text-sm py-20">
          Select a date range and click <strong>Generate</strong> to view the report.
        </div>
      )}

      {data && (
        <>
          {/* ── Summary cards ──────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Kard label="Net Revenue"   value={`Ks ${fmt(data.netRevenue)}`}   accent="bg-indigo-50 border-indigo-100" />
            <Kard label="Gross Profit"  value={`Ks ${fmt(data.grossProfit)}`}  accent="bg-emerald-50 border-emerald-100" />
            <Kard label="Total Expenses" value={`Ks ${fmt(data.totalExpenses)}`} accent="bg-rose-50 border-rose-100" />
            <Kard
              label={isProfit ? 'Net Profit' : 'Net Loss'}
              value={`Ks ${fmt(Math.abs(data.netProfit))}`}
              sub={isProfit ? '▲ Profit' : '▼ Loss'}
              accent={isProfit ? 'bg-indigo-50 border-indigo-200' : 'bg-rose-50 border-rose-200'}
            />
          </div>

          {/* ── Main 2-col ─────────────────────────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start">

            {/* LEFT — Statement ─────────────────────────────────────────── */}
            <div className="lg:col-span-3 bg-white border border-slate-200 rounded-lg overflow-hidden text-sm flex flex-col">

              {/* title bar — sticky */}
              <div className="bg-slate-800 text-white px-4 py-2.5 flex justify-between items-center flex-shrink-0">
                <span className="font-bold">P&amp;L Statement</span>
                <span className="text-[10px] text-slate-400">{data.from} → {data.to}</span>
              </div>

              {/* scrollable body */}
              <div className="overflow-y-auto" style={{ maxHeight: '520px' }}>

              {/* ① REVENUE */}
              <SecHead label="Revenue" />
              <DeductRow label="Gross Sales"    value={data.grossSales}    indent />
              <DeductRow label="(-) Sales Returns" value={data.salesReturns} indent deduct />
              <SubtotalRow label="Net Revenue" value={data.netRevenue} color="indigo" />

              {/* ② COST OF GOODS SOLD */}
              <SecHead label="Cost of Goods Sold" />
              <DeductRow label="Purchases"              value={data.purchases}       indent />
              <DeductRow label="(-) Purchase Returns"   value={data.purchaseReturns} indent deduct />
              <SubtotalRow label="Net Purchases" value={data.netPurchases} color="amber" />

              {/* ③ GROSS PROFIT */}
              <GrossProfitRow value={data.grossProfit} />

              {/* ④ OTHER INCOME */}
              {data.otherIncomeItems.length > 0 && (
                <>
                  <SecHead label="Other Income" />
                  {data.otherIncomeItems.map(item => <LineRow key={item.accountCode} item={item} />)}
                  <SubtotalRow label="Total Other Income" value={data.totalOtherIncome} color="emerald" />
                </>
              )}

              {/* ⑤ EXPENSES */}
              <SecHead label="Expenses" />
              {data.expenseItems.length === 0
                ? <EmptyRow />
                : data.expenseItems.map(item => <LineRow key={item.accountCode} item={item} />)
              }
              <SubtotalRow label="Total Expenses" value={data.totalExpenses} color="rose" />

              </div>{/* end scrollable body */}

              {/* ⑥ NET PROFIT — always visible at bottom */}
              <div className={`px-4 py-3 flex justify-between items-center border-t-2 flex-shrink-0
                ${isProfit ? 'bg-indigo-50 border-indigo-400' : 'bg-rose-50 border-rose-400'}`}>
                <div className="flex items-center gap-2">
                  <span className={`p-1 rounded ${isProfit ? 'bg-indigo-100 text-indigo-600' : 'bg-rose-100 text-rose-600'}`}>
                    {isProfit ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
                  </span>
                  <div>
                    <p className={`font-bold ${isProfit ? 'text-indigo-800' : 'text-rose-800'}`}>Net Profit</p>
                    <p className={`text-[9px] ${isProfit ? 'text-indigo-500' : 'text-rose-500'}`}>
                      Gross Profit + Other Income − Expenses
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className={`font-bold tabular-nums text-base ${isProfit ? 'text-indigo-700' : 'text-rose-700'}`}>
                    Ks {fmt(data.netProfit)}
                  </p>
                  <p className={`text-[9px] font-bold uppercase tracking-wider ${isProfit ? 'text-indigo-400' : 'text-rose-400'}`}>
                    {isProfit ? 'Profit' : 'Loss'}
                  </p>
                </div>
              </div>
            </div>

            {/* RIGHT — Charts ───────────────────────────────────────────── */}
            <div className="lg:col-span-2 space-y-4">

              {/* Bar chart */}
              <div className="bg-white border border-slate-200 rounded-lg p-4">
                <p className="text-xs font-bold text-slate-700 mb-3">Key Figures</p>
                <div className="h-[200px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={barData} barCategoryGap="30%">
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                      <XAxis dataKey="name" axisLine={false} tickLine={false}
                        tick={{ fill: '#94a3b8', fontSize: 9, fontWeight: 600 }} interval={0} />
                      <YAxis axisLine={false} tickLine={false}
                        tick={{ fill: '#94a3b8', fontSize: 9 }}
                        tickFormatter={fmtK} width={36} />
                      <Tooltip content={<BarTip />} cursor={{ fill: '#f8fafc' }} />
                      <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                        {barData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>

              {/* Pie chart */}
              {pieData.length > 0 && (
                <div className="bg-white border border-slate-200 rounded-lg p-4">
                  <p className="text-xs font-bold text-slate-700 mb-3">Income &amp; Expense Breakdown</p>
                  <div className="h-[210px]">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie data={pieData} cx="50%" cy="44%"
                          innerRadius={42} outerRadius={68}
                          paddingAngle={2} dataKey="value">
                          {pieData.map((_, i) => (
                            <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip
                          formatter={(v: number) => `Ks ${fmt(v)}`}
                          contentStyle={{ fontSize: '11px', borderRadius: '6px', border: 'none', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}
                        />
                        <Legend iconType="circle" iconSize={7}
                          wrapperStyle={{ fontSize: '10px', lineHeight: '1.8' }} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

// ── Sub-components ─────────────────────────────────────────────────────────────

const SecHead = ({ label }: { label: string }) => (
  <div className="px-4 py-1.5 bg-slate-100 border-t border-slate-200">
    <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">{label}</span>
  </div>
);

const DeductRow = ({
  label, value, indent, deduct
}: { label: string; value: number; indent?: boolean; deduct?: boolean }) => (
  <div className="px-4 py-2 flex justify-between items-center border-b border-slate-50 hover:bg-slate-50 text-sm">
    <span className={`text-slate-600 ${indent ? 'pl-2' : ''}`}>{label}</span>
    <span className={`tabular-nums ${deduct ? 'text-rose-500' : 'text-slate-700'} font-medium`}>
      {deduct ? `(${fmt(value)})` : `Ks ${fmt(value)}`}
    </span>
  </div>
);

const LineRow: React.FC<{ item: ProfitLossLineItem }> = ({ item }) => (
  <div className="px-4 py-2 flex justify-between items-center border-b border-slate-50 hover:bg-slate-50 text-sm">
    <div className="flex items-center gap-2 pl-2">
      <span className="text-[9px] font-mono bg-slate-100 text-slate-400 px-1.5 py-0.5 rounded">
        {item.accountCode}
      </span>
      <span className="text-slate-600">{item.accountName}</span>
    </div>
    <span className="font-medium text-slate-700 tabular-nums ml-4">Ks {fmt(item.amount)}</span>
  </div>
);

const SubtotalRow = ({
  label, value, color
}: { label: string; value: number; color: 'indigo' | 'amber' | 'emerald' | 'rose' }) => {
  const styles: Record<string, string> = {
    indigo:  'bg-indigo-50  border-indigo-100  text-indigo-800  [&_span]:text-indigo-700',
    amber:   'bg-amber-50   border-amber-100   text-amber-800   [&_span]:text-amber-700',
    emerald: 'bg-emerald-50 border-emerald-100 text-emerald-800 [&_span]:text-emerald-700',
    rose:    'bg-rose-50    border-rose-100    text-rose-800    [&_span]:text-rose-700',
  };
  return (
    <div className={`px-4 py-2 flex justify-between items-center border-t text-sm ${styles[color]}`}>
      <span className="font-bold">{label}</span>
      <span className="font-bold tabular-nums">Ks {fmt(value)}</span>
    </div>
  );
};

const GrossProfitRow = ({ value }: { value: number }) => (
  <div className="px-4 py-2.5 flex justify-between items-center bg-emerald-600 text-sm">
    <span className="font-bold text-white">Gross Profit</span>
    <span className="font-bold text-white tabular-nums">Ks {fmt(value)}</span>
  </div>
);

const EmptyRow = () => (
  <div className="px-4 py-2 text-xs text-slate-400 italic pl-6">No records</div>
);

export default ProfitLossReport;
