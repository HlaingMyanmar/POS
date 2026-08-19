import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle, ArrowRight, CheckCircle2, Clock3, CreditCard, FileText, Package,
  PackageCheck, RefreshCw, ShoppingCart, Store, Undo2, Wrench,
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

const money = (value: number) => new Intl.NumberFormat('my-MM', {
  maximumFractionDigits: 0,
}).format(value) + ' ကျပ်';

const Metric = ({ label, value, detail, icon, tone = 'slate' }: {
  label: string; value: string; detail: string; icon: React.ReactNode; tone?: string;
}) => (
  <div className={'rounded-xl border bg-white p-4 shadow-sm ' + tone}>
    <div className="flex items-center justify-between gap-3">
      <p className="text-xs font-bold text-slate-500">{label}</p>
      <span className="text-slate-400">{icon}</span>
    </div>
    <p className="mt-3 text-xl font-black text-slate-950 tabular-nums">{value}</p>
    <p className="mt-1 text-xs font-medium text-slate-400">{detail}</p>
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

  const quickActions = [
    { label: 'ရောင်းချမှုအသစ်', route: AppRoute.SALES, icon: <ShoppingCart size={17} /> },
    { label: 'ဝယ်ယူမှုအသစ်', route: AppRoute.PURCHASES, icon: <Package size={17} /> },
    { label: 'Service Job', route: AppRoute.SERVICE_JOBS, icon: <Wrench size={17} /> },
    { label: 'ပစ္စည်းစာရင်း', route: AppRoute.PRODUCTS, icon: <Store size={17} /> },
  ];

  return (
    <main className="min-h-full bg-slate-50 p-4 sm:p-6">
      <header className="mb-6 flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-indigo-600">လုပ်ငန်းအခြေအနေ</p>
          <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-950 sm:text-3xl">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500">Database မှ နောက်ဆုံးရရှိထားသော လုပ်ငန်းအချက်အလက်များ</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {(['today', 'month', 'custom'] as Period[]).map((item) => (
            <button key={item} type="button" onClick={() => setPeriod(item)} className={'rounded-lg border px-3 py-2 text-xs font-bold ' + (period === item ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-slate-200 bg-white text-slate-600')}>
              {item === 'today' ? 'ဒီနေ့' : item === 'month' ? 'ယခုလ' : 'သတ်မှတ်ကာလ'}
            </button>
          ))}
          {period === 'custom' && <><input aria-label="စတင်ရက်" type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs" /><input aria-label="ပြီးဆုံးရက်" type="date" value={to} onChange={(e) => setTo(e.target.value)} className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs" /></>}
        </div>
      </header>

      {error && <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-700">{error}</div>}
      {loading && <div className="mb-4 rounded-lg border border-indigo-100 bg-indigo-50 px-4 py-3 text-sm font-bold text-indigo-700">Data ရယူနေပါသည်...</div>}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="ဒီနေ့ ရောင်းရငွေ" value={money(stats.todaySalesAmount)} detail={`${stats.todaySalesCount} ကြိမ် ရောင်းချထားသည်`} icon={<ShoppingCart size={18} />} tone="border-emerald-100" />
        <Metric label="ဝန်ဆောင်မှုဝင်ငွေ" value={money(stats.periodServiceAmount)} detail={`${stats.periodServiceCount} ခု လက်ခံထားသည်`} icon={<Wrench size={18} />} tone="border-blue-100" />
        <Metric label="Customer ထံမှရရန်" value={money(stats.totalPendingAR)} detail={`${stats.pendingARCount} ခု ကျန်ရှိသည်`} icon={<CreditCard size={18} />} tone="border-amber-100" />
        <Metric label="Supplier ကိုပေးရန်" value={money(stats.supplierPayable)} detail="ပေးရန်ကျန် အဝယ်စာရင်း" icon={<PackageCheck size={18} />} tone="border-rose-100" />
        <Metric label="လက်ရှိ Stock တန်ဖိုး" value={money(stats.stockValue)} detail={`${stats.lowStockCount} မျိုး Stock နည်းနေသည်`} icon={<Package size={18} />} />
        <Metric label="ဒီနေ့ / ကာလအတွင်း ဝယ်ယူမှု" value={money(stats.periodPurchaseAmount)} detail={`${stats.periodPurchaseCount} ကြိမ်`} icon={<Store size={18} />} />
        <Metric label="Warranty / Rework" value={`${stats.reworkCount} ခု`} detail={`Upgrade ${stats.upgradeCount} ခု`} icon={<RefreshCw size={18} />} />
        <Metric label="Refund" value={`${stats.refundCount} ခု`} detail={money(stats.refundAmount)} icon={<Undo2 size={18} />} />
      </section>

      <section className="mt-5 grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-4 flex items-center justify-between"><div><h2 className="font-black text-slate-900">Service Job အခြေအနေ</h2><p className="text-xs text-slate-400">လက်ရှိ database status အရ</p></div><Wrench className="text-indigo-600" size={20} /></div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
            <Metric label="လက်ခံ" value={String(stats.receivedJobCount)} detail="ခု" icon={<FileText size={16} />} />
            <Metric label="လုပ်ဆောင်ဆဲ" value={String(stats.inProgressJobCount)} detail="ခု" icon={<Clock3 size={16} />} />
            <Metric label="ပြီးစီး" value={String(stats.completedJobCount)} detail="ခု" icon={<CheckCircle2 size={16} />} />
            <Metric label="ငွေရှင်းရန်" value={String(stats.pendingPaymentJobCount)} detail="ခု" icon={<CreditCard size={16} />} />
            <Metric label="ပစ္စည်းပေးရန်" value={String(stats.pendingDeliveryJobCount)} detail="ခု" icon={<PackageCheck size={16} />} />
          </div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-4 flex items-center justify-between"><div><h2 className="font-black text-slate-900">Stock နည်းနေသောပစ္စည်း</h2><p className="text-xs text-slate-400">ပြန်လည်မှာယူရန် လိုအပ်နိုင်သောစာရင်း</p></div><AlertTriangle className="text-amber-500" size={20} /></div>
          {stats.lowStockProducts.length === 0 ? <p className="py-5 text-center text-sm font-medium text-slate-400">Stock နည်းနေသော ပစ္စည်းမရှိပါ</p> : <div className="space-y-2">{stats.lowStockProducts.map((name) => <div key={name} className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm font-bold text-amber-800"><AlertTriangle size={15} />{name}</div>)}</div>}
        </div>
      </section>

      <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="mb-3 flex items-center justify-between"><div><h2 className="font-black text-slate-900">အမြန်လုပ်ဆောင်ရန်</h2><p className="text-xs text-slate-400">သက်ဆိုင်ရာစာမျက်နှာသို့ တိုက်ရိုက်သွားရန်</p></div><ArrowRight size={18} className="text-slate-400" /></div>
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4">{quickActions.map((action) => <button key={action.label} type="button" onClick={() => navigate(action.route)} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-3 text-left text-sm font-bold text-slate-700 transition hover:border-indigo-300 hover:bg-indigo-50"><span className="flex items-center gap-2">{action.icon}{action.label}</span><ArrowRight size={15} /></button>)}</div>
      </section>

      <footer className="mt-4 flex items-center gap-2 text-xs font-medium text-slate-400"><Clock3 size={14} /> နောက်ဆုံး update: {stats.updatedAt ? new Date(stats.updatedAt).toLocaleString('my-MM') : 'မရရှိသေးပါ'}</footer>
    </main>
  );
};

export default Dashboard;
