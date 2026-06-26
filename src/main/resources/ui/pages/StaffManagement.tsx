
import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { staffService } from '../services/staffapiservice';
import { StaffDTO } from '../types';
import { 
  Loader2, Plus, Search, Edit2, X, 
  UserCircle, Phone, Briefcase, Save, Trash2,
  CheckCircle2, XCircle, RotateCcw, AlertCircle,
  UserCheck
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

const StaffManagement: React.FC = () => {
  const [staffList, setStaffList] = useState<StaffDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterActive, setFilterActive] = useState<'all' | 'active' | 'inactive'>('all');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingStaff, setEditingStaff] = useState<StaffDTO | null>(null);
  const [formData, setFormData] = useState<Partial<StaffDTO>>({
    name: '',
    phone: '',
    role: '',
    active: true
  });
  const [saving, setSaving] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const data = await staffService.getAll();
      setStaffList(data);
    } catch (error) {
      console.error("Failed to load staff directory", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // WebSocket topic matches backend: /topic/staff
  useWebsocket('/topic/staff', fetchData);

  const handleOpenModal = (staff?: StaffDTO) => {
    if (staff) {
      setEditingStaff(staff);
      setFormData({
        name: staff.name,
        phone: staff.phone || '',
        role: staff.role,
        active: staff.active
      });
    } else {
      setEditingStaff(null);
      setFormData({
        name: '',
        phone: '',
        role: '',
        active: true
      });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingStaff) {
        await staffService.update(editingStaff.id, formData);
      } else {
        await staffService.create(formData);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Registry Updated', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Operation failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Deactivate Staff?',
      text: "Warning: This employee will be marked as inactive. Active credentials will be restricted.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, Deactivate'
    });

    if (result.isConfirmed) {
      try {
        await staffService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'Status Updated', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message || 'Action failed.', 'error');
      }
    }
  };

  const filteredStaff = useMemo(() => {
    const s = searchTerm.toLowerCase().trim();
    return staffList.filter(staff => {
      const matchesSearch = !s || 
        staff.name.toLowerCase().includes(s) || 
        (staff.phone && staff.phone.includes(s)) ||
        staff.role.toLowerCase().includes(s);
      
      const matchesFilter = filterActive === 'all' || 
        (filterActive === 'active' && staff.active) || 
        (filterActive === 'inactive' && !staff.active);
        
      return matchesSearch && matchesFilter;
    });
  }, [staffList, searchTerm, filterActive]);

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden text-left">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 shrink-0 px-1 text-left">
        <div className="flex items-center gap-4 text-left">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100 shrink-0">
            <UserCheck size={24} />
          </div>
          <div className="text-left">
            <h2 className="text-base font-black text-slate-800 tracking-tight uppercase">HR Directory</h2>
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest mt-0.5 flex items-center gap-1.5">
              <Briefcase size={10} className="text-indigo-400" /> Professional Staff Management
            </p>
          </div>
        </div>
        <button 
          onClick={() => handleOpenModal()} 
          className="bg-indigo-600 text-white px-5 py-2.5 rounded-2xl text-[11px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center gap-2 hover:bg-indigo-700"
        >
          <Plus size={18} /> Enroll Staff
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white p-5 rounded-[2rem] border border-slate-200 shadow-sm mx-1">
        <div className="flex flex-col lg:flex-row gap-5 items-center">
          <div className="relative flex-1 group w-full">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={20} />
            <input 
              type="text" 
              placeholder="Search by name, role or contact..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-12 pr-4 py-3.5 bg-slate-50 border border-slate-200 rounded-2xl outline-none text-[13px] font-bold focus:bg-white focus:border-indigo-500 transition-all shadow-sm"
            />
          </div>
          
          <div className="flex items-center gap-3 bg-slate-50 p-1.5 rounded-2xl border border-slate-200">
            <span className="text-[9px] font-black text-slate-400 uppercase tracking-widest px-3">Filter Status</span>
            <div className="flex gap-1">
              {['all', 'active', 'inactive'].map((f) => (
                <button
                  key={f}
                  onClick={() => setFilterActive(f as any)}
                  className={`px-4 py-2 rounded-xl text-[10px] font-black uppercase transition-all ${
                    filterActive === f ? 'bg-white text-indigo-600 shadow-md border border-slate-100' : 'text-slate-500 hover:text-indigo-600'
                  }`}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>

          <button 
            onClick={() => { setSearchTerm(''); setFilterActive('all'); fetchData(); }}
            className="p-3.5 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-2xl transition-all border border-transparent hover:border-indigo-100 bg-white shadow-sm"
          >
            <RotateCcw size={20} />
          </button>
        </div>
      </div>

      {/* Table Content */}
      <div className="bg-white rounded-[2rem] shadow-2xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden mx-1">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[900px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm text-left">
              <tr>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Personnel Profile</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Designation</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">Contact Info</th>
                <th className="px-4 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Engagement</th>
                <th className="px-8 py-5 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredStaff.length > 0 ? filteredStaff.map((staff) => (
                <tr key={staff.id} className="hover:bg-slate-50/50 transition-all group cursor-pointer" onClick={() => handleOpenModal(staff)}>
                  <td className="px-8 py-5">
                    <div className="flex items-center gap-4 text-left">
                      <div className={`w-11 h-11 rounded-2xl border flex items-center justify-center shadow-sm transition-all ${staff.active ? 'bg-indigo-50 border-indigo-100 text-indigo-600 group-hover:bg-indigo-600 group-hover:text-white' : 'bg-slate-50 border-slate-200 text-slate-300'}`}>
                        <UserCircle size={22} />
                      </div>
                      <div className="text-left">
                        <p className={`text-[14px] font-black tracking-tight leading-tight ${staff.active ? 'text-slate-800' : 'text-slate-400 line-through'}`}>{staff.name}</p>
                        <p className="text-[9px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">ID: STF-{staff.id.toString().padStart(4, '0')}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-5 text-left">
                    <div className="flex items-center gap-2">
                      <Briefcase size={14} className="text-slate-300" />
                      <span className="text-[12px] font-bold text-slate-600 uppercase tracking-tight">{staff.role}</span>
                    </div>
                  </td>
                  <td className="px-4 py-5 text-left">
                    <div className="flex items-center gap-2">
                      <Phone size={14} className="text-slate-300" />
                      <span className="text-[12px] font-bold text-slate-600 tracking-tight">{staff.phone || 'N/A'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-5 text-center">
                    <div className="flex justify-center">
                      <span className={`px-4 py-1.5 rounded-full text-[9px] font-black uppercase tracking-widest border flex items-center gap-2 ${staff.active ? 'bg-emerald-50 text-emerald-600 border-emerald-100 shadow-sm' : 'bg-rose-50 text-rose-600 border-rose-100 shadow-sm'}`}>
                        {staff.active ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                        {staff.active ? 'Active' : 'Terminated'}
                      </span>
                    </div>
                  </td>
                  <td className="px-8 py-5 text-right">
                    <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-all">
                      <button onClick={(e) => { e.stopPropagation(); handleOpenModal(staff); }} className="p-2.5 bg-indigo-50 text-indigo-600 border border-indigo-100 hover:bg-indigo-600 hover:text-white rounded-xl transition-all shadow-sm">
                        <Edit2 size={16} />
                      </button>
                      <button onClick={(e) => { e.stopPropagation(); handleDelete(staff.id); }} className="p-2.5 bg-rose-50 text-rose-600 border border-rose-100 hover:bg-rose-600 hover:text-white rounded-xl transition-all shadow-sm">
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr>
                  <td colSpan={5} className="px-6 py-40 text-center">
                    <div className="flex flex-col items-center gap-5">
                      <div className="w-20 h-20 bg-slate-50 rounded-full flex items-center justify-center border border-dashed border-slate-200 text-left">
                        <UserCheck className="text-slate-200" size={40} />
                      </div>
                      <p className="text-sm font-black uppercase tracking-widest text-slate-400 italic">No Personnel Records Matching Filters</p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Footer info */}
        <div className="px-10 py-5 bg-slate-50 border-t border-slate-100 flex items-center justify-between text-left">
          <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest text-left">
             Total Staff Strength: <span className="text-indigo-600 font-black">{staffList.length}</span>
          </span>
          <div className="flex items-center gap-6">
             <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-emerald-500"></div><span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">On-Duty ({staffList.filter(s => s.active).length})</span></div>
             <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-rose-500"></div><span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">Off-Boarded ({staffList.filter(s => !s.active).length})</span></div>
          </div>
        </div>
      </div>

      {/* Enrollment Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200 text-left">
          <div className="bg-white w-full max-w-lg rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between bg-slate-50/50 text-left">
              <div className="text-left">
                <h3 className="text-[15px] font-black text-slate-800 uppercase tracking-tight">{editingStaff ? 'Update Profile' : 'Enroll Personnel'}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-1 tracking-widest italic">Identity & Contract Management</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2.5 hover:bg-white hover:shadow-md rounded-2xl transition-all border border-transparent hover:border-slate-100"><X size={20} className="text-slate-400" /></button>
            </div>
            
            <form onSubmit={handleSave} className="p-8 space-y-6 text-left">
              <div className="space-y-2 text-left">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block">Full Legal Name</label>
                <div className="relative group text-left">
                  <UserCircle className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input 
                    type="text" required value={formData.name} 
                    onChange={(e) => setFormData({...formData, name: e.target.value})} 
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                    placeholder="Enter personnel name"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-left">
                <div className="space-y-2 text-left">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block">Phone Identification</label>
                  <div className="relative group text-left">
                    <Phone className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                    <input 
                      type="text" value={formData.phone} 
                      onChange={(e) => setFormData({...formData, phone: e.target.value})} 
                      className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                      placeholder="09xxxxxxxx"
                    />
                  </div>
                </div>

                <div className="space-y-2 text-left">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 block text-left">Job Designation (Role)</label>
                  <div className="relative group text-left">
                    <Briefcase className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                    <input 
                      type="text" required value={formData.role} 
                      onChange={(e) => setFormData({...formData, role: e.target.value})} 
                      className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm" 
                      placeholder="e.g. Technician, Manager"
                    />
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-4 p-5 bg-slate-50 rounded-2xl border border-slate-200 text-left">
                <div className="flex items-center gap-3 text-left">
                  <input 
                    type="checkbox" 
                    id="staff_active_check"
                    checked={formData.active}
                    onChange={(e) => setFormData({...formData, active: e.target.checked})}
                    className="w-5 h-5 rounded-lg border-slate-300 text-indigo-600 focus:ring-indigo-500 transition-all cursor-pointer"
                  />
                  <label htmlFor="staff_active_check" className="text-[12px] font-black text-slate-700 uppercase tracking-widest cursor-pointer select-none">Engagement Active</label>
                </div>
                {!formData.active && (
                  <div className="flex items-center gap-1.5 ml-auto animate-in slide-in-from-right-2 text-left">
                    <AlertCircle size={14} className="text-rose-500" />
                    <span className="text-[9px] font-black text-rose-500 uppercase tracking-widest">Termination Logic Enabled</span>
                  </div>
                )}
              </div>

              <div className="flex gap-4 pt-4 shrink-0 text-left">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-4 border border-slate-200 rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm">Discard</button>
                <button type="submit" disabled={saving} className="flex-[2] py-4 bg-indigo-600 text-white rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest shadow-2xl shadow-indigo-600/30 active:scale-[0.98] transition-all flex items-center justify-center gap-2 hover:bg-indigo-700">
                  {saving ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />} Finalize Profile
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default StaffManagement;
