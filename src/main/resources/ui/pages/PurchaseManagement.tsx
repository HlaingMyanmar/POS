import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { useLocation, useNavigate } from 'react-router-dom';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { purchaseApiService, PurchasePage, PurchaseStats } from '../services/purchaseapiservice';
import { purchaseReturnApiService } from '../services/purchasereturnapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { accountingApiService } from '../services/accountingapiservice';
import { supplierService } from '../services/supplierapiservice';
import { staffService } from '../services/staffapiservice';
import { productService } from '../services/productapiservice';
import { AppRoute, PurchaseDTO, PurchaseDetailDTO, SupplierDTO, StaffDTO, ProductDTO, PaymentMethodDTO, PaymentTransactionDTO, PurchaseReturnDTO, ProductStockHistoryMovementDTO } from '../types';
import { Plus, Trash2, Save, ShoppingCart, Hash, DollarSign, User, List, Eye, X, RefreshCw, ArrowLeft, FileText, AlertCircle, CheckCircle, Search, Calendar, Filter, CreditCard, Box, Printer, Camera, Share2, ChevronDown, ChevronUp, Download } from 'lucide-react';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { buildPurchaseVoucherHtml } from './purchaseVoucherTemplate';
import { getCachedCompanySettings } from '../utils/companySettings';
import { getFromSession } from '../utils/storageHelper';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import Swal from 'sweetalert2';

type PurchaseDetailForm = PurchaseDetailDTO & { productSearch?: string; assignSerials?: boolean };
const resizeSerials = (serials: string[] = [], qty: number) => {
  const safeQty = Math.max(0, qty || 0);
  const next = [...serials];
  if (next.length > safeQty) return next.slice(0, safeQty);
  if (next.length < safeQty) return [...next, ...Array(safeQty - next.length).fill('')];
  return next;
};
const resizeStrings = (arr: string[] = [], size: number) => {
  const n = Math.max(0, size);
  if (arr.length > n) return arr.slice(0, n);
  if (arr.length < n) return [...arr, ...Array(n - arr.length).fill('')];
  return arr;
};
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

const dateInput = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const parseDateInput = (date: string) => {
  const [year, month, day] = date.split('-').map(Number);
  return year && month && day ? new Date(year, month - 1, day) : new Date();
};

const addDaysInput = (date: string, days: number) => {
  const base = date ? parseDateInput(date) : new Date();
  base.setDate(base.getDate() + Math.max(0, Number(days) || 0));
  return dateInput(base);
};

const getTodayRange = () => {
  const today = new Date();
  return { from: dateInput(today), to: dateInput(today) };
};

const getThisWeekRange = () => {
  const today = new Date();
  const start = new Date(today);
  const day = start.getDay();
  start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
  return { from: dateInput(start), to: dateInput(today) };
};

const getThisMonthRange = () => {
  const today = new Date();
  return { from: dateInput(new Date(today.getFullYear(), today.getMonth(), 1)), to: dateInput(today) };
};

const PurchaseManagement: React.FC = () => {
  const currentUser = useMemo(() => {
    try {
      return JSON.parse(getFromSession('sspd_user') || '{}') as { staffId?: number; roles?: string[]; permissions?: string[] };
    } catch {
      return {};
    }
  }, []);
  const canOverrideStaff = (currentUser.roles || []).some((role) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role))
    || (currentUser.permissions || []).includes('CAN_ACCESS_PURCHASE_STAFF_OVERRIDE');
  const location = useLocation();
  const navigate = useNavigate();
  const [showNewVoucherForm, setShowNewVoucherForm] = useState(false);
  const [purchases, setPurchases] = useState<PurchaseDTO[]>([]);
  const [purchasesLoading, setPurchasesLoading] = useState(true);
  const [purchasePage, setPurchasePage] = useState(0);
  const [purchasePageSize, setPurchasePageSize] = useState(20);
  const [purchaseTotalElements, setPurchaseTotalElements] = useState(0);
  const [purchaseTotalPages, setPurchaseTotalPages] = useState(0);
  const [viewPurchase, setViewPurchase] = useState<PurchaseDTO | null>(null);
  const [relatedReturns, setRelatedReturns] = useState<PurchaseReturnDTO[]>([]);
  const [relatedReturnsLoading, setRelatedReturnsLoading] = useState(false);
  const [purchaseHistoryPayments, setPurchaseHistoryPayments] = useState<PaymentTransactionDTO[]>([]);
  const [purchaseStockMovements, setPurchaseStockMovements] = useState<ProductStockHistoryMovementDTO[]>([]);
  const [suppliers, setSuppliers] = useState<SupplierDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [paymentMethods, setPaymentMethods] = useState<PaymentMethodDTO[]>([]);
  
  const [selectedSupplierId, setSelectedSupplierId] = useState<number>(0);
  const [selectedStaffId, setSelectedStaffId] = useState<number>(0);
  const [supplierSearch, setSupplierSearch] = useState('');
  const [staffSearch, setStaffSearch] = useState('');
  const [supplierOpen, setSupplierOpen] = useState(false);
  const [staffOpen, setStaffOpen] = useState(false);
  const [purchaseDate, setPurchaseDate] = useState(dateInput(new Date()));
  const [paymentTermDays, setPaymentTermDays] = useState(30);
  const [dueDate, setDueDate] = useState(addDaysInput(dateInput(new Date()), 30));
  const [paidAmount, setPaidAmount] = useState<number>(0);
  const [purchasePayments, setPurchasePayments] = useState<PaymentTransactionDTO[]>([]);
  const [discountAmount, setDiscountAmount] = useState<number>(0);
  const [remark, setRemark] = useState('');
  const [selectedPaymentMethodId, setSelectedPaymentMethodId] = useState<number>(0);
  const [transactionNo, setTransactionNo] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [voucherLookup, setVoucherLookup] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [filterStatus, setFilterStatus] = useState<'All' | 'Paid' | 'Partial' | 'Due'>('All');
  const defaultDateRange = getTodayRange();
  const [dateFrom, setDateFrom] = useState(defaultDateRange.from);
  const [dateTo, setDateTo] = useState(defaultDateRange.to);
  const [dateShortcut, setDateShortcut] = useState<'TODAY' | 'WEEK' | 'MONTH' | 'ALL' | 'CUSTOM'>('TODAY');
  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const [serverStats, setServerStats] = useState<PurchaseStats>({ count: 0, totalAmount: 0, paidAmount: 0, dueAmount: 0 });
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [isQuickSupplierModalOpen, setIsQuickSupplierModalOpen] = useState(false);
  const [quickSupplierSaving, setQuickSupplierSaving] = useState(false);
  const [quickSupplierForm, setQuickSupplierForm] = useState({ name: '', phone: '', address: '' });
  const [isVoucherPaymentModalOpen, setIsVoucherPaymentModalOpen] = useState(false);
  const [previewPurchase, setPreviewPurchase] = useState<PurchaseDTO | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [sendToPurchase, setSendToPurchase] = useState<PurchaseDTO | null>(null);
  const [sendToLoading, setSendToLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [paymentSaving, setPaymentSaving] = useState(false);
  const [paymentForm, setPaymentForm] = useState({
    purchaseId: 0,
    amount: '',
    paymentMethodId: 0,
    transactionNo: '',
    payments: [] as PaymentTransactionDTO[]
  });
  
  const [details, setDetails] = useState<PurchaseDetailForm[]>([
    { productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, batchNumber: '', expiryDate: '', itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }
  ]);

  const fetchPurchases = useCallback(async (page: number, size: number, search: string, from: string, to: string) => {
    setPurchasesLoading(true);
    try {
      const result: PurchasePage = await purchaseApiService.getAllPaged(page, size, search, from, to);
      setPurchases(result.content);
      setPurchaseTotalElements(result.totalElements);
      setPurchaseTotalPages(result.totalPages);
    } catch (e) {
      console.error('Failed to load purchases', e);
    } finally {
      setPurchasesLoading(false);
    }
  }, []);

  const fetchStats = useCallback(async (from: string, to: string) => {
    try {
      const stats = await purchaseApiService.getStats(from, to);
      setServerStats(stats);
    } catch (e) {
      console.error('Failed to load stats', e);
    }
  }, []);

  const fetchMasterData = useCallback(async () => {
    try {
      const [supRes, staffRes, prodRes, payRes] = await Promise.all([
        supplierService.getAll(),
        staffService.getAll(),
        productService.getAll(),
        paymentMethodService.getAllActive()
      ]);
      setSuppliers(supRes);
      setStaffs(staffRes);
      const linkedStaff = staffRes.find((staff) => staff.id === currentUser.staffId);
      setSelectedStaffId((previous) => previous || linkedStaff?.id || (canOverrideStaff ? staffRes[0]?.id || 0 : 0));
      if (linkedStaff) setStaffSearch(linkedStaff.name);
      setProducts(prodRes);
      setPaymentMethods(payRes);
    } catch (error) {
      console.error('Error fetching data:', error);
    }
  }, []);

  useEffect(() => { fetchMasterData(); }, [fetchMasterData]);
  useRefreshOnTabActivate(fetchMasterData);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setPurchasePage(0);
      setDebouncedSearch(searchTerm.trim());
    }, 400);
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current); };
  }, [searchTerm]);

  useEffect(() => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
  }, [fetchPurchases, fetchStats, purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo]);
  useDataEvents(['Purchase'], () => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
  });

  const generateSerialNumbers = (qty: number, existingAll: string[] = []): string[] => {
    const now = new Date();
    const p = (n: number, len = 2) => String(n).padStart(len, '0');
    const prefix = `${now.getFullYear()}${p(now.getMonth()+1)}${p(now.getDate())}${p(now.getHours())}${p(now.getMinutes())}${p(now.getSeconds())}`;
    const used = new Set(existingAll);
    const result: string[] = [];
    while (result.length < qty) {
      const candidate = `${prefix}${String(Math.floor(Math.random() * 1_000)).padStart(3, '0')}`;
      if (!used.has(candidate)) { used.add(candidate); result.push(candidate); }
    }
    return result;
  };

  const handleAddRow = () => {
    setDetails([...details, { productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, batchNumber: '', expiryDate: '', itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }]);
  };

  const handleRemoveRow = (index: number) => {
    if (details.length <= 1) return;
    const newDetails = [...details];
    newDetails.splice(index, 1);
    setDetails(newDetails);
  };

  const isSerialRequired = useCallback((productId: number) => {
    const product = products.find((p) => p.id === productId);
    return product ? product.hasSerial !== false : true;
  }, [products]);

  const handleDetailChange = (index: number, field: keyof PurchaseDetailDTO, value: any) => {
    const newDetails = [...details];
    const detail = { ...newDetails[index], [field]: value };

    if (field === 'qty') {
      const qty = parseInt(value) || 0;
      detail.qty = qty;
      const baseWarranty = Number(detail.warrantyMonths ?? 0);
      const currentWarranties = detail.itemWarranties || [];
      const resizedWarranties = [...currentWarranties];
      if (resizedWarranties.length > qty) {
        detail.itemWarranties = resizedWarranties.slice(0, qty);
      } else if (resizedWarranties.length < qty) {
        detail.itemWarranties = [...resizedWarranties, ...Array(qty - resizedWarranties.length).fill(baseWarranty)];
      } else {
        detail.itemWarranties = resizedWarranties;
      }
      if (isSerialRequired(detail.productId)) {
        detail.serialNumbers    = resizeSerials(detail.serialNumbers || [], qty);
        detail.serialConditions = resizeStrings(detail.serialConditions || [], qty);
        detail.serialPhotos     = resizeStrings(detail.serialPhotos || [], qty);
      } else if (detail.assignSerials) {
        detail.serialNumbers    = resizeSerials(detail.serialNumbers || [], qty);
        detail.serialConditions = resizeStrings(detail.serialConditions || [], qty);
        detail.serialPhotos     = resizeStrings(detail.serialPhotos || [], qty);
      } else {
        detail.serialNumbers    = [];
        detail.serialConditions = [];
        detail.serialPhotos     = [];
      }
    }

    if (field === 'warrantyMonths') {
      const months = Math.max(0, Number(value) || 0);
      detail.warrantyMonths = months;
      detail.itemWarranties = Array.from({ length: Math.max(0, detail.qty || 0) }, () => months);
    }

    detail.subtotal = detail.qty * detail.unitCost;
    newDetails[index] = detail;
    setDetails(newDetails);
  };

  const handleItemWarrantyChange = (detailIndex: number, itemIndex: number, value: number) => {
    const newDetails = [...details];
    const row = { ...newDetails[detailIndex] };
    const list = [...(row.itemWarranties || Array.from({ length: row.qty || 0 }, () => row.warrantyMonths || 0))];
    list[itemIndex] = Math.max(0, Number(value) || 0);
    row.itemWarranties = list;
    row.warrantyMonths = list.length > 0 ? Math.min(...list) : (row.warrantyMonths || 0);
    newDetails[detailIndex] = row;
    setDetails(newDetails);
  };

  const applyWarrantyToAllItems = (detailIndex: number) => {
    const newDetails = [...details];
    const row = { ...newDetails[detailIndex] };
    const months = Math.max(0, Number(row.warrantyMonths) || 0);
    row.itemWarranties = Array.from({ length: Math.max(0, row.qty || 0) }, () => months);
    newDetails[detailIndex] = row;
    setDetails(newDetails);
  };

  const getProductLabel = (p: ProductDTO) => `${p.name} (${p.productCode}) [${p.productType ?? 'New'}]`;
  const getProductLabelById = (id: number) => {
    const p = products.find((x) => x.id === id);
    return p ? getProductLabel(p) : '';
  };

  const handleProductSearchChange = (index: number, value: string) => {
    const newDetails = [...details];
    const matched = products.find((p) => getProductLabel(p).toLowerCase() === value.toLowerCase());
    const serialNumbers = matched
      ? (matched.hasSerial !== false ? resizeSerials(newDetails[index].serialNumbers || [], newDetails[index].qty) : [])
      : [''];
    newDetails[index] = {
      ...newDetails[index],
      productSearch: value,
      productId: matched ? matched.id : 0,
      serialNumbers,
      serialConditions: resizeStrings(newDetails[index].serialConditions || [], serialNumbers.length),
      serialPhotos:     resizeStrings(newDetails[index].serialPhotos || [], serialNumbers.length),
      assignSerials: false,
    };
    setDetails(newDetails);
  };

  const handleSerialChange = (detailIndex: number, serialIndex: number, value: string) => {
    const newDetails = [...details];
    const serials = [...newDetails[detailIndex].serialNumbers];
    serials[serialIndex] = value;
    newDetails[detailIndex].serialNumbers = serials;
    setDetails(newDetails);
  };

  const handleConditionChange = (detailIndex: number, serialIndex: number, value: string) => {
    const newDetails = [...details];
    const conditions = [...(newDetails[detailIndex].serialConditions || [])];
    conditions[serialIndex] = value;
    newDetails[detailIndex].serialConditions = conditions;
    setDetails(newDetails);
  };

  const handleSerialPhotoChange = (detailIndex: number, serialIndex: number, file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const newDetails = [...details];
      const photos = [...(newDetails[detailIndex].serialPhotos || [])];
      photos[serialIndex] = reader.result as string;
      newDetails[detailIndex].serialPhotos = photos;
      setDetails(newDetails);
    };
    reader.readAsDataURL(file);
  };

  const serialEntries = details.flatMap((d) =>
    (d.serialNumbers || [])
      .map((serial) => serial.trim())
      .filter((serial) => serial.length > 0)
  );
  const duplicateSerials = serialEntries.filter((serial, index, arr) =>
    arr.findIndex((x) => x.toLowerCase() === serial.toLowerCase()) !== index
  );
  const hasDuplicateSerials = duplicateSerials.length > 0;

  const totalAmount = details.reduce((sum, d) => sum + d.subtotal, 0);
  const safeDiscountAmount = Math.min(Math.max(0, discountAmount || 0), totalAmount);
  const netAmount = Math.max(0, totalAmount - safeDiscountAmount);
  const normalizedPurchasePayments = normalizePayments(purchasePayments);
  const effectivePaidAmount = normalizedPurchasePayments.length > 0 ? paymentTotal(purchasePayments) : paidAmount;
  const dueAmount = Math.max(0, netAmount - effectivePaidAmount);
  const isValid = selectedSupplierId > 0
    && selectedStaffId > 0
    && details.every((d) => {
      if (d.productId <= 0 || d.qty <= 0 || d.unitCost <= 0) return false;
      const _prod = products.find((p) => p.id === d.productId);
      if (_prod?.hasSerial && (_prod?.unlinkedQty ?? 0) > 0) return false;
      if (!_prod?.hasSerial && (_prod?.stockQty ?? 0) > 0 && d.assignSerials) return false;
      if (isSerialRequired(d.productId) && d.serialNumbers?.some((sn) => !sn.trim())) return false;
      if (d.assignSerials && d.serialNumbers?.some((sn) => !sn.trim())) return false;
      return true;
    })
    && safeDiscountAmount <= totalAmount
    && effectivePaidAmount <= netAmount
    && !hasDuplicateSerials
    && !!purchaseDate
    && (dueAmount <= 0 || !!dueDate)
    && (effectivePaidAmount <= 0 || selectedPaymentMethodId > 0 || normalizedPurchasePayments.length > 0);

  const handleSave = async () => {
    if (!isValid || saving) return;
    setSaving(true);

    try {
      const payload: PurchaseDTO = {
        supplierId: selectedSupplierId,
        staffId: selectedStaffId,
        purchaseDate: `${purchaseDate}T00:00:00`,
        dueDate: dueAmount > 0 ? dueDate : undefined,
        totalAmount,
        discountAmount: safeDiscountAmount,
        netAmount,
        paidAmount: effectivePaidAmount,
        dueAmount,
        remark,
        paymentMethodId: effectivePaidAmount > 0 ? (normalizedPurchasePayments[0]?.paymentMethodId || selectedPaymentMethodId) : undefined,
        transactionNo: effectivePaidAmount > 0 ? transactionNo : undefined,
        payments: normalizedPurchasePayments.length > 0 ? normalizedPurchasePayments : undefined,
        details: details.map(d => ({
          productId: Number(d.productId),
          qty: d.qty,
          unitCost: d.unitCost,
          subtotal: d.subtotal,
          batchNumber: d.batchNumber?.trim() || undefined,
          expiryDate: d.expiryDate || undefined,
          warrantyMonths: d.warrantyMonths ?? 0,
          itemWarranties: (d.itemWarranties && d.itemWarranties.length > 0
            ? d.itemWarranties
            : Array.from({ length: Math.max(0, d.qty || 0) }, () => d.warrantyMonths ?? 0)
          ).map((m) => Math.max(0, Number(m) || 0)),
          serialNumbers: (isSerialRequired(d.productId) || d.assignSerials)
            ? resizeSerials((d.serialNumbers || []).map((sn) => (sn || '').trim()), d.qty)
            : [],
          serialConditions: (isSerialRequired(d.productId) || d.assignSerials)
            ? resizeStrings(d.serialConditions || [], d.qty)
            : [],
          serialPhotos: (isSerialRequired(d.productId) || d.assignSerials)
            ? resizeStrings(d.serialPhotos || [], d.qty)
            : []
        }))
      };

      const res = await purchaseApiService.create(payload);
      if (res) {
        Swal.fire({
          icon: 'success',
          title: 'Success',
          text: 'Purchase recorded successfully',
          timer: 2000,
          showConfirmButton: false
        });
        fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
        fetchStats(dateFrom, dateTo);
        setShowNewVoucherForm(false);
        setSelectedSupplierId(0);
        setSelectedStaffId(0);
        setSupplierSearch('');
        setStaffSearch('');
        setPurchaseDate(dateInput(new Date()));
        setPaymentTermDays(30);
        setDueDate(addDaysInput(dateInput(new Date()), 30));
        setPaidAmount(0);
        setPurchasePayments([]);
        setDiscountAmount(0);
        setRemark('');
        setSelectedPaymentMethodId(0);
        setTransactionNo('');
        setDetails([{ productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, batchNumber: '', expiryDate: '', itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }]);
      }
    } catch (error: any) {
      Swal.fire({
        icon: 'error',
        title: 'Error',
        text: error.message || 'Failed to record purchase'
      });
    } finally {
      setSaving(false);
    }
  };

  const openView = useCallback(async (id: number) => {
    setRelatedReturnsLoading(true);
    setRelatedReturns([]);
    setPurchaseHistoryPayments([]);
    setPurchaseStockMovements([]);
    try {
      const purchase = await purchaseApiService.getById(id);
      const productIds = [...new Set((purchase.details || []).map((detail) => detail.productId))];
      const [purchaseReturns, payments, stockHistories] = await Promise.all([
        purchaseReturnApiService.getByPurchaseId(id),
        accountingApiService.getTransactionsByRef(id, 'Purchase'),
        Promise.all(productIds.map((productId) => productService.getStockHistory(productId, { size: 100 })))
      ]);
      setViewPurchase(purchase);
      setRelatedReturns(purchaseReturns || []);
      setPurchaseHistoryPayments(payments || purchase.payments || []);
      setPurchaseStockMovements(stockHistories.flatMap((history) => history.movements || []).filter((movement) => movement.referenceId === id));
    } catch (e) {
      Swal.fire('Error', 'Failed to load purchase', 'error');
    } finally {
      setRelatedReturnsLoading(false);
    }
  }, []);

  const closeView = () => {
    setViewPurchase(null);
    setRelatedReturns([]);
    setPurchaseHistoryPayments([]);
    setPurchaseStockMovements([]);
    setRelatedReturnsLoading(false);
  };

  const handleVoucherLookup = async () => {
    const keyword = voucherLookup.trim();
    if (!keyword) return;
    try {
      const result = await purchaseApiService.getAllPaged(0, 10, keyword);
      const normalized = keyword.replace(/^#/, '').trim().toLowerCase();
      const exact = result.content.find((p) =>
        String(p.purchaseCode || '').toLowerCase() === normalized ||
        String(p.purchaseCode || '').toLowerCase() === keyword.toLowerCase() ||
        String(p.id || '') === normalized
      );
      if (exact?.id) {
        await openView(exact.id);
        return;
      }
      if (result.content.length === 1 && result.content[0].id) {
        await openView(result.content[0].id);
        return;
      }
      setSearchTerm(keyword);
      setDebouncedSearch(keyword);
      setPurchasePage(0);
      Swal.fire({ icon: 'info', title: 'တိတိကျကျမတွေ့ပါ', text: 'စာရင်းထဲတွင် ကိုက်ညီသော ဘောင်ချာများကို ပြထားပါသည်။', timer: 1800, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Voucher ရှာမရပါ', 'error');
    }
  };

  const printPurchaseVoucher = (purchase: typeof viewPurchase) => {
    if (!purchase) return;
    const { html, popupSize } = buildPurchaseVoucherHtml({ purchase, settings: getCachedCompanySettings() });
    const w = window.open('', '_blank', popupSize);
    if (!w) return;
    w.document.write(html);
    w.document.close();
  };

  const openVoucherPreview = async (id: number) => {
    setPreviewLoading(true);
    setPreviewPurchase(null);
    try {
      const purchase = await purchaseApiService.getById(id);
      setPreviewPurchase(purchase);
    } catch (e) {
      Swal.fire('Error', 'Failed to load purchase', 'error');
    } finally {
      setPreviewLoading(false);
    }
  };

  const openSendTo = async (id: number) => {
    setSendToLoading(true);
    setSendToPurchase(null);
    try {
      const purchase = await purchaseApiService.getById(id);
      setSendToPurchase(purchase);
    } catch (e) {
      Swal.fire('Error', 'Failed to load purchase', 'error');
    } finally {
      setSendToLoading(false);
    }
  };

  const buildVoucherText = (purchase: PurchaseDTO): string => {
    const fmt = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(v || 0);
    const lines: string[] = [
      `=== Purchase Voucher ===`,
      `Voucher : ${purchase.purchaseCode || `#${purchase.id}`}`,
      `Supplier: ${purchase.supplierName || '-'}`,
      `Staff   : ${purchase.staffName || '-'}`,
      `Date    : ${purchase.purchaseDate ? new Date(purchase.purchaseDate).toLocaleDateString() : '-'}`,
      `------------------------`,
    ];
    (purchase.details || []).forEach((d, i) => {
      lines.push(`${i + 1}. ${d.productName || `Product #${d.productId}`}`);
      lines.push(`   ${d.qty} × ${fmt(d.unitCost)} = ${fmt(d.subtotal)}`);
      if (d.serialNumbers?.filter(s => s).length) {
        lines.push(`   Serials: ${d.serialNumbers.filter(s => s).join(', ')}`);
      }
    });
    lines.push(`------------------------`);
    lines.push(`Total : ${fmt(purchase.totalAmount)}`);
    if ((purchase.discountAmount || 0) > 0) {
      lines.push(`Disc  : ${fmt(purchase.discountAmount || 0)}`);
      lines.push(`Net   : ${fmt(purchase.netAmount ?? purchase.totalAmount)}`);
    }
    lines.push(`Paid  : ${fmt(purchase.paidAmount)}`);
    if ((purchase.dueAmount || 0) > 0) lines.push(`Due   : ${fmt(purchase.dueAmount)}`);
    if (purchase.remark) lines.push(`Remark: ${purchase.remark}`);
    return lines.join('\n');
  };

  useEffect(() => {
    if (purchasesLoading) return;

    const raw = new URLSearchParams(location.search).get('purchaseId');
    const linkedPurchaseId = Number(raw);
    if (!Number.isInteger(linkedPurchaseId) || linkedPurchaseId <= 0) return;

    openView(linkedPurchaseId).finally(() => {
      navigate({ pathname: location.pathname, search: '' }, { replace: true });
    });
  }, [location.pathname, location.search, navigate, openView, purchasesLoading]);

  const getSupplierLabel = (s: SupplierDTO) => `${s.name} (${s.code})`;
  const getSupplierLabelById = (id: number) => {
    const s = suppliers.find((x) => x.id === id);
    return s ? getSupplierLabel(s) : '';
  };
  const getStaffLabel = (s: StaffDTO) => s.name;
  const getStaffLabelById = (id: number) => {
    const s = staffs.find((x) => x.id === id);
    return s ? getStaffLabel(s) : '';
  };

  const handleSupplierSearchChange = (value: string) => {
    setSupplierSearch(value);
    const matched = suppliers.find((s) => getSupplierLabel(s).toLowerCase() === value.toLowerCase());
    setSelectedSupplierId(matched ? matched.id : 0);
  };

  const handleStaffSearchChange = (value: string) => {
    setStaffSearch(value);
    const matched = staffs.find((s) => getStaffLabel(s).toLowerCase() === value.toLowerCase());
    setSelectedStaffId(matched ? matched.id : 0);
  };

  const handleSupplierSelect = (supplier: SupplierDTO) => {
    setSelectedSupplierId(supplier.id);
    setSupplierSearch(getSupplierLabel(supplier));
    setSupplierOpen(false);
  };

  const openQuickSupplierModal = () => {
    setQuickSupplierForm({ name: supplierSearch.trim(), phone: '', address: '' });
    setSupplierOpen(false);
    setIsQuickSupplierModalOpen(true);
  };

  const handleQuickSupplierSave = async (event: React.FormEvent) => {
    event.preventDefault();
    const name = quickSupplierForm.name.trim();
    if (!name || quickSupplierSaving) return;

    setQuickSupplierSaving(true);
    try {
      const created = await supplierService.create({
        name,
        phone: quickSupplierForm.phone.trim() || undefined,
        address: quickSupplierForm.address.trim() || undefined,
        openingBalance: 0
      });
      setSuppliers((current) => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      handleSupplierSelect(created);
      setIsQuickSupplierModalOpen(false);
      Swal.fire({ icon: 'success', title: 'Supplier created', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Failed to create supplier', 'error');
    } finally {
      setQuickSupplierSaving(false);
    }
  };

  const handleStaffSelect = (staff: StaffDTO) => {
    setSelectedStaffId(staff.id);
    setStaffSearch(getStaffLabel(staff));
    setStaffOpen(false);
  };

  const filteredSuppliers = suppliers.filter((s) => {
    const query = supplierSearch.trim().toLowerCase();
    if (!query) return true;
    return [s.name, s.code, s.phone, s.address]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(query));
  });

  const filteredStaffs = staffs.filter((s) => {
    const query = staffSearch.trim().toLowerCase();
    if (!query) return true;
    return [s.name, s.role, s.phone]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(query));
  });

  const openPaymentModal = (p: PurchaseDTO) => {
    if (!p.id) return;
    setPaymentForm({
      purchaseId: p.id,
      amount: p.dueAmount ? String(p.dueAmount) : '',
      paymentMethodId: paymentMethods[0]?.id ?? 0,
      transactionNo: '',
      payments: []
    });
    setIsPaymentModalOpen(true);
  };

  const handleSavePayment = async (e: React.FormEvent) => {
    e.preventDefault();
    const normalizedPaymentFormPayments = normalizePayments(paymentForm.payments || []);
    const amount = normalizedPaymentFormPayments.length > 0 ? paymentTotal(paymentForm.payments || []) : parseFloat(paymentForm.amount);
    const missing: string[] = [];
    if (!paymentForm.purchaseId) missing.push('Purchase');
    if (paymentForm.paymentMethodId <= 0 && normalizedPaymentFormPayments.length === 0) missing.push('Payment Method');
    if (!amount || amount <= 0) missing.push('Amount');
    if (missing.length > 0) {
      Swal.fire('Validation', `Please fill ${missing.join(', ')}.`, 'warning');
      return;
    }
    setPaymentSaving(true);
    try {
      if (normalizedPaymentFormPayments.length > 0) {
        await Promise.all(normalizedPaymentFormPayments.map((payment) => accountingApiService.createPaymentTransaction({
          referenceId: paymentForm.purchaseId,
          referenceType: 'Purchase',
          paymentMethodId: payment.paymentMethodId!,
          amount: payment.amount || 0,
          transactionNo: payment.transactionNo || undefined
        })));
      } else {
        await accountingApiService.createPaymentTransaction({
          referenceId: paymentForm.purchaseId,
          referenceType: 'Purchase',
          paymentMethodId: paymentForm.paymentMethodId,
          amount,
          transactionNo: paymentForm.transactionNo.trim() || undefined
        });
      }
      setIsPaymentModalOpen(false);
      fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
      fetchStats(dateFrom, dateTo);
      Swal.fire({ icon: 'success', title: 'Payment recorded', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (err: any) {
      Swal.fire('Error', err.message || 'Failed to record payment', 'error');
    } finally {
      setPaymentSaving(false);
    }
  };

  const normalizeStatusKey = (status: string) => {
    const raw = status.toLowerCase();
    if (raw.includes('paid') && !raw.includes('partial')) return 'paid';
    if (raw.includes('partial')) return 'partial';
    if (raw.includes('due') || raw.includes('unpaid') || raw.includes('pending')) return 'due';
    return raw;
  };

  const getStatusKey = (p: PurchaseDTO) => {
    const backendStatus = (p.paymentStatus || '').trim();
    if (backendStatus) return normalizeStatusKey(backendStatus);
    if (p.dueAmount > 0 && p.paidAmount > 0) return 'partial';
    if (p.dueAmount > 0) return 'due';
    return 'paid';
  };

  const getStatusDisplay = (p: PurchaseDTO) => {
    const backendStatus = (p.paymentStatus || '').trim();
    if (backendStatus) return backendStatus;
    if (p.dueAmount > 0 && p.paidAmount > 0) return 'Partial';
    if (p.dueAmount > 0) return 'Due';
    return 'Paid';
  };

  const getStatusLabel = (p: PurchaseDTO) => {
    const key = getStatusKey(p);
    if (key === 'paid') return 'ငွေချေပြီး';
    if (key === 'partial') return 'တစ်စိတ်တစ်ပိုင်း';
    if (key === 'due') return 'ပေးရန်ကျန်';
    return getStatusDisplay(p);
  };

  const filteredPurchases = purchases.filter((p) => {
    const statusKey = getStatusKey(p);
    return filterStatus === 'All' || statusKey === filterStatus.toLowerCase();
  });
  const visiblePurchaseRows = useMemo(() => filteredPurchases.filter((purchase): purchase is typeof purchase & { id: number } => typeof purchase.id === 'number'), [filteredPurchases]);
  const bulk = useBulkSelection<PurchaseDTO & { id: number }>(visiblePurchaseRows);

  const handleBulkAction = (action: { key: string }) => {
    if (action.key !== 'export') return;
    const csv = [
      ['ID', 'Purchase Code', 'Date', 'Supplier', 'Staff', 'Total', 'Due'],
      ...bulk.selectedRows.map((purchase) => [purchase.id, purchase.purchaseCode || '', purchase.purchaseDate || '', purchase.supplierName || '', purchase.staffName || '', purchase.totalAmount || 0, purchase.dueAmount || 0])
    ].map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `purchases-selected-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    bulk.clear();
  };

  const paidCount = filteredPurchases.filter((p) => getStatusKey(p) === 'paid').length;
  const statusStyles: Record<string, string> = {
    paid: 'bg-emerald-100 text-emerald-700',
    partial: 'bg-amber-100 text-amber-700',
    due: 'bg-rose-100 text-rose-700'
  };

  const applyDateShortcut = (shortcut: 'TODAY' | 'WEEK' | 'MONTH' | 'ALL') => {
    setDateShortcut(shortcut);
    if (shortcut === 'TODAY') {
      const range = getTodayRange();
      setDateFrom(range.from);
      setDateTo(range.to);
    } else if (shortcut === 'WEEK') {
      const range = getThisWeekRange();
      setDateFrom(range.from);
      setDateTo(range.to);
    } else if (shortcut === 'MONTH') {
      const range = getThisMonthRange();
      setDateFrom(range.from);
      setDateTo(range.to);
    } else {
      setDateFrom('');
      setDateTo('');
    }
  };

  return (
    <div className="w-full max-w-none space-y-6">
      {!showNewVoucherForm ? (
        <>
          <div className="flex flex-col gap-4 2xl:flex-row 2xl:items-center">
            <div className="ml-auto flex w-full flex-shrink-0 flex-col gap-2 sm:w-auto sm:flex-row sm:items-center 2xl:order-2 2xl:ml-0">
              <button onClick={() => { fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo); fetchStats(dateFrom, dateTo); }} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50">
                <RefreshCw size={14} className={purchasesLoading ? 'animate-spin' : ''} />
                ပြန်ဖတ်ရန်
              </button>
              <button onClick={() => setShowNewVoucherForm(true)} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700">
                <Plus size={16} />
                ဝယ်ယူမှုအသစ်
              </button>
            </div>
          {/* Purchase Dashboard - Stat cards */}
          <div className="grid flex-1 grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4 2xl:order-1">
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ဘောင်ချာစုစုပေါင်း</p>
                <p className="text-2xl font-bold text-slate-800">{serverStats.count}</p>
                <p className="text-[10px] text-slate-400 mt-1">ဤစာမျက်နှာ: {filteredPurchases.length}</p>
              </div>
              <div className="w-11 h-11 rounded-lg bg-indigo-50 flex items-center justify-center">
                <FileText size={20} className="text-indigo-600" />
              </div>
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ဝယ်ယူမှု စုစုပေါင်း</p>
                <p className="text-2xl font-bold text-slate-800">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(serverStats.totalAmount)}</p>
              </div>
              <div className="w-11 h-11 rounded-lg bg-slate-100 flex items-center justify-center">
                <DollarSign size={20} className="text-slate-600" />
              </div>
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ပေးချေပြီး</p>
                <p className="text-2xl font-bold text-emerald-700">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(serverStats.paidAmount)}</p>
              </div>
              <div className="w-11 h-11 rounded-lg bg-emerald-50 flex items-center justify-center">
                <CheckCircle size={20} className="text-emerald-600" />
              </div>
            </div>
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 flex items-center justify-between">
              <div>
                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">ပေးရန်ကျန်</p>
                <p className="text-2xl font-bold text-amber-700">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(serverStats.dueAmount)}</p>
              </div>
              <div className="w-11 h-11 rounded-lg bg-amber-50 flex items-center justify-center">
                <AlertCircle size={20} className="text-amber-600" />
              </div>
            </div>
          </div>
          </div>

          {/* Filters */}
          <div className="space-y-3">
            <div className="grid grid-cols-1 xl:grid-cols-[1fr_0.85fr] gap-3">
              <div className="rounded-xl border border-slate-200 bg-white p-3">
                <label className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">Quick Search</label>
                <div className="relative mt-1.5">
                  <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="ဘောင်ချာနံပါတ်၊ ပေးသွင်းသူ၊ လက်ခံသူ ရှာပါ..."
                    className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 pl-9 pr-9 text-sm font-medium text-slate-700 outline-none focus:border-indigo-400 focus:bg-white"
                  />
                  {purchasesLoading && searchTerm && (
                    <span className="absolute right-3 top-1/2 -translate-y-1/2">
                      <svg className="animate-spin h-4 w-4 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>
                    </span>
                  )}
                </div>
              </div>

              <div className="rounded-xl border border-indigo-100 bg-indigo-50 p-3">
                <label className="text-[11px] font-bold text-indigo-600 uppercase tracking-wide">Voucher Direct Search</label>
                <div className="mt-1.5 flex gap-2">
                  <div className="relative min-w-0 flex-1">
                    <Hash size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-indigo-300" />
                    <input
                      type="text"
                      value={voucherLookup}
                      onChange={(e) => setVoucherLookup(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void handleVoucherLookup(); } }}
                      placeholder="ဥပမာ PUR-238"
                      className="h-10 w-full rounded-lg border border-indigo-200 bg-white pl-9 pr-3 text-sm font-medium text-slate-700 outline-none focus:border-indigo-500"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleVoucherLookup()}
                    className="inline-flex h-10 items-center gap-1.5 rounded-lg bg-indigo-600 px-3 text-xs font-bold text-white hover:bg-indigo-700"
                  >
                    <Eye size={13} /> ဖွင့်
                  </button>
                </div>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2">
              <div className="flex min-w-0 items-center gap-2">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-indigo-50 text-indigo-600">
                  <Filter size={14} />
                </span>
                <div className="min-w-0">
                  <p className="text-xs font-black text-slate-700">စစ်ထုတ်ရန်</p>
                  <p className="truncate text-[10px] font-semibold text-slate-400">Today default ဖြင့် ဝယ်ယူမှုမှတ်တမ်းများ</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowFilterPanel(v => !v)}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 text-xs font-bold text-slate-600 hover:bg-slate-100"
              >
                {showFilterPanel ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                {showFilterPanel ? 'Hide' : 'Show'}
              </button>
            </div>

            {showFilterPanel && (
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 space-y-3">
                <div className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)_minmax(190px,0.6fr)_auto] xl:items-end">
                  <div className="rounded-lg border border-slate-200 bg-white p-2">
                    <p className="mb-1.5 px-1 text-[10px] font-black uppercase tracking-wide text-slate-400">လက်ခံရက်စွဲ</p>
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                      <label className="min-w-0">
                        <span className="mb-1 block text-[10px] font-semibold text-slate-500">From</span>
                        <input
                          type="date"
                          value={dateFrom}
                          onChange={(e) => { setDateFrom(e.target.value); setDateShortcut('CUSTOM'); }}
                          className="h-9 w-full rounded-md border border-slate-200 bg-slate-50 px-2.5 text-xs font-semibold text-slate-700 outline-none focus:border-indigo-400 focus:bg-white"
                        />
                      </label>
                      <label className="min-w-0">
                        <span className="mb-1 block text-[10px] font-semibold text-slate-500">To</span>
                        <input
                          type="date"
                          value={dateTo}
                          onChange={(e) => { setDateTo(e.target.value); setDateShortcut('CUSTOM'); }}
                          className="h-9 w-full rounded-md border border-slate-200 bg-slate-50 px-2.5 text-xs font-semibold text-slate-700 outline-none focus:border-indigo-400 focus:bg-white"
                        />
                      </label>
                    </div>
                  </div>

                  <div className="rounded-lg border border-slate-200 bg-white p-2">
                    <p className="mb-1.5 px-1 text-[10px] font-black uppercase tracking-wide text-slate-400">ကာလရွေးရန်</p>
                    <div className="grid grid-cols-2 gap-1 sm:grid-cols-4">
                      {[
                        { key: 'TODAY', label: 'Today' },
                        { key: 'WEEK', label: 'This Week' },
                        { key: 'MONTH', label: 'This Month' },
                        { key: 'ALL', label: 'All' },
                      ].map((item) => (
                        <button
                          key={item.key}
                          type="button"
                          onClick={() => applyDateShortcut(item.key as 'TODAY' | 'WEEK' | 'MONTH' | 'ALL')}
                          className={`min-h-9 rounded-md border px-2 text-[11px] font-bold transition-colors ${dateShortcut === item.key ? 'border-slate-800 bg-slate-800 text-white shadow-sm' : 'border-transparent bg-slate-50 text-slate-600 hover:bg-slate-100'}`}
                        >
                          {item.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <label className="rounded-lg border border-slate-200 bg-white p-2">
                    <span className="mb-1.5 block px-1 text-[10px] font-black uppercase tracking-wide text-slate-400">ငွေပေးချေမှု</span>
                    <select
                      value={filterStatus}
                      onChange={(e) => setFilterStatus(e.target.value as any)}
                      className="h-9 w-full rounded-md border border-slate-200 bg-slate-50 px-2.5 text-xs font-semibold text-slate-700 outline-none focus:border-indigo-400 focus:bg-white"
                    >
                      <option value="All">အခြေအနေအားလုံး</option>
                      <option value="Paid">ငွေချေပြီး</option>
                      <option value="Partial">တစ်စိတ်တစ်ပိုင်း</option>
                      <option value="Due">ပေးရန်ကျန်</option>
                    </select>
                  </label>

                  <button
                    type="button"
                    onClick={() => { setSearchTerm(''); setVoucherLookup(''); setFilterStatus('All'); const r = getTodayRange(); setDateFrom(r.from); setDateTo(r.to); setDateShortcut('TODAY'); }}
                    className="h-10 rounded-lg border border-rose-200 bg-white px-3 text-xs font-bold text-rose-600 transition-colors hover:bg-rose-50"
                  >
                    Reset
                  </button>
                </div>

                <div className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-2 lg:flex-row lg:items-center">
                  <span className="px-1 text-[10px] font-black uppercase tracking-wide text-slate-400">အမြန်ရွေးရန်</span>
                  <div className="flex flex-1 flex-wrap gap-1.5">
                    {([
                      ['All', 'အားလုံး'],
                      ['Paid', 'ငွေချေပြီး'],
                      ['Partial', 'တစ်စိတ်တစ်ပိုင်း'],
                      ['Due', 'ပေးရန်ကျန်'],
                    ] as ['All' | 'Paid' | 'Partial' | 'Due', string][]).map(([key, label]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setFilterStatus(key)}
                        className={`min-h-8 rounded-md border px-3 text-[11px] font-bold transition-colors ${filterStatus === key ? 'border-indigo-600 bg-indigo-600 text-white shadow-sm' : 'border-transparent bg-slate-50 text-slate-600 hover:bg-slate-100 hover:text-indigo-700'}`}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                  <span className="shrink-0 rounded-md bg-slate-50 px-2.5 py-1.5 text-[10px] font-semibold text-slate-400">
                    {purchaseTotalElements === 0 ? 0 : purchasePage * purchasePageSize + 1}–{Math.min((purchasePage + 1) * purchasePageSize, purchaseTotalElements)} / {purchaseTotalElements.toLocaleString()} ခု
                  </span>
                </div>
              </div>
            )}
          </div>
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="p-4 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <List size={18} className="text-indigo-500 shrink-0" />
                <span className="font-semibold text-slate-800">ဝယ်ယူမှု ဘောင်ချာစာရင်း</span>
                {!purchasesLoading && purchaseTotalElements > 0 && (
                  <span className="text-sm text-slate-500">
                    {purchasePage * purchasePageSize + 1} မှ {Math.min((purchasePage + 1) * purchasePageSize, purchaseTotalElements)} / {purchaseTotalElements.toLocaleString()} ခု ပြနေသည် — ဤစာမျက်နှာတွင် {paidCount} ခု ငွေချေပြီး
                  </span>
                )}
              </div>
              <div className="text-xs text-slate-400 font-medium">
                နောက်ဆုံးဖတ်ချိန် {new Date().toLocaleDateString()}
              </div>
            </div>
            <div className="overflow-auto max-h-[58vh] custom-scrollbar">
              <BulkSelectionToolbar
                visibleCount={visiblePurchaseRows.length}
                selectedCount={bulk.selectedCount}
                allVisibleSelected={bulk.allVisibleSelected}
                someVisibleSelected={bulk.someVisibleSelected}
                onToggleVisible={() => bulk.allVisibleSelected ? bulk.clear() : bulk.selectVisible()}
                onClear={bulk.clear}
                selectedRows={bulk.selectedRows}
                selectedTotal={bulk.selectedRows.reduce((sum, purchase) => sum + (Number(purchase.totalAmount) || 0), 0)}
                totalLabel="Selected Total"
                actions={[{ key: 'export', label: 'Export selected', icon: <Download size={13} />, tone: 'indigo' }]}
                onAction={handleBulkAction}
              />
              {purchasesLoading ? (
                <div className="p-8 text-center text-slate-400">ဖတ်နေသည်...</div>
              ) : (
                <table className="w-full min-w-[1180px] table-fixed border-collapse text-sm">
                  <colgroup>
                    <col className="w-[54px]" />
                    <col className="w-[132px]" />
                    <col className="w-[190px]" />
                    <col className="w-[160px]" />
                    <col className="w-[132px]" />
                    <col className="w-[132px]" />
                    <col className="w-[124px]" />
                    <col className="w-[126px]" />
                    <col className="w-[230px]" />
                    <col className="w-[230px]" />
                  </colgroup>
                  <thead className="sticky top-0 z-10 border-b border-slate-200 bg-slate-50">
                    <tr className="text-[11px] font-bold uppercase tracking-wide text-slate-500">
                                            <th className="px-3 py-3 text-center">Select</th>
                      <th className="px-3 py-3 text-left">#</th>
                      <th className="px-3 py-3 text-left">ဘောင်ချာ</th>
                      <th className="px-3 py-3 text-left">ပေးသွင်းသူ</th>
                      <th className="px-3 py-3 text-left">ဝယ်ယူသူ</th>
                      <th className="px-3 py-3 text-left">ရက်စွဲ</th>
                      <th className="px-3 py-3 text-right">စုစုပေါင်း</th>
                      <th className="px-3 py-3 text-right">ပေးရန်ကျန်</th>
                      <th className="px-3 py-3 text-center">အခြေအနေ</th>
                      <th className="px-3 py-3 text-right">လုပ်ဆောင်ချက်</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredPurchases.length > 0 ? (
                      filteredPurchases.map((p, index) => {
                        const statusKey = getStatusKey(p);
                        const statusLabel = getStatusLabel(p);
                        const canPay = statusKey !== 'paid' && p.dueAmount > 0;
                        return (
                          <tr key={p.id!} className="h-[58px] hover:bg-slate-50/80 transition-colors">
                                                        <td className="px-3 py-3 text-center"><input type="checkbox" checked={bulk.selectedIds.has(p.id as number)} onChange={() => bulk.toggle(p.id as number)} className="h-4 w-4 accent-indigo-600" aria-label={`Select purchase ${p.purchaseCode || p.id}`} /></td>
                            <td className="px-3 py-3 text-xs font-semibold tabular-nums text-slate-400">{purchasePage * purchasePageSize + index + 1}</td>
                            <td className="px-3 py-3 font-mono text-xs font-bold text-slate-800"><span className="block truncate">{p.purchaseCode || `#${p.id}`}</span></td>
                            <td className="px-3 py-3 font-semibold text-slate-700"><span className="block truncate">{p.supplierName || '-'}</span></td>
                            <td className="px-3 py-3 text-xs font-medium text-slate-500"><span className="block truncate">{p.staffName || '-'}</span></td>
                            <td className="px-3 py-3 text-xs font-medium text-slate-500"><span className="block truncate">{p.purchaseDate ? new Date(p.purchaseDate).toLocaleDateString() : '-'}</span></td>
                            <td className="px-3 py-3 text-right font-semibold tabular-nums text-slate-800">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(p.totalAmount)}</td>
                            <td className={`px-3 py-3 text-right font-bold tabular-nums ${(p.dueAmount || 0) > 0 ? 'text-rose-700' : 'text-slate-300'}`}>
                              {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(p.dueAmount || 0)}
                            </td>
                            <td className="px-3 py-3 text-center">
                              <span className={`inline-flex min-w-[92px] justify-center rounded-md px-2.5 py-1 text-[10px] font-bold ${statusStyles[statusKey] || 'bg-slate-100 text-slate-600'}`}>
                                {statusLabel}
                              </span>
                            </td>
                            <td className="px-3 py-3 text-right">
                              <div className="inline-flex items-center justify-end gap-1.5 whitespace-nowrap">
                                {canPay && (
                                  <button
                                    onClick={() => openPaymentModal(p)}
                                    className="inline-flex h-8 items-center gap-1 rounded-md border border-emerald-200 px-2.5 text-xs font-bold text-emerald-700 hover:bg-emerald-50"
                                    title="ငွေချေမည်"
                                  >
                                    <CreditCard size={14} /> ငွေချေ
                                  </button>
                                )}
                                <button
                                  onClick={() => openVoucherPreview(p.id!)}
                                  className="inline-flex h-8 items-center gap-1 rounded-md border border-violet-200 px-2.5 text-xs font-bold text-violet-700 hover:bg-violet-50"
                                  title="Voucher Preview"
                                >
                                  <Printer size={14} />
                                </button>
                                <button
                                  onClick={() => openSendTo(p.id!)}
                                  className="inline-flex h-8 items-center gap-1 rounded-md border border-sky-200 px-2.5 text-xs font-bold text-sky-700 hover:bg-sky-50"
                                  title="Send To"
                                >
                                  <Share2 size={14} /> ပို့မည်
                                </button>
                                <button onClick={() => openView(p.id!)} className="inline-flex h-8 items-center gap-1 rounded-md border border-indigo-200 px-2.5 text-xs font-bold text-indigo-700 hover:bg-indigo-50">
                                  <Eye size={14} /> ကြည့်မည်
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan={10} className="px-4 py-10 text-center text-slate-400">လက်ရှိ filter နှင့်ကိုက်ညီသော ဝယ်ယူမှုဘောင်ချာ မရှိပါ။</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              )}
            </div>

            {/* Pagination */}
            {!purchasesLoading && purchaseTotalPages > 0 && (
              <div className="px-4 py-3 border-t border-slate-100 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <p className="text-xs text-slate-500">
                  {purchaseTotalElements === 0 ? 0 : purchasePage * purchasePageSize + 1} မှ {Math.min((purchasePage + 1) * purchasePageSize, purchaseTotalElements)} / {purchaseTotalElements.toLocaleString()} ခု
                </p>
                <div className="flex items-center gap-3 flex-wrap">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs text-slate-500">ပြရန်</span>
                    <select
                      value={purchasePageSize}
                      onChange={e => { setPurchasePageSize(Number(e.target.value)); setPurchasePage(0); }}
                      className="border border-slate-200 rounded-lg px-2 py-1 text-xs bg-white focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                    >
                      {[10, 20, 50, 100].map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </div>
                  <div className="flex items-center gap-1 flex-wrap">
                    <button onClick={() => setPurchasePage(p => Math.max(0, p - 1))} disabled={purchasePage === 0}
                      className="w-8 h-8 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">‹</button>
                    {(() => {
                      const pages: (number | -1)[] = [];
                      const delta = 2;
                      for (let i = 0; i < purchaseTotalPages; i++) {
                        if (i === 0 || i === purchaseTotalPages - 1 || (i >= purchasePage - delta && i <= purchasePage + delta)) {
                          pages.push(i);
                        } else if (pages[pages.length - 1] !== -1) {
                          pages.push(-1);
                        }
                      }
                      return pages.map((p, idx) =>
                        p === -1
                          ? <span key={`e${idx}`} className="px-1 text-slate-400 text-xs select-none">...</span>
                          : <button key={p} onClick={() => setPurchasePage(p)}
                              className={`w-8 h-8 rounded-lg text-xs font-semibold transition-colors ${p === purchasePage ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
                              {p + 1}
                            </button>
                      );
                    })()}
                    <button onClick={() => setPurchasePage(p => Math.min(purchaseTotalPages - 1, p + 1))} disabled={purchasePage >= purchaseTotalPages - 1}
                      className="w-8 h-8 rounded-lg text-xs font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed">›</button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </>
      ) : (
        <>
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 p-4 sm:p-5 border-b border-slate-100">
              <div className="flex items-start gap-3 min-w-0">
                <button onClick={() => setShowNewVoucherForm(false)} className="mt-0.5 inline-flex items-center justify-center w-9 h-9 rounded-lg border border-slate-200 text-slate-500 hover:bg-slate-50 hover:text-slate-800 transition-colors" title="စာရင်းသို့ပြန်မည်">
                  <ArrowLeft size={17} />
                </button>
                <div className="min-w-0">
                  <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Purchase Voucher</p>
                  <h2 className="text-xl font-black text-slate-900 mt-0.5">ဝယ်ယူမှုဘောင်ချာအသစ်</h2>
                </div>
              </div>
              <div className="w-full lg:w-auto">
                <button
                  type="button"
                  onClick={() => setIsVoucherPaymentModalOpen(true)}
                  className="w-full lg:w-auto inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 text-white text-sm font-bold hover:bg-indigo-700 transition-colors"
                >
                  <CreditCard size={16} /> ငွေပေးချေမှု ဖွင့်မည်
                </button>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Form Details */}
        <div className="lg:col-span-3 space-y-6">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 sm:p-6 space-y-6">
            <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Voucher Info</p>
                <h3 className="text-sm font-black text-slate-800 mt-0.5">ပေးသွင်းသူ၊ ဝန်ထမ်းနှင့်ရက်စွဲ</h3>
              </div>
              <span className="hidden sm:inline-flex px-2.5 py-1 rounded-lg bg-slate-50 border border-slate-200 text-[10px] font-bold text-slate-500">Step 1</span>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-6 2xl:grid-cols-8">
              <div className="space-y-1.5 xl:col-span-3 2xl:col-span-3">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                  <ShoppingCart size={12} /> ပေးသွင်းသူ
                </label>
                <div className="relative">
                  <input
                    value={supplierSearch && supplierSearch.length > 0 ? supplierSearch : getSupplierLabelById(selectedSupplierId)}
                    onChange={(e) => {
                      handleSupplierSearchChange(e.target.value);
                      setSupplierOpen(true);
                    }}
                    onFocus={() => setSupplierOpen(true)}
                    onBlur={() => setTimeout(() => setSupplierOpen(false), 120)}
                    placeholder="ပေးသွင်းသူ ရှာပါ..."
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  />
                  {supplierOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                      {filteredSuppliers.length > 0 ? filteredSuppliers.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onMouseDown={() => handleSupplierSelect(s)}
                          className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${selectedSupplierId === s.id ? 'bg-indigo-50' : ''}`}
                        >
                          <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                          <p className="text-xs text-slate-500">{s.code || '-'} {s.phone ? `| ${s.phone}` : ''}</p>
                        </button>
                      )) : (
                        <div className="p-3 text-center">
                          <p className="text-xs text-slate-400">ပေးသွင်းသူ မတွေ့ပါ။</p>
                          <button
                            type="button"
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={openQuickSupplierModal}
                            className="mt-2 inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-indigo-700"
                          >
                            <Plus size={13} /> ပေးသွင်းသူအသစ် ဖန်တီးမည်
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
              <div className="space-y-1.5 xl:col-span-3 2xl:col-span-2">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                  <User size={12} /> ဝယ်ယူမှုတာဝန်ခံ (Staff)
                </label>
                <div className="relative">
                  <input
                    value={staffSearch && staffSearch.length > 0 ? staffSearch : getStaffLabelById(selectedStaffId)}
                    readOnly={!canOverrideStaff}
                    onChange={(e) => {
                      handleStaffSearchChange(e.target.value);
                      setStaffOpen(true);
                    }}
                    onFocus={() => { if (canOverrideStaff) setStaffOpen(true); }}
                    onBlur={() => setTimeout(() => setStaffOpen(false), 120)}
                    placeholder="ဝန်ထမ်း ရှာပါ..."
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  />
                  {staffOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                      {filteredStaffs.length > 0 ? filteredStaffs.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onMouseDown={() => handleStaffSelect(s)}
                          className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${selectedStaffId === s.id ? 'bg-indigo-50' : ''}`}
                        >
                          <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                          <p className="text-xs text-slate-500">{s.role || '-'} {s.active === false ? '| Inactive' : ''}</p>
                        </button>
                      )) : <p className="px-3 py-2 text-xs text-slate-400">ဝန်ထမ်း မတွေ့ပါ။</p>}
                    </div>
                  )}
                </div>
              </div>

              <div className="contents">
              <div className="flex min-w-0 flex-col gap-1.5 xl:col-span-2 2xl:col-span-1">
                <label className="flex h-4 items-center gap-1.5 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  <Calendar size={12} className="shrink-0" /> <span className="truncate">Purchase Date</span>
                </label>
                <input
                  type="date"
                  value={purchaseDate}
                  onChange={(e) => {
                    const next = e.target.value;
                    setPurchaseDate(next);
                    if (paymentTermDays >= 0) setDueDate(addDaysInput(next, paymentTermDays));
                  }}
                  className="h-10 w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                />
              </div>
              <div className="flex min-w-0 flex-col gap-1.5 xl:col-span-2 2xl:col-span-1">
                <label className="flex h-4 items-center text-[10px] font-bold text-slate-400 uppercase tracking-wider"><span className="truncate">Payment Term</span></label>
                <select
                  value={paymentTermDays}
                  onChange={(e) => {
                    const days = Number(e.target.value);
                    setPaymentTermDays(days);
                    setDueDate(addDaysInput(purchaseDate, days));
                  }}
                  className="h-10 w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                >
                  <option value={-1}>Custom due date</option>
                  <option value={0}>Cash / Same day</option>
                  <option value={7}>Net 7 days</option>
                  <option value={15}>Net 15 days</option>
                  <option value={30}>Net 30 days</option>
                  <option value={45}>Net 45 days</option>
                  <option value={60}>Net 60 days</option>
                </select>
              </div>
              <div className="flex min-w-0 flex-col gap-1.5 sm:col-span-2 xl:col-span-2 2xl:col-span-1">
                <label className="flex h-4 items-center text-[10px] font-bold text-slate-400 uppercase tracking-wider"><span className="truncate">Due Date</span></label>
                <input
                  type="date"
                  value={dueDate}
                  onChange={(e) => { setDueDate(e.target.value); setPaymentTermDays(-1); }}
                  disabled={dueAmount <= 0}
                  className="h-10 w-full px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all disabled:bg-slate-100 disabled:text-slate-400"
                />
              </div>
              </div>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pt-2">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Items</p>
                <h3 className="text-sm font-black text-slate-800 mt-0.5">ဝယ်မည့်ပစ္စည်းများ</h3>
              </div>
              <p className="text-xs text-slate-500">Qty, ဝယ်ဈေး, warranty နှင့် serial ကို တစ်ကြောင်းချင်းဖြည့်ပါ။</p>
            </div>

            <div className="border border-slate-200 rounded-xl overflow-auto bg-white shadow-sm">
              <table className="w-full min-w-[760px] text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                    <th className="px-4 py-3 border-b border-slate-100">ပစ္စည်း</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-24">Qty</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-32">ဝယ်ဈေး</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-36">Batch / Expiry</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-24">Warranty (လ)</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-32 text-right">စုစုပေါင်း</th>
                    <th className="px-4 py-3 border-b border-slate-100 w-12"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {details.map((detail, dIndex) => {
                    const _rowProduct = products.find((p) => p.id === detail.productId);
                    const _unlinkedQty = _rowProduct?.unlinkedQty ?? 0;
                    const _existingQty = (!_rowProduct?.hasSerial && (_rowProduct?.stockQty ?? 0) > 0)
                      ? (_rowProduct?.stockQty ?? 0) : 0;
                    return (
                    <React.Fragment key={dIndex}>
                      <tr className="hover:bg-slate-50/50 transition-colors">
                        <td className="px-4 py-3">
                          <div className="relative">
                            <input
                              list={`product-options-${dIndex}`}
                              value={detail.productSearch && detail.productSearch.length > 0 ? detail.productSearch : getProductLabelById(detail.productId)}
                              onChange={(e) => handleProductSearchChange(dIndex, e.target.value)}
                              placeholder="ပစ္စည်းရှာပါ..."
                              className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none font-medium"
                            />
                            <datalist id={`product-options-${dIndex}`}>
                              {products.map(p => (
                                <option key={p.id} value={getProductLabel(p)} />
                              ))}
                            </datalist>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <input 
                            type="number" 
                            min="1"
                            value={detail.qty}
                            onChange={(e) => handleDetailChange(dIndex, 'qty', e.target.value)}
                            className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none"
                          />
                        </td>
                        <td className="px-4 py-3">
                          <div className="space-y-1">
                            <input
                              type="text"
                              value={detail.batchNumber || ''}
                              onChange={(e) => handleDetailChange(dIndex, 'batchNumber', e.target.value)}
                              placeholder="Batch no."
                              className="w-full rounded border border-slate-200 px-2 py-1 text-xs focus:border-indigo-400 focus:outline-none"
                            />
                            <input
                              type="date"
                              value={detail.expiryDate || ''}
                              onChange={(e) => handleDetailChange(dIndex, 'expiryDate', e.target.value)}
                              className="w-full rounded border border-slate-200 px-2 py-1 text-xs focus:border-indigo-400 focus:outline-none"
                            />
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <input
                            type="number"
                            value={detail.unitCost || ''}
                            onChange={(e) => handleDetailChange(dIndex, 'unitCost', parseFloat(e.target.value) || 0)}
                            placeholder="0.00"
                            className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none"
                          />
                        </td>
                        <td className="px-4 py-3">
                          <div className="space-y-1">
                            <input
                              type="number"
                              min="0"
                              value={(detail as any).warrantyMonths ?? 0}
                              onChange={(e) => handleDetailChange(dIndex, 'warrantyMonths', parseInt(e.target.value) || 0)}
                              placeholder="0"
                              className="w-full px-2 py-1 bg-transparent border-none text-sm focus:ring-0 focus:outline-none"
                            />
                            <button
                              type="button"
                              onClick={() => applyWarrantyToAllItems(dIndex)}
                              className="text-[10px] text-indigo-600 hover:underline"
                            >
                              Apply to all
                            </button>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-right font-bold text-slate-700">
                          {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(detail.subtotal)}
                        </td>
                        <td className="px-3 py-3 text-center">
                          <button 
                            onClick={() => handleRemoveRow(dIndex)}
                            className="p-1.5 text-slate-300 hover:text-rose-500 transition-colors"
                            disabled={details.length <= 1}
                          >
                            <Trash2 size={14} />
                          </button>
                        </td>
                      </tr>
                      {/* Smart Serial Input Row */}
                      {detail.productId > 0 && detail.qty > 0 && (isSerialRequired(detail.productId) ? (
                        _unlinkedQty > 0 ? (
                        <tr className="bg-rose-50/30">
                          <td colSpan={6} className="px-4 py-3">
                            <div className="flex items-start gap-2.5 p-3 bg-rose-50 border border-rose-200 rounded-xl">
                              <AlertCircle size={16} className="text-rose-500 mt-0.5 shrink-0" />
                              <div>
                                <p className="text-xs font-bold text-rose-700">Cannot purchase — orphaned stock detected</p>
                                <p className="text-[11px] text-rose-600 mt-1">
                                  This product has <strong>{_unlinkedQty}</strong> unlinked unit(s) in stock from a prior qty-only purchase.
                                  Go to <strong>Inventory → Products</strong> and click the <strong>#</strong> button on this product to assign serial numbers to those units first, then come back to purchase.
                                </p>
                              </div>
                            </div>
                          </td>
                        </tr>
                        ) : (
                        <tr className="bg-slate-50/30">
                          <td colSpan={6} className="px-4 py-3">
                            <div className="flex flex-wrap gap-2">
                              <div className="w-full flex items-center gap-2 mb-1">
                                <Hash size={12} className="text-indigo-500" />
                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Serial Numbers for {detail.qty} items</span>
                                <button
                                  type="button"
                                  onClick={() => {
                                    const allExisting = details.flatMap((d, i) => i === dIndex ? [] : d.serialNumbers ?? []);
                                    const generated = generateSerialNumbers(detail.qty, allExisting);
                                    const newDetails = [...details];
                                    newDetails[dIndex] = { ...newDetails[dIndex], serialNumbers: generated };
                                    setDetails(newDetails);
                                  }}
                                  className="ml-auto inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[10px] font-bold text-indigo-600 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200"
                                >
                                  <Hash size={10} /> Assign Serial Numbers
                                </button>
                              </div>
                              {resizeSerials(detail.serialNumbers || [], detail.qty).map((sn, sIndex) => {
                                const cond  = (detail.serialConditions || [])[sIndex] ?? '';
                                const photo = (detail.serialPhotos || [])[sIndex] ?? '';
                                return (
                                <div key={sIndex} className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5">
                                  {/* thumbnail or camera */}
                                  <label className="cursor-pointer shrink-0">
                                    {photo ? (
                                      <img src={photo} alt="" className="w-8 h-8 rounded object-cover border border-slate-200" />
                                    ) : (
                                      <div className="w-8 h-8 rounded bg-slate-100 flex items-center justify-center text-slate-300 hover:bg-slate-200 transition-colors">
                                        <Camera size={14} />
                                      </div>
                                    )}
                                    <input type="file" accept="image/*" className="hidden"
                                      onChange={e => { const f = e.target.files?.[0]; if (f) handleSerialPhotoChange(dIndex, sIndex, f); }} />
                                  </label>
                                  <input
                                    type="text"
                                    value={sn}
                                    onChange={(e) => handleSerialChange(dIndex, sIndex, e.target.value)}
                                    placeholder={`Serial #${sIndex + 1}`}
                                    className="px-2 py-1 bg-slate-50 border border-slate-100 rounded text-[11px] w-28 focus:outline-none focus:ring-1 focus:ring-indigo-400"
                                  />
                                  <input
                                    type="text"
                                    value={cond}
                                    onChange={(e) => handleConditionChange(dIndex, sIndex, e.target.value)}
                                    placeholder="Condition"
                                    className="px-2 py-1 bg-amber-50 border border-amber-100 rounded text-[11px] w-28 focus:outline-none focus:ring-1 focus:ring-amber-400"
                                  />
                                </div>
                                );
                              })}
                            </div>
                            <div className="mt-3">
                              <div className="w-full flex items-center gap-2 mb-1">
                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Warranty months per item</span>
                              </div>
                              <div className="flex flex-wrap gap-2">
                                {Array.from({ length: detail.qty }).map((_, wIndex) => (
                                  <input
                                    key={`w-${wIndex}`}
                                    type="number"
                                    min="0"
                                    value={(detail.itemWarranties?.[wIndex] ?? detail.warrantyMonths ?? 0)}
                                    onChange={(e) => handleItemWarrantyChange(dIndex, wIndex, parseInt(e.target.value) || 0)}
                                    placeholder={`W#${wIndex + 1}`}
                                    className="px-2 py-1 bg-white border border-slate-200 rounded text-[11px] w-24 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
                                  />
                                ))}
                              </div>
                            </div>
                          </td>
                        </tr>
                        )
                      ) : (
                        <tr className={detail.assignSerials ? 'bg-indigo-50/40' : 'bg-slate-50/30'}>
                          <td colSpan={6} className="px-4 py-3 space-y-3">
                            {/* Toggle row */}
                            <div className="flex items-center gap-3 flex-wrap">
                              {detail.assignSerials ? (
                                <>
                                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-100 border border-indigo-200 text-[10px] font-bold uppercase tracking-wider text-indigo-700">
                                    <Hash size={11} /> Internal Serials
                                  </span>
                                  <span className="text-[10px] text-indigo-500">
                                    This product will become serial-tracked after saving.
                                  </span>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      const newDetails = [...details];
                                      newDetails[dIndex] = { ...newDetails[dIndex], assignSerials: false, serialNumbers: [] };
                                      setDetails(newDetails);
                                    }}
                                    className="ml-auto text-[10px] font-semibold text-slate-500 hover:text-red-500 underline"
                                  >
                                    Remove Serials
                                  </button>
                                </>
                              ) : (
                                <>
                                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 text-[10px] font-bold uppercase tracking-wider text-slate-600">
                                    <Box size={11} /> Qty Only
                                  </span>
                                  {detail.productId > 0 && (
                                    _existingQty > 0 ? (
                                      <div className="flex items-start gap-2 p-2.5 bg-amber-50 border border-amber-200 rounded-lg flex-1">
                                        <AlertCircle size={14} className="text-amber-500 mt-0.5 shrink-0" />
                                        <p className="text-[11px] text-amber-700">
                                          <strong>{_existingQty}</strong> unit(s) already in stock. Go to{' '}
                                          <strong>Inventory → Products</strong> and click{' '}
                                          <strong>#</strong> to assign serial numbers to those units first.
                                        </p>
                                      </div>
                                    ) : (
                                    <button
                                      type="button"
                                      onClick={() => {
                                        const allExisting = details.flatMap((d, i) => i === dIndex ? [] : d.serialNumbers ?? []);
                                        const newDetails = [...details];
                                        newDetails[dIndex] = {
                                          ...newDetails[dIndex],
                                          assignSerials: true,
                                          serialNumbers: generateSerialNumbers(detail.qty, allExisting),
                                        };
                                        setDetails(newDetails);
                                      }}
                                      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-indigo-600 text-white text-[10px] font-bold hover:bg-indigo-700 transition-colors"
                                    >
                                      <Hash size={11} /> Assign Internal Serials
                                    </button>
                                    )
                                  )}
                                </>
                              )}
                            </div>

                            {/* Serial inputs — shown when assignSerials=true */}
                            {detail.assignSerials && (
                              <div>
                                <div className="flex items-center gap-2 mb-1.5">
                                  <Hash size={12} className="text-indigo-500" />
                                  <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                                    Serial Numbers ({detail.qty} items) — edit if needed
                                  </span>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                  {(detail.serialNumbers || []).map((sn, sIndex) => {
                                    const cond  = (detail.serialConditions || [])[sIndex] ?? '';
                                    const photo = (detail.serialPhotos || [])[sIndex] ?? '';
                                    return (
                                    <div key={sIndex} className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5">
                                      <label className="cursor-pointer shrink-0">
                                        {photo ? (
                                          <img src={photo} alt="" className="w-7 h-7 rounded object-cover border border-slate-200" />
                                        ) : (
                                          <div className="w-7 h-7 rounded bg-slate-100 flex items-center justify-center text-slate-300 hover:bg-slate-200 transition-colors">
                                            <Camera size={12} />
                                          </div>
                                        )}
                                        <input type="file" accept="image/*" className="hidden"
                                          onChange={e => { const f = e.target.files?.[0]; if (f) handleSerialPhotoChange(dIndex, sIndex, f); }} />
                                      </label>
                                      <input
                                        type="text"
                                        value={sn}
                                        onChange={(e) => handleSerialChange(dIndex, sIndex, e.target.value)}
                                        placeholder={`Serial #${sIndex + 1}`}
                                        className={`px-2 py-1 border rounded text-[11px] w-32 focus:outline-none focus:ring-1 focus:ring-indigo-400 font-mono ${!sn.trim() ? 'border-red-300 bg-red-50' : 'border-slate-100 bg-slate-50'}`}
                                      />
                                      <input
                                        type="text"
                                        value={cond}
                                        onChange={(e) => handleConditionChange(dIndex, sIndex, e.target.value)}
                                        placeholder="Condition"
                                        className="px-2 py-1 bg-amber-50 border border-amber-100 rounded text-[11px] w-28 focus:outline-none focus:ring-1 focus:ring-amber-400"
                                      />
                                    </div>
                                    );
                                  })}
                                </div>
                              </div>
                            )}

                            {/* Warranty months per item */}
                            <div>
                              <div className="flex items-center gap-2 mb-1">
                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Warranty months per item</span>
                              </div>
                              <div className="flex flex-wrap gap-2">
                                {Array.from({ length: detail.qty }).map((_, wIndex) => (
                                  <input
                                    key={`w-ns-${wIndex}`}
                                    type="number"
                                    min="0"
                                    value={(detail.itemWarranties?.[wIndex] ?? detail.warrantyMonths ?? 0)}
                                    onChange={(e) => handleItemWarrantyChange(dIndex, wIndex, parseInt(e.target.value) || 0)}
                                    placeholder={`W#${wIndex + 1}`}
                                    className="px-2 py-1 bg-white border border-slate-200 rounded text-[11px] w-24 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500"
                                  />
                                ))}
                              </div>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <button 
              onClick={handleAddRow}
              className="inline-flex w-full sm:w-auto justify-center items-center gap-2 px-4 py-2.5 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-xl text-sm font-black transition-all"
            >
              <Plus size={16} />
              ပစ္စည်းအတန်းထပ်ထည့်
            </button>
          </div>
        </div>

        {isVoucherPaymentModalOpen && (
          <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-900/60 p-0 sm:p-4" onMouseDown={() => setIsVoucherPaymentModalOpen(false)}>
            <div className="h-[100dvh] w-full overflow-y-auto rounded-none bg-white shadow-xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-xl sm:rounded-2xl" onMouseDown={(e) => e.stopPropagation()}>
          <div className="min-h-full bg-white p-4 pb-8 space-y-5 sm:min-h-0 sm:rounded-2xl sm:p-6 sm:space-y-6">
            <div className="sticky top-0 z-10 -mx-4 -mt-4 flex items-start justify-between gap-3 border-b border-slate-100 bg-white px-4 pt-4 pb-4 sm:static sm:mx-0 sm:mt-0 sm:p-0 sm:pb-4">
              <div className="min-w-0">
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Payment Summary</p>
                <h3 className="font-black text-slate-800 text-sm flex items-center gap-2 mt-0.5">
                  <DollarSign size={16} className="text-indigo-500" />
                  ငွေပေးချေမှုနှင့် စုစုပေါင်း
                </h3>
              </div>
              <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
                <span className={`hidden sm:inline-flex px-2.5 py-1 rounded-lg text-[10px] font-black border ${dueAmount > 0 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200'}`}>
                  {dueAmount > 0 ? 'Credit' : 'Paid'}
                </span>
                <button type="button" onClick={() => setIsVoucherPaymentModalOpen(false)} className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700" title="ပိတ်မည်">
                  <X size={18} />
                </button>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex flex-wrap justify-between items-center gap-x-4 gap-y-1 text-sm">
                <span className="text-slate-500">ကုန်ဖိုးစုစုပေါင်း</span>
                <span className="font-bold text-slate-800">
                  {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(totalAmount)}
                </span>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Discount Amount</label>
                <input
                  type="number"
                  min="0"
                  max={totalAmount || undefined}
                  value={discountAmount || ''}
                  onChange={(e) => setDiscountAmount(Math.min(totalAmount, Math.max(0, parseFloat(e.target.value) || 0)))}
                  placeholder="0.00"
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold text-amber-600 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                />
                <p className="text-[10px] text-slate-400">Return amount will use discounted net cost.</p>
              </div>

              <div className="flex flex-wrap justify-between items-center gap-x-4 gap-y-1 text-sm">
                <span className="text-slate-500">Discount ပြီးကျသင့်ငွေ</span>
                <span className="font-bold text-indigo-700">
                  {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(netAmount)}
                </span>
              </div>

              <div className="space-y-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Payment Method</label>
                  <select
                    value={selectedPaymentMethodId}
                    onChange={(e) => setSelectedPaymentMethodId(Number(e.target.value))}
                    className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all ${
                      selectedPaymentMethodId > 0 || effectivePaidAmount <= 0 || normalizedPurchasePayments.length > 0 ? 'border-slate-200' : 'border-rose-200'
                    }`}
                  >
                    <option value={0}>Select Payment Method</option>
                    {paymentMethods.map((m) => (
                      <option key={m.id} value={m.id}>{m.methodName}</option>
                    ))}
                  </select>
                  <p className="text-[10px] text-slate-400">Required when paid amount is greater than 0.</p>
                </div>

                <div className="space-y-1.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Paid Amount</label>
                    <div className="flex items-center gap-1">
                      <button type="button" onClick={() => { setPaidAmount(netAmount); setPurchasePayments([]); }} className="px-2 py-1 rounded bg-emerald-600 text-white text-[10px] font-bold hover:bg-emerald-700">Full</button>
                      <button type="button" onClick={() => { setPaidAmount(0); setPurchasePayments([]); }} className="px-2 py-1 rounded bg-amber-500 text-white text-[10px] font-bold hover:bg-amber-600">Credit</button>
                    </div>
                  </div>
                  <input 
                    type="number" 
                    value={paidAmount || ''}
                    onChange={(e) => { setPaidAmount(parseFloat(e.target.value) || 0); if (purchasePayments.length > 0) setPurchasePayments([]); }}
                    placeholder="0.00"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold text-emerald-600 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Transaction No</label>
                  <input
                    type="text"
                    value={transactionNo}
                    onChange={(e) => setTransactionNo(e.target.value)}
                    placeholder="Optional reference no."
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                  />
                </div>

                {effectivePaidAmount > 0 && selectedPaymentMethodId === 0 && normalizedPurchasePayments.length === 0 && (
                  <p className="text-[10px] text-rose-500">Please select a payment method for paid amount.</p>
                )}
                {hasDuplicateSerials && (
                  <p className="text-[10px] text-rose-500">Duplicate serial numbers in this purchase: {Array.from(new Set(duplicateSerials)).join(', ')}</p>
                )}
                {dueAmount > 0 && !dueDate && (
                  <p className="text-[10px] text-rose-500">Please choose a due date for credit purchase.</p>
                )}
                <SplitPaymentEditor
                  methods={paymentMethods}
                  payments={purchasePayments}
                  onChange={(next) => {
                    setPurchasePayments(next);
                    const total = paymentTotal(next);
                    setPaidAmount(total);
                  }}
                  label="Split Payment"
                />
              </div>

              <div className="flex flex-wrap justify-between items-center gap-x-4 gap-y-1 text-sm pt-2 border-t border-slate-100">
                <span className="text-slate-500">ပေးရန်ကျန်</span>
                <span className={`font-bold ${dueAmount > 0 ? 'text-rose-500' : 'text-emerald-600'}`}>
                  {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(dueAmount)}
                </span>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Remark</label>
                <textarea 
                  value={remark}
                  onChange={(e) => setRemark(e.target.value)}
                  rows={3}
                  placeholder="Optional notes..."
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all resize-none"
                />
              </div>

              <button
                disabled={!isValid || saving}
                onClick={handleSave}
                className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold transition-all shadow-lg mt-4
                  ${isValid && !saving
                    ? 'bg-indigo-600 text-white hover:bg-indigo-700 shadow-indigo-200'
                    : 'bg-slate-100 text-slate-400 cursor-not-allowed shadow-none'}`}
              >
                {saving ? (
                  <>
                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/>
                    </svg>
                    Processing... ထပ်မနှိပ်ပါနှင့်
                  </>
                ) : (
                  <><Save size={18} /> ဝယ်ယူမှု သိမ်းမည်</>
                )}
              </button>
            </div>
            </div>
          </div>
          </div>
        )}
      </div>
        </>
      )}

      {isQuickSupplierModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4" onMouseDown={() => setIsQuickSupplierModalOpen(false)}>
          <form onSubmit={handleQuickSupplierSave} onMouseDown={(e) => e.stopPropagation()} className="w-full rounded-t-2xl bg-white p-5 shadow-xl sm:max-w-md sm:rounded-2xl">
            <div className="mb-5 flex items-center justify-between gap-3">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-indigo-500">Quick Supplier</p>
                <h3 className="mt-0.5 text-base font-black text-slate-800">ပေးသွင်းသူအသစ် ဖန်တီးမည်</h3>
              </div>
              <button type="button" onClick={() => setIsQuickSupplierModalOpen(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" title="ပိတ်မည်"><X size={18} /></button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">ပေးသွင်းသူအမည် *</label>
                <input autoFocus required value={quickSupplierForm.name} onChange={(e) => setQuickSupplierForm((form) => ({ ...form, name: e.target.value }))} placeholder="ပေးသွင်းသူအမည်" className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20" />
              </div>
              <div>
                <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">ဖုန်းနံပါတ်</label>
                <input value={quickSupplierForm.phone} onChange={(e) => setQuickSupplierForm((form) => ({ ...form, phone: e.target.value }))} placeholder="Optional" className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20" />
              </div>
              <div>
                <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">လိပ်စာ</label>
                <input value={quickSupplierForm.address} onChange={(e) => setQuickSupplierForm((form) => ({ ...form, address: e.target.value }))} placeholder="Optional" className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20" />
              </div>
            </div>
            <div className="mt-5 flex gap-2">
              <button type="button" onClick={() => setIsQuickSupplierModalOpen(false)} className="flex-1 rounded-lg px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-100">မလုပ်တော့ပါ</button>
              <button disabled={quickSupplierSaving || !quickSupplierForm.name.trim()} className="flex-1 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-300">{quickSupplierSaving ? 'ဖန်တီးနေသည်...' : 'ဖန်တီးမည်'}</button>
            </div>
          </form>
        </div>
      )}

      {isPaymentModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6">
            <div className="flex items-center justify-between mb-6">
              <div>
                <h3 className="font-bold text-slate-800">Record Payment</h3>
                <p className="text-xs text-slate-500 mt-1">Purchase #{paymentForm.purchaseId}</p>
              </div>
              <button onClick={() => setIsPaymentModalOpen(false)} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg">
                <X size={18} />
              </button>
            </div>
            <form onSubmit={handleSavePayment} className="space-y-4">
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Payment Method</label>
                <select
                  value={paymentForm.paymentMethodId}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, paymentMethodId: Number(e.target.value) }))}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                >
                  <option value={0}>Select method</option>
                  {paymentMethods.map((m) => (
                    <option key={m.id} value={m.id}>{m.methodName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Amount</label>
                <input
                  type="number"
                  step="0.01"
                  value={paymentForm.amount}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, amount: e.target.value, payments: [] }))}
                  placeholder="0.00"
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1">Transaction No</label>
                <input
                  type="text"
                  value={paymentForm.transactionNo}
                  onChange={(e) => setPaymentForm((f) => ({ ...f, transactionNo: e.target.value }))}
                  placeholder="e.g. TXN-001"
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>
              <SplitPaymentEditor
                methods={paymentMethods}
                payments={paymentForm.payments || []}
                onChange={(next) => setPaymentForm((f) => ({ ...f, payments: next, amount: paymentTotal(next) > 0 ? String(paymentTotal(next).toFixed(2)) : f.amount }))}
                label="Split Payment"
              />
              <div className="flex justify-end gap-2 pt-4">
                <button type="button" onClick={() => setIsPaymentModalOpen(false)} className="px-4 py-2 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium">
                  Cancel
                </button>
                <button type="submit" disabled={paymentSaving} className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 disabled:opacity-50">
                  <Save size={16} />
                  {paymentSaving ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {viewPurchase && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-bold text-slate-800">Purchase: {viewPurchase.purchaseCode || `#${viewPurchase.id}`}</h3>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => printPurchaseVoucher(viewPurchase)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg hover:bg-indigo-100"
                >
                  <Printer size={14} />
                  Print Voucher
                </button>
                <button
                  onClick={() => navigate(`${AppRoute.PURCHASE_RETURNS}?purchaseId=${viewPurchase.id}`)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-lg hover:bg-rose-100"
                >
                  <RefreshCw size={14} />
                  ဝယ်ပြန်ပို့
                </button>
                <button onClick={closeView} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg">
                  <X size={18} />
                </button>
              </div>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600"><span className="font-medium text-slate-500">Supplier:</span> {viewPurchase.supplierName}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Staff:</span> {viewPurchase.staffName}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Date:</span> {viewPurchase.purchaseDate ? new Date(viewPurchase.purchaseDate).toLocaleString() : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Due Date:</span> {viewPurchase.dueDate ? new Date(viewPurchase.dueDate).toLocaleDateString() : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Total:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.totalAmount)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Discount:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.discountAmount || 0)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Net:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.netAmount ?? (viewPurchase.totalAmount - (viewPurchase.discountAmount || 0)))}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Paid:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.paidAmount)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Due:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.dueAmount)}</p>
                {viewPurchase.paymentMethodId && (
                  <p className="text-slate-600">
                    <span className="font-medium text-slate-500">Payment Method:</span>{' '}
                    {paymentMethods.find((m) => m.id === viewPurchase.paymentMethodId)?.methodName || `#${viewPurchase.paymentMethodId}`}
                  </p>
                )}
                {viewPurchase.transactionNo && (
                  <p className="text-slate-600">
                    <span className="font-medium text-slate-500">Transaction No:</span> {viewPurchase.transactionNo}
                  </p>
                )}
              </div>
              {viewPurchase.remark && <p className="text-sm text-slate-600"><span className="font-medium text-slate-500">Remark:</span> {viewPurchase.remark}</p>}
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold">
                    <th className="px-3 py-2 border-b">Product</th>
                    <th className="px-3 py-2 border-b w-16">Qty</th>
                    <th className="px-3 py-2 border-b text-right">Unit Cost</th>
                    <th className="px-3 py-2 border-b">Serial / Warranty</th>
                    <th className="px-3 py-2 border-b text-right">Subtotal</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {(viewPurchase.details || []).map((d, i) => (
                    <tr key={i}>
                      <td className="px-3 py-2">{d.productName || d.productId}</td>
                      <td className="px-3 py-2">{d.qty}</td>
                      <td className="px-3 py-2 text-right">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(d.unitCost)}</td>
                      <td className="px-3 py-2 text-xs text-slate-600">
                        {(d.serialNumbers && d.serialNumbers.length > 0) ? (
                          <div className="space-y-1">
                            {d.serialNumbers.map((sn, idx) => (
                              <div key={`${sn}-${idx}`} className="flex items-center gap-2 flex-wrap">
                                <span className="font-mono text-slate-700">{sn}</span>
                                <span className="text-slate-400">|</span>
                                <span>{(d.itemWarranties?.[idx] ?? d.warrantyMonths ?? 0)} mo</span>
                                {(d.serialConditions?.[idx]) && (
                                  <span className="px-1.5 py-0.5 bg-amber-50 border border-amber-200 text-amber-700 rounded text-[9px] font-bold">{d.serialConditions[idx]}</span>
                                )}
                              </div>
                            ))}
                          </div>
                        ) : (
                          <span>{(d.itemWarranties && d.itemWarranties.length > 0)
                            ? d.itemWarranties.map((m, idx) => `#${idx + 1}:${m}mo`).join(', ')
                            : `${d.warrantyMonths ?? 0} mo (bulk)`}</span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-right font-medium">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(d.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="pt-2">
                <div className="mb-2 flex items-center justify-between">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Payment History</h4>
                  <span className="text-[11px] text-slate-400">{purchaseHistoryPayments.length} payment(s)</span>
                </div>
                {purchaseHistoryPayments.length === 0 ? (
                  <div className="py-3 text-xs text-slate-400">No payment history found.</div>
                ) : (
                  <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full min-w-[620px] text-left text-sm">
                      <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Payment Method</th><th className="border-b px-3 py-2">Transaction No</th><th className="border-b px-3 py-2 text-right">Amount</th></tr></thead>
                      <tbody className="divide-y divide-slate-100">{purchaseHistoryPayments.map((payment, index) => <tr key={payment.id || `${payment.transactionNo}-${index}`}><td className="px-3 py-2 text-slate-600">{payment.paymentDate ? new Date(payment.paymentDate).toLocaleString() : '-'}</td><td className="px-3 py-2 text-slate-600">{payment.paymentMethodName || (payment.paymentMethodId ? `#${payment.paymentMethodId}` : '-')}</td><td className="px-3 py-2 text-slate-500">{payment.transactionNo || '-'}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(payment.amount || 0)}</td></tr>)}</tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="pt-2">
                <div className="mb-2 flex items-center justify-between">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Stock Movement</h4>
                  <span className="text-[11px] text-slate-400">{purchaseStockMovements.length} movement(s)</span>
                </div>
                {purchaseStockMovements.length === 0 ? (
                  <div className="py-3 text-xs text-slate-400">No stock movement found for this purchase.</div>
                ) : (
                  <div className="overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full min-w-[680px] text-left text-sm">
                      <thead><tr className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><th className="border-b px-3 py-2">Date</th><th className="border-b px-3 py-2">Product</th><th className="border-b px-3 py-2">Type</th><th className="border-b px-3 py-2 text-right">In</th><th className="border-b px-3 py-2 text-right">Balance</th></tr></thead>
                      <tbody className="divide-y divide-slate-100">{purchaseStockMovements.map((movement, index) => <tr key={movement.id || `${movement.productId}-${index}`}><td className="px-3 py-2 text-slate-600">{movement.date ? new Date(movement.date).toLocaleString() : '-'}</td><td className="px-3 py-2"><p className="font-semibold text-slate-700">{movement.productName || '-'}</p><p className="text-[10px] text-slate-400">{movement.productCode || ''}</p></td><td className="px-3 py-2 text-slate-500">{movement.type}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">+{movement.quantityIn.toLocaleString()}</td><td className="px-3 py-2 text-right font-bold text-slate-700">{movement.balance.toLocaleString()}</td></tr>)}</tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="pt-2">
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">Related Purchase Returns</h4>
                  {!relatedReturnsLoading && (
                    <span className="text-[11px] text-slate-400">{relatedReturns.length} voucher(s)</span>
                  )}
                </div>

                {relatedReturnsLoading ? (
                  <div className="text-xs text-slate-400 py-3">Loading related returns...</div>
                ) : relatedReturns.length === 0 ? (
                  <div className="text-xs text-slate-400 py-3">No returns found for this purchase.</div>
                ) : (
                  <table className="w-full text-left border-collapse text-sm">
                    <thead>
                      <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold">
                        <th className="px-3 py-2 border-b">Return No</th>
                        <th className="px-3 py-2 border-b">Date</th>
                        <th className="px-3 py-2 border-b text-right">Total</th>
                        <th className="px-3 py-2 border-b text-right">Refund</th>
                        <th className="px-3 py-2 border-b">Reason</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {relatedReturns.map((r) => (
                        <tr key={r.id || r.returnNo}>
                          <td className="px-3 py-2">{r.returnNo || `#${r.id}`}</td>
                          <td className="px-3 py-2">{r.returnDate ? new Date(r.returnDate).toLocaleString() : '-'}</td>
                          <td className="px-3 py-2 text-right">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(r.totalReturnAmount || 0)}</td>
                          <td className="px-3 py-2 text-right">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(r.refundAmount ?? r.totalReturnAmount ?? 0)}</td>
                          <td className="px-3 py-2">{r.reason || '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Voucher Preview Modal */}
      {(previewLoading || previewPurchase) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between shrink-0">
              <h3 className="font-bold text-slate-800">
                {previewPurchase
                  ? `Voucher Preview — ${previewPurchase.purchaseCode || `#${previewPurchase.id}`}`
                  : 'ဖတ်နေသည်...'}
              </h3>
              <div className="flex items-center gap-2">
                {previewPurchase && (
                  <button
                    onClick={() => printPurchaseVoucher(previewPurchase)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg hover:bg-indigo-100"
                  >
                    <Printer size={14} />
                    Print
                  </button>
                )}
                <button
                  onClick={() => { setPreviewPurchase(null); setPreviewLoading(false); }}
                  className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"
                >
                  <X size={18} />
                </button>
              </div>
            </div>
            <div className="flex-1 overflow-hidden min-h-0">
              {previewLoading ? (
                <div className="flex items-center justify-center h-48 text-slate-400">ဖတ်နေသည်...</div>
              ) : previewPurchase ? (
                <iframe
                  srcDoc={buildPurchaseVoucherHtml({ purchase: previewPurchase, settings: getCachedCompanySettings(), preview: true }).html}
                  title="Purchase Voucher Preview"
                  className="w-full border-0"
                  style={{ height: '70vh' }}
                />
              ) : null}
            </div>
          </div>
        </div>
      )}

      {/* Send To Modal */}
      {(sendToLoading || sendToPurchase) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-slate-800">Voucher ပို့ရန်</h3>
                {sendToPurchase && (
                  <p className="text-xs text-slate-500 mt-0.5">{sendToPurchase.purchaseCode || `#${sendToPurchase.id}`} — {sendToPurchase.supplierName}</p>
                )}
              </div>
              <button
                onClick={() => { setSendToPurchase(null); setSendToLoading(false); }}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"
              >
                <X size={18} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              {sendToLoading ? (
                <div className="flex items-center justify-center h-24 text-slate-400">ဖတ်နေသည်...</div>
              ) : sendToPurchase ? (
                <>
                  <textarea
                    readOnly
                    value={buildVoucherText(sendToPurchase)}
                    rows={10}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-mono text-slate-700 resize-none focus:outline-none"
                  />
                  <div className="flex flex-col gap-2">
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(buildVoucherText(sendToPurchase!));
                        Swal.fire({ icon: 'success', title: 'ကူးယူပြီး!', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
                      }}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700"
                    >
                      <FileText size={16} />
                      Text ကူးယူမည်
                    </button>
                    {typeof navigator.share === 'function' && (
                      <button
                        onClick={() => {
                          navigator.share({
                            title: `Purchase Voucher ${sendToPurchase!.purchaseCode || `#${sendToPurchase!.id}`}`,
                            text: buildVoucherText(sendToPurchase!),
                          }).catch(() => {});
                        }}
                        className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-emerald-600 text-white rounded-lg text-sm font-bold hover:bg-emerald-700"
                      >
                        <Share2 size={16} />
                        Share (Viber / Telegram / ...)
                      </button>
                    )}
                    <button
                      onClick={() => { printPurchaseVoucher(sendToPurchase); setSendToPurchase(null); }}
                      className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-white border border-slate-200 text-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50"
                    >
                      <Printer size={16} />
                      Print Voucher
                    </button>
                  </div>
                </>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseManagement;
