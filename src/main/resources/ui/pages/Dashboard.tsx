
import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { useNavigate } from 'react-router-dom';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Cell,
} from 'recharts';
import {
  TrendingUp, DollarSign, Package, Users, Wrench, ArrowRight,
  Clock, Loader2, ShoppingCart, Truck, CreditCard, BookOpen,
  BarChart3, ChevronRight, FileText, ShoppingBag, AlertTriangle,
  AlertCircle, CheckCircle, Database, Zap, Plus, Calendar,
  RefreshCw,
} from 'lucide-react';
import { DashboardStats, AppRoute } from '../types';
import { dashboardService, adminService } from '../services/api';

const money = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(v || 0);

const fmtDate = (iso: string) => {
  if (!iso) return '-';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

// ── Alert Banner ───────────────────────────────────────────────────────────────
const AlertBanner = ({
  type, icon, title, body, action, onAction, loading,
}: {
  type: 'error' | 'warning' | 'info' | 'success';
  icon: React.ReactNode;
  title: string;
  body: string;
  action?: string;
  onAction?: () => void;
  loading?: boolean;
}) => {
  const styles = {
    error:   'bg-rose-50 border-rose-200 text-rose-800',
    warning: 'bg-amber-50 border-amber-200 text-amber-800',
    info:    'bg-blue-50 border-blue-200 text-blue-800',
    success: 'bg-emerald-50 border-emerald-200 text-emerald-800',
  };
  const btnStyles = {
    error:   'bg-rose-600 hover:bg-rose-700 text-white',
    warning: 'bg-amber-600 hover:bg-amber-700 text-white',
    info:    'bg-blue-600 hover:bg-blue-700 text-white',
    success: 'bg-emerald-600 hover:bg-emerald-700 text-white',
  };
  return (
    <div className={`flex items-start gap-3 rounded-xl border px-4 py-3 ${styles[type]}`}>
      <span className="mt-0.5 shrink-0">{icon}</span>
      <div className="flex-1 min-w-0">
        <p className="text-xs font-bold">{title}</p>
        <p className="text-[11px] mt-0.5 opacity-80">{body}</p>
      </div>
      {action && onAction && (
        <button
          onClick={onAction}
          disabled={loading}
          className={`shrink-0 flex items-center gap-1.5 text-[11px] font-bold px-3 py-1.5 rounded-lg ${btnStyles[type]} disabled:opacity-60`}
        >
          {loading && <Loader2 size={11} className="animate-spin" />}
          {action}
        </button>
      )}
    </div>
  );
};

// ── Stat Card ──────────────────────────────────────────────────────────────────
const StatCard = ({
  label, value, sub, icon, accent, onClick,
}: {
  label: string; value: string; sub?: string;
  icon: React.ReactNode; accent: string; onClick?: () => void;
}) => (
  <div
    onClick={onClick}
    className={`rounded-xl border p-4 flex flex-col gap-2 ${accent} ${onClick ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}`}
  >
    <div className="flex items-center justify-between">
      <span className="opacity-60">{icon}</span>
      {onClick && <ChevronRight size={14} className="opacity-40" />}
    </div>
    <div>
      <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">{label}</p>
      <p className="text-base font-bold text-slate-800 tabular-nums mt-0.5">{value}</p>
      {sub && <p className="text-[10px] text-slate-400 mt-0.5">{sub}</p>}
    </div>
  </div>
);

// ── Quick Action Button ────────────────────────────────────────────────────────
const QuickAction: React.FC<{
  label: string;
  icon: React.ReactNode;
  onClick: () => void;
  accent: string;
}> = ({
  label, icon, onClick, accent,
}) => (
  <button
    onClick={onClick}
    className={`flex flex-col items-center gap-2 px-4 py-3 rounded-xl border transition-all hover:shadow-md active:scale-95 ${accent}`}
  >
    <span>{icon}</span>
    <span className="text-[10px] font-bold">{label}</span>
  </button>
);

// ── Main ───────────────────────────────────────────────────────────────────────
const Dashboard: React.FC = () => {
  const [stats, setStats]         = useState<DashboardStats | null>(null);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState('');
  const [backfilling, setBackfilling] = useState(false);
  const [backfillDone, setBackfillDone] = useState(false);
  const navigate = useNavigate();

  const fetchStats = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setStats(await dashboardService.getStats());
    } catch (err: any) {
      setError(err?.message || 'Dashboard ဒေတာ ဖတ်မရပါ');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void fetchStats(); }, [fetchStats]);
  useDataEvents(['Sale', 'Service Job', 'Booking', 'Stock'], fetchStats);

  const handleBackfill = async () => {
    setBackfilling(true);
    try {
      await adminService.backfillJournals();
      setBackfillDone(true);
      await fetchStats();
    } catch {
      // silent — just re-fetch
    } finally {
      setBackfilling(false);
    }
  };

  const quickActions = [
    { label: 'ရောင်းမယ်',     icon: <TrendingUp size={18} className="text-indigo-600" />,  path: `${AppRoute.SALES}?mode=create`, accent: 'bg-indigo-50 border-indigo-100 text-indigo-700 hover:bg-indigo-100' },
    { label: 'ဝယ်မယ်', icon: <ShoppingCart size={18} className="text-amber-600" />, path: AppRoute.PURCHASES,    accent: 'bg-amber-50 border-amber-100 text-amber-700 hover:bg-amber-100' },
    { label: 'ပစ္စည်းလက်ခံ',  icon: <Calendar size={18} className="text-sky-600" />,       path: AppRoute.BOOKINGS,     accent: 'bg-sky-50 border-sky-100 text-sky-700 hover:bg-sky-100' },
    { label: 'ကုန်ကျစရိတ်',  icon: <DollarSign size={18} className="text-rose-600" />,    path: AppRoute.EXPENSE_INCOME, accent: 'bg-rose-50 border-rose-100 text-rose-700 hover:bg-rose-100' },
    { label: 'ဝန်ဆောင်မှု', icon: <Wrench size={18} className="text-emerald-600" />,     path: AppRoute.SERVICE_JOBS, accent: 'bg-emerald-50 border-emerald-100 text-emerald-700 hover:bg-emerald-100' },
    { label: 'အမြတ်/အရှုံး', icon: <BarChart3 size={18} className="text-purple-600" />,   path: AppRoute.PROFIT_LOSS,  accent: 'bg-purple-50 border-purple-100 text-purple-700 hover:bg-purple-100' },
  ];

  const sideNav = [
    { label: 'ရောင်းချမှု',     icon: <TrendingUp size={18} />,  path: AppRoute.SALES,                accent: 'bg-indigo-500/15 text-indigo-400 group-hover:bg-indigo-500/25' },
    { label: 'ဝယ်ယူမှု',     icon: <ShoppingCart size={18}/>, path: AppRoute.PURCHASES,            accent: 'bg-blue-500/15 text-blue-400 group-hover:bg-blue-500/25' },
    { label: 'ပစ္စည်းများ',      icon: <Package size={18} />,     path: AppRoute.PRODUCTS,             accent: 'bg-amber-500/15 text-amber-400 group-hover:bg-amber-500/25' },
    { label: 'ဖောက်သည်',     icon: <Users size={18} />,       path: AppRoute.CUSTOMERS,            accent: 'bg-green-500/15 text-green-400 group-hover:bg-green-500/25' },
    { label: 'ပေးသွင်းသူ',     icon: <Truck size={18} />,       path: AppRoute.SUPPLIERS,            accent: 'bg-orange-500/15 text-orange-400 group-hover:bg-orange-500/25' },
    { label: 'စာရင်းကိုင်',    icon: <BarChart3 size={18} />,   path: AppRoute.ACCOUNTING_DASHBOARD, accent: 'bg-purple-500/15 text-purple-400 group-hover:bg-purple-500/25' },
    { label: 'အမြတ်/အရှုံး', icon: <FileText size={18} />,    path: AppRoute.PROFIT_LOSS,          accent: 'bg-emerald-500/15 text-emerald-400 group-hover:bg-emerald-500/25' },
  ];

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="animate-spin text-indigo-600" size={28} />
          <p className="text-xs text-slate-500 font-medium">ဖွင့်နေသည်...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-center space-y-3">
          <p className="text-sm font-semibold text-rose-600">{error}</p>
          <button onClick={fetchStats}
            className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white text-xs font-bold rounded-lg hover:bg-indigo-700">
            <RefreshCw size={13} /> ပြန်ကြိုးစား
          </button>
        </div>
      </div>
    );
  }

  const s = stats!;

  return (
    <div className="flex gap-4 h-full overflow-hidden">

      {/* ── Side Nav ──────────────────────────────────────────────────────── */}
      <div className="hidden lg:flex flex-col w-56 bg-slate-900 text-white rounded-2xl p-5 shadow-lg border border-slate-800 shrink-0">
        <h3 className="text-[10px] font-bold uppercase tracking-widest mb-4 text-slate-400">လမ်းညွှန်</h3>
        <div className="space-y-1 flex-1">
          {sideNav.map(item => (
            <button key={item.path} onClick={() => navigate(item.path)}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all hover:bg-slate-800 active:scale-95 group">
              <div className={`p-1.5 rounded-lg ${item.accent}`}>{item.icon}</div>
              <span className="text-xs font-medium flex-1 text-left">{item.label}</span>
              <ChevronRight size={13} className="text-slate-600 group-hover:text-slate-400" />
            </button>
          ))}
        </div>
        <div className="pt-4 border-t border-slate-700 space-y-1">
          {[
            { label: 'အကြွေး',       icon: <CreditCard size={14} />,  path: AppRoute.CREDIT },
            { label: 'စာရင်းဇယား',  icon: <BookOpen size={14} />,    path: AppRoute.COA },
          ].map(item => (
            <button key={item.path} onClick={() => navigate(item.path)}
              className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-[11px] text-slate-400 hover:text-white hover:bg-slate-800 transition-all">
              {item.icon}<span>{item.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* ── Main Content ──────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto">
        <div className="space-y-4 pb-4">

          {/* Header */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold text-slate-800 tracking-tight">လုပ်ငန်းခြုံငုံသုံးသပ်ချက်</h2>
              <p className="text-slate-400 text-xs flex items-center gap-1">
                <Clock size={11} />
                {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric' })}
              </p>
            </div>
            <button onClick={fetchStats}
              className="flex items-center gap-1.5 bg-white border border-slate-200 text-slate-600 px-3 py-1.5 rounded-lg text-xs font-semibold hover:bg-slate-50 self-start sm:self-auto">
              <RefreshCw size={12} /> ပြန်ဖတ်
            </button>
          </div>

          {/* ── Alert Banners ─────────────────────────────────────────────── */}
          {!s.hasJournalEntries && !backfillDone && (
            <AlertBanner
              type="warning"
              icon={<Database size={15} />}
              title="Accounting data not initialized"
              body="Journal entries missing — existing sales/purchases are not reflected in financial reports. Click to backfill from current data."
              action={backfilling ? 'Backfilling...' : 'Backfill Now'}
              onAction={handleBackfill}
              loading={backfilling}
            />
          )}

          {backfillDone && (
            <AlertBanner
              type="success"
              icon={<CheckCircle size={15} />}
              title="Backfill complete"
              body="Journal entries created from existing transactions. Financial reports (Trial Balance, P&L, Balance Sheet) should now show data."
            />
          )}

          {s.overdueARCount > 0 && (
            <AlertBanner
              type="error"
              icon={<AlertCircle size={15} />}
              title={`${s.overdueARCount} overdue invoice${s.overdueARCount > 1 ? 's' : ''} — Ks ${money(s.totalOverdueAR)} total`}
              body="These customers have passed their due dates. Follow up immediately to collect."
              action="View Credits"
              onAction={() => navigate(AppRoute.CREDIT)}
            />
          )}

          {s.lowStockCount > 0 && (
            <AlertBanner
              type="warning"
              icon={<AlertTriangle size={15} />}
              title={`${s.lowStockCount} product${s.lowStockCount > 1 ? 's' : ''} running low on stock`}
              body={s.lowStockProducts.length > 0
                ? `Low items: ${s.lowStockProducts.slice(0, 3).join(', ')}${s.lowStockProducts.length > 3 ? ` +${s.lowStockProducts.length - 3} more` : ''}`
                : 'Stock at or below 5 units.'}
              action="View Products"
              onAction={() => navigate(AppRoute.PRODUCTS)}
            />
          )}

          {/* ── Quick Actions ─────────────────────────────────────────────── */}
          <div>
            <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-2 flex items-center gap-1">
              <Zap size={10} /> အမြန်လုပ်ဆောင်ချက်
            </p>
            <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
              {quickActions.map(a => (
                <QuickAction key={a.path} label={a.label} icon={a.icon} onClick={() => navigate(a.path)} accent={a.accent} />
              ))}
            </div>
          </div>

          {/* ── Stat Cards ────────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard
              label="ယနေ့ရောင်းအား"
              value={`Ks ${money(s.todaySalesAmount)}`}
              sub={`${s.todaySalesCount} ကြိမ်`}
              icon={<TrendingUp size={16} />}
              accent="bg-indigo-50 border-indigo-100"
              onClick={() => navigate(AppRoute.SALES)}
            />
            <StatCard
              label="ရရန်ကျန်ငွေ"
              value={`Ks ${money(s.totalPendingAR)}`}
              sub={`${s.pendingARCount} ခု`}
              icon={<CreditCard size={16} />}
              accent={s.overdueARCount > 0 ? 'bg-rose-50 border-rose-200' : 'bg-sky-50 border-sky-100'}
              onClick={() => navigate(AppRoute.CREDIT)}
            />
            <StatCard
              label="ဝန်ဆောင်မှု"
              value={String(s.pendingServiceJobs)}
              sub="လက်ခံပြီး / ပြင်ဆင်နေ"
              icon={<Wrench size={16} />}
              accent={s.pendingServiceJobs > 0 ? 'bg-emerald-50 border-emerald-200' : 'bg-slate-50 border-slate-100'}
              onClick={() => navigate(AppRoute.SERVICE_JOBS)}
            />
            <StatCard
              label="လက်ကျန်နည်း"
              value={String(s.lowStockCount)}
              sub="≤ ၅ ခု ကျန်"
              icon={<Package size={16} />}
              accent={s.lowStockCount > 0 ? 'bg-amber-50 border-amber-200' : 'bg-slate-50 border-slate-100'}
              onClick={() => navigate(AppRoute.PRODUCTS)}
            />
          </div>

          {/* ── Summary row ───────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <StatCard label="စုစုပေါင်းရောင်းအား" value={`Ks ${money(s.totalSales)}`}     icon={<DollarSign size={16} />}  accent="bg-white border-slate-100" />
            <StatCard label="စုစုပေါင်းဝယ်ယူ" value={`Ks ${money(s.totalPurchases)}`} icon={<ShoppingBag size={16} />} accent="bg-white border-slate-100" />
            <StatCard label="ဖောက်သည်စုစုပေါင်း" value={s.totalCustomers.toLocaleString()} icon={<Users size={16} />}     accent="bg-white border-slate-100" />
            <StatCard label="ဝန်ဆောင်မှုစုစုပေါင်း" value={s.totalServices.toLocaleString()}  icon={<Wrench size={16} />}    accent="bg-white border-slate-100" />
          </div>

          {/* ── Charts + Ledger ───────────────────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

            {/* Bar Chart */}
            <div className="lg:col-span-2 bg-white p-4 rounded-xl border border-slate-100">
              <div className="flex items-center justify-between mb-3">
                <h3 className="font-bold text-slate-800 text-sm">မကြာသေးမီ ရောင်းချမှု</h3>
                <span className="text-[10px] text-slate-400">နောက်ဆုံး {s.recentSales.length} ခု</span>
              </div>
              {s.recentSales.length === 0 ? (
                <div className="h-[200px] flex flex-col items-center justify-center gap-2 text-slate-400">
                  <ShoppingCart size={24} className="opacity-30" />
                  <p className="text-xs">No sales yet — <button onClick={() => navigate(AppRoute.SALES)} className="text-indigo-500 font-semibold underline">create your first sale</button></p>
                </div>
              ) : (
                <div className="h-[200px]">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={s.recentSales.map(s => ({ name: s.saleCode || `#${s.id}`, amount: s.amount, status: s.status }))} barCategoryGap="30%">
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                      <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 9, fontWeight: 600 }} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 9 }}
                        tickFormatter={v => v >= 1000 ? `${(v / 1000).toFixed(0)}K` : v} width={38} />
                      <Tooltip formatter={(v: number) => [`Ks ${money(v)}`, 'Amount']}
                        contentStyle={{ fontSize: '11px', borderRadius: '8px', border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }} />
                      <Bar dataKey="amount" radius={[4, 4, 0, 0]}>
                        {s.recentSales.map((sale, i) => (
                          <Cell key={i} fill={sale.status === 'Paid' ? '#6366f1' : sale.status === 'Partial' ? '#f59e0b' : '#94a3b8'} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
              <div className="flex items-center gap-4 mt-2">
                {[['#6366f1', 'Paid'], ['#f59e0b', 'Partial'], ['#94a3b8', 'Pending']].map(([c, l]) => (
                  <div key={l} className="flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full" style={{ background: c }} />
                    <span className="text-[9px] font-bold text-slate-400 uppercase">{l}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Business Summary */}
            <div className="bg-white p-4 rounded-xl border border-slate-100 flex flex-col gap-4">
              <h3 className="font-bold text-slate-800 text-sm">လုပ်ငန်းအကျဉ်း</h3>
              <div className="space-y-3">
                {[
                  { label: 'စုစုပေါင်းရောင်းအား',     value: s.totalSales,     color: 'indigo' },
                  { label: 'စုစုပေါင်းဝယ်ယူ', value: s.totalPurchases, color: 'amber' },
                ].map(({ label, value, color }) => {
                  const max = Math.max(s.totalSales, s.totalPurchases, 1);
                  const pct = Math.round((value / max) * 100);
                  return (
                    <div key={label} className="space-y-1">
                      <div className="flex justify-between text-xs">
                        <span className="text-slate-500 font-medium">{label}</span>
                        <span className="font-bold text-slate-700">Ks {money(value)}</span>
                      </div>
                      <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                        <div className={`h-full rounded-full bg-${color}-500`} style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* AR breakdown */}
              {(s.pendingARCount > 0 || s.overdueARCount > 0) && (
                <div className="pt-3 border-t border-slate-100 space-y-2">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-slate-400">ရရန်ကျန်</p>
                  {s.overdueARCount > 0 && (
                    <div className="flex justify-between items-center">
                      <span className="text-[11px] text-rose-600 font-semibold flex items-center gap-1">
                        <AlertCircle size={11} /> Overdue ({s.overdueARCount})
                      </span>
                      <span className="text-xs font-bold text-rose-700">Ks {money(s.totalOverdueAR)}</span>
                    </div>
                  )}
                  {s.pendingARCount > 0 && (
                    <div className="flex justify-between items-center">
                      <span className="text-[11px] text-slate-500 flex items-center gap-1">
                        <Clock size={11} /> Pending ({s.pendingARCount})
                      </span>
                      <span className="text-xs font-bold text-slate-700">Ks {money(s.totalPendingAR)}</span>
                    </div>
                  )}
                </div>
              )}

              <div className="pt-3 border-t border-slate-100 grid grid-cols-2 gap-3">
                <div className="bg-sky-50 rounded-lg px-3 py-2 cursor-pointer hover:bg-sky-100" onClick={() => navigate(AppRoute.CUSTOMERS)}>
                  <p className="text-[10px] font-bold text-slate-500 uppercase">ဖောက်သည်</p>
                  <p className="text-lg font-bold tabular-nums text-sky-700">{s.totalCustomers.toLocaleString()}</p>
                </div>
                <div className="bg-emerald-50 rounded-lg px-3 py-2 cursor-pointer hover:bg-emerald-100" onClick={() => navigate(AppRoute.SERVICE_JOBS)}>
                  <p className="text-[10px] font-bold text-slate-500 uppercase">လုပ်ငန်းများ</p>
                  <p className="text-lg font-bold tabular-nums text-emerald-700">{s.pendingServiceJobs.toLocaleString()}</p>
                </div>
              </div>
            </div>
          </div>

          {/* ── Sales Ledger ──────────────────────────────────────────────── */}
          <div className="bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-slate-800 text-sm">မကြာသေးမီ ရောင်းချမှု</h3>
                <p className="text-[10px] text-slate-500">နောက်ဆုံး ၁၀ ခု</p>
              </div>
              <div className="flex items-center gap-2">
                <button onClick={() => navigate(`${AppRoute.SALES}?mode=create`)}
                  className="flex items-center gap-1 text-xs font-bold text-indigo-600 hover:bg-indigo-50 px-2 py-1 rounded-md">
                  <Plus size={12} /> ရောင်းမယ်
                </button>
                <button onClick={() => navigate(AppRoute.SALES)}
                  className="text-indigo-600 font-bold text-xs flex items-center gap-1 hover:bg-indigo-50 px-2 py-1 rounded-md">
                  မှတ်တမ်းအပြည့် <ArrowRight size={12} />
                </button>
              </div>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="bg-slate-50 border-b border-slate-100">
                  <tr>
                    <th className="px-4 py-3 text-[9px] font-bold text-slate-400 uppercase tracking-widest">ပြေစာ</th>
                    <th className="px-4 py-3 text-[9px] font-bold text-slate-400 uppercase tracking-widest">ဖောက်သည်</th>
                    <th className="px-4 py-3 text-[9px] font-bold text-slate-400 uppercase tracking-widest">ရက်စွဲ</th>
                    <th className="px-4 py-3 text-[9px] font-bold text-slate-400 uppercase tracking-widest text-right">ပမာဏ</th>
                    <th className="px-4 py-3 text-[9px] font-bold text-slate-400 uppercase tracking-widest">အခြေအနေ</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {s.recentSales.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-10 text-center">
                        <div className="flex flex-col items-center gap-2 text-slate-400">
                          <ShoppingCart size={20} className="opacity-30" />
                          <p className="text-xs">No sales yet</p>
                          <button onClick={() => navigate(`${AppRoute.SALES}?mode=create`)}
                            className="text-indigo-600 text-xs font-bold hover:underline">Create your first sale →</button>
                        </div>
                      </td>
                    </tr>
                  ) : s.recentSales.map(sale => (
                    <tr key={sale.id} className="hover:bg-slate-50 transition-colors">
                      <td className="px-4 py-3 text-xs font-bold text-slate-700">{sale.saleCode}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{sale.customerName}</td>
                      <td className="px-4 py-3 text-xs text-slate-500">{fmtDate(sale.date)}</td>
                      <td className="px-4 py-3 text-xs font-bold text-slate-900 text-right">Ks {money(sale.amount)}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider ${
                          sale.status === 'Paid'    ? 'bg-emerald-50 text-emerald-600' :
                          sale.status === 'Partial' ? 'bg-amber-50 text-amber-600'    :
                                                      'bg-slate-100 text-slate-500'
                        }`}>
                          {sale.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};

export default Dashboard;
