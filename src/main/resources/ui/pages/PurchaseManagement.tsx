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
import { AppRoute, PurchaseDTO, PurchaseDetailDTO, SupplierDTO, StaffDTO, ProductDTO, PaymentMethodDTO, PaymentTransactionDTO, PurchaseReturnDTO, ProductStockHistoryDTO, ProductStockHistoryMovementDTO, ReorderSuggestionDTO } from '../types';
import { Plus, Trash2, Save, ShoppingCart, Hash, DollarSign, User, List, Eye, X, RefreshCw, ArrowLeft, FileText, AlertCircle, CheckCircle, Search, Filter, CreditCard, Box, Printer, Camera, Share2, ChevronDown, ChevronUp, Download, Loader2, ClipboardList, Ban, ScanLine, FileSpreadsheet, Upload } from 'lucide-react';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { buildPurchaseVoucherHtml } from './purchaseVoucherTemplate';
import { getCachedCompanySettings } from '../utils/companySettings';
import { getFromSession } from '../utils/storageHelper';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import BarcodeScannerCamera from '../components/BarcodeScannerCamera';
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
const money = (v: number) => new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v || 0);

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
  const [productHistory, setProductHistory] = useState<ProductStockHistoryDTO | null>(null);
  const [productHistoryProduct, setProductHistoryProduct] = useState<ProductDTO | null>(null);
  const [productHistoryLoading, setProductHistoryLoading] = useState(false);
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
  const [taxAmount, setTaxAmount] = useState<number>(0);
  const [otherCharges, setOtherCharges] = useState<number>(0);
  const [attachmentName, setAttachmentName] = useState('');
  const [attachmentData, setAttachmentData] = useState('');
  const [isBarcodeOpen, setIsBarcodeOpen] = useState(false);
  const [barcodeInput, setBarcodeInput] = useState('');
  const barcodeInputRef = useRef<HTMLInputElement>(null);
  const [showReorderModal, setShowReorderModal] = useState(false);
  const [reorderSuggestions, setReorderSuggestions] = useState<ReorderSuggestionDTO[]>([]);
  const [reorderSearch, setReorderSearch] = useState('');
  const [reorderLoading, setReorderLoading] = useState(false);
  const [selectedReorder, setSelectedReorder] = useState<Set<number>>(new Set());
  const [rowActionBusy, setRowActionBusy] = useState(false);
  const [exportingExcel, setExportingExcel] = useState(false);
  const [remark, setRemark] = useState('');
  const [selectedPaymentMethodId, setSelectedPaymentMethodId] = useState<number>(0);
  const [transactionNo, setTransactionNo] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [purchaseProductSearch, setPurchaseProductSearch] = useState('');
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
  const [paymentItemsOpen, setPaymentItemsOpen] = useState(false);
  const [splitPaymentOpen, setSplitPaymentOpen] = useState(false);
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
    { productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }
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
    setDetails([...details, { productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }]);
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
    const unitCost = matched ? (Number(matched.costPrice ?? 0) || newDetails[index].unitCost) : newDetails[index].unitCost;
    newDetails[index] = {
      ...newDetails[index],
      productSearch: value,
      productId: matched ? matched.id : 0,
      unitCost,
      subtotal: newDetails[index].qty * unitCost,
      serialNumbers,
      serialConditions: resizeStrings(newDetails[index].serialConditions || [], serialNumbers.length),
      serialPhotos:     resizeStrings(newDetails[index].serialPhotos || [], serialNumbers.length),
      assignSerials: false,
    };
    setDetails(newDetails);
  };

  const openProductHistory = async (product: ProductDTO) => {
    setProductHistoryProduct(product);
    setProductHistory(null);
    setProductHistoryLoading(true);
    try {
      setProductHistory(await productService.getStockHistory(product.id, { size: 25 }));
    } catch {
      Swal.fire('Error', 'Failed to load product stock history', 'error');
    } finally {
      setProductHistoryLoading(false);
    }
  };

  const closeProductHistory = () => {
    setProductHistory(null);
    setProductHistoryProduct(null);
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
  const safeTaxAmount = Math.max(0, taxAmount || 0);
  const safeOtherCharges = Math.max(0, otherCharges || 0);
  const netAmount = Math.max(0, totalAmount - safeDiscountAmount + safeTaxAmount + safeOtherCharges);
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

  const selectedSupplier = suppliers.find((s) => s.id === selectedSupplierId);
  const selectedStaff = staffs.find((s) => s.id === selectedStaffId);
  const filledItemCount = details.filter((d) => d.productId > 0).length;
  const voucherHint = !selectedSupplierId
    ? 'ပေးသွင်းသူ ရွေးပါ'
    : !selectedStaffId
      ? 'ဝယ်ယူမှုတာဝန်ခံ ရွေးပါ'
      : filledItemCount === 0
        ? 'ပစ္စည်း ထည့်ပါ'
        : details.some((d) => d.productId > 0 && d.unitCost <= 0)
          ? 'ဝယ်ဈေး ဖြည့်ပါ'
          : hasDuplicateSerials
            ? `Serial ထပ်နေသည်: ${Array.from(new Set(duplicateSerials)).join(', ')}`
            : dueAmount > 0 && !dueDate
              ? 'အကြွေးဝယ်ယူမှုအတွက် due date ရွေးပါ'
              : effectivePaidAmount > 0 && selectedPaymentMethodId === 0 && normalizedPurchasePayments.length === 0
                ? 'ငွေပေးချေနည်း ရွေးပါ'
                : details.some((d) => {
                    const product = products.find((p) => p.id === d.productId);
                    return !!(product?.hasSerial && (product?.unlinkedQty ?? 0) > 0);
                  })
                  ? 'Orphaned stock ရှိသော ပစ္စည်းကို အရင် serial ချိတ်ပါ'
                  : !isValid
                    ? 'Serial နှင့် လိုအပ်သောအချက်အလက်များ ပြည့်စုံအောင် ဖြည့်ပါ'
                    : '';

  const buildPayload = (status?: 'DRAFT'): PurchaseDTO => ({
    supplierId: selectedSupplierId,
    staffId: selectedStaffId,
    purchaseDate: `${purchaseDate}T00:00:00`,
    dueDate: !status && dueAmount > 0 ? dueDate : undefined,
    paymentTermDays: !status && dueAmount > 0 && paymentTermDays >= 0 ? paymentTermDays : undefined,
    totalAmount,
    discountAmount: safeDiscountAmount,
    taxAmount: safeTaxAmount,
    otherCharges: safeOtherCharges,
    netAmount,
    paidAmount: status ? 0 : effectivePaidAmount,
    dueAmount: status ? netAmount : dueAmount,
    status,
    remark,
    attachmentName: attachmentName.trim() || undefined,
    attachmentData: attachmentData || undefined,
    paymentMethodId: !status && effectivePaidAmount > 0 ? (normalizedPurchasePayments[0]?.paymentMethodId || selectedPaymentMethodId) : undefined,
    transactionNo: !status && effectivePaidAmount > 0 ? transactionNo : undefined,
    payments: !status && normalizedPurchasePayments.length > 0 ? normalizedPurchasePayments : undefined,
    details: details.map(d => ({
      productId: Number(d.productId),
      qty: d.qty,
      unitCost: d.unitCost,
      subtotal: d.subtotal,
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
  });

  const resetFormFields = () => {
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
    setTaxAmount(0);
    setOtherCharges(0);
    setAttachmentName('');
    setAttachmentData('');
    setRemark('');
    setSelectedPaymentMethodId(0);
    setTransactionNo('');
    setDetails([{ productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0, itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''], productSearch: '', assignSerials: false }]);
  };

  const refreshLists = () => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
  };

  const handleSave = async () => {
    if (!isValid || saving) return;
    setSaving(true);

    try {
      const res = await purchaseApiService.create(buildPayload());
      if (res) {
        Swal.fire({
          icon: 'success',
          title: 'Success',
          text: 'Purchase recorded successfully',
          timer: 2000,
          showConfirmButton: false
        });
        refreshLists();
        setShowNewVoucherForm(false);
        resetFormFields();
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

  const handleSaveDraft = async () => {
    if (saving) return;
    const draftValid = selectedSupplierId > 0 && selectedStaffId > 0
      && details.some((d) => d.productId > 0 && d.qty > 0 && d.unitCost >= 0);
    if (!draftValid) {
      Swal.fire({ icon: 'warning', title: 'မူကြမ်းသိမ်းဆည်းရန်', text: 'ပေးသွင်းသူ၊ ဝယ်ယူသူနှင့် ပစ္စည်းအနည်းဆုံးတစ်ခု ရွေးချယ်ပါ။' });
      return;
    }
    setSaving(true);
    try {
      await purchaseApiService.create(buildPayload('DRAFT'));
      Swal.fire({ icon: 'success', title: 'မူကြမ်း သိမ်းဆည်းပြီး', text: 'Draft purchase saved. Stock/Journal မဖန်တီးရသေးပါ။', timer: 2200, showConfirmButton: false });
      refreshLists();
      setShowNewVoucherForm(false);
      resetFormFields();
    } catch (error: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: error.message || 'Failed to save draft' });
    } finally {
      setSaving(false);
    }
  };

  const handleAttachmentChange = (file: File | null) => {
    if (!file) { setAttachmentName(''); setAttachmentData(''); return; }
    if (file.size > 2 * 1024 * 1024) {
      Swal.fire({ icon: 'warning', title: 'File ကြီးလွန်းသည်', text: 'Attachment အတွက် 2MB အောက် ဖိုင်သာ ရွေးပါ။' });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => { setAttachmentName(file.name); setAttachmentData(reader.result as string); };
    reader.readAsDataURL(file);
  };

  const applyScannedProduct = (code: string) => {
    const clean = (code || '').trim().toLowerCase();
    if (!clean) return false;
    const product = products.find((p) =>
      (p.productCode || '').toLowerCase() === clean
      || p.name.toLowerCase() === clean
    );
    if (!product) {
      Swal.fire({ icon: 'warning', title: 'မတွေ့ပါ', text: `Code "${code}" နှင့်ကိုက်ညီသော ပစ္စည်း မရှိပါ။` });
      return false;
    }
    setDetails((prev) => {
      const idx = prev.findIndex((d) => d.productId === product.id);
      if (idx >= 0) {
        const next = [...prev];
        const row = { ...next[idx], qty: (Number(next[idx].qty) || 0) + 1 };
        const baseWarranty = Number(row.warrantyMonths ?? 0);
        const qty = row.qty;
        const resizedW = [...(row.itemWarranties || [])];
        while (resizedW.length < qty) resizedW.push(baseWarranty);
        row.itemWarranties = resizedW.slice(0, qty);
        if (isSerialRequired(row.productId)) {
          row.serialNumbers = resizeSerials(row.serialNumbers || [], qty);
          row.serialConditions = resizeStrings(row.serialConditions || [], qty);
          row.serialPhotos = resizeStrings(row.serialPhotos || [], qty);
        }
        row.subtotal = row.qty * row.unitCost;
        next[idx] = row;
        return next;
      }
      const blankIdx = prev.findIndex((d) => !d.productId);
      const newRow: PurchaseDetailForm = {
        productId: product.id,
        qty: 1, unitCost: Number(product.costPrice ?? 0) || 0,
        subtotal: 0, warrantyMonths: 0,
        itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''],
        productSearch: getProductLabel(product), assignSerials: false
      };
      newRow.subtotal = newRow.qty * newRow.unitCost;
      if (blankIdx >= 0) {
        const next = [...prev];
        next[blankIdx] = newRow;
        return next;
      }
      return [...prev, newRow];
    });
    Swal.fire({ icon: 'success', title: product.name, text: 'Row ထဲသို့ ထည့်ပြီးပါပြီ', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    return true;
  };

  const handleBarcodeDetected = (code: string) => {
    setIsBarcodeOpen(false);
    applyScannedProduct(code);
  };

  const handleBarcodeInputSubmit = () => {
    const code = barcodeInput.trim();
    if (!code) return;
    applyScannedProduct(code);
    setBarcodeInput('');
    barcodeInputRef.current?.focus();
  };

  const openReorderModal = async () => {
    setReorderSearch('');
    setShowReorderModal(true);
    setReorderLoading(true);
    try {
      const list = await purchaseApiService.getReorderSuggestions();
      setReorderSuggestions(list);
      setSelectedReorder(new Set(list.filter((r) => r.productId && r.suggestedQty > 0).map((r) => r.productId)));
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: e.message || 'Failed to load reorder suggestions' });
      setShowReorderModal(false);
    } finally {
      setReorderLoading(false);
    }
  };

  const filteredReorderSuggestions = useMemo(() => {
    const query = reorderSearch.trim().toLocaleLowerCase();
    if (!query) return reorderSuggestions;
    return reorderSuggestions.filter((row) => [
      row.productName,
      row.productCode,
      row.hasSerial ? 'serial' : 'non serial',
      String(row.stockQty ?? ''),
      String(row.reorderLevel ?? '')
    ].some((value) => String(value || '').toLocaleLowerCase().includes(query)));
  }, [reorderSearch, reorderSuggestions]);

  const toggleReorderItem = (productId: number) => {
    setSelectedReorder((prev) => {
      const next = new Set(prev);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  const toggleAllReorderItems = () => {
    const visibleIds = filteredReorderSuggestions.filter((r) => r.productId && r.suggestedQty > 0).map((r) => r.productId);
    setSelectedReorder((prev) => {
      const next = new Set(prev);
      const allVisibleSelected = visibleIds.length > 0 && visibleIds.every((id) => next.has(id));
      visibleIds.forEach((id) => allVisibleSelected ? next.delete(id) : next.add(id));
      return next;
    });
  };

  const importReorderIntoForm = () => {
    const usable = reorderSuggestions.filter((r) => selectedReorder.has(r.productId) && r.suggestedQty > 0);
    if (usable.length === 0) return;
    const rows: PurchaseDetailForm[] = usable.map((r) => {
      const row: PurchaseDetailForm = {
        productId: r.productId,
        qty: r.suggestedQty,
        unitCost: Number(r.lastCost ?? 0),
        subtotal: r.suggestedQty * Number(r.lastCost ?? 0),
        warrantyMonths: 0,
        itemWarranties: Array.from({ length: r.suggestedQty }, () => 0),
        serialNumbers: [], serialConditions: [], serialPhotos: [],
        productSearch: r.productName ? `${r.productName} (${r.productCode})` : '',
        assignSerials: false
      };
      return row;
    });
    setDetails(rows);
    setShowReorderModal(false);
    setShowNewVoucherForm(true);
    Swal.fire({ icon: 'success', title: 'Import ပြီးပါပြီ', text: `${rows.length} မျိုး ဝယ်ယူမှုဖောင်ထဲသို့ ထည့်ပြီး။`, toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
  };

  const handleConfirmDraft = async (p: PurchaseDTO) => {
    if (rowActionBusy) return;
    const result = await Swal.fire({
      icon: 'question',
      title: 'မူကြမ်း အတည်ပြုမည်လား?',
      html: `<b>${p.purchaseCode || `#${p.id}`}</b> — Stock၊ Serial၊ Journal များ ဖန်တီးမည်ဖြစ်သည်။<br/><span style="font-size:12px;color:#94a3b8">Serial ပစ္စည်းပါရှိလျှင် ဤမူကြမ်းကို ပယ်ဖျက်ပြီး အသစ်ပြန်ဖွင့်၍ Serial များနှင့်အတူ သိမ်းပါ။</span>`,
      showCancelButton: true,
      confirmButtonText: 'အတည်ပြု',
      cancelButtonText: 'မလုပ်တော့'
    });
    if (!result.isConfirmed) return;
    setRowActionBusy(true);
    try {
      await purchaseApiService.confirmDraft(p.id!);
      Swal.fire({ icon: 'success', title: 'အတည်ပြုပြီး', text: 'Purchase confirmed successfully', timer: 1800, showConfirmButton: false });
      refreshLists();
      if (viewPurchase?.id === p.id) closeView();
    } catch (error: any) {
      Swal.fire({ icon: 'error', title: 'Error', text: error.message || 'Failed to confirm draft' });
    } finally {
      setRowActionBusy(false);
    }
  };

  const handleCancelPurchase = async (p: PurchaseDTO) => {
    if (rowActionBusy) return;
    const isDraft = (p.status || '').toUpperCase() === 'DRAFT';
    const result = await Swal.fire({
      icon: 'warning',
      title: isDraft ? 'မူကြမ်း ပယ်ဖျက်မည်လား?' : 'ဘောင်ချာ ပယ်ဖျက်မည်လား?',
      html: isDraft
        ? `<b>${p.purchaseCode || `#${p.id}`}</b> ကို အပြီးအပိုင် ဖျက်မည်။`
        : `<b>${p.purchaseCode || `#${p.id}`}</b> — Stock ပြန်နုတ်၊ Journal ပြန်ပြင်မည်။<br/><span style="font-size:12px;color:#ef4444">Return ရှိပြီးသော voucher များကို ပယ်ဖျက်မရပါ။</span>`,
      showCancelButton: true,
      confirmButtonText: 'ပယ်ဖျက်',
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonColor: '#dc2626'
    });
    if (!result.isConfirmed) return;
    setRowActionBusy(true);
    try {
      await purchaseApiService.cancel(p.id!);
      Swal.fire({ icon: 'success', title: isDraft ? 'မူကြမ်း ဖျက်ပြီး' : 'ပယ်ဖျက်ပြီး', timer: 1500, showConfirmButton: false });
      refreshLists();
      if (viewPurchase?.id === p.id) closeView();
    } catch (error: any) {
      Swal.fire({ icon: 'error', title: 'Cannot cancel', text: error.message || 'Failed to cancel purchase' });
    } finally {
      setRowActionBusy(false);
    }
  };

  const handleExportExcel = async () => {
    if (exportingExcel) return;
    setExportingExcel(true);
    try {
      await purchaseApiService.exportExcel(dateFrom, dateTo);
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Export failed', text: e.message || 'Failed to export excel' });
    } finally {
      setExportingExcel(false);
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

  const [viewAttachmentBusy, setViewAttachmentBusy] = useState(false);
  const viewAttachmentInputRef = useRef<HTMLInputElement | null>(null);

  const handleViewAttachmentFile = async (file: File | null) => {
    if (!file || !viewPurchase?.id) return;
    if (!file.type.startsWith('image/') && file.type !== 'application/pdf') {
      Swal.fire('Error', 'ပုံ (image) ဒါမှမဟုတ် PDF ဖိုင်ပဲ ရွေးနိုင်ပါတယ်', 'error');
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      Swal.fire('Error', 'ဖိုင် size က 2MB အောက် ဖြစ်ရမည်', 'error');
      return;
    }
    const result = await Swal.fire({
      icon: 'question',
      title: 'ပေးသွင်းသူဘောင်ချာ ပြောင်းမလား?',
      text: file.name,
      showCancelButton: true,
      confirmButtonText: 'ပြောင်းမည်',
      cancelButtonText: 'မလုပ်တော့ဘူး',
      confirmButtonColor: '#4f46e5'
    });
    if (!result.isConfirmed) return;
    setViewAttachmentBusy(true);
    try {
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      const updated = await purchaseApiService.updateAttachment(viewPurchase.id, file.name, dataUrl);
      setViewPurchase((prev) => prev ? { ...prev, attachmentData: updated.attachmentData, attachmentName: updated.attachmentName } : prev);
      Swal.fire({ icon: 'success', title: 'Attachment Updated', timer: 1400, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to update attachment', 'error');
    } finally {
      setViewAttachmentBusy(false);
      if (viewAttachmentInputRef.current) viewAttachmentInputRef.current.value = '';
    }
  };

  const handleViewAttachmentRemove = async () => {
    if (!viewPurchase?.id || viewAttachmentBusy) return;
    const result = await Swal.fire({
      icon: 'warning',
      title: 'Attachment ဖယ်ရှားမလား?',
      text: viewPurchase.attachmentName || '',
      showCancelButton: true,
      confirmButtonText: 'ဖယ်ရှားမည်',
      cancelButtonText: 'မလုပ်တော့ဘူး',
      confirmButtonColor: '#e11d48'
    });
    if (!result.isConfirmed) return;
    setViewAttachmentBusy(true);
    try {
      await purchaseApiService.updateAttachment(viewPurchase.id);
      setViewPurchase((prev) => prev ? { ...prev, attachmentData: undefined, attachmentName: undefined } : prev);
      Swal.fire({ icon: 'success', title: 'Attachment Removed', timer: 1400, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e.message || 'Failed to remove attachment', 'error');
    } finally {
      setViewAttachmentBusy(false);
    }
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
    const supplierDays = Math.max(0, Number(supplier.defaultCreditDays ?? 30));
    setPaymentTermDays(supplierDays);
    setDueDate(addDaysInput(purchaseDate, supplierDays));
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
    const voucherStatus = (p.status || '').trim().toUpperCase();
    if (voucherStatus === 'DRAFT') return 'draft';
    if (voucherStatus === 'CANCELLED') return 'cancelled';
    const backendStatus = (p.paymentStatus || '').trim();
    if (backendStatus) return normalizeStatusKey(backendStatus);
    if (p.dueAmount > 0 && p.paidAmount > 0) return 'partial';
    if (p.dueAmount > 0) return 'due';
    return 'paid';
  };

  const getStatusDisplay = (p: PurchaseDTO) => {
    const voucherStatus = (p.status || '').trim().toUpperCase();
    if (voucherStatus === 'DRAFT') return 'Draft';
    if (voucherStatus === 'CANCELLED') return 'Cancelled';
    const backendStatus = (p.paymentStatus || '').trim();
    if (backendStatus) return backendStatus;
    if (p.dueAmount > 0 && p.paidAmount > 0) return 'Partial';
    if (p.dueAmount > 0) return 'Due';
    return 'Paid';
  };

  const getStatusLabel = (p: PurchaseDTO) => {
    const key = getStatusKey(p);
    if (key === 'draft') return 'မူကြမ်း';
    if (key === 'cancelled') return 'ပယ်ဖျက်ပြီး';
    if (key === 'paid') return 'ငွေချေပြီး';
    if (key === 'partial') return 'တစ်စိတ်တစ်ပိုင်း';
    if (key === 'due') return 'ပေးရန်ကျန်';
    return getStatusDisplay(p);
  };

  const isDraftPurchase = (p: PurchaseDTO) => getStatusKey(p) === 'draft';
  const canDeletePurchases = (currentUser.permissions || []).includes('CAN_ACCESS_PURCHASE_DELETE');
  const canUpdatePurchases = (currentUser.permissions || []).includes('CAN_ACCESS_PURCHASE_UPDATE');

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
    due: 'bg-rose-100 text-rose-700',
    draft: 'bg-sky-100 text-sky-700 border border-sky-200',
    cancelled: 'bg-slate-200 text-slate-500 line-through'
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
              <button onClick={handleExportExcel} disabled={exportingExcel} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-emerald-200 rounded-lg text-xs font-bold text-emerald-700 hover:bg-emerald-50 disabled:opacity-60">
                {exportingExcel ? <Loader2 size={14} className="animate-spin" /> : <FileSpreadsheet size={14} />}
                Excel
              </button>
              <button onClick={() => void openReorderModal()} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-amber-200 rounded-lg text-xs font-bold text-amber-700 hover:bg-amber-50">
                <ClipboardList size={14} />
                Reorder
              </button>
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
            <div className="grid grid-cols-1 xl:grid-cols-[1fr_0.85fr_0.85fr] gap-3">
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

              <div className="rounded-xl border border-teal-100 bg-teal-50 p-3">
                <label className="text-[11px] font-bold uppercase tracking-wide text-teal-700">ပစ္စည်း Stock Search</label>
                <div className="relative mt-1.5">
                  <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-teal-400" />
                  <input
                    list="purchase-page-product-options"
                    type="text"
                    value={purchaseProductSearch}
                    onChange={(event) => {
                      const value = event.target.value;
                      setPurchaseProductSearch(value);
                      const product = products.find((item) => getProductLabel(item).toLowerCase() === value.trim().toLowerCase());
                      if (product) void openProductHistory(product);
                    }}
                    placeholder="ပစ္စည်းအမည် / Code ရှာပါ..."
                    className="h-10 w-full rounded-lg border border-teal-200 bg-white pl-9 pr-3 text-sm font-medium text-slate-700 outline-none focus:border-teal-500"
                  />
                  <datalist id="purchase-page-product-options">
                    {products.map((product) => <option key={product.id} value={getProductLabel(product)} />)}
                  </datalist>
                </div>
                <p className="mt-1.5 text-[10px] font-semibold text-teal-600">ရွေးပြီးလျှင် Stock History အလိုအလျောက်ဖွင့်မည်</p>
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
                         const canPay = statusKey !== 'paid' && p.dueAmount > 0 && statusKey !== 'draft' && statusKey !== 'cancelled';
                         const isDraftRow = statusKey === 'draft';
                         const isCancelledRow = statusKey === 'cancelled';
                         return (
                           <tr key={p.id!} className={`h-[58px] hover:bg-slate-50/80 transition-colors ${isCancelledRow ? 'opacity-60' : ''}`}>
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
                                 {isDraftRow && canDeletePurchases && (
                                   <button
                                     onClick={() => handleConfirmDraft(p)}
                                     disabled={rowActionBusy}
                                     className="inline-flex h-8 items-center gap-1 rounded-md border border-emerald-200 bg-emerald-600 px-2.5 text-xs font-bold text-white hover:bg-emerald-700 disabled:opacity-60"
                                     title="မူကြမ်းကို အတည်ပြုမည် (Stock/Journal ဖန်တီးမည်)"
                                   >
                                     <CheckCircle size={14} /> အတည်ပြု
                                   </button>
                                 )}
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
                                 {(isDraftRow || (!isCancelledRow && canDeletePurchases)) && (
                                   <button
                                     onClick={() => handleCancelPurchase(p)}
                                     disabled={rowActionBusy}
                                     className="inline-flex h-8 items-center gap-1 rounded-md border border-slate-300 px-2.5 text-xs font-bold text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-60"
                                     title={isDraftRow ? 'မူကြမ်း ဖျက်မည်' : 'ဘောင်ချာ ပယ်ဖျက် (Void)'}
                                   >
                                     <Ban size={14} />
                                   </button>
                                 )}
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
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <button type="button" onClick={() => setShowNewVoucherForm(false)} className="inline-flex items-center gap-2 px-3 py-1.5 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium">
              <ArrowLeft size={16} />
              စာရင်းသို့ပြန်မည်
            </button>
            <h2 className="text-xl font-bold text-slate-800 text-center">ဝယ်ယူမှုဘောင်ချာအသစ်</h2>
            <button type="button" onClick={resetFormFields} className="inline-flex items-center justify-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">
              ရှင်းမည်
            </button>
          </div>

          <div className="bg-white rounded-xl border border-slate-200 p-4 sm:p-5 space-y-5">
            <div className="grid grid-cols-1 xl:grid-cols-4 gap-3">
              <div>
                <div className="flex h-[28px] items-center justify-between">
                  <label className="text-xs font-semibold text-slate-600">ပေးသွင်းသူ <span className="text-rose-500">*</span></label>
                  <button
                    type="button"
                    onClick={openQuickSupplierModal}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded border border-indigo-200 text-indigo-700 text-[11px] font-semibold hover:bg-indigo-50"
                  >
                    <Plus size={11} /> အသစ်
                  </button>
                </div>
                <div className="relative">
                  <input
                    value={supplierSearch && supplierSearch.length > 0 ? supplierSearch : getSupplierLabelById(selectedSupplierId)}
                    onChange={(e) => {
                      handleSupplierSearchChange(e.target.value);
                      setSupplierOpen(true);
                    }}
                    onFocus={() => setSupplierOpen(true)}
                    onBlur={() => setTimeout(() => setSupplierOpen(false), 120)}
                    placeholder="အမည် / ကုဒ် / ဖုန်းနံပါတ်ဖြင့်ရှာပါ..."
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                  />
                  {supplierOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                      {filteredSuppliers.length > 0 ? filteredSuppliers.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onMouseDown={() => handleSupplierSelect(s)}
                          className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${selectedSupplierId === s.id ? 'bg-indigo-50' : ''}`}
                        >
                          <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                          <p className="text-xs text-slate-400">{s.code || '-'} {s.phone ? `· ${s.phone}` : ''}</p>
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

              <div>
                <label className="flex h-[28px] items-center text-xs font-semibold text-slate-600">ဝယ်ယူမှုတာဝန်ခံ <span className="text-rose-500">*</span></label>
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
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400 disabled:bg-slate-100"
                  />
                  {staffOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                      {filteredStaffs.length > 0 ? filteredStaffs.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onMouseDown={() => handleStaffSelect(s)}
                          className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${selectedStaffId === s.id ? 'bg-indigo-50' : ''}`}
                        >
                          <p className="text-sm font-semibold text-slate-800">{s.name}</p>
                          <p className="text-xs text-slate-400">{s.role || '-'} {s.active === false ? '· Inactive' : ''}</p>
                        </button>
                      )) : <p className="px-3 py-3 text-xs text-slate-400 text-center">ဝန်ထမ်း မတွေ့ပါ။</p>}
                    </div>
                  )}
                </div>
              </div>

              <div>
                <label className="flex h-[28px] items-center text-xs font-semibold text-slate-600">ဝယ်ရက်</label>
                <input
                  type="date"
                  value={purchaseDate}
                  onChange={(e) => {
                    const next = e.target.value;
                    setPurchaseDate(next);
                    if (paymentTermDays >= 0) setDueDate(addDaysInput(next, paymentTermDays));
                  }}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                />
              </div>

              <div>
                <label className="flex h-[28px] items-center text-xs font-semibold text-slate-600">ပေးသွင်းသူဘောင်ချာ</label>
                {attachmentData ? (
                  <div className="flex h-[38px] items-center justify-between gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-2.5">
                    <span className="truncate text-xs font-bold text-emerald-700">{attachmentName}</span>
                    <button type="button" onClick={() => handleAttachmentChange(null)} className="shrink-0 p-1 text-slate-400 hover:text-rose-600" title="ဖယ်ရှားမည်"><X size={14} /></button>
                  </div>
                ) : (
                  <label className="flex h-[38px] cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-dashed border-slate-300 bg-slate-50 px-2.5 text-xs font-semibold text-slate-500 hover:border-indigo-400 hover:text-indigo-600">
                    <Upload size={13} /> ဖိုင်ရွေး
                    <input type="file" accept="image/*,.pdf" className="hidden" onChange={(e) => handleAttachmentChange(e.target.files?.[0] || null)} />
                  </label>
                )}
              </div>
            </div>

            <div className={`rounded-lg border px-4 py-2.5 ${
              !selectedSupplier
                ? 'bg-slate-50 text-slate-600 border-slate-200'
                : 'bg-emerald-50 text-emerald-700 border-emerald-200'
            }`}>
              <p className="text-sm font-semibold inline-flex items-center gap-2">
                {!selectedSupplier ? <User size={15} /> : <CheckCircle size={15} />}
                {!selectedSupplier
                  ? 'ပေးသွင်းသူ ရွေးပြီး ပစ္စည်းထည့်ပါ'
                  : `${selectedSupplier.name}${selectedStaff ? ` · ${selectedStaff.name}` : ''} · ${filledItemCount} မျိုး · ကျသင့်ငွေ ${money(netAmount)}`}
              </p>
            </div>

            <div className="flex items-center gap-2 px-3 py-2 bg-violet-50 border border-violet-200 rounded-lg">
              <ScanLine size={15} className="text-violet-400 shrink-0" />
              <input
                ref={barcodeInputRef}
                type="text"
                value={barcodeInput}
                onChange={(e) => setBarcodeInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleBarcodeInputSubmit(); } }}
                placeholder="Barcode scan သို့မဟုတ် product code ရိုက်ပြီး Enter"
                className="flex-1 bg-transparent outline-none text-sm font-medium text-violet-800 placeholder:text-violet-300 min-w-0"
                autoComplete="off"
              />
              <button
                type="button"
                onClick={() => setIsBarcodeOpen(true)}
                title="ကင်မရာဖြင့် scan မည်"
                className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 bg-violet-600 hover:bg-violet-700 text-white rounded-lg text-[11px] font-black uppercase tracking-wide"
              >
                <Camera size={13} /> Camera
              </button>
            </div>

            <div className="border border-slate-200 rounded-lg overflow-auto">
              <table className="w-full min-w-[820px] text-xs">
                <thead className="bg-slate-100 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                  <tr>
                    <th className="px-3 py-2.5 text-left">ပစ္စည်း</th>
                    <th className="px-3 py-2.5 text-left w-20">Qty</th>
                    <th className="px-3 py-2.5 text-left w-32">ဝယ်ဈေး</th>
                    <th className="px-3 py-2.5 text-left w-28">အာမခံ (လ)</th>
                    <th className="px-3 py-2.5 text-right w-32">စုစုပေါင်း</th>
                    <th className="px-3 py-2.5 w-10"></th>
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
                      <tr className="border-b border-slate-100 hover:bg-slate-50/50">
                        <td className="px-3 py-2">
                          <div className="relative flex items-center gap-1.5">
                            <input
                              list={`product-options-${dIndex}`}
                              value={detail.productSearch && detail.productSearch.length > 0 ? detail.productSearch : getProductLabelById(detail.productId)}
                              onChange={(e) => handleProductSearchChange(dIndex, e.target.value)}
                              placeholder="ပစ္စည်းရှာပါ..."
                              className="min-w-0 flex-1 px-2 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400"
                            />
                            {_rowProduct && (
                              <button type="button" title="Stock History" onClick={() => void openProductHistory(_rowProduct)} className="shrink-0 rounded-md border border-teal-200 bg-teal-50 p-1.5 text-teal-700 hover:bg-teal-100">
                                <ClipboardList size={14} />
                              </button>
                            )}
                            <datalist id={`product-options-${dIndex}`}>
                              {products.map((product) => (
                                <option key={product.id} value={getProductLabel(product)}>
                                  Stock: {Number(product.stockQty ?? product.currentStock ?? 0).toLocaleString()}
                                </option>
                              ))}
                            </datalist>
                          </div>
                          {_rowProduct && (
                            <div className="mt-1 flex flex-wrap items-center gap-1.5">
                              <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold ${isSerialRequired(detail.productId) ? 'bg-indigo-50 text-indigo-700' : 'bg-slate-100 text-slate-600'}`}>
                                {isSerialRequired(detail.productId) ? 'Serial' : 'Qty only'}
                              </span>
                              <span className="text-[10px] font-semibold text-slate-400">Stock {Number(_rowProduct.stockQty ?? _rowProduct.currentStock ?? 0).toLocaleString()}</span>
                            </div>
                          )}
                        </td>
                        <td className="px-3 py-2">
                          <input
                            type="number"
                            min="1"
                            value={detail.qty}
                            onChange={(e) => handleDetailChange(dIndex, 'qty', e.target.value)}
                            className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400"
                          />
                        </td>
                        <td className="px-3 py-2">
                          <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={detail.unitCost || ''}
                            onChange={(e) => handleDetailChange(dIndex, 'unitCost', parseFloat(e.target.value) || 0)}
                            placeholder="0.00"
                            className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400"
                          />
                          {_rowProduct?.costPrice ? <p className="mt-1 text-[10px] text-slate-400">နောက်ဆုံး {money(Number(_rowProduct.costPrice))}</p> : null}
                        </td>
                        <td className="px-3 py-2">
                          <div className="space-y-1">
                            <input
                              type="number"
                              min="0"
                              value={(detail as any).warrantyMonths ?? 0}
                              onChange={(e) => handleDetailChange(dIndex, 'warrantyMonths', parseInt(e.target.value) || 0)}
                              placeholder="0"
                              className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400"
                            />
                            <button
                              type="button"
                              onClick={() => applyWarrantyToAllItems(dIndex)}
                              className="text-[10px] font-semibold text-indigo-600 hover:underline"
                            >
                              အားလုံးသို့သုံး
                            </button>
                          </div>
                        </td>
                        <td className="px-3 py-2 text-right font-bold text-slate-700">
                          {money(detail.subtotal)}
                        </td>
                        <td className="px-3 py-2 text-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveRow(dIndex)}
                            className="p-1.5 rounded text-slate-400 hover:text-rose-500 hover:bg-rose-50 disabled:opacity-30"
                            disabled={details.length <= 1}
                          >
                            <Trash2 size={13} />
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
                                  <Hash size={10} /> Serial နံပါတ်ထည့်မည်
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
                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">တစ်ခုချင်း အာမခံ (လ)</span>
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
                                    <Hash size={11} /> Internal Serial
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
                                    Serial ဖယ်မည်
                                  </button>
                                </>
                              ) : (
                                <>
                                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 text-[10px] font-bold uppercase tracking-wider text-slate-600">
                                    <Box size={11} /> Qty သာ
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
                                      <Hash size={11} /> Internal Serial ထည့်မည်
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
                                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">တစ်ခုချင်း အာမခံ (လ)</span>
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

            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <button type="button" onClick={handleAddRow} className="inline-flex flex-shrink-0 items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700">
                <Plus size={14} /> ပစ္စည်းထပ်ထည့်
              </button>
              <div className="flex flex-col sm:flex-row items-center gap-3 sm:gap-4 ml-auto w-full sm:w-auto">
                <div className="flex items-center gap-4">
                  <div className="text-right">
                    <p className="text-[11px] text-slate-400 uppercase font-semibold">ကုန်ဖိုး</p>
                    <p className="text-sm font-bold text-slate-700">{money(totalAmount)}</p>
                  </div>
                  {(safeDiscountAmount > 0 || safeTaxAmount > 0 || safeOtherCharges > 0) && (
                    <div className="text-right">
                      <p className="text-[11px] text-slate-400 uppercase font-semibold">Discount / Tax</p>
                      <p className="text-sm font-bold text-slate-600">
                        {safeDiscountAmount > 0 ? `-${money(safeDiscountAmount)}` : money(0)}
                        {(safeTaxAmount > 0 || safeOtherCharges > 0) ? ` + ${money(safeTaxAmount + safeOtherCharges)}` : ''}
                      </p>
                    </div>
                  )}
                  <div className="text-right border-l border-slate-200 pl-4">
                    <p className="text-[11px] text-slate-400 uppercase font-semibold">ကျသင့်ငွေ</p>
                    <p className="text-lg font-black text-slate-900">{money(netAmount)}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setPaymentItemsOpen(false);
                    setSplitPaymentOpen(purchasePayments.length > 0);
                    setIsVoucherPaymentModalOpen(true);
                  }}
                  disabled={details.every((d) => d.productId <= 0)}
                  className="w-full sm:w-auto px-5 py-2 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50 inline-flex items-center justify-center gap-2"
                >
                  <CreditCard size={14} /> ငွေရှင်းမည်
                </button>
              </div>
            </div>
            {voucherHint && (
              <p className="text-[11px] font-semibold text-amber-700">{voucherHint}</p>
            )}
          </div>

        {isVoucherPaymentModalOpen && (
          <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-0 backdrop-blur-sm sm:items-center sm:p-4" onMouseDown={() => setIsVoucherPaymentModalOpen(false)}>
            <div className="flex h-[100dvh] w-full flex-col overflow-hidden rounded-none bg-white shadow-2xl sm:h-auto sm:max-h-[calc(100dvh-2rem)] sm:max-w-lg sm:rounded-2xl" onMouseDown={(e) => e.stopPropagation()}>
              <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2.5 sm:px-5">
                <div className="flex items-center gap-2 min-w-0">
                  <CreditCard size={16} className="text-emerald-600 shrink-0" />
                  <h3 className="text-sm font-bold text-slate-800">ငွေရှင်းခြင်း</h3>
                  <span className={`inline-flex px-2 py-0.5 rounded-md text-[10px] font-bold border ${dueAmount > 0 ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200'}`}>
                    {dueAmount > 0 ? 'အကြွေး' : 'ငွေချေပြီး'}
                  </span>
                </div>
                <button type="button" onClick={() => setIsVoucherPaymentModalOpen(false)} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600">
                  <X size={16} />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto px-4 py-3 space-y-2.5 sm:px-5">
                <div className="rounded-lg border border-slate-200 overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setPaymentItemsOpen((open) => !open)}
                    className="w-full flex items-center justify-between gap-2 bg-slate-50 px-3 py-2 text-left"
                  >
                    <span className="text-xs font-semibold text-slate-600">{filledItemCount} မျိုး</span>
                    <span className="flex items-center gap-2">
                      <span className="text-sm font-black text-slate-900">{money(totalAmount)}</span>
                      {paymentItemsOpen ? <ChevronUp size={14} className="text-slate-400" /> : <ChevronDown size={14} className="text-slate-400" />}
                    </span>
                  </button>
                  {paymentItemsOpen && (
                    <div className="max-h-28 overflow-y-auto divide-y divide-slate-100 border-t border-slate-100">
                      {details.filter((d) => d.productId > 0).map((d, i) => {
                        const prod = products.find((p) => p.id === d.productId);
                        return (
                          <div key={i} className="flex items-center justify-between px-3 py-1.5 text-xs">
                            <p className="min-w-0 truncate text-slate-700">{prod?.name ?? d.productSearch ?? '—'} <span className="text-slate-400">· {d.qty} × {money(d.unitCost)}</span></p>
                            <span className="ml-2 shrink-0 font-semibold text-slate-800">{money(d.subtotal)}</span>
                          </div>
                        );
                      })}
                      {filledItemCount === 0 && <p className="px-3 py-2 text-xs text-slate-400 text-center">ပစ္စည်း မထည့်ရသေးပါ</p>}
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">လျှော့ဈေး</label>
                    <input
                      type="number"
                      min="0"
                      max={totalAmount || undefined}
                      value={discountAmount || ''}
                      onChange={(e) => setDiscountAmount(Math.min(totalAmount, Math.max(0, parseFloat(e.target.value) || 0)))}
                      placeholder="0"
                      className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                    />
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Tax</label>
                    <input
                      type="number"
                      min="0"
                      value={taxAmount || ''}
                      onChange={(e) => setTaxAmount(Math.max(0, parseFloat(e.target.value) || 0))}
                      placeholder="0"
                      className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                    />
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">အခြားကုန်ကျ</label>
                    <input
                      type="number"
                      min="0"
                      value={otherCharges || ''}
                      onChange={(e) => setOtherCharges(Math.max(0, parseFloat(e.target.value) || 0))}
                      placeholder="0"
                      className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-between rounded-lg bg-slate-900 px-3 py-2 text-white">
                  <span className="text-xs font-semibold">ကျသင့်ငွေ</span>
                  <span className="text-base font-black tracking-tight">{money(netAmount)}</span>
                </div>

                <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2.5 space-y-2">
                  <div className="flex items-center gap-2">
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={paidAmount || ''}
                      onChange={(e) => { setPaidAmount(parseFloat(e.target.value) || 0); if (purchasePayments.length > 0) setPurchasePayments([]); }}
                      placeholder="ပေးချေငွေ"
                      className="min-w-0 flex-1 px-3 py-2 rounded-lg border border-emerald-300 bg-white text-base font-bold text-emerald-900 focus:outline-none focus:border-emerald-500"
                    />
                    <button type="button" onClick={() => { setPaidAmount(netAmount); setPurchasePayments([]); setSplitPaymentOpen(false); }} className="px-2.5 py-2 rounded-lg bg-emerald-600 text-white text-[11px] font-bold hover:bg-emerald-700 shrink-0">Full</button>
                    <button type="button" onClick={() => { setPaidAmount(0); setPurchasePayments([]); setSplitPaymentOpen(false); }} className="px-2.5 py-2 rounded-lg bg-amber-500 text-white text-[11px] font-bold hover:bg-amber-600 shrink-0">အကြွေး</button>
                  </div>

                  {effectivePaidAmount > 0 && !splitPaymentOpen && (
                    <div className="grid grid-cols-2 gap-2">
                      <select
                        value={selectedPaymentMethodId}
                        onChange={(e) => setSelectedPaymentMethodId(Number(e.target.value))}
                        className={`w-full px-2 py-1.5 rounded-lg border bg-white text-sm focus:outline-none focus:border-emerald-500 ${
                          selectedPaymentMethodId > 0 ? 'border-emerald-300' : 'border-rose-300'
                        }`}
                      >
                        <option value={0}>ငွေပေးချေနည်း</option>
                        {paymentMethods.map((m) => (
                          <option key={m.id} value={m.id}>{m.methodName}</option>
                        ))}
                      </select>
                      <input
                        type="text"
                        value={transactionNo}
                        onChange={(e) => setTransactionNo(e.target.value)}
                        placeholder="Txn no"
                        className="w-full px-2 py-1.5 rounded-lg border border-emerald-300 bg-white text-sm focus:outline-none focus:border-emerald-500"
                      />
                    </div>
                  )}

                  {splitPaymentOpen ? (
                    <div className="space-y-1.5">
                      <SplitPaymentEditor
                        methods={paymentMethods}
                        payments={purchasePayments}
                        onChange={(next) => {
                          setPurchasePayments(next);
                          setPaidAmount(paymentTotal(next));
                        }}
                        label="ခွဲပေးငွေ"
                        compact
                      />
                      <button
                        type="button"
                        onClick={() => { setSplitPaymentOpen(false); setPurchasePayments([]); }}
                        className="text-[11px] font-semibold text-slate-500 hover:text-slate-700"
                      >
                        ပုံမှန်ပေးချေမှုသို့ ပြန်မည်
                      </button>
                    </div>
                  ) : (
                    effectivePaidAmount > 0 && (
                      <button
                        type="button"
                        onClick={() => {
                          setSplitPaymentOpen(true);
                          if (purchasePayments.length === 0 && selectedPaymentMethodId > 0 && paidAmount > 0) {
                            setPurchasePayments([{ paymentMethodId: selectedPaymentMethodId, amount: paidAmount, transactionNo: transactionNo || undefined }]);
                          }
                        }}
                        className="text-[11px] font-semibold text-emerald-800 hover:underline"
                      >
                        ငွေခွဲပေးမည်
                      </button>
                    )
                  )}

                  <div className={`flex items-center justify-between text-sm font-bold ${dueAmount > 0 ? 'text-rose-700' : 'text-emerald-800'}`}>
                    <span>{dueAmount > 0 ? 'ပေးရန်ကျန်' : 'အပြည့်ပေးပြီး'}</span>
                    <span>{money(dueAmount)}</span>
                  </div>
                </div>

                {dueAmount > 0 && (
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-500 mb-1">ငွေချေကာလ</label>
                      <select
                        value={paymentTermDays}
                        onChange={(e) => {
                          const days = Number(e.target.value);
                          setPaymentTermDays(days);
                          if (days >= 0) setDueDate(addDaysInput(purchaseDate, days));
                        }}
                        className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                      >
                        <option value={-1}>စိတ်ကြိုက်</option>
                        <option value={0}>ဒီနေ့</option>
                        <option value={7}>ရက် 7</option>
                        <option value={15}>ရက် 15</option>
                        <option value={30}>ရက် 30</option>
                        <option value={45}>ရက် 45</option>
                        <option value={60}>ရက် 60</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[10px] font-semibold text-slate-500 mb-1">နောက်ဆုံးရက်</label>
                      <input type="date" value={dueDate} onChange={(e) => { setDueDate(e.target.value); setPaymentTermDays(-1); }} className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
                    </div>
                  </div>
                )}

                <input
                  value={remark}
                  onChange={(e) => setRemark(e.target.value)}
                  placeholder="မှတ်ချက် (optional)"
                  className="w-full px-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                />

                {voucherHint && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 flex items-center gap-2">
                    <AlertCircle size={14} className="text-amber-600 flex-shrink-0" />
                    <p className="text-xs font-semibold text-amber-800">{voucherHint}</p>
                  </div>
                )}
              </div>

              <div className="flex gap-2 border-t border-slate-100 bg-white px-4 py-3 sm:px-5">
                <button
                  type="button"
                  disabled={saving || !selectedSupplierId || !details.some((d) => d.productId > 0 && d.qty > 0)}
                  onClick={handleSaveDraft}
                  className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2.5 text-sm font-semibold text-sky-700 hover:bg-sky-100 disabled:opacity-50"
                  title="Stock/Serial/Journal မဖန်တီးဘဲ မူကြမ်းအဖြစ်သိမ်းမည်"
                >
                  <FileText size={14} /> မူကြမ်း
                </button>
                <button
                  type="button"
                  disabled={!isValid || saving}
                  onClick={handleSave}
                  className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
                >
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} {saving ? 'သိမ်းနေသည်...' : 'ဘောင်ချာသိမ်းမည်'}
                </button>
              </div>
            </div>
          </div>
        )}
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
              <div className="flex items-center gap-2 min-w-0">
                <h3 className="font-bold text-slate-800 truncate">Purchase: {viewPurchase.purchaseCode || `#${viewPurchase.id}`}</h3>
                {(() => {
                  const vk = getStatusKey(viewPurchase);
                  if (vk !== 'draft' && vk !== 'cancelled') return null;
                  return (
                    <span className={`inline-flex shrink-0 rounded-md px-2 py-0.5 text-[10px] font-black ${statusStyles[vk]}`}>
                      {getStatusLabel(viewPurchase)}
                    </span>
                  );
                })()}
              </div>
              <div className="flex items-center gap-2">
                {getStatusKey(viewPurchase) === 'draft' && canDeletePurchases && (
                  <button
                    onClick={() => handleConfirmDraft(viewPurchase)}
                    disabled={rowActionBusy}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-emerald-600 border border-emerald-200 rounded-lg hover:bg-emerald-700 disabled:opacity-60"
                  >
                    <CheckCircle size={14} />
                    အတည်ပြု
                  </button>
                )}
                {(getStatusKey(viewPurchase) === 'draft' || (getStatusKey(viewPurchase) !== 'cancelled' && canDeletePurchases)) && (
                  <button
                    onClick={() => handleCancelPurchase(viewPurchase)}
                    disabled={rowActionBusy}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-slate-600 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100 disabled:opacity-60"
                  >
                    <Ban size={14} />
                    {getStatusKey(viewPurchase) === 'draft' ? 'ဖျက်မည်' : 'ပယ်ဖျက်'}
                  </button>
                )}
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
                <p className="text-slate-600"><span className="font-medium text-slate-500">Payment Term:</span> {viewPurchase.paymentTermDays != null ? `${viewPurchase.paymentTermDays} days` : '-'}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Total:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.totalAmount)}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Discount:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.discountAmount || 0)}</p>
                {(Number(viewPurchase.taxAmount) || 0) > 0 && (
                  <p className="text-slate-600"><span className="font-medium text-slate-500">Tax / VAT:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.taxAmount || 0)}</p>
                )}
                {(Number(viewPurchase.otherCharges) || 0) > 0 && (
                  <p className="text-slate-600"><span className="font-medium text-slate-500">Other Charges:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.otherCharges || 0)}</p>
                )}
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
              {(viewPurchase.attachmentData || canUpdatePurchases) && (
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                  <div className="mb-1.5 flex items-center justify-between gap-2">
                    <p className="text-[10px] font-black uppercase tracking-wider text-slate-400">Supplier Invoice Attachment</p>
                    {canUpdatePurchases && (
                      viewAttachmentBusy ? (
                        <span className="inline-flex items-center gap-1 text-[10px] font-bold text-slate-400"><Loader2 size={11} className="animate-spin" /> သိမ်းနေသည်...</span>
                      ) : (
                        <div className="flex items-center gap-1.5">
                          <button type="button" onClick={() => viewAttachmentInputRef.current?.click()} className="inline-flex items-center gap-1 rounded-md border border-indigo-200 bg-white px-2 py-1 text-[10px] font-bold text-indigo-700 hover:bg-indigo-50">
                            <Upload size={11} /> {viewPurchase.attachmentData ? 'ပြောင်းမည်' : 'ထည့်မည်'}
                          </button>
                          {viewPurchase.attachmentData && (
                            <button type="button" onClick={handleViewAttachmentRemove} className="inline-flex items-center gap-1 rounded-md border border-rose-200 bg-white px-2 py-1 text-[10px] font-bold text-rose-600 hover:bg-rose-50">
                              <X size={11} /> ဖယ်ရှား
                            </button>
                          )}
                        </div>
                      )
                    )}
                  </div>
                  {viewPurchase.attachmentData ? (
                    (viewPurchase.attachmentData || '').startsWith('data:image') ? (
                      <img src={viewPurchase.attachmentData} alt={viewPurchase.attachmentName || 'invoice'} className="max-h-56 rounded border border-slate-200" />
                    ) : (
                      <a href={viewPurchase.attachmentData} download={viewPurchase.attachmentName || 'invoice'} className="inline-flex items-center gap-1.5 text-xs font-bold text-indigo-600 hover:underline">
                        <Download size={13} /> {viewPurchase.attachmentName || 'ဖိုင်ကို ဒေါင်းလုဒ်လုပ်မည်'}
                      </a>
                    )
                  ) : (
                    <p className="text-xs font-semibold text-slate-400">ဘောင်ချာ attach လုပ်ထားခြင်း မရှိပါ။</p>
                  )}
                  <input ref={viewAttachmentInputRef} type="file" accept="image/*,.pdf" className="hidden" onChange={(e) => handleViewAttachmentFile(e.target.files?.[0] || null)} />
                </div>
              )}
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

      {productHistoryProduct && (
        <div className="fixed inset-0 z-[110] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm" onClick={closeProductHistory}>
          <div className="flex max-h-[88vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-5 py-4">
              <div><p className="text-[10px] font-black uppercase tracking-widest text-teal-600">Stock History</p><h3 className="text-lg font-black text-slate-800">{productHistoryProduct.name}</h3><p className="text-xs text-slate-500">{productHistoryProduct.productCode} · လက်ရှိ Stock: {Number(productHistoryProduct.stockQty ?? productHistoryProduct.currentStock ?? 0).toLocaleString()}</p></div>
              <button type="button" onClick={closeProductHistory} className="rounded-lg p-2 text-slate-400 hover:bg-white hover:text-slate-700"><X size={18} /></button>
            </div>
            {productHistoryLoading ? <div className="flex items-center justify-center gap-2 p-16 text-sm font-bold text-slate-500"><Loader2 size={18} className="animate-spin" /> History ဖတ်နေသည်...</div> : productHistory ? (
              <div className="flex-1 space-y-4 overflow-auto p-5">
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                  {[['Opening', productHistory.openingBalance], ['Total In', `+${productHistory.totalIn}`], ['Total Out', `-${productHistory.totalOut}`], ['Closing', productHistory.closingBalance]].map(([label, value]) => <div key={String(label)} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2"><p className="text-[10px] font-bold uppercase text-slate-400">{label}</p><p className="text-lg font-black text-slate-700">{Number(value).toLocaleString()}</p></div>)}
                </div>
                <div className="overflow-x-auto rounded-xl border border-emerald-200">
                  <div className="border-b border-emerald-100 bg-emerald-50 px-4 py-3"><p className="text-sm font-black text-emerald-800">ဝယ်ယူထားသော မှတ်တမ်း</p><p className="text-[11px] font-semibold text-emerald-700">ဘယ် Supplier ဆီက ဘယ်နှစ်ခု ဝယ်ထားသည်ကို ကြည့်ရန်</p></div>
                  <table className="w-full min-w-[620px] text-left text-xs"><thead className="bg-white text-[10px] font-bold uppercase text-slate-500"><tr><th className="px-3 py-2">ဝယ်ရက်</th><th className="px-3 py-2">Supplier</th><th className="px-3 py-2">Voucher</th><th className="px-3 py-2 text-right">ဝယ်ယူအရေအတွက်</th></tr></thead><tbody className="divide-y divide-slate-100">{productHistory.movements.filter((movement) => String(movement.type || '').toUpperCase().includes('PURCHASE') && Number(movement.quantityIn || 0) > 0).map((movement, index) => <tr key={`purchase-${movement.id || index}`}><td className="px-3 py-2 text-slate-600">{movement.date ? new Date(movement.date).toLocaleDateString() : '-'}</td><td className="px-3 py-2 font-semibold text-slate-700">{movement.partyName || '-'}</td><td className="px-3 py-2 font-mono text-slate-500">{movement.referenceNumber || '-'}</td><td className="px-3 py-2 text-right font-black text-emerald-700">+{Number(movement.quantityIn || 0).toLocaleString()}</td></tr>)}</tbody></table>{productHistory.movements.filter((movement) => String(movement.type || '').toUpperCase().includes('PURCHASE') && Number(movement.quantityIn || 0) > 0).length === 0 && <p className="p-6 text-center text-sm text-slate-400">ဝယ်ယူမှုမှတ်တမ်း မရှိသေးပါ။</p>}
                </div>
                <div className="overflow-x-auto rounded-xl border border-slate-200"><table className="w-full min-w-[650px] text-left text-xs"><thead className="bg-slate-50 text-[10px] font-bold uppercase text-slate-500"><tr><th className="px-3 py-2">Date</th><th className="px-3 py-2">Type</th><th className="px-3 py-2">Reference</th><th className="px-3 py-2 text-right">In</th><th className="px-3 py-2 text-right">Out</th><th className="px-3 py-2 text-right">Balance</th></tr></thead><tbody className="divide-y divide-slate-100">{productHistory.movements.filter((movement) => String(movement.type || '').toUpperCase() !== 'SALE').map((movement, index) => <tr key={movement.id || index}><td className="px-3 py-2 text-slate-600">{movement.date ? new Date(movement.date).toLocaleString() : '-'}</td><td className="px-3 py-2 font-semibold text-slate-600">{movement.type}</td><td className="px-3 py-2 text-slate-500">{movement.referenceNumber || movement.partyName || '-'}</td><td className="px-3 py-2 text-right font-bold text-emerald-700">+{Number(movement.quantityIn || 0).toLocaleString()}</td><td className="px-3 py-2 text-right font-bold text-rose-700">-{Number(movement.quantityOut || 0).toLocaleString()}</td><td className="px-3 py-2 text-right font-bold text-slate-700">{Number(movement.balance || 0).toLocaleString()}</td></tr>)}</tbody></table>{productHistory.movements.filter((movement) => String(movement.type || '').toUpperCase() !== 'SALE').length === 0 && <p className="p-8 text-center text-sm text-slate-400">Purchase stock movement မရှိသေးပါ။</p>}</div>
              </div>
            ) : null}
          </div>
        </div>
      )}
      {isBarcodeOpen && (
        <BarcodeScannerCamera
          onDetected={handleBarcodeDetected}
          onClose={() => setIsBarcodeOpen(false)}
        />
      )}

      {showReorderModal && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm" onMouseDown={() => setShowReorderModal(false)}>
          <div className="flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl" onMouseDown={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-amber-500">Reorder Suggestions</p>
                <h3 className="text-base font-black text-slate-800">ပြန်မှာယူသင့်သော ပစ္စည်းများ</h3>
              </div>
              <button type="button" onClick={() => setShowReorderModal(false)} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={18} /></button>
            </div>
            {reorderLoading ? (
              <div className="flex flex-1 items-center justify-center gap-2 p-16 text-sm font-bold text-slate-500"><Loader2 size={18} className="animate-spin" /> တွက်ချက်နေသည်...</div>
            ) : reorderSuggestions.length === 0 ? (
              <div className="flex-1 p-16 text-center text-sm font-semibold text-slate-400">ပြန်မှာယူရန် ပစ္စည်း မရှိပါ။ 🎉</div>
            ) : (
              <>
                <div className="px-4 pt-3">
                  <div className="relative">
                    <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      type="search"
                      autoFocus
                      value={reorderSearch}
                      onChange={(e) => setReorderSearch(e.target.value)}
                      placeholder="ပစ္စည်းအမည်၊ Code၊ Serial အမျိုးအစားဖြင့် ရှာမည်..."
                      className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-10 text-sm text-slate-700 outline-none transition focus:border-indigo-400 focus:bg-white focus:ring-2 focus:ring-indigo-100"
                    />
                    {reorderSearch && <button type="button" onClick={() => setReorderSearch('')} aria-label="Clear reorder search" className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700"><X size={14} /></button>}
                  </div>
                  <p className="mt-1.5 text-[11px] font-semibold text-slate-400">{filteredReorderSuggestions.length} / {reorderSuggestions.length} မျိုး တွေ့ရှိသည်</p>
                </div>
                <div className="flex items-center justify-between px-4 pt-2">
                  <label className="inline-flex cursor-pointer items-center gap-2 text-xs font-bold text-slate-600">
                    <input
                      type="checkbox"
                      checked={filteredReorderSuggestions.length > 0 && filteredReorderSuggestions.filter((r) => r.suggestedQty > 0).every((r) => selectedReorder.has(r.productId))}
                      onChange={toggleAllReorderItems}
                      className="h-4 w-4 accent-indigo-600"
                    />
                    အားလုံး ရွေးမည်
                  </label>
                  <span className="text-[11px] font-semibold text-slate-400">{selectedReorder.size} ခု ရွေးထားသည်</span>
                </div>
                <div className="flex-1 overflow-auto p-4">
                  {filteredReorderSuggestions.length === 0 ? (
                    <div className="flex min-h-52 flex-col items-center justify-center text-center text-slate-400"><Search size={28} className="mb-2" /><p className="text-sm font-bold">ကိုက်ညီသော ပစ္စည်း မတွေ့ပါ</p><button type="button" onClick={() => setReorderSearch('')} className="mt-2 text-xs font-bold text-indigo-600 hover:text-indigo-800">ရှာဖွေမှု ရှင်းမည်</button></div>
                  ) : (
                  <table className="w-full min-w-[620px] text-left text-xs">
                    <thead className="bg-slate-50 text-[10px] font-black uppercase tracking-wide text-slate-400">
                      <tr><th className="px-3 py-3 w-10"></th><th className="px-4 py-3">ပစ္စည်း</th><th className="px-4 py-3 text-right">လက်ရှိ Stock</th><th className="px-4 py-3 text-right">Reorder Level</th><th className="px-4 py-3 text-right">အကြံပြုအရေအတွက်</th><th className="px-4 py-3 text-right">နောက်ဆုံးဝယ်ဈေး</th></tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {filteredReorderSuggestions.map((row) => (
                        <tr key={row.productId} className={`cursor-pointer transition-colors ${selectedReorder.has(row.productId) ? 'bg-indigo-50/60' : 'hover:bg-amber-50/40'}`} onClick={() => toggleReorderItem(row.productId)}>
                          <td className="px-3 py-3" onClick={(e) => e.stopPropagation()}>
                            <input
                              type="checkbox"
                              checked={selectedReorder.has(row.productId)}
                              onChange={() => toggleReorderItem(row.productId)}
                              className="h-4 w-4 accent-indigo-600"
                              aria-label={`Select ${row.productName}`}
                            />
                          </td>
                          <td className="px-4 py-3"><p className={`font-black ${selectedReorder.has(row.productId) ? 'text-indigo-800' : 'text-slate-800'}`}>{row.productName}</p><p className="mt-0.5 text-[10px] font-bold text-slate-400">{row.productCode}</p></td>
                          <td className="px-4 py-3 text-right font-black text-rose-600">{Number(row.stockQty).toLocaleString()}</td>
                          <td className="px-4 py-3 text-right font-semibold text-slate-600">{Number(row.reorderLevel).toLocaleString()}</td>
                          <td className="px-4 py-3 text-right">
                            <span className={`font-black ${selectedReorder.has(row.productId) ? 'text-indigo-700' : 'text-amber-700'}`}>+{Number(row.suggestedQty).toLocaleString()}</span>
                          </td>
                          <td className="px-4 py-3 text-right font-bold text-slate-700">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(row.lastCost || 0)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  )}
                </div>
                <div className="border-t border-slate-100 p-4">
                  <button
                    onClick={importReorderIntoForm}
                    disabled={selectedReorder.size === 0}
                    className="w-full rounded-xl bg-indigo-600 px-4 py-3 text-sm font-bold text-white hover:bg-indigo-700 disabled:bg-slate-300 disabled:cursor-not-allowed"
                  >
                    ရွေးထားသော {selectedReorder.size} မျိုးကို ဝယ်ယူမှုဖောင်ထဲ ထည့်မည်
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseManagement;
