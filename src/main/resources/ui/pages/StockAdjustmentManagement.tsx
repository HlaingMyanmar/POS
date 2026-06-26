import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Eye,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  X
} from 'lucide-react';
import Swal from 'sweetalert2';
import { useDataEvents } from '../hooks/useDataEvents';
import { productService } from '../services/productapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { staffService } from '../services/staffapiservice';
import { stockAdjustmentApiService } from '../services/stockadjustmentapiservice';
import { AdjustmentType, ProductDTO, ProductSerialDTO, StaffDTO, StockAdjustmentDTO } from '../types';

type TypeFilter = 'ALL' | AdjustmentType;

type JournalLine = {
  side: 'DR' | 'CR';
  account: string;
};

const nowLocalDateTime = () => {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

const badgeClass: Record<AdjustmentType, string> = {
  [AdjustmentType.DAMAGE]: 'bg-rose-100 text-rose-700 border border-rose-200',
  [AdjustmentType.LOSS]: 'bg-orange-100 text-orange-700 border border-orange-200',
  [AdjustmentType.FOUND]: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
  [AdjustmentType.CORRECTION]: 'bg-indigo-100 text-indigo-700 border border-indigo-200'
};

const adjustmentLabel: Record<AdjustmentType, string> = {
  [AdjustmentType.DAMAGE]: 'DAMAGE - ပျက်စီး',
  [AdjustmentType.LOSS]: 'LOSS - ပျောက်',
  [AdjustmentType.FOUND]: 'FOUND - ပြန်တွေ့',
  [AdjustmentType.CORRECTION]: 'CORRECTION - ပြင်ဆင်'
};

const statusKey = (value: unknown) => String(value || '').replace(/[\s-]+/g, '_').toUpperCase();
const isAvailable = (value: unknown) => statusKey(value) === 'AVAILABLE';
const isDamagedOrLost = (value: unknown) => ['DAMAGED', 'LOST'].includes(statusKey(value));

const productLabel = (product?: ProductDTO | null) => {
  if (!product) return '';
  return `${product.name}${product.productCode ? ` (${product.productCode})` : ''}`;
};

const staffLabel = (staff?: StaffDTO | null) => {
  if (!staff) return '';
  return `${staff.name}${staff.role ? ` (${staff.role})` : ''}`;
};

const splitSerials = (value?: string) =>
  (value || '')
    .split(',')
    .map((serial) => serial.trim())
    .filter(Boolean);

const journalPreview = (type: AdjustmentType, qtyChange: number): JournalLine[] => {
  if (type === AdjustmentType.DAMAGE || type === AdjustmentType.LOSS) {
    return [
      { side: 'DR', account: 'Inventory Loss' },
      { side: 'CR', account: 'Inventory/Stock' }
    ];
  }

  if (type === AdjustmentType.FOUND) {
    return [
      { side: 'DR', account: 'Inventory/Stock' },
      { side: 'CR', account: 'Inventory Gain' }
    ];
  }

  if (qtyChange >= 0) {
    return [
      { side: 'DR', account: 'Inventory/Stock' },
      { side: 'CR', account: 'Inventory Over' }
    ];
  }

  return [
    { side: 'DR', account: 'Inventory Short' },
    { side: 'CR', account: 'Inventory/Stock' }
  ];
};

const StockAdjustmentManagement: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [masterLoading, setMasterLoading] = useState(true);
  const [serialsLoading, setSerialsLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [viewRow, setViewRow] = useState<StockAdjustmentDTO | null>(null);
  const [showJournal, setShowJournal] = useState(true);

  const [rows, setRows] = useState<StockAdjustmentDTO[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(20);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [productSerials, setProductSerials] = useState<ProductSerialDTO[]>([]);
  const [staffs, setStaffs] = useState<StaffDTO[]>([]);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const [productId, setProductId] = useState(0);
  const [productSearch, setProductSearch] = useState('');
  const [productOpen, setProductOpen] = useState(false);
  const [staffId, setStaffId] = useState(0);
  const [staffSearch, setStaffSearch] = useState('');
  const [staffOpen, setStaffOpen] = useState(false);
  const [adjustmentDate, setAdjustmentDate] = useState(nowLocalDateTime());
  const [adjustmentType, setAdjustmentType] = useState<AdjustmentType>(AdjustmentType.DAMAGE);
  const [qtyInput, setQtyInput] = useState('');
  const [serialInputs, setSerialInputs] = useState<string[]>(['']);
  const [reason, setReason] = useState('');

  const loadRows = useCallback(async (page: number, size: number, search: string) => {
    setLoading(true);
    try {
      const data = await stockAdjustmentApiService.getAll(page, size, search);
      setRows(data.content);
      setTotalElements(data.totalElements);
      setTotalPages(data.totalPages);
      setCurrentPage(data.pageNumber);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load stock adjustments', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMaster = useCallback(async () => {
    setMasterLoading(true);
    try {
      const [productRows, staffRows] = await Promise.all([
        productService.getAll(),
        staffService.getAll()
      ]);
      setProducts(productRows || []);
      setStaffs(staffRows || []);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load stock adjustment master data', 'error');
    } finally {
      setMasterLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadMaster();
  }, [loadMaster]);

  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = setTimeout(() => {
      setCurrentPage(0);
      setDebouncedSearch(search);
    }, 400);
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current); };
  }, [search]);

  useEffect(() => {
    void loadRows(currentPage, pageSize, debouncedSearch);
  }, [loadRows, currentPage, pageSize, debouncedSearch]);

  useDataEvents(['Stock', 'Product'], () => void loadRows(currentPage, pageSize, debouncedSearch));

  const selectedProduct = useMemo(() => products.find((row) => row.id === productId) || null, [products, productId]);
  const serialProduct = selectedProduct ? selectedProduct.hasSerial !== false : false;

  const currentStock = useMemo(() => {
    if (!selectedProduct) return 0;
    if (!serialProduct) return Number(selectedProduct.stockQty ?? selectedProduct.currentStock ?? 0);
    return productSerials.filter((serial) => serial.productId === selectedProduct.id && isAvailable(serial.status)).length;
  }, [productSerials, selectedProduct, serialProduct]);

  const serialPool = useMemo(() => {
    if (!serialProduct || !selectedProduct) return [];

    const allowed = productSerials.filter((serial) => {
      if (serial.productId !== selectedProduct.id) return false;
      if (adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) return isAvailable(serial.status);
      if (adjustmentType === AdjustmentType.FOUND) return isDamagedOrLost(serial.status);
      return true;
    });

    return Array.from(new Set<string>(allowed.map((serial) => serial.serialNumber.trim()).filter(Boolean))).sort((a, b) => a.localeCompare(b));
  }, [adjustmentType, productSerials, selectedProduct, serialProduct]);

  const normalizedSerials = useMemo(() => serialInputs.map((serial) => serial.trim()).filter(Boolean), [serialInputs]);

  const qtyChange = useMemo(() => {
    if (serialProduct) {
      const count = normalizedSerials.length;
      if (adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) return -count;
      return count;
    }

    const raw = qtyInput.trim();
    if (!raw) return 0;
    const parsed = Math.trunc(Number(raw));
    if (Number.isNaN(parsed)) return 0;

    if (adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) return -Math.abs(parsed);
    if (adjustmentType === AdjustmentType.FOUND) return Math.abs(parsed);
    return parsed;
  }, [adjustmentType, normalizedSerials.length, qtyInput, serialProduct]);

  const expectedAfter = currentStock + qtyChange;
  const journalLines = useMemo(() => journalPreview(adjustmentType, qtyChange), [adjustmentType, qtyChange]);

  const serialLookup = useMemo(() => {
    const map = new Map<string, ProductSerialDTO>();
    productSerials.forEach((serial) => {
      map.set(serial.serialNumber.trim().toUpperCase(), serial);
    });
    return map;
  }, [productSerials]);

  const validationErrors = useMemo(() => {
    const errors: string[] = [];

    if (productId <= 0) errors.push('Product is required.');
    if (staffId <= 0) errors.push('Staff is required.');
    if (!adjustmentType) errors.push('Adjustment type is required.');

    if (serialProduct) {
      if (normalizedSerials.length === 0) errors.push('At least one serial number is required.');
      if (Math.abs(qtyChange) !== normalizedSerials.length) errors.push('Serial count must match qty change.');

      const uniqueSerials = new Set(normalizedSerials.map((serial) => serial.toUpperCase()));
      if (uniqueSerials.size !== normalizedSerials.length) errors.push('Duplicate serial numbers are not allowed.');

      const invalidSerial = normalizedSerials.find((serial) => {
        const serialRow = serialLookup.get(serial.toUpperCase());
        if (!serialRow || serialRow.productId !== productId) return true;
        if (adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) return !isAvailable(serialRow.status);
        if (adjustmentType === AdjustmentType.FOUND) return !isDamagedOrLost(serialRow.status);
        return false;
      });
      if (invalidSerial) errors.push(`Serial '${invalidSerial}' is not allowed for this adjustment type.`);
    } else {
      if (qtyChange === 0) errors.push('Qty change is required.');
      if ((adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) && qtyChange >= 0) errors.push('Damage/Loss must reduce stock.');
      if (adjustmentType === AdjustmentType.FOUND && qtyChange <= 0) errors.push('Found must increase stock.');
    }

    if ((adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) && !reason.trim()) {
      errors.push('Reason is required for damage/loss.');
    }

    if (expectedAfter < 0) errors.push('Qty after cannot be negative.');

    return errors;
  }, [adjustmentType, expectedAfter, normalizedSerials, productId, qtyChange, reason, serialLookup, serialProduct, staffId]);

  const validForm = validationErrors.length === 0;

  const resetForm = useCallback(() => {
    setProductId(0);
    setProductSearch('');
    setStaffId(0);
    setStaffSearch('');
    setAdjustmentDate(nowLocalDateTime());
    setAdjustmentType(AdjustmentType.DAMAGE);
    setQtyInput('');
    setSerialInputs(['']);
    setReason('');
    setShowJournal(true);
    setProductSerials([]);
  }, []);

  const openForm = () => {
    resetForm();
    setShowForm(true);
  };

  const handleProductSelect = (product: ProductDTO) => {
    setProductId(product.id);
    setProductSearch(productLabel(product));
    setProductOpen(false);
    setSerialInputs(['']);
    setQtyInput('');
    setProductSerials([]);
    if (product.hasSerial !== false) {
      setSerialsLoading(true);
      productSerialService.getByProductId(product.id)
        .then((data) => setProductSerials(data || []))
        .catch(() => {})
        .finally(() => setSerialsLoading(false));
    }
  };

  const handleProductSearch = (value: string) => {
    setProductSearch(value);
    const matched = products.find((product) => productLabel(product).toLowerCase() === value.toLowerCase());
    setProductId(matched?.id || 0);
    setProductOpen(true);
    setSerialInputs(['']);
    setQtyInput('');
  };

  const handleStaffSelect = (staff: StaffDTO) => {
    setStaffId(staff.id);
    setStaffSearch(staffLabel(staff));
    setStaffOpen(false);
  };

  const handleStaffSearch = (value: string) => {
    setStaffSearch(value);
    const matched = staffs.find((staff) => staffLabel(staff).toLowerCase() === value.toLowerCase());
    setStaffId(matched?.id || 0);
    setStaffOpen(true);
  };

  const filteredProducts = useMemo(() => {
    const query = productSearch.trim().toLowerCase();
    if (!query) return products;
    return products.filter((product) =>
      [product.name, product.productCode]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(query))
    );
  }, [productSearch, products]);

  const filteredStaffs = useMemo(() => {
    const query = staffSearch.trim().toLowerCase();
    if (!query) return staffs;
    return staffs.filter((staff) =>
      [staff.name, staff.role, staff.phone]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(query))
    );
  }, [staffSearch, staffs]);

  const handleQtyInput = (value: string) => {
    if (value === '') {
      setQtyInput('');
      return;
    }

    const parsed = Math.trunc(Number(value));
    if (Number.isNaN(parsed)) return;

    if (adjustmentType === AdjustmentType.CORRECTION) {
      setQtyInput(String(parsed));
      return;
    }

    setQtyInput(String(Math.abs(parsed)));
  };

  const handleSerialChange = (index: number, value: string) => {
    setSerialInputs((prev) => prev.map((serial, serialIndex) => (serialIndex === index ? value : serial)));
  };

  const addSerialRow = () => setSerialInputs((prev) => [...prev, '']);
  const removeSerialRow = (index: number) => setSerialInputs((prev) => (prev.length <= 1 ? prev : prev.filter((_, serialIndex) => serialIndex !== index)));

  const handleSave = async () => {
    if (!validForm) {
      Swal.fire('Validation', validationErrors[0] || 'Please complete the stock adjustment form.', 'warning');
      return;
    }

    setSaving(true);
    try {
      const payload: StockAdjustmentDTO = {
        productId,
        adjustmentType,
        qtyChange,
        qtyBefore: currentStock,
        qtyAfter: expectedAfter,
        serialNumbers: serialProduct ? normalizedSerials.join(',') : undefined,
        reason: reason.trim() || undefined,
        staffId,
        createdAt: adjustmentDate || undefined
      };

      await stockAdjustmentApiService.create(payload);
      setShowForm(false);
      resetForm();
      await loadRows(0, pageSize, debouncedSearch);
      Swal.fire({ icon: 'success', title: 'Stock adjustment saved', toast: true, position: 'top-end', timer: 1500, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to save stock adjustment', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleView = async (id?: number) => {
    if (!id) return;
    try {
      const data = await stockAdjustmentApiService.getById(id);
      setViewRow(data);
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Failed to load stock adjustment detail', 'error');
    }
  };

  const filteredRows = useMemo(() => {
    const from = dateFrom ? new Date(dateFrom) : null;
    const to = dateTo ? new Date(dateTo) : null;
    if (to) to.setHours(23, 59, 59, 999);

    return rows.filter((row) => {
      if (typeFilter !== 'ALL' && row.adjustmentType !== typeFilter) return false;
      if (!from && !to) return true;
      const created = new Date(row.createdAt || '');
      if (Number.isNaN(created.getTime())) return false;
      if (from && created < from) return false;
      if (to && created > to) return false;
      return true;
    });
  }, [dateFrom, dateTo, rows, typeFilter]);

  const stats = useMemo(() => ({
    total: totalElements,
    damageLoss: rows.filter((row) => row.adjustmentType === AdjustmentType.DAMAGE || row.adjustmentType === AdjustmentType.LOSS).length,
    found: rows.filter((row) => row.adjustmentType === AdjustmentType.FOUND).length,
    correction: rows.filter((row) => row.adjustmentType === AdjustmentType.CORRECTION).length
  }), [rows, totalElements]);

  const summaryWarnings = useMemo(() => {
    const warnings: string[] = [];

    if ((adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) && expectedAfter < 0) {
      warnings.push('Damage/Loss adjustment would make stock negative.');
    }

    if (serialProduct && adjustmentType === AdjustmentType.FOUND) {
      const invalidFound = normalizedSerials.find((serial) => {
        const serialRow = serialLookup.get(serial.toUpperCase());
        return !serialRow || !isDamagedOrLost(serialRow.status);
      });
      if (invalidFound) warnings.push(`Found serial '${invalidFound}' is not Damaged/Lost.`);
    }

    return warnings;
  }, [adjustmentType, expectedAfter, normalizedSerials, serialLookup, serialProduct]);

  const loadingState = loading || masterLoading;

  if (loadingState && !showForm && rows.length === 0) {
    return (
      <div className="h-full flex items-center justify-center">
        <RefreshCw size={28} className="animate-spin text-indigo-600" />
      </div>
    );
  }

  if (showForm) {
    return (
      <div className="w-full max-w-none space-y-6">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <h2 className="text-xl font-bold text-slate-800 text-left">New Stock Adjustment</h2>
            <p className="text-xs text-slate-500 mt-1">Record damage, loss, found, and stock correction with serial-aware validation.</p>
          </div>
          <button
            onClick={() => {
              setShowForm(false);
              resetForm();
            }}
            className="inline-flex w-full sm:w-auto justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50"
          >
            <ArrowLeft size={14} /> Back
          </button>
        </div>

        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="xl:col-span-2 space-y-5">
            <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-5">
              <div className="space-y-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">Section 1 - Product Selection</h3>
                  <p className="text-[11px] text-slate-500 mt-1">Select product and staff, then choose the adjustment type.</p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Product</label>
                    <div className="relative">
                      <input
                        value={productSearch}
                        onChange={(e) => handleProductSearch(e.target.value)}
                        onFocus={() => setProductOpen(true)}
                        onBlur={() => setTimeout(() => setProductOpen(false), 120)}
                        placeholder="Search by product name/code"
                        className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${productId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                      />
                      {productOpen && (
                        <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                          {filteredProducts.length > 0 ? filteredProducts.map((product) => (
                            <button
                              key={product.id}
                              type="button"
                              onMouseDown={() => handleProductSelect(product)}
                              className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${productId === product.id ? 'bg-indigo-50' : ''}`}
                            >
                              <p className="text-sm font-semibold text-slate-800">{product.name}</p>
                              <p className="text-xs text-slate-500">{product.productCode || '-'} | {product.hasSerial !== false ? 'Has Serial' : 'Qty Only'}</p>
                            </button>
                          )) : <p className="px-3 py-2 text-xs text-slate-400">No product found.</p>}
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Staff</label>
                    <div className="relative">
                      <input
                        value={staffSearch}
                        onChange={(e) => handleStaffSearch(e.target.value)}
                        onFocus={() => setStaffOpen(true)}
                        onBlur={() => setTimeout(() => setStaffOpen(false), 120)}
                        placeholder="Select staff"
                        className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${staffId > 0 ? 'border-slate-200' : 'border-rose-200'}`}
                      />
                      {staffOpen && (
                        <div className="absolute z-20 mt-1 w-full max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white shadow-lg">
                          {filteredStaffs.length > 0 ? filteredStaffs.map((staff) => (
                            <button
                              key={staff.id}
                              type="button"
                              onMouseDown={() => handleStaffSelect(staff)}
                              className={`w-full px-3 py-2 text-left hover:bg-indigo-50 ${staffId === staff.id ? 'bg-indigo-50' : ''}`}
                            >
                              <p className="text-sm font-semibold text-slate-800">{staff.name}</p>
                              <p className="text-xs text-slate-500">{staff.role || '-'} {staff.phone ? `| ${staff.phone}` : ''}</p>
                            </button>
                          )) : <p className="px-3 py-2 text-xs text-slate-400">No staff found.</p>}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Adjustment Date</label>
                    <input
                      type="datetime-local"
                      value={adjustmentDate}
                      onChange={(e) => setAdjustmentDate(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Adjustment Type</label>
                    <select
                      value={adjustmentType}
                      onChange={(e) => setAdjustmentType(e.target.value as AdjustmentType)}
                      className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                    >
                      {(Object.values(AdjustmentType) as AdjustmentType[]).map((type) => (
                        <option key={type} value={type}>{adjustmentLabel[type]}</option>
                      ))}
                    </select>
                  </div>
                </div>

                {selectedProduct && (
                  <div className="flex flex-wrap items-center gap-2 text-xs">
                    <span className="inline-flex px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 font-semibold">
                      {selectedProduct.productCode || 'NO-CODE'}
                    </span>
                    <span className={`inline-flex px-2.5 py-1 rounded-full font-semibold ${serialProduct ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-700'}`}>
                      {serialProduct ? 'Has Serial' : 'Qty Only'}
                    </span>
                    <span className="inline-flex px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-700 font-semibold">
                      Current Stock: {currentStock}
                    </span>
                  </div>
                )}
              </div>

              <div className="space-y-3">
                <div>
                  <h3 className="text-sm font-bold text-slate-800">Section 2 - Qty / Serial</h3>
                  <p className="text-[11px] text-slate-500 mt-1">Use serial numbers for serial products; quantity input only for non-serial products.</p>
                </div>

                {serialProduct ? (
                  <div className="space-y-3">
                    <div className="flex items-center justify-between">
                      <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Serial Numbers</label>
                      <button
                        onClick={addSerialRow}
                        type="button"
                        className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-indigo-200 text-indigo-700 text-xs font-semibold hover:bg-indigo-50"
                      >
                        <Plus size={12} /> Add Serial
                      </button>
                    </div>

                    <div className="space-y-2">
                      {serialInputs.map((serial, index) => (
                        <div key={index} className="flex items-center gap-2">
                          <input
                            list="stock-adjustment-serial-options"
                            value={serial}
                            onChange={(e) => handleSerialChange(index, e.target.value)}
                            placeholder={`Serial #${index + 1}`}
                            className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                          />
                          <button
                            type="button"
                            onClick={() => removeSerialRow(index)}
                            className="p-2 rounded-lg border border-slate-200 text-slate-400 hover:text-rose-600 hover:border-rose-200 hover:bg-rose-50 disabled:opacity-50"
                            disabled={serialInputs.length <= 1}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      ))}
                    </div>

                    <datalist id="stock-adjustment-serial-options">
                      {serialPool.map((serial) => (
                        <option key={serial} value={serial} />
                      ))}
                    </datalist>

                    {serialsLoading && (
                      <p className="text-[11px] text-indigo-500 flex items-center gap-1.5">
                        <RefreshCw size={11} className="animate-spin" /> Loading serials...
                      </p>
                    )}

                    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                      Qty change auto-calculated from serial count:
                      <span className="font-semibold text-slate-700"> {qtyChange > 0 ? `+${qtyChange}` : qtyChange}</span>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Qty Change</label>
                    <input
                      type="number"
                      step="1"
                      value={qtyInput}
                      onChange={(e) => handleQtyInput(e.target.value)}
                      placeholder={adjustmentType === AdjustmentType.CORRECTION ? 'Allow positive or negative' : 'Enter quantity'}
                      className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 ${qtyChange !== 0 ? 'border-slate-200' : 'border-rose-200'}`}
                    />
                    <p className="text-[10px] text-slate-400">
                      {adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS
                        ? 'Damage/Loss shows positive input but saves as negative qty.'
                        : adjustmentType === AdjustmentType.FOUND
                          ? 'Found always saves as positive qty.'
                          : 'Correction supports positive and negative qty.'}
                    </p>
                  </div>
                )}
              </div>

              <div className="space-y-1.5">
                <h3 className="text-sm font-bold text-slate-800">Section 3 - Reason</h3>
                <textarea
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  rows={4}
                  placeholder="Reason is required for DAMAGE and LOSS"
                  className={`w-full px-3 py-2 bg-slate-50 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none ${reason.trim() || !(adjustmentType === AdjustmentType.DAMAGE || adjustmentType === AdjustmentType.LOSS) ? 'border-slate-200' : 'border-rose-200'}`}
                />
              </div>
            </div>
          </div>

          <div className="space-y-6">
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 sm:p-6 space-y-5 xl:sticky xl:top-20">
              <div className="flex items-center justify-between gap-3">
                <h3 className="font-bold text-slate-800 text-sm">Summary</h3>
                <span className={`inline-flex px-2.5 py-1 rounded-full text-[10px] font-bold ${badgeClass[adjustmentType]}`}>{adjustmentType}</span>
              </div>

              <div className="space-y-2 text-sm">
                <div className="flex justify-between gap-3"><span className="text-slate-500">Product</span><span className="font-medium text-slate-800 text-right">{selectedProduct ? productLabel(selectedProduct) : '-'}</span></div>
                <div className="flex justify-between gap-3"><span className="text-slate-500">Current Stock</span><span className="font-medium text-slate-800">{currentStock}</span></div>
                <div className="flex justify-between gap-3"><span className="text-slate-500">Qty Change</span><span className={`font-bold ${qtyChange < 0 ? 'text-rose-700' : qtyChange > 0 ? 'text-emerald-700' : 'text-slate-700'}`}>{qtyChange > 0 ? `+${qtyChange}` : qtyChange}</span></div>
                <div className={`flex justify-between gap-3 rounded-lg border px-3 py-2 ${expectedAfter < 0 ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-slate-50'}`}><span className={expectedAfter < 0 ? 'text-rose-700' : 'text-slate-500'}>Expected After</span><span className={`font-bold ${expectedAfter < 0 ? 'text-rose-700' : 'text-slate-800'}`}>{expectedAfter}</span></div>
              </div>

              {summaryWarnings.length > 0 && (
                <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 space-y-2">
                  {summaryWarnings.map((warning) => (
                    <p key={warning} className="text-[11px] text-rose-700 inline-flex items-start gap-1.5">
                      <AlertTriangle size={13} className="mt-0.5 shrink-0" /> {warning}
                    </p>
                  ))}
                </div>
              )}

              {validationErrors.length > 0 && (
                <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
                  <p className="text-[11px] font-semibold text-amber-700">Validation</p>
                  <ul className="mt-2 space-y-1 text-[11px] text-amber-700">
                    {validationErrors.slice(0, 4).map((error) => (
                      <li key={error}>{error}</li>
                    ))}
                  </ul>
                </div>
              )}

              <div className="rounded-xl border border-slate-200 overflow-hidden">
                <button type="button" onClick={() => setShowJournal((prev) => !prev)} className="w-full px-4 py-3 bg-slate-50 border-b border-slate-200 flex items-center justify-between text-sm font-bold text-slate-800">
                  <span>Journal Preview</span>
                  {showJournal ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
                {showJournal && (
                  <div className="p-4 space-y-3">
                    {journalLines.map((line, index) => (
                      <div key={`${line.side}-${index}`} className="flex items-center justify-between text-sm">
                        <span className={`inline-flex px-2 py-0.5 rounded text-[10px] font-bold ${line.side === 'DR' ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>{line.side}</span>
                        <span className="flex-1 px-3 text-slate-700">{line.account}</span>
                      </div>
                    ))}
                    <p className="text-[11px] text-slate-500">Note: Journal only created if cost_price &gt; 0.</p>
                  </div>
                )}
              </div>

              <button
                onClick={() => void handleSave()}
                disabled={!validForm || saving}
                className={`w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold transition-all ${validForm && !saving ? 'bg-indigo-600 text-white hover:bg-indigo-700' : 'bg-slate-100 text-slate-400 cursor-not-allowed'}`}
              >
                {saving ? <RefreshCw size={16} className="animate-spin" /> : <Save size={16} />} Save Adjustment
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
          <h2 className="text-xl font-bold text-slate-800 text-left">Stock Adjustment</h2>
          <p className="text-xs text-slate-500 mt-1">Track stock corrections, damage/loss, and found inventory with serial support.</p>
        </div>
        <div className="flex flex-col sm:flex-row sm:items-center gap-2">
          <button onClick={() => void loadRows(currentPage, pageSize, debouncedSearch)} className="inline-flex justify-center items-center gap-2 px-3 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-50">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
          <button onClick={openForm} className="inline-flex justify-center items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700">
            <Plus size={16} /> New Adjustment
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4"><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Total Adjustments</p><p className="text-2xl font-bold text-slate-800 mt-1">{stats.total}</p></div>
        <div className="bg-white rounded-xl border border-rose-100 shadow-sm p-4"><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Damage / Loss</p><p className="text-2xl font-bold text-rose-700 mt-1">{stats.damageLoss}</p></div>
        <div className="bg-white rounded-xl border border-emerald-100 shadow-sm p-4"><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Found</p><p className="text-2xl font-bold text-emerald-700 mt-1">{stats.found}</p></div>
        <div className="bg-white rounded-xl border border-indigo-100 shadow-sm p-4"><p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Corrections</p><p className="text-2xl font-bold text-indigo-700 mt-1">{stats.correction}</p></div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4">
        <div className="flex flex-col xl:flex-row xl:items-center gap-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search product name, staff name, reason..."
              className="w-full pl-9 pr-8 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
            />
            {loading && search && <RefreshCw size={14} className="absolute right-3 top-1/2 -translate-y-1/2 animate-spin text-indigo-500" />}
          </div>
          <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as TypeFilter)} className="w-full xl:w-auto px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-600">
            <option value="ALL">All</option>
            {(Object.values(AdjustmentType) as AdjustmentType[]).map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
          <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto_1fr] gap-2 xl:flex xl:items-center xl:gap-3">
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-full px-2 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-600" />
            <span className="hidden sm:flex items-center justify-center text-slate-300 text-xs">-</span>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-full px-2 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs font-medium text-slate-600" />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <h3 className="font-bold text-slate-800 text-sm">Adjustment History</h3>
          <span className="text-[11px] text-slate-400">{filteredRows.length} row(s)</span>
        </div>
        <div className="overflow-auto max-h-[65vh] custom-scrollbar">
          <table className="w-full min-w-[980px] lg:min-w-[1320px] text-left border-collapse">
            <thead className="sticky top-0 bg-white z-10 shadow-sm">
              <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold tracking-wider">
                <th className="px-4 py-3 border-b border-slate-100">#</th>
                <th className="px-4 py-3 border-b border-slate-100">Product</th>
                <th className="px-4 py-3 border-b border-slate-100">Type</th>
                <th className="px-4 py-3 border-b border-slate-100 text-right">Qty Before</th>
                <th className="px-4 py-3 border-b border-slate-100 text-right">Qty Change</th>
                <th className="px-4 py-3 border-b border-slate-100 text-right">Qty After</th>
                <th className="px-4 py-3 border-b border-slate-100">Serials</th>
                <th className="px-4 py-3 border-b border-slate-100">Reason</th>
                <th className="px-4 py-3 border-b border-slate-100">Staff</th>
                <th className="px-4 py-3 border-b border-slate-100">Date</th>
                <th className="px-4 py-3 border-b border-slate-100 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredRows.length === 0 ? (
                <tr><td colSpan={11} className="px-4 py-10 text-center text-slate-400 text-sm">No stock adjustments found.</td></tr>
              ) : filteredRows.map((row, index) => (
                <tr key={row.id || `${row.productId}-${index}`} className="hover:bg-slate-50 text-xs">
                  <td className="px-4 py-3 text-slate-500">{index + 1}</td>
                  <td className="px-4 py-3 text-slate-700 font-medium">
                    <div>{row.productName || `Product #${row.productId}`}</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">{row.productCode || '-'}</div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-1 rounded-full text-[10px] font-bold ${badgeClass[row.adjustmentType]}`}>{row.adjustmentType}</span>
                  </td>
                  <td className="px-4 py-3 text-right text-slate-700 font-semibold">{Number(row.qtyBefore) || 0}</td>
                  <td className={`px-4 py-3 text-right font-bold ${Number(row.qtyChange) < 0 ? 'text-rose-700' : 'text-emerald-700'}`}>
                    {Number(row.qtyChange) > 0 ? `+${row.qtyChange}` : row.qtyChange}
                  </td>
                  <td className="px-4 py-3 text-right text-slate-700 font-semibold">{Number(row.qtyAfter) || 0}</td>
                  <td className="px-4 py-3 text-slate-500 max-w-[220px] truncate">{row.serialNumbers || '-'}</td>
                  <td className="px-4 py-3 text-slate-500 max-w-[260px] truncate">{row.reason || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{row.staffName || '-'}</td>
                  <td className="px-4 py-3 text-slate-600">{row.createdAt ? new Date(row.createdAt).toLocaleString() : '-'}</td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => void handleView(row.id)} className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-indigo-50" title="View">
                      <Eye size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between px-1">
          <p className="text-xs text-slate-500">{totalElements} records · Page {currentPage + 1} of {totalPages}</p>
          <div className="flex items-center gap-1">
            <button onClick={() => setCurrentPage(0)} disabled={currentPage === 0} className="px-2 py-1 text-xs rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-50">«</button>
            <button onClick={() => setCurrentPage((p) => Math.max(0, p - 1))} disabled={currentPage === 0} className="px-2 py-1 text-xs rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-50">‹</button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              const start = Math.max(0, Math.min(currentPage - 2, totalPages - 5));
              return start + i;
            }).map((p) => (
              <button key={p} onClick={() => setCurrentPage(p)} className={`px-2 py-1 text-xs rounded border ${p === currentPage ? 'bg-indigo-600 text-white border-indigo-600' : 'border-slate-200 hover:bg-slate-50'}`}>{p + 1}</button>
            ))}
            <button onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))} disabled={currentPage >= totalPages - 1} className="px-2 py-1 text-xs rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-50">›</button>
            <button onClick={() => setCurrentPage(totalPages - 1)} disabled={currentPage >= totalPages - 1} className="px-2 py-1 text-xs rounded border border-slate-200 disabled:opacity-40 hover:bg-slate-50">»</button>
          </div>
        </div>
      )}

      {viewRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60">
          <div className="bg-white rounded-2xl shadow-xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="font-bold text-slate-800">Stock Adjustment #{viewRow.id}</h3>
              <button onClick={() => setViewRow(null)} className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg">
                <X size={18} />
              </button>
            </div>
            <div className="p-4 overflow-y-auto space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p className="text-slate-600"><span className="font-medium text-slate-500">Product:</span> {viewRow.productName || `Product #${viewRow.productId}`}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Type:</span> <span className={`inline-flex ml-1 px-2 py-0.5 rounded-full text-[10px] font-bold ${badgeClass[viewRow.adjustmentType]}`}>{viewRow.adjustmentType}</span></p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Qty Before:</span> {Number(viewRow.qtyBefore) || 0}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Qty Change:</span> <span className={Number(viewRow.qtyChange) < 0 ? 'text-rose-700 font-semibold' : 'text-emerald-700 font-semibold'}>{Number(viewRow.qtyChange) > 0 ? `+${viewRow.qtyChange}` : viewRow.qtyChange}</span></p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Qty After:</span> {Number(viewRow.qtyAfter) || 0}</p>
                <p className="text-slate-600"><span className="font-medium text-slate-500">Staff:</span> {viewRow.staffName || '-'}</p>
                <p className="text-slate-600 sm:col-span-2"><span className="font-medium text-slate-500">Serial Numbers:</span> {viewRow.serialNumbers || '-'}</p>
                <p className="text-slate-600 sm:col-span-2"><span className="font-medium text-slate-500">Reason:</span> {viewRow.reason || '-'}</p>
                <p className="text-slate-600 sm:col-span-2"><span className="font-medium text-slate-500">Date:</span> {viewRow.createdAt ? new Date(viewRow.createdAt).toLocaleString() : '-'}</p>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
                Journal note: <span className="font-medium text-slate-700">Journal entry created if cost_price &gt; 0</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StockAdjustmentManagement;
