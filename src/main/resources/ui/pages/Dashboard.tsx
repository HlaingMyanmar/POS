import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle, ArrowRight, CheckCircle2, Clock3, CreditCard, FileText,
  Package, PackageCheck, RefreshCw, ShoppingCart, Store, Undo2, Wrench,
} from 'lucide-react';
import { AppRoute, DashboardStats } from '../types';
import { dashboardService } from '../services/api';

type Period = 'today' | 'month' | 'custom';

const emptyStats: DashboardStats = {
  totalSales: 0, totalPurchases: 0, totalServices: 0, totalCustomers: 0, recentSales: [],
  todaySalesAmount: 0, todaySalesCount: 0, periodServiceAmount: 0, periodServiceCount: 0,
  periodPurchaseAmount: 0, periodPurchaseCount: 0, totalOverdueAR: 0, overdueARCount: 0,
  totalPendingAR: 0, pendingARCount: 0, pendingServiceJobs: 0, receivedJobCount: 0,
  inProgressJobCount: 0, completedJobCount: 0, pendingPaymentJobCount: 0,
  pendingDeliveryJobCount: 0, lowStockCount: 0, lowStockProducts: [], stockValue: 0,
  supplierPayable: 0, reworkCount: 0, upgradeCount: 0, refundCount: 0, refundAmount: 0,
  updatedAt: '', hasJournalEntries: false,
};

const money = (value: number) =>
  `${Number(value || 0).toLocaleString(undefined, { maximumFractionDigits: 0 })} Ks`;

type Tone = 'neutral' | 'positive' | 'warning' | 'critical';

const toneBorder: Record<Tone, string> = {
  neutral: 'border-slate-200',
  positive: 'border-slate-200',
  warning: 'border-amber-200',
  critical: 'border-rose-200',
};

const toneValue: Record<Tone, string> = {
  neutral: 'text-slate-950',
  positive: 'text-slate-950',
  warning: 'text-amber-800',
  critical: 'text-rose-700',
};

const KpiCard = ({
  label, value, detail, icon, tone = 'neutral', onClick, loading,
}: {
  label: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
  tone?: Tone;
  onClick?: () => void;
  loading?: boolean;
}) => {
  const interactive = Boolean(onClick);
  const Comp: any = interactive ? 'button' : 'div';
  return (
    <Comp
      type={interactive ? 'button' : undefined}
      onClick={onClick}
      className={`rounded-xl border bg-white p-4 sm:p-5 text-left transition-all ${toneBorder[tone]} ${
        interactive
          ? 'cursor-pointer hover:border-indigo-300 hover:bg-indigo-50/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500'
          : ''
      }`}
      aria-label={interactive ? `${label}: ${value}` : undefined}
    >
      {loading ? (
        <div className="animate-pulse space-y-3" aria-hidden="true">
          <div className="h-3 w-24 rounded bg-slate-100" />
          <div className="h-7 w-36 rounded bg-slate-100" />
          <div className="h-3 w-20 rounded bg-slate-100" />
        </div>
      ) : (
        <>
          <div className="flex items-start justify-between gap-3">
            <p className="text-[13px] sm:text-sm font-semibold text-slate-500 leading-snug">{label}</p>
            <span className="shrink-0 text-slate-400" aria-hidden="true">{icon}</span>
          </div>
          <p className={`mt-3 text-2xl sm:text-[1.7rem] font-black tabular-nums tracking-tight leading-none ${toneValue[tone]}`}>
            {value}
          </p>
          <p className="mt-2 text-xs sm:text-[13px] font-medium text-slate-400 leading-snug">{detail}</p>
        </>
      )}
    </Comp>
  );
};

const StatusCard = ({
  label, value, icon, onClick, loading,
}: {
  label: string;
  value: number;
  icon: React.ReactNode;
  onClick?: () => void;
  loading?: boolean;
}) => {
  const interactive = Boolean(onClick);
  const Comp: any = interactive ? 'button' : 'div';
  return (
    <Comp
      type={interactive ? 'button' : undefined}
      onClick={onClick}
      className={`rounded-xl border border-slate-200 bg-white px-3.5 py-3.5 text-left min-h-[88px] transition-all ${
        interactive
          ? 'cursor-pointer hover:border-indigo-300 hover:bg-indigo-50/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500'
          : ''
      }`}
      aria-label={interactive ? `${label}: ${value}` : undefined}
    >
      {loading ? (
        <div className="animate-pulse space-y-2" aria-hidden="true">
          <div className="h-3 w-16 rounded bg-slate-100" />
          <div className="h-6 w-10 rounded bg-slate-100" />
        </div>
      ) : (
        <>
          <div className="flex items-center justify-between gap-2">
            <p className="text-[13px] font-semibold text-slate-500 leading-snug">{label}</p>
            <span className="text-slate-400 shrink-0" aria-hidden="true">{icon}</span>
          </div>
          <p className="mt-2 text-2xl font-black tabular-nums text-slate-950 leading-none">{value}</p>
        </>
      )}
    </Comp>
  );
};

const SectionTitle = ({ title, subtitle, action, id }: {
  title: string; subtitle?: string; action?: React.ReactNode; id?: string;
}) => (
  <div className="mb-3 flex items-end justify-between gap-3">
    <div className="min-w-0">
      <h2 id={id} className="text-lg sm:text-xl font-black text-slate-900 tracking-tight">{title}</h2>
      {subtitle && <p className="mt-0.5 text-[13px] text-slate-500 font-medium">{subtitle}</p>}
    </div>
    {action}
  </div>
);

const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats>(emptyStats);
  const [period, setPeriod] = useState<Period>('today');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    dashboardService.getStats({ period, ...(period === 'custom' ? { from, to } : {}) })
      .then((data) => { if (active) setStats(data); })
      .catch(() => { if (active) setError('Dashboard data ကို မရယူနိုင်ပါ။'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [period, from, to]);

  const periodLabel = useMemo(() => {
    if (period === 'today') return 'Today';
    if (period === 'month') return 'This Month';
    if (from && to) return `${from} → ${to}`;
    return 'Custom range';
  }, [period, from, to]);

  const updatedLabel = stats.updatedAt
    ? new Date(stats.updatedAt).toLocaleString(undefined, {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
      })
    : '—';

  const attentionItems = useMemo(() => {
    const items: { key: string; title: string; detail: string; tone: Tone; route: string }[] = [];
    if (stats.lowStockCount > 0) {
      items.push({
        key: 'low-stock',
        title: 'Low stock products',
        detail: `${stats.lowStockCount} မျိုး ပြန်မှာယူရန် လိုအပ်နိုင်သည်`,
        tone: 'warning',
        route: `${AppRoute.PRODUCTS}?lowStock=1`,
      });
    }
    if (stats.overdueARCount > 0 || stats.totalOverdueAR > 0) {
      items.push({
        key: 'overdue-ar',
        title: 'Overdue customer balances',
        detail: `${money(stats.totalOverdueAR)} · ${stats.overdueARCount} invoice`,
        tone: 'critical',
        route: AppRoute.CREDIT,
      });
    }
    if (stats.pendingARCount > 0 || stats.totalPendingAR > 0) {
      items.push({
        key: 'pending-ar',
        title: 'Customer outstanding',
        detail: `${money(stats.totalPendingAR)} · ${stats.pendingARCount} ခု`,
        tone: 'warning',
        route: AppRoute.CREDIT,
      });
    }
    if (stats.supplierPayable > 0) {
      items.push({
        key: 'supplier-ap',
        title: 'Supplier payables',
        detail: money(stats.supplierPayable),
        tone: 'warning',
        route: AppRoute.AP_AGING,
      });
    }
    if (stats.pendingServiceJobs > 0) {
      items.push({
        key: 'pending-jobs',
        title: 'Open service jobs',
        detail: `${stats.pendingServiceJobs} ခု (Received / Inspecting / In Progress)`,
        tone: 'warning',
        route: AppRoute.SERVICE_JOBS,
      });
    }
    if (stats.pendingPaymentJobCount > 0) {
      items.push({
        key: 'job-payment',
        title: 'Service jobs awaiting payment',
        detail: `${stats.pendingPaymentJobCount} ခု`,
        tone: 'warning',
        route: AppRoute.SERVICE_JOBS,
      });
    }
    if (stats.pendingDeliveryJobCount > 0) {
      items.push({
        key: 'job-delivery',
        title: 'Service jobs awaiting delivery',
        detail: `${stats.pendingDeliveryJobCount} ခု`,
        tone: 'warning',
        route: AppRoute.SERVICE_JOBS,
      });
    }
    if (stats.reworkCount > 0) {
      items.push({
        key: 'rework',
        title: 'Warranty / Rework',
        detail: `${stats.reworkCount} ခု · Upgrade ${stats.upgradeCount}`,
        tone: 'warning',
        route: AppRoute.SERVICE_JOBS,
      });
    }
    return items;
  }, [stats]);

  const quickActions = [
    { label: 'New Sale', route: AppRoute.SALES, icon: <ShoppingCart size={18} aria-hidden="true" /> },
    { label: 'New Purchase', route: AppRoute.PURCHASES, icon: <Package size={18} aria-hidden="true" /> },
    { label: 'Service Job', route: AppRoute.SERVICE_JOBS, icon: <Wrench size={18} aria-hidden="true" /> },
    { label: 'Product / Stock', route: AppRoute.PRODUCTS, icon: <Store size={18} aria-hidden="true" /> },
  ];

  const go = (path: string) => navigate(path);

  return (
    <main className="min-h-full bg-slate-50">
      <div className="mx-auto w-full max-w-[1560px] px-4 sm:px-5 lg:px-6 py-5 sm:py-6">

        {/* Header */}
        <header className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl sm:text-[1.7rem] font-black tracking-tight text-slate-950">Dashboard</h1>
            <p className="mt-1 text-[13px] sm:text-sm text-slate-500 font-medium">
              လုပ်ငန်းအခြေအနေ · Last updated {updatedLabel}
              <span className="text-slate-300 mx-1.5" aria-hidden="true">·</span>
              Range: {periodLabel}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Date range">
            {([
              ['today', 'Today'],
              ['month', 'This Month'],
              ['custom', 'Custom'],
            ] as const).map(([key, label]) => (
              <button
                key={key}
                type="button"
                aria-pressed={period === key}
                onClick={() => setPeriod(key)}
                className={`min-h-[40px] rounded-lg border px-3.5 py-2 text-[13px] font-bold transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${
                  period === key
                    ? 'border-indigo-600 bg-indigo-600 text-white shadow-sm'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
                }`}
              >
                {label}
              </button>
            ))}
            {period === 'custom' && (
              <>
                <input
                  aria-label="From date"
                  type="date"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                  className="min-h-[40px] rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                />
                <input
                  aria-label="To date"
                  type="date"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                  className="min-h-[40px] rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-[13px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                />
              </>
            )}
          </div>
        </header>

        {error && (
          <div className="mb-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700" role="alert">
            {error}
          </div>
        )}

        {/* Business Overview */}
        <section className="mb-6" aria-labelledby="business-overview-heading">
          <SectionTitle
            id="business-overview-heading"
            title="Business Overview"
            subtitle="အဓိက ငွေကြေးနှင့် လုပ်ငန်းအချက်အလက်များ"
          />
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard
              loading={loading}
              label="Sales Revenue (Today)"
              value={money(stats.todaySalesAmount)}
              detail={`${stats.todaySalesCount} invoice${stats.todaySalesCount === 1 ? '' : 's'}`}
              icon={<ShoppingCart size={18} />}
              tone="positive"
              onClick={() => go(AppRoute.SALES)}
            />
            <KpiCard
              loading={loading}
              label={`Service Revenue (${periodLabel})`}
              value={money(stats.periodServiceAmount)}
              detail={`${stats.periodServiceCount} job${stats.periodServiceCount === 1 ? '' : 's'}`}
              icon={<Wrench size={18} />}
              tone="positive"
              onClick={() => go(AppRoute.SERVICE_JOBS)}
            />
            <KpiCard
              loading={loading}
              label="Customer Due"
              value={money(stats.totalPendingAR)}
              detail={`${stats.pendingARCount} outstanding`}
              icon={<CreditCard size={18} />}
              tone={stats.totalPendingAR > 0 ? 'warning' : 'neutral'}
              onClick={() => go(AppRoute.CREDIT)}
            />
            <KpiCard
              loading={loading}
              label="Supplier Due"
              value={money(stats.supplierPayable)}
              detail="Active payables"
              icon={<PackageCheck size={18} />}
              tone={stats.supplierPayable > 0 ? 'warning' : 'neutral'}
              onClick={() => go(AppRoute.AP_AGING)}
            />
            <KpiCard
              loading={loading}
              label="Stock Value"
              value={money(stats.stockValue)}
              detail={stats.lowStockCount > 0 ? `${stats.lowStockCount} low-stock items` : 'Inventory at cost/sale valuation'}
              icon={<Package size={18} />}
              onClick={() => go(AppRoute.PRODUCTS)}
            />
            <KpiCard
              loading={loading}
              label={`Purchases (${periodLabel})`}
              value={money(stats.periodPurchaseAmount)}
              detail={`${stats.periodPurchaseCount} purchase${stats.periodPurchaseCount === 1 ? '' : 's'}`}
              icon={<Store size={18} />}
              onClick={() => go(AppRoute.PURCHASES)}
            />
            <KpiCard
              loading={loading}
              label="Warranty / Rework"
              value={`${stats.reworkCount}`}
              detail={`Upgrade ${stats.upgradeCount} · in selected range`}
              icon={<RefreshCw size={18} />}
              tone={stats.reworkCount > 0 ? 'warning' : 'neutral'}
              onClick={() => go(AppRoute.SERVICE_JOBS)}
            />
            <KpiCard
              loading={loading}
              label="Refunds"
              value={`${stats.refundCount}`}
              detail={money(stats.refundAmount)}
              icon={<Undo2 size={18} />}
              onClick={() => go(AppRoute.SALE_RETURNS)}
            />
          </div>
        </section>

        {/* Attention Required */}
        <section className="mb-6" aria-labelledby="attention-heading">
          <SectionTitle
            id="attention-heading"
            title="Attention Required"
            subtitle="ချက်ချင်းကြည့်ရန် လိုအပ်နိုင်သော အချက်များ"
          />
          {loading ? (
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-[72px] rounded-xl border border-slate-200 bg-white animate-pulse" />
              ))}
            </div>
          ) : attentionItems.length === 0 ? (
            <div className="rounded-xl border border-emerald-200 bg-emerald-50/70 px-4 py-4 flex items-start gap-3">
              <CheckCircle2 size={18} className="text-emerald-600 mt-0.5 shrink-0" aria-hidden="true" />
              <div>
                <p className="text-sm font-bold text-emerald-900">No urgent attention items</p>
                <p className="text-[13px] text-emerald-800/80 mt-0.5">Low stock, overdue balances, and open job queues are clear.</p>
              </div>
            </div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
              {attentionItems.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => go(item.route)}
                  className={`rounded-xl border bg-white px-4 py-3.5 text-left min-h-[44px] transition-all hover:border-indigo-300 hover:bg-indigo-50/30 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${
                    item.tone === 'critical' ? 'border-rose-200' : 'border-amber-200'
                  }`}
                >
                  <div className="flex items-start gap-2.5">
                    <AlertTriangle
                      size={16}
                      className={`mt-0.5 shrink-0 ${item.tone === 'critical' ? 'text-rose-600' : 'text-amber-600'}`}
                      aria-hidden="true"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-bold text-slate-900 leading-snug">{item.title}</p>
                      <p className="mt-0.5 text-[13px] font-medium text-slate-500">{item.detail}</p>
                    </div>
                    <ArrowRight size={15} className="text-slate-300 shrink-0 mt-1" aria-hidden="true" />
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>

        {/* Service Ops + Low Stock */}
        <section className="mb-6 grid gap-4 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] items-start">
          <div className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
            <SectionTitle
              title="Service Operations"
              subtitle="လက်ရှိ Service Job status"
              action={
                <button
                  type="button"
                  onClick={() => go(AppRoute.SERVICE_JOBS)}
                  className="inline-flex items-center gap-1 text-[13px] font-bold text-indigo-600 hover:text-indigo-800 min-h-[40px] px-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                >
                  View all <ArrowRight size={14} aria-hidden="true" />
                </button>
              }
            />
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
              <StatusCard
                loading={loading}
                label="Received"
                value={stats.receivedJobCount}
                icon={<FileText size={16} />}
                onClick={() => go(`${AppRoute.SERVICE_JOBS}?status=RECEIVED`)}
              />
              <StatusCard
                loading={loading}
                label="In Progress"
                value={stats.inProgressJobCount}
                icon={<Clock3 size={16} />}
                onClick={() => go(`${AppRoute.SERVICE_JOBS}?status=IN_PROGRESS`)}
              />
              <StatusCard
                loading={loading}
                label="Completed"
                value={stats.completedJobCount}
                icon={<CheckCircle2 size={16} />}
                onClick={() => go(`${AppRoute.SERVICE_JOBS}?status=COMPLETED`)}
              />
              <StatusCard
                loading={loading}
                label="Awaiting Payment"
                value={stats.pendingPaymentJobCount}
                icon={<CreditCard size={16} />}
                onClick={() => go(AppRoute.SERVICE_JOBS)}
              />
              <StatusCard
                loading={loading}
                label="Awaiting Delivery"
                value={stats.pendingDeliveryJobCount}
                icon={<PackageCheck size={16} />}
                onClick={() => go(AppRoute.SERVICE_JOBS)}
              />
            </div>
            {!loading && stats.pendingServiceJobs === 0 && stats.pendingPaymentJobCount === 0 && stats.pendingDeliveryJobCount === 0 && (
              <p className="mt-3 text-[13px] font-medium text-emerald-700 flex items-center gap-1.5">
                <CheckCircle2 size={14} aria-hidden="true" /> No pending service queue
              </p>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5">
            <SectionTitle
              title="Low Stock"
              subtitle={stats.lowStockCount > 0 ? `${stats.lowStockCount} products below threshold` : 'ပြန်မှာယူရန် စာရင်း'}
              action={
                <button
                  type="button"
                  onClick={() => go(`${AppRoute.PRODUCTS}?lowStock=1`)}
                  className="inline-flex items-center gap-1 text-[13px] font-bold text-indigo-600 hover:text-indigo-800 min-h-[40px] px-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                >
                  View all <ArrowRight size={14} aria-hidden="true" />
                </button>
              }
            />
            {loading ? (
              <div className="space-y-2 animate-pulse" aria-hidden="true">
                {[1, 2, 3].map((i) => <div key={i} className="h-11 rounded-lg bg-slate-100" />)}
              </div>
            ) : stats.lowStockProducts.length === 0 ? (
              <div className="rounded-lg border border-emerald-100 bg-emerald-50/70 px-3.5 py-4 flex items-start gap-2.5">
                <CheckCircle2 size={16} className="text-emerald-600 mt-0.5 shrink-0" aria-hidden="true" />
                <div>
                  <p className="text-sm font-bold text-emerald-900">No low-stock products</p>
                  <p className="text-[13px] text-emerald-800/80 mt-0.5">Inventory is above the low-stock threshold.</p>
                </div>
              </div>
            ) : (
              <ul className="space-y-1.5">
                {stats.lowStockProducts.map((name) => (
                  <li key={name}>
                    <button
                      type="button"
                      onClick={() => go(`${AppRoute.PRODUCTS}?lowStock=1`)}
                      className="w-full flex items-center gap-2.5 rounded-lg border border-amber-100 bg-amber-50/60 px-3 py-2.5 text-left min-h-[44px] hover:bg-amber-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-500 transition-colors"
                    >
                      <AlertTriangle size={15} className="text-amber-600 shrink-0" aria-hidden="true" />
                      <span className="min-w-0 flex-1 text-sm font-bold text-amber-950 truncate">{name}</span>
                      <span className="text-[12px] font-semibold text-amber-700/80 shrink-0">Low stock</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {!loading && stats.lowStockCount > stats.lowStockProducts.length && (
              <p className="mt-2 text-[12px] font-medium text-slate-400">
                Showing {stats.lowStockProducts.length} of {stats.lowStockCount} · open Products for the full list
              </p>
            )}
          </div>
        </section>

        {/* Quick Actions */}
        <section className="mb-2" aria-labelledby="quick-actions-heading">
          <SectionTitle id="quick-actions-heading" title="Quick Actions" subtitle="မကြာခဏသုံးသော လုပ်ဆောင်ချက်များ" />
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
            {quickActions.map((action) => (
              <button
                key={action.label}
                type="button"
                onClick={() => go(action.route)}
                className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3.5 min-h-[52px] text-left text-sm font-bold text-slate-700 transition hover:border-indigo-300 hover:bg-indigo-50/50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
              >
                <span className="flex items-center gap-2.5">
                  <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-50 text-slate-500 border border-slate-100">
                    {action.icon}
                  </span>
                  {action.label}
                </span>
                <ArrowRight size={15} className="text-slate-300" aria-hidden="true" />
              </button>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
};

export default Dashboard;
