import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useDataEvents } from '../hooks/useDataEvents';
import { accountingApiService } from '../services/accountingapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { profitLossService } from '../services/profitlossapiservice';
import { coaService } from '../services/coaapiservice';
import {
  AccountBalanceDTO, PaymentTransactionDTO, PaymentMethodDTO,
  AccountType, ProfitLossDTO, ChartOfAccountDTO, AppRoute
} from '../types';
import {
  Wallet, Banknote, Building2, RefreshCw,
  TrendingUp, TrendingDown, ArrowDownLeft, ArrowUpRight,
  ArrowLeftRight, Save, ChevronRight, ExternalLink, Landmark, PieChart
} from 'lucide-react';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const money = (v: number | undefined) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(v || 0);

const fmtDate = (d?: string | null) => {
  if (!d) return '-';
  try { return new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: '2-digit' }); }
  catch { return '-'; }
};

type Direction = 'IN' | 'OUT' | 'NEUTRAL';
const directionOf = (refType?: string): Direction => {
  switch ((refType || '').toLowerCase().replace(/[^a-z_]/g, '')) {
    case 'sale':            return 'IN';
    case 'purchase_return': return 'IN';
    case 'service':         return 'IN';
    case 'purchase':        return 'OUT';
    case 'debt_payment':    return 'OUT';
    case 'sale_return':     return 'OUT';
    default:                return 'NEUTRAL';
  }
};

const TYPE_LABELS: Record<string, string> = {
  Sale: 'Sale', Purchase: 'Purchase', Sale_Return: 'Sale Return',
  Purchase_Return: 'Purchase Return', Debt_Payment: 'Debt Payment',
  Opening_Balance: 'Opening Bal.', Transfer: 'Transfer', Service: 'Service', Other: 'Other',
};

const firstOfMonth = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
};
const today = () => new Date().toISOString().slice(0, 10);

const AccountingDashboard: React.FC = () => {
  const [balances, setBalances]           = useState<AccountBalanceDTO[]>([]);
  const [transactions, setTransactions]   = useState<PaymentTransactionDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [accounts, setAccounts]           = useState<ChartOfAccountDTO[]>([]);
  const [pl, setPl]                       = useState<ProfitLossDTO | null>(null);
  const [loading, setLoading]             = useState(true);
  const [plLoading, setPlLoading]         = useState(false);
  const [plFrom, setPlFrom]               = useState(firstOfMonth());
  const [plTo, setPlTo]                   = useState(today());

  const [transferSaving, setTransferSaving] = useState(false);
  const [transferForm, setTransferForm]     = useState({
    fromPaymentMethodId: 0, toPaymentMethodId: 0, amount: '', transactionNo: '', note: ''
  });

  const fetchData = async () => {
    setLoading(true);
    try {
      const [balanceRes, transRes, payRes, coaRes] = await Promise.all([
        accountingApiService.getAllBalances(),
        accountingApiService.getDetailedReport(),
        paymentMethodService.getAllActive(),
        coaService.getAll(),
      ]);
      setBalances(balanceRes);
      setTransactions(Array.isArray(transRes) ? transRes : []);
      setPaymentMethods(payRes);
      setAccounts(coaRes);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchPL = async (from = plFrom, to = plTo) => {
    setPlLoading(true);
    try { setPl(await profitLossService.getReport(from, to)); }
    catch { } finally { setPlLoading(false); }
  };

  useEffect(() => { fetchData(); fetchPL(); }, []);
  useRefreshOnTabActivate(fetchData);
  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Payment', 'Journal'], () => {
    fetchData(); fetchPL();
  });

  const accountById        = useMemo(() => new Map(accounts.map(a => [a.id, a])), [accounts]);
  const balanceByAccountId = useMemo(() => new Map(balances.map(b => [b.accountId, b])), [balances]);

  const cashAndBankMethods = useMemo(() => {
    const seen = new Set<number>();
    return paymentMethods
      .filter(m => {
        const txt = `${m.methodName || ''} ${m.accountName || ''}`.toLowerCase();
        return m.accountId && ['cash', 'bank', 'kpay', 'wave', 'pay', 'kbz', 'aya', 'mpu'].some(t => txt.includes(t));
      })
      .filter(m => {
        if (!m.accountId || seen.has(m.accountId)) return false;
        seen.add(m.accountId);
        return true;
      })
      .map(m => {
        const b = balanceByAccountId.get(m.accountId!);
        return { ...m, currentBalance: Number(b?.currentBalance) || 0 };
      });
  }, [balanceByAccountId, paymentMethods]);

  const totalCashBank = cashAndBankMethods.reduce((s, m) => s + m.currentBalance, 0);

  const totalByType = (type: AccountType) =>
    balances.reduce((sum, b) => {
      const acc = accountById.get(b.accountId);
      return acc?.accountType === type ? sum + (Number(b.currentBalance) || 0) : sum;
    }, 0);

  const totalIn  = useMemo(() => transactions.filter(t => directionOf(t.referenceType) === 'IN').reduce((s, t) => s + (t.amount || 0), 0), [transactions]);
  const totalOut = useMemo(() => transactions.filter(t => directionOf(t.referenceType) === 'OUT').reduce((s, t) => s + (t.amount || 0), 0), [transactions]);

  const recentTxns = useMemo(() =>
    [...transactions]
      .sort((a, b) => new Date(b.paymentDate ?? 0).getTime() - new Date(a.paymentDate ?? 0).getTime())
      .slice(0, 8)
  , [transactions]);

  const saveTransfer = async () => {
    const amount = Number(transferForm.amount);
    if (!transferForm.fromPaymentMethodId || !transferForm.toPaymentMethodId)
      return Swal.fire('Validation', 'From / To payment method ရွေးပေးပါ။', 'warning');
    if (transferForm.fromPaymentMethodId === transferForm.toPaymentMethodId)
      return Swal.fire('Validation', 'From နှင့် To မတူသင့်ပါ။', 'warning');
    if (!amount || amount <= 0)
      return Swal.fire('Validation', 'Amount ထည့်ပေးပါ။', 'warning');
    setTransferSaving(true);
    try {
      await accountingApiService.transferPaymentMethodBalance({
        fromPaymentMethodId: transferForm.fromPaymentMethodId,
        toPaymentMethodId:   transferForm.toPaymentMethodId,
        amount,
        transactionNo: transferForm.transactionNo.trim() || undefined,
        note:          transferForm.note.trim() || undefined,
      });
      setTransferForm({ fromPaymentMethodId: 0, toPaymentMethodId: 0, amount: '', transactionNo: '', note: '' });
      Swal.fire({ icon: 'success', title: 'Transfer လုပ်ဆောင်ပြီးပါပြီ', toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
      await fetchData();
    } catch (err: any) {
      Swal.fire('Error', err?.message || 'Failed', 'error');
    } finally {
      setTransferSaving(false);
    }
  };

  return (
    <div className="space-y-5">

      {/* ── Header ──────────────────────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Accounting Dashboard</h2>
          <p className="text-xs text-slate-500 mt-0.5">Financial overview &amp; cash position</p>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to={AppRoute.PAYMENT_TRANSACTIONS}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700 transition-colors"
          >
            <ExternalLink size={13} /> ငွေပေးချေမှတ်တမ်း
          </Link>
          <button
            onClick={() => { fetchData(); fetchPL(); }}
            className="flex items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50"
          >
            <RefreshCw size={13} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
        </div>
      </div>

      {/* ── KPI Row ─────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {/* Cash & Bank */}
        <div className="bg-white rounded-xl border border-emerald-200 border-l-4 border-l-emerald-500 shadow-sm p-4">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 bg-emerald-50 rounded-lg flex items-center justify-center">
              <Wallet size={15} className="text-emerald-600" />
            </div>
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Cash &amp; Bank</span>
          </div>
          <p className="text-2xl font-bold text-emerald-700 tabular-nums leading-none">{money(totalCashBank)}</p>
          <p className="text-[10px] text-slate-400 mt-1">Ks — Liquid assets</p>
        </div>

        {/* Total IN */}
        <div className="bg-white rounded-xl border border-indigo-200 border-l-4 border-l-indigo-500 shadow-sm p-4">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 bg-indigo-50 rounded-lg flex items-center justify-center">
              <ArrowDownLeft size={15} className="text-indigo-600" />
            </div>
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">ဝင်ငွေ (IN)</span>
          </div>
          <p className="text-2xl font-bold text-indigo-700 tabular-nums leading-none">{money(totalIn)}</p>
          <p className="text-[10px] text-slate-400 mt-1">Ks — All time</p>
        </div>

        {/* Total OUT */}
        <div className="bg-white rounded-xl border border-rose-200 border-l-4 border-l-rose-500 shadow-sm p-4">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 bg-rose-50 rounded-lg flex items-center justify-center">
              <ArrowUpRight size={15} className="text-rose-600" />
            </div>
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">ထွက်ငွေ (OUT)</span>
          </div>
          <p className="text-2xl font-bold text-rose-700 tabular-nums leading-none">{money(totalOut)}</p>
          <p className="text-[10px] text-slate-400 mt-1">Ks — All time</p>
        </div>

        {/* Net Profit / Loss */}
        <div className={`bg-white rounded-xl shadow-sm p-4 border-l-4 ${pl && pl.netProfit >= 0 ? 'border border-amber-200 border-l-amber-500' : 'border border-orange-200 border-l-orange-500'}`}>
          <div className="flex items-center gap-2 mb-2">
            <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${pl && pl.netProfit >= 0 ? 'bg-amber-50' : 'bg-orange-50'}`}>
              {pl && pl.netProfit >= 0
                ? <TrendingUp size={15} className="text-amber-600" />
                : <TrendingDown size={15} className="text-orange-600" />}
            </div>
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
              Net {pl && pl.netProfit >= 0 ? 'Profit' : 'Loss'}
            </span>
          </div>
          <p className={`text-2xl font-bold tabular-nums leading-none ${pl && pl.netProfit >= 0 ? 'text-amber-700' : 'text-orange-700'}`}>
            {money(Math.abs(pl?.netProfit ?? 0))}
          </p>
          <p className="text-[10px] text-slate-400 mt-1">Ks — This period</p>
        </div>
      </div>

      {/* ── P&L  +  Cash Position ──────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">

        {/* P&L Panel */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
          <div className="px-5 py-3 bg-slate-800 flex flex-wrap items-center justify-between gap-2 flex-shrink-0">
            <span className="font-bold text-white text-sm">Profit &amp; Loss</span>
            <div className="flex items-center gap-1.5">
              <input type="date" value={plFrom} onChange={e => setPlFrom(e.target.value)}
                className="px-2 py-1 bg-slate-700 border border-slate-600 rounded text-[11px] text-slate-200 focus:outline-none" />
              <span className="text-slate-400 text-xs">→</span>
              <input type="date" value={plTo} onChange={e => setPlTo(e.target.value)}
                className="px-2 py-1 bg-slate-700 border border-slate-600 rounded text-[11px] text-slate-200 focus:outline-none" />
              <button onClick={() => fetchPL(plFrom, plTo)} disabled={plLoading}
                className="px-2 py-1 bg-indigo-600 hover:bg-indigo-500 text-white text-[11px] font-bold rounded disabled:opacity-60">
                {plLoading ? '…' : 'Apply'}
              </button>
              <button
                onClick={() => { const f = firstOfMonth(); const t = today(); setPlFrom(f); setPlTo(t); fetchPL(f, t); }}
                className="px-2 py-1 bg-slate-700 hover:bg-slate-600 text-slate-300 text-[11px] rounded"
              >
                This Month
              </button>
            </div>
          </div>

          {pl ? (
            <>
              <div className="grid grid-cols-2 divide-x divide-y divide-slate-100 flex-1">
                <div className="p-5 bg-indigo-50">
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Net Revenue</p>
                  <p className="text-2xl font-bold text-indigo-700 mt-1 tabular-nums">{money(pl.netRevenue)}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5">Ks</p>
                </div>
                <div className="p-5 bg-amber-50">
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Net Purchases</p>
                  <p className="text-2xl font-bold text-amber-700 mt-1 tabular-nums">{money(pl.netPurchases)}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5">Ks</p>
                </div>
                <div className="p-5 bg-rose-50">
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Total Expenses</p>
                  <p className="text-2xl font-bold text-rose-700 mt-1 tabular-nums">{money(pl.totalExpenses)}</p>
                  <p className="text-[10px] text-slate-400 mt-0.5">Ks</p>
                </div>
                <div className={`p-5 ${pl.netProfit >= 0 ? 'bg-emerald-50' : 'bg-orange-50'}`}>
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                    {pl.netProfit >= 0 ? 'Net Profit' : 'Net Loss'}
                  </p>
                  <p className={`text-2xl font-bold mt-1 tabular-nums ${pl.netProfit >= 0 ? 'text-emerald-700' : 'text-orange-700'}`}>
                    {money(Math.abs(pl.netProfit))}
                  </p>
                  <p className="text-[10px] text-slate-400 mt-0.5">Ks</p>
                </div>
              </div>
              <div className="px-5 py-3 border-t border-slate-100 bg-slate-50 flex flex-wrap items-center gap-4 text-xs text-slate-500">
                <span>Gross Sales: <strong className="text-slate-700">{money(pl.grossSales)} Ks</strong></span>
                {pl.salesReturns > 0 && <span className="text-rose-500">(-) Returns: {money(pl.salesReturns)} Ks</span>}
                <span>Gross Profit: <strong className={pl.grossProfit >= 0 ? 'text-emerald-600' : 'text-rose-600'}>{money(pl.grossProfit)} Ks</strong></span>
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center p-10 text-slate-400 text-xs">
              {plLoading ? 'Loading P&L...' : 'No P&L data available'}
            </div>
          )}
        </div>

        {/* Cash Position Panel */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
          <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between flex-shrink-0">
            <span className="font-bold text-slate-800 text-sm flex items-center gap-2">
              <Banknote size={16} className="text-emerald-600" /> Cash &amp; Bank Position
            </span>
            <span className="text-xs font-bold text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-lg border border-emerald-200 tabular-nums">
              {money(totalCashBank)} Ks
            </span>
          </div>

          <div className="flex-1 divide-y divide-slate-100 overflow-auto">
            {cashAndBankMethods.length > 0 ? cashAndBankMethods.map(m => {
              const pct = totalCashBank > 0 ? Math.max(0, (m.currentBalance / totalCashBank) * 100) : 0;
              return (
                <div key={m.id} className="px-5 py-3.5">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-semibold text-slate-700">{m.methodName}</span>
                    <span className={`text-sm font-bold tabular-nums ${m.currentBalance >= 0 ? 'text-emerald-700' : 'text-rose-600'}`}>
                      {money(m.currentBalance)} Ks
                    </span>
                  </div>
                  <div className="w-full bg-slate-100 rounded-full h-1.5">
                    <div className="bg-emerald-500 h-1.5 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
                  </div>
                  <p className="text-[10px] text-slate-400 mt-1">{pct.toFixed(1)}% of total</p>
                </div>
              );
            }) : (
              <div className="flex items-center justify-center p-10 text-slate-400 text-xs">
                No payment methods configured
              </div>
            )}
          </div>

          {/* Balance sheet mini summary */}
          <div className="grid grid-cols-3 border-t border-slate-100 bg-slate-50 flex-shrink-0">
            {[
              { label: 'Assets', value: totalByType(AccountType.Asset), icon: <Wallet size={13} />, color: 'text-emerald-600 bg-emerald-50' },
              { label: 'Liabilities', value: totalByType(AccountType.Liability), icon: <Landmark size={13} />, color: 'text-rose-600 bg-rose-50' },
              { label: 'Equity', value: totalByType(AccountType.Equity), icon: <PieChart size={13} />, color: 'text-indigo-600 bg-indigo-50' },
            ].map(c => (
              <div key={c.label} className="p-3 text-center border-r last:border-r-0 border-slate-100">
                <div className={`inline-flex items-center justify-center w-6 h-6 rounded-md mb-1 ${c.color}`}>
                  {c.icon}
                </div>
                <p className="text-[9px] font-bold text-slate-400 uppercase tracking-wider">{c.label}</p>
                <p className={`text-sm font-bold ${c.color.split(' ')[0]} tabular-nums mt-0.5`}>{money(c.value)}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Transfer Panel ──────────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-indigo-200 shadow-sm overflow-hidden">
        <div className="px-5 py-3 border-b border-indigo-100 bg-indigo-50 flex items-center gap-2">
          <div className="w-7 h-7 bg-indigo-100 rounded-lg flex items-center justify-center">
            <ArrowLeftRight size={14} className="text-indigo-600" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-800 leading-none">Cash / KBZ / Bank Transfer</h3>
            <p className="text-[10px] text-slate-500 mt-0.5">ငွေပေးချေနည်းကြား ပြောင်းလဲမှု</p>
          </div>
        </div>
        <div className="p-4 space-y-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2">
            <select
              value={transferForm.fromPaymentMethodId}
              onChange={e => setTransferForm(f => ({ ...f, fromPaymentMethodId: Number(e.target.value) || 0 }))}
              className="px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            >
              <option value={0}>From — မှ</option>
              {paymentMethods.map(m => <option key={m.id} value={m.id}>{m.methodName}</option>)}
            </select>
            <select
              value={transferForm.toPaymentMethodId}
              onChange={e => setTransferForm(f => ({ ...f, toPaymentMethodId: Number(e.target.value) || 0 }))}
              className="px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            >
              <option value={0}>To — သို့</option>
              {paymentMethods.map(m => <option key={m.id} value={m.id}>{m.methodName}</option>)}
            </select>
            <input
              type="number" min="0" step="0.01" value={transferForm.amount}
              onChange={e => setTransferForm(f => ({ ...f, amount: e.target.value }))}
              placeholder="Amount (Ks)"
              className="px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
            <input
              value={transferForm.transactionNo}
              onChange={e => setTransferForm(f => ({ ...f, transactionNo: e.target.value }))}
              placeholder="Transaction No. (optional)"
              className="px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
            />
            <button
              onClick={saveTransfer} disabled={transferSaving}
              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 disabled:opacity-60 transition-colors"
            >
              <Save size={14} /> {transferSaving ? 'Saving...' : 'Transfer'}
            </button>
          </div>
          <input
            value={transferForm.note}
            onChange={e => setTransferForm(f => ({ ...f, note: e.target.value }))}
            placeholder="Note / မှတ်ချက် (optional)"
            className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-400"
          />
        </div>
      </div>

      {/* ── Recent Transactions ──────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/60 flex items-center justify-between">
          <div>
            <h3 className="font-bold text-slate-800 text-sm">Recent Transactions</h3>
            <p className="text-[10px] text-slate-400 mt-0.5">Latest 8 records</p>
          </div>
          <Link
            to={AppRoute.PAYMENT_TRANSACTIONS}
            className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors"
          >
            View All <ChevronRight size={13} />
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[680px]">
            <thead>
              <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                <th className="px-4 py-3 border-b border-slate-100">Date</th>
                <th className="px-4 py-3 border-b border-slate-100">Dir</th>
                <th className="px-4 py-3 border-b border-slate-100">Type</th>
                <th className="px-4 py-3 border-b border-slate-100">Reference</th>
                <th className="px-4 py-3 border-b border-slate-100">Entity</th>
                <th className="px-4 py-3 border-b border-slate-100">Method</th>
                <th className="px-4 py-3 border-b border-slate-100 text-right">Amount (Ks)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {recentTxns.length > 0 ? recentTxns.map(t => {
                const dir = directionOf(t.referenceType);
                return (
                  <tr key={t.id} className="hover:bg-slate-50 text-xs transition-colors">
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{fmtDate(t.paymentDate as any)}</td>
                    <td className="px-4 py-3">
                      {dir === 'IN'  && <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200"><ArrowDownLeft size={9}/> IN</span>}
                      {dir === 'OUT' && <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200"><ArrowUpRight size={9}/> OUT</span>}
                      {dir === 'NEUTRAL' && <span className="px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-500 border border-slate-200">—</span>}
                    </td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-0.5 bg-slate-100 text-slate-600 rounded text-[10px] font-bold">
                        {TYPE_LABELS[t.referenceType ?? ''] ?? t.referenceType ?? '-'}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-indigo-600 font-medium">{t.referenceCode ?? `#${t.referenceId}`}</td>
                    <td className="px-4 py-3 text-slate-700 max-w-[120px] truncate">{t.entityName ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-500">{t.paymentMethodName ?? '-'}</td>
                    <td className={`px-4 py-3 text-right font-bold tabular-nums ${dir === 'IN' ? 'text-emerald-700' : dir === 'OUT' ? 'text-rose-700' : 'text-slate-700'}`}>
                      {dir === 'IN' ? '+' : dir === 'OUT' ? '-' : ''}{money(t.amount)}
                    </td>
                  </tr>
                );
              }) : (
                <tr>
                  <td colSpan={7} className="px-4 py-10 text-center text-slate-400 italic text-xs">
                    No transactions found
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

    </div>
  );
};

export default AccountingDashboard;
