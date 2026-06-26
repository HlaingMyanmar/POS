import React, { useCallback, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import {
  ArrowDownUp,
  ArrowLeft,
  ChevronDown,
  ChevronRight,
  Download,
  Loader2,
  RefreshCw,
  Save,
  Search
} from 'lucide-react';
import Swal from 'sweetalert2';
import { coaService } from '../services/coaapiservice';
import { expenseApiService } from '../services/expenseapiservice';
import { incomeApiService } from '../services/incomeapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { staffService } from '../services/staffapiservice';
import {
  AccountType,
  ChartOfAccountDTO,
  ExpenseDTO,
  IncomeDTO,
  PaymentMethodDTO,
  StaffDTO
} from '../types';

type EntryTab = 'EXPENSE' | 'INCOME';
type SortField = 'date' | 'code' | 'type' | 'account' | 'amount' | 'payment' | 'staff';
type SortDir = 'asc' | 'desc';

type UnifiedTx = {
  key: string;
  type: EntryTab;
  code: string;
  date?: string;
  accountId: number;
  accountName?: string;
  paymentMethodId: number;
  paymentMethodName?: string;
  paymentAccountName?: string;
  amount: number;
  description?: string;
  staffId: number;
  staffName?: string;
};

type JournalLine = {
  side: 'DR' | 'CR';
  account: string;
  amount: number;
};

const toLocalDateTime = (value?: string) => {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const nowLocalDateTime = () => toLocalDateTime(new Date().toISOString());
const money = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

const INCOME_SYSTEM_CODES = new Set(['INC-002', 'INC-006', 'INC-007', 'INC-008']);
const AUTO_EXPENSE_KEYWORDS = ['cogs', 'cost of goods', 'purchase', 'purchases', 'inventory', 'stock'];
const AUTO_INCOME_KEYWORDS = ['product sale', 'inventory gain', 'purchase return', 'inventory over'];
const MANUAL_PAYMENT_METHODS = ['cash', 'bank', 'kpay', 'wavepay'];
// Asset accounts that are cash/payment accounts — exclude from expense form
const ASSET_PAYMENT_CODES = new Set(['ASS-002','ASS-003','ASS-004','ASS-005','ASS-006','ASS-007']);

const includesAny = (text: string, tokens: string[]) => tokens.some((token) => text.includes(token));

const isManualExpenseAccount = (account: ChartOfAccountDTO) => {
  // Fixed Asset accounts (DR Asset / CR Cash) — allowed
  if (account.accountType === AccountType.Asset) {
    return !ASSET_PAYMENT_CODES.has(account.code ?? '');
  }
  if (account.accountType !== AccountType.Expense) return false;
  const text = `${account.accountName || ''} ${account.code || ''}`.toLowerCase();
  return !includesAny(text, AUTO_EXPENSE_KEYWORDS);
};

const isManualIncomeAccount = (account: ChartOfAccountDTO) => {
  if (account.accountType !== AccountType.Income) return false;
  if (INCOME_SYSTEM_CODES.has((account.code || '').toUpperCase())) return false;
  const text = `${account.accountName || ''} ${account.code || ''}`.toLowerCase();
  return !includesAny(text, AUTO_INCOME_KEYWORDS);
};

const toCsvCell = (value: unknown) => {
  const text = String(value ?? '');
  if (text.includes('"') || text.includes(',') || text.includes('\n')) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

const ExpenseIncomeManagement: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [activeView, setActiveView] = useState<'list' | EntryTab>('list');

  const [expenses, setExpenses] = useState<ExpenseDTO[]>([]);
  const [incomes, setIncomes] = useState<IncomeDTO[]>([]);
  const [accounts, setAccounts] = useState<ChartOfAccountDTO[]>([]);
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);

  const [tab, setTab] = useState<EntryTab>('EXPENSE');
  const [entryDate, setEntryDate] = useState(nowLocalDateTime());
  const [accountId, setAccountId] = useState(0);
  const [accountSearch, setAccountSearch] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState(0);
  const [amountInput, setAmountInput] = useState('');
  const [description, setDescription] = useState('');
  const [staffId, setStaffId] = useState(0);

  const [search, setSearch] = useState('');
  const [filterDateFrom, setFilterDateFrom] = useState('');
  const [filterDateTo, setFilterDateTo] = useState('');
  const [filterAccountId, setFilterAccountId] = useState(0);
  const [filterType, setFilterType] = useState<'ALL' | EntryTab>('ALL');
  const [sortField, setSortField] = useState<SortField>('date');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const accountLabel = useCallback((a?: ChartOfAccountDTO) => {
    if (!a) return '';
    return `${a.accountName} (${a.code})`;
  }, []);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [expenseRows, incomeRows, accountRows, methodRows, staffRows] = await Promise.all([
        expenseApiService.getAll(),
        incomeApiService.getAll(),
        coaService.getAll(),
        paymentMethodService.getAllActive(),
        staffService.getAll()
      ]);

      setExpenses(expenseRows || []);
      setIncomes(incomeRows || []);
      setAccounts(accountRows || []);
      setMethods(methodRows || []);
      setStaffs((staffRows || []).filter((s) => s.active !== false));

      const paymentOptions = (methodRows || []).filter((m) => {
        const text = (m.methodName || '').toLowerCase();
        return includesAny(text, MANUAL_PAYMENT_METHODS);
      });
      const preferredMethods = paymentOptions.length > 0 ? paymentOptions : (methodRows || []);

      if (preferredMethods.length > 0) {
        setPaymentMethodId((prev) => prev || preferredMethods[0].id);
      }
      if ((staffRows || []).length > 0) {
        setStaffId((prev) => prev || staffRows[0].id);
      }
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load expense/income data', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void loadAll();
  }, [loadAll]);
  useDataEvents(['Expense', 'Income'], loadAll);

  const expenseAccounts = useMemo(
    () => accounts.filter(isManualExpenseAccount),
    [accounts]
  );

  const incomeAccounts = useMemo(
    () => accounts.filter(isManualIncomeAccount),
    [accounts]
  );

  const allManualAccounts = useMemo(() => {
    const map = new Map<number, ChartOfAccountDTO>();
    [...expenseAccounts, ...incomeAccounts].forEach((a) => map.set(a.id, a));
    return Array.from(map.values());
  }, [expenseAccounts, incomeAccounts]);

  const accountOptions = tab === 'EXPENSE' ? expenseAccounts : incomeAccounts;
  const accountById = useMemo(() => new Map(accounts.map((a) => [a.id, a])), [accounts]);
  const methodById = useMemo(() => new Map(methods.map((m) => [m.id, m])), [methods]);

  const paymentMethodOptions = useMemo(() => {
    const options = methods.filter((m) => {
      const text = (m.methodName || '').toLowerCase();
      return includesAny(text, MANUAL_PAYMENT_METHODS);
    });
    return options.length > 0 ? options : methods;
  }, [methods]);

  const onAccountSearch = (value: string) => {
    setAccountSearch(value);
    const needle = value.trim().toLowerCase();
    if (!needle) {
      setAccountId(0);
      return;
    }

    const exact = accountOptions.find((a) => {
      const label = accountLabel(a).toLowerCase();
      return label === needle
        || (a.accountName || '').toLowerCase() === needle
        || (a.code || '').toLowerCase() === needle;
    });
    setAccountId(exact?.id || 0);
  };

  const selectedAccount = accountById.get(accountId);
  const selectedMethod = methodById.get(paymentMethodId);
  const amount = useMemo(() => {
    if (!amountInput.trim()) return 0;
    const n = Number(amountInput);
    return Number.isNaN(n) ? 0 : n;
  }, [amountInput]);

  const isAssetPurchase = tab === 'EXPENSE' && selectedAccount?.accountType === AccountType.Asset;

  const liveJournal = useMemo<JournalLine[]>(() => {
    const drAccountExpense = selectedAccount?.accountName || (isAssetPurchase ? '[select asset account]' : '[select expense account]');
    const crAccountExpense = selectedMethod?.accountName || selectedMethod?.methodName || '[select payment account]';
    const drAccountIncome = selectedMethod?.accountName || selectedMethod?.methodName || '[select payment account]';
    const crAccountIncome = selectedAccount?.accountName || '[select income account]';

    if (tab === 'EXPENSE') {
      return [
        { side: 'DR', account: drAccountExpense, amount },
        { side: 'CR', account: crAccountExpense, amount }
      ];
    }

    return [
      { side: 'DR', account: drAccountIncome, amount },
      { side: 'CR', account: crAccountIncome, amount }
    ];
  }, [amount, selectedAccount?.accountName, selectedAccount?.accountType, selectedMethod?.accountName, selectedMethod?.methodName, tab, isAssetPurchase]);

  const validForm = accountId > 0
    && paymentMethodId > 0
    && staffId > 0
    && amount > 0
    && Boolean(selectedAccount)
    && Boolean(selectedMethod);

  const clearForm = () => {
    setEntryDate(nowLocalDateTime());
    setAccountId(0);
    setAccountSearch('');
    setAmountInput('');
    setDescription('');
  };

  const switchTab = (next: EntryTab) => {
    setTab(next);
    setAccountId(0);
    setAccountSearch('');
    setActiveView(next);
  };

  const openConfirm = () => {
    if (!validForm) {
      Swal.fire('Validation', 'Please select account/payment/staff and enter a valid amount.', 'warning');
      return;
    }
    setConfirmOpen(true);
  };

  const submit = async () => {
    if (saving || !validForm) return;
    setSaving(true);
    try {
      if (tab === 'EXPENSE') {
        const payload: ExpenseDTO = {
          expenseDate: entryDate || undefined,
          accountId,
          paymentMethodId,
          amount,
          description: description.trim() || undefined,
          staffId
        };
        await expenseApiService.create(payload);
      } else {
        const payload: IncomeDTO = {
          incomeDate: entryDate || undefined,
          accountId,
          paymentMethodId,
          amount,
          description: description.trim() || undefined,
          staffId
        };
        await incomeApiService.create(payload);
      }

      setConfirmOpen(false);
      clearForm();
      setActiveView('list');
      await loadAll();

      Swal.fire({
        icon: 'success',
        title: tab === 'EXPENSE' ? 'Expense saved' : 'Income saved',
        toast: true,
        position: 'top-end',
        timer: 1500,
        showConfirmButton: false
      });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save transaction', 'error');
    } finally {
      setSaving(false);
    }
  };

  const unifiedRows = useMemo<UnifiedTx[]>(() => {
    const expenseRows: UnifiedTx[] = (expenses || []).map((e) => {
      const method = methodById.get(e.paymentMethodId);
      return {
        key: `E-${e.id || e.expenseCode || Math.random()}`,
        type: 'EXPENSE',
        code: e.expenseCode || `EXP#${e.id}`,
        date: e.expenseDate,
        accountId: e.accountId,
        accountName: e.accountName || accountById.get(e.accountId)?.accountName,
        paymentMethodId: e.paymentMethodId,
        paymentMethodName: e.paymentMethodName || method?.methodName,
        paymentAccountName: method?.accountName,
        amount: Number(e.amount) || 0,
        description: e.description,
        staffId: e.staffId,
        staffName: e.staffName || staffs.find((s) => s.id === e.staffId)?.name
      };
    });

    const incomeRows: UnifiedTx[] = (incomes || []).map((i) => {
      const method = methodById.get(i.paymentMethodId);
      return {
        key: `I-${i.id || i.incomeCode || Math.random()}`,
        type: 'INCOME',
        code: i.incomeCode || `INC#${i.id}`,
        date: i.incomeDate,
        accountId: i.accountId,
        accountName: i.accountName || accountById.get(i.accountId)?.accountName,
        paymentMethodId: i.paymentMethodId,
        paymentMethodName: i.paymentMethodName || method?.methodName,
        paymentAccountName: method?.accountName,
        amount: Number(i.amount) || 0,
        description: i.description,
        staffId: i.staffId,
        staffName: i.staffName || staffs.find((s) => s.id === i.staffId)?.name
      };
    });

    return [...expenseRows, ...incomeRows];
  }, [accountById, expenses, incomes, methodById, staffs]);

  const dateFilteredRows = useMemo(() => {
    const from = filterDateFrom ? new Date(filterDateFrom) : null;
    const to = filterDateTo ? new Date(filterDateTo) : null;
    if (to) to.setHours(23, 59, 59, 999);

    return unifiedRows.filter((r) => {
      if (!from && !to) return true;
      const d = new Date(r.date || '');
      if (Number.isNaN(d.getTime())) return false;
      if (from && d < from) return false;
      if (to && d > to) return false;
      return true;
    });
  }, [filterDateFrom, filterDateTo, unifiedRows]);

  const summary = useMemo(() => {
    const expenseTotal = dateFilteredRows
      .filter((r) => r.type === 'EXPENSE')
      .reduce((sum, r) => sum + r.amount, 0);
    const incomeTotal = dateFilteredRows
      .filter((r) => r.type === 'INCOME')
      .reduce((sum, r) => sum + r.amount, 0);
    return {
      expenseTotal,
      incomeTotal,
      net: incomeTotal - expenseTotal
    };
  }, [dateFilteredRows]);

  const filteredRows = useMemo(() => {
    const keyword = search.trim().toLowerCase();

    return dateFilteredRows.filter((r) => {
      if (filterType !== 'ALL' && r.type !== filterType) return false;
      if (filterAccountId > 0 && r.accountId !== filterAccountId) return false;

      const text = [
        r.code,
        r.type,
        r.accountName,
        r.paymentMethodName,
        r.description,
        r.staffName
      ].filter(Boolean).join(' ').toLowerCase();

      return !keyword || text.includes(keyword);
    });
  }, [dateFilteredRows, filterAccountId, filterType, search]);

  const sortedRows = useMemo(() => {
    const getValue = (row: UnifiedTx) => {
      if (sortField === 'date') return new Date(row.date || '').getTime() || 0;
      if (sortField === 'amount') return row.amount;
      if (sortField === 'code') return row.code.toLowerCase();
      if (sortField === 'type') return row.type.toLowerCase();
      if (sortField === 'account') return (row.accountName || '').toLowerCase();
      if (sortField === 'payment') return (row.paymentMethodName || '').toLowerCase();
      return (row.staffName || '').toLowerCase();
    };

    const next = [...filteredRows].sort((a, b) => {
      const av = getValue(a);
      const bv = getValue(b);
      if (typeof av === 'number' && typeof bv === 'number') {
        return sortDir === 'asc' ? av - bv : bv - av;
      }
      const cmp = String(av).localeCompare(String(bv));
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return next;
  }, [filteredRows, sortDir, sortField]);

  const tableTotals = useMemo(() => ({
    expense: sortedRows.filter((r) => r.type === 'EXPENSE').reduce((sum, r) => sum + r.amount, 0),
    income: sortedRows.filter((r) => r.type === 'INCOME').reduce((sum, r) => sum + r.amount, 0)
  }), [sortedRows]);

  const sortBy = (field: SortField) => {
    setSortField((prev) => {
      if (prev === field) {
        setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
        return prev;
      }
      setSortDir('asc');
      return field;
    });
  };

  const toggleExpand = (key: string) => {
    setExpanded((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const rowJournal = (row: UnifiedTx): JournalLine[] => {
    const paymentAccount = row.paymentAccountName || row.paymentMethodName || '-';
    const mainAccount = row.accountName || '-';

    if (row.type === 'EXPENSE') {
      return [
        { side: 'DR', account: mainAccount, amount: row.amount },
        { side: 'CR', account: paymentAccount, amount: row.amount }
      ];
    }
    return [
      { side: 'DR', account: paymentAccount, amount: row.amount },
      { side: 'CR', account: mainAccount, amount: row.amount }
    ];
  };

  const exportCsv = () => {
    if (sortedRows.length === 0) {
      Swal.fire('Export', 'No rows to export.', 'info');
      return;
    }

    const headers = ['Code', 'Date', 'Type', 'Account', 'Amount', 'Payment Method', 'Description', 'Staff'];
    const lines = sortedRows.map((r) => ([
      r.code,
      r.date ? new Date(r.date).toLocaleString() : '',
      r.type,
      r.accountName || '',
      r.amount.toFixed(2),
      r.paymentMethodName || '',
      r.description || '',
      r.staffName || ''
    ]));

    const csv = [headers, ...lines].map((row) => row.map(toCsvCell).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `expense-income-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <Loader2 size={28} className="animate-spin text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="w-full max-w-none space-y-4">

      {activeView !== 'list' ? (
        <>
          {/* Full-page form header */}
          <div className="flex items-center justify-between gap-3">
            <button
              onClick={() => { clearForm(); setActiveView('list'); }}
              className="inline-flex items-center gap-2 px-3 py-1.5 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium"
            >
              <ArrowLeft size={15} /> Back to Transactions
            </button>
            <h2 className="text-base font-bold text-slate-800">
              {tab === 'EXPENSE' ? 'New Expense Entry' : 'New Income Entry'}
            </h2>
            <div className="w-36" />
          </div>

          {/* Form grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Left: fields */}
            <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">Date</label>
                  <input type="datetime-local" value={entryDate} onChange={(e) => setEntryDate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">Amount</label>
                  <input type="number" min="0" step="0.01" value={amountInput} onChange={(e) => setAmountInput(e.target.value)} placeholder="0.00"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">
                    {tab === 'EXPENSE'
                      ? (isAssetPurchase ? 'Asset Account (DR Fixed Asset)' : 'Expense Account')
                      : 'Income Account'}
                  </label>
                  <input
                    list="ei-account-options"
                    value={accountSearch}
                    onChange={(e) => onAccountSearch(e.target.value)}
                    placeholder="Search account..."
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  />
                  <datalist id="ei-account-options">
                    {accountOptions.map((a) => (
                      <option key={a.id} value={accountLabel(a)} />
                    ))}
                  </datalist>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">Payment Method</label>
                  <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value) || 0)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all">
                    <option value={0}>Select payment method</option>
                    {paymentMethodOptions.map((m) => (
                      <option key={m.id} value={m.id}>{m.methodName}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">Staff</label>
                  <select value={staffId} onChange={(e) => setStaffId(Number(e.target.value) || 0)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all">
                    <option value={0}>Select staff</option>
                    {staffs.map((s) => (
                      <option key={s.id} value={s.id}>{s.name} ({s.role})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-500 mb-1.5">Description</label>
                  <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all" />
                </div>
              </div>
              <div className="flex justify-end pt-2">
                <button onClick={clearForm} className="px-3 py-2 text-sm text-slate-500 border border-slate-200 rounded-lg hover:bg-slate-50">
                  Clear
                </button>
              </div>
            </div>

            {/* Right: journal summary + save */}
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 sticky top-4 space-y-4">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">Journal Entry</h3>
              <div className="space-y-2">
                {liveJournal.map((line, idx) => (
                  <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 rounded-lg border border-slate-200">
                    <div>
                      <span className={`text-[10px] font-bold ${line.side === 'DR' ? 'text-emerald-600' : 'text-rose-600'}`}>{line.side}</span>
                      <p className="text-sm text-slate-700 mt-0.5">{line.account}</p>
                    </div>
                    <p className="text-sm font-bold text-slate-800">{money(line.amount)}</p>
                  </div>
                ))}
              </div>

              <div className="border-t border-slate-200 pt-4 space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-500">Amount</span>
                  <span className="font-bold text-slate-800">{money(amount)}</span>
                </div>
                {selectedAccount && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-500">Account</span>
                    <span className="text-slate-700 text-right max-w-[160px] truncate">{selectedAccount.accountName}</span>
                  </div>
                )}
                {selectedMethod && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-500">Payment</span>
                    <span className="text-slate-700">{selectedMethod.methodName}</span>
                  </div>
                )}
              </div>

              <button
                onClick={openConfirm}
                disabled={!validForm}
                className={`w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg text-sm font-semibold transition-colors ${validForm ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}
              >
                <Save size={14} /> Save {tab === 'EXPENSE' ? 'Expense' : 'Income'}
              </button>
            </div>
          </div>
        </>
      ) : (
        <>
          {/* List view header + summary */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
              <div>
                <h2 className="text-base font-semibold text-gray-800">Expense & Income</h2>
                <p className="text-xs text-gray-400 mt-0.5">Manual cash/bank entries</p>
              </div>
              <div className="flex items-center gap-2">
                <button onClick={() => switchTab('EXPENSE')} className="px-3 py-1.5 rounded border text-xs font-semibold bg-white text-rose-600 border-rose-300 hover:bg-rose-50 transition-colors">
                  + New Expense
                </button>
                <button onClick={() => switchTab('INCOME')} className="px-3 py-1.5 rounded border text-xs font-semibold bg-white text-emerald-600 border-emerald-300 hover:bg-emerald-50 transition-colors">
                  + New Income
                </button>
                <button onClick={() => void loadAll()} className="p-1.5 rounded border border-gray-200 text-gray-400 hover:bg-gray-50" title="Refresh">
                  <RefreshCw size={13} />
                </button>
              </div>
            </div>
            <div className="grid grid-cols-3 divide-x divide-gray-200 border border-gray-200 rounded-lg overflow-hidden">
              <div className="p-3 text-center">
                <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1">Expenses</p>
                <p className="text-sm font-bold text-rose-600">{money(summary.expenseTotal)}</p>
              </div>
              <div className="p-3 text-center">
                <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1">Income</p>
                <p className="text-sm font-bold text-emerald-600">{money(summary.incomeTotal)}</p>
              </div>
              <div className="p-3 text-center">
                <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-1">Net</p>
                <p className={`text-sm font-bold ${summary.net >= 0 ? 'text-sky-600' : 'text-rose-600'}`}>{money(summary.net)}</p>
              </div>
            </div>
          </div>

          {/* Filters + Table */}
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-200 flex flex-wrap items-center gap-2">
              <div className="relative flex-1 min-w-40">
                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
                <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search..." className="w-full pl-8 pr-3 py-1.5 text-xs border border-gray-300 rounded" />
              </div>
              <select value={filterType} onChange={(e) => setFilterType(e.target.value as 'ALL' | EntryTab)} className="px-2 py-1.5 border border-gray-300 rounded text-xs text-gray-600">
                <option value="ALL">All Types</option>
                <option value="EXPENSE">Expense</option>
                <option value="INCOME">Income</option>
              </select>
              <input type="date" title="From" value={filterDateFrom} onChange={(e) => setFilterDateFrom(e.target.value)} className="px-2 py-1.5 border border-gray-300 rounded text-xs text-gray-600" />
              <input type="date" title="To" value={filterDateTo} onChange={(e) => setFilterDateTo(e.target.value)} className="px-2 py-1.5 border border-gray-300 rounded text-xs text-gray-600" />
              <select value={filterAccountId} onChange={(e) => setFilterAccountId(Number(e.target.value) || 0)} className="px-2 py-1.5 border border-gray-300 rounded text-xs text-gray-600">
                <option value={0}>All Accounts</option>
                {allManualAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.accountName}</option>
                ))}
              </select>
              <button onClick={() => { setSearch(''); setFilterDateFrom(''); setFilterDateTo(''); setFilterAccountId(0); setFilterType('ALL'); }} className="px-2.5 py-1.5 text-xs text-gray-500 border border-gray-200 rounded hover:bg-gray-50">Reset</button>
              <button onClick={exportCsv} className="inline-flex items-center gap-1 px-2.5 py-1.5 text-xs text-gray-600 border border-gray-200 rounded hover:bg-gray-50">
                <Download size={12} /> CSV
              </button>
              <span className="ml-auto text-xs text-gray-400">{sortedRows.length} rows</span>
            </div>
            <div className="overflow-auto max-h-[60vh]">
              <table className="w-full min-w-[1000px] text-xs">
                <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 z-10">
                  <tr className="text-gray-500 uppercase tracking-wide">
                    <th className="w-8 px-3 py-2"></th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('code')}><span className="inline-flex items-center gap-1">Code <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('date')}><span className="inline-flex items-center gap-1">Date <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('type')}><span className="inline-flex items-center gap-1">Type <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('account')}><span className="inline-flex items-center gap-1">Account <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-right cursor-pointer hover:text-gray-700" onClick={() => sortBy('amount')}><span className="inline-flex items-center gap-1">Amount <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('payment')}><span className="inline-flex items-center gap-1">Payment <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2 text-left">Description</th>
                    <th className="px-3 py-2 text-left cursor-pointer hover:text-gray-700" onClick={() => sortBy('staff')}><span className="inline-flex items-center gap-1">Staff <ArrowDownUp size={11} /></span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {sortedRows.length === 0 ? (
                    <tr><td colSpan={9} className="px-3 py-10 text-center text-gray-400">No transactions found.</td></tr>
                  ) : sortedRows.map((row) => {
                    const open = Boolean(expanded[row.key]);
                    return (
                      <React.Fragment key={row.key}>
                        <tr className="hover:bg-gray-50">
                          <td className="px-3 py-2">
                            <button onClick={() => toggleExpand(row.key)} className="p-0.5 text-gray-400 hover:text-gray-600" title="Journal">
                              {open ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
                            </button>
                          </td>
                          <td className="px-3 py-2 font-medium text-gray-800">{row.code}</td>
                          <td className="px-3 py-2 text-gray-500">{row.date ? new Date(row.date).toLocaleString() : '-'}</td>
                          <td className="px-3 py-2">
                            <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold ${row.type === 'EXPENSE' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>{row.type}</span>
                          </td>
                          <td className="px-3 py-2 text-gray-700">{row.accountName || '-'}</td>
                          <td className={`px-3 py-2 text-right font-semibold ${row.type === 'EXPENSE' ? 'text-rose-600' : 'text-emerald-600'}`}>{money(row.amount)}</td>
                          <td className="px-3 py-2 text-gray-500">{row.paymentMethodName || '-'}</td>
                          <td className="px-3 py-2 text-gray-400 max-w-[220px] truncate">{row.description || '-'}</td>
                          <td className="px-3 py-2 text-gray-500">{row.staffName || '-'}</td>
                        </tr>
                        {open && (
                          <tr className="bg-gray-50">
                            <td colSpan={9} className="px-6 py-3">
                              <table className="text-xs border border-gray-200 rounded overflow-hidden w-auto">
                                <thead className="bg-gray-100">
                                  <tr className="text-gray-500">
                                    <th className="px-3 py-1.5 text-left border-b border-gray-200">Side</th>
                                    <th className="px-3 py-1.5 text-left border-b border-gray-200">Account</th>
                                    <th className="px-3 py-1.5 text-right border-b border-gray-200">Amount</th>
                                  </tr>
                                </thead>
                                <tbody className="bg-white divide-y divide-gray-100">
                                  {rowJournal(row).map((line, idx) => (
                                    <tr key={`${row.key}-${line.side}-${idx}`}>
                                      <td className="px-3 py-1.5 font-bold" style={{ color: line.side === 'DR' ? '#059669' : '#e11d48' }}>{line.side}</td>
                                      <td className="px-3 py-1.5 text-gray-700">{line.account}</td>
                                      <td className="px-3 py-1.5 text-right text-gray-700">{money(line.amount)}</td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    );
                  })}
                </tbody>
                {sortedRows.length > 0 && (
                  <tfoot className="bg-gray-50 border-t border-gray-200 text-xs font-semibold">
                    <tr>
                      <td colSpan={5} className="px-3 py-2 text-right text-gray-500">Total</td>
                      <td className="px-3 py-2 text-right">
                        <span className="text-emerald-600">{money(tableTotals.income)}</span>
                        <span className="mx-1 text-gray-300">/</span>
                        <span className="text-rose-600">{money(tableTotals.expense)}</span>
                      </td>
                      <td colSpan={3} className="px-3 py-2 text-gray-500">
                        Net: <span className={`font-bold ${(tableTotals.income - tableTotals.expense) >= 0 ? 'text-sky-600' : 'text-rose-600'}`}>{money(tableTotals.income - tableTotals.expense)}</span>
                      </td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          </div>
        </>
      )}

      {/* Confirm Modal */}
      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40">
          <div className="bg-white rounded-lg shadow-lg max-w-md w-full overflow-hidden border border-gray-200">
            <div className="px-4 py-3 border-b border-gray-200 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-gray-800">Confirm {tab === 'EXPENSE' ? 'Expense' : 'Income'}</h3>
              <button onClick={() => setConfirmOpen(false)} className="text-gray-400 hover:text-gray-600 text-sm">✕</button>
            </div>
            <div className="p-4 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-gray-600">
                <p><span className="text-gray-400">Date: </span>{entryDate || '-'}</p>
                <p><span className="text-gray-400">Amount: </span><span className="font-semibold">{money(amount)}</span></p>
                <p><span className="text-gray-400">Account: </span>{selectedAccount?.accountName || '-'}</p>
                <p><span className="text-gray-400">Payment: </span>{selectedMethod?.methodName || '-'}</p>
              </div>
              <table className="w-full border border-gray-200 rounded overflow-hidden">
                <thead className="bg-gray-50 text-gray-500">
                  <tr>
                    <th className="px-3 py-1.5 text-left border-b border-gray-200">Side</th>
                    <th className="px-3 py-1.5 text-left border-b border-gray-200">Account</th>
                    <th className="px-3 py-1.5 text-right border-b border-gray-200">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {liveJournal.map((line, idx) => (
                    <tr key={`confirm-${idx}`}>
                      <td className="px-3 py-1.5 font-bold" style={{ color: line.side === 'DR' ? '#059669' : '#e11d48' }}>{line.side}</td>
                      <td className="px-3 py-1.5 text-gray-700">{line.account}</td>
                      <td className="px-3 py-1.5 text-right text-gray-700">{money(line.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="px-4 py-3 border-t border-gray-200 flex justify-end gap-2">
              <button onClick={() => setConfirmOpen(false)} className="px-3 py-1.5 text-xs text-gray-600 border border-gray-200 rounded hover:bg-gray-50">Cancel</button>
              <button onClick={() => void submit()} disabled={saving} className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-60">
                {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExpenseIncomeManagement;
