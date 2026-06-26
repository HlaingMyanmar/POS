
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { categoryService } from '../services/categoryapiservice';
import { CategoryDTO } from '../types';
import { 
  Loader2, Plus, Search, Edit2, X, 
  ChevronRight, ChevronDown, FolderTree,
  Folder, Tag, ChevronLeft, ChevronRight as ChevronRightIcon,
  Layers, Save, Trash2
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

interface CategoryOption {
  id: number;
  displayName: string;
}

const CategoryManagement: React.FC = () => {
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [flatCategories, setFlatCategories] = useState<CategoryDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  const [filterActive, setFilterActive] = useState(true);
  const [filterInactive, setFilterInactive] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryDTO | null>(null);
  const [formData, setFormData] = useState({ name: '', description: '', active: true, parentId: null as number | null });
  const [saving, setSaving] = useState(false);

  const flatten = useCallback((items: CategoryDTO[], pid: number | null = null, result: CategoryDTO[] = []) => {
    items.forEach(item => {
      const currentParentId = (item.parentId !== undefined && item.parentId !== null) ? item.parentId : pid;
      const normalized = { ...item, parentId: currentParentId };
      result.push(normalized);
      if (item.children && item.children.length > 0) flatten(item.children, item.id, result);
    });
    return result;
  }, []);

  const fetchTreeData = useCallback(async () => {
    try {
      const data = await categoryService.getTree();
      setCategories(data);
      setFlatCategories(flatten(data));
    } catch (error) { console.error("Load error", error); }
    finally { setLoading(false); }
  }, [flatten]);

  useEffect(() => { fetchTreeData(); }, [fetchTreeData]);
  useWebsocket('/topic/category', () => { setWsStatus('online'); fetchTreeData(); setTimeout(() => setWsStatus('offline'), 5000); });

  // Helper for hierarchical select options
  const getHierarchyOptions = useCallback((nodes: CategoryDTO[], level = 0): CategoryOption[] => {
    return nodes.reduce((acc: CategoryOption[], node) => {
      // Don't include the category being edited (to prevent circular parent)
      if (editingCategory && node.id === editingCategory.id) return acc;
      
      const indentation = "\u00A0\u00A0".repeat(level * 2);
      const prefix = level > 0 ? "↳ " : "";
      acc.push({ id: node.id, displayName: `${indentation}${prefix}${node.name}` });
      
      if (node.children && node.children.length > 0) {
        acc.push(...getHierarchyOptions(node.children, level + 1));
      }
      return acc;
    }, []);
  }, [editingCategory]);

  const parentOptions = useMemo(() => getHierarchyOptions(categories), [categories, getHierarchyOptions]);

  const filteredCategories = useMemo(() => {
    const searchLower = searchTerm.toLowerCase().trim();
    const filterNodes = (nodes: CategoryDTO[]): CategoryDTO[] => {
      return nodes.reduce((acc: CategoryDTO[], node) => {
        const matchesStatus = (node.active && filterActive) || (!node.active && filterInactive);
        const matchesSearch = !searchLower || node.name.toLowerCase().includes(searchLower) || (node.description?.toLowerCase().includes(searchLower));
        const filteredChildren = node.children ? filterNodes(node.children) : [];
        if ((matchesStatus && matchesSearch) || filteredChildren.length > 0) acc.push({ ...node, children: filteredChildren });
        return acc;
      }, []);
    };
    return filterNodes(categories);
  }, [categories, searchTerm, filterActive, filterInactive]);

  const totalPages = Math.ceil(filteredCategories.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedCategories = filteredCategories.slice(startIndex, startIndex + itemsPerPage);

  useEffect(() => { setCurrentPage(1); }, [searchTerm, filterActive, filterInactive]);

  const toggleExpand = (id: number) => {
    const next = new Set(expandedIds);
    if (next.has(id)) next.delete(id); else next.add(id);
    setExpandedIds(next);
  };

  const handleOpenModal = (cat?: CategoryDTO) => {
    if (cat) {
      const target = flatCategories.find(f => f.id === cat.id) || cat;
      setEditingCategory(target);
      setFormData({ 
        name: target.name, 
        description: target.description || '', 
        active: target.active, 
        parentId: target.parentId ?? null 
      });
    } else {
      setEditingCategory(null);
      setFormData({ name: '', description: '', active: true, parentId: null });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingCategory) await categoryService.update(editingCategory.id, formData);
      else await categoryService.create(formData);
      setIsModalOpen(false); 
      fetchTreeData();
      Swal.fire({ icon: 'success', title: 'Category Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (err: any) {
      Swal.fire('Error', err.message || 'Action failed', 'error');
    } finally { setSaving(false); }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Delete Category?',
      text: "Warning: This might affect all sub-categories and linked products.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, delete'
    });

    if (result.isConfirmed) {
      try {
        await categoryService.delete(id);
        fetchTreeData();
        Swal.fire({ icon: 'success', title: 'Deleted', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (err: any) {
        Swal.fire('Error', err.message || 'Delete failed', 'error');
      }
    }
  };

  const CategoryRow: React.FC<{ cat: CategoryDTO; level: number; isLast: boolean; parentPaths: boolean[] }> = ({ cat, level, isLast, parentPaths }) => {
    const isExpanded = expandedIds.has(cat.id);
    const hasChildren = cat.children && cat.children.length > 0;
    return (
      <>
        <tr className="group hover:bg-indigo-50/40 border-b border-slate-50 transition-colors cursor-pointer" onClick={() => handleOpenModal(cat)}>
          <td className="px-4 py-1.5 text-left">
            <div className="flex items-center h-full">
              <div className="flex h-10 shrink-0">
                {parentPaths.map((hasNeighbor, i) => (<div key={i} className="relative w-8 flex-shrink-0">{hasNeighbor && <div className="absolute left-1/2 top-0 bottom-0 w-[1px] bg-slate-200" />}</div>))}
                <div className="relative w-8 flex-shrink-0">
                  <div className={`absolute left-1/2 top-0 ${isLast ? 'h-1/2' : 'h-full'} w-[1px] bg-slate-200`} />
                  <div className="absolute left-1/2 top-1/2 w-4 h-[1px] bg-slate-200" />
                </div>
              </div>
              <div className="flex items-center gap-1.5 ml-1">
                <div className="flex items-center justify-center w-6 h-6" onClick={(e) => e.stopPropagation()}>
                  {hasChildren && <button onClick={() => toggleExpand(cat.id)} className={`p-1 hover:bg-indigo-100 rounded text-slate-500 transition-all ${isExpanded ? 'text-indigo-600' : ''}`}>{isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}</button>}
                </div>
                <div className={`w-8 h-8 rounded-xl flex items-center justify-center border transition-all ${cat.active ? 'bg-white border-indigo-100 text-indigo-600 shadow-sm' : 'bg-slate-50 border-slate-200 text-slate-400'}`}>
                  {hasChildren ? <Folder size={14} fill={cat.active ? "currentColor" : "none"} fillOpacity={0.1} /> : <Tag size={13} />}
                </div>
                <div className="ml-2 truncate text-left">
                  <p className={`text-[11px] font-black tracking-tight ${cat.active ? 'text-slate-700' : 'text-slate-400'}`}>{cat.name}</p>
                  {cat.description && <p className="text-[9px] text-slate-400 truncate max-w-[200px]">{cat.description}</p>}
                </div>
              </div>
            </div>
          </td>
          <td className="px-4 text-center">
            <span className={`inline-flex px-3 py-1 rounded-full text-[9px] font-black uppercase tracking-widest border ${cat.active ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-slate-50 text-slate-400 border-slate-200'}`}>{cat.active ? 'Active' : 'Inactive'}</span>
          </td>
          <td className="px-8 py-2 text-right">
            <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button onClick={(e) => { e.stopPropagation(); handleOpenModal(cat); }} className="p-2 text-slate-400 hover:text-indigo-600 transition-colors bg-white border border-slate-100 rounded-lg shadow-sm"><Edit2 size={12} /></button>
              <button onClick={(e) => { e.stopPropagation(); handleDelete(cat.id); }} className="p-2 text-slate-400 hover:text-rose-600 transition-colors bg-white border border-slate-100 rounded-lg shadow-sm"><Trash2 size={12} /></button>
            </div>
          </td>
        </tr>
        {isExpanded && hasChildren && cat.children?.map((child, index) => (<CategoryRow key={child.id} cat={child} level={level + 1} isLast={index === cat.children!.length - 1} parentPaths={[...parentPaths, !isLast]} />))}
      </>
    );
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 text-left h-full flex flex-col overflow-hidden">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 shrink-0">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100"><FolderTree size={24} /></div>
          <div><h2 className="text-base font-black text-slate-800 tracking-tight uppercase">Taxonomy Tree</h2><p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest">Classification Hierarchy</p></div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} /><input type="text" placeholder="Search..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} className="pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-2xl text-[11px] font-bold outline-none w-48 focus:border-indigo-500 shadow-sm" /></div>
          <button onClick={() => handleOpenModal()} className="bg-indigo-600 text-white px-4 py-2 rounded-2xl text-[11px] font-black uppercase shadow-lg shadow-indigo-100 hover:bg-indigo-700 transition-all"><Plus size={16} className="inline mr-1" /> New Entry</button>
        </div>
      </div>

      <div className="bg-white rounded-[1.5rem] shadow-xl shadow-slate-200/40 border border-slate-200/60 flex flex-col flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full border-collapse min-w-[700px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
              <tr>
                <th className="px-8 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Hierarchy</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Status</th>
                <th className="px-8 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {paginatedCategories.length > 0 ? paginatedCategories.map((cat, index) => (<CategoryRow key={cat.id} cat={cat} level={0} isLast={index === paginatedCategories.length - 1} parentPaths={[]} />)) : <tr><td colSpan={3} className="py-20 text-center text-slate-400 text-xs font-bold uppercase tracking-widest">NO DATA AVAILABLE</td></tr>}
            </tbody>
          </table>
        </div>

        {/* Sticky Pagination Footer */}
        {filteredCategories.length > 0 && (
          <div className="sticky bottom-0 z-30 px-8 py-5 bg-white border-t border-slate-200 flex flex-col md:flex-row items-center justify-between gap-6 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
                Showing <span className="text-indigo-600">{startIndex + 1}</span> to <span className="text-indigo-600">{Math.min(startIndex + itemsPerPage, filteredCategories.length)}</span> root branches of <span className="text-slate-800">{filteredCategories.length}</span>
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
                <button disabled={currentPage === totalPages} onClick={() => setCurrentPage(prev => prev + 1)} className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 disabled:opacity-30 transition-all active:scale-90"><ChevronRightIcon size={16} /></button>
              </div>
            </div>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-[2rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="text-[12px] font-black text-slate-800 uppercase tracking-tight">{editingCategory ? 'Update' : 'New'} Category</h3>
                <p className="text-[9px] text-slate-400 font-bold uppercase mt-0.5 tracking-widest italic">Organizational Node</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2 text-slate-400 hover:bg-slate-50 rounded-xl transition-colors"><X size={20} /></button>
            </div>
            
            <form onSubmit={handleSave} className="p-6 space-y-5 text-left overflow-y-auto max-h-[70vh] custom-scrollbar">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Category Name</label>
                <input 
                  type="text" required value={formData.name} 
                  onChange={(e) => setFormData({...formData, name: e.target.value})} 
                  className="w-full px-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all" 
                  placeholder="e.g. Laptops"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Parent Category</label>
                <div className="relative group">
                  <Layers className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={14} />
                  <select 
                    value={formData.parentId || ''} 
                    onChange={(e) => setFormData({...formData, parentId: e.target.value ? Number(e.target.value) : null})}
                    className="w-full pl-10 pr-8 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all appearance-none"
                  >
                    <option value="">Root Category (Main)</option>
                    {parentOptions.map(opt => (
                      <option key={opt.id} value={opt.id}>{opt.displayName}</option>
                    ))}
                  </select>
                  <ChevronDown size={14} className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                </div>
                <p className="text-[9px] text-slate-400 font-bold ml-1 uppercase">Leave blank to keep as top-level</p>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Description</label>
                <textarea 
                  rows={2}
                  value={formData.description} 
                  onChange={(e) => setFormData({...formData, description: e.target.value})} 
                  className="w-full px-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all resize-none" 
                  placeholder="Brief description..."
                />
              </div>

              <div className="flex items-center gap-3 p-4 bg-slate-50 rounded-2xl border border-slate-200">
                <input 
                  type="checkbox" 
                  id="active_check"
                  checked={formData.active}
                  onChange={(e) => setFormData({...formData, active: e.target.checked})}
                  className="w-4 h-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                />
                <label htmlFor="active_check" className="text-[11px] font-black text-slate-700 uppercase tracking-wider cursor-pointer">Status: Active</label>
              </div>

              <div className="flex gap-3 pt-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-3 border border-slate-200 rounded-2xl text-[10px] font-black uppercase bg-white text-slate-500 hover:bg-slate-50 transition-colors">Discard</button>
                <button type="submit" disabled={saving} className="flex-1 py-3 bg-indigo-600 text-white rounded-2xl text-[10px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center justify-center gap-2">
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                  Confirm Save
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoryManagement;
