import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import {
  AlertTriangle,
  Ban,
  Bell,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock3,
  CreditCard,
  DollarSign,
  Loader2,
  Pencil,
  Plus,
  Search,
  ShieldAlert,
  Trash2,
  User,
  X
} from 'lucide-react';
import { customerService } from '../services/customerapiservice';
import { creditAlertService } from '../services/creditalertapiservice';
import { creditTermService } from '../services/credittermapiservice';
import { customerPaymentService } from '../services/customerpaymentapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { saleApiService } from '../services/saleapiservice';
import { useDataEvents } from '../hooks/useDataEvents';
import { CreditAlertDTO, CustomerCreditTermDTO, CustomerCreditTermHistoryDTO, CustomerDTO, CustomerPaymentDTO, PaymentMethodDTO, SaleDTO } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type ModalTab = 'basic' | 'credit' | 'payments' | 'history';
type StatusFilter = 'all' | 'normal' | 'hold' | 'blacklist' | 'hasDue' | 'overdue';

const DEFAULT_BASIC = { name: '', phone: '', address: '' };
const DEFAULT_CONTROL = {
  creditHold: false,
  creditHoldReason: '',
  blacklisted: false,
  blacklistReason: ''
};
const DEFAULT_TERM = {
  creditAllowed: false,
  creditLimit: 0,
  creditDays: 0
};
const DEFAULT_PAYMENT = { saleId: 0, amount: '', paymentMethodId: 0, transactionNo: '', note: '' };

const money = (value: number) => `${new Intl.NumberFormat('en-US').format(value || 0)} Ks`;

const fmtDate = (value?: string) => {
  if (!value) return '-';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString('en-GB', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const CustomerManagement: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);

  const [customers, setCustomers] = useState<CustomerDTO[]>([]);
  const [sales, setSales] = useState<SaleDTO[]>([]);
  const [terms, setTerms] = useState<CustomerCreditTermDTO[]>([]);
  const [alerts, setAlerts] = useState<CreditAlertDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [payments, setPayments] = useState<CustomerPaymentDTO[]>([]);

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [customerPage, setCustomerPage] = useState(0);
  const [customerPageSize, setCustomerPageSize] = useState(20);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<ModalTab>('basic');
  const [editingCustomer, setEditingCustomer] = useState<CustomerDTO | null>(null);
  const [history, setHistory] = useState<CustomerCreditTermHistoryDTO[]>([]);

  const [basicForm, setBasicForm] = useState(DEFAULT_BASIC);
  const [controlForm, setControlForm] = useState(DEFAULT_CONTROL);
  const [termForm, setTermForm] = useState(DEFAULT_TERM);
  const [paymentForm, setPaymentForm] = useState(DEFAULT_PAYMENT);

  const termByCustomer = useMemo(() => new Map(terms.map((t) => [t.customerId, t])), [terms]);
  const customerSaleCount = useMemo(() => {
    const map = new Map<number, number>();
    sales.forEach((sale) => {
      if (!sale.customerId) return;
      map.set(sale.customerId, (map.get(sale.customerId) || 0) + 1);
    });
    return map;
  }, [sales]);
  const dueByCustomer = useMemo(() => {
    const map = new Map<number, number>();
    sales.forEach((sale) => {
      const due = Number(sale.dueAmount) || 0;
      if (due > 0) map.set(sale.customerId, (map.get(sale.customerId) || 0) + due);
    });
    return map;
  }, [sales]);
  const overdueByCustomer = useMemo(() => {
    const map = new Map<number, number>();
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    sales.forEach((sale) => {
      const due = Number(sale.dueAmount) || 0;
      if (due <= 0 || !sale.dueDate) return;
      const dueDate = new Date(sale.dueDate);
      dueDate.setHours(0, 0, 0, 0);
      if (dueDate < today) map.set(sale.customerId, (map.get(sale.customerId) || 0) + 1);
    });
    return map;
  }, [sales]);
  const alertByCustomer = useMemo(() => {
    const map = new Map<number, number>();
    alerts.filter((a) => !a.resolved).forEach((alert) => {
      map.set(alert.customerId, (map.get(alert.customerId) || 0) + 1);
    });
    return map;
  }, [alerts]);

  const stats = useMemo(() => ({
    total: customers.length,
    normal: customers.filter((c) => !c.creditHold && !c.blacklisted).length,
    hold: customers.filter((c) => Boolean(c.creditHold)).length,
    blacklist: customers.filter((c) => Boolean(c.blacklisted)).length,
    due: Array.from(dueByCustomer.values()).reduce((sum, value) => sum + value, 0),
    overdue: Array.from(overdueByCustomer.values()).reduce((sum, value) => sum + value, 0),
    alerts: alerts.filter((a) => !a.resolved).length
  }), [alerts, customers, dueByCustomer, overdueByCustomer]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [customerRows, saleRows, termRows, alertRows, methodRows] = await Promise.all([
        customerService.getAll(),
        saleApiService.getAll(),
        creditTermService.getAll(),
        creditAlertService.getAllUnresolved(),
        paymentMethodService.getAllActive()
      ]);
      setCustomers(customerRows || []);
      setSales(saleRows || []);
      setTerms(termRows || []);
      setAlerts(alertRows || []);
      setPaymentMethods(methodRows || []);
    } catch (error: any) {
      Swal.fire('Error', error?.message || 'Failed to load customer data', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);
  useRefreshOnTabActivate(loadData);

  useDataEvents(['Customer', 'Sale', 'Customer Payment'], loadData);

  const filteredCustomers = useMemo(() => {
    const needle = searchTerm.trim().toLowerCase();
    return customers.filter((customer) => {
      const matchesSearch = !needle || [
      customer.name,
      customer.phone,
      customer.address
      ].filter(Boolean).join(' ').toLowerCase().includes(needle);

      const matchesStatus =
        statusFilter === 'all' ||
        (statusFilter === 'normal' && !customer.creditHold && !customer.blacklisted) ||
        (statusFilter === 'hold' && Boolean(customer.creditHold)) ||
        (statusFilter === 'blacklist' && Boolean(customer.blacklisted)) ||
        (statusFilter === 'hasDue' && (dueByCustomer.get(customer.id) || 0) > 0) ||
        (statusFilter === 'overdue' && (overdueByCustomer.get(customer.id) || 0) > 0);

      return matchesSearch && matchesStatus;
    });
  }, [customers, dueByCustomer, overdueByCustomer, searchTerm, statusFilter]);

  useEffect(() => {
    setCustomerPage(0);
  }, [searchTerm, statusFilter, customerPageSize]);

  const customerTotalPages = Math.max(1, Math.ceil(filteredCustomers.length / customerPageSize));
  const safeCustomerPage = Math.min(customerPage, customerTotalPages - 1);
  const pagedCustomers = useMemo(
    () => filteredCustomers.slice(safeCustomerPage * customerPageSize, (safeCustomerPage + 1) * customerPageSize),
    [customerPageSize, filteredCustomers, safeCustomerPage]
  );
  const customerPageItems = useMemo(() => {
    const pages: Array<number | 'ellipsis'> = [];
    for (let page = 0; page < customerTotalPages; page += 1) {
      const visible = page === 0 || page === customerTotalPages - 1 || Math.abs(page - safeCustomerPage) <= 2;
      if (visible) pages.push(page);
      else if (pages[pages.length - 1] !== 'ellipsis') pages.push('ellipsis');
    }
    return pages;
  }, [customerTotalPages, safeCustomerPage]);

  const statusMeta = (customer: CustomerDTO) => {
    if (customer.blacklisted) {
      return {
        label: 'Blacklist',
        className: 'bg-slate-900 text-white',
        icon: <ShieldAlert size={12} />
      };
    }
    if (customer.creditHold) {
      return {
        label: 'Credit Hold',
        className: 'bg-rose-100 text-rose-700',
        icon: <Ban size={12} />
      };
    }
    return {
      label: 'Normal',
      className: 'bg-emerald-100 text-emerald-700',
      icon: <CheckCircle2 size={12} />
    };
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingCustomer(null);
    setActiveTab('basic');
    setHistory([]);
    setBasicForm(DEFAULT_BASIC);
    setControlForm(DEFAULT_CONTROL);
    setTermForm(DEFAULT_TERM);
    setPaymentForm(DEFAULT_PAYMENT);
    setPayments([]);
  };

  const loadCustomerHistory = async (customerId: number) => {
    setHistoryLoading(true);
    try {
      const historyRows = await creditTermService.getHistoryByCustomer(customerId);
      setHistory(historyRows || []);
    } catch {
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  const openCreateModal = () => {
    setEditingCustomer(null);
    setBasicForm(DEFAULT_BASIC);
    setControlForm(DEFAULT_CONTROL);
    setTermForm(DEFAULT_TERM);
    setHistory([]);
    setPayments([]);
    setPaymentForm((prev) => ({ ...DEFAULT_PAYMENT, paymentMethodId: prev.paymentMethodId || paymentMethods[0]?.id || 0 }));
    setActiveTab('basic');
    setIsModalOpen(true);
  };

  const openEditModal = (customer: CustomerDTO) => {
    const currentTerm = termByCustomer.get(customer.id);

    setEditingCustomer(customer);
    setBasicForm({
      name: customer.name || '',
      phone: customer.phone || '',
      address: customer.address || ''
    });
    setControlForm({
      creditHold: Boolean(customer.creditHold),
      creditHoldReason: customer.creditHoldReason || '',
      blacklisted: Boolean(customer.blacklisted),
      blacklistReason: customer.blacklistReason || ''
    });
    setTermForm({
      creditAllowed: Boolean(currentTerm?.creditAllowed),
      creditLimit: Number(currentTerm?.creditLimit) || 0,
      creditDays: Number(currentTerm?.creditDays) || 0
    });
    setHistory([]);
    setPayments([]);
    setPaymentForm((prev) => ({ ...DEFAULT_PAYMENT, paymentMethodId: prev.paymentMethodId || paymentMethods[0]?.id || 0 }));
    setActiveTab('basic');
    setIsModalOpen(true);

    loadCustomerHistory(customer.id);
    customerPaymentService.getByCustomer(customer.id).then(setPayments).catch(() => setPayments([]));
  };

  const validateForm = () => {
    const name = basicForm.name.trim();
    const phone = basicForm.phone.trim();
    const address = basicForm.address.trim();

    if (!name || !phone || !address) {
      return 'Name, phone and address are required.';
    }
    if (controlForm.creditHold && !controlForm.creditHoldReason.trim()) {
      return 'Credit hold reason is required.';
    }
    if (controlForm.blacklisted && !controlForm.blacklistReason.trim()) {
      return 'Blacklist reason is required.';
    }
    if (termForm.creditAllowed) {
      if (termForm.creditLimit < 0) return 'Credit limit must be zero or greater.';
      if (termForm.creditDays < 0) return 'Credit days must be zero or greater.';
    }
    return '';
  };

  const saveTerm = async (customerId: number) => {
    const existing = termByCustomer.get(customerId);
    const payload: CustomerCreditTermDTO = {
      id: existing?.id,
      customerId,
      creditAllowed: termForm.creditAllowed,
      creditLimit: termForm.creditAllowed ? termForm.creditLimit : 0,
      creditDays: termForm.creditAllowed ? termForm.creditDays : 0
    };

    if (payload.id) {
      await creditTermService.update(payload);
      return;
    }

    await creditTermService.create(payload);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();

    const validationError = validateForm();
    if (validationError) {
      Swal.fire('Validation', validationError, 'warning');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        name: basicForm.name.trim(),
        phone: basicForm.phone.trim(),
        address: basicForm.address.trim(),
        creditHold: controlForm.creditHold,
        creditHoldReason: controlForm.creditHold ? controlForm.creditHoldReason.trim() : '',
        blacklisted: controlForm.blacklisted,
        blacklistReason: controlForm.blacklisted ? controlForm.blacklistReason.trim() : ''
      };

      if (editingCustomer) {
        await customerService.update(editingCustomer.id, payload);
        await saveTerm(editingCustomer.id);
      } else {
        const created = await customerService.create(payload);
        await saveTerm(created.id);
      }

      await loadData();
      closeModal();
      Swal.fire({
        icon: 'success',
        title: 'Customer saved',
        toast: true,
        position: 'top-end',
        timer: 1300,
        showConfirmButton: false
      });
    } catch (error: any) {
      Swal.fire('Error', error?.message || 'Failed to save customer', 'error');
    } finally {
      setSaving(false);
    }
  };

  const collectPayment = async () => {
    if (!editingCustomer) return;
    const amount = Number(paymentForm.amount);
    const invoice = sales.find((sale) => sale.id === paymentForm.saleId);
    const due = Number(invoice?.dueAmount) || 0;
    if (!paymentForm.saleId) {
      Swal.fire('Validation', 'Invoice ရွေးပါ', 'warning');
      return;
    }
    if (!paymentForm.paymentMethodId) {
      Swal.fire('Validation', 'Payment method ရွေးပါ', 'warning');
      return;
    }
    if (!amount || amount <= 0) {
      Swal.fire('Validation', 'Amount မှန်မှန်ထည့်ပါ', 'warning');
      return;
    }
    if (amount > due) {
      Swal.fire('Validation', 'Amount သည် invoice due ထက် မကျော်ရပါ', 'warning');
      return;
    }

    setSaving(true);
    try {
      await customerPaymentService.create({
        customerId: editingCustomer.id,
        saleId: paymentForm.saleId,
        amount,
        paymentMethodId: paymentForm.paymentMethodId,
        transactionNo: paymentForm.transactionNo || undefined,
        note: paymentForm.note || undefined
      } as CustomerPaymentDTO);
      setPaymentForm((prev) => ({ ...DEFAULT_PAYMENT, paymentMethodId: prev.paymentMethodId }));
      await Promise.all([
        loadData(),
        customerPaymentService.getByCustomer(editingCustomer.id).then(setPayments)
      ]);
      Swal.fire({ icon: 'success', title: 'Payment received', toast: true, position: 'top-end', timer: 1300, showConfirmButton: false });
    } catch (error: any) {
      Swal.fire('Error', error?.message || 'Payment failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (customer: CustomerDTO) => {
    const saleCount = customerSaleCount.get(customer.id) || 0;
    if (saleCount > 0) {
      Swal.fire('Blocked', 'This customer already has sale records, delete is disabled.', 'info');
      return;
    }

    const result = await Swal.fire({
      title: 'Delete customer?',
      text: `Customer: ${customer.name}`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Delete',
      confirmButtonColor: '#dc2626'
    });

    if (!result.isConfirmed) return;

    try {
      await customerService.delete(customer.id);
      await loadData();
      Swal.fire({
        icon: 'success',
        title: 'Customer deleted',
        toast: true,
        position: 'top-end',
        timer: 1300,
        showConfirmButton: false
      });
    } catch (error: any) {
      Swal.fire('Error', error?.message || 'Delete failed', 'error');
    }
  };

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <Loader2 size={28} className="animate-spin text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="flex flex-col gap-3 2xl:flex-row 2xl:items-center">
      <div className="grid flex-1 grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-7">
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <p className="text-xs text-slate-500 uppercase">စုစုပေါင်း</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{stats.total}</p>
        </div>
        <div className="rounded-xl border border-emerald-100 bg-emerald-50 p-4">
          <p className="text-xs text-slate-500 uppercase">ပုံမှန်</p>
          <p className="text-2xl font-bold text-emerald-700 mt-1">{stats.normal}</p>
        </div>
        <div className="rounded-xl border border-rose-100 bg-rose-50 p-4">
          <p className="text-xs text-slate-500 uppercase">အကြွေးပိတ်</p>
          <p className="text-2xl font-bold text-rose-700 mt-1">{stats.hold}</p>
        </div>
        <div className="rounded-xl border border-slate-300 bg-slate-100 p-4">
          <p className="text-xs text-slate-600 uppercase">ပိတ်ပင်</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{stats.blacklist}</p>
        </div>
        <div className="rounded-xl border border-rose-100 bg-rose-50 p-4">
          <p className="text-xs text-slate-500 uppercase">Outstanding</p>
          <p className="text-xl font-bold text-rose-700 mt-1">{money(stats.due)}</p>
        </div>
        <div className="rounded-xl border border-amber-100 bg-amber-50 p-4">
          <p className="text-xs text-slate-500 uppercase">Overdue</p>
          <p className="text-2xl font-bold text-amber-700 mt-1">{stats.overdue}</p>
        </div>
        <div className="rounded-xl border border-indigo-100 bg-indigo-50 p-4">
          <p className="text-xs text-slate-500 uppercase">Alerts</p>
          <p className="text-2xl font-bold text-indigo-700 mt-1">{stats.alerts}</p>
        </div>
      </div>
      <button
        onClick={openCreateModal}
        className="inline-flex w-full shrink-0 items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-700 sm:w-auto"
      >
        <Plus size={16} /> ဖောက်သည်အသစ်
      </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/60 flex flex-col xl:flex-row gap-3">
          <div className="relative max-w-md flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="အမည် သို့ ဖုန်းနံပါတ် ရှာပါ"
              className="w-full pl-9 pr-3 py-2 bg-white border border-slate-200 rounded-lg text-sm"
            />
          </div>
          <div className="flex flex-wrap gap-2">
            {[
              { key: 'all', label: 'All' },
              { key: 'normal', label: 'Normal' },
              { key: 'hold', label: 'Hold' },
              { key: 'blacklist', label: 'Blacklist' },
              { key: 'hasDue', label: 'Due' },
              { key: 'overdue', label: 'Overdue' },
            ].map((item) => (
              <button
                key={item.key}
                onClick={() => setStatusFilter(item.key as StatusFilter)}
                className={`px-3 py-2 rounded-lg border text-xs font-bold uppercase ${
                  statusFilter === item.key
                    ? 'bg-indigo-600 text-white border-indigo-600'
                    : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
        <div className="overflow-auto max-h-[70vh]">
          <table className="w-full min-w-[1280px] text-sm">
            <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 text-xs text-slate-500 uppercase">
              <tr>
                <th className="px-4 py-3 text-left">ID</th>
                <th className="px-4 py-3 text-left">အမည်</th>
                <th className="px-4 py-3 text-left">ဖုန်း</th>
                <th className="px-4 py-3 text-left">အခြေအနေ</th>
                <th className="px-4 py-3 text-right">Outstanding</th>
                <th className="px-4 py-3 text-center">Overdue</th>
                <th className="px-4 py-3 text-center">Alerts</th>
                <th className="px-4 py-3 text-center">ရောင်းအကြိမ်</th>
                <th className="px-4 py-3 text-right">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredCustomers.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-4 py-12 text-center text-slate-400">ဖောက်သည် ရှာမတွေ့ပါ</td>
                </tr>
              )}
              {pagedCustomers.map((customer) => {
                const status = statusMeta(customer);
                const saleCount = customerSaleCount.get(customer.id) || 0;
                const due = dueByCustomer.get(customer.id) || 0;
                const overdue = overdueByCustomer.get(customer.id) || 0;
                const alertCount = alertByCustomer.get(customer.id) || 0;
                const deleteDisabled = saleCount > 0;

                return (
                  <tr key={customer.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-600">#{customer.id}</td>
                    <td className="px-4 py-3">
                      <div>
                        <p className="font-semibold text-slate-800">{customer.name}</p>
                        <p className="text-xs text-slate-500">{customer.address}</p>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-slate-600">{customer.phone}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-semibold ${status.className}`}>
                        {status.icon}
                        {status.label}
                      </span>
                    </td>
                    <td className={`px-4 py-3 text-right font-semibold ${due > 0 ? 'text-rose-700' : 'text-slate-400'}`}>{money(due)}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-flex min-w-8 justify-center rounded-full px-2 py-1 text-xs font-bold ${overdue > 0 ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-500'}`}>
                        {overdue}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-flex min-w-8 justify-center rounded-full px-2 py-1 text-xs font-bold ${alertCount > 0 ? 'bg-indigo-100 text-indigo-800' : 'bg-slate-100 text-slate-500'}`}>
                        {alertCount}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center text-slate-700 font-semibold">{saleCount}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => openEditModal(customer)}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md border border-indigo-200 text-indigo-700 hover:bg-indigo-50"
                        >
                          <Pencil size={13} /> ကြည့်/ပြင်
                        </button>
                        <button
                          onClick={() => handleDelete(customer)}
                          disabled={deleteDisabled}
                          title={deleteDisabled ? 'Disable: customer has sales' : 'Delete customer'}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md border border-rose-200 text-rose-700 hover:bg-rose-50 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          <Trash2 size={13} /> ဖျက်
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {filteredCustomers.length > 0 && (
          <div className="flex flex-col items-center justify-between gap-3 border-t border-slate-100 bg-white px-4 py-3 sm:flex-row">
            <p className="text-xs font-medium text-slate-500">
              Showing {safeCustomerPage * customerPageSize + 1}–{Math.min((safeCustomerPage + 1) * customerPageSize, filteredCustomers.length)} of {filteredCustomers.length}
            </p>
            <div className="flex flex-wrap items-center justify-center gap-2">
              <select
                value={customerPageSize}
                onChange={(e) => setCustomerPageSize(Number(e.target.value))}
                className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-semibold text-slate-600 outline-none focus:border-indigo-400"
              >
                <option value={10}>Show 10</option>
                <option value={20}>Show 20</option>
                <option value={50}>Show 50</option>
              </select>
              <button type="button" disabled={safeCustomerPage === 0} onClick={() => setCustomerPage((page) => Math.max(0, page - 1))} className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronLeft size={16} /></button>
              {customerPageItems.map((item, index) => item === 'ellipsis' ? (
                <span key={`ellipsis-${index}`} className="px-1 text-sm text-slate-400">…</span>
              ) : (
                <button key={item} type="button" onClick={() => setCustomerPage(item)} className={`inline-flex h-8 min-w-8 items-center justify-center rounded-lg px-2 text-xs font-bold ${item === safeCustomerPage ? 'bg-indigo-600 text-white' : 'border border-slate-200 text-slate-600 hover:bg-slate-50'}`}>{item + 1}</button>
              ))}
              <button type="button" disabled={safeCustomerPage >= customerTotalPages - 1} onClick={() => setCustomerPage((page) => Math.min(customerTotalPages - 1, page + 1))} className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"><ChevronRight size={16} /></button>
            </div>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/55">
          <div className="w-full max-w-5xl bg-white rounded-2xl border border-slate-200 shadow-xl overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-800">{editingCustomer ? `ဖောက်သည် #${editingCustomer.id}` : 'ဖောက်သည်အသစ်'}</h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  {editingCustomer ? 'ပရိုဖိုင်၊ အကြွေးထိန်းချုပ်မှု ပြင်ဆင်ရန်' : 'ဖောက်သည်ပရိုဖိုင် ဖန်တီးရန်'}
                </p>
              </div>
              <button onClick={closeModal} className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100">
                <X size={16} />
              </button>
            </div>

            <div className="border-b border-slate-200 flex">
              <button
                onClick={() => setActiveTab('basic')}
                className={`px-4 py-2.5 text-sm font-semibold ${activeTab === 'basic' ? 'text-indigo-700 border-b-2 border-indigo-600' : 'text-slate-500'}`}
              >
                အခြေခံ
              </button>
              <button
                onClick={() => setActiveTab('credit')}
                className={`px-4 py-2.5 text-sm font-semibold ${activeTab === 'credit' ? 'text-indigo-700 border-b-2 border-indigo-600' : 'text-slate-500'}`}
              >
                အကြွေး
              </button>
              <button
                onClick={() => setActiveTab('payments')}
                disabled={!editingCustomer}
                className={`px-4 py-2.5 text-sm font-semibold ${activeTab === 'payments' ? 'text-indigo-700 border-b-2 border-indigo-600' : 'text-slate-500'} disabled:opacity-40 disabled:cursor-not-allowed`}
              >
                Payments
              </button>
              <button
                onClick={() => setActiveTab('history')}
                disabled={!editingCustomer}
                className={`px-4 py-2.5 text-sm font-semibold ${activeTab === 'history' ? 'text-indigo-700 border-b-2 border-indigo-600' : 'text-slate-500'} disabled:opacity-40 disabled:cursor-not-allowed`}
              >
                မှတ်တမ်း
              </button>
            </div>

            <form onSubmit={handleSave} className="p-5 space-y-4 max-h-[72vh] overflow-auto">
              {activeTab === 'basic' && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-600 mb-1">အမည်</label>
                    <input
                      value={basicForm.name}
                      onChange={(e) => setBasicForm((prev) => ({ ...prev, name: e.target.value }))}
                      className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                      placeholder="ဖောက်သည်အမည်"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-600 mb-1">ဖုန်းနံပါတ်</label>
                    <input
                      value={basicForm.phone}
                      onChange={(e) => setBasicForm((prev) => ({ ...prev, phone: e.target.value }))}
                      className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                      placeholder="09xxxxxxxxx"
                    />
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-xs font-semibold text-slate-600 mb-1">လိပ်စာ</label>
                    <textarea
                      rows={3}
                      value={basicForm.address}
                      onChange={(e) => setBasicForm((prev) => ({ ...prev, address: e.target.value }))}
                      className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                      placeholder="ဖောက်သည်လိပ်စာ"
                    />
                  </div>
                </div>
              )}

              {activeTab === 'credit' && (
                <div className="space-y-5">
                  <div className="rounded-lg border border-slate-200 p-4 space-y-3">
                    <h4 className="text-sm font-bold text-slate-800">အကောင့်ထိန်းချုပ်မှု</h4>
                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={controlForm.creditHold}
                        onChange={(e) => setControlForm((prev) => ({ ...prev, creditHold: e.target.checked }))}
                      />
                      အကြွေးပိတ်ထားမည်
                    </label>
                    {controlForm.creditHold && (
                      <textarea
                        rows={2}
                        value={controlForm.creditHoldReason}
                        onChange={(e) => setControlForm((prev) => ({ ...prev, creditHoldReason: e.target.value }))}
                        className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                        placeholder="Reason for hold"
                      />
                    )}

                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={controlForm.blacklisted}
                        onChange={(e) => setControlForm((prev) => ({ ...prev, blacklisted: e.target.checked }))}
                      />
                      ပိတ်ပင်ထားမည်
                    </label>
                    {controlForm.blacklisted && (
                      <textarea
                        rows={2}
                        value={controlForm.blacklistReason}
                        onChange={(e) => setControlForm((prev) => ({ ...prev, blacklistReason: e.target.value }))}
                        className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                        placeholder="Reason for blacklist"
                      />
                    )}
                  </div>

                  <div className="rounded-lg border border-slate-200 p-4 space-y-3">
                    <h4 className="text-sm font-bold text-slate-800">အကြွေးသတ်မှတ်ချက်</h4>
                    <label className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        checked={termForm.creditAllowed}
                        onChange={(e) => setTermForm((prev) => ({ ...prev, creditAllowed: e.target.checked }))}
                      />
                      အကြွေးခွင့်ပြု
                    </label>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-semibold text-slate-600 mb-1">အကြွေးကန့်သတ်</label>
                        <input
                          type="number"
                          min="0"
                          value={termForm.creditLimit}
                          disabled={!termForm.creditAllowed}
                          onChange={(e) => setTermForm((prev) => ({ ...prev, creditLimit: Number(e.target.value) || 0 }))}
                          className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm disabled:opacity-60"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-semibold text-slate-600 mb-1">အကြွေးရက်</label>
                        <input
                          type="number"
                          min="0"
                          value={termForm.creditDays}
                          disabled={!termForm.creditAllowed}
                          onChange={(e) => setTermForm((prev) => ({ ...prev, creditDays: Number(e.target.value) || 0 }))}
                          className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm disabled:opacity-60"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'payments' && editingCustomer && (() => {
                const customerDueSales = sales.filter((sale) => sale.customerId === editingCustomer.id && (Number(sale.dueAmount) || 0) > 0);
                const selectedSale = customerDueSales.find((sale) => sale.id === paymentForm.saleId);
                const selectedDue = Number(selectedSale?.dueAmount) || 0;
                const customerAlerts = alerts.filter((alert) => alert.customerId === editingCustomer.id);

                return (
                  <div className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                      <div className="rounded-lg border border-rose-100 bg-rose-50 p-3">
                        <p className="text-xs font-semibold text-rose-600 uppercase">Outstanding</p>
                        <p className="mt-1 text-lg font-bold text-rose-800">{money(dueByCustomer.get(editingCustomer.id) || 0)}</p>
                      </div>
                      <div className="rounded-lg border border-amber-100 bg-amber-50 p-3">
                        <p className="text-xs font-semibold text-amber-600 uppercase">Overdue invoices</p>
                        <p className="mt-1 text-lg font-bold text-amber-800">{overdueByCustomer.get(editingCustomer.id) || 0}</p>
                      </div>
                      <div className="rounded-lg border border-indigo-100 bg-indigo-50 p-3">
                        <p className="text-xs font-semibold text-indigo-600 uppercase">Credit alerts</p>
                        <p className="mt-1 text-lg font-bold text-indigo-800">{customerAlerts.length}</p>
                      </div>
                      <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                        <p className="text-xs font-semibold text-slate-500 uppercase">Open invoices</p>
                        <p className="mt-1 text-lg font-bold text-slate-800">{customerDueSales.length}</p>
                      </div>
                    </div>

                    {customerAlerts.length > 0 && (
                      <div className="rounded-lg border border-indigo-100 bg-indigo-50 p-3 space-y-2">
                        {customerAlerts.slice(0, 3).map((alert) => (
                          <div key={alert.id} className="flex items-start gap-2 text-xs text-indigo-800">
                            <Bell size={14} className="mt-0.5 shrink-0" />
                            <span>{alert.message || alert.alertType || 'Credit alert'}</span>
                          </div>
                        ))}
                      </div>
                    )}

                    <div className="grid grid-cols-1 lg:grid-cols-[1.3fr_1fr] gap-4">
                      <div className="rounded-lg border border-slate-200 overflow-hidden">
                        <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
                          <h4 className="text-sm font-bold text-slate-800">Unpaid invoices</h4>
                          <span className="text-xs text-slate-500">{customerDueSales.length} open</span>
                        </div>
                        <div className="overflow-auto max-h-[320px]">
                          <table className="w-full min-w-[620px] text-xs">
                            <thead className="bg-white text-slate-500 uppercase">
                              <tr>
                                <th className="px-3 py-2 text-left">Invoice</th>
                                <th className="px-3 py-2 text-left">Due date</th>
                                <th className="px-3 py-2 text-right">Total</th>
                                <th className="px-3 py-2 text-right">Due</th>
                                <th className="px-3 py-2 text-right">Action</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                              {customerDueSales.length === 0 && (
                                <tr>
                                  <td colSpan={5} className="px-3 py-8 text-center text-slate-400">No unpaid invoices.</td>
                                </tr>
                              )}
                              {customerDueSales.map((sale) => (
                                <tr key={sale.id}>
                                  <td className="px-3 py-2 font-semibold text-slate-700">{sale.saleCode || `#${sale.id}`}</td>
                                  <td className="px-3 py-2 text-slate-600">{fmtDate(sale.dueDate || sale.saleDate)}</td>
                                  <td className="px-3 py-2 text-right text-slate-600">{money(Number(sale.netAmount ?? sale.totalAmount) || 0)}</td>
                                  <td className="px-3 py-2 text-right font-semibold text-rose-700">{money(Number(sale.dueAmount) || 0)}</td>
                                  <td className="px-3 py-2 text-right">
                                    <button
                                      type="button"
                                      onClick={() => setPaymentForm((prev) => ({ ...prev, saleId: sale.id || 0, amount: String(Number(sale.dueAmount) || 0) }))}
                                      className="px-2.5 py-1.5 rounded-md border border-indigo-200 text-indigo-700 hover:bg-indigo-50 font-semibold"
                                    >
                                      Select
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>

                      <div className="rounded-lg border border-slate-200 p-4 space-y-3">
                        <div className="flex items-center gap-2">
                          <CreditCard size={16} className="text-indigo-600" />
                          <h4 className="text-sm font-bold text-slate-800">Receive payment</h4>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">Invoice</label>
                          <select
                            value={paymentForm.saleId}
                            onChange={(e) => {
                              const saleId = Number(e.target.value) || 0;
                              const sale = customerDueSales.find((row) => row.id === saleId);
                              setPaymentForm((prev) => ({ ...prev, saleId, amount: sale ? String(Number(sale.dueAmount) || 0) : '' }));
                            }}
                            className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                          >
                            <option value={0}>Select invoice</option>
                            {customerDueSales.map((sale) => (
                              <option key={sale.id} value={sale.id}>{sale.saleCode || `#${sale.id}`} - {money(Number(sale.dueAmount) || 0)}</option>
                            ))}
                          </select>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">Amount</label>
                            <input
                              type="number"
                              min="0"
                              max={selectedDue || undefined}
                              value={paymentForm.amount}
                              onChange={(e) => setPaymentForm((prev) => ({ ...prev, amount: e.target.value }))}
                              className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                            />
                          </div>
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">Method</label>
                            <select
                              value={paymentForm.paymentMethodId}
                              onChange={(e) => setPaymentForm((prev) => ({ ...prev, paymentMethodId: Number(e.target.value) || 0 }))}
                              className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                            >
                              <option value={0}>Select</option>
                              {paymentMethods.map((method) => (
                                <option key={method.id} value={method.id}>{method.name}</option>
                              ))}
                            </select>
                          </div>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">Transaction no</label>
                          <input
                            value={paymentForm.transactionNo}
                            onChange={(e) => setPaymentForm((prev) => ({ ...prev, transactionNo: e.target.value }))}
                            className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                          />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">Note</label>
                          <textarea
                            rows={2}
                            value={paymentForm.note}
                            onChange={(e) => setPaymentForm((prev) => ({ ...prev, note: e.target.value }))}
                            className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                          />
                        </div>
                        <button
                          type="button"
                          onClick={collectPayment}
                          disabled={saving}
                          className="w-full px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center justify-center gap-2"
                        >
                          {saving ? <Loader2 size={14} className="animate-spin" /> : <DollarSign size={14} />}
                          Collect payment
                        </button>
                      </div>
                    </div>

                    <div className="rounded-lg border border-slate-200 overflow-hidden">
                      <div className="px-4 py-3 border-b border-slate-200 bg-slate-50">
                        <h4 className="text-sm font-bold text-slate-800">Payment history</h4>
                      </div>
                      <div className="overflow-auto max-h-[240px]">
                        <table className="w-full min-w-[680px] text-xs">
                          <thead className="bg-white text-slate-500 uppercase">
                            <tr>
                              <th className="px-3 py-2 text-left">Date</th>
                              <th className="px-3 py-2 text-left">Invoice</th>
                              <th className="px-3 py-2 text-left">Method</th>
                              <th className="px-3 py-2 text-left">Reference</th>
                              <th className="px-3 py-2 text-right">Amount</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            {payments.length === 0 && (
                              <tr>
                                <td colSpan={5} className="px-3 py-8 text-center text-slate-400">No payments yet.</td>
                              </tr>
                            )}
                            {payments.map((payment) => (
                              <tr key={payment.id}>
                                <td className="px-3 py-2 text-slate-600">{fmtDate(payment.paymentDate)}</td>
                                <td className="px-3 py-2 text-slate-700 font-semibold">{payment.saleCode || (payment.saleId ? `#${payment.saleId}` : '-')}</td>
                                <td className="px-3 py-2 text-slate-600">{payment.paymentMethodName || '-'}</td>
                                <td className="px-3 py-2 text-slate-600">{payment.transactionNo || payment.note || '-'}</td>
                                <td className="px-3 py-2 text-right font-semibold text-emerald-700">{money(Number(payment.amount) || 0)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>
                );
              })()}

              {activeTab === 'history' && (
                <div className="rounded-lg border border-slate-200 overflow-hidden">
                  <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
                    <h4 className="text-sm font-bold text-slate-800">အကြွေးပြောင်းလဲမှု မှတ်တမ်း</h4>
                    {historyLoading && <Loader2 size={14} className="animate-spin text-slate-500" />}
                  </div>
                  <div className="overflow-auto max-h-[420px]">
                    <table className="w-full min-w-[760px] text-xs">
                      <thead className="bg-slate-50 text-slate-500 uppercase">
                        <tr>
                          <th className="px-3 py-2 text-left">Changed At</th>
                          <th className="px-3 py-2 text-left">Credit Allowed</th>
                          <th className="px-3 py-2 text-left">Credit Days</th>
                          <th className="px-3 py-2 text-left">Credit Limit</th>
                          <th className="px-3 py-2 text-left">Changed By</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {!historyLoading && history.length === 0 && (
                          <tr>
                            <td colSpan={5} className="px-3 py-8 text-center text-slate-400">
                              No history records found.
                            </td>
                          </tr>
                        )}
                        {history.map((row, index) => (
                          <tr key={row.id || index}>
                            <td className="px-3 py-2 text-slate-600">{fmtDate(row.changedAt || row.createdAt)}</td>
                            <td className="px-3 py-2 text-slate-600">{String(row.oldCreditAllowed)} {'->'} {String(row.newCreditAllowed)}</td>
                            <td className="px-3 py-2 text-slate-600">{row.oldCreditDays ?? '-'} {'->'} {row.newCreditDays ?? '-'}</td>
                            <td className="px-3 py-2 text-slate-600">{row.oldCreditLimit ?? '-'} {'->'} {row.newCreditLimit ?? '-'}</td>
                            <td className="px-3 py-2 text-slate-600">{row.changedByStaffName || row.staffName || row.changedBy || '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              <div className="pt-2 flex items-center justify-between gap-3 border-t border-slate-100">
                <div className="inline-flex items-center gap-2 text-xs text-slate-500">
                  <Clock3 size={14} />
                  {editingCustomer ? 'Updates also refresh via /topic/customer websocket.' : 'Create new customer profile.'}
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={closeModal}
                    className="px-4 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50"
                  >
                    မလုပ်တော့
                  </button>
                  <button
                    type="submit"
                    disabled={saving}
                    className="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center gap-2"
                  >
                    {saving ? <Loader2 size={14} className="animate-spin" /> : <User size={14} />}
                    သိမ်းမည်
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 inline-flex items-center gap-2">
        <AlertTriangle size={14} />
        ရောင်းချမှု ရှိပြီးသား ဖောက်သည်ကို ဖျက်၍မရပါ။
      </div>
    </div>
  );
};

export default CustomerManagement;
