
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, CreditCard, Download, Eye, FileText, List, PackageCheck, Plus, RefreshCw, RotateCcw, Save, Search, Trash2, User, X } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import Swal from 'sweetalert2';
import { saleReturnApiService, saleReturnReasonApiService } from '../services/salereturnapiservice';
import { AppRoute, CustomerDTO, PaymentMethodDTO, PaymentTransactionDTO, ProductDTO, ProductStockHistoryMovementDTO, SaleDTO, SaleReturnDTO, SaleReturnDetailDTO, SaleReturnReasonDTO } from '../types';
import { saleApiService } from '../services/saleapiservice';
import { productService } from '../services/productapiservice';
import { customerService } from '../services/customerapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { useDataEvents } from '../hooks/useDataEvents';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';

type DetailForm = SaleReturnDetailDTO & { productSearch: string; serialNumbers: string[]; restock: boolean; reasonId?: number };

type SaleProductOption = {
  productId: number;
  productName: string;
  unitPrice: number;
  hasSerial: boolean;
  serialNumbers: string[];
};

const sanitizeSerial = (serial: string) => serial.trim().toUpperCase();
const normalizeSerial = (serial: string) => sanitizeSerial(serial).toLowerCase();

const ensureSerialCount = (serials: string[] | undefined, qty: number): string[] => {
  const safeQty = Math.max(0, qty || 0);
  const next = [...(serials || [])];
  if (next.length > safeQty) return next.slice(0, safeQty);
  if (next.length < safeQty) return [...next, ...Array(safeQty - next.length).fill('')];
  return next;
};

const emptyDetail = (): DetailForm => ({
  productId: 0,
  qty: 1,
  unitPrice: 0,
  subtotal: 0,
  productSearch: '',
  serialNumbers: [''],
  restock: true,
  reasonId: undefined
});

const toLocalDateTime = (value?: string) => {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const nowLocalDateTime = () => toLocalDateTime(new Date().toISOString());
const money = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);
const normalizePayments = (payments: PaymentTransactionDTO[]) =>
  payments
    .map((p) => ({
      ...p,
      paymentMethodId: Number(p.paymentMethodId) || 0,
      amount: Number(p.amount) || 0,
      transactionNo: p.transactionNo?.trim() || undefined
    }))
    .filter((p) => p.paymentMethodId > 0 && p.amount > 0);
const paymentTotal = (payments: PaymentTransactionDTO[]) => normalizePayments(payments).reduce((sum, p) => sum + (Number(p.amount) || 0), 0);
const netUnitPrice = (sale: SaleDTO, detail: { qty: number; unitPrice: number; subtotal: number }) => {
  const qty = Number(detail.qty) || 0;
  if (qty <= 0) return Number(detail.unitPrice) || 0;
  const lineNet = Number(detail.subtotal || 0);
  const allLineNet = (sale.details || []).reduce((sum, d) => sum + Number(d.subtotal || 0), 0);
  const overallDiscount = Math.max(0, Number(sale.discountAmount || 0));
  const allocatedOverallDiscount = allLineNet > 0 && overallDiscount > 0
    ? (lineNet * overallDiscount) / allLineNet
    : 0;
  const returnableNet = Math.max(0, lineNet - allocatedOverallDiscount);
  return Math.round((returnableNet / qty) * 100) / 100;
};

const SaleReturnManagement: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [rows, setRows] = useState<SaleReturnDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [masterLoading, setMasterLoading] = useState(true);
  const [saleLoading, setSaleLoading] = useState(false);
  const [loadError, setLoadError] = useState('');

  const [customers, setCustomers] = useState<CustomerDTO[]>([]);
  const [sales, setSales] = useState<SaleDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [reasons, setReasons] = useState<SaleReturnReasonDTO[]>([]);
  const [selectedSale, setSelectedSale] = useState<SaleDTO | null>(null);

  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [isRefundModalOpen, setIsRefundModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [viewRow, setViewRow] = useState<SaleReturnDTO | null>(null);
  const [returnStockMovements, setReturnStockMovements] = useState<ProductStockHistoryMovementDTO[]>([]);

  const [customerId, setCustomerId] = useState(0);
  const [customerSearch, setCustomerSearch] = useState('');
  const [saleId, setSaleId] = useState(0);
  const [saleSearch, setSaleSearch] = useState('');
  const [returnDate, setReturnDate] = useState(nowLocalDateTime());
  const [reason, setReason] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [refundPayments, setRefundPayments] = useState<PaymentTransactionDTO[]>([]);
  const [paymentMethodId, setPaymentMethodId] = useState(0);
  const [transactionNo, setTransactionNo] = useState('');
  const [details, setDetails] = useState<DetailForm[]>([emptyDetail()]);

  const productById = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);

  const customerLabel = useCallback((c?: CustomerDTO) => {
    if (!c) return '';
    const code = (c as any).code ? ` (${(c as any).code})` : '';
    return `${c.name}${code}`;
  }, []);

  const customerLabelById = useCallback((id?: number) => {
    if (!id) return '-';
    const c = customers.find((x) => x.id === id);
    return c ? customerLabel(c) : `Customer #${id}`;
  }, [customers, customerLabel]);

  const saleLabel = useCallback((s?: SaleDTO) => {
    if (!s) return '';
    return `${s.saleCode || `#${s.id}`} - ${s.customerName || `Customer #${s.customerId}`}`;
  }, []);

  const saleLabelById = useCallback((id?: number) => {
    if (!id) return '-';
    const s = sales.find((x) => x.id === id);
    return s ? saleLabel(s) : `#${id}`;
  }, [sales, saleLabel]);

  const loadRows = useCallback(async (page: number, size: number, search: string) => {
    setLoading(true);
    setLoadError('');
    try {
      const result = await saleReturnApiService.getAll(page, size, search);
      setRows(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
    } catch (e: any) {
      console.error('Failed to load sale returns', e);
      setRows([]);
      setLoadError(e?.message || 'Failed to load sale return vouchers.');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMaster = useCallback(async () => {
    setMasterLoading(true);
    try {
      const [cus, sal, pro, pm, rs] = await Promise.all([
        customerService.getAll(),
        saleApiService.getAll(),
        productService.getAll(),
        paymentMethodService.getAllActive(),
        saleReturnReasonApiService.getAll(true).catch(() => [])
      ]);
      setCustomers(cus);
      setSales(sal);
      setProducts(pro);
      setPaymentMethods(pm);
      setReasons(rs || []);
    } catch (e) {
      console.error('Failed to load master data', e);
    } finally {
      setMasterLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMaster();
  }, [loadMaster]);
  useRefreshOnTabActivate(loadMaster);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setCurrentPage(0);
      setDebouncedSearch(search.trim());
    }, 400);
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current); };
  }, [search]);

  useEffect(() => {
    void loadRows(currentPage, pageSize, debouncedSearch);
  }, [loadRows, currentPage, pageSize, debouncedSearch]);

  useDataEvents(['Return', 'Sale'], () => void loadRows(currentPage, pageSize, debouncedSearch));
  useEffect(() => {
    if (saleId <= 0) {
      setSelectedSale(null);
      return;
    }

    let active = true;
    setSaleLoading(true);

    saleApiService.getById(saleId)
      .then((data) => {
        if (!active) return;
        setSelectedSale(data);
        if (data.customerId) {
          setCustomerId(data.customerId);
          setCustomerSearch(customerLabelById(data.customerId));
        }
      })
      .catch((e) => {
        if (!active) return;
        console.error('Failed to load sale details', e);
        setSelectedSale(null);
      })
      .finally(() => {
        if (active) setSaleLoading(false);
      });

    return () => {
      active = false;
    };
  }, [saleId, customerLabelById]);

  const filteredSales = useMemo(() => {
    if (customerId <= 0) return [];
    return sales.filter((s) => s.customerId === customerId);
  }, [sales, customerId]);

  const productOptions = useMemo<SaleProductOption[]>(() => {
    if (!selectedSale?.details || selectedSale.details.length === 0) return [];

    const map = new Map<number, SaleProductOption>();
    selectedSale.details.forEach((detail) => {
      const serialNumbers = Array.from(
        new Set<string>((detail.serialNumbers || []).map((sn) => sanitizeSerial(sn)).filter(Boolean))
      );
      const detailProduct = productById.get(detail.productId);
      const hasSerial = detailProduct ? detailProduct.hasSerial !== false : serialNumbers.length > 0;
      const unitPrice = netUnitPrice(selectedSale, detail);

      const existing = map.get(detail.productId);
      if (!existing) {
        map.set(detail.productId, {
          productId: detail.productId,
          productName: detail.productName || `Product #${detail.productId}`,
          unitPrice,
          hasSerial,
          serialNumbers
        });
        return;
      }

      existing.serialNumbers = Array.from(new Set<string>([...existing.serialNumbers, ...serialNumbers]));
      if (existing.unitPrice <= 0 && unitPrice > 0) existing.unitPrice = unitPrice;
      else if (unitPrice > 0) existing.unitPrice = Math.round(((existing.unitPrice + unitPrice) / 2) * 100) / 100;
      if (!existing.hasSerial && hasSerial) existing.hasSerial = true;
    });

    return Array.from(map.values());
  }, [productById, selectedSale]);

  const productLabel = useCallback((p: SaleProductOption) => `${p.productName} (#${p.productId})`, []);

  const productOptionById = useCallback((productId: number) => {
    return productOptions.find((option) => option.productId === productId);
  }, [productOptions]);

  const getProductSerialPool = useCallback((productId: number) => {
    return productOptionById(productId)?.serialNumbers || [];
  }, [productOptionById]);

  const isSerialProduct = useCallback((productId: number) => {
    return Boolean(productOptionById(productId)?.hasSerial);
  }, [productOptionById]);

  const getSerialOptionsForRow = useCallback((rowIndex: number) => {
    const row = details[rowIndex];
    if (!row || row.productId <= 0 || !isSerialProduct(row.productId)) return [];

    const pool = getProductSerialPool(row.productId);
    const usedInOtherRows = new Set(
      details.flatMap((detail, index) => {
        if (index === rowIndex || !isSerialProduct(detail.productId)) return [];
        return detail.serialNumbers.map((serial) => normalizeSerial(serial)).filter(Boolean);
      })
    );

    return pool.filter((serial) => {
      const normalized = normalizeSerial(serial);
      const belongsToCurrentRow = row.serialNumbers.some((value) => normalizeSerial(value) === normalized);
      return belongsToCurrentRow || !usedInOtherRows.has(normalized);
    });
  }, [details, getProductSerialPool, isSerialProduct]);

  const resetForm = () => {
    setEditingId(null);
    setCustomerId(0);
    setCustomerSearch('');
    setSaleId(0);
    setSaleSearch('');
    setSelectedSale(null);
    setReturnDate(nowLocalDateTime());
    setReason('');
    setRefundAmount('');
    setRefundPayments([]);
    setPaymentMethodId(paymentMethods[0]?.id ?? 0);
    setTransactionNo('');
    setDetails([emptyDetail()]);
  };

  const total = useMemo(() => details.reduce((s, d) => s + d.subtotal, 0), [details]);
  const resolvedRefund = useMemo(() => refundAmount.trim() === '' ? total : parseFloat(refundAmount), [refundAmount, total]);
  const normalizedRefundPayments = useMemo(() => normalizePayments(refundPayments), [refundPayments]);
  const splitRefund = useMemo(() => paymentTotal(refundPayments), [refundPayments]);
  const effectiveRefund = normalizedRefundPayments.length > 0 ? splitRefund : resolvedRefund;
  const paymentRequired = !Number.isNaN(effectiveRefund) && effectiveRefund > 0;
  const validRefund = !Number.isNaN(effectiveRefund) && effectiveRefund >= 0 && effectiveRefund <= total;

  const serialValidation = useMemo(() => {
    const rowsWithProduct = details.filter((row) => row.productId > 0);
    const serialRows = rowsWithProduct.filter((row) => isSerialProduct(row.productId));
    const serials = serialRows.flatMap((row) => row.serialNumbers.map((sn) => sanitizeSerial(sn)).filter(Boolean));
    const unique = new Set(serials.map((sn) => sn.toLowerCase()));

    const qtyMatchesSerialCount = serialRows.every((row) => row.serialNumbers.length === row.qty);
    const allRowsHaveSerials = serialRows.every((row) => row.qty > 0 && row.serialNumbers.length > 0 && row.serialNumbers.every((sn) => sanitizeSerial(sn).length > 0));
    const strictCheckAvailable = serialRows.some((row) => getProductSerialPool(row.productId).length > 0);
    const belongsToSelectedProduct = serialRows.every((row) => {
      const pool = getProductSerialPool(row.productId);
      if (pool.length === 0) return true;
      const normalizedPool = new Set(pool.map((sn) => normalizeSerial(sn)));
      return row.serialNumbers.every((sn) => normalizedPool.has(normalizeSerial(sn)));
    });

    return {
      qtyMatchesSerialCount,
      allRowsHaveSerials,
      uniqueAcrossRows: unique.size === serials.length,
      belongsToSelectedProduct,
      strictCheckAvailable
    };
  }, [details, getProductSerialPool, isSerialProduct]);

  const validForm = customerId > 0
    && saleId > 0
    && details.length > 0
    && details.every((d) => d.productId > 0 && d.qty > 0 && d.unitPrice > 0)
    && serialValidation.qtyMatchesSerialCount
    && serialValidation.allRowsHaveSerials
    && serialValidation.uniqueAcrossRows
    && serialValidation.belongsToSelectedProduct
    && validRefund
    && (!paymentRequired || paymentMethodId > 0 || normalizedRefundPayments.length > 0);

  const onDetailChange = (index: number, field: 'qty' | 'unitPrice', value: string) => {
    setDetails((prev) => prev.map((d, i) => {
      if (i !== index) return d;
      const next = { ...d };
      if (field === 'qty') {
        next.qty = Math.max(0, parseInt(value, 10) || 0);
        next.serialNumbers = isSerialProduct(next.productId) ? ensureSerialCount(next.serialNumbers, next.qty) : [];
      }
      if (field === 'unitPrice') next.unitPrice = Math.max(0, parseFloat(value) || 0);
      next.subtotal = next.qty * next.unitPrice;
      return next;
    }));
  };

  const onProductSearch = (index: number, value: string) => {
    const match = productOptions.find((p) => productLabel(p).toLowerCase() === value.toLowerCase());
    setDetails((prev) => prev.map((d, i) => {
      if (i !== index) return d;
      const unitPrice = match ? (d.unitPrice > 0 ? d.unitPrice : match.unitPrice) : d.unitPrice;
      const serialNumbers = match?.hasSerial ? ensureSerialCount(d.serialNumbers, d.qty) : [];
      return { ...d, productSearch: value, productId: match?.productId || 0, unitPrice, serialNumbers, subtotal: d.qty * unitPrice };
    }));
  };

  const onSerialChange = (detailIndex: number, serialIndex: number, value: string) => {
    setDetails((prev) => prev.map((detail, rowIndex) => {
      if (rowIndex !== detailIndex) return detail;
      const serialNumbers = [...detail.serialNumbers];
      serialNumbers[serialIndex] = sanitizeSerial(value);
      return { ...detail, serialNumbers };
    }));
  };
  const onCustomerSearch = (value: string) => {
    setCustomerSearch(value);
    const match = customers.find((c) => customerLabel(c).toLowerCase() === value.toLowerCase());
    const nextCustomerId = match?.id || 0;

    if (nextCustomerId !== customerId) {
      setCustomerId(nextCustomerId);
      setSaleId(0);
      setSaleSearch('');
      setSelectedSale(null);
      setDetails([emptyDetail()]);
      return;
    }

    setCustomerId(nextCustomerId);
  };

  const onSaleSearch = (value: string) => {
    setSaleSearch(value);
    const match = filteredSales.find((s) => saleLabel(s).toLowerCase() === value.toLowerCase());
    const nextSaleId = match?.id || 0;

    if (nextSaleId !== saleId) {
      setSaleId(nextSaleId);
      setDetails([emptyDetail()]);
    }

    if (match && match.customerId !== customerId) {
      setCustomerId(match.customerId);
      setCustomerSearch(customerLabelById(match.customerId));
    }
  };

  const openCreate = () => {
    resetForm();
    setShowForm(true);
  };

  const openView = useCallback(async (id: number) => {
    try {
      const data = await saleReturnApiService.getById(id);
      setViewRow(data);
      setReturnStockMovements([]);
      const productIds = [...new Set((data.details || []).map((detail) => detail.productId))];
      const histories = await Promise.allSettled(productIds.map((productId) => productService.getStockHistory(productId, { size: 100 })));
      setReturnStockMovements(histories.flatMap((result) => result.status === 'fulfilled' ? result.value.movements || [] : []).filter((movement) => movement.referenceId === id));
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to load sale return', 'error');
    }
  }, []);

  const handleDelete = useCallback(async (id: number, label: string) => {
    const result = await Swal.fire({
      title: `${label} ကို ပယ်ဖျက်မည်?`,
      html: `Stock, journal နှင့် sale ပြင်ဆင်ချက်များကို <strong>ပြန်လည်ပြင်ဆင်မည်</strong>။ ပြန်မလုပ်နိုင်ပါ။`,
      icon: 'warning',
      input: 'textarea',
      inputLabel: 'ပယ်ဖျက်ရသည့်အကြောင်းရင်း',
      inputPlaceholder: 'အကြောင်းရင်း လိုအပ်သည်',
      showCancelButton: true,
      confirmButtonColor: '#dc2626',
      confirmButtonText: 'ပယ်ဖျက်မည်',
      cancelButtonText: 'မလုပ်တော့',
      inputValidator: (value) => value?.trim() ? undefined : 'အကြောင်းရင်း ဖြည့်ပါ'
    });
    if (!result.isConfirmed) return;
    try {
      await saleReturnApiService.voidReturn(id, String(result.value).trim());
      await loadRows(currentPage, pageSize, debouncedSearch);
      Swal.fire({ icon: 'success', title: 'Sale return ပယ်ဖျက်ပြီး', toast: true, showConfirmButton: false, timer: 1500, position: 'top-end' });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to void sale return', 'error');
    }
  }, [currentPage, pageSize, debouncedSearch, loadRows]);

  useEffect(() => {
    if (loading) return;

    const params = new URLSearchParams(location.search);
    const linkedReturnId = Number(params.get('saleReturnId'));
    if (!Number.isInteger(linkedReturnId) || linkedReturnId <= 0) return;

    openView(linkedReturnId).finally(() => {
      navigate({ pathname: location.pathname, search: '' }, { replace: true });
    });
  }, [loading, location.pathname, location.search, navigate, openView]);

  const onSave = async () => {
    if (saving) return;
    if (!validForm) {
      Swal.fire('Validation', 'Please select customer/sale, fill details, and provide valid serial numbers.', 'warning');
      return;
    }

    setSaving(true);
    try {
      const payload: SaleReturnDTO = {
        saleId,
        customerId: selectedSale?.customerId || customerId || undefined,
        staffId: selectedSale?.staffId || undefined,
        returnDate: returnDate || undefined,
        reason: reason.trim() || undefined,
        warehouseName: selectedSale?.warehouseName || 'Main',
        totalReturnAmount: total,
        refundAmount: normalizedRefundPayments.length > 0 ? effectiveRefund : (refundAmount.trim() === '' ? undefined : Number(refundAmount)),
        paymentMethodId: paymentRequired ? (normalizedRefundPayments[0]?.paymentMethodId || paymentMethodId) : undefined,
        payments: normalizedRefundPayments.length > 0 ? normalizedRefundPayments : undefined,
        transactionNo: transactionNo.trim() || undefined,
        details: details.map((d) => ({
          returnId: editingId || undefined,
          productId: Number(d.productId),
          qty: Number(d.qty),
          unitPrice: Number(d.unitPrice),
          subtotal: Number((d.qty * d.unitPrice).toFixed(2)),
          serialNumbers: isSerialProduct(d.productId) ? d.serialNumbers.map((sn) => sanitizeSerial(sn)).filter(Boolean) : [],
          reasonId: d.reasonId || reasons[0]?.id,
          restock: d.restock !== false
        }))
      };

      if (editingId) {
        await saleReturnApiService.update(editingId, payload);
      } else {
        await saleReturnApiService.create(payload);
      }

      setShowForm(false);
      resetForm();
      await loadRows(currentPage, pageSize, debouncedSearch);
      Swal.fire({
        icon: 'success',
        title: editingId ? 'Sale return updated' : 'Sale return created',
        toast: true,
        showConfirmButton: false,
        timer: 1500,
        position: 'top-end'
      });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to save sale return', 'error');
    } finally {
      setSaving(false);
    }
  };

  const filtered = useMemo(() => {
    const from = dateFrom ? new Date(dateFrom) : null;
    const to = dateTo ? new Date(dateTo) : null;
    if (to) to.setHours(23, 59, 59, 999);
    if (!from && !to) return rows;
    return rows.filter((r) => {
      if (!r.returnDate) return false;
      const d = new Date(r.returnDate);
      if (Number.isNaN(d.getTime())) return false;
      if (from && d < from) return false;
      if (to && d > to) return false;
      return true;
    });
  }, [dateFrom, dateTo, rows]);

  const stats = useMemo(() => ({
    count: totalElements,
    total: filtered.reduce((s, r) => s + (r.totalReturnAmount || 0), 0),
    refund: filtered.reduce((s, r) => s + (r.refundAmount ?? r.totalReturnAmount ?? 0), 0)
  }), [filtered, totalElements]);
  const visibleReturnRows = useMemo(() => filtered.filter((row): row is typeof row & { id: number } => typeof row.id === 'number'), [filtered]);
  const bulk = useBulkSelection<SaleReturnDTO & { id: number }>(visibleReturnRows);
  const handleBulkAction = (action: { key: string }) => {
    if (action.key !== 'export') return;
    const csv = [
      ['ID', 'Return Code', 'Date', 'Customer', 'Total', 'Refund'],
      ...bulk.selectedRows.map((row) => [row.id, row.returnCode || '', row.returnDate || '', row.customerName || '', row.totalReturnAmount || 0, row.refundAmount ?? row.totalReturnAmount ?? 0])
    ].map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `sale-returns-selected-${new Date().toISOString().slice(0, 10)}.csv`; link.click(); URL.revokeObjectURL(url);
    bulk.clear();
  };

  if (showForm) {
    return (
      <div className="w-full max-w-none space-y-6">
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:p-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-start gap-3">
            <button onClick={() => { setShowForm(false); resetForm(); }} className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50" title="နောက်သို့"><ArrowLeft size={16} /></button>
            <div className="min-w-0">
              <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Sale Return Voucher</p>
              <h2 className="mt-0.5 text-xl font-black text-slate-900">{editingId ? 'Sale Return ပြင်ရန်' : 'Sale Return အသစ်'}</h2>
            </div>
          </div>
          <button type="button" onClick={() => setIsRefundModalOpen(true)} className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 text-sm font-bold text-white hover:bg-indigo-700 lg:w-auto"><CreditCard size={16} /> ပြန်အမ်းငွေ ဖွင့်မည်</button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-6">
          <div className="space-y-5">
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
              <div className="flex items-center gap-2 border-b border-slate-100 pb-3"><FileText size={16} className="text-indigo-600" /><div><h3 className="text-sm font-bold text-slate-800">မူရင်းဘောင်ချာ အချက်အလက်</h3><p className="text-[11px] text-slate-500">Return လုပ်မည့် sale ကိုအရင်ရွေးပါ။</p></div></div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-6">
                <div className="space-y-1.5 xl:col-span-3">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ဖောက်သည်</label>
                  <input list="sr-customers" value={customerSearch} onChange={(e) => onCustomerSearch(e.target.value)} placeholder="ဖောက်သည် ရှာရန်..." className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${customerId > 0 ? 'border-slate-200' : 'border-rose-200'}`} />
                  <datalist id="sr-customers">{customers.map((c) => <option key={c.id} value={customerLabel(c)} />)}</datalist>
                </div>
                <div className="space-y-1.5 xl:col-span-3">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">မူရင်း Sale</label>
                  <input list="sr-sales" value={saleSearch} onChange={(e) => onSaleSearch(e.target.value)} placeholder={customerId > 0 ? 'မူရင်း sale ဘောင်ချာရွေးရန်...' : 'ဖောက်သည်အရင်ရွေးပါ'} disabled={customerId <= 0} className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed ${saleId > 0 ? 'border-slate-200' : 'border-rose-200'}`} />
                  <datalist id="sr-sales">{filteredSales.map((s) => <option key={s.id} value={saleLabel(s)} />)}</datalist>
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Return ရက်စွဲ</label>
                <input type="datetime-local" value={returnDate} onChange={(e) => setReturnDate(e.target.value)} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ပြန်လက်ခံရသည့်အကြောင်းရင်း</label>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="ဥပမာ - ပစ္စည်းမှားဝယ် / defect / exchange / customer refund" className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none" />
              </div>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-100 flex items-center justify-between">
                <h3 className="font-bold text-slate-800 text-sm">ပြန်လက်ခံမည့် ပစ္စည်းများ</h3>
                <button onClick={() => setDetails((d) => [...d, emptyDetail()])} className="flex items-center gap-2 px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700"><Plus size={14} /> Add Row</button>
              </div>
              <div className="overflow-auto">
                <table className="w-full text-left border-collapse min-w-[700px]">
                  <thead className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                    <tr>
                      <th className="px-4 py-3 border-b border-slate-100">ပစ္စည်း</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-24">အရေအတွက်</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-32">တစ်ခုဈေး</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-36">အကြောင်းရင်း</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-28">Restock</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-36 text-right">စုစုပေါင်း</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-12"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {details.map((d, i) => {
                      const serialRequired = isSerialProduct(d.productId);
                      const serialOptions = getSerialOptionsForRow(i);
                      return (
                        <React.Fragment key={i}>
                          <tr className="hover:bg-slate-50/60">
                            <td className="px-4 py-3">
                              <input list={`sr-products-${i}`} value={d.productSearch} onChange={(e) => onProductSearch(i, e.target.value)} placeholder={saleId > 0 ? 'ပြန်လက်ခံမည့် product ရွေးပါ...' : 'Sale အရင်ရွေးပါ'} disabled={saleId <= 0 || productOptions.length === 0} className={`w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed ${d.productId > 0 ? 'text-slate-700' : 'text-rose-500'}`} />
                              <datalist id={`sr-products-${i}`}>{productOptions.map((p) => <option key={p.productId} value={productLabel(p)} />)}</datalist>
                            </td>
                            <td className="px-4 py-3"><input type="number" min="1" value={d.qty || ''} onChange={(e) => onDetailChange(i, 'qty', e.target.value)} className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none" /></td>
                            <td className="px-4 py-3"><input type="number" min="0" step="0.01" value={d.unitPrice || ''} onChange={(e) => onDetailChange(i, 'unitPrice', e.target.value)} className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none" /></td>
                            <td className="px-4 py-3">
                              <select value={d.reasonId || 0} onChange={(e) => setDetails((prev) => prev.map((row, idx) => idx === i ? { ...row, reasonId: Number(e.target.value) || undefined } : row))} className="w-full rounded border border-slate-200 bg-white px-2 py-1 text-xs">
                                <option value={0}>Reason</option>
                                {reasons.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
                              </select>
                            </td>
                            <td className="px-4 py-3">
                              <label className="inline-flex items-center gap-1 text-xs"><input type="checkbox" checked={d.restock !== false} onChange={(e) => setDetails((prev) => prev.map((row, idx) => idx === i ? { ...row, restock: e.target.checked } : row))} /> Stock ပြန်တက်</label>
                            </td>
                            <td className="px-4 py-3 text-right font-bold text-slate-700">{money(d.subtotal)}</td>
                            <td className="px-4 py-3 text-center"><button onClick={() => setDetails((prev) => prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i))} className="p-1.5 text-slate-300 hover:text-rose-500 disabled:opacity-40" disabled={details.length <= 1}><Trash2 size={14} /></button></td>
                          </tr>
                          {d.productId > 0 && d.qty > 0 && (
                            <tr className="bg-slate-50/50">
                              <td colSpan={7} className="px-4 py-3">
                                {serialRequired ? (
                                  <div className="space-y-2">
                                    <div className="flex items-center justify-between text-[10px] text-slate-500"><span className="font-bold uppercase tracking-wider">Serial နံပါတ်များ ({d.qty})</span><span>{serialOptions.length} ရနိုင်</span></div>
                                    <div className="flex flex-wrap gap-2">
                                      {ensureSerialCount(d.serialNumbers, d.qty).map((serial, serialIndex) => (
                                        <input key={serialIndex} list={`sr-serial-options-${i}`} type="text" value={serial} onChange={(e) => onSerialChange(i, serialIndex, e.target.value)} placeholder={`Serial #${serialIndex + 1}`} className="px-2 py-1 bg-white border border-slate-200 rounded text-[11px] w-36 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500" />
                                      ))}
                                      <datalist id={`sr-serial-options-${i}`}>{serialOptions.map((serial) => <option key={serial} value={serial} />)}</datalist>
                                    </div>
                                  </div>
                                ) : <span className="inline-flex px-2.5 py-1 rounded text-[11px] font-semibold bg-slate-100 text-slate-700">Qty ဖြင့်ပြန်ဝင်</span>}
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
          </div>

          {isRefundModalOpen && (
            <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4" onMouseDown={() => setIsRefundModalOpen(false)}>
              <div className="h-[100dvh] w-full overflow-y-auto rounded-none bg-white shadow-xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-xl sm:rounded-2xl" onMouseDown={(e) => e.stopPropagation()}>
            <div className="min-h-full bg-white p-4 pb-8 space-y-5 sm:min-h-0 sm:rounded-2xl sm:p-6">
              <div className="sticky top-0 z-10 -mx-4 -mt-4 flex items-center justify-between gap-3 border-b border-slate-100 bg-white px-4 pt-4 pb-4 sm:static sm:mx-0 sm:mt-0 sm:p-0 sm:pb-0 sm:border-0">
                <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2"><RotateCcw size={16} className="text-indigo-500" /> Refund & စုစုပေါင်း</h3>
                <button type="button" onClick={() => setIsRefundModalOpen(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" title="ပိတ်မည်"><X size={18} /></button>
              </div>
              <div className="flex justify-between items-center text-sm pb-2 border-b border-slate-100"><span className="text-slate-500">Return စုစုပေါင်း</span><span className="font-bold text-slate-800">{money(total)}</span></div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ပြန်အမ်းငွေ</label>
                <input type="number" min="0" step="0.01" value={refundAmount} onChange={(e) => { setRefundAmount(e.target.value); if (refundPayments.length > 0) setRefundPayments([]); }} placeholder="အပြည့်ပြန်အမ်းလျှင် blank ထားနိုင်" className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${validRefund ? 'border-slate-200' : 'border-rose-200'}`} />
                <p className="text-[10px] text-slate-400">Blank ထားလျှင် return စုစုပေါင်းအတိုင်း refund လုပ်မည်။</p>
                {!Number.isNaN(effectiveRefund) && effectiveRefund > total && <p className="text-[10px] text-rose-500">Refund amount သည် return စုစုပေါင်းထက် မကျော်ရပါ။</p>}
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ငွေပြန်အမ်းနည်း</label>
                <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value))} className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${!paymentRequired || paymentMethodId > 0 ? 'border-slate-200' : 'border-rose-200'}`}>
                  <option value={0}>ငွေပြန်အမ်းနည်း ရွေးပါ</option>
                  {paymentMethods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                </select>
                <p className="text-[10px] text-slate-400">Refund amount ရှိမှသာလိုအပ်ပါသည်။</p>
              </div>

              <SplitPaymentEditor
                methods={paymentMethods}
                payments={refundPayments}
                onChange={(next) => {
                  setRefundPayments(next);
                  const totalPaid = paymentTotal(next);
                  setRefundAmount(totalPaid > 0 ? String(totalPaid.toFixed(2)) : '');
                }}
                label="Split Refund"
              />

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Transaction No / မှတ်တမ်းနံပါတ်</label>
                <input type="text" value={transactionNo} onChange={(e) => setTransactionNo(e.target.value)} placeholder="မဖြည့်လည်းရ" className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
              </div>

              {!serialValidation.qtyMatchesSerialCount && <p className="text-[10px] text-rose-500">Each serial-tracked row must contain exactly `qty` serial numbers.</p>}
              {serialValidation.qtyMatchesSerialCount && !serialValidation.allRowsHaveSerials && <p className="text-[10px] text-rose-500">Every serial number field is required for serial products.</p>}
              {!serialValidation.uniqueAcrossRows && <p className="text-[10px] text-rose-500">Duplicate serial numbers are not allowed.</p>}
              {!serialValidation.belongsToSelectedProduct && serialValidation.strictCheckAvailable && <p className="text-[10px] text-rose-500">Serial number must belong to selected sale and product.</p>}

              <button onClick={onSave} disabled={!validForm || saving} className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold transition-all ${validForm && !saving ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}>
                <Save size={16} /> {saving ? 'သိမ်းနေသည်...' : editingId ? 'Sale Return ပြင်မည်' : 'Sale Return သိမ်းမည်'}
              </button>
            </div>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-none space-y-6">
      <div className="flex flex-col gap-4 2xl:flex-row 2xl:items-center">
        <div className="flex w-full shrink-0 flex-col gap-2 sm:w-auto sm:flex-row sm:items-center 2xl:order-2">
          <button onClick={() => loadRows(currentPage, pageSize, debouncedSearch)} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> ပြန်ဖတ်ရန်</button>
          <button onClick={openCreate} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700"><Plus size={16} /> အရောင်းပြန်လက်ခံမှုအသစ်</button>
        </div>
      <div className="grid flex-1 grid-cols-1 gap-4 sm:grid-cols-3 2xl:order-1">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ဘောင်ချာအရေအတွက်</p><p className="text-2xl font-bold text-slate-800">{stats.count}</p></div><div className="w-11 h-11 rounded-lg bg-indigo-50 flex items-center justify-center"><List size={20} className="text-indigo-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Return စုစုပေါင်းed</p><p className="text-2xl font-bold text-slate-800">{money(stats.total)}</p></div><div className="w-11 h-11 rounded-lg bg-slate-100 flex items-center justify-center"><RotateCcw size={20} className="text-slate-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Refund စုစုပေါင်း</p><p className="text-2xl font-bold text-emerald-700">{money(stats.refund)}</p></div><div className="w-11 h-11 rounded-lg bg-emerald-50 flex items-center justify-center"><RotateCcw size={20} className="text-emerald-600" /></div></div>
      </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col lg:flex-row lg:items-center gap-4">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Return no / sale no / customer ဖြင့်ရှာရန်..." className="w-full pl-9 pr-9 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
            {loading && search && (
              <span className="absolute right-3 top-1/2 -translate-y-1/2">
                <svg className="animate-spin h-4 w-4 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>
              </span>
            )}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto_1fr_auto] gap-2 items-center">
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-600 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
            <span className="hidden sm:block text-slate-300 text-xs">-</span>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-600 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
            <button onClick={() => { setSearch(''); setDateFrom(''); setDateTo(''); }} className="px-3 py-1.5 text-xs font-semibold text-slate-500 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100">ရှင်းမည်</button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <h3 className="font-bold text-slate-800 text-sm">Sale Return ဘောင်ချာများ</h3>
          <div className="flex items-center gap-3">
            {loadError && <span className="text-[11px] text-rose-500">{loadError}</span>}
            {masterLoading && <span className="text-[11px] text-slate-400">Master data ဖတ်နေသည်...</span>}
          </div>
        </div>
        <div className="px-4 pt-3">
          <BulkSelectionToolbar visibleCount={visibleReturnRows.length} selectedCount={bulk.selectedCount} allVisibleSelected={bulk.allVisibleSelected} someVisibleSelected={bulk.someVisibleSelected} onToggleVisible={() => bulk.allVisibleSelected ? bulk.clear() : bulk.selectVisible()} onClear={bulk.clear} selectedRows={bulk.selectedRows} selectedTotal={bulk.selectedRows.reduce((sum, row) => sum + (Number(row.totalReturnAmount) || 0), 0)} totalLabel="Selected Total" actions={[{ key: 'export', label: 'Export selected', icon: <Download size={13} />, tone: 'indigo' }]} onAction={handleBulkAction} />
        </div>
        <div className="overflow-auto max-h-[65vh] custom-scrollbar">
          {loading ? <div className="p-8 text-center text-slate-400">Loading...</div> : (
            <table className="w-full text-left border-collapse min-w-[920px]">
              <thead className="sticky top-0 bg-white z-10 shadow-sm">
                <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                  <th className="px-3 py-3 border-b border-slate-100">Select</th>
                  <th className="px-4 py-3 border-b border-slate-100">Return No</th>
                  <th className="px-4 py-3 border-b border-slate-100">မူရင်း Sale</th>
                  <th className="px-4 py-3 border-b border-slate-100">ဖောက်သည်</th>
                  <th className="px-4 py-3 border-b border-slate-100">ရက်စွဲ</th>
                  <th className="px-4 py-3 border-b border-slate-100">အခြေအနေ</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">စုစုပေါင်း</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Refund</th>
                  <th className="px-4 py-3 border-b border-slate-100">ပြန်လက်ခံရသည့်အကြောင်းရင်း</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">လုပ်ဆောင်ချက်</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.length > 0 ? filtered.map((r) => (
                  <tr key={r.id || r.returnCode} className="hover:bg-slate-50 text-xs">
                    <td className="px-3 py-3 text-center"><input type="checkbox" checked={bulk.selectedIds.has(r.id as number)} onChange={() => bulk.toggle(r.id as number)} className="h-4 w-4 accent-indigo-600" aria-label={`Select sale return ${r.returnCode || r.id}`} /></td>
                    <td className="px-4 py-3 font-medium text-slate-800">{r.returnCode || `#${r.id}`}</td>
                    <td className="px-4 py-3 text-slate-600">{r.saleId > 0 ? <Link to={`${AppRoute.SALES}?saleId=${r.saleId}`} className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium">{saleLabelById(r.saleId)}</Link> : '-'}</td>
                    <td className="px-4 py-3 text-slate-600">{r.customerName || customerLabelById(sales.find((s) => s.id === r.saleId)?.customerId)}</td>
                    <td className="px-4 py-3 text-slate-600">{r.returnDate ? new Date(r.returnDate).toLocaleString() : '-'}</td>
                    <td className="px-4 py-3">{(r.status || '').toUpperCase() === 'VOIDED' || r.voidedAt ? <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-700">VOIDED</span> : <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700">{r.status || 'COMPLETED'}</span>}</td>
                    <td className="px-4 py-3 text-right font-bold text-slate-700">{money(r.totalReturnAmount || 0)}</td>
                    <td className="px-4 py-3 text-right font-bold text-emerald-700">{money(r.refundAmount ?? r.totalReturnAmount ?? 0)}</td>
                    <td className="px-4 py-3 text-slate-500 max-w-[260px] truncate">{r.reason || '-'}</td>
                    <td className="px-4 py-3 text-right flex items-center justify-end gap-1">
                      <button onClick={() => r.id && openView(r.id)} className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-indigo-50" title="View"><Eye size={15} /></button>
                      {r.id && (r.status || '').toUpperCase() !== 'VOIDED' && !r.voidedAt && (
                        <button onClick={() => handleDelete(r.id as number, r.returnCode || `#${r.id}`)} className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50" title="Void"><Trash2 size={15} /></button>
                      )}
                    </td>
                  </tr>
                )) : <tr><td colSpan={10} className="px-4 py-10 text-center text-slate-400">သတ်မှတ်ထားသော filter ဖြင့် Sale Return မတွေ့ပါ။</td></tr>}
              </tbody>
            </table>
          )}
        </div>

        {totalPages > 1 && (
          <div className="p-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
            <span className="text-[11px] text-slate-500">
              Showing {totalElements === 0 ? 0 : currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()}
            </span>
            <div className="flex items-center gap-1.5 flex-wrap">
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(0); }} className="text-xs px-2 py-1 border border-slate-200 rounded-lg bg-white">
                {[10, 20, 50, 100].map((n) => <option key={n} value={n}>Show {n}</option>)}
              </select>
              <button onClick={() => setCurrentPage((p) => Math.max(0, p - 1))} disabled={currentPage === 0} className="px-2 py-1 text-xs rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40">‹</button>
              {(() => {
                const delta = 2; const pages: (number | string)[] = []; let prev = -1;
                for (let i = 0; i < totalPages; i++) {
                  if (i === 0 || i === totalPages - 1 || (i >= currentPage - delta && i <= currentPage + delta)) {
                    if (prev !== -1 && i - prev > 1) pages.push('…');
                    pages.push(i); prev = i;
                  }
                }
                return pages.map((p, idx) => typeof p === 'string'
                  ? <span key={`e${idx}`} className="px-1 text-slate-400 text-xs">…</span>
                  : <button key={p} onClick={() => setCurrentPage(p as number)} className={`px-2.5 py-1 text-xs rounded-lg border ${currentPage === p ? 'bg-indigo-600 text-white border-indigo-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}>{(p as number) + 1}</button>
                );
              })()}
              <button onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))} disabled={currentPage >= totalPages - 1} className="px-2 py-1 text-xs rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40">›</button>
            </div>
          </div>
        )}
      </div>

      {viewRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-bold text-slate-800">Sale Return: {viewRow.returnCode || `#${viewRow.id}`}</h3>
              <button onClick={() => setViewRow(null)} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"><X size={18} /></button>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600"><span className="font-medium text-slate-500">Sale:</span> {viewRow.saleId > 0 ? <Link to={`${AppRoute.SALES}?saleId=${viewRow.saleId}`} className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium">{saleLabelById(viewRow.saleId)}</Link> : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Customer:</span> {viewRow.customerName || customerLabelById(sales.find((s) => s.id === viewRow.saleId)?.customerId)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Return Date:</span> {viewRow.returnDate ? new Date(viewRow.returnDate).toLocaleString() : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Return စုစုပေါင်း:</span> {money(viewRow.totalReturnAmount || 0)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Refund:</span> {money(viewRow.refundAmount ?? viewRow.totalReturnAmount ?? 0)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">အခြေအနေ:</span> {viewRow.status || 'COMPLETED'}{viewRow.creditNoteNo ? ` · CN ${viewRow.creditNoteNo}` : ''}</p>
              </div>
              {viewRow.voidReason && <p className="text-sm text-rose-600"><span className="font-medium">ပယ်ဖျက်:</span> {viewRow.voidReason}</p>}
              {viewRow.reason && <p className="text-sm text-slate-600"><span className="font-medium text-slate-500">Reason:</span> {viewRow.reason}</p>}
              <table className="w-full text-left border-collapse text-sm">
                <thead><tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold"><th className="px-3 py-2 border-b">ပစ္စည်း</th><th className="px-3 py-2 border-b w-16">အရေအတွက်</th><th className="px-3 py-2 border-b text-right">တစ်ခုဈေး</th><th className="px-3 py-2 border-b text-right">စုစုပေါင်း</th><th className="px-3 py-2 border-b">အကြောင်းရင်း</th><th className="px-3 py-2 border-b">Restock</th><th className="px-3 py-2 border-b">Serials</th></tr></thead>
                <tbody className="divide-y divide-slate-100">{(viewRow.details || []).map((d, i) => <tr key={i}><td className="px-3 py-2">{d.productName || `Product #${d.productId}`}</td><td className="px-3 py-2">{d.qty}</td><td className="px-3 py-2 text-right">{money(d.unitPrice)}</td><td className="px-3 py-2 text-right font-medium">{money(d.subtotal)}</td><td className="px-3 py-2 text-[11px] text-slate-500">{d.reasonName || '-'}</td><td className="px-3 py-2 text-[11px]">{d.restock === false ? 'Scrap' : 'Stock ပြန်တက်'}</td><td className="px-3 py-2 text-[11px] text-slate-500">{d.serialNumbers && d.serialNumbers.length > 0 ? d.serialNumbers.join(', ') : '-'}</td></tr>)}</tbody>
              </table>
              <section className="pt-2"><div className="mb-2 flex items-center justify-between"><h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Refund Payment History</h4><span className="text-[11px] text-slate-400">{viewRow.payments?.length || 0} payment(s)</span></div>{!viewRow.payments?.length ? <p className="py-3 text-xs text-slate-400">No refund payment history found.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200"><table className="w-full min-w-[600px] text-left text-sm"><thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Payment Method</th><th className="border-b px-3 py-2">Transaction No</th><th className="border-b px-3 py-2 text-right">Amount</th></tr></thead><tbody className="divide-y divide-slate-100">{viewRow.payments.map((payment, index) => <tr key={payment.id || `${payment.transactionNo}-${index}`}><td className="px-3 py-2 text-slate-600">{payment.paymentDate ? new Date(payment.paymentDate).toLocaleString() : '-'}</td><td className="px-3 py-2 text-slate-600">{payment.paymentMethodName || (payment.paymentMethodId ? `#${payment.paymentMethodId}` : '-')}</td><td className="px-3 py-2 text-slate-500">{payment.transactionNo || '-'}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">{money(payment.amount || 0)}</td></tr>)}</tbody></table></div>}</section>
              <section className="pt-2"><div className="mb-2 flex items-center justify-between"><h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Stock Movement</h4><span className="text-[11px] text-slate-400">{returnStockMovements.length} movement(s)</span></div>{!returnStockMovements.length ? <p className="py-3 text-xs text-slate-400">No stock movement found for this return.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200"><table className="w-full min-w-[650px] text-left text-sm"><thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Product</th><th className="border-b px-3 py-2">Type</th><th className="border-b px-3 py-2 text-right">In</th><th className="border-b px-3 py-2 text-right">Balance</th></tr></thead><tbody className="divide-y divide-slate-100">{returnStockMovements.map((movement, index) => <tr key={movement.id || `${movement.productId}-${index}`}><td className="px-3 py-2 text-slate-600">{movement.date ? new Date(movement.date).toLocaleString() : '-'}</td><td className="px-3 py-2"><p className="font-semibold text-slate-700">{movement.productName || '-'}</p><p className="text-[10px] text-slate-400">{movement.productCode || ''}</p></td><td className="px-3 py-2 text-slate-500">{movement.type}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">+{movement.quantityIn.toLocaleString()}</td><td className="px-3 py-2 text-right font-bold text-slate-700">{movement.balance.toLocaleString()}</td></tr>)}</tbody></table></div>}</section>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SaleReturnManagement;
