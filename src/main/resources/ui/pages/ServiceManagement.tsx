import React, { useEffect, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { serviceTypeService, serviceItemService, subServiceTypeService, exportService } from '../services/api';
import Swal from 'sweetalert2';

const emptyType = { name: '', description: '', isActive: true };
const emptyItem = { item: '', price: '', serviceTypeId: '', subServiceTypeId: '', isActive: true };
const emptySubType = { name: '', description: '', isActive: true, serviceTypeId: 0 };

const ServiceManagement: React.FC = () => {
  const [types, setTypes]         = useState<any[]>([]);
  const [items, setItems]         = useState<any[]>([]);
  const [typeTab, setTypeTab]     = useState<'types'|'items'>('items');
  const [typeForm, setTypeForm]   = useState<any>(emptyType);
  const [itemForm, setItemForm]   = useState<any>(emptyItem);
  const [editTypeId, setEditTypeId] = useState<number|null>(null);
  const [editItemId, setEditItemId] = useState<number|null>(null);
  const [showTypeModal, setShowTypeModal] = useState(false);
  const [showItemModal, setShowItemModal] = useState(false);

  // Sub types for item form dropdown
  const [itemSubTypes, setItemSubTypes] = useState<any[]>([]);

  // Sub service type state
  const [expandedTypeId, setExpandedTypeId]   = useState<number|null>(null);
  const [subTypes, setSubTypes]               = useState<Record<number, any[]>>({});
  const [showSubModal, setShowSubModal]       = useState(false);
  const [subForm, setSubForm]                 = useState<any>(emptySubType);
  const [editSubId, setEditSubId]             = useState<number|null>(null);
  const [subParentTypeId, setSubParentTypeId] = useState<number|null>(null);
  const [savingType, setSavingType]   = useState(false);
  const [savingSub, setSavingSub]     = useState(false);
  const [savingItem, setSavingItem]   = useState(false);

  const loadAll = async () => {
    const [t, s] = await Promise.all([serviceTypeService.getAll(), serviceItemService.getAll()]);
    if (t.success) setTypes(t.data ?? []);
    if (s.success) setItems(s.data ?? []);
  };

  useEffect(() => { loadAll(); }, []);
  useDataEvents(['Service'], loadAll);

  // ── Service Types ──────────────────────────────────────
  const openTypeModal = (row?: any) => {
    setTypeForm(row ? { name: row.name, description: row.description, isActive: row.isActive } : emptyType);
    setEditTypeId(row?.id ?? null);
    setShowTypeModal(true);
  };

  const saveType = async () => {
    if (savingType) return;
    setSavingType(true);
    try {
      const res = editTypeId
        ? await serviceTypeService.update(editTypeId, typeForm)
        : await serviceTypeService.create(typeForm);
      if (res.success) { loadAll(); setShowTypeModal(false); }
      else Swal.fire('Error', res.message, 'error');
    } catch { Swal.fire('Error', 'Failed', 'error'); } finally { setSavingType(false); }
  };

  const deleteType = async (id: number) => {
    const c = await Swal.fire({ title: 'Delete?', icon: 'warning', showCancelButton: true });
    if (c.isConfirmed) { await serviceTypeService.remove(id); loadAll(); }
  };

  // ── Sub Service Types ──────────────────────────────────
  const toggleExpand = async (typeId: number) => {
    if (expandedTypeId === typeId) {
      setExpandedTypeId(null);
      return;
    }
    setExpandedTypeId(typeId);
    if (!subTypes[typeId]) {
      const res = await subServiceTypeService.getByType(typeId);
      if (res.success) setSubTypes(prev => ({ ...prev, [typeId]: res.data ?? [] }));
    }
  };

  const reloadSubTypes = async (typeId: number) => {
    const res = await subServiceTypeService.getByType(typeId);
    if (res.success) setSubTypes(prev => ({ ...prev, [typeId]: res.data ?? [] }));
  };

  const openSubModal = (parentTypeId: number, row?: any) => {
    setSubParentTypeId(parentTypeId);
    setSubForm(row
      ? { name: row.name, description: row.description, isActive: row.isActive, serviceTypeId: parentTypeId }
      : { ...emptySubType, serviceTypeId: parentTypeId });
    setEditSubId(row?.id ?? null);
    setShowSubModal(true);
  };

  const saveSub = async () => {
    if (savingSub || !subParentTypeId) return;
    setSavingSub(true);
    try {
      const res = editSubId
        ? await subServiceTypeService.update(editSubId, subForm)
        : await subServiceTypeService.create(subForm);
      if (res.success) {
        await reloadSubTypes(subParentTypeId);
        setShowSubModal(false);
      } else Swal.fire('Error', res.message, 'error');
    } catch { Swal.fire('Error', 'Failed', 'error'); } finally { setSavingSub(false); }
  };

  const deleteSub = async (id: number, parentTypeId: number) => {
    const c = await Swal.fire({ title: 'Delete?', icon: 'warning', showCancelButton: true });
    if (c.isConfirmed) {
      await subServiceTypeService.remove(id);
      await reloadSubTypes(parentTypeId);
    }
  };

  // ── Service Items ──────────────────────────────────────
  const loadItemSubTypes = async (typeId: number | string) => {
    if (!typeId) { setItemSubTypes([]); return; }
    const res = await subServiceTypeService.getActiveByType(Number(typeId));
    if (res.success) setItemSubTypes(res.data ?? []);
  };

  const openItemModal = async (row?: any) => {
    const form = row
      ? { item: row.item, price: row.price, serviceTypeId: row.serviceTypeId, subServiceTypeId: row.subServiceTypeId ?? '', isActive: row.isActive }
      : emptyItem;
    setItemForm(form);
    setEditItemId(row?.id ?? null);
    if (row?.serviceTypeId) await loadItemSubTypes(row.serviceTypeId);
    else setItemSubTypes([]);
    setShowItemModal(true);
  };

  const saveItem = async () => {
    if (savingItem) return;
    setSavingItem(true);
    try {
      const payload = {
        ...itemForm,
        price: Number(itemForm.price),
        serviceTypeId: Number(itemForm.serviceTypeId),
        subServiceTypeId: itemForm.subServiceTypeId ? Number(itemForm.subServiceTypeId) : null,
      };
      const res = editItemId
        ? await serviceItemService.update(editItemId, payload)
        : await serviceItemService.create(payload);
      if (res.success) { loadAll(); setShowItemModal(false); }
      else Swal.fire('Error', res.message, 'error');
    } catch { Swal.fire('Error', 'Failed', 'error'); } finally { setSavingItem(false); }
  };

  const deleteItem = async (id: number) => {
    const c = await Swal.fire({ title: 'Delete?', icon: 'warning', showCancelButton: true });
    if (c.isConfirmed) { await serviceItemService.remove(id); loadAll(); }
  };

  return (
    <div>
      <div className="bg-white rounded-xl shadow overflow-hidden">
        {/* Tabs + Export toolbar */}
        <div className="flex flex-wrap items-center gap-2 px-3 py-2 border-b bg-slate-50/60">
          {(['items','types'] as const).map(t => (
            <button key={t} onClick={() => setTypeTab(t)}
              className={`px-4 py-1.5 text-sm font-bold rounded-lg transition-colors ${
                typeTab === t ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-500 hover:bg-slate-200'}`}>
              {t === 'items' ? 'ဝန်ဆောင်မှုများ' : 'ဝန်ဆောင်မှု အမျိုးအစား'}
            </button>
          ))}
          <a href={exportService.services()} target="_blank" rel="noreferrer"
            className="ml-auto px-4 py-1.5 bg-emerald-600 text-white rounded-lg text-sm font-bold hover:bg-emerald-700">
            Export Excel
          </a>
        </div>

      {/* Service Types Tab */}
      {typeTab === 'types' && (
        <div>
          <div className="flex items-center justify-between p-4 border-b">
            <span className="font-medium text-slate-700">ဝန်ဆောင်မှု အမျိုးအစား</span>
            <button onClick={() => openTypeModal()}
              className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700">+ ထည့်မည်</button>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-purple-600 text-left">
              <tr>
                <th className="px-4 py-3.5 w-8"></th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အမည်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ဖော်ပြချက်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အခြေအနေ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {types.map(t => (
                <React.Fragment key={t.id}>
                  <tr className="hover:bg-slate-50">
                    <td className="px-4 py-3">
                      <button onClick={() => toggleExpand(t.id)}
                        className="text-slate-400 hover:text-indigo-600 transition-transform"
                        style={{ transform: expandedTypeId === t.id ? 'rotate(90deg)' : 'rotate(0deg)', display: 'inline-block' }}>
                        ▶
                      </button>
                    </td>
                    <td className="px-4 py-3 font-medium">{t.name}</td>
                    <td className="px-4 py-3 text-slate-500">{t.description || '-'}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${t.isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                        {t.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3 flex gap-2">
                      <button onClick={() => openTypeModal(t)} className="text-indigo-600 hover:underline text-xs">Edit</button>
                      <button onClick={() => deleteType(t.id)} className="text-red-500 hover:underline text-xs">Delete</button>
                    </td>
                  </tr>

                  {/* Sub Service Types expanded row */}
                  {expandedTypeId === t.id && (
                    <tr>
                      <td colSpan={5} className="bg-slate-50 px-8 py-3">
                        <div className="border rounded-lg overflow-hidden">
                          <div className="flex items-center justify-between px-4 py-2 bg-indigo-50 border-b">
                            <span className="text-xs font-semibold text-indigo-700 uppercase tracking-wide">
                              Sub Types — {t.name}
                            </span>
                            <button onClick={() => openSubModal(t.id)}
                              className="px-2.5 py-1 bg-indigo-600 text-white rounded text-xs hover:bg-indigo-700">
                              + Add Sub Type
                            </button>
                          </div>
                          {(subTypes[t.id] ?? []).length === 0 ? (
                            <p className="text-xs text-slate-400 px-4 py-3">No sub types yet.</p>
                          ) : (
                            <table className="w-full text-xs">
                              <thead className="bg-white text-slate-500 text-left border-b">
                                <tr>
                                  <th className="px-4 py-2">Name</th>
                                  <th className="px-4 py-2">Description</th>
                                  <th className="px-4 py-2">Status</th>
                                  <th className="px-4 py-2">Actions</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y bg-white">
                                {(subTypes[t.id] ?? []).map((sub: any) => (
                                  <tr key={sub.id} className="hover:bg-slate-50">
                                    <td className="px-4 py-2 font-medium">{sub.name}</td>
                                    <td className="px-4 py-2 text-slate-500">{sub.description || '-'}</td>
                                    <td className="px-4 py-2">
                                      <span className={`px-2 py-0.5 rounded-full font-medium ${sub.isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                                        {sub.isActive ? 'Active' : 'Inactive'}
                                      </span>
                                    </td>
                                    <td className="px-4 py-2 flex gap-2">
                                      <button onClick={() => openSubModal(t.id, sub)} className="text-indigo-600 hover:underline">Edit</button>
                                      <button onClick={() => deleteSub(sub.id, t.id)} className="text-red-500 hover:underline">Delete</button>
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Services Tab */}
      {typeTab === 'items' && (
        <div>
          <div className="flex items-center justify-between p-4 border-b">
            <span className="font-medium text-slate-700">ဝန်ဆောင်မှုများ</span>
            <button onClick={() => openItemModal()}
              className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700">+ ထည့်မည်</button>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-purple-600 text-left">
              <tr>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ကုဒ်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ဝန်ဆောင်မှုအမည်</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အမျိုးအစား</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အုပ်စုခွဲ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide text-right">ဈေးနှုန်း</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အခြေအနေ</th>
                <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လုပ်ဆောင်ချက်</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {items.map(s => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-500">{s.code}</td>
                  <td className="px-4 py-3 font-medium">{s.item}</td>
                  <td className="px-4 py-3">{s.serviceTypeName}</td>
                  <td className="px-4 py-3 text-slate-500">{s.subServiceTypeName || '-'}</td>
                  <td className="px-4 py-3 text-right">{Number(s.price).toLocaleString()}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${s.isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      {s.isActive ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3 flex gap-2">
                    <button onClick={() => openItemModal(s)} className="text-indigo-600 hover:underline text-xs">Edit</button>
                    <button onClick={() => deleteItem(s.id)} className="text-red-500 hover:underline text-xs">Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      </div>{/* end card wrapper */}

      {/* Type Modal */}
      {showTypeModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-md space-y-4">
            <h2 className="font-semibold text-lg">{editTypeId ? 'Edit' : 'Add'} Service Type</h2>
            <input placeholder="Name" value={typeForm.name}
              onChange={e => setTypeForm((p: any) => ({ ...p, name: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" />
            <textarea placeholder="Description" value={typeForm.description}
              onChange={e => setTypeForm((p: any) => ({ ...p, description: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" rows={3} />
            {editTypeId && (
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={typeForm.isActive}
                  onChange={e => setTypeForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                  className="w-4 h-4 rounded" />
                Active
              </label>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowTypeModal(false)} className="px-4 py-2 text-sm rounded-lg border hover:bg-slate-50">Cancel</button>
              <button onClick={saveType} disabled={savingType} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-60">{savingType ? 'Saving...' : 'Save'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Sub Type Modal */}
      {showSubModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-md space-y-4">
            <h2 className="font-semibold text-lg">{editSubId ? 'Edit' : 'Add'} Sub Service Type</h2>
            <p className="text-xs text-slate-500">
              Parent: <span className="font-medium text-slate-700">
                {types.find(t => t.id === subParentTypeId)?.name}
              </span>
            </p>
            <input placeholder="Name" value={subForm.name}
              onChange={e => setSubForm((p: any) => ({ ...p, name: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" />
            <textarea placeholder="Description" value={subForm.description}
              onChange={e => setSubForm((p: any) => ({ ...p, description: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" rows={3} />
            {editSubId && (
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={subForm.isActive}
                  onChange={e => setSubForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                  className="w-4 h-4 rounded" />
                Active
              </label>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowSubModal(false)} className="px-4 py-2 text-sm rounded-lg border hover:bg-slate-50">Cancel</button>
              <button onClick={saveSub} disabled={savingSub} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-60">{savingSub ? 'Saving...' : 'Save'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Item Modal */}
      {showItemModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-md space-y-4">
            <h2 className="font-semibold text-lg">{editItemId ? 'Edit' : 'Add'} Service</h2>
            <select value={itemForm.serviceTypeId}
              onChange={e => {
                const val = e.target.value;
                setItemForm((p: any) => ({ ...p, serviceTypeId: val, subServiceTypeId: '' }));
                loadItemSubTypes(val);
              }}
              className="w-full border rounded-lg px-3 py-2 text-sm">
              <option value="">Select Type</option>
              {types.filter(t => t.isActive).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
            {itemSubTypes.length > 0 && (
              <select value={itemForm.subServiceTypeId}
                onChange={e => setItemForm((p: any) => ({ ...p, subServiceTypeId: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2 text-sm">
                <option value="">Sub Category (Optional)</option>
                {itemSubTypes.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            )}
            <input placeholder="Service Name" value={itemForm.item}
              onChange={e => setItemForm((p: any) => ({ ...p, item: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" />
            <input type="number" placeholder="Price" value={itemForm.price}
              onChange={e => setItemForm((p: any) => ({ ...p, price: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm" />
            {editItemId && (
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={itemForm.isActive}
                  onChange={e => setItemForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                  className="w-4 h-4 rounded" />
                Active
              </label>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowItemModal(false)} className="px-4 py-2 text-sm rounded-lg border hover:bg-slate-50">Cancel</button>
              <button onClick={saveItem} disabled={savingItem} className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-60">{savingItem ? 'Saving...' : 'Save'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ServiceManagement;
