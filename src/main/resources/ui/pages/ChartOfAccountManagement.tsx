
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { coaService } from '../services/coaapiservice';
import { ChartOfAccountDTO, AccountType } from '../types';
import { 
  Loader2, Plus, Edit2, X, 
  ChevronRight, ChevronDown, BookOpen,
  PieChart, Layers, Save, Trash2, 
  Briefcase, Landmark, TrendingUp, TrendingDown,
  Info, RotateCcw
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

interface COAOption {
  id: number;
  displayName: string;
}

const ChartOfAccountManagement: React.FC = () => {
  const [coaTree, setCoaTree] = useState<ChartOfAccountDTO[]>([]);
  const [flatCoa, setFlatCoa] = useState<ChartOfAccountDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCoa, setEditingCoa] = useState<ChartOfAccountDTO | null>(null);
  const [formData, setFormData] = useState<Partial<ChartOfAccountDTO>>({
    accountName: '',
    accountType: AccountType.Asset,
    parentId: null
  });
  const [saving, setSaving] = useState(false);

  const flatten = useCallback((items: ChartOfAccountDTO[], result: ChartOfAccountDTO[] = []) => {
    items.forEach(item => {
      result.push(item);
      if (item.children && item.children.length > 0) flatten(item.children, result);
    });
    return result;
  }, []);

  const fetchData = useCallback(async () => {
    try {
      const data = await coaService.getTree();
      setCoaTree(data);
      setFlatCoa(flatten(data));
      
      // Auto-expand top level if empty
      if (expandedIds.size === 0 && data.length > 0) {
        setExpandedIds(new Set(data.map(d => d.id)));
      }
    } catch (error) {
      console.error("Failed to load COA data", error);
    } finally {
      setLoading(false);
    }
  }, [flatten, expandedIds.size]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  useWebsocket('/topic/coa', fetchData);

  const toggleExpand = (id: number) => {
    const next = new Set(expandedIds);
    if (next.has(id)) next.delete(id); else next.add(id);
    setExpandedIds(next);
  };

  const handleOpenModal = (coa?: ChartOfAccountDTO) => {
    if (coa) {
      setEditingCoa(coa);
      setFormData({
        accountName: coa.accountName,
        accountType: coa.accountType,
        parentId: coa.parentId
      });
    } else {
      setEditingCoa(null);
      setFormData({
        accountName: '',
        accountType: AccountType.Asset,
        parentId: null
      });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingCoa) {
        await coaService.update(editingCoa.id, formData);
      } else {
        await coaService.create(formData);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Ledger Updated', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Database Error', error.message || 'Operation failed. Check if class type length is valid in database.', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Remove Account?',
      text: "Warning: Accounts with sub-accounts or active transactions cannot be removed.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, Delete'
    });

    if (result.isConfirmed) {
      try {
        await coaService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'Deleted Successfully', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Restricted', error.message || 'Deletion failed.', 'error');
      }
    }
  };

  const getAccountTypeConfig = (type: AccountType) => {
    switch (type) {
      case AccountType.Asset: return { icon: <Landmark size={14} />, color: 'emerald', bg: 'bg-emerald-50', text: 'text-emerald-600', border: 'border-emerald-100' };
      case AccountType.Liability: return { icon: <Briefcase size={14} />, color: 'rose', bg: 'bg-rose-50', text: 'text-rose-600', border: 'border-rose-100' };
      case AccountType.Equity: return { icon: <PieChart size={14} />, color: 'indigo', bg: 'bg-indigo-50', text: 'text-indigo-600', border: 'border-indigo-100' };
      case AccountType.Income: return { icon: <TrendingUp size={14} />, color: 'sky', bg: 'bg-sky-50', text: 'text-sky-600', border: 'border-sky-100' };
      case AccountType.Expense: return { icon: <TrendingDown size={14} />, color: 'amber', bg: 'bg-amber-50', text: 'text-amber-600', border: 'border-amber-100' };
      default: return { icon: <Info size={14} />, color: 'slate', bg: 'bg-slate-50', text: 'text-slate-600', border: 'border-slate-100' };
    }
  };

  const getHierarchyOptions = useCallback((nodes: ChartOfAccountDTO[], level = 0): COAOption[] => {
    return nodes.reduce((acc: COAOption[], node) => {
      if (editingCoa && node.id === editingCoa.id) return acc;
      const indentation = "\u00A0\u00A0".repeat(level * 2);
      acc.push({ id: node.id, displayName: `${indentation}↳ ${node.accountName}` });
      if (node.children && node.children.length > 0) {
        acc.push(...getHierarchyOptions(node.children, level + 1));
      }
      return acc;
    }, []);
  }, [editingCoa]);

  const parentOptions = useMemo(() => getHierarchyOptions(coaTree), [coaTree, getHierarchyOptions]);

  const AccountRow: React.FC<{ coa: ChartOfAccountDTO; level: number; isLast: boolean; parentPaths: boolean[] }> = ({ coa, level, isLast, parentPaths }) => {
    const isExpanded = expandedIds.has(coa.id);
    const hasChildren = coa.children && coa.children.length > 0;
    const typeConfig = getAccountTypeConfig(coa.accountType);
    
    return (
      <>
        <tr className="group hover:bg-slate-50 border-b border-slate-50 transition-colors cursor-pointer" onClick={() => handleOpenModal(coa)}>
          <td className="px-4 py-1.5 text-left">
            <div className="flex items-center h-full">
              <div className="flex h-10 shrink-0">
                {parentPaths.map((hasNeighbor, i) => (
                  <div key={i} className="relative w-8 flex-shrink-0">
                    {hasNeighbor && <div className="absolute left-1/2 top-0 bottom-0 w-[1px] bg-slate-200" />}
                  </div>
                ))}
                <div className="relative w-8 flex-shrink-0">
                  <div className={`absolute left-1/2 top-0 ${isLast ? 'h-1/2' : 'h-full'} w-[1px] bg-slate-200`} />
                  <div className="absolute left-1/2 top-1/2 w-4 h-[1px] bg-slate-200" />
                </div>
              </div>
              <div className="flex items-center gap-1.5 ml-1">
                <div className="flex items-center justify-center w-6 h-6" onClick={(e) => e.stopPropagation()}>
                  {hasChildren && (
                    <button 
                      onClick={() => toggleExpand(coa.id)} 
                      className={`p-1 hover:bg-indigo-100 rounded text-slate-400 transition-all ${isExpanded ? 'text-indigo-600' : ''}`}
                    >
                      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                    </button>
                  )}
                </div>
                <div className={`w-8 h-8 rounded-xl flex items-center justify-center border transition-all bg-white ${typeConfig.border} ${typeConfig.text} shadow-sm`}>
                  {typeConfig.icon}
                </div>
                <div className="ml-2 truncate text-left">
                  <p className="text-[11px] font-black text-slate-800 tracking-tight leading-none mb-1">{coa.accountName}</p>
                  <p className="text-[9px] text-slate-400 font-bold uppercase tracking-widest">{coa.code}</p>
                </div>
              </div>
            </div>
          </td>
          <td className="px-4 text-center">
            <span className={`inline-flex px-3 py-1 rounded-full text-[9px] font-black uppercase tracking-widest border ${typeConfig.bg} ${typeConfig.text} ${typeConfig.border}`}>
              {coa.accountType}
            </span>
          </td>
          <td className="px-8 py-2 text-right">
            <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button onClick={(e) => { e.stopPropagation(); handleOpenModal(coa); }} className="p-2 bg-white text-slate-400 hover:text-indigo-600 border border-slate-100 rounded-lg shadow-sm transition-all">
                <Edit2 size={12} />
              </button>
              <button onClick={(e) => { e.stopPropagation(); handleDelete(coa.id); }} className="p-2 bg-white text-slate-400 hover:text-rose-600 border border-slate-100 rounded-lg shadow-sm transition-all">
                <Trash2 size={12} />
              </button>
            </div>
          </td>
        </tr>
        {isExpanded && hasChildren && coa.children?.map((child, index) => (
          <AccountRow 
            key={child.id} 
            coa={child} 
            level={level + 1} 
            isLast={index === coa.children!.length - 1} 
            parentPaths={[...parentPaths, !isLast]} 
          />
        ))}
      </>
    );
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden text-left">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 shrink-0 px-1">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100 shrink-0">
            <BookOpen size={24} />
          </div>
          <div>
            <h2 className="text-base font-black text-slate-800 tracking-tight uppercase">General Ledger Registry</h2>
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest mt-0.5 flex items-center gap-1.5">
              <PieChart size={10} className="text-indigo-400" /> Chart of Accounts Hierarchy
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
           <button onClick={() => fetchData()} className="p-2.5 text-slate-400 hover:text-indigo-600 bg-white border border-slate-200 rounded-2xl shadow-sm transition-all">
             <RotateCcw size={18} />
           </button>
           <button 
             onClick={() => handleOpenModal()} 
             className="bg-indigo-600 text-white px-5 py-2.5 rounded-2xl text-[11px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center gap-2 hover:bg-indigo-700"
           >
             <Plus size={18} /> New Ledger Entry
           </button>
        </div>
      </div>

      {/* Main Container */}
      <div className="bg-white rounded-[2rem] shadow-2xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden mx-1">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[800px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
              <tr>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest">Account Hierarchy</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Class Type</th>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {coaTree.length > 0 ? coaTree.map((coa, index) => (
                <AccountRow 
                  key={coa.id} 
                  coa={coa} 
                  level={0} 
                  isLast={index === coaTree.length - 1} 
                  parentPaths={[]} 
                />
              )) : (
                <tr>
                  <td colSpan={3} className="px-6 py-40 text-center">
                    <div className="flex flex-col items-center gap-5">
                      <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center border border-dashed border-slate-200">
                        <BookOpen className="text-slate-200" size={40} />
                      </div>
                      <p className="text-sm font-black uppercase tracking-widest text-slate-400 italic">Financial Structure Empty</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        
        {/* Footer Info */}
        <div className="px-10 py-5 bg-slate-50 border-t border-slate-100 flex items-center justify-between">
          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
            Total Ledger Points: <span className="text-indigo-600 font-black">{flatCoa.length}</span>
          </span>
          <div className="flex items-center gap-4">
             {Object.values(AccountType).map(type => {
               const cfg = getAccountTypeConfig(type);
               const count = flatCoa.filter(c => c.accountType === type).length;
               if (count === 0) return null;
               return (
                 <div key={type} className="flex items-center gap-1.5">
                   <div className={`w-2 h-2 rounded-full ${cfg.bg.replace('bg-', 'bg-').replace('-50', '-500')}`}></div>
                   <span className="text-[9px] font-black text-slate-500 uppercase">{type} ({count})</span>
                 </div>
               )
             })}
          </div>
        </div>
      </div>

      {/* Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200 text-left">
          <div className="bg-white w-full max-w-lg rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between bg-slate-50/50 text-left">
              <div className="text-left">
                <h3 className="text-[14px] font-black text-slate-800 uppercase tracking-tight">{editingCoa ? 'Update Ledger' : 'New Ledger Point'}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-1 tracking-widest italic">Identity & Financial Classification</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2.5 hover:bg-white hover:shadow-md rounded-2xl transition-all border border-transparent hover:border-slate-100"><X size={20} className="text-slate-400" /></button>
            </div>
            
            <form onSubmit={handleSave} className="p-8 space-y-6 text-left max-h-[75vh] overflow-y-auto custom-scrollbar">
              <div className="space-y-2 text-left">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Account Label</label>
                <div className="relative group">
                  <BookOpen className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input 
                    type="text" required value={formData.accountName} 
                    onChange={(e) => setFormData({...formData, accountName: e.target.value})} 
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                    placeholder="Enter official account name"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-2 text-left">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Parent Ledger</label>
                  <div className="relative group">
                    <Layers className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                    <select 
                      value={formData.parentId || ''} 
                      onChange={(e) => {
                        const pid = e.target.value ? Number(e.target.value) : null;
                        const parent = flatCoa.find(a => a.id === pid);
                        setFormData({
                          ...formData, 
                          parentId: pid,
                          accountType: parent ? parent.accountType : formData.accountType
                        });
                      }}
                      className="w-full pl-12 pr-10 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 transition-all appearance-none shadow-sm"
                    >
                      <option value="">Root Level (Main)</option>
                      {parentOptions.map(opt => <option key={opt.id} value={opt.id}>{opt.displayName}</option>)}
                    </select>
                    <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                  </div>
                </div>

                <div className="space-y-2 text-left">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Class Type</label>
                  <div className="relative group">
                    <PieChart className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                    <select 
                      disabled={formData.parentId !== null}
                      value={formData.accountType} 
                      onChange={(e) => setFormData({...formData, accountType: e.target.value as AccountType})}
                      className="w-full pl-12 pr-10 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 transition-all appearance-none shadow-sm disabled:opacity-50"
                    >
                      {Object.values(AccountType).map(type => <option key={type} value={type}>{type}</option>)}
                    </select>
                    <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                  </div>
                  {formData.parentId !== null && <p className="text-[8px] text-amber-500 font-bold uppercase mt-1 tracking-widest ml-1">Inherited from Parent</p>}
                </div>
              </div>

              {editingCoa && (
                <div className="p-4 bg-indigo-50 border border-indigo-100 rounded-2xl flex items-start gap-3">
                  <Info size={18} className="text-indigo-600 shrink-0 mt-0.5" />
                  <div className="text-left">
                    <p className="text-[11px] font-black text-indigo-900 uppercase tracking-tight leading-none mb-1">System Identity Code</p>
                    <p className="text-[14px] font-black text-indigo-700 tracking-widest tabular-nums">{editingCoa.code}</p>
                    <p className="text-[9px] text-indigo-400 font-bold mt-1 uppercase italic">Identity is generated based on account class sequence.</p>
                  </div>
                </div>
              )}

              <div className="flex gap-4 pt-4 shrink-0">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-4 border border-slate-200 rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm">Discard</button>
                <button type="submit" disabled={saving} className="flex-[2] py-4 bg-indigo-600 text-white rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest shadow-2xl shadow-indigo-600/30 active:scale-[0.98] transition-all flex items-center justify-center gap-2 hover:bg-indigo-700">
                  {saving ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />} Finalize Entry
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ChartOfAccountManagement;
