import React, { useCallback, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import {
  ArrowDownUp,
  ArrowLeft,
  CalendarDays,
  ChevronDown,
  ChevronRight,
  Download,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Search,
  TrendingDown,
  TrendingUp,
  Wallet
} from 'lucide-react';
import Swal from 'sweetalert2';
import { coaService } from '../services/coaapiservice';
import { expenseApiService } from '../services/expenseapiservice';
import { incomeApiService } from '../services/incomeapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { staffService } from '../services/staffapiservice';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { getFromSession } from '../utils/storageHelper';
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

type ExpenseIncomeManagementProps = {
  canBackdateExpense: boolean;
  canFutureDateExpense: boolean;
  canBackdateIncome: boolean;
  canFutureDateIncome: boolean;
};

const ExpenseIncomeManagement: React.FC<ExpenseIncomeManagementProps> = ({
  canBackdateExpense,
  canFutureDateExpense,
  canBackdateIncome,
  canFutureDateIncome
}) => {
  const currentUser = useMemo(() => {
    try {
      return JSON.parse(getFromSession('sspd_user') || '{}') as { name?: string; phone?: string; staffId?: number; roles?: string[]; permissions?: string[] };
    } catch {
      return {};
    }
  }, []);
  const canOverrideStaff = (currentUser.roles || []).some((role) =>
    ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role)
  ) || (currentUser as any).permissions?.some((permission: string) =>
    ['CAN_ACCESS_EXPENSE_STAFF_OVERRIDE', 'CAN_ACCESS_INCOME_STAFF_OVERRIDE'].includes(permission)
  );
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
        const matchedStaff = (staffRows || []).find((staff) => staff.id === currentUser.staffId);
        setStaffId((prev) => prev || matchedStaff?.id || (canOverrideStaff ? staffRows[0].id : 0));
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
  useRefreshOnTabActivate(loadAll);
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
  const todayDate = nowLocalDateTime().slice(0, 10);
  const canBackdate = tab === 'EXPENSE' ? canBackdateExpense : canBackdateIncome;
  const canFutureDate = tab === 'EXPENSE' ? canFutureDateExpense : canFutureDateIncome;
  const dateIsAllowed = () => {
    const selectedDate = entryDate.slice(0, 10);
    if (selectedDate < todayDate && !canBackdate) {
      Swal.fire('ခွင့်ပြုချက်လိုအပ်သည်', 'အတိတ်ရက်စွဲဖြင့် ထည့်သွင်းရန် သင့်မှာ ခွင့်ပြုချက်မရှိပါ။', 'warning');
      return false;
    }
    if (selectedDate > todayDate && !canFutureDate) {
      Swal.fire('ခွင့်ပြုချက်လိုအပ်သည်', 'အနာဂတ်ရက်စွဲဖြင့် ထည့်သွင်းရန် သင့်မှာ ခွင့်ပြုချက်မရှိပါ။', 'warning');
      return false;
    }
    return true;
  };
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
      Swal.fire('ဖြည့်စွက်ရန်လိုအပ်သည်', 'အကောင့်၊ ငွေပေးချေနည်း၊ ဝန်ထမ်းနှင့် ပမာဏကို မှန်ကန်စွာ ရွေး/ထည့်ပါ။', 'warning');
      return;
    }
    if (!dateIsAllowed()) return;
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
        title: tab === 'EXPENSE' ? 'ကုန်ကျစရိတ် သိမ်းပြီး' : 'ဝင်ငွေ သိမ်းပြီး',
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
      Swal.fire('Export', 'ထုတ်ယူရန် စာရင်းမရှိပါ။', 'info');
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

  const localIso = (d: Date) => {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  };

  const applyListRange = (preset: 'TODAY' | 'WEEK' | 'MONTH' | 'ALL') => {
    const today = new Date();
    if (preset === 'ALL') {
      setFilterDateFrom('');
      setFilterDateTo('');
      return;
    }
    if (preset === 'TODAY') {
      const t = localIso(today);
      setFilterDateFrom(t);
      setFilterDateTo(t);
      return;
    }
    if (preset === 'WEEK') {
      const start = new Date(today);
      const day = start.getDay() || 7;
      start.setDate(start.getDate() - day + 1);
      setFilterDateFrom(localIso(start));
      setFilterDateTo(localIso(today));
      return;
    }
    setFilterDateFrom(localIso(new Date(today.getFullYear(), today.getMonth(), 1)));
    setFilterDateTo(localIso(today));
  };

  const rangePreset = (() => {
    const today = localIso(new Date());
    if (!filterDateFrom && !filterDateTo) return 'ALL';
    if (filterDateFrom === today && filterDateTo === today) return 'TODAY';
    return '';
  })();

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <Loader2 size={28} className="animate-spin text-indigo-600" />
      </div>
    );
  }

  const fieldCls = 'w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500';
  const expenseForm = tab === 'EXPENSE';

  return (
    <div className="w-full max-w-none space-y-4">

      {activeView !== 'list' ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <button
              onClick={() => { clearForm(); setActiveView('list'); }}
              className="inline-flex items-center gap-2 px-3 py-2 text-slate-600 hover:bg-white rounded-xl text-sm font-semibold border border-slate-200"
            >
              <ArrowLeft size={15} /> စာရင်းသို့ ပြန်မည်
            </button>
            <div className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-black ${expenseForm ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'}`}>
              {expenseForm ? <TrendingDown size={14} /> : <TrendingUp size={14} />}
              {expenseForm ? 'ကုန်ကျစရိတ် အသစ်' : 'ဝင်ငွေ အသစ်'}
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
            <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-sm p-5 sm:p-6 space-y-4">
              <div className={`rounded-2xl border p-4 ${expenseForm ? 'border-rose-200 bg-rose-50' : 'border-emerald-200 bg-emerald-50'}`}>
                <p className={`text-xs font-bold ${expenseForm ? 'text-rose-600' : 'text-emerald-700'}`}>ပမာဏ (Ks)</p>
                <input type="number" min="0" step="0.01" value={amountInput} onChange={(e) => setAmountInput(e.target.value)} placeholder="0"
                  className={`mt-1 w-full bg-transparent text-3xl font-black tracking-tight outline-none ${expenseForm ? 'text-rose-700' : 'text-emerald-800'}`} />
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <label className="space-y-1.5">
                  <span className="block text-xs font-bold text-slate-500">ရက်စွဲ</span>
                  <input type="datetime-local" value={entryDate}
                    min={canBackdate ? undefined : `${todayDate}T00:00`}
                    max={canFutureDate ? undefined : `${todayDate}T23:59`}
                    onChange={(e) => setEntryDate(e.target.value)}
                    className={fieldCls} />
                </label>
                <label className="space-y-1.5">
                  <span className="block text-xs font-bold text-slate-500">
                    {expenseForm ? (isAssetPurchase ? 'ပိုင်ဆိုင်မှု အကောင့် *' : 'ကုန်ကျစရိတ် အကောင့် *') : 'ဝင်ငွေ အကောင့် *'}
                  </span>
                  <input
                    list="ei-account-options"
                    value={accountSearch}
                    onChange={(e) => onAccountSearch(e.target.value)}
                    placeholder="အကောင့် ရှာပါ / ရွေးပါ"
                    className={fieldCls}
                  />
                  <datalist id="ei-account-options">
                    {accountOptions.map((a) => (
                      <option key={a.id} value={accountLabel(a)} />
                    ))}
                  </datalist>
                </label>
                <label className="space-y-1.5">
                  <span className="block text-xs font-bold text-slate-500">ငွေပေးချေနည်း *</span>
                  <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value) || 0)} className={fieldCls}>
                    <option value={0}>ရွေးပါ</option>
                    {paymentMethodOptions.map((m) => (
                      <option key={m.id} value={m.id}>{m.methodName}</option>
                    ))}
                  </select>
                </label>
                <label className="space-y-1.5">
                  <span className="block text-xs font-bold text-slate-500">ဝန်ထမ်း *</span>
                  <select value={staffId} disabled={!canOverrideStaff}
                    onChange={(e) => setStaffId(Number(e.target.value) || 0)}
                    className={`${fieldCls} disabled:opacity-70`}>
                    <option value={0}>ရွေးပါ</option>
                    {staffs.map((s) => (
                      <option key={s.id} value={s.id}>{s.name} ({s.role})</option>
                    ))}
                  </select>
                </label>
                <label className="space-y-1.5 md:col-span-2">
                  <span className="block text-xs font-bold text-slate-500">မှတ်ချက်</span>
                  <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="ဥပမာ — ရုံးငှားခ / အပိုဝင်ငွေ"
                    className={fieldCls} />
                </label>
              </div>
              <div className="flex justify-end">
                <button onClick={clearForm} className="px-3 py-2 text-sm font-semibold text-slate-500 border border-slate-200 rounded-xl hover:bg-slate-50">
                  ရှင်းမည်
                </button>
              </div>
            </div>

            <div className={`rounded-2xl border shadow-sm p-5 sticky top-4 space-y-4 ${expenseForm ? 'border-rose-100 bg-white' : 'border-emerald-100 bg-white'}`}>
              <h3 className="text-xs font-black uppercase tracking-wider text-slate-500">စာရင်းသွင်းပုံ</h3>
              <div className="space-y-2">
                {liveJournal.map((line, idx) => (
                  <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 rounded-xl border border-slate-100">
                    <div>
                      <span className={`text-[10px] font-black ${line.side === 'DR' ? 'text-emerald-600' : 'text-rose-600'}`}>{line.side === 'DR' ? 'DR ဝင်' : 'CR ထွက်'}</span>
                      <p className="text-sm font-semibold text-slate-700 mt-0.5">{line.account}</p>
                    </div>
                    <p className="text-sm font-black text-slate-800">{money(line.amount)}</p>
                  </div>
                ))}
              </div>
              <div className="border-t border-slate-100 pt-4 space-y-2 text-sm">
                <div className="flex justify-between"><span className="text-slate-500">ပမာဏ</span><span className="font-black">{money(amount)}</span></div>
                {selectedAccount && <div className="flex justify-between gap-3"><span className="text-slate-500">အကောင့်</span><span className="font-semibold text-right truncate">{selectedAccount.accountName}</span></div>}
                {selectedMethod && <div className="flex justify-between"><span className="text-slate-500">ငွေပေးချေ</span><span className="font-semibold">{selectedMethod.methodName}</span></div>}
              </div>
              <button
                onClick={openConfirm}
                disabled={!validForm}
                className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-black ${
                  validForm
                    ? (expenseForm ? 'bg-rose-600 text-white hover:bg-rose-700' : 'bg-emerald-600 text-white hover:bg-emerald-700')
                    : 'bg-slate-100 text-slate-400 cursor-not-allowed'
                }`}
              >
                <Save size={15} /> {expenseForm ? 'ကုန်ကျစရိတ် သိမ်းမည်' : 'ဝင်ငွေ သိမ်းမည်'}
              </button>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-5 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                  <Wallet size={18} />
                </span>
                <div>
                  <h2 className="text-base font-black text-slate-800">ဝင်ငွေ / ကုန်ကျစရိတ်</h2>
                  <p className="text-xs text-slate-400">လက်ရှိငွေသား / ဘဏ်မှ ကိုယ်တိုင်သွင်းသည့်စာရင်း</p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <button onClick={() => switchTab('EXPENSE')} className="inline-flex min-h-10 items-center gap-1.5 px-3 rounded-xl border border-rose-200 bg-rose-50 text-xs font-black text-rose-700 hover:bg-rose-100">
                  <Plus size={14} /> ကုန်ကျစရိတ်
                </button>
                <button onClick={() => switchTab('INCOME')} className="inline-flex min-h-10 items-center gap-1.5 px-3 rounded-xl border border-emerald-200 bg-emerald-50 text-xs font-black text-emerald-700 hover:bg-emerald-100">
                  <Plus size={14} /> ဝင်ငွေ
                </button>
                <button onClick={() => void loadAll()} className="p-2 rounded-xl border border-slate-200 text-slate-400 hover:bg-slate-50" title="ပြန်ဆွဲရန်">
                  <RefreshCw size={14} />
                </button>
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="rounded-xl border border-rose-100 bg-rose-50 px-4 py-3">
                <p className="text-[11px] font-bold text-rose-500">ကုန်ကျစုစုပေါင်း</p>
                <p className="mt-1 text-lg font-black text-rose-700">{money(summary.expenseTotal)} <span className="text-xs font-bold">Ks</span></p>
              </div>
              <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3">
                <p className="text-[11px] font-bold text-emerald-600">ဝင်ငွေစုစုပေါင်း</p>
                <p className="mt-1 text-lg font-black text-emerald-700">{money(summary.incomeTotal)} <span className="text-xs font-bold">Ks</span></p>
              </div>
              <div className={`rounded-xl border px-4 py-3 ${summary.net >= 0 ? 'border-sky-100 bg-sky-50' : 'border-rose-100 bg-rose-50'}`}>
                <p className={`text-[11px] font-bold ${summary.net >= 0 ? 'text-sky-600' : 'text-rose-500'}`}>အသားတင်</p>
                <p className={`mt-1 text-lg font-black ${summary.net >= 0 ? 'text-sky-700' : 'text-rose-700'}`}>{money(summary.net)} <span className="text-xs font-bold">Ks</span></p>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
            <div className="px-4 py-3 border-b border-slate-100 flex flex-wrap items-center gap-2">
              <div className="relative flex-1 min-w-44">
                <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="ကုဒ်၊ အကောင့်၊ မှတ်ချက် ရှာပါ"
                  className="w-full pl-9 pr-3 py-2 text-sm border border-slate-200 rounded-xl bg-slate-50 focus:outline-none focus:border-indigo-400" />
              </div>
              <div className="flex flex-wrap gap-1.5">
                {([
                  ['ALL', 'အားလုံး'],
                  ['TODAY', 'ယနေ့'],
                  ['WEEK', 'ဒီအပတ်'],
                  ['MONTH', 'ဒီလ']
                ] as const).map(([key, label]) => (
                  <button key={key} type="button" onClick={() => applyListRange(key)}
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold border ${
                      rangePreset === key ? 'border-indigo-300 bg-indigo-50 text-indigo-700' : 'border-slate-200 text-slate-500 hover:bg-slate-50'
                    }`}>
                    {key !== 'ALL' && <CalendarDays size={11} />}{label}
                  </button>
                ))}
              </div>
              <select value={filterType} onChange={(e) => setFilterType(e.target.value as 'ALL' | EntryTab)} className="px-2.5 py-2 border border-slate-200 rounded-xl text-xs font-semibold text-slate-600 bg-slate-50">
                <option value="ALL">အမျိုးအစားအားလုံး</option>
                <option value="EXPENSE">ကုန်ကျစရိတ်</option>
                <option value="INCOME">ဝင်ငွေ</option>
              </select>
              <input type="date" title="မှ" value={filterDateFrom} onChange={(e) => setFilterDateFrom(e.target.value)} className="px-2.5 py-2 border border-slate-200 rounded-xl text-xs text-slate-600 bg-slate-50" />
              <input type="date" title="အထိ" value={filterDateTo} onChange={(e) => setFilterDateTo(e.target.value)} className="px-2.5 py-2 border border-slate-200 rounded-xl text-xs text-slate-600 bg-slate-50" />
              <select value={filterAccountId} onChange={(e) => setFilterAccountId(Number(e.target.value) || 0)} className="px-2.5 py-2 border border-slate-200 rounded-xl text-xs text-slate-600 bg-slate-50">
                <option value={0}>အကောင့်အားလုံး</option>
                {allManualAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.accountName}</option>
                ))}
              </select>
              <button onClick={() => { setSearch(''); setFilterDateFrom(''); setFilterDateTo(''); setFilterAccountId(0); setFilterType('ALL'); }} className="px-3 py-2 text-xs font-semibold text-slate-500 border border-slate-200 rounded-xl hover:bg-slate-50">ရှင်းမည်</button>
              <button onClick={exportCsv} className="inline-flex items-center gap-1 px-3 py-2 text-xs font-semibold text-slate-600 border border-slate-200 rounded-xl hover:bg-slate-50">
                <Download size={12} /> CSV
              </button>
              <span className="ml-auto text-xs font-semibold text-slate-400">{sortedRows.length} ကြောင်း</span>
            </div>
            <div className="overflow-auto max-h-[60vh]">
              <table className="w-full min-w-[1000px] text-xs">
                <thead className="sticky top-0 bg-slate-50 border-b border-slate-100 z-10">
                  <tr className="text-slate-500 font-black">
                    <th className="w-8 px-3 py-2.5"></th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('code')}><span className="inline-flex items-center gap-1">ကုဒ် <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('date')}><span className="inline-flex items-center gap-1">ရက်စွဲ <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('type')}><span className="inline-flex items-center gap-1">အမျိုးအစား <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('account')}><span className="inline-flex items-center gap-1">အကောင့် <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-right cursor-pointer" onClick={() => sortBy('amount')}><span className="inline-flex items-center gap-1">ပမာဏ <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('payment')}><span className="inline-flex items-center gap-1">ငွေပေးချေ <ArrowDownUp size={11} /></span></th>
                    <th className="px-3 py-2.5 text-left">မှတ်ချက်</th>
                    <th className="px-3 py-2.5 text-left cursor-pointer" onClick={() => sortBy('staff')}><span className="inline-flex items-center gap-1">ဝန်ထမ်း <ArrowDownUp size={11} /></span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {sortedRows.length === 0 ? (
                    <tr><td colSpan={9} className="px-3 py-16 text-center text-slate-400">
                      <p className="font-bold text-slate-600">စာရင်းမရှိသေးပါ</p>
                      <p className="mt-1 text-xs">ကုန်ကျစရိတ် သို့မဟုတ် ဝင်ငွေ အသစ်ထည့်ပါ။</p>
                    </td></tr>
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
                            <span className={`inline-flex px-1.5 py-0.5 rounded-md text-[10px] font-black ${row.type === 'EXPENSE' ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>{row.type === 'EXPENSE' ? 'ကုန်ကျ' : 'ဝင်ငွေ'}</span>
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
                      <td colSpan={5} className="px-3 py-2 text-right text-slate-500">စုစုပေါင်း</td>
                      <td className="px-3 py-2 text-right">
                        <span className="text-emerald-600">{money(tableTotals.income)}</span>
                        <span className="mx-1 text-gray-300">/</span>
                        <span className="text-rose-600">{money(tableTotals.expense)}</span>
                      </td>
                      <td colSpan={3} className="px-3 py-2 text-gray-500">
                        အသားတင်: <span className={`font-black ${(tableTotals.income - tableTotals.expense) >= 0 ? 'text-sky-600' : 'text-rose-600'}`}>{money(tableTotals.income - tableTotals.expense)}</span>
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
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full overflow-hidden border border-slate-200">
            <div className="px-5 py-3.5 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-sm font-black text-slate-800">{tab === 'EXPENSE' ? 'ကုန်ကျစရိတ် အတည်ပြုမည်' : 'ဝင်ငွေ အတည်ပြုမည်'}</h3>
              <button onClick={() => setConfirmOpen(false)} className="text-slate-400 hover:text-slate-600 text-sm">✕</button>
            </div>
            <div className="p-5 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-slate-600">
                <p><span className="text-slate-400">ရက်စွဲ: </span>{entryDate || '-'}</p>
                <p><span className="text-slate-400">ပမာဏ: </span><span className="font-black">{money(amount)}</span></p>
                <p><span className="text-slate-400">အကောင့်: </span>{selectedAccount?.accountName || '-'}</p>
                <p><span className="text-slate-400">ငွေပေးချေ: </span>{selectedMethod?.methodName || '-'}</p>
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
            <div className="px-5 py-3.5 border-t border-slate-100 flex justify-end gap-2">
              <button onClick={() => setConfirmOpen(false)} className="px-3 py-2 text-xs font-semibold text-slate-600 border border-slate-200 rounded-xl hover:bg-slate-50">မလုပ်တော့ပါ</button>
              <button onClick={() => void submit()} disabled={saving} className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-black text-white rounded-xl disabled:opacity-60 ${tab === 'EXPENSE' ? 'bg-rose-600 hover:bg-rose-700' : 'bg-emerald-600 hover:bg-emerald-700'}`}>
                {saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
                အတည်ပြု သိမ်းမည်
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExpenseIncomeManagement;
