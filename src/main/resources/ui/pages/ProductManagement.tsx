
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { productService } from '../services/productapiservice';
import { brandService } from '../services/brandapiservice';
import { categoryService } from '../services/categoryapiservice';
import { unitService } from '../services/unitapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { purchaseApiService } from '../services/purchaseapiservice';
import { AppRoute, ProductDTO, BrandDTO, CategoryDTO, UnitDTO, ProductType, ProductSerialDTO, SerialStatus } from '../types';
import {
  Loader2, Plus, Search, Trash2, Edit2, X,
  Box, ChevronLeft, ChevronRight, ChevronDown, ChevronUp,
  Tag, Package, Ruler, Layers, DollarSign,
  Save, Hash, Send, Barcode, Camera,
  CheckCircle2, AlertCircle, Filter, RotateCcw,
  ClipboardList, Eye, Info, LayoutList, Wallet,
  Settings2, AlertTriangle, ArrowLeft, Shield,
  Download, Upload, FileSpreadsheet, FileDown, CheckCheck
} from 'lucide-react';
import { useDataEvents } from '../hooks/useDataEvents';
import Swal from 'sweetalert2';
import BarcodeLabel from '../components/BarcodeLabel';
import BarcodeScannerCamera from '../components/BarcodeScannerCamera';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

interface CategoryOption {
  id: number;
  displayName: string;
}

interface ProductGroup {
  groupId: string;
  displayName: string;
  brandName: string;
  categoryName: string;
  brandId?: number;
  categoryId?: number;
  unitName: string;
  products: ProductDTO[]; 
  totalAvailable: number;
  totalUnits: number;
  groupStockValue: number;
}

const productTypeBadgeClass = (type: string) => {
  if (type === ProductType.NEW)        return 'bg-indigo-50 text-indigo-600 border-indigo-100';
  if (type === ProductType.SECOND_NEW) return 'bg-violet-50 text-violet-600 border-violet-100';
  return 'bg-amber-50 text-amber-600 border-amber-100'; // Second
};

const formatProductType = (type: string) => type.replace('_', ' ');

const formatWarranty = (product?: Partial<ProductDTO> | null) => {
  if (!product) return '-';
  const terms = String(product.warrantyTerms || '').trim();
  if (terms) return terms;
  const months = Number(product.warrantyMonths || 0);
  if (months <= 0) return '-';
  if (months % 12 === 0) return `${months / 12} နှစ်`;
  return `${months} လ`;
};

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const toDateOnly = (value?: string) => value ? String(value).slice(0, 10) : '';
const todayDateOnly = () => new Date().toISOString().slice(0, 10);
const addWarrantyDays = (dateIso: string, days: number) => {
  const date = new Date(`${dateIso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
};
const countWarrantyDays = (start?: string, end?: string) => {
  if (!start || !end) return 0;
  const startTime = new Date(`${toDateOnly(start)}T00:00:00`).getTime();
  const endTime = new Date(`${toDateOnly(end)}T00:00:00`).getTime();
  if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) return 0;
  return Math.round((endTime - startTime) / MS_PER_DAY);
};
const formatSerialWarranty = (serial?: Partial<ProductSerialDTO> | null, fallbackProduct?: Partial<ProductDTO> | null) => {
  const months = Number(serial?.warrantyMonths ?? 0) || 0;
  if (months > 0 && months % 12 === 0) return `${months / 12} နှစ်`;
  if (months > 0) return `${months} လ`;
  const days = countWarrantyDays(serial?.warrantyStartDate, serial?.warrantyEndDate);
  if (days > 0) return `${days} ရက်`;
  return formatWarranty(fallbackProduct);
};

const InventoryMetricCard: React.FC<{
  label: string;
  value: string;
  icon: React.ReactNode;
  tone: 'indigo' | 'slate' | 'emerald' | 'amber' | 'rose' | 'teal';
}> = ({ label, value, icon, tone }) => {
  const styles = {
    indigo: 'bg-indigo-50 text-indigo-700 border-indigo-100',
    slate: 'bg-slate-50 text-slate-700 border-slate-200',
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    amber: 'bg-amber-50 text-amber-700 border-amber-100',
    rose: 'bg-rose-50 text-rose-700 border-rose-100',
    teal: 'bg-teal-50 text-teal-700 border-teal-100',
  }[tone];

  return (
    <div className={`rounded-lg border ${styles} px-3 py-2.5 flex items-center gap-3 min-w-0`}>
      <div className="shrink-0">{icon}</div>
      <div className="min-w-0">
        <p className="text-[10px] font-black uppercase tracking-wide opacity-75 truncate">{label}</p>
        <p className="text-sm font-black tabular-nums truncate">{value}</p>
      </div>
    </div>
  );
};

const OpeningStockBadge: React.FC<{ product: ProductDTO }> = ({ product }) => {
  const qtyStock = Number(product.stockQty ?? product.currentStock ?? 0);
  const cost = Number(product.costPrice ?? 0);
  const serialTracked = product.hasSerial !== false;

  if (serialTracked) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-md border bg-amber-50 text-amber-700 border-amber-100 text-[10px] font-black whitespace-nowrap">
        <Shield size={11} /> Purchase မှ Serial
      </span>
    );
  }

  if (qtyStock > 0) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-md border bg-slate-50 text-slate-600 border-slate-200 text-[10px] font-black whitespace-nowrap">
        <Package size={11} /> Stock ရှိပြီး
      </span>
    );
  }

  if (cost <= 0) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-md border bg-rose-50 text-rose-700 border-rose-100 text-[10px] font-black whitespace-nowrap">
        <AlertTriangle size={11} /> Cost လိုသည်
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-md border bg-emerald-50 text-emerald-700 border-emerald-100 text-[10px] font-black whitespace-nowrap">
      <CheckCircle2 size={11} /> Opening Ready
    </span>
  );
};

const getCanDelete = (): boolean => {
  try {
    const raw = sessionStorage.getItem('sspd_user');
    if (!raw) return false;
    const u = JSON.parse(raw);
    if ((u.roles || []).some((r: string) => r === 'ADMINISTRATOR' || r === 'ROLE_ADMINISTRATOR')) return true;
    return (u.permissions || []).includes('CAN_ACCESS_PRODUCT_DELETE');
  } catch { return false; }
};

const ProductManagement: React.FC = () => {
  const navigate = useNavigate();
  const canDelete = getCanDelete();
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [lowStockProducts, setLowStockProducts] = useState<ProductDTO[]>([]);
  const [brands, setBrands] = useState<BrandDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [units, setUnits] = useState<UnitDTO[]>([]);
  const [allSerials, setAllSerials] = useState<ProductSerialDTO[]>([]);
  
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showScanSearch, setShowScanSearch] = useState(false);
  const [filterCondition, setFilterCondition] = useState<'All' | 'New' | 'Second' | 'Second_New'>('All');
  const [filterStatus, setFilterStatus] = useState<'All' | 'In Stock' | 'Out of Stock'>('All');
  const [filterBrandId, setFilterBrandId] = useState<'All' | number>('All');
  const [filterCategoryId, setFilterCategoryId] = useState<'All' | number>('All');
  const [filterTracking, setFilterTracking] = useState<'All' | 'Serial' | 'Qty'>('All');
  const [filterLowStockOnly, setFilterLowStockOnly] = useState(false);
  const [showFilterTools, setShowFilterTools] = useState(false);
  
  // Per-group filter for nested rows
  const [nestedFilters, setNestedFilters] = useState<Record<string, 'All' | 'New' | 'Second' | 'Second_New'>>({});

  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [isSerialModalOpen, setIsSerialModalOpen] = useState(false);
  const [selectedProductForSerial, setSelectedProductForSerial] = useState<ProductDTO | null>(null);
  const [selectedSerialGroup, setSelectedSerialGroup] = useState<ProductGroup | null>(null);
  const [editingSerialId, setEditingSerialId] = useState<number | null>(null);
  const [serialEditForm, setSerialEditForm] = useState<{ serialNumber: string; status: SerialStatus; warrantyValue: number; warrantyUnit: 'ရက်' | 'လ' | 'နှစ်' }>({
    serialNumber: '',
    status: SerialStatus.AVAILABLE,
    warrantyValue: 0,
    warrantyUnit: 'လ',
  });
  const [serialEditSaving, setSerialEditSaving] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductDTO | null>(null);
  const [newSerialInput, setNewSerialInput] = useState('');
  const [isBarcodeModalOpen, setIsBarcodeModalOpen] = useState(false);
  const [barcodeProduct, setBarcodeProduct] = useState<ProductDTO | null>(null);

  // Assign-Serials modal (for qty-only products with existing stock)
  const [isAssignSerialsOpen, setIsAssignSerialsOpen] = useState(false);
  const [assignSerialsProduct, setAssignSerialsProduct] = useState<ProductDTO | null>(null);
  const [assignSerialsInputs, setAssignSerialsInputs] = useState<string[]>([]);
  const [assignSerialsWarranty, setAssignSerialsWarranty] = useState<number>(0);
  const [assignSerialsSaving, setAssignSerialsSaving] = useState(false);

  // Form warranty value+unit (so users don't accidentally enter months instead of years)
  const [formWarrantyValue, setFormWarrantyValue] = useState(0);
  const [formWarrantyUnit, setFormWarrantyUnit] = useState<'ရက်' | 'လ' | 'နှစ်'>('နှစ်');

  // Warranty quick-edit modal (qty products)
  const [isWarrantyModalOpen, setIsWarrantyModalOpen] = useState(false);
  const [warrantyProduct, setWarrantyProduct] = useState<ProductDTO | null>(null);
  const [warrantyValue, setWarrantyValue] = useState(0);
  const [warrantyUnit, setWarrantyUnit] = useState<'ရက်' | 'လ' | 'နှစ်'>('လ');
  const [warrantyNote, setWarrantyNote] = useState('');
  const [warrantySaving, setWarrantySaving] = useState(false);

  const [formData, setFormData] = useState<Partial<ProductDTO>>({
    name: '',
    hasSerial: true,
    stockQty: 0,
    productType: ProductType.NEW,
    sellingPrice: 0,
    costPrice: 0,
    reorderLevel: 0,
    warrantyMonths: 0,
    warrantyTerms: '',
    remark: '',
    categoryId: undefined,
    brandId: undefined,
    unitId: undefined
  });
  const [saving, setSaving] = useState(false);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importDragging, setImportDragging] = useState(false);
  const [importLoading, setImportLoading] = useState(false);
  const [importResult, setImportResult] = useState<{ successCount: number; errorCount: number; errors: { row: number; message: string }[] } | null>(null);
  const [exportLoading, setExportLoading] = useState(false);
  const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [formPhoto, setFormPhoto] = useState<string | undefined>(undefined);
  const [photoChanged, setPhotoChanged] = useState(false);
  const [viewFormPhoto, setViewFormPhoto] = useState<string | null>(null);

  const compressImage = (file: File, maxDim = 320, quality = 0.75): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement('canvas');
          let w = img.width, h = img.height;
          if (w > maxDim || h > maxDim) {
            if (w > h) { h = Math.round(h * maxDim / w); w = maxDim; }
            else { w = Math.round(w * maxDim / h); h = maxDim; }
          }
          canvas.width = w; canvas.height = h;
          canvas.getContext('2d')!.drawImage(img, 0, 0, w, h);
          resolve(canvas.toDataURL('image/jpeg', quality));
        };
        img.onerror = reject;
        img.src = e.target!.result as string;
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });

  const handlePhotoUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const compressed = await compressImage(file);
      setFormPhoto(compressed);
      setPhotoChanged(true);
    } catch {
      Swal.fire('Error', 'Failed to process image', 'error');
    }
    e.target.value = '';
  };

  const [brandSearch, setBrandSearch] = useState('');
  const [categorySearch, setCategorySearch] = useState('');
  const [unitSearch, setUnitSearch] = useState('');
  const [brandOpen, setBrandOpen] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [unitOpen, setUnitOpen] = useState(false);

  const [isPricePickerOpen, setIsPricePickerOpen] = useState(false);
  const [pricePickerRows, setPricePickerRows] = useState<{ purchaseCode: string; supplierName: string; purchaseDate: string; unitCost: number; qty: number }[]>([]);
  const [pricePickerLoading, setPricePickerLoading] = useState(false);
  const [pickedCost, setPickedCost] = useState<number | null>(null);
  const [pickedSelling, setPickedSelling] = useState<string>('');

  const fetchSerials = useCallback(async () => {
    try {
      const sData = await productSerialService.getAll();
      setAllSerials(sData);
    } catch (error) {
      console.error("Failed to load serial data", error);
    }
  }, []);

  const genSerials = (productCode: string, qty: number): string[] => {
    const d = new Date();
    const ds = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    return Array.from({ length: qty }, (_, i) => `${productCode}-${ds}-${String(i + 1).padStart(3, '0')}`);
  };

  const getAssignableSerialQty = (product: ProductDTO) => {
    if (product.hasSerial !== false) return Number(product.unlinkedQty ?? 0);
    return Number(product.stockQty ?? product.currentStock ?? 0);
  };

  const handleOpenAssignSerials = (product: ProductDTO) => {
    const qty = getAssignableSerialQty(product);
    setAssignSerialsProduct(product);
    setAssignSerialsInputs(genSerials(product.productCode || String(product.id), qty));
    setAssignSerialsWarranty(product.warrantyMonths ?? 0);
    setIsAssignSerialsOpen(true);
  };

  const handleSaveAssignSerials = async () => {
    if (!assignSerialsProduct) return;
    const serials = assignSerialsInputs.map((s) => s.trim());
    if (serials.some((s) => !s)) {
      Swal.fire('စစ်ဆေးမှု', 'စီရီရယ်နံပါတ်အားလုံးကို ဖြည့်ရန်လိုသည်။', 'warning');
      return;
    }
    setAssignSerialsSaving(true);
    try {
      await productService.assignSerials(assignSerialsProduct.id, {
        serialNumbers: serials,
        warrantyMonths: assignSerialsWarranty,
      });
      setIsAssignSerialsOpen(false);
      setAssignSerialsProduct(null);
      await fetchData();
      Swal.fire({ icon: 'success', title: 'စီရီရယ် သတ်မှတ်ပြီး', text: `${serials.length} ခု စီရီရယ်ဖန်တီးပြီး။ ကုန်ပစ္စည်းကို ယခုစီရီရယ်ဖြင့် ခြေရာခံမည်။`, timer: 2500, showConfirmButton: false });
    } catch (err: any) {
      Swal.fire('Error', err?.response?.data?.message || err.message || 'Failed to assign serials', 'error');
    } finally {
      setAssignSerialsSaving(false);
    }
  };

  const fetchData = useCallback(async () => {
    try {
      const [pData, bData, cTreeData, uData, sData, lowStockData] = await Promise.all([
        productService.getAll(),
        brandService.getAll(),
        categoryService.getTree(),
        unitService.getAll(),
        productSerialService.getAll(),
        productService.getLowStock()
      ]);
      setProducts(pData);
      setBrands(bData.filter(b => b.isActive));
      setCategories(cTreeData);
      setUnits(uData);
      setAllSerials(sData);
      setLowStockProducts(lowStockData);
    } catch (error) {
      console.error("Failed to load inventory data", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);
  useRefreshOnTabActivate(fetchData);

  useDataEvents(['Product', 'Stock', 'Sale'], fetchData);

  const handleExportExcel = async () => {
    setExportLoading(true);
    try {
      await productService.exportExcel();
    } catch {
      Swal.fire('Error', 'Export မအောင်မြင်ပါ', 'error');
    } finally {
      setExportLoading(false);
    }
  };

  const handleExportFiltered = () => {
    const rows: string[][] = [
      ['ကုဒ်', 'ကုန်ပစ္စည်းနာမည်', 'ဘရန်း', 'အမျိုးအစား', 'ယူနစ်', 'ပစ္စည်းအခြေအနေ', 'ခြေရာခံနည်း', 'ကုန်ကျစရိတ်', 'ရောင်းဈေး', 'ရောင်းနိုင်သောပမာဏ', 'ပြန်မှာမည့်အဆင့်', 'အာမခံ'],
    ];
    productGroups.forEach(group => {
      group.products.forEach(p => {
        const available = getAvailableCount(p);
        rows.push([
          p.productCode || '',
          p.name,
          p.brandName || '',
          p.categoryName || '',
          p.unitName || '',
          p.productType === 'NEW' ? 'အသစ်' : p.productType === 'SECOND_NEW' ? 'အသစ်နှင့်တူသော' : 'အသုံးပြုပြီး',
          p.hasSerial !== false ? 'Serial' : 'Qty',
          String(p.costPrice ?? 0),
          String(p.sellingPrice ?? 0),
          String(available),
          String(p.reorderLevel ?? 0),
          formatWarranty(p),
        ]);
      });
    });

    const csvContent = rows.map(r => r.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\r\n');
    const bom = '﻿';
    const blob = new Blob([bom + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `products_filtered_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleDownloadTemplate = async () => {
    setTemplateLoading(true);
    try {
      await productService.downloadTemplate();
    } catch {
      Swal.fire('Error', 'Template ဒေါင်းလုပ်မအောင်မြင်ပါ', 'error');
    } finally {
      setTemplateLoading(false);
    }
  };

  const handleImportFileChange = (file: File | null) => {
    setImportFile(file);
    setImportResult(null);
  };

  const handleImportSubmit = async () => {
    if (!importFile) return;
    setImportLoading(true);
    try {
      const res = await productService.importExcel(importFile);
      setImportResult(res.data);
      if (res.data.successCount > 0) await fetchData();
    } catch (err: any) {
      Swal.fire('Error', err?.message || 'Import မအောင်မြင်ပါ', 'error');
    } finally {
      setImportLoading(false);
    }
  };

  const handleCloseImportModal = () => {
    setIsImportModalOpen(false);
    setImportFile(null);
    setImportResult(null);
    setImportDragging(false);
  };

  const getCategoryOptions = useCallback((nodes: CategoryDTO[], level = 0): CategoryOption[] => {
    return nodes.reduce((acc: CategoryOption[], node) => {
      const indentation = "\u00A0\u00A0".repeat(level * 2);
      const prefix = level > 0 ? "↳ " : "";
      acc.push({ id: node.id, displayName: `${indentation}${prefix}${node.name}` });
      if (node.children && node.children.length > 0) {
        acc.push(...getCategoryOptions(node.children, level + 1));
      }
      return acc;
    }, []);
  }, []);

  const flatCategoryOptions = useMemo(() => getCategoryOptions(categories), [categories, getCategoryOptions]);

  const getAvailableCount = useCallback((product: ProductDTO) => (
    product.hasSerial !== false
      ? allSerials.filter((s) => s.productId === product.id && s.status === SerialStatus.AVAILABLE).length
      : (product.stockQty ?? product.currentStock ?? 0)
  ), [allSerials]);

  const lowStockIds = useMemo(() => new Set(lowStockProducts.map(p => p.id)), [lowStockProducts]);

  // Calculate total value of currently available stock (serial-tracked + qty-only)
  const totalAvailableStockValue = useMemo(() => {
    return products.reduce((sum, product) => (
      sum + (getAvailableCount(product) * (product.sellingPrice || 0))
    ), 0);
  }, [products, getAvailableCount]);

  const productGroups = useMemo(() => {
    const s = searchTerm.toLowerCase().trim();
    const groupsMap = new Map<string, ProductGroup>();
    
    products.forEach(p => {
      const normalizedName = p.name.toLowerCase().replace(/\s+/g, '');
      const brandId = p.brandId || 0;
      const categoryId = p.categoryId || 0;
      const groupId = `${normalizedName}-${brandId}-${categoryId}`;

      const serials = allSerials.filter(sr => sr.productId === p.id);
      const availableCount = getAvailableCount(p);
      const totalUnits = p.hasSerial !== false ? serials.length : (p.stockQty ?? p.currentStock ?? 0);

      if (!groupsMap.has(groupId)) {
        groupsMap.set(groupId, {
          groupId,
          displayName: p.name,
          brandName: p.brandName || 'Unknown',
          categoryName: p.categoryName || 'General',
          brandId: p.brandId,
          categoryId: p.categoryId,
          unitName: p.unitName || '',
          products: [p],
          totalAvailable: availableCount,
          totalUnits,
          groupStockValue: availableCount * p.sellingPrice
        });
      } else {
        const group = groupsMap.get(groupId)!;
        group.products.push(p);
        group.totalAvailable += availableCount;
        group.totalUnits += totalUnits;
        group.groupStockValue += availableCount * p.sellingPrice;
      }
    });

    return Array.from(groupsMap.values()).filter(group => {
      const matchesSearch = !s || 
        group.displayName.toLowerCase().includes(s) || 
        group.brandName.toLowerCase().includes(s) ||
        group.categoryName.toLowerCase().includes(s) ||
        group.products.some(p => (p.productCode || '').toLowerCase().includes(s)) ||
        allSerials.filter(sr => group.products.some(p => p.id === sr.productId))
          .some(sn => sn.serialNumber.toLowerCase().includes(s));

      const matchesCondition = filterCondition === 'All' || 
        group.products.some(p => p.productType === filterCondition);

      const matchesStatus = filterStatus === 'All' || 
        (filterStatus === 'In Stock' && group.totalAvailable > 0) || 
        (filterStatus === 'Out of Stock' && group.totalAvailable === 0);

      const matchesBrand = filterBrandId === 'All' || group.products.some(p => p.brandId === filterBrandId);

      const matchesCategory = filterCategoryId === 'All' || group.products.some(p => p.categoryId === filterCategoryId);

      const matchesTracking = filterTracking === 'All' ||
        (filterTracking === 'Serial' && group.products.some(p => p.hasSerial !== false)) ||
        (filterTracking === 'Qty' && group.products.some(p => p.hasSerial === false));

      const matchesLowStock = !filterLowStockOnly || group.products.some(p => lowStockIds.has(p.id));

      return matchesSearch && matchesCondition && matchesStatus && matchesBrand && matchesCategory && matchesTracking && matchesLowStock;
    });
  }, [products, allSerials, searchTerm, filterCondition, filterStatus, filterBrandId, filterCategoryId, filterTracking, filterLowStockOnly, lowStockIds, getAvailableCount]);

  const stockSummary = useMemo(() => {
    const serialProducts = products.filter(p => p.hasSerial !== false).length;
    const qtyProducts = products.length - serialProducts;
    const availableUnits = products.reduce((sum, product) => sum + getAvailableCount(product), 0);
    const outProducts = products.filter(product => getAvailableCount(product) <= 0).length;
    return {
      totalProducts: products.length,
      totalGroups: productGroups.length,
      serialProducts,
      qtyProducts,
      availableUnits,
      outProducts,
      lowStock: lowStockProducts.length,
      value: totalAvailableStockValue,
    };
  }, [products, productGroups.length, lowStockProducts.length, totalAvailableStockValue, getAvailableCount]);

  const activeFilterCount = useMemo(() => {
    return [
      filterCondition !== 'All',
      filterStatus !== 'All',
      filterBrandId !== 'All',
      filterCategoryId !== 'All',
      filterTracking !== 'All',
      filterLowStockOnly,
    ].filter(Boolean).length;
  }, [filterCondition, filterStatus, filterBrandId, filterCategoryId, filterTracking, filterLowStockOnly]);

  const resetProductFilters = () => {
    setSearchTerm('');
    setFilterCondition('All');
    setFilterStatus('All');
    setFilterBrandId('All');
    setFilterCategoryId('All');
    setFilterTracking('All');
    setFilterLowStockOnly(false);
  };
  useEffect(() => {
    setCurrentPage(1);
    setExpandedGroups(new Set());
    setNestedFilters({});
  }, [searchTerm, filterCondition, filterStatus, filterBrandId, filterCategoryId, filterTracking, filterLowStockOnly]);

  const toggleGroup = (groupId: string) => {
    const newExpanded = new Set(expandedGroups);
    if (newExpanded.has(groupId)) newExpanded.delete(groupId);
    else newExpanded.add(groupId);
    setExpandedGroups(newExpanded);
  };

  const getNestedFilter = (groupId: string): 'All' | 'New' | 'Second' | 'Second_New' => nestedFilters[groupId] || 'All';

  const setNestedFilterForGroup = (groupId: string, value: 'All' | 'New' | 'Second' | 'Second_New') => {
    setNestedFilters(prev => ({ ...prev, [groupId]: value }));
  };

  const totalPages = Math.ceil(productGroups.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedGroups = productGroups.slice(startIndex, startIndex + itemsPerPage);

  const pageNumbers = useMemo<(number | '...')[]>(() => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
    if (currentPage <= 4) return [1, 2, 3, 4, 5, '...', totalPages];
    if (currentPage >= totalPages - 3) return [1, '...', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
    return [1, '...', currentPage - 1, currentPage, currentPage + 1, '...', totalPages];
  }, [currentPage, totalPages]);

  const handleOpenModal = (product?: ProductDTO) => {
    if (product) {
      setEditingProduct(product);
      setFormData({
        name: product.name,
        hasSerial: product.hasSerial !== false,
        stockQty: product.stockQty ?? product.currentStock ?? 0,
        productType: product.productType,
        sellingPrice: product.sellingPrice,
        costPrice: product.costPrice ?? 0,
        reorderLevel: product.reorderLevel ?? 0,
        warrantyMonths: product.warrantyMonths ?? 0,
        warrantyTerms: product.warrantyTerms || '',
        remark: product.remark || '',
        categoryId: product.categoryId,
        brandId: product.brandId,
        unitId: product.unitId
      });
      setFormPhoto(product.photoBase64 || undefined);
      // Initialize warranty display state from existing data
      const wm = product.warrantyMonths ?? 0;
      if (wm > 0 && wm % 12 === 0) {
        setFormWarrantyValue(wm / 12);
        setFormWarrantyUnit('နှစ်');
      } else {
        setFormWarrantyValue(wm);
        setFormWarrantyUnit('လ');
      }
    } else {
      setEditingProduct(null);
      setFormWarrantyValue(0);
      setFormWarrantyUnit('နှစ်');
      setFormData({
        name: '',
        hasSerial: true,
        stockQty: 0,
        productType: ProductType.NEW,
        sellingPrice: 0,
        costPrice: 0,
        reorderLevel: 0,
        warrantyMonths: 0,
        warrantyTerms: '',
        remark: '',
        categoryId: undefined,
        brandId: undefined,
        unitId: undefined
      });
      setFormPhoto(undefined);
    }
    setPhotoChanged(false);
    setBrandSearch('');
    setCategorySearch('');
    setUnitSearch('');
    setBrandOpen(false);
    setCategoryOpen(false);
    setUnitOpen(false);
    setShowForm(true);
  };

  const handleOpenPricePicker = async () => {
    if (!formData.name && !editingProduct) return;
    const productId = editingProduct?.id;
    if (!productId) return;
    setIsPricePickerOpen(true);
    setPricePickerLoading(true);
    setPickedCost(null);
    setPickedSelling(String(formData.sellingPrice ?? 0));
    try {
      const all = await purchaseApiService.getAll();
      const rows: typeof pricePickerRows = [];
      all.forEach(p => {
        (p.details || []).forEach(d => {
          if (d.productId === productId) {
            rows.push({
              purchaseCode: p.purchaseCode || '-',
              supplierName: p.supplierName || '-',
              purchaseDate: p.purchaseDate ? String(p.purchaseDate).slice(0, 10) : '-',
              unitCost: Number(d.unitCost),
              qty: Number(d.qty),
            });
          }
        });
      });
      rows.sort((a, b) => b.purchaseDate.localeCompare(a.purchaseDate));
      setPricePickerRows(rows);
    } catch {
      Swal.fire('Error', 'Purchase history ဖတ်မရပါ။', 'error');
    } finally {
      setPricePickerLoading(false);
    }
  };

  const handleOpenSerialModal = (product: ProductDTO) => {
    if (product.hasSerial === false) {
      Swal.fire('အရေအတွက်သာ ကုန်ပစ္စည်း', 'ဤကုန်ပစ္စည်းသည် အရေအတွက်သာ ကုန်သိုလှောင်မှုကို အသုံးပြုသဖြင့် စီရီရယ်နံပါတ်မလိုအပ်ပါ။', 'info');
      return;
    }
    setSelectedProductForSerial(product);
    setNewSerialInput('');
    setIsSerialModalOpen(true);
  };

  const getSerialWarrantyEditUnit = (serial: ProductSerialDTO): { warrantyValue: number; warrantyUnit: 'ရက်' | 'လ' | 'နှစ်' } => {
    const safeMonths = Math.max(0, Number(serial.warrantyMonths ?? 0) || 0);
    if (safeMonths > 0 && safeMonths % 12 === 0) return { warrantyValue: safeMonths / 12, warrantyUnit: 'နှစ်' };
    if (safeMonths > 0) return { warrantyValue: safeMonths, warrantyUnit: 'လ' };
    const days = countWarrantyDays(serial.warrantyStartDate, serial.warrantyEndDate);
    if (days > 0) return { warrantyValue: days, warrantyUnit: 'ရက်' };
    return { warrantyValue: 0, warrantyUnit: 'လ' };
  };

  const buildSerialWarrantyPayload = (serial: ProductSerialDTO) => {
    const value = Math.max(0, Number(serialEditForm.warrantyValue) || 0);
    if (value <= 0) return { warrantyMonths: 0, warrantyStartDate: undefined, warrantyEndDate: undefined };
    const start = toDateOnly(serial.warrantyStartDate) || todayDateOnly();
    if (serialEditForm.warrantyUnit === 'ရက်') {
      return { warrantyMonths: 0, warrantyStartDate: start, warrantyEndDate: addWarrantyDays(start, value) };
    }
    return {
      warrantyMonths: toWarrantyMonths(value, serialEditForm.warrantyUnit),
      warrantyStartDate: start,
      warrantyEndDate: undefined,
    };
  };

  const startEditSerial = (serial: ProductSerialDTO) => {
    setEditingSerialId(serial.id);
    setSerialEditForm({
      serialNumber: serial.serialNumber,
      status: serial.status,
      ...getSerialWarrantyEditUnit(serial),
    });
  };

  const cancelEditSerial = () => {
    setEditingSerialId(null);
    setSerialEditForm({ serialNumber: '', status: SerialStatus.AVAILABLE, warrantyValue: 0, warrantyUnit: 'လ' });
  };

  const handleSaveSerialEdit = async (serial: ProductSerialDTO) => {
    const serialNumber = serialEditForm.serialNumber.trim().toUpperCase();
    if (!serialNumber || serialEditSaving) return;
    setSerialEditSaving(true);
    try {
      await productSerialService.update(serial.id, {
        ...serial,
        serialNumber,
        status: serialEditForm.status,
        ...buildSerialWarrantyPayload(serial),
      });
      cancelEditSerial();
      await fetchSerials();
      Swal.fire({ icon: 'success', title: 'Serial updated', toast: true, position: 'top-end', showConfirmButton: false, timer: 1400 });
    } catch (err: any) {
      Swal.fire('Error', err?.response?.data?.message || err?.message || 'Failed to update serial', 'error');
    } finally {
      setSerialEditSaving(false);
    }
  };

  const handleOpenGroupSerials = (group: ProductGroup) => {
    if (!group.products.some((product) => product.hasSerial !== false)) return;
    setSelectedSerialGroup(group);
  };

  const handleAddSerial = async () => {
    if (!newSerialInput.trim() || !selectedProductForSerial) return;
    setSaving(true);
    try {
      await productSerialService.create({
        serialNumber: newSerialInput.trim().toUpperCase(),
        status: SerialStatus.AVAILABLE,
        productId: selectedProductForSerial.id
      });
      setNewSerialInput('');
      fetchSerials();
      Swal.fire({ icon: 'success', title: 'စီရီရယ် မှတ်ပုံတင်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (err: any) {
      Swal.fire('ပဋိပက္ခ', 'ဤစီရီရယ်နံပါတ်ကို အသုံးပြုပြီးဖြစ်သည်။', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleSaveProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.categoryId) { Swal.fire('စစ်ဆေးမှု', 'အမျိုးအစားတစ်ခု ရွေးပါ။', 'warning'); return; }
    if (!formData.brandId) { Swal.fire('စစ်ဆေးမှု', 'ဘရန်းတစ်ခု ရွေးပါ။', 'warning'); return; }
    if (!formData.unitId) { Swal.fire('စစ်ဆေးမှု', 'တိုင်းတာမှုယူနစ်တစ်ခု ရွေးပါ။', 'warning'); return; }
    setSaving(true);
    try {
      const warrantyMonthsComputed =
        formWarrantyUnit === 'နှစ်' ? formWarrantyValue * 12 :
        formWarrantyUnit === 'ရက်' ? Math.round(formWarrantyValue / 30) :
        formWarrantyValue;
      const payload: any = {
        ...formData,
        warrantyMonths: warrantyMonthsComputed,
        hasSerial: formData.hasSerial !== false,
        stockQty: editingProduct ? Number(formData.stockQty ?? editingProduct.stockQty ?? editingProduct.currentStock ?? 0) : 0,
        reorderLevel: Math.max(0, Number(formData.reorderLevel || 0))
      };
      let savedId: number | undefined;
      if (editingProduct) {
        await productService.update(editingProduct.id, payload);
        savedId = editingProduct.id;
      } else {
        const created = await productService.create(payload);
        savedId = created?.id;
      }
      // Upload photo if changed
      if (photoChanged && savedId) {
        await productService.updatePhoto(savedId, formPhoto ?? null);
      }
      setShowForm(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'မှတ်တမ်းသိမ်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message, 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteProduct = async (product: ProductDTO) => {
    const result = await Swal.fire({
      title: `"${product.name}" ကိုဖျက်မည်လား?`,
      text: 'ဤ Product နှင့် ချိတ်ဆက်ထားသော Serial နံပါတ်များလည်း ပါဝင်ဖျက်မည်။ ပြန်မရနိုင်ပါ။',
      icon: 'warning',
      showCancelButton: true,
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'ဖျက်ရန်'
    });
    if (result.isConfirmed) {
      try {
        await productService.delete(product.id);
        await fetchData();
        Swal.fire({ icon: 'success', title: 'ဖျက်ပြီးပြီ', toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
      } catch (error: any) {
        Swal.fire('Error', error?.message || 'ဖျက်မရပါ', 'error');
      }
    }
  };

  const parseWarrantyDisplay = (terms?: string, months?: number): { value: number; unit: 'ရက်' | 'လ' | 'နှစ်'; note: string } => {
    const t = (terms || '').trim();
    const m = /^(\d+)\s*(ရက်|နှစ်|လ)(.*)$/.exec(t);
    if (m) return { value: Number(m[1]), unit: m[2] as 'ရက်' | 'လ' | 'နှစ်', note: m[3].trim() };
    if (months && months > 0) {
      if (months % 12 === 0) return { value: months / 12, unit: 'နှစ်', note: '' };
      return { value: months, unit: 'လ', note: '' };
    }
    return { value: 0, unit: 'လ', note: '' };
  };

  const toWarrantyMonths = (value: number, unit: 'ရက်' | 'လ' | 'နှစ်'): number => {
    if (unit === 'ရက်') return 0;
    if (unit === 'နှစ်') return value * 12;
    return value;
  };

  const handleOpenWarrantyEdit = (product: ProductDTO) => {
    const parsed = parseWarrantyDisplay(product.warrantyTerms, product.warrantyMonths);
    setWarrantyProduct(product);
    setWarrantyValue(parsed.value);
    setWarrantyUnit(parsed.unit);
    setWarrantyNote(parsed.note);
    setIsWarrantyModalOpen(true);
  };

  const handleSaveWarranty = async () => {
    if (!warrantyProduct || warrantySaving) return;
    setWarrantySaving(true);
    try {
      const displayStr = warrantyValue > 0
        ? `${warrantyValue} ${warrantyUnit}${warrantyNote ? ' ' + warrantyNote : ''}`
        : '';
      await productService.update(warrantyProduct.id, {
        ...warrantyProduct,
        warrantyMonths: toWarrantyMonths(warrantyValue, warrantyUnit),
        warrantyTerms: displayStr,
      });
      setIsWarrantyModalOpen(false);
      await fetchData();
      Swal.fire({ icon: 'success', title: 'အာမခံ မွမ်းမံပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1800 });
    } catch (err: any) {
      Swal.fire('Error', err?.message || 'မွမ်းမံမရပါ', 'error');
    } finally {
      setWarrantySaving(false);
    }
  };

  const handleDeleteSerial = async (id: number) => {
    const result = await Swal.fire({
      title: 'ဖျက်မည်လား?',
      text: "ပြန်မရနိုင်သောလုပ်ဆောင်ချက်",
      icon: 'warning',
      showCancelButton: true,
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'ဖျက်ရန်'
    });
    if (result.isConfirmed) {
      try {
        await productSerialService.delete(id);
        fetchSerials();
        Swal.fire({ icon: 'success', title: 'ဖျက်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message, 'error');
      }
    }
  };

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  if (showForm) return (
    <div className="w-full max-w-none animate-in fade-in duration-200 text-left">
      {/* Full-page form header */}
      <div className="flex items-center gap-3 mb-6">
        <button
          type="button"
          onClick={() => setShowForm(false)}
          className="p-2 hover:bg-slate-100 rounded-lg transition-all text-slate-500 hover:text-slate-800"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="w-9 h-9 bg-indigo-600 rounded-lg flex items-center justify-center text-white shrink-0">
          <Package size={18} />
        </div>
        <div>
          <h2 className="text-lg font-black text-slate-800 uppercase tracking-tight">
            {editingProduct ? 'မှတ်တမ်းသတ်မှတ်ချက် ပြင်ဆင်ရန်' : 'မာစတာ မှတ်ပုံတင်ချက် အသစ်'}
          </h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">ကုန်ပစ္စည်း သတ်မှတ်ချက်</p>
        </div>
      </div>

      <form onSubmit={handleSaveProduct} className="w-full pb-10">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">

          {/* ══ LEFT COLUMN ══ */}
          <div className="space-y-5">

            {/* Section: Identity */}
            <div className="bg-white border border-slate-200 rounded-2xl">
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
                <Package size={13} className="text-indigo-500" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ကုန်ပစ္စည်း သတ်မှတ်ချက်</span>
              </div>
              <div className="p-5 space-y-4">

                {/* Name */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ကုန်ပစ္စည်းနာမည် <span className="text-rose-400">*</span></label>
                  <div className="relative group">
                    <Package className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={15} />
                    <input
                      type="text" required value={formData.name}
                      onChange={(e) => setFormData({...formData, name: e.target.value})}
                      className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                      placeholder="E.g. NVIDIA GeForce GTX 1050Ti"
                    />
                  </div>
                </div>

                {/* Stock Tracking */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ကုန်သိုလှောင်မှု နည်းလမ်း <span className="text-rose-400">*</span></label>
                  <div className="flex gap-2 p-1 bg-slate-100 rounded-xl border border-slate-200">
                    <button type="button"
                      onClick={() => setFormData({ ...formData, hasSerial: true, stockQty: 0 })}
                      className={`flex-1 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${formData.hasSerial !== false ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      စီရီရယ်လိုအပ်သည်
                    </button>
                    <button type="button"
                      onClick={() => setFormData({ ...formData, hasSerial: false, stockQty: Number(formData.stockQty || 0) })}
                      className={`flex-1 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${formData.hasSerial === false ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      အရေအတွက်သာ
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-400 ml-1">
                    {formData.hasSerial !== false
                      ? 'စီရီရယ်နံပါတ်ဖြင့် တစ်ခုချင်းစီ ခြေရာခံသည်။'
                      : 'ကုန်သိုလှောင်မှုကို စုစုပေါင်းအရေအတွက်ဖြင့် ခြေရာခံသည် — စီရီရယ်မလိုအပ်ပါ။'}
                  </p>
                </div>

                {/* Opening stock is managed from the Opening Stock page. */}
                {formData.hasSerial === false && (
                  <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 flex items-start gap-3">
                    <CheckCircle2 size={16} className="text-emerald-600 mt-0.5 shrink-0" />
                    <div>
                      <p className="text-xs font-black text-emerald-800">ကနဦးကုန်လက်ကျန်ကို Opening Stock page တွင်ထည့်ပါ</p>
                      <p className="text-[10px] font-semibold text-emerald-700 mt-0.5">
                        Product Master တွင် item အချက်အလက်၊ ဝယ်ဈေး၊ ပြန်မှာယူအဆင့်ကိုသာထိန်းပါမည်။ New product stock သည် 0 ဖြင့်စပါမည်။
                      </p>
                    </div>
                  </div>
                )}

              </div>
            </div>

            {/* Section: Pricing */}
            <div className="bg-white border border-slate-200 rounded-2xl">
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
                <DollarSign size={13} className="text-emerald-500" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ဈေးနှုန်းနှင့် အနည်းဆုံးပမာဏ</span>
                {editingProduct && (
                  <button type="button" onClick={handleOpenPricePicker}
                    className="ml-auto flex items-center gap-1.5 px-3 py-1 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-lg text-[10px] font-black uppercase transition-all">
                    <ClipboardList size={11} /> ဝယ်ယူမှုမှ ရွေးချယ်ရန်
                  </button>
                )}
              </div>
              <div className="p-5 grid grid-cols-2 gap-4">

                {/* Selling Price */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ရောင်းဈေး</label>
                  <div className="relative group">
                    <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-emerald-500 transition-colors" size={14} />
                    <input type="number" min="0"
                      value={formData.sellingPrice ?? 0}
                      onChange={(e) => setFormData({...formData, sellingPrice: Number(e.target.value)})}
                      className="w-full pl-9 pr-3 py-3 border rounded-xl text-sm font-semibold outline-none transition-all bg-slate-50 border-slate-200 focus:border-emerald-400 focus:bg-white"
                    />
                  </div>
                  <p className="text-[9px] text-slate-400 ml-1">MMK · မထည့်လည်းရသည် · Purchase ပြီးမှ auto update ဖြစ်သည်</p>
                </div>

                {/* Cost Price */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ကုန်ကျစရိတ်</label>
                  <div className="relative group">
                    <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={14} />
                    <input type="number" min="0"
                      value={formData.costPrice ?? 0}
                      onChange={(e) => setFormData({...formData, costPrice: Number(e.target.value)})}
                      className="w-full pl-9 pr-3 py-3 border rounded-xl text-sm font-semibold outline-none transition-all bg-slate-50 border-slate-200 focus:border-indigo-400 focus:bg-white"
                    />
                  </div>
                  <p className="text-[9px] text-slate-400 ml-1">MMK · Opening Stock သိမ်းရန် ဝယ်ဈေးလိုအပ်သည်</p>
                </div>

                {/* Reorder Level */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ပြန်မှာယူမည့် အဆင့်</label>
                  <div className="relative group">
                    <AlertCircle className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-amber-500 transition-colors" size={14} />
                    <input type="number" min="0"
                      value={formData.reorderLevel ?? 0}
                      onChange={(e) => setFormData({...formData, reorderLevel: Math.max(0, Number(e.target.value) || 0)})}
                      className="w-full pl-9 pr-3 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-amber-400 focus:bg-white transition-all"
                      placeholder="0"
                    />
                  </div>
                  <p className="text-[9px] text-slate-400 ml-1">ကုန်သိုလှောင်မှု နည်းပါးသတိပေး သတ်မှတ်ချက်</p>
                </div>

                {/* Warranty */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အခြေခံ အာမခံ</label>
                  <div className="flex gap-2">
                    <input type="number" min="0"
                      value={formWarrantyValue}
                      onChange={e => setFormWarrantyValue(Math.max(0, Number(e.target.value) || 0))}
                      className="flex-1 px-3 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                      placeholder="0"
                    />
                    <select
                      value={formWarrantyUnit}
                      onChange={e => setFormWarrantyUnit(e.target.value as 'ရက်' | 'လ' | 'နှစ်')}
                      className="px-3 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                    >
                      <option value="ရက်">ရက်</option>
                      <option value="လ">လ</option>
                      <option value="နှစ်">နှစ်</option>
                    </select>
                  </div>
                  {formWarrantyValue > 0 && (
                    <p className="text-[9px] text-indigo-500 font-semibold ml-1">
                      = {formWarrantyUnit === 'နှစ်' ? formWarrantyValue * 12 : formWarrantyUnit === 'ရက်' ? Math.round(formWarrantyValue / 30) : formWarrantyValue} လ
                    </p>
                  )}
                </div>

                {/* Warranty Terms */}
                <div className="col-span-2 space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အာမခံ စည်းကမ်း</label>
                  <input
                    type="text"
                    value={formData.warrantyTerms ?? ''}
                    onChange={(e) => setFormData({ ...formData, warrantyTerms: e.target.value })}
                    className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none focus:border-indigo-500 focus:bg-white transition-all"
                    placeholder="ဥပမာ: ၇ ရက် ဝန်ဆောင်မှုအာမခံ / ၁ လ စစ်ဆေးမှုအာမခံ / အာမခံမပါ"
                  />
                  <p className="text-[9px] text-slate-400 ml-1">ဒုတိယမြောက်ပစ္စည်းနှင့် ကွဲပြားသောအာမခံမူဝါဒများအတွက် ပြောင်းလွယ်သောစာသား။</p>
                </div>

              </div>
            </div>

          </div>{/* end LEFT */}

          {/* ══ RIGHT COLUMN ══ */}
          <div className="space-y-5">

            {/* Section: Classification */}
            <div className="bg-white border border-slate-200 rounded-2xl">
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
                <Layers size={13} className="text-indigo-500" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">အမျိုးအစား သတ်မှတ်ချက်</span>
              </div>
              <div className="p-5 space-y-4">

                {/* Condition */}
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ပစ္စည်း အခြေအနေ <span className="text-rose-400">*</span></label>
                  <div className="flex gap-2 p-1 bg-slate-100 rounded-xl border border-slate-200">
                    <button type="button" onClick={() => setFormData({...formData, productType: ProductType.NEW})}
                      className={`flex-1 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${formData.productType === ProductType.NEW ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      အသစ်
                    </button>
                    <button type="button" onClick={() => setFormData({...formData, productType: ProductType.SECOND})}
                      className={`flex-1 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${formData.productType === ProductType.SECOND ? 'bg-white text-amber-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      အသုံးပြုပြီး
                    </button>
                    <button type="button" onClick={() => setFormData({...formData, productType: ProductType.SECOND_NEW})}
                      className={`flex-1 py-2.5 rounded-lg text-xs font-black uppercase tracking-wider transition-all ${formData.productType === ProductType.SECOND_NEW ? 'bg-white text-violet-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      အသစ်နှင့်တူသော
                    </button>
                  </div>
                </div>

                {/* Category */}
                <div className="space-y-1.5 relative">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အမျိုးအစား <span className="text-rose-400">*</span></label>
                  <div className="relative group">
                    <Layers className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors z-10" size={15} />
                    <input type="text"
                      value={categorySearch || (formData.categoryId ? flatCategoryOptions.find(c => c.id === formData.categoryId)?.displayName || '' : '')}
                      onFocus={() => { setCategoryOpen(true); setCategorySearch(''); }}
                      onChange={(e) => { setCategorySearch(e.target.value); setCategoryOpen(true); }}
                      onBlur={() => setTimeout(() => setCategoryOpen(false), 150)}
                      placeholder="အမျိုးအစား ရှာပါ..."
                      className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                    />
                  </div>
                  {categoryOpen && (
                    <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-48 overflow-y-auto">
                      {flatCategoryOptions.filter(c => c.displayName.toLowerCase().includes((categorySearch || '').toLowerCase())).map(c => (
                        <div key={c.id} onMouseDown={() => { setFormData({...formData, categoryId: c.id}); setCategorySearch(''); setCategoryOpen(false); }}
                          className={`px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 transition-colors ${formData.categoryId === c.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                          {c.displayName}
                        </div>
                      ))}
                      {flatCategoryOptions.filter(c => c.displayName.toLowerCase().includes((categorySearch || '').toLowerCase())).length === 0 && (
                        <div className="px-4 py-3 text-xs text-slate-400 font-bold">ရလဒ်မတွေ့ပါ</div>
                      )}
                    </div>
                  )}
                </div>

                {/* Brand */}
                <div className="space-y-1.5 relative">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ဘရန်း <span className="text-rose-400">*</span></label>
                  <div className="relative group">
                    <Tag className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors z-10" size={15} />
                    <input type="text"
                      value={brandSearch || (formData.brandId ? brands.find(b => b.id === formData.brandId)?.name || '' : '')}
                      onFocus={() => { setBrandOpen(true); setBrandSearch(''); }}
                      onChange={(e) => { setBrandSearch(e.target.value); setBrandOpen(true); }}
                      onBlur={() => setTimeout(() => setBrandOpen(false), 150)}
                      placeholder="ဘရန်း ရှာပါ..."
                      className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                    />
                  </div>
                  {brandOpen && (
                    <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-48 overflow-y-auto">
                      {brands.filter(b => b.name.toLowerCase().includes((brandSearch || '').toLowerCase())).map(b => (
                        <div key={b.id} onMouseDown={() => { setFormData({...formData, brandId: b.id}); setBrandSearch(''); setBrandOpen(false); }}
                          className={`px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 transition-colors ${formData.brandId === b.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                          {b.name}
                        </div>
                      ))}
                      {brands.filter(b => b.name.toLowerCase().includes((brandSearch || '').toLowerCase())).length === 0 && (
                        <div className="px-4 py-3 text-xs text-slate-400 font-bold">ရလဒ်မတွေ့ပါ</div>
                      )}
                    </div>
                  )}
                </div>

                {/* Unit */}
                <div className="space-y-1.5 relative">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">တိုင်းတာမှု ယူနစ် <span className="text-rose-400">*</span></label>
                  <div className="relative group">
                    <Ruler className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors z-10" size={15} />
                    <input type="text"
                      value={unitSearch || (formData.unitId ? units.find(u => u.id === formData.unitId)?.unitName || '' : '')}
                      onFocus={() => { setUnitOpen(true); setUnitSearch(''); }}
                      onChange={(e) => { setUnitSearch(e.target.value); setUnitOpen(true); }}
                      onBlur={() => setTimeout(() => setUnitOpen(false), 150)}
                      placeholder="ဥပမာ: ခု၊ ဘောက်စ်၊ ကီလို..."
                      className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                    />
                  </div>
                  {unitOpen && (
                    <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-48 overflow-y-auto">
                      {units.filter(u => u.unitName.toLowerCase().includes((unitSearch || '').toLowerCase())).map(u => (
                        <div key={u.id} onMouseDown={() => { setFormData({...formData, unitId: u.id}); setUnitSearch(''); setUnitOpen(false); }}
                          className={`px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 transition-colors ${formData.unitId === u.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                          {u.unitName}
                        </div>
                      ))}
                      {units.filter(u => u.unitName.toLowerCase().includes((unitSearch || '').toLowerCase())).length === 0 && (
                        <div className="px-4 py-3 text-xs text-slate-400 font-bold">ရလဒ်မတွေ့ပါ</div>
                      )}
                    </div>
                  )}
                </div>

              </div>
            </div>

            {/* Section: Remarks */}
            <div className="bg-white border border-slate-200 rounded-2xl">
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
                <Info size={13} className="text-slate-400" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">မှတ်ချက်</span>
                <span className="text-[10px] text-slate-400">(ရွေးချယ်နိုင်)</span>
              </div>
              <div className="p-5">
                <textarea rows={4}
                  value={formData.remark}
                  onChange={(e) => setFormData({...formData, remark: e.target.value})}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none focus:border-indigo-500 focus:bg-white transition-all resize-none"
                  placeholder="နည်းပညာ မှတ်ချက်များ၊ တွေ့ရှိချက်များ၊ ဖော်ပြချက်များ..."
                />
              </div>
            </div>

            {/* Photo Upload */}
            <div className="bg-white border border-slate-200 rounded-2xl">
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
                <Camera size={13} className="text-indigo-500" />
                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ကုန်ပစ္စည်း ဓာတ်ပုံ</span>
                <span className="ml-auto text-[9px] text-slate-400 font-bold">အလိုအလျောက်ချုံ့ · အများဆုံး ၃၂၀×၃၂၀</span>
              </div>
              <div className="p-5">
                {formPhoto ? (
                  <div className="flex items-start gap-4">
                    <img src={formPhoto} alt="product" className="w-28 h-28 object-contain rounded-xl border border-slate-200 bg-slate-50" />
                    <div className="flex flex-col gap-2 pt-1">
                      <label className="inline-flex items-center gap-2 px-3 py-1.5 border border-indigo-200 bg-indigo-50 text-indigo-700 text-xs font-bold rounded-lg cursor-pointer hover:bg-indigo-100 transition-all">
                        <Camera size={13} /> ဓာတ်ပုံ ပြောင်းရန်
                        <input type="file" accept="image/*" className="hidden" onChange={handlePhotoUpload} />
                      </label>
                      <button type="button" onClick={() => setViewFormPhoto(formPhoto)}
                        className="inline-flex items-center gap-2 px-3 py-1.5 border border-indigo-200 bg-indigo-50 text-indigo-700 text-xs font-bold rounded-lg hover:bg-indigo-100 transition-all">
                        <Eye size={13} /> ဓာတ်ပုံ ကြည့်ရန်
                      </button>
                      <button type="button" onClick={() => { setFormPhoto(undefined); setPhotoChanged(true); }}
                        className="inline-flex items-center gap-2 px-3 py-1.5 border border-rose-200 bg-rose-50 text-rose-700 text-xs font-bold rounded-lg hover:bg-rose-100 transition-all">
                        <X size={13} /> ဖယ်ရှားရန်
                      </button>
                    </div>
                  </div>
                ) : (
                  <label className="flex flex-col items-center gap-3 p-8 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-indigo-400 hover:bg-indigo-50/30 transition-all">
                    <div className="w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center">
                      <Camera size={22} className="text-indigo-400" />
                    </div>
                    <div className="text-center">
                      <p className="text-sm font-bold text-slate-600">ဓာတ်ပုံတင်ရန် နှိပ်ပါ</p>
                      <p className="text-xs text-slate-400 mt-1">JPG, PNG, WEBP — ~30KB အထိ အလိုအလျောက်ချုံ့</p>
                    </div>
                    <input type="file" accept="image/*" className="hidden" onChange={handlePhotoUpload} />
                  </label>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-3">
              <button type="button" onClick={() => setShowForm(false)}
                className="flex items-center gap-2 px-5 py-3 border border-slate-200 rounded-xl text-xs font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm">
                <ArrowLeft size={14} /> နောက်သို့
              </button>
              <button type="submit" disabled={saving}
                className="flex-1 py-3 bg-indigo-600 text-white rounded-xl text-xs font-black uppercase tracking-widest shadow-lg shadow-indigo-600/25 active:scale-[0.98] transition-all flex items-center justify-center gap-2 hover:bg-indigo-700">
                {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                {editingProduct ? 'ပြောင်းလဲချက် သိမ်းရန်' : 'ကုန်ပစ္စည်း မှတ်ပုံတင်ရန်'}
              </button>
            </div>

          </div>{/* end RIGHT */}

        </div>
      </form>

      {/* Product photo viewer */}
      {viewFormPhoto && (
        <div
          className="fixed inset-0 z-[300] flex items-center justify-center bg-black/85 backdrop-blur-sm"
          onClick={() => setViewFormPhoto(null)}
        >
          <div className="flex flex-col items-center gap-3" onClick={e => e.stopPropagation()}>
            <img
              src={viewFormPhoto}
              alt="Product photo"
              className="rounded-xl object-contain shadow-2xl"
              style={{ maxHeight: '50vh', maxWidth: '90vw' }}
            />
            <button
              onClick={() => setViewFormPhoto(null)}
              className="px-5 py-2 bg-white/10 hover:bg-white/20 text-white text-xs font-bold rounded-full border border-white/20 transition-all"
            >
              ပိတ်ရန်
            </button>
          </div>
        </div>
      )}

      {/* Purchase History Price Picker Modal (used inside full-page form too) */}
      {isPricePickerOpen && (
        <div className="fixed inset-0 z-[250] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white w-full max-w-2xl rounded-3xl shadow-2xl border border-slate-200 flex flex-col max-h-[85vh]">
            <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/60 shrink-0">
              <div>
                <h3 className="text-[13px] font-black text-slate-800 uppercase tracking-tight flex items-center gap-2">
                  <ClipboardList size={15} className="text-indigo-600" /> ဝယ်ယူမှု မှတ်တမ်း — {editingProduct?.name}
                </h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5 tracking-widest">
                  ဝယ်ထားသော purchase တစ်ခုကို ရွေးပြီး price သတ်မှတ်ပါ
                </p>
              </div>
              <button onClick={() => setIsPricePickerOpen(false)} className="p-2 hover:bg-slate-200 rounded-xl transition-all">
                <X size={18} className="text-slate-400" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto">
              {pricePickerLoading ? (
                <div className="flex items-center justify-center py-16 gap-2 text-slate-400">
                  <Loader2 size={20} className="animate-spin" />
                  <span className="text-[12px] font-bold">ဒေတာ ယူနေသည်...</span>
                </div>
              ) : pricePickerRows.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-slate-300 gap-2">
                  <ClipboardList size={32} />
                  <p className="text-[11px] font-black uppercase tracking-widest">ဝယ်ယူမှု မှတ်တမ်း မတွေ့ပါ</p>
                  <p className="text-[10px] text-slate-400">ဤကုန်ပစ္စည်း ပါဝင်သောဝယ်ယူမှုမရှိသေးပါ</p>
                </div>
              ) : (
                <table className="w-full text-xs">
                  <thead className="sticky top-0 bg-slate-50 border-b border-slate-200">
                    <tr className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
                      <th className="px-4 py-3 text-left">ဝယ်ယူမှု ကုဒ်</th>
                      <th className="px-4 py-3 text-left">ပေးသွင်းသူ</th>
                      <th className="px-4 py-3 text-left">နေ့ရက်</th>
                      <th className="px-4 py-3 text-right">အရေအတွက်</th>
                      <th className="px-4 py-3 text-right">ယူနစ် ကုန်ကျစရိတ်</th>
                      <th className="px-4 py-3 text-center">ရွေးချယ်ရန်</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {pricePickerRows.map((row, i) => (
                      <tr key={i}
                        onClick={() => { setPickedCost(row.unitCost); setPickedSelling(String(row.unitCost)); }}
                        className={`cursor-pointer transition-colors hover:bg-indigo-50 ${pickedCost === row.unitCost && pickedSelling === String(row.unitCost) ? 'bg-indigo-50' : ''}`}>
                        <td className="px-4 py-3 font-black text-indigo-600">{row.purchaseCode}</td>
                        <td className="px-4 py-3 text-slate-600 font-semibold">{row.supplierName}</td>
                        <td className="px-4 py-3 text-slate-500">{row.purchaseDate}</td>
                        <td className="px-4 py-3 text-right font-bold text-slate-700">{row.qty}</td>
                        <td className="px-4 py-3 text-right font-black text-slate-800">
                          {row.unitCost.toLocaleString()} <span className="text-[9px] text-slate-400 font-bold">MMK</span>
                        </td>
                        <td className="px-4 py-3 text-center">
                          <button type="button"
                            onClick={(e) => { e.stopPropagation(); setPickedCost(row.unitCost); setPickedSelling(String(row.unitCost)); }}
                            className={`px-3 py-1 rounded-lg text-[10px] font-black transition-all ${pickedCost === row.unitCost ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-indigo-100 hover:text-indigo-700'}`}>
                            {pickedCost === row.unitCost ? '✓ ရွေးထားပြီ' : 'ရွေးရန်'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            {pickedCost !== null && (
              <div className="px-6 py-4 border-t border-slate-100 bg-slate-50/60 shrink-0 space-y-3">
                <div className="flex items-center gap-3 flex-wrap">
                  <div className="flex items-center gap-2 px-4 py-2.5 bg-white border border-slate-200 rounded-xl">
                    <span className="text-[10px] font-black text-slate-400 uppercase">ကုန်ကျစရိတ်</span>
                    <span className="text-[14px] font-black text-slate-800">{pickedCost.toLocaleString()}</span>
                    <span className="text-[10px] text-slate-400">MMK</span>
                  </div>
                  <div className="flex items-center gap-2 flex-1 min-w-[200px]">
                    <label className="text-[10px] font-black text-slate-500 uppercase whitespace-nowrap">ရောင်းဈေး</label>
                    <div className="relative flex-1">
                      <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300" size={14} />
                      <input
                        type="number" min="0"
                        value={pickedSelling}
                        onChange={e => setPickedSelling(e.target.value)}
                        className="w-full pl-8 pr-3 py-2.5 bg-white border border-indigo-300 rounded-xl text-[12px] font-bold outline-none focus:border-indigo-500 shadow-sm"
                        placeholder="ရောင်းဈေး ထည့်ပါ"
                        autoFocus
                      />
                    </div>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button type="button" onClick={() => setIsPricePickerOpen(false)}
                    className="flex-1 py-2.5 rounded-xl bg-slate-100 text-slate-500 text-[11px] font-black uppercase hover:bg-slate-200 transition-all">
                    မလုပ်တော့
                  </button>
                  <button type="button"
                    onClick={() => {
                      const selling = Math.max(0, Number(pickedSelling) || 0);
                      setFormData(prev => ({ ...prev, costPrice: pickedCost!, sellingPrice: selling }));
                      setIsPricePickerOpen(false);
                    }}
                    className="flex-[2] py-2.5 rounded-xl bg-indigo-600 text-white text-[11px] font-black uppercase hover:bg-indigo-700 flex items-center justify-center gap-1.5 transition-all">
                    <Save size={13} /> ဈေးနှုန်း သတ်မှတ်ရန်
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );

  return (
    <div className="space-y-3 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden text-left">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-indigo-600 rounded-lg flex items-center justify-center text-white shrink-0">
            <LayoutList size={24} />
          </div>
          <div>
            <h2 className="text-lg font-bold text-slate-800">ကုန်ပစ္စည်း မာစတာ စာရင်း</h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={handleDownloadTemplate}
            disabled={templateLoading}
            className="bg-teal-600 text-white px-3 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-teal-700 disabled:opacity-60"
            title="Import Format Template ဒေါင်းလုပ်ဆွဲရန်"
          >
            {templateLoading ? <Loader2 size={14} className="animate-spin" /> : <FileDown size={14} />}
            Template
          </button>
          <button
            onClick={() => setIsImportModalOpen(true)}
            className="bg-emerald-600 text-white px-3 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-emerald-700"
            title="Excel မှ ကုန်ပစ္စည်းတွေ Import လုပ်ရန်"
          >
            <Upload size={14} /> Import
          </button>
          <div className="relative">
            <div className="flex rounded-lg overflow-hidden">
              <button
                onClick={handleExportFiltered}
                disabled={exportLoading}
                className="bg-green-600 text-white px-3 py-2 text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-green-700 disabled:opacity-60"
                title={`ပြသနေသော ကုန်ပစ္စည်း ${productGroups.reduce((s, g) => s + g.products.length, 0)} ခု CSV Export လုပ်ရန်`}
              >
                {exportLoading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
                Export{(activeFilterCount > 0 || searchTerm) ? ` (${productGroups.reduce((s, g) => s + g.products.length, 0)})` : ''}
              </button>
              <button
                onClick={() => setExportDropdownOpen(v => !v)}
                className="bg-green-700 text-white px-1.5 py-2 hover:bg-green-800 border-l border-green-500"
              >
                <ChevronDown size={12} />
              </button>
            </div>
            {exportDropdownOpen && (
              <div className="absolute right-0 top-full mt-1 z-50 bg-white border border-slate-200 rounded-xl shadow-xl min-w-[190px]" onMouseLeave={() => setExportDropdownOpen(false)}>
                <button
                  onClick={() => { handleExportFiltered(); setExportDropdownOpen(false); }}
                  className="w-full flex items-center gap-2 px-4 py-2.5 text-xs font-bold text-slate-700 hover:bg-green-50 hover:text-green-700 rounded-t-xl"
                >
                  <FileSpreadsheet size={13} />
                  Filter CSV ({productGroups.reduce((s, g) => s + g.products.length, 0)} ခု)
                </button>
                <button
                  onClick={() => { handleExportExcel(); setExportDropdownOpen(false); }}
                  disabled={exportLoading}
                  className="w-full flex items-center gap-2 px-4 py-2.5 text-xs font-bold text-slate-700 hover:bg-emerald-50 hover:text-emerald-700 rounded-b-xl disabled:opacity-50"
                >
                  <Download size={13} />
                  အားလုံး Excel
                </button>
              </div>
            )}
          </div>
          <button
            onClick={() => navigate(AppRoute.LABEL_DESIGNER)}
            className="bg-violet-600 text-white px-3 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-violet-700"
          >
            <Barcode size={14} /> တံဆိပ်
          </button>
          <button
            onClick={() => handleOpenModal()}
            className="bg-indigo-600 text-white px-3 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-indigo-700"
          >
            <Plus size={14} /> ထည့်ရန်
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-6 gap-2 shrink-0">
        <InventoryMetricCard label="Products" value={stockSummary.totalProducts.toLocaleString()} icon={<Package size={16} />} tone="indigo" />
        <InventoryMetricCard label="Groups" value={stockSummary.totalGroups.toLocaleString()} icon={<LayoutList size={16} />} tone="slate" />
        <InventoryMetricCard label="Available Units" value={stockSummary.availableUnits.toLocaleString()} icon={<CheckCircle2 size={16} />} tone="emerald" />
        <InventoryMetricCard label="Low Stock" value={stockSummary.lowStock.toLocaleString()} icon={<AlertTriangle size={16} />} tone="amber" />
        <InventoryMetricCard label="Out" value={stockSummary.outProducts.toLocaleString()} icon={<AlertCircle size={16} />} tone="rose" />
        <InventoryMetricCard label="Stock Value" value={`${stockSummary.value.toLocaleString()} Ks`} icon={<Wallet size={16} />} tone="teal" />
      </div>

      {/* Primary Filters */}
      <div className="bg-white p-3 sm:p-4 rounded-lg border border-slate-200 space-y-3">
        <div className="flex flex-col sm:flex-row gap-2 sm:gap-3 items-stretch sm:items-center">
          {/* Search + Scan */}
          <div className="relative flex-1 group flex items-center gap-2 min-w-0">
            <div className="relative flex-1 min-w-0">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={16} />
              <input
                type="text"
                placeholder="နာမည်၊ ကုဒ်၊ ဘရန်း သို့မဟုတ် စီရီရယ်ဖြင့် ရှာပါ..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg outline-none text-sm font-medium focus:bg-white focus:border-indigo-500"
              />
            </div>
            <button
              onClick={() => setShowScanSearch(true)}
              title="Scan barcode / QR to search"
              className="shrink-0 flex items-center gap-1.5 px-3 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold uppercase transition-colors"
            >
              <Camera size={15} />
              <span className="hidden sm:inline">စကမ်ဖတ်ရန်</span>
            </button>
          </div>

          {/* Filter button */}
          <button
            type="button"
            onClick={() => setShowFilterTools(prev => !prev)}
            className={`shrink-0 inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg border text-xs font-bold uppercase transition-colors ${
              showFilterTools || activeFilterCount > 0
                ? 'border-indigo-200 bg-indigo-50 text-indigo-700 hover:bg-indigo-100'
                : 'border-slate-200 bg-slate-50 text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Filter size={14} />
            <span>စစ်ထုတ်မည့်ကိရိယာ</span>
            {activeFilterCount > 0 && (
              <span className="min-w-5 h-5 px-1.5 rounded-full bg-indigo-600 text-white text-[10px] font-black inline-flex items-center justify-center">
                {activeFilterCount}
              </span>
            )}
            {showFilterTools ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </div>

        {showFilterTools && (
          <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-3 space-y-2">
            {/* Row 1: condition + status + tracking */}
            <div className="flex flex-wrap gap-2">
              <div className="flex items-center gap-1 p-1 bg-slate-100 border border-slate-200 rounded-lg">
                <span className="text-[10px] font-bold text-slate-500 uppercase px-1.5">အခြေအနေ</span>
                {[
                  { value: 'All',        label: 'အားလုံး' },
                  { value: 'New',        label: 'အသစ်' },
                  { value: 'Second',     label: 'အသုံး' },
                  { value: 'Second_New', label: 'အသစ်နှင့်တူ' },
                ].map(({ value, label }) => (
                  <button
                    key={value}
                    onClick={() => setFilterCondition(value as any)}
                    className={`px-2.5 py-1.5 rounded-md text-xs font-bold uppercase whitespace-nowrap ${
                      filterCondition === value ? 'bg-white text-indigo-700 border border-slate-200 shadow-sm' : 'text-slate-500 hover:text-indigo-700'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="flex items-center gap-1 p-1 bg-slate-100 border border-slate-200 rounded-lg">
                <span className="text-[10px] font-bold text-slate-500 uppercase px-1.5">အဆင့်</span>
                {[
                  { value: 'All',          label: 'All' },
                  { value: 'In Stock',     label: 'ရှိ' },
                  { value: 'Out of Stock', label: 'ကုန်' },
                ].map(({ value: status, label }) => (
                  <button
                    key={status}
                    onClick={() => setFilterStatus(status as any)}
                    className={`px-2.5 py-1.5 rounded-md text-xs font-bold uppercase whitespace-nowrap ${
                      filterStatus === status ? 'bg-white text-indigo-700 border border-slate-200 shadow-sm' : 'text-slate-500 hover:text-indigo-700'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>

              <div className="flex items-center gap-1 p-1 bg-slate-100 border border-slate-200 rounded-lg">
                <span className="text-[10px] font-bold text-slate-500 uppercase px-1.5">Track</span>
                {[
                  { value: 'All',    label: 'All' },
                  { value: 'Serial', label: 'Serial' },
                  { value: 'Qty',    label: 'Qty' },
                ].map(({ value, label }) => (
                  <button
                    key={value}
                    onClick={() => setFilterTracking(value as any)}
                    className={`px-2.5 py-1.5 rounded-md text-xs font-bold uppercase ${
                      filterTracking === value ? 'bg-white text-indigo-700 border border-slate-200 shadow-sm' : 'text-slate-500 hover:text-indigo-700'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            {/* Row 2: selects + action buttons */}
            <div className="flex flex-wrap items-center gap-2">
              <select
                value={filterBrandId}
                onChange={(e) => setFilterBrandId(e.target.value === 'All' ? 'All' : Number(e.target.value))}
                className="h-9 bg-white border border-slate-200 rounded-lg px-3 text-xs font-bold text-slate-600 outline-none focus:border-indigo-500 max-w-[160px]"
              >
                <option value="All">Brand: All</option>
                {brands.map(brand => <option key={brand.id} value={brand.id}>{brand.name}</option>)}
              </select>

              <select
                value={filterCategoryId}
                onChange={(e) => setFilterCategoryId(e.target.value === 'All' ? 'All' : Number(e.target.value))}
                className="h-9 bg-white border border-slate-200 rounded-lg px-3 text-xs font-bold text-slate-600 outline-none focus:border-indigo-500 max-w-[180px]"
              >
                <option value="All">Category: All</option>
                {flatCategoryOptions.map(category => <option key={category.id} value={category.id}>{category.displayName}</option>)}
              </select>

              <button
                onClick={() => setFilterLowStockOnly(v => !v)}
                className={`h-9 px-3 rounded-lg border inline-flex items-center gap-1.5 text-xs font-bold uppercase whitespace-nowrap ${
                  filterLowStockOnly
                    ? 'bg-amber-500 text-white border-amber-500'
                    : 'bg-white text-amber-700 border-amber-200 hover:bg-amber-50'
                }`}
              >
                <AlertTriangle size={13} /> Low stock
              </button>

              <button onClick={resetProductFilters} className="h-9 px-3 text-slate-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg border border-slate-200 bg-white inline-flex items-center gap-1.5 text-xs font-bold uppercase whitespace-nowrap">
                <RotateCcw size={13} /> Reset
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Main Ledger Groups */}
      <div className="bg-white rounded-lg border border-slate-200 flex flex-col flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[1080px]">
            <thead className="sticky top-0 z-30 bg-slate-50 border-b border-slate-200">
              <tr>
                <th className="w-14 px-3"></th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">ကုန်ပစ္စည်းအုပ်စု</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">အခြေအနေ</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">ဘရန်း / အမျိုးအစား</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-center">ရရှိနိုင်သော</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-right">ပစ္စည်းတန်ဖိုး</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-right">ရောင်းဈေး</th>
                <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-right">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {paginatedGroups.length > 0 ? paginatedGroups.map((group) => {
                const isExpanded = expandedGroups.has(group.groupId);
                const hasLowStock = group.products.some(p => lowStockIds.has(p.id));
                const serialProductCount = group.products.filter(p => p.hasSerial !== false).length;
                const qtyProductCount = group.products.length - serialProductCount;
                const hasSerialProducts = serialProductCount > 0;
                const primaryProduct = group.products[0];
                const singleProduct = group.products.length === 1;
                const assignableQtyProduct = group.products.find(p => p.hasSerial === false && Number(p.stockQty ?? p.currentStock ?? 0) > 0);
                const assignableSerialProduct = group.products.find(p => p.hasSerial !== false && Number(p.unlinkedQty ?? 0) > 0);
                const assignableProduct = assignableQtyProduct || assignableSerialProduct;
                return (
                  <React.Fragment key={group.groupId}>
                    <tr
                      onClick={() => handleOpenGroupSerials(group)}
                      className={`group ${hasSerialProducts ? 'cursor-pointer' : ''} ${isExpanded ? 'bg-indigo-50/40' : hasLowStock ? 'bg-amber-50/60 hover:bg-amber-50' : 'hover:bg-slate-50'}`}
                    >
                      <td className="px-3 text-center">
                        <button
                          onClick={(e) => { e.stopPropagation(); toggleGroup(group.groupId); }}
                          className={`p-1.5 rounded-md ${isExpanded ? 'bg-indigo-600 text-white' : 'text-slate-500 hover:bg-slate-100'}`}
                        >
                          {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                        </button>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          {(() => {
                            const anyPhoto = group.products.find(p => p.photoBase64)?.photoBase64;
                            return anyPhoto
                              ? <img src={anyPhoto} alt="" className="w-9 h-9 rounded-md object-contain border border-slate-200 bg-slate-50 shrink-0" />
                              : <div className={`w-9 h-9 rounded-md flex items-center justify-center shrink-0 ${isExpanded ? 'bg-indigo-600 text-white' : hasLowStock ? 'bg-amber-100 text-amber-600 border border-amber-200' : 'bg-indigo-50 text-indigo-600 border border-indigo-100'}`}><Package size={18} /></div>;
                          })()}
                          <div>
                            <div className="flex items-center gap-2">
                              <p className="text-sm font-bold text-slate-800">{group.displayName}</p>
                              {hasLowStock && (
                                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-black bg-amber-100 text-amber-700 border border-amber-200 uppercase tracking-wide">
                                  <AlertTriangle size={9} /> ပစ္စည်းနည်းပါး
                                </span>
                              )}
                            </div>
                            <div className="mt-1 flex flex-wrap items-center gap-1.5">
                              <span className="text-xs text-slate-500 font-medium">{group.products.length} ဖြစ်ရပ်</span>
                              {serialProductCount > 0 && (
                                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded border border-indigo-100 bg-indigo-50 text-[9px] font-black text-indigo-700 uppercase">
                                  <Hash size={9} /> Serial x{serialProductCount}
                                </span>
                              )}
                              {qtyProductCount > 0 && (
                                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded border border-slate-200 bg-slate-50 text-[9px] font-black text-slate-600 uppercase">
                                  <Box size={9} /> Qty x{qtyProductCount}
                                </span>
                              )}
                              {serialProductCount > 0 && qtyProductCount > 0 && (
                                <span className="inline-flex px-1.5 py-0.5 rounded border border-amber-100 bg-amber-50 text-[9px] font-black text-amber-700 uppercase">Mixed</span>
                              )}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-1.5">
                          {(() => {
                            const newCnt       = group.products.filter(p => p.productType === ProductType.NEW).length;
                            const secCnt       = group.products.filter(p => p.productType === ProductType.SECOND).length;
                            const secNewCnt    = group.products.filter(p => p.productType === ProductType.SECOND_NEW).length;
                            return (
                              <>
                                {newCnt    > 0 && <span className="px-2 py-0.5 rounded-md text-[10px] font-bold border bg-indigo-50 text-indigo-600 border-indigo-100">New ×{newCnt}</span>}
                                {secCnt    > 0 && <span className="px-2 py-0.5 rounded-md text-[10px] font-bold border bg-amber-50 text-amber-600 border-amber-100">Second ×{secCnt}</span>}
                                {secNewCnt > 0 && <span className="px-2 py-0.5 rounded-md text-[10px] font-bold border bg-violet-50 text-violet-600 border-violet-100">S.New ×{secNewCnt}</span>}
                              </>
                            );
                          })()}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-2">
                            <Tag size={12} className="text-indigo-500" />
                            <span className="text-xs font-bold text-slate-700">{group.brandName}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <Layers size={12} className="text-slate-300" />
                            <span className="text-xs font-medium text-slate-500">{group.categoryName}</span>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <div className="flex justify-center">
                          {group.totalAvailable > 0 ? (
                            <span className="px-3 py-1 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-100 text-xs font-bold flex items-center gap-2">
                              <CheckCircle2 size={14} /> {group.totalAvailable} ခု
                            </span>
                          ) : (
                            <span className="px-3 py-1 rounded-md bg-rose-50 text-rose-700 border border-rose-100 text-xs font-bold flex items-center gap-2">
                              <AlertCircle size={14} /> ပစ္စည်းကုန်ဆုံး
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span className="text-sm font-bold text-slate-800 tabular-nums">
                          {group.groupStockValue.toLocaleString()} <span className="text-xs text-slate-500 ml-0.5 font-bold uppercase">Ks</span>
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {(() => {
                          const prices = group.products.map(p => p.sellingPrice);
                          const minP = Math.min(...prices);
                          const maxP = Math.max(...prices);
                          return minP === maxP ? (
                            <span className="text-sm font-bold text-indigo-700 tabular-nums">
                              {minP.toLocaleString()} <span className="text-xs text-slate-500 ml-0.5 font-bold uppercase">Ks</span>
                            </span>
                          ) : (
                            <span className="text-xs font-bold text-indigo-700 tabular-nums leading-relaxed">
                              {minP.toLocaleString()} – {maxP.toLocaleString()} <span className="text-[10px] text-slate-500 ml-0.5 font-bold uppercase">Ks</span>
                            </span>
                          );
                        })()}
                      </td>
                      <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                        <div className="flex justify-end gap-2">
                          <button
                            onClick={() => { setBarcodeProduct(primaryProduct); setIsBarcodeModalOpen(true); }}
                            title="Barcode print"
                            className="p-1.5 bg-violet-50 text-violet-700 border border-violet-100 hover:bg-violet-600 hover:text-white rounded-md"
                          >
                            <Barcode size={14} />
                          </button>
                          {assignableProduct && (
                            <button
                              onClick={() => handleOpenAssignSerials(assignableProduct)}
                              title="Assign serial numbers"
                              className="p-1.5 bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-600 hover:text-white rounded-md"
                            >
                              <Hash size={14} />
                            </button>
                          )}
                          {singleProduct ? (
                            <button onClick={() => handleOpenModal(primaryProduct)} title="Edit Product" className="p-1.5 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-md"><Edit2 size={14} /></button>
                          ) : (
                            <button onClick={() => toggleGroup(group.groupId)} title="Product variants" className={`p-1.5 border rounded-md ${isExpanded ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-700 hover:text-white'}`}><LayoutList size={14} /></button>
                          )}
                          {canDelete && singleProduct && (
                            <button onClick={() => handleDeleteProduct(primaryProduct)} title="Delete Product" className="p-1.5 bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-600 hover:text-white rounded-md"><Trash2 size={14} /></button>
                          )}
                        </div>
                      </td>
                    </tr>
                    
                    {/* NESTED BREAKDOWN TABLE WITH INTEGRATED FILTER */}
                    {isExpanded && (
                      <tr className="bg-slate-50">
                        <td colSpan={8} className="px-6 py-4">
                          <div className="bg-white rounded-lg border border-slate-200 overflow-hidden animate-in slide-in-from-top-2 duration-300">
                            <div className="px-4 py-3 bg-slate-50 border-b border-slate-100 flex items-center justify-between">
                               <div className="flex items-center gap-2">
                                  <Info size={14} className="text-indigo-500" />
                                  <span className="text-xs font-bold text-slate-700 uppercase">ပစ္စည်း အသေးစိတ်</span>
                                </div>
                                <div className="flex items-center gap-2 px-3 py-1 bg-white border border-slate-200 rounded-md">
                                   <span className="text-[10px] font-bold text-slate-500 uppercase">အခြေအနေ:</span>
                                   <select
                                     value={getNestedFilter(group.groupId)}
                                     onChange={(e) => setNestedFilterForGroup(group.groupId, e.target.value as 'All' | 'New' | 'Second')}
                                     className="bg-transparent text-xs font-bold text-indigo-700 outline-none uppercase cursor-pointer"
                                   >
                                    <option value="All">အခြေအနေ အားလုံး</option>
                                    <option value="New">အသစ်သာ</option>
                                    <option value="Second">အသုံးပြုပြီးသာ</option>
                                    <option value="Second_New">အသစ်နှင့်တူသောသာ</option>
                                  </select>
                               </div>
                            </div>
                            <table className="w-full text-left">
                              <thead className="bg-slate-100">
                                <tr>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">ကုဒ်</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-center">
                                    <div className="flex items-center justify-center gap-2">
                                      အခြေအနေ <Filter size={10} className="text-indigo-400" />
                                    </div>
                                  </th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">မှတ်ချက်</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase">စီရီရယ် / အရေအတွက်</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-center">ကနဦး</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-center">အာမခံ</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-center">အဆင့်</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-right">ဈေးနှုန်း</th>
                                  <th className="px-4 py-3 text-xs font-bold text-slate-600 uppercase text-right">လုပ်ဆောင်ချက်</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-slate-100">
                                {group.products
                                  .filter(p => getNestedFilter(group.groupId) === 'All' || p.productType === getNestedFilter(group.groupId))
                                  .map(p => {
                                  const isSerialTracked = p.hasSerial !== false;
                                  const qtyStock = p.stockQty ?? p.currentStock ?? 0;
                                  const pSerials = allSerials.filter(sr => sr.productId === p.id);
                                  const unlinkedQty = Number(p.unlinkedQty ?? 0);

                                  if (!isSerialTracked) {
                                    return (
                                      <tr key={p.id} className="hover:bg-slate-50 group/row">
                                        <td className="px-4 py-3">
                                          <div className="flex items-center gap-2">
                                            {p.photoBase64
                                              ? <img src={p.photoBase64} alt="" className="w-8 h-8 rounded object-contain border border-slate-100 bg-white shrink-0" />
                                              : <div className="w-8 h-8 rounded bg-slate-100 flex items-center justify-center shrink-0"><Package size={13} className="text-slate-400" /></div>}
                                            <span className="font-bold text-indigo-700 text-xs">{p.productCode}</span>
                                          </div>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                          <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border ${productTypeBadgeClass(p.productType)}`}>
                                            {formatProductType(p.productType)}
                                          </span>
                                        </td>
                                        <td className="px-4 py-3"><p className="text-xs text-slate-500 truncate max-w-[180px]">{p.remark || '-'}</p></td>
                                        <td className="px-4 py-3">
                                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-100 text-slate-700 border border-slate-200 text-xs font-bold">
                                            <Box size={12} />
                                            Qty: {qtyStock}
                                          </span>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                          <OpeningStockBadge product={p} />
                                        </td>
                                        <td className="px-4 py-3 text-center text-xs font-semibold text-slate-600">
                                          {formatWarranty(p)}
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                          <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border ${
                                            qtyStock > 0 ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-rose-50 text-rose-600 border-rose-100'
                                          }`}>
                                            {qtyStock > 0 ? 'ပစ္စည်းရှိ' : 'ပစ္စည်းကုန်ဆုံး'}
                                          </span>
                                        </td>
                                        <td className="px-4 py-3 text-right font-bold text-slate-800 text-xs">{p.sellingPrice.toLocaleString()}</td>
                                        <td className="px-4 py-3 text-right">
                                          <div className="flex justify-end gap-2">
                                            {qtyStock > 0 && (
                                              <button
                                                onClick={() => handleOpenAssignSerials(p)}
                                                title="Assign serial numbers to existing stock"
                                                className="p-1.5 bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-600 hover:text-white rounded-md"
                                              >
                                                <Hash size={14} />
                                              </button>
                                            )}
                                            <button onClick={() => handleOpenWarrantyEdit(p)} title="အာမခံ ပြင်ဆင်" className="p-1.5 bg-emerald-50 text-emerald-700 border border-emerald-100 hover:bg-emerald-600 hover:text-white rounded-md"><Shield size={14} /></button>
                                            <button onClick={() => handleOpenModal(p)} title="Edit Product" className="p-1.5 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-md"><Edit2 size={14} /></button>

                                            {canDelete && (
                                              <button onClick={() => handleDeleteProduct(p)} title="Delete Product" className="p-1.5 bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-600 hover:text-white rounded-md"><Trash2 size={14} /></button>
                                            )}
                                          </div>
                                        </td>
                                      </tr>
                                    );
                                  }

                                  if (pSerials.length === 0) {
                                    return (
                                      <tr key={p.id} className="hover:bg-slate-50 group/row">
                                        <td className="px-4 py-3">
                                          <div className="flex items-center gap-2">
                                            {p.photoBase64
                                              ? <img src={p.photoBase64} alt="" className="w-8 h-8 rounded object-contain border border-slate-100 bg-white shrink-0" />
                                              : <div className="w-8 h-8 rounded bg-slate-100 flex items-center justify-center shrink-0"><Package size={13} className="text-slate-400" /></div>}
                                            <span className="font-bold text-indigo-700 text-xs">{p.productCode}</span>
                                          </div>
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                          <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border ${productTypeBadgeClass(p.productType)}`}>
                                            {formatProductType(p.productType)}
                                          </span>
                                        </td>
                                        <td className="px-4 py-3"><p className="text-xs text-slate-500 truncate max-w-[180px]">{p.remark || '-'}</p></td>
                                        <td className="px-4 py-3 text-xs text-rose-600 font-semibold">စီရီရယ် မချိတ်ဆက်ရသေး</td>
                                        <td className="px-4 py-3 text-center">
                                          <OpeningStockBadge product={p} />
                                        </td>
                                        <td className="px-4 py-3 text-center text-xs font-semibold text-slate-600">
                                          {formatWarranty(p)}
                                        </td>
                                        <td className="px-4 py-3 text-center text-slate-400 text-xs">-</td>
                                        <td className="px-4 py-3 text-right font-bold text-slate-800 text-xs">{p.sellingPrice.toLocaleString()}</td>
                                        <td className="px-4 py-3 text-right">
                                          <div className="flex justify-end gap-2">
                                            {unlinkedQty > 0 && (
                                              <button
                                                onClick={() => handleOpenAssignSerials(p)}
                                                title="Assign serial numbers to unlinked stock"
                                                className="p-1.5 bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-600 hover:text-white rounded-md"
                                              >
                                                <Hash size={14} />
                                              </button>
                                            )}
                                            <button onClick={() => handleOpenModal(p)} title="Edit Product" className="p-1.5 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-md"><Edit2 size={14} /></button>

                                            {canDelete && (
                                              <button onClick={() => handleDeleteProduct(p)} title="Delete Product" className="p-1.5 bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-600 hover:text-white rounded-md"><Trash2 size={14} /></button>
                                            )}
                                          </div>
                                        </td>
                                      </tr>
                                    );
                                  }

                                  return pSerials.map((s, idx) => (
                                    <tr key={s.id} className="hover:bg-slate-50 group/row">
                                      <td className="px-4 py-3">
                                        {idx === 0 ? (
                                          <div className="flex items-center gap-2">
                                            {p.photoBase64
                                              ? <img src={p.photoBase64} alt="" className="w-8 h-8 rounded object-contain border border-slate-100 bg-white shrink-0" />
                                              : <div className="w-8 h-8 rounded bg-slate-100 flex items-center justify-center shrink-0"><Package size={13} className="text-slate-400" /></div>}
                                            <span className="font-black text-indigo-600 text-[11px]">{p.productCode}</span>
                                          </div>
                                        ) : <span className="text-slate-200 font-bold ml-2">↳</span>}
                                      </td>
                                      <td className="px-4 py-3 text-center">
                                        {idx === 0 ? (
                                          <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border ${productTypeBadgeClass(p.productType)}`}>
                                            {formatProductType(p.productType)}
                                          </span>
                                        ) : <span className="text-[10px] text-slate-400">same</span>}
                                      </td>
                                      <td className="px-4 py-3">
                                        {idx === 0 ? <p className="text-xs text-slate-500 truncate max-w-[180px]">{p.remark || '-'}</p> : <span className="text-slate-300">-</span>}
                                      </td>
                                      <td className="px-4 py-3 font-bold text-slate-700 text-xs">{s.serialNumber}</td>
                                      <td className="px-4 py-3 text-center">
                                        {idx === 0 ? <OpeningStockBadge product={p} /> : <span className="text-slate-300">-</span>}
                                      </td>
                                      <td className="px-4 py-3 text-center text-xs font-semibold text-slate-600">
                                        {formatSerialWarranty(s, p)}
                                      </td>
                                      <td className="px-4 py-3 text-center">
                                        <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold uppercase border ${
                                          s.status === SerialStatus.AVAILABLE ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-slate-50 text-slate-500 border-slate-200'
                                        }`}>{s.status.replace(/_/g, ' ')}</span>
                                      </td>
                                      <td className="px-4 py-3 text-right font-bold text-slate-800 text-xs">
                                        {idx === 0 ? p.sellingPrice.toLocaleString() : <span className="text-slate-300 font-normal">-</span>}
                                      </td>
                                      <td className="px-4 py-3 text-right">
                                        <div className="flex justify-end gap-2">
                                          {idx === 0 ? (
                                            <>
                                              {unlinkedQty > 0 && (
                                                <button onClick={() => handleOpenAssignSerials(p)} title="Assign serial numbers to unlinked stock" className="p-1.5 bg-amber-50 text-amber-700 border border-amber-200 hover:bg-amber-600 hover:text-white rounded-md"><Hash size={14} /></button>
                                              )}
                                              <button onClick={() => { setSelectedSerialGroup(group); startEditSerial(s); }} title="Edit Serial Warranty" className="p-1.5 bg-emerald-50 text-emerald-700 border border-emerald-100 hover:bg-emerald-600 hover:text-white rounded-md"><Shield size={14} /></button>
                                              <button onClick={() => handleOpenModal(p)} title="Edit Product" className="p-1.5 bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-md"><Edit2 size={14} /></button>
                                              {canDelete && (
                                                <button onClick={() => handleDeleteProduct(p)} title="Delete Product" className="p-1.5 bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-600 hover:text-white rounded-md"><Trash2 size={14} /></button>
                                              )}
                                            </>
                                          ) : (
                                            <>
                                              <button onClick={() => { setSelectedSerialGroup(group); startEditSerial(s); }} title="Edit Serial Warranty" className="p-1.5 text-emerald-600 hover:text-white hover:bg-emerald-600 rounded-md"><Shield size={12} /></button>
                                              <button onClick={() => handleDeleteSerial(s.id)} title="Remove Serial" className="p-1.5 text-rose-500 hover:text-rose-700 hover:bg-rose-50 rounded-md"><Trash2 size={12} /></button>
                                            </>
                                          )}
                                        </div>
                                      </td>
                                    </tr>
                                  ));
                                })}
                                {group.products.filter(p => getNestedFilter(group.groupId) === 'All' || p.productType === getNestedFilter(group.groupId)).length === 0 && (
                                  <tr>
                                    <td colSpan={9} className="px-6 py-10 text-center text-[10px] font-black text-slate-300 uppercase tracking-widest italic">
                                      ဤအခြေအနေနှင့် ကိုက်ညီသောဖြစ်ရပ်မရှိပါ: {getNestedFilter(group.groupId)}
                                    </td>
                                  </tr>
                                )}
                              </tbody>
                            </table>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              }) : (
                <tr>
                  <td colSpan={8} className="px-6 py-40 text-center text-slate-400">
                    <div className="flex flex-col items-center gap-5">
                      <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center border border-dashed border-slate-200">
                        <Filter className="text-slate-200" size={40} />
                      </div>
                      <p className="text-sm font-black uppercase tracking-widest text-slate-400 italic">ကိုက်ညီသောကုန်ပစ္စည်းဒေတာ မရှိပါ</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Footer Pagination */}
        {productGroups.length > 0 && (
          <div className="sticky bottom-0 z-30 px-10 py-6 bg-white border-t border-slate-100 flex flex-col md:flex-row items-center justify-between gap-8 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest flex items-center gap-2">
                ကုန်ပစ္စည်း ညွှန်းကိန်း <span className="w-6 h-[1px] bg-slate-200"></span>
                ပြသနေသည် <span className="text-indigo-600">{startIndex + 1}-{Math.min(startIndex + itemsPerPage, productGroups.length)}</span>
                / <span className="text-slate-800">{productGroups.length}</span> အဓိကအုပ်စု
              </span>
            </div>

            <div className="w-full md:w-auto flex flex-col sm:flex-row items-center justify-center gap-5 order-1 md:order-2">
              <select 
                value={itemsPerPage} onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(1); }}
                className="appearance-none bg-slate-50 border border-slate-200 rounded-xl px-5 py-2.5 pr-12 text-[11px] font-black text-slate-600 outline-none focus:bg-white focus:border-indigo-500 cursor-pointer transition-all shadow-sm"
              >
                <option value={10}>၁၀ ခုပြ</option>
                <option value={25}>၂၅ ခုပြ</option>
                <option value={50}>၅၀ ခုပြ</option>
              </select>

              <div className="flex items-center gap-2">
                <button disabled={currentPage === 1} onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))} className="p-3 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"><ChevronLeft size={18} /></button>
                <div className="flex gap-1.5">
                  {pageNumbers.map((page, i) => page === '...' ? (
                    <span key={`ellipsis-${i}`} className="min-w-[42px] h-11 px-2 inline-flex items-center justify-center text-slate-300 font-black">
                      ...
                    </span>
                  ) : (
                    <button
                      key={page}
                      onClick={() => setCurrentPage(page)}
                      className={`min-w-[42px] h-11 rounded-xl text-[12px] font-black transition-all ${currentPage === page ? 'bg-indigo-600 text-white shadow-xl border-indigo-600' : 'bg-white border border-slate-100 text-slate-500 hover:bg-slate-100'}`}
                    >
                      {page}
                    </button>
                  ))}
                </div>
                <button disabled={currentPage === totalPages} onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))} className="p-3 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"><ChevronRight size={18} /></button>
              </div>
            </div>
          </div>
        )}
      </div>

      {selectedSerialGroup && (() => {
        const serialProducts = selectedSerialGroup.products.filter(product => product.hasSerial !== false);
        const serialRows = serialProducts.flatMap(product =>
          allSerials
            .filter(serial => serial.productId === product.id)
            .map(serial => ({ product, serial }))
        );
        const availableCount = serialRows.filter(row => row.serial.status === SerialStatus.AVAILABLE).length;

        return (
          <div className="fixed inset-0 z-[103] flex items-center justify-center p-4 bg-slate-900/45 backdrop-blur-sm animate-in fade-in duration-150" onClick={() => { cancelEditSerial(); setSelectedSerialGroup(null); }}>
            <div className="bg-white w-full max-w-2xl rounded-2xl shadow-2xl border border-slate-200 flex flex-col max-h-[86vh] overflow-hidden" onClick={(e) => e.stopPropagation()}>
              <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-xl bg-indigo-600 text-white flex items-center justify-center shrink-0"><Hash size={18} /></div>
                  <div className="min-w-0">
                    <h3 className="text-sm font-black text-slate-800 truncate">{selectedSerialGroup.displayName}</h3>
                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest truncate">
                      Serial List - {serialRows.length} units - {availableCount} available
                    </p>
                  </div>
                </div>
                <button onClick={() => { cancelEditSerial(); setSelectedSerialGroup(null); }} className="p-2 rounded-lg text-slate-400 hover:bg-white hover:text-slate-700 border border-transparent hover:border-slate-200">
                  <X size={18} />
                </button>
              </div>

              <div className="px-5 py-3 border-b border-slate-100 flex flex-wrap gap-2 bg-white">
                {serialProducts.map(product => {
                  const count = allSerials.filter(serial => serial.productId === product.id).length;
                  return (
                    <span key={product.id} className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border border-slate-200 bg-slate-50 text-[11px] font-bold text-slate-600">
                      <Package size={12} /> {product.productCode} - {formatProductType(product.productType)} - {count}
                    </span>
                  );
                })}
              </div>

              <div className="flex-1 overflow-y-auto custom-scrollbar bg-slate-50/60">
                {serialRows.length === 0 ? (
                  <div className="py-16 flex flex-col items-center justify-center gap-3 text-slate-300">
                    <Hash size={34} />
                    <p className="text-[11px] font-black uppercase tracking-widest">No serial numbers yet</p>
                  </div>
                ) : (
                  <table className="w-full text-left text-xs">
                    <thead className="sticky top-0 bg-white border-b border-slate-200 text-[10px] font-black text-slate-400 uppercase tracking-widest">
                      <tr>
                        <th className="px-4 py-3">Serial No</th>
                        <th className="px-4 py-3">Product Code</th>
                        <th className="px-4 py-3 text-center">Warranty</th>
                        <th className="px-4 py-3 text-center">Status</th>
                        <th className="px-4 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                      {serialRows.map(({ product, serial }) => {
                        const isEditing = editingSerialId === serial.id;
                        return (
                          <tr key={serial.id} className="hover:bg-indigo-50/50">
                            <td className="px-4 py-3">
                              {isEditing ? (
                                <input
                                  value={serialEditForm.serialNumber}
                                  onChange={(e) => setSerialEditForm(prev => ({ ...prev, serialNumber: e.target.value }))}
                                  className="w-full min-w-[160px] px-2 py-1.5 rounded-md border border-indigo-200 bg-white text-xs font-black text-slate-800 tabular-nums focus:outline-none focus:border-indigo-500"
                                />
                              ) : (
                                <span className="font-black text-slate-800 tabular-nums">{serial.serialNumber}</span>
                              )}
                            </td>
                            <td className="px-4 py-3">
                              <div className="flex items-center gap-2">
                                {product.photoBase64
                                  ? <img src={product.photoBase64} alt="" className="w-7 h-7 rounded object-contain border border-slate-100 bg-slate-50 shrink-0" />
                                  : <div className="w-7 h-7 rounded bg-slate-100 flex items-center justify-center shrink-0"><Package size={12} className="text-slate-400" /></div>}
                                <div>
                                  <p className="font-bold text-indigo-700">{product.productCode}</p>
                                  <p className="text-[10px] text-slate-400 font-semibold">{formatProductType(product.productType)}</p>
                                </div>
                              </div>
                            </td>
                            <td className="px-4 py-3 text-center font-semibold text-slate-600">
                              {isEditing ? (
                                <div className="flex justify-center gap-1.5">
                                  <input
                                    type="number"
                                    min={0}
                                    value={serialEditForm.warrantyValue}
                                    onChange={(e) => setSerialEditForm(prev => ({ ...prev, warrantyValue: Math.max(0, Number(e.target.value) || 0) }))}
                                    className="w-16 px-2 py-1.5 rounded-md border border-indigo-200 bg-white text-xs text-center font-bold focus:outline-none focus:border-indigo-500"
                                  />
                                  <select
                                    value={serialEditForm.warrantyUnit}
                                    onChange={(e) => setSerialEditForm(prev => ({ ...prev, warrantyUnit: e.target.value as 'ရက်' | 'လ' | 'နှစ်' }))}
                                    className="px-2 py-1.5 rounded-md border border-indigo-200 bg-white text-xs font-bold text-slate-700 focus:outline-none focus:border-indigo-500"
                                  >
                                    <option value="လ">လ</option>
                                    <option value="ရက်">ရက်</option>
                                    <option value="နှစ်">နှစ်</option>
                                  </select>
                                </div>
                              ) : (
                                formatSerialWarranty(serial, product)
                              )}
                            </td>
                            <td className="px-4 py-3 text-center">
                              {isEditing ? (
                                <select
                                  value={serialEditForm.status}
                                  onChange={(e) => setSerialEditForm(prev => ({ ...prev, status: e.target.value as SerialStatus }))}
                                  className="px-2 py-1.5 rounded-md border border-indigo-200 bg-white text-[10px] font-black uppercase text-slate-700 focus:outline-none focus:border-indigo-500"
                                >
                                  {Object.values(SerialStatus).map(status => <option key={status} value={status}>{status.replace(/_/g, ' ')}</option>)}
                                </select>
                              ) : (
                                <span className={`px-2 py-0.5 rounded-md text-[10px] font-black uppercase border ${serial.status === SerialStatus.AVAILABLE ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-slate-50 text-slate-500 border-slate-200'}`}>
                                  {serial.status.replace(/_/g, ' ')}
                                </span>
                              )}
                            </td>
                            <td className="px-4 py-3 text-right">
                              {isEditing ? (
                                <div className="flex justify-end gap-1.5">
                                  <button onClick={() => handleSaveSerialEdit(serial)} disabled={serialEditSaving || !serialEditForm.serialNumber.trim()} title="Save serial" className="p-1.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-100 hover:bg-emerald-600 hover:text-white disabled:opacity-50">
                                    {serialEditSaving ? <Loader2 size={13} className="animate-spin" /> : <Save size={13} />}
                                  </button>
                                  <button onClick={cancelEditSerial} title="Cancel" className="p-1.5 rounded-md bg-slate-50 text-slate-500 border border-slate-200 hover:bg-slate-200"><X size={13} /></button>
                                </div>
                              ) : (
                                <button onClick={() => startEditSerial(serial)} title="Edit serial" className="p-1.5 rounded-md bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-600 hover:text-white">
                                  <Edit2 size={13} />
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>

              <div className="px-5 py-3 border-t border-slate-100 bg-white flex justify-end">
                <button onClick={() => { cancelEditSerial(); setSelectedSerialGroup(null); }} className="px-5 py-2 rounded-lg bg-slate-100 text-slate-600 text-xs font-bold hover:bg-slate-200">Close</button>
              </div>
            </div>
          </div>
        );
      })()}
      {/* Warranty Quick-Edit Modal — qty products only */}
      {isWarrantyModalOpen && warrantyProduct && (
        <div className="fixed inset-0 z-[102] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm" onClick={() => setIsWarrantyModalOpen(false)}>
          <div className="bg-white w-full max-w-sm rounded-2xl shadow-2xl border border-slate-200" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-emerald-50 text-emerald-600 rounded-lg"><Shield size={15} /></div>
                <div>
                  <p className="text-sm font-bold text-slate-800">အာမခံ ပြင်ဆင်</p>
                  <p className="text-[10px] text-slate-400">{warrantyProduct.productCode} · {warrantyProduct.name}</p>
                </div>
              </div>
              <button onClick={() => setIsWarrantyModalOpen(false)} className="p-1.5 hover:bg-slate-100 rounded-lg text-slate-400"><X size={15} /></button>
            </div>
            <div className="px-5 py-4 space-y-4">
              <div>
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">အာမခံကာလ</label>
                <div className="mt-1 flex gap-2">
                  <input
                    type="number"
                    min={0}
                    value={warrantyValue}
                    onChange={e => setWarrantyValue(Math.max(0, Number(e.target.value) || 0))}
                    className="flex-1 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-emerald-400"
                    placeholder="0"
                  />
                  <select
                    value={warrantyUnit}
                    onChange={e => setWarrantyUnit(e.target.value as 'ရက်' | 'လ' | 'နှစ်')}
                    className="border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-emerald-400 bg-white"
                  >
                    <option value="ရက်">ရက်</option>
                    <option value="လ">လ</option>
                    <option value="နှစ်">နှစ်</option>
                  </select>
                </div>
                {warrantyValue > 0 && (
                  <p className="mt-1 text-[10px] text-emerald-600 font-semibold">
                    = {warrantyUnit === 'ရက်' ? warrantyValue : toWarrantyMonths(warrantyValue, warrantyUnit)} {warrantyUnit === 'ရက်' ? 'ရက်' : 'လ'} (တွက်ချက်မှု)
                  </p>
                )}
              </div>
              <div>
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">အာမခံ မှတ်ချက် (ရွေးချယ်မှုဖြင့်)</label>
                <textarea
                  rows={2}
                  value={warrantyNote}
                  onChange={e => setWarrantyNote(e.target.value)}
                  className="mt-1 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-emerald-400 resize-none"
                  placeholder="ဥပမာ: ရေ / မီး ထိမိမှု မပါ"
                />
              </div>
              {warrantyValue > 0 && (
                <div className="bg-emerald-50 border border-emerald-100 rounded-lg px-3 py-2">
                  <p className="text-[10px] text-slate-500 font-bold uppercase tracking-widest mb-0.5">ပြသမည့် ပုံစံ</p>
                  <p className="text-sm font-bold text-emerald-700">
                    {warrantyValue} {warrantyUnit}{warrantyNote ? ' · ' + warrantyNote : ''}
                  </p>
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2 px-5 py-3 border-t border-slate-100">
              <button onClick={() => setIsWarrantyModalOpen(false)} className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg">မလုပ်တော့</button>
              <button onClick={handleSaveWarranty} disabled={warrantySaving} className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg disabled:opacity-60">
                {warrantySaving ? <Loader2 size={12} className="animate-spin" /> : <Save size={12} />}
                သိမ်းမည်
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Assign Serials Modal — retroactively convert qty-only stock to serial-tracked */}
      {isAssignSerialsOpen && assignSerialsProduct && (
        <div className="fixed inset-0 z-[102] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white w-full max-w-2xl rounded-2xl shadow-2xl border border-slate-200 flex flex-col max-h-[90vh] overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
              <div>
                <h3 className="font-bold text-slate-800 text-sm">စီရီရယ်နံပါတ် သတ်မှတ်ရန်</h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  {assignSerialsProduct.name} · {assignSerialsProduct.productCode} · ပစ္စည်းတွင် {getAssignableSerialQty(assignSerialsProduct)} ခုရှိသည်
                </p>
              </div>
              <button onClick={() => setIsAssignSerialsOpen(false)} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400">
                <X size={18} />
              </button>
            </div>

            {/* Info banner */}
            <div className="mx-6 mt-4 px-3 py-2.5 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-start gap-2">
              <Hash size={13} className="mt-0.5 shrink-0 text-amber-600" />
              <span>
                သိမ်းပြီးနောက် <strong>{assignSerialsProduct.name}</strong> သည် <strong>စီရီရယ်ဖြင့် ခြေရာခံမည်</strong>ဖြစ်သည်။
                ကုန်သိုလှောင်မှုကို စီရီရယ်နံပါတ်တစ်ခုချင်းစီဖြင့် စီမံခန့်ခွဲမည်။
              </span>
            </div>

            {/* Warranty input */}
            <div className="px-6 pt-4 flex items-center gap-3">
              <label className="text-xs font-bold text-slate-500 uppercase tracking-wide whitespace-nowrap">အာမခံ (လ)</label>
              <input
                type="number"
                min={0}
                value={assignSerialsWarranty}
                onChange={(e) => setAssignSerialsWarranty(Math.max(0, Number(e.target.value) || 0))}
                className="w-24 px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-sm text-center focus:outline-none focus:border-indigo-400"
              />
              <span className="text-xs text-slate-400">စီရီရယ်အားလုံးသို့ ပြုသည်။ မသိလျှင် ၀ ထည့်ပါ။</span>
              <button
                onClick={() => setAssignSerialsInputs(genSerials(assignSerialsProduct.productCode || String(assignSerialsProduct.id), getAssignableSerialQty(assignSerialsProduct)))}
                className="ml-auto text-xs font-semibold text-indigo-600 hover:underline"
              >
                ပြန်ထုတ်ရန်
              </button>
            </div>

            {/* Serial inputs */}
            <div className="px-6 py-4 overflow-y-auto flex-1">
              <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-3">
                စီရီရယ်နံပါတ်များ — {assignSerialsInputs.length} ခု
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                {assignSerialsInputs.map((sn, i) => (
                  <div key={i} className="flex items-center gap-1.5">
                    <span className="text-[10px] font-bold text-slate-400 w-6 text-right shrink-0">#{i + 1}</span>
                    <input
                      type="text"
                      value={sn}
                      onChange={(e) => {
                        const next = [...assignSerialsInputs];
                        next[i] = e.target.value;
                        setAssignSerialsInputs(next);
                      }}
                      placeholder={`Serial #${i + 1}`}
                      className={`flex-1 px-2 py-1.5 rounded-md border text-xs font-mono focus:outline-none focus:ring-1 focus:ring-indigo-500 ${!sn.trim() ? 'border-red-300 bg-red-50' : 'border-slate-200 bg-white'}`}
                    />
                  </div>
                ))}
              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t border-slate-200 flex justify-end gap-2">
              <button onClick={() => setIsAssignSerialsOpen(false)} className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg font-medium">
                မလုပ်တော့
              </button>
              <button
                onClick={handleSaveAssignSerials}
                disabled={assignSerialsSaving || assignSerialsInputs.some((s) => !s.trim())}
                className="flex items-center gap-2 px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-bold hover:bg-indigo-700 disabled:opacity-50"
              >
                <Save size={15} />
                {assignSerialsSaving ? 'သိမ်းနေသည်...' : `${assignSerialsInputs.length} ခု စီရီရယ်သတ်မှတ်ရန်`}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Serial Management Modal */}
      {isSerialModalOpen && selectedProductForSerial && (
        <div className="fixed inset-0 z-[101] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200 text-left">
          <div className="bg-white w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 flex flex-col max-h-[85vh] overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between shrink-0 bg-slate-50/50">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <div className="p-1.5 bg-indigo-600 text-white rounded-lg"><Hash size={14} /></div>
                  <h3 className="text-[14px] font-black text-slate-800 uppercase tracking-tight">စီရီရယ်ယူနစ် မှတ်ပုံတင်ရန်</h3>
                </div>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest truncate max-w-[200px]">{selectedProductForSerial.name} ({selectedProductForSerial.productCode})</p>
              </div>
              <button onClick={() => setIsSerialModalOpen(false)} className="p-2.5 hover:bg-white hover:shadow-md rounded-2xl transition-all border border-transparent hover:border-slate-100"><X size={20} className="text-slate-400" /></button>
            </div>
            
            <div className="p-8 border-b border-slate-50 bg-white shrink-0">
              <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 mb-3 block">ယူနစ်မှတ်ပုံ (ဘားကုဒ်/စီရီရယ်)</label>
              <div className="flex gap-3">
                <div className="relative flex-1 group">
                  <Barcode className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input 
                    type="text" autoFocus value={newSerialInput} onChange={(e) => setNewSerialInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleAddSerial()}
                    className="w-full pl-12 pr-4 py-4 bg-slate-50 border border-slate-200 rounded-[1.25rem] text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white shadow-sm transition-all"
                    placeholder="E.g. SN-882-GTX"
                  />
                </div>
                <button onClick={handleAddSerial} disabled={saving || !newSerialInput.trim()} className="bg-indigo-600 text-white px-5 rounded-[1.25rem] shadow-xl shadow-indigo-100 disabled:opacity-50 transition-all flex items-center justify-center hover:bg-indigo-700 active:scale-95"><Send size={20} /></button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-6 custom-scrollbar bg-slate-50/20">
              <div className="space-y-3">
                <div className="px-2 mb-2 text-[9px] font-black text-slate-400 uppercase tracking-widest">လက်ရှိ ယူနစ်များ ({allSerials.filter(s => s.productId === selectedProductForSerial.id).length})</div>
                {allSerials.filter(s => s.productId === selectedProductForSerial.id).map((s) => (
                  <div key={s.id} className="flex items-center justify-between p-4 bg-white border border-slate-100 rounded-2xl hover:border-indigo-200 transition-all group shadow-sm">
                    <div className="flex items-center gap-4">
                      <div className="w-10 h-10 rounded-xl bg-slate-50 flex items-center justify-center text-slate-400 group-hover:bg-indigo-50 group-hover:text-indigo-600 transition-colors"><Hash size={16} /></div>
                      <div>
                        <p className="text-[11px] font-black text-slate-700 tracking-tight">{s.serialNumber}</p>
                        <span className="text-[9px] font-black text-emerald-600 uppercase tracking-widest">{s.status.replace(/_/g, ' ')}</span>
                      </div>
                    </div>
                    <button
                      onClick={() => handleDeleteSerial(s.id)}
                      title="Delete Serial"
                      className="p-2 rounded-xl text-rose-300 hover:text-rose-600 hover:bg-rose-50 transition-all"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}
              </div>
            </div>
            <div className="p-6 border-t border-slate-100 bg-white flex justify-center shrink-0">
               <button onClick={() => setIsSerialModalOpen(false)} className="px-10 py-3 rounded-2xl text-[11px] font-black uppercase tracking-widest text-slate-500 hover:text-slate-800 transition-all">ပိတ်ရန်</button>
            </div>
          </div>
        </div>
      )}

      {/* (form is now full-page via showForm early return above) */}
      {false && (
        <div>
            
        </div>
      )}

      {/* Purchase History Price Picker Modal */}
      {isPricePickerOpen && (
        <div className="fixed inset-0 z-[250] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white w-full max-w-2xl rounded-3xl shadow-2xl border border-slate-200 flex flex-col max-h-[85vh]">
            {/* Header */}
            <div className="px-6 py-5 border-b border-slate-100 flex items-center justify-between bg-slate-50/60 shrink-0">
              <div>
                <h3 className="text-[13px] font-black text-slate-800 uppercase tracking-tight flex items-center gap-2">
                  <ClipboardList size={15} className="text-indigo-600" /> ဝယ်ယူမှု မှတ်တမ်း — {editingProduct?.name}
                </h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5 tracking-widest">
                  ဝယ်ထားသော purchase တစ်ခုကို ရွေးပြီး price သတ်မှတ်ပါ
                </p>
              </div>
              <button onClick={() => setIsPricePickerOpen(false)} className="p-2 hover:bg-slate-200 rounded-xl transition-all">
                <X size={18} className="text-slate-400" />
              </button>
            </div>

            {/* Purchase list */}
            <div className="flex-1 overflow-y-auto">
              {pricePickerLoading ? (
                <div className="flex items-center justify-center py-16 gap-2 text-slate-400">
                  <Loader2 size={20} className="animate-spin" />
                  <span className="text-[12px] font-bold">ဒေတာ ယူနေသည်...</span>
                </div>
              ) : pricePickerRows.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-slate-300 gap-2">
                  <ClipboardList size={32} />
                  <p className="text-[11px] font-black uppercase tracking-widest">ဝယ်ယူမှု မှတ်တမ်း မတွေ့ပါ</p>
                  <p className="text-[10px] text-slate-400">ဤကုန်ပစ္စည်း ပါဝင်သောဝယ်ယူမှုမရှိသေးပါ</p>
                </div>
              ) : (
                <table className="w-full text-xs">
                  <thead className="sticky top-0 bg-slate-50 border-b border-slate-200">
                    <tr className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
                      <th className="px-4 py-3 text-left">ဝယ်ယူမှု ကုဒ်</th>
                      <th className="px-4 py-3 text-left">ပေးသွင်းသူ</th>
                      <th className="px-4 py-3 text-left">နေ့ရက်</th>
                      <th className="px-4 py-3 text-right">အရေအတွက်</th>
                      <th className="px-4 py-3 text-right">ယူနစ် ကုန်ကျစရိတ်</th>
                      <th className="px-4 py-3 text-center">ရွေးချယ်ရန်</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {pricePickerRows.map((row, i) => (
                      <tr key={i}
                        onClick={() => { setPickedCost(row.unitCost); setPickedSelling(String(row.unitCost)); }}
                        className={`cursor-pointer transition-colors hover:bg-indigo-50 ${pickedCost === row.unitCost && pickedSelling === String(row.unitCost) ? 'bg-indigo-50' : ''}`}>
                        <td className="px-4 py-3 font-black text-indigo-600">{row.purchaseCode}</td>
                        <td className="px-4 py-3 text-slate-600 font-semibold">{row.supplierName}</td>
                        <td className="px-4 py-3 text-slate-500">{row.purchaseDate}</td>
                        <td className="px-4 py-3 text-right font-bold text-slate-700">{row.qty}</td>
                        <td className="px-4 py-3 text-right font-black text-slate-800">
                          {row.unitCost.toLocaleString()} <span className="text-[9px] text-slate-400 font-bold">MMK</span>
                        </td>
                        <td className="px-4 py-3 text-center">
                          <button type="button"
                            onClick={(e) => { e.stopPropagation(); setPickedCost(row.unitCost); setPickedSelling(String(row.unitCost)); }}
                            className={`px-3 py-1 rounded-lg text-[10px] font-black transition-all ${pickedCost === row.unitCost ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-indigo-100 hover:text-indigo-700'}`}>
                            {pickedCost === row.unitCost ? '✓ ရွေးထားပြီ' : 'ရွေးရန်'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Bottom — selling price input & apply */}
            {pickedCost !== null && (
              <div className="px-6 py-4 border-t border-slate-100 bg-slate-50/60 shrink-0 space-y-3">
                <div className="flex items-center gap-3 flex-wrap">
                  <div className="flex items-center gap-2 px-4 py-2.5 bg-white border border-slate-200 rounded-xl">
                    <span className="text-[10px] font-black text-slate-400 uppercase">ကုန်ကျစရိတ်</span>
                    <span className="text-[14px] font-black text-slate-800">{pickedCost.toLocaleString()}</span>
                    <span className="text-[10px] text-slate-400">MMK</span>
                  </div>
                  <div className="flex items-center gap-2 flex-1 min-w-[200px]">
                    <label className="text-[10px] font-black text-slate-500 uppercase whitespace-nowrap">ရောင်းဈေး</label>
                    <div className="relative flex-1">
                      <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300" size={14} />
                      <input
                        type="number" min="0"
                        value={pickedSelling}
                        onChange={e => setPickedSelling(e.target.value)}
                        className="w-full pl-8 pr-3 py-2.5 bg-white border border-indigo-300 rounded-xl text-[12px] font-bold outline-none focus:border-indigo-500 shadow-sm"
                        placeholder="ရောင်းဈေး ထည့်ပါ"
                        autoFocus
                      />
                    </div>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button type="button" onClick={() => setIsPricePickerOpen(false)}
                    className="flex-1 py-2.5 rounded-xl bg-slate-100 text-slate-500 text-[11px] font-black uppercase hover:bg-slate-200 transition-all">
                    မလုပ်တော့
                  </button>
                  <button type="button"
                    onClick={() => {
                      const selling = Math.max(0, Number(pickedSelling) || 0);
                      setFormData(prev => ({ ...prev, costPrice: pickedCost!, sellingPrice: selling }));
                      setIsPricePickerOpen(false);
                    }}
                    className="flex-[2] py-2.5 rounded-xl bg-indigo-600 text-white text-[11px] font-black uppercase hover:bg-indigo-700 flex items-center justify-center gap-1.5 transition-all">
                    <Save size={13} /> ဈေးနှုန်း သတ်မှတ်ရန်
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Barcode Print Modal */}
      {isBarcodeModalOpen && barcodeProduct && (() => {
        const isSerial = barcodeProduct.hasSerial !== false;
        const productSerials = allSerials.filter(s => s.productId === barcodeProduct.id);
        return (
          <div className="fixed inset-0 z-[200] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
            <div className="bg-white w-full max-w-3xl rounded-3xl shadow-2xl border border-slate-200 flex flex-col max-h-[90vh]">
              <div className="p-6 border-b border-slate-100 flex items-center justify-between bg-slate-50/50 shrink-0">
                <div>
                  <h3 className="text-[13px] font-black text-slate-800 uppercase tracking-tight flex items-center gap-2">
                    <Barcode size={16} className="text-violet-600" /> ဘားကုဒ် ပုံနှိပ်ရန် — {barcodeProduct.name}
                  </h3>
                  <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5 tracking-widest">
                    {isSerial ? `စီရီရယ်ယူနစ် ${productSerials.length} ခု` : `စီရီရယ်မပါ · ကုန်ပစ္စည်းကုဒ်`}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => window.print()}
                    className="px-4 py-2 bg-violet-600 text-white rounded-xl text-[11px] font-black uppercase tracking-widest hover:bg-violet-700 flex items-center gap-1.5"
                  >
                    <Barcode size={13} /> ပုံနှိပ်ရန်
                  </button>
                  <button onClick={() => setIsBarcodeModalOpen(false)} className="p-2 hover:bg-slate-100 rounded-xl transition-all">
                    <X size={18} className="text-slate-400" />
                  </button>
                </div>
              </div>

              <div id="barcode-print-area" className="p-6 overflow-y-auto">
                {isSerial ? (
                  productSerials.length === 0 ? (
                    <p className="text-center text-slate-400 text-sm py-10">ဤကုန်ပစ္စည်းနှင့် ချိတ်ဆက်ထားသောစီရီရယ်နံပါတ် မရှိသေးပါ။</p>
                  ) : (
                    <div className="grid grid-cols-3 gap-4">
                      {productSerials.map(s => (
                        <div key={s.id} className="border border-slate-200 rounded-xl p-3 flex flex-col items-center gap-1 bg-white">
                          <BarcodeLabel
                            value={s.serialNumber}
                            label={barcodeProduct.name}
                            subLabel={`S/N · ${s.serialNumber}`}
                            width={1.7}
                            height={40}
                          />
                          <span className={`mt-1 px-2 py-0.5 rounded text-[9px] font-black uppercase ${s.status === SerialStatus.AVAILABLE ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                            {s.status}
                          </span>
                        </div>
                      ))}
                    </div>
                  )
                ) : (
                  <div className="flex justify-center py-4">
                    <div className="border border-slate-200 rounded-xl p-6 flex flex-col items-center gap-2 bg-white w-72 print:page-break-after-always print:break-after-page print:break-inside-avoid print:page-break-inside-avoid">
                      <BarcodeLabel
                        value={barcodeProduct.productCode || String(barcodeProduct.id)}
                        label={barcodeProduct.name}
                        subLabel={`Code · ${barcodeProduct.productCode}`}
                        width={2}
                        height={50}
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })()}

      {showScanSearch && (
        <BarcodeScannerCamera
          onDetected={(code) => {
            setSearchTerm(code);
            setShowScanSearch(false);
          }}
          onClose={() => setShowScanSearch(false)}
        />
      )}

      <style>{`
        @media print {
          @page { margin: 8mm; size: A4; }
          body * { visibility: hidden !important; }
          #barcode-print-area,
          #barcode-print-area * { visibility: visible !important; }
          #barcode-print-area {
            position: absolute !important;
            top: 0 !important; left: 0 !important;
            width: 100% !important;
            overflow: visible !important;
            padding: 8px !important;
          }
          #barcode-print-area .grid {
            display: grid !important;
            grid-template-columns: repeat(1, 1fr) !important;
            gap: 8px !important;
          }
          #barcode-print-area .grid > div {
            width: 100% !important;
            page-break-inside: avoid !important;
            break-inside: avoid-column !important;
            break-inside: avoid-page !important;
            page-break-after: always !important;
            break-after: page !important;
          }
          #barcode-print-area .grid > div:last-child {
            page-break-after: auto !important;
            break-after: auto !important;
          }
          #barcode-print-area > div { overflow: visible !important; }
        }
      `}</style>

      {/* ── Import Excel Modal ─────────────────────────────────── */}
      {isImportModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg flex flex-col overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 bg-emerald-600 rounded-lg flex items-center justify-center text-white">
                  <FileSpreadsheet size={18} />
                </div>
                <div>
                  <h3 className="text-sm font-black text-slate-800 uppercase tracking-tight">Excel မှ Import လုပ်ရန်</h3>
                  <p className="text-[10px] text-slate-400 font-semibold">ကုန်ပစ္စည်းစာရင်း အသုတ်လိုက် ထည့်သွင်းရန်</p>
                </div>
              </div>
              <button onClick={handleCloseImportModal} className="text-slate-400 hover:text-slate-600 transition-colors">
                <X size={20} />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {/* Template hint */}
              {!importResult && (
                <div className="flex items-start gap-3 bg-teal-50 border border-teal-200 rounded-xl px-4 py-3">
                  <FileDown size={16} className="text-teal-600 mt-0.5 shrink-0" />
                  <div className="text-xs text-teal-700">
                    <span className="font-bold">Template မရှိသေးပါက</span> — ညာဘက်အပေါ် Template ခလုတ်မှ ဒေါင်းလုပ်ဆွဲပြီး ဖြည့်သွင်းပါ။
                    Brand / Category / Unit နာမည်များ မှန်ကန်ရပါမည်။
                  </div>
                </div>
              )}

              {/* Result */}
              {importResult ? (
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4 text-center">
                      <CheckCheck size={20} className="text-emerald-600 mx-auto mb-1" />
                      <p className="text-2xl font-black text-emerald-700">{importResult.successCount}</p>
                      <p className="text-[11px] font-bold text-emerald-600 uppercase">အောင်မြင်သည်</p>
                    </div>
                    <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 text-center">
                      <AlertCircle size={20} className="text-rose-500 mx-auto mb-1" />
                      <p className="text-2xl font-black text-rose-600">{importResult.errorCount}</p>
                      <p className="text-[11px] font-bold text-rose-500 uppercase">မအောင်မြင်သည်</p>
                    </div>
                  </div>
                  {importResult.errors.length > 0 && (
                    <div className="bg-rose-50 border border-rose-200 rounded-xl p-3 max-h-48 overflow-y-auto space-y-1.5">
                      <p className="text-[10px] font-black text-rose-600 uppercase tracking-widest mb-2">အမှားများ</p>
                      {importResult.errors.map((e, i) => (
                        <div key={i} className="flex items-start gap-2 text-xs text-rose-700">
                          <span className="bg-rose-200 text-rose-700 font-black rounded px-1.5 py-0.5 text-[10px] shrink-0">Row {e.row}</span>
                          <span>{e.message}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  <button
                    onClick={handleCloseImportModal}
                    className="w-full py-2.5 bg-indigo-600 text-white rounded-xl text-xs font-black uppercase tracking-widest hover:bg-indigo-700"
                  >
                    ပိတ်မည်
                  </button>
                </div>
              ) : (
                <>
                  {/* Drop Zone */}
                  <div
                    onDragOver={e => { e.preventDefault(); setImportDragging(true); }}
                    onDragLeave={() => setImportDragging(false)}
                    onDrop={e => {
                      e.preventDefault();
                      setImportDragging(false);
                      const f = e.dataTransfer.files[0];
                      if (f) handleImportFileChange(f);
                    }}
                    className={`relative border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-all ${
                      importDragging ? 'border-emerald-400 bg-emerald-50' : importFile ? 'border-emerald-400 bg-emerald-50' : 'border-slate-200 hover:border-emerald-300 hover:bg-slate-50'
                    }`}
                    onClick={() => document.getElementById('import-file-input')?.click()}
                  >
                    <input
                      id="import-file-input"
                      type="file"
                      accept=".xlsx,.xls"
                      className="hidden"
                      onChange={e => handleImportFileChange(e.target.files?.[0] ?? null)}
                    />
                    {importFile ? (
                      <div className="flex items-center justify-center gap-3">
                        <FileSpreadsheet size={32} className="text-emerald-500" />
                        <div className="text-left">
                          <p className="text-sm font-bold text-slate-800 truncate max-w-[220px]">{importFile.name}</p>
                          <p className="text-xs text-slate-400">{(importFile.size / 1024).toFixed(1)} KB</p>
                        </div>
                        <button
                          type="button"
                          onClick={e => { e.stopPropagation(); handleImportFileChange(null); }}
                          className="ml-auto text-slate-300 hover:text-rose-400 transition-colors"
                        >
                          <X size={16} />
                        </button>
                      </div>
                    ) : (
                      <>
                        <Upload size={32} className="text-slate-300 mx-auto mb-2" />
                        <p className="text-sm font-bold text-slate-500">ဖိုင်ကို ဤနေရာသို့ ဆွဲချပါ</p>
                        <p className="text-xs text-slate-400 mt-1">သို့မဟုတ် နှိပ်၍ ရွေးချယ်ပါ (.xlsx, .xls)</p>
                      </>
                    )}
                  </div>

                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={handleCloseImportModal}
                      className="flex-1 py-2.5 border border-slate-200 text-slate-600 rounded-xl text-xs font-bold uppercase hover:bg-slate-50"
                    >
                      မလုပ်တော့ပါ
                    </button>
                    <button
                      type="button"
                      onClick={handleImportSubmit}
                      disabled={!importFile || importLoading}
                      className="flex-1 py-2.5 bg-emerald-600 text-white rounded-xl text-xs font-black uppercase tracking-widest hover:bg-emerald-700 disabled:opacity-50 flex items-center justify-center gap-2"
                    >
                      {importLoading ? <><Loader2 size={15} className="animate-spin" /> လုပ်ဆောင်နေသည်...</> : <><Upload size={15} /> Import လုပ်မည်</>}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductManagement;
