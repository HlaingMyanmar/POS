import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Ban,
  Camera,
  CheckCircle2,
  CreditCard,
  Eye,
  Loader2,
  Printer,
  Plus,
  RefreshCw,
  Save,
  Search,
  ShieldAlert,
  Trash2,
  User,
  X
} from 'lucide-react';
import { saleApiService } from '../services/saleapiservice';
import { saleReturnApiService } from '../services/salereturnapiservice';
import { customerService } from '../services/customerapiservice';
import { staffService } from '../services/staffapiservice';
import { productService } from '../services/productapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { creditTermService } from '../services/credittermapiservice';
import { customerPaymentService } from '../services/customerpaymentapiservice';
import { InvoicePrintPreview } from '../print/components/InvoicePrintPreview';
import BarcodeScannerCamera from '../components/BarcodeScannerCamera';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import { useWebsocket } from '../hooks/useWebsocket';
import { useDataEvents } from '../hooks/useDataEvents';
import { getFromSession } from '../utils/storageHelper';
import {
  AppRoute,
  CustomerCreditTermDTO,
  CustomerDTO,
  CustomerPaymentDTO,
  PaymentMethodDTO,
  PaymentTransactionDTO,
  ProductDTO,
  ProductSerialDTO,
  SaleDTO,
  SaleDetailDTO,
  SaleReturnDTO,
  SerialStatus,
  StaffDTO
} from '../types';

type SaleState = 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE';
type ListFilter = 'ALL' | 'DUE' | SaleState;
type DateShortcut = 'TODAY' | 'WEEK' | 'MONTH' | 'ALL' | 'CUSTOM';
type DetailForm = SaleDetailDTO;
type NewCustomerForm = { name: string; phone: string; address: string };
const emptyDetail = (): DetailForm => ({
  productId: 0,
  qty: 1,
  unitPrice: 0,
  subtotal: 0,
  discountAmount: 0,
  foc: false,
  serialNumbers: []
});

const normalizeSerial = (v: string) => v.trim().toUpperCase();
const fitSerialCount = (serials: string[] | undefined, qty: number) => {
  const safeQty = Math.max(0, Number(qty) || 0);
  const next = [...(serials || [])];
  if (next.length > safeQty) return next.slice(0, safeQty);
  if (next.length < safeQty) return [...next, ...Array(safeQty - next.length).fill('')];
  return next;
};
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
const dateInput = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
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
const fmtDate = (v?: string) => {
  if (!v) return '-';
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString('en-GB', { year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

const formatProductWarranty = (product?: ProductDTO, detail?: Partial<SaleDetailDTO>) => {
  const warrantyTerms = String((detail as any)?.warrantyTerms || product?.warrantyTerms || '').trim();
  const warrantyMonths = Number(detail?.warrantyMonths ?? product?.warrantyMonths ?? 0) || 0;

  if (warrantyTerms) return warrantyTerms;
  if (warrantyMonths > 0) return `${warrantyMonths} month${warrantyMonths > 1 ? 's' : ''} warranty`;
  return 'No warranty';
};

const getSaleState = (row: SaleDTO): SaleState => {
  const due = Number(row.dueAmount) || 0;
  const paid = Number(row.paidAmount) || 0;
  if (due <= 0) return 'PAID';
  if (row.dueDate) {
    const dd = new Date(row.dueDate);
    const t = new Date();
    dd.setHours(0, 0, 0, 0);
    t.setHours(0, 0, 0, 0);
    if (!Number.isNaN(dd.getTime()) && dd < t) return 'OVERDUE';
  }
  if (paid > 0) return 'PARTIAL';
  return 'PENDING';
};

const canCurrentUserBackdateSale = () => {
  const raw = getFromSession('sspd_user');
  if (!raw) return false;
  try {
    const user = JSON.parse(raw);
    const roles: string[] = user.roles || [];
    const permissions: string[] = user.permissions || [];
    return roles.includes('ADMINISTRATOR') ||
      roles.includes('ROLE_ADMINISTRATOR') ||
      permissions.includes('CAN_ACCESS_SALE_BACKDATE');
  } catch {
    return false;
  }
};

const badgeByState: Record<SaleState, string> = {
  PENDING: 'bg-sky-100 text-sky-700 border border-sky-200',
  PARTIAL: 'bg-amber-100 text-amber-700 border border-amber-200',
  PAID: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
  OVERDUE: 'bg-rose-100 text-rose-700 border border-rose-200'
};
const saleStateLabel: Record<SaleState, string> = {
  PENDING: 'မပေးရသေး',
  PARTIAL: 'တချို့ပေးပြီး',
  PAID: 'ပေးပြီး',
  OVERDUE: 'ရက်ကျော်'
};

const SaleManagement: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [showCreateSaleForm, setShowCreateSaleForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [paySaving, setPaySaving] = useState(false);

  const [rows, setRows] = useState<SaleDTO[]>([]);
  const [customers, setCustomers] = useState<CustomerDTO[]>([]);
  const [terms, setTerms] = useState<CustomerCreditTermDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [serials, setSerials] = useState<ProductSerialDTO[]>([]);

  const [salePage, setSalePage] = useState(0);
  const [salePageSize, setSalePageSize] = useState(20);
  const [saleTotalElements, setSaleTotalElements] = useState(0);
  const [saleTotalPages, setSaleTotalPages] = useState(0);

  const [search, setSearch] = useState('');
  const [voucherLookup, setVoucherLookup] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [filter, setFilter] = useState<ListFilter>('ALL');
  const todayStr = dateInput(new Date());
  const defaultDateRange = getThisMonthRange();
  const [dateFrom, setDateFrom] = useState(defaultDateRange.from);
  const [dateTo, setDateTo] = useState(defaultDateRange.to);
  const [dateShortcut, setDateShortcut] = useState<DateShortcut>('MONTH');
  const [staffFilter, setStaffFilter] = useState(0);

  const [customerId, setCustomerId] = useState(0);
  const [customerSearch, setCustomerSearch] = useState('');
  const [customerOpen, setCustomerOpen] = useState(false);
  const [showAddCustomer, setShowAddCustomer] = useState(false);
  const [creatingCustomer, setCreatingCustomer] = useState(false);
  const [newCustomer, setNewCustomer] = useState<NewCustomerForm>({ name: '', phone: '', address: '' });
  const [staffId, setStaffId] = useState(0);
  const [saleDate, setSaleDate] = useState(todayStr);
  const [discountInput, setDiscountInput] = useState('');
  const [paidInput, setPaidInput] = useState('');
  const [salePayments, setSalePayments] = useState<PaymentTransactionDTO[]>([]);
  const [dueDate, setDueDate] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState(0);
  const [remark, setRemark] = useState('');
  const [details, setDetails] = useState<DetailForm[]>([emptyDetail()]);
  const [productSearches, setProductSearches] = useState<string[]>(['']);
  const [barcodeInput, setBarcodeInput] = useState('');
  const [cameraOpen, setCameraOpen] = useState(false);
  const [payModalOpen, setPayModalOpen] = useState(false);
  const canBackdateSale = useMemo(() => canCurrentUserBackdateSale(), []);
  const detailsRef = useRef<DetailForm[]>([emptyDetail()]);
  const productSearchesRef = useRef<string[]>(['']);


  const [viewSale, setViewSale] = useState<SaleDTO | null>(null);
  const [viewPayments, setViewPayments] = useState<CustomerPaymentDTO[]>([]);
  const [viewReturns, setViewReturns] = useState<SaleReturnDTO[]>([]);
  const [viewReturnsLoading, setViewReturnsLoading] = useState(false);
  const [payForm, setPayForm] = useState({ amount: '', paymentMethodId: 0, transactionNo: '', note: '', payments: [] as PaymentTransactionDTO[] });
  const [printPreviewSaleId, setPrintPreviewSaleId] = useState<number | null>(null);

  const loadSales = useCallback(async (page: number, size: number, search: string, dateFrom: string, dateTo: string) => {
    setLoading(true);
    try {
      const effectiveDateFrom = search ? '' : dateFrom;
      const effectiveDateTo   = search ? '' : dateTo;
      const result = await saleApiService.getAllPaged(page, size, search, effectiveDateFrom, effectiveDateTo);
      setRows(result.content);
      setSaleTotalElements(result.totalElements);
      setSaleTotalPages(result.totalPages);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load sales', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMaster = useCallback(async () => {
    try {
      const [customerRes, termRes, staffRes, productRes, methodRes, serialRes] = await Promise.all([
        customerService.getAll(),
        creditTermService.getAll(),
        staffService.getAll(),
        productService.getAll(),
        paymentMethodService.getAllActive(),
        productSerialService.getAll()
      ]);
      setCustomers(customerRes || []);
      setTerms(termRes || []);
      setStaffs(staffRes || []);
      setProducts(productRes || []);
      setMethods(methodRes || []);
      setSerials(serialRes || []);
      if (methodRes.length > 0) {
        setPaymentMethodId((prev) => prev || methodRes[0].id);
      }
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load master data', 'error');
    }
  }, []);

  useEffect(() => {
    loadMaster();
  }, [loadMaster]);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setSalePage(0);
      setDebouncedSearch(search.trim());
    }, 400);
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current); };
  }, [search]);

  useEffect(() => {
    setSalePage(0);
  }, [dateFrom, dateTo]);

  useEffect(() => {
    loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo);
  }, [loadSales, salePage, salePageSize, debouncedSearch, dateFrom, dateTo]);

  useEffect(() => {
    detailsRef.current = details;
    productSearchesRef.current = productSearches;
  }, [details, productSearches]);

  const barcodeScanRef = useRef<(code: string) => void>(() => {});
  useWebsocket('/topic/barcode-scan', useCallback((code: string) => {
    barcodeScanRef.current(code.trim());
  }, []));
  useDataEvents(['Sale'], () => loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo));

  const termMap = useMemo(() => new Map(terms.map((t) => [t.customerId, t])), [terms]);
  const selectedCustomer = useMemo(() => customers.find((c) => c.id === customerId) || null, [customers, customerId]);
  const selectedTerm = useMemo(() => (selectedCustomer ? termMap.get(selectedCustomer.id) : undefined), [selectedCustomer, termMap]);

  const availableSerials = useMemo(() => serials.filter((s) => String(s.status).toLowerCase() === SerialStatus.AVAILABLE.toLowerCase()), [serials]);
  const isSerialProduct = useCallback((productId: number) => {
    const product = products.find((p) => p.id === productId);
    return product ? product.hasSerial !== false : true;
  }, [products]);

  const getQtyOnlyStock = useCallback((productId: number) => {
    const product = products.find((p) => p.id === productId);
    return Number(product?.stockQty ?? product?.currentStock ?? 0);
  }, [products]);

  const getSerialPool = useCallback((productId: number) => (
    Array.from(new Set(
      availableSerials
        .filter((s) => s.productId === productId)
        .map((s) => normalizeSerial(s.serialNumber))
        .filter(Boolean)
    ))
  ), [availableSerials]);

  const discount = useMemo(() => {
    if (!discountInput.trim()) return 0;
    const n = Number(discountInput);
    return Number.isNaN(n) ? 0 : n;
  }, [discountInput]);
  const paid = useMemo(() => {
    if (!paidInput.trim()) return 0;
    const n = Number(paidInput);
    return Number.isNaN(n) ? 0 : n;
  }, [paidInput]);
  const normalizedSalePayments = useMemo(() => normalizePayments(salePayments), [salePayments]);
  const splitPaid = useMemo(() => paymentTotal(salePayments), [salePayments]);
  const effectivePaid = normalizedSalePayments.length > 0 ? splitPaid : paid;
  const totalAmount = useMemo(() => details.reduce((sum, d) => sum + (d.subtotal || 0), 0), [details]);
  const netAmount = useMemo(() => Math.max(0, totalAmount - discount), [totalAmount, discount]);
  const dueAmount = useMemo(() => Math.max(0, netAmount - effectivePaid), [netAmount, effectivePaid]);

  const outstandingByCustomer = useMemo(() => {
    const m = new Map<number, number>();
    rows.forEach((r) => {
      const due = Number(r.dueAmount) || 0;
      if (due > 0) m.set(r.customerId, (m.get(r.customerId) || 0) + due);
    });
    return m;
  }, [rows]);

  const currentOutstanding = selectedCustomer ? (outstandingByCustomer.get(selectedCustomer.id) || 0) : 0;
  const projectedOutstanding = currentOutstanding + dueAmount;
  const creditLimit = Number(selectedTerm?.creditLimit) || 0;
  const creditAllowed = Boolean(selectedTerm?.creditAllowed);
  const limitExceeded = dueAmount > 0 && creditLimit > 0 && projectedOutstanding > creditLimit;
  const limitNear = dueAmount > 0 && creditLimit > 0 && !limitExceeded && projectedOutstanding >= (creditLimit * 0.8);


  const customerOptions = useMemo(() => {
    const needle = customerSearch.trim().toLowerCase();
    if (!needle) return customers.slice(0, 30);
    return customers.filter((c) => [c.name, c.phone, c.address].filter(Boolean).join(' ').toLowerCase().includes(needle)).slice(0, 30);
  }, [customers, customerSearch]);

  const onCustomerSearch = (value: string) => {
    setCustomerSearch(value);
    setCustomerOpen(true);
    const needle = value.trim().toLowerCase();
    if (!needle) {
      setCustomerId(0);
      return;
    }
    const exact = customers.find((c) => {
      const label = [c.name, c.phone].filter(Boolean).join(' ').toLowerCase();
      return label === needle || (c.name || '').toLowerCase() === needle || (c.phone || '').toLowerCase() === needle;
    });
    setCustomerId(exact?.id || 0);
  };

  const onCustomerSelect = (c: CustomerDTO) => {
    setCustomerId(c.id);
    setCustomerSearch(`${c.name} (${c.phone || '-'})`);
    setCustomerOpen(false);
  };

  const resetNewCustomerForm = () => {
    setNewCustomer({ name: '', phone: '', address: '' });
  };

  const createCustomerFromSale = async () => {
    const name = newCustomer.name.trim();
    const phone = newCustomer.phone.trim();
    const address = newCustomer.address.trim();
    if (!name) {
      Swal.fire('Validation', 'Customer name is required.', 'warning');
      return;
    }

    setCreatingCustomer(true);
    try {
      const created = await customerService.create({ name, phone, address });
      if (created?.id) {
        setCustomers((prev) => {
          const exists = prev.some((c) => c.id === created.id);
          return exists ? prev : [created, ...prev];
        });
        onCustomerSelect(created);
      } else {
        await loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo);
        const fallback = customers.find((c) => c.phone === phone && c.name === name);
        if (fallback) onCustomerSelect(fallback);
      }

      setShowAddCustomer(false);
      resetNewCustomerForm();
      Swal.fire({ icon: 'success', title: 'Customer created', toast: true, position: 'top-end', showConfirmButton: false, timer: 1200 });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to create customer', 'error');
    } finally {
      setCreatingCustomer(false);
    }
  };

  const changeDetail = (index: number, patch: Partial<DetailForm>) => {
    setDetails((prev) => prev.map((row, i) => {
      if (i !== index) return row;
      const next = { ...row, ...patch };
      if (patch.qty !== undefined) {
        const qty = Math.max(0, Number(patch.qty) || 0);
        next.serialNumbers = isSerialProduct(next.productId) ? fitSerialCount(next.serialNumbers, qty) : [];
      }
      const gross = (next.qty || 0) * (next.unitPrice || 0);
      const lineDiscount = Number((next as any).discountAmount || 0);
      next.subtotal = Math.max(0, Boolean((next as any).foc) ? 0 : gross - lineDiscount);
      return next;
    }));
  };

  const onProductSearch = (rowIndex: number, value: string) => {
    setProductSearches((prev) => {
      const next = [...prev];
      next[rowIndex] = value;
      return next;
    });

    const needle = value.trim().toLowerCase();
    if (!needle) {
      changeDetail(rowIndex, { productId: 0, unitPrice: 0 });
      return;
    }

    const exact = products.find((p) => {
      const label = [p.name, p.productCode].filter(Boolean).join(' ').toLowerCase();
      return label === needle || (p.name || '').toLowerCase() === needle || (p.productCode || '').toLowerCase() === needle;
    });

    if (exact) {
      const qty = Math.max(0, Number(details[rowIndex]?.qty) || 0);
      const serialRequired = exact.hasSerial !== false;
      changeDetail(rowIndex, {
        productId: exact.id,
        unitPrice: Number(exact.sellingPrice) || 0,
        serialNumbers: serialRequired ? fitSerialCount(details[rowIndex]?.serialNumbers, qty) : []
      });
      setProductSearches((prev) => {
        const next = [...prev];
        next[rowIndex] = `${exact.name}${exact.productCode ? ` (${exact.productCode})` : ''}`;
        return next;
      });
    } else {
      changeDetail(rowIndex, { productId: 0, unitPrice: 0 });
    }
  };

  const addScannedRow = useCallback((row: DetailForm, productLabel: string) => {
    const currentDetails = detailsRef.current;
    const currentSearches = productSearchesRef.current;
    const emptyIndex = currentDetails.findIndex((d) => d.productId <= 0);

    if (emptyIndex >= 0) {
      const nextDetails = currentDetails.map((d, i) => (i === emptyIndex ? row : d));
      const nextSearches = currentSearches.map((s, i) => (i === emptyIndex ? productLabel : s));
      setDetails(nextDetails);
      setProductSearches(nextSearches);
      return;
    }

    setDetails([...currentDetails, row]);
    setProductSearches([...currentSearches, productLabel]);
  }, []);

  const handleBarcodeScan = (raw: string) => {
    const code = raw.trim().toUpperCase();
    if (!code) return;
    const currentDetails = detailsRef.current;

    // 1. Try match by serial number (serial products)
    const matchedSerial = serials.find(s => normalizeSerial(s.serialNumber) === code);
    if (matchedSerial) {
      const product = products.find(p => p.id === matchedSerial.productId);
      if (!product) { Swal.fire('Not Found', `Serial "${code}" exists but product not loaded.`, 'warning'); setBarcodeInput(''); return; }
      if (String(matchedSerial.status).toLowerCase() !== 'available') {
        Swal.fire('Not Available', `Serial "${code}" is already ${matchedSerial.status}.`, 'warning');
        setBarcodeInput(''); return;
      }
      // Check if this serial is already in details
      const alreadyInRow = currentDetails.findIndex(d => (d.serialNumbers || []).map(s => s.toUpperCase()).includes(code));
      if (alreadyInRow >= 0) {
        Swal.fire('Duplicate', `Serial "${code}" is already added in row ${alreadyInRow + 1}.`, 'info');
        setBarcodeInput(''); return;
      }
      const newRow: DetailForm = {
        ...emptyDetail(),
        productId: product.id,
        unitPrice: Number(product.sellingPrice) || 0,
        qty: 1,
        serialNumbers: [code],
        subtotal: Number(product.sellingPrice) || 0
      };
      addScannedRow(newRow, `${product.name}${product.productCode ? ` (${product.productCode})` : ''}`);
      setBarcodeInput('');
      return;
    }

    // 2. Try match by product code (non-serial or serial products)
    const productByCode = products.find(p => (p.productCode || '').toUpperCase() === code);
    if (productByCode) {
      const newRow: DetailForm = {
        ...emptyDetail(),
        productId: productByCode.id,
        unitPrice: Number(productByCode.sellingPrice) || 0,
        qty: 1,
        serialNumbers: isSerialProduct(productByCode.id) ? [''] : [],
        subtotal: Number(productByCode.sellingPrice) || 0
      };
      addScannedRow(newRow, `${productByCode.name}${productByCode.productCode ? ` (${productByCode.productCode})` : ''}`);
      setBarcodeInput('');
      return;
    }

    Swal.fire('Not Found', `Barcode "${code}" မတွေ့ပါ။ Serial Number (သို့) Product Code နဲ့ မကိုက်ညီပါ။`, 'warning');
    setBarcodeInput('');
  };

  // Keep ref in sync so WebSocket callback always calls latest version
  useEffect(() => { barcodeScanRef.current = handleBarcodeScan; });

  const onSerialChange = (rowIndex: number, serialIndex: number, value: string) => {
    setDetails((prev) => prev.map((row, i) => {
      if (i !== rowIndex) return row;
      const serialNumbers = [...row.serialNumbers];
      serialNumbers[serialIndex] = normalizeSerial(value);
      return { ...row, serialNumbers };
    }));
  };

  const autofillSerials = (rowIndex: number) => {
    setDetails((prev) => {
      const used = new Set<string>();
      prev.forEach((row, i) => {
        if (i === rowIndex) return;
        row.serialNumbers.forEach((sn) => {
          const normalized = normalizeSerial(sn);
          if (normalized) used.add(normalized);
        });
      });

      return prev.map((row, i) => {
        if (i !== rowIndex) return row;
        if (row.productId <= 0 || row.qty <= 0) return row;
        if (!isSerialProduct(row.productId)) return { ...row, serialNumbers: [] };
        const pool = getSerialPool(row.productId).filter((sn) => !used.has(sn));
        return { ...row, serialNumbers: Array.from({ length: row.qty }, (_, sidx) => pool[sidx] || '') };
      });
    });
  };

  const addRow = () => {
    setDetails((prev) => [...prev, emptyDetail()]);
    setProductSearches((prev) => [...prev, '']);
  };

  const removeRow = (index: number) => {
    setDetails((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== index)));
    setProductSearches((prev) => (prev.length <= 1 ? prev : prev.filter((_, i) => i !== index)));
  };

  const resetCreateForm = () => {
    setCustomerId(0);
    setCustomerSearch('');
    setCustomerOpen(false);
    setStaffId(0);
    setSaleDate(todayStr);
    setDiscountInput('');
    setPaidInput('');
    setSalePayments([]);
    setDueDate('');
    setRemark('');
    setDetails([emptyDetail()]);
    setProductSearches(['']);
    setPaymentMethodId(methods[0]?.id || 0);
  };

  const productMap = useMemo(() => new Map(products.map((product) => [product.id, product])), [products]);
  const paymentMethodMap = useMemo(() => new Map(methods.map((method) => [method.id, method])), [methods]);

  const validateSale = () => {
    if (customerId <= 0) return 'Customer is required.';
    if (!saleDate) return 'Sale date is required.';
    if (saleDate < todayStr && !canBackdateSale) return 'Back date sale requires permission.';
    if (details.length === 0) return 'Sale detail is required.';

    const usedSerials = new Set<string>();
    for (const row of details) {
      if (row.productId <= 0) return 'Please select product in each row.';
      if (row.qty <= 0) return 'Quantity must be greater than zero.';
      if (row.unitPrice <= 0) return 'Unit price must be greater than zero.';
      const gross = row.qty * row.unitPrice;
      const lineDiscount = Number((row as any).discountAmount || 0);
      if (lineDiscount < 0) return 'Line discount cannot be negative.';
      if (!Boolean((row as any).foc) && lineDiscount > gross) return 'Line discount cannot exceed line amount.';

      if (isSerialProduct(row.productId)) {
        const pool = getSerialPool(row.productId);
        if (pool.length === 0) return 'No available serial numbers for selected serial product.';
        if (row.qty > pool.length) return `Only ${pool.length} serial(s) available for this product, cannot sell ${row.qty}.`;
        if (row.serialNumbers.length !== row.qty) return 'Serial count must match qty.';
        const poolSet = new Set(pool.map((sn) => sn.toLowerCase()));
        for (const raw of row.serialNumbers) {
          const sn = normalizeSerial(raw);
          if (!sn) return 'Serial number is required for serial products.';
          const key = sn.toLowerCase();
          if (!poolSet.has(key)) return `Serial ${sn} is not available.`;
          if (usedSerials.has(key)) return 'Duplicate serial number detected.';
          usedSerials.add(key);
        }
      }
    }

    if (discount < 0) return 'Discount cannot be negative.';
    if (effectivePaid < 0) return 'Paid amount cannot be negative.';
    if (effectivePaid > netAmount) return 'Paid amount cannot exceed net amount.';

    if (effectivePaid > 0 && normalizedSalePayments.length === 0 && paymentMethodId <= 0) return 'Payment method is required when paid amount > 0.';

    if (!selectedCustomer) return 'Customer not found.';
    if (dueAmount > 0) {
      if (selectedCustomer.blacklisted) return 'Customer is blacklisted. Credit sale blocked.';
      if (selectedCustomer.creditHold) return 'Customer is on credit hold. Credit sale blocked.';
      if (!creditAllowed) return 'Credit not allowed for this customer.';
      if (limitExceeded) return 'Credit limit exceeded for this customer.';
    }

    return '';
  };

  const saveSale = async () => {
    if (saving) return;
    const error = validateSale();
    if (error) {
      Swal.fire('Validation', error, 'warning');
      return;
    }

    setSaving(true);
    try {
      const payload: SaleDTO = {
        customerId,
        staffId,
        saleDate: `${saleDate}T00:00:00`,
        totalAmount,
        discountAmount: discount,
        netAmount,
        paidAmount: effectivePaid,
        dueAmount,
        dueDate: dueAmount > 0 ? (dueDate || undefined) : undefined,
        paymentMethodId: effectivePaid > 0 ? (normalizedSalePayments[0]?.paymentMethodId || paymentMethodId) : undefined,
        payments: normalizedSalePayments.length > 0 ? normalizedSalePayments : undefined,
        remark: remark.trim() || undefined,
        details: details.map((d) => ({
          productId: d.productId,
          qty: d.qty,
          unitPrice: d.unitPrice,
          subtotal: Number((Math.max(0, Boolean((d as any).foc) ? 0 : ((d.qty * d.unitPrice) - Number((d as any).discountAmount || 0)))).toFixed(2)),
          discountAmount: (d as any).discountAmount || 0,
          foc: !!(d as any).foc,
          serialNumbers: isSerialProduct(d.productId)
            ? d.serialNumbers.map((sn) => normalizeSerial(sn)).filter(Boolean)
            : []
        }))
      };

      await saleApiService.create(payload);
      await loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo);
      resetCreateForm();
      setShowCreateSaleForm(false);
      Swal.fire({ icon: 'success', title: 'Sale created', toast: true, position: 'top-end', showConfirmButton: false, timer: 1200 });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save sale', 'error');
    } finally {
      setSaving(false);
    }
  };

  const filteredRows = useMemo(() => {
    return rows.filter((row) => {
      const state = getSaleState(row);
      if (filter === 'DUE' && (Number(row.dueAmount) || 0) <= 0) return false;
      if (filter !== 'ALL' && filter !== 'DUE' && state !== filter) return false;
      if (staffFilter && row.staffId !== staffFilter) return false;
      return true;
    });
  }, [rows, filter, staffFilter]);

  const stats = useMemo(() => ({
    count: saleTotalElements,
    net: rows.reduce((sum, r) => sum + (Number(r.netAmount) || 0), 0),
    due: rows.reduce((sum, r) => sum + (Number(r.dueAmount) || 0), 0),
    overdue: rows.filter((r) => getSaleState(r) === 'OVERDUE').length
  }), [rows, saleTotalElements]);

  const openCreateSale = useCallback(() => {
    setShowCreateSaleForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  const closeCreateSale = useCallback(() => {
    setShowCreateSaleForm(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }, []);

  const openDetail = async (saleId: number) => {
    setViewReturnsLoading(true);
    setViewReturns([]);
    try {
      const [sale, payments, returns] = await Promise.all([
        saleApiService.getById(saleId),
        customerPaymentService.getBySale(saleId),
        saleReturnApiService.getBySaleId(saleId)
      ]);
      setViewSale(sale);
      setViewPayments(payments || []);
      setViewReturns(returns || []);
      setPayForm({
        amount: String(Number(sale.dueAmount) || ''),
        paymentMethodId: methods[0]?.id || 0,
        transactionNo: '',
        note: '',
        payments: []
      });
    } catch (e: any) {
      setViewReturns([]);
      Swal.fire('Error', e?.message || 'Failed to load sale detail', 'error');
    } finally {
      setViewReturnsLoading(false);
    }
  };

  const handleVoucherLookup = async () => {
    const keyword = voucherLookup.trim();
    if (!keyword) return;
    try {
      const result = await saleApiService.getAllPaged(0, 10, keyword, '', '');
      const normalized = keyword.replace(/^#/, '').trim().toLowerCase();
      const exact = result.content.find((sale) =>
        String(sale.saleCode || '').toLowerCase() === normalized ||
        String(sale.saleCode || '').toLowerCase() === keyword.toLowerCase() ||
        String(sale.id || '') === normalized
      );
      if (exact?.id) {
        await openDetail(exact.id);
        return;
      }
      if (result.content.length === 1 && result.content[0].id) {
        await openDetail(result.content[0].id);
        return;
      }
      setSearch(keyword);
      setDebouncedSearch(keyword);
      setSalePage(0);
      Swal.fire({ icon: 'info', title: 'ဘောင်ချာကို တိတိကျကျ မတွေ့ပါ', text: 'ကိုက်ညီနိုင်သော sale များကို စာရင်းထဲတွင် ပြထားပါသည်။', timer: 1800, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Voucher ရှာမရပါ', 'error');
    }
  };

  const applyDateShortcut = (shortcut: Exclude<DateShortcut, 'CUSTOM'>) => {
    setDateShortcut(shortcut);
    if (shortcut === 'ALL') {
      setDateFrom('');
      setDateTo('');
      return;
    }
    const range = shortcut === 'TODAY'
      ? getTodayRange()
      : shortcut === 'WEEK'
        ? getThisWeekRange()
        : getThisMonthRange();
    setDateFrom(range.from);
    setDateTo(range.to);
  };

  useEffect(() => {
    if (loading) return;

    const mode = new URLSearchParams(location.search).get('mode');
    if (mode === 'create') {
      setShowCreateSaleForm(true);
      navigate({ pathname: location.pathname, search: '' }, { replace: true });
      return;
    }

    const raw = new URLSearchParams(location.search).get('saleId');
    const linkedSaleId = Number(raw);
    if (!Number.isInteger(linkedSaleId) || linkedSaleId <= 0) return;

    setShowCreateSaleForm(false);
    openDetail(linkedSaleId).finally(() => {
      navigate({ pathname: location.pathname, search: '' }, { replace: true });
    });
  }, [loading, location.pathname, location.search, navigate]);

  const submitPayDue = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!viewSale?.id) return;
    const normalizedPayDuePayments = normalizePayments(payForm.payments || []);
    const amount = normalizedPayDuePayments.length > 0 ? paymentTotal(payForm.payments || []) : Number(payForm.amount);
    const due = Number(viewSale.dueAmount) || 0;

    if (!amount || amount <= 0) return Swal.fire('Validation', 'Amount must be greater than zero.', 'warning');
    if (amount > due) return Swal.fire('Validation', 'Amount cannot exceed due.', 'warning');
    if (!payForm.paymentMethodId && normalizedPayDuePayments.length === 0) return Swal.fire('Validation', 'Payment method is required.', 'warning');

    setPaySaving(true);
    try {
      await saleApiService.payDue(viewSale.id, {
        paidAmount: amount,
        paymentMethodId: normalizedPayDuePayments[0]?.paymentMethodId || payForm.paymentMethodId,
        transactionNo: payForm.transactionNo.trim() || undefined,
        payments: normalizedPayDuePayments.length > 0 ? normalizedPayDuePayments : undefined,
        staffId: viewSale.staffId,
        note: payForm.note.trim() || undefined
      });

      const [updatedSale, payments] = await Promise.all([
        saleApiService.getById(viewSale.id),
        customerPaymentService.getBySale(viewSale.id)
      ]);
      setViewSale(updatedSale);
      setViewPayments(payments || []);
      setPayForm((prev) => ({ ...prev, amount: String(Number(updatedSale.dueAmount) || ''), transactionNo: '', note: '', payments: [] }));
      await loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo);

      Swal.fire({ icon: 'success', title: 'Payment recorded', toast: true, position: 'top-end', showConfirmButton: false, timer: 1200 });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save payment', 'error');
    } finally {
      setPaySaving(false);
    }
  };

  const printSaleVoucher = useCallback((sale: SaleDTO) => {
    setPrintPreviewSaleId(sale.id);
  }, []);

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center">
        <Loader2 size={28} className="animate-spin text-indigo-600" />
      </div>
    );
  }

  const creditBannerClass = selectedCustomer?.blacklisted
    ? 'bg-slate-900 text-white border-slate-900'
    : selectedCustomer?.creditHold
      ? 'bg-rose-100 text-rose-700 border-rose-200'
      : limitExceeded
        ? 'bg-rose-100 text-rose-700 border-rose-200'
        : limitNear
          ? 'bg-amber-100 text-amber-700 border-amber-200'
          : 'bg-emerald-100 text-emerald-700 border-emerald-200';

  if (showCreateSaleForm) {
    return (
      <>
      <div className="w-full max-w-none space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <button onClick={closeCreateSale} className="inline-flex w-full sm:w-auto justify-center sm:justify-start items-center gap-2 px-3 py-1.5 text-slate-600 hover:bg-slate-100 rounded-lg text-sm font-medium">
            <ArrowLeft size={16} />
            စာရင်းသို့ပြန်မည်
          </button>
          <div className="text-center sm:text-left">
            <h2 className="text-xl font-bold text-slate-800">ရောင်းချမှုဘောင်ချာအသစ်</h2>
            <p className="text-xs text-slate-500 mt-0.5">ဖောက်သည်ရွေး၊ barcode/serial ဖြင့်ပစ္စည်းထည့်၊ cash/credit ကို checkout မှာသတ်မှတ်ပါ။</p>
          </div>
          <div className="hidden sm:block w-24" />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <div className={`rounded-xl border p-3 ${customerId > 0 ? 'bg-emerald-50 border-emerald-200' : 'bg-white border-slate-200'}`}>
            <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">1. Customer</p>
            <p className={`text-sm font-bold mt-1 ${customerId > 0 ? 'text-emerald-700' : 'text-slate-700'}`}>{customerId > 0 ? 'ရွေးပြီး' : 'ရွေးရန်လို'}</p>
          </div>
          <div className={`rounded-xl border p-3 ${details.some(d => d.productId > 0) ? 'bg-emerald-50 border-emerald-200' : 'bg-white border-slate-200'}`}>
            <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">2. Items</p>
            <p className="text-sm font-bold mt-1 text-slate-700">{details.filter(d => d.productId > 0).length} line(s)</p>
          </div>
          <div className="rounded-xl border border-slate-200 bg-white p-3">
            <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">3. Net Amount</p>
            <p className="text-sm font-bold mt-1 text-slate-800">{money(netAmount)}</p>
          </div>
          <div className={`rounded-xl border p-3 ${dueAmount > 0 ? 'bg-amber-50 border-amber-200' : 'bg-emerald-50 border-emerald-200'}`}>
            <p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">4. Payment</p>
            <p className={`text-sm font-bold mt-1 ${dueAmount > 0 ? 'text-amber-700' : 'text-emerald-700'}`}>{dueAmount > 0 ? 'ရရန်ကျန်ရှိ' : 'ငွေရပြီး'}</p>
          </div>
        </div>

        <div>
          <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-5">
          <div className="grid grid-cols-1 xl:grid-cols-4 gap-3">
            <div>
              <div className="mb-1.5 flex items-center justify-between">
                <label className="text-xs font-semibold text-slate-600">ဖောက်သည် <span className="text-rose-500">*</span></label>
                <button
                  type="button"
                  onClick={() => { setShowAddCustomer(true); setCustomerOpen(false); }}
                  className="inline-flex items-center gap-1 px-2 py-1 rounded border border-indigo-200 text-indigo-700 text-[11px] font-semibold hover:bg-indigo-50"
                >
                  <Plus size={11} /> အသစ်
                </button>
              </div>
              <div className="relative">
                <input
                  value={customerSearch}
                  onChange={(e) => onCustomerSearch(e.target.value)}
                  onFocus={() => setCustomerOpen(true)}
                  onBlur={() => setTimeout(() => setCustomerOpen(false), 120)}
                  placeholder="အမည် / ဖုန်းနံပါတ်ဖြင့်ရှာပါ..."
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                />
                {customerOpen && (
                  <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                    {customerOptions.length > 0 ? customerOptions.map((c) => (
                      <button key={c.id} type="button" onMouseDown={() => onCustomerSelect(c)} className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${customerId === c.id ? 'bg-indigo-50' : ''}`}>
                        <p className="text-sm font-semibold text-slate-800">{c.name}</p>
                        <p className="text-xs text-slate-400">{c.phone || '-'} · {c.blacklisted ? 'Blacklisted' : c.creditHold ? 'Credit Hold' : 'Normal'}</p>
                      </button>
                    )) : <p className="px-3 py-3 text-xs text-slate-400 text-center">No customer found.</p>}
                  </div>
                )}
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5">ရောင်းသူ</label>
              <select value={staffId} onChange={(e) => setStaffId(Number(e.target.value) || 0)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400">
                <option value={0}>- ရောင်းသူရွေးပါ -</option>
                {staffs.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.role})</option>)}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5">ရောင်းသည့်ရက်</label>
              <input
                type="date"
                value={saleDate}
                max={canBackdateSale ? undefined : todayStr}
                onChange={(e) => setSaleDate(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
              />
              {!canBackdateSale && <p className="text-[10px] text-slate-400 mt-1">နောက်ရက်စွဲဖြင့်ထည့်ရန် permission လိုအပ်သည်။</p>}
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-600 mb-1.5">မှတ်ချက်</label>
              <input value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="လိုအပ်ပါကမှတ်ချက်ရေးပါ" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
            </div>
          </div>

          <div className={`rounded-lg border px-4 py-2.5 ${creditBannerClass}`}>
            <p className="text-sm font-semibold inline-flex items-center gap-2">
              {!selectedCustomer ? <User size={15} /> : selectedCustomer.blacklisted ? <ShieldAlert size={15} /> : selectedCustomer.creditHold ? <Ban size={15} /> : (limitExceeded || limitNear) ? <AlertTriangle size={15} /> : <CheckCircle2 size={15} />}
              {!selectedCustomer
                ? 'Select a customer to begin'
                : selectedCustomer.blacklisted
                  ? 'Customer is blacklisted - cash sale only'
                  : selectedCustomer.creditHold
                    ? 'Customer is on credit hold - cash sale only'
                    : limitExceeded
                      ? `Credit limit exceeded (${money(projectedOutstanding)} / ${money(creditLimit)})`
                      : limitNear
                        ? `Credit near 80% (${money(projectedOutstanding)} / ${money(creditLimit)})`
                        : dueAmount > 0
                          ? `Credit OK - ${money(projectedOutstanding)} / ${money(creditLimit || projectedOutstanding)}`
                          : 'Cash sale - no due amount'}
            </p>
          </div>

          <div className="flex items-center gap-2 px-3 py-2 bg-violet-50 border border-violet-200 rounded-lg">
            <Search size={15} className="text-violet-400 shrink-0" />
            <input
              type="text"
              value={barcodeInput}
              onChange={e => setBarcodeInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleBarcodeScan(barcodeInput); } }}
              placeholder="Barcode scan သို့မဟုတ် serial / product code ရိုက်ပြီး Enter"
              className="flex-1 bg-transparent outline-none text-sm font-medium text-violet-800 placeholder:text-violet-300 min-w-0"
              autoComplete="off"
            />
            <button
              type="button"
              onClick={() => setCameraOpen(true)}
              title="Scan with phone camera"
              className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 bg-violet-600 hover:bg-violet-700 text-white rounded-lg text-[11px] font-black uppercase tracking-wide transition-all"
            >
              <Camera size={13} /> Camera
            </button>
          </div>

          <div className="border border-slate-200 rounded-lg overflow-auto">
            <table className="w-full min-w-[980px] text-xs">
              <thead className="bg-slate-100 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                <tr>
                  <th className="px-3 py-2.5 text-left">ပစ္စည်း</th>
                  <th className="px-3 py-2.5 text-left w-20">Qty</th>
                  <th className="px-3 py-2.5 text-left w-32">ရောင်းဈေး</th>
                  <th className="px-3 py-2.5 text-left w-28">Line Discount</th>
                  <th className="px-3 py-2.5 text-center w-14">FOC</th>
                  <th className="px-3 py-2.5 text-left w-28">လက်ကျန်</th>
                  <th className="px-3 py-2.5 text-right w-32">စုစုပေါင်း</th>
                  <th className="px-3 py-2.5 w-10"></th>
                </tr>
              </thead>
              <tbody>
                {details.map((row, rowIndex) => {
                  const pool = getSerialPool(row.productId);
                  const serialRequired = isSerialProduct(row.productId);
                  const qtyOnlyStock = getQtyOnlyStock(row.productId);
                  const insufficient = serialRequired && pool.length > 0 && row.qty > pool.length;
                  return (
                    <React.Fragment key={rowIndex}>
                      <tr className="border-b border-slate-100 hover:bg-slate-50/50">
                        <td className="px-3 py-2">
                          <input
                            list={`product-opt-${rowIndex}`}
                            value={productSearches[rowIndex] || ''}
                            onChange={(e) => onProductSearch(rowIndex, e.target.value)}
                            placeholder="ပစ္စည်းရှာပါ..."
                            className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400"
                          />
                          <datalist id={`product-opt-${rowIndex}`}>
                            {products.map((p) => <option key={p.id} value={`${p.name}${p.productCode ? ` ${p.productCode}` : ''}`} />)}
                          </datalist>
                        </td>
                        <td className="px-3 py-2"><input type="number" min="1" max={serialRequired ? pool.length : undefined} value={row.qty || ''} onChange={(e) => changeDetail(rowIndex, { qty: Math.max(0, Number(e.target.value) || 0) })} className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" /></td>
                        <td className="px-3 py-2"><input type="number" min="0" step="0.01" value={row.unitPrice || ''} onChange={(e) => changeDetail(rowIndex, { unitPrice: Math.max(0, Number(e.target.value) || 0) })} className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" /></td>
                        <td className="px-3 py-2">
                          <input type="number" min="0" step="0.01"
                            value={(row as any).discountAmount || ''}
                            onChange={(e) => changeDetail(rowIndex, { discountAmount: Math.max(0, Number(e.target.value) || 0) } as any)}
                            placeholder="0"
                            className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" />
                        </td>
                        <td className="px-3 py-2 text-center">
                          <input type="checkbox"
                            checked={!!(row as any).foc}
                            onChange={(e) => changeDetail(rowIndex, { foc: e.target.checked } as any)}
                            className="w-4 h-4 accent-emerald-600 cursor-pointer" />
                        </td>
                        <td className="px-3 py-2">
                          {serialRequired ? (
                            <span className={`inline-flex px-2 py-1 rounded-md text-[11px] font-bold ${insufficient ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>{pool.length} avail.</span>
                          ) : (
                            <span className="inline-flex px-2 py-1 rounded-md text-[11px] font-bold bg-slate-100 text-slate-600">{qtyOnlyStock} in stock</span>
                          )}
                        </td>
                        <td className="px-3 py-2 text-right font-bold text-slate-700">{money(row.subtotal || 0)}</td>
                        <td className="px-3 py-2 text-center">
                          <button type="button" onClick={() => removeRow(rowIndex)} disabled={details.length <= 1} className="p-1.5 rounded text-slate-400 hover:text-rose-500 hover:bg-rose-50 disabled:opacity-30 transition-colors">
                            <Trash2 size={13} />
                          </button>
                        </td>
                      </tr>
                      <tr className="bg-slate-50/60 border-b border-slate-100">
                        <td colSpan={8} className="px-4 py-2.5">
                          {serialRequired ? (
                            <>
                              <div className="flex flex-wrap items-center gap-2 mb-2">
                                <span className="text-[10px] uppercase tracking-wider font-bold text-slate-400">Serial Numbers</span>
                                <button type="button" onClick={() => autofillSerials(rowIndex)} className="px-2 py-0.5 rounded bg-indigo-600 text-white text-[10px] font-bold hover:bg-indigo-700">Auto Fill</button>
                                {insufficient && <span className="text-[10px] text-amber-700 font-semibold inline-flex items-center gap-1"><AlertTriangle size={11} /> Qty exceeds available serials</span>}
                              </div>
                              <div className="flex flex-wrap gap-1.5">
                                {fitSerialCount(row.serialNumbers, row.qty).map((sn, sidx) => (
                                  <input key={sidx} list={`sn-opt-${rowIndex}`} value={sn} onChange={(e) => onSerialChange(rowIndex, sidx, e.target.value)} placeholder={`SN ${sidx + 1}`} className="w-36 px-2 py-1 rounded border border-slate-200 bg-white text-[11px] focus:outline-none focus:border-indigo-400" />
                                ))}
                                <datalist id={`sn-opt-${rowIndex}`}>{pool.map((sn) => <option key={sn} value={sn} />)}</datalist>
                              </div>
                            </>
                          ) : (
                            <span className="text-[11px] text-slate-400 font-medium">No serial required for this product</span>
                          )}
                        </td>
                      </tr>
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <button type="button" onClick={addRow} className="inline-flex flex-shrink-0 items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700">
              <Plus size={14} /> ပစ္စည်းထပ်ထည့်
            </button>
            <div className="flex items-center gap-4 ml-auto">
              <div className="text-right">
                <p className="text-[11px] text-slate-400 uppercase font-semibold">Subtotal</p>
                <p className="text-sm font-bold text-slate-700">{money(totalAmount)}</p>
              </div>
              {discount > 0 && (
                <div className="text-right">
                  <p className="text-[11px] text-rose-400 uppercase font-semibold">Overall Discount</p>
                  <p className="text-sm font-bold text-rose-600">- {money(discount)}</p>
                </div>
              )}
              <div className="text-right border-l border-slate-200 pl-4">
                <p className="text-[11px] text-slate-400 uppercase font-semibold">ကျသင့်ငွေ</p>
                <p className="text-lg font-black text-slate-900">{money(netAmount)}</p>
              </div>
              <div className="flex items-center gap-2">
                <button type="button" onClick={resetCreateForm} className="px-4 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">ရှင်းမည်</button>
                <button
                  type="button"
                  onClick={() => setPayModalOpen(true)}
                  disabled={details.every(d => d.productId <= 0)}
                  className="px-5 py-2 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50 inline-flex items-center gap-2"
                >
                  <CreditCard size={14} /> ငွေရှင်းမည်
                </button>
              </div>
            </div>
          </div>
          </div>

        </div>

        {payModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md flex flex-col max-h-[90vh]">
              {/* Modal header */}
              <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <CreditCard size={18} className="text-emerald-600" />
                  <h3 className="text-base font-bold text-slate-800">ငွေရှင်းခြင်း</h3>
                </div>
                <button type="button" onClick={() => setPayModalOpen(false)} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600">
                  <X size={16} />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
                {/* Items summary */}
                <div className="rounded-xl border border-slate-200 overflow-hidden">
                  <div className="bg-slate-50 px-3 py-2 border-b border-slate-200">
                    <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">ရောင်းမည့်ပစ္စည်းများ</p>
                  </div>
                  <div className="divide-y divide-slate-100">
                    {details.filter(d => d.productId > 0).map((d, i) => {
                      const prod = productMap.get(d.productId);
                      const lineTotal = d.qty * d.unitPrice - Number((d as any).discountAmount || 0);
                      return (
                        <div key={i} className="flex items-center justify-between px-3 py-2 text-sm">
                          <div className="flex-1 min-w-0">
                            <p className="font-medium text-slate-700 truncate">{prod?.productName ?? '—'}</p>
                            <p className="text-[11px] text-slate-400">{d.qty} × {money(d.unitPrice)}</p>
                          </div>
                          <span className="font-semibold text-slate-800 shrink-0 ml-2">{money(lineTotal)}</span>
                        </div>
                      );
                    })}
                    {details.every(d => d.productId <= 0) && (
                      <p className="px-3 py-3 text-sm text-slate-400 text-center">No items</p>
                    )}
                  </div>
                </div>

                {/* Discount */}
                <div>
                  <label className="block text-xs font-semibold text-slate-600 mb-1.5">Overall Discount</label>
                  <input
                    type="number" min="0" step="0.01"
                    value={discountInput}
                    onChange={(e) => setDiscountInput(e.target.value)}
                    placeholder="0.00"
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                  />
                </div>

                {/* Totals summary */}
                <div className="rounded-xl bg-slate-50 border border-slate-200 px-4 py-3 space-y-1.5 text-sm">
                  <div className="flex justify-between text-slate-600"><span>Subtotal</span><span className="font-semibold text-slate-800">{money(totalAmount)}</span></div>
                  {discount > 0 && <div className="flex justify-between text-slate-600"><span>Discount</span><span className="font-semibold text-rose-600">- {money(discount)}</span></div>}
                  <div className="flex justify-between font-bold text-slate-900 border-t border-slate-200 pt-1.5 text-base"><span>ကျသင့်ငွေ</span><span>{money(netAmount)}</span></div>
                </div>

                {/* Paid Amount */}
                <div className="rounded-xl border-2 border-emerald-300 bg-emerald-50 px-4 py-3 space-y-3">
                  <label className="block text-[11px] font-bold text-emerald-700 uppercase tracking-wide">ရရှိငွေ</label>
                  <div className="flex gap-2">
                    <input
                      type="number" min="0" step="0.01"
                      value={paidInput}
                      onChange={(e) => setPaidInput(e.target.value)}
                      placeholder="0.00"
                      className="flex-1 w-0 px-3 py-2.5 rounded-lg border-2 border-emerald-300 bg-white text-lg font-bold text-emerald-900 focus:outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200"
                    />
                    <button type="button" onClick={() => setPaidInput(netAmount > 0 ? String(netAmount.toFixed(2)) : '0')}
                      className="px-3 py-2 rounded-lg bg-emerald-600 text-white text-xs font-bold hover:bg-emerald-700 shrink-0">Full</button>
                    <button type="button" onClick={() => setPaidInput('0')}
                      className="px-3 py-2 rounded-lg bg-sky-500 text-white text-xs font-bold hover:bg-sky-600 shrink-0">အကြွေး</button>
                  </div>

                  {/* Payment method */}
                  <div>
                    <label className="block text-[10px] font-semibold text-emerald-700 uppercase mb-1">ငွေလက်ခံနည်း</label>
                    <select
                      value={paymentMethodId}
                      onChange={(e) => setPaymentMethodId(Number(e.target.value) || 0)}
                      disabled={effectivePaid <= 0 || normalizedSalePayments.length > 0}
                      className="w-full px-3 py-2 rounded-lg border border-emerald-300 bg-white text-sm focus:outline-none focus:border-emerald-500 disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      <option value={0}>- ငွေလက်ခံနည်းရွေးပါ -</option>
                      {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                    </select>
                    {effectivePaid <= 0 && <p className="text-[10px] text-emerald-600 mt-0.5">Credit sale — ငွေပေးချေမှုမရှိပါ</p>}
                  </div>

                  <SplitPaymentEditor
                    methods={methods}
                    payments={salePayments}
                    onChange={(next) => {
                      setSalePayments(next);
                      const total = paymentTotal(next);
                      setPaidInput(total > 0 ? String(total.toFixed(2)) : '');
                    }}
                    label="Split Payment"
                  />

                  {/* Paid / Due summary */}
                  <div className="border-t border-emerald-200 pt-2 space-y-1 text-sm">
                    <div className="flex justify-between text-slate-600"><span>ရရှိငွေ</span><span className="font-semibold text-emerald-700">{money(paid)}</span></div>
                    <div className={`flex justify-between font-bold text-base ${dueAmount > 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
                      <span>ရရန်ကျန်</span><span>{money(dueAmount)}</span>
                    </div>
                  </div>
                </div>

                {/* Due date — only when credit */}
                {dueAmount > 0 && (
                  <div>
                    <label className="block text-xs font-semibold text-slate-600 mb-1.5">ရရန်နောက်ဆုံးရက် <span className="text-slate-400 font-normal">(အကြွေးရောင်း)</span></label>
                    <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
                  </div>
                )}

                {/* Credit limit warning */}
                {limitExceeded && dueAmount > 0 && (
                  <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 flex items-center gap-2">
                    <AlertTriangle size={15} className="text-rose-600 flex-shrink-0" />
                    <p className="text-sm font-semibold text-rose-700">Credit limit exceeded — this sale will be blocked.</p>
                  </div>
                )}
              </div>

              {/* Modal footer */}
              <div className="px-5 py-4 border-t border-slate-100 flex gap-3">
                <button type="button" onClick={() => setPayModalOpen(false)} className="flex-1 px-4 py-2.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">
                  ပယ်ဖျက်
                </button>
                <button
                  type="button"
                  onClick={saveSale}
                  disabled={saving}
                  className="flex-1 px-4 py-2.5 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center justify-center gap-2"
                >
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} ဘောင်ချာသိမ်းမည်
                </button>
              </div>
            </div>
          </div>
        )}

        {cameraOpen && (
          <BarcodeScannerCamera
            onDetected={(code) => { handleBarcodeScan(code); }}
            onClose={() => setCameraOpen(false)}
          />
        )}
      </div>

      {showAddCustomer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/55">
          <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">Create New Customer</h3>
              <button type="button" onClick={() => { if (creatingCustomer) return; setShowAddCustomer(false); resetNewCustomerForm(); }} className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100">
                <X size={15} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Name</label>
                <input value={newCustomer.name} onChange={(e) => setNewCustomer((prev) => ({ ...prev, name: e.target.value }))} placeholder="Customer name" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Phone</label>
                <input value={newCustomer.phone} onChange={(e) => setNewCustomer((prev) => ({ ...prev, phone: e.target.value }))} placeholder="Phone number" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Address</label>
                <textarea value={newCustomer.address} onChange={(e) => setNewCustomer((prev) => ({ ...prev, address: e.target.value }))} placeholder="Address" rows={3} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm resize-none" />
              </div>
              <div className="flex items-center justify-end gap-2 pt-1">
                <button type="button" onClick={() => { if (creatingCustomer) return; setShowAddCustomer(false); resetNewCustomerForm(); }} className="px-3 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">Cancel</button>
                <button type="button" disabled={creatingCustomer} onClick={createCustomerFromSale} className="px-3 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center gap-2">
                  {creatingCustomer ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} Save Customer
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-800">ရောင်းချမှု စီမံခန့်ခွဲမှု</h2>
          <p className="text-sm text-slate-500 mt-0.5">ယခုလ ရောင်းချမှုများကိုကြည့်ပြီး ဘောင်ချာ၊ ဖောက်သည်၊ ပေးရန်ကျန်များကို လျင်မြန်စွာစစ်ဆေးပါ။</p>
        </div>
        <div className="flex w-full sm:w-auto gap-2">
          <button onClick={() => loadSales(salePage, salePageSize, debouncedSearch, dateFrom, dateTo)} className="inline-flex flex-1 sm:flex-none justify-center items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50">
            <RefreshCw size={14} /> ပြန်တင်
          </button>
          <button onClick={openCreateSale} className="inline-flex flex-1 sm:flex-none justify-center items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700">
            <Plus size={14} /> ရောင်းချမှုအသစ်
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">ဘောင်ချာအရေအတွက်</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{stats.count}</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wide">ရောင်းရငွေ စုစုပေါင်း</p>
          <p className="text-2xl font-bold text-slate-800 mt-1">{money(stats.net)}</p>
        </div>
        <div className="rounded-xl border border-amber-100 bg-amber-50 p-4">
          <p className="text-[11px] font-semibold text-amber-400 uppercase tracking-wide">ရရန်ကျန်</p>
          <p className="text-2xl font-bold text-amber-700 mt-1">{money(stats.due)}</p>
        </div>
        <div className="rounded-xl border border-rose-100 bg-rose-50 p-4">
          <p className="text-[11px] font-semibold text-rose-400 uppercase tracking-wide">ရက်ကျော်ဘောင်ချာ</p>
          <p className="text-2xl font-bold text-rose-700 mt-1">{stats.overdue}</p>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
        <div className="border-b border-slate-200 px-4 py-3 flex items-center justify-between gap-3">
          <h3 className="text-sm font-bold text-slate-800">
            ရောင်းချမှု ဘောင်ချာစာရင်း <span className="ml-1.5 text-[10px] font-bold bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded-full">{rows.length}</span>
          </h3>
          <button onClick={openCreateSale} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700">
            <Plus size={16} /> ရောင်းချမှုအသစ်
          </button>
        </div>

        {showCreateSaleForm ? (
          <div className="p-5 space-y-5">
            <div className="grid grid-cols-1 xl:grid-cols-4 gap-3">
              <div>
                <div className="mb-1.5 flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-600">Customer <span className="text-rose-500">*</span></label>
                  <button
                    type="button"
                    onClick={() => { setShowAddCustomer(true); setCustomerOpen(false); }}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded border border-indigo-200 text-indigo-700 text-[11px] font-semibold hover:bg-indigo-50"
                  >
                    <Plus size={11} /> New
                  </button>
                </div>
                <div className="relative">
                  <input
                    value={customerSearch}
                    onChange={(e) => onCustomerSearch(e.target.value)}
                    onFocus={() => setCustomerOpen(true)}
                    onBlur={() => setTimeout(() => setCustomerOpen(false), 120)}
                    placeholder="Search by name or phone…"
                    className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                  />
                  {customerOpen && (
                    <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                      {customerOptions.length > 0 ? customerOptions.map((c) => (
                        <button key={c.id} type="button" onMouseDown={() => onCustomerSelect(c)} className={`w-full px-3 py-2.5 text-left hover:bg-indigo-50 ${customerId === c.id ? 'bg-indigo-50' : ''}`}>
                          <p className="text-sm font-semibold text-slate-800">{c.name}</p>
                          <p className="text-xs text-slate-400">{c.phone || '—'} · {c.blacklisted ? '⚠ Blacklisted' : c.creditHold ? '⚠ Credit Hold' : 'Normal'}</p>
                        </button>
                      )) : <p className="px-3 py-3 text-xs text-slate-400 text-center">No customer found.</p>}
                    </div>
                  )}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Staff</label>
                <select value={staffId} onChange={(e) => setStaffId(Number(e.target.value) || 0)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400">
                  <option value={0}>— Select staff —</option>
                  {staffs.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.role})</option>)}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Sale Date</label>
                <input
                  type="date"
                  value={saleDate}
                  max={canBackdateSale ? undefined : todayStr}
                  onChange={(e) => setSaleDate(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                />
                {!canBackdateSale && <p className="text-[10px] text-slate-400 mt-1">Back date requires permission.</p>}
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Remark</label>
                <input value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="Optional note" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
              </div>
            </div>

            <div className={`rounded-lg border px-4 py-2.5 ${creditBannerClass}`}>
              <p className="text-sm font-semibold inline-flex items-center gap-2">
                {!selectedCustomer ? <User size={15} /> : selectedCustomer.blacklisted ? <ShieldAlert size={15} /> : selectedCustomer.creditHold ? <Ban size={15} /> : (limitExceeded || limitNear) ? <AlertTriangle size={15} /> : <CheckCircle2 size={15} />}
                {!selectedCustomer
                  ? 'Select a customer to begin'
                  : selectedCustomer.blacklisted
                    ? 'Customer is blacklisted — cash sale only'
                    : selectedCustomer.creditHold
                      ? 'Customer is on credit hold — cash sale only'
                      : limitExceeded
                        ? `Credit limit exceeded (${money(projectedOutstanding)} / ${money(creditLimit)})`
                        : limitNear
                          ? `Credit near 80% (${money(projectedOutstanding)} / ${money(creditLimit)})`
                          : dueAmount > 0
                            ? `Credit OK — ${money(projectedOutstanding)} / ${money(creditLimit || projectedOutstanding)}`
                            : 'Cash sale — no due amount'}
              </p>
            </div>

            {/* Barcode Scanner */}
            <div className="flex items-center gap-2 px-3 py-2 bg-violet-50 border border-violet-200 rounded-lg">
              <Search size={15} className="text-violet-400 shrink-0" />
              <input
                type="text"
                value={barcodeInput}
                onChange={e => setBarcodeInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); handleBarcodeScan(barcodeInput); } }}
                placeholder="Barcode reader ဖြင့် scan (သို့) serial / product code ရိုက်ပြီး Enter"
                className="flex-1 bg-transparent outline-none text-sm font-medium text-violet-800 placeholder:text-violet-300 min-w-0"
                autoComplete="off"
              />
              <button
                type="button"
                onClick={() => setCameraOpen(true)}
                title="Scan with phone camera"
                className="shrink-0 flex items-center gap-1.5 px-3 py-1.5 bg-violet-600 hover:bg-violet-700 text-white rounded-lg text-[11px] font-black uppercase tracking-wide transition-all"
              >
                <Camera size={13} /> Camera
              </button>
            </div>

            <div className="border border-slate-200 rounded-lg overflow-auto">
              <table className="w-full min-w-[980px] text-xs">
                <thead className="bg-slate-100 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                  <tr>
                    <th className="px-3 py-2.5 text-left">Product</th>
                    <th className="px-3 py-2.5 text-left w-20">Qty</th>
                    <th className="px-3 py-2.5 text-left w-32">Unit Price</th>
                    <th className="px-3 py-2.5 text-left w-28">Discount</th>
                    <th className="px-3 py-2.5 text-center w-14">FOC</th>
                    <th className="px-3 py-2.5 text-left w-28">Stock / Pool</th>
                    <th className="px-3 py-2.5 text-right w-32">Subtotal</th>
                    <th className="px-3 py-2.5 w-10"></th>
                  </tr>
                </thead>
                <tbody>
                  {details.map((row, rowIndex) => {
                    const pool = getSerialPool(row.productId);
                    const serialRequired = isSerialProduct(row.productId);
                    const qtyOnlyStock = getQtyOnlyStock(row.productId);
                    const insufficient = serialRequired && pool.length > 0 && row.qty > pool.length;
                    return (
                      <React.Fragment key={rowIndex}>
                        <tr className="border-b border-slate-100 hover:bg-slate-50/50">
                          <td className="px-3 py-2">
                            <input
                              list={`product-opt-${rowIndex}`}
                              value={productSearches[rowIndex] || ''}
                              onChange={(e) => onProductSearch(rowIndex, e.target.value)}
                              placeholder="Search product…"
                              className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400"
                            />
                            <datalist id={`product-opt-${rowIndex}`}>
                              {products.map((p) => <option key={p.id} value={`${p.name}${p.productCode ? ` ${p.productCode}` : ''}`} />)}
                            </datalist>
                          </td>
                          <td className="px-3 py-2"><input type="number" min="1" max={serialRequired ? pool.length : undefined} value={row.qty || ''} onChange={(e) => changeDetail(rowIndex, { qty: Math.max(0, Number(e.target.value) || 0) })} className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" /></td>
                          <td className="px-3 py-2"><input type="number" min="0" step="0.01" value={row.unitPrice || ''} onChange={(e) => changeDetail(rowIndex, { unitPrice: Math.max(0, Number(e.target.value) || 0) })} className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" /></td>
                          <td className="px-3 py-2">
                            <input type="number" min="0" step="0.01"
                              value={(row as any).discountAmount || ''}
                              onChange={(e) => changeDetail(rowIndex, { discountAmount: Math.max(0, Number(e.target.value) || 0) } as any)}
                              placeholder="0"
                              className="w-full px-2 py-1.5 rounded border border-slate-200 bg-white focus:outline-none focus:border-indigo-400" />
                          </td>
                          <td className="px-3 py-2 text-center">
                            <input type="checkbox"
                              checked={!!(row as any).foc}
                              onChange={(e) => changeDetail(rowIndex, { foc: e.target.checked } as any)}
                              className="w-4 h-4 accent-emerald-600 cursor-pointer" />
                          </td>
                          <td className="px-3 py-2">
                            {serialRequired ? (
                              <span className={`inline-flex px-2 py-1 rounded-md text-[11px] font-bold ${insufficient ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>{pool.length} avail.</span>
                            ) : (
                              <span className="inline-flex px-2 py-1 rounded-md text-[11px] font-bold bg-slate-100 text-slate-600">{qtyOnlyStock} in stock</span>
                            )}
                          </td>
                          <td className="px-3 py-2 text-right font-bold text-slate-700">{money(row.subtotal || 0)}</td>
                          <td className="px-3 py-2 text-center">
                            <button type="button" onClick={() => removeRow(rowIndex)} disabled={details.length <= 1} className="p-1.5 rounded text-slate-400 hover:text-rose-500 hover:bg-rose-50 disabled:opacity-30 transition-colors">
                              <Trash2 size={13} />
                            </button>
                          </td>
                        </tr>
                        <tr className="bg-slate-50/60 border-b border-slate-100">
                          <td colSpan={8} className="px-4 py-2.5">
                            {serialRequired ? (
                              <>
                                <div className="flex flex-wrap items-center gap-2 mb-2">
                                  <span className="text-[10px] uppercase tracking-wider font-bold text-slate-400">Serial Numbers</span>
                                  <button type="button" onClick={() => autofillSerials(rowIndex)} className="px-2 py-0.5 rounded bg-indigo-600 text-white text-[10px] font-bold hover:bg-indigo-700">Auto Fill</button>
                                  {insufficient && <span className="text-[10px] text-amber-700 font-semibold inline-flex items-center gap-1"><AlertTriangle size={11} /> Qty exceeds available serials</span>}
                                </div>
                                <div className="flex flex-wrap gap-1.5">
                                  {fitSerialCount(row.serialNumbers, row.qty).map((sn, sidx) => (
                                    <input key={sidx} list={`sn-opt-${rowIndex}`} value={sn} onChange={(e) => onSerialChange(rowIndex, sidx, e.target.value)} placeholder={`SN ${sidx + 1}`} className="w-36 px-2 py-1 rounded border border-slate-200 bg-white text-[11px] focus:outline-none focus:border-indigo-400" />
                                  ))}
                                  <datalist id={`sn-opt-${rowIndex}`}>{pool.map((sn) => <option key={sn} value={sn} />)}</datalist>
                                </div>
                              </>
                            ) : (
                              <span className="text-[11px] text-slate-400 font-medium">No serial required for this product</span>
                            )}
                          </td>
                        </tr>
                      </React.Fragment>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="flex flex-col lg:flex-row items-start gap-4">
              <button type="button" onClick={addRow} className="inline-flex flex-shrink-0 items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700">
                <Plus size={14} /> Add Item
              </button>
              <div className="flex-1 w-full lg:max-w-sm ml-auto rounded-lg border border-slate-200 bg-slate-50 p-4 space-y-3">
                <div className="flex gap-2">
                  <div className="flex-1">
                    <label className="block text-[10px] font-semibold text-slate-500 uppercase mb-1">Discount</label>
                    <input type="number" min="0" step="0.01" value={discountInput} onChange={(e) => setDiscountInput(e.target.value)} placeholder="0.00" className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400" />
                  </div>
                  <div className="flex-1">
                    <label className="block text-[10px] font-semibold text-slate-500 uppercase mb-1">Paid</label>
                    <div className="flex gap-1">
                      <input type="number" min="0" step="0.01" value={paidInput} onChange={(e) => setPaidInput(e.target.value)} placeholder="0.00" className="flex-1 w-0 px-2.5 py-1.5 rounded border border-slate-200 bg-white text-sm focus:outline-none focus:border-indigo-400" />
                      <button type="button" onClick={() => setPaidInput(netAmount > 0 ? String(netAmount.toFixed(2)) : '0')} className="px-2 py-1.5 rounded bg-emerald-600 text-white text-[10px] font-bold hover:bg-emerald-700">Full</button>
                      <button type="button" onClick={() => setPaidInput('0')} className="px-2 py-1.5 rounded bg-sky-500 text-white text-[10px] font-bold hover:bg-sky-600">Credit</button>
                    </div>
                  </div>
                </div>
                <div className="border-t border-slate-200 pt-3 space-y-1.5 text-sm">
                  <div className="flex justify-between text-slate-600"><span>Subtotal</span><span className="font-semibold text-slate-800">{money(totalAmount)}</span></div>
                  <div className="flex justify-between text-slate-600"><span>Discount</span><span className="font-semibold text-rose-600">— {money(discount)}</span></div>
                  <div className="flex justify-between text-slate-700 font-semibold border-t border-slate-200 pt-1.5"><span>Net Amount</span><span className="text-slate-900">{money(netAmount)}</span></div>
                  <div className="flex justify-between text-slate-600"><span>Paid</span><span className="font-semibold text-emerald-600">{money(paid)}</span></div>
                  <div className={`flex justify-between font-bold ${dueAmount > 0 ? 'text-rose-700' : 'text-emerald-700'}`}><span>Due</span><span>{money(dueAmount)}</span></div>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 border-t border-slate-100 pt-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Due Date <span className="text-slate-400 font-normal">(credit only)</span></label>
                <input type="date" value={dueDate} disabled={dueAmount <= 0} onChange={(e) => setDueDate(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5">Payment Method</label>
                <select value={paymentMethodId} disabled={effectivePaid <= 0 || normalizedSalePayments.length > 0} onChange={(e) => setPaymentMethodId(Number(e.target.value) || 0)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed">
                  <option value={0}>— Select method —</option>
                  {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                </select>
                {effectivePaid <= 0 && <p className="text-[10px] text-slate-400 mt-1">Required only when paid amount &gt; 0</p>}
              </div>
              <div className="md:col-span-2">
                <SplitPaymentEditor
                  methods={methods}
                  payments={salePayments}
                  onChange={(next) => {
                    setSalePayments(next);
                    const total = paymentTotal(next);
                    setPaidInput(total > 0 ? String(total.toFixed(2)) : '');
                  }}
                  label="Split Payment"
                />
              </div>
              <div className="flex items-end justify-end gap-2">
                <button type="button" onClick={resetCreateForm} className="px-4 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">Clear</button>
                <button type="button" onClick={saveSale} disabled={saving} className="px-5 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center gap-2">
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} Save Sale
                </button>
              </div>
            </div>

            {limitExceeded && dueAmount > 0 && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 flex items-center gap-2">
                <AlertTriangle size={15} className="text-rose-600 flex-shrink-0" />
                <p className="text-sm font-semibold text-rose-700">Credit limit exceeded — this sale will be blocked.</p>
              </div>
            )}
          </div>
        ) : (
          <div className="p-4 space-y-3">
            <div className="grid grid-cols-1 xl:grid-cols-[1.1fr_1fr] gap-3">
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <label className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">Quick Search</label>
                <div className="relative mt-1.5">
                  <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input value={search} onChange={e => setSearch(e.target.value)}
                    placeholder="ဘောင်ချာနံပါတ် / ဖောက်သည် / ရောင်းသူ"
                    className="w-full pl-8 pr-8 py-2 text-sm bg-white border border-slate-200 rounded-lg focus:outline-none focus:border-indigo-400" />
                  {loading && search && (
                    <span className="absolute right-2.5 top-1/2 -translate-y-1/2">
                      <svg className="animate-spin h-3.5 w-3.5 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>
                    </span>
                  )}
                </div>
              </div>
              <div className="rounded-lg border border-indigo-100 bg-indigo-50 p-3">
                <label className="text-[11px] font-bold text-indigo-600 uppercase tracking-wide">Voucher No Direct Search</label>
                <div className="flex gap-2 mt-1.5">
                  <input value={voucherLookup}
                    onChange={(e) => setVoucherLookup(e.target.value)}
                    onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleVoucherLookup(); } }}
                    placeholder="ဥပမာ SALE-0001"
                    className="flex-1 min-w-0 px-3 py-2 text-sm bg-white border border-indigo-200 rounded-lg focus:outline-none focus:border-indigo-500" />
                  <button type="button" onClick={handleVoucherLookup}
                    className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700">
                    <Eye size={13} /> ဖွင့်
                  </button>
                </div>
              </div>
            </div>
            {/* Filter row */}
            <div className="flex flex-wrap gap-2 items-end">
              {/* Search */}
              <div className="hidden">
                <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
                <input value={search} onChange={e => setSearch(e.target.value)}
                  placeholder="Search all sales..."
                  className="w-full pl-8 pr-8 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:border-indigo-400" />
                {loading && search && (
                  <span className="absolute right-2.5 top-1/2 -translate-y-1/2">
                    <svg className="animate-spin h-3.5 w-3.5 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>
                  </span>
                )}
              </div>
              {/* Date From */}
              <div className="flex flex-col gap-0.5">
                <label className="text-[10px] font-semibold text-slate-500">From</label>
                <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setDateShortcut('CUSTOM'); }}
                  className="border border-slate-200 rounded px-2 py-1.5 text-xs text-slate-700 bg-slate-50 focus:outline-none focus:border-indigo-400" />
              </div>
              {/* Date To */}
              <div className="flex flex-col gap-0.5">
                <label className="text-[10px] font-semibold text-slate-500">To</label>
                <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setDateShortcut('CUSTOM'); }}
                  className="border border-slate-200 rounded px-2 py-1.5 text-xs text-slate-700 bg-slate-50 focus:outline-none focus:border-indigo-400" />
              </div>
              {/* Staff */}
              <div className="flex flex-col gap-0.5">
                <label className="text-[10px] font-semibold text-slate-500">Staff</label>
                <select value={staffFilter} onChange={e => setStaffFilter(Number(e.target.value))}
                  className="border border-slate-200 rounded px-2 py-1.5 text-xs text-slate-700 bg-slate-50 focus:outline-none focus:border-indigo-400">
                  <option value={0}>ရောင်းသူအားလုံး</option>
                  {staffs.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {[
                  { key: 'TODAY', label: 'Today' },
                  { key: 'WEEK', label: 'This Week' },
                  { key: 'MONTH', label: 'This Month' },
                  { key: 'ALL', label: 'All' }
                ].map((item) => (
                  <button key={item.key} type="button"
                    onClick={() => applyDateShortcut(item.key as Exclude<DateShortcut, 'CUSTOM'>)}
                    className={`px-3 py-1.5 rounded-lg text-[11px] font-bold border ${dateShortcut === item.key ? 'bg-slate-800 text-white border-slate-800' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>
                    {item.label}
                  </button>
                ))}
              </div>
              {/* Clear */}
              {(dateShortcut !== 'MONTH' || staffFilter > 0 || search || voucherLookup || filter !== 'ALL') && (
                <button onClick={() => { const range = getThisMonthRange(); setDateFrom(range.from); setDateTo(range.to); setDateShortcut('MONTH'); setStaffFilter(0); setSearch(''); setVoucherLookup(''); setFilter('ALL'); }}
                  className="text-xs text-rose-500 hover:text-rose-700 font-semibold px-2 py-1.5 border border-rose-200 rounded bg-rose-50">
                  Reset
                </button>
              )}
            </div>
            {/* Status filter pills */}
            <div className="flex flex-wrap gap-1.5">
              {([
                ['ALL', 'အားလုံး'],
                ['DUE', 'ရရန်ကျန်'],
                ['PENDING', 'မပေးရသေး'],
                ['PARTIAL', 'တချို့ပေးပြီး'],
                ['PAID', 'ပေးပြီး'],
                ['OVERDUE', 'ရက်ကျော်']
              ] as [ListFilter, string][]).map(([key, label]) => (
                <button key={key} onClick={() => setFilter(key)}
                  className={`px-3 py-1 rounded-full text-[11px] font-bold border ${filter === key ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>
                  {label}
                </button>
              ))}
              <span className="ml-auto text-[10px] text-slate-400 self-center">
                Showing {saleTotalElements === 0 ? 0 : salePage * salePageSize + 1}–{Math.min((salePage + 1) * salePageSize, saleTotalElements)} of {saleTotalElements.toLocaleString()}
              </span>
            </div>

            <div className="border border-slate-200 rounded-lg overflow-auto max-h-[68vh]">
              <table className="w-full min-w-[920px] text-sm">
                <thead className="sticky top-0 bg-slate-100 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                  <tr>
                    <th className="px-4 py-3 text-left">ဘောင်ချာ</th>
                    <th className="px-4 py-3 text-left">ရက်စွဲ</th>
                    <th className="px-4 py-3 text-left">ဖောက်သည်</th>
                    <th className="px-4 py-3 text-left">ရောင်းသူ</th>
                    <th className="px-4 py-3 text-right">စုစုပေါင်း</th>
                    <th className="px-4 py-3 text-right">ရပြီး</th>
                    <th className="px-4 py-3 text-right">ရရန်ကျန်</th>
                    <th className="px-4 py-3 text-center">အခြေအနေ</th>
                    <th className="px-4 py-3 text-right">လုပ်ဆောင်ချက်</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredRows.length === 0 ? (
                    <tr><td colSpan={9} className="px-4 py-12 text-center text-slate-400">ရောင်းချမှု မတွေ့ပါ။</td></tr>
                  ) : filteredRows.map((r) => {
                    const state = getSaleState(r);
                    const isDue = (Number(r.dueAmount) || 0) > 0;
                    return (
                      <tr key={r.id || r.saleCode} className="hover:bg-slate-50 transition-colors">
                        <td className="px-4 py-3 font-semibold text-slate-800">{r.saleCode || `#${r.id}`}</td>
                        <td className="px-4 py-3 text-xs text-slate-500">{fmtDate(r.saleDate)}</td>
                        <td className="px-4 py-3 font-medium text-slate-700">{r.customerName || '—'}</td>
                        <td className="px-4 py-3 text-xs text-slate-500">{r.staffName || '—'}</td>
                        <td className="px-4 py-3 text-right font-semibold text-slate-700">{money(Number(r.netAmount) || 0)}</td>
                        <td className="px-4 py-3 text-right font-semibold text-emerald-600">{money(Number(r.paidAmount) || 0)}</td>
                        <td className={`px-4 py-3 text-right font-bold ${isDue ? 'text-rose-600' : 'text-slate-300'}`}>{isDue ? money(Number(r.dueAmount) || 0) : '—'}</td>
                        <td className="px-4 py-3 text-center">
                          <span className={`inline-flex px-2.5 py-1 rounded-full text-[10px] font-bold ${badgeByState[state]}`}>{saleStateLabel[state]}</span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-end gap-1.5">
                            <button onClick={() => r.id && openDetail(r.id)} className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-xs font-medium hover:bg-slate-50">
                              <Eye size={12} /> ကြည့်ရန်
                            </button>
                            {isDue && (
                              <button onClick={() => r.id && openDetail(r.id)} className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-emerald-200 text-emerald-700 text-xs font-medium hover:bg-emerald-50">
                                <CreditCard size={12} /> ငွေသွင်း
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {saleTotalPages > 1 && (
              <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
                <span className="text-[11px] text-slate-500">
                  Showing {saleTotalElements === 0 ? 0 : salePage * salePageSize + 1}–{Math.min((salePage + 1) * salePageSize, saleTotalElements)} of {saleTotalElements.toLocaleString()}
                </span>
                <div className="flex items-center gap-1.5 flex-wrap">
                  <select value={salePageSize} onChange={(e) => { setSalePageSize(Number(e.target.value)); setSalePage(0); }} className="text-xs px-2 py-1 border border-slate-200 rounded-lg bg-white">
                    {[10, 20, 50, 100].map((n) => <option key={n} value={n}>Show {n}</option>)}
                  </select>
                  <button onClick={() => setSalePage((p) => Math.max(0, p - 1))} disabled={salePage === 0} className="px-2 py-1 text-xs rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40">‹</button>
                  {(() => {
                    const delta = 2; const pages: (number | string)[] = []; let prev = -1;
                    for (let i = 0; i < saleTotalPages; i++) {
                      if (i === 0 || i === saleTotalPages - 1 || (i >= salePage - delta && i <= salePage + delta)) {
                        if (prev !== -1 && i - prev > 1) pages.push('…');
                        pages.push(i); prev = i;
                      }
                    }
                    return pages.map((p, idx) => typeof p === 'string'
                      ? <span key={`e${idx}`} className="px-1 text-slate-400 text-xs">…</span>
                      : <button key={p} onClick={() => setSalePage(p as number)} className={`px-2.5 py-1 text-xs rounded-lg border ${salePage === p ? 'bg-indigo-600 text-white border-indigo-600' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}>{(p as number) + 1}</button>
                    );
                  })()}
                  <button onClick={() => setSalePage((p) => Math.min(saleTotalPages - 1, p + 1))} disabled={salePage >= saleTotalPages - 1} className="px-2 py-1 text-xs rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40">›</button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {showAddCustomer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/55">
          <div className="w-full max-w-md bg-white rounded-xl border border-slate-200 shadow-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">Create New Customer</h3>
              <button
                type="button"
                onClick={() => {
                  if (creatingCustomer) return;
                  setShowAddCustomer(false);
                  resetNewCustomerForm();
                }}
                className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100"
              >
                <X size={15} />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Name</label>
                <input
                  value={newCustomer.name}
                  onChange={(e) => setNewCustomer((prev) => ({ ...prev, name: e.target.value }))}
                  placeholder="Customer name"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Phone</label>
                <input
                  value={newCustomer.phone}
                  onChange={(e) => setNewCustomer((prev) => ({ ...prev, phone: e.target.value }))}
                  placeholder="Phone number"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Address</label>
                <textarea
                  value={newCustomer.address}
                  onChange={(e) => setNewCustomer((prev) => ({ ...prev, address: e.target.value }))}
                  placeholder="Address"
                  rows={3}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm resize-none"
                />
              </div>
              <div className="flex items-center justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => {
                    if (creatingCustomer) return;
                    setShowAddCustomer(false);
                    resetNewCustomerForm();
                  }}
                  className="px-3 py-2 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={creatingCustomer}
                  onClick={createCustomerFromSale}
                  className="px-3 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-60 inline-flex items-center gap-2"
                >
                  {creatingCustomer ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                  Save Customer
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {viewSale && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/55">
          <div className="w-full max-w-5xl bg-white rounded-2xl border border-slate-200 shadow-xl overflow-hidden max-h-[92vh] flex flex-col">

            <div className="px-5 py-4 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 flex-shrink-0">
              <div>
                <h3 className="text-base font-bold text-slate-800">Invoice {viewSale.saleCode || `#${viewSale.id}`}</h3>
                <p className="text-xs text-slate-500 mt-0.5">{viewSale.customerName || '—'} · {fmtDate(viewSale.saleDate)} · Staff: {viewSale.staffName || '—'}</p>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                <button onClick={() => viewSale.id && setPrintPreviewSaleId(viewSale.id)} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-semibold hover:bg-indigo-700">
                  <Printer size={13} /> Print
                </button>
                <button onClick={() => { setViewSale(null); setViewReturns([]); setViewReturnsLoading(false); }} className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600">
                  <X size={16} />
                </button>
              </div>
            </div>

            <div className="overflow-auto flex-1">
              <div className="p-5 space-y-5">

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <p className="text-xs text-slate-500 font-medium">Net Amount</p>
                    <p className="text-lg font-bold text-slate-800 mt-1">{money(Number(viewSale.netAmount) || 0)}</p>
                  </div>
                  <div className="rounded-lg border border-emerald-100 bg-emerald-50 p-3">
                    <p className="text-xs text-emerald-600 font-medium">Paid</p>
                    <p className="text-lg font-bold text-emerald-700 mt-1">{money(Number(viewSale.paidAmount) || 0)}</p>
                  </div>
                  <div className="rounded-lg border border-rose-100 bg-rose-50 p-3">
                    <p className="text-xs text-rose-500 font-medium">Due</p>
                    <p className="text-lg font-bold text-rose-700 mt-1">{money(Number(viewSale.dueAmount) || 0)}</p>
                  </div>
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <p className="text-xs text-slate-500 font-medium">Due Date</p>
                    <p className="text-base font-bold text-slate-800 mt-1">{viewSale.dueDate || '—'}</p>
                  </div>
                </div>

                <div>
                  <h4 className="text-[11px] font-bold text-slate-400 uppercase tracking-wide mb-2">Items Sold</h4>
                  <div className="border border-slate-200 rounded-lg overflow-auto">
                    <table className="w-full min-w-[820px] text-sm">
                      <thead className="bg-slate-100 border-b border-slate-200 text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                        <tr>
                          <th className="px-4 py-2.5 text-left">Product</th>
                          <th className="px-4 py-2.5 text-center w-16">Qty</th>
                          <th className="px-4 py-2.5 text-right w-28">Unit Price</th>
                          <th className="px-4 py-2.5 text-left">Serial #</th>
                          <th className="px-4 py-2.5 text-center w-24">Warranty</th>
                          <th className="px-4 py-2.5 text-center w-28">Expiry</th>
                          <th className="px-4 py-2.5 text-right w-28">Subtotal</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {(viewSale.details || []).map((d, i) => (
                          <tr key={i} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-medium text-slate-700">{d.productName || `Product #${d.productId}`}</td>
                            <td className="px-4 py-3 text-center text-slate-600">{d.qty}</td>
                            <td className="px-4 py-3 text-right text-slate-700">{money(Number(d.unitPrice) || 0)}</td>
                            <td className="px-4 py-3 text-xs text-slate-500">{isSerialProduct(d.productId) ? (d.serialNumbers?.length ? d.serialNumbers.join(', ') : '—') : <span className="text-slate-400">Qty only</span>}</td>
                            <td className="px-4 py-3 text-center text-xs text-slate-500">{Number(d.warrantyMonths || 0) > 0 ? `${d.warrantyMonths} mo.` : '—'}</td>
                            <td className="px-4 py-3 text-center text-xs text-slate-500">{d.warrantyExpiryDate || '—'}</td>
                            <td className="px-4 py-3 text-right font-semibold text-slate-800">{money(Number(d.subtotal) || 0)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  <div className="rounded-lg border border-slate-200 p-4">
                    <h4 className="text-sm font-bold text-slate-800 mb-3">{(Number(viewSale.dueAmount) || 0) > 0 ? 'Collect Payment' : 'Payment'}</h4>
                    {(Number(viewSale.dueAmount) || 0) > 0 ? (
                      <form onSubmit={submitPayDue} className="space-y-2.5">
                        <div>
                          <label className="block text-xs font-semibold text-slate-500 mb-1">Amount</label>
                          <input type="number" min="0" step="0.01" value={payForm.amount} onChange={(e) => setPayForm((p) => ({ ...p, amount: e.target.value, payments: [] }))} placeholder="0.00" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-500 mb-1">Payment Method</label>
                          <select value={payForm.paymentMethodId} onChange={(e) => setPayForm((p) => ({ ...p, paymentMethodId: Number(e.target.value) || 0 }))} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400">
                            <option value={0}>— Select method —</option>
                            {methods.map((m) => <option key={m.id} value={m.id}>{m.methodName}</option>)}
                          </select>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-500 mb-1">Transaction No <span className="font-normal text-slate-400">(optional)</span></label>
                          <input value={payForm.transactionNo} onChange={(e) => setPayForm((p) => ({ ...p, transactionNo: e.target.value }))} placeholder="e.g. TXN-12345" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-500 mb-1">Note <span className="font-normal text-slate-400">(optional)</span></label>
                          <input value={payForm.note} onChange={(e) => setPayForm((p) => ({ ...p, note: e.target.value }))} placeholder="Payment note" className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
                        </div>
                        <SplitPaymentEditor
                          methods={methods}
                          payments={payForm.payments || []}
                          onChange={(next) => setPayForm((p) => ({ ...p, payments: next, amount: paymentTotal(next) > 0 ? String(paymentTotal(next).toFixed(2)) : p.amount }))}
                          label="Split Payment"
                        />
                        <button type="submit" disabled={paySaving} className="w-full py-2 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-60 inline-flex items-center justify-center gap-2">
                          {paySaving ? <Loader2 size={14} className="animate-spin" /> : <CreditCard size={14} />} Confirm Payment
                        </button>
                      </form>
                    ) : (
                      <div className="flex items-center gap-2 text-emerald-700 bg-emerald-50 rounded-lg px-3 py-2.5">
                        <CheckCircle2 size={16} /><p className="text-sm font-semibold">Fully paid</p>
                      </div>
                    )}
                  </div>

                  <div className="rounded-lg border border-slate-200 p-4">
                    <h4 className="text-sm font-bold text-slate-800 mb-3">Payment History</h4>
                    <div className="overflow-auto max-h-56 border border-slate-100 rounded-lg">
                      <table className="w-full min-w-[380px] text-xs">
                        <thead className="bg-slate-50 border-b border-slate-200 text-[10px] font-semibold text-slate-500 uppercase tracking-wide">
                          <tr>
                            <th className="px-3 py-2 text-left">Date</th>
                            <th className="px-3 py-2 text-left">Method</th>
                            <th className="px-3 py-2 text-right">Amount</th>
                            <th className="px-3 py-2 text-left">Staff</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {viewPayments.length === 0 ? (
                            <tr><td colSpan={4} className="px-3 py-6 text-center text-slate-400">No payments yet.</td></tr>
                          ) : viewPayments.map((p) => (
                            <tr key={p.id} className="hover:bg-slate-50">
                              <td className="px-3 py-2 text-slate-600">{fmtDate(p.paymentDate)}</td>
                              <td className="px-3 py-2 text-slate-600">{p.paymentMethodName || '—'}</td>
                              <td className="px-3 py-2 text-right font-semibold text-emerald-700">{money(Number(p.amount) || 0)}</td>
                              <td className="px-3 py-2 text-slate-500">{p.staffName || '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>

                <div className="rounded-lg border border-slate-200 p-4">
                  <div className="flex items-center justify-between mb-3">
                    <h4 className="text-sm font-bold text-slate-800">Sale Returns</h4>
                    <span className="text-xs text-slate-500 bg-slate-100 px-2 py-0.5 rounded-full font-semibold">{viewReturns.length}</span>
                  </div>
                  <div className="border border-slate-100 rounded-lg overflow-auto max-h-52">
                    <table className="w-full min-w-[560px] text-xs">
                      <thead className="bg-slate-50 border-b border-slate-200 text-[10px] font-semibold text-slate-500 uppercase tracking-wide">
                        <tr>
                          <th className="px-3 py-2 text-left">Return No</th>
                          <th className="px-3 py-2 text-left">Date</th>
                          <th className="px-3 py-2 text-right">Total</th>
                          <th className="px-3 py-2 text-right">Refund</th>
                          <th className="px-3 py-2 text-left">Reason</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {viewReturnsLoading ? (
                          <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-400">Loading…</td></tr>
                        ) : viewReturns.length === 0 ? (
                          <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-400">No returns for this sale.</td></tr>
                        ) : viewReturns.map((row) => (
                          <tr key={row.id || row.returnCode} className="hover:bg-slate-50">
                            <td className="px-3 py-2">
                              {row.id ? (
                                <a href={`#${AppRoute.SALE_RETURNS}?saleReturnId=${row.id}&saleId=${viewSale.id}`} className="font-semibold text-indigo-600 hover:underline">
                                  {row.returnCode || `#${row.id}`}
                                </a>
                              ) : (
                                <span className="font-medium text-slate-700">{row.returnCode || '—'}</span>
                              )}
                            </td>
                            <td className="px-3 py-2 text-slate-500">{fmtDate(row.returnDate)}</td>
                            <td className="px-3 py-2 text-right font-semibold text-slate-700">{money(Number(row.totalReturnAmount) || 0)}</td>
                            <td className="px-3 py-2 text-right font-semibold text-emerald-700">{money(Number(row.refundAmount ?? row.totalReturnAmount) || 0)}</td>
                            <td className="px-3 py-2 text-slate-500 max-w-[200px] truncate">{row.reason || '—'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

              </div>
            </div>
          </div>
        </div>
      )}

      {cameraOpen && (
        <BarcodeScannerCamera
          onDetected={(code) => {
            handleBarcodeScan(code);
            // keep camera open so user can scan multiple items
          }}
          onClose={() => setCameraOpen(false)}
        />
      )}

      {printPreviewSaleId && (
        <InvoicePrintPreview
          documentType="SALE"
          documentId={printPreviewSaleId}
          title="Sale Invoice"
          onClose={() => setPrintPreviewSaleId(null)}
        />
      )}
    </div>
  );
};

export default SaleManagement;
