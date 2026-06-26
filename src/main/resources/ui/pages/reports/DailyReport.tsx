import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../../hooks/useDataEvents';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, PieChart, Pie, Cell, ComposedChart, Line,
} from 'recharts';
import { summaryReportService } from '../../services/api';
import {
  CalendarDays, ShoppingCart, Wrench, Wallet,
  TrendingUp, BarChart2, MinusCircle, DollarSign,
  PackageX, RotateCcw, AlertTriangle,
} from 'lucide-react';

// ── Palette ────────────────────────────────────────────────
const C = {
  sales:    '#6366f1',
  service:  '#10b981',
  other:    '#f59e0b',
  expense:  '#f43f5e',
  profit:   '#0ea5e9',
  purchase: '#8b5cf6',
  loss:     '#fb923c',
};

const MONTH_NAMES = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

// ── Helpers ────────────────────────────────────────────────
const fmt = (v: any) => Number(v ?? 0).toLocaleString();
const n   = (v: any) => Number(v ?? 0);

const todayStr = () => new Date().toISOString().slice(0, 10);
const currentYearMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
};
const currentYear = () => new Date().getFullYear();
const monthRange  = (ym: string) => {
  const [y, m] = ym.split('-').map(Number);
  const last   = new Date(y, m, 0);
  return {
    from: `${y}-${String(m).padStart(2, '0')}-01`,
    to:   `${y}-${String(m).padStart(2, '0')}-${String(last.getDate()).padStart(2, '0')}`,
  };
};
const fmtMonth = (ym: string) => {
  const [y, m] = ym.split('-').map(Number);
  return `${MONTH_NAMES[m - 1]} ${y}`;
};

// ── Types ──────────────────────────────────────────────────
interface PeriodData {
  saleCount: number;
  saleRevenue: number; saleReturnAmount: number; netSaleRevenue: number;
  saleProfit: number;
  serviceRevenue: number; otherIncome: number; totalIncome: number;
  purchaseAmount: number; purchaseReturnAmount: number; netPurchaseCost: number;
  stockAdjLoss: number;
  totalExpenses: number; grossProfit: number; netProfit: number;
}
interface MonthlyRow {
  month: number; label: string; saleCount: number;
  saleRevenue: number; saleReturnAmount: number; netSaleRevenue: number;
  saleProfit: number; serviceRevenue: number; otherIncome: number;
  totalIncome: number; purchaseAmount: number; purchaseReturnAmount: number;
  netPurchaseCost: number; stockAdjLoss: number;
  totalExpenses: number; netProfit: number;
}
interface YearlyData {
  year: number; months: MonthlyRow[];
  totalSaleRevenue: number; totalSaleReturnAmount: number; totalNetSaleRevenue: number;
  totalServiceRevenue: number; totalOtherIncome: number; totalIncome: number;
  totalPurchaseAmount: number; totalPurchaseReturnAmount: number; totalNetPurchaseCost: number;
  totalStockAdjLoss: number; totalExpenses: number; totalNetProfit: number;
}

// ── Custom Tooltip ─────────────────────────────────────────
const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-slate-200 rounded-lg shadow-lg p-3 text-xs min-w-[160px]">
      <p className="font-bold text-slate-700 mb-2">{label}</p>
      {payload.map((p: any, i: number) => (
        <div key={i} className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-sm flex-shrink-0" style={{ background: p.color || p.fill }} />
            <span className="text-slate-500">{p.name}</span>
          </div>
          <span className="font-semibold text-slate-800">{n(p.value).toLocaleString()} Ks</span>
        </div>
      ))}
    </div>
  );
};

// ── Stat Card ──────────────────────────────────────────────
function StatCard({ label, value, sub, cls }: { label: string; value: string; sub?: string; cls?: string }) {
  return (
    <div className={`rounded-xl border p-4 bg-white ${cls ?? 'border-slate-200'}`}>
      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="text-lg font-black text-slate-800 mt-0.5">{value}</p>
      {sub && <p className="text-[10px] text-slate-400 mt-0.5">{sub}</p>}
    </div>
  );
}

// ── PeriodView (Daily / Monthly) ───────────────────────────
function PeriodView({ data, label }: { data: PeriodData; label: string }) {
  const saleCost = n(data.saleRevenue) - n(data.saleProfit);

  const pieData = [
    { name: 'Net Sales',    value: n(data.netSaleRevenue),  color: C.sales   },
    { name: 'Service',      value: n(data.serviceRevenue),  color: C.service },
    { name: 'Other Income', value: n(data.otherIncome),     color: C.other   },
  ].filter(d => d.value > 0);

  const profitBarData = [
    { name: 'Sale Profit',     value: n(data.saleProfit),       fill: C.sales    },
    { name: 'Service',         value: n(data.serviceRevenue),   fill: C.service  },
    { name: 'Other Income',    value: n(data.otherIncome),      fill: C.other    },
    { name: 'Sale Returns',    value: n(data.saleReturnAmount), fill: C.loss     },
    { name: 'Stock Adj Loss',  value: n(data.stockAdjLoss),     fill: C.purchase },
    { name: 'Expenses',        value: n(data.totalExpenses),    fill: C.expense  },
    { name: 'Net Profit',      value: n(data.netProfit),        fill: n(data.netProfit) >= 0 ? C.profit : C.expense },
  ];

  return (
    <div className="space-y-4">
      <div className="text-[11px] font-black uppercase tracking-widest text-slate-400 px-1">{label}</div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard label="Total Income"      value={`${fmt(data.totalIncome)} Ks`} />
        <StatCard label="Net Profit"        value={`${fmt(data.netProfit)} Ks`}
          cls={n(data.netProfit) >= 0 ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'} />
        <StatCard label="Net Purchase Cost" value={`${fmt(data.netPurchaseCost)} Ks`} cls="border-violet-100" />
        <StatCard label="Sale Transactions" value={`${data.saleCount}`} sub="vouchers" />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white border border-slate-200 rounded-xl p-4">
          <p className="text-xs font-black uppercase tracking-wide text-slate-500 mb-3">Income Breakdown</p>
          {pieData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%"
                  innerRadius="42%" outerRadius="68%"
                  dataKey="value" nameKey="name" paddingAngle={2}>
                  {pieData.map((d, i) => <Cell key={i} fill={d.color} />)}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend iconType="circle" iconSize={9} wrapperStyle={{ fontSize: '11px' }} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-[220px] flex items-center justify-center text-slate-300 text-sm">No income data</div>
          )}
        </div>

        <div className="bg-white border border-slate-200 rounded-xl p-4">
          <p className="text-xs font-black uppercase tracking-wide text-slate-500 mb-3">Profit Breakdown</p>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={profitBarData} layout="vertical"
              margin={{ left: 4, right: 20, top: 4, bottom: 4 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" tickFormatter={v => `${(n(v)/1000).toFixed(0)}K`} tick={{ fontSize: 10 }} />
              <YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 11 }} />
              <Tooltip content={<CustomTooltip />} />
              <Bar dataKey="value" radius={[0, 4, 4, 0]}>
                {profitBarData.map((d, i) => <Cell key={i} fill={d.fill} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Income section */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-4 py-2.5 border-b bg-indigo-50 flex items-center gap-2">
          <DollarSign size={14} className="text-indigo-600" />
          <span className="text-xs font-black text-indigo-700 uppercase tracking-wide">Income</span>
        </div>
        <div className="divide-y divide-slate-100">
          <div className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2.5">
              <ShoppingCart size={14} className="text-indigo-400" />
              <span className="text-sm text-slate-600">Sales <span className="text-[11px] text-slate-400">({data.saleCount} txn)</span></span>
            </div>
            <span className="font-bold text-sm text-indigo-700">{fmt(data.saleRevenue)} Ks</span>
          </div>
          {n(data.saleReturnAmount) > 0 && (
            <div className="flex items-center justify-between px-4 py-2.5 bg-orange-50">
              <div className="flex items-center gap-2.5 pl-5">
                <RotateCcw size={13} className="text-orange-400" />
                <span className="text-sm text-slate-500">(-) Sale Returns</span>
              </div>
              <span className="font-semibold text-sm text-orange-600">−{fmt(data.saleReturnAmount)} Ks</span>
            </div>
          )}
          <div className="flex items-center justify-between px-4 py-2.5 bg-slate-50">
            <span className="text-sm font-semibold text-slate-600 pl-5">Net Sales Revenue</span>
            <span className="font-bold text-sm text-slate-800">{fmt(data.netSaleRevenue)} Ks</span>
          </div>
          <div className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2.5">
              <Wrench size={14} className="text-emerald-400" />
              <span className="text-sm text-slate-600">Service Charges</span>
            </div>
            <span className="font-bold text-sm text-emerald-700">{fmt(data.serviceRevenue)} Ks</span>
          </div>
          <div className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2.5">
              <Wallet size={14} className="text-amber-400" />
              <span className="text-sm text-slate-600">Other Income</span>
            </div>
            <span className="font-bold text-sm text-amber-700">{fmt(data.otherIncome)} Ks</span>
          </div>
          <div className="flex items-center justify-between px-4 py-3 bg-indigo-50">
            <div className="flex items-center gap-2.5">
              <TrendingUp size={14} className="text-indigo-600" />
              <span className="text-sm font-black text-indigo-800">Total Income</span>
            </div>
            <span className="font-black text-base text-indigo-700">{fmt(data.totalIncome)} Ks</span>
          </div>
        </div>
      </div>

      {/* Purchase cash-flow section */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-4 py-2.5 border-b bg-violet-50 flex items-center gap-2">
          <PackageX size={14} className="text-violet-600" />
          <span className="text-xs font-black text-violet-700 uppercase tracking-wide">Stock Purchases (Cash Outflow)</span>
        </div>
        <div className="divide-y divide-slate-100">
          <div className="flex items-center justify-between px-4 py-3">
            <span className="text-sm text-slate-600">Purchase Amount</span>
            <span className="font-bold text-sm text-violet-700">{fmt(data.purchaseAmount)} Ks</span>
          </div>
          {n(data.purchaseReturnAmount) > 0 && (
            <div className="flex items-center justify-between px-4 py-2.5 bg-emerald-50">
              <div className="flex items-center gap-2.5 pl-5">
                <RotateCcw size={13} className="text-emerald-500" />
                <span className="text-sm text-slate-500">(-) Purchase Returns</span>
              </div>
              <span className="font-semibold text-sm text-emerald-600">−{fmt(data.purchaseReturnAmount)} Ks</span>
            </div>
          )}
          <div className="flex items-center justify-between px-4 py-2.5 bg-violet-50">
            <span className="text-sm font-black text-violet-800">Net Purchase Cost</span>
            <span className="font-black text-base text-violet-700">{fmt(data.netPurchaseCost)} Ks</span>
          </div>
        </div>
      </div>

      {/* Profit table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-4 py-2.5 border-b bg-rose-50 flex items-center gap-2">
          <BarChart2 size={14} className="text-rose-600" />
          <span className="text-xs font-black text-rose-700 uppercase tracking-wide">Profit &amp; Loss</span>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-slate-50 text-[11px] text-slate-400 uppercase">
              <th className="px-4 py-2 text-left font-semibold">Item</th>
              <th className="px-4 py-2 text-right font-semibold">Revenue</th>
              <th className="px-4 py-2 text-right font-semibold">Cost / Deduction</th>
              <th className="px-4 py-2 text-right font-semibold">Profit Impact</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            <tr>
              <td className="px-4 py-2.5 text-slate-600">
                <div className="flex items-center gap-2"><ShoppingCart size={13} className="text-indigo-400" /> Sales</div>
              </td>
              <td className="px-4 py-2.5 text-right text-slate-500">{fmt(data.saleRevenue)}</td>
              <td className="px-4 py-2.5 text-right text-rose-400">{fmt(saleCost)}</td>
              <td className="px-4 py-2.5 text-right font-bold text-indigo-700">{fmt(data.saleProfit)}</td>
            </tr>
            {n(data.saleReturnAmount) > 0 && (
              <tr className="bg-orange-50">
                <td className="px-4 py-2.5 text-slate-600">
                  <div className="flex items-center gap-2 pl-4"><RotateCcw size={13} className="text-orange-400" /> Sale Returns</div>
                </td>
                <td className="px-4 py-2.5 text-right text-slate-300">—</td>
                <td className="px-4 py-2.5 text-right text-orange-500">{fmt(data.saleReturnAmount)}</td>
                <td className="px-4 py-2.5 text-right font-bold text-orange-600">−{fmt(data.saleReturnAmount)}</td>
              </tr>
            )}
            <tr>
              <td className="px-4 py-2.5 text-slate-600">
                <div className="flex items-center gap-2"><Wrench size={13} className="text-emerald-400" /> Service Charges</div>
              </td>
              <td className="px-4 py-2.5 text-right text-slate-500">{fmt(data.serviceRevenue)}</td>
              <td className="px-4 py-2.5 text-right text-slate-300">—</td>
              <td className="px-4 py-2.5 text-right font-bold text-emerald-700">{fmt(data.serviceRevenue)}</td>
            </tr>
            <tr>
              <td className="px-4 py-2.5 text-slate-600">
                <div className="flex items-center gap-2"><Wallet size={13} className="text-amber-400" /> Other Income</div>
              </td>
              <td className="px-4 py-2.5 text-right text-slate-500">{fmt(data.otherIncome)}</td>
              <td className="px-4 py-2.5 text-right text-slate-300">—</td>
              <td className="px-4 py-2.5 text-right font-bold text-amber-700">{fmt(data.otherIncome)}</td>
            </tr>
            {n(data.stockAdjLoss) > 0 && (
              <tr className="bg-orange-50">
                <td className="px-4 py-2.5 text-slate-600">
                  <div className="flex items-center gap-2"><AlertTriangle size={13} className="text-orange-500" /> Stock Adj Loss</div>
                </td>
                <td className="px-4 py-2.5 text-right text-slate-300">—</td>
                <td className="px-4 py-2.5 text-right text-orange-500">{fmt(data.stockAdjLoss)}</td>
                <td className="px-4 py-2.5 text-right font-bold text-orange-600">−{fmt(data.stockAdjLoss)}</td>
              </tr>
            )}
            <tr className="bg-slate-50">
              <td className="px-4 py-2.5 font-semibold text-slate-700">
                <div className="flex items-center gap-2"><MinusCircle size={13} className="text-rose-400" /> Operating Expenses</div>
              </td>
              <td className="px-4 py-2.5 text-right text-slate-300">—</td>
              <td className="px-4 py-2.5 text-right text-rose-500 font-semibold">{fmt(data.totalExpenses)}</td>
              <td className="px-4 py-2.5 text-right font-bold text-rose-500">−{fmt(data.totalExpenses)}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-slate-300 bg-rose-50">
              <td className="px-4 py-3 font-black text-slate-800">Net Profit</td>
              <td className="px-4 py-3 text-right font-black text-slate-600">{fmt(data.totalIncome)}</td>
              <td className="px-4 py-3 text-right font-black text-rose-500">
                {fmt(saleCost + n(data.saleReturnAmount) + n(data.stockAdjLoss) + n(data.totalExpenses))}
              </td>
              <td className={`px-4 py-3 text-right font-black text-lg ${n(data.netProfit) >= 0 ? 'text-emerald-700' : 'text-rose-700'}`}>
                {fmt(data.netProfit)} Ks
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}

// ── YearlyView ─────────────────────────────────────────────
function YearlyView({ data }: { data: YearlyData }) {
  const chartData = data.months.map(m => ({
    label:          m.label,
    'Net Sales':    n(m.netSaleRevenue),
    Service:        n(m.serviceRevenue),
    Other:          n(m.otherIncome),
    'Net Profit':   n(m.netProfit),
  }));

  return (
    <div className="space-y-4">
      <div className="text-[11px] font-black uppercase tracking-widest text-slate-400 px-1">Yearly — {data.year}</div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <StatCard label="Total Income"      value={`${fmt(data.totalIncome)} Ks`} />
        <StatCard label="Net Profit"        value={`${fmt(data.totalNetProfit)} Ks`}
          cls={n(data.totalNetProfit) >= 0 ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'} />
        <StatCard label="Net Purchase Cost" value={`${fmt(data.totalNetPurchaseCost)} Ks`} cls="border-violet-100" />
        <StatCard label="Year"              value={`${data.year}`} sub="Annual Summary" />
      </div>

      {/* 12-month trend chart */}
      <div className="bg-white border border-slate-200 rounded-xl p-4">
        <p className="text-xs font-black uppercase tracking-wide text-slate-500 mb-4">12-Month Income &amp; Profit Trend</p>
        <ResponsiveContainer width="100%" height={300}>
          <ComposedChart data={chartData} margin={{ top: 4, right: 20, left: 0, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={v => `${(n(v) / 1000).toFixed(0)}K`} tick={{ fontSize: 10 }} />
            <Tooltip content={<CustomTooltip />} />
            <Legend iconType="circle" iconSize={9} wrapperStyle={{ fontSize: '11px' }} />
            <Bar dataKey="Net Sales" stackId="income" fill={C.sales}   />
            <Bar dataKey="Service"   stackId="income" fill={C.service} />
            <Bar dataKey="Other"     stackId="income" fill={C.other}   radius={[4, 4, 0, 0]} />
            <Line type="monotone" dataKey="Net Profit" stroke={C.profit}
              strokeWidth={2.5} dot={{ r: 4, fill: C.profit }} activeDot={{ r: 6 }} />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* Monthly breakdown table */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden">
        <div className="px-4 py-2.5 border-b bg-slate-50">
          <span className="text-xs font-black text-slate-600 uppercase tracking-wide">Monthly Breakdown</span>
        </div>
        <div className="overflow-auto">
          <table className="w-full min-w-[900px] text-xs">
            <thead className="bg-slate-50 border-b border-slate-200 text-[10px] font-semibold text-slate-400 uppercase sticky top-0">
              <tr>
                <th className="px-3 py-2.5 text-left">Month</th>
                <th className="px-3 py-2.5 text-right">Gross Sales</th>
                <th className="px-3 py-2.5 text-right text-orange-500">Returns</th>
                <th className="px-3 py-2.5 text-right">Net Sales</th>
                <th className="px-3 py-2.5 text-right">Service</th>
                <th className="px-3 py-2.5 text-right">Other</th>
                <th className="px-3 py-2.5 text-right">Total Income</th>
                <th className="px-3 py-2.5 text-right text-violet-500">Purchases</th>
                <th className="px-3 py-2.5 text-right text-orange-500">Stock Loss</th>
                <th className="px-3 py-2.5 text-right text-rose-500">Expenses</th>
                <th className="px-3 py-2.5 text-right">Net Profit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.months.map(m => (
                <tr key={m.month} className="hover:bg-slate-50 transition-colors">
                  <td className="px-3 py-2.5 font-semibold text-slate-700">{m.label}</td>
                  <td className="px-3 py-2.5 text-right text-indigo-600">{fmt(m.saleRevenue)}</td>
                  <td className="px-3 py-2.5 text-right text-orange-500">{n(m.saleReturnAmount) > 0 ? `−${fmt(m.saleReturnAmount)}` : '—'}</td>
                  <td className="px-3 py-2.5 text-right text-slate-700 font-semibold">{fmt(m.netSaleRevenue)}</td>
                  <td className="px-3 py-2.5 text-right text-emerald-600">{fmt(m.serviceRevenue)}</td>
                  <td className="px-3 py-2.5 text-right text-amber-600">{fmt(m.otherIncome)}</td>
                  <td className="px-3 py-2.5 text-right font-semibold text-slate-700">{fmt(m.totalIncome)}</td>
                  <td className="px-3 py-2.5 text-right text-violet-600">{fmt(m.netPurchaseCost)}</td>
                  <td className="px-3 py-2.5 text-right text-orange-500">{n(m.stockAdjLoss) > 0 ? fmt(m.stockAdjLoss) : '—'}</td>
                  <td className="px-3 py-2.5 text-right text-rose-500">{fmt(m.totalExpenses)}</td>
                  <td className={`px-3 py-2.5 text-right font-bold ${n(m.netProfit) >= 0 ? 'text-emerald-700' : 'text-rose-700'}`}>
                    {fmt(m.netProfit)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t-2 border-slate-300 bg-slate-50">
              <tr>
                <td className="px-3 py-3 font-black text-slate-800">Total</td>
                <td className="px-3 py-3 text-right font-bold text-indigo-600">{fmt(data.totalSaleRevenue)}</td>
                <td className="px-3 py-3 text-right font-bold text-orange-500">
                  {n(data.totalSaleReturnAmount) > 0 ? `−${fmt(data.totalSaleReturnAmount)}` : '—'}
                </td>
                <td className="px-3 py-3 text-right font-bold text-slate-800">{fmt(data.totalNetSaleRevenue)}</td>
                <td className="px-3 py-3 text-right font-bold text-emerald-600">{fmt(data.totalServiceRevenue)}</td>
                <td className="px-3 py-3 text-right font-bold text-amber-600">{fmt(data.totalOtherIncome)}</td>
                <td className="px-3 py-3 text-right font-black text-slate-800">{fmt(data.totalIncome)}</td>
                <td className="px-3 py-3 text-right font-bold text-violet-600">{fmt(data.totalNetPurchaseCost)}</td>
                <td className="px-3 py-3 text-right font-bold text-orange-500">
                  {n(data.totalStockAdjLoss) > 0 ? fmt(data.totalStockAdjLoss) : '—'}
                </td>
                <td className="px-3 py-3 text-right font-bold text-rose-500">{fmt(data.totalExpenses)}</td>
                <td className={`px-3 py-3 text-right font-black text-sm ${n(data.totalNetProfit) >= 0 ? 'text-emerald-700' : 'text-rose-700'}`}>
                  {fmt(data.totalNetProfit)} Ks
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>
  );
}

// ── Main ───────────────────────────────────────────────────
type Tab = 'daily' | 'monthly' | 'yearly';

export default function DailyReport() {
  const [tab, setTab] = useState<Tab>('daily');

  const [dailyData,   setDailyData]   = useState<PeriodData | null>(null);
  const [monthlyData, setMonthlyData] = useState<PeriodData | null>(null);
  const [yearlyData,  setYearlyData]  = useState<YearlyData | null>(null);

  const [dailyLoading,   setDailyLoading]   = useState(false);
  const [monthlyLoading, setMonthlyLoading] = useState(false);
  const [yearlyLoading,  setYearlyLoading]  = useState(false);

  const [date,  setDate]  = useState(todayStr());
  const [month, setMonth] = useState(currentYearMonth());
  const [year,  setYear]  = useState(currentYear());

  const loadDaily = useCallback(async () => {
    setDailyLoading(true);
    try {
      const res = await summaryReportService.daily(date, date);
      setDailyData(res.data as PeriodData);
    } catch {}
    setDailyLoading(false);
  }, [date]);

  const loadMonthly = useCallback(async () => {
    setMonthlyLoading(true);
    try {
      const { from, to } = monthRange(month);
      const res = await summaryReportService.daily(from, to);
      setMonthlyData(res.data as PeriodData);
    } catch {}
    setMonthlyLoading(false);
  }, [month]);

  const loadYearly = useCallback(async () => {
    setYearlyLoading(true);
    try {
      const res = await summaryReportService.yearly(year);
      setYearlyData(res.data as YearlyData);
    } catch {}
    setYearlyLoading(false);
  }, [year]);

  useEffect(() => { if (tab === 'daily')   loadDaily();   }, [tab, loadDaily]);
  useEffect(() => { if (tab === 'monthly') loadMonthly(); }, [tab, loadMonthly]);
  useEffect(() => { if (tab === 'yearly')  loadYearly();  }, [tab, loadYearly]);
  useDataEvents(['Sale', 'Service Job', 'Expense', 'Income'], () => {
    if (tab === 'daily') loadDaily();
    else if (tab === 'monthly') loadMonthly();
    else loadYearly();
  });

  const tabs: { key: Tab; label: string; color: string }[] = [
    { key: 'daily',   label: 'Daily',   color: 'bg-indigo-600' },
    { key: 'monthly', label: 'Monthly', color: 'bg-emerald-600' },
    { key: 'yearly',  label: 'Yearly',  color: 'bg-violet-600' },
  ];

  return (
    <div className="p-4 md:p-6 space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3">
        <CalendarDays size={22} className="text-indigo-500" />
        <h1 className="text-lg font-black text-slate-800">Income &amp; Profit Report</h1>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-100 p-1 rounded-xl w-fit">
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-5 py-1.5 rounded-lg text-sm font-bold transition-all ${
              tab === t.key
                ? 'bg-white text-indigo-700 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {/* ── Daily ── */}
      {tab === 'daily' && (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-200 rounded-xl p-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Date</label>
              <input type="date" value={date} onChange={e => setDate(e.target.value)}
                className="border rounded-lg px-3 py-1.5 text-sm" />
            </div>
            <button onClick={loadDaily}
              className="px-4 py-1.5 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700">
              Apply
            </button>
            {date !== todayStr() && (
              <button onClick={() => setDate(todayStr())}
                className="px-3 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">
                Today
              </button>
            )}
          </div>
          {dailyLoading
            ? <div className="text-center py-16 text-slate-400">Loading...</div>
            : dailyData && <PeriodView data={dailyData} label={`Daily — ${date}`} />
          }
        </div>
      )}

      {/* ── Monthly ── */}
      {tab === 'monthly' && (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-200 rounded-xl p-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Month</label>
              <input type="month" value={month} onChange={e => setMonth(e.target.value)}
                className="border rounded-lg px-3 py-1.5 text-sm" />
            </div>
            <button onClick={loadMonthly}
              className="px-4 py-1.5 bg-emerald-600 text-white text-sm rounded-lg hover:bg-emerald-700">
              Apply
            </button>
            {month !== currentYearMonth() && (
              <button onClick={() => setMonth(currentYearMonth())}
                className="px-3 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">
                This Month
              </button>
            )}
          </div>
          {monthlyLoading
            ? <div className="text-center py-16 text-slate-400">Loading...</div>
            : monthlyData && <PeriodView data={monthlyData} label={`Monthly — ${fmtMonth(month)}`} />
          }
        </div>
      )}

      {/* ── Yearly ── */}
      {tab === 'yearly' && (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-3 items-end bg-white border border-slate-200 rounded-xl p-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Year</label>
              <input type="number" value={year} onChange={e => setYear(Number(e.target.value))}
                min={2020} max={2099} className="border rounded-lg px-3 py-1.5 text-sm w-24" />
            </div>
            <button onClick={loadYearly}
              className="px-4 py-1.5 bg-violet-600 text-white text-sm rounded-lg hover:bg-violet-700">
              Apply
            </button>
            {year !== currentYear() && (
              <button onClick={() => setYear(currentYear())}
                className="px-3 py-1.5 bg-slate-100 text-slate-600 text-sm rounded-lg hover:bg-slate-200">
                This Year
              </button>
            )}
          </div>
          {yearlyLoading
            ? <div className="text-center py-16 text-slate-400">Loading...</div>
            : yearlyData && <YearlyView data={yearlyData} />
          }
        </div>
      )}
    </div>
  );
}
