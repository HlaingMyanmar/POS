import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { ArrowLeft, Eye, List, Plus, RefreshCw, RotateCcw, Save, Search, Trash2, X } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import Swal from 'sweetalert2';
import { purchaseReturnApiService, PurchaseReturnPage } from '../services/purchasereturnapiservice';
import { purchaseApiService } from '../services/purchaseapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { supplierService } from '../services/supplierapiservice';
import { AppRoute, PaymentMethodDTO, PaymentTransactionDTO, PurchaseDTO, PurchaseReturnDTO, PurchaseReturnDetailDTO, SupplierDTO } from '../types';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type DetailForm = PurchaseReturnDetailDTO & { productSearch: string; serialNumbers: string[] };

type PurchaseProductOption = {
  productId: number;
  productName: string;
  unitPrice: number;
  purchasedQty: number;
  returnedQty: number;
  returnableQty: number;
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
  serialNumbers: ['']
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
const discountedUnitCost = (purchase: PurchaseDTO, detail: { productId: number; qty: number; unitCost: number; subtotal: number }) => {
  const gross = Number(purchase.totalAmount || 0);
  const net = Number(purchase.netAmount ?? (gross - Number(purchase.discountAmount || 0)));
  if (gross <= 0 || detail.qty <= 0) return Number(detail.unitCost || 0);
  const lineNet = Number(detail.subtotal || 0) * (net / gross);
  return Math.round((lineNet / detail.qty) * 100) / 100;
};

const PurchaseReturnManagement: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [rows, setRows] = useState<PurchaseReturnDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [masterLoading, setMasterLoading] = useState(true);
  const [purchaseLoading, setPurchaseLoading] = useState(false);

  const [suppliers, setSuppliers] = useState<SupplierDTO[]>([]);
  const [purchases, setPurchases] = useState<PurchaseDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  const [selectedPurchase, setSelectedPurchase] = useState<PurchaseDTO | null>(null);
  const [selectedPurchaseReturns, setSelectedPurchaseReturns] = useState<PurchaseReturnDTO[]>([]);

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
  const [editingId, setEditingId] = useState<number | null>(null);
  const [viewRow, setViewRow] = useState<PurchaseReturnDTO | null>(null);

  const [supplierId, setSupplierId] = useState(0);
  const [supplierSearch, setSupplierSearch] = useState('');
  const [purchaseId, setPurchaseId] = useState(0);
  const [purchaseSearch, setPurchaseSearch] = useState('');
  const [returnDate, setReturnDate] = useState(nowLocalDateTime());
  const [reason, setReason] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [refundPayments, setRefundPayments] = useState<PaymentTransactionDTO[]>([]);
  const [paymentMethodId, setPaymentMethodId] = useState(0);
  const [transactionNo, setTransactionNo] = useState('');
  const [details, setDetails] = useState<DetailForm[]>([emptyDetail()]);

  const supplierLabel = useCallback((s?: SupplierDTO) => {
    if (!s) return '';
    return `${s.name} (${s.code})`;
  }, []);

  const supplierLabelById = useCallback((id?: number) => {
    if (!id) return '-';
    const s = suppliers.find((x) => x.id === id);
    return s ? supplierLabel(s) : `Supplier #${id}`;
  }, [suppliers, supplierLabel]);

  const purchaseLabel = useCallback((p?: PurchaseDTO) => {
    if (!p) return '';
    return `${p.purchaseCode || `#${p.id}`} - ${p.supplierName || `Supplier #${p.supplierId}`}`;
  }, []);

  const purchaseLabelById = useCallback((id?: number) => {
    if (!id) return '-';
    const p = purchases.find((x) => x.id === id);
    return p ? purchaseLabel(p) : `#${id}`;
  }, [purchases, purchaseLabel]);

  const loadRows = useCallback(async (page: number, size: number, search: string) => {
    setLoading(true);
    try {
      const result: PurchaseReturnPage = await purchaseReturnApiService.getAll(page, size, search);
      setRows(result.content);
      setTotalElements(result.totalElements);
      setTotalPages(result.totalPages);
    } catch (e) {
      console.error('Failed to load purchase returns', e);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMaster = useCallback(async () => {
    setMasterLoading(true);
    try {
      const [sup, pur, pm] = await Promise.all([
        supplierService.getAll(),
        purchaseApiService.getAll(),
        paymentMethodService.getAllActive()
      ]);
      setSuppliers(sup);
      setPurchases(pur);
      setPaymentMethods(pm);
    } catch (e) {
      console.error('Failed to load master data', e);
    } finally {
      setMasterLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMaster();
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
    loadRows(currentPage, pageSize, debouncedSearch);
  }, [loadRows, currentPage, pageSize, debouncedSearch]);
  useDataEvents(['Purchase Return', 'Purchase'], () => loadRows(currentPage, pageSize, debouncedSearch));

  useEffect(() => {
    const rawPurchaseId = Number(searchParams.get('purchaseId') || 0);
    if (!rawPurchaseId || masterLoading || purchases.length === 0 || purchaseId === rawPurchaseId) return;
    const purchase = purchases.find((p) => p.id === rawPurchaseId);
    if (!purchase) return;
    setSupplierId(purchase.supplierId);
    setSupplierSearch(supplierLabelById(purchase.supplierId));
    setPurchaseId(rawPurchaseId);
    setPurchaseSearch(purchaseLabel(purchase));
    setDetails([emptyDetail()]);
    setShowForm(true);
  }, [searchParams, masterLoading, purchases, purchaseId, supplierLabelById, purchaseLabel]);

  useEffect(() => {
    if (purchaseId <= 0) {
      setSelectedPurchase(null);
      setSelectedPurchaseReturns([]);
      return;
    }

    let active = true;
    setPurchaseLoading(true);

    Promise.all([
      purchaseApiService.getById(purchaseId),
      purchaseReturnApiService.getByPurchaseId(purchaseId)
    ])
      .then(([data, returns]) => {
        if (!active) return;
        setSelectedPurchase(data);
        setSelectedPurchaseReturns(returns || []);
        if (data.supplierId) {
          setSupplierId(data.supplierId);
          setSupplierSearch(supplierLabelById(data.supplierId));
        }
      })
      .catch((e) => {
        if (!active) return;
        console.error('Failed to load purchase details', e);
        setSelectedPurchase(null);
        setSelectedPurchaseReturns([]);
      })
      .finally(() => {
        if (active) setPurchaseLoading(false);
      });

    return () => {
      active = false;
    };
  }, [purchaseId, supplierLabelById]);

  const filteredPurchases = useMemo(() => {
    if (supplierId <= 0) return [];
    return purchases.filter((p) => p.supplierId === supplierId);
  }, [purchases, supplierId]);

  const productOptions = useMemo<PurchaseProductOption[]>(() => {
    if (!selectedPurchase?.details || selectedPurchase.details.length === 0) return [];

    const returnedByProduct = new Map<number, number>();
    const returnedSerials = new Set<string>();
    selectedPurchaseReturns.forEach((ret) => {
      (ret.details || []).forEach((detail) => {
        returnedByProduct.set(detail.productId, (returnedByProduct.get(detail.productId) || 0) + (Number(detail.qty) || 0));
        (detail.serialNumbers || []).forEach((sn) => returnedSerials.add(normalizeSerial(sn)));
      });
    });

    const map = new Map<number, PurchaseProductOption>();
    selectedPurchase.details.forEach((detail) => {
      const existing = map.get(detail.productId);
      const rawSerials = Array.from(new Set((detail.serialNumbers || []).map((sn) => sanitizeSerial(sn)).filter(Boolean)));
      const netUnitCost = discountedUnitCost(selectedPurchase, detail);
      const serialNumbers = rawSerials.filter((sn) => !returnedSerials.has(normalizeSerial(sn)));
      if (!existing) {
        const purchasedQty = Number(detail.qty) || 0;
        const returnedQty = returnedByProduct.get(detail.productId) || 0;
        map.set(detail.productId, {
          productId: detail.productId,
          productName: detail.productName || `Product #${detail.productId}`,
          unitPrice: netUnitCost,
          purchasedQty,
          returnedQty,
          returnableQty: Math.max(0, purchasedQty - returnedQty),
          serialNumbers
        });
        return;
      }

      existing.purchasedQty += Number(detail.qty) || 0;
      existing.returnedQty = returnedByProduct.get(detail.productId) || 0;
      existing.returnableQty = Math.max(0, existing.purchasedQty - existing.returnedQty);
      existing.unitPrice = Math.round(((existing.unitPrice * (existing.purchasedQty - (Number(detail.qty) || 0))) + (netUnitCost * (Number(detail.qty) || 0))) / Math.max(1, existing.purchasedQty) * 100) / 100;
      existing.serialNumbers = Array.from(new Set([
        ...existing.serialNumbers,
        ...serialNumbers
      ]));
    });

    return Array.from(map.values()).filter((option) => option.returnableQty > 0);
  }, [selectedPurchase, selectedPurchaseReturns]);

  const productLabel = useCallback((p: PurchaseProductOption) => `${p.productName} (#${p.productId}) · ပြန်ပို့နိုင် ${p.returnableQty}`, []);

  const productOptionById = useCallback((productId: number) => {
    return productOptions.find((option) => option.productId === productId);
  }, [productOptions]);

  const getProductSerialPool = useCallback((productId: number) => {
    return productOptionById(productId)?.serialNumbers || [];
  }, [productOptionById]);

  const getSerialOptionsForRow = useCallback((rowIndex: number) => {
    const row = details[rowIndex];
    if (!row || row.productId <= 0) return [];

    const pool = getProductSerialPool(row.productId);
    const usedInOtherRows = new Set(
      details
        .flatMap((detail, index) =>
          index === rowIndex ? [] : detail.serialNumbers.map((serial) => normalizeSerial(serial)).filter(Boolean)
        )
    );

    return pool.filter((serial) => {
      const normalized = normalizeSerial(serial);
      const belongsToCurrentRow = row.serialNumbers.some((value) => normalizeSerial(value) === normalized);
      return belongsToCurrentRow || !usedInOtherRows.has(normalized);
    });
  }, [details, getProductSerialPool]);

  const resetForm = () => {
    setEditingId(null);
    setSupplierId(0);
    setSupplierSearch('');
    setPurchaseId(0);
    setPurchaseSearch('');
    setSelectedPurchase(null);
    setSelectedPurchaseReturns([]);
    setReturnDate(nowLocalDateTime());
    setReason('');
    setRefundAmount('');
    setRefundPayments([]);
    setPaymentMethodId(paymentMethods[0]?.id ?? 0);
    setTransactionNo('');
    setDetails([emptyDetail()]);
  };

  const total = useMemo(() => details.reduce((s, d) => s + d.subtotal, 0), [details]);
  const resolvedRefund = useMemo(() => refundAmount.trim() === '' ? 0 : parseFloat(refundAmount), [refundAmount]);
  const existingReturnAmount = Number(selectedPurchase?.returnAmount || 0);
  const existingRefundAmount = Number(selectedPurchase?.refundAmount || 0);
  const maxRefund = useMemo(() => {
    if (!selectedPurchase) return 0;
    const purchaseNet = Number(selectedPurchase.netAmount ?? (Number(selectedPurchase.totalAmount || 0) - Number(selectedPurchase.discountAmount || 0)));
    const netAfterReturn = Math.max(0, purchaseNet - existingReturnAmount - total);
    return Math.max(0, Number(selectedPurchase.paidAmount || 0) - netAfterReturn - existingRefundAmount);
  }, [selectedPurchase, existingReturnAmount, existingRefundAmount, total]);
  const normalizedRefundPayments = useMemo(() => normalizePayments(refundPayments), [refundPayments]);
  const splitRefund = useMemo(() => paymentTotal(refundPayments), [refundPayments]);
  const effectiveRefund = normalizedRefundPayments.length > 0 ? splitRefund : resolvedRefund;
  const paymentRequired = !Number.isNaN(effectiveRefund) && effectiveRefund > 0;
  const validRefund = !Number.isNaN(effectiveRefund) && effectiveRefund >= 0 && effectiveRefund <= maxRefund;

  const serialValidation = useMemo(() => {
    const rowsWithProduct = details.filter((row) => row.productId > 0);
    const serialRows = rowsWithProduct.filter((row) => getProductSerialPool(row.productId).length > 0);
    const serials = serialRows.flatMap((row) => row.serialNumbers.map((sn) => sanitizeSerial(sn)).filter(Boolean));
    const unique = new Set(serials.map((sn) => sn.toLowerCase()));

    const qtyMatchesSerialCount = serialRows.every((row) => row.serialNumbers.length === row.qty);
    const allRowsHaveSerials = serialRows.every((row) => {
      if (row.qty <= 0) return false;
      if (!row.serialNumbers || row.serialNumbers.length === 0) return false;
      return row.serialNumbers.every((sn) => sanitizeSerial(sn).length > 0);
    });

    const strictCheckAvailable = rowsWithProduct.some((row) => getProductSerialPool(row.productId).length > 0);

    const belongsToSelectedProduct = serialRows.every((row) => {
      const pool = getProductSerialPool(row.productId);
      if (pool.length === 0) return true; // backend will verify ownership when purchase serial list is unavailable
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
  }, [details, getProductSerialPool]);

  const validForm = supplierId > 0
    && purchaseId > 0
    && details.length > 0
    && details.every((d) => {
      const option = productOptionById(d.productId);
      return d.productId > 0 && d.qty > 0 && d.unitPrice > 0 && !!option && d.qty <= option.returnableQty;
    })
    && reason.trim().length > 0
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
        const option = productOptionById(next.productId);
        const maxQty = option?.returnableQty ?? 0;
        next.qty = Math.min(maxQty || 0, Math.max(0, parseInt(value, 10) || 0));
        next.serialNumbers = getProductSerialPool(next.productId).length > 0
          ? ensureSerialCount(next.serialNumbers, next.qty)
          : [];
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
      const qty = match ? Math.min(Math.max(1, d.qty || 1), match.returnableQty) : d.qty;
      return {
        ...d,
        productSearch: value,
        productId: match?.productId || 0,
        qty,
        unitPrice,
        serialNumbers: match && match.serialNumbers.length > 0 ? ensureSerialCount(d.serialNumbers, qty) : [],
        subtotal: qty * unitPrice
      };
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

  const onSupplierSearch = (value: string) => {
    setSupplierSearch(value);
    const match = suppliers.find((s) => supplierLabel(s).toLowerCase() === value.toLowerCase());
    const nextSupplierId = match?.id || 0;

    if (nextSupplierId !== supplierId) {
      setSupplierId(nextSupplierId);
      setPurchaseId(0);
      setPurchaseSearch('');
      setSelectedPurchase(null);
      setSelectedPurchaseReturns([]);
      setDetails([emptyDetail()]);
      return;
    }

    setSupplierId(nextSupplierId);
  };

  const onPurchaseSearch = (value: string) => {
    setPurchaseSearch(value);
    const match = filteredPurchases.find((p) => purchaseLabel(p).toLowerCase() === value.toLowerCase());
    const nextPurchaseId = match?.id || 0;

    if (nextPurchaseId !== purchaseId) {
      setPurchaseId(nextPurchaseId);
      setSelectedPurchaseReturns([]);
      setDetails([emptyDetail()]);
    }

    if (match && match.supplierId !== supplierId) {
      setSupplierId(match.supplierId);
      setSupplierSearch(supplierLabelById(match.supplierId));
    }
  };

  const openCreate = () => {
    resetForm();
    setShowForm(true);
  };

  const openView = async (id: number) => {
    try {
      const data = await purchaseReturnApiService.getById(id);
      setViewRow(data);
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to load purchase return', 'error');
    }
  };

  const onVoid = async (row: PurchaseReturnDTO) => {
    if (!row.id || row.status === 'VOIDED') return;
    const result = await Swal.fire({
      icon: 'warning',
      title: `${row.returnNo || `#${row.id}`} ကို Void လုပ်မည်`,
      input: 'textarea',
      inputLabel: 'Void reason',
      inputPlaceholder: 'ဘာကြောင့် void လုပ်တာလဲ ရေးပါ',
      showCancelButton: true,
      confirmButtonText: 'Void လုပ်မည်',
      confirmButtonColor: '#e11d48',
      cancelButtonText: 'မလုပ်တော့ပါ',
      inputValidator: (value) => value && value.trim().length > 0 ? null : 'Void reason လိုအပ်ပါသည်'
    });
    if (!result.isConfirmed) return;
    try {
      await purchaseReturnApiService.voidReturn(row.id, String(result.value || '').trim());
      await loadRows(currentPage, pageSize, debouncedSearch);
      if (viewRow?.id === row.id) setViewRow(null);
      Swal.fire({ icon: 'success', title: 'Purchase return void လုပ်ပြီးပါပြီ', toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to void purchase return', 'error');
    }
  };


  const onSave = async () => {
    if (!validForm) {
      Swal.fire('Validation', 'Please select supplier/purchase, fill details, and provide valid serial numbers.', 'warning');
      return;
    }
    setSaving(true);
    try {
      const payload: PurchaseReturnDTO = {
        purchaseId,
        returnDate: returnDate || undefined,
        reason: reason.trim() || undefined,
        totalReturnAmount: total,
        refundAmount: effectiveRefund,
        paymentMethodId: paymentRequired ? (normalizedRefundPayments[0]?.paymentMethodId || paymentMethodId) : undefined,
        payments: normalizedRefundPayments.length > 0 ? normalizedRefundPayments : undefined,
        transactionNo: transactionNo.trim() || undefined,
        details: details.map((d) => ({
          returnId: editingId || undefined,
          productId: Number(d.productId),
          qty: Number(d.qty),
          unitPrice: Number(d.unitPrice),
          subtotal: Number((d.qty * d.unitPrice).toFixed(2)),
          serialNumbers: d.serialNumbers.map((sn) => sanitizeSerial(sn)).filter(Boolean)
        }))
      };

      if (editingId) {
        await purchaseReturnApiService.update(editingId, payload);
      } else {
        await purchaseReturnApiService.create(payload);
      }

      setShowForm(false);
      resetForm();
      await loadRows(currentPage, pageSize, debouncedSearch);
      Swal.fire({
        icon: 'success',
        title: editingId ? 'Purchase return updated' : 'Purchase return created',
        toast: true,
        showConfirmButton: false,
        timer: 1500,
        position: 'top-end'
      });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to save purchase return', 'error');
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

  const stats = useMemo(() => {
    const activeRows = rows.filter((r) => r.status !== 'VOIDED');
    return {
      count: totalElements,
      total: activeRows.reduce((s, r) => s + (r.totalReturnAmount || 0), 0),
      refund: activeRows.reduce((s, r) => s + (r.refundAmount ?? 0), 0)
    };
  }, [totalElements, rows]);

  if (showForm) {
    return (
      <div className="w-full max-w-none space-y-6">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-slate-800 text-left">{editingId ? 'Update Purchase Return' : 'New Purchase Return'}</h2>
            <p className="text-xs text-slate-500 mt-1">Supplier ရွေးပြီး သူဆီက purchase ကိုရွေးပါ။ Product/Serial သည် အဲဒီ purchase မှပဲ ရနိုင်မည်။</p>
          </div>
          <button onClick={() => { setShowForm(false); resetForm(); }} className="inline-flex w-full sm:w-auto justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50">
            <ArrowLeft size={14} /> Back
          </button>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="xl:col-span-2 space-y-5">
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Supplier</label>
                  <input
                    list="pr-suppliers"
                    value={supplierSearch}
                    onChange={(e) => onSupplierSearch(e.target.value)}
                    placeholder="Select supplier..."
                    className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${supplierId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                  />
                  <datalist id="pr-suppliers">
                    {suppliers.map((s) => <option key={s.id} value={supplierLabel(s)} />)}
                  </datalist>
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Purchase</label>
                  <input
                    list="pr-purchases"
                    value={purchaseSearch}
                    onChange={(e) => onPurchaseSearch(e.target.value)}
                    placeholder={supplierId > 0 ? 'Select purchase...' : 'Select supplier first'}
                    disabled={supplierId <= 0}
                    className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed ${purchaseId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                  />
                  <datalist id="pr-purchases">
                    {filteredPurchases.map((p) => <option key={p.id} value={purchaseLabel(p)} />)}
                  </datalist>
                </div>
              </div>
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Return Date</label>
                  <input type="datetime-local" value={returnDate} onChange={(e) => setReturnDate(e.target.value)} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Purchase Info</label>
                  <div className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-600">
                    {purchaseLoading ? 'Loading purchase details...' : purchaseId > 0 ? `${productOptions.length} product(s) available for return` : '-'}
                  </div>
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Reason</label>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="Optional reason..." className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none" />
              </div>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-slate-100 flex items-center justify-between">
                <h3 className="font-bold text-slate-800 text-sm">Return Items</h3>
                <button onClick={() => setDetails((d) => [...d, emptyDetail()])} className="flex items-center gap-2 px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-bold hover:bg-indigo-700"><Plus size={14} /> Add Row</button>
              </div>
              <div className="overflow-auto">
                <table className="w-full text-left border-collapse min-w-[700px]">
                  <thead className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                    <tr>
                      <th className="px-4 py-3 border-b border-slate-100">Product</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-24">Qty</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-32">Unit Price</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-36 text-right">Subtotal</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-12"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {details.map((d, i) => {
                      const serialOptions = getSerialOptionsForRow(i);
                      const option = productOptionById(d.productId);
                      return (
                        <React.Fragment key={i}>
                          <tr className="hover:bg-slate-50/60">
                            <td className="px-4 py-3">
                              <input
                                list={`pr-products-${i}`}
                                value={d.productSearch}
                                onChange={(e) => onProductSearch(i, e.target.value)}
                                placeholder={purchaseId > 0 ? 'Select product...' : 'Select purchase first'}
                                disabled={purchaseId <= 0 || productOptions.length === 0}
                                className={`w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed ${d.productId > 0 ? 'text-slate-700' : 'text-rose-500'}`}
                              />
                              <datalist id={`pr-products-${i}`}>
                                {productOptions.map((p) => <option key={p.productId} value={productLabel(p)} />)}
                              </datalist>
                            </td>
                            <td className="px-4 py-3">
                              <input type="number" min="1" max={option?.returnableQty || undefined} value={d.qty || ''} onChange={(e) => onDetailChange(i, 'qty', e.target.value)} className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none" />
                              {option && <p className="text-[10px] text-slate-400 mt-0.5">Max {option.returnableQty}</p>}
                            </td>
                            <td className="px-4 py-3">
                              <input type="number" min="0" step="0.01" value={d.unitPrice || ''} onChange={(e) => onDetailChange(i, 'unitPrice', e.target.value)} className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none" />
                            </td>
                            <td className="px-4 py-3 text-right font-bold text-slate-700">{money(d.subtotal)}</td>
                            <td className="px-4 py-3 text-center">
                              <button onClick={() => setDetails((prev) => prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i))} className="p-1.5 text-slate-300 hover:text-rose-500 disabled:opacity-40" disabled={details.length <= 1}><Trash2 size={14} /></button>
                            </td>
                          </tr>
                          {d.productId > 0 && d.qty > 0 && serialOptions.length > 0 && (
                            <tr className="bg-slate-50/50">
                              <td colSpan={5} className="px-4 py-3">
                                <div className="space-y-2">
                                  <div className="flex items-center justify-between text-[10px] text-slate-500">
                                    <span className="font-bold uppercase tracking-wider">Serial Numbers ({d.qty})</span>
                                    <span>{serialOptions.length} available</span>
                                  </div>
                                  <div className="flex flex-wrap gap-2">
                                    {d.serialNumbers.map((serial, serialIndex) => (
                                      <input
                                        key={serialIndex}
                                        list={`pr-serial-options-${i}`}
                                        type="text"
                                        value={serial}
                                        onChange={(e) => onSerialChange(i, serialIndex, e.target.value)}
                                        placeholder={`Serial #${serialIndex + 1}`}
                                        className="px-2 py-1 bg-white border border-slate-200 rounded text-[11px] w-36 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
                                      />
                                    ))}
                                    <datalist id={`pr-serial-options-${i}`}>
                                      {serialOptions.map((serial) => <option key={serial} value={serial} />)}
                                    </datalist>
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
          </div>

          <div className="space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 sm:p-6 space-y-5 xl:sticky xl:top-20">
              <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2"><RotateCcw size={16} className="text-indigo-500" /> Refund & Summary</h3>
              <div className="flex justify-between items-center text-sm pb-2 border-b border-slate-100"><span className="text-slate-500">Total Return</span><span className="font-bold text-slate-800">{money(total)}</span></div>
              <div className="flex justify-between items-center text-xs pb-2 border-b border-slate-100"><span className="text-slate-500">Max Refund</span><span className="font-bold text-emerald-700">{money(maxRefund)}</span></div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Refund Amount</label>
                <input type="number" min="0" max={maxRefund || undefined} step="0.01" value={refundAmount} onChange={(e) => { setRefundAmount(e.target.value); if (refundPayments.length > 0) setRefundPayments([]); }} placeholder="0 = supplier credit/payable reduction only" className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${validRefund ? 'border-slate-200' : 'border-rose-200'}`} />
                <p className="text-[10px] text-slate-400">Blank means 0 refund. Refund is allowed only when this purchase has supplier credit.</p>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Payment Method</label>
                <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value))} className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${!paymentRequired || paymentMethodId > 0 ? 'border-slate-200' : 'border-rose-200'}`}>
                  <option value={0}>Select payment method</option>
                  {paymentMethods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                </select>
                <p className="text-[10px] text-slate-400">Required only if refund amount is greater than zero.</p>
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
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Transaction No</label>
                <input type="text" value={transactionNo} onChange={(e) => setTransactionNo(e.target.value)} placeholder="Optional transaction no" className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
              </div>

              {!serialValidation.qtyMatchesSerialCount && (
                <p className="text-[10px] text-rose-500">Each return row must contain exactly `qty` serial numbers.</p>
              )}

              {serialValidation.qtyMatchesSerialCount && !serialValidation.allRowsHaveSerials && (
                <p className="text-[10px] text-rose-500">Every serial number field is required.</p>
              )}

              {!serialValidation.uniqueAcrossRows && (
                <p className="text-[10px] text-rose-500">Duplicate serial numbers are not allowed.</p>
              )}

              {!serialValidation.belongsToSelectedProduct && serialValidation.strictCheckAvailable && (
                <p className="text-[10px] text-rose-500">Serial number must belong to selected product in this purchase.</p>
              )}

              <button onClick={onSave} disabled={!validForm || saving} className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold transition-all ${validForm && !saving ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}>
                <Save size={16} /> {saving ? 'Saving...' : editingId ? 'Update Return' : 'Create Return'}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="w-full max-w-none space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-800 text-left">Purchase Return Management</h2>
          <p className="text-xs text-slate-500 mt-1">Track returns, refunded amounts, and return voucher history.</p>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <button onClick={() => loadRows(currentPage, pageSize, debouncedSearch)} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh</button>
          <button onClick={openCreate} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700"><Plus size={16} /> New Purchase Return</button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Vouchers</p><p className="text-2xl font-bold text-slate-800">{stats.count}</p></div><div className="w-11 h-11 rounded-lg bg-indigo-50 flex items-center justify-center"><List size={20} className="text-indigo-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Returned</p><p className="text-2xl font-bold text-slate-800">{money(stats.total)}</p></div><div className="w-11 h-11 rounded-lg bg-slate-100 flex items-center justify-center"><RotateCcw size={20} className="text-slate-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Refunded</p><p className="text-2xl font-bold text-emerald-700">{money(stats.refund)}</p></div><div className="w-11 h-11 rounded-lg bg-emerald-50 flex items-center justify-center"><RotateCcw size={20} className="text-emerald-600" /></div></div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col lg:flex-row lg:items-center gap-4">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search return no, supplier or reason... (searches all data)" className="w-full pl-9 pr-9 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
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
            <button onClick={() => { setSearch(''); setDateFrom(''); setDateTo(''); }} className="px-3 py-1.5 text-xs font-semibold text-slate-500 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100">Clear</button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
          <h3 className="font-bold text-slate-800 text-sm">Purchase Return Vouchers</h3>
          {masterLoading && <span className="text-[11px] text-slate-400">Loading master data...</span>}
        </div>
        <div className="overflow-auto max-h-[60vh] custom-scrollbar">
          {loading ? <div className="p-8 text-center text-slate-400">Loading...</div> : (
            <table className="w-full text-left border-collapse min-w-[920px]">
              <thead className="sticky top-0 bg-white z-10 shadow-sm">
                <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                  <th className="px-4 py-3 border-b border-slate-100">Return No</th>
                  <th className="px-4 py-3 border-b border-slate-100">Purchase</th>
                  <th className="px-4 py-3 border-b border-slate-100">Supplier</th>
                  <th className="px-4 py-3 border-b border-slate-100">Date</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Total</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Refund</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-center">Status</th>
                  <th className="px-4 py-3 border-b border-slate-100">Reason</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.length > 0 ? filtered.map((r) => {
                  const voided = r.status === 'VOIDED';
                  return (
                  <tr key={r.id || r.returnNo} className={`hover:bg-slate-50 text-xs ${voided ? 'bg-slate-50/70' : ''}`}>
                    <td className="px-4 py-3 font-medium text-slate-800">{r.returnNo || `#${r.id}`}</td>
                    <td className="px-4 py-3 text-slate-600">
                      {r.purchaseId > 0 ? (
                        <Link
                          to={`${AppRoute.PURCHASES}?purchaseId=${r.purchaseId}`}
                          className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium"
                          title="Open related purchase"
                        >
                          {purchaseLabelById(r.purchaseId)}
                        </Link>
                      ) : (
                        '-'
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{r.supplierName || supplierLabelById(purchases.find((p) => p.id === r.purchaseId)?.supplierId)}</td>
                    <td className="px-4 py-3 text-slate-600">{r.returnDate ? new Date(r.returnDate).toLocaleString() : '-'}</td>
                    <td className="px-4 py-3 text-right font-bold text-slate-700">{money(r.totalReturnAmount || 0)}</td>
                    <td className={`px-4 py-3 text-right font-bold ${voided ? 'text-slate-400' : 'text-emerald-700'}`}>{money(r.refundAmount ?? 0)}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-flex px-2 py-0.5 rounded-md border text-[10px] font-black ${voided ? 'bg-slate-100 text-slate-500 border-slate-200' : 'bg-emerald-50 text-emerald-700 border-emerald-100'}`}>
                        {voided ? 'VOIDED' : 'CONFIRMED'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-500 max-w-[260px] truncate">{r.reason || '-'}</td>
                    <td className="px-4 py-3 text-right"><div className="inline-flex items-center gap-1">
                      <button onClick={() => r.id && openView(r.id)} className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-indigo-50" title="View"><Eye size={15} /></button>
                      {!voided && <button onClick={() => onVoid(r)} className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50" title="Void"><X size={15} /></button>}
                    </div></td>
                  </tr>
                );}) : <tr><td colSpan={9} className="px-4 py-10 text-center text-slate-400">No purchase returns match the current filters.</td></tr>}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination */}
        {!loading && totalPages > 0 && (
          <div className="px-4 py-3 border-t border-slate-100 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <p className="text-xs text-slate-500">
              Showing {totalElements === 0 ? 0 : currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()}
            </p>
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-1.5">
                <span className="text-xs text-slate-500">Show</span>
                <select
                  value={pageSize}
                  onChange={e => { setPageSize(Number(e.target.value)); setCurrentPage(0); }}
                  className="border border-slate-200 rounded-lg px-2 py-1 text-xs bg-white focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                >
                  {[10, 20, 50, 100].map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div className="flex items-center gap-1 flex-wrap">
                <button onClick={() => setCurrentPage(p => Math.max(0, p - 1))} disabled={currentPage === 0}
                  className="w-8 h-8 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">‹</button>
                {(() => {
                  const pages: (number | -1)[] = [];
                  const delta = 2;
                  for (let i = 0; i < totalPages; i++) {
                    if (i === 0 || i === totalPages - 1 || (i >= currentPage - delta && i <= currentPage + delta)) {
                      pages.push(i);
                    } else if (pages[pages.length - 1] !== -1) {
                      pages.push(-1);
                    }
                  }
                  return pages.map((p, idx) =>
                    p === -1
                      ? <span key={`e${idx}`} className="px-1 text-slate-400 text-xs select-none">...</span>
                      : <button key={p} onClick={() => setCurrentPage(p)}
                          className={`w-8 h-8 rounded-lg text-xs font-semibold transition-colors ${p === currentPage ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
                          {p + 1}
                        </button>
                  );
                })()}
                <button onClick={() => setCurrentPage(p => Math.min(totalPages - 1, p + 1))} disabled={currentPage >= totalPages - 1}
                  className="w-8 h-8 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">›</button>
              </div>
            </div>
          </div>
        )}
      </div>

      {viewRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-bold text-slate-800">Purchase Return: {viewRow.returnNo || `#${viewRow.id}`}</h3>
              <button onClick={() => setViewRow(null)} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"><X size={18} /></button>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600">
                  <span className="font-medium text-slate-500">Purchase:</span>{' '}
                  {viewRow.purchaseId > 0 ? (
                    <Link
                      to={`${AppRoute.PURCHASES}?purchaseId=${viewRow.purchaseId}`}
                      className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium"
                      title="Open related purchase"
                    >
                      {purchaseLabelById(viewRow.purchaseId)}
                    </Link>
                  ) : (
                    '-'
                  )}
                </p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Supplier:</span> {viewRow.supplierName || supplierLabelById(purchases.find((p) => p.id === viewRow.purchaseId)?.supplierId)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Return Date:</span> {viewRow.returnDate ? new Date(viewRow.returnDate).toLocaleString() : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Total Return:</span> {money(viewRow.totalReturnAmount || 0)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Refund:</span> {money(viewRow.refundAmount ?? viewRow.totalReturnAmount ?? 0)}</p>
              </div>
              {viewRow.reason && <p className="text-sm text-slate-600"><span className="font-medium text-slate-500">Reason:</span> {viewRow.reason}</p>}
              <table className="w-full text-left border-collapse text-sm">
                <thead><tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold"><th className="px-3 py-2 border-b">Product</th><th className="px-3 py-2 border-b w-16">Qty</th><th className="px-3 py-2 border-b text-right">Unit Price</th><th className="px-3 py-2 border-b text-right">Subtotal</th><th className="px-3 py-2 border-b">Serials</th></tr></thead>
                <tbody className="divide-y divide-slate-100">{(viewRow.details || []).map((d, i) => <tr key={i}><td className="px-3 py-2">{d.productName || `Product #${d.productId}`}</td><td className="px-3 py-2">{d.qty}</td><td className="px-3 py-2 text-right">{money(d.unitPrice)}</td><td className="px-3 py-2 text-right font-medium">{money(d.subtotal)}</td><td className="px-3 py-2 text-[11px] text-slate-500">{d.serialNumbers && d.serialNumbers.length > 0 ? d.serialNumbers.join(', ') : '-'}</td></tr>)}</tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseReturnManagement;
