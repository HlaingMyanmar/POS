import React, { useCallback, useEffect, useState } from 'react';
import { Loader2, Plus, RefreshCw, Ticket } from 'lucide-react';
import Swal from 'sweetalert2';
import { api } from '../services/api';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type Promo = {
  id?: number;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  startsAt: string;
  endsAt: string;
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  maxDiscount?: number | null;
  minOrderAmount?: number | null;
  usageLimit?: number | null;
  perCustomerLimit?: number | null;
  usedCount?: number;
  productIds?: number[];
  categoryIds?: number[];
};

const empty = (): Promo => {
  const start = new Date();
  const end = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const iso = (d: Date) => d.toISOString().slice(0, 16);
  return {
    code: '',
    name: '',
    active: true,
    startsAt: iso(start),
    endsAt: iso(end),
    discountType: 'PERCENT',
    discountValue: 10,
    maxDiscount: 5000,
    minOrderAmount: 0,
    usageLimit: 100,
    perCustomerLimit: 1,
    productIds: [],
    categoryIds: [],
  };
};

const CustomerPromoCodesPage: React.FC = () => {
  const [rows, setRows] = useState<Promo[]>([]);
  const [form, setForm] = useState<Promo>(empty());
  const [productIds, setProductIds] = useState('');
  const [categoryIds, setCategoryIds] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<any>('/v1/customer-promo-codes');
      setRows(res.data ?? []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useRefreshOnTabActivate(() => { void load(); });

  const edit = (row: Promo) => {
    setForm({
      ...row,
      startsAt: row.startsAt?.slice(0, 16) || '',
      endsAt: row.endsAt?.slice(0, 16) || '',
    });
    setProductIds((row.productIds || []).join(','));
    setCategoryIds((row.categoryIds || []).join(','));
  };

  const save = async () => {
    setSaving(true);
    try {
      const payload = {
        ...form,
        code: form.code.trim().toUpperCase(),
        startsAt: form.startsAt.length === 16 ? `${form.startsAt}:00` : form.startsAt,
        endsAt: form.endsAt.length === 16 ? `${form.endsAt}:00` : form.endsAt,
        maxDiscount: form.maxDiscount || null,
        minOrderAmount: form.minOrderAmount || null,
        usageLimit: form.usageLimit || null,
        perCustomerLimit: form.perCustomerLimit || null,
        productIds: productIds.split(',').map((s) => Number(s.trim())).filter((n) => n > 0),
        categoryIds: categoryIds.split(',').map((s) => Number(s.trim())).filter((n) => n > 0),
      };
      await api.post('/v1/customer-promo-codes', payload);
      setForm(empty());
      setProductIds('');
      setCategoryIds('');
      await load();
      await Swal.fire({ icon: 'success', title: 'သိမ်းပြီးပါပြီ', timer: 1200, showConfirmButton: false });
    } catch (e: any) {
      await Swal.fire({ icon: 'error', title: 'မသိမ်းနိုင်ပါ', text: e?.response?.data?.message || e?.message || '' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold"><Ticket size={22} /> Customer Promo Codes</h1>
          <p className="text-sm text-slate-500">App ကတွက်သော လျှော့ဈေးကို မယုံပါ။ Order တင်ချိန် backend က code ပြန်စစ်ပြီး snapshot သိမ်းသည်။</p>
        </div>
        <button type="button" onClick={() => void load()} className="rounded-xl border px-3 py-2 text-sm font-bold">
          <RefreshCw size={16} className="inline" /> ပြန်ဖတ်
        </button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-2 rounded-xl border bg-white p-4">
          <p className="font-bold">{form.id ? 'ပြင်ဆင်ရန်' : 'Promo အသစ်'}</p>
          <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} placeholder="CODE" className="w-full rounded-lg border px-3 py-2 text-sm" />
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="အမည်" className="w-full rounded-lg border px-3 py-2 text-sm" />
          <div className="grid grid-cols-2 gap-2">
            <input type="datetime-local" value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} className="rounded-lg border px-3 py-2 text-sm" />
            <input type="datetime-local" value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} className="rounded-lg border px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <select value={form.discountType} onChange={(e) => setForm({ ...form, discountType: e.target.value as Promo['discountType'] })} className="rounded-lg border px-3 py-2 text-sm">
              <option value="PERCENT">ရာခိုင်နှုန်း</option>
              <option value="FIXED">ပမာဏသတ်မှတ်</option>
            </select>
            <input type="number" value={form.discountValue} onChange={(e) => setForm({ ...form, discountValue: Number(e.target.value) })} className="rounded-lg border px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <input type="number" value={form.maxDiscount ?? ''} onChange={(e) => setForm({ ...form, maxDiscount: e.target.value ? Number(e.target.value) : null })} placeholder="အများဆုံး လျှော့" className="rounded-lg border px-3 py-2 text-sm" />
            <input type="number" value={form.minOrderAmount ?? ''} onChange={(e) => setForm({ ...form, minOrderAmount: e.target.value ? Number(e.target.value) : null })} placeholder="အနည်းဆုံး မှာယူ" className="rounded-lg border px-3 py-2 text-sm" />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <input type="number" value={form.usageLimit ?? ''} onChange={(e) => setForm({ ...form, usageLimit: e.target.value ? Number(e.target.value) : null })} placeholder="စုစုပေါင်း အကြိမ်" className="rounded-lg border px-3 py-2 text-sm" />
            <input type="number" value={form.perCustomerLimit ?? ''} onChange={(e) => setForm({ ...form, perCustomerLimit: e.target.value ? Number(e.target.value) : null })} placeholder="တစ်ဦးချင်း" className="rounded-lg border px-3 py-2 text-sm" />
          </div>
          <input value={productIds} onChange={(e) => setProductIds(e.target.value)} placeholder="Product IDs (comma) — ဗလာ = အားလုံး" className="w-full rounded-lg border px-3 py-2 text-sm" />
          <input value={categoryIds} onChange={(e) => setCategoryIds(e.target.value)} placeholder="Category IDs (comma)" className="w-full rounded-lg border px-3 py-2 text-sm" />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active
          </label>
          <div className="flex gap-2">
            <button type="button" disabled={saving} onClick={() => void save()} className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white">
              {saving ? <Loader2 className="inline h-4 w-4 animate-spin" /> : <Plus className="inline h-4 w-4" />} သိမ်းမည်
            </button>
            <button type="button" onClick={() => { setForm(empty()); setProductIds(''); setCategoryIds(''); }} className="rounded-xl border px-4 py-2 text-sm">အသစ်</button>
          </div>
        </div>

        <div className="overflow-hidden rounded-xl border bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
              <tr>
                <th className="px-3 py-2 text-left">Code</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Used</th>
                <th className="px-3 py-2">Active</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={4} className="py-10 text-center text-slate-400">Loading...</td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={4} className="py-10 text-center text-slate-400">Promo မရှိသေးပါ</td></tr>
              ) : rows.map((row) => (
                <tr key={row.id} className="cursor-pointer border-t hover:bg-slate-50" onClick={() => edit(row)}>
                  <td className="px-3 py-2 font-bold">{row.code}<div className="text-[11px] font-normal text-slate-500">{row.name}</div></td>
                  <td className="px-3 py-2 text-center">{row.discountType} {row.discountValue}</td>
                  <td className="px-3 py-2 text-center">{row.usedCount || 0}{row.usageLimit ? ` / ${row.usageLimit}` : ''}</td>
                  <td className="px-3 py-2 text-center">{row.active ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default CustomerPromoCodesPage;
