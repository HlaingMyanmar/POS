
import React, { useEffect, useState, useCallback } from 'react';
import { supplierService } from '../services/supplierapiservice';
import { SupplierDTO } from '../types';
import { 
  Loader2, Plus, Search, Trash2, Edit2, X, 
  Truck, Phone, MapPin,
  ChevronLeft, ChevronRight, ChevronDown, Save,
  ClipboardList, RefreshCw, DollarSign, AlertCircle
} from 'lucide-react';
import { useDataEvents } from '../hooks/useDataEvents';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';
import { useBulkSelection } from '../hooks/useBulkSelection';
import { BulkSelectionToolbar } from '../components/BulkSelectionToolbar';
import { Download } from 'lucide-react';

const SupplierManagement: React.FC = () => {
  const [suppliers, setSuppliers] = useState<SupplierDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  
  const [currentPage, setCurrentPage] = useState(0); 
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState<SupplierDTO | null>(null);
  const [formData, setFormData] = useState<Partial<SupplierDTO>>({
    name: '',
    phone: '',
    address: '',
    openingBalance: 0,
    defaultCreditDays: 30,
    creditLimit: 0
  });
  const [saving, setSaving] = useState(false);
  const bulk = useBulkSelection<SupplierDTO>(suppliers);

  const fetchPaginatedData = useCallback(async () => {
    try {
      const pageData = await supplierService.getPaginated(currentPage, itemsPerPage);
      setSuppliers(Array.isArray(pageData?.content) ? pageData.content : []);
      setTotalElements(Number(pageData?.totalElements) || 0);
      setTotalPages(Number(pageData?.totalPages) || 0);
    } catch (error) {
      console.error("Failed to load supplier data", error);
    } finally {
      setLoading(false);
    }
  }, [currentPage, itemsPerPage]);

  useEffect(() => {
    fetchPaginatedData();
  }, [fetchPaginatedData]);
  useRefreshOnTabActivate(fetchPaginatedData);

  useDataEvents(['Supplier', 'Purchase'], fetchPaginatedData);

  const handleOpenModal = (supplier?: SupplierDTO) => {
    if (supplier) {
      setEditingSupplier(supplier);
      setFormData({
        name: supplier.name,
        phone: supplier.phone || '',
        address: supplier.address || '',
        openingBalance: supplier.openingBalance,
        defaultCreditDays: supplier.defaultCreditDays ?? 30,
        creditLimit: supplier.creditLimit ?? 0
      });
    } else {
      setEditingSupplier(null);
      setFormData({
        name: '',
        phone: '',
        address: '',
        openingBalance: 0,
        defaultCreditDays: 30,
        creditLimit: 0
      });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingSupplier) {
        await supplierService.update(editingSupplier.id, formData);
      } else {
        await supplierService.create(formData);
      }
      setIsModalOpen(false);
      await fetchPaginatedData();
      Swal.fire({ icon: 'success', title: 'Supplier Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Operation failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Remove Supplier?',
      text: "Warning: This action is permanent. Suppliers with outstanding balances cannot be deleted.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, Delete'
    });

    if (result.isConfirmed) {
      try {
        await supplierService.delete(id);
        fetchPaginatedData();
        Swal.fire({ icon: 'success', title: 'Deleted Successfully', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Restricted', error.message || 'Check outstanding balance before deletion.', 'error');
      }
    }
  };

  const handleSearch = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setSearchTerm(val);
    if (val.trim() === '') {
      fetchPaginatedData();
    } else if (val.length >= 2) {
      try {
        const results = await supplierService.search(val);
        setSuppliers(results);
        setTotalPages(1);
      } catch (err) {}
    }
  };

  const handleBulkAction = (action: { key: string }) => {
    if (action.key !== 'export') return;
    const csv = [
      ['Code', 'Name', 'Phone', 'Address', 'Current Balance'],
      ...bulk.selectedRows.map((supplier) => [supplier.code, supplier.name, supplier.phone || '', supplier.address || '', String(supplier.currentBalance ?? 0)])
    ].map((row) => row.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `suppliers-selected-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    bulk.clear();
  };

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  return (
    <div className="flex h-full flex-col space-y-3 overflow-hidden text-left animate-in fade-in duration-400">
      <div className="flex shrink-0 flex-col justify-between gap-3 px-1 sm:flex-row sm:items-center">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-lg shadow-indigo-100">
            <Truck size={22} />
          </div>
          <div>
            <h2 className="text-base font-black tracking-tight text-slate-800">ပေးသွင်းသူစာရင်း</h2>
            <p className="mt-0.5 flex items-center gap-1.5 text-[11px] font-medium text-slate-500">
              <ClipboardList size={12} className="text-indigo-500" /> ပေးသွင်းသူများနှင့် ပေးရန်ကျန်ငွေစာရင်း
            </p>
          </div>
        </div>
        <button 
          onClick={() => handleOpenModal()} 
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-bold text-white shadow-lg shadow-indigo-100 transition-all hover:bg-indigo-700 active:scale-95"
        >
          <Plus size={17} /> ပေးသွင်းသူအသစ်
        </button>
      </div>

      <div className="mx-1 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
        <div className="flex flex-col gap-2 sm:flex-row">
          <div className="relative flex-1 group">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={18} />
            <input 
              type="text" 
              placeholder="အမည် သို့မဟုတ် ကုဒ်ဖြင့် ရှာပါ..." 
              value={searchTerm}
              onChange={handleSearch}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm font-medium outline-none transition-all focus:border-indigo-500 focus:bg-white focus:ring-2 focus:ring-indigo-500/10"
            />
          </div>
          <button 
            onClick={() => fetchPaginatedData()}
            title="ပြန်ဖတ်ရန်"
            className="inline-flex h-10 items-center justify-center rounded-xl border border-slate-200 bg-white px-3 text-slate-500 transition-all hover:border-indigo-100 hover:bg-indigo-50 hover:text-indigo-600"
          >
            <RefreshCw size={17} />
          </button>
        </div>
      </div>

      <div className="mx-1">
        <BulkSelectionToolbar
          visibleCount={suppliers.length}
          selectedCount={bulk.selectedCount}
          allVisibleSelected={bulk.allVisibleSelected}
          someVisibleSelected={bulk.someVisibleSelected}
          onToggleVisible={() => bulk.allVisibleSelected ? bulk.clear() : bulk.selectVisible()}
          onClear={bulk.clear}
          selectedRows={bulk.selectedRows}
          actions={[{ key: 'export', label: 'Export selected', icon: <Download size={13} />, tone: 'indigo' }]}
          onAction={handleBulkAction}
        />
      </div>

      <div className="mx-1 flex flex-1 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full min-w-[760px] border-collapse text-left">
            <thead className="sticky top-0 z-30 border-b border-slate-200 bg-slate-50/95 shadow-sm backdrop-blur-sm">
              <tr>
                <th className="w-12 px-3 text-center"><span className="sr-only">Select</span></th>
                <th className="px-5 py-3 text-[11px] font-bold text-slate-500">ပေးသွင်းသူ အချက်အလက်</th>
                <th className="px-4 py-3 text-[11px] font-bold text-slate-500">ဆက်သွယ်ရန်</th>
                <th className="px-4 py-3 text-[11px] font-bold text-slate-500">လိပ်စာ</th>
                <th className="px-4 py-3 text-right text-[11px] font-bold text-slate-500">ပေးရန်ကျန်ငွေ</th>
                <th className="px-5 py-3 text-right text-[11px] font-bold text-slate-500">လုပ်ဆောင်ရန်</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {suppliers.length > 0 ? suppliers.map((supplier) => (
                <tr key={supplier.id} className="group border-b border-slate-100 last:border-0 transition-colors hover:bg-indigo-50/30">
                  <td className="px-3 text-center">
                    <input type="checkbox" checked={bulk.selectedIds.has(supplier.id)} onChange={() => bulk.toggle(supplier.id)} className="h-4 w-4 accent-indigo-600" aria-label={`Select ${supplier.name}`} />
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-indigo-100 bg-indigo-50 text-indigo-600 transition-all group-hover:bg-indigo-600 group-hover:text-white">
                        <Truck size={19} />
                      </div>
                      <div className="text-left">
                        <p className="text-sm font-bold tracking-tight text-slate-800">{supplier.name}</p>
                        <p className="mt-0.5 text-[10px] font-semibold tracking-wide text-slate-400">{supplier.code}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3.5">
                    <div className="flex items-center gap-2">
                      <Phone size={14} className="text-slate-300" />
                      <span className="text-xs font-medium text-slate-600">{supplier.phone || 'မရှိသေးပါ'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-left">
                    <div className="flex max-w-[220px] items-center gap-2">
                      <MapPin size={14} className="text-slate-300 shrink-0" />
                      <span className="truncate text-xs text-slate-500">{supplier.address || 'လိပ်စာမထည့်ရသေးပါ'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    <div className="flex flex-col items-end">
                      <span className={`text-[14px] font-black tabular-nums ${supplier.currentBalance > 0 ? 'text-rose-600' : 'text-emerald-600'}`}>
        {Number(supplier.currentBalance ?? 0).toLocaleString()} <span className="text-[10px] ml-0.5 font-bold uppercase">Ks</span>
                      </span>
                      {supplier.openingBalance > 0 && (
                        <p className="text-[9px] text-slate-400 font-bold uppercase tracking-tighter">OB: {Number(supplier.openingBalance ?? 0).toLocaleString()}</p>
                      )}
                    </div>
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="flex justify-end gap-1.5">
                      <button title="ပြင်မည်" onClick={() => handleOpenModal(supplier)} className="rounded-lg border border-indigo-100 bg-indigo-50 p-2 text-indigo-600 transition-all hover:bg-indigo-600 hover:text-white">
                        <Edit2 size={15} />
                      </button>
                      <button title="ဖျက်မည်" onClick={() => handleDelete(supplier.id)} className="rounded-lg border border-rose-100 bg-rose-50 p-2 text-rose-600 transition-all hover:bg-rose-600 hover:text-white">
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={6} className="px-6 py-40 text-center">
                    <div className="flex flex-col items-center gap-5">
                      <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center border border-dashed border-slate-200">
                        <Truck className="text-slate-200" size={40} />
                      </div>
                      <p className="text-sm font-black uppercase tracking-widest text-slate-400 italic">No Suppliers Enlisted</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalElements > 0 && (
          <div className="sticky bottom-0 z-30 px-10 py-6 bg-white border-t border-slate-100 flex flex-col md:flex-row items-center justify-between gap-8">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest">
                Procurement Hub <span className="w-6 h-[1px] bg-slate-200 inline-block mx-2"></span> 
                Found <span className="text-indigo-600">{totalElements}</span> Registered Partners
              </span>
            </div>

            <div className="w-full md:w-auto flex flex-col sm:flex-row items-center justify-center gap-5 order-1 md:order-2">
              <select 
                value={itemsPerPage} onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(0); }}
                className="appearance-none bg-slate-50 border border-slate-200 rounded-xl px-5 py-2.5 pr-12 text-[11px] font-black text-slate-600 outline-none focus:bg-white focus:border-indigo-500 cursor-pointer shadow-sm transition-all"
              >
                <option value={10}>Show 10</option>
                <option value={25}>Show 25</option>
                <option value={50}>Show 50</option>
              </select>

              <div className="flex items-center gap-2">
                <button disabled={currentPage === 0} onClick={() => setCurrentPage(prev => Math.max(prev - 1, 0))} className="p-3 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all shadow-sm active:scale-90"><ChevronLeft size={18} /></button>
                <div className="flex gap-1.5">
                  {[...Array(totalPages)].map((_, i) => (
                    <button key={i} onClick={() => setCurrentPage(i)} className={`min-w-[42px] h-11 rounded-xl text-[12px] font-black transition-all ${currentPage === i ? 'bg-indigo-600 text-white shadow-xl shadow-indigo-100 border-indigo-600' : 'bg-white border border-slate-100 text-slate-500 hover:bg-slate-50'}`}>{i + 1}</button>
                  ))}
                </div>
                <button disabled={currentPage === totalPages - 1} onClick={() => setCurrentPage(prev => prev + 1)} className="p-3 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all shadow-sm active:scale-90"><ChevronRight size={18} /></button>
              </div>
            </div>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200 text-left">
          <div className="bg-white w-full max-w-lg rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <div className="text-left">
                <h3 className="text-[15px] font-black text-slate-800 uppercase tracking-tight">{editingSupplier ? 'ပါတနာပြင်မည်' : 'ပေးသွင်းသူအသစ် မှတ်ပုံတင်မည်'}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-1 tracking-widest italic">Procurement Identity & Finance</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2 hover:bg-slate-100 rounded-xl transition-colors"><X size={20} className="text-slate-400" /></button>
            </div>
            
            <form onSubmit={handleSave} className="p-8 space-y-6 text-left max-h-[75vh] overflow-y-auto custom-scrollbar">
              <div className="space-y-2">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Supplier Name</label>
                <div className="relative group">
                  <Truck className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input 
                    type="text" required value={formData.name} 
                    onChange={(e) => setFormData({...formData, name: e.target.value})} 
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                    placeholder="Enter official business name"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-2 text-left">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Phone Number</label>
                  <div className="relative group">
                    <Phone className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                    <input 
                      type="text" value={formData.phone} 
                      onChange={(e) => setFormData({...formData, phone: e.target.value})} 
                      className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                      placeholder="e.g. 09xxxxxxxxx"
                    />
                  </div>
                </div>

                {!editingSupplier && (
                  <div className="space-y-2 text-left">
                    <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 text-emerald-600">Opening Balance (MMK)</label>
                    <div className="relative group">
                      <DollarSign className="absolute left-4 top-1/2 -translate-y-1/2 text-emerald-300 group-focus-within:text-emerald-500 transition-colors" size={16} />
                      <input 
                        type="number" value={formData.openingBalance} 
                        onChange={(e) => setFormData({...formData, openingBalance: Number(e.target.value)})} 
                        className="w-full pl-12 pr-5 py-4 bg-emerald-50/30 border border-emerald-100 rounded-2xl text-[12px] font-bold outline-none focus:border-emerald-500 focus:bg-white transition-all shadow-sm" 
                        placeholder="0.00"
                      />
                    </div>
                  </div>
                )}
              </div>

              <div className="space-y-2 text-left">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Business Address</label>
                <div className="relative group">
                  <MapPin className="absolute left-4 top-4 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <textarea 
                    rows={3} value={formData.address} 
                    onChange={(e) => setFormData({...formData, address: e.target.value})} 
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 shadow-sm transition-all resize-none" 
                    placeholder="Enter full business location..."
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 rounded-2xl border border-amber-100 bg-amber-50/40 p-4">
                <div className="space-y-2">
                  <label className="text-[10px] font-black text-amber-700 uppercase tracking-widest">Default Credit Days</label>
                  <input type="number" min="0" max="3650" value={formData.defaultCreditDays ?? 30}
                    onChange={(e) => setFormData({...formData, defaultCreditDays: Math.max(0, Number(e.target.value) || 0)})}
                    className="w-full rounded-xl border border-amber-200 bg-white px-4 py-3 text-sm font-bold outline-none focus:border-amber-500" />
                  <p className="text-[9px] font-semibold text-amber-600">Purchase ရွေးချယ်ချိန် due date အလိုအလျောက်တွက်မည်။</p>
                </div>
                <div className="space-y-2">
                  <label className="text-[10px] font-black text-amber-700 uppercase tracking-widest">Credit Limit (MMK)</label>
                  <input type="number" min="0" step="0.01" value={formData.creditLimit ?? 0}
                    onChange={(e) => setFormData({...formData, creditLimit: Math.max(0, Number(e.target.value) || 0)})}
                    className="w-full rounded-xl border border-amber-200 bg-white px-4 py-3 text-sm font-bold outline-none focus:border-amber-500" />
                </div>
              </div>

              {editingSupplier && (
                <div className="p-4 bg-indigo-50 border border-indigo-100 rounded-2xl flex items-start gap-3">
                  <AlertCircle size={18} className="text-indigo-600 shrink-0 mt-0.5" />
                  <div className="text-left">
                    <p className="text-[11px] font-black text-indigo-900 uppercase tracking-tight">Current Debt Balance</p>
                    <p className="text-[15px] font-black text-indigo-700 tabular-nums">{Number(editingSupplier.currentBalance ?? 0).toLocaleString()} MMK</p>
                    <p className="text-[9px] text-indigo-400 font-bold mt-1">Balances are updated via procurement transactions and payments.</p>
                  </div>
                </div>
              )}

              <div className="flex gap-4 pt-4 shrink-0">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-4 border border-slate-200 rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm">Discard</button>
                <button type="submit" disabled={saving} className="flex-[2] py-4 bg-indigo-600 text-white rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest shadow-2xl shadow-indigo-600/30 active:scale-[0.98] transition-all flex items-center justify-center gap-2 hover:bg-indigo-700">
                  {saving ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />} Save Record
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default SupplierManagement;
