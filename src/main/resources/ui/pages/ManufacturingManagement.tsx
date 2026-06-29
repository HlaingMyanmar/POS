
import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Plus, Loader2, Package, Trash2, CheckCircle2, X,
  ChevronDown, ChevronUp, Layers, Tag, Ruler,
  DollarSign, AlertTriangle, ArrowLeft, Hash,
  ClipboardList, CheckCheck, Ban, Edit2, BookOpen, FileText
} from 'lucide-react';
import Swal from 'sweetalert2';
import {
  ProductDTO, BrandDTO, CategoryDTO, UnitDTO, ProductSerialDTO,
  SerialStatus, ManufacturingOrderDTO, ManufacturingOrderItemDTO,
  ManufacturingStatus, ManufacturingFormulaDTO, ManufacturingFormulaItemDTO
} from '../types';
import { productService } from '../services/productapiservice';
import { brandService } from '../services/brandapiservice';
import { categoryService } from '../services/categoryapiservice';
import { unitService } from '../services/unitapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { manufacturingService } from '../services/manufacturingapiservice';
import { manufacturingFormulaService } from '../services/manufacturingformulaapiservice';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

/* ─── helpers ─── */
const statusBadge = (status?: string) => {
  if (status === ManufacturingStatus.COMPLETED)
    return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-black bg-emerald-50 text-emerald-700 border border-emerald-100"><CheckCheck size={10} />ပြီးဆုံးပြီ</span>;
  if (status === ManufacturingStatus.CANCELLED)
    return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-black bg-rose-50 text-rose-700 border border-rose-100"><Ban size={10} />ပယ်ဖျက်ပြီ</span>;
  return <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-black bg-amber-50 text-amber-700 border border-amber-100"><ClipboardList size={10} />ဆောင်ရွက်နေ</span>;
};

interface CategoryOption { id: number; displayName: string; }
const flattenCategories = (nodes: CategoryDTO[], level = 0): CategoryOption[] =>
  nodes.reduce((acc: CategoryOption[], node) => {
    acc.push({ id: node.id, displayName: `${'  '.repeat(level * 2)}${level > 0 ? '↳ ' : ''}${node.name}` });
    if (node.children?.length) acc.push(...flattenCategories(node.children, level + 1));
    return acc;
  }, []);

/* ═══════════════════════════════════════════════════════════
   Shared component form (used by both Order form & Formula form)
   ═══════════════════════════════════════════════════════════ */
interface ComponentListProps {
  items: ManufacturingOrderItemDTO[] | ManufacturingFormulaItemDTO[];
  products: ProductDTO[];
  allSerials: ProductSerialDTO[];
  onChange: (items: any[]) => void;
  showSerialPicker?: boolean;
  onOpenSerialPicker?: (idx: number) => void;
}

const ComponentList: React.FC<ComponentListProps> = ({ items, products, allSerials, onChange, showSerialPicker, onOpenSerialPicker }) => {
  const [productSearch, setProductSearch] = useState('');
  const [productOpen, setProductOpen] = useState(false);

  const getAvailableSerials = (productId: number) =>
    allSerials.filter(s => s.productId === productId && s.status === SerialStatus.AVAILABLE);

  const addComponent = (product: ProductDTO) => {
    if (items.some((i: any) => i.productId === product.id)) {
      Swal.fire({ icon: 'info', title: 'ပါဝင်ပြီး', text: 'qty ကို တိုးပါ', timer: 1200, showConfirmButton: false });
      return;
    }
    const isSerial = product.hasSerial !== false;
    const avail = isSerial ? getAvailableSerials(product.id).length : (product.stockQty ?? product.currentStock ?? 0);
    onChange([...items, {
      productId: product.id, productName: product.name, productCode: product.productCode,
      hasSerial: isSerial, qty: 1, unitCost: product.costPrice ?? 0,
      selectedSerialIds: [], selectedSerialNumbers: [], availableQty: avail,
    }]);
    setProductSearch(''); setProductOpen(false);
  };

  return (
    <div className="space-y-3">
      {/* search */}
      <div className="relative">
        <input type="text" value={productSearch}
          onFocus={() => setProductOpen(true)}
          onChange={e => { setProductSearch(e.target.value); setProductOpen(true); }}
          onBlur={() => setTimeout(() => setProductOpen(false), 150)}
          placeholder="ပစ္စည်းရှာပြီး ထည့်ရန်..."
          className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
        {productOpen && (
          <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-52 overflow-y-auto">
            {products.filter(p => {
              const s = productSearch.toLowerCase();
              return p.name.toLowerCase().includes(s) || (p.productCode || '').toLowerCase().includes(s);
            }).slice(0, 30).map(p => {
              const isSerial = p.hasSerial !== false;
              const avail = isSerial ? getAvailableSerials(p.id).length : (p.stockQty ?? p.currentStock ?? 0);
              return (
                <div key={p.id} onMouseDown={() => addComponent(p)}
                  className="px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 text-slate-700 flex items-center justify-between gap-2">
                  <div><div>{p.name}</div><div className="text-[10px] text-slate-400">{p.productCode} · {isSerial ? 'Serial' : 'Qty'}</div></div>
                  <span className={`text-[10px] font-black px-2 py-0.5 rounded-full ${avail > 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'}`}>{avail} ရှိ</span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {items.length === 0 && (
        <div className="text-center py-6 text-slate-400 text-xs font-bold"><Package size={28} className="mx-auto mb-2 opacity-30" />ပစ္စည်း မထည့်ရသေးပါ</div>
      )}

      {items.map((item: any, idx: number) => {
        const isSerial = item.hasSerial !== false;
        const selectedCount = item.selectedSerialIds?.length ?? 0;
        const needed = item.qty ?? 1;
        const serialOk = !isSerial || selectedCount >= needed;
        return (
          <div key={idx} className="border border-slate-200 rounded-xl p-3 space-y-2">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-xs font-black text-slate-800 truncate">{item.productName}</p>
                <p className="text-[10px] text-slate-400">{item.productCode} · {isSerial ? 'Serial' : 'Qty'}{item.availableQty !== undefined ? ` · ရှိ: ${item.availableQty}` : ''}</p>
              </div>
              <button onClick={() => onChange(items.filter((_: any, i: number) => i !== idx))} className="text-rose-400 hover:text-rose-600 shrink-0"><X size={14} /></button>
            </div>
            <div className="flex gap-2">
              <div className="flex-1">
                <label className="text-[9px] text-slate-400 font-black uppercase">အရေအတွက်</label>
                <input type="number" min="1" value={item.qty ?? 1}
                  onChange={e => onChange(items.map((it: any, i: number) => i === idx ? { ...it, qty: Math.max(1, Number(e.target.value)) } : it))}
                  className="w-full mt-0.5 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400" />
              </div>
              <div className="flex-1">
                <label className="text-[9px] text-slate-400 font-black uppercase">ကုန်ကျစရိတ်</label>
                <input type="number" min="0" value={Number(item.unitCost ?? 0)}
                  onChange={e => onChange(items.map((it: any, i: number) => i === idx ? { ...it, unitCost: Number(e.target.value) } : it))}
                  className="w-full mt-0.5 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400" />
              </div>
            </div>
            {showSerialPicker && isSerial && (
              <button type="button" onClick={() => onOpenSerialPicker?.(idx)}
                className={`w-full flex items-center justify-between px-3 py-2 rounded-lg border text-xs font-bold transition-all ${serialOk ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-amber-50 border-amber-200 text-amber-700'}`}>
                <span className="flex items-center gap-1.5"><Hash size={12} />Serial ရွေးရန် ({selectedCount}/{needed})</span>
                {serialOk ? <CheckCircle2 size={13} /> : <AlertTriangle size={13} />}
              </button>
            )}
            {showSerialPicker && item.selectedSerialNumbers?.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {item.selectedSerialNumbers.map((sn: string) => (
                  <span key={sn} className="text-[9px] font-black px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-100 rounded-full">{sn}</span>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

/* ═══════════════════════════════════════════════════════════
   Shared finished-product fields panel
   ═══════════════════════════════════════════════════════════ */
interface ProductFieldsProps {
  name: string; onNameChange: (v: string) => void;
  type: string; onTypeChange: (v: string) => void;
  brandId: number | undefined; onBrandChange: (v: number) => void;
  categoryId: number | undefined; onCategoryChange: (v: number) => void;
  unitId: number | undefined; onUnitChange: (v: number) => void;
  price: number; onPriceChange: (v: number) => void;
  brands: BrandDTO[]; flatCategories: CategoryOption[]; units: UnitDTO[];
  totalCost: number;
  extraFields?: React.ReactNode;
}

const ProductFields: React.FC<ProductFieldsProps> = ({
  name, onNameChange, type, onTypeChange,
  brandId, onBrandChange, categoryId, onCategoryChange,
  unitId, onUnitChange, price, onPriceChange,
  brands, flatCategories, units, totalCost, extraFields,
}) => {
  const [brandSearch, setBrandSearch] = useState('');
  const [categorySearch, setCategorySearch] = useState('');
  const [unitSearch, setUnitSearch] = useState('');
  const [brandOpen, setBrandOpen] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [unitOpen, setUnitOpen] = useState(false);

  return (
    <div className="space-y-4">
      {extraFields}
      <div className="space-y-1.5">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ထုတ်ကုန်နာမည် *</label>
        <input type="text" value={name} onChange={e => onNameChange(e.target.value)}
          className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
          placeholder="ဥပမာ: Gaming Desktop i5-12400F" />
      </div>
      <div className="space-y-1.5">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ပစ္စည်းအမျိုးအစား</label>
        <div className="flex gap-2 p-1 bg-slate-100 rounded-xl border border-slate-200">
          {[['New', 'အသစ်'], ['Second', 'အသုံးပြုပြီး'], ['Second_New', 'အသစ်နှင့်တူ']].map(([v, l]) => (
            <button key={v} type="button" onClick={() => onTypeChange(v)}
              className={`flex-1 py-2 rounded-lg text-xs font-black transition-all ${type === v ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>{l}</button>
          ))}
        </div>
      </div>
      {/* Category */}
      <div className="space-y-1.5 relative">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">အမျိုးအစား *</label>
        <div className="relative"><Layers className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
          <input type="text"
            value={categorySearch || (categoryId ? flatCategories.find(c => c.id === categoryId)?.displayName || '' : '')}
            onFocus={() => { setCategoryOpen(true); setCategorySearch(''); }}
            onChange={e => { setCategorySearch(e.target.value); setCategoryOpen(true); }}
            onBlur={() => setTimeout(() => setCategoryOpen(false), 150)}
            placeholder="အမျိုးအစား ရှာပါ..."
            className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
        </div>
        {categoryOpen && (
          <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
            {flatCategories.filter(c => c.displayName.toLowerCase().includes(categorySearch.toLowerCase())).map(c => (
              <div key={c.id} onMouseDown={() => { onCategoryChange(c.id); setCategorySearch(''); setCategoryOpen(false); }}
                className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${categoryId === c.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>{c.displayName}</div>
            ))}
          </div>
        )}
      </div>
      {/* Brand */}
      <div className="space-y-1.5 relative">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ဘရန်း *</label>
        <div className="relative"><Tag className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
          <input type="text"
            value={brandSearch || (brandId ? brands.find(b => b.id === brandId)?.name || '' : '')}
            onFocus={() => { setBrandOpen(true); setBrandSearch(''); }}
            onChange={e => { setBrandSearch(e.target.value); setBrandOpen(true); }}
            onBlur={() => setTimeout(() => setBrandOpen(false), 150)}
            placeholder="ဘရန်း ရှာပါ..."
            className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
        </div>
        {brandOpen && (
          <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
            {brands.filter(b => b.name.toLowerCase().includes(brandSearch.toLowerCase())).map(b => (
              <div key={b.id} onMouseDown={() => { onBrandChange(b.id); setBrandSearch(''); setBrandOpen(false); }}
                className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${brandId === b.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>{b.name}</div>
            ))}
          </div>
        )}
      </div>
      {/* Unit */}
      <div className="space-y-1.5 relative">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ယူနစ် *</label>
        <div className="relative"><Ruler className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
          <input type="text"
            value={unitSearch || (unitId ? units.find(u => u.id === unitId)?.unitName || '' : '')}
            onFocus={() => { setUnitOpen(true); setUnitSearch(''); }}
            onChange={e => { setUnitSearch(e.target.value); setUnitOpen(true); }}
            onBlur={() => setTimeout(() => setUnitOpen(false), 150)}
            placeholder="ယူနစ် ရှာပါ..."
            className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
        </div>
        {unitOpen && (
          <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
            {units.filter(u => u.unitName.toLowerCase().includes(unitSearch.toLowerCase())).map(u => (
              <div key={u.id} onMouseDown={() => { onUnitChange(u.id); setUnitSearch(''); setUnitOpen(false); }}
                className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${unitId === u.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>{u.unitName}</div>
            ))}
          </div>
        )}
      </div>
      <div className="space-y-1.5">
        <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ရောင်းဈေး</label>
        <div className="relative"><DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300" size={14} />
          <input type="number" min="0" value={price} onChange={e => onPriceChange(Number(e.target.value))}
            className="w-full pl-9 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-emerald-400 focus:bg-white transition-all" />
        </div>
      </div>
      <div className="bg-indigo-50 border border-indigo-100 rounded-xl px-4 py-3 flex items-center justify-between">
        <span className="text-xs font-black text-indigo-700">Component ကုန်ကျ စုစုပေါင်း</span>
        <span className="text-sm font-black text-indigo-700 tabular-nums">{totalCost.toLocaleString()} Ks</span>
      </div>
    </div>
  );
};

/* ═══════════════════════════════════════════════════════════
   Main Component
   ═══════════════════════════════════════════════════════════ */
const ManufacturingManagement: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'orders' | 'formulas'>('orders');
  const [orders, setOrders] = useState<ManufacturingOrderDTO[]>([]);
  const [formulas, setFormulas] = useState<ManufacturingFormulaDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [brands, setBrands] = useState<BrandDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [units, setUnits] = useState<UnitDTO[]>([]);
  const [allSerials, setAllSerials] = useState<ProductSerialDTO[]>([]);
  const flatCategories = useMemo(() => flattenCategories(categories), [categories]);

  /* ── view state ── */
  type ViewMode = 'list' | 'order-form' | 'formula-form';
  const [view, setView] = useState<ViewMode>('list');
  const [editingOrder, setEditingOrder] = useState<ManufacturingOrderDTO | null>(null);
  const [editingFormula, setEditingFormula] = useState<ManufacturingFormulaDTO | null>(null);
  const [saving, setSaving] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  /* ── serial picker ── */
  const [serialPickerItem, setSerialPickerItem] = useState<{ idx: number; productId: number } | null>(null);

  /* ── shared form fields ── */
  const [fName, setFName] = useState('');
  const [fType, setFType] = useState('New');
  const [fBrandId, setFBrandId] = useState<number | undefined>();
  const [fCategoryId, setFCategoryId] = useState<number | undefined>();
  const [fUnitId, setFUnitId] = useState<number | undefined>();
  const [fPrice, setFPrice] = useState(0);
  const [fProductionQty, setFProductionQty] = useState(1);
  const [fLaborCost, setFLaborCost] = useState(0);
  const [fOverheadCost, setFOverheadCost] = useState(0);
  const [fWasteCost, setFWasteCost] = useState(0);
  const [fNotes, setFNotes] = useState('');
  const [fItems, setFItems] = useState<any[]>([]);
  /* formula-only */
  const [fFormulaName, setFFormulaName] = useState('');
  const [fFormulaDesc, setFFormulaDesc] = useState('');
  /* order-only: selected formula / finished product */
  const [selectedFormulaId, setSelectedFormulaId] = useState<number | ''>('');
  const [fFinishedProductId, setFFinishedProductId] = useState<number | undefined>();

  const totalCost = useMemo(() =>
    fItems.reduce((s: number, i: any) => s + (Number(i.unitCost ?? 0) * (i.qty ?? 1)), 0), [fItems]);
  const extraCost = Number(fLaborCost || 0) + Number(fOverheadCost || 0) + Number(fWasteCost || 0);
  const totalProductionCost = totalCost + extraCost;
  const unitProductionCost = totalProductionCost / Math.max(1, Number(fProductionQty || 1));

  /* ── fetch ── */
  const fetchAll = useCallback(async () => {
    try {
      const [o, f, p, b, c, u, s] = await Promise.all([
        manufacturingService.getAll(),
        manufacturingFormulaService.getAll(),
        productService.getAll(),
        brandService.getAll(),
        categoryService.getTree(),
        unitService.getAll(),
        productSerialService.getAll(),
      ]);
      setOrders(o); setFormulas(f); setProducts(p);
      setBrands(b.filter((x: BrandDTO) => x.isActive));
      setCategories(c); setUnits(u); setAllSerials(s);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);
  useRefreshOnTabActivate(fetchAll);

  /* ── form helpers ── */
  const resetForm = () => {
    setFName(''); setFType('New'); setFBrandId(undefined); setFCategoryId(undefined);
    setFUnitId(undefined); setFPrice(0); setFProductionQty(1); setFLaborCost(0); setFOverheadCost(0); setFWasteCost(0); setFNotes('');
    setFItems([]); setFFormulaName(''); setFFormulaDesc('');
    setSelectedFormulaId(''); setFFinishedProductId(undefined); setEditingOrder(null); setEditingFormula(null);
  };

  const openOrderForm = (order?: ManufacturingOrderDTO) => {
    resetForm();
    if (order) {
      setEditingOrder(order);
      setFFinishedProductId(order.finishedProductId);
      setFName(order.finishedProductName); setFType(order.finishedProductType || 'New');
      setFBrandId(order.finishedProductBrandId); setFCategoryId(order.finishedProductCategoryId);
      setFUnitId(order.finishedProductUnitId); setFPrice(Number(order.finishedProductSellingPrice ?? 0));
      setFProductionQty(Number(order.productionQty ?? 1)); setFLaborCost(Number(order.laborCost ?? 0));
      setFOverheadCost(Number(order.overheadCost ?? 0)); setFWasteCost(Number(order.wasteCost ?? 0));
      setFNotes(order.notes || ''); setFItems((order.items || []).map(i => ({ ...i })));
    }
    setView('order-form');
  };

  const openFormulaForm = (formula?: ManufacturingFormulaDTO) => {
    resetForm();
    if (formula) {
      setEditingFormula(formula);
      setFFormulaName(formula.name); setFFormulaDesc(formula.description || '');
      setFName(formula.finishedProductName || ''); setFType(formula.finishedProductType || 'New');
      setFBrandId(formula.finishedProductBrandId); setFCategoryId(formula.finishedProductCategoryId);
      setFUnitId(formula.finishedProductUnitId); setFPrice(Number(formula.finishedProductSellingPrice ?? 0));
      setFItems((formula.items || []).map(i => ({ ...i })));
    }
    setView('formula-form');
  };

  /* Apply formula to order form */
  const applyFormula = (formulaId: number | '') => {
    setSelectedFormulaId(formulaId);
    if (!formulaId) return;
    setFFinishedProductId(undefined);
    const formula = formulas.find(f => f.id === formulaId);
    if (!formula) return;
    setFName(formula.finishedProductName || '');
    setFType(formula.finishedProductType || 'New');
    setFBrandId(formula.finishedProductBrandId);
    setFCategoryId(formula.finishedProductCategoryId);
    setFUnitId(formula.finishedProductUnitId);
    setFPrice(Number(formula.finishedProductSellingPrice ?? 0));
    setFItems(formula.items.map(i => ({
      productId: i.productId, productName: i.productName, productCode: i.productCode,
      hasSerial: i.hasSerial, qty: i.qty, unitCost: i.unitCost,
      selectedSerialIds: [], selectedSerialNumbers: [],
      availableQty: i.hasSerial !== false
        ? allSerials.filter(s => s.productId === i.productId && s.status === SerialStatus.AVAILABLE).length
        : (products.find(p => p.id === i.productId)?.stockQty ?? 0),
    })));
  };

  const applyFinishedProduct = (productId: number | '') => {
    if (productId === '') { setFFinishedProductId(undefined); return; }
    const product = products.find(p => p.id === productId);
    if (!product) return;
    setFFinishedProductId(product.id);
    setFName(product.name || '');
    setFType(product.productType || 'New');
    setFBrandId(product.brandId);
    setFCategoryId(product.categoryId);
    setFUnitId(product.unitId);
    setFPrice(Number(product.sellingPrice ?? 0));
  };

  /* ── save order ── */
  const handleSaveOrder = async () => {
    if (!fName.trim()) { Swal.fire('စစ်ဆေးမှု', 'ထုတ်ကုန်နာမည် ဖြည့်ပါ', 'warning'); return; }
    if (!fFinishedProductId && !fBrandId) { Swal.fire('စစ်ဆေးမှု', 'ဘရန်း ရွေးပါ', 'warning'); return; }
    if (!fFinishedProductId && !fCategoryId) { Swal.fire('စစ်ဆေးမှု', 'အမျိုးအစား ရွေးပါ', 'warning'); return; }
    if (!fFinishedProductId && !fUnitId) { Swal.fire('စစ်ဆေးမှု', 'ယူနစ် ရွေးပါ', 'warning'); return; }
    if (fItems.length === 0) { Swal.fire('စစ်ဆေးမှု', 'ပစ္စည်းများ ထည့်ပါ', 'warning'); return; }
    setSaving(true);
    try {
      const dto: ManufacturingOrderDTO = {
        finishedProductId: fFinishedProductId, finishedProductName: fName.trim(), finishedProductBrandId: fBrandId,
        finishedProductCategoryId: fCategoryId, finishedProductUnitId: fUnitId,
        finishedProductType: fType, finishedProductSellingPrice: fPrice, productionQty: Math.max(1, Number(fProductionQty || 1)),
        laborCost: Number(fLaborCost || 0), overheadCost: Number(fOverheadCost || 0), wasteCost: Number(fWasteCost || 0), notes: fNotes,
        items: fItems.map(i => ({ productId: i.productId, qty: i.qty, unitCost: i.unitCost, selectedSerialIds: i.selectedSerialIds ?? [] })),
      };
      editingOrder?.id ? await manufacturingService.update(editingOrder.id, dto) : await manufacturingService.create(dto);
      setView('list'); await fetchAll();
      Swal.fire({ icon: 'success', title: 'သိမ်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error');
    } finally { setSaving(false); }
  };

  /* ── save formula ── */
  const handleSaveFormula = async () => {
    if (!fFormulaName.trim()) { Swal.fire('စစ်ဆေးမှု', 'Formula နာမည် ဖြည့်ပါ', 'warning'); return; }
    if (fItems.length === 0) { Swal.fire('စစ်ဆေးမှု', 'ပစ္စည်းများ ထည့်ပါ', 'warning'); return; }
    setSaving(true);
    try {
      const dto: ManufacturingFormulaDTO = {
        name: fFormulaName.trim(), description: fFormulaDesc,
        finishedProductName: fName, finishedProductBrandId: fBrandId,
        finishedProductCategoryId: fCategoryId, finishedProductUnitId: fUnitId,
        finishedProductType: fType, finishedProductSellingPrice: fPrice,
        items: fItems.map(i => ({ productId: i.productId, qty: i.qty, unitCost: i.unitCost })),
      };
      editingFormula?.id ? await manufacturingFormulaService.update(editingFormula.id, dto) : await manufacturingFormulaService.create(dto);
      setView('list'); setActiveTab('formulas'); await fetchAll();
      Swal.fire({ icon: 'success', title: 'သိမ်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error');
    } finally { setSaving(false); }
  };

  /* ── complete / cancel / delete order ── */
  const handleComplete = async (order: ManufacturingOrderDTO) => {
    const r = await Swal.fire({ title: 'ထုတ်လုပ်ရေး ပြီးဆုံးမည်', html: `Component stock ဖြတ်ပြီး <strong>${order.finishedProductName}</strong> ကို <strong>${Number(order.productionQty ?? 1).toLocaleString()}</strong> ခု stock ထဲ ထည့်မည်။<br/>Total cost: <strong>${Number(order.totalProductionCost ?? order.totalComponentCost ?? 0).toLocaleString()} Ks</strong> · Unit cost: <strong>${Number(order.unitProductionCost ?? 0).toLocaleString()} Ks</strong>`, icon: 'question', showCancelButton: true, cancelButtonText: 'မလုပ်တော့', confirmButtonText: 'Finish', confirmButtonColor: '#16a34a' });
    if (!r.isConfirmed || !order.id) return;
    try { await manufacturingService.complete(order.id); await fetchAll(); Swal.fire({ icon: 'success', title: 'ပြီးဆုံးပြီ! ကုန်ပစ္စည်းသစ် မာစတာ ထဲ ထည့်ပြီး', timer: 2500, showConfirmButton: false }); }
    catch (e: any) { Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error'); }
  };

  const handleCancelOrder = async (order: ManufacturingOrderDTO) => {
    const r = await Swal.fire({ title: 'ပယ်ဖျက်မည်လား?', icon: 'warning', showCancelButton: true, cancelButtonText: 'မလုပ်တော့', confirmButtonText: 'ပယ်ဖျက်မည်', confirmButtonColor: '#dc2626' });
    if (!r.isConfirmed || !order.id) return;
    try { await manufacturingService.cancel(order.id); await fetchAll(); }
    catch (e: any) { Swal.fire('Error', e?.response?.data?.message || e?.message, 'error'); }
  };

  const handleDeleteOrder = async (order: ManufacturingOrderDTO) => {
    const r = await Swal.fire({ title: `"${order.orderCode}" ဖျက်မည်လား?`, icon: 'warning', showCancelButton: true, cancelButtonText: 'မလုပ်တော့', confirmButtonText: 'ဖျက်မည်', confirmButtonColor: '#dc2626' });
    if (!r.isConfirmed || !order.id) return;
    try { await manufacturingService.delete(order.id); await fetchAll(); }
    catch (e: any) { Swal.fire('Error', e?.response?.data?.message || e?.message, 'error'); }
  };

  const handleDeleteFormula = async (formula: ManufacturingFormulaDTO) => {
    const r = await Swal.fire({ title: `"${formula.name}" ဖျက်မည်လား?`, icon: 'warning', showCancelButton: true, cancelButtonText: 'မလုပ်တော့', confirmButtonText: 'ဖျက်မည်', confirmButtonColor: '#dc2626' });
    if (!r.isConfirmed || !formula.id) return;
    try { await manufacturingFormulaService.delete(formula.id); await fetchAll(); }
    catch (e: any) { Swal.fire('Error', e?.response?.data?.message || e?.message, 'error'); }
  };

  /* ── serial picker ── */
  const getAvailableSerials = (productId: number) =>
    allSerials.filter(s => s.productId === productId && s.status === SerialStatus.AVAILABLE);

  const toggleSerial = (serialId: number, serialNumber: string, idx: number) => {
    setFItems(prev => prev.map((item, i) => {
      if (i !== idx) return item;
      const ids: number[] = item.selectedSerialIds ?? [];
      const nums: string[] = item.selectedSerialNumbers ?? [];
      if (ids.includes(serialId)) return { ...item, selectedSerialIds: ids.filter((x: number) => x !== serialId), selectedSerialNumbers: nums.filter((x: string) => x !== serialNumber) };
      if (ids.length >= (item.qty ?? 1)) { Swal.fire({ icon: 'warning', title: `${item.qty} ခုသာ ရွေးနိုင်သည်`, toast: true, position: 'top-end', showConfirmButton: false, timer: 1200 }); return item; }
      return { ...item, selectedSerialIds: [...ids, serialId], selectedSerialNumbers: [...nums, serialNumber] };
    }));
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  /* ══════════════════ ORDER FORM ══════════════════ */
  if (view === 'order-form') return (
    <div className="w-full animate-in fade-in duration-200">
      <div className="flex items-center gap-3 mb-6">
        <button type="button" onClick={() => setView('list')} className="p-2 hover:bg-slate-100 rounded-lg transition-all text-slate-500"><ArrowLeft size={20} /></button>
        <div className="w-9 h-9 bg-indigo-600 rounded-lg flex items-center justify-center text-white shrink-0"><Package size={18} /></div>
        <div>
          <h2 className="text-lg font-black text-slate-800 uppercase tracking-tight">{editingOrder ? 'Order ပြင်ဆင်ရန်' : 'Order အသစ်'}</h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Manufacturing / Assembly</p>
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <Package size={13} className="text-indigo-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ထုတ်ကုန်အသစ် သတ်မှတ်ချက်</span>
            </div>
            <div className="p-5">
              <ProductFields
                name={fName} onNameChange={setFName} type={fType} onTypeChange={setFType}
                brandId={fBrandId} onBrandChange={setFBrandId} categoryId={fCategoryId} onCategoryChange={setFCategoryId}
                unitId={fUnitId} onUnitChange={setFUnitId} price={fPrice} onPriceChange={setFPrice}
                brands={brands} flatCategories={flatCategories} units={units} totalCost={totalCost}
                extraFields={
                  <div className="space-y-3 mb-4">
                    <div className="space-y-1.5">
                      <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Existing Finished Product</label>
                      <select value={fFinishedProductId ?? ''} onChange={e => applyFinishedProduct(e.target.value === '' ? '' : Number(e.target.value))}
                        className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all">
                        <option value="">-- Product အသစ်အဖြစ် ထည့်မည် --</option>
                        {products.filter(p => p.hasSerial === false).map(p => <option key={p.id} value={p.id}>{p.name} · Stock: {Number(p.stockQty ?? p.currentStock ?? 0).toLocaleString()}</option>)}
                      </select>
                    </div>
                    <div className="space-y-1.5">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Formula ရွေးမည် (ရွေးချင်မှ)</label>
                    <select value={selectedFormulaId} onChange={e => applyFormula(e.target.value === '' ? '' : Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all">
                      <option value="">── Formula မရွေးပါ ──</option>
                      {formulas.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
                    </select>
                    {selectedFormulaId && <p className="text-[10px] text-indigo-600 font-bold">✓ Formula အရ data အားလုံး ဖြည့်ပြီး — လိုသလို ပြင်နိုင်သည်</p>}
                    </div>
                    <div className="grid grid-cols-2 gap-2 pt-2">
                      <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ထုတ်လုပ်အရေအတွက် *</label><input type="number" min="1" value={fProductionQty} onChange={e => setFProductionQty(Math.max(1, Number(e.target.value)))} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" /></div>
                      <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">တစ်ခုချင်း ကုန်ကျ</label><div className="px-4 py-3 bg-emerald-50 border border-emerald-100 rounded-xl text-sm font-black text-emerald-700 tabular-nums">{unitProductionCost.toLocaleString(undefined, { maximumFractionDigits: 2 })} Ks</div></div>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">လုပ်အားခ</label><input type="number" min="0" value={fLaborCost} onChange={e => setFLaborCost(Math.max(0, Number(e.target.value)))} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400" /></div>
                      <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">အထွေထွေ</label><input type="number" min="0" value={fOverheadCost} onChange={e => setFOverheadCost(Math.max(0, Number(e.target.value)))} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400" /></div>
                      <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ဆုံးရှုံး</label><input type="number" min="0" value={fWasteCost} onChange={e => setFWasteCost(Math.max(0, Number(e.target.value)))} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400" /></div>
                    </div>
                    <div className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 flex items-center justify-between"><span className="text-xs font-black text-slate-600">စုစုပေါင်း ထုတ်လုပ်ကုန်ကျ</span><span className="text-sm font-black text-slate-800 tabular-nums">{totalProductionCost.toLocaleString()} Ks</span></div>
                  </div>
                }
              />
              <div className="mt-4 space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">မှတ်ချက်</label>
                <textarea value={fNotes} onChange={e => setFNotes(e.target.value)} rows={2}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white resize-none" />
              </div>
            </div>
          </div>
        </div>
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <ClipboardList size={13} className="text-slate-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Component ပစ္စည်းများ</span>
            </div>
            <div className="p-4">
              <ComponentList items={fItems} products={products} allSerials={allSerials} onChange={setFItems}
                showSerialPicker onOpenSerialPicker={idx => {
                  const item = fItems[idx];
                  setSerialPickerItem({ idx, productId: item.productId });
                }} />
            </div>
          </div>
          <button onClick={handleSaveOrder} disabled={saving}
            className="w-full bg-indigo-600 text-white py-3 rounded-xl font-black text-sm uppercase flex items-center justify-center gap-2 hover:bg-indigo-700 disabled:opacity-60">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {editingOrder ? 'မွမ်းမံသိမ်းမည်' : 'Draft သိမ်းမည်'}
          </button>
        </div>
      </div>

      {/* Serial Picker */}
      {serialPickerItem && (() => {
        const { idx, productId } = serialPickerItem;
        const item = fItems[idx];
        const serials = getAvailableSerials(productId);
        const product = products.find(p => p.id === productId);
        return (
          <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
              <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-black text-slate-800">{product?.name} · Serial ရွေးမည်</h3>
                  <p className="text-[10px] text-slate-400 font-bold">{item.qty} ခု ရွေးရမည် · ရွေးပြီး: {item.selectedSerialIds?.length ?? 0}</p>
                </div>
                <button onClick={() => setSerialPickerItem(null)} className="text-slate-400 hover:text-slate-700"><X size={18} /></button>
              </div>
              <div className="p-4 max-h-72 overflow-y-auto space-y-2">
                {serials.length === 0 && <p className="text-center text-xs text-slate-400 font-bold py-4">AVAILABLE Serial မရှိပါ</p>}
                {serials.map(s => {
                  const selected = (item.selectedSerialIds ?? []).includes(s.id);
                  return (
                    <div key={s.id} onClick={() => toggleSerial(s.id, s.serialNumber, idx)}
                      className={`flex items-center gap-3 px-4 py-2.5 rounded-xl border cursor-pointer transition-all ${selected ? 'bg-indigo-50 border-indigo-300 text-indigo-700' : 'bg-slate-50 border-slate-200 text-slate-700 hover:border-indigo-200'}`}>
                      <div className={`w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 ${selected ? 'bg-indigo-600 border-indigo-600' : 'border-slate-300'}`}>
                        {selected && <CheckCheck size={10} className="text-white" />}
                      </div>
                      <span className="text-xs font-black">{s.serialNumber}</span>
                    </div>
                  );
                })}
              </div>
              <div className="px-5 py-3 border-t border-slate-100">
                <button onClick={() => setSerialPickerItem(null)} className="w-full bg-indigo-600 text-white py-2 rounded-xl text-sm font-black hover:bg-indigo-700">ပြီးပါပြီ</button>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );

  /* ══════════════════ FORMULA FORM ══════════════════ */
  if (view === 'formula-form') return (
    <div className="w-full animate-in fade-in duration-200">
      <div className="flex items-center gap-3 mb-6">
        <button type="button" onClick={() => { setView('list'); setActiveTab('formulas'); }} className="p-2 hover:bg-slate-100 rounded-lg transition-all text-slate-500"><ArrowLeft size={20} /></button>
        <div className="w-9 h-9 bg-violet-600 rounded-lg flex items-center justify-center text-white shrink-0"><BookOpen size={18} /></div>
        <div>
          <h2 className="text-lg font-black text-slate-800 uppercase tracking-tight">{editingFormula ? 'Formula ပြင်ဆင်ရန်' : 'Formula အသစ်'}</h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">BOM Template</p>
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <FileText size={13} className="text-violet-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Formula သတ်မှတ်ချက်</span>
            </div>
            <div className="p-5 space-y-4">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Formula နာမည် *</label>
                <input type="text" value={fFormulaName} onChange={e => setFFormulaName(e.target.value)}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-violet-500 focus:bg-white transition-all"
                  placeholder="ဥပမာ: Gaming Desktop Standard Build" />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ဖော်ပြချက်</label>
                <textarea value={fFormulaDesc} onChange={e => setFFormulaDesc(e.target.value)} rows={2}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-violet-500 focus:bg-white resize-none" />
              </div>
              <hr className="border-slate-100" />
              <ProductFields
                name={fName} onNameChange={setFName} type={fType} onTypeChange={setFType}
                brandId={fBrandId} onBrandChange={setFBrandId} categoryId={fCategoryId} onCategoryChange={setFCategoryId}
                unitId={fUnitId} onUnitChange={setFUnitId} price={fPrice} onPriceChange={setFPrice}
                brands={brands} flatCategories={flatCategories} units={units} totalCost={totalCost}
              />
            </div>
          </div>
        </div>
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <ClipboardList size={13} className="text-slate-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Component ပစ္စည်းများ</span>
            </div>
            <div className="p-4">
              <ComponentList items={fItems} products={products} allSerials={allSerials} onChange={setFItems} />
            </div>
          </div>
          <button onClick={handleSaveFormula} disabled={saving}
            className="w-full bg-violet-600 text-white py-3 rounded-xl font-black text-sm uppercase flex items-center justify-center gap-2 hover:bg-violet-700 disabled:opacity-60">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {editingFormula ? 'မွမ်းမံသိမ်းမည်' : 'Formula သိမ်းမည်'}
          </button>
        </div>
      </div>
    </div>
  );

  /* ══════════════════ LIST VIEW ══════════════════ */
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-slate-800">ထုတ်လုပ်ရေး</h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Manufacturing / Assembly</p>
        </div>
        <button onClick={() => activeTab === 'orders' ? openOrderForm() : openFormulaForm()}
          className={`px-4 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 text-white ${activeTab === 'orders' ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-violet-600 hover:bg-violet-700'}`}>
          <Plus size={14} /> {activeTab === 'orders' ? 'Order အသစ်' : 'Formula အသစ်'}
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-slate-100 rounded-xl border border-slate-200 w-fit">
        <button onClick={() => setActiveTab('orders')}
          className={`px-4 py-2 rounded-lg text-xs font-black uppercase tracking-wide transition-all ${activeTab === 'orders' ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
          <span className="flex items-center gap-1.5"><ClipboardList size={13} />Orders ({orders.length})</span>
        </button>
        <button onClick={() => setActiveTab('formulas')}
          className={`px-4 py-2 rounded-lg text-xs font-black uppercase tracking-wide transition-all ${activeTab === 'formulas' ? 'bg-white text-violet-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
          <span className="flex items-center gap-1.5"><BookOpen size={13} />Formulas ({formulas.length})</span>
        </button>
      </div>

      {/* ── ORDERS TAB ── */}
      {activeTab === 'orders' && (
        <div className="space-y-3">
          {orders.length === 0 && (
            <div className="bg-white border border-slate-200 rounded-2xl text-center py-16 text-slate-400">
              <Package size={40} className="mx-auto mb-3 opacity-30" />
              <p className="text-sm font-bold">Order မရှိသေးပါ</p>
              <button onClick={() => openOrderForm()} className="mt-4 bg-indigo-600 text-white px-4 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 mx-auto hover:bg-indigo-700">
                <Plus size={14} /> Order အသစ်
              </button>
            </div>
          )}
          {orders.map(order => (
            <div key={order.id} className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
              <div className="px-5 py-4 flex items-center justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-black text-slate-500">{order.orderCode}</span>
                    {statusBadge(order.status)}
                  </div>
                  <p className="text-sm font-black text-slate-800 mt-0.5 truncate">{order.finishedProductName}</p>
                  <p className="text-[10px] text-slate-400 font-semibold">
                    {order.finishedProductBrandName} · {order.finishedProductCategoryName} · {order.finishedProductUnitName}
                    {` · Qty: ${Number(order.productionQty ?? 1).toLocaleString()}`}
                    {order.totalProductionCost ? ` · Total: ${Number(order.totalProductionCost).toLocaleString()} Ks` : ''}
                    {order.unitProductionCost ? ` · Unit: ${Number(order.unitProductionCost).toLocaleString()} Ks` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {order.status === ManufacturingStatus.DRAFT && <>
                    <button onClick={() => openOrderForm(order)} className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-all"><Edit2 size={15} /></button>
                    <button onClick={() => handleComplete(order)} className="px-3 py-1.5 bg-emerald-600 text-white rounded-lg text-xs font-black uppercase flex items-center gap-1 hover:bg-emerald-700"><CheckCheck size={13} />Finish</button>
                    <button onClick={() => handleCancelOrder(order)} className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all"><Ban size={15} /></button>
                  </>}
                  {order.status !== ManufacturingStatus.COMPLETED && (
                    <button onClick={() => handleDeleteOrder(order)} className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all"><Trash2 size={15} /></button>
                  )}
                  <button onClick={() => setExpandedId(expandedId === order.id ? null : (order.id ?? null))} className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-all">
                    {expandedId === order.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                </div>
              </div>
              {expandedId === order.id && (
                <div className="border-t border-slate-100 px-5 py-4">
                  <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-3">Component ပစ္စည်းများ</p>
                  <div className="space-y-2">
                    {(order.items || []).map((item, i) => (
                      <div key={i} className="flex items-center justify-between bg-slate-50 rounded-xl px-4 py-2.5 gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-black text-slate-800 truncate">{item.productName}</p>
                          <p className="text-[10px] text-slate-400">{item.productCode} · {item.hasSerial ? 'Serial' : 'Qty'}</p>
                          {item.selectedSerialNumbers && item.selectedSerialNumbers.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                              {item.selectedSerialNumbers.map((sn: string) => (
                                <span key={sn} className="text-[9px] font-black px-2 py-0.5 bg-indigo-50 text-indigo-700 border border-indigo-100 rounded-full">{sn}</span>
                              ))}
                            </div>
                          )}
                        </div>
                        <div className="text-right shrink-0">
                          <p className="text-xs font-black text-slate-700">{item.qty} ခု</p>
                          <p className="text-[10px] text-slate-400">{Number(item.unitCost ?? 0).toLocaleString()} Ks/ခု</p>
                        </div>
                      </div>
                    ))}
                  </div>
                  {order.notes && <p className="mt-3 text-xs text-slate-500 border-t border-slate-100 pt-3">{order.notes}</p>}
                  {order.status === ManufacturingStatus.COMPLETED && order.finishedProductId && (
                    <div className="mt-3 flex items-center gap-2 bg-emerald-50 border border-emerald-100 rounded-xl px-4 py-2.5">
                      <CheckCircle2 size={14} className="text-emerald-600 shrink-0" />
                      <p className="text-xs font-black text-emerald-700">မာစတာ ထဲ {Number(order.productionQty ?? 1).toLocaleString()} ခု ထည့်ပြီး (ID: {order.finishedProductId}) · {order.completedAt?.slice(0, 10)}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* ── FORMULAS TAB ── */}
      {activeTab === 'formulas' && (
        <div className="space-y-3">
          {formulas.length === 0 && (
            <div className="bg-white border border-slate-200 rounded-2xl text-center py-16 text-slate-400">
              <BookOpen size={40} className="mx-auto mb-3 opacity-30" />
              <p className="text-sm font-bold">Formula မရှိသေးပါ</p>
              <button onClick={() => openFormulaForm()} className="mt-4 bg-violet-600 text-white px-4 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 mx-auto hover:bg-violet-700">
                <Plus size={14} /> Formula အသစ်
              </button>
            </div>
          )}
          {formulas.map(formula => (
            <div key={formula.id} className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
              <div className="px-5 py-4 flex items-center justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-black bg-violet-50 text-violet-700 border border-violet-100"><BookOpen size={10} />Formula</span>
                  </div>
                  <p className="text-sm font-black text-slate-800 mt-0.5">{formula.name}</p>
                  <p className="text-[10px] text-slate-400 font-semibold">
                    ထုတ်ကုန်: {formula.finishedProductName || '—'} · {formula.items.length} ပစ္စည်း
                    {formula.finishedProductBrandName ? ` · ${formula.finishedProductBrandName}` : ''}
                  </p>
                  {formula.description && <p className="text-[10px] text-slate-400 mt-0.5">{formula.description}</p>}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button onClick={() => { openOrderForm(); setTimeout(() => applyFormula(formula.id!), 0); setView('order-form'); }}
                    className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-xs font-black uppercase flex items-center gap-1 hover:bg-indigo-700">
                    <Plus size={13} />Order ဖန်တီး
                  </button>
                  <button onClick={() => openFormulaForm(formula)} className="p-2 text-slate-400 hover:text-violet-600 hover:bg-violet-50 rounded-lg transition-all"><Edit2 size={15} /></button>
                  <button onClick={() => handleDeleteFormula(formula)} className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all"><Trash2 size={15} /></button>
                </div>
              </div>
              <div className="border-t border-slate-100 px-5 py-3">
                <div className="flex flex-wrap gap-2">
                  {formula.items.map((item, i) => (
                    <span key={i} className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-1 bg-slate-50 border border-slate-200 rounded-lg text-slate-600">
                      <Package size={10} />{item.productName} × {item.qty}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default ManufacturingManagement;
