import React, { useEffect, useState, useCallback } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { shelfLocationService } from '../services/shelfLocationApiService';
import { ShelfLocationDTO } from '../types';
import Swal from 'sweetalert2';

const emptyForm: Omit<ShelfLocationDTO, 'id'> = { code: '', label: '', active: true };

const ShelfLocationManagement: React.FC = () => {
  const [locations, setLocations] = useState<ShelfLocationDTO[]>([]);
  const [loading, setLoading]     = useState(true);
  const [search, setSearch]       = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId]       = useState<number | null>(null);
  const [form, setForm]           = useState<Omit<ShelfLocationDTO, 'id'>>(emptyForm);
  const [saving, setSaving]       = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try { setLocations(await shelfLocationService.getAll()); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);
  useDataEvents(['Shelf'], load);

  const filtered = locations.filter(l =>
    l.code.toLowerCase().includes(search.toLowerCase()) ||
    (l.label ?? '').toLowerCase().includes(search.toLowerCase())
  );

  const openAdd = () => { setForm(emptyForm); setEditId(null); setShowModal(true); };
  const openEdit = (loc: ShelfLocationDTO) => {
    setForm({ code: loc.code, label: loc.label ?? '', active: loc.active });
    setEditId(loc.id!);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.code.trim()) { Swal.fire('Error', 'Code ထည့်ပါ (e.g. A-01)', 'error'); return; }
    setSaving(true);
    try {
      const res = editId
        ? await shelfLocationService.update(editId, form)
        : await shelfLocationService.create(form);
      if (res.success) { load(); setShowModal(false); }
      else Swal.fire('Error', res.message, 'error');
    } catch (e: any) { Swal.fire('Error', e?.message ?? 'Failed', 'error'); }
    setSaving(false);
  };

  const handleDelete = async (loc: ShelfLocationDTO) => {
    const c = await Swal.fire({ title: `Delete "${loc.code}"?`, icon: 'warning', showCancelButton: true, confirmButtonColor: '#ef4444' });
    if (!c.isConfirmed) return;
    const res = await shelfLocationService.delete(loc.id!);
    if (res.success) load();
    else Swal.fire('Error', res.message, 'error');
  };

  return (
    <div>
      <div className="bg-white rounded-xl shadow">
        <div className="flex flex-wrap items-center gap-2 px-3 py-2.5 border-b bg-slate-50/60">
          <input value={search} onChange={e => setSearch(e.target.value)}
            placeholder="ကုဒ် သို့ အညွှန်း ရှာပါ..."
            className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-44 focus:ring-2 focus:ring-teal-500 bg-white" />
          <span className="text-sm text-slate-400">{filtered.length} နေရာ</span>
          <button onClick={openAdd}
            className="ml-auto px-4 py-1.5 bg-teal-600 text-white rounded-lg text-sm font-bold hover:bg-teal-700">
            + နေရာအသစ်
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-purple-600 text-left">
              <tr>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ကုဒ်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အညွှန်း / ဖော်ပြချက်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အခြေအနေ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {loading ? (
                <tr><td colSpan={4} className="px-4 py-8 text-center text-slate-400">ဖွင့်နေသည်...</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={4} className="px-4 py-8 text-center text-slate-400">နေရာ ရှာမတွေ့ပါ</td></tr>
              ) : filtered.map(loc => (
                <tr key={loc.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <span className="font-mono font-bold text-teal-700 bg-teal-50 border border-teal-200 rounded px-2 py-0.5">
                      📦 {loc.code}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{loc.label || '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-semibold rounded-full px-2 py-0.5 ${loc.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      {loc.active ? 'အသုံးပြုနေ' : 'ပိတ်ထား'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-3">
                      <button onClick={() => openEdit(loc)} className="text-indigo-600 hover:underline text-xs font-medium">ပြင်ဆင်</button>
                      <button onClick={() => handleDelete(loc)} className="text-red-500 hover:underline text-xs font-medium">ဖျက်မည်</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm flex flex-col">
            <div className="flex items-center justify-between px-5 py-4 border-b">
              <h2 className="font-semibold">{editId ? 'ပြင်ဆင်' : 'အသစ်'} ကန့်တည်နေရာ</h2>
              <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600 text-xl">✕</button>
            </div>
            <div className="p-5 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Code <span className="text-red-500">*</span></label>
                <input value={form.code} onChange={e => setForm(p => ({ ...p, code: e.target.value.toUpperCase() }))}
                  placeholder="e.g. A-01, B-03, SHELF-5"
                  className="w-full border rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-teal-500" />
                <p className="text-xs text-slate-400 mt-1">Unique identifier — uppercase ဖြင့် သိမ်းသည်</p>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">Label / Description</label>
                <input value={form.label ?? ''} onChange={e => setForm(p => ({ ...p, label: e.target.value }))}
                  placeholder="e.g. Row A, Slot 1 — Phones"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500" />
              </div>
              <div className="flex items-center gap-2">
                <input type="checkbox" id="active-chk" checked={form.active}
                  onChange={e => setForm(p => ({ ...p, active: e.target.checked }))}
                  className="w-4 h-4 accent-teal-600" />
                <label htmlFor="active-chk" className="text-sm text-slate-600">Active</label>
              </div>
            </div>
            <div className="flex gap-2 px-5 pb-5">
              <button onClick={() => setShowModal(false)}
                className="flex-1 px-4 py-2 text-sm rounded-lg border hover:bg-slate-50">Cancel</button>
              <button onClick={handleSave} disabled={saving}
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

export default ShelfLocationManagement;
