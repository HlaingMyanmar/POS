import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { accountingApiService } from '../services/accountingapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { PaymentTransactionDTO, PaymentMethodDTO } from '../types';
import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight, Minus, RefreshCw, Save, Search, X } from 'lucide-react';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const money = (v: number | undefined) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(v || 0);

const fmtDate = (d?: string | null) => {
  if (!d) return '-';
  try { return new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }); }
  catch { return '-'; }
};

const fmtTime = (d?: string | null) => {
  if (!d) return '';
  try { return new Date(d).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }); }
  catch { return ''; }
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
  Sale:            'Sale',
  Purchase:        'Purchase',
  Sale_Return:     'Sale Return',
  Purchase_Return: 'Purchase Return',
  Debt_Payment:    'Debt Payment',
  Opening_Balance: 'Opening Balance',
  Transfer:        'Transfer',
  Service:         'Service',
  Other:           'Other',
};

const TYPE_COLORS: Record<Direction, string> = {
  IN:      'bg-emerald-50 text-emerald-700 border-emerald-200',
  OUT:     'bg-rose-50 text-rose-700 border-rose-200',
  NEUTRAL: 'bg-slate-100 text-slate-600 border-slate-200',
};

const ALL_TYPES = ['Sale', 'Purchase', 'Sale_Return', 'Purchase_Return', 'Debt_Payment', 'Opening_Balance', 'Transfer', 'Service', 'Other'];

const PaymentTransactionManagement: React.FC = () => {
  const [list, setList]       = useState<PaymentTransactionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [transferSaving, setTransferSaving] = useState(false);
  const [transferForm, setTransferForm] = useState({ fromPaymentMethodId: 0, toPaymentMethodId: 0, amount: '', transactionNo: '', note: '' });

  const [search, setSearch]     = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo]     = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [dirFilter, setDirFilter]   = useState<'' | 'IN' | 'OUT'>('');

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [txns, meth] = await Promise.all([
        accountingApiService.getDetailedReport(),
        paymentMethodService.getAllActive()
      ]);
      setList(Array.isArray(txns) ? txns : []);
      setMethods(meth);
    } catch (e) {
      console.error('Failed to load payment transactions', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);
  useRefreshOnTabActivate(fetchData);
  useDataEvents(['Sale', 'Service Job', 'Purchase', 'Payment'], fetchData);

  const filtered = useMemo(() => {
    const kw  = search.trim().toLowerCase();
    const from = dateFrom ? new Date(dateFrom) : null;
    const to   = dateTo   ? new Date(dateTo + 'T23:59:59') : null;
    return list.filter(t => {
      if (typeFilter && t.referenceType !== typeFilter) return false;
      const dir = directionOf(t.referenceType);
      if (dirFilter && dir !== dirFilter) return false;
      if (kw && !`${t.entityName ?? ''} ${t.referenceCode ?? ''} ${t.transactionNo ?? ''} ${t.paymentMethodName ?? ''} ${t.referenceType ?? ''}`.toLowerCase().includes(kw)) return false;
      if (from || to) {
        const d = t.paymentDate ? new Date(t.paymentDate) : null;
        if (!d) return false;
        if (from && d < from) return false;
        if (to   && d > to)   return false;
      }
      return true;
    });
  }, [list, search, dateFrom, dateTo, typeFilter, dirFilter]);

  const totalIn  = useMemo(() => filtered.filter(t => directionOf(t.referenceType) === 'IN').reduce((s, t) => s + (t.amount || 0), 0), [filtered]);
  const totalOut = useMemo(() => filtered.filter(t => directionOf(t.referenceType) === 'OUT').reduce((s, t) => s + (t.amount || 0), 0), [filtered]);
  const netFlow  = totalIn - totalOut;

  const clearFilters = () => { setSearch(''); setDateFrom(''); setDateTo(''); setTypeFilter(''); setDirFilter(''); };

  const saveTransfer = async () => {
    const amount = Number(transferForm.amount);
    if (!transferForm.fromPaymentMethodId || !transferForm.toPaymentMethodId) return alert('Select from/to payment methods.');
    if (transferForm.fromPaymentMethodId === transferForm.toPaymentMethodId) return alert('From and To methods must be different.');
    if (!amount || amount <= 0) return alert('Amount must be greater than zero.');
    setTransferSaving(true);
    try {
      await accountingApiService.transferPaymentMethodBalance({
        fromPaymentMethodId: transferForm.fromPaymentMethodId,
        toPaymentMethodId: transferForm.toPaymentMethodId,
        amount,
        transactionNo: transferForm.transactionNo.trim() || undefined,
        description: transferForm.note.trim() || undefined
      });
      setTransferForm({ fromPaymentMethodId: 0, toPaymentMethodId: 0, amount: '', transactionNo: '', note: '' });
      await fetchData();
    } finally {
      setTransferSaving(false);
    }
  };

  return (
    <div className="w-full max-w-none space-y-5">

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-800">Payment Transactions</h2>
          <p className="text-xs text-slate-500 mt-0.5">Sale / Purchase / Debt Payment — system-recorded cash flows</p>
        </div>
        <button onClick={fetchData} className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-emerald-200 border-l-4 border-l-emerald-500 shadow-sm p-4 flex items-center gap-4">
          <div className="w-10 h-10 rounded-lg bg-emerald-50 flex items-center justify-center flex-shrink-0">
            <ArrowDownLeft size={20} className="text-emerald-600" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Total IN (ဝင်ငွေ)</p>
            <p className="text-xl font-bold text-emerald-700">{money(totalIn)} Ks</p>
          </div>
        </div>
        <div className="bg-white rounded-xl border border-rose-200 border-l-4 border-l-rose-500 shadow-sm p-4 flex items-center gap-4">
          <div className="w-10 h-10 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
            <ArrowUpRight size={20} className="text-rose-600" />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Total OUT (ထွက်ငွေ)</p>
            <p className="text-xl font-bold text-rose-700">{money(totalOut)} Ks</p>
          </div>
        </div>
        <div className={`bg-white rounded-xl shadow-sm p-4 flex items-center gap-4 border-l-4 ${netFlow >= 0 ? 'border border-indigo-200 border-l-indigo-500' : 'border border-orange-200 border-l-orange-500'}`}>
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${netFlow >= 0 ? 'bg-indigo-50' : 'bg-orange-50'}`}>
            <Minus size={20} className={netFlow >= 0 ? 'text-indigo-600' : 'text-orange-600'} />
          </div>
          <div>
            <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Net Cash Flow</p>
            <p className={`text-xl font-bold ${netFlow >= 0 ? 'text-indigo-700' : 'text-orange-700'}`}>{netFlow >= 0 ? '+' : ''}{money(netFlow)} Ks</p>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 space-y-3">
        <div className="flex items-center gap-2">
          <ArrowLeftRight size={16} className="text-indigo-600" />
          <h3 className="text-sm font-bold text-slate-800">Cash / KPay / Bank Transfer</h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-5 gap-2">
          <select value={transferForm.fromPaymentMethodId} onChange={(e) => setTransferForm((f) => ({ ...f, fromPaymentMethodId: Number(e.target.value) || 0 }))} className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm">
            <option value={0}>From method</option>
            {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
          </select>
          <select value={transferForm.toPaymentMethodId} onChange={(e) => setTransferForm((f) => ({ ...f, toPaymentMethodId: Number(e.target.value) || 0 }))} className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm">
            <option value={0}>To method</option>
            {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
          </select>
          <input type="number" min="0" step="0.01" value={transferForm.amount} onChange={(e) => setTransferForm((f) => ({ ...f, amount: e.target.value }))} placeholder="Amount" className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm" />
          <input value={transferForm.transactionNo} onChange={(e) => setTransferForm((f) => ({ ...f, transactionNo: e.target.value }))} placeholder="Transaction no" className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm" />
          <button onClick={saveTransfer} disabled={transferSaving} className="inline-flex items-center justify-center gap-2 px-3 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 disabled:opacity-60">
            <Save size={14} /> {transferSaving ? 'Saving...' : 'Transfer'}
          </button>
        </div>
        <input value={transferForm.note} onChange={(e) => setTransferForm((f) => ({ ...f, note: e.target.value }))} placeholder="Note" className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm" />
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 space-y-3">
        <div className="flex flex-col lg:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text" value={search} onChange={e => setSearch(e.target.value)}
              placeholder="Entity name, reference code, transaction no..."
              className="w-full pl-8 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
            />
          </div>
          <div className="flex flex-wrap gap-2 items-center">
            <input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)}
              className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-600 focus:outline-none" />
            <span className="text-slate-300 text-xs">-</span>
            <input type="date" value={dateTo} onChange={e => setDateTo(e.target.value)}
              className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-600 focus:outline-none" />
          </div>
        </div>
        <div className="flex flex-wrap gap-2 items-center">
          {/* Direction pills */}
          {(['', 'IN', 'OUT'] as const).map(d => (
            <button key={d} onClick={() => setDirFilter(d)}
              className={`px-3 py-1 rounded-full text-[11px] font-bold border transition-all ${dirFilter === d
                ? d === 'IN'  ? 'bg-emerald-600 text-white border-emerald-600'
                : d === 'OUT' ? 'bg-rose-600 text-white border-rose-600'
                              : 'bg-indigo-600 text-white border-indigo-600'
                : 'bg-white text-slate-500 border-slate-200 hover:bg-slate-50'}`}>
              {d === '' ? 'All Flow' : d === 'IN' ? '↓ IN (ဝင်ငွေ)' : '↑ OUT (ထွက်ငွေ)'}
            </button>
          ))}
          <span className="text-slate-200">|</span>
          <select value={typeFilter} onChange={e => setTypeFilter(e.target.value)}
            className="px-2 py-1 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-600 focus:outline-none">
            <option value="">All Types</option>
            {ALL_TYPES.map(t => <option key={t} value={t}>{TYPE_LABELS[t] ?? t}</option>)}
          </select>
          {(search || dateFrom || dateTo || typeFilter || dirFilter) && (
            <button onClick={clearFilters} className="flex items-center gap-1 px-2 py-1 text-xs text-slate-500 hover:text-rose-500">
              <X size={12} /> Clear
            </button>
          )}
          <span className="ml-auto text-[11px] text-slate-400">{filtered.length} record(s)</span>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-auto max-h-[65vh] custom-scrollbar">
          {loading ? (
            <div className="p-10 text-center text-slate-400 text-sm">Loading...</div>
          ) : (
            <table className="w-full text-left border-collapse min-w-[820px]">
              <thead className="sticky top-0 bg-white z-10 shadow-sm">
                <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                  <th className="px-4 py-3 border-b border-slate-100">Date / Time</th>
                  <th className="px-4 py-3 border-b border-slate-100">Direction</th>
                  <th className="px-4 py-3 border-b border-slate-100">Type</th>
                  <th className="px-4 py-3 border-b border-slate-100">Reference</th>
                  <th className="px-4 py-3 border-b border-slate-100">Entity (Customer / Supplier)</th>
                  <th className="px-4 py-3 border-b border-slate-100">Method</th>
                  <th className="px-4 py-3 border-b border-slate-100">Trans No</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Amount (Ks)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.length > 0 ? filtered.map(t => {
                  const dir = directionOf(t.referenceType);
                  const typeLabel = TYPE_LABELS[t.referenceType ?? ''] ?? (t.referenceType ?? '-');
                  return (
                    <tr key={t.id} className="hover:bg-slate-50 text-xs">
                      <td className="px-4 py-3 text-slate-700">
                        <div className="font-medium">{fmtDate(t.paymentDate as any)}</div>
                        <div className="text-slate-400 text-[10px]">{fmtTime(t.paymentDate as any)}</div>
                      </td>
                      <td className="px-4 py-3">
                        {dir === 'IN' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            <ArrowDownLeft size={10} /> IN
                          </span>
                        )}
                        {dir === 'OUT' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-50 text-rose-700 border border-rose-200">
                            <ArrowUpRight size={10} /> OUT
                          </span>
                        )}
                        {dir === 'NEUTRAL' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-500 border border-slate-200">
                            <Minus size={10} /> —
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded text-[10px] font-bold border ${TYPE_COLORS[dir]}`}>
                          {typeLabel}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-indigo-600 font-medium">
                        {t.referenceCode ?? `#${t.referenceId}`}
                      </td>
                      <td className="px-4 py-3 text-slate-700 font-medium">
                        {t.entityName ?? <span className="text-slate-400 italic">-</span>}
                      </td>
                      <td className="px-4 py-3 text-slate-500">{t.paymentMethodName ?? '-'}</td>
                      <td className="px-4 py-3 text-slate-400 font-mono text-[11px]">{t.transactionNo ?? '-'}</td>
                      <td className={`px-4 py-3 text-right font-bold text-sm tabular-nums ${dir === 'IN' ? 'text-emerald-700' : dir === 'OUT' ? 'text-rose-700' : 'text-slate-700'}`}>
                        {dir === 'IN' ? '+' : dir === 'OUT' ? '-' : ''}{money(t.amount)}
                      </td>
                    </tr>
                  );
                }) : (
                  <tr>
                    <td colSpan={8} className="px-4 py-12 text-center text-slate-400 italic text-sm">
                      No transactions match the current filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
};

export default PaymentTransactionManagement;
