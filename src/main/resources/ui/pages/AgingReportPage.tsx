import React, { useEffect, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { financialReportService } from '../services/financialreportapiservice';
import { AgingLineItem, AgingReportDTO } from '../types';

type ViewMode = 'invoice' | 'party';

const fmt = (v?: number) => Number(v || 0).toLocaleString();

const BUCKET_META: Record<string, { label: string; color: string; bg: string; border: string }> = {
  Current: { label: 'Not Yet Due', color: 'text-emerald-700', bg: 'bg-emerald-50', border: 'border-emerald-300' },
  '0-30': { label: '0–30 Days', color: 'text-yellow-800', bg: 'bg-yellow-50', border: 'border-yellow-300' },
  '31-60': { label: '31–60 Days', color: 'text-orange-700', bg: 'bg-orange-50', border: 'border-orange-300' },
  '61-90': { label: '61–90 Days', color: 'text-red-700', bg: 'bg-red-50', border: 'border-red-300' },
  '>90': { label: 'Over 90 Days', color: 'text-rose-800', bg: 'bg-rose-100', border: 'border-rose-400' },
};

const BUCKET_ORDER = ['Current', '0-30', '31-60', '61-90', '>90'];

const AgingReportPage: React.FC<{ type: 'ar' | 'ap' }> = ({ type }) => {
  const [asOf, setAsOf] = useState(new Date().toISOString().slice(0, 10));
  const [data, setData] = useState<AgingReportDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<ViewMode>('party');
  const [search, setSearch] = useState('');
  const [bucketFilter, setBucketFilter] = useState<string | null>(null);
  const [expandedParty, setExpandedParty] = useState<string | null>(null);

  const partyLabel = type === 'ar' ? 'Customer' : 'Supplier';

  const load = async () => {
    setLoading(true);
    try {
      const res = type === 'ar'
        ? await financialReportService.getArAging(asOf)
        : await financialReportService.getApAging(asOf);
      setData(res);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [type]);
  useDataEvents(['Sale', 'Purchase', 'Customer Payment'], load);

  // ── Filter / group lines ─────────────────────────────────────────
  const filteredLines = useMemo(() => {
    if (!data) return [];
    return data.lines.filter(l => {
      if (bucketFilter && l.bucket !== bucketFilter) return false;
      if (search.trim() && !`${l.partyName} ${l.referenceNo}`.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [data, bucketFilter, search]);

  type PartyGroup = {
    partyName: string;
    invoices: AgingLineItem[];
    totalDue: number;
    oldestDays: number;
    worstBucket: string;
  };

  const partyGroups: PartyGroup[] = useMemo(() => {
    const map = new Map<string, PartyGroup>();
    for (const line of filteredLines) {
      const existing = map.get(line.partyName);
      if (existing) {
        existing.invoices.push(line);
        existing.totalDue += Number(line.dueAmount || 0);
        if (line.daysPastDue > existing.oldestDays) {
          existing.oldestDays = line.daysPastDue;
          existing.worstBucket = line.bucket;
        }
      } else {
        map.set(line.partyName, {
          partyName: line.partyName,
          invoices: [line],
          totalDue: Number(line.dueAmount || 0),
          oldestDays: line.daysPastDue,
          worstBucket: line.bucket,
        });
      }
    }
    return Array.from(map.values()).sort((a, b) => b.totalDue - a.totalDue);
  }, [filteredLines]);

  const total = data?.totalOutstanding || 0;

  // ── Render ────────────────────────────────────────────────────────
  return (
    <div className="p-4 space-y-4">
      {/* Title + controls */}
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <h2 className="text-lg font-bold">
            {type === 'ar' ? 'Accounts Receivable Aging' : 'Accounts Payable Aging'}
          </h2>
          <p className="text-xs text-gray-500">
            {type === 'ar' ? 'Customers ဆီက ရရန်ရှိ' : 'Suppliers ကို ပေးရန်ရှိ'}
          </p>
        </div>
        <div className="flex-1" />
        <div>
          <label className="block text-xs text-gray-500 mb-0.5">As Of</label>
          <input
            className="border rounded px-2 py-1 text-sm"
            type="date"
            value={asOf}
            onChange={e => setAsOf(e.target.value)}
          />
        </div>
        <button
          className="bg-indigo-600 text-white px-4 py-1.5 rounded text-sm hover:bg-indigo-700 disabled:opacity-50"
          onClick={load}
          disabled={loading}
        >
          {loading ? 'Loading…' : 'Refresh'}
        </button>
        <button
          className="border px-3 py-1.5 rounded text-sm hover:bg-slate-50"
          onClick={() => window.print()}
        >
          🖨 Print
        </button>
      </div>

      {!data ? (
        <div className="text-center py-12 text-gray-400 border rounded">
          Click <b>Refresh</b> to generate the aging report.
        </div>
      ) : (
        <>
          {/* Bucket cards (clickable filters) */}
          <div className="grid grid-cols-2 md:grid-cols-6 gap-2">
            <button
              onClick={() => setBucketFilter(null)}
              className={`border-2 rounded p-3 text-left transition ${
                bucketFilter === null ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200 bg-white hover:bg-slate-50'
              }`}
            >
              <div className="text-xs text-gray-500">Total Outstanding</div>
              <div className="text-base font-bold text-indigo-700">{fmt(total)}</div>
              <div className="text-[10px] text-gray-400 mt-0.5">
                {data.totalInvoices} invoice{(data.totalInvoices || 0) !== 1 ? 's' : ''} · {data.totalParties} {partyLabel.toLowerCase()}{(data.totalParties || 0) !== 1 ? 's' : ''}
              </div>
            </button>
            {BUCKET_ORDER.map(b => {
              const meta = BUCKET_META[b];
              const value =
                b === 'Current' ? data.bucketCurrent || 0 :
                b === '0-30' ? data.bucket0To30 :
                b === '31-60' ? data.bucket31To60 :
                b === '61-90' ? data.bucket61To90 :
                data.bucketOver90;
              const pct = total > 0 ? (value / total) * 100 : 0;
              const active = bucketFilter === b;
              return (
                <button
                  key={b}
                  onClick={() => setBucketFilter(active ? null : b)}
                  className={`border-2 rounded p-3 text-left transition ${
                    active ? `border-indigo-500 ${meta.bg}` : `${meta.border} ${meta.bg} hover:opacity-80`
                  }`}
                >
                  <div className={`text-xs ${meta.color} font-medium`}>{meta.label}</div>
                  <div className={`text-base font-bold ${meta.color}`}>{fmt(value)}</div>
                  <div className="h-1 bg-white rounded-full mt-1 overflow-hidden">
                    <div className={`h-full ${meta.color.replace('text-', 'bg-')}`} style={{ width: `${pct}%` }} />
                  </div>
                  <div className="text-[10px] text-gray-500 mt-0.5">{pct.toFixed(1)}%</div>
                </button>
              );
            })}
          </div>

          {/* Search + view toggle */}
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="text"
              placeholder={`Search ${partyLabel.toLowerCase()} or invoice…`}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="border rounded px-3 py-1.5 text-sm flex-1 min-w-[180px]"
            />
            <div className="inline-flex rounded border overflow-hidden text-sm">
              <button
                onClick={() => setView('party')}
                className={`px-3 py-1.5 ${view === 'party' ? 'bg-indigo-600 text-white' : 'bg-white hover:bg-slate-50'}`}
              >
                By {partyLabel}
              </button>
              <button
                onClick={() => setView('invoice')}
                className={`px-3 py-1.5 ${view === 'invoice' ? 'bg-indigo-600 text-white' : 'bg-white hover:bg-slate-50'}`}
              >
                By Invoice
              </button>
            </div>
            {bucketFilter && (
              <button
                onClick={() => setBucketFilter(null)}
                className="text-xs px-2 py-1 bg-slate-100 hover:bg-slate-200 rounded"
              >
                Clear filter: {BUCKET_META[bucketFilter]?.label} ✕
              </button>
            )}
          </div>

          {filteredLines.length === 0 ? (
            <div className="text-center py-12 text-gray-400 border rounded bg-white">
              {data.lines.length === 0
                ? '🎉 No outstanding balances.'
                : 'No invoices match the current filter.'}
            </div>
          ) : view === 'party' ? (
            <PartyView groups={partyGroups} expandedParty={expandedParty} setExpandedParty={setExpandedParty} partyLabel={partyLabel} />
          ) : (
            <InvoiceView lines={filteredLines} partyLabel={partyLabel} />
          )}
        </>
      )}
    </div>
  );
};

// ── Party-grouped View ──────────────────────────────────────────────

const PartyView: React.FC<{
  groups: { partyName: string; invoices: AgingLineItem[]; totalDue: number; oldestDays: number; worstBucket: string }[];
  expandedParty: string | null;
  setExpandedParty: (p: string | null) => void;
  partyLabel: string;
}> = ({ groups, expandedParty, setExpandedParty, partyLabel }) => (
  <div className="bg-white border rounded overflow-hidden">
    <table className="w-full text-sm">
      <thead className="bg-slate-100 text-left">
        <tr>
          <th className="p-2 w-6"></th>
          <th className="p-2">{partyLabel}</th>
          <th className="p-2 text-right">Invoices</th>
          <th className="p-2 text-right">Total Due</th>
          <th className="p-2 text-right">Oldest (days)</th>
          <th className="p-2">Worst Bucket</th>
        </tr>
      </thead>
      <tbody>
        {groups.map(g => {
          const expanded = expandedParty === g.partyName;
          const meta = BUCKET_META[g.worstBucket];
          return (
            <React.Fragment key={g.partyName}>
              <tr
                onClick={() => setExpandedParty(expanded ? null : g.partyName)}
                className="border-t cursor-pointer hover:bg-slate-50"
              >
                <td className="p-2 text-gray-400">{expanded ? '▾' : '▸'}</td>
                <td className="p-2 font-medium">{g.partyName}</td>
                <td className="p-2 text-right text-gray-600">{g.invoices.length}</td>
                <td className="p-2 text-right font-bold text-indigo-700">{fmt(g.totalDue)}</td>
                <td className="p-2 text-right">
                  {g.oldestDays > 0
                    ? <span className="text-red-600">{g.oldestDays}</span>
                    : <span className="text-gray-400">—</span>}
                </td>
                <td className="p-2">
                  <span className={`text-xs px-2 py-0.5 rounded ${meta?.bg} ${meta?.color}`}>
                    {meta?.label}
                  </span>
                </td>
              </tr>
              {expanded && (
                <tr>
                  <td></td>
                  <td colSpan={5} className="p-0 bg-slate-50">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="text-left text-gray-500">
                          <th className="px-2 py-1.5">Invoice</th>
                          <th className="px-2 py-1.5">Date</th>
                          <th className="px-2 py-1.5">Due Date</th>
                          <th className="px-2 py-1.5 text-right">Original</th>
                          <th className="px-2 py-1.5 text-right">Paid</th>
                          <th className="px-2 py-1.5 text-right">Due</th>
                          <th className="px-2 py-1.5 text-right">Days</th>
                          <th className="px-2 py-1.5">Bucket</th>
                        </tr>
                      </thead>
                      <tbody>
                        {g.invoices.map(inv => <InvoiceRow key={inv.referenceNo} inv={inv} />)}
                      </tbody>
                    </table>
                  </td>
                </tr>
              )}
            </React.Fragment>
          );
        })}
      </tbody>
    </table>
  </div>
);

// ── Flat Invoice View ───────────────────────────────────────────────

const InvoiceView: React.FC<{ lines: AgingLineItem[]; partyLabel: string }> = ({ lines, partyLabel }) => (
  <div className="bg-white border rounded overflow-hidden overflow-x-auto">
    <table className="w-full text-xs">
      <thead className="bg-slate-100 text-left">
        <tr>
          <th className="p-2">Invoice</th>
          <th className="p-2">{partyLabel}</th>
          <th className="p-2">Invoice Date</th>
          <th className="p-2">Due Date</th>
          <th className="p-2 text-right">Original</th>
          <th className="p-2 text-right">Paid</th>
          <th className="p-2 text-right">Due</th>
          <th className="p-2 text-right">Days</th>
          <th className="p-2">Bucket</th>
        </tr>
      </thead>
      <tbody>
        {lines.map(line => (
          <tr key={line.referenceNo} className="border-t hover:bg-slate-50">
            <td className="p-2 font-mono">{line.referenceNo}</td>
            <td className="p-2">{line.partyName}</td>
            <td className="p-2 text-gray-600">{line.invoiceDate}</td>
            <td className="p-2 text-gray-600">{line.dueDate}</td>
            <td className="p-2 text-right">{fmt(line.originalAmount)}</td>
            <td className="p-2 text-right text-gray-500">{fmt(line.paidAmount)}</td>
            <td className="p-2 text-right font-semibold text-indigo-700">{fmt(line.dueAmount)}</td>
            <td className="p-2 text-right">
              {line.daysPastDue > 0
                ? <span className="text-red-600 font-medium">{line.daysPastDue}</span>
                : line.daysToDue
                  ? <span className="text-gray-500">in {line.daysToDue}d</span>
                  : <span className="text-gray-400">due</span>}
            </td>
            <td className="p-2">
              <BucketBadge bucket={line.bucket} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const InvoiceRow: React.FC<{ inv: AgingLineItem }> = ({ inv }) => (
  <tr className="border-t border-slate-200 hover:bg-white">
    <td className="px-2 py-1.5 font-mono">{inv.referenceNo}</td>
    <td className="px-2 py-1.5 text-gray-600">{inv.invoiceDate}</td>
    <td className="px-2 py-1.5 text-gray-600">{inv.dueDate}</td>
    <td className="px-2 py-1.5 text-right">{fmt(inv.originalAmount)}</td>
    <td className="px-2 py-1.5 text-right text-gray-500">{fmt(inv.paidAmount)}</td>
    <td className="px-2 py-1.5 text-right font-semibold text-indigo-700">{fmt(inv.dueAmount)}</td>
    <td className="px-2 py-1.5 text-right">
      {inv.daysPastDue > 0
        ? <span className="text-red-600 font-medium">{inv.daysPastDue}</span>
        : inv.daysToDue
          ? <span className="text-gray-500">in {inv.daysToDue}d</span>
          : <span className="text-gray-400">due</span>}
    </td>
    <td className="px-2 py-1.5">
      <BucketBadge bucket={inv.bucket} />
    </td>
  </tr>
);

const BucketBadge: React.FC<{ bucket: string }> = ({ bucket }) => {
  const meta = BUCKET_META[bucket];
  if (!meta) return <>{bucket}</>;
  return (
    <span className={`text-xs px-2 py-0.5 rounded ${meta.bg} ${meta.color}`}>
      {meta.label}
    </span>
  );
};

export default AgingReportPage;
