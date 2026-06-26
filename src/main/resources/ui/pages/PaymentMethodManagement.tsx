
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { paymentMethodService } from '../services/paymentmethodapiservice';
import { coaService } from '../services/coaapiservice';
import { PaymentMethodDTO, ChartOfAccountDTO } from '../types';
import { 
  Loader2, Plus, Search, Edit2, X, 
  CreditCard, BookOpen, Layers, Save, Trash2,
  ChevronDown, RotateCcw, CheckCircle2, AlertCircle
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

interface COAOption {
  id: number;
  displayName: string;
}

const PaymentMethodManagement: React.FC = () => {
  const [methods, setMethods] = useState<PaymentMethodDTO[]>([]);
  const [coaTree, setCoaTree] = useState<ChartOfAccountDTO[]>([]);
  const [flatCoa, setFlatCoa] = useState<ChartOfAccountDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMethod, setEditingMethod] = useState<PaymentMethodDTO | null>(null);
  const [formData, setFormData] = useState<Partial<PaymentMethodDTO>>({
    methodName: '',
    active: true,
    accountId: null
  });
  const [saving, setSaving] = useState(false);

  const flattenCoa = useCallback((items: ChartOfAccountDTO[], result: ChartOfAccountDTO[] = []) => {
    items.forEach(item => {
      result.push(item);
      if (item.children && item.children.length > 0) flattenCoa(item.children, result);
    });
    return result;
  }, []);

  const fetchData = useCallback(async () => {
    try {
      const [pData, cData] = await Promise.all([
        paymentMethodService.getAll(),
        coaService.getTree()
      ]);
      setMethods(pData);
      setCoaTree(cData);
      setFlatCoa(flattenCoa(cData));
    } catch (error) {
      console.error("Failed to load payment methods", error);
    } finally {
      setLoading(false);
    }
  }, [flattenCoa]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  useWebsocket('/topic/payment-method', fetchData);

  const handleOpenModal = (method?: PaymentMethodDTO) => {
    if (method) {
      setEditingMethod(method);
      setFormData({
        methodName: method.methodName,
        active: method.active,
        accountId: method.accountId
      });
    } else {
      setEditingMethod(null);
      setFormData({
        methodName: '',
        active: true,
        accountId: null
      });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingMethod) {
        await paymentMethodService.update(editingMethod.id, formData);
      } else {
        await paymentMethodService.create(formData);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Payment Method Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Operation failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Remove Payment Method?',
      text: "Permanent action. Ensure no active invoices are linked to this method.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, Delete'
    });

    if (result.isConfirmed) {
      try {
        await paymentMethodService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'Deleted Successfully', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Restricted', error.message || 'Deletion failed.', 'error');
      }
    }
  };

  const getHierarchyOptions = useCallback((nodes: ChartOfAccountDTO[], level = 0): COAOption[] => {
    return nodes.reduce((acc: COAOption[], node) => {
      const indentation = "\u00A0\u00A0".repeat(level * 2);
      acc.push({ id: node.id, displayName: `${indentation}↳ ${node.accountName} (${node.code})` });
      if (node.children && node.children.length > 0) {
        acc.push(...getHierarchyOptions(node.children, level + 1));
      }
      return acc;
    }, []);
  }, []);

  const coaOptions = useMemo(() => getHierarchyOptions(coaTree), [coaTree, getHierarchyOptions]);

  const filteredMethods = methods.filter(m => 
    m.methodName.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden text-left">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 shrink-0 px-1 text-left">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100 shrink-0">
            <CreditCard size={24} />
          </div>
          <div>
            <h2 className="text-base font-black text-slate-800 tracking-tight uppercase">Payment Channels</h2>
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest mt-0.5 flex items-center gap-1.5">
              <BookOpen size={10} className="text-indigo-400" /> Integrated Transaction Gates
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
             <Plus size={18} /> New Payment Channel
           </button>
        </div>
      </div>

      {/* Main Container */}
      <div className="bg-white rounded-[2rem] shadow-2xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden mx-1">
        <div className="p-5 border-b border-slate-100">
           <div className="relative group max-w-md">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={20} />
            <input 
              type="text" 
              placeholder="Filter by name..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-12 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl outline-none text-[12px] font-bold focus:bg-white focus:border-indigo-500 transition-all shadow-sm"
            />
          </div>
        </div>

        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[800px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
              <tr>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest">Channel Identity</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest">Accounting Bridge (COA)</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Active Status</th>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredMethods.length > 0 ? filteredMethods.map((method) => (
                <tr key={method.id} className="group hover:bg-slate-50 transition-colors cursor-pointer" onClick={() => handleOpenModal(method)}>
                  <td className="px-8 py-5 text-left">
                    <div className="flex items-center gap-4">
                      <div className={`w-10 h-10 rounded-2xl flex items-center justify-center transition-all ${method.active ? 'bg-indigo-50 text-indigo-600 border border-indigo-100 shadow-sm' : 'bg-slate-100 text-slate-400'}`}>
                        <CreditCard size={20} />
                      </div>
                      <p className="text-[13px] font-black text-slate-800 tracking-tight">{method.methodName}</p>
                    </div>
                  </td>
                  <td className="px-4 py-5">
                    <div className="flex items-center gap-2">
                      <Layers size={14} className="text-slate-300" />
                      <span className="text-[11px] font-bold text-slate-600">{method.accountName || 'No Link Established'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-5 text-center">
                    <div className="flex justify-center">
                      <span className={`px-4 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest border flex items-center gap-2 ${method.active ? 'bg-emerald-50 text-emerald-600 border-emerald-100 shadow-sm' : 'bg-slate-50 text-slate-400 border-slate-200'}`}>
                        {method.active ? <CheckCircle2 size={12} /> : <AlertCircle size={12} />}
                        {method.active ? 'Operational' : 'Suspended'}
                      </span>
                    </div>
                  </td>
                  <td className="px-8 py-5 text-right">
                    <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={(e) => { e.stopPropagation(); handleOpenModal(method); }} className="p-2.5 bg-white text-slate-400 hover:text-indigo-600 border border-slate-100 rounded-xl shadow-sm transition-all">
                        <Edit2 size={14} />
                      </button>
                      <button onClick={(e) => { e.stopPropagation(); handleDelete(method.id); }} className="p-2.5 bg-white text-slate-400 hover:text-rose-600 border border-slate-100 rounded-xl shadow-sm transition-all">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={4} className="px-6 py-40 text-center">
                    <div className="flex flex-col items-center gap-5">
                      <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center border border-dashed border-slate-200">
                        <CreditCard className="text-slate-200" size={40} />
                      </div>
                      <p className="text-sm font-black uppercase tracking-widest text-slate-400 italic">No Payment Channels Detected</p>
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
            Total Channels Enrolled: <span className="text-indigo-600 font-black">{methods.length}</span>
          </span>
          <div className="flex items-center gap-6">
             <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-emerald-500"></div><span className="text-[9px] font-black text-slate-500 uppercase">Active</span></div>
             <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-slate-300"></div><span className="text-[9px] font-black text-slate-500 uppercase">Suspended</span></div>
          </div>
        </div>
      </div>

      {/* Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200 text-left">
          <div className="bg-white w-full max-w-lg rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between bg-slate-50/50 text-left">
              <div className="text-left">
                <h3 className="text-[14px] font-black text-slate-800 uppercase tracking-tight">{editingMethod ? 'Update Channel' : 'New Payment Gate'}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-1 tracking-widest italic">Channel Specs & Account Pairing</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2.5 hover:bg-white hover:shadow-md rounded-2xl transition-all border border-transparent hover:border-slate-100"><X size={20} className="text-slate-400" /></button>
            </div>
            
            <form onSubmit={handleSave} className="p-8 space-y-6 text-left max-h-[75vh] overflow-y-auto custom-scrollbar">
              <div className="space-y-2 text-left">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Display Name</label>
                <div className="relative group">
                  <CreditCard className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input 
                    type="text" required value={formData.methodName} 
                    onChange={(e) => setFormData({...formData, methodName: e.target.value})} 
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                    placeholder="e.g. KBZ Pay, Cash in Hand"
                  />
                </div>
              </div>

              <div className="space-y-2 text-left">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Accounting Entry (COA)</label>
                <div className="relative group">
                  <BookOpen className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <select 
                    required value={formData.accountId || ''} 
                    onChange={(e) => setFormData({...formData, accountId: e.target.value ? Number(e.target.value) : null})}
                    className="w-full pl-12 pr-10 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 appearance-none shadow-sm transition-all"
                  >
                    <option value="" disabled>Select Target Account</option>
                    {coaOptions.map(opt => <option key={opt.id} value={opt.id}>{opt.displayName}</option>)}
                  </select>
                  <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                </div>
                <p className="text-[9px] text-slate-400 font-bold ml-1 uppercase">Payments through this channel will hit this ledger account.</p>
              </div>

              <div className="flex items-center gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-200">
                <div className="flex items-center gap-3">
                  <input 
                    type="checkbox" 
                    id="active_check"
                    checked={formData.active}
                    onChange={(e) => setFormData({...formData, active: e.target.checked})}
                    className="w-5 h-5 rounded-lg border-slate-300 text-indigo-600 focus:ring-indigo-500 transition-all cursor-pointer"
                  />
                  <label htmlFor="active_check" className="text-[12px] font-black text-slate-700 uppercase tracking-widest cursor-pointer select-none">Channel is Operational</label>
                </div>
              </div>

              <div className="flex gap-4 pt-4 shrink-0">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-4 border border-slate-200 rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm">Discard</button>
                <button type="submit" disabled={saving} className="flex-[2] py-4 bg-indigo-600 text-white rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest shadow-2xl shadow-indigo-600/30 active:scale-[0.98] transition-all flex items-center justify-center gap-2 hover:bg-indigo-700">
                  {saving ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />} Deploy Gate
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PaymentMethodManagement;
