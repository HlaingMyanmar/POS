import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { useDataEvents } from '../hooks/useDataEvents';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { warehouseApiService, WarehouseDTO, WarehouseTransferDTO } from '../services/warehouseapiservice';
import { productService } from '../services/productapiservice';
import { ProductDTO } from '../types';

const emptyForm: Omit<WarehouseDTO, 'id'> = { code: '', name: '', address: '', active: true };

const WarehouseManagement: React.FC = () => {
  const [warehouses, setWarehouses] = useState<WarehouseDTO[]>([]);
  const [transfers, setTransfers] = useState<WarehouseTransferDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form, setForm] = useState<Omit<WarehouseDTO, 'id'>>(emptyForm);
  const [saving, setSaving] = useState(false);

  const [productSearch, setProductSearch] = useState('');
  const [productOpen, setProductOpen] = useState(false);
  const [transferProductId, setTransferProductId] = useState(0);
  const [fromWarehouseId, setFromWarehouseId] = useState(0);
  const [toWarehouseId, setToWarehouseId] = useState(0);
  const [transferQty, setTransferQty] = useState(1);
  const [transferRemark, setTransferRemark] = useState('');
  const [transferring, setTransferring] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [wh, hist, prods] = await Promise.all([
        warehouseApiService.list(false),
        warehouseApiService.transferHistory(),
        productService.getAll()
      ]);
      setWarehouses(wh);
      setTransfers(hist);
      setProducts(prods);
    } catch (e: any) {
      Swal.fire('Error', e?.message ?? 'Failed to load warehouses', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useRefreshOnTabActivate(load);
  useDataEvents(['Stock', 'Purchase'], load);

  const filtered = warehouses.filter((w) =>
    w.code?.toLowerCase().includes(search.toLowerCase()) ||
    w.name.toLowerCase().includes(search.toLowerCase()) ||
    (w.address ?? '').toLowerCase().includes(search.toLowerCase())
  );

  const activeWarehouses = useMemo(() => warehouses.filter((w) => w.active !== false), [warehouses]);

  const filteredProducts = useMemo(() => {
    const q = productSearch.trim().toLowerCase();
    if (!q) return products.slice(0, 30);
    return products.filter((p) =>
      p.name?.toLowerCase().includes(q) ||
      p.productCode?.toLowerCase().includes(q)
    ).slice(0, 30);
  }, [products, productSearch]);

  const selectedProduct = products.find((p) => p.id === transferProductId);

  const openAdd = () => { setForm(emptyForm); setEditId(null); setShowModal(true); };
  const openEdit = (wh: WarehouseDTO) => {
    setForm({ code: wh.code ?? '', name: wh.name, address: wh.address ?? '', active: wh.active !== false });
    setEditId(wh.id!);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) { Swal.fire('Error', 'ဂိုဒေါင်အမည် ထည့်ပါ', 'error'); return; }
    setSaving(true);
    try {
      if (editId) await warehouseApiService.update(editId, form);
      else await warehouseApiService.create(form);
      await load();
      setShowModal(false);
      Swal.fire({ icon: 'success', title: 'သိမ်းပြီး', timer: 1200, showConfirmButton: false });
    } catch (e: any) {
      Swal.fire('Error', e?.message ?? 'Failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleTransfer = async () => {
    if (!transferProductId || !fromWarehouseId || !toWarehouseId || transferQty <= 0) {
      Swal.fire('Required', 'Product, from/to warehouse and qty are required.', 'warning');
      return;
    }
    if (fromWarehouseId === toWarehouseId) {
      Swal.fire('Invalid', 'From နှင့် To warehouse မတူရပါ။', 'warning');
      return;
    }
    setTransferring(true);
    try {
      await warehouseApiService.transfer({
        productId: transferProductId,
        fromWarehouseId,
        toWarehouseId,
        qty: transferQty,
        remark: transferRemark.trim() || undefined
      });
      Swal.fire({ icon: 'success', title: 'Transfer recorded', timer: 1400, showConfirmButton: false });
      setTransferQty(1);
      setTransferRemark('');
      await load();
    } catch (e: any) {
      Swal.fire('Transfer failed', e?.message ?? 'Unable to transfer', 'error');
    } finally {
      setTransferring(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl shadow">
        <div className="flex flex-wrap items-center gap-2 px-3 py-2.5 border-b bg-slate-50/60">
          <input value={search} onChange={(e) => setSearch(e.target.value)}
            placeholder="ကုဒ် / အမည် ရှာပါ..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-44 focus:ring-2 focus:ring-teal-500 bg-white" />
          <span className="text-sm text-slate-400">{filtered.length} ဂိုဒေါင်</span>
          <button onClick={openAdd}
            className="ml-auto px-4 py-1.5 bg-teal-600 text-white rounded-lg text-sm font-bold hover:bg-teal-700">
            + ဂိုဒေါင်အသစ်
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-purple-600 text-left">
              <tr>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ကုဒ်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အမည်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လိပ်စာ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အခြေအနေ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {loading ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-slate-400">ဖွင့်နေသည်...</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-slate-400">ဂိုဒေါင် ရှာမတွေ့ပါ</td></tr>
              ) : filtered.map((wh) => (
                <tr key={wh.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <span className="font-mono font-bold text-teal-700 bg-teal-50 border border-teal-200 rounded px-2 py-0.5">
                      {wh.code || '—'}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-semibold text-slate-700">{wh.name}</td>
                  <td className="px-4 py-3 text-slate-600">{wh.address || '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-semibold rounded-full px-2 py-0.5 ${wh.active !== false ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      {wh.active !== false ? 'အသုံးပြုနေ' : 'ပိတ်ထား'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button onClick={() => openEdit(wh)} className="text-indigo-600 hover:underline text-xs font-medium">ပြင်ဆင်</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow p-4 space-y-3">
        <div>
          <h3 className="text-sm font-black text-slate-800">Warehouse Transfer</h3>
          <p className="text-[10px] text-slate-500">ကုန်ပစ္စည်းကို ဂိုဒေါင် တစ်ခုမှ တစ်ခုသို့ ရွှေ့မည် (FEFO lot).</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="relative lg:col-span-2">
            <label className="block text-[10px] font-bold uppercase text-slate-400 mb-1">Product</label>
            <input
              value={productSearch || (selectedProduct ? `${selectedProduct.name} (${selectedProduct.productCode})` : '')}
              onChange={(e) => { setProductSearch(e.target.value); setProductOpen(true); }}
              onFocus={() => setProductOpen(true)}
              onBlur={() => setTimeout(() => setProductOpen(false), 150)}
              placeholder="ပစ္စည်း ရှာပါ..."
              className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500"
            />
            {productOpen && (
              <div className="absolute z-20 mt-1 max-h-48 w-full overflow-auto rounded-lg border bg-white shadow-lg">
                {filteredProducts.length === 0 ? (
                  <p className="px-3 py-2 text-xs text-slate-400">မတွေ့ပါ</p>
                ) : filteredProducts.map((p) => (
                  <button key={p.id} type="button"
                    onMouseDown={() => {
                      setTransferProductId(p.id!);
                      setProductSearch(`${p.name} (${p.productCode})`);
                      setProductOpen(false);
                    }}
                    className="block w-full px-3 py-2 text-left text-xs hover:bg-teal-50">
                    <b>{p.name}</b> <span className="text-slate-400">{p.productCode}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase text-slate-400 mb-1">From</label>
            <select value={fromWarehouseId} onChange={(e) => setFromWarehouseId(Number(e.target.value))}
              className="w-full border rounded-lg px-2 py-2 text-sm">
              <option value={0}>ရွေးပါ</option>
              {activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase text-slate-400 mb-1">To</label>
            <select value={toWarehouseId} onChange={(e) => setToWarehouseId(Number(e.target.value))}
              className="w-full border rounded-lg px-2 py-2 text-sm">
              <option value={0}>ရွေးပါ</option>
              {activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-[10px] font-bold uppercase text-slate-400 mb-1">Qty</label>
            <input type="number" min={1} value={transferQty || ''} onChange={(e) => setTransferQty(Math.max(1, Number(e.target.value) || 0))}
              className="w-full border rounded-lg px-2 py-2 text-sm font-bold" />
          </div>
        </div>
        <div className="flex flex-wrap gap-2 items-end">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-[10px] font-bold uppercase text-slate-400 mb-1">Remark</label>
            <input value={transferRemark} onChange={(e) => setTransferRemark(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm" placeholder="Optional" />
          </div>
          <button onClick={() => void handleTransfer()} disabled={transferring}
            className="px-4 py-2 bg-teal-600 text-white rounded-lg text-sm font-bold hover:bg-teal-700 disabled:opacity-50">
            {transferring ? 'Transferring...' : 'Transfer'}
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <div className="px-4 py-3 border-b bg-slate-50/60">
          <h3 className="text-sm font-black text-slate-800">Transfer History</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-100 text-left text-xs text-slate-500">
              <tr>
                <th className="px-4 py-2">No</th>
                <th className="px-4 py-2">Product</th>
                <th className="px-4 py-2">From → To</th>
                <th className="px-4 py-2 text-right">Qty</th>
                <th className="px-4 py-2">When / By</th>
                <th className="px-4 py-2">Remark</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {transfers.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-400">Transfer မရှိသေးပါ</td></tr>
              ) : transfers.map((t) => (
                <tr key={t.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2 font-mono text-xs font-bold">{t.transferNo || `#${t.id}`}</td>
                  <td className="px-4 py-2">{t.productName || t.productId}</td>
                  <td className="px-4 py-2 text-xs">{t.fromWarehouseName} → {t.toWarehouseName}</td>
                  <td className="px-4 py-2 text-right font-bold">{t.qty}</td>
                  <td className="px-4 py-2 text-xs text-slate-500">
                    {t.transferredAt ? new Date(t.transferredAt).toLocaleString() : '-'}
                    <span className="block">{t.transferredBy || ''}</span>
                  </td>
                  <td className="px-4 py-2 text-xs text-slate-500">{t.remark || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm flex flex-col">
            <div className="flex items-center justify-between px-5 py-4 border-b">
              <h2 className="font-semibold">{editId ? 'ပြင်ဆင်' : 'အသစ်'} ဂိုဒေါင်</h2>
              <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600 text-xl">✕</button>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Code</label>
                <input value={form.code ?? ''} onChange={(e) => setForm((p) => ({ ...p, code: e.target.value.toUpperCase() }))}
                  placeholder="e.g. MAIN, WH-2"
                  className="w-full border rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-teal-500" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Name <span className="text-red-500">*</span></label>
                <input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                  placeholder="e.g. Main Warehouse"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Address</label>
                <input value={form.address ?? ''} onChange={(e) => setForm((p) => ({ ...p, address: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500" />
              </div>
              <div className="flex items-center gap-2">
                <input type="checkbox" id="wh-active" checked={form.active !== false}
                  onChange={(e) => setForm((p) => ({ ...p, active: e.target.checked }))}
                  className="w-4 h-4 accent-teal-600" />
                <label htmlFor="wh-active" className="text-sm text-slate-600">Active</label>
              </div>
            </div>
            <div className="flex gap-2 px-5 pb-5">
              <button onClick={() => setShowModal(false)} className="flex-1 px-4 py-2 text-sm rounded-lg border hover:bg-slate-50">Cancel</button>
              <button onClick={() => void handleSave()} disabled={saving}
                className="flex-1 px-4 py-2 text-sm bg-teal-600 text-white rounded-lg hover:bg-teal-700 font-semibold disabled:opacity-60">
                {saving ? 'Saving...' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default WarehouseManagement;
