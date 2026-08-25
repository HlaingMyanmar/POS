import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import Swal from 'sweetalert2';
import { Ban, CheckCircle2, CreditCard, Loader2, RefreshCw, Save, Search } from 'lucide-react';
import { creditAlertService } from '../services/creditalertapiservice';
import { creditTermService } from '../services/credittermapiservice';
import { customerPaymentService } from '../services/customerpaymentapiservice';
import { customerService } from '../services/customerapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { saleApiService } from '../services/saleapiservice';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { getFromSession } from '../utils/storageHelper';
import { AlertType, CreditAlertDTO, CustomerCreditTermDTO, CustomerDTO, CustomerPaymentDTO, PaymentMethodDTO, PaymentTransactionDTO, SaleDTO } from '../types';

type TabKey = 'portfolio' | 'alerts';
type AlertFilter = 'All' | AlertType;

type TermForm = { creditAllowed: boolean; creditLimit: number; creditDays: number };
type ControlForm = { creditHold: boolean; creditHoldReason: string; blacklisted: boolean; blacklistReason: string };
type PaymentForm = { saleId: number; amount: string; paymentMethodId: number; transactionNo: string; note: string; payments: PaymentTransactionDTO[]; allocateFifo: boolean };
const DEFAULT_TERM: TermForm = { creditAllowed: false, creditLimit: 0, creditDays: 0 };
const DEFAULT_CONTROL: ControlForm = { creditHold: false, creditHoldReason: '', blacklisted: false, blacklistReason: '' };
const DEFAULT_PAYMENT: PaymentForm = { saleId: 0, amount: '', paymentMethodId: 0, transactionNo: '', note: '', payments: [], allocateFifo: true };

type ReceivableRow = { saleId: number; saleCode?: string; dueDate?: string; netAmount?: number; paidAmount?: number; dueAmount?: number };
type CreditSummary = { advanceBalance?: number; availableCredit?: number };

const currentStaffId = () => {
  try {
    return Number(JSON.parse(getFromSession('sspd_user') || '{}').staffId) || 0;
  } catch {
    return 0;
  }
};

const money = (v: number) => `${new Intl.NumberFormat('en-US').format(v || 0)} Ks`;
const normalizePayments = (payments: PaymentTransactionDTO[]) =>
  payments
    .map((p) => ({ ...p, paymentMethodId: Number(p.paymentMethodId) || 0, amount: Number(p.amount) || 0, transactionNo: p.transactionNo?.trim() || undefined }))
    .filter((p) => p.paymentMethodId > 0 && p.amount > 0);
const paymentTotal = (payments: PaymentTransactionDTO[]) => normalizePayments(payments).reduce((sum, p) => sum + (Number(p.amount) || 0), 0);
const fmt = (v?: string) => (!v ? '-' : Number.isNaN(new Date(v).getTime()) ? v : new Date(v).toLocaleString('en-CA', { hour12: false }));
const normalizeAlertType = (t?: string): AlertType => {
  const s = (t || '').toLowerCase();
  if (s.includes('overdue')) return 'Overdue';
  if (s.includes('due_soon') || s.includes('duesoon')) return 'Due_Soon';
  if (s.includes('large')) return 'Large_Credit_Sale';
  return 'Credit_Limit_Exceeded';
};

const CreditManagement: React.FC = () => {
  const [tab, setTab] = useState<TabKey>('portfolio');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [alertFilter, setAlertFilter] = useState<AlertFilter>('All');

  const [customers, setCustomers] = useState<CustomerDTO[]>([]);
  const [terms, setTerms] = useState<CustomerCreditTermDTO[]>([]);
  const [alerts, setAlerts] = useState<CreditAlertDTO[]>([]);
  const [sales, setSales] = useState<SaleDTO[]>([]);
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [payments, setPayments] = useState<CustomerPaymentDTO[]>([]);

  const [selectedCustomerId, setSelectedCustomerId] = useState(0);
  const [termForm, setTermForm] = useState<TermForm>(DEFAULT_TERM);
  const [controlForm, setControlForm] = useState<ControlForm>(DEFAULT_CONTROL);
  const [paymentForm, setPaymentForm] = useState<PaymentForm>(DEFAULT_PAYMENT);
  const [creditSummary, setCreditSummary] = useState<CreditSummary>({});
  const [receivables, setReceivables] = useState<ReceivableRow[]>([]);
  const [allocAmounts, setAllocAmounts] = useState<Record<number, string>>({});
  const [applySaleId, setApplySaleId] = useState(0);
  const [applyAmount, setApplyAmount] = useState('');
  const [applyReason, setApplyReason] = useState('');

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [cus, termData, alertData, saleData, methodData] = await Promise.all([
        customerService.getAll(),
        creditTermService.getAll(),
        creditAlertService.getAllUnresolved(),
        saleApiService.getAll(),
        paymentMethodService.getAllActive()
      ]);
      setCustomers(cus || []);
      setTerms(termData || []);
      setAlerts((alertData || []).map((a) => ({ ...a, alertType: normalizeAlertType(a.alertType), resolved: Boolean(a.resolved) })));
      setSales(saleData || []);
      setMethods(methodData || []);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load credit data', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshAll = useCallback(async () => {
    const [cus, termData, alertData, saleData] = await Promise.all([
      customerService.getAll(),
      creditTermService.getAll(),
      creditAlertService.getAllUnresolved(),
      saleApiService.getAll()
    ]);
    setCustomers(cus || []);
    setTerms(termData || []);
    setAlerts((alertData || []).map((a) => ({ ...a, alertType: normalizeAlertType(a.alertType), resolved: Boolean(a.resolved) })));
    setSales(saleData || []);
  }, []);

  const loadFinance = useCallback(async (customerId: number) => {
    const [pays, rec, sum] = await Promise.all([
      customerPaymentService.getByCustomer(customerId).catch(() => []),
      customerPaymentService.receivables(customerId).catch(() => []),
      customerPaymentService.creditSummary(customerId).catch(() => ({} as CreditSummary))
    ]);
    setPayments(pays || []);
    setReceivables(rec || []);
    setCreditSummary(sum || {});
    setAllocAmounts({});
    setApplySaleId(0);
    setApplyAmount('');
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);
  useDataEvents(['Sale', 'Customer Payment', 'Customer'], loadAll);

  const selected = useMemo(() => customers.find((c) => c.id === selectedCustomerId) || null, [customers, selectedCustomerId]);
  const termByCustomer = useMemo(() => new Map(terms.map((t) => [t.customerId, t])), [terms]);
  const dueByCustomer = useMemo(() => {
    const m = new Map<number, number>();
    sales.forEach((s) => {
      const due = Number(s.dueAmount) || 0;
      if (due > 0) m.set(s.customerId, (m.get(s.customerId) || 0) + due);
    });
    return m;
  }, [sales]);
  const overdueByCustomer = useMemo(() => {
    const m = new Map<number, number>();
    const today = new Date(); today.setHours(0, 0, 0, 0);
    sales.forEach((s) => {
      if ((Number(s.dueAmount) || 0) <= 0 || !s.dueDate) return;
      const d = new Date(s.dueDate); d.setHours(0, 0, 0, 0);
      if (d < today) m.set(s.customerId, (m.get(s.customerId) || 0) + 1);
    });
    return m;
  }, [sales]);

  const filteredCustomers = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return customers;
    return customers.filter((c) => [c.name, c.phone, c.address].join(' ').toLowerCase().includes(q));
  }, [customers, search]);

  const dueSales = useMemo(
    () => sales.filter((s) => s.customerId === selectedCustomerId && (Number(s.dueAmount) || 0) > 0),
    [sales, selectedCustomerId]
  );

  const filteredAlerts = useMemo(
    () => (alertFilter === 'All' ? alerts : alerts.filter((a) => normalizeAlertType(a.alertType) === alertFilter)),
    [alerts, alertFilter]
  );

  const summary = useMemo(() => ({
    totalDue: Array.from(dueByCustomer.values()).reduce((a: number, b: number) => a + b, 0),
    overdue: Array.from(overdueByCustomer.values()).reduce((a: number, b: number) => a + b, 0),
    hold: customers.filter((c) => Boolean(c.creditHold)).length,
    blacklist: customers.filter((c) => Boolean(c.blacklisted)).length
  }), [customers, dueByCustomer, overdueByCustomer]);

  useEffect(() => {
    if (!customers.length) return;
    const nextId = selectedCustomerId && customers.some((c) => c.id === selectedCustomerId) ? selectedCustomerId : customers[0].id;
    setSelectedCustomerId(nextId);
  }, [customers, selectedCustomerId]);

  useEffect(() => {
    if (!selected) return;
    const term = termByCustomer.get(selected.id);
    setTermForm({ creditAllowed: Boolean(term?.creditAllowed), creditLimit: Number(term?.creditLimit) || 0, creditDays: Number(term?.creditDays) || 0 });
    setControlForm({
      creditHold: Boolean(selected.creditHold),
      creditHoldReason: selected.creditHoldReason || '',
      blacklisted: Boolean(selected.blacklisted),
      blacklistReason: selected.blacklistReason || ''
    });
    setPaymentForm((p) => ({ ...DEFAULT_PAYMENT, paymentMethodId: p.paymentMethodId || methods[0]?.id || 0, allocateFifo: true }));
    void loadFinance(selected.id);
  }, [selected, termByCustomer, methods, loadFinance]);

  const saveTerm = async () => {
    if (!selected) return;
    setSaving(true);
    try {
      const existing = termByCustomer.get(selected.id);
      const payload: CustomerCreditTermDTO = {
        id: existing?.id,
        customerId: selected.id,
        creditAllowed: termForm.creditAllowed,
        creditLimit: termForm.creditAllowed ? termForm.creditLimit : 0,
        creditDays: termForm.creditAllowed ? termForm.creditDays : 0
      };
      if (payload.id) await creditTermService.update(payload); else await creditTermService.create(payload);
      await refreshAll();
      Swal.fire({ icon: 'success', title: 'Credit terms saved', toast: true, timer: 1200, position: 'top-end', showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save terms', 'error');
    } finally {
      setSaving(false);
    }
  };

  const saveControl = async () => {
    if (!selected) return;
    if (controlForm.creditHold && !controlForm.creditHoldReason.trim()) return Swal.fire('Validation', 'Hold reason required', 'warning');
    if (controlForm.blacklisted && !controlForm.blacklistReason.trim()) return Swal.fire('Validation', 'Blacklist reason required', 'warning');
    setSaving(true);
    try {
      await customerService.update(selected.id, {
        name: selected.name,
        phone: selected.phone,
        address: selected.address,
        creditHold: controlForm.creditHold,
        creditHoldReason: controlForm.creditHold ? controlForm.creditHoldReason.trim() : '',
        blacklisted: controlForm.blacklisted,
        blacklistReason: controlForm.blacklisted ? controlForm.blacklistReason.trim() : ''
      });
      await refreshAll();
      Swal.fire({ icon: 'success', title: 'Customer control updated', toast: true, timer: 1200, position: 'top-end', showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save controls', 'error');
    } finally {
      setSaving(false);
    }
  };

  const collectPayment = async () => {
    if (!selected) return;
    const staffId = currentStaffId();
    if (staffId <= 0) return Swal.fire('Validation', 'Staff session is required for allocation', 'warning');
    const normalizedPaymentFormPayments = normalizePayments(paymentForm.payments || []);
    const amount = normalizedPaymentFormPayments.length > 0 ? paymentTotal(paymentForm.payments || []) : Number(paymentForm.amount);
    const methodId = normalizedPaymentFormPayments[0]?.paymentMethodId || paymentForm.paymentMethodId;
    if (!methodId) return Swal.fire('Validation', 'Select payment method', 'warning');
    if (!amount || amount <= 0) return Swal.fire('Validation', 'Amount must be greater than zero', 'warning');
    if (normalizedPaymentFormPayments.length > 1) {
      return Swal.fire('Validation', 'Multi-invoice allocation uses a single payment method. Use one method, or collect a single invoice with split pay.', 'warning');
    }

    const manualAllocations = Object.entries(allocAmounts)
      .map(([saleId, amt]) => ({ saleId: Number(saleId), amount: Number(amt) || 0 }))
      .filter((item) => item.saleId > 0 && item.amount > 0);
    if (!paymentForm.allocateFifo && manualAllocations.length === 0 && paymentForm.saleId > 0) {
      manualAllocations.push({ saleId: paymentForm.saleId, amount });
    }
    if (!paymentForm.allocateFifo && manualAllocations.length === 0) {
      return Swal.fire('Validation', 'Enter invoice amounts or enable FIFO allocation', 'warning');
    }
    if (!paymentForm.allocateFifo && manualAllocations.reduce((sum, item) => sum + item.amount, 0) > amount) {
      return Swal.fire('Validation', 'Allocated amount exceeds payment', 'warning');
    }

    setSaving(true);
    try {
      const result = await customerPaymentService.allocate({
        customerId: selected.id,
        paymentMethodId: methodId,
        staffId,
        amount,
        transactionNo: paymentForm.transactionNo || undefined,
        remark: paymentForm.note || undefined,
        allocations: paymentForm.allocateFifo ? undefined : manualAllocations
      });
      setPaymentForm((p) => ({ ...DEFAULT_PAYMENT, paymentMethodId: p.paymentMethodId, allocateFifo: true }));
      await Promise.all([refreshAll(), loadFinance(selected.id)]);
      Swal.fire({
        icon: 'success',
        title: 'Payment allocated',
        html: `<b>${result.paymentNo || ''}</b><br/>Allocated: ${money(Number(result.allocatedAmount) || 0)}<br/>Advance: ${money(Number(result.advanceAmount) || 0)}`
      });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to collect payment', 'error');
    } finally {
      setSaving(false);
    }
  };

  const applyCustomerCredit = async () => {
    if (!selected) return;
    const staffId = currentStaffId();
    if (staffId <= 0) return Swal.fire('Validation', 'Staff session is required', 'warning');
    const saleId = applySaleId || paymentForm.saleId;
    const amount = Number(applyAmount);
    if (!saleId) return Swal.fire('Validation', 'Select invoice to apply credit', 'warning');
    if (!amount || amount <= 0) return Swal.fire('Validation', 'Credit amount must be greater than zero', 'warning');
    setSaving(true);
    try {
      const result = await customerPaymentService.applyCredit({
        customerId: selected.id,
        saleId,
        staffId,
        amount,
        reason: applyReason.trim() || undefined
      });
      setApplyAmount('');
      setApplyReason('');
      await Promise.all([refreshAll(), loadFinance(selected.id)]);
      Swal.fire({
        icon: 'success',
        title: 'Credit applied',
        html: `<b>${result.applicationNo || ''}</b><br/>Applied: ${money(Number(result.amount) || 0)}<br/>Remaining due: ${money(Number(result.remainingDue) || 0)}`
      });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to apply credit', 'error');
    } finally {
      setSaving(false);
    }
  };

  const voidCustomerPayment = async (payment: CustomerPaymentDTO) => {
    if (!payment.id || payment.voided) return;
    const staffId = currentStaffId();
    if (staffId <= 0) return Swal.fire('Validation', 'Staff session is required', 'warning');
    const result = await Swal.fire({
      icon: 'warning',
      title: 'Void customer payment?',
      html: `<b>${payment.paymentNo || payment.id}</b><br/>Allocated: ${money(Number(payment.allocatedAmount) || Number(payment.amount) || 0)}`,
      input: 'textarea',
      inputLabel: 'Void reason',
      inputPlaceholder: 'Reason is required',
      showCancelButton: true,
      confirmButtonText: 'Void',
      confirmButtonColor: '#dc2626',
      inputValidator: (value) => value?.trim() ? undefined : 'Void reason is required'
    });
    if (!result.isConfirmed) return;
    setSaving(true);
    try {
      await customerPaymentService.voidPayment(payment.id, String(result.value).trim(), staffId);
      if (selected) await Promise.all([refreshAll(), loadFinance(selected.id)]);
      Swal.fire({ icon: 'success', title: 'Payment voided', timer: 1400, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to void payment', 'error');
    } finally {
      setSaving(false);
    }
  };

  const resolveAlert = async (alertId?: number) => {
    if (!alertId) return;
    try {
      await creditAlertService.resolve(alertId);
      setAlerts((prev) => prev.map((a) => (a.id === alertId ? { ...a, resolved: true } : a)));
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to resolve alert', 'error');
    }
  };

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="flex flex-wrap justify-between items-start gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Credit Operations Desk</h1>
          <p className="text-sm text-slate-500 mt-1">Real workflow: portfolio, controls, invoice collection, and alert monitoring.</p>
        </div>
        <button onClick={loadAll} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
        <div className="rounded-xl border border-rose-100 bg-rose-50 p-4"><p className="text-xs text-slate-500 uppercase">Outstanding</p><p className="text-2xl font-bold text-rose-700 mt-1">{money(summary.totalDue)}</p></div>
        <div className="rounded-xl border border-amber-100 bg-amber-50 p-4"><p className="text-xs text-slate-500 uppercase">Overdue Invoices</p><p className="text-2xl font-bold text-amber-700 mt-1">{summary.overdue}</p></div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs text-slate-500 uppercase">Credit Hold</p><p className="text-2xl font-bold text-slate-700 mt-1">{summary.hold}</p></div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4"><p className="text-xs text-slate-500 uppercase">Blacklist</p><p className="text-2xl font-bold text-slate-700 mt-1">{summary.blacklist}</p></div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="flex border-b border-slate-200">
          <button onClick={() => setTab('portfolio')} className={`flex-1 py-3 text-sm font-semibold ${tab === 'portfolio' ? 'text-indigo-700 border-b-2 border-indigo-600 bg-white' : 'text-slate-500 bg-slate-50'}`}>Portfolio</button>
          <button onClick={() => setTab('alerts')} className={`flex-1 py-3 text-sm font-semibold ${tab === 'alerts' ? 'text-indigo-700 border-b-2 border-indigo-600 bg-white' : 'text-slate-500 bg-slate-50'}`}>Alerts</button>
        </div>

        {tab === 'portfolio' ? (
          <div className="p-4 grid grid-cols-1 xl:grid-cols-12 gap-4">
            <div className="xl:col-span-5 space-y-3">
              <div className="relative"><Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search customer" className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" /></div>
              <div className="border border-slate-200 rounded-lg max-h-[620px] overflow-auto">
                <table className="w-full min-w-[650px] text-sm">
                  <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 text-xs text-slate-500 uppercase"><tr><th className="px-3 py-2 text-left">Customer</th><th className="px-3 py-2 text-left">Status</th><th className="px-3 py-2 text-right">Due</th><th className="px-3 py-2 text-center">Overdue</th></tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredCustomers.map((c) => <tr key={c.id} onClick={() => setSelectedCustomerId(c.id)} className={`cursor-pointer ${selectedCustomerId === c.id ? 'bg-indigo-50' : 'hover:bg-slate-50'}`}><td className="px-3 py-2"><p className="font-semibold text-slate-800">{c.name}</p><p className="text-xs text-slate-500">{c.phone}</p></td><td className="px-3 py-2">{c.blacklisted ? <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-800 text-white">Blacklist</span> : c.creditHold ? <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-700">Hold</span> : <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700">{termByCustomer.get(c.id)?.creditAllowed ? 'Credit' : 'Cash'}</span>}</td><td className="px-3 py-2 text-right font-semibold">{money(dueByCustomer.get(c.id) || 0)}</td><td className="px-3 py-2 text-center">{overdueByCustomer.get(c.id) || 0}</td></tr>)}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="xl:col-span-7 space-y-4">
              {selected ? (
                <>
                  <div className="border border-slate-200 rounded-lg p-4">
                    <h3 className="text-lg font-bold text-slate-800">{selected.name}</h3>
                    <p className="text-xs text-slate-500">{selected.phone} | {selected.address}</p>
                    <p className="text-xs font-semibold text-indigo-700 mt-2">Available credit: {money(Number(creditSummary.availableCredit ?? creditSummary.advanceBalance ?? selected.advanceBalance) || 0)}</p>
                  </div>
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <div className="border border-slate-200 rounded-lg p-4 space-y-2"><h4 className="text-sm font-bold text-slate-800 inline-flex items-center gap-2"><Ban size={14} /> Account Controls</h4><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={controlForm.creditHold} onChange={(e) => setControlForm((p) => ({ ...p, creditHold: e.target.checked }))} />Credit Hold</label>{controlForm.creditHold && <textarea rows={2} value={controlForm.creditHoldReason} onChange={(e) => setControlForm((p) => ({ ...p, creditHoldReason: e.target.value }))} placeholder="Hold reason" className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" />}<label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={controlForm.blacklisted} onChange={(e) => setControlForm((p) => ({ ...p, blacklisted: e.target.checked }))} />Blacklist</label>{controlForm.blacklisted && <textarea rows={2} value={controlForm.blacklistReason} onChange={(e) => setControlForm((p) => ({ ...p, blacklistReason: e.target.value }))} placeholder="Blacklist reason" className="w-full px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" />}<button onClick={saveControl} disabled={saving} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 text-white text-xs font-semibold hover:bg-slate-900 disabled:opacity-60">{saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}Save Controls</button></div>
                    <div className="border border-slate-200 rounded-lg p-4 space-y-2"><h4 className="text-sm font-bold text-slate-800 inline-flex items-center gap-2"><CreditCard size={14} /> Credit Terms</h4><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={termForm.creditAllowed} onChange={(e) => setTermForm((p) => ({ ...p, creditAllowed: e.target.checked }))} />Credit Allowed</label><div className="grid grid-cols-2 gap-2"><input type="number" min="0" value={termForm.creditLimit} onChange={(e) => setTermForm((p) => ({ ...p, creditLimit: Number(e.target.value) || 0 }))} placeholder="Limit" className="px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg disabled:opacity-60" disabled={!termForm.creditAllowed} /><input type="number" min="0" value={termForm.creditDays} onChange={(e) => setTermForm((p) => ({ ...p, creditDays: Number(e.target.value) || 0 }))} placeholder="Days" className="px-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg disabled:opacity-60" disabled={!termForm.creditAllowed} /></div><button onClick={saveTerm} disabled={saving} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 disabled:opacity-60">{saving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}Save Terms</button></div>
                  </div>

                  <div className="border border-slate-200 rounded-lg p-4 space-y-2">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <h4 className="text-sm font-bold text-slate-800">Invoice Collection</h4>
                      <label className="inline-flex items-center gap-2 text-xs font-semibold text-slate-600">
                        <input type="checkbox" checked={paymentForm.allocateFifo} onChange={(e) => setPaymentForm((p) => ({ ...p, allocateFifo: e.target.checked }))} />
                        FIFO allocate (leftover → advance)
                      </label>
                    </div>
                    <div className="overflow-auto max-h-[220px] border border-slate-100 rounded">
                      <table className="w-full min-w-[620px] text-sm">
                        <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
                          <tr>
                            <th className="px-2 py-1 text-left">Invoice</th>
                            <th className="px-2 py-1 text-left">Due Date</th>
                            <th className="px-2 py-1 text-right">Due</th>
                            <th className="px-2 py-1 text-right">Allocate</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {(receivables.length ? receivables : dueSales.map((s) => ({ saleId: s.id || 0, saleCode: s.saleCode, dueDate: s.dueDate, dueAmount: Number(s.dueAmount) || 0 }))).map((row) => (
                            <tr key={row.saleId}>
                              <td className="px-2 py-1">{row.saleCode || row.saleId}</td>
                              <td className="px-2 py-1">{fmt(row.dueDate)}</td>
                              <td className="px-2 py-1 text-right font-semibold">{money(Number(row.dueAmount) || 0)}</td>
                              <td className="px-2 py-1 text-right">
                                <input
                                  type="number"
                                  min="0"
                                  step="0.01"
                                  disabled={paymentForm.allocateFifo}
                                  value={allocAmounts[row.saleId] || ''}
                                  onChange={(e) => {
                                    setAllocAmounts((prev) => ({ ...prev, [row.saleId]: e.target.value }));
                                    setPaymentForm((p) => ({ ...p, saleId: row.saleId, allocateFifo: false }));
                                    setApplySaleId(row.saleId);
                                  }}
                                  placeholder={paymentForm.allocateFifo ? 'FIFO' : '0'}
                                  className="w-28 px-2 py-1 text-right text-sm bg-white border border-slate-200 rounded disabled:bg-slate-50"
                                />
                              </td>
                            </tr>
                          ))}
                          {!receivables.length && !dueSales.length && <tr><td colSpan={4} className="px-2 py-4 text-center text-slate-400">No open invoices</td></tr>}
                        </tbody>
                      </table>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
                      <input value={paymentForm.amount} onChange={(e) => setPaymentForm((p) => ({ ...p, amount: e.target.value, payments: [] }))} type="number" min="0" step="0.01" placeholder="Amount received" className="px-2 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" />
                      <select value={paymentForm.paymentMethodId} onChange={(e) => setPaymentForm((p) => ({ ...p, paymentMethodId: Number(e.target.value) || 0 }))} className="px-2 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg"><option value={0}>Method</option>{methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}</select>
                      <input value={paymentForm.transactionNo} onChange={(e) => setPaymentForm((p) => ({ ...p, transactionNo: e.target.value }))} placeholder="Transaction no" className="px-2 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" />
                      <button onClick={collectPayment} disabled={saving} className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-emerald-600 text-white text-xs font-semibold hover:bg-emerald-700 disabled:opacity-60">{saving ? <Loader2 size={12} className="animate-spin" /> : <CheckCircle2 size={12} />}Allocate</button>
                    </div>
                    <SplitPaymentEditor
                      methods={methods}
                      payments={paymentForm.payments || []}
                      onChange={(next) => setPaymentForm((p) => ({ ...p, payments: next, amount: paymentTotal(next) > 0 ? String(paymentTotal(next).toFixed(2)) : p.amount }))}
                      label="Split Payment (single method for allocate)"
                    />
                    <textarea rows={2} value={paymentForm.note} onChange={(e) => setPaymentForm((p) => ({ ...p, note: e.target.value }))} placeholder="Payment note" className="w-full px-2 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg" />
                    <div className="rounded-lg border border-indigo-100 bg-indigo-50/60 p-3 space-y-2">
                      <h5 className="text-xs font-bold uppercase tracking-wide text-indigo-700">Apply existing credit</h5>
                      <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
                        <select value={applySaleId} onChange={(e) => setApplySaleId(Number(e.target.value) || 0)} className="px-2 py-2 text-sm bg-white border border-slate-200 rounded-lg">
                          <option value={0}>Invoice</option>
                          {(receivables.length ? receivables : dueSales.map((s) => ({ saleId: s.id || 0, saleCode: s.saleCode }))).map((row) => (
                            <option key={row.saleId} value={row.saleId}>{row.saleCode || row.saleId}</option>
                          ))}
                        </select>
                        <input type="number" min="0" step="0.01" value={applyAmount} onChange={(e) => setApplyAmount(e.target.value)} placeholder="Amount" className="px-2 py-2 text-sm bg-white border border-slate-200 rounded-lg" />
                        <input value={applyReason} onChange={(e) => setApplyReason(e.target.value)} placeholder="Reason" className="px-2 py-2 text-sm bg-white border border-slate-200 rounded-lg" />
                        <button onClick={applyCustomerCredit} disabled={saving || !(Number(creditSummary.availableCredit ?? creditSummary.advanceBalance) > 0)} className="inline-flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700 disabled:opacity-60">Apply Credit</button>
                      </div>
                    </div>
                  </div>

                  <div className="border border-slate-200 rounded-lg p-4">
                    <h4 className="text-sm font-bold text-slate-800 mb-2">Payment History</h4>
                    <div className="overflow-auto max-h-[220px]">
                      <table className="w-full min-w-[720px] text-sm">
                        <thead className="bg-slate-50 text-xs text-slate-500 uppercase">
                          <tr>
                            <th className="px-2 py-1 text-left">Date</th>
                            <th className="px-2 py-1 text-left">No</th>
                            <th className="px-2 py-1 text-left">Method</th>
                            <th className="px-2 py-1 text-right">Allocated</th>
                            <th className="px-2 py-1 text-right">Advance</th>
                            <th className="px-2 py-1 text-right">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {payments.map((p) => (
                            <tr key={p.id} className={p.voided ? 'opacity-50 bg-slate-50' : ''}>
                              <td className="px-2 py-1">{fmt(p.paymentDate)}</td>
                              <td className="px-2 py-1">{p.paymentNo || p.saleCode || '-'}</td>
                              <td className="px-2 py-1">{p.paymentMethodName || '-'}</td>
                              <td className="px-2 py-1 text-right font-semibold">{money(Number(p.allocatedAmount ?? p.amount) || 0)}</td>
                              <td className="px-2 py-1 text-right">{money(Number(p.advanceAmount) || 0)}</td>
                              <td className="px-2 py-1 text-right">
                                {!p.voided && (p.allocations?.length || Number(p.allocatedAmount) > 0) ? (
                                  <button onClick={() => voidCustomerPayment(p)} className="px-2 py-1 rounded border border-rose-200 text-rose-700 text-xs font-semibold hover:bg-rose-50">Void</button>
                                ) : p.voided ? <span className="text-[10px] font-bold text-rose-600">VOIDED</span> : '-'}
                              </td>
                            </tr>
                          ))}
                          {!payments.length && <tr><td colSpan={6} className="px-2 py-4 text-center text-slate-400">No payments</td></tr>}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </>
              ) : <div className="p-8 text-center text-slate-400">Select customer.</div>}
            </div>
          </div>
        ) : (
          <div className="p-4 space-y-3">
            <div className="flex flex-wrap gap-2">{(['All', 'Overdue', 'Due_Soon', 'Credit_Limit_Exceeded', 'Large_Credit_Sale'] as AlertFilter[]).map((f) => <button key={f} onClick={() => setAlertFilter(f)} className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${alertFilter === f ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>{f}</button>)}</div>
            <div className="border border-slate-200 rounded-lg max-h-[660px] overflow-auto">
              <table className="w-full min-w-[820px] text-sm"><thead className="sticky top-0 bg-slate-50 border-b border-slate-200 text-xs text-slate-500 uppercase"><tr><th className="px-3 py-2 text-left">Type</th><th className="px-3 py-2 text-left">Customer</th><th className="px-3 py-2 text-left">Invoice</th><th className="px-3 py-2 text-left">Date</th><th className="px-3 py-2 text-right">Action</th></tr></thead><tbody className="divide-y divide-slate-100">{filteredAlerts.map((a) => <tr key={a.id} className={a.resolved ? 'opacity-60 bg-slate-50' : ''}><td className="px-3 py-2">{normalizeAlertType(a.alertType)}</td><td className="px-3 py-2 font-semibold">{a.customerName || a.customerId}</td><td className="px-3 py-2">{a.saleCode || a.saleId || '-'}</td><td className="px-3 py-2">{fmt(a.alertDate)}</td><td className="px-3 py-2 text-right">{!a.resolved && <button onClick={() => resolveAlert(a.id)} className="px-2.5 py-1.5 rounded-md border border-emerald-600 text-emerald-700 text-xs font-semibold hover:bg-emerald-50">Resolve</button>}</td></tr>)}</tbody></table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CreditManagement;
