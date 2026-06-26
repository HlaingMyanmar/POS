
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { brandService } from '../services/brandapiservice';
import { BrandDTO } from '../types';
import { 
  Loader2, Plus, Search, Trash2, Edit2, X, 
  Package, ChevronLeft, ChevronRight, ChevronDown
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

const BrandManagement: React.FC = () => {
  const [brands, setBrands] = useState<BrandDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingBrand, setEditingBrand] = useState<BrandDTO | null>(null);
  const [formData, setFormData] = useState({ name: '', isActive: true });
  const [saving, setSaving] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const data = await brandService.getAll();
      setBrands(data);
    } catch (error) { console.error("Load error", error); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);
  useWebsocket('/topic/brand', () => { setWsStatus('online'); fetchData(); setTimeout(() => setWsStatus('offline'), 5000); });

  const filteredBrands = useMemo(() => {
    return brands.filter(b => b.name.toLowerCase().includes(searchTerm.toLowerCase()));
  }, [brands, searchTerm]);

  useEffect(() => { setCurrentPage(1); }, [searchTerm]);

  const totalPages = Math.ceil(filteredBrands.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedBrands = filteredBrands.slice(startIndex, startIndex + itemsPerPage);

  const handleOpenModal = (brand?: BrandDTO) => {
    if (brand) {
      setEditingBrand(brand);
      setFormData({ name: brand.name, isActive: brand.isActive });
    } else {
      setEditingBrand(null);
      setFormData({ name: '', isActive: true });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingBrand) await brandService.update(editingBrand.id, formData);
      else await brandService.create(formData);
      setIsModalOpen(false); fetchData();
      Swal.fire({ icon: 'success', title: 'Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } finally { setSaving(false); }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Delete Brand?',
      text: "This might affect products associated with this brand.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, delete'
    });

    if (result.isConfirmed) {
      try {
        await brandService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'Deleted', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (err: any) {
        Swal.fire('Error', err.message || 'Delete failed', 'error');
      }
    }
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 text-left shrink-0">
        <div>
          <h2 className="text-lg font-bold text-slate-800 tracking-tight uppercase">Brand Registry</h2>
          <p className="text-slate-500 text-xs">Manage system-wide corporate brands.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <input 
              type="text" placeholder="Filter brands..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-lg outline-none text-xs w-48 focus:border-indigo-500 transition-all shadow-sm"
            />
          </div>
          <button onClick={() => handleOpenModal()} className="bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold shadow-md flex items-center gap-1.5 hover:bg-indigo-700 transition-all active:scale-95">
            <Plus size={14} /> Add Brand
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[600px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
              <tr>
                <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Brand Label</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Status</th>
                <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 relative">
              {paginatedBrands.length > 0 ? paginatedBrands.map((brand) => (
                <tr key={brand.id} className="hover:bg-slate-50/50 transition-colors group">
                  <td className="px-6 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-center text-slate-400 group-hover:bg-indigo-50 group-hover:text-indigo-600 transition-colors">
                        <Package size={16} />
                      </div>
                      <p className="text-xs font-bold text-slate-700">{brand.name}</p>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-center">
                      <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider border ${
                        brand.isActive ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-slate-50 text-slate-500 border-slate-200'
                      }`}>
                        {brand.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-3 text-right">
                    <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => handleOpenModal(brand)} className="p-2 text-slate-400 hover:text-indigo-600 transition-colors"><Edit2 size={12} /></button>
                      <button onClick={() => handleDelete(brand.id)} className="p-2 text-slate-400 hover:text-rose-600 transition-colors"><Trash2 size={12} /></button>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr><td colSpan={3} className="px-6 py-20 text-center text-slate-400 text-xs font-bold tracking-widest">NO RECORDS FOUND</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Sticky Pagination Footer */}
        {filteredBrands.length > 0 && (
          <div className="sticky bottom-0 z-30 px-6 py-4 bg-white border-t border-slate-200 flex flex-col md:flex-row items-center justify-between gap-4 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest">
                Showing <span className="text-indigo-600">{startIndex + 1}</span> to <span className="text-indigo-600">{Math.min(startIndex + itemsPerPage, filteredBrands.length)}</span> of <span className="text-slate-800">{filteredBrands.length}</span> brands
              </span>
            </div>

            <div className="w-full md:w-auto flex flex-col sm:flex-row items-center justify-center gap-4 order-1 md:order-2">
              <div className="relative group">
                <select 
                  value={itemsPerPage} onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(1); }}
                  className="appearance-none bg-slate-50 border border-slate-200 rounded-xl px-4 py-1.5 pr-10 text-[10px] font-black text-slate-600 outline-none focus:bg-white focus:border-indigo-500 cursor-pointer transition-all"
                >
                  <option value={10}>10 per page</option>
                  <option value={20}>20 per page</option>
                  <option value={50}>50 per page</option>
                </select>
                <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none group-hover:text-indigo-500 transition-colors" />
              </div>

              <div className="flex items-center gap-1.5">
                <button 
                  disabled={currentPage === 1} 
                  onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))} 
                  className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"
                >
                  <ChevronLeft size={16} />
                </button>
                <div className="flex gap-1">
                  {[...Array(totalPages)].map((_, i) => {
                    const pageNum = i + 1;
                    if (totalPages > 5 && Math.abs(pageNum - currentPage) > 1 && pageNum !== 1 && pageNum !== totalPages) return null;
                    return (
                      <button 
                        key={i} 
                        onClick={() => setCurrentPage(pageNum)} 
                        className={`min-w-[38px] h-10 rounded-xl text-[11px] font-black transition-all ${
                          currentPage === pageNum ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-100 border-indigo-600 scale-105' : 'bg-white border border-slate-200 text-slate-500 hover:bg-slate-50'
                        }`}
                      >
                        {pageNum}
                      </button>
                    );
                  })}
                </div>
                <button 
                  disabled={currentPage === totalPages} 
                  onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))} 
                  className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-[1.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95">
            <div className="p-5 border-b border-slate-100 flex items-center justify-between text-left">
              <h3 className="text-sm font-bold text-slate-800 uppercase tracking-tight">{editingBrand ? 'Edit Brand' : 'New Brand'}</h3>
              <button onClick={() => setIsModalOpen(false)} className="p-2 hover:bg-slate-100 rounded-xl transition-colors"><X size={18} className="text-slate-400" /></button>
            </div>
            <form onSubmit={handleSave} className="p-6 space-y-4 text-left">
              <div className="space-y-1.5"><label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Brand Name</label><input type="text" required value={formData.name} onChange={(e) => setFormData({...formData, name: e.target.value})} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500 transition-all" /></div>
              <div className="flex gap-2 pt-2"><button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-3 border border-slate-200 rounded-2xl text-[10px] font-black bg-white text-slate-500 hover:bg-slate-50 transition-colors">Discard</button><button type="submit" disabled={saving} className="flex-1 py-3 bg-indigo-600 text-white rounded-2xl text-[10px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all">Confirm</button></div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default BrandManagement;
