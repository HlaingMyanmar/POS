import React, { useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { CheckCircle, XCircle, RefreshCw, Scale, Database } from 'lucide-react';
import { financialReportService } from '../services/financialreportapiservice';
import { adminService } from '../services/api';
import { TrialBalanceDTO } from '../types';

const today = () => new Date().toISOString().slice(0, 10);

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const TYPE_BADGE: Record<string, string> = {
  Asset:     'bg-blue-100   text-blue-700',
  Liability: 'bg-orange-100 text-orange-700',
  Equity:    'bg-purple-100 text-purple-700',
  Income:    'bg-emerald-100 text-emerald-700',
  Expense:   'bg-rose-100   text-rose-700',
};

const TYPE_ORDER = ['Asset', 'Liability', 'Equity', 'Income', 'Expense'];

const TrialBalanceReport: React.FC = () => {
  const [asOf, setAsOf]           = useState(today());
  const [data, setData]           = useState<TrialBalanceDTO | null>(null);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [backfilling, setBackfilling] = useState(false);
  const [backfillMsg, setBackfillMsg] = useState('');

  const fetchReport = async () => {
    if (!asOf) return;
    setLoading(true);
    setError('');
    try {
      setData(await financialReportService.getTrialBalance(asOf));
    } catch (e: any) {
      setError(e?.message || 'Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Journal'], fetchReport);

  const handleBackfill = async () => {
    setBackfilling(true);
    setBackfillMsg('');
    try {
      const res = await adminService.backfillJournals();
      const d = (res as any)?.data as Record<string, number> | undefined;
      if (d) {
        setBackfillMsg(`Done — Sales: ${d.sales}, Purchases: ${d.purchases}, Expenses: ${d.expenses}. Click Generate to refresh.`);
      } else {
        setBackfillMsg('Backfill complete. Click Generate to refresh.');
      }
    } catch (e: any) {
      setBackfillMsg('Backfill failed: ' + (e?.message || 'unknown error'));
    } finally {
      setBackfilling(false);
    }
  };

  // Group lines by account type, respecting display order
  const grouped = data
    ? TYPE_ORDER
        .map(type => ({
          type,
          lines: data.lines.filter(l => l.accountType === type),
        }))
        .filter(g => g.lines.length > 0)
    : [];

  const diff = data ? Math.abs(data.grandTotalDebit - data.grandTotalCredit) : 0;

  return (
    <div className="p-4 space-y-4">

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="p-2 bg-violet-50 text-violet-600 rounded-lg">
            <Scale size={16} />
          </div>
          <div>
            <h1 className="text-sm font-bold text-slate-800">Trial Balance</h1>
            <p className="text-[10px] text-slate-400">All accounts · Debit = Credit verification</p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2 items-end">
          <div className="flex flex-col gap-0.5">
            <label className="text-[10px] font-semibold text-slate-500">As of Date</label>
            <input type="date" value={asOf} onChange={e => setAsOf(e.target.value)}
              className="border border-slate-300 rounded px-2.5 py-1 text-xs text-slate-700 focus:outline-none focus:border-violet-400" />
          </div>
          <button onClick={fetchReport} disabled={loading}
            className="flex items-center gap-1.5 bg-violet-600 hover:bg-violet-700 text-white text-xs font-semibold px-3 py-1.5 rounded disabled:opacity-60">
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
            {loading ? 'Loading...' : 'Generate'}
          </button>
          <button onClick={handleBackfill} disabled={backfilling}
            title="Create journal entries for existing sales/purchases/expenses that are missing them"
            className="flex items-center gap-1.5 bg-amber-500 hover:bg-amber-600 text-white text-xs font-semibold px-3 py-1.5 rounded disabled:opacity-60">
            <Database size={12} className={backfilling ? 'animate-pulse' : ''} />
            {backfilling ? 'Backfilling...' : 'Backfill Data'}
          </button>
        </div>
      </div>

      {backfillMsg && (
        <div className="bg-amber-50 border border-amber-200 text-amber-800 text-xs rounded-lg px-4 py-2">{backfillMsg}</div>
      )}

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
            data.balanced
              ? 'bg-emerald-50 border-emerald-200'
              : 'bg-rose-50 border-rose-200'
          }`}>
            {data.balanced
              ? <CheckCircle size={18} className="text-emerald-500 shrink-0" />
              : <XCircle    size={18} className="text-rose-500 shrink-0" />
            }
            <div>
              <p className={`text-sm font-bold ${data.balanced ? 'text-emerald-700' : 'text-rose-700'}`}>
                {data.balanced ? 'Books are Balanced' : 'Out of Balance'}
              </p>
              <p className="text-[10px] text-slate-500">
                {data.balanced
                  ? `Total Debit = Total Credit = Ks ${fmt(data.grandTotalDebit)}`
                  : `Difference: Ks ${fmt(diff)} — check for missing journal entries`
                }
              </p>
            </div>
          </div>

          {/* ── Summary cards ───────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="Total Accounts"  value={String(data.lines.length)}                  accent="bg-slate-50 border-slate-200" />
            <StatCard label="Total Debit"     value={`Ks ${fmt(data.grandTotalDebit)}`}          accent="bg-blue-50 border-blue-100" />
            <StatCard label="Total Credit"    value={`Ks ${fmt(data.grandTotalCredit)}`}         accent="bg-emerald-50 border-emerald-100" />
            <StatCard label="Difference"      value={`Ks ${fmt(diff)}`}                          accent={diff === 0 ? 'bg-slate-50 border-slate-200' : 'bg-rose-50 border-rose-200'} />
          </div>

          {/* ── Table ───────────────────────────────────────────────────────── */}
          <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
            <div className="bg-slate-800 text-white px-4 py-2.5 flex justify-between items-center">
              <span className="font-bold text-sm">Trial Balance</span>
              <span className="text-[10px] text-slate-400">As of {data.asOf}</span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th className="text-left px-3 py-2 font-bold text-slate-500 uppercase tracking-wider w-24">Code</th>
                    <th className="text-left px-3 py-2 font-bold text-slate-500 uppercase tracking-wider">Account Name</th>
                    <th className="text-left px-3 py-2 font-bold text-slate-500 uppercase tracking-wider w-28">Type</th>
                    <th className="text-right px-3 py-2 font-bold text-blue-500 uppercase tracking-wider w-36">Debit (DR)</th>
                    <th className="text-right px-3 py-2 font-bold text-emerald-500 uppercase tracking-wider w-36">Credit (CR)</th>
                  </tr>
                </thead>
                <tbody>
                  {grouped.map(group => (
                    <React.Fragment key={group.type}>
                      {/* group header */}
                      <tr className="bg-slate-100">
                        <td colSpan={5} className="px-3 py-1.5">
                          <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">
                            {group.type} Accounts
                          </span>
                        </td>
                      </tr>
                      {group.lines.map((line, idx) => (
                        <tr key={line.accountCode}
                          className={`border-b border-slate-50 hover:bg-slate-50 ${idx % 2 === 0 ? '' : 'bg-slate-50/40'}`}>
                          <td className="px-3 py-2 font-mono text-slate-400">{line.accountCode}</td>
                          <td className="px-3 py-2 text-slate-700 font-medium">{line.accountName}</td>
                          <td className="px-3 py-2">
                            <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded-full ${TYPE_BADGE[line.accountType] || 'bg-slate-100 text-slate-500'}`}>
                              {line.accountType}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums">
                            {line.totalDebit > 0
                              ? <span className="font-semibold text-blue-700">Ks {fmt(line.totalDebit)}</span>
                              : <span className="text-slate-300">—</span>
                            }
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums">
                            {line.totalCredit > 0
                              ? <span className="font-semibold text-emerald-700">Ks {fmt(line.totalCredit)}</span>
                              : <span className="text-slate-300">—</span>
                            }
                          </td>
                        </tr>
                      ))}
                    </React.Fragment>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="bg-slate-800 text-white">
                    <td colSpan={3} className="px-3 py-3 font-bold text-sm">Grand Total</td>
                    <td className="px-3 py-3 text-right tabular-nums font-bold text-blue-300">
                      Ks {fmt(data.grandTotalDebit)}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums font-bold text-emerald-300">
                      Ks {fmt(data.grandTotalCredit)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

const StatCard = ({ label, value, accent }: { label: string; value: string; accent: string }) => (
  <div className={`rounded-lg border px-4 py-3 ${accent}`}>
    <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-1">{label}</p>
    <p className="text-sm font-bold tabular-nums text-slate-800">{value}</p>
  </div>
);

export default TrialBalanceReport;
