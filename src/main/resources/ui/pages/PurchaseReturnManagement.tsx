import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { ArrowLeft, CreditCard, Download, Eye, List, Plus, Printer, ReceiptText, RefreshCw, RotateCcw, Save, Search, Trash2, X } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import Swal from 'sweetalert2';
import { purchaseReturnApiService, PurchaseReturnPage } from '../services/purchasereturnapiservice';
import { purchaseApiService } from '../services/purchaseapiservice';
import { productService } from '../services/productapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { supplierService } from '../services/supplierapiservice';
import { AppRoute, PaymentMethodDTO, PaymentTransactionDTO, PurchaseDTO, PurchaseReturnDTO, PurchaseReturnDetailDTO, ProductStockHistoryMovementDTO, SupplierDTO } from '../types';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';
import { getCachedCompanySettings } from '../utils/companySettings';
import { buildPurchaseReturnVoucherHtml } from './purchaseReturnVoucherTemplate';

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
const purchaseVoucherStatus = (p?: PurchaseDTO) => (p?.status || '').trim().toUpperCase();
const isReturnablePurchase = (p?: PurchaseDTO) => {
  const status = purchaseVoucherStatus(p);
  return status !== 'CANCELLED' && status !== 'DRAFT';
};

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
  const [isRefundModalOpen, setIsRefundModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [viewRow, setViewRow] = useState<PurchaseReturnDTO | null>(null);
  const [returnStockMovements, setReturnStockMovements] = useState<ProductStockHistoryMovementDTO[]>([]);

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
    if (!isReturnablePurchase(purchase)) {
      Swal.fire({
        icon: 'info',
        title: purchaseVoucherStatus(purchase) === 'CANCELLED' ? 'ပယ်ဖျက်ပြီး ဘောင်ချာ' : 'မူကြမ်း ဘောင်ချာ',
        text: purchaseVoucherStatus(purchase) === 'CANCELLED'
          ? 'ပယ်ဖျက်ပြီး ဝယ်ယူမှုဘောင်ချာကို ဝယ်ပြန်ပို့ မလုပ်နိုင်ပါ။ Stock နှင့် Journal ကို ပယ်ဖျက်စဉ်က ပြန်ပြင်ပြီးဖြစ်သည်။'
          : 'မူကြမ်းကို အရင်အတည်ပြုပြီးမှသာ ဝယ်ပြန်ပို့ လုပ်နိုင်သည်။'
      });
      return;
    }
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
    return purchases.filter((p) => p.supplierId === supplierId && isReturnablePurchase(p));
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
      const rawSerials: string[] = Array.from(new Set<string>((detail.serialNumbers || []).map((sn: string) => sanitizeSerial(sn)).filter(Boolean)));
      const netUnitCost = discountedUnitCost(selectedPurchase, detail);
      const serialNumbers = rawSerials.filter((sn) => !returnedSerials.has(normalizeSerial(sn)));
      if (!existing) {
        const purchasedQty = Number(detail.qty) || 0;
        const returnedQty = returnedByProduct.get(detail.productId) || 0;
        map.set(detail.productId, {
          productId: detail.productId,
          productName: detail.productName || `ပစ္စည်း #${detail.productId}`,
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
      existing.serialNumbers = Array.from(new Set<string>([
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
  const originalPurchaseNet = useMemo(() => {
    if (!selectedPurchase) return 0;
    return Number(selectedPurchase.totalAmount || 0) - Number(selectedPurchase.discountAmount || 0);
  }, [selectedPurchase]);
  const netAfterReturn = useMemo(() => Math.max(0, originalPurchaseNet - existingReturnAmount - total), [originalPurchaseNet, existingReturnAmount, total]);
  const dueAfterReturn = useMemo(() => Math.max(0, netAfterReturn - Number(selectedPurchase?.paidAmount || 0)), [netAfterReturn, selectedPurchase]);
  const supplierCreditBeforeRefund = useMemo(() => Math.max(0, Number(selectedPurchase?.paidAmount || 0) - netAfterReturn - existingRefundAmount), [selectedPurchase, netAfterReturn, existingRefundAmount]);
  const maxRefund = useMemo(() => {
    if (!selectedPurchase) return 0;
    return supplierCreditBeforeRefund;
  }, [selectedPurchase, supplierCreditBeforeRefund]);
  const normalizedRefundPayments = useMemo(() => normalizePayments(refundPayments), [refundPayments]);
  const splitRefund = useMemo(() => paymentTotal(refundPayments), [refundPayments]);
  const effectiveRefund = normalizedRefundPayments.length > 0 ? splitRefund : resolvedRefund;
  const paymentRequired = !Number.isNaN(effectiveRefund) && effectiveRefund > 0;
  const validRefund = !Number.isNaN(effectiveRefund) && effectiveRefund >= 0 && effectiveRefund <= maxRefund;
  const supplierCreditAfterRefund = useMemo(() => Math.max(0, supplierCreditBeforeRefund - (Number.isNaN(effectiveRefund) ? 0 : effectiveRefund)), [supplierCreditBeforeRefund, effectiveRefund]);
  const totalReturnableQty = useMemo(() => productOptions.reduce((sum, option) => sum + option.returnableQty, 0), [productOptions]);
  const totalSelectedQty = useMemo(() => details.reduce((sum, detail) => sum + (Number(detail.qty) || 0), 0), [details]);
  const selectedSerialCount = useMemo(() => details.reduce((sum, detail) => sum + detail.serialNumbers.filter((sn) => sanitizeSerial(sn)).length, 0), [details]);
  const activeReturnCount = useMemo(() => selectedPurchaseReturns.filter((row) => row.status !== 'VOIDED').length, [selectedPurchaseReturns]);
  const refundMode = effectiveRefund > 0 ? 'ငွေသား/ဘဏ်မှ ပြန်အမ်းမည်' : dueAfterReturn > 0 ? 'ပေးရန်ကျန်ငွေထဲမှ လျှော့မည်' : supplierCreditAfterRefund > 0 ? 'Supplier credit အဖြစ်ထားမည်' : 'ငွေကြေးလှုပ်ရှားမှုမရှိ';

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
      setReturnStockMovements([]);
      const productIds = [...new Set((data.details || []).map((detail) => detail.productId))];
      const histories = await Promise.allSettled(productIds.map((productId) => productService.getStockHistory(productId, { size: 100 })));
      setReturnStockMovements(histories.flatMap((result) => result.status === 'fulfilled' ? result.value.movements || [] : []).filter((movement) => movement.referenceId === id));
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to load purchase return', 'error');
    }
  };

  const onVoid = async (row: PurchaseReturnDTO) => {
    if (!row.id || row.status === 'VOIDED') return;
    const result = await Swal.fire({
      icon: 'warning',
      title: `${row.returnNo || `#${row.id}`} ကို ပယ်ဖျက်ရန် လုပ်မည်`,
      input: 'textarea',
      inputLabel: 'ပယ်ဖျက်ရန် reason',
      inputPlaceholder: 'ဘာကြောင့် void လုပ်တာလဲ ရေးပါ',
      showCancelButton: true,
      confirmButtonText: 'ပယ်ဖျက်ရန် လုပ်မည်',
      confirmButtonColor: '#e11d48',
      cancelButtonText: 'မလုပ်တော့ပါ',
      inputValidator: (value) => value && value.trim().length > 0 ? null : 'ပယ်ဖျက်ရန် reason လိုအပ်ပါသည်'
    });
    if (!result.isConfirmed) return;
    try {
      await purchaseReturnApiService.voidReturn(row.id, String(result.value || '').trim());
      await loadRows(currentPage, pageSize, debouncedSearch);
      if (viewRow?.id === row.id) setViewRow(null);
      Swal.fire({ icon: 'success', title: 'ဝယ်ယူပြန်ပို့ဘောင်ချာ ပယ်ဖျက်ပြီးပါပြီ', toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to void purchase return', 'error');
    }
  };


  const onSave = async () => {
    if (!validForm) {
      Swal.fire('စစ်ဆေးရန်', 'Supplier/Purchase ရွေးပြီး item, serial number နှင့် အကြောင်းပြချက်ကို မှန်ကန်အောင်ဖြည့်ပါ။', 'warning');
      return;
    }
    if (!isReturnablePurchase(selectedPurchase || undefined)) {
      Swal.fire('မလုပ်နိုင်ပါ', 'ပယ်ဖျက်ပြီး သို့မဟုတ် မူကြမ်း ဘောင်ချာကို ဝယ်ပြန်ပို့ မလုပ်နိုင်ပါ။', 'warning');
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
        title: editingId ? 'ပြန်ပို့ဘောင်ချာ ပြင်ပြီးပါပြီ' : 'ပြန်ပို့ဘောင်ချာ ဖန်တီးပြီးပါပြီ',
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
  const visibleReturnRows = useMemo(() => filtered.filter((row): row is typeof row & { id: number } => typeof row.id === 'number'), [filtered]);
  const bulk = useBulkSelection<PurchaseReturnDTO & { id: number }>(visibleReturnRows);
  const handleBulkAction = (action: { key: string }) => {
    if (action.key !== 'export') return;
    const csv = [
      ['ID', 'Return No', 'Date', 'Supplier', 'Total', 'Refund'],
      ...bulk.selectedRows.map((row) => [row.id, row.returnNo || '', row.returnDate || '', row.supplierName || '', row.totalReturnAmount || 0, row.refundAmount || 0])
    ].map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `purchase-returns-selected-${new Date().toISOString().slice(0, 10)}.csv`; link.click(); URL.revokeObjectURL(url);
    bulk.clear();
  };

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
      <div className="w-full max-w-none space-y-5">
        <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          <div className="flex flex-col gap-4 border-b border-slate-100 bg-white p-4 sm:p-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 items-start gap-3">
              <button onClick={() => { setShowForm(false); resetForm(); }} className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50" title="နောက်သို့"><ArrowLeft size={16} /></button>
              <div className="min-w-0">
              <div className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                <ReceiptText size={14} /> ဝယ်ယူပြန်ပို့ ဘောင်ချာ
              </div>
              <h2 className="mt-1 text-xl font-bold text-slate-800 text-left">{editingId ? 'ဝယ်ယူပြန်ပို့ဘောင်ချာ ပြင်ရန်' : 'ဝယ်ယူပြန်ပို့ဘောင်ချာ အသစ်'}</h2>
              </div>
            </div>
            <button type="button" onClick={() => setIsRefundModalOpen(true)} className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 text-sm font-bold text-white hover:bg-indigo-700 lg:w-auto"><CreditCard size={16} /> ပြန်အမ်းငွေ ဖွင့်မည်</button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-5">
          <div className="min-w-0 space-y-5">
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm p-4 sm:p-5 space-y-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">ဘောင်ချာအရင်းအမြစ်</h3>
                  <p className="text-xs text-slate-500">Supplier နှင့် ဝယ်ယူမှုဘောင်ချာကို အရင်ရွေးပါ။</p>
                </div>
                {purchaseLoading && <span className="text-[11px] font-semibold text-indigo-600">ဖတ်နေသည်...</span>}
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-6">
                <div className="flex min-w-0 flex-col gap-1.5 xl:col-span-3">
                  <label className="h-4 text-[10px] font-bold text-slate-400 uppercase tracking-wider">Supplier</label>
                  <input
                    list="pr-suppliers"
                    value={supplierSearch}
                    onChange={(e) => onSupplierSearch(e.target.value)}
                    placeholder="Supplier ရွေးပါ..."
                    className={`h-10 w-full rounded-lg border bg-slate-50 px-3 text-sm transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${supplierId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                  />
                  <datalist id="pr-suppliers">
                    {suppliers.map((s) => <option key={s.id} value={supplierLabel(s)} />)}
                  </datalist>
                </div>
                <div className="flex min-w-0 flex-col gap-1.5 xl:col-span-3">
                  <label className="h-4 text-[10px] font-bold text-slate-400 uppercase tracking-wider">ဝယ်ယူမှုဘောင်ချာ</label>
                  <input
                    list="pr-purchases"
                    value={purchaseSearch}
                    onChange={(e) => onPurchaseSearch(e.target.value)}
                    placeholder={supplierId > 0 ? 'ဝယ်ယူမှုဘောင်ချာ ရွေးပါ...' : 'Supplier ကိုအရင်ရွေးပါ'}
                    disabled={supplierId <= 0}
                    className={`h-10 w-full rounded-lg border bg-slate-50 px-3 text-sm transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-60 disabled:cursor-not-allowed ${purchaseId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                  />
                  <datalist id="pr-purchases">
                    {filteredPurchases.map((p) => <option key={p.id} value={purchaseLabel(p)} />)}
                  </datalist>
                </div>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-[220px_minmax(0,1fr)] gap-4">
                <div className="flex min-w-0 flex-col gap-1.5">
                  <label className="h-4 text-[10px] font-bold text-slate-400 uppercase tracking-wider">ပြန်ပို့ရက်စွဲ</label>
                  <input type="datetime-local" value={returnDate} onChange={(e) => setReturnDate(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
                </div>
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <div><p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">မူလကျသင့်ငွေ</p><p className="text-sm font-black text-slate-800">{money(originalPurchaseNet)}</p></div>
                    <div><p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ပြန်ပို့ပြီးငွေ</p><p className="text-sm font-black text-slate-800">{money(existingReturnAmount)}</p></div>
                    <div><p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ပေးပြီးငွေ</p><p className="text-sm font-black text-slate-800">{money(selectedPurchase?.paidAmount || 0)}</p></div>
                    <div><p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">ကျန်ငွေဟောင်း</p><p className="text-sm font-black text-slate-800">{money(selectedPurchase?.dueAmount || 0)}</p></div>
                  </div>
                </div>
              </div>

              <div className="flex min-w-0 flex-col gap-1.5">
                <label className="h-4 text-[10px] font-bold text-slate-400 uppercase tracking-wider">အကြောင်းပြချက်</label>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="ပြန်ပို့ရသည့်အကြောင်းပြချက် ရေးပါ၊ ဥပမာ ပျက်စီး၊ ပစ္စည်းမှား၊ Supplier လဲပေး..." className={`w-full resize-none rounded-lg border bg-slate-50 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${reason.trim() ? 'border-slate-200' : 'border-rose-200'}`} />
              </div>
            </div>

            {selectedPurchase && (
              <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                <div className="flex flex-col gap-1 border-b border-slate-100 bg-slate-50/70 p-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-800">ဤဝယ်ယူမှုမှ ပြန်ပို့နိုင်သော ပစ္စည်းများ</h3>
                    <p className="text-xs text-slate-500">ယခင်ပြန်မပို့ရသေးသော qty/serial များကိုသာ ပြထားသည်။</p>
                  </div>
                  <span className="text-[11px] font-bold text-slate-500">{activeReturnCount} ကြိမ် ပြန်ပို့ပြီး</span>
                </div>
                <div className="overflow-auto">
                  <table className="w-full min-w-[680px] text-left text-xs">
                    <thead className="bg-white text-[10px] font-bold uppercase tracking-wider text-slate-500">
                      <tr>
                        <th className="border-b border-slate-100 px-4 py-3">ပစ္စည်း</th>
                        <th className="border-b border-slate-100 px-4 py-3 text-right">ဝယ်ယူခဲ့</th>
                        <th className="border-b border-slate-100 px-4 py-3 text-right">ပြန်ပို့ပြီး</th>
                        <th className="border-b border-slate-100 px-4 py-3 text-right">ပြန်ပို့နိုင်</th>
                        <th className="border-b border-slate-100 px-4 py-3 text-right">တစ်ခုဝယ်ဈေး</th>
                        <th className="border-b border-slate-100 px-4 py-3">Serial များ</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {productOptions.length > 0 ? productOptions.map((option) => (
                        <tr key={option.productId} className="hover:bg-slate-50/70">
                          <td className="px-4 py-3 font-semibold text-slate-700">{option.productName}</td>
                          <td className="px-4 py-3 text-right text-slate-600">{option.purchasedQty}</td>
                          <td className="px-4 py-3 text-right text-slate-600">{option.returnedQty}</td>
                          <td className="px-4 py-3 text-right font-black text-emerald-700">{option.returnableQty}</td>
                          <td className="px-4 py-3 text-right text-slate-700">{money(option.unitPrice)}</td>
                          <td className="px-4 py-3 text-slate-500">{option.serialNumbers.length > 0 ? `${option.serialNumbers.length} serial` : 'အရေအတွက် item'}</td>
                        </tr>
                      )) : (
                        <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-400">ဤဝယ်ယူမှုအတွက် ပြန်ပို့နိုင်သော ပစ္စည်းမကျန်တော့ပါ။</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
              <div className="flex flex-col gap-3 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h3 className="font-bold text-slate-800 text-sm">ပြန်ပို့မည့် ပစ္စည်းများ</h3>
                  <p className="text-xs text-slate-500">အရေအတွက် item ဖြစ်ပါက stock ထဲမှနှုတ်မည်။ Serial item ဖြစ်ပါက serial ရွေးပြီး inventory ထဲမှဖယ်မည်။</p>
                </div>
                <button onClick={() => setDetails((d) => [...d, emptyDetail()])} className="inline-flex h-9 w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-3 text-xs font-bold text-white hover:bg-indigo-700 sm:w-auto"><Plus size={14} /> အတန်းထည့်</button>
              </div>
              <div className="overflow-auto">
                <table className="w-full text-left border-collapse min-w-[760px]">
                  <thead className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                    <tr>
                      <th className="px-4 py-3 border-b border-slate-100">ပစ္စည်း</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-24">အရေအတွက်</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-32">တစ်ခုဈေး</th>
                      <th className="px-4 py-3 border-b border-slate-100 w-36 text-right">ကျသင့်ငွေ</th>
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
                                placeholder={purchaseId > 0 ? 'ပစ္စည်းရွေးပါ...' : 'ဝယ်ယူမှုကိုအရင်ရွေးပါ'}
                                disabled={purchaseId <= 0 || productOptions.length === 0}
                                className={`h-9 w-full rounded-md border border-transparent bg-slate-50 px-2 text-sm focus:border-indigo-200 focus:bg-white focus:ring-2 focus:ring-indigo-500/10 focus:outline-none disabled:opacity-60 disabled:cursor-not-allowed ${d.productId > 0 ? 'text-slate-700' : 'text-rose-500'}`}
                              />
                              <datalist id={`pr-products-${i}`}>
                                {productOptions.map((p) => <option key={p.productId} value={productLabel(p)} />)}
                              </datalist>
                            </td>
                            <td className="px-4 py-3 align-top">
                              <input type="number" min="1" max={option?.returnableQty || undefined} value={d.qty || ''} onChange={(e) => onDetailChange(i, 'qty', e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-slate-50 px-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/10 focus:outline-none" />
                              {option && <p className="text-[10px] text-slate-400 mt-1">အများဆုံး {option.returnableQty}</p>}
                            </td>
                            <td className="px-4 py-3 align-top">
                              <input type="number" min="0" step="0.01" value={d.unitPrice || ''} onChange={(e) => onDetailChange(i, 'unitPrice', e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-slate-50 px-2 text-sm focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/10 focus:outline-none" />
                            </td>
                            <td className="px-4 py-3 text-right align-top font-bold text-slate-700">{money(d.subtotal)}</td>
                            <td className="px-4 py-3 text-center align-top">
                              <button onClick={() => setDetails((prev) => prev.length <= 1 ? prev : prev.filter((_, idx) => idx !== i))} className="p-1.5 text-slate-300 hover:text-rose-500 disabled:opacity-40" disabled={details.length <= 1} title="Remove row"><Trash2 size={14} /></button>
                            </td>
                          </tr>
                          {d.productId > 0 && d.qty > 0 && serialOptions.length > 0 && (
                            <tr className="bg-slate-50/60">
                              <td colSpan={5} className="px-4 py-3">
                                <div className="space-y-2">
                                  <div className="flex items-center justify-between text-[10px] text-slate-500">
                                    <span className="font-bold uppercase tracking-wider">Serial နံပါတ်များ ({d.qty})</span>
                                    <span>{serialOptions.length} ရနိုင်, {selectedSerialCount} ရွေးထား</span>
                                  </div>
                                  <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
                                    {d.serialNumbers.map((serial, serialIndex) => (
                                      <input
                                        key={serialIndex}
                                        list={`pr-serial-options-${i}`}
                                        type="text"
                                        value={serial}
                                        onChange={(e) => onSerialChange(i, serialIndex, e.target.value)}
                                        placeholder={`Serial #${serialIndex + 1}`}
                                        className="h-9 rounded-md border border-slate-200 bg-white px-2 text-[11px] focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
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

          {isRefundModalOpen && (
            <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4" onMouseDown={() => setIsRefundModalOpen(false)}>
              <div className="h-[100dvh] w-full overflow-y-auto rounded-none bg-white shadow-xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-xl sm:rounded-2xl" onMouseDown={(e) => e.stopPropagation()}>
            <div className="min-h-full bg-white p-4 pb-8 space-y-4 sm:min-h-0 sm:rounded-2xl sm:p-5">
              <div className="sticky top-0 z-10 -mx-4 -mt-4 flex items-center justify-between gap-3 border-b border-slate-100 bg-white px-4 pt-4 pb-4 sm:static sm:mx-0 sm:mt-0 sm:p-0 sm:pb-0 sm:border-0">
                <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2"><RotateCcw size={16} className="text-indigo-500" /> ပြန်အမ်းငွေ နှင့် စာရင်း</h3>
                <button type="button" onClick={() => setIsRefundModalOpen(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" title="ပိတ်မည်"><X size={18} /></button>
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2">
                <div className="flex justify-between items-center text-sm"><span className="text-slate-500">ယခုပြန်ပို့ငွေ</span><span className="font-black text-slate-800">{money(total)}</span></div>
                <div className="flex justify-between items-center text-xs"><span className="text-slate-500">ပြန်ပို့ပြီး ကျန်ငွေ</span><span className="font-bold text-slate-700">{money(dueAfterReturn)}</span></div>
                <div className="flex justify-between items-center text-xs"><span className="text-slate-500">အများဆုံး ငွေပြန်အမ်းနိုင်</span><span className="font-bold text-emerald-700">{money(maxRefund)}</span></div>
                <div className="flex justify-between items-center text-xs"><span className="text-slate-500">ကျန်မည့် Supplier credit</span><span className="font-bold text-indigo-700">{money(supplierCreditAfterRefund)}</span></div>
              </div>

              <div className="rounded-lg border border-indigo-100 bg-indigo-50/70 p-3 text-xs text-indigo-900">
                <p className="font-bold">{refundMode}</p>
                <p className="mt-1 text-indigo-700">ပြန်အမ်းငွေကို 0 ထားပါက ပေးရန်ကျန်ငွေထဲမှသာ လျှော့မည်။ Supplier က ငွေသား/ဘဏ်ဖြင့် တကယ်ပြန်အမ်းမှ ပြန်အမ်းငွေထည့်ပါ။</p>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ပြန်အမ်းငွေ</label>
                <input type="number" min="0" max={maxRefund || undefined} step="0.01" value={refundAmount} onChange={(e) => { setRefundAmount(e.target.value); if (refundPayments.length > 0) setRefundPayments([]); }} placeholder="0 = ကျန်ငွေ/credit ထဲမှသာ လျှော့မည်" className={`h-10 w-full rounded-lg border bg-slate-50 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${validRefund ? 'border-slate-200' : 'border-rose-200'}`} />
                {!validRefund ? <p className="text-[10px] text-rose-500">ပြန်အမ်းငွေသည် 0 မှ {money(maxRefund)} အတွင်း ဖြစ်ရမည်။</p> : <p className="text-[10px] text-slate-400">Supplier က ငွေသား/ဘဏ်ဖြင့် ပြန်ပေးသော ပမာဏကိုသာထည့်ပါ။</p>}
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ငွေပေးချေမှုနည်းလမ်း</label>
                <select value={paymentMethodId} onChange={(e) => setPaymentMethodId(Number(e.target.value))} className={`h-10 w-full rounded-lg border bg-slate-50 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${!paymentRequired || paymentMethodId > 0 ? 'border-slate-200' : 'border-rose-200'}`}>
                  <option value={0}>ငွေပေးချေမှုနည်းလမ်း ရွေးပါ</option>
                  {paymentMethods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                </select>
                <p className="text-[10px] text-slate-400">ပြန်အမ်းငွေ 0 ထက်များမှ လိုအပ်သည်။</p>
              </div>

              <SplitPaymentEditor
                methods={paymentMethods}
                payments={refundPayments}
                onChange={(next) => {
                  setRefundPayments(next);
                  const totalPaid = paymentTotal(next);
                  setRefundAmount(totalPaid > 0 ? String(totalPaid.toFixed(2)) : '');
                }}
                label="ပြန်အမ်းငွေ ခွဲပေးရန်"
              />

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">ငွေလွှဲအမှတ်</label>
                <input type="text" value={transactionNo} onChange={(e) => setTransactionNo(e.target.value)} placeholder="ငွေလွှဲအမှတ် ရှိပါကထည့်ပါ" className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
              </div>

              <div className="space-y-1.5">
                {!serialValidation.qtyMatchesSerialCount && <p className="text-[10px] text-rose-500">ပြန်ပို့ item တစ်ကြောင်းစီတွင် qty နှင့်ညီသော serial နံပါတ်များ လိုအပ်သည်။</p>}
                {serialValidation.qtyMatchesSerialCount && !serialValidation.allRowsHaveSerials && <p className="text-[10px] text-rose-500">Serial နံပါတ်ကွက်တိုင်း ဖြည့်ရန်လိုအပ်သည်။</p>}
                {!serialValidation.uniqueAcrossRows && <p className="text-[10px] text-rose-500">Serial နံပါတ် ထပ်နေ၍မရပါ။</p>}
                {!serialValidation.belongsToSelectedProduct && serialValidation.strictCheckAvailable && <p className="text-[10px] text-rose-500">Serial နံပါတ်သည် ရွေးထားသောဝယ်ယူမှုထဲမှ ပစ္စည်းနှင့်ကိုက်ညီရမည်။</p>}
              </div>

              <button onClick={onSave} disabled={!validForm || saving} className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold transition-all ${validForm && !saving ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}>
                <Save size={16} /> {saving ? 'Saving...' : editingId ? 'ပြန်ပို့ဘောင်ချာ ပြင်မည်' : 'ဝယ်ယူပြန်ပို့မှု အတည်ပြုမည်'}
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
          <button onClick={openCreate} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700"><Plus size={16} /> ဝယ်ယူပြန်ပို့ဘောင်ချာ အသစ်</button>
        </div>
      <div className="grid flex-1 grid-cols-1 gap-4 sm:grid-cols-3 2xl:order-1">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ဘောင်ချာစုစုပေါင်း</p><p className="text-2xl font-bold text-slate-800">{stats.count}</p></div><div className="w-11 h-11 rounded-lg bg-indigo-50 flex items-center justify-center"><List size={20} className="text-indigo-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">စုစုပေါင်း ပြန်ပို့ပြီး</p><p className="text-2xl font-bold text-slate-800">{money(stats.total)}</p></div><div className="w-11 h-11 rounded-lg bg-slate-100 flex items-center justify-center"><RotateCcw size={20} className="text-slate-600" /></div></div>
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between"><div><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ပြန်အမ်းငွေစုစုပေါင်း</p><p className="text-2xl font-bold text-emerald-700">{money(stats.refund)}</p></div><div className="w-11 h-11 rounded-lg bg-emerald-50 flex items-center justify-center"><RotateCcw size={20} className="text-emerald-600" /></div></div>
      </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col lg:flex-row lg:items-center gap-4">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="ပြန်ပို့အမှတ်၊ Supplier သို့မဟုတ် အကြောင်းပြချက် ရှာရန်..." className="w-full pl-9 pr-9 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500" />
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
          <h3 className="font-bold text-slate-800 text-sm">ဝယ်ယူပြန်ပို့ ဘောင်ချာs</h3>
          {masterLoading && <span className="text-[11px] text-slate-400">အချက်အလက်များ ဖတ်နေသည်...</span>}
        </div>
        <div className="px-4 pt-3">
          <BulkSelectionToolbar visibleCount={visibleReturnRows.length} selectedCount={bulk.selectedCount} allVisibleSelected={bulk.allVisibleSelected} someVisibleSelected={bulk.someVisibleSelected} onToggleVisible={() => bulk.allVisibleSelected ? bulk.clear() : bulk.selectVisible()} onClear={bulk.clear} selectedRows={bulk.selectedRows} selectedTotal={bulk.selectedRows.reduce((sum, row) => sum + (Number(row.totalReturnAmount) || 0), 0)} totalLabel="Selected Total" actions={[{ key: 'export', label: 'Export selected', icon: <Download size={13} />, tone: 'indigo' }]} onAction={handleBulkAction} />
        </div>
        <div className="overflow-auto max-h-[60vh] custom-scrollbar">
          {loading ? <div className="p-8 text-center text-slate-400">ဖတ်နေသည်...</div> : (
            <table className="w-full text-left border-collapse min-w-[920px]">
              <thead className="sticky top-0 bg-white z-10 shadow-sm">
                <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                  <th className="px-3 py-3 border-b border-slate-100">Select</th>
                  <th className="px-4 py-3 border-b border-slate-100">ပြန်ပို့အမှတ်</th>
                  <th className="px-4 py-3 border-b border-slate-100">Purchase</th>
                  <th className="px-4 py-3 border-b border-slate-100">Supplier</th>
                  <th className="px-4 py-3 border-b border-slate-100">ရက်စွဲ</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">စုစုပေါင်း</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">Refund</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-center">အခြေအနေ</th>
                  <th className="px-4 py-3 border-b border-slate-100">အကြောင်းပြချက်</th>
                  <th className="px-4 py-3 border-b border-slate-100 text-right">လုပ်ဆောင်ချက်</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.length > 0 ? filtered.map((r) => {
                  const voided = r.status === 'VOIDED';
                  return (
                  <tr key={r.id || r.returnNo} className={`hover:bg-slate-50 text-xs ${voided ? 'bg-slate-50/70' : ''}`}>
                    <td className="px-3 py-3 text-center"><input type="checkbox" checked={bulk.selectedIds.has(r.id as number)} onChange={() => bulk.toggle(r.id as number)} className="h-4 w-4 accent-indigo-600" aria-label={`Select purchase return ${r.returnNo || r.id}`} /></td>
                    <td className="px-4 py-3 font-medium text-slate-800">{r.returnNo || `#${r.id}`}</td>
                    <td className="px-4 py-3 text-slate-600">
                      {r.purchaseId > 0 ? (
                        <Link
                          to={`${AppRoute.PURCHASES}?purchaseId=${r.purchaseId}`}
                          className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium"
                          title="သက်ဆိုင်သောဝယ်ယူမှု ဖွင့်ရန်"
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
                        {voided ? 'ပယ်ဖျက်ပြီး' : 'အတည်ပြုပြီး'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-500 max-w-[260px] truncate">{r.reason || '-'}</td>
                    <td className="px-4 py-3 text-right"><div className="inline-flex items-center gap-1">
                      <button onClick={() => r.id && openView(r.id)} className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-indigo-50" title="ကြည့်ရန်"><Eye size={15} /></button>
                      {!voided && <button onClick={() => onVoid(r)} className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50" title="ပယ်ဖျက်ရန်"><X size={15} /></button>}
                    </div></td>
                  </tr>
                );}) : <tr><td colSpan={9} className="px-4 py-10 text-center text-slate-400">လက်ရှိ filter နှင့်ကိုက်သော ဝယ်ယူပြန်ပို့မှတ်တမ်း မရှိပါ။</td></tr>}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination */}
        {!loading && totalPages > 0 && (
          <div className="px-4 py-3 border-t border-slate-100 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
            <p className="text-xs text-slate-500">
              ပြနေသည် {totalElements === 0 ? 0 : currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, totalElements)} / {totalElements.toLocaleString()}
            </p>
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-1.5">
                <span className="text-xs text-slate-500">ပြရန်</span>
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
              <h3 className="font-bold text-slate-800">ဝယ်ယူပြန်ပို့မှု: {viewRow.returnNo || `#${viewRow.id}`}</h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => {
                    const { html, popupSize } = buildPurchaseReturnVoucherHtml({ row: viewRow, settings: getCachedCompanySettings() });
                    const w = window.open('', '_blank', popupSize);
                    if (!w) return;
                    w.document.write(html);
                    w.document.close();
                  }}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg hover:bg-indigo-100"
                >
                  <Printer size={14} /> Print
                </button>
                <button onClick={() => { setViewRow(null); setReturnStockMovements([]); }} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"><X size={18} /></button>
              </div>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600">
                  <span className="font-medium text-slate-500">ဝယ်ယူမှု:</span>{' '}
                  {viewRow.purchaseId > 0 ? (
                    <Link
                      to={`${AppRoute.PURCHASES}?purchaseId=${viewRow.purchaseId}`}
                      className="text-indigo-600 hover:text-indigo-700 hover:underline font-medium"
                      title="သက်ဆိုင်သောဝယ်ယူမှု ဖွင့်ရန်"
                    >
                      {purchaseLabelById(viewRow.purchaseId)}
                    </Link>
                  ) : (
                    '-'
                  )}
                </p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Supplier:</span> {viewRow.supplierName || supplierLabelById(purchases.find((p) => p.id === viewRow.purchaseId)?.supplierId)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">ပြန်ပို့ရက်စွဲ:</span> {viewRow.returnDate ? new Date(viewRow.returnDate).toLocaleString() : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">စုစုပေါင်း Return:</span> {money(viewRow.totalReturnAmount || 0)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">ပြန်အမ်းငွေ:</span> {money(viewRow.refundAmount ?? viewRow.totalReturnAmount ?? 0)}</p>
              </div>
              {viewRow.reason && <p className="text-sm text-slate-600"><span className="font-medium text-slate-500">အကြောင်းပြချက်:</span> {viewRow.reason}</p>}
              <table className="w-full text-left border-collapse text-sm">
                <thead><tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold"><th className="px-3 py-2 border-b">ပစ္စည်း</th><th className="px-3 py-2 border-b w-16">အရေအတွက်</th><th className="px-3 py-2 border-b text-right">တစ်ခုဈေး</th><th className="px-3 py-2 border-b text-right">ကျသင့်ငွေ</th><th className="px-3 py-2 border-b">Serial များ</th></tr></thead>
                <tbody className="divide-y divide-slate-100">{(viewRow.details || []).map((d, i) => <tr key={i}><td className="px-3 py-2">{d.productName || `ပစ္စည်း #${d.productId}`}</td><td className="px-3 py-2">{d.qty}</td><td className="px-3 py-2 text-right">{money(d.unitPrice)}</td><td className="px-3 py-2 text-right font-medium">{money(d.subtotal)}</td><td className="px-3 py-2 text-[11px] text-slate-500">{d.serialNumbers && d.serialNumbers.length > 0 ? d.serialNumbers.join(', ') : '-'}</td></tr>)}</tbody>
              </table>
              <section className="pt-2">
                <div className="mb-2 flex items-center justify-between"><h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Refund Payment History</h4><span className="text-[11px] text-slate-400">{viewRow.payments?.length || 0} payment(s)</span></div>
                {!viewRow.payments?.length ? <p className="py-3 text-xs text-slate-400">No refund payment history found.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200"><table className="w-full min-w-[600px] text-left text-sm"><thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Payment Method</th><th className="border-b px-3 py-2">Transaction No</th><th className="border-b px-3 py-2 text-right">Amount</th></tr></thead><tbody className="divide-y divide-slate-100">{viewRow.payments.map((payment, index) => <tr key={payment.id || `${payment.transactionNo}-${index}`}><td className="px-3 py-2 text-slate-600">{payment.paymentDate ? new Date(payment.paymentDate).toLocaleString() : '-'}</td><td className="px-3 py-2 text-slate-600">{payment.paymentMethodName || (payment.paymentMethodId ? `#${payment.paymentMethodId}` : '-')}</td><td className="px-3 py-2 text-slate-500">{payment.transactionNo || '-'}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">{money(payment.amount || 0)}</td></tr>)}</tbody></table></div>}
              </section>
              <section className="pt-2">
                <div className="mb-2 flex items-center justify-between"><h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Stock Movement</h4><span className="text-[11px] text-slate-400">{returnStockMovements.length} movement(s)</span></div>
                {!returnStockMovements.length ? <p className="py-3 text-xs text-slate-400">No stock movement found for this return.</p> : <div className="overflow-x-auto rounded-lg border border-slate-200"><table className="w-full min-w-[660px] text-left text-sm"><thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Product</th><th className="border-b px-3 py-2">Type</th><th className="border-b px-3 py-2 text-right">Out</th><th className="border-b px-3 py-2 text-right">Balance</th></tr></thead><tbody className="divide-y divide-slate-100">{returnStockMovements.map((movement, index) => <tr key={movement.id || `${movement.productId}-${index}`}><td className="px-3 py-2 text-slate-600">{movement.date ? new Date(movement.date).toLocaleString() : '-'}</td><td className="px-3 py-2"><p className="font-semibold text-slate-700">{movement.productName || '-'}</p><p className="text-[10px] text-slate-400">{movement.productCode || ''}</p></td><td className="px-3 py-2 text-slate-500">{movement.type}</td><td className="px-3 py-2 text-right font-bold text-rose-700">-{movement.quantityOut.toLocaleString()}</td><td className="px-3 py-2 text-right font-bold text-slate-700">{movement.balance.toLocaleString()}</td></tr>)}</tbody></table></div>}
              </section>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseReturnManagement;

