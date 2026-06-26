import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowDownRight,
  ArrowUpRight,
  Building2,
  CheckCircle2,
  Edit3,
  Filter,
  Loader2,
  RefreshCw,
  Search,
  ShieldCheck,
  Wallet,
  X
} from 'lucide-react';
import Swal from 'sweetalert2';
import { accountingApiService } from '../services/accountingapiservice';
import { coaService } from '../services/coaapiservice';
import { staffService } from '../services/staffapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import {
  AccountBalanceDTO,
  ChartOfAccountDTO,
  PaymentMethodDTO,
  StaffDTO
} from '../types';

interface AccountRow {
  accountId: number;
  accountName: string;
  accountType: string;
  accountCode: string;
  opening: number;
  current: number;
}

interface EditState {
  row: AccountRow;
  amountStr: string;
  staffId: number | null;
  pmId: number | null;
  saving: boolean;
  error: string | null;
}

const TYPE_ORDER = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'];

const TYPE_META: Record<string, { label: string; short: string; badge: string; dot: string }> = {
  ASSET: {
    label: 'Asset',
    short: 'ပိုင်ဆိုင်မှု',
    badge: 'bg-sky-50 text-sky-700 border-sky-100',
    dot: 'bg-sky-500'
  },
  LIABILITY: {
    label: 'Liability',
    short: 'အကြွေး',
    badge: 'bg-rose-50 text-rose-700 border-rose-100',
    dot: 'bg-rose-500'
  },
  EQUITY: {
    label: 'Equity',
    short: 'ရင်းနှီးငွေ',
    badge: 'bg-violet-50 text-violet-700 border-violet-100',
    dot: 'bg-violet-500'
  },
  INCOME: {
    label: 'Income',
    short: 'ဝင်ငွေ',
    badge: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    dot: 'bg-emerald-500'
  },
  EXPENSE: {
    label: 'Expense',
    short: 'ကုန်ကျငွေ',
    badge: 'bg-amber-50 text-amber-700 border-amber-100',
    dot: 'bg-amber-500'
  }
};

const fmt = (n: number) => n.toLocaleString('en-US', { maximumFractionDigits: 0 });
const money = (n: number) => `${fmt(n)} Ks`;
const normalizeType = (value?: string) => String(value || '').toUpperCase();

const MetricCard: React.FC<{
  label: string;
  value: string;
  hint: string;
  icon: React.ReactNode;
  tone: 'blue' | 'rose' | 'violet' | 'emerald' | 'slate';
}> = ({ label, value, hint, icon, tone }) => {
  const color = {
    blue: 'bg-blue-50 text-blue-700 border-blue-100',
    rose: 'bg-rose-50 text-rose-700 border-rose-100',
    violet: 'bg-violet-50 text-violet-700 border-violet-100',
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    slate: 'bg-slate-50 text-slate-700 border-slate-200'
  }[tone];

  return (
    <div className={`rounded-lg border ${color} px-4 py-3 flex items-start gap-3 min-w-0`}>
      <div className="mt-0.5 shrink-0">{icon}</div>
      <div className="min-w-0">
        <p className="text-[10px] font-black uppercase tracking-wide opacity-75">{label}</p>
        <p className="text-lg font-black tabular-nums truncate">{value}</p>
        <p className="text-[11px] font-semibold opacity-70 truncate">{hint}</p>
      </div>
    </div>
  );
};

const OpeningBalancePage: React.FC = () => {
  const [accounts, setAccounts] = useState<AccountRow[]>([]);
  const [staffList, setStaffList] = useState<StaffDTO[]>([]);
  const [pmList, setPmList] = useState<PaymentMethodDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [query, setQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | string>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'SET' | 'EMPTY'>('ALL');

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [balances, coas, staff, pms] = await Promise.all([
        accountingApiService.getAllBalances(),
        coaService.getAll(),
        staffService.getAllActive(),
        paymentMethodService.getAllActive(),
      ]);

      const merged: AccountRow[] = (coas as ChartOfAccountDTO[]).map(coa => {
        const bal = (balances as AccountBalanceDTO[]).find(b => b.accountId === coa.id);
        return {
          accountId: coa.id,
          accountName: coa.accountName,
          accountType: normalizeType(coa.accountType),
          accountCode: coa.code ?? '',
          opening: Number(bal?.openingBalance ?? 0),
          current: Number(bal?.currentBalance ?? 0),
        };
      });

      setAccounts(merged);
      setStaffList(staff as StaffDTO[]);
      setPmList(pms as PaymentMethodDTO[]);
    } catch (e: any) {
      setError(e?.message ?? 'ဒေတာဖတ်မရပါ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const summary = useMemo(() => {
    const byType = (type: string) => accounts
      .filter(a => a.accountType === type)
      .reduce((sum, row) => sum + row.current, 0);
    const totalOpening = accounts.reduce((sum, row) => sum + row.opening, 0);
    const setCount = accounts.filter(row => row.opening > 0).length;
    const assets = byType('ASSET');
    const liabilities = byType('LIABILITY');
    const equity = byType('EQUITY');
    const netCapital = assets - liabilities;
    const difference = assets - liabilities - equity;
    return { assets, liabilities, equity, netCapital, difference, totalOpening, setCount };
  }, [accounts]);

  const filteredRows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return accounts
      .filter(row => typeFilter === 'ALL' || row.accountType === typeFilter)
      .filter(row => statusFilter === 'ALL' || (statusFilter === 'SET' ? row.opening > 0 : row.opening <= 0))
      .filter(row => !q ||
        row.accountName.toLowerCase().includes(q) ||
        row.accountCode.toLowerCase().includes(q) ||
        row.accountType.toLowerCase().includes(q)
      )
      .sort((a, b) => {
        const typeDelta = TYPE_ORDER.indexOf(a.accountType) - TYPE_ORDER.indexOf(b.accountType);
        if (typeDelta !== 0) return typeDelta;
        return a.accountCode.localeCompare(b.accountCode);
      });
  }, [accounts, query, statusFilter, typeFilter]);

  const typeTotals = useMemo(() => {
    const map: Record<string, { count: number; opening: number; current: number }> = {};
    for (const row of accounts) {
      const key = row.accountType;
      if (!map[key]) map[key] = { count: 0, opening: 0, current: 0 };
      map[key].count += 1;
      map[key].opening += row.opening;
      map[key].current += row.current;
    }
    return TYPE_ORDER.filter(type => map[type]).map(type => ({ type, ...map[type] }));
  }, [accounts]);

  const openEdit = (row: AccountRow) => {
    setEditState({
      row,
      amountStr: row.opening > 0 ? String(Math.round(row.opening)) : '',
      staffId: staffList[0]?.id ?? null,
      pmId: pmList[0]?.id ?? null,
      saving: false,
      error: null,
    });
  };

  const saveEdit = async () => {
    if (!editState) return;
    const amount = parseFloat(editState.amountStr);
    if (Number.isNaN(amount) || amount < 0) {
      setEditState(s => s && { ...s, error: 'ငွေပမာဏကို မှန်မှန်ထည့်ပါ' });
      return;
    }
    if (!editState.staffId) {
      setEditState(s => s && { ...s, error: 'Staff ရွေးပါ' });
      return;
    }
    if (!editState.pmId) {
      setEditState(s => s && { ...s, error: 'Payment method ရွေးပါ' });
      return;
    }

    setEditState(s => s && { ...s, saving: true, error: null });
    try {
      await accountingApiService.setOpeningBalance(
        editState.row.accountId,
        amount,
        editState.staffId,
        editState.pmId
      );
      setEditState(null);
      await load();
      Swal.fire({ icon: 'success', title: 'Opening balance သိမ်းပြီးပါပြီ', timer: 1300, showConfirmButton: false });
    } catch (e: any) {
      setEditState(s => s && { ...s, saving: false, error: e?.message ?? 'သိမ်းမရပါ' });
    }
  };

  const clearFilters = () => {
    setQuery('');
    setTypeFilter('ALL');
    setStatusFilter('ALL');
  };

  return (
    <div className="h-full flex flex-col bg-slate-50/50 overflow-hidden">
      <div className="shrink-0 bg-white border-b border-slate-200 px-5 py-4">
        <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-lg bg-blue-600 text-white flex items-center justify-center shadow-sm">
              <Building2 size={23} />
            </div>
            <div>
              <h1 className="text-lg font-black text-slate-900 tracking-tight">Opening Balance</h1>
              <p className="text-xs text-slate-500 font-semibold">စာရင်းကိုင် account များ၏ စတင်လက်ကျန် သတ်မှတ်ရန်</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={load}
              disabled={loading}
              className="h-10 px-4 rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-black uppercase inline-flex items-center gap-2 disabled:opacity-50"
            >
              <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-4">
        {error && (
          <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-sm font-semibold">
            <AlertCircle size={16} />
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          <MetricCard label="Assets" value={money(summary.assets)} hint="စုစုပေါင်း ပိုင်ဆိုင်မှု" icon={<ArrowUpRight size={17} />} tone="blue" />
          <MetricCard label="Liabilities" value={money(summary.liabilities)} hint="ပေးရန်ရှိအကြွေး" icon={<ArrowDownRight size={17} />} tone="rose" />
          <MetricCard label="Equity" value={money(summary.equity)} hint="ရင်းနှီးငွေ account" icon={<Wallet size={17} />} tone="violet" />
          <MetricCard label="Net Capital" value={money(summary.netCapital)} hint="Assets minus liabilities" icon={<ShieldCheck size={17} />} tone={summary.netCapital >= 0 ? 'emerald' : 'rose'} />
          <MetricCard label="Completed" value={`${summary.setCount}/${accounts.length}`} hint={`${money(summary.totalOpening)} opening total`} icon={<CheckCircle2 size={17} />} tone="slate" />
        </div>

        <div className={`rounded-lg border px-4 py-3 flex flex-col md:flex-row md:items-center justify-between gap-3 ${
          Math.abs(summary.difference) < 1
            ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
            : 'bg-amber-50 border-amber-200 text-amber-800'
        }`}>
          <div className="flex items-center gap-3">
            {Math.abs(summary.difference) < 1 ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
            <div>
              <p className="text-sm font-black">
                {Math.abs(summary.difference) < 1 ? 'Accounting equation balanced' : 'Accounting equation needs review'}
              </p>
              <p className="text-xs font-semibold opacity-80">Assets - Liabilities - Equity = {money(summary.difference)}</p>
            </div>
          </div>
          <p className="text-xs font-bold opacity-80">Opening balances create journals, so choose staff and payment method carefully.</p>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
          {typeTotals.map(item => {
            const meta = TYPE_META[item.type] ?? TYPE_META.ASSET;
            return (
              <button
                key={item.type}
                onClick={() => setTypeFilter(typeFilter === item.type ? 'ALL' : item.type)}
                className={`rounded-lg border px-3 py-2 text-left transition-all ${
                  typeFilter === item.type
                    ? `${meta.badge} ring-2 ring-offset-1 ring-blue-200`
                    : 'bg-white border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <span className={`w-2 h-2 rounded-full ${meta.dot}`} />
                  <span className="text-[11px] font-black uppercase">{meta.label}</span>
                  <span className="ml-auto text-[10px] font-bold opacity-70">{item.count}</span>
                </div>
                <p className="text-xs font-black tabular-nums">{money(item.current)}</p>
              </button>
            );
          })}
        </div>

        <div className="rounded-lg bg-white border border-slate-200 p-3 flex flex-col xl:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Account code / name / type ရှာပါ..."
              className="w-full h-10 pl-9 pr-3 rounded-lg border border-slate-200 bg-slate-50 text-sm font-semibold outline-none focus:bg-white focus:border-blue-500"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 p-1 bg-slate-100 border border-slate-200 rounded-lg">
              <Filter size={13} className="ml-2 text-slate-500" />
              {[
                { key: 'ALL', label: 'All' },
                { key: 'SET', label: 'Set' },
                { key: 'EMPTY', label: 'Empty' },
              ].map(item => (
                <button
                  key={item.key}
                  onClick={() => setStatusFilter(item.key as any)}
                  className={`px-3 py-1.5 rounded-md text-xs font-black uppercase ${
                    statusFilter === item.key ? 'bg-white text-blue-700 border border-slate-200' : 'text-slate-500 hover:text-blue-700'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <button
              onClick={clearFilters}
              className="h-10 px-3 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 text-xs font-black uppercase"
            >
              Clear
            </button>
          </div>
        </div>

        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          {loading ? (
            <div className="h-72 flex flex-col items-center justify-center text-slate-400 gap-3">
              <Loader2 size={30} className="animate-spin text-blue-600" />
              <p className="text-sm font-bold">Opening balances loading...</p>
            </div>
          ) : (
            <div className="overflow-auto">
              <table className="w-full min-w-[980px] text-left">
                <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 z-10">
                  <tr>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide">Account</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide">Type</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide text-right">Opening</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide text-right">Current</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide text-center">Status</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase tracking-wide text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredRows.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="px-4 py-20 text-center text-slate-400">
                        <div className="flex flex-col items-center gap-2">
                          <Search size={28} />
                          <p className="text-sm font-black uppercase tracking-wide">No accounts found</p>
                        </div>
                      </td>
                    </tr>
                  ) : filteredRows.map(row => {
                    const meta = TYPE_META[row.accountType] ?? TYPE_META.ASSET;
                    const isSet = row.opening > 0;
                    return (
                      <tr key={row.accountId} className="hover:bg-slate-50">
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <div className={`w-9 h-9 rounded-lg ${meta.badge} border flex items-center justify-center shrink-0`}>
                              <Building2 size={15} />
                            </div>
                            <div className="min-w-0">
                              <p className="text-sm font-black text-slate-800 truncate">{row.accountName}</p>
                              <p className="text-[11px] text-slate-500 font-mono font-bold">{row.accountCode || '-'}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-black uppercase ${meta.badge}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
                            {meta.label}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right text-sm font-black text-slate-800 tabular-nums">{money(row.opening)}</td>
                        <td className="px-4 py-3 text-right text-sm font-bold text-slate-600 tabular-nums">{money(row.current)}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-black uppercase ${
                            isSet
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
                              : 'bg-slate-50 text-slate-500 border-slate-200'
                          }`}>
                            {isSet ? <CheckCircle2 size={12} /> : <AlertCircle size={12} />}
                            {isSet ? 'Set' : 'Empty'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => openEdit(row)}
                            className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 text-xs font-black uppercase"
                          >
                            <Edit3 size={13} />
                            Set
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {editState && (
        <OpeningBalanceDrawer
          state={editState}
          staffList={staffList}
          pmList={pmList}
          onChange={patch => setEditState(s => s ? { ...s, ...patch } : s)}
          onSave={saveEdit}
          onClose={() => setEditState(null)}
        />
      )}
    </div>
  );
};

interface DrawerProps {
  state: EditState;
  staffList: StaffDTO[];
  pmList: PaymentMethodDTO[];
  onChange: (patch: Partial<EditState>) => void;
  onSave: () => void;
  onClose: () => void;
}

const OpeningBalanceDrawer: React.FC<DrawerProps> = ({ state, staffList, pmList, onChange, onSave, onClose }) => {
  const meta = TYPE_META[state.row.accountType] ?? TYPE_META.ASSET;
  const amount = Number(state.amountStr || 0);

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex justify-end" onMouseDown={onClose}>
      <div
        className="w-full max-w-md h-full bg-white shadow-2xl flex flex-col"
        onMouseDown={e => e.stopPropagation()}
      >
        <div className="px-5 py-4 border-b border-slate-200 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-black uppercase ${meta.badge}`}>
                <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
                {meta.label}
              </span>
            </div>
            <h2 className="text-lg font-black text-slate-900">Set Opening Balance</h2>
            <p className="text-xs text-slate-500 font-semibold mt-1">
              <span className="font-mono">{state.row.accountCode || '-'}</span> · {state.row.accountName}
            </p>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg hover:bg-slate-100 text-slate-500">
            <X size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-auto p-5 space-y-4">
          <div className="rounded-lg bg-slate-50 border border-slate-200 p-4 grid grid-cols-2 gap-3">
            <div>
              <p className="text-[10px] font-black text-slate-500 uppercase">Current opening</p>
              <p className="text-sm font-black text-slate-800 tabular-nums">{money(state.row.opening)}</p>
            </div>
            <div>
              <p className="text-[10px] font-black text-slate-500 uppercase">New opening</p>
              <p className="text-sm font-black text-blue-700 tabular-nums">{money(amount)}</p>
            </div>
          </div>

          <div>
            <label className="block text-xs font-black text-slate-600 uppercase tracking-wide mb-1">Opening Amount (Ks)</label>
            <input
              type="number"
              min="0"
              value={state.amountStr}
              onChange={e => onChange({ amountStr: e.target.value })}
              className="w-full h-12 rounded-lg border border-slate-200 bg-slate-50 px-3 text-lg font-black tabular-nums outline-none focus:bg-white focus:border-blue-500"
              placeholder="0"
              autoFocus
            />
          </div>

          <div>
            <label className="block text-xs font-black text-slate-600 uppercase tracking-wide mb-1">Staff</label>
            <select
              value={state.staffId ?? ''}
              onChange={e => onChange({ staffId: Number(e.target.value) || null })}
              className="w-full h-11 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold outline-none focus:border-blue-500"
            >
              <option value="">Staff ရွေးပါ</option>
              {staffList.map(staff => (
                <option key={staff.id} value={staff.id}>{staff.name}{staff.role ? ` (${staff.role})` : ''}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-black text-slate-600 uppercase tracking-wide mb-1">Payment Method</label>
            <select
              value={state.pmId ?? ''}
              onChange={e => onChange({ pmId: Number(e.target.value) || null })}
              className="w-full h-11 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold outline-none focus:border-blue-500"
            >
              <option value="">Payment method ရွေးပါ</option>
              {pmList.map(method => (
                <option key={method.id} value={method.id}>{method.methodName}</option>
              ))}
            </select>
          </div>

          <div className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-3 text-xs text-blue-800 font-semibold leading-relaxed">
            Opening balance သိမ်းပြီးပါက journal entry တစ်ခုဖန်တီးမည်။ နောက်ပိုင်းပြန်ပြင်လျှင် accounting report များပြောင်းနိုင်သည်။
          </div>

          {state.error && (
            <div className="flex items-center gap-2 rounded-lg bg-rose-50 border border-rose-200 px-3 py-2 text-sm text-rose-700 font-semibold">
              <AlertCircle size={15} />
              {state.error}
            </div>
          )}
        </div>

        <div className="p-5 border-t border-slate-200 bg-white flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 h-11 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 text-xs font-black uppercase"
          >
            Cancel
          </button>
          <button
            onClick={onSave}
            disabled={state.saving}
            className="flex-[2] h-11 rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 text-xs font-black uppercase inline-flex items-center justify-center gap-2"
          >
            {state.saving ? <Loader2 size={15} className="animate-spin" /> : <CheckCircle2 size={15} />}
            Save Opening
          </button>
        </div>
      </div>
    </div>
  );
};

export default OpeningBalancePage;
