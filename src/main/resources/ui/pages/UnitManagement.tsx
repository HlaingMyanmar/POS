
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { unitService } from '../services/unitapiservice';
import { UnitDTO } from '../types';
import { 
  Loader2, Plus, Search, Trash2, Edit2, X, 
  Ruler, Hash, ChevronLeft, ChevronRight, ChevronDown,
  Info
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

const UnitManagement: React.FC = () => {
  const [units, setUnits] = useState<UnitDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingUnit, setEditingUnit] = useState<UnitDTO | null>(null);
  const [formData, setFormData] = useState({ unitName: '', description: '' });
  const [saving, setSaving] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const data = await unitService.getAll();
      setUnits(data);
    } catch (error) { console.error("Load error", error); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);
  useWebsocket('/topic/brand', () => { setWsStatus('online'); fetchData(); setTimeout(() => setWsStatus('offline'), 5000); });

  const filteredUnits = useMemo(() => {
    return units.filter(u => u.unitName.toLowerCase().includes(searchTerm.toLowerCase()));
  }, [units, searchTerm]);

  useEffect(() => { setCurrentPage(1); }, [searchTerm]);

  const totalPages = Math.ceil(filteredUnits.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedUnits = filteredUnits.slice(startIndex, startIndex + itemsPerPage);

  const handleOpenModal = (unit?: UnitDTO) => {
    if (unit) {
      setEditingUnit(unit);
      setFormData({ unitName: unit.unitName, description: unit.description || '' });
    } else {
      setEditingUnit(null);
      setFormData({ unitName: '', description: '' });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingUnit) await unitService.update(editingUnit.id, formData);
      else await unitService.create(formData);
      setIsModalOpen(false); fetchData();
      Swal.fire({ icon: 'success', title: 'Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Failed to save', 'error');
    } finally { setSaving(false); }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({ title: 'Remove Unit?', icon: 'warning', showCancelButton: true, confirmButtonColor: '#ef4444', confirmButtonText: 'Delete' });
    if (result.isConfirmed) { 
      try {
        await unitService.delete(id); 
        fetchData(); 
        Swal.fire({ icon: 'success', title: 'Deleted', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message || 'Failed to delete', 'error');
      }
    }
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 text-left h-full flex flex-col overflow-hidden">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100"><Ruler size={24} /></div>
          <div><h2 className="text-base font-black text-slate-800 tracking-tight uppercase">Measurement Units</h2><p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest mt-0.5">Global Unit Registry</p></div>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative group"><Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={16} /><input type="text" placeholder="Search..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} className="pl-11 pr-3 py-2.5 bg-white border border-slate-200 rounded-2xl outline-none text-[11px] font-bold w-full md:w-56 focus:border-indigo-500 transition-all shadow-sm" /></div>
          <button onClick={() => handleOpenModal()} className="bg-indigo-600 text-white px-5 py-2.5 rounded-2xl text-[11px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all"><Plus size={18} className="inline mr-1" /> Add Unit</button>
        </div>
      </div>

      <div className="bg-slate-50/50 rounded-[1.5rem] border border-slate-200 flex flex-col flex-1 overflow-hidden shadow-inner">
        <div className="flex-1 overflow-auto custom-scrollbar p-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
            {paginatedUnits.length > 0 ? paginatedUnits.map((unit) => (
              <div key={unit.id} className="bg-white p-5 rounded-[1.5rem] border border-slate-100 shadow-sm hover:shadow-xl hover:shadow-indigo-50 transition-all group relative overflow-hidden animate-in zoom-in-95">
                <div className="flex justify-between items-start mb-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-center text-indigo-600 group-hover:bg-indigo-600 group-hover:text-white transition-all"><Hash size={20} /></div>
                  <div className="flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity"><button onClick={() => handleOpenModal(unit)} className="p-2 text-slate-400 hover:text-indigo-600 bg-white border border-slate-200 rounded-xl shadow-sm transition-all"><Edit2 size={12} /></button><button onClick={() => handleDelete(unit.id)} className="p-2 text-slate-400 hover:text-rose-600 bg-white border border-slate-200 rounded-xl shadow-sm transition-all"><Trash2 size={12} /></button></div>
                </div>
                <h3 className="text-sm font-black text-slate-800 tracking-tight text-left">{unit.unitName}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-0.5 text-left">ID: UNIT-{unit.id}</p>
                <div className="mt-4 pt-4 border-t border-slate-50"><p className="text-[10px] text-slate-500 line-clamp-2 min-h-[30px] font-medium leading-relaxed italic text-left">{unit.description || "System standard unit."}</p></div>
              </div>
            )) : (
              <div className="col-span-full py-20 text-center text-slate-400 text-xs font-bold uppercase tracking-widest">NO UNITS MATCHING YOUR SEARCH</div>
            )}
          </div>
        </div>

        {/* Sticky Pagination Footer */}
        {filteredUnits.length > 0 && (
          <div className="sticky bottom-0 z-30 px-8 py-5 bg-white border-t border-slate-200 flex flex-col md:flex-row items-center justify-between gap-6 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest">
                Showing <span className="text-indigo-600">{startIndex + 1}</span> to <span className="text-indigo-600">{Math.min(startIndex + itemsPerPage, filteredUnits.length)}</span> of <span className="text-slate-800">{filteredUnits.length}</span> units
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
                <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none group-hover:text-indigo-500" />
              </div>

              <div className="flex items-center gap-1.5">
                <button disabled={currentPage === 1} onClick={() => setCurrentPage(prev => prev - 1)} className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"><ChevronLeft size={16} /></button>
                <div className="flex gap-1.5">
                  {[...Array(totalPages)].map((_, i) => (
                    <button key={i} onClick={() => setCurrentPage(i + 1)} className={`min-w-[40px] h-10 rounded-xl text-[11px] font-black transition-all ${currentPage === i + 1 ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-100 border-indigo-600 scale-105' : 'bg-white border border-slate-200 text-slate-500 hover:bg-slate-50'}`}>{i + 1}</button>
                  ))}
                </div>
                <button disabled={currentPage === totalPages} onClick={() => setCurrentPage(prev => prev + 1)} className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"><ChevronRight size={16} /></button>
              </div>
            </div>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-[2rem] shadow-2xl border border-slate-200 animate-in zoom-in-95">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between"><h3 className="text-[12px] font-black text-slate-800 uppercase tracking-tight">{editingUnit ? 'Edit' : 'New'} Unit</h3><button onClick={() => setIsModalOpen(false)} className="p-2 hover:bg-slate-100 rounded-xl text-slate-400 transition-colors"><X size={20} /></button></div>
            <form onSubmit={handleSave} className="p-6 space-y-5 text-left">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Unit Label</label>
                <input 
                  type="text" required value={formData.unitName} 
                  onChange={(e) => setFormData({...formData, unitName: e.target.value})} 
                  className="w-full px-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all" 
                  placeholder="e.g. Kilograms"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Description</label>
                <div className="relative group">
                  <Info className="absolute left-3.5 top-3 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={14} />
                  <textarea 
                    rows={3}
                    value={formData.description} 
                    onChange={(e) => setFormData({...formData, description: e.target.value})} 
                    className="w-full pl-10 pr-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all resize-none" 
                    placeholder="Short description of this unit..."
                  />
                </div>
              </div>
              <div className="flex gap-3 pt-3"><button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-3 border border-slate-200 rounded-2xl text-[10px] font-black uppercase bg-white text-slate-500 hover:bg-slate-50 transition-colors">Cancel</button><button type="submit" disabled={saving} className="flex-1 py-3 bg-indigo-600 text-white rounded-2xl text-[10px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center justify-center gap-2">
                {saving && <Loader2 size={14} className="animate-spin" />}
                Confirm Save
              </button></div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default UnitManagement;
