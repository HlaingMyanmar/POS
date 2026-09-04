import React, { useEffect, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import {
  AlertTriangle,
  BookOpen,
  CalendarRange,
  CheckCircle2,
  ChevronDown,
  Filter,
  RefreshCw,
  Search,
  X
} from 'lucide-react';
import Swal from 'sweetalert2';
import { accountingApiService } from '../services/accountingapiservice';
import { coaService } from '../services/coaapiservice';
import { journalApiService } from '../services/journalapiservice';
import { staffService } from '../services/staffapiservice';
import { AccountBalanceDTO, ChartOfAccountDTO, JournalEntryDTO, StaffDTO } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type DateShortcut = 'TODAY' | 'WEEK' | 'MONTH' | 'ALL';
type BalanceFilter = 'ALL' | 'BALANCED' | 'CHECK';
type SourceFilter = 'ALL' | 'SALE' | 'PURCHASE' | 'RETURN' | 'EXPENSE' | 'INCOME' | 'STOCK' | 'OPENING' | 'MANUAL';

type LedgerRow = {
  key: string;
  entryDate?: string;
  referenceNo: string;
  description: string;
  debit: number;
  credit: number;
  runningBalance: number;
  status?: string;
};

const money = (value: number, empty = '0 Ks') => {
  if (!value) return empty;
  return `${new Intl.NumberFormat('en-US').format(value)} Ks`;
};

const moneySigned = (value: number) => {
  const abs = Math.abs(value);
  const formatted = new Intl.NumberFormat('en-US').format(abs);
  if (value < 0) return `(${formatted}) Ks`;
  return `${formatted} Ks`;
};

const dateInput = (date: Date) => date.toISOString().slice(0, 10);

const getShortcutRange = (shortcut: DateShortcut) => {
  const today = new Date();
  const start = new Date(today);

  if (shortcut === 'TODAY') return { from: dateInput(today), to: dateInput(today) };
  if (shortcut === 'WEEK') {
    const day = today.getDay() || 7;
    start.setDate(today.getDate() - day + 1);
    return { from: dateInput(start), to: dateInput(today) };
  }
  if (shortcut === 'MONTH') {
    start.setDate(1);
    return { from: dateInput(start), to: dateInput(today) };
  }
  return { from: '', to: '' };
};

const fmtDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
};

const sourceLabels: Record<SourceFilter, string> = {
  ALL: 'အားလုံး',
  SALE: 'အရောင်း',
  PURCHASE: 'ဝယ်ယူမှု',
  RETURN: 'ပြန်ပို့/ပြန်လက်ခံ',
  EXPENSE: 'အသုံးစရိတ်',
  INCOME: 'ဝင်ငွေ',
  STOCK: 'Stock',
  OPENING: 'Opening',
  MANUAL: 'Manual'
};

const detectSource = (entry: JournalEntryDTO): SourceFilter => {
  const text = [entry.referenceNo, entry.description].filter(Boolean).join(' ').toUpperCase();
  if (text.includes('SALE RETURN') || text.includes('PURCHASE RETURN') || text.startsWith('SR') || text.startsWith('PR')) return 'RETURN';
  if (text.includes('SALE') || text.startsWith('SAL')) return 'SALE';
  if (text.includes('PURCHASE') || text.startsWith('PUR')) return 'PURCHASE';
  if (text.includes('EXPENSE') || text.startsWith('EXP')) return 'EXPENSE';
  if (text.includes('INCOME') || text.startsWith('INC')) return 'INCOME';
  if (text.includes('STOCK') || text.startsWith('ADJ')) return 'STOCK';
  if (text.includes('OPENING') || text.startsWith('OB')) return 'OPENING';
  return 'MANUAL';
};

const isDebitNormal = (accountType?: string) => {
  const t = String(accountType || '').toLowerCase();
  return t === 'asset' || t === 'expense';
};

const flattenCoa = (nodes: ChartOfAccountDTO[]): ChartOfAccountDTO[] => {
  const out: ChartOfAccountDTO[] = [];
  const walk = (list: ChartOfAccountDTO[]) => {
    list.forEach((node) => {
      out.push(node);
      if (node.children?.length) walk(node.children);
    });
  };
  walk(nodes || []);
  return out;
};

const JournalEntryManagement: React.FC = () => {
  const monthRange = useMemo(() => getShortcutRange('MONTH'), []);
  const [entries, setEntries] = useState<JournalEntryDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [balances, setBalances] = useState<AccountBalanceDTO[]>([]);
  const [accounts, setAccounts] = useState<ChartOfAccountDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [staffFilter, setStaffFilter] = useState('ALL');
  const [sourceFilter, setSourceFilter] = useState<SourceFilter>('ALL');
  const [balanceFilter, setBalanceFilter] = useState<BalanceFilter>('ALL');
  const [dateShortcut, setDateShortcut] = useState<DateShortcut>('MONTH');
  const [dateFrom, setDateFrom] = useState(monthRange.from);
  const [dateTo, setDateTo] = useState(monthRange.to);
  const [ledgerAccountId, setLedgerAccountId] = useState<number | null>(null);

  const loadData = async () => {
    setLoading(true);
    try {
      const [entriesRes, staffRes, balancesRes, coaRes] = await Promise.all([
        journalApiService.getAll(),
        staffService.getAll(),
        accountingApiService.getAllBalances(),
        coaService.getAll()
      ]);
      setEntries(entriesRes || []);
      setStaffs(staffRes || []);
      setBalances(balancesRes || []);
      setAccounts(flattenCoa(coaRes || []));
    } catch (error) {
      console.error('Error fetching journal data:', error);
      Swal.fire('Error', 'ဂျာနယ်မှတ်တမ်း ဖတ်၍မရပါ။', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);
  useRefreshOnTabActivate(loadData);
  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Journal', 'StockAdj', 'Return', 'Accounting'], loadData);

  const getStaffName = (id?: number) => staffs.find((staff) => staff.id === id)?.name || '-';

  const getTotals = (entry: JournalEntryDTO) => {
    const details = entry.details || [];
    return {
      debit: details.reduce((sum, detail) => sum + (Number(detail.debit) || 0), 0),
      credit: details.reduce((sum, detail) => sum + (Number(detail.credit) || 0), 0)
    };
  };

  const latestBalanceByAccountId = useMemo(() => {
    const map = new Map<number, AccountBalanceDTO>();
    balances.forEach((row) => {
      const current = map.get(row.accountId);
      const currentTime = new Date(current?.lastUpdated || 0).getTime();
      const nextTime = new Date(row.lastUpdated || 0).getTime();
      if (!current || Number.isNaN(currentTime) || nextTime >= currentTime) map.set(row.accountId, row);
    });
    return map;
  }, [balances]);

  const accountById = useMemo(() => {
    const map = new Map<number, ChartOfAccountDTO>();
    accounts.forEach((a) => {
      if (a.id != null) map.set(a.id, a);
    });
    return map;
  }, [accounts]);

  const applyShortcut = (shortcut: DateShortcut) => {
    const range = getShortcutRange(shortcut);
    setDateShortcut(shortcut);
    setDateFrom(range.from);
    setDateTo(range.to);
  };

  const filteredEntries = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    const from = dateFrom ? new Date(dateFrom) : null;
    const to = dateTo ? new Date(dateTo) : null;
    if (to) to.setHours(23, 59, 59, 999);

    return entries.filter((entry) => {
      const staffName = entry.staffName || getStaffName(entry.staffId);
      const totals = getTotals(entry);
      const balanced = Math.abs(totals.debit - totals.credit) < 0.01;
      const source = detectSource(entry);
      const text = [
        entry.referenceNo,
        entry.description,
        staffName,
        entry.id ? `#${entry.id}` : '',
        ...(entry.details || []).map((detail) => detail.accountName || '')
      ].join(' ').toLowerCase();

      if (keyword && !text.includes(keyword)) return false;
      if (staffFilter !== 'ALL' && String(entry.staffId || '') !== staffFilter) return false;
      if (sourceFilter !== 'ALL' && source !== sourceFilter) return false;
      if (balanceFilter === 'BALANCED' && !balanced) return false;
      if (balanceFilter === 'CHECK' && balanced) return false;
      if (!from && !to) return true;
      if (!entry.entryDate) return false;
      const date = new Date(entry.entryDate);
      if (Number.isNaN(date.getTime())) return false;
      if (from && date < from) return false;
      if (to && date > to) return false;
      return true;
    });
  }, [balanceFilter, dateFrom, dateTo, entries, search, sourceFilter, staffFilter, staffs]);

  const summary = useMemo(() => filteredEntries.reduce(
    (acc, entry) => {
      const totals = getTotals(entry);
      const balanced = Math.abs(totals.debit - totals.credit) < 0.01;
      acc.count += 1;
      acc.totalDebit += totals.debit;
      acc.totalCredit += totals.credit;
      if (balanced) acc.balanced += 1;
      else acc.check += 1;
      return acc;
    },
    { count: 0, totalDebit: 0, totalCredit: 0, balanced: 0, check: 0 }
  ), [filteredEntries]);

  const ledgerAccount = ledgerAccountId != null ? accountById.get(ledgerAccountId) : undefined;
  const ledgerBalance = ledgerAccountId != null ? latestBalanceByAccountId.get(ledgerAccountId) : undefined;
  const ledgerAccountName = ledgerAccount?.accountName
    || ledgerBalance?.accountName
    || (ledgerAccountId != null ? `Account #${ledgerAccountId}` : '');

  const ledgerView = useMemo(() => {
    if (ledgerAccountId == null) return null;
    const accountType = String(ledgerAccount?.accountType || '');
    const debitNormal = isDebitNormal(accountType);
    const opening = Number(ledgerBalance?.openingBalance) || 0;

    const lines: Array<{
      key: string;
      entryDate?: string;
      referenceNo: string;
      description: string;
      debit: number;
      credit: number;
      status?: string;
      sortTs: number;
    }> = [];

    entries.forEach((entry) => {
      (entry.details || []).forEach((detail, idx) => {
        if (detail.accountId !== ledgerAccountId) return;
        const debit = Number(detail.debit) || 0;
        const credit = Number(detail.credit) || 0;
        if (debit === 0 && credit === 0) return;
        const ts = entry.entryDate ? new Date(entry.entryDate).getTime() : 0;
        lines.push({
          key: `${entry.id || entry.referenceNo}-${idx}`,
          entryDate: entry.entryDate,
          referenceNo: entry.referenceNo || `#${entry.id}`,
          description: entry.description || '-',
          debit,
          credit,
          status: entry.status,
          sortTs: Number.isNaN(ts) ? 0 : ts
        });
      });
    });

    lines.sort((a, b) => a.sortTs - b.sortTs || a.referenceNo.localeCompare(b.referenceNo));

    let running = opening;
    const rows: LedgerRow[] = lines.map((line) => {
      const delta = debitNormal ? line.debit - line.credit : line.credit - line.debit;
      running += delta;
      return {
        key: line.key,
        entryDate: line.entryDate,
        referenceNo: line.referenceNo,
        description: line.description,
        debit: line.debit,
        credit: line.credit,
        runningBalance: running,
        status: line.status
      };
    });

    return {
      opening,
      closing: running,
      debitNormal,
      accountType,
      rows,
      totalDebit: rows.reduce((s, r) => s + r.debit, 0),
      totalCredit: rows.reduce((s, r) => s + r.credit, 0)
    };
  }, [entries, ledgerAccount, ledgerAccountId, ledgerBalance]);

  return (
    <div className="w-full max-w-none space-y-4">
      <div className="flex flex-col xl:flex-row xl:items-start xl:justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-bold">
            <BookOpen size={14} /> General Ledger
          </div>
          <h2 className="text-2xl font-extrabold text-slate-900 mt-3">ဂျာနယ်မှတ်တမ်း</h2>
          <p className="text-sm text-slate-500 mt-1">
            Account ကို နှိပ်ပြီး Ledger (Opening → Transactions → Running → Closing) ကြည့်နိုင်သည်။
          </p>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <SummaryCard label="Entry" value={summary.count.toString()} color="slate" />
          <SummaryCard label="Debit" value={money(summary.totalDebit)} color="emerald" />
          <SummaryCard label="Credit" value={money(summary.totalCredit)} color="rose" />
          <SummaryCard label="စစ်ရန်" value={summary.check.toString()} color={summary.check ? 'amber' : 'emerald'} />
        </div>
      </div>

      <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50 flex flex-col 2xl:flex-row 2xl:items-center gap-3">
          <div className="relative flex-1 min-w-[260px]">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Ref No / အကြောင်းအရာ / Staff / Account ရှာပါ..."
              className="w-full pl-9 pr-3 py-2.5 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-slate-500/20 focus:border-slate-500"
            />
          </div>

          <div className="relative min-w-[180px]">
            <Filter size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <select
              value={staffFilter}
              onChange={(e) => setStaffFilter(e.target.value)}
              className="w-full pl-9 pr-3 py-2.5 bg-white border border-slate-200 rounded-lg text-sm text-slate-600 focus:outline-none"
            >
              <option value="ALL">Staff အားလုံး</option>
              {staffs.map((staff) => (
                <option key={staff.id} value={String(staff.id)}>{staff.name}</option>
              ))}
            </select>
          </div>

          <div className="inline-flex items-center gap-2 px-3 py-2.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600">
            <CalendarRange size={14} className="text-slate-400" />
            <input type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setDateShortcut('ALL'); }} className="bg-transparent outline-none" />
            <span className="text-slate-400">-</span>
            <input type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setDateShortcut('ALL'); }} className="bg-transparent outline-none" />
          </div>

          <button
            onClick={() => void loadData()}
            className="inline-flex items-center justify-center gap-2 px-3 py-2.5 bg-slate-900 text-white rounded-lg text-xs font-bold hover:bg-slate-800"
          >
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
        </div>

        <div className="px-4 py-3 border-b border-slate-100 flex flex-wrap gap-2">
          {([
            ['TODAY', 'Today'],
            ['WEEK', 'This Week'],
            ['MONTH', 'This Month'],
            ['ALL', 'All']
          ] as [DateShortcut, string][]).map(([value, label]) => (
            <button key={value} onClick={() => applyShortcut(value)} className={`px-3 py-1.5 rounded-full text-xs font-bold border ${dateShortcut === value ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-600 border-slate-200'}`}>
              {label}
            </button>
          ))}
        </div>

        <div className="px-4 py-3 border-b border-slate-100 flex flex-wrap gap-2">
          {(Object.keys(sourceLabels) as SourceFilter[]).map((source) => (
            <button key={source} onClick={() => setSourceFilter(source)} className={`px-3 py-1.5 rounded-full text-xs font-bold border ${sourceFilter === source ? 'bg-slate-800 text-white border-slate-800' : 'bg-white text-slate-600 border-slate-200'}`}>
              {sourceLabels[source]}
            </button>
          ))}
          <button onClick={() => setBalanceFilter('ALL')} className={`px-3 py-1.5 rounded-full text-xs font-bold border ${balanceFilter === 'ALL' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200'}`}>Status အားလုံး</button>
          <button onClick={() => setBalanceFilter('BALANCED')} className={`px-3 py-1.5 rounded-full text-xs font-bold border ${balanceFilter === 'BALANCED' ? 'bg-emerald-600 text-white border-emerald-600' : 'bg-white text-slate-600 border-slate-200'}`}>Balanced</button>
          <button onClick={() => setBalanceFilter('CHECK')} className={`px-3 py-1.5 rounded-full text-xs font-bold border ${balanceFilter === 'CHECK' ? 'bg-amber-600 text-white border-amber-600' : 'bg-white text-slate-600 border-slate-200'}`}>စစ်ရန် {summary.check}</button>
        </div>

        <div className="overflow-auto">
          <table className="min-w-full text-sm">
            <thead className="sticky top-0 bg-white z-10 shadow-sm">
              <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                <th className="px-4 py-3 text-left">Ref No</th>
                <th className="px-4 py-3 text-left">Date / Source</th>
                <th className="px-4 py-3 text-left">Description</th>
                <th className="px-4 py-3 text-left">Staff</th>
                <th className="px-4 py-3 text-right">Debit</th>
                <th className="px-4 py-3 text-right">Credit</th>
                <th className="px-4 py-3 text-center">Status</th>
                <th className="px-4 py-3 text-center">Lines</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-500">ဂျာနယ်မှတ်တမ်းများ ဖတ်နေသည်...</td></tr>
              ) : filteredEntries.length === 0 ? (
                <tr><td colSpan={8} className="px-4 py-12 text-center text-slate-500">ကိုက်ညီသော ဂျာနယ်မှတ်တမ်း မရှိပါ။</td></tr>
              ) : filteredEntries.map((entry, index) => {
                const totals = getTotals(entry);
                const balanced = Math.abs(totals.debit - totals.credit) < 0.01;
                const isOpen = expandedId === entry.id;
                const source = detectSource(entry);
                const journalStatus = (entry.status || 'POSTED').toUpperCase();
                const isReversed = journalStatus === 'REVERSED';
                const isReversalEntry = Boolean(entry.reversalOfId);

                return (
                  <React.Fragment key={entry.id || `${entry.referenceNo}-${index}`}>
                    <tr className="hover:bg-slate-50 text-xs border-b border-slate-100">
                      <td className="px-4 py-3 align-top">
                        <div className="font-extrabold text-slate-900">{entry.referenceNo || `#${entry.id}`}</div>
                        <div className="text-[11px] text-slate-400 mt-1">ID #{entry.id || '-'}</div>
                        {entry.reversalOfId && <div className="text-[10px] text-violet-600 mt-1">↩ Reversal of #{entry.reversalOfId}</div>}
                      </td>
                      <td className="px-4 py-3 align-top">
                        <div className="text-slate-700">{fmtDateTime(entry.entryDate)}</div>
                        <span className="inline-flex mt-1 px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 text-[10px] font-bold">{sourceLabels[source]}</span>
                      </td>
                      <td className="px-4 py-3 align-top text-slate-600 max-w-[460px]">
                        <div className="line-clamp-2">{entry.description || '-'}</div>
                      </td>
                      <td className="px-4 py-3 align-top text-slate-600">{entry.staffName || getStaffName(entry.staffId)}</td>
                      <td className="px-4 py-3 align-top text-right font-mono font-bold text-emerald-700">{money(totals.debit)}</td>
                      <td className="px-4 py-3 align-top text-right font-mono font-bold text-rose-700">{money(totals.credit)}</td>
                      <td className="px-4 py-3 align-top text-center">
                        <div className="flex flex-col items-center gap-1">
                          <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-bold ${isReversed ? 'bg-slate-200 text-slate-700' : isReversalEntry ? 'bg-violet-50 text-violet-700' : 'bg-emerald-50 text-emerald-700'}`}>
                            {isReversed ? 'REVERSED' : isReversalEntry ? 'REVERSAL' : journalStatus}
                          </span>
                          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold ${balanced ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                            {balanced ? <CheckCircle2 size={10} /> : <AlertTriangle size={10} />} {balanced ? 'Balanced' : 'စစ်ရန်'}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3 align-top text-center">
                        <button type="button" onClick={() => setExpandedId(isOpen ? null : entry.id || null)} className="inline-flex items-center justify-center w-8 h-8 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50">
                          <ChevronDown size={15} className={`transition-transform ${isOpen ? 'rotate-180' : ''}`} />
                        </button>
                      </td>
                    </tr>

                    {isOpen && (
                      <tr className="bg-slate-50/70">
                        <td colSpan={8} className="px-4 py-4">
                          <div className="rounded-lg border border-slate-200 bg-white overflow-hidden">
                            <div className="px-4 py-3 border-b border-slate-100 flex flex-col md:flex-row md:items-center md:justify-between gap-2">
                              <div>
                                <h4 className="font-extrabold text-slate-800 text-sm">Double-entry lines</h4>
                                <p className="text-xs text-slate-500 mt-1">{entry.referenceNo} • {sourceLabels[source]} • {journalStatus}</p>
                                {(entry.reversedBy || entry.reversalReason) && (
                                  <p className="text-[11px] text-slate-500 mt-1">
                                    {entry.reversedBy && <>Reversed by {entry.reversedBy}</>}
                                    {entry.reversedAt && <> · {fmtDateTime(entry.reversedAt)}</>}
                                    {entry.reversalReason && <> · {entry.reversalReason}</>}
                                  </p>
                                )}
                              </div>
                              <div className={`text-xs font-bold ${balanced ? 'text-emerald-700' : 'text-amber-700'}`}>
                                Difference: {money(Math.abs(totals.debit - totals.credit))}
                              </div>
                            </div>

                            <div className="overflow-auto">
                              <table className="min-w-full text-xs">
                                <thead>
                                  <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                                    <th className="px-4 py-3 text-left">Account</th>
                                    <th className="px-4 py-3 text-right">Debit</th>
                                    <th className="px-4 py-3 text-right">Credit</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {(entry.details || []).map((detail, detailIndex) => (
                                    <tr key={detailIndex} className="border-t border-slate-100">
                                      <td className="px-4 py-3">
                                        <button
                                          type="button"
                                          onClick={() => detail.accountId && setLedgerAccountId(detail.accountId)}
                                          className="text-left group"
                                          title="Account Ledger ကြည့်ရန်"
                                        >
                                          <div className="font-bold text-indigo-700 group-hover:underline">
                                            {detail.accountName || `Account #${detail.accountId}`}
                                          </div>
                                          <div className="text-[11px] text-slate-400">Account ID: {detail.accountId || '-'} · Ledger ဖွင့်ရန် နှိပ်ပါ</div>
                                        </button>
                                      </td>
                                      <td className="px-4 py-3 text-right font-mono text-emerald-700">{money(Number(detail.debit) || 0, '-')}</td>
                                      <td className="px-4 py-3 text-right font-mono text-rose-700">{money(Number(detail.credit) || 0, '-')}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {ledgerAccountId != null && ledgerView && (
        <div className="fixed inset-0 z-50 flex items-stretch justify-end bg-slate-900/40">
          <button type="button" className="flex-1 cursor-default" aria-label="Close ledger" onClick={() => setLedgerAccountId(null)} />
          <div className="w-full max-w-3xl h-full bg-white shadow-2xl border-l border-slate-200 flex flex-col">
            <div className="px-5 py-4 border-b border-slate-100 flex items-start justify-between gap-3">
              <div>
                <div className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Account Ledger</div>
                <h3 className="text-lg font-extrabold text-slate-900 mt-1">{ledgerAccountName}</h3>
                <p className="text-xs text-slate-500 mt-1">
                  {ledgerView.accountType || 'Account'} · {ledgerView.debitNormal ? 'Debit-normal' : 'Credit-normal'}
                  {ledgerBalance?.fiscalYear ? ` · FY ${ledgerBalance.fiscalYear}` : ''}
                </p>
              </div>
              <button type="button" onClick={() => setLedgerAccountId(null)} className="p-2 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50">
                <X size={16} />
              </button>
            </div>

            <div className="px-5 py-4 grid grid-cols-2 sm:grid-cols-4 gap-3 border-b border-slate-100 bg-slate-50/60">
              <SummaryCard label="Opening Balance" value={moneySigned(ledgerView.opening)} color="slate" />
              <SummaryCard label="Period Debit" value={money(ledgerView.totalDebit)} color="emerald" />
              <SummaryCard label="Period Credit" value={money(ledgerView.totalCredit)} color="rose" />
              <SummaryCard label="Closing Balance" value={moneySigned(ledgerView.closing)} color={ledgerView.closing >= 0 ? 'emerald' : 'amber'} />
            </div>

            <div className="flex-1 overflow-auto">
              <table className="min-w-full text-xs">
                <thead className="sticky top-0 bg-white z-10 shadow-sm">
                  <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                    <th className="px-4 py-3 text-left">Date</th>
                    <th className="px-4 py-3 text-left">Ref / Description</th>
                    <th className="px-4 py-3 text-right">Debit</th>
                    <th className="px-4 py-3 text-right">Credit</th>
                    <th className="px-4 py-3 text-right">Running Balance</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="border-b border-slate-100 bg-indigo-50/40">
                    <td className="px-4 py-3 text-slate-500" colSpan={4}>
                      <span className="font-bold text-indigo-700">Opening Balance</span>
                    </td>
                    <td className="px-4 py-3 text-right font-mono font-bold text-indigo-800">{moneySigned(ledgerView.opening)}</td>
                  </tr>
                  {ledgerView.rows.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-10 text-center text-slate-400">ဤ account အတွက် journal transaction မရှိသေးပါ။</td>
                    </tr>
                  ) : ledgerView.rows.map((row) => (
                    <tr key={row.key} className="border-b border-slate-100 hover:bg-slate-50">
                      <td className="px-4 py-3 text-slate-600 whitespace-nowrap">{fmtDateTime(row.entryDate)}</td>
                      <td className="px-4 py-3">
                        <div className="font-bold text-slate-800">{row.referenceNo}</div>
                        <div className="text-slate-500 mt-0.5 line-clamp-2">{row.description}</div>
                        {row.status && row.status.toUpperCase() !== 'POSTED' && (
                          <div className="text-[10px] font-bold text-slate-400 mt-1 uppercase">{row.status}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-emerald-700">{money(row.debit, '-')}</td>
                      <td className="px-4 py-3 text-right font-mono text-rose-700">{money(row.credit, '-')}</td>
                      <td className={`px-4 py-3 text-right font-mono font-bold ${row.runningBalance >= 0 ? 'text-slate-800' : 'text-rose-700'}`}>
                        {moneySigned(row.runningBalance)}
                      </td>
                    </tr>
                  ))}
                  <tr className="bg-emerald-50/50">
                    <td className="px-4 py-3 font-bold text-emerald-800" colSpan={4}>Closing Balance</td>
                    <td className="px-4 py-3 text-right font-mono font-extrabold text-emerald-900">{moneySigned(ledgerView.closing)}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const SummaryCard: React.FC<{ label: string; value: string; color: 'slate' | 'emerald' | 'rose' | 'amber' }> = ({ label, value, color }) => {
  const styles = {
    slate: 'border-slate-200 text-slate-900 bg-white',
    emerald: 'border-emerald-100 text-emerald-700 bg-emerald-50/50',
    rose: 'border-rose-100 text-rose-700 bg-rose-50/50',
    amber: 'border-amber-100 text-amber-700 bg-amber-50/50'
  };
  return (
    <div className={`rounded-lg border px-4 py-3 min-w-[150px] ${styles[color]}`}>
      <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">{label}</p>
      <p className="text-xl font-extrabold mt-1">{value}</p>
    </div>
  );
};

export default JournalEntryManagement;
