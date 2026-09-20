import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import {
  AlertTriangle,
  Database,
  Download,
  Play,
  RefreshCw,
  Search,
  ShieldCheck,
  Terminal,
} from 'lucide-react';
import { adminQueryService } from '../services/api';
import { toCsv as serializeCsv } from '../utils/csv';
import { getFromSession } from '../utils/storageHelper';

type AdminQueryDefinition = {
  id: string;
  name: string;
  description: string;
  category: string;
};

type AdminQueryResult = {
  queryId: string;
  queryName: string;
  columns: string[];
  rows: unknown[][];
  rowCount: number;
  elapsedMs: number;
  truncated: boolean;
  affectedRows?: number | null;
  mode?: string;
  message?: string;
};

type SqlMode = 'READ' | 'WRITE';

type SessionUser = {
  roles?: string[];
  permissions?: string[];
};

const isAdmin = (user: SessionUser) =>
  (user.roles || []).some(r => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR');

const hasPerm = (user: SessionUser, perm: string) =>
  isAdmin(user) || (user.permissions || []).includes(perm);

const fmtCell = (value: unknown) => {
  if (value == null) return '—';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (value instanceof Date) return value.toLocaleString();
  return value;
};

const toCsv = (columns: string[], rows: unknown[][]) =>
  serializeCsv([columns, ...rows.map((row) => row.map(fmtCell))]);

const ResultsPanel: React.FC<{ result: AdminQueryResult | null; onExport?: () => void }> = ({ result, onExport }) => {
  if (!result) {
    return (
      <div className="flex min-h-[280px] items-center justify-center p-8 text-sm text-slate-400">
        Query Run နှိပ်ပါ
      </div>
    );
  }

  return (
    <div className="p-4 space-y-3">
      <div className="flex flex-wrap gap-3 text-xs text-slate-600">
        {result.mode && (
          <span className="rounded-full bg-indigo-100 px-3 py-1 font-semibold text-indigo-800">
            {result.mode}
          </span>
        )}
        {result.mode === 'WRITE' ? (
          <span className="rounded-full bg-slate-100 px-3 py-1 font-semibold">
            affected: {result.affectedRows ?? 0}
          </span>
        ) : (
          <span className="rounded-full bg-slate-100 px-3 py-1 font-semibold">
            {result.rowCount} rows
          </span>
        )}
        <span className="rounded-full bg-slate-100 px-3 py-1 font-semibold">
          {result.elapsedMs} ms
        </span>
        {result.truncated && (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-3 py-1 font-semibold text-amber-800">
            <AlertTriangle size={12} />
            Max rows reached — truncated
          </span>
        )}
      </div>

      {result.message && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">
          {result.message}
        </div>
      )}

      {result.mode !== 'WRITE' && result.rows.length === 0 && (
        <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-sm text-slate-500">
          ရလဒ် မရှိပါ (0 rows)
        </div>
      )}

      {result.rows.length > 0 && (
        <div className="overflow-auto max-h-[calc(100vh-320px)] rounded-xl border border-slate-200">
          <table className="min-w-full text-xs">
            <thead className="sticky top-0 bg-slate-50">
              <tr>
                {result.columns.map(col => (
                  <th key={col} className="whitespace-nowrap border-b border-slate-200 px-3 py-2 text-left font-bold text-slate-700">
                    {col}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row, ri) => (
                <tr key={ri} className="odd:bg-white even:bg-slate-50/60">
                  {row.map((cell, ci) => (
                    <td key={ci} className="whitespace-nowrap border-b border-slate-100 px-3 py-2 text-slate-700">
                      {fmtCell(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {onExport && result.rows.length > 0 && (
        <button
          type="button"
          onClick={onExport}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
        >
          <Download size={14} />
          Export CSV
        </button>
      )}
    </div>
  );
};

const AdminQueryPage: React.FC = () => {
  const sessionUser = useMemo<SessionUser>(() => {
    try {
      return JSON.parse(getFromSession('sspd_user') || '{}') as SessionUser;
    } catch {
      return {};
    }
  }, []);

  const canRead = hasPerm(sessionUser, 'CAN_ACCESS_ADMIN_QUERY_READ');
  const canWrite = hasPerm(sessionUser, 'CAN_ACCESS_ADMIN_QUERY_WRITE');
  const [featureEnabled, setFeatureEnabled] = useState<boolean | null>(null);

  const [tab, setTab] = useState<'console' | 'catalog'>(canRead ? 'console' : 'catalog');
  const [sql, setSql] = useState('SELECT * FROM products LIMIT 20');
  const [sqlMode, setSqlMode] = useState<SqlMode>('READ');
  const [runningCustom, setRunningCustom] = useState(false);
  const [customResult, setCustomResult] = useState<AdminQueryResult | null>(null);

  const [queries, setQueries] = useState<AdminQueryDefinition[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [runningId, setRunningId] = useState<string | null>(null);
  const [catalogResult, setCatalogResult] = useState<AdminQueryResult | null>(null);
  const [filter, setFilter] = useState('');
  const [activeCategory, setActiveCategory] = useState<string>('');

  const loadQueries = useCallback(async () => {
    if (!canRead || featureEnabled !== true) {
      setQueries([]);
      setLoadingList(false);
      return;
    }
    setLoadingList(true);
    try {
      const res = await adminQueryService.list();
      const items = res.data ?? [];
      setQueries(items);
      setActiveCategory(prev => prev || (items[0]?.category ?? ''));
    } catch (e: any) {
      Swal.fire('မအောင်မြင်ပါ', e?.message || 'Query list load failed', 'error');
    } finally {
      setLoadingList(false);
    }
  }, [canRead, featureEnabled]);

  useEffect(() => {
    void adminQueryService.status()
      .then(response => setFeatureEnabled(response.data?.enabled === true))
      .catch(() => setFeatureEnabled(false));
  }, []);

  useEffect(() => {
    if (featureEnabled === true) void loadQueries();
  }, [featureEnabled, loadQueries]);

  const categories = useMemo(() => [...new Set(queries.map(q => q.category))], [queries]);

  const filteredQueries = useMemo(() => {
    const q = filter.trim().toLowerCase();
    return queries.filter(item => {
      if (activeCategory && item.category !== activeCategory) return false;
      if (!q) return true;
      return item.name.toLowerCase().includes(q)
        || item.description.toLowerCase().includes(q)
        || item.id.toLowerCase().includes(q);
    });
  }, [queries, filter, activeCategory]);

  const runCustom = async () => {
    if (featureEnabled !== true) return;
    const trimmed = sql.trim();
    if (!trimmed) {
      Swal.fire('SQL ထည့်ပါ', 'Query ရိုက်ထည့်ပြီး Run နှိပ်ပါ', 'warning');
      return;
    }
    if (sqlMode === 'READ' && !canRead) {
      Swal.fire('ခွင့်ပြုချက် မရှိပါ', 'CAN_ACCESS_ADMIN_QUERY_READ လိုအပ်သည်', 'warning');
      return;
    }
    if (sqlMode === 'WRITE' && !canWrite) {
      Swal.fire('ခွင့်ပြုချက် မရှိပါ', 'CAN_ACCESS_ADMIN_QUERY_WRITE လိုအပ်သည်', 'warning');
      return;
    }
    if (sqlMode === 'WRITE') {
      const confirm = await Swal.fire({
        icon: 'warning',
        title: 'Write SQL Run?',
        text: 'INSERT / UPDATE / DELETE က database ကို ပြောင်းပါမည်။ ဆက်လုပ်မလား?',
        showCancelButton: true,
        confirmButtonText: 'Run',
        cancelButtonText: 'Cancel',
        confirmButtonColor: '#dc2626',
      });
      if (!confirm.isConfirmed) return;
    }

    setRunningCustom(true);
    setCustomResult(null);
    try {
      const res = await adminQueryService.execute(trimmed, sqlMode);
      if (!res.success || !res.data) throw new Error(res.message || 'Query failed');
      setCustomResult(res.data);
    } catch (e: any) {
      Swal.fire('Query မအောင်မြင်ပါ', e?.message || 'Run failed', 'error');
    } finally {
      setRunningCustom(false);
    }
  };

  const runCatalogQuery = async (query: AdminQueryDefinition) => {
    if (featureEnabled !== true) return;
    setRunningId(query.id);
    setCatalogResult(null);
    try {
      const res = await adminQueryService.run(query.id);
      if (!res.success || !res.data) throw new Error(res.message || 'Query failed');
      setCatalogResult(res.data);
    } catch (e: any) {
      Swal.fire('Query မအောင်မြင်ပါ', e?.message || 'Run failed', 'error');
    } finally {
      setRunningId(null);
    }
  };

  const exportCsv = (result: AdminQueryResult | null) => {
    if (!result || result.rows.length === 0) return;
    const blob = new Blob([toCsv(result.columns, result.rows)], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${result.queryId}-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (featureEnabled === null) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
        SQL Console availability စစ်ဆေးနေသည်...
      </div>
    );
  }

  if (!featureEnabled) {
    return (
      <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-center text-amber-900">
        <ShieldCheck size={28} className="mx-auto mb-3" />
        <h1 className="text-lg font-black">SQL Console disabled</h1>
        <p className="mt-2 text-sm">
          Server configuration မှာ explicit opt-in မလုပ်ထားသောကြောင့် query execution ပိတ်ထားသည်။
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-10">
      <div>
        <h1 className="text-2xl font-black text-slate-900 flex items-center gap-2">
          <Terminal size={24} className="text-indigo-600" />
          SQL Console
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          SQL ရိုက်ပြီး run — Permission ဖြင့် READ/WRITE ခွဲထိန်းချုပ်ထားသည်
        </p>
      </div>

      <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 flex gap-3">
        <ShieldCheck size={18} className="shrink-0 mt-0.5" />
        <div>
          <p className="font-semibold">Permission</p>
          <p className="mt-0.5 text-amber-800">
            READ: {canRead ? '✓ CAN_ACCESS_ADMIN_QUERY_READ' : '✗ မရှိ'}
            {' · '}
            WRITE: {canWrite ? '✓ CAN_ACCESS_ADMIN_QUERY_WRITE' : '✗ မရှိ'}
            {' · '}
            DROP/TRUNCATE/ALTER blocked · Query run တိုင်း Audit Log မှတ်ပါသည်
          </p>
        </div>
      </div>

      <div className="flex gap-2 border-b border-slate-200">
        {canRead && (
          <button
            type="button"
            onClick={() => setTab('console')}
            className={`px-4 py-2 text-sm font-bold border-b-2 -mb-px ${
              tab === 'console' ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-500'
            }`}
          >
            SQL ရိုက် Run
          </button>
        )}
        {canRead && (
          <button
            type="button"
            onClick={() => setTab('catalog')}
            className={`px-4 py-2 text-sm font-bold border-b-2 -mb-px ${
              tab === 'catalog' ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-500'
            }`}
          >
            Diagnostic Queries
          </button>
        )}
      </div>

      {tab === 'console' && canRead && (
        <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="border-b border-slate-100 px-4 py-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-bold text-slate-800">SQL Editor</p>
              <div className="flex gap-2">
                <select
                  value={sqlMode}
                  onChange={e => setSqlMode(e.target.value as SqlMode)}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold"
                  disabled={sqlMode === 'WRITE' && !canWrite}
                >
                  <option value="READ">READ (SELECT)</option>
                  {canWrite && <option value="WRITE">WRITE (INSERT/UPDATE/DELETE)</option>}
                </select>
                <button
                  type="button"
                  disabled={runningCustom || (sqlMode === 'WRITE' && !canWrite)}
                  onClick={() => void runCustom()}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-bold text-white hover:bg-indigo-700 disabled:opacity-50"
                >
                  <Play size={14} />
                  {runningCustom ? 'Running...' : 'Run'}
                </button>
              </div>
            </div>
            <textarea
              value={sql}
              onChange={e => setSql(e.target.value)}
              spellCheck={false}
              className="min-h-[320px] w-full resize-y border-0 bg-slate-950 p-4 font-mono text-sm text-emerald-300 focus:outline-none focus:ring-0"
              placeholder="SELECT * FROM products LIMIT 20"
            />
            <div className="border-t border-slate-100 px-4 py-2 text-[11px] text-slate-500">
              READ: SELECT, SHOW, DESCRIBE, EXPLAIN · WRITE: INSERT, UPDATE, DELETE · statement တစ်ခုသာ
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="border-b border-slate-100 px-4 py-3 flex items-center gap-2 text-sm font-bold text-slate-800">
              <Database size={16} className="text-indigo-600" />
              {customResult?.queryName || 'Results'}
            </div>
            <ResultsPanel result={customResult} onExport={() => exportCsv(customResult)} />
          </div>
        </div>
      )}

      {tab === 'catalog' && canRead && (
        <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
          <div className="space-y-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="relative">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  value={filter}
                  onChange={e => setFilter(e.target.value)}
                  placeholder="Query ရှာပါ..."
                  className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3 text-sm"
                />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {categories.map(cat => (
                  <button
                    key={cat}
                    type="button"
                    onClick={() => setActiveCategory(cat)}
                    className={`rounded-full px-3 py-1 text-xs font-semibold border ${
                      activeCategory === cat
                        ? 'border-indigo-300 bg-indigo-50 text-indigo-700'
                        : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => void loadQueries()}
                className="mt-3 inline-flex items-center gap-2 text-xs font-semibold text-slate-600 hover:text-indigo-600"
              >
                <RefreshCw size={14} className={loadingList ? 'animate-spin' : ''} />
                Refresh list
              </button>
            </div>

            <div className="space-y-2">
              {loadingList && (
                <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-500">
                  Loading...
                </div>
              )}
              {!loadingList && filteredQueries.map(query => (
                <div key={query.id} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-bold text-slate-900">{query.name}</p>
                      <p className="mt-1 text-xs text-slate-500">{query.description}</p>
                    </div>
                    <button
                      type="button"
                      disabled={runningId != null}
                      onClick={() => void runCatalogQuery(query)}
                      className="inline-flex shrink-0 items-center gap-1.5 rounded-xl bg-indigo-600 px-3 py-2 text-xs font-bold text-white hover:bg-indigo-700 disabled:opacity-50"
                    >
                      <Play size={12} />
                      {runningId === query.id ? '...' : 'Run'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="border-b border-slate-100 px-4 py-3 flex items-center gap-2 text-sm font-bold text-slate-800">
              <Database size={16} className="text-indigo-600" />
              {catalogResult?.queryName || 'Results'}
            </div>
            <ResultsPanel result={catalogResult} onExport={() => exportCsv(catalogResult)} />
          </div>
        </div>
      )}

      {!canRead && canWrite && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-800">
          WRITE permission သာ ရှိပြီး READ permission မရှိပါ — SELECT verify မလုပ်နိုင်သေးပါ။ Role Management မှာ READ permission ထည့်ပေးပါ။
        </div>
      )}
    </div>
  );
};

export default AdminQueryPage;
