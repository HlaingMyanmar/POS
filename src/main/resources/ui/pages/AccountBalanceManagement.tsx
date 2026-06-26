import React, { useState, useEffect, useCallback } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { accountingApiService } from '../services/accountingapiservice';
import { coaService } from '../services/coaapiservice';
import { AccountBalanceDTO, ChartOfAccountDTO } from '../types';
import { Wallet, RefreshCw, Filter, X } from 'lucide-react';

const AccountBalanceManagement: React.FC = () => {
  const [balances, setBalances] = useState<AccountBalanceDTO[]>([]);
  const [accounts, setAccounts] = useState<ChartOfAccountDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterAccountId, setFilterAccountId] = useState<number>(0);
  const [filterYear, setFilterYear] = useState('');
  const [filterResult, setFilterResult] = useState<AccountBalanceDTO | null>(null);
  const [filtering, setFiltering] = useState(false);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [balRes, coaRes] = await Promise.all([
        accountingApiService.getAllBalances(),
        coaService.getAll()
      ]);
      setBalances(balRes);
      setAccounts(coaRes);
      setFilterResult(null);
    } catch (e) {
      console.error('Failed to load account balances', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);
  useDataEvents(['Sale', 'Purchase', 'Expense', 'Income', 'Payment'], fetchAll);

  const applyFilter = async () => {
    if (!filterAccountId || !filterYear.trim()) return;
    setFiltering(true);
    try {
      const one = await accountingApiService.getBalanceByAccountAndYear(filterAccountId, filterYear.trim());
      setFilterResult(one);
    } catch (e) {
      console.error('Filter failed', e);
      setFilterResult(null);
    } finally {
      setFiltering(false);
    }
  };

  const clearFilter = () => {
    setFilterAccountId(0);
    setFilterYear('');
    setFilterResult(null);
  };

  const displayList = filterResult !== null ? [filterResult] : balances;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-slate-800">Account Balances</h2>
        <button
          onClick={fetchAll}
          className="flex items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-wrap items-end gap-3 mb-4">
          <div className="flex items-center gap-2 text-slate-600 text-sm font-medium">
            <Filter size={16} />
            Filter by Account & Year
          </div>
          <select
            value={filterAccountId}
            onChange={(e) => setFilterAccountId(Number(e.target.value))}
            className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
          >
            <option value={0}>Select account</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>{a.accountName} ({a.code})</option>
            ))}
          </select>
          <input
            type="text"
            value={filterYear}
            onChange={(e) => setFilterYear(e.target.value)}
            placeholder="Fiscal year (e.g. 2024)"
            className="px-3 py-2 w-32 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
          />
          <button
            onClick={applyFilter}
            disabled={!filterAccountId || !filterYear.trim() || filtering}
            className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 disabled:opacity-50"
          >
            {filtering ? 'Loading...' : 'Apply'}
          </button>
          {filterResult !== null && (
            <button onClick={clearFilter} className="flex items-center gap-1 px-3 py-2 text-slate-600 hover:bg-slate-100 rounded-lg text-sm">
              <X size={14} />
              Clear filter
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex items-center gap-2">
          <Wallet size={18} className="text-indigo-500" />
          <h3 className="font-bold text-slate-800 text-sm">
            {filterResult !== null ? 'Filtered Balance' : 'All Account Balances (Trial Balance)'}
          </h3>
        </div>
        <div className="overflow-auto max-h-[60vh] custom-scrollbar">
          {loading ? (
            <div className="p-8 text-center text-slate-400">Loading...</div>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead className="sticky top-0 bg-white z-10 shadow-sm">
                <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                  <th className="px-4 py-3 border-b border-slate-100">Account</th>
                  <th className="px-4 py-3 border-b border-slate-100">Fiscal Year</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Opening Balance</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Current Balance</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Last Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {displayList.length > 0 ? (
                  displayList.map((b) => (
                    <tr key={b.id} className="hover:bg-slate-50 text-xs">
                      <td className="px-4 py-3 font-medium text-slate-700">{b.accountName}</td>
                      <td className="px-4 py-3 text-slate-500">{b.fiscalYear}</td>
                      <td className="px-4 py-3 text-right text-slate-600">
                        {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(b.openingBalance)}
                      </td>
                      <td className={`px-4 py-3 text-right font-bold ${b.currentBalance >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(b.currentBalance)}
                      </td>
                      <td className="px-4 py-3 text-right text-slate-400">
                        {new Date(b.lastUpdated).toLocaleString()}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-400 italic">
                      {filterResult !== null ? 'No balance found for this filter.' : 'No balance records found.'}
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

export default AccountBalanceManagement;
