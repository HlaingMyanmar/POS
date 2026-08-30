import React, { useEffect, useMemo, useState } from 'react';
import { useDataEvents } from '../hooks/useDataEvents';
import { serviceTypeService, serviceItemService, subServiceTypeService, exportService } from '../services/api';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const emptyType = { name: '', description: '', isActive: true };
const emptyItem = { item: '', price: '', costPrice: '', minPrice: '', maxPrice: '', commissionPercent: '0', warrantyMonths: '0', durationMinutes: '0', description: '', focDefault: false, taxRate: '0', skillRequired: '', supportedDeviceTypes: '', defaultRequiredParts: '', serviceTypeId: '', subServiceTypeId: '', isActive: true };
const emptySubType = { name: '', description: '', isActive: true, serviceTypeId: 0 };

const FormSection: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <section className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-3 sm:p-4">
    <h3 className="text-xs font-bold uppercase tracking-wide text-indigo-700">{title}</h3>
    {children}
  </section>
);

const ServiceItemsTable: React.FC<{
  items: any[];
  onEdit: (item: any) => void;
  onDelete: (id: number) => void;
  onHistory: (item: any) => void;
  showType: boolean;
}> = ({ items, onEdit, onDelete, onHistory, showType }) => (
  <div className="overflow-x-auto">
    <table className="w-full min-w-[760px] text-sm">
      <thead className="bg-purple-600 text-left">
        <tr>
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ကုဒ်</th>
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">ဝန်ဆောင်မှုအမည်</th>
          {showType && <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အမျိုးအစား</th>}
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အုပ်စုခွဲ</th>
          <th className="px-4 py-3.5 text-right text-[13px] font-extrabold text-white tracking-wide">ဈေးနှုန်း</th>
          <th className="px-4 py-3.5 text-right text-[13px] font-extrabold text-white tracking-wide">ကုန်ကျ</th>
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အာမခံ</th>
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">အခြေအနေ</th>
          <th className="px-4 py-3.5 text-[13px] font-extrabold text-white tracking-wide">လုပ်ဆောင်ချက်</th>
        </tr>
      </thead>
      <tbody className="divide-y">
        {items.map(item => (
          <tr key={item.id} className="hover:bg-slate-50">
            <td className="px-4 py-3 text-slate-500">{item.code}</td>
            <td className="px-4 py-3 font-medium">{item.item}</td>
            {showType && <td className="px-4 py-3">{item.serviceTypeName || '-'}</td>}
            <td className="px-4 py-3 text-slate-500">{item.subServiceTypeName || '-'}</td>
            <td className="px-4 py-3 text-right">
              <div className="font-semibold text-indigo-700">ပုံမှန် {Number(item.price).toLocaleString()} Ks</div>
              <div className="mt-1 text-xs text-slate-500">
                Min {item.minPrice == null ? '-' : Number(item.minPrice).toLocaleString()} · Max {item.maxPrice == null ? '-' : Number(item.maxPrice).toLocaleString()}
              </div>
            </td>
            <td className="px-4 py-3 text-right text-slate-500">{Number(item.costPrice || 0).toLocaleString()}</td>
            <td className="px-4 py-3 text-slate-500">{item.warrantyMonths ? `${item.warrantyMonths} လ` : '-'}</td>
            <td className="px-4 py-3">
              <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${item.isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                {item.isActive ? 'Active' : 'Inactive'}
              </span>
            </td>
            <td className="px-4 py-3">
              <div className="flex gap-2">
                <button onClick={() => onEdit(item)} className="text-indigo-600 hover:underline text-xs">Edit</button>
                <button onClick={() => onHistory(item)} className="text-amber-600 hover:underline text-xs">History</button>
                <button onClick={() => onDelete(item.id)} className="text-red-500 hover:underline text-xs">Delete</button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

const ServiceManagement: React.FC = () => {
  const [types, setTypes]         = useState<any[]>([]);
  const [items, setItems]         = useState<any[]>([]);
  const [typeTab, setTypeTab]     = useState<'types'|'items'>('items');
  const [itemView, setItemView]   = useState<'grouped'|'all'>('grouped');
  const [itemSearch, setItemSearch] = useState('');
  const [typeForm, setTypeForm]   = useState<any>(emptyType);
  const [itemForm, setItemForm]   = useState<any>(emptyItem);
  const [editTypeId, setEditTypeId] = useState<number|null>(null);
  const [editItemId, setEditItemId] = useState<number|null>(null);
  const [showTypeModal, setShowTypeModal] = useState(false);
  const [showItemModal, setShowItemModal] = useState(false);
  const [priceHistory, setPriceHistory] = useState<any[]>([]);
  const [historyItem, setHistoryItem] = useState<any|null>(null);

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

  const filteredItems = useMemo(() => {
    const query = itemSearch.trim().toLowerCase();
    if (!query) return items;
    return items.filter(item => [item.code, item.item, item.serviceTypeName, item.subServiceTypeName]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(query)));
  }, [items, itemSearch]);

  const groupedItems = useMemo(() => {
    const groups = new Map<string, any[]>();
    filteredItems.forEach(item => {
      const groupName = item.serviceTypeName || 'အမျိုးအစား မသတ်မှတ်ရသေး';
      const group = groups.get(groupName) ?? [];
      group.push(item);
      groups.set(groupName, group);
    });
    return Array.from(groups.entries()).map(([name, groupItems]) => ({ name, items: groupItems }));
  }, [filteredItems]);

  useEffect(() => { loadAll(); }, []);
  useRefreshOnTabActivate(loadAll);
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
      ? { item: row.item, price: row.price, costPrice: row.costPrice ?? '', minPrice: row.minPrice ?? '', maxPrice: row.maxPrice ?? '', commissionPercent: row.commissionPercent ?? 0, warrantyMonths: row.warrantyMonths ?? 0, durationMinutes: row.durationMinutes ?? 0, description: row.description ?? '', focDefault: Boolean(row.focDefault), taxRate: row.taxRate ?? 0, skillRequired: row.skillRequired ?? '', supportedDeviceTypes: row.supportedDeviceTypes ?? '', defaultRequiredParts: row.defaultRequiredParts ?? '', serviceTypeId: row.serviceTypeId, subServiceTypeId: row.subServiceTypeId ?? '', isActive: row.isActive }
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
        costPrice: Number(itemForm.costPrice || 0),
        minPrice: itemForm.minPrice === '' ? null : Number(itemForm.minPrice),
        maxPrice: itemForm.maxPrice === '' ? null : Number(itemForm.maxPrice),
        commissionPercent: Number(itemForm.commissionPercent || 0),
        warrantyMonths: Number(itemForm.warrantyMonths || 0),
        durationMinutes: Number(itemForm.durationMinutes || 0),
        taxRate: Number(itemForm.taxRate || 0),
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

  const viewPriceHistory = async (item: any) => {
    try {
      const res = await serviceItemService.getPriceHistory(item.id);
      if (!res.success) return Swal.fire('Error', res.message, 'error');
      setPriceHistory(res.data ?? []);
      setHistoryItem(item);
    } catch {
      Swal.fire('Error', 'ဈေးနှုန်းမှတ်တမ်း ဖတ်မရပါ', 'error');
    }
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
            <div>
              <span className="font-medium text-slate-700">ဝန်ဆောင်မှုများ</span>
              <p className="mt-0.5 text-xs text-slate-400">အမျိုးအစားအလိုက် စုစည်းပြထားသည်</p>
            </div>
            <div className="flex flex-wrap items-center justify-end gap-2">
              <input
                value={itemSearch}
                onChange={event => setItemSearch(event.target.value)}
                placeholder="ဝန်ဆောင်မှု ရှာရန်..."
                className="w-52 rounded-lg border border-slate-200 px-3 py-1.5 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
              />
              <div className="flex rounded-lg border border-slate-200 bg-white p-0.5 text-xs font-bold">
                <button onClick={() => setItemView('grouped')}
                  className={`rounded-md px-3 py-1.5 ${itemView === 'grouped' ? 'bg-indigo-600 text-white' : 'text-slate-500 hover:bg-slate-100'}`}>
                  အုပ်စုလိုက်
                </button>
                <button onClick={() => setItemView('all')}
                  className={`rounded-md px-3 py-1.5 ${itemView === 'all' ? 'bg-indigo-600 text-white' : 'text-slate-500 hover:bg-slate-100'}`}>
                  အားလုံး
                </button>
              </div>
              <button onClick={() => openItemModal()}
                className="px-3 py-1.5 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700">+ ထည့်မည်</button>
            </div>
          </div>
          {filteredItems.length === 0 ? (
            <p className="px-4 py-10 text-center text-sm text-slate-400">ရှာဖွေမှုနှင့် ကိုက်ညီသော ဝန်ဆောင်မှု မတွေ့ပါ</p>
          ) : itemView === 'grouped' ? groupedItems.map(group => (
            <section key={group.name} className="border-b last:border-b-0">
              <div className="flex items-center justify-between bg-indigo-50/60 px-4 py-3">
                <div className="flex items-center gap-2">
                  <span className="grid h-8 w-8 place-items-center rounded-lg bg-indigo-600 text-sm font-black text-white">{group.name.charAt(0)}</span>
                  <h3 className="font-bold text-indigo-900">{group.name}</h3>
                </div>
                <span className="rounded-full bg-white px-2.5 py-1 text-xs font-bold text-indigo-700">{group.items.length} ခု</span>
              </div>
              <ServiceItemsTable items={group.items} onEdit={openItemModal} onDelete={deleteItem} onHistory={viewPriceHistory} showType={false} />
            </section>
          )) : <ServiceItemsTable items={filteredItems} onEdit={openItemModal} onDelete={deleteItem} onHistory={viewPriceHistory} showType />}
        </div>
      )}
      </div>{/* end card wrapper */}

      {/* Type Modal */}
      {showTypeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-white shadow-xl sm:h-auto sm:max-h-[90vh] sm:max-w-2xl sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between border-b px-4 py-3 sm:px-6">
              <h2 className="text-lg font-semibold">{editTypeId ? 'ဝန်ဆောင်မှုအမျိုးအစား ပြင်ဆင်ရန်' : 'ဝန်ဆောင်မှုအမျိုးအစား အသစ်ထည့်ရန်'}</h2>
              <button type="button" onClick={() => setShowTypeModal(false)} aria-label="ပိတ်ရန်" className="flex h-10 w-10 items-center justify-center rounded-full text-xl text-slate-500 hover:bg-slate-100">✕</button>
            </div>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 sm:p-6">
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">အမည်</span>
                <input placeholder="အမျိုးအစားအမည်" value={typeForm.name}
                  onChange={e => setTypeForm((p: any) => ({ ...p, name: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" />
              </label>
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">ဖော်ပြချက်</span>
                <textarea placeholder="ဖော်ပြချက်" value={typeForm.description}
                  onChange={e => setTypeForm((p: any) => ({ ...p, description: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" rows={3} />
              </label>
              {editTypeId && (
                <label className="flex min-h-12 items-center gap-2 text-sm">
                  <input type="checkbox" checked={typeForm.isActive}
                    onChange={e => setTypeForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                    className="w-4 h-4 rounded" />
                  အသုံးပြုနေဆဲ
                </label>
              )}
            </div>
            <div className="flex shrink-0 flex-col-reverse gap-2 border-t px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:px-6">
              <button type="button" onClick={() => setShowTypeModal(false)} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold sm:w-auto">မလုပ်တော့ပါ</button>
              <button type="button" onClick={saveType} disabled={savingType} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white disabled:opacity-60 sm:w-auto">{savingType ? 'သိမ်းနေသည်...' : 'သိမ်းဆည်းမည်'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Sub Type Modal */}
      {showSubModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-white shadow-xl sm:h-auto sm:max-h-[90vh] sm:max-w-2xl sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between border-b px-4 py-3 sm:px-6">
              <h2 className="text-lg font-semibold">{editSubId ? 'အမျိုးအစားခွဲ ပြင်ဆင်ရန်' : 'အမျိုးအစားခွဲ အသစ်ထည့်ရန်'}</h2>
              <button type="button" onClick={() => setShowSubModal(false)} aria-label="ပိတ်ရန်" className="flex h-10 w-10 items-center justify-center rounded-full text-xl text-slate-500 hover:bg-slate-100">✕</button>
            </div>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 sm:p-6">
              <p className="text-xs text-slate-500">
                ပင်မအမျိုးအစား: <span className="font-medium text-slate-700">
                  {types.find(t => t.id === subParentTypeId)?.name}
                </span>
              </p>
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">အမည်</span>
                <input placeholder="အမျိုးအစားခွဲအမည်" value={subForm.name}
                  onChange={e => setSubForm((p: any) => ({ ...p, name: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" />
              </label>
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">ဖော်ပြချက်</span>
                <textarea placeholder="ဖော်ပြချက်" value={subForm.description}
                  onChange={e => setSubForm((p: any) => ({ ...p, description: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" rows={3} />
              </label>
              {editSubId && (
                <label className="flex min-h-12 items-center gap-2 text-sm">
                  <input type="checkbox" checked={subForm.isActive}
                    onChange={e => setSubForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                    className="w-4 h-4 rounded" />
                  အသုံးပြုနေဆဲ
                </label>
              )}
            </div>
            <div className="flex shrink-0 flex-col-reverse gap-2 border-t px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:px-6">
              <button type="button" onClick={() => setShowSubModal(false)} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold sm:w-auto">မလုပ်တော့ပါ</button>
              <button type="button" onClick={saveSub} disabled={savingSub} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white disabled:opacity-60 sm:w-auto">{savingSub ? 'သိမ်းနေသည်...' : 'သိမ်းဆည်းမည်'}</button>
            </div>
          </div>
        </div>
      )}

      {historyItem && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[80vh] overflow-auto">
            <div className="sticky top-0 flex items-center justify-between border-b bg-white px-5 py-4">
              <div><h2 className="font-semibold text-lg">ဈေးနှုန်းမှတ်တမ်း</h2><p className="text-xs text-slate-500">{historyItem.code} - {historyItem.item}</p></div>
              <button onClick={() => setHistoryItem(null)} className="rounded-lg border px-3 py-1.5 text-sm">ပိတ်ရန်</button>
            </div>
            {priceHistory.length === 0 ? <p className="p-8 text-center text-sm text-slate-400">ဈေးနှုန်းပြောင်းလဲမှု မရှိသေးပါ။</p> : (
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left"><tr><th className="px-4 py-3">ပြောင်းသည့်အချိန်</th><th className="px-4 py-3">ဈေးနှုန်း</th><th className="px-4 py-3">ကုန်ကျ</th><th className="px-4 py-3">ပြောင်းသူ</th></tr></thead>
                <tbody className="divide-y">{priceHistory.map(row => (
                  <tr key={row.id}><td className="px-4 py-3">{row.changedAt ? new Date(row.changedAt).toLocaleString() : '-'}</td><td className="px-4 py-3">{Number(row.oldPrice || 0).toLocaleString()} → {Number(row.newPrice || 0).toLocaleString()}</td><td className="px-4 py-3">{Number(row.oldCost || 0).toLocaleString()} → {Number(row.newCost || 0).toLocaleString()}</td><td className="px-4 py-3">{row.changedBy || 'SYSTEM'}</td></tr>
                ))}</tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* Item Modal */}
      {showItemModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-0 sm:p-4">
          <div className="flex h-[100dvh] w-full flex-col overflow-hidden bg-white shadow-xl sm:h-auto sm:max-h-[90vh] sm:max-w-3xl sm:rounded-2xl">
            <div className="flex shrink-0 items-center justify-between border-b bg-white px-4 py-3 sm:px-6 sm:py-4">
              <div>
                <h2 className="text-lg font-bold text-slate-800 sm:text-xl">{editItemId ? 'ဝန်ဆောင်မှု ပြင်ဆင်ရန်' : 'ဝန်ဆောင်မှု အသစ်ထည့်ရန်'}</h2>
                <p className="mt-0.5 text-xs text-slate-500">အခြေခံအချက်အလက်၊ စျေးနှုန်း၊ အာမခံနှင့် ကိရိယာသတ်မှတ်ချက် ထည့်ပါ</p>
              </div>
              <button type="button" onClick={() => setShowItemModal(false)} aria-label="ပိတ်ရန်" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-xl text-slate-500 hover:bg-slate-100">✕</button>
            </div>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto overscroll-contain p-4 pb-24 sm:p-6 sm:pb-6 [&_input]:min-h-11 [&_select]:min-h-11">
            <FormSection title="အခြေခံအချက်အလက်">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">ဝန်ဆောင်မှုအမျိုးအစား</span>
                  <select value={itemForm.serviceTypeId}
                    onChange={e => {
                      const val = e.target.value;
                      setItemForm((p: any) => ({ ...p, serviceTypeId: val, subServiceTypeId: '' }));
                      loadItemSubTypes(val);
                    }}
                    className="w-full rounded-lg border bg-white px-3 py-2 text-sm">
                    <option value="">— အမျိုးအစားရွေးပါ —</option>
                    {types.filter(t => t.isActive).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                  </select>
                </label>
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အမျိုးအစားခွဲ</span>
                  <select value={itemForm.subServiceTypeId}
                    disabled={itemSubTypes.length === 0}
                    onChange={e => setItemForm((p: any) => ({ ...p, subServiceTypeId: e.target.value }))}
                    className="w-full rounded-lg border bg-white px-3 py-2 text-sm disabled:bg-slate-100">
                    <option value="">— မသတ်မှတ်ထား —</option>
                    {itemSubTypes.map((subItem: any) => <option key={subItem.id} value={subItem.id}>{subItem.name}</option>)}
                  </select>
                </label>
              </div>
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">ဝန်ဆောင်မှုအမည်</span>
                <input placeholder="ဥပမာ - Storage Health Check" value={itemForm.item}
                  onChange={e => setItemForm((p: any) => ({ ...p, item: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" />
              </label>
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">ဖော်ပြချက်</span>
                <textarea placeholder="ဝန်ဆောင်မှုအသေးစိတ်..." value={itemForm.description}
                  onChange={e => setItemForm((p: any) => ({ ...p, description: e.target.value }))}
                  className="w-full resize-y rounded-lg border px-3 py-2 text-sm" rows={3} />
              </label>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <label className="flex min-h-12 items-center gap-3 rounded-xl border bg-white px-3 text-sm">
                  <input type="checkbox" checked={Boolean(itemForm.focDefault)}
                    onChange={e => setItemForm((p: any) => ({ ...p, focDefault: e.target.checked }))} />
                  <span><strong className="block text-slate-700">ပုံမှန်အခမဲ့ (FOC)</strong><span className="text-xs text-slate-500">အာမခံ / အခမဲ့ဝန်ဆောင်မှု</span></span>
                </label>
                {editItemId && (
                  <label className="flex min-h-12 items-center gap-3 rounded-xl border bg-white px-3 text-sm">
                    <input type="checkbox" checked={itemForm.isActive}
                      onChange={e => setItemForm((p: any) => ({ ...p, isActive: e.target.checked }))}
                      className="h-4 w-4 rounded" />
                    <span><strong className="block text-slate-700">အသုံးပြုနေဆဲ</strong><span className="text-xs text-slate-500">Booking / Job တွင် ရွေးချယ်နိုင်မည်</span></span>
                  </label>
                )}
              </div>
            </FormSection>

            <FormSection title="စျေးနှုန်း">
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">ပုံမှန်ရောင်းဈေး (Ks)</span>
                <input type="number" min="0" placeholder="0" value={itemForm.price}
                  onChange={e => setItemForm((p: any) => ({ ...p, price: e.target.value }))}
                  className="w-full border rounded-lg px-3 py-2 text-sm" />
              </label>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အနည်းဆုံးဈေး (Ks)</span>
                  <input type="number" min="0" placeholder="မသတ်မှတ်ထား" value={itemForm.minPrice}
                    onChange={e => setItemForm((p: any) => ({ ...p, minPrice: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အများဆုံးဈေး (Ks)</span>
                  <input type="number" min="0" placeholder="မသတ်မှတ်ထား" value={itemForm.maxPrice}
                    onChange={e => setItemForm((p: any) => ({ ...p, maxPrice: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">ဆိုင်ဘက်ကုန်ကျစရိတ် (Ks)</span>
                  <input type="number" min="0" placeholder="0" value={itemForm.costPrice}
                    onChange={e => setItemForm((p: any) => ({ ...p, costPrice: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အခွန်နှုန်း (%)</span>
                  <input type="number" min="0" max="100" placeholder="0" value={itemForm.taxRate}
                    onChange={e => setItemForm((p: any) => ({ ...p, taxRate: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
                <label className="block space-y-1 sm:col-span-2">
                  <span className="text-xs font-semibold text-slate-600">ဝန်ထမ်းကော်မရှင် (%)</span>
                  <input type="number" min="0" max="100" placeholder="0" value={itemForm.commissionPercent}
                    onChange={e => setItemForm((p: any) => ({ ...p, commissionPercent: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
              </div>
            </FormSection>

            <FormSection title="အာမခံ / ကြာချိန်">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အာမခံကာလ (လ)</span>
                  <input type="number" min="0" placeholder="0" value={itemForm.warrantyMonths}
                    onChange={e => setItemForm((p: any) => ({ ...p, warrantyMonths: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">ခန့်မှန်းကြာချိန် (မိနစ်)</span>
                  <input type="number" min="0" placeholder="0" value={itemForm.durationMinutes}
                    onChange={e => setItemForm((p: any) => ({ ...p, durationMinutes: e.target.value }))}
                    className="w-full border rounded-lg px-3 py-2 text-sm" />
                </label>
              </div>
            </FormSection>

            <FormSection title="ကိရိယာ / အပိုပစ္စည်း သတ်မှတ်ချက်">
              <label className="block space-y-1">
                <span className="text-xs font-semibold text-slate-600">လိုအပ်သောကျွမ်းကျင်မှု</span>
                <input placeholder="ဥပမာ - Hardware Technician" value={itemForm.skillRequired}
                  onChange={e => setItemForm((p: any) => ({ ...p, skillRequired: e.target.value }))}
                  className="w-full rounded-lg border px-3 py-2 text-sm" />
              </label>
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">အသုံးပြုနိုင်သောပစ္စည်းအမျိုးအစားများ</span>
                  <textarea placeholder="Phone, Laptop, HDD, SSD" value={itemForm.supportedDeviceTypes}
                    onChange={e => setItemForm((p: any) => ({ ...p, supportedDeviceTypes: e.target.value }))}
                    className="w-full resize-y rounded-lg border px-3 py-2 text-sm" rows={3} />
                  <span className="block text-[11px] text-slate-400">ကော်မာဖြင့် ခွဲရေးပါ။ Booking / Job စစ်ထုတ်ရာတွင် သုံးမည်။</span>
                </label>
                <label className="block space-y-1">
                  <span className="text-xs font-semibold text-slate-600">ပုံမှန်လိုအပ်နိုင်သော အပိုပစ္စည်းများ</span>
                  <textarea placeholder={'SSD\nSATA Cable\nDrive Caddy'} value={itemForm.defaultRequiredParts}
                    onChange={e => setItemForm((p: any) => ({ ...p, defaultRequiredParts: e.target.value }))}
                    className="w-full resize-y rounded-lg border px-3 py-2 text-sm" rows={3} />
                  <span className="block text-[11px] text-slate-400">အပိုပစ္စည်း တစ်ခုစီကို တစ်ကြောင်းစီ ရေးပါ။</span>
                </label>
              </div>
            </FormSection>
            </div>
            <div className="flex shrink-0 flex-col-reverse gap-2 border-t bg-white px-4 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:flex-row sm:justify-end sm:px-6">
              <button type="button" onClick={() => setShowItemModal(false)} className="min-h-11 w-full rounded-xl border px-4 text-sm font-semibold text-slate-600 hover:bg-slate-50 sm:w-auto">မလုပ်တော့ပါ</button>
              <button type="button" onClick={saveItem} disabled={savingItem} className="min-h-11 w-full rounded-xl bg-indigo-600 px-5 text-sm font-bold text-white hover:bg-indigo-700 disabled:opacity-60 sm:w-auto">{savingItem ? 'သိမ်းနေသည်...' : 'သိမ်းဆည်းမည်'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ServiceManagement;
