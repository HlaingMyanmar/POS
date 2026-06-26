
import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Plus, Loader2, Package, Trash2, CheckCircle2, X,
  ChevronDown, ChevronUp, Layers, Tag, Ruler,
  DollarSign, AlertTriangle, ArrowLeft, Hash,
  ClipboardList, CheckCheck, Ban, Edit2
} from 'lucide-react';
import Swal from 'sweetalert2';
import {
  ProductDTO, BrandDTO, CategoryDTO, UnitDTO, ProductSerialDTO,
  SerialStatus, ManufacturingOrderDTO, ManufacturingOrderItemDTO,
  ManufacturingStatus, ProductType, AppRoute
} from '../types';
import { productService } from '../services/productapiservice';
import { brandService } from '../services/brandapiservice';
import { categoryService } from '../services/categoryapiservice';
import { unitService } from '../services/unitapiservice';
import { productSerialService } from '../services/productserialapiservice';
import { manufacturingService } from '../services/manufacturingapiservice';

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
    const pad = '  '.repeat(level * 2);
    const pre = level > 0 ? '↳ ' : '';
    acc.push({ id: node.id, displayName: `${pad}${pre}${node.name}` });
    if (node.children?.length) acc.push(...flattenCategories(node.children, level + 1));
    return acc;
  }, []);

const ManufacturingManagement: React.FC = () => {
  const [orders, setOrders] = useState<ManufacturingOrderDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [brands, setBrands] = useState<BrandDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [units, setUnits] = useState<UnitDTO[]>([]);
  const [allSerials, setAllSerials] = useState<ProductSerialDTO[]>([]);

  const [showForm, setShowForm] = useState(false);
  const [editingOrder, setEditingOrder] = useState<ManufacturingOrderDTO | null>(null);
  const [saving, setSaving] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Serial picker modal
  const [serialPickerItem, setSerialPickerItem] = useState<{ itemIdx: number; product: ProductDTO } | null>(null);

  // Form state
  const [formName, setFormName] = useState('');
  const [formBrandId, setFormBrandId] = useState<number | undefined>();
  const [formCategoryId, setFormCategoryId] = useState<number | undefined>();
  const [formUnitId, setFormUnitId] = useState<number | undefined>();
  const [formType, setFormType] = useState<string>('New');
  const [formPrice, setFormPrice] = useState<number>(0);
  const [formNotes, setFormNotes] = useState('');
  const [formItems, setFormItems] = useState<ManufacturingOrderItemDTO[]>([]);

  const [brandSearch, setBrandSearch] = useState('');
  const [categorySearch, setCategorySearch] = useState('');
  const [unitSearch, setUnitSearch] = useState('');
  const [brandOpen, setBrandOpen] = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [unitOpen, setUnitOpen] = useState(false);
  const [productSearch, setProductSearch] = useState('');
  const [productOpen, setProductOpen] = useState(false);

  const flatCategories = useMemo(() => flattenCategories(categories), [categories]);

  const fetchAll = useCallback(async () => {
    try {
      const [o, p, b, c, u, s] = await Promise.all([
        manufacturingService.getAll(),
        productService.getAll(),
        brandService.getAll(),
        categoryService.getTree(),
        unitService.getAll(),
        productSerialService.getAll(),
      ]);
      setOrders(o);
      setProducts(p);
      setBrands(b.filter(x => x.isActive));
      setCategories(c);
      setUnits(u);
      setAllSerials(s);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const openNew = () => {
    setEditingOrder(null);
    setFormName(''); setFormBrandId(undefined); setFormCategoryId(undefined);
    setFormUnitId(undefined); setFormType('New'); setFormPrice(0);
    setFormNotes(''); setFormItems([]);
    setShowForm(true);
  };

  const openEdit = (order: ManufacturingOrderDTO) => {
    setEditingOrder(order);
    setFormName(order.finishedProductName);
    setFormBrandId(order.finishedProductBrandId);
    setFormCategoryId(order.finishedProductCategoryId);
    setFormUnitId(order.finishedProductUnitId);
    setFormType(order.finishedProductType || 'New');
    setFormPrice(Number(order.finishedProductSellingPrice ?? 0));
    setFormNotes(order.notes || '');
    setFormItems((order.items || []).map(i => ({ ...i })));
    setShowForm(true);
  };

  const getAvailableSerials = (productId: number) =>
    allSerials.filter(s => s.productId === productId && s.status === SerialStatus.AVAILABLE);

  const addComponent = (product: ProductDTO) => {
    const existing = formItems.find(i => i.productId === product.id);
    if (existing) {
      Swal.fire({ icon: 'info', title: 'ပါဝင်ပြီးဖြစ်သည်', text: 'ဤပစ္စည်းကို ထပ်ထည့်ရန် qty ကို တိုးပါ', timer: 1500, showConfirmButton: false });
      return;
    }
    const isSerial = product.hasSerial !== false;
    const availQty = isSerial
      ? getAvailableSerials(product.id).length
      : (product.stockQty ?? product.currentStock ?? 0);

    setFormItems(prev => [...prev, {
      productId: product.id,
      productName: product.name,
      productCode: product.productCode,
      hasSerial: isSerial,
      qty: 1,
      unitCost: product.costPrice ?? 0,
      selectedSerialIds: [],
      selectedSerialNumbers: [],
      availableQty: availQty,
    }]);
    setProductSearch(''); setProductOpen(false);
  };

  const removeComponent = (idx: number) =>
    setFormItems(prev => prev.filter((_, i) => i !== idx));

  const updateItemQty = (idx: number, qty: number) =>
    setFormItems(prev => prev.map((item, i) => i === idx ? { ...item, qty: Math.max(1, qty) } : item));

  const updateItemCost = (idx: number, cost: number) =>
    setFormItems(prev => prev.map((item, i) => i === idx ? { ...item, unitCost: cost } : item));

  const totalComponentCost = useMemo(() =>
    formItems.reduce((s, i) => s + (Number(i.unitCost ?? 0) * (i.qty ?? 1)), 0),
    [formItems]);

  const handleSave = async () => {
    if (!formName.trim()) { Swal.fire('စစ်ဆေးမှု', 'ထုတ်ကုန်နာမည် ဖြည့်ပါ', 'warning'); return; }
    if (!formBrandId) { Swal.fire('စစ်ဆေးမှု', 'ဘရန်း ရွေးပါ', 'warning'); return; }
    if (!formCategoryId) { Swal.fire('စစ်ဆေးမှု', 'အမျိုးအစား ရွေးပါ', 'warning'); return; }
    if (!formUnitId) { Swal.fire('စစ်ဆေးမှု', 'ယူနစ် ရွေးပါ', 'warning'); return; }
    if (formItems.length === 0) { Swal.fire('စစ်ဆေးမှု', 'ပစ္စည်းများ ထည့်ပါ', 'warning'); return; }

    setSaving(true);
    try {
      const dto: ManufacturingOrderDTO = {
        finishedProductName: formName.trim(),
        finishedProductBrandId: formBrandId,
        finishedProductCategoryId: formCategoryId,
        finishedProductUnitId: formUnitId,
        finishedProductType: formType,
        finishedProductSellingPrice: formPrice,
        notes: formNotes,
        items: formItems.map(i => ({
          productId: i.productId,
          qty: i.qty,
          unitCost: i.unitCost,
          selectedSerialIds: i.selectedSerialIds ?? [],
        })),
      };
      if (editingOrder?.id) {
        await manufacturingService.update(editingOrder.id, dto);
      } else {
        await manufacturingService.create(dto);
      }
      setShowForm(false);
      await fetchAll();
      Swal.fire({ icon: 'success', title: 'သိမ်းဆည်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleComplete = async (order: ManufacturingOrderDTO) => {
    const result = await Swal.fire({
      title: 'ထုတ်လုပ်ရေး ပြီးဆုံးမည်',
      html: `<p>Component တွေ stock မှ ဖြတ်တောက်ပြီး <strong>${order.finishedProductName}</strong> ကို ကုန်ပစ္စည်းမာစတာ ထဲ ထည့်သွင်းမည်။</p>`,
      icon: 'question',
      showCancelButton: true,
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonText: 'Finish လုပ်မည်',
      confirmButtonColor: '#16a34a',
    });
    if (!result.isConfirmed || !order.id) return;
    try {
      await manufacturingService.complete(order.id);
      await fetchAll();
      Swal.fire({ icon: 'success', title: 'ပြီးဆုံးပြီ! ကုန်ပစ္စည်းသစ် မာစတာ ထဲ ထည့်ပြီး', timer: 2500, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error');
    }
  };

  const handleCancel = async (order: ManufacturingOrderDTO) => {
    const result = await Swal.fire({
      title: 'ပယ်ဖျက်မည်လား?',
      icon: 'warning',
      showCancelButton: true,
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonText: 'ပယ်ဖျက်မည်',
      confirmButtonColor: '#dc2626',
    });
    if (!result.isConfirmed || !order.id) return;
    try {
      await manufacturingService.cancel(order.id);
      await fetchAll();
      Swal.fire({ icon: 'success', title: 'ပယ်ဖျက်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မအောင်မြင်ပါ', 'error');
    }
  };

  const handleDelete = async (order: ManufacturingOrderDTO) => {
    const result = await Swal.fire({
      title: `"${order.orderCode}" ဖျက်မည်လား?`,
      icon: 'warning',
      showCancelButton: true,
      cancelButtonText: 'မလုပ်တော့',
      confirmButtonText: 'ဖျက်မည်',
      confirmButtonColor: '#dc2626',
    });
    if (!result.isConfirmed || !order.id) return;
    try {
      await manufacturingService.delete(order.id);
      await fetchAll();
      Swal.fire({ icon: 'success', title: 'ဖျက်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (e: any) {
      Swal.fire('Error', e?.response?.data?.message || e?.message || 'မဖျက်နိုင်ပါ', 'error');
    }
  };

  // ────────── Serial Picker ──────────
  const openSerialPicker = (idx: number) => {
    const item = formItems[idx];
    const product = products.find(p => p.id === item.productId);
    if (!product) return;
    setSerialPickerItem({ itemIdx: idx, product });
  };

  const toggleSerial = (serialId: number, serialNumber: string, itemIdx: number) => {
    setFormItems(prev => prev.map((item, i) => {
      if (i !== itemIdx) return item;
      const ids = item.selectedSerialIds ?? [];
      const nums = item.selectedSerialNumbers ?? [];
      if (ids.includes(serialId)) {
        return { ...item, selectedSerialIds: ids.filter(x => x !== serialId), selectedSerialNumbers: nums.filter(x => x !== serialNumber) };
      }
      const needed = item.qty ?? 1;
      if (ids.length >= needed) {
        Swal.fire({ icon: 'warning', title: `Serial ${needed} ခုသာ ရွေးနိုင်သည်`, toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
        return item;
      }
      return { ...item, selectedSerialIds: [...ids, serialId], selectedSerialNumbers: [...nums, serialNumber] };
    }));
  };

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  // ────────── FORM VIEW ──────────
  if (showForm) return (
    <div className="w-full max-w-none animate-in fade-in duration-200">
      <div className="flex items-center gap-3 mb-6">
        <button type="button" onClick={() => setShowForm(false)}
          className="p-2 hover:bg-slate-100 rounded-lg transition-all text-slate-500 hover:text-slate-800">
          <ArrowLeft size={20} />
        </button>
        <div className="w-9 h-9 bg-indigo-600 rounded-lg flex items-center justify-center text-white shrink-0">
          <Package size={18} />
        </div>
        <div>
          <h2 className="text-lg font-black text-slate-800 uppercase tracking-tight">
            {editingOrder ? 'Order ပြင်ဆင်ရန်' : 'ထုတ်လုပ်ရေး Order အသစ်'}
          </h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Manufacturing / Assembly</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">

        {/* LEFT — Finished Product details */}
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <Package size={13} className="text-indigo-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ထုတ်ကုန်အသစ် သတ်မှတ်ချက်</span>
            </div>
            <div className="p-5 space-y-4">

              {/* Name */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ထုတ်ကုန်နာမည် *</label>
                <input type="text" value={formName} onChange={e => setFormName(e.target.value)}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all"
                  placeholder="ဥပမာ: Gaming Desktop i5-12400F" />
              </div>

              {/* Type */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ပစ္စည်းအမျိုးအစား</label>
                <div className="flex gap-2 p-1 bg-slate-100 rounded-xl border border-slate-200">
                  {[['New', 'အသစ်'], ['Second', 'အသုံးပြုပြီး'], ['Second_New', 'အသစ်နှင့်တူ']] .map(([v, l]) => (
                    <button key={v} type="button" onClick={() => setFormType(v)}
                      className={`flex-1 py-2 rounded-lg text-xs font-black transition-all ${formType === v ? 'bg-white text-indigo-600 shadow border border-slate-200' : 'text-slate-400 hover:text-slate-600'}`}>
                      {l}
                    </button>
                  ))}
                </div>
              </div>

              {/* Category */}
              <div className="space-y-1.5 relative">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">အမျိုးအစား *</label>
                <div className="relative">
                  <Layers className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
                  <input type="text"
                    value={categorySearch || (formCategoryId ? flatCategories.find(c => c.id === formCategoryId)?.displayName || '' : '')}
                    onFocus={() => { setCategoryOpen(true); setCategorySearch(''); }}
                    onChange={e => { setCategorySearch(e.target.value); setCategoryOpen(true); }}
                    onBlur={() => setTimeout(() => setCategoryOpen(false), 150)}
                    placeholder="အမျိုးအစား ရှာပါ..."
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
                </div>
                {categoryOpen && (
                  <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
                    {flatCategories.filter(c => c.displayName.toLowerCase().includes(categorySearch.toLowerCase())).map(c => (
                      <div key={c.id} onMouseDown={() => { setFormCategoryId(c.id); setCategorySearch(''); setCategoryOpen(false); }}
                        className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${formCategoryId === c.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                        {c.displayName}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Brand */}
              <div className="space-y-1.5 relative">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ဘရန်း *</label>
                <div className="relative">
                  <Tag className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
                  <input type="text"
                    value={brandSearch || (formBrandId ? brands.find(b => b.id === formBrandId)?.name || '' : '')}
                    onFocus={() => { setBrandOpen(true); setBrandSearch(''); }}
                    onChange={e => { setBrandSearch(e.target.value); setBrandOpen(true); }}
                    onBlur={() => setTimeout(() => setBrandOpen(false), 150)}
                    placeholder="ဘရန်း ရှာပါ..."
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
                </div>
                {brandOpen && (
                  <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
                    {brands.filter(b => b.name.toLowerCase().includes(brandSearch.toLowerCase())).map(b => (
                      <div key={b.id} onMouseDown={() => { setFormBrandId(b.id); setBrandSearch(''); setBrandOpen(false); }}
                        className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${formBrandId === b.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                        {b.name}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Unit */}
              <div className="space-y-1.5 relative">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ယူနစ် *</label>
                <div className="relative">
                  <Ruler className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 z-10" size={14} />
                  <input type="text"
                    value={unitSearch || (formUnitId ? units.find(u => u.id === formUnitId)?.unitName || '' : '')}
                    onFocus={() => { setUnitOpen(true); setUnitSearch(''); }}
                    onChange={e => { setUnitSearch(e.target.value); setUnitOpen(true); }}
                    onBlur={() => setTimeout(() => setUnitOpen(false), 150)}
                    placeholder="ယူနစ် ရှာပါ..."
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
                </div>
                {unitOpen && (
                  <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-40 overflow-y-auto">
                    {units.filter(u => u.unitName.toLowerCase().includes(unitSearch.toLowerCase())).map(u => (
                      <div key={u.id} onMouseDown={() => { setFormUnitId(u.id); setUnitSearch(''); setUnitOpen(false); }}
                        className={`px-4 py-2 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 ${formUnitId === u.id ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700'}`}>
                        {u.unitName}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Selling price */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">ရောင်းဈေး</label>
                <div className="relative">
                  <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-300" size={14} />
                  <input type="number" min="0" value={formPrice} onChange={e => setFormPrice(Number(e.target.value))}
                    className="w-full pl-9 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-emerald-400 focus:bg-white transition-all" />
                </div>
              </div>

              {/* Notes */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest">မှတ်ချက်</label>
                <textarea value={formNotes} onChange={e => setFormNotes(e.target.value)} rows={2}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all resize-none"
                  placeholder="မှတ်ချက်..." />
              </div>
            </div>
          </div>

          {/* Cost summary */}
          <div className="bg-indigo-50 border border-indigo-100 rounded-xl px-4 py-3 flex items-center justify-between">
            <span className="text-xs font-black text-indigo-700">Component ကုန်ကျစရိတ် စုစုပေါင်း</span>
            <span className="text-sm font-black text-indigo-700 tabular-nums">{totalComponentCost.toLocaleString()} Ks</span>
          </div>
        </div>

        {/* RIGHT — Component list */}
        <div className="space-y-4">
          <div className="bg-white border border-slate-200 rounded-2xl">
            <div className="px-5 py-3 bg-slate-50 border-b border-slate-100 flex items-center gap-2 rounded-t-2xl">
              <ClipboardList size={13} className="text-slate-500" />
              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest">Component ပစ္စည်းများ</span>
            </div>
            <div className="p-4 space-y-3">

              {/* Product search to add */}
              <div className="relative">
                <input type="text" value={productSearch}
                  onFocus={() => setProductOpen(true)}
                  onChange={e => { setProductSearch(e.target.value); setProductOpen(true); }}
                  onBlur={() => setTimeout(() => setProductOpen(false), 150)}
                  placeholder="ပစ္စည်းရှာပြီး ထည့်ရန်..."
                  className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold outline-none focus:border-indigo-500 focus:bg-white transition-all" />
                {productOpen && (
                  <div className="absolute z-50 top-full mt-1 left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl max-h-52 overflow-y-auto">
                    {products
                      .filter(p => {
                        const s = productSearch.toLowerCase();
                        return (p.name.toLowerCase().includes(s) || (p.productCode || '').toLowerCase().includes(s));
                      })
                      .slice(0, 30)
                      .map(p => {
                        const isSerial = p.hasSerial !== false;
                        const avail = isSerial
                          ? getAvailableSerials(p.id).length
                          : (p.stockQty ?? p.currentStock ?? 0);
                        return (
                          <div key={p.id} onMouseDown={() => addComponent(p)}
                            className="px-4 py-2.5 text-xs font-bold cursor-pointer hover:bg-indigo-50 hover:text-indigo-700 text-slate-700 flex items-center justify-between gap-2">
                            <div>
                              <div>{p.name}</div>
                              <div className="text-[10px] text-slate-400 font-semibold">{p.productCode} · {isSerial ? 'Serial' : 'Qty'}</div>
                            </div>
                            <span className={`text-[10px] font-black px-2 py-0.5 rounded-full ${avail > 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'}`}>
                              {avail} ရှိ
                            </span>
                          </div>
                        );
                      })}
                  </div>
                )}
              </div>

              {/* Component rows */}
              {formItems.length === 0 && (
                <div className="text-center py-8 text-slate-400 text-xs font-bold">
                  <Package size={32} className="mx-auto mb-2 opacity-30" />
                  ပစ္စည်းများ ထည့်ပါ
                </div>
              )}

              {formItems.map((item, idx) => {
                const isSerial = item.hasSerial !== false;
                const selectedCount = item.selectedSerialIds?.length ?? 0;
                const needed = item.qty ?? 1;
                const serialOk = !isSerial || selectedCount >= needed;

                return (
                  <div key={idx} className="border border-slate-200 rounded-xl p-3 space-y-2">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-xs font-black text-slate-800 truncate">{item.productName}</p>
                        <p className="text-[10px] text-slate-400 font-semibold">{item.productCode} · {isSerial ? 'Serial' : 'Qty'} · ရှိ: {item.availableQty}</p>
                      </div>
                      <button onClick={() => removeComponent(idx)} className="text-rose-400 hover:text-rose-600 shrink-0">
                        <X size={14} />
                      </button>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="flex-1 space-y-1">
                        <label className="text-[9px] text-slate-400 font-black uppercase">အရေအတွက်</label>
                        <input type="number" min="1" value={item.qty ?? 1}
                          onChange={e => updateItemQty(idx, Number(e.target.value))}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400 focus:bg-white" />
                      </div>
                      <div className="flex-1 space-y-1">
                        <label className="text-[9px] text-slate-400 font-black uppercase">ကုန်ကျစရိတ်</label>
                        <input type="number" min="0" value={Number(item.unitCost ?? 0)}
                          onChange={e => updateItemCost(idx, Number(e.target.value))}
                          className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm font-bold outline-none focus:border-indigo-400 focus:bg-white" />
                      </div>
                    </div>
                    {isSerial && (
                      <div>
                        <button type="button" onClick={() => openSerialPicker(idx)}
                          className={`w-full flex items-center justify-between px-3 py-2 rounded-lg border text-xs font-bold transition-all ${serialOk ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-amber-50 border-amber-200 text-amber-700'}`}>
                          <span className="flex items-center gap-1.5">
                            <Hash size={12} />
                            Serial ရွေးရန် ({selectedCount}/{needed})
                          </span>
                          {serialOk ? <CheckCircle2 size={13} /> : <AlertTriangle size={13} />}
                        </button>
                        {selectedCount > 0 && (
                          <div className="mt-1 flex flex-wrap gap-1">
                            {item.selectedSerialNumbers?.map(sn => (
                              <span key={sn} className="text-[9px] font-black px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-100 rounded-full">{sn}</span>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Save */}
          <button onClick={handleSave} disabled={saving}
            className="w-full bg-indigo-600 text-white py-3 rounded-xl font-black text-sm uppercase flex items-center justify-center gap-2 hover:bg-indigo-700 disabled:opacity-60 transition-all">
            {saving ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle2 size={16} />}
            {editingOrder ? 'မွမ်းမံသိမ်းဆည်းမည်' : 'Draft သိမ်းဆည်းမည်'}
          </button>
        </div>
      </div>

      {/* Serial Picker Modal */}
      {serialPickerItem && (() => {
        const { itemIdx, product } = serialPickerItem;
        const item = formItems[itemIdx];
        const serials = getAvailableSerials(product.id);
        return (
          <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
              <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-black text-slate-800">{product.name} · Serial ရွေးမည်</h3>
                  <p className="text-[10px] text-slate-400 font-bold">{item.qty} ခု ရွေးရမည် · ရွေးပြီး: {item.selectedSerialIds?.length ?? 0}</p>
                </div>
                <button onClick={() => setSerialPickerItem(null)} className="text-slate-400 hover:text-slate-700"><X size={18} /></button>
              </div>
              <div className="p-4 max-h-72 overflow-y-auto space-y-2">
                {serials.length === 0 && <p className="text-center text-xs text-slate-400 font-bold py-4">AVAILABLE Serial မရှိပါ</p>}
                {serials.map(s => {
                  const selected = (item.selectedSerialIds ?? []).includes(s.id);
                  return (
                    <div key={s.id} onClick={() => toggleSerial(s.id, s.serialNumber, itemIdx)}
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
                <button onClick={() => setSerialPickerItem(null)}
                  className="w-full bg-indigo-600 text-white py-2 rounded-xl text-sm font-black hover:bg-indigo-700">
                  ပြီးပါပြီ
                </button>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );

  // ────────── LIST VIEW ──────────
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold text-slate-800">ထုတ်လုပ်ရေး</h2>
          <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">Manufacturing / Assembly</p>
        </div>
        <button onClick={openNew}
          className="bg-indigo-600 text-white px-4 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 hover:bg-indigo-700">
          <Plus size={14} /> Order အသစ်
        </button>
      </div>

      {orders.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-2xl text-center py-16 text-slate-400">
          <Package size={40} className="mx-auto mb-3 opacity-30" />
          <p className="text-sm font-bold">ထုတ်လုပ်ရေး Order မရှိသေးပါ</p>
          <button onClick={openNew} className="mt-4 bg-indigo-600 text-white px-4 py-2 rounded-lg text-xs font-bold uppercase flex items-center gap-1.5 mx-auto hover:bg-indigo-700">
            <Plus size={14} /> Order အသစ် ဖန်တီးမည်
          </button>
        </div>
      )}

      <div className="space-y-3">
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
                  {order.totalComponentCost ? ` · ကုန်ကျ: ${Number(order.totalComponentCost).toLocaleString()} Ks` : ''}
                </p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                {order.status === ManufacturingStatus.DRAFT && (
                  <>
                    <button onClick={() => openEdit(order)}
                      className="p-2 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-all" title="ပြင်ဆင်ရန်">
                      <Edit2 size={15} />
                    </button>
                    <button onClick={() => handleComplete(order)}
                      className="px-3 py-1.5 bg-emerald-600 text-white rounded-lg text-xs font-black uppercase flex items-center gap-1 hover:bg-emerald-700">
                      <CheckCheck size={13} /> Finish
                    </button>
                    <button onClick={() => handleCancel(order)}
                      className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all" title="ပယ်ဖျက်ရန်">
                      <Ban size={15} />
                    </button>
                  </>
                )}
                {order.status !== ManufacturingStatus.COMPLETED && (
                  <button onClick={() => handleDelete(order)}
                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-all" title="ဖျက်ရန်">
                    <Trash2 size={15} />
                  </button>
                )}
                <button onClick={() => setExpandedId(expandedId === order.id ? null : (order.id ?? null))}
                  className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-all">
                  {expandedId === order.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
              </div>
            </div>

            {expandedId === order.id && (
              <div className="border-t border-slate-100 px-5 py-4">
                <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-3">Component ပစ္စည်းများ</p>
                {(order.items || []).length === 0 && <p className="text-xs text-slate-400 font-bold">ပစ္စည်း မထည့်ရသေးပါ</p>}
                <div className="space-y-2">
                  {(order.items || []).map((item, i) => (
                    <div key={i} className="flex items-center justify-between bg-slate-50 rounded-xl px-4 py-2.5 gap-3">
                      <div className="min-w-0">
                        <p className="text-xs font-black text-slate-800 truncate">{item.productName}</p>
                        <p className="text-[10px] text-slate-400 font-semibold">{item.productCode} · {item.hasSerial ? 'Serial' : 'Qty'}</p>
                        {item.selectedSerialNumbers && item.selectedSerialNumbers.length > 0 && (
                          <div className="flex flex-wrap gap-1 mt-1">
                            {item.selectedSerialNumbers.map(sn => (
                              <span key={sn} className="text-[9px] font-black px-2 py-0.5 bg-indigo-50 text-indigo-700 border border-indigo-100 rounded-full">{sn}</span>
                            ))}
                          </div>
                        )}
                      </div>
                      <div className="text-right shrink-0">
                        <p className="text-xs font-black text-slate-700">{item.qty} ခု</p>
                        <p className="text-[10px] text-slate-400 font-semibold">{Number(item.unitCost ?? 0).toLocaleString()} Ks/ခု</p>
                      </div>
                    </div>
                  ))}
                </div>
                {order.notes && (
                  <p className="mt-3 text-xs text-slate-500 font-semibold border-t border-slate-100 pt-3">{order.notes}</p>
                )}
                {order.status === ManufacturingStatus.COMPLETED && order.finishedProductId && (
                  <div className="mt-3 flex items-center gap-2 bg-emerald-50 border border-emerald-100 rounded-xl px-4 py-2.5">
                    <CheckCircle2 size={14} className="text-emerald-600 shrink-0" />
                    <p className="text-xs font-black text-emerald-700">
                      ကုန်ပစ္စည်းမာစတာ ထဲ ထည့်ပြီး (ID: {order.finishedProductId}) · {order.completedAt?.slice(0, 10)}
                    </p>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default ManufacturingManagement;
