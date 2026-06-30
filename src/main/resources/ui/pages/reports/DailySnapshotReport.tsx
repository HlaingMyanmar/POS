import React, { useState, useCallback, useRef } from 'react';
import {
  RefreshCw, FileDown, Share2, Copy, Check,
  ShoppingCart, Truck, Wrench, CalendarClock,
  Wallet, Package, TrendingUp, TrendingDown,
  ChevronDown, ChevronUp, ClipboardList, Send,
} from 'lucide-react';
import { summaryReportService, serviceJobService, bookingService } from '../../services/api';
import { saleApiService } from '../../services/saleapiservice';
import { purchaseApiService } from '../../services/purchaseapiservice';
import { expenseApiService } from '../../services/expenseapiservice';
import { incomeApiService } from '../../services/incomeapiservice';
import { productService } from '../../services/productapiservice';
import { getCachedCompanySettings } from '../../utils/companySettings';
import { buildSnapshotReportHtml, SnapshotData } from './snapshotReportTemplate';

// ── Date helpers ──────────────────────────────────────────────────────────────
const toDateStr = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const todayStr = () => toDateStr(new Date());

const getWeekRange = () => {
  const d = new Date();
  const day = d.getDay();
  const start = new Date(d);
  start.setDate(d.getDate() - (day === 0 ? 6 : day - 1));
  return { from: toDateStr(start), to: toDateStr(d) };
};

const getMonthRange = () => {
  const d = new Date();
  return { from: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`, to: toDateStr(d) };
};

const getYearRange = () => {
  const y = new Date().getFullYear();
  return { from: `${y}-01-01`, to: `${y}-12-31` };
};

const fmt = (v: any) => Number(v ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

// ── Types ─────────────────────────────────────────────────────────────────────
type Period = 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'CUSTOM';

interface LoadedData {
  summary: SnapshotData['summary'];
  sales: any[];
  purchases: any[];
  serviceJobs: any[];
  bookings: any[];
  expenses: any[];
  incomes: any[];
  products: any[];
}

// ── Section Card wrapper ──────────────────────────────────────────────────────
function SectionCard({
  icon, title, count, children, accentClass = 'bg-slate-700',
}: {
  icon: React.ReactNode;
  title: string;
  count?: number;
  children: React.ReactNode;
  accentClass?: string;
}) {
  const [open, setOpen] = useState(true);
  return (
    <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen(o => !o)}
        className={`w-full flex items-center justify-between gap-3 px-4 py-3 text-white ${accentClass} hover:opacity-90`}
      >
        <div className="flex items-center gap-2">
          {icon}
          <span className="text-sm font-black uppercase tracking-wide">{title}</span>
          {count !== undefined && (
            <span className="ml-1 px-2 py-0.5 rounded-full bg-white/20 text-xs font-bold">{count}</span>
          )}
        </div>
        {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {open && <div className="overflow-auto">{children}</div>}
    </div>
  );
}

// ── Stat Card ─────────────────────────────────────────────────────────────────
function StatCard({
  label, value, sub, colorClass,
}: {
  label: string; value: string; sub?: string; colorClass: string;
}) {
  return (
    <div className={`rounded-xl border p-4 ${colorClass}`}>
      <p className="text-[10px] font-black uppercase tracking-widest text-current opacity-60">{label}</p>
      <p className="text-xl font-black mt-1">{value}</p>
      {sub && <p className="text-[10px] opacity-60 mt-0.5">{sub}</p>}
    </div>
  );
}

// ── Table helpers ─────────────────────────────────────────────────────────────
const Th: React.FC<{ children: React.ReactNode; right?: boolean }> = ({ children, right }) => (
  <th className={`px-3 py-2 text-[10px] font-black uppercase tracking-wider text-slate-500 bg-slate-50 border-b border-slate-200 ${right ? 'text-right' : 'text-left'} whitespace-nowrap`}>
    {children}
  </th>
);
const Td: React.FC<{ children: React.ReactNode; right?: boolean; mono?: boolean; muted?: boolean }> = ({
  children, right, mono, muted,
}) => (
  <td className={`px-3 py-2 text-sm border-b border-slate-100 ${right ? 'text-right' : ''} ${mono ? 'font-mono font-bold text-indigo-700' : ''} ${muted ? 'text-slate-400' : 'text-slate-700'}`}>
    {children}
  </td>
);

const statusBadge = (status?: string) => {
  if (!status) return <span className="text-slate-400 text-xs">—</span>;
  const s = status.toLowerCase();
  const cls = s.includes('paid') || s.includes('complete') || s.includes('deliver')
    ? 'bg-emerald-100 text-emerald-700'
    : s.includes('partial') || s.includes('progress')
    ? 'bg-amber-100 text-amber-700'
    : s.includes('due') || s.includes('pending') || s.includes('cancel')
    ? 'bg-rose-100 text-rose-700'
    : 'bg-slate-100 text-slate-600';
  return <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-bold ${cls}`}>{status}</span>;
};

const EmptyRow = ({ cols }: { cols: number }) => (
  <tr><td colSpan={cols} className="px-4 py-8 text-center text-slate-400 text-sm italic">မရှိပါ</td></tr>
);

// ── Main Component ────────────────────────────────────────────────────────────
const DailySnapshotReport: React.FC = () => {
  const [period, setPeriod]     = useState<Period>('TODAY');
  const [dateFrom, setDateFrom] = useState(todayStr());
  const [dateTo, setDateTo]     = useState(todayStr());
  const [loading, setLoading]   = useState(false);
  const [data, setData]         = useState<LoadedData | null>(null);
  const [copied, setCopied]     = useState(false);
  const firstLoadRef            = useRef(false);

  const setPeriodRange = (p: Period) => {
    setPeriod(p);
    if (p === 'TODAY') { const t = todayStr(); setDateFrom(t); setDateTo(t); }
    else if (p === 'WEEK')  { const r = getWeekRange();  setDateFrom(r.from); setDateTo(r.to); }
    else if (p === 'MONTH') { const r = getMonthRange(); setDateFrom(r.from); setDateTo(r.to); }
    else if (p === 'YEAR')  { const r = getYearRange();  setDateFrom(r.from); setDateTo(r.to); }
  };

  const filterByDate = (arr: any[], dateField: string) =>
    arr.filter(r => {
      const d = (r[dateField] || '').slice(0, 10);
      return (!dateFrom || d >= dateFrom) && (!dateTo || d <= dateTo);
    });

  const loadAll = useCallback(async (from: string, to: string) => {
    setLoading(true);
    try {
      const [summaryRes, salesRes, purchasesRes, jobsRes, bookingsRes, expensesRes, incomesRes, productsRes] =
        await Promise.allSettled([
          summaryReportService.daily(from, to),
          saleApiService.getAllPaged(0, 500, '', from, to),
          purchaseApiService.getAllPaged(0, 500, '', from, to),
          serviceJobService.getAll(0, 500, '', from, to),
          bookingService.getAll(0, 500, '', from, to),
          expenseApiService.getAll(),
          incomeApiService.getAll(),
          productService.getAll(),
        ]);

      const summary = summaryRes.status === 'fulfilled' ? (summaryRes.value as any)?.data ?? {} : {};
      const sales     = salesRes.status === 'fulfilled'     ? (salesRes.value as any)?.content ?? [] : [];
      const purchases = purchasesRes.status === 'fulfilled' ? (purchasesRes.value as any)?.content ?? [] : [];
      const jobs      = jobsRes.status === 'fulfilled'      ? (jobsRes.value as any)?.data?.content ?? (jobsRes.value as any)?.content ?? [] : [];
      const bookings  = bookingsRes.status === 'fulfilled'  ? (bookingsRes.value as any)?.data?.content ?? (bookingsRes.value as any)?.content ?? [] : [];
      const allExpenses = expensesRes.status === 'fulfilled' ? (expensesRes.value as any) ?? [] : [];
      const allIncomes  = incomesRes.status === 'fulfilled'  ? (incomesRes.value as any) ?? [] : [];
      const products    = productsRes.status === 'fulfilled' ? (productsRes.value as any) ?? [] : [];

      const expenses = filterByDate(Array.isArray(allExpenses) ? allExpenses : [], 'expenseDate');
      const incomes  = filterByDate(Array.isArray(allIncomes)  ? allIncomes  : [], 'incomeDate');

      setData({
        summary: {
          saleCount:        Number(summary.saleCount ?? 0),
          totalIncome:      Number(summary.totalIncome ?? 0),
          totalExpenses:    Number(summary.totalExpenses ?? 0),
          netProfit:        Number(summary.netProfit ?? 0),
          netSaleRevenue:   Number(summary.netSaleRevenue ?? 0),
          serviceRevenue:   Number(summary.serviceRevenue ?? 0),
          otherIncome:      Number(summary.otherIncome ?? 0),
          purchaseAmount:   Number(summary.purchaseAmount ?? 0),
          netPurchaseCost:  Number(summary.netPurchaseCost ?? 0),
        },
        sales:       Array.isArray(sales)     ? sales     : [],
        purchases:   Array.isArray(purchases) ? purchases : [],
        serviceJobs: Array.isArray(jobs)      ? jobs      : [],
        bookings:    Array.isArray(bookings)  ? bookings  : [],
        expenses,
        incomes,
        products:    Array.isArray(products)  ? products  : [],
      });
    } finally {
      setLoading(false);
    }
  }, []); // eslint-disable-line

  // Auto-load on first render
  React.useEffect(() => {
    if (!firstLoadRef.current) {
      firstLoadRef.current = true;
      loadAll(dateFrom, dateTo);
    }
  }, []); // eslint-disable-line

  const handleRefresh = () => loadAll(dateFrom, dateTo);

  const periodLabel = () => {
    if (period === 'TODAY') return `Today (${dateFrom})`;
    if (period === 'WEEK')  return `This Week (${dateFrom} ~ ${dateTo})`;
    if (period === 'MONTH') return `This Month (${dateFrom} ~ ${dateTo})`;
    if (period === 'YEAR')  return `This Year (${dateFrom} ~ ${dateTo})`;
    return `${dateFrom} ~ ${dateTo}`;
  };

  const handlePdf = () => {
    if (!data) return;
    const snapshotData: SnapshotData = {
      periodLabel: periodLabel(),
      dateFrom,
      dateTo,
      summary: data.summary,
      sales:       data.sales,
      purchases:   data.purchases,
      serviceJobs: data.serviceJobs,
      bookings:    data.bookings,
      expenses:    data.expenses,
      incomes:     data.incomes,
      products:    data.products,
    };
    const html = buildSnapshotReportHtml(snapshotData, getCachedCompanySettings());
    const w = window.open('', '_blank', 'width=900,height=700');
    if (!w) return;
    w.document.write(html);
    w.document.close();
    setTimeout(() => w.print(), 600);
  };

  const buildShareText = (): string => {
    if (!data) return '';
    const d = data.summary;
    const lines = [
      `📊 Daily Snapshot — ${periodLabel()}`,
      `━━━━━━━━━━━━━━━━━━━━━━`,
      `💰 Total Income   : ${fmt(d.totalIncome)} Ks`,
      `📦 Net Purchase   : ${fmt(d.netPurchaseCost)} Ks`,
      `💸 Expenses       : ${fmt(d.totalExpenses)} Ks`,
      `📈 Net Profit     : ${fmt(d.netProfit)} Ks`,
      `━━━━━━━━━━━━━━━━━━━━━━`,
      `🛒 Sales          : ${data.sales.length} vouchers`,
      `🏭 Purchases      : ${data.purchases.length} vouchers`,
      `🔧 Service Jobs   : ${data.serviceJobs.length}`,
      `📥 Bookings       : ${data.bookings.length}`,
      `📦 Products       : ${data.products.length} items`,
      ...(data.products.filter(p => (p.currentStock ?? p.stockQty ?? 0) <= (p.minStockLevel ?? 0) && (p.minStockLevel ?? 0) > 0).length > 0
        ? [`⚠️  Low Stock items: ${data.products.filter(p => (p.currentStock ?? p.stockQty ?? 0) <= (p.minStockLevel ?? 0) && (p.minStockLevel ?? 0) > 0).length}`]
        : []),
    ];
    return lines.join('\n');
  };

  const handleShare = async () => {
    const text = buildShareText();
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: `Daily Snapshot — ${periodLabel()}`, text });
      } catch (_) { /* cancelled */ }
    } else {
      await copyShareText();
    }
  };

  const copyShareText = async () => {
    const text = buildShareText();
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.opacity = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (_) {
      // Some WebViews block clipboard access. The share target will still receive the text.
    }
    return text;
  };

  const handleTelegramShare = async () => {
    const text = await copyShareText();
    const url = `https://t.me/share/url?url=&text=${encodeURIComponent(text)}`;
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleViberShare = async () => {
    const text = await copyShareText();
    window.location.href = `viber://forward?text=${encodeURIComponent(text)}`;
  };

  const handleCopy = async () => {
    await copyShareText();
  };
  const d = data?.summary;
  const PERIODS: { key: Period; label: string }[] = [
    { key: 'TODAY', label: 'Today' },
    { key: 'WEEK',  label: 'This Week' },
    { key: 'MONTH', label: 'This Month' },
    { key: 'YEAR',  label: 'This Year' },
  ];

  return (
    <div className="w-full max-w-none space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center">
            <ClipboardList size={20} className="text-white" />
          </div>
          <div>
            <h2 className="text-xl font-black text-slate-800">တစ်နေ့တာ Snapshot Report</h2>
            <p className="text-xs text-slate-500 mt-0.5">Sales · Purchases · Services · Bookings · Income/Expense · Stock</p>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={handleRefresh}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} />
            Refresh
          </button>
          <button
            onClick={handleCopy}
            disabled={!data}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40"
          >
            {copied ? <Check size={13} className="text-emerald-500" /> : <Copy size={13} />}
            {copied ? 'Copied!' : 'Copy Text'}
          </button>
          {typeof navigator.share === 'function' && (
            <button
              onClick={handleShare}
              disabled={!data}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-sky-600 text-white rounded-lg text-xs font-bold hover:bg-sky-700 disabled:opacity-40"
            >
              <Share2 size={13} />
              Share
            </button>
          )}
          <button
            onClick={handleTelegramShare}
            disabled={!data}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#229ED9] text-white rounded-lg text-xs font-bold hover:bg-[#168ac1] disabled:opacity-40"
          >
            <Send size={13} />
            Telegram
          </button>
          <button
            onClick={handleViberShare}
            disabled={!data}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[#7360F2] text-white rounded-lg text-xs font-bold hover:bg-[#5d4bd6] disabled:opacity-40"
          >
            <Share2 size={13} />
            Viber
          </button>
          <button
            onClick={handlePdf}
            disabled={!data}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700 disabled:opacity-40"
          >
            <FileDown size={13} />
            PDF ထုတ်မည်
          </button>
        </div>
      </div>

      {/* Period selector + date range */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col lg:flex-row lg:items-center gap-3">
          <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-lg flex-shrink-0">
            {PERIODS.map(({ key, label }) => (
              <button
                key={key}
                onClick={() => { setPeriodRange(key); }}
                className={`px-3 py-1.5 rounded-md text-xs font-bold transition-colors ${period === key ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-indigo-700'}`}
              >
                {label}
              </button>
            ))}
            <button
              onClick={() => setPeriod('CUSTOM')}
              className={`px-3 py-1.5 rounded-md text-xs font-bold transition-colors ${period === 'CUSTOM' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-indigo-700'}`}
            >
              Custom
            </button>
          </div>
          <div className="flex items-center gap-2">
            <input
              type="date"
              value={dateFrom}
              onChange={e => { setDateFrom(e.target.value); setPeriod('CUSTOM'); }}
              className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
            <span className="text-slate-400 text-xs">—</span>
            <input
              type="date"
              value={dateTo}
              onChange={e => { setDateTo(e.target.value); setPeriod('CUSTOM'); }}
              className="px-2.5 py-1.5 border border-slate-200 rounded-lg text-xs bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
            <button
              onClick={handleRefresh}
              className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700"
            >
              Load
            </button>
          </div>
          {data && (
            <span className="text-xs text-slate-400 ml-auto">
              {periodLabel()}
            </span>
          )}
        </div>
      </div>

      {loading && (
        <div className="flex items-center justify-center h-32 text-slate-400 text-sm">
          <RefreshCw size={18} className="animate-spin mr-2" /> ဖတ်နေသည်...
        </div>
      )}

      {!loading && data && (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <StatCard
              label="Total Income"
              value={`${fmt(d!.totalIncome)} Ks`}
              sub={`Sales: ${fmt(d!.netSaleRevenue)}`}
              colorClass="bg-blue-50 border-blue-200 text-blue-800"
            />
            <StatCard
              label="Total Expenses"
              value={`${fmt(d!.totalExpenses)} Ks`}
              sub={`Purchase: ${fmt(d!.netPurchaseCost)}`}
              colorClass="bg-rose-50 border-rose-200 text-rose-800"
            />
            <StatCard
              label="Net Profit"
              value={`${fmt(d!.netProfit)} Ks`}
              sub={d!.netProfit >= 0 ? 'Profit' : 'Loss'}
              colorClass={d!.netProfit >= 0 ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-orange-50 border-orange-200 text-orange-800'}
            />
            <StatCard
              label="Sales Vouchers"
              value={String(data.sales.length)}
              sub={`Service Jobs: ${data.serviceJobs.length}`}
              colorClass="bg-violet-50 border-violet-200 text-violet-800"
            />
          </div>

          {/* Income / Expense mini breakdown */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-2">
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">Income Breakdown</p>
              {[
                { label: 'Net Sales Revenue', value: d!.netSaleRevenue, icon: <TrendingUp size={13} className="text-indigo-500" /> },
                { label: 'Service Revenue',   value: d!.serviceRevenue, icon: <Wrench size={13} className="text-emerald-500" /> },
                { label: 'Other Income',      value: d!.otherIncome,    icon: <Wallet size={13} className="text-amber-500" /> },
              ].map(r => (
                <div key={r.label} className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 text-xs text-slate-600">{r.icon}{r.label}</div>
                  <span className="text-xs font-bold text-slate-800">{fmt(r.value)} Ks</span>
                </div>
              ))}
              <div className="flex items-center justify-between border-t border-slate-100 pt-2">
                <span className="text-xs font-black text-slate-700">Total Income</span>
                <span className="text-sm font-black text-blue-700">{fmt(d!.totalIncome)} Ks</span>
              </div>
            </div>
            <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-2">
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">Expense Breakdown</p>
              {[
                { label: 'Net Purchase Cost', value: d!.netPurchaseCost, icon: <Truck size={13} className="text-violet-500" /> },
                { label: 'Expenses',          value: d!.totalExpenses,   icon: <TrendingDown size={13} className="text-rose-500" /> },
              ].map(r => (
                <div key={r.label} className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 text-xs text-slate-600">{r.icon}{r.label}</div>
                  <span className="text-xs font-bold text-slate-800">{fmt(r.value)} Ks</span>
                </div>
              ))}
              <div className="flex items-center justify-between border-t border-slate-100 pt-2">
                <span className="text-xs font-black text-slate-700">Net Profit</span>
                <span className={`text-sm font-black ${d!.netProfit >= 0 ? 'text-emerald-700' : 'text-rose-700'}`}>{fmt(d!.netProfit)} Ks</span>
              </div>
            </div>
          </div>

          {/* Sales */}
          <SectionCard icon={<ShoppingCart size={15} />} title="ရောင်းချမှုများ / Sales" count={data.sales.length} accentClass="bg-indigo-700">
            <table className="w-full min-w-[600px]">
              <thead><tr>
                <Th>#</Th><Th>Code</Th><Th>Customer</Th><Th>Staff</Th><Th>Date</Th><Th right>Amount (Ks)</Th><Th>Status</Th>
              </tr></thead>
              <tbody>
                {data.sales.length === 0 ? <EmptyRow cols={7} /> : data.sales.map((s, i) => (
                  <tr key={s.id} className="hover:bg-slate-50">
                    <Td muted>{i + 1}</Td>
                    <Td mono>{s.saleCode || `#${s.id}`}</Td>
                    <Td>{s.customerName || '—'}</Td>
                    <Td>{s.staffName || '—'}</Td>
                    <Td muted>{s.saleDate ? new Date(s.saleDate).toLocaleDateString() : '—'}</Td>
                    <Td right>{fmt(s.netAmount ?? s.totalAmount)}</Td>
                    <Td>{statusBadge(s.paymentStatus)}</Td>
                  </tr>
                ))}
              </tbody>
              {data.sales.length > 0 && (
                <tfoot>
                  <tr className="bg-indigo-50">
                    <td colSpan={5} className="px-3 py-2 text-right text-xs font-black text-slate-600">Total ({data.sales.length})</td>
                    <td className="px-3 py-2 text-right text-sm font-black text-indigo-700">
                      {fmt(data.sales.reduce((s, r) => s + (r.netAmount ?? r.totalAmount ?? 0), 0))}
                    </td>
                    <td />
                  </tr>
                </tfoot>
              )}
            </table>
          </SectionCard>

          {/* Purchases */}
          <SectionCard icon={<Truck size={15} />} title="ဝယ်ယူမှုများ / Purchases" count={data.purchases.length} accentClass="bg-violet-700">
            <table className="w-full min-w-[600px]">
              <thead><tr>
                <Th>#</Th><Th>Code</Th><Th>Supplier</Th><Th>Staff</Th><Th>Date</Th><Th right>Amount (Ks)</Th><Th>Status</Th>
              </tr></thead>
              <tbody>
                {data.purchases.length === 0 ? <EmptyRow cols={7} /> : data.purchases.map((p, i) => (
                  <tr key={p.id} className="hover:bg-slate-50">
                    <Td muted>{i + 1}</Td>
                    <Td mono>{p.purchaseCode || `#${p.id}`}</Td>
                    <Td>{p.supplierName || '—'}</Td>
                    <Td>{p.staffName || '—'}</Td>
                    <Td muted>{p.purchaseDate ? new Date(p.purchaseDate).toLocaleDateString() : '—'}</Td>
                    <Td right>{fmt(p.netAmount ?? p.totalAmount)}</Td>
                    <Td>{statusBadge(p.paymentStatus)}</Td>
                  </tr>
                ))}
              </tbody>
              {data.purchases.length > 0 && (
                <tfoot>
                  <tr className="bg-violet-50">
                    <td colSpan={5} className="px-3 py-2 text-right text-xs font-black text-slate-600">Total ({data.purchases.length})</td>
                    <td className="px-3 py-2 text-right text-sm font-black text-violet-700">
                      {fmt(data.purchases.reduce((s, r) => s + (r.netAmount ?? r.totalAmount ?? 0), 0))}
                    </td>
                    <td />
                  </tr>
                </tfoot>
              )}
            </table>
          </SectionCard>

          {/* Service Jobs */}
          <SectionCard icon={<Wrench size={15} />} title="ဝန်ဆောင်မှုလုပ်ငန်းများ / Service Jobs" count={data.serviceJobs.length} accentClass="bg-emerald-700">
            <table className="w-full min-w-[640px]">
              <thead><tr>
                <Th>#</Th><Th>Job No</Th><Th>Customer</Th><Th>Item</Th><Th>Staff</Th><Th>Status</Th><Th right>Amount (Ks)</Th>
              </tr></thead>
              <tbody>
                {data.serviceJobs.length === 0 ? <EmptyRow cols={7} /> : data.serviceJobs.map((j, i) => (
                  <tr key={j.id} className="hover:bg-slate-50">
                    <Td muted>{i + 1}</Td>
                    <Td mono>{j.jobNo || `#${j.id}`}</Td>
                    <Td>{j.customerName || '—'}</Td>
                    <Td>{j.itemName || '—'}</Td>
                    <Td>{j.assignedStaffName || '—'}</Td>
                    <Td>{statusBadge(j.status)}</Td>
                    <Td right>{fmt(j.netAmount ?? j.finalCost ?? 0)}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </SectionCard>

          {/* Bookings */}
          <SectionCard icon={<CalendarClock size={15} />} title="ပစ္စည်းလက်ခံ / Bookings" count={data.bookings.length} accentClass="bg-sky-700">
            <table className="w-full min-w-[680px]">
              <thead><tr>
                <Th>#</Th><Th>Booking No</Th><Th>Customer</Th><Th>Item / Device</Th><Th>Date In</Th><Th>Staff</Th><Th>Status</Th>
              </tr></thead>
              <tbody>
                {data.bookings.length === 0 ? <EmptyRow cols={7} /> : data.bookings.map((b, i) => (
                  <tr key={b.id} className="hover:bg-slate-50">
                    <Td muted>{i + 1}</Td>
                    <Td mono>{b.bookingNo || `#${b.id}`}</Td>
                    <Td>{b.customerName || '—'}</Td>
                    <Td>{b.itemName || b.deviceModel || '—'}</Td>
                    <Td muted>{b.receivedDate || b.bookingDate ? new Date(b.receivedDate || b.bookingDate).toLocaleDateString() : '—'}</Td>
                    <Td>{b.assignedStaffName || b.technicianName || '—'}</Td>
                    <Td>{statusBadge(b.status)}</Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </SectionCard>

          {/* Income & Expenses */}
          <SectionCard icon={<Wallet size={15} />} title="ဝင်ငွေ / ထွက်ငွေ (Income & Expenses)" count={data.expenses.length + data.incomes.length} accentClass="bg-amber-700">
            {(() => {
              const combined = [
                ...data.expenses.map(e => ({ ...e, _type: 'Expense' as const })),
                ...data.incomes.map(i  => ({ ...i, _type: 'Income'  as const })),
              ].sort((a, b) =>
                new Date(a.expenseDate || a.incomeDate || 0).getTime() -
                new Date(b.expenseDate || b.incomeDate || 0).getTime()
              );
              return (
                <table className="w-full min-w-[560px]">
                  <thead><tr>
                    <Th>#</Th><Th>Code</Th><Th>Date</Th><Th>Description</Th><Th>Staff</Th><Th right>Amount (Ks)</Th><Th>Type</Th>
                  </tr></thead>
                  <tbody>
                    {combined.length === 0 ? <EmptyRow cols={7} /> : combined.map((r, i) => {
                      const isExp = r._type === 'Expense';
                      return (
                        <tr key={`${r._type}-${r.id}`} className="hover:bg-slate-50">
                          <Td muted>{i + 1}</Td>
                          <Td mono>{r.expenseCode || r.incomeCode || `#${r.id}`}</Td>
                          <Td muted>{r.expenseDate || r.incomeDate ? new Date(r.expenseDate || r.incomeDate).toLocaleDateString() : '—'}</Td>
                          <Td>{r.description || r.accountName || '—'}</Td>
                          <Td>{r.staffName || '—'}</Td>
                          <td className={`px-3 py-2 text-sm border-b border-slate-100 text-right font-bold ${isExp ? 'text-rose-600' : 'text-emerald-600'}`}>
                            {isExp ? '−' : '+'}{fmt(r.amount)}
                          </td>
                          <Td>
                            <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-bold ${isExp ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'}`}>
                              {r._type}
                            </span>
                          </Td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              );
            })()}
          </SectionCard>

          {/* Product Stock */}
          <SectionCard icon={<Package size={15} />} title="ပစ္စည်းလက်ကျန် / Product Stock" count={data.products.length} accentClass="bg-slate-700">
            <table className="w-full min-w-[560px]">
              <thead><tr>
                <Th>#</Th><Th>Code</Th><Th>Product Name</Th><Th>Category</Th><Th right>Stock</Th><Th>Unit</Th><Th>Alert</Th>
              </tr></thead>
              <tbody>
                {data.products.length === 0 ? <EmptyRow cols={7} /> : data.products.map((p, i) => {
                  const stock = p.currentStock ?? p.stockQty ?? 0;
                  const isLow = stock <= (p.minStockLevel ?? 0) && (p.minStockLevel ?? 0) > 0;
                  return (
                    <tr key={p.id} className={`hover:bg-slate-50 ${isLow ? 'bg-amber-50/60' : ''}`}>
                      <Td muted>{i + 1}</Td>
                      <Td mono>{p.productCode}</Td>
                      <Td>{p.name}</Td>
                      <Td muted>{p.categoryName || '—'}</Td>
                      <td className={`px-3 py-2 text-sm border-b border-slate-100 text-right font-bold ${isLow ? 'text-amber-700' : 'text-slate-700'}`}>
                        {stock.toLocaleString()}
                      </td>
                      <Td muted>{p.unitName || '—'}</Td>
                      <Td>
                        {isLow && (
                          <span className="inline-block px-2 py-0.5 rounded text-[10px] font-black bg-amber-100 text-amber-700">LOW</span>
                        )}
                      </Td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </SectionCard>
        </>
      )}

      {!loading && !data && (
        <div className="flex flex-col items-center justify-center h-48 text-slate-400 gap-3">
          <ClipboardList size={36} className="opacity-30" />
          <p className="text-sm">Period ရွေးပြီး "Load" နှိပ်ပါ</p>
        </div>
      )}
    </div>
  );
};

export default DailySnapshotReport;
