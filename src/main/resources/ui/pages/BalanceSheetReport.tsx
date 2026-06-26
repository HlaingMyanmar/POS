import React, { useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { CheckCircle, XCircle, RefreshCw, BarChart2, TrendingUp, TrendingDown } from 'lucide-react';
import { financialReportService } from '../services/financialreportapiservice';
import { BalanceSheetDTO } from '../types';

const today = () => new Date().toISOString().slice(0, 10);

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const BalanceSheetReport: React.FC = () => {
  const [asOf, setAsOf]       = useState(today());
  const [data, setData]       = useState<BalanceSheetDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const fetchReport = async () => {
    if (!asOf) return;
    setLoading(true);
    setError('');
    try {
      setData(await financialReportService.getBalanceSheet(asOf));
    } catch (e: any) {
      setError(e?.message || 'Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Journal'], fetchReport);

  const isProfit = (data?.currentYearPnL ?? 0) >= 0;
  const diff = data ? Math.abs(data.totalAssets - data.totalLiabilitiesAndEquity) : 0;

  return (
    <div className="p-4 space-y-4">

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg">
            <BarChart2 size={16} />
          </div>
          <div>
            <h1 className="text-sm font-bold text-slate-800">Balance Sheet</h1>
            <p className="text-[10px] text-slate-400">Assets = Liabilities + Equity</p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 items-end">
          <div className="flex flex-col gap-0.5">
            <label className="text-[10px] font-semibold text-slate-500">As of Date</label>
            <input type="date" value={asOf} onChange={e => setAsOf(e.target.value)}
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
          Select an "as of" date and click <strong>Generate</strong>.
        </div>
      )}

      {data && (
        <>
          {/* ── Balance status banner ───────────────────────────────────────── */}
          <div className={`flex items-center gap-3 rounded-lg border px-4 py-3 ${
            data.balanced ? 'bg-emerald-50 border-emerald-200' : 'bg-rose-50 border-rose-200'
          }`}>
            {data.balanced
              ? <CheckCircle size={18} className="text-emerald-500 shrink-0" />
              : <XCircle    size={18} className="text-rose-500 shrink-0" />
            }
            <div>
              <p className={`text-sm font-bold ${data.balanced ? 'text-emerald-700' : 'text-rose-700'}`}>
                {data.balanced ? 'Balance Sheet Balances' : 'Balance Sheet Does Not Balance'}
              </p>
              <p className="text-[10px] text-slate-500">
                {data.balanced
                  ? `Assets (Ks ${fmt(data.totalAssets)}) = Liabilities + Equity (Ks ${fmt(data.totalLiabilitiesAndEquity)})`
                  : `Difference: Ks ${fmt(diff)} — check journal entries for missing postings`
                }
              </p>
            </div>
          </div>

          {/* ── Summary cards ───────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="Total Assets"      value={`Ks ${fmt(data.totalAssets)}`}      accent="bg-blue-50 border-blue-100" />
            <StatCard label="Total Liabilities" value={`Ks ${fmt(data.totalLiabilities)}`} accent="bg-orange-50 border-orange-100" />
            <StatCard label="Total Equity"      value={`Ks ${fmt(data.totalEquity)}`}      accent="bg-purple-50 border-purple-100" />
            <StatCard
              label={isProfit ? 'Net Profit' : 'Net Loss'}
              value={`Ks ${fmt(Math.abs(data.currentYearPnL))}`}
              accent={isProfit ? 'bg-emerald-50 border-emerald-100' : 'bg-rose-50 border-rose-100'}
            />
          </div>

          {/* ── Two-column statement ─────────────────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 items-start">

            {/* LEFT — Assets ────────────────────────────────────────────────── */}
            <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
              <SectionHeader label="Assets" color="blue" date={data.asOf} />
              <div className="divide-y divide-slate-50">
                {data.assets.length === 0
                  ? <EmptyRow />
                  : data.assets.map(item => <AccountRow key={item.accountCode} item={item} color="blue" />)
                }
              </div>
              <TotalRow label="Total Assets" value={data.totalAssets} color="blue" />
            </div>

            {/* RIGHT — Liabilities + Equity ─────────────────────────────────── */}
            <div className="space-y-4">

              {/* Liabilities */}
              <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
                <SectionHeader label="Liabilities" color="orange" date={data.asOf} />
                <div className="divide-y divide-slate-50">
                  {data.liabilities.length === 0
                    ? <EmptyRow />
                    : data.liabilities.map(item => <AccountRow key={item.accountCode} item={item} color="orange" />)
                  }
                </div>
                <TotalRow label="Total Liabilities" value={data.totalLiabilities} color="orange" />
              </div>

              {/* Equity */}
              <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
                <SectionHeader label="Equity" color="purple" date={data.asOf} />
                <div className="divide-y divide-slate-50">
                  {data.equityItems.length === 0
                    ? <EmptyRow />
                    : data.equityItems.map(item => <AccountRow key={item.accountCode} item={item} color="purple" />)
                  }
                  {/* Current Year P/L line */}
                  <div className={`px-4 py-2.5 flex justify-between items-center hover:bg-slate-50`}>
                    <div className="flex items-center gap-2 pl-2">
                      <span className={`p-0.5 rounded ${isProfit ? 'text-emerald-500' : 'text-rose-500'}`}>
                        {isProfit ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
                      </span>
                      <span className="text-slate-600 text-xs">Accumulated Profit / Loss</span>
                    </div>
                    <span className={`tabular-nums text-xs font-semibold ${isProfit ? 'text-emerald-700' : 'text-rose-700'}`}>
                      {isProfit ? '' : '('}{`Ks ${fmt(Math.abs(data.currentYearPnL))}`}{isProfit ? '' : ')'}
                    </span>
                  </div>
                </div>
                <TotalRow label="Total Equity" value={data.totalEquity} color="purple" />
              </div>

              {/* Grand total L+E */}
              <div className="bg-slate-800 text-white rounded-lg px-4 py-3 flex justify-between items-center">
                <span className="font-bold text-sm">Total Liabilities + Equity</span>
                <span className="font-bold tabular-nums text-sm">Ks {fmt(data.totalLiabilitiesAndEquity)}</span>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

// ── Sub-components ──────────────────────────────────────────────────────────────

const COLOR_MAP = {
  blue:   { header: 'bg-blue-600',   total: 'bg-blue-50 border-blue-200 text-blue-800',   badge: 'text-blue-700'   },
  orange: { header: 'bg-orange-600', total: 'bg-orange-50 border-orange-200 text-orange-800', badge: 'text-orange-700' },
  purple: { header: 'bg-purple-600', total: 'bg-purple-50 border-purple-200 text-purple-800', badge: 'text-purple-700' },
};

const SectionHeader = ({ label, color, date }: { label: string; color: keyof typeof COLOR_MAP; date: string }) => (
  <div className={`${COLOR_MAP[color].header} text-white px-4 py-2.5 flex justify-between items-center`}>
    <span className="font-bold text-sm">{label}</span>
    <span className="text-[10px] opacity-70">as of {date}</span>
  </div>
);

const AccountRow = ({ item, color }: { item: { accountCode: string; accountName: string; balance: number }; color: keyof typeof COLOR_MAP }) => (
  <div className="px-4 py-2.5 flex justify-between items-center hover:bg-slate-50">
    <div className="flex items-center gap-2 pl-2">
      <span className="text-[9px] font-mono bg-slate-100 text-slate-400 px-1.5 py-0.5 rounded">{item.accountCode}</span>
      <span className="text-slate-600 text-xs">{item.accountName}</span>
    </div>
    <span className={`tabular-nums text-xs font-semibold ${COLOR_MAP[color].badge}`}>
      Ks {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(item.balance)}
    </span>
  </div>
);

const TotalRow = ({ label, value, color }: { label: string; value: number; color: keyof typeof COLOR_MAP }) => (
  <div className={`px-4 py-2.5 flex justify-between items-center border-t font-bold text-sm ${COLOR_MAP[color].total}`}>
    <span>{label}</span>
    <span className="tabular-nums">Ks {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)}</span>
  </div>
);

const EmptyRow = () => (
  <div className="px-6 py-3 text-xs text-slate-400 italic">No accounts with balances</div>
);

const StatCard = ({ label, value, accent }: { label: string; value: string; accent: string }) => (
  <div className={`rounded-lg border px-4 py-3 ${accent}`}>
    <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-1">{label}</p>
    <p className="text-sm font-bold tabular-nums text-slate-800">{value}</p>
  </div>
);

export default BalanceSheetReport;
