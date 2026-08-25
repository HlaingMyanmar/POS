import React, { useState, useEffect, useCallback, useRef, useMemo, useLayoutEffect } from 'react';
import { createPortal } from 'react-dom';
import { useDataEvents } from '../hooks/useDataEvents';
import { useLocation, useNavigate } from 'react-router-dom';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { purchaseApiService, PurchasePage, PurchaseStats, PurchaseTrendPoint, TopSupplierPoint, PurchaseImportPreview, PurchaseTimelineEvent, PurchaseAnalytics } from '../services/purchaseapiservice';
import { purchaseReturnApiService } from '../services/purchasereturnapiservice';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { accountingApiService } from '../services/accountingapiservice';
import { supplierService } from '../services/supplierapiservice';
import { staffService } from '../services/staffapiservice';
import { productService } from '../services/productapiservice';
import { AppRoute, PurchaseDTO, PurchaseDetailDTO, PurchaseBudgetCheck, SupplierDTO, StaffDTO, ProductDTO, PaymentMethodDTO, PaymentTransactionDTO, PurchaseReturnDTO, ProductStockHistoryMovementDTO, ReorderSuggestionDTO } from '../types';
import { Plus, Trash2, Save, ShoppingCart, Hash, DollarSign, User, List, Eye, X, RefreshCw, ArrowLeft, FileText, AlertCircle, CheckCircle, Search, Filter, CreditCard, Box, Printer, Camera, Share2, ChevronDown, ChevronUp, Download, Loader2, ClipboardList, Ban, ScanLine, FileSpreadsheet, Upload, AlertTriangle, Copy, FileDown, Warehouse, BarChart3, History } from 'lucide-react';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { buildPurchaseVoucherHtml } from './purchaseVoucherTemplate';
import { getCachedCompanySettings } from '../utils/companySettings';
import { getFromSession } from '../utils/storageHelper';
import SplitPaymentEditor from '../components/SplitPaymentEditor';
import BarcodeScannerCamera from '../components/BarcodeScannerCamera';
import Swal from 'sweetalert2';
import { supplierPaymentApiService, SupplierPayable, SupplierPayment } from '../services/supplierpaymentapiservice';
import { purchaseBudgetApiService, PurchaseBudgetDTO } from '../services/purchasebudgetapiservice';
import { stockLotApiService, StockLotDTO, WarehouseBalanceDTO } from '../services/stocklotapiservice';
import { warehouseApiService, WarehouseDTO } from '../services/warehouseapiservice';
import { Bar, BarChart, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

type PurchaseDetailForm = PurchaseDetailDTO & { productSearch?: string; assignSerials?: boolean };

type ComboboxItem = { id: number; label: string; sub?: string; searchText?: string };

/** Portaled searchable combobox — menu renders on document.body (avoids table/modal overflow clip). */
const PortaledCombobox: React.FC<{
  items: ComboboxItem[];
  value: number;
  displayValue?: string;
  placeholder?: string;
  onChange: (id: number, item?: ComboboxItem) => void;
  onQueryChange?: (query: string) => void;
  inputClassName?: string;
  maxItems?: number;
}> = ({
  items,
  value,
  displayValue,
  placeholder = 'ရှာပါ...',
  onChange,
  onQueryChange,
  inputClassName,
  maxItems = 80,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const selected = items.find((i) => i.id === value);
  const shownLabel = displayValue ?? selected?.label ?? '';

  useEffect(() => {
    if (!open) setSearch(shownLabel);
  }, [value, shownLabel, open]);

  useLayoutEffect(() => {
    if (!open || !inputRef.current) {
      setMenuPos(null);
      return;
    }
    const place = () => {
      if (!inputRef.current) return;
      const rect = inputRef.current.getBoundingClientRect();
      const maxH = 224;
      const width = Math.min(Math.max(rect.width, 280), window.innerWidth - 16);
      const spaceBelow = window.innerHeight - rect.bottom - 8;
      const openUp = spaceBelow < 160 && rect.top > spaceBelow;
      const height = Math.min(maxH, openUp ? Math.max(120, rect.top - 8) : Math.max(120, spaceBelow));
      setMenuPos({
        top: openUp ? Math.max(8, rect.top - height - 4) : rect.bottom + 4,
        left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
        width,
      });
    };
    place();
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, search, value]);

  const q = search.trim().toLowerCase();
  const filtered = items.filter((i) => {
    if (!q) return true;
    return `${i.label} ${i.sub || ''} ${i.searchText || ''}`.toLowerCase().includes(q);
  });
  const choices = filtered.slice(0, maxItems);

  return (
    <div className="relative min-w-0 flex-1">
      <input
        ref={inputRef}
        value={open ? search : shownLabel}
        onChange={(e) => {
          const next = e.target.value;
          setSearch(next);
          setOpen(true);
          onQueryChange?.(next);
          if (!next.trim()) onChange(0);
        }}
        onFocus={() => {
          setSearch('');
          setOpen(true);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={shownLabel || placeholder}
        className={inputClassName || 'min-w-0 w-full rounded border border-slate-200 bg-white px-2 py-1.5 text-sm focus:border-indigo-400 focus:outline-none'}
      />
      {open && menuPos && createPortal(
        <div
          style={{ position: 'fixed', top: menuPos.top, left: menuPos.left, width: menuPos.width, zIndex: 9999 }}
          className="max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-xl"
        >
          {choices.length > 0 ? (
            <>
              {choices.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    onChange(item.id, item);
                    setSearch(item.label);
                    setOpen(false);
                  }}
                  className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${value === item.id ? 'bg-indigo-50' : ''}`}
                >
                  <p className="text-xs font-semibold text-slate-800 sm:text-sm">{item.label}</p>
                  {item.sub ? <p className="text-[10px] text-slate-400 sm:text-[11px]">{item.sub}</p> : null}
                </button>
              ))}
              {filtered.length > choices.length && (
                <p className="sticky bottom-0 border-t border-slate-100 bg-slate-50 px-3 py-1.5 text-[10px] text-slate-500">
                  {filtered.length} ခုထဲမှ {choices.length} ခု — ပိုရှာရန် စာရိုက်ပါ
                </p>
              )}
            </>
          ) : (
            <p className="px-3 py-2.5 text-xs text-slate-400">ရှာမတွေ့ပါ</p>
          )}
        </div>,
        document.body
      )}
    </div>
  );
};

/** Compact searchable dropdown used by Supplier Payment Allocation modal. */
const PaymentSearchSelect: React.FC<{
  items: { id: number; label: string; sub?: string; searchText?: string }[];
  value: number;
  placeholder?: string;
  onChange: (id: number) => void;
}> = ({ items, value, placeholder = 'ရှာပါ...', onChange }) => (
  <PortaledCombobox
    items={items}
    value={value}
    placeholder={placeholder}
    onChange={(id) => onChange(id)}
    inputClassName="w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pl-3 pr-3 text-sm focus:border-blue-400 focus:bg-white focus:outline-none"
  />
);

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
const suggestedCost = (p?: ProductDTO | null) => Number(p?.lastPurchaseCost ?? p?.costPrice ?? 0) || 0;
const emptyLine = (): PurchaseDetailForm => ({
  productId: 0, qty: 1, unitCost: 0, subtotal: 0, warrantyMonths: 0,
  itemWarranties: [0], serialNumbers: [''], serialConditions: [''], serialPhotos: [''],
  productSearch: '', assignSerials: false, batchNumber: '', expiryDate: ''
});

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
const endOfMonthInput = (date: string) => {
  const base = date ? parseDateInput(date) : new Date();
  return dateInput(new Date(base.getFullYear(), base.getMonth() + 1, 0));
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

const getThisYearRange = () => {
  const today = new Date();
  return { from: dateInput(new Date(today.getFullYear(), 0, 1)), to: dateInput(today) };
};

interface DraftSerialLine {
  productId: number;
  productName: string;
  productCode: string;
  qty: number;
  unitCost: number;
  warrantyMonths?: number;
  serials: string[];
}

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
  const canApproveCreditOverride = (currentUser.roles || []).some((role) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role))
    || (currentUser.permissions || []).includes('CAN_ACCESS_CREDIT_OVERRIDE_APPROVE');
  const isPurchaseAdmin = (currentUser.roles || []).some((role) => ['ADMINISTRATOR', 'ROLE_ADMINISTRATOR'].includes(role));
  const hasPurchasePerm = (perm: string) => isPurchaseAdmin || (currentUser.permissions || []).includes(perm);
  const canAccessBudgets = hasPurchasePerm('CAN_ACCESS_PURCHASE_BUDGET');
  const canAccessReorder = hasPurchasePerm('CAN_ACCESS_PURCHASE_REORDER');
  const canAccessWarehouse = hasPurchasePerm('CAN_ACCESS_PURCHASE_WAREHOUSE');
  const canAccessExpiry = hasPurchasePerm('CAN_ACCESS_PURCHASE_EXPIRY');
  const canAccessAnalytics = hasPurchasePerm('CAN_ACCESS_PURCHASE_ANALYTICS');
  const canAccessImport = hasPurchasePerm('CAN_ACCESS_PURCHASE_IMPORT');
  const canAccessSupplierPayment = hasPurchasePerm('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE');
  const canCreatePurchases = hasPurchasePerm('CAN_ACCESS_PURCHASE_CREATE');
  const canUpdatePurchases = hasPurchasePerm('CAN_ACCESS_PURCHASE_UPDATE');
  const canDeletePurchases = hasPurchasePerm('CAN_ACCESS_PURCHASE_DELETE');
  const canManageBudgets = canAccessBudgets;
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
  const [creditLimitOverride, setCreditLimitOverride] = useState(false);
  const [creditOverrideReason, setCreditOverrideReason] = useState('');
  const [paidAmount, setPaidAmount] = useState<number>(0);
  const [purchasePayments, setPurchasePayments] = useState<PaymentTransactionDTO[]>([]);
  const [discountAmount, setDiscountAmount] = useState<number>(0);
  const [taxAmount, setTaxAmount] = useState<number>(0);
  const [taxMode, setTaxMode] = useState<'EXCLUSIVE' | 'INCLUSIVE'>('EXCLUSIVE');
  const [taxRate, setTaxRate] = useState<number>(0);
  const [withholdingTaxAmount, setWithholdingTaxAmount] = useState<number>(0);
  const [otherCharges, setOtherCharges] = useState<number>(0);
  const [landedCostAllocationMethod, setLandedCostAllocationMethod] = useState<'VALUE' | 'QUANTITY' | 'MANUAL'>('VALUE');
  const [warehouseName, setWarehouseName] = useState('');
  const [warehouses, setWarehouses] = useState<WarehouseDTO[]>([]);
  const [currencyCode, setCurrencyCode] = useState('MMK');
  const [exchangeRate, setExchangeRate] = useState<number>(1);
  const [attachmentName, setAttachmentName] = useState('');
  const [attachmentData, setAttachmentData] = useState('');
  const [isBarcodeOpen, setIsBarcodeOpen] = useState(false);
  const [barcodeInput, setBarcodeInput] = useState('');
  const barcodeInputRef = useRef<HTMLInputElement>(null);
  const purchaseImportRef=useRef<HTMLInputElement>(null);
  const ocrImportRef=useRef<HTMLInputElement>(null);
  const [purchaseImporting,setPurchaseImporting]=useState(false);
  const [importPreview,setImportPreview]=useState<PurchaseImportPreview|null>(null);
  const [warehousePanelOpen,setWarehousePanelOpen]=useState(false);
  const [warehouseBalances,setWarehouseBalances]=useState<WarehouseBalanceDTO[]>([]);
  const [analyticsPanelOpen,setAnalyticsPanelOpen]=useState(false);
  const [purchaseAnalytics,setPurchaseAnalytics]=useState<PurchaseAnalytics|null>(null);
  const [purchaseTimeline,setPurchaseTimeline]=useState<PurchaseTimelineEvent[]>([]);
  const [showReorderModal, setShowReorderModal] = useState(false);
  const [reorderSuggestions, setReorderSuggestions] = useState<ReorderSuggestionDTO[]>([]);
  const [reorderSearch, setReorderSearch] = useState('');
  const [reorderLoading, setReorderLoading] = useState(false);
  const [selectedReorder, setSelectedReorder] = useState<Set<number>>(new Set());
  const [showSerialModal, setShowSerialModal] = useState(false);
  const [serialTarget, setSerialTarget] = useState<{ id: number; purchaseCode: string } | null>(null);
  const [serialDraftLines, setSerialDraftLines] = useState<DraftSerialLine[]>([]);
  const [serialCameraLine, setSerialCameraLine] = useState<number | null>(null);
  const [serialEntry, setSerialEntry] = useState<Record<number, string>>({});
  const [rowActionBusy, setRowActionBusy] = useState(false);
  const [exportingExcel, setExportingExcel] = useState(false);
  const [remark, setRemark] = useState('');
  const [supplierInvoiceNo, setSupplierInvoiceNo] = useState('');
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
  const [supplierPaymentOpen, setSupplierPaymentOpen] = useState(false);
  const [supplierPaymentSupplierId, setSupplierPaymentSupplierId] = useState(0);
  const [supplierPaymentMethodId, setSupplierPaymentMethodId] = useState(0);
  const [supplierPaymentAmount, setSupplierPaymentAmount] = useState(0);
  const [supplierPaymentTxn, setSupplierPaymentTxn] = useState('');
  const [supplierPaymentRemark, setSupplierPaymentRemark] = useState('');
  const [supplierPaymentManual, setSupplierPaymentManual] = useState(false);
  const [supplierPayableSearch, setSupplierPayableSearch] = useState('');
  const [supplierPayables, setSupplierPayables] = useState<SupplierPayable[]>([]);
  const [supplierAllocations, setSupplierAllocations] = useState<Record<number, number>>({});
  const [supplierPaymentSaving, setSupplierPaymentSaving] = useState(false);
  const [supplierPaymentHistory, setSupplierPaymentHistory] = useState<SupplierPayment[]>([]);
  const [supplierPaymentHistoryLoading, setSupplierPaymentHistoryLoading] = useState(false);
  const [supplierCreditSummary, setSupplierCreditSummary] = useState({ advanceBalance: 0, returnCreditBalance: 0, availableCredit: 0 });
  const [supplierCreditTargetId, setSupplierCreditTargetId] = useState(0);
  const [supplierCreditAmount, setSupplierCreditAmount] = useState(0);
  const [supplierCreditReason, setSupplierCreditReason] = useState('');
  const [purchaseTrend, setPurchaseTrend] = useState<PurchaseTrendPoint[]>([]);
  const [topSuppliers, setTopSuppliers] = useState<TopSupplierPoint[]>([]);
  const [chartView, setChartView] = useState<'trend' | 'suppliers'>('trend');
  const [chartPeriod, setChartPeriod] = useState<'today' | 'month' | 'year' | 'all'>('today');
  const [trendLoading, setTrendLoading] = useState(false);
  const [purchaseBudgets,setPurchaseBudgets]=useState<PurchaseBudgetDTO[]>([]);
  const [budgetPanelOpen,setBudgetPanelOpen]=useState(false);
  const [budgetSaving,setBudgetSaving]=useState(false);
  const [budgetForm,setBudgetForm]=useState<PurchaseBudgetDTO>({name:'Monthly Purchase Budget',dateFrom:getThisMonthRange().from,dateTo:getThisMonthRange().to,limitAmount:0,enforcement:'BLOCK',active:true});
  const [expiringLots,setExpiringLots]=useState<StockLotDTO[]>([]);
  const [expiryPanelOpen,setExpiryPanelOpen]=useState(false);
  const [expiryDays,setExpiryDays]=useState(90);
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<PurchaseDTO | null>(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelRefundMethodId, setCancelRefundMethodId] = useState(0);
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
  
  const [details, setDetails] = useState<PurchaseDetailForm[]>([emptyLine()]);
  const [listTab, setListTab] = useState<'all' | 'overdue'>('all');
  const [overduePurchases, setOverduePurchases] = useState<PurchaseDTO[]>([]);

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

  const fetchTrend = useCallback(async (from: string, to: string) => {
    setTrendLoading(true);
    try {
      const [trend, suppliers] = await Promise.all([
        purchaseApiService.getTrend(from, to),
        purchaseApiService.getTopSuppliers(from, to),
      ]);
      setPurchaseTrend(trend);
      setTopSuppliers(suppliers);
    } catch (e) {
      console.error('Failed to load purchase trend', e);
      setPurchaseTrend([]);
      setTopSuppliers([]);
    } finally {
      setTrendLoading(false);
    }
  }, []);

  const fetchOverdue = useCallback(async () => {
    try {
      setOverduePurchases(await purchaseApiService.getOverdue());
    } catch (e) {
      console.error('Failed to load overdue payables', e);
      setOverduePurchases([]);
    }
  }, []);

  const fetchBudgets=useCallback(async()=>{if(!canAccessBudgets){setPurchaseBudgets([]);return}try{setPurchaseBudgets(await purchaseBudgetApiService.list())}catch{setPurchaseBudgets([])}},[canAccessBudgets]);
  const fetchExpiringLots=useCallback(async(days:number)=>{if(!canAccessExpiry){setExpiringLots([]);return}try{setExpiringLots(await stockLotApiService.expiring(days))}catch{setExpiringLots([])}},[canAccessExpiry]);

  const fetchMasterData = useCallback(async () => {
    try {
      const [supRes, staffRes, prodRes, payRes, whRes] = await Promise.all([
        supplierService.getAll(),
        staffService.getAll(),
        productService.getAll(),
        paymentMethodService.getAllActive(),
        warehouseApiService.list(true).catch(() => [] as WarehouseDTO[])
      ]);
      setSuppliers(supRes);
      setStaffs(staffRes);
      const linkedStaff = staffRes.find((staff) => staff.id === currentUser.staffId);
      setSelectedStaffId((previous) => previous || linkedStaff?.id || (canOverrideStaff ? staffRes[0]?.id || 0 : 0));
      if (linkedStaff) setStaffSearch(linkedStaff.name);
      setProducts(prodRes);
      setPaymentMethods(payRes);
      setWarehouses(whRes);
      setWarehouseName((prev) => {
        if (prev.trim()) return prev;
        const main = whRes.find((w) => (w.name || '').toLowerCase() === 'main' || (w.code || '').toLowerCase() === 'main');
        return main?.name || whRes[0]?.name || '';
      });
    } catch (error) {
      console.error('Error fetching data:', error);
    }
  }, []);

  const activeWarehouses = useMemo(
    () => warehouses.filter((w) => w.active !== false),
    [warehouses]
  );

  const defaultWarehouseName = useCallback(() => {
    const main = activeWarehouses.find(
      (w) => (w.name || '').toLowerCase() === 'main' || (w.code || '').toLowerCase() === 'main'
    );
    return main?.name || activeWarehouses[0]?.name || '';
  }, [activeWarehouses]);

  useEffect(() => { fetchMasterData(); fetchBudgets(); fetchExpiringLots(expiryDays); }, [fetchMasterData, fetchBudgets, fetchExpiringLots, expiryDays]);
  useRefreshOnTabActivate(fetchMasterData);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setPurchasePage(0);
      setDebouncedSearch(searchTerm.trim());
    }, 400);
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current); };
  }, [searchTerm]);

  // List filters default to Today. Chart period select updates dates only when the user changes it
  // (do not auto-sync on mount — previously default chartPeriod=month overwrote Today).

  useEffect(() => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
    fetchTrend(dateFrom, dateTo);
    fetchOverdue();
    fetchBudgets();
  }, [fetchPurchases, fetchStats, fetchTrend, fetchOverdue, fetchBudgets, purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo]);
  useDataEvents(['Purchase'], () => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
    fetchTrend(dateFrom, dateTo);
    fetchOverdue();
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
    setDetails([...details, emptyLine()]);
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
  const productComboboxItems = useMemo<ComboboxItem[]>(
    () => products.map((p) => ({
      id: p.id,
      label: getProductLabel(p),
      sub: `Stock ${Number(p.stockQty ?? p.currentStock ?? 0).toLocaleString()} · ဈေး ${money(suggestedCost(p))}`,
      searchText: `${p.name} ${p.productCode} ${p.productType ?? ''}`,
    })),
    // getProductLabel depends only on product fields already in `products`
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [products]
  );

  const handleProductSearchChange = (index: number, value: string) => {
    const newDetails = [...details];
    newDetails[index] = {
      ...newDetails[index],
      productSearch: value,
      productId: 0,
      serialNumbers: [''],
      serialConditions: [''],
      serialPhotos: [''],
      assignSerials: false,
    };
    setDetails(newDetails);
  };

  const handleProductSelect = (index: number, productId: number) => {
    const newDetails = [...details];
    const matched = products.find((p) => p.id === productId);
    if (!matched) {
      newDetails[index] = { ...newDetails[index], productId: 0, productSearch: '' };
      setDetails(newDetails);
      return;
    }
    const serialNumbers = matched.hasSerial !== false
      ? resizeSerials(newDetails[index].serialNumbers || [], newDetails[index].qty)
      : [];
    const unitCost = suggestedCost(matched) || newDetails[index].unitCost;
    newDetails[index] = {
      ...newDetails[index],
      productSearch: getProductLabel(matched),
      productId: matched.id,
      unitCost,
      subtotal: newDetails[index].qty * unitCost,
      serialNumbers,
      serialConditions: resizeStrings(newDetails[index].serialConditions || [], serialNumbers.length),
      serialPhotos: resizeStrings(newDetails[index].serialPhotos || [], serialNumbers.length),
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
  const safeTaxAmount = Math.max(0, taxAmount || 0);
  const safeWithholdingTaxAmount = Math.max(0, withholdingTaxAmount || 0);
  const safeOtherCharges = Math.max(0, otherCharges || 0);
  const grossPayable = totalAmount - safeDiscountAmount + safeOtherCharges + (taxMode === 'EXCLUSIVE' ? safeTaxAmount : 0);
  const netAmount = Math.max(0, grossPayable - safeWithholdingTaxAmount);
  const safeExchangeRate = currencyCode === 'MMK' ? 1 : Math.max(0, Number(exchangeRate) || 0);
  const foreignNetAmount = safeExchangeRate > 0 ? netAmount / safeExchangeRate : 0;
  const manualLandedTotal = details.reduce((sum, d) => sum + (Number(d.allocatedLandedCost) || 0), 0);
  const landedAllocationValid = landedCostAllocationMethod !== 'MANUAL' || Math.abs(manualLandedTotal - safeOtherCharges) < 0.01;
  const normalizedPurchasePayments = normalizePayments(purchasePayments);
  const effectivePaidAmount = normalizedPurchasePayments.length > 0 ? paymentTotal(purchasePayments) : paidAmount;
  const dueAmount = Math.max(0, netAmount - effectivePaidAmount);
  const selectedSupplier = suppliers.find((s) => s.id === selectedSupplierId);
  const supplierCurrentBalance = Math.max(0, Number(selectedSupplier?.currentBalance) || 0);
  const supplierCreditLimit = Math.max(0, Number(selectedSupplier?.creditLimit) || 0);
  const projectedSupplierBalance = supplierCurrentBalance + dueAmount;
  const creditUsagePercent = supplierCreditLimit > 0 ? Math.min(100, (projectedSupplierBalance / supplierCreditLimit) * 100) : 0;
  const supplierLimitExceeded = dueAmount > 0 && supplierCreditLimit > 0 && projectedSupplierBalance > supplierCreditLimit;
  const supplierLimitNear = dueAmount > 0 && supplierCreditLimit > 0 && !supplierLimitExceeded && projectedSupplierBalance >= supplierCreditLimit * 0.8;
  const creditOverrideValid = !supplierLimitExceeded || (canApproveCreditOverride && creditLimitOverride && !!creditOverrideReason.trim());
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
    && safeWithholdingTaxAmount <= grossPayable
    && landedAllocationValid
    && currencyCode.length === 3
    && safeExchangeRate > 0
    && effectivePaidAmount <= netAmount
    && !hasDuplicateSerials
    && !!purchaseDate
    && (dueAmount <= 0 || !!dueDate)
    && creditOverrideValid
    && (effectivePaidAmount <= 0 || selectedPaymentMethodId > 0 || normalizedPurchasePayments.length > 0);

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
    creditLimitOverride: !status && supplierLimitExceeded ? creditLimitOverride : false,
    creditOverrideReason: !status && supplierLimitExceeded && creditLimitOverride ? creditOverrideReason.trim() : undefined,
    totalAmount,
    discountAmount: safeDiscountAmount,
    taxAmount: safeTaxAmount,
    taxMode,
    taxRate,
    withholdingTaxAmount: safeWithholdingTaxAmount,
    otherCharges: safeOtherCharges,
    landedCostAllocationMethod,
    warehouseName: warehouseName.trim() || undefined,
    currencyCode: currencyCode.trim().toUpperCase() || 'MMK',
    exchangeRate: safeExchangeRate,
    foreignNetAmount,
    netAmount,
    paidAmount: status ? 0 : effectivePaidAmount,
    dueAmount: status ? netAmount : dueAmount,
    status,
    remark,
    supplierInvoiceNo: supplierInvoiceNo.trim() || undefined,
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
      allocatedLandedCost: landedCostAllocationMethod === 'MANUAL' ? (Number(d.allocatedLandedCost) || 0) : undefined,
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
        : [],
      batchNumber: d.batchNumber?.trim() || undefined,
      expiryDate: d.expiryDate || undefined
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
    setCreditLimitOverride(false);
    setCreditOverrideReason('');
    setPaidAmount(0);
    setPurchasePayments([]);
    setDiscountAmount(0);
    setTaxAmount(0);
    setTaxMode('EXCLUSIVE');
    setTaxRate(0);
    setWithholdingTaxAmount(0);
    setOtherCharges(0);
    setLandedCostAllocationMethod('VALUE');
    setWarehouseName(defaultWarehouseName());
    setCurrencyCode('MMK');
    setExchangeRate(1);
    setAttachmentName('');
    setAttachmentData('');
    setRemark('');
    setSupplierInvoiceNo('');
    setSelectedPaymentMethodId(0);
    setTransactionNo('');
    setDetails([emptyLine()]);
  };

  const refreshLists = () => {
    fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo);
    fetchStats(dateFrom, dateTo);
    fetchTrend(dateFrom, dateTo);
    fetchOverdue();
  };

  const confirmBudgetWarnings = async (payload: PurchaseDTO) => {
    try {
      const result: PurchaseBudgetCheck = await purchaseApiService.checkBudget(payload);
      const blocks = result.blocks ?? [];
      const warnings = result.warnings ?? [];
      if (result.blocked || blocks.length) {
        await Swal.fire({
          icon: 'error',
          title: 'ဘတ်ဂျက် ကျော်နေပါသည်',
          text: blocks.join('\n') || 'Purchase exceeds a blocking budget.'
        });
        return false;
      }
      if (!warnings.length) return true;
      const confirm = await Swal.fire({
        icon: 'warning',
        title: 'Budget warning',
        text: warnings.join('\n'),
        showCancelButton: true,
        confirmButtonText: 'Continue anyway',
        cancelButtonText: 'Cancel'
      });
      return confirm.isConfirmed;
    } catch (error: any) {
      await Swal.fire({
        icon: 'error',
        title: 'ဘတ်ဂျက် စစ်ဆေးမရပါ',
        text: error.message || 'Could not check budget.'
      });
      return false;
    }
  };

  const handleSave = async () => {
    if (!isValid || saving || !canCreatePurchases) return;
    setSaving(true);
    try {
      const payload = buildPayload();
      if (!(await confirmBudgetWarnings(payload))) return;
      const res = await purchaseApiService.create(payload);
      if (res) {
        Swal.fire({
          icon: res.budgetWarnings?.length ? 'warning' : 'success',
          title: 'Success',
          text: res.budgetWarnings?.length ? res.budgetWarnings.join('\n') : 'Purchase recorded successfully',
          timer: res.budgetWarnings?.length ? 4000 : 2000,
          showConfirmButton: !!res.budgetWarnings?.length
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
    if (saving || !canCreatePurchases) return;
    setSaving(true);
    try {
      const draftValid = selectedSupplierId > 0 && selectedStaffId > 0
        && details.some((d) => d.productId > 0 && d.qty > 0 && d.unitCost >= 0);
      if (!draftValid) {
        Swal.fire({ icon: 'warning', title: 'မူကြမ်းသိမ်းဆည်းရန်', text: 'ပေးသွင်းသူ၊ ဝယ်ယူသူနှင့် ပစ္စည်းအနည်းဆုံးတစ်ခု ရွေးချယ်ပါ။' });
        return;
      }
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
        qty: 1, unitCost: suggestedCost(product),
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
    if (!canAccessReorder) {
      Swal.fire({ icon: 'warning', title: 'ခွင့်ပြုချက် မရှိပါ', text: 'Reorder အသုံးပြုရန် CAN_ACCESS_PURCHASE_REORDER လိုအပ်သည်။' });
      return;
    }
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
        unitCost: Number(r.lastCost ?? 0) || suggestedCost(products.find((p) => p.id === r.productId)),
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

  const doConfirmDraft = async (id: number, details?: Array<{ productId: number; qty: number; unitCost: number; warrantyMonths?: number; serialNumbers: string[]; serialConditions: string[]; serialPhotos: string[] }>) => {
    setRowActionBusy(true);
    try {
      const full = await purchaseApiService.getById(id);
      const payload = (details ? { ...full, details } : full) as PurchaseDTO;
      if (!(await confirmBudgetWarnings(payload))) return;
      const confirmed = await purchaseApiService.confirmDraft(id, details ? ({ details } as Partial<PurchaseDTO>) : undefined);
      Swal.fire({ icon: confirmed.budgetWarnings?.length ? 'warning' : 'success', title: 'အတည်ပြုပြီး', text: confirmed.budgetWarnings?.length ? confirmed.budgetWarnings.join('\n') : 'Stock၊ Serial၊ Journal များ ဖန်တီးပြီးပါပြီ', timer: confirmed.budgetWarnings?.length ? 4000 : 1800, showConfirmButton: !!confirmed.budgetWarnings?.length });
      refreshLists();
      setShowSerialModal(false);
      setSerialTarget(null);
      if (viewPurchase?.id === id) closeView();
    } catch (error: any) {
      Swal.fire({ icon: 'error', title: 'အတည်ပြုမရပါ', text: error.message || 'Failed to confirm draft' });
    } finally {
      setRowActionBusy(false);
    }
  };

  const handleConfirmDraft = async (p: PurchaseDTO) => {
    if (rowActionBusy || !canUpdatePurchases) return;
    setRowActionBusy(true);
    let full: PurchaseDTO;
    try {
      full = await purchaseApiService.getById(p.id!);
    } catch (error: any) {
      setRowActionBusy(false);
      Swal.fire({ icon: 'error', title: 'Error', text: error.message || 'Failed to load draft' });
      return;
    }

    const code = full.purchaseCode || `#${p.id}`;
    const draftLines: DraftSerialLine[] = (full.details || [])
      .filter((d) => products.find((pr) => pr.id === d.productId)?.hasSerial)
      .map((d) => {
        const prod = products.find((pr) => pr.id === d.productId)!;
        return {
          productId: d.productId,
          productName: d.productName || prod.name || `Product #${d.productId}`,
          productCode: prod.productCode || '',
          qty: Number(d.qty) || 0,
          unitCost: Number(d.unitCost) || 0,
          warrantyMonths: d.warrantyMonths ?? prod.warrantyMonths ?? 0,
          serials: Array.from({ length: Number(d.qty) || 0 }, () => '')
        };
      });

    if (draftLines.length > 0) {
      setRowActionBusy(false);
      setSerialTarget({ id: p.id!, purchaseCode: code });
      setSerialDraftLines(draftLines);
      setSerialEntry({});
      setShowSerialModal(true);
      return;
    }

    const result = await Swal.fire({
      icon: 'question',
      title: 'မူကြမ်း အတည်ပြုမည်လား?',
      html: `<b>${code}</b> ကို အတည်ပြုပြီး Stock နှင့် Journal မှတ်တမ်းများ ဖန်တီးပါမည်။<br/><span style="font-size:12px;color:#94a3b8">Serial ပစ္စည်းများအတွက် နောက်တစ်ဆင့်တွင် Serial Number များ ထည့်နိုင်ပါသည်။</span>`,
      showCancelButton: true,
      confirmButtonText: 'အတည်ပြု',
      cancelButtonText: 'မလုပ်တော့'
    });
    if (!result.isConfirmed) {
      setRowActionBusy(false);
      return;
    }
    await doConfirmDraft(p.id!);
  };

  const serialLineIssue = (line: DraftSerialLine): string | null => {
    const filled = line.serials.map((s) => s.trim());
    if (filled.some((s) => !s)) return `${line.qty} ခုလုံး ထည့်ရန် လိုအပ်သေးသည်`;
    if (new Set(filled).size !== filled.length) return 'ဤပစ္စည်းထဲ Duplicate serial ပါနေသည်';
    return null;
  };

  const globalDupSerial = (): string | null => {
    const seen = new Set<string>();
    for (const line of serialDraftLines) {
      for (const s of line.serials) {
        const t = s.trim();
        if (!t) continue;
        if (seen.has(t)) return t;
        seen.add(t);
      }
    }
    return null;
  };

  const serialModalReady = serialDraftLines.length > 0
    && serialDraftLines.every((l) => serialLineIssue(l) === null)
    && globalDupSerial() === null;

  const appendSerialToLine = (lineIdx: number, raw: string) => {
    const code = raw.trim();
    if (!code) return;
    const line = serialDraftLines[lineIdx];
    if (!line) return;
    if (line.serials.includes(code)) {
      Swal.fire({ icon: 'warning', title: 'Duplicate', text: `'${code}' က ဤပစ္စည်းထဲ ရှိပြီးသားဖြစ်သည်`, timer: 1400, showConfirmButton: false });
      return;
    }
    if (serialDraftLines.some((l, li) => li !== lineIdx && l.serials.some((s) => s.trim() === code))) {
      Swal.fire({ icon: 'warning', title: 'Duplicate', text: `'${code}' က အခြားပစ္စည်းတွင် ထည့်ထားပြီးဖြစ်သည်`, timer: 1600, showConfirmButton: false });
      return;
    }
    const emptyIdx = line.serials.findIndex((s) => !s.trim());
    if (emptyIdx === -1) {
      Swal.fire({ icon: 'info', title: 'ပြည့်ပြီး', text: `'${code}' — ${line.productName} အတွက် ${line.qty} ခု ပြည့်နေပြီး`, timer: 1500, showConfirmButton: false });
      return;
    }
    setSerialDraftLines((prev) => prev.map((l, li) => li === lineIdx ? { ...l, serials: l.serials.map((s, si) => si === emptyIdx ? code : s) } : l));
  };

  const removeSerialFromLine = (lineIdx: number, slotIdx: number) => {
    setSerialDraftLines((prev) => prev.map((l, li) => li === lineIdx ? { ...l, serials: l.serials.map((s, si) => si === slotIdx ? '' : s) } : l));
  };

  const submitEntry = (lineIdx: number) => {
    appendSerialToLine(lineIdx, serialEntry[lineIdx] ?? '');
    setSerialEntry((prev) => ({ ...prev, [lineIdx]: '' }));
  };

  const handleSerialScanDetected = (code: string) => {
    if (serialCameraLine == null) return;
    appendSerialToLine(serialCameraLine, code);
  };

  const handleCancelPurchase = async (p: PurchaseDTO) => {
    if (rowActionBusy || !canDeletePurchases) return;
    const isDraft = (p.status || '').toUpperCase() === 'DRAFT';
    if (isDraft) {
      const result = await Swal.fire({
        icon: 'warning',
        title: 'မူကြမ်း ပယ်ဖျက်မည်လား?',
        html: `<b>${p.purchaseCode || `#${p.id}`}</b> ကို ပယ်ဖျက်ထားကြောင်း မှတ်တမ်းတင်မည်။`,
        showCancelButton: true,
        confirmButtonText: 'ပယ်ဖျက်',
        cancelButtonText: 'မလုပ်တော့',
        confirmButtonColor: '#dc2626',
        input: 'textarea',
        inputLabel: 'ပယ်ဖျက်ရသည့်အကြောင်းရင်း',
        inputPlaceholder: 'အကြောင်းရင်းကို မဖြစ်မနေ ရေးပါ',
        inputAttributes: { maxlength: '1000' },
        inputValidator: (value) => !value?.trim() ? 'ပယ်ဖျက်ရသည့်အကြောင်းရင်း ထည့်ပါ' : undefined
      });
      if (!result.isConfirmed) return;
      setRowActionBusy(true);
      try {
        await purchaseApiService.cancel(p.id!, String(result.value).trim());
        Swal.fire({ icon: 'success', title: 'မူကြမ်း ဖျက်ပြီး', timer: 1500, showConfirmButton: false });
        refreshLists();
        if (viewPurchase?.id === p.id) closeView();
      } catch (error: any) {
        Swal.fire({ icon: 'error', title: 'Cannot cancel', text: error.message || 'Failed to cancel purchase' });
      } finally {
        setRowActionBusy(false);
      }
      return;
    }

    setCancelTarget(p);
    setCancelReason('');
    setCancelRefundMethodId(0);
  };

  const closeCancelModal = () => {
    if (rowActionBusy) return;
    setCancelTarget(null);
    setCancelReason('');
    setCancelRefundMethodId(0);
  };

  const submitCancelPurchase = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cancelTarget?.id || rowActionBusy) return;
    const reason = cancelReason.trim();
    if (!reason) {
      Swal.fire('စစ်ဆေးရန်', 'ပယ်ဖျက်ရသည့်အကြောင်းရင်း ထည့်ပါ', 'warning');
      return;
    }
    const paid = Number(cancelTarget.paidAmount) || 0;
    if (paid > 0 && cancelRefundMethodId <= 0) {
      Swal.fire('စစ်ဆေးရန်', 'ငွေပြန်ဝင်မည့် payment method ရွေးပါ', 'warning');
      return;
    }
    setRowActionBusy(true);
    try {
      await purchaseApiService.cancel(cancelTarget.id, reason, paid > 0 ? cancelRefundMethodId : undefined);
      setCancelTarget(null);
      setCancelReason('');
      setCancelRefundMethodId(0);
      Swal.fire({ icon: 'success', title: 'ပယ်ဖျက်ပြီး', timer: 1500, showConfirmButton: false });
      refreshLists();
      if (viewPurchase?.id === cancelTarget.id) closeView();
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

  const applyImportPreview=()=>{
    if(!importPreview)return;
    const valid=importPreview.rows.filter(r=>r.valid&&r.productId&&r.qty&&r.unitCost);
    if(valid.length===0){Swal.fire({icon:'error',title:'No valid rows',text:'Check the spreadsheet columns.'});return}
    setDetails(valid.map(r=>({productId:r.productId!,qty:r.qty!,unitCost:Number(r.unitCost),subtotal:Number(r.subtotal)||r.qty!*Number(r.unitCost),warrantyMonths:0,itemWarranties:Array.from({length:r.qty!},()=>0),serialNumbers:Array.from({length:r.qty!},()=>''),serialConditions:Array.from({length:r.qty!},()=>''),serialPhotos:Array.from({length:r.qty!},()=>''),productSearch:`${r.productName||r.productCode} (${r.productCode})`,assignSerials:false,batchNumber:r.batchNumber||'',expiryDate:r.expiryDate||''})));
    setImportPreview(null);
    Swal.fire({icon:'success',title:`${valid.length} rows imported`,text:valid.some(r=>r.serialRequired)?'Serial products require serial numbers before confirmation.':'Review and save the voucher.',timer:2200,showConfirmButton:false});
  };
  const handlePurchaseImport=async(file:File|null)=>{
    if(!file)return;
    if(file.size>5*1024*1024){Swal.fire({icon:'warning',title:'File ကြီးလွန်းသည်',text:'Excel import အတွက် 5MB အောက် ဖိုင်သာ ရွေးပါ။'});return}
    setPurchaseImporting(true);
    try{
      setImportPreview(await purchaseApiService.previewImport(file));
    }catch(e:any){Swal.fire('Import failed',e.message||'Unable to read spreadsheet','error')}finally{setPurchaseImporting(false);if(purchaseImportRef.current)purchaseImportRef.current.value=''}
  };

  const matchProductByHint = (hint?: string): ProductDTO | undefined => {
    const q = (hint || '').trim().toLowerCase();
    if (!q) return undefined;
    const exact = products.find((p) => (p.name || '').toLowerCase() === q || (p.productCode || '').toLowerCase() === q);
    if (exact) return exact;
    return products.find((p) => (p.name || '').toLowerCase().includes(q) || (p.productCode || '').toLowerCase().includes(q));
  };

  const handleOcrImport = async (file: File | null) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      Swal.fire({ icon: 'warning', title: 'File ကြီးလွန်းသည်', text: 'OCR import အတွက် 5MB အောက် ဖိုင်သာ ရွေးပါ။' });
      return;
    }
    setPurchaseImporting(true);
    try {
      const preview = await purchaseApiService.ocrPreview(file);
      const lines = preview.lines || [];
      if (lines.length === 0) {
        Swal.fire({
          icon: 'info',
          title: 'OCR preview',
          text: preview.note || 'No invoice lines detected.'
        });
        return;
      }
      const mapped: PurchaseDetailForm[] = [];
      let unmatched = 0;
      for (const line of lines) {
        const product = matchProductByHint(line.productHint);
        if (!product?.id) {
          unmatched += 1;
          continue;
        }
        const qty = Math.max(1, Number(line.qty) || 1);
        const unitCost = Math.max(0, Number(line.unitCost) || suggestedCost(product));
        mapped.push({
          productId: product.id,
          qty,
          unitCost,
          subtotal: qty * unitCost,
          warrantyMonths: 0,
          itemWarranties: Array.from({ length: qty }, () => 0),
          serialNumbers: Array.from({ length: qty }, () => ''),
          serialConditions: Array.from({ length: qty }, () => ''),
          serialPhotos: Array.from({ length: qty }, () => ''),
          productSearch: `${product.name} (${product.productCode})`,
          assignSerials: false,
          batchNumber: '',
          expiryDate: ''
        });
      }
      if (mapped.length > 0) setDetails(mapped);
      if (preview.supplierInvoiceNo) setSupplierInvoiceNo(preview.supplierInvoiceNo);
      if (preview.suggestedTax != null && Number(preview.suggestedTax) > 0) setTaxAmount(Number(preview.suggestedTax));
      Swal.fire({
        icon: mapped.length > 0 ? 'success' : 'warning',
        title: mapped.length > 0 ? `OCR: ${mapped.length} line(s) filled` : 'OCR: no product match',
        text: [
          preview.note,
          unmatched > 0 ? `${unmatched} line(s) had no matching product (soft-fail).` : undefined
        ].filter(Boolean).join(' ') || 'Review the voucher before saving.'
      });
    } catch (e: any) {
      Swal.fire('OCR failed', e.message || 'Unable to preview invoice', 'error');
    } finally {
      setPurchaseImporting(false);
      if (ocrImportRef.current) ocrImportRef.current.value = '';
    }
  };

  const loadSupplierPayables = async (supplierId: number) => {
    setSupplierPaymentSupplierId(supplierId);
    setSupplierAllocations({});
    setSupplierPayableSearch('');
    setSupplierCreditTargetId(0); setSupplierCreditAmount(0);
    if (supplierId > 0) {
      setSupplierPaymentHistoryLoading(true);
      try {
        const [payables, credit, history] = await Promise.all([
          supplierPaymentApiService.payables(supplierId),
          supplierPaymentApiService.creditSummary(supplierId),
          supplierPaymentApiService.history(supplierId)
        ]);
        setSupplierPayables(payables);
        setSupplierCreditSummary(credit);
        setSupplierPaymentHistory(history);
      } finally {
        setSupplierPaymentHistoryLoading(false);
      }
    } else {
      setSupplierPayables([]);
      setSupplierCreditSummary({ advanceBalance: 0, returnCreditBalance: 0, availableCredit: 0 });
      setSupplierPaymentHistory([]);
    }
  };

  const saveSupplierPayment = async () => {
    if (supplierPaymentSaving || supplierPaymentSupplierId <= 0 || supplierPaymentMethodId <= 0 || supplierPaymentAmount <= 0) return;
    const manualAllocations = Object.entries(supplierAllocations)
      .map(([purchaseId, amount]) => ({ purchaseId: Number(purchaseId), amount: Number(amount) || 0 }))
      .filter((item) => item.amount > 0);
    if (supplierPaymentManual && manualAllocations.reduce((sum, item) => sum + item.amount, 0) > supplierPaymentAmount) {
      Swal.fire({ icon: 'warning', title: 'Allocation exceeds payment' }); return;
    }
    setSupplierPaymentSaving(true);
    try {
      const result = await supplierPaymentApiService.create({
        supplierId: supplierPaymentSupplierId,
        staffId: Number(currentUser.staffId) || selectedStaffId,
        paymentMethodId: supplierPaymentMethodId,
        amount: supplierPaymentAmount,
        transactionNo: supplierPaymentTxn.trim() || undefined,
        remark: supplierPaymentRemark.trim() || undefined,
        allocations: supplierPaymentManual ? manualAllocations : undefined
      });
      const allocationLines = (result.allocations || [])
        .map((a) => `${a.purchaseCode}: ${money(a.amount)}`)
        .join('<br/>');
      Swal.fire({
        icon: 'success',
        title: 'Supplier payment saved',
        html: `<b>${result.paymentNo}</b><br/>Allocated: ${money(result.allocatedAmount)}<br/>Advance: ${money(result.advanceAmount)}${allocationLines ? `<br/><br/><b>Voucher allocations</b><br/>${allocationLines}` : ''}`
      });
      setSupplierPaymentAmount(0); setSupplierPaymentTxn(''); setSupplierPaymentRemark('');
      setSupplierAllocations({});
      await loadSupplierPayables(supplierPaymentSupplierId);
      refreshLists(); await fetchMasterData();
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Supplier payment failed', text: e.message || 'Unable to save payment' });
    } finally { setSupplierPaymentSaving(false); }
  };

  const applySupplierCredit = async () => {
    if (supplierPaymentSaving || supplierPaymentSupplierId <= 0 || supplierCreditTargetId <= 0 || supplierCreditAmount <= 0) return;
    setSupplierPaymentSaving(true);
    try {
      const result = await supplierPaymentApiService.applyCredit({
        supplierId: supplierPaymentSupplierId, purchaseId: supplierCreditTargetId,
        staffId: Number(currentUser.staffId) || selectedStaffId, amount: supplierCreditAmount,
        reason: supplierCreditReason.trim() || undefined
      });
      Swal.fire({ icon: 'success', title: 'Supplier credit applied',
        html: `<b>${result.applicationNo}</b><br/>Applied: ${money(result.amount)}<br/>Remaining due: ${money(result.remainingDue)}` });
      await loadSupplierPayables(supplierPaymentSupplierId);
      refreshLists(); await fetchMasterData();
      setSupplierCreditReason('');
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Credit application failed', text: e.message || 'Unable to apply credit' });
    } finally { setSupplierPaymentSaving(false); }
  };

  const voidSupplierPayment = async (payment: SupplierPayment) => {
    if (!payment.id || payment.voided) return;
    const result = await Swal.fire({
      icon: 'warning',
      title: 'Void supplier payment?',
      html: `<b>${payment.paymentNo}</b><br/>Allocated: ${money(payment.allocatedAmount)}`,
      input: 'textarea',
      inputLabel: 'Void reason',
      inputPlaceholder: 'Reason is required',
      showCancelButton: true,
      confirmButtonText: 'Void',
      confirmButtonColor: '#dc2626',
      inputValidator: (value) => value?.trim() ? undefined : 'Void reason is required'
    });
    if (!result.isConfirmed) return;
    setSupplierPaymentSaving(true);
    try {
      await supplierPaymentApiService.voidPayment(payment.id, {
        reason: String(result.value).trim(),
        staffId: Number(currentUser.staffId) || selectedStaffId
      });
      Swal.fire({ icon: 'success', title: 'Payment voided', timer: 1400, showConfirmButton: false });
      await loadSupplierPayables(supplierPaymentSupplierId);
      refreshLists();
      await fetchMasterData();
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: 'Void failed', text: e.message || 'Unable to void payment' });
    } finally {
      setSupplierPaymentSaving(false);
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
      const [purchaseReturns, payments, stockHistories, timeline] = await Promise.all([
        purchaseReturnApiService.getByPurchaseId(id),
        accountingApiService.getTransactionsByRef(id, 'Purchase'),
        Promise.all(productIds.map((productId) => productService.getStockHistory(productId, { size: 100 }))),
        purchaseApiService.getTimeline(id).catch(() => [])
      ]);
      setViewPurchase(purchase);
      setRelatedReturns(purchaseReturns || []);
      setPurchaseHistoryPayments(payments || purchase.payments || []);
      setPurchaseStockMovements(stockHistories.flatMap((history) => history.movements || []).filter((movement) => movement.referenceId === id));
      setPurchaseTimeline(timeline);
    } catch (e) {
      Swal.fire('Error', 'Failed to load purchase', 'error');
    } finally {
      setRelatedReturnsLoading(false);
    }
  }, []);

  const copyPurchaseToNewVoucher = async (id: number) => {
    try {
      const source = await purchaseApiService.getById(id);
      resetFormFields();
      setSelectedSupplierId(source.supplierId);
      setSelectedStaffId(source.staffId);
      setPurchaseDate(dateInput(new Date()));
      setPaymentTermDays(Math.max(0, Number(source.paymentTermDays) || 30));
      setDueDate(addDaysInput(dateInput(new Date()), Math.max(0, Number(source.paymentTermDays) || 30)));
      setDiscountAmount(Number(source.discountAmount) || 0);
      setTaxAmount(Number(source.taxAmount) || 0);
      setTaxMode(source.taxMode || 'EXCLUSIVE');
      setTaxRate(Number(source.taxRate) || 0);
      setWithholdingTaxAmount(Number(source.withholdingTaxAmount) || 0);
      setOtherCharges(Number(source.otherCharges) || 0);
      setLandedCostAllocationMethod(source.landedCostAllocationMethod || 'VALUE');
      setWarehouseName(source.warehouseName || '');
      setCurrencyCode(source.currencyCode || 'MMK');
      setExchangeRate(Number(source.exchangeRate) || 1);
      setRemark(source.remark ? `Copied from ${source.purchaseCode || source.id}: ${source.remark}` : `Copied from ${source.purchaseCode || source.id}`);
      setDetails((source.details || []).map((d) => ({
        productId: d.productId,
        qty: d.qty,
        unitCost: Number(d.unitCost) || 0,
        subtotal: d.qty * (Number(d.unitCost) || 0),
        allocatedLandedCost: Number(d.allocatedLandedCost) || 0,
        warrantyMonths: Number(d.warrantyMonths) || 0,
        itemWarranties: d.itemWarranties?.length ? [...d.itemWarranties] : Array.from({length:d.qty},()=>Number(d.warrantyMonths)||0),
        serialNumbers: Array.from({length:d.qty},()=> ''),
        serialConditions: Array.from({length:d.qty},()=> ''),
        serialPhotos: Array.from({length:d.qty},()=> ''),
        productSearch: d.productName || '',
        assignSerials: false,
        batchNumber: d.batchNumber || '',
        expiryDate: d.expiryDate || ''
      })));
      setShowNewVoucherForm(true);
      window.scrollTo({ top: 0, behavior: 'smooth' });
      Swal.fire({icon:'success',title:'Voucher copied',text:'Invoice number, attachment, payment and serials were cleared for safety.',timer:2200,showConfirmButton:false});
    } catch (e:any) {
      Swal.fire({icon:'error',title:'Copy failed',text:e.message || 'Unable to load purchase'});
    }
  };

  const closeView = () => {
    setViewPurchase(null);
    setRelatedReturns([]);
    setPurchaseHistoryPayments([]);
    setPurchaseStockMovements([]);
    setPurchaseTimeline([]);
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
      purchase.poCode ? `PO      : ${purchase.poCode}` : '',
      purchase.supplierInvoiceNo ? `Inv No  : ${purchase.supplierInvoiceNo}` : '',
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
      if (d.batchNumber) lines.push(`   Batch: ${d.batchNumber}`);
      if (d.expiryDate) lines.push(`   Expiry: ${d.expiryDate}`);
    });
    lines.push(`------------------------`);
    lines.push(`Subtotal : ${fmt(purchase.totalAmount)}`);
    if ((purchase.discountAmount || 0) > 0) lines.push(`Discount : - ${fmt(purchase.discountAmount || 0)}`);
    if ((purchase.taxAmount || 0) > 0) lines.push(`Tax / VAT: ${fmt(purchase.taxAmount || 0)}`);
    if ((purchase.otherCharges || 0) > 0) lines.push(`Other    : ${fmt(purchase.otherCharges || 0)}`);
    lines.push(`Net      : ${fmt(purchase.netAmount ?? Math.max(0, (purchase.totalAmount || 0) - (purchase.discountAmount || 0) + (purchase.taxAmount || 0) + (purchase.otherCharges || 0)))}`);
    lines.push(`Paid     : ${fmt(purchase.paidAmount)}`);
    const dueText = Math.max(0, Number(purchase.dueAmount ?? ((purchase.netAmount || purchase.totalAmount || 0) - (purchase.paidAmount || 0))));
    if (dueText > 0) lines.push(`Due      : ${fmt(dueText)}`);
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
    setCreditLimitOverride(false);
    setCreditOverrideReason('');
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
  const budgetCategories=useMemo(()=>Array.from(new Map(products.filter(p=>p.categoryId).map(p=>[p.categoryId!,{id:p.categoryId!,name:p.categoryName||`Category ${p.categoryId}`}])).values()),[products]);
  const saveBudget=async()=>{if(!canManageBudgets){Swal.fire('ခွင့်ပြုချက် မရှိပါ','Purchase Budget စီမံရန် CAN_ACCESS_PURCHASE_BUDGET လိုအပ်သည်။','warning');return}if(!budgetForm.name.trim()||!budgetForm.dateFrom||!budgetForm.dateTo||budgetForm.limitAmount<=0){Swal.fire('Required','Name, valid dates and limit amount are required.','warning');return}setBudgetSaving(true);try{await purchaseBudgetApiService.save(budgetForm);await fetchBudgets();setBudgetForm({name:'Monthly Purchase Budget',dateFrom:getThisMonthRange().from,dateTo:getThisMonthRange().to,limitAmount:0,enforcement:'BLOCK',active:true,categoryId:undefined,supplierId:undefined});Swal.fire({icon:'success',title:'Purchase budget saved',timer:1400,showConfirmButton:false})}catch(e:any){Swal.fire('Budget failed',e.message||'Unable to save budget','error')}finally{setBudgetSaving(false)}};
  const toggleBudget=async(b:PurchaseBudgetDTO)=>{if(!canManageBudgets||!b.id)return;try{await purchaseBudgetApiService.active(b.id,!b.active);await fetchBudgets()}catch(e:any){Swal.fire('Update failed',e.message||'Unable to update budget','error')}};
  const editBudget=(b:PurchaseBudgetDTO)=>setBudgetForm({...b});
  const deleteBudget=async(b:PurchaseBudgetDTO)=>{if(!canManageBudgets||!b.id)return;const ok=await Swal.fire({icon:'warning',title:'Delete budget?',text:b.name,showCancelButton:true,confirmButtonText:'Delete'});if(!ok.isConfirmed)return;try{await purchaseBudgetApiService.remove(b.id);await fetchBudgets()}catch(e:any){Swal.fire('Delete failed',e.message||'Unable to delete budget','error')}};
  const openWarehousePanel=async()=>{if(!canAccessWarehouse){Swal.fire('ခွင့်ပြုချက် မရှိပါ','Warehouse ကြည့်ရန် CAN_ACCESS_PURCHASE_WAREHOUSE လိုအပ်သည်။','warning');return}setWarehousePanelOpen(v=>!v);if(!warehousePanelOpen){try{setWarehouseBalances(await stockLotApiService.warehouseBalances())}catch{setWarehouseBalances([])}}};
  const openAnalyticsPanel=async()=>{if(!canAccessAnalytics){Swal.fire('ခွင့်ပြုချက် မရှိပါ','Analytics ကြည့်ရန် CAN_ACCESS_PURCHASE_ANALYTICS လိုအပ်သည်။','warning');return}setAnalyticsPanelOpen(v=>!v);if(!analyticsPanelOpen){try{setPurchaseAnalytics(await purchaseApiService.getAnalytics(dateFrom,dateTo))}catch{setPurchaseAnalytics(null)}}};

  const filteredPurchases = (listTab === 'overdue' ? overduePurchases : purchases).filter((p) => {
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
      setChartPeriod('today');
    } else if (shortcut === 'WEEK') {
      const range = getThisWeekRange();
      setDateFrom(range.from);
      setDateTo(range.to);
    } else if (shortcut === 'MONTH') {
      const range = getThisMonthRange();
      setDateFrom(range.from);
      setDateTo(range.to);
      setChartPeriod('month');
    } else {
      setDateFrom('');
      setDateTo('');
      setChartPeriod('all');
    }
  };

  return (
    <div className="w-full max-w-none space-y-3">
      {!showNewVoucherForm ? (
        <>

          {budgetPanelOpen&&canAccessBudgets&&<section className="rounded-xl border border-violet-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between"><div><h3 className="text-sm font-black text-slate-800">Purchase Budgets</h3><p className="text-[10px] text-slate-500">Overall or product-category spending control before stock is received.</p></div><button onClick={()=>setBudgetPanelOpen(false)} className="p-1 text-slate-400"><X size={16}/></button></div>
            {canManageBudgets&&<div className="mb-4 grid gap-2 rounded-xl bg-violet-50 p-3 sm:grid-cols-2 lg:grid-cols-5">
              <input value={budgetForm.name} onChange={e=>setBudgetForm(v=>({...v,name:e.target.value}))} placeholder="Budget name" className="rounded-lg border border-violet-200 px-2 py-2 text-xs"/>
              <div className="flex gap-1"><input type="date" value={budgetForm.dateFrom} onChange={e=>setBudgetForm(v=>({...v,dateFrom:e.target.value}))} className="min-w-0 flex-1 rounded-lg border border-violet-200 px-2 text-xs"/><input type="date" value={budgetForm.dateTo} onChange={e=>setBudgetForm(v=>({...v,dateTo:e.target.value}))} className="min-w-0 flex-1 rounded-lg border border-violet-200 px-2 text-xs"/></div>
              <select value={budgetForm.categoryId||''} onChange={e=>setBudgetForm(v=>({...v,categoryId:e.target.value?Number(e.target.value):undefined}))} className="rounded-lg border border-violet-200 px-2 py-2 text-xs"><option value="">All Categories</option>{budgetCategories.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select>
              <select value={budgetForm.supplierId||''} onChange={e=>setBudgetForm(v=>({...v,supplierId:e.target.value?Number(e.target.value):undefined}))} className="rounded-lg border border-violet-200 px-2 py-2 text-xs"><option value="">All Suppliers</option>{suppliers.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select>
              <div className="flex gap-1"><input type="number" min="0" value={budgetForm.limitAmount||''} onChange={e=>setBudgetForm(v=>({...v,limitAmount:Math.max(0,Number(e.target.value)||0)}))} placeholder="Limit" className="min-w-0 flex-1 rounded-lg border border-violet-200 px-2 text-xs"/><select value={budgetForm.enforcement} onChange={e=>setBudgetForm(v=>({...v,enforcement:e.target.value as 'WARN'|'BLOCK'}))} className="rounded-lg border border-violet-200 px-1 text-xs"><option value="BLOCK">Block</option><option value="WARN">Warn</option></select><button disabled={budgetSaving} onClick={()=>void saveBudget()} className="rounded-lg bg-violet-600 px-3 text-xs font-bold text-white disabled:opacity-50">Save</button></div>
            </div>}
            <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-3">{purchaseBudgets.map(b=><div key={b.id} className={`rounded-lg border p-3 ${b.active?'border-violet-200':'border-slate-200 opacity-60'}`}><div className="flex justify-between gap-2"><div><p className="text-xs font-bold text-slate-800">{b.name}</p><p className="text-[10px] text-slate-500">{b.categoryName||'All Categories'}{b.supplierName?` · ${b.supplierName}`:''} / {b.dateFrom} - {b.dateTo}</p></div>{canManageBudgets&&<div className="flex gap-2"><button onClick={()=>editBudget(b)} className="text-[10px] font-bold text-slate-600">Edit</button><button onClick={()=>void toggleBudget(b)} className="text-[10px] font-bold text-violet-700">{b.active?'Disable':'Enable'}</button><button onClick={()=>void deleteBudget(b)} className="text-[10px] font-bold text-rose-600">Delete</button></div>}</div><div className="mt-2 h-2 overflow-hidden rounded bg-slate-100"><div className={`h-full ${(b.usagePercent||0)>100?'bg-rose-500':'bg-violet-500'}`} style={{width:`${Math.min(100,b.usagePercent||0)}%`}}/></div><div className="mt-1 flex justify-between text-[10px]"><span>Spent {money(b.spentAmount||0)} / {money(b.limitAmount)}</span><b className={(b.usagePercent||0)>100?'text-rose-600':'text-violet-700'}>{b.usagePercent||0}% {b.enforcement}</b></div></div>)}</div>
            {purchaseBudgets.length===0&&<p className="py-4 text-center text-xs text-slate-400">No purchase budgets configured.</p>}
          </section>}

          {expiryPanelOpen&&canAccessExpiry&&<section className="rounded-xl border border-rose-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2"><div><h3 className="text-sm font-black text-slate-800">Expiry / FEFO Stock Lots</h3><p className="text-[10px] text-slate-500">Sales consume tracked lots by earliest expiry date first.</p></div><div className="flex items-center gap-2"><select value={expiryDays} onChange={e=>setExpiryDays(Number(e.target.value))} className="rounded-lg border border-rose-200 px-2 py-1 text-xs"><option value={30}>30 days</option><option value={60}>60 days</option><option value={90}>90 days</option><option value={180}>180 days</option></select><button onClick={()=>setExpiryPanelOpen(false)} className="p-1 text-slate-400"><X size={16}/></button></div></div>
            <div className="overflow-auto rounded-lg border border-slate-100"><table className="w-full min-w-[760px] text-xs"><thead className="bg-slate-50 text-slate-500"><tr><th className="px-3 py-2 text-left">Product / Batch</th><th className="px-3 py-2 text-left">Purchase</th><th className="px-3 py-2 text-left">Warehouse</th><th className="px-3 py-2 text-right">Remaining</th><th className="px-3 py-2 text-left">Expiry</th><th className="px-3 py-2 text-center">Alert</th></tr></thead><tbody className="divide-y">{expiringLots.map(l=><tr key={l.id}><td className="px-3 py-2"><b>{l.productName}</b><span className="block text-[10px] text-slate-400">{l.productCode} / {l.batchNumber||`Lot #${l.id}`}</span></td><td className="px-3 py-2 font-mono">{l.purchaseCode}</td><td className="px-3 py-2">{l.warehouseName||'Main'}</td><td className="px-3 py-2 text-right font-bold">{l.remainingQty} / {l.receivedQty}</td><td className="px-3 py-2">{l.expiryDate}<span className="block text-[10px] text-slate-400">{l.daysToExpiry<0?`${Math.abs(l.daysToExpiry)} days overdue`:`${l.daysToExpiry} days left`}</span></td><td className="px-3 py-2 text-center"><span className={`rounded px-2 py-1 text-[10px] font-bold ${l.alertLevel==='EXPIRED'?'bg-slate-200 text-slate-700':l.alertLevel==='CRITICAL'?'bg-rose-100 text-rose-700':l.alertLevel==='WARNING'?'bg-amber-100 text-amber-700':'bg-blue-50 text-blue-700'}`}>{l.alertLevel}</span></td></tr>)}</tbody></table>{expiringLots.length===0&&<p className="p-5 text-center text-xs text-slate-400">No tracked lots expire in this period.</p>}</div>
          </section>}

          {warehousePanelOpen&&canAccessWarehouse&&<section className="rounded-xl border border-teal-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between"><div><h3 className="text-sm font-black text-slate-800">Warehouse stock balance</h3><p className="text-[10px] text-slate-500">Remaining tracked lot quantity grouped by warehouse name.</p></div><button onClick={()=>setWarehousePanelOpen(false)} className="p-1 text-slate-400"><X size={16}/></button></div>
            <div className="overflow-auto rounded-lg border border-slate-100"><table className="w-full min-w-[640px] text-xs"><thead className="bg-slate-50 text-slate-500"><tr><th className="px-3 py-2 text-left">Warehouse</th><th className="px-3 py-2 text-left">Product</th><th className="px-3 py-2 text-right">Remaining</th><th className="px-3 py-2 text-right">Received</th><th className="px-3 py-2 text-right">Lots</th></tr></thead><tbody className="divide-y">{warehouseBalances.map((row,i)=><tr key={`${row.warehouseName}-${row.productId}-${i}`}><td className="px-3 py-2 font-semibold">{row.warehouseName}</td><td className="px-3 py-2"><b>{row.productName}</b><span className="block text-[10px] text-slate-400">{row.productCode}</span></td><td className="px-3 py-2 text-right font-bold">{row.remainingQty}</td><td className="px-3 py-2 text-right">{row.receivedQty}</td><td className="px-3 py-2 text-right">{row.lotCount}</td></tr>)}</tbody></table>{warehouseBalances.length===0&&<p className="p-5 text-center text-xs text-slate-400">No warehouse lot balances yet.</p>}</div>
          </section>}

          {analyticsPanelOpen&&canAccessAnalytics&&purchaseAnalytics&&<section className="rounded-xl border border-indigo-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between"><div><h3 className="text-sm font-black text-slate-800">Procurement analytics</h3><p className="text-[10px] text-slate-500">{dateFrom || 'From beginning'} - {dateTo || 'Until today'}</p></div><button onClick={()=>setAnalyticsPanelOpen(false)} className="p-1 text-slate-400"><X size={16}/></button></div>
            <div className="mb-3 grid grid-cols-2 gap-2 md:grid-cols-4 xl:grid-cols-8 text-xs">
              {[['Spent',purchaseAnalytics.totalSpent],['Paid',purchaseAnalytics.paidAmount],['Due',purchaseAnalytics.dueAmount],['Tax',purchaseAnalytics.taxAmount],['WHT',purchaseAnalytics.withholdingTaxAmount],['Landed',purchaseAnalytics.landedCostAmount],['Returns',purchaseAnalytics.returnAmount],['FX amount',purchaseAnalytics.foreignAmount]].map(([label,value])=><div key={String(label)} className="rounded-lg border border-slate-100 bg-slate-50 p-2"><p className="text-[10px] text-slate-400">{label}</p><b>{money(Number(value))}</b></div>)}
            </div>
            <div className="grid gap-3 md:grid-cols-3 text-xs">
              {[{title:'By category',rows:purchaseAnalytics.byCategory},{title:'By supplier',rows:purchaseAnalytics.bySupplier},{title:'By currency',rows:purchaseAnalytics.byCurrency}].map(block=><div key={block.title} className="overflow-auto rounded-lg border border-slate-100"><p className="bg-slate-50 px-3 py-2 font-bold">{block.title}</p><table className="w-full"><tbody>{block.rows.map(row=><tr key={row.name} className="border-t"><td className="px-3 py-1.5">{row.name}</td><td className="px-3 py-1.5 text-right text-slate-400">{row.count}</td><td className="px-3 py-1.5 text-right font-bold">{money(row.amount)}</td></tr>)}{block.rows.length===0&&<tr><td className="px-3 py-4 text-center text-slate-400">No data</td></tr>}</tbody></table></div>)}
            </div>
            <p className="mt-2 text-[10px] text-slate-400">GRN {purchaseAnalytics.grnCount} · Variance {purchaseAnalytics.grnVarianceCount} · FX vouchers {purchaseAnalytics.fxVoucherCount}</p>
          </section>}

          <div className="flex flex-col gap-3 xl:flex-row xl:items-start">
          <aside className="order-2 flex w-full shrink-0 flex-col gap-3 xl:sticky xl:top-2 xl:w-1/5 xl:min-w-[260px] xl:max-w-[320px] xl:max-h-[calc(100vh-5.5rem)] xl:overflow-y-auto custom-scrollbar">
            {canCreatePurchases && (
            <button onClick={() => setShowNewVoucherForm(true)} className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-3 py-2.5 text-sm font-bold text-white hover:bg-indigo-700">
              <Plus size={16} />
              ဝယ်ယူမှုအသစ်
            </button>
            )}
            <div className="grid grid-cols-2 gap-1.5">
              <button onClick={handleExportExcel} disabled={exportingExcel} className="inline-flex items-center justify-center gap-1 rounded-lg border border-emerald-200 bg-white px-2 py-1.5 text-[11px] font-bold text-emerald-700 hover:bg-emerald-50 disabled:opacity-60">
                {exportingExcel ? <Loader2 size={13} className="animate-spin" /> : <FileSpreadsheet size={13} />} Excel
              </button>
              {canAccessReorder && (
                <button onClick={() => void openReorderModal()} className="inline-flex items-center justify-center gap-1 rounded-lg border border-amber-200 bg-white px-2 py-1.5 text-[11px] font-bold text-amber-700 hover:bg-amber-50">
                  <ClipboardList size={13} /> Reorder
                </button>
              )}
              {canAccessSupplierPayment && (
                <button onClick={() => setSupplierPaymentOpen(true)} className="inline-flex items-center justify-center gap-1 rounded-lg border border-blue-200 bg-white px-2 py-1.5 text-[11px] font-bold text-blue-700 hover:bg-blue-50">
                  <CreditCard size={13} /> Payment
                </button>
              )}
              {canAccessBudgets && (
                <button onClick={()=>setBudgetPanelOpen(v=>!v)} className="inline-flex items-center justify-center gap-1 rounded-lg border border-violet-200 bg-white px-2 py-1.5 text-[11px] font-bold text-violet-700 hover:bg-violet-50"><DollarSign size={13}/> Budgets</button>
              )}
              {canAccessWarehouse && (
                <button onClick={()=>void openWarehousePanel()} className="inline-flex items-center justify-center gap-1 rounded-lg border border-teal-200 bg-white px-2 py-1.5 text-[11px] font-bold text-teal-700 hover:bg-teal-50"><Warehouse size={13}/> Warehouse</button>
              )}
              {canAccessAnalytics && (
                <button onClick={()=>void openAnalyticsPanel()} className="inline-flex items-center justify-center gap-1 rounded-lg border border-indigo-200 bg-white px-2 py-1.5 text-[11px] font-bold text-indigo-700 hover:bg-indigo-50"><BarChart3 size={13}/> Analytics</button>
              )}
              {canAccessExpiry && (
                <button onClick={()=>setExpiryPanelOpen(v=>!v)} className="relative inline-flex items-center justify-center gap-1 rounded-lg border border-rose-200 bg-white px-2 py-1.5 text-[11px] font-bold text-rose-700 hover:bg-rose-50"><AlertTriangle size={13}/> Expiry{expiringLots.length>0&&<span className="ml-0.5 rounded-full bg-rose-600 px-1 text-[9px] text-white">{expiringLots.length}</span>}</button>
              )}
              <button onClick={() => { fetchPurchases(purchasePage, purchasePageSize, debouncedSearch, dateFrom, dateTo); fetchStats(dateFrom, dateTo); }} className="inline-flex items-center justify-center gap-1 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-[11px] font-medium text-slate-600 hover:bg-slate-50">
                <RefreshCw size={13} className={purchasesLoading ? 'animate-spin' : ''} /> ပြန်ဖတ်
              </button>
            </div>
            <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
              <p className="mb-2 text-[10px] font-black uppercase tracking-wider text-slate-400">အနှစ်ချုပ်</p>
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold text-slate-500">ဘောင်ချာ</span>
                  <b className="text-sm text-slate-800">{serverStats.count}</b>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold text-slate-500">ဝယ်ယူမှု</span>
                  <b className="text-sm tabular-nums text-slate-800">{money(serverStats.totalAmount)}</b>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold text-slate-500">ပေးချေပြီး</span>
                  <b className="text-sm tabular-nums text-emerald-700">{money(serverStats.paidAmount)}</b>
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold text-slate-500">ပေးရန်ကျန်</span>
                  <b className="text-sm tabular-nums text-amber-700">{money(serverStats.dueAmount)}</b>
                </div>
                <p className="text-[10px] text-slate-400">ဤစာမျက်နှာ: {filteredPurchases.length}</p>
              </div>
            </div>

          <section className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
            <div className="mb-2 flex items-center justify-between gap-2 flex-wrap">
              <div>
                <h2 className="text-xs font-black text-slate-800">Purchase / Paid / Payable</h2>
                <span className="text-[10px] font-semibold text-slate-400">
                  {dateFrom || 'From beginning'} - {dateTo || 'Until today'}
                </span>
              </div>
              <div className="flex items-center gap-1.5 flex-wrap">
                <select
                  value={chartPeriod}
                  onChange={(e) => {
                    const v = e.target.value as 'today' | 'month' | 'year' | 'all';
                    setChartPeriod(v);
                    if (v === 'today') {
                      const range = getTodayRange();
                      setDateFrom(range.from);
                      setDateTo(range.to);
                      setDateShortcut('TODAY');
                    } else if (v === 'month') {
                      applyDateShortcut('MONTH');
                    } else if (v === 'year') {
                      const range = getThisYearRange();
                      setDateFrom(range.from);
                      setDateTo(range.to);
                      setDateShortcut('CUSTOM');
                    } else {
                      applyDateShortcut('ALL');
                    }
                  }}
                  className="rounded border border-slate-200 bg-white px-2 py-1 text-[10px] font-medium focus:border-indigo-400 focus:outline-none"
                >
                  <option value="today">Today</option>
                  <option value="month">This Month</option>
                  <option value="year">This Year</option>
                  <option value="all">All Time</option>
                </select>
                <button
                  onClick={() => setChartView('trend')}
                  className={`px-2.5 py-1 rounded text-[10px] font-bold ${chartView === 'trend' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                >
                  Trend
                </button>
                <button
                  onClick={() => setChartView('suppliers')}
                  className={`px-2.5 py-1 rounded text-[10px] font-bold ${chartView === 'suppliers' ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                >
                  Top Suppliers
                </button>
              </div>
            </div>
            {trendLoading ? (
              <div className="flex h-28 items-center justify-center text-xs text-slate-400">
                <Loader2 size={14} className="mr-2 animate-spin" /> Loading...
              </div>
            ) : purchaseTrend.length === 0 ? (
              <div className="flex h-24 items-center justify-center rounded-lg border border-dashed border-slate-200 bg-slate-50 text-[11px] text-slate-400">
                No data
              </div>
            ) : (
              <div className="h-36 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  {chartView === 'trend' ? (
                    <ComposedChart data={purchaseTrend} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                      <XAxis dataKey="date" tick={{ fontSize: 9 }} minTickGap={28} hide={false} />
                      <YAxis tick={{ fontSize: 9 }} width={32} tickFormatter={(value) => {
                        const amount = Number(value) || 0;
                        if (Math.abs(amount) >= 1000000) return `${(amount / 1000000).toFixed(1)}M`;
                        if (Math.abs(amount) >= 1000) return `${(amount / 1000).toFixed(0)}K`;
                        return String(amount);
                      }} />
                      <Tooltip formatter={(value) => money(Number(value))}
                        labelFormatter={(label) => `Date: ${label}`}
                        contentStyle={{ borderRadius: 10, borderColor: '#e2e8f0', fontSize: 12 }} />
                      <Legend iconType="circle" iconSize={6} wrapperStyle={{ fontSize: 9 }} />
                      <Bar dataKey="purchaseAmount" name="Purchase" fill="#4f46e5" radius={[4, 4, 0, 0]} maxBarSize={18} />
                      <Line type="monotone" dataKey="paidAmount" name="Paid" stroke="#059669" strokeWidth={2.5} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                      <Line type="monotone" dataKey="payableAmount" name="Payable" stroke="#e11d48" strokeWidth={2.5} dot={{ r: 3 }} activeDot={{ r: 5 }} />
                    </ComposedChart>
                  ) : (
                    <BarChart data={topSuppliers.slice(0, 8).reverse()} layout="vertical" margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#e2e8f0" />
                      <XAxis type="number" tick={{ fontSize: 9 }} width={32} tickFormatter={(value) => {
                        const amount = Number(value) || 0;
                        if (Math.abs(amount) >= 1000000) return `${(amount / 1000000).toFixed(1)}M`;
                        if (Math.abs(amount) >= 1000) return `${(amount / 1000).toFixed(0)}K`;
                        return String(amount);
                      }} />
                      <YAxis type="category" dataKey="supplierName" tick={{ fontSize: 9 }} width={100} interval={0} />
                      <Tooltip formatter={(value) => money(Number(value))}
                        labelFormatter={(label) => `Supplier: ${label}`}
                        contentStyle={{ borderRadius: 10, borderColor: '#e2e8f0', fontSize: 12 }} />
                      <Bar dataKey="totalAmount" name="Total Amount" fill="#059669" radius={[0, 4, 4, 0]} maxBarSize={18} />
                    </BarChart>
                  )}
                </ResponsiveContainer>
              </div>
            )}
          </section>

          {/* Filters */}
          <div className="space-y-3">
            <div className="grid grid-cols-1 gap-3">
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
                <div className="grid gap-3">
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
                    <div className="grid grid-cols-2 gap-1">
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

                <div className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-2">
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
          </aside>
          <div className="order-1 min-w-0 w-full xl:flex-1">
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            <div className="p-4 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2 flex-wrap">
                <List size={18} className="text-indigo-500 shrink-0" />
                <span className="font-semibold text-slate-800">ဝယ်ယူမှု ဘောင်ချာစာရင်း</span>
                <div className="flex rounded-lg border border-slate-200 overflow-hidden text-[11px] font-bold">
                  <button type="button" onClick={() => setListTab('all')} className={`px-2.5 py-1 ${listTab === 'all' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-600'}`}>အားလုံး</button>
                  <button type="button" onClick={() => setListTab('overdue')} className={`px-2.5 py-1 ${listTab === 'overdue' ? 'bg-rose-600 text-white' : 'bg-white text-slate-600'}`}>
                    ကြွေးကျန် {overduePurchases.length > 0 ? `(${overduePurchases.length})` : ''}
                  </button>
                </div>
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
            <div className="overflow-auto max-h-[70vh] xl:max-h-[calc(100vh-11rem)] custom-scrollbar">
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
                <table className="w-full min-w-[1100px] table-fixed border-collapse text-sm">
                  <colgroup>
                    <col className="w-10" />
                    <col className="w-11" />
                    <col className="w-[150px]" />
                    <col className="w-[160px]" />
                    <col className="w-[120px]" />
                    <col className="w-[108px]" />
                    <col className="w-[118px]" />
                    <col className="w-[118px]" />
                    <col className="w-[120px]" />
                    <col className="w-[210px]" />
                  </colgroup>
                  <thead className="sticky top-0 z-10 border-b border-slate-200 bg-slate-50">
                    <tr className="text-[11px] font-bold uppercase tracking-wide text-slate-500">
                      <th className="px-1.5 py-3 text-center" title="Select">
                        <span className="sr-only">Select</span>
                      </th>
                      <th className="px-1.5 py-3 text-center">#</th>
                      <th className="px-3 py-3 text-left">ဘောင်ချာ</th>
                      <th className="px-3 py-3 text-left">ပေးသွင်းသူ</th>
                      <th className="px-3 py-3 text-left">ဝယ်ယူသူ</th>
                      <th className="px-3 py-3 text-left">ရက်စွဲ</th>
                      <th className="px-3 py-3 text-right">စုစုပေါင်း</th>
                      <th className="px-3 py-3 text-right">ပေးရန်ကျန်</th>
                      <th className="px-2 py-3 text-center">အခြေအနေ</th>
                      <th className="sticky right-0 z-20 border-l border-slate-200 bg-slate-50 px-3 py-3 text-right">လုပ်ဆောင်ချက်</th>
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
                           <tr key={p.id!} className={`group h-[58px] hover:bg-slate-50/80 transition-colors ${isCancelledRow ? 'opacity-60' : ''}`}>
                            <td className="px-1.5 py-3 text-center">
                              <input type="checkbox" checked={bulk.selectedIds.has(p.id as number)} onChange={() => bulk.toggle(p.id as number)} className="h-4 w-4 accent-indigo-600" aria-label={`Select purchase ${p.purchaseCode || p.id}`} />
                            </td>
                            <td className="px-1.5 py-3 text-center text-xs font-semibold tabular-nums text-slate-400">{purchasePage * purchasePageSize + index + 1}</td>
                            <td className="px-3 py-3 font-mono text-xs font-bold text-slate-800">
                              <span className="block truncate">{p.purchaseCode || `#${p.id}`}</span>
                              {p.poCode && <span className="block truncate text-[10px] font-semibold text-indigo-500">{p.poCode}</span>}
                              {p.supplierInvoiceNo && <span className="block truncate text-[10px] font-medium text-slate-400">{p.supplierInvoiceNo}</span>}
                            </td>
                            <td className="px-3 py-3 font-semibold text-slate-700"><span className="block truncate">{p.supplierName || '-'}</span></td>
                            <td className="px-3 py-3 text-xs font-medium text-slate-500"><span className="block truncate">{p.staffName || '-'}</span></td>
                            <td className="px-3 py-3 text-xs font-medium text-slate-500"><span className="block truncate">{p.purchaseDate ? new Date(p.purchaseDate).toLocaleDateString() : '-'}</span></td>
                            <td className="px-3 py-3 text-right font-semibold tabular-nums text-slate-800">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(p.totalAmount)}</td>
                            <td className={`px-3 py-3 text-right font-bold tabular-nums ${(p.dueAmount || 0) > 0 ? 'text-rose-700' : 'text-slate-300'}`}>
                              {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(p.dueAmount || 0)}
                            </td>
                            <td className="px-2 py-3 text-center">
                              <span className={`inline-flex max-w-full truncate justify-center rounded-md px-2 py-1 text-[10px] font-bold ${statusStyles[statusKey] || 'bg-slate-100 text-slate-600'}`}>
                                {statusLabel}
                              </span>
                            </td>
                             <td className="sticky right-0 z-[5] border-l border-slate-100 bg-white px-3 py-3 text-right group-hover:bg-slate-50">
                               <div className="inline-flex w-full items-center justify-end gap-1.5 whitespace-nowrap">
                                 {isDraftRow && canUpdatePurchases && (
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
                                  className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-violet-200 text-violet-700 hover:bg-violet-50"
                                  title="Voucher Preview"
                                  aria-label="Voucher Preview"
                                >
                                  <Printer size={14} />
                                </button>
                                <button
                                  onClick={() => openSendTo(p.id!)}
                                  className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-sky-200 text-sky-700 hover:bg-sky-50"
                                  title="Send To"
                                  aria-label="Send voucher"
                                >
                                  <Share2 size={14} />
                                </button>
                                <button onClick={() => void copyPurchaseToNewVoucher(p.id!)} className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-amber-200 text-amber-700 hover:bg-amber-50" title="Copy to new voucher" aria-label="Copy purchase voucher">
                                  <Copy size={14} />
                                </button>
                                 <button onClick={() => openView(p.id!)} className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-indigo-200 text-indigo-700 hover:bg-indigo-50" title="အသေးစိတ်ကြည့်မည်" aria-label="View purchase details">
                                   <Eye size={14} />
                                 </button>
                                 {!isCancelledRow && canDeletePurchases && (
                                   <button
                                     onClick={() => handleCancelPurchase(p)}
                                     disabled={rowActionBusy}
                                     className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-300 text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-60"
                                     title={isDraftRow ? 'မူကြမ်း ဖျက်မည်' : 'ဘောင်ချာ ပယ်ဖျက် (Void)'}
                                     aria-label={isDraftRow ? 'မူကြမ်း ဖျက်မည်' : 'ဘောင်ချာ ပယ်ဖျက်မည်'}
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
          </div>
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
            <div className="flex flex-wrap items-center justify-end gap-2">
              <input ref={purchaseImportRef} type="file" accept=".xlsx,.xls" className="hidden" onChange={e=>void handlePurchaseImport(e.target.files?.[0]||null)}/>
              <input ref={ocrImportRef} type="file" accept=".txt,.csv,.tsv,text/plain,text/csv" className="hidden" onChange={e=>void handleOcrImport(e.target.files?.[0]||null)}/>
              {canAccessImport&&<>
                <button type="button" onClick={()=>void purchaseApiService.downloadImportTemplate().catch(e=>Swal.fire('Template failed',e.message||'Unable to download template','error'))} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-50"><FileDown size={14}/> Template</button>
                <button type="button" disabled={purchaseImporting} onClick={()=>purchaseImportRef.current?.click()} className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 px-3 py-1.5 text-xs font-bold text-emerald-700 hover:bg-emerald-50 disabled:opacity-50">{purchaseImporting?<Loader2 size={14} className="animate-spin"/>:<Upload size={14}/>} Excel Import</button>
              </>}
              {(canAccessImport || canCreatePurchases) && (
                <button type="button" disabled={purchaseImporting} onClick={()=>ocrImportRef.current?.click()} className="inline-flex items-center gap-1.5 rounded-lg border border-indigo-200 px-3 py-1.5 text-xs font-bold text-indigo-700 hover:bg-indigo-50 disabled:opacity-50">{purchaseImporting?<Loader2 size={14} className="animate-spin"/>:<FileText size={14}/>} OCR Import</button>
              )}
              <button type="button" onClick={resetFormFields} className="inline-flex items-center justify-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-sm font-medium hover:bg-slate-50">ရှင်းမည်</button>
            </div>
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
                    else if (paymentTermDays === -2) setDueDate(endOfMonthInput(next));
                  }}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                />
              </div>

              <div>
                <label className="flex h-[28px] items-center text-xs font-semibold text-slate-600">ပေးသွင်းသူ Invoice No.</label>
                <input
                  value={supplierInvoiceNo}
                  onChange={(e) => setSupplierInvoiceNo(e.target.value)}
                  placeholder="SUP-INV-001"
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

            <div className="border border-slate-200 rounded-lg">
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
                            <PortaledCombobox
                              items={productComboboxItems}
                              value={detail.productId || 0}
                              displayValue={detail.productSearch && detail.productSearch.length > 0 ? detail.productSearch : getProductLabelById(detail.productId)}
                              placeholder="ပစ္စည်းရှာပါ..."
                              onQueryChange={(q) => handleProductSearchChange(dIndex, q)}
                              onChange={(id) => {
                                if (id > 0) handleProductSelect(dIndex, id);
                                else handleProductSearchChange(dIndex, '');
                              }}
                            />
                          </div>
                          {_rowProduct && (
                            <div className="mt-1 flex flex-wrap items-center gap-1.5">
                              <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold ${isSerialRequired(detail.productId) ? 'bg-indigo-50 text-indigo-700' : 'bg-slate-100 text-slate-600'}`}>
                                {isSerialRequired(detail.productId) ? 'Serial' : 'Qty only'}
                              </span>
                              <span className="text-[10px] font-semibold text-slate-400">Stock {Number(_rowProduct.stockQty ?? _rowProduct.currentStock ?? 0).toLocaleString()}</span>
                            </div>
                          )}
                          {detail.productId > 0 && (
                            <div className="mt-1.5 grid grid-cols-2 gap-1.5">
                              <input
                                value={detail.batchNumber || ''}
                                onChange={(e) => handleDetailChange(dIndex, 'batchNumber', e.target.value)}
                                placeholder="Batch"
                                className="px-2 py-1 rounded border border-slate-200 bg-white text-[11px] focus:outline-none focus:border-indigo-400"
                              />
                              <input
                                type="date"
                                value={detail.expiryDate || ''}
                                onChange={(e) => handleDetailChange(dIndex, 'expiryDate', e.target.value)}
                                className="px-2 py-1 rounded border border-slate-200 bg-white text-[11px] focus:outline-none focus:border-indigo-400"
                              />
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
                          {_rowProduct?.lastPurchaseCost || _rowProduct?.costPrice ? <p className="mt-1 text-[10px] text-slate-400">နောက်ဆုံးဝယ်ဈေး {money(suggestedCost(_rowProduct))}</p> : null}
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

                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
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
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Tax Mode</label>
                    <select value={taxMode} onChange={(e) => {
                      const mode = e.target.value as 'EXCLUSIVE'|'INCLUSIVE'; setTaxMode(mode);
                      const taxable = Math.max(0, totalAmount - safeDiscountAmount);
                      setTaxAmount(taxRate > 0 ? (mode === 'INCLUSIVE' ? taxable * taxRate / (100 + taxRate) : taxable * taxRate / 100) : 0);
                    }} className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm">
                      <option value="EXCLUSIVE">Tax Exclusive</option><option value="INCLUSIVE">Tax Inclusive</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Tax Rate / Amount</label>
                    <div className="flex gap-1"><input type="number" min="0" step="0.01" value={taxRate || ''} onChange={(e) => {
                      const rate=Math.max(0,Number(e.target.value)||0);setTaxRate(rate);
                      const taxable=Math.max(0,totalAmount-safeDiscountAmount);
                      setTaxAmount(rate>0?(taxMode==='INCLUSIVE'?taxable*rate/(100+rate):taxable*rate/100):0);
                    }} placeholder="%" className="w-16 px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm"/>
                    <input type="number" min="0" value={taxAmount || ''} onChange={(e)=>setTaxAmount(Math.max(0,Number(e.target.value)||0))} placeholder="Tax" className="min-w-0 flex-1 px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm"/></div>
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
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Withholding Tax</label>
                    <input type="number" min="0" max={grossPayable||undefined} value={withholdingTaxAmount||''} onChange={(e)=>setWithholdingTaxAmount(Math.max(0,Number(e.target.value)||0))} placeholder="0" className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm"/>
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Landed Cost Allocation</label>
                    <select value={landedCostAllocationMethod} onChange={(e)=>setLandedCostAllocationMethod(e.target.value as 'VALUE'|'QUANTITY'|'MANUAL')} className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm"><option value="VALUE">By Value</option><option value="QUANTITY">By Quantity</option><option value="MANUAL">Manual</option></select>
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Warehouse / Branch</label>
                    <select
                      value={warehouseName}
                      onChange={(e) => setWarehouseName(e.target.value)}
                      className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                    >
                      {activeWarehouses.length === 0 && (
                        <option value="">ဂိုဒေါင် မရှိသေး — ဂိုဒေါင် စာမျက်နှာတွင် ထည့်ပါ</option>
                      )}
                      {activeWarehouses.map((w) => (
                        <option key={w.id ?? w.name} value={w.name}>
                          {w.name}{w.code ? ` (${w.code})` : ''}
                        </option>
                      ))}
                      {/* Keep previously saved free-text name selectable if not in master */}
                      {warehouseName && !activeWarehouses.some((w) => w.name === warehouseName) && (
                        <option value={warehouseName}>{warehouseName} (စာရင်းမရှိ)</option>
                      )}
                    </select>
                    {activeWarehouses.length === 0 && (
                      <p className="mt-1 text-[10px] text-amber-600">ဝယ်ယူရေး → ဂိုဒေါင် မှ warehouse အသစ်ထည့်ပါ။</p>
                    )}
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">Invoice Currency</label>
                    <input maxLength={3} value={currencyCode} onChange={(e)=>{ const code=e.target.value.toUpperCase().replace(/[^A-Z]/g,'').slice(0,3); setCurrencyCode(code); if(code==='MMK') setExchangeRate(1); }} placeholder="MMK" className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm uppercase"/>
                  </div>
                  <div>
                    <label className="block text-[10px] font-semibold text-slate-500 mb-1">1 Currency = MMK</label>
                    <input type="number" min="0.000001" step="0.000001" disabled={currencyCode==='MMK'} value={currencyCode==='MMK'?1:(exchangeRate||'')} onChange={(e)=>setExchangeRate(Math.max(0,Number(e.target.value)||0))} className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm disabled:opacity-60"/>
                  </div>
                </div>
                {landedCostAllocationMethod === 'MANUAL' && (
                  <div className={`space-y-1.5 rounded-lg border p-2 ${landedAllocationValid?'border-emerald-200 bg-emerald-50/50':'border-rose-200 bg-rose-50/50'}`}>
                    <div className="flex justify-between text-[10px] font-bold"><span>Manual Landed Cost per Line</span><span>{money(manualLandedTotal)} / {money(safeOtherCharges)}</span></div>
                    {details.map((d,index)=>d.productId>0?<label key={index} className="flex items-center justify-between gap-2 text-xs"><span className="truncate text-slate-600">{products.find(p=>p.id===d.productId)?.name||`Line ${index+1}`}</span><input type="number" min="0" value={d.allocatedLandedCost||''} onChange={(e)=>setDetails(prev=>prev.map((row,i)=>i===index?{...row,allocatedLandedCost:Math.max(0,Number(e.target.value)||0)}:row))} className="w-28 rounded border border-slate-200 bg-white px-2 py-1 text-right"/></label>:null)}
                    {!landedAllocationValid && <p className="text-[10px] font-bold text-rose-600">Allocated total must equal Other Charges.</p>}
                  </div>
                )}

                <div className="flex items-center justify-between rounded-lg bg-slate-900 px-3 py-2 text-white">
                  <span className="text-xs font-semibold">ကျသင့်ငွေ {safeWithholdingTaxAmount>0&&<small className="ml-1 text-slate-400">(WHT -{money(safeWithholdingTaxAmount)})</small>}</span>
                  <span className="text-base font-black tracking-tight">{money(netAmount)}</span>
                </div>
                {currencyCode !== 'MMK' && safeExchangeRate > 0 && <div className="flex justify-between rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-xs font-semibold text-indigo-800"><span>Foreign payable ({currencyCode})</span><span>{foreignNetAmount.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2})} / Rate {safeExchangeRate.toLocaleString()}</span></div>}

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
                          else if (days === -2) setDueDate(endOfMonthInput(purchaseDate));
                        }}
                        className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400"
                      >
                        <option value={-1}>စိတ်ကြိုက်</option>
                        <option value={-2}>လကုန်တွင်ပေးမည်</option>
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

                {dueAmount > 0 && selectedSupplier && (
                  <div className={`rounded-xl border p-3 ${supplierLimitExceeded ? 'border-rose-200 bg-rose-50' : supplierLimitNear ? 'border-amber-200 bg-amber-50' : 'border-emerald-200 bg-emerald-50'}`}>
                    <div className="flex items-start justify-between gap-3 text-xs">
                      <div>
                        <p className="font-bold text-slate-700">Supplier Credit</p>
                        <p className="mt-0.5 text-slate-600">လက်ရှိ {money(supplierCurrentBalance)} + အသစ် {money(dueAmount)} Ks</p>
                      </div>
                      <p className={`text-right font-black ${supplierLimitExceeded ? 'text-rose-700' : supplierLimitNear ? 'text-amber-700' : 'text-emerald-700'}`}>
                        {supplierCreditLimit > 0 ? `${money(projectedSupplierBalance)} / ${money(supplierCreditLimit)} Ks` : 'Limit မသတ်မှတ်ထား'}
                      </p>
                    </div>
                    {supplierCreditLimit > 0 && (
                      <div className="mt-2 h-2 overflow-hidden rounded-full bg-white/80">
                        <div className={`h-full rounded-full transition-all ${supplierLimitExceeded ? 'bg-rose-500' : supplierLimitNear ? 'bg-amber-500' : 'bg-emerald-500'}`} style={{ width: `${creditUsagePercent}%` }} />
                      </div>
                    )}
                    <p className={`mt-1.5 text-[11px] font-semibold ${supplierLimitExceeded ? 'text-rose-700' : supplierLimitNear ? 'text-amber-700' : 'text-emerald-700'}`}>
                      {supplierLimitExceeded ? 'Credit limit ကျော်လွန်နေသည်' : supplierLimitNear ? 'Credit limit 80% ကျော်နီးကပ်နေသည်' : supplierCreditLimit > 0 ? `အသုံးပြုမှု ${creditUsagePercent.toFixed(0)}%` : 'အကြွေးကန့်သတ်ချက်မရှိပါ'}
                    </p>
                    {supplierLimitExceeded && canApproveCreditOverride && (
                      <div className="mt-2 space-y-2 border-t border-rose-200 pt-2">
                        <label className="flex items-center gap-2 text-xs font-bold text-rose-800">
                          <input type="checkbox" checked={creditLimitOverride} onChange={(e) => setCreditLimitOverride(e.target.checked)} className="h-4 w-4 accent-rose-600" />
                          Manager override ဖြင့် ဆက်လုပ်မည်
                        </label>
                        {creditLimitOverride && <textarea value={creditOverrideReason} onChange={(e) => setCreditOverrideReason(e.target.value)} rows={2} maxLength={1000} placeholder="Override အကြောင်းရင်းကို မဖြစ်မနေထည့်ပါ" className="w-full resize-none rounded-lg border border-rose-200 bg-white px-2.5 py-2 text-xs outline-none focus:border-rose-400" />}
                      </div>
                    )}
                    {supplierLimitExceeded && !canApproveCreditOverride && <p className="mt-2 border-t border-rose-200 pt-2 text-[11px] font-bold text-rose-700">Manager approval လိုအပ်ပါသည်။</p>}
                  </div>
                )}

                {dueAmount > 0 && dueDate && (
                  <div className="rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-2 text-xs text-indigo-800">
                    <span className="font-bold">ငွေချေမှုအနှစ်ချုပ်:</span> {purchaseDate} → {dueDate} · ပေးရန်ကျန် {money(dueAmount)} Ks
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
                {canCreatePurchases && (
                <button
                  type="button"
                  disabled={saving || !selectedSupplierId || !details.some((d) => d.productId > 0 && d.qty > 0)}
                  onClick={handleSaveDraft}
                  className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-sky-200 bg-sky-50 px-3 py-2.5 text-sm font-semibold text-sky-700 hover:bg-sky-100 disabled:opacity-50"
                  title="Stock/Serial/Journal မဖန်တီးဘဲ မူကြမ်းအဖြစ်သိမ်းမည်"
                >
                  <FileText size={14} /> မူကြမ်း
                </button>
                )}
                {canCreatePurchases && (
                <button
                  type="button"
                  disabled={!isValid || saving}
                  onClick={handleSave}
                  className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-60"
                >
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />} {saving ? 'သိမ်းနေသည်...' : 'ဘောင်ချာသိမ်းမည်'}
                </button>
                )}
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

      {cancelTarget && (
        <div className="fixed inset-0 z-[70] flex items-end justify-center bg-slate-900/60 p-0 sm:items-center sm:p-4" onMouseDown={closeCancelModal}>
          <form onSubmit={submitCancelPurchase} onMouseDown={(e) => e.stopPropagation()} className="w-full rounded-t-2xl bg-white p-5 shadow-xl sm:max-w-md sm:rounded-2xl">
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <p className="text-[10px] font-black uppercase tracking-widest text-rose-500">ဘောင်ချာ ပယ်ဖျက်မည်</p>
                <h3 className="mt-0.5 text-base font-black text-slate-800">{cancelTarget.purchaseCode || `#${cancelTarget.id}`}</h3>
                <p className="mt-1 text-xs leading-5 text-slate-500">Stock ပြန်နုတ်၊ Journal ပြန်ပြင်မည်။ Return ရှိပြီးသော voucher များကို ပယ်ဖျက်မရပါ။</p>
              </div>
              <button type="button" onClick={closeCancelModal} disabled={rowActionBusy} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700" title="ပိတ်မည်"><X size={18} /></button>
            </div>
            {(Number(cancelTarget.paidAmount) || 0) > 0 && (
              <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5">
                <label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wider text-amber-800">ငွေပြန်ဝင်မည့် Payment Method *</label>
                <select
                  autoFocus
                  required
                  value={cancelRefundMethodId}
                  onChange={(e) => setCancelRefundMethodId(Number(e.target.value))}
                  className="h-10 w-full rounded-lg border border-amber-200 bg-white px-3 text-sm font-semibold text-slate-800 focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500/20"
                >
                  <option value={0}>ရွေးပါ — Cash / Bank</option>
                  {paymentMethods.map((m) => (
                    <option key={m.id} value={m.id}>{m.methodName}{m.accountName ? ` · ${m.accountName}` : ''}</option>
                  ))}
                </select>
                <p className="mt-1.5 text-[11px] leading-5 text-amber-800">
                  ပေးပြီး {money(Number(cancelTarget.paidAmount) || 0)} · မူလပေးချေ {paymentMethods.find((m) => m.id === cancelTarget.paymentMethodId)?.methodName || cancelTarget.payments?.[0]?.paymentMethodName || '—'}။
                  Cash ဝယ်ပြီး Bank ပြန်ဝင်တာမျိုး နည်းလမ်းပြောင်းရွေးနိုင်သည်။
                </p>
              </div>
            )}
            <div>
              <label className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-400">ပယ်ဖျက်ရသည့်အကြောင်းရင်း *</label>
              <textarea
                autoFocus={(Number(cancelTarget.paidAmount) || 0) <= 0}
                required
                maxLength={1000}
                rows={3}
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="အကြောင်းရင်းကို မဖြစ်မနေ ရေးပါ"
                className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500/20"
              />
            </div>
            <div className="mt-5 flex gap-2">
              <button type="button" onClick={closeCancelModal} disabled={rowActionBusy} className="flex-1 rounded-lg px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-100 disabled:opacity-60">မလုပ်တော့ပါ</button>
              <button type="submit" disabled={rowActionBusy || !cancelReason.trim() || ((Number(cancelTarget.paidAmount) || 0) > 0 && cancelRefundMethodId <= 0)} className="flex-1 rounded-lg bg-rose-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-slate-300">
                {rowActionBusy ? 'ပယ်ဖျက်နေသည်...' : 'ပယ်ဖျက်မည်'}
              </button>
            </div>
          </form>
        </div>
      )}

      {importPreview && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[90vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div>
                <h3 className="font-bold text-slate-800">Excel import preview</h3>
                <p className="text-xs text-slate-500">Valid {importPreview.validRows} / {importPreview.totalRows} · Invalid {importPreview.invalidRows} will be skipped</p>
              </div>
              <button onClick={()=>setImportPreview(null)} className="p-1.5 text-slate-400"><X size={18}/></button>
            </div>
            <div className="flex-1 overflow-auto p-4">
              <table className="w-full min-w-[720px] text-xs">
                <thead className="bg-slate-50 text-slate-500"><tr><th className="px-2 py-2 text-left">Row</th><th className="px-2 py-2 text-left">Code</th><th className="px-2 py-2 text-left">Product</th><th className="px-2 py-2 text-right">Qty</th><th className="px-2 py-2 text-right">Cost</th><th className="px-2 py-2 text-left">Batch / Expiry</th><th className="px-2 py-2 text-left">Status</th></tr></thead>
                <tbody className="divide-y">{importPreview.rows.map(row=>(
                  <tr key={row.rowNumber} className={row.valid?'':'bg-rose-50'}>
                    <td className="px-2 py-2">{row.rowNumber}</td>
                    <td className="px-2 py-2 font-mono">{row.productCode}</td>
                    <td className="px-2 py-2">{row.productName||'-'}</td>
                    <td className="px-2 py-2 text-right">{row.qty??'-'}</td>
                    <td className="px-2 py-2 text-right">{row.unitCost??'-'}</td>
                    <td className="px-2 py-2">{[row.batchNumber,row.expiryDate].filter(Boolean).join(' / ')||'-'}</td>
                    <td className="px-2 py-2">{row.valid?'Valid':(row.errors||[]).join('; ')}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
            <div className="flex justify-end gap-2 border-t border-slate-100 p-4">
              <button onClick={()=>setImportPreview(null)} className="rounded-lg border border-slate-200 px-3 py-2 text-xs font-bold text-slate-600">Cancel</button>
              <button onClick={applyImportPreview} className="rounded-lg bg-emerald-600 px-4 py-2 text-xs font-bold text-white">Import valid rows</button>
            </div>
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
                {getStatusKey(viewPurchase) === 'draft' && canUpdatePurchases && (
                  <button
                    onClick={() => handleConfirmDraft(viewPurchase)}
                    disabled={rowActionBusy}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-emerald-600 border border-emerald-200 rounded-lg hover:bg-emerald-700 disabled:opacity-60"
                  >
                    <CheckCircle size={14} />
                    အတည်ပြု
                  </button>
                )}
                {getStatusKey(viewPurchase) !== 'cancelled' && canDeletePurchases && (
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
                {getStatusKey(viewPurchase) !== 'cancelled' && getStatusKey(viewPurchase) !== 'draft' && (
                  <button
                    onClick={() => navigate(`${AppRoute.PURCHASE_RETURNS}?purchaseId=${viewPurchase.id}`)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-rose-700 bg-rose-50 border border-rose-200 rounded-lg hover:bg-rose-100"
                  >
                    <RefreshCw size={14} />
                    ဝယ်ပြန်ပို့
                  </button>
                )}
                <button onClick={closeView} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg">
                  <X size={18} />
                </button>
              </div>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              {getStatusKey(viewPurchase) === 'cancelled' && (
                <div className="rounded-xl border border-slate-300 bg-slate-50 px-4 py-3 text-slate-700">
                  <p className="text-sm font-black">ပယ်ဖျက်ပြီး — ဝယ်ပြန်ပို့ မလုပ်နိုင်ပါ</p>
                  <p className="text-xs mt-1">
                    {viewPurchase.cancelReason || 'အကြောင်းရင်း မရှိပါ'}
                    {viewPurchase.cancelledBy ? ` · ${viewPurchase.cancelledBy}` : ''}
                    {viewPurchase.cancelledAt ? ` · ${new Date(viewPurchase.cancelledAt).toLocaleString()}` : ''}
                  </p>
                  <p className="text-[11px] text-slate-500 mt-1">Stock၊ Serial နှင့် Journal ကို ပယ်ဖျက်စဉ်က ပြန်ပြင်ပြီးဖြစ်သည်။ မှတ်တမ်းကြည့်ရန်သာ ဖြစ်သည်။</p>
                </div>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600"><span className="font-medium text-slate-500">Supplier:</span> {viewPurchase.supplierName}</p>
                {viewPurchase.poCode && <p className="text-slate-600"><span className="font-medium text-slate-500">PO:</span> {viewPurchase.poCode}</p>}
                {viewPurchase.supplierInvoiceNo && <p className="text-slate-600"><span className="font-medium text-slate-500">Supplier Invoice:</span> {viewPurchase.supplierInvoiceNo}</p>}
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
                  <p className="text-slate-600"><span className="font-medium text-slate-500">Other Charges:</span> {new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(viewPurchase.otherCharges || 0)}{viewPurchase.landedCostAllocationMethod ? ` · ${viewPurchase.landedCostAllocationMethod}` : ''}</p>
                )}
                {viewPurchase.landedCostAllocationMethod && !(Number(viewPurchase.otherCharges) > 0) && (
                  <p className="text-slate-600"><span className="font-medium text-slate-500">Landed allocation:</span> {viewPurchase.landedCostAllocationMethod}</p>
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
              {viewPurchase.cancelReason && (
                <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                  <p className="font-bold">ပယ်ဖျက်ရသည့်အကြောင်းရင်း</p>
                  <p>{viewPurchase.cancelReason}</p>
                  <p className="mt-1 text-xs text-rose-600">{viewPurchase.cancelledBy || 'Unknown'}{viewPurchase.cancelledAt ? ` • ${new Date(viewPurchase.cancelledAt).toLocaleString()}` : ''}</p>
                </div>
              )}
              {viewPurchase.creditLimitOverride && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                  <p className="font-bold">Credit Limit Manager Override</p>
                  <p>{viewPurchase.creditOverrideReason || '-'}</p>
                  <p className="mt-1 text-xs text-amber-600">{viewPurchase.creditOverrideBy || 'Unknown'}{viewPurchase.creditOverrideAt ? ` • ${new Date(viewPurchase.creditOverrideAt).toLocaleString()}` : ''}</p>
                </div>
              )}
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
                    <th className="px-3 py-2 border-b text-right">Landed Cost</th>
                    <th className="px-3 py-2 border-b text-right">Subtotal</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {(viewPurchase.details || []).map((d, i) => (
                    <tr key={i}>
                      <td className="px-3 py-2">
                        <p>{d.productName || d.productId}</p>
                        {(d.batchNumber || d.expiryDate) && (
                          <p className="text-[10px] text-slate-400">
                            {d.batchNumber ? `Batch ${d.batchNumber}` : ''}{d.batchNumber && d.expiryDate ? ' · ' : ''}{d.expiryDate ? `Exp ${d.expiryDate}` : ''}
                          </p>
                        )}
                      </td>
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
                      <td className="px-3 py-2 text-right text-indigo-700">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(Number(d.allocatedLandedCost) || 0)}</td>
                      <td className="px-3 py-2 text-right font-medium">{new Intl.NumberFormat('en-US', { minimumFractionDigits: 2 }).format(d.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="pt-2">
                <div className="mb-2 flex items-center justify-between">
                  <h4 className="flex items-center gap-1 text-xs font-bold uppercase tracking-wider text-slate-500"><History size={13}/> Activity Timeline</h4>
                  <span className="text-[11px] text-slate-400">{purchaseTimeline.length} event(s)</span>
                </div>
                {purchaseTimeline.length===0 ? <div className="py-3 text-xs text-slate-400">No timeline events yet.</div> : (
                  <div className="space-y-2">{purchaseTimeline.map((event,i)=>(
                    <div key={`${event.type}-${event.refCode||i}`} className="flex gap-3 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2">
                      <span className="mt-0.5 min-w-[88px] text-[10px] font-black uppercase text-indigo-600">{event.type}</span>
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-bold text-slate-800">{event.title}{event.refCode ? ` · ${event.refCode}` : ''}</p>
                        <p className="text-[11px] text-slate-500">{event.detail || event.at || '-'}</p>
                      </div>
                      {event.amount!=null && <b className="text-xs tabular-nums text-slate-700">{money(Number(event.amount)||0)}</b>}
                    </div>
                  ))}</div>
                )}
              </div>
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

      {/* Product-wide Stock History lives in Product Management, not Purchase entry.
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
      */}
      {isBarcodeOpen && (
        <BarcodeScannerCamera
          onDetected={handleBarcodeDetected}
          onClose={() => setIsBarcodeOpen(false)}
        />
      )}

      {showSerialModal && serialTarget && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm" onMouseDown={() => { if (!rowActionBusy) setShowSerialModal(false); }}>
          <div className="flex max-h-[88vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl" onMouseDown={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
              <div>
                <h3 className="text-base font-black text-slate-800">Serial Numbers ထည့်သွင်းခြင်း</h3>
                <p className="text-xs font-semibold text-slate-400">{serialTarget.purchaseCode} — Serial အရေအတွက်ကိုက်ညီမှုရှိမှ အတည်ပြုနိုင်မည်</p>
              </div>
              <button onClick={() => setShowSerialModal(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"><X size={18} /></button>
            </div>

            <div className="flex-1 space-y-3 overflow-auto p-4">
              {serialDraftLines.map((line, lineIdx) => {
                const filledCount = line.serials.filter((s) => s.trim()).length;
                const issue = serialLineIssue(line);
                return (
                  <div key={line.productId} className={`rounded-xl border p-3 ${issue ? 'border-amber-200 bg-amber-50/50' : 'border-emerald-200 bg-emerald-50/40'}`}>
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <div>
                        <p className="text-sm font-black text-slate-800">{line.productName}</p>
                        <p className="text-[10px] font-bold text-slate-400">{line.productCode} · Qty {line.qty}</p>
                      </div>
                      <span className={`rounded-full px-2.5 py-1 text-[11px] font-black text-white ${filledCount >= line.qty ? 'bg-emerald-600' : 'bg-amber-500'}`}>{filledCount} / {line.qty}</span>
                    </div>
                    <div className="mb-2 flex flex-wrap gap-1.5">
                      {line.serials.map((s, si) => (
                        <span key={si} className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-bold ${s.trim() ? 'bg-indigo-100 text-indigo-800' : 'border border-dashed border-slate-300 bg-white text-slate-300'}`}>
                          {s.trim() || `Slot ${si + 1}`}
                          {s.trim() && (
                            <button type="button" onClick={() => removeSerialFromLine(lineIdx, si)} className="text-indigo-400 hover:text-rose-600" title="ဖယ်ရှား"><X size={11} /></button>
                          )}
                        </span>
                      ))}
                    </div>
                    <div className="flex items-center gap-1.5">
                      <input
                        value={serialEntry[lineIdx] ?? ''}
                        onChange={(e) => setSerialEntry((prev) => ({ ...prev, [lineIdx]: e.target.value }))}
                        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); submitEntry(lineIdx); } }}
                        placeholder="Serial ရိုက်/Scan ပြီး Enter နှိပ်ပါ"
                        className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs focus:border-indigo-400 focus:outline-none"
                        autoComplete="off"
                      />
                      <button type="button" onClick={() => submitEntry(lineIdx)} className="shrink-0 rounded-lg bg-indigo-600 px-2.5 py-1.5 text-[11px] font-black text-white hover:bg-indigo-700">ထည့်</button>
                      <button type="button" onClick={() => setSerialCameraLine(lineIdx)} title="ကင်မရာဖြင့် scan မည်" className="shrink-0 rounded-lg bg-violet-600 p-2 text-white hover:bg-violet-700"><Camera size={13} /></button>
                    </div>
                    {issue && <p className="mt-1.5 text-[11px] font-bold text-amber-600">{issue}</p>}
                  </div>
                );
              })}
            </div>

            <div className="border-t border-slate-100 p-4">
              {globalDupSerial() && (
                <p className="mb-2 text-[11px] font-bold text-rose-600">Duplicate serial (ပစ္စည်းများကြား): '{globalDupSerial()}'</p>
              )}
              <div className="flex gap-2">
                <button onClick={() => setShowSerialModal(false)} disabled={rowActionBusy} className="flex-1 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-50 disabled:opacity-60">နောက်မှ</button>
                <button
                  onClick={() => serialTarget && doConfirmDraft(serialTarget.id, serialDraftLines.map((l) => ({
                    productId: l.productId, qty: l.qty, unitCost: l.unitCost, warrantyMonths: l.warrantyMonths,
                    serialNumbers: l.serials.map((s) => s.trim()), serialConditions: [], serialPhotos: []
                  })))}
                  disabled={!serialModalReady || rowActionBusy}
                  className="flex-[2] rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-black text-white hover:bg-emerald-700 disabled:bg-slate-300 disabled:cursor-not-allowed"
                >
                  {rowActionBusy ? 'အတည်ပြုနေသည်...' : `အတည်ပြု (${serialDraftLines.reduce((a, l) => a + l.qty, 0)} Serial)`}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {serialCameraLine != null && (
        <BarcodeScannerCamera
          onDetected={handleSerialScanDetected}
          onClose={() => setSerialCameraLine(null)}
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
      {supplierPaymentOpen && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-slate-900/60 p-4">
          <div className="flex max-h-[92vh] w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-100 p-4">
              <div><p className="text-[10px] font-black uppercase tracking-widest text-blue-500">Accounts Payable</p><h3 className="text-base font-black text-slate-800">Supplier Payment Allocation</h3></div>
              <button onClick={() => setSupplierPaymentOpen(false)} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100"><X size={18}/></button>
            </div>
            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="space-y-1"><span className="text-[10px] font-bold uppercase text-slate-400">Supplier</span>
                  <PaymentSearchSelect
                    value={supplierPaymentSupplierId}
                    placeholder="ပေးသွင်းသူ အမည် / ကုဒ် / ဖုန်းဖြင့်ရှာပါ..."
                    items={suppliers.map((s) => ({
                      id: s.id,
                      label: s.name,
                      sub: [s.code, s.phone, s.currentBalance != null ? `Balance ${money(Number(s.currentBalance) || 0)}` : '']
                        .filter(Boolean)
                        .join(' · '),
                      searchText: [s.name, s.code, s.phone, s.address].filter(Boolean).join(' ')
                    }))}
                    onChange={(id) => void loadSupplierPayables(id)}
                  />
                </label>
                <label className="space-y-1"><span className="text-[10px] font-bold uppercase text-slate-400">Payment Method</span>
                  <PaymentSearchSelect
                    value={supplierPaymentMethodId}
                    placeholder="ငွေပေးနည်း ရှာပါ..."
                    items={paymentMethods.map((m) => ({
                      id: m.id,
                      label: m.methodName,
                      sub: m.accountName || undefined
                    }))}
                    onChange={setSupplierPaymentMethodId}
                  />
                </label>
                <label className="space-y-1"><span className="text-[10px] font-bold uppercase text-slate-400">Total Payment</span>
                  <input type="number" min="0.01" step="0.01" value={supplierPaymentAmount || ''} onChange={(e) => setSupplierPaymentAmount(Math.max(0, Number(e.target.value) || 0))} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm font-bold text-blue-700"/></label>
                <label className="space-y-1"><span className="text-[10px] font-bold uppercase text-slate-400">Transaction No.</span>
                  <input value={supplierPaymentTxn} onChange={(e) => setSupplierPaymentTxn(e.target.value)} className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"/></label>
              </div>
              {supplierPaymentSupplierId > 0 && <div className="grid grid-cols-3 gap-2 rounded-xl bg-slate-50 p-3 text-center text-xs">
                <div><p className="text-slate-400">Payable</p><b className="text-rose-600">{money(supplierPayables.reduce((s,p)=>s+p.dueAmount,0))}</b></div>
                <div><p className="text-slate-400">Existing Advance</p><b className="text-indigo-600">{money(suppliers.find(s=>s.id===supplierPaymentSupplierId)?.advanceBalance || 0)}</b></div>
                <div><p className="text-slate-400">New Advance</p><b className="text-emerald-600">{money(Math.max(0, supplierPaymentAmount - (supplierPaymentManual ? Object.values(supplierAllocations).reduce((s: number,v)=>s+(Number(v)||0),0) : supplierPayables.reduce((s,p)=>s+p.dueAmount,0))))}</b></div>
              </div>}
              {supplierPaymentSupplierId > 0 && supplierCreditSummary.availableCredit > 0 && (
                <div className="space-y-3 rounded-xl border border-indigo-200 bg-indigo-50/60 p-3">
                  <div className="flex items-center justify-between"><div><p className="text-xs font-black text-indigo-800">Apply Existing Supplier Credit</p><p className="text-[10px] text-indigo-500">Advance {money(supplierCreditSummary.advanceBalance)} + Return Credit {money(supplierCreditSummary.returnCreditBalance)}</p></div><b className="text-sm text-indigo-700">{money(supplierCreditSummary.availableCredit)}</b></div>
                  <div className="grid gap-2 sm:grid-cols-[1.3fr_0.7fr]">
                    <PaymentSearchSelect
                      value={supplierCreditTargetId}
                      placeholder="ကြွေး voucher ကုဒ်ဖြင့်ရှာပါ..."
                      items={supplierPayables.map((p) => ({
                        id: p.purchaseId,
                        label: p.purchaseCode,
                        sub: `Due ${money(p.dueAmount)}${p.dueDate ? ` · ${p.dueDate}` : ''}`
                      }))}
                      onChange={(id) => {
                        setSupplierCreditTargetId(id);
                        const due = supplierPayables.find((p) => p.purchaseId === id)?.dueAmount || 0;
                        setSupplierCreditAmount(Math.min(due, supplierCreditSummary.availableCredit));
                      }}
                    />
                    <input type="number" min="0" max={Math.min(supplierCreditSummary.availableCredit,supplierPayables.find(p=>p.purchaseId===supplierCreditTargetId)?.dueAmount||0)} value={supplierCreditAmount||''} onChange={(e)=>setSupplierCreditAmount(Math.max(0,Number(e.target.value)||0))} placeholder="Credit amount" className="rounded-lg border border-indigo-200 bg-white px-2 py-2 text-right text-xs font-bold text-indigo-700"/>
                  </div>
                  <input value={supplierCreditReason} onChange={(e)=>setSupplierCreditReason(e.target.value)} placeholder="Application note (optional)" className="w-full rounded-lg border border-indigo-200 bg-white px-2 py-2 text-xs"/>
                  <button onClick={applySupplierCredit} disabled={supplierPaymentSaving||supplierCreditTargetId<=0||supplierCreditAmount<=0} className="w-full rounded-lg bg-indigo-600 px-3 py-2 text-xs font-bold text-white hover:bg-indigo-700 disabled:bg-slate-300">Apply Credit to Voucher</button>
                </div>
              )}
              <label className="flex items-center gap-2 text-xs font-bold text-slate-600"><input type="checkbox" checked={supplierPaymentManual} onChange={(e)=>setSupplierPaymentManual(e.target.checked)} className="accent-indigo-600"/> Manual voucher allocation (off = FIFO by due date)</label>
              {supplierPaymentManual && (
                <div className="space-y-2">
                  <div className="relative">
                    <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
                    <input
                      value={supplierPayableSearch}
                      onChange={(e) => setSupplierPayableSearch(e.target.value)}
                      placeholder="Voucher ကုဒ် / due date ဖြင့်ရှာပါ..."
                      className="w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pl-8 pr-3 text-sm focus:border-blue-400 focus:outline-none"
                    />
                  </div>
                  <div className="overflow-auto rounded-xl border border-slate-200">
                    <table className="w-full min-w-[620px] text-xs">
                      <thead className="bg-slate-50 text-slate-500"><tr><th className="px-3 py-2 text-left">Voucher</th><th className="px-3 py-2 text-left">Due Date</th><th className="px-3 py-2 text-right">Due</th><th className="px-3 py-2 text-right">Allocate</th></tr></thead>
                      <tbody className="divide-y">
                        {supplierPayables
                          .filter((p) => {
                            const q = supplierPayableSearch.trim().toLowerCase();
                            if (!q) return true;
                            return [p.purchaseCode, p.dueDate, String(p.dueAmount)].filter(Boolean).some((v) => String(v).toLowerCase().includes(q));
                          })
                          .map((p) => (
                            <tr key={p.purchaseId}>
                              <td className="px-3 py-2 font-bold">{p.purchaseCode}</td>
                              <td className="px-3 py-2 text-slate-500">{p.dueDate || '-'}</td>
                              <td className="px-3 py-2 text-right text-rose-600">{money(p.dueAmount)}</td>
                              <td className="px-3 py-2 text-right">
                                <input type="number" min="0" max={p.dueAmount} value={supplierAllocations[p.purchaseId] || ''} onChange={(e)=>setSupplierAllocations(prev=>({...prev,[p.purchaseId]:Math.min(p.dueAmount,Math.max(0,Number(e.target.value)||0))}))} className="w-28 rounded border border-slate-200 px-2 py-1 text-right font-bold"/>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              <textarea value={supplierPaymentRemark} onChange={(e)=>setSupplierPaymentRemark(e.target.value)} rows={2} placeholder="Remark" className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"/>
              {supplierPaymentSupplierId > 0 && (
                <div className="space-y-2 rounded-xl border border-slate-200 bg-white p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <p className="text-xs font-black text-slate-800">Allocation Payment History</p>
                      <p className="text-[10px] text-slate-400">ဘယ်နေ့ / ဘယ် voucher / ဘယ်လောက်ဆပ်ခဲ့သည်</p>
                    </div>
                    <span className="text-[11px] font-semibold text-slate-400">
                      {supplierPaymentHistoryLoading ? 'Loading...' : `${supplierPaymentHistory.length} payment(s)`}
                    </span>
                  </div>
                  {supplierPaymentHistoryLoading ? (
                    <p className="py-4 text-center text-xs text-slate-400">ဖတ်နေသည်...</p>
                  ) : supplierPaymentHistory.length === 0 ? (
                    <p className="py-4 text-center text-xs text-slate-400">ဤပေးသွင်းသူအတွက် ငွေပေးမှတ်တမ်း မရှိသေးပါ။</p>
                  ) : (
                    <div className="overflow-auto rounded-lg border border-slate-100">
                      <table className="w-full min-w-[820px] text-xs">
                        <thead className="bg-slate-50 text-slate-500">
                          <tr>
                            <th className="px-3 py-2 text-left">ရက်စွဲ</th>
                            <th className="px-3 py-2 text-left">Payment No</th>
                            <th className="px-3 py-2 text-left">Voucher</th>
                            <th className="px-3 py-2 text-right">ဆပ်ငွေ</th>
                            <th className="px-3 py-2 text-right">လက်ကျန် (ယခု)</th>
                            <th className="px-3 py-2 text-left">နည်း</th>
                            <th className="px-3 py-2 text-right">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                          {supplierPaymentHistory.flatMap((payment) => {
                            const paidAt = payment.paymentDate
                              ? new Date(payment.paymentDate).toLocaleString('en-GB', {
                                  day: '2-digit', month: 'short', year: 'numeric',
                                  hour: '2-digit', minute: '2-digit'
                                })
                              : '-';
                            const rows = (payment.allocations && payment.allocations.length > 0)
                              ? payment.allocations
                              : [{ purchaseId: 0, purchaseCode: payment.advanceAmount > 0 ? '(Advance only)' : '-', amount: payment.allocatedAmount || 0, remainingDue: 0 }];
                            return rows.map((alloc, idx) => (
                              <tr key={`${payment.id}-${alloc.purchaseId || 'adv'}-${idx}`} className={`hover:bg-slate-50/80 ${payment.voided ? 'opacity-60' : ''}`}>
                                <td className="px-3 py-2 whitespace-nowrap text-slate-600">{idx === 0 ? paidAt : ''}</td>
                                <td className="px-3 py-2 font-mono font-semibold text-slate-700">
                                  {idx === 0 && (
                                    <span className="inline-flex flex-wrap items-center gap-1.5">
                                      {payment.paymentNo}
                                      {payment.voided && (
                                        <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[9px] font-black uppercase text-rose-700" title={payment.voidReason || ''}>Voided</span>
                                      )}
                                    </span>
                                  )}
                                </td>
                                <td className="px-3 py-2 font-bold text-indigo-700">{alloc.purchaseCode}</td>
                                <td className="px-3 py-2 text-right font-bold text-emerald-700">{money(alloc.amount)}</td>
                                <td className="px-3 py-2 text-right text-rose-600">{alloc.purchaseId ? money(alloc.remainingDue) : '-'}</td>
                                <td className="px-3 py-2 text-slate-500">{idx === 0 ? (payment.paymentMethodName || '-') : ''}</td>
                                <td className="px-3 py-2 text-right">
                                  {idx === 0 && !payment.voided && (
                                    <button
                                      type="button"
                                      disabled={supplierPaymentSaving}
                                      onClick={() => void voidSupplierPayment(payment)}
                                      className="rounded border border-rose-200 px-2 py-1 text-[10px] font-bold text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                                    >
                                      Void
                                    </button>
                                  )}
                                  {idx === 0 && payment.voided && (
                                    <span className="text-[10px] text-slate-400">{payment.voidedBy || 'voided'}</span>
                                  )}
                                </td>
                              </tr>
                            ));
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </div>
            <div className="border-t border-slate-100 p-4"><button onClick={saveSupplierPayment} disabled={supplierPaymentSaving || supplierPaymentSupplierId<=0 || supplierPaymentMethodId<=0 || supplierPaymentAmount<=0} className="w-full rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white hover:bg-blue-700 disabled:bg-slate-300">{supplierPaymentSaving ? 'Saving...' : 'Allocate & Pay Supplier'}</button></div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseManagement;
