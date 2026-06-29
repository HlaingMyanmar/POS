import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  Box,
  CheckCircle2,
  ChevronDown,
  ClipboardPaste,
  Download,
  Filter,
  Loader2,
  Lock,
  Package,
  RefreshCw,
  Save,
  Search,
  ShieldAlert,
  X
} from 'lucide-react';
import Swal from 'sweetalert2';
import { productService } from '../services/productapiservice';
import { staffService } from '../services/staffapiservice';
import { stockAdjustmentApiService } from '../services/stockadjustmentapiservice';
import { AdjustmentType, ProductDTO, StaffDTO } from '../types';

interface StockRow {
  productId: number;
  productCode: string;
  productName: string;
  category: string;
  brand: string;
  unit: string;
  currentStock: number;
  costPrice: number;
  openingQty: string;
  hasSerial: boolean;
  saved: boolean;
}

type StatusFilter = 'ALL' | 'READY' | 'ENTERED' | 'EMPTY' | 'SERIAL' | 'EXISTING' | 'NO_COST' | 'INVALID';
type EntryMode = 'EMPTY_ONLY' | 'TARGET_QTY';

const fmt = (n: number) => n.toLocaleString('en-US', { maximumFractionDigits: 0 });
const qty = (n: number, unit = 'pcs') => fmt(n) + ' ' + (unit || 'pcs');
const today = () => new Date().toISOString().slice(0, 10);
const defaultRef = () => 'OPN-STK-' + today().replace(/-/g, '');

const parseQty = (value: string) => {
  if (value.trim() === '') return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0 || !Number.isInteger(parsed)) return NaN;
  return parsed;
};

const MetricCard: React.FC<{
  label: string;
  value: string;
  hint: string;
  icon: React.ReactNode;
  tone: 'emerald' | 'blue' | 'amber' | 'slate' | 'rose';
}> = ({ label, value, hint, icon, tone }) => {
  const color = {
    emerald: 'bg-emerald-50 text-emerald-700 border-emerald-100',
    blue: 'bg-blue-50 text-blue-700 border-blue-100',
    amber: 'bg-amber-50 text-amber-700 border-amber-100',
    slate: 'bg-slate-50 text-slate-700 border-slate-200',
    rose: 'bg-rose-50 text-rose-700 border-rose-100',
  }[tone];

  return (
    <div className={`rounded-lg border ${color} px-4 py-3 flex items-start gap-3 min-w-0`}>
      <div className="mt-0.5 shrink-0">{icon}</div>
      <div className="min-w-0">
        <p className="text-[10px] font-black uppercase opacity-75">{label}</p>
        <p className="text-lg font-black tabular-nums truncate">{value}</p>
        <p className="text-[11px] font-semibold opacity-70 truncate">{hint}</p>
      </div>
    </div>
  );
};

const OpeningStockPage: React.FC = () => {
  const [rows, setRows] = useState<StockRow[]>([]);
  const [staffList, setStaffList] = useState<StaffDTO[]>([]);
  const [staffId, setStaffId] = useState<number | null>(null);
  const [mode, setMode] = useState<EntryMode>('EMPTY_ONLY');
  const [referenceNo, setReferenceNo] = useState(defaultRef);
  const [countDate, setCountDate] = useState(today);
  const [sessionNote, setSessionNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [catFilter, setCatFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('READY');
  const inputRefs = useRef<Map<number, HTMLInputElement>>(new Map());

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [products, staff] = await Promise.all([
        productService.getAll(),
        staffService.getAllActive(),
      ]);

      let draft = null as any;
      try { draft = JSON.parse(localStorage.getItem('sspd.openingStock.draft.v1') || 'null'); } catch { draft = null; }
      const draftEntries: Record<string, string> = draft?.entries || {};

      setRows(
        (products as ProductDTO[]).map((p) => ({
          productId: p.id,
          productCode: p.productCode ?? '',
          productName: p.name,
          category: p.categoryName ?? '-',
          brand: p.brandName ?? '-',
          unit: p.unitName ?? 'pcs',
          currentStock: Number(p.currentStock ?? p.stockQty ?? 0),
          costPrice: Number(p.costPrice ?? 0),
          openingQty: draftEntries[String(p.id)] ?? '',
          hasSerial: p.hasSerial === true,
          saved: false,
        }))
      );

      if (draft?.mode === 'EMPTY_ONLY' || draft?.mode === 'TARGET_QTY') setMode(draft.mode);
      if (draft?.referenceNo) setReferenceNo(draft.referenceNo);
      if (draft?.countDate) setCountDate(draft.countDate);
      if (draft?.sessionNote) setSessionNote(draft.sessionNote);

      const staffRows = staff as StaffDTO[];
      setStaffList(staffRows);
      if (!staffId && staffRows.length > 0) setStaffId(staffRows[0].id);
    } catch (e: any) {
      setError(e?.message ?? 'ဒေတာဖတ်မရပါ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  useEffect(() => {
    if (loading) return;
    const entries = rows.reduce<Record<string, string>>((acc, row) => {
      if (row.openingQty.trim()) acc[String(row.productId)] = row.openingQty;
      return acc;
    }, {});
    localStorage.setItem('sspd.openingStock.draft.v1', JSON.stringify({ entries, mode, referenceNo, countDate, sessionNote }));
  }, [countDate, loading, mode, referenceNo, rows, sessionNote]);

  const hasCost = (row: StockRow) => row.costPrice > 0;
  const isEditable = (row: StockRow) => !row.hasSerial && hasCost(row) && (mode === 'TARGET_QTY' || row.currentStock === 0);
  const deltaQty = (row: StockRow) => {
    const v = parseQty(row.openingQty);
    if (v === null || Number.isNaN(v)) return 0;
    return mode === 'TARGET_QTY' ? Number(v) - row.currentStock : Number(v);
  };

  const categories = useMemo(() => Array.from(new Set(rows.map((r) => r.category))).sort(), [rows]);

  const summary = useMemo(() => {
    const ready = rows.filter(isEditable).length;
    const serial = rows.filter((r) => r.hasSerial).length;
    const existing = rows.filter((r) => !r.hasSerial && r.currentStock > 0).length;
    const noCost = rows.filter((r) => !r.hasSerial && r.currentStock === 0 && !hasCost(r)).length;
    const entered = rows.filter((r) => isEditable(r) && r.openingQty.trim() !== '').length;
    const invalid = rows.filter((r) => Number.isNaN(parseQty(r.openingQty))).length;
    const saveCount = rows.filter((r) => {
      const v = parseQty(r.openingQty);
      return isEditable(r) && v !== null && !Number.isNaN(v) && v > 0;
    }).length;
    const totalQty = rows.reduce((sum, r) => {
      const v = parseQty(r.openingQty);
      return sum + (isEditable(r) && v !== null && !Number.isNaN(v) ? v : 0);
    }, 0);
    return { ready, serial, existing, noCost, entered, invalid, saveCount, totalQty, total: rows.length };
  }, [rows]);

  const catTotals = useMemo(() => {
    const map: Record<string, { count: number; ready: number; entered: number }> = {};
    rows.forEach((r) => {
      if (!map[r.category]) map[r.category] = { count: 0, ready: 0, entered: 0 };
      map[r.category].count += 1;
      if (isEditable(r)) map[r.category].ready += 1;
      if (isEditable(r) && r.openingQty.trim() !== '') map[r.category].entered += 1;
    });
    return categories.map((cat) => ({ cat, ...map[cat] }));
  }, [rows, categories]);

  const filteredRows = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return rows
      .filter((r) => catFilter === 'ALL' || r.category === catFilter)
      .filter((r) => {
        const invalid = Number.isNaN(parseQty(r.openingQty));
        if (statusFilter === 'READY') return isEditable(r);
        if (statusFilter === 'ENTERED') return isEditable(r) && r.openingQty.trim() !== '';
        if (statusFilter === 'EMPTY') return isEditable(r) && r.openingQty.trim() === '';
        if (statusFilter === 'SERIAL') return r.hasSerial;
        if (statusFilter === 'EXISTING') return !r.hasSerial && r.currentStock > 0;
        if (statusFilter === 'NO_COST') return !r.hasSerial && r.currentStock === 0 && !hasCost(r);
        if (statusFilter === 'INVALID') return invalid;
        return true;
      })
      .filter((r) => !keyword ||
        r.productName.toLowerCase().includes(keyword) ||
        r.productCode.toLowerCase().includes(keyword) ||
        r.category.toLowerCase().includes(keyword) ||
        r.brand.toLowerCase().includes(keyword)
      );
  }, [rows, query, catFilter, statusFilter]);

  const rowsToSave = useMemo(() => rows.filter((r) => {
    const v = parseQty(r.openingQty);
    if (!isEditable(r) || v === null || Number.isNaN(v)) return false;
    return mode === 'TARGET_QTY' ? Number(v) !== r.currentStock : Number(v) > 0;
  }), [mode, rows]);

  const setQty = (productId: number, value: string) =>
    setRows((prev) => prev.map((r) => r.productId === productId ? { ...r, openingQty: value, saved: false } : r));

  const clearEntry = (productId: number) =>
    setRows((prev) => prev.map((r) => r.productId === productId ? { ...r, openingQty: '', saved: false } : r));

  const resetEntries = () => {
    setRows((prev) => prev.map((r) => ({ ...r, openingQty: '', saved: false })));
    localStorage.removeItem('sspd.openingStock.draft.v1');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, idx: number) => {
    if (e.key === 'Enter' || e.key === 'ArrowDown') {
      e.preventDefault();
      const next = filteredRows.slice(idx + 1).find(isEditable);
      if (next) inputRefs.current.get(next.productId)?.focus();
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prev = [...filteredRows.slice(0, idx)].reverse().find(isEditable);
      if (prev) inputRefs.current.get(prev.productId)?.focus();
    }
  };


  const exportCountSheet = () => {
    const header = ['Product Code', 'Product Name', 'Category', 'Brand', 'Unit', 'Current Stock', 'Cost Price', 'Counted Qty', 'Note'];
    const csvRows = [header, ...filteredRows.map((row) => [row.productCode, row.productName, row.category, row.brand, row.unit, row.currentStock, row.costPrice, row.openingQty, row.hasSerial ? 'Serial product' : !hasCost(row) ? 'Cost price required' : ''])];
    const csv = csvRows.map((line) => line.map((value) => {
      const cell = String(value ?? '');
      return /[",\n]/.test(cell) ? '"' + cell.replace(/"/g, '""') + '"' : cell;
    }).join(',')).join('\n');
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'opening_stock_count_' + (countDate || today()) + '.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const pasteCounts = async () => {
    const result = await Swal.fire({ title: 'Paste Count Sheet', input: 'textarea', inputPlaceholder: 'Product Code\tQty\nPRD-000001\t10', inputAttributes: { rows: '12' }, showCancelButton: true, confirmButtonText: 'Apply' });
    if (!result.isConfirmed || !String(result.value || '').trim()) return;
    const updates = new Map<number, string>();
    const misses: string[] = [];
    const locked: string[] = [];
    String(result.value).split(/\r?\n/).map((line) => line.trim()).filter(Boolean).forEach((line) => {
      const cols = line.split(/\t|,/).map((col) => col.trim()).filter(Boolean);
      if (cols.length < 2 || cols.some((col) => ['code', 'productcode', 'qty', 'quantity'].includes(col.toLowerCase()))) return;
      const parsed = parseQty(cols[cols.length - 1]);
      const key = cols[0].toLowerCase();
      const row = rows.find((r) => r.productCode.toLowerCase() === key || r.productName.toLowerCase() === key);
      if (!row || parsed === null || Number.isNaN(parsed)) { misses.push(cols[0]); return; }
      if (!isEditable(row)) { locked.push(row.productName); return; }
      updates.set(row.productId, String(parsed));
    });
    setRows((prev) => prev.map((row) => updates.has(row.productId) ? { ...row, openingQty: updates.get(row.productId)!, saved: false } : row));
    Swal.fire({ icon: updates.size > 0 ? 'success' : 'info', title: updates.size + ' row applied', html: [misses.length ? 'Not found/invalid: ' + misses.slice(0, 8).join(', ') : '', locked.length ? 'Locked: ' + locked.slice(0, 8).join(', ') : ''].filter(Boolean).join('<br/>') });
  };

  const saveAll = async () => {
    if (!staffId) {
      Swal.fire({ icon: 'warning', title: 'Staff ရွေးပါ', confirmButtonText: 'OK' });
      return;
    }
    if (summary.invalid > 0) {
      setStatusFilter('INVALID');
      Swal.fire({ icon: 'warning', title: 'Qty မမှန်ပါ', text: 'ကနဦးလက်ကျန်တွင် 0 ထက်ကြီးသော အရေအတွက်မှန်မှန်ထည့်ပါ။' });
      return;
    }
    if (rowsToSave.length === 0) {
      Swal.fire({ icon: 'info', title: 'သိမ်းရန် ကနဦးလက်ကျန် မရှိပါ', confirmButtonText: 'OK' });
      return;
    }

    const confirm = await Swal.fire({
      icon: 'question',
      title: `${rowsToSave.length} မျိုးကို ကနဦးလက်ကျန်အဖြစ် သိမ်းမည်`,
      html: '<div style="font-size:12px;text-align:left">Stock မရှိသေးသော quantity-only product များအတွက်သာ သိမ်းပါမည်။ Product Master တွင် ဝယ်ဈေးရှိမှ stock value journal ထွက်နိုင်ပါသည်။</div>',
      showCancelButton: true,
      confirmButtonText: 'သိမ်းမည်',
      cancelButtonText: 'မလုပ်တော့',
    });
    if (!confirm.isConfirmed) return;

    setSaving(true);
    setProgress({ done: 0, total: rowsToSave.length });
    let failed = 0;
    const failedNames: string[] = [];

    for (let i = 0; i < rowsToSave.length; i++) {
      const row = rowsToSave[i];
      const openingQty = Number(parseQty(row.openingQty));
      const qtyChange = deltaQty(row);
      try {
        await stockAdjustmentApiService.create({
          productId: row.productId,
          adjustmentType: AdjustmentType.CORRECTION,
          qtyChange,
          staffId,
          reason: ['Opening Stock ' + (referenceNo || ''), 'Count date: ' + (countDate || today()), 'Physical: ' + openingQty, 'Before: ' + row.currentStock, sessionNote.trim() ? 'Note: ' + sessionNote.trim() : ''].filter(Boolean).join(' | '),
        });
        setRows((prev) => prev.map((r) =>
          r.productId === row.productId
            ? { ...r, currentStock: openingQty, openingQty: '', saved: true }
            : r
        ));
      } catch (e: any) {
        failed++;
        failedNames.push(`${row.productName}: ${e?.response?.data?.message ?? e?.message ?? 'error'}`);
      }
      setProgress({ done: i + 1, total: rowsToSave.length });
    }

    setSaving(false);
    setProgress(null);

    if (failed === 0) {
      localStorage.removeItem('sspd.openingStock.draft.v1');
      Swal.fire({ icon: 'success', title: 'ကနဦး ကုန်လက်ကျန် သိမ်းပြီးပါပြီ', timer: 1400, showConfirmButton: false });
      setStatusFilter('READY');
    } else {
      Swal.fire({
        icon: 'warning',
        title: `${failed} ခု မအောင်မြင်ပါ`,
        html: failedNames.map((n) => `<div style="font-size:12px;text-align:left">${n}</div>`).join(''),
      });
    }
  };

  return (
    <div className="h-full flex flex-col bg-slate-50/50 overflow-hidden">
      <div className="shrink-0 bg-white border-b border-slate-200 px-5 py-4">
        <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-lg bg-emerald-600 text-white flex items-center justify-center shadow-sm">
              <Package size={23} />
            </div>
            <div>
              <h1 className="text-lg font-black text-slate-900 tracking-tight">ကနဦး ကုန်လက်ကျန်</h1>
              <p className="text-xs text-slate-500 font-semibold">
                Stock မရှိသေးသော quantity-only ကုန်ပစ္စည်းများကို go-live ကနဦးလက်ကျန်အဖြစ် ထည့်သွင်းရန်
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative">
              <select
                value={staffId ?? ''}
                onChange={(e) => setStaffId(Number(e.target.value) || null)}
                className="h-10 pl-3 pr-8 rounded-lg border border-slate-200 bg-white text-sm font-semibold text-slate-700 outline-none focus:border-emerald-500 appearance-none cursor-pointer"
              >
                <option value="">Staff ရွေးပါ</option>
                {staffList.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}{s.role ? ` (${s.role})` : ''}</option>
                ))}
              </select>
              <ChevronDown size={14} className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-slate-400" />
            </div>

            <button
              onClick={exportCountSheet}
              className="h-10 px-3 rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-black uppercase inline-flex items-center gap-2"
            >
              <Download size={15} />
              Count Sheet
            </button>

            <button
              onClick={pasteCounts}
              className="h-10 px-3 rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-black uppercase inline-flex items-center gap-2"
            >
              <ClipboardPaste size={15} />
              Paste
            </button>

            <button
              onClick={load}
              disabled={loading}
              className="h-10 px-4 rounded-lg border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 text-xs font-black uppercase inline-flex items-center gap-2 disabled:opacity-50"
            >
              <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
              ပြန်ဖတ်
            </button>

            <button
              onClick={saveAll}
              disabled={saving || rowsToSave.length === 0 || !staffId || summary.invalid > 0}
              className="h-10 px-4 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 text-xs font-black uppercase inline-flex items-center gap-2 disabled:opacity-50 shadow-sm"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
              သိမ်းမည် ({rowsToSave.length})
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-auto p-5 space-y-4">
        {error && (
          <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-sm font-semibold">
            <AlertCircle size={16} />
            {error}
          </div>
        )}

        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 flex items-start gap-3 text-amber-800">
          <ShieldAlert size={18} className="mt-0.5 shrink-0" />
          <div>
            <p className="text-sm font-bold">Opening Stock သည် correction screen မဟုတ်ပါ</p>
            <p className="text-xs font-semibold mt-0.5">
              ရောင်း/ဝယ် လည်ပတ်ပြီးသား stock ကိုပြင်ရန် Stock Adjustment ကိုသုံးပါ။ Serial product များကို Purchase မှ serial number ဖြင့်ထည့်ပါ။
            </p>
          </div>
        </div>


        <div className="rounded-lg bg-white border border-slate-200 p-3 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-3">
          <input value={referenceNo} onChange={(e) => setReferenceNo(e.target.value)} className="h-10 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold outline-none focus:bg-white focus:border-emerald-500" />
          <input type="date" value={countDate} onChange={(e) => setCountDate(e.target.value)} className="h-10 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold outline-none focus:bg-white focus:border-emerald-500" />
          <select value={mode} onChange={(e) => setMode(e.target.value as EntryMode)} className="h-10 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold outline-none focus:bg-white focus:border-emerald-500">
            <option value="EMPTY_ONLY">Empty stock only</option>
            <option value="TARGET_QTY">Target physical count</option>
          </select>
          <input value={sessionNote} onChange={(e) => setSessionNote(e.target.value)} placeholder="Session note" className="h-10 rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold outline-none focus:bg-white focus:border-emerald-500" />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
          <MetricCard label="Ready" value={String(summary.ready)} hint="ထည့်နိုင်သော product" icon={<Box size={17} />} tone="emerald" />
          <MetricCard label="Entered" value={String(summary.entered)} hint="Qty ဖြည့်ထားသည်" icon={<CheckCircle2 size={17} />} tone="blue" />
          <MetricCard label="Opening Qty" value={qty(summary.totalQty)} hint="သိမ်းမည့် qty" icon={<Package size={17} />} tone="slate" />
          <MetricCard label="Existing Stock" value={String(summary.existing)} hint="stock ရှိပြီး locked" icon={<Lock size={17} />} tone="amber" />
          <MetricCard label="Need Cost" value={String(summary.noCost)} hint="Product Master ဝယ်ဈေးလိုသည်" icon={<ShieldAlert size={17} />} tone="rose" />
        </div>

        {catTotals.length > 1 && (
          <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-2">
            <button
              onClick={() => setCatFilter('ALL')}
              className={`rounded-lg border px-3 py-2 text-left transition-all ${
                catFilter === 'ALL'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-100 ring-2 ring-offset-1 ring-emerald-200'
                  : 'bg-white border-slate-200 hover:bg-slate-50 text-slate-700'
              }`}
            >
              <div className="flex items-center gap-2 mb-1">
                <span className="w-2 h-2 rounded-full bg-slate-400" />
                <span className="text-[11px] font-black uppercase">All</span>
                <span className="ml-auto text-[10px] font-bold opacity-70">{rows.length}</span>
              </div>
              <p className="text-xs font-black tabular-nums">{summary.ready} ready</p>
            </button>
            {catTotals.map(({ cat, count, ready, entered }) => (
              <button
                key={cat}
                onClick={() => setCatFilter(catFilter === cat ? 'ALL' : cat)}
                className={`rounded-lg border px-3 py-2 text-left transition-all ${
                  catFilter === cat
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100 ring-2 ring-offset-1 ring-emerald-200'
                    : 'bg-white border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <span className="w-2 h-2 rounded-full bg-emerald-500" />
                  <span className="text-[11px] font-black uppercase truncate">{cat}</span>
                  <span className="ml-auto text-[10px] font-bold opacity-70 shrink-0">{count}</span>
                </div>
                <p className="text-xs font-black tabular-nums">{entered}/{ready} entered</p>
              </button>
            ))}
          </div>
        )}

        <div className="rounded-lg bg-white border border-slate-200 p-3 flex flex-col xl:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ကုန်ပစ္စည်း / code / category ရှာပါ..."
              className="w-full h-10 pl-9 pr-3 rounded-lg border border-slate-200 bg-slate-50 text-sm font-semibold outline-none focus:bg-white focus:border-emerald-500"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex flex-wrap items-center gap-1 p-1 bg-slate-100 border border-slate-200 rounded-lg">
              <Filter size={13} className="ml-2 text-slate-500" />
              {([
                { key: 'READY', label: 'Ready' },
                { key: 'ENTERED', label: 'Entered' },
                { key: 'EMPTY', label: 'Empty' },
                { key: 'SERIAL', label: 'Serial' },
                { key: 'EXISTING', label: 'Existing' },
                { key: 'NO_COST', label: 'No Cost' },
                { key: 'INVALID', label: 'Invalid' },
                { key: 'ALL', label: 'All' },
              ] as const).map((item) => (
                <button
                  key={item.key}
                  onClick={() => setStatusFilter(item.key)}
                  className={`px-3 py-1.5 rounded-md text-xs font-black uppercase transition-colors ${
                    statusFilter === item.key
                      ? 'bg-white text-emerald-700 border border-slate-200 shadow-sm'
                      : 'text-slate-500 hover:text-emerald-700'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <button
              onClick={() => { setQuery(''); setCatFilter('ALL'); setStatusFilter('READY'); }}
              className="h-10 px-3 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 text-xs font-black uppercase inline-flex items-center gap-2"
            >
              <X size={14} />
              ရှင်းမည်
            </button>
            <button
              onClick={resetEntries}
              disabled={saving || summary.entered === 0}
              className="h-10 px-3 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 text-xs font-black uppercase disabled:opacity-50"
            >
              Entry Reset
            </button>
          </div>
        </div>

        {saving && progress && (
          <div className="rounded-lg bg-white border border-slate-200 px-4 py-3 space-y-2">
            <div className="flex justify-between text-xs font-black text-slate-600 uppercase">
              <span className="flex items-center gap-2">
                <Loader2 size={13} className="animate-spin text-emerald-600" />
                Saving...
              </span>
              <span>{progress.done} / {progress.total}</span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2">
              <div
                className="h-2 bg-emerald-500 rounded-full transition-all duration-200"
                style={{ width: `${(progress.done / progress.total) * 100}%` }}
              />
            </div>
          </div>
        )}

        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          {loading ? (
            <div className="h-72 flex flex-col items-center justify-center text-slate-400 gap-3">
              <Loader2 size={30} className="animate-spin text-emerald-600" />
              <p className="text-sm font-bold">Products loading...</p>
            </div>
          ) : (
            <div className="overflow-auto">
              <table className="w-full min-w-[1080px] text-left">
                <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 z-10">
                  <tr>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase">Product</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase">Category</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase text-right">Current</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase text-right">Cost</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase text-center">Opening Qty</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase text-center">Status</th>
                    <th className="px-4 py-3 text-[11px] font-black text-slate-500 uppercase text-left">Note</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredRows.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-20 text-center text-slate-400">
                        <div className="flex flex-col items-center gap-2">
                          <Search size={28} />
                          <p className="text-sm font-black uppercase">No products found</p>
                        </div>
                      </td>
                    </tr>
                  ) : filteredRows.map((row, idx) => {
                    const value = parseQty(row.openingQty);
                    const invalid = Number.isNaN(value);
                    const editable = isEditable(row);
                    const entered = editable && value !== null && !invalid && value > 0;
                    const lockedExisting = !row.hasSerial && row.currentStock > 0;
                    const missingCost = !row.hasSerial && row.currentStock === 0 && !hasCost(row);

                    return (
                      <tr
                        key={row.productId}
                        className={`transition-colors ${
                          invalid
                            ? 'bg-rose-50'
                            : entered || row.saved
                              ? 'bg-emerald-50/40'
                              : !editable
                                ? 'bg-slate-50/70'
                                : 'hover:bg-slate-50'
                        }`}
                      >
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <div className={`w-9 h-9 rounded-lg border flex items-center justify-center shrink-0 ${
                              editable ? 'bg-emerald-50 border-emerald-100 text-emerald-600' : 'bg-slate-100 border-slate-200 text-slate-400'
                            }`}>
                              {row.hasSerial || lockedExisting || missingCost ? <Lock size={15} /> : <Box size={15} />}
                            </div>
                            <div className="min-w-0">
                              <p className="text-sm font-black text-slate-800 truncate">{row.productName}</p>
                              <p className="text-[11px] text-slate-500 font-mono font-bold">{row.productCode || '-'}</p>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <p className="text-sm font-semibold text-slate-700 truncate">{row.category}</p>
                          {row.brand !== '-' && <p className="text-[11px] text-slate-400 font-semibold">{row.brand}</p>}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className={`text-sm font-black tabular-nums ${row.currentStock === 0 ? 'text-slate-400' : 'text-slate-800'}`}>
                            {fmt(row.currentStock)}
                          </span>
                          <span className="text-[10px] text-slate-400 ml-1">{row.unit}</span>
                        </td>
                        <td className={`px-4 py-3 text-right text-sm font-black tabular-nums ${hasCost(row) ? 'text-slate-700' : 'text-rose-700'}`}>
                          {hasCost(row) ? `${fmt(row.costPrice)} Ks` : '-'}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-center gap-2">
                            <input
                              ref={(el) => { if (el) inputRefs.current.set(row.productId, el); }}
                              type="number"
                              min="0"
                              step="1"
                              value={row.openingQty}
                              onChange={(e) => setQty(row.productId, e.target.value)}
                              onKeyDown={(e) => handleKeyDown(e, idx)}
                              disabled={saving || !editable}
                              className={`w-28 h-9 text-center rounded-lg border text-sm font-black tabular-nums outline-none transition-colors disabled:opacity-50 focus:ring-2 ${
                                invalid
                                  ? 'border-rose-400 bg-rose-50 focus:ring-rose-300'
                                  : entered
                                    ? 'border-emerald-400 bg-emerald-50 focus:ring-emerald-200'
                                    : 'border-slate-200 bg-slate-50 focus:bg-white focus:border-emerald-500 focus:ring-emerald-200'
                              }`}
                              placeholder="0"
                            />
                            {row.openingQty && editable && (
                              <button
                                type="button"
                                onClick={() => clearEntry(row.productId)}
                                className="h-9 w-9 rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50"
                              >
                                <X size={14} className="mx-auto" />
                              </button>
                            )}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-center">
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-[11px] font-black uppercase ${
                            invalid
                              ? 'bg-rose-50 text-rose-700 border-rose-100'
                                : row.hasSerial
                                  ? 'bg-amber-50 text-amber-700 border-amber-100'
                                  : missingCost
                                    ? 'bg-rose-50 text-rose-700 border-rose-100'
                                  : lockedExisting
                                  ? 'bg-slate-100 text-slate-600 border-slate-200'
                                  : entered || row.saved
                                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
                                    : 'bg-blue-50 text-blue-700 border-blue-100'
                          }`}>
                            {invalid ? <AlertCircle size={12} /> : editable ? <CheckCircle2 size={12} /> : <Lock size={12} />}
                            {invalid ? 'Invalid' : row.hasSerial ? 'Serial' : missingCost ? 'No Cost' : lockedExisting ? 'Existing' : entered || row.saved ? 'Entered' : 'Ready'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-slate-500">
                          {row.hasSerial
                            ? 'Purchase မှ serial number ဖြင့်ထည့်ပါ'
                            : missingCost
                              ? 'Product Master တွင် ဝယ်ဈေးထည့်ပြီးမှ သိမ်းနိုင်သည်'
                            : lockedExisting
                              ? 'Stock ရှိပြီးသားဖြစ်၍ Stock Adjustment တွင်ပြင်ပါ'
                              : 'Go-live ကနဦး Qty ထည့်နိုင်သည်'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {!loading && (
          <div className="rounded-lg bg-white border border-slate-200 px-4 py-3 flex items-center justify-between gap-4">
            <div className="flex items-center gap-4 text-sm">
              <span className="text-slate-500 font-semibold">
                Showing <strong className="text-slate-800">{filteredRows.length}</strong> of {rows.length} products
              </span>
              {rowsToSave.length > 0 && (
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-100 text-xs font-black uppercase">
                  <CheckCircle2 size={12} />
                  {rowsToSave.length} ready to save
                </span>
              )}
              {summary.invalid > 0 && (
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-rose-50 text-rose-700 border border-rose-100 text-xs font-black uppercase">
                  <AlertCircle size={12} />
                  {summary.invalid} invalid
                </span>
              )}
            </div>
            <button
              onClick={saveAll}
              disabled={saving || rowsToSave.length === 0 || !staffId || summary.invalid > 0}
              className="h-10 px-5 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 text-xs font-black uppercase inline-flex items-center gap-2 disabled:opacity-50 shadow-sm"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
              သိမ်းမည် ({rowsToSave.length})
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default OpeningStockPage;
