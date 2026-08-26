
import React, { useEffect, useState, useCallback } from 'react';
import { roleService } from '../services/roleapiservice';
import { permissionService } from '../services/permissionapiservice';
import { RoleDTO, PermissionDTO } from '../types';
import { 
  Loader2, Plus, ShieldCheck, Key, Settings2, Search, 
  Trash2, Edit2, Save, X, CheckSquare, Square,
  ChevronDown
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const RoleManagement: React.FC = () => {
  const [roles, setRoles] = useState<RoleDTO[]>([]);
  const [allPermissions, setAllPermissions] = useState<PermissionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleDTO | null>(null);
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([]);
  const [permSearchTerm, setPermSearchTerm] = useState('');
  const [saving, setSaving] = useState(false);

  const recommendedPermissionNames = (roleName: string): string[] => {
    const role = roleName.toUpperCase();
    const read = [
      'CAN_ACCESS_CUSTOMER_READ', 'CAN_ACCESS_PRODUCT_READ', 'CAN_ACCESS_SERVICE_READ',
      'CAN_ACCESS_SERVICE_JOB_READ', 'CAN_ACCESS_BOOKING_READ',
    ];
    if (role.includes('ADMIN')) return allPermissions.map(permission => permission.name);
    if (role.includes('MANAGER')) return [
      ...read, 'CAN_ACCESS_SALE_READ', 'CAN_ACCESS_SALE_CREATE', 'CAN_ACCESS_SALE_UPDATE',
      'CAN_ACCESS_PURCHASE_READ', 'CAN_ACCESS_PURCHASE_CREATE', 'CAN_ACCESS_PURCHASE_UPDATE',
      'CAN_ACCESS_SUPPLIER_READ', 'CAN_ACCESS_STOCK_READ', 'CAN_ACCESS_STOCK_UPDATE',
      'CAN_ACCESS_BOOKING_UPDATE', 'CAN_ACCESS_SERVICE_JOB_UPDATE', 'CAN_ACCESS_SERVICE_JOB_SETTLE',
      'CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN',
      'CAN_ACCESS_REPORT_READ', 'CAN_ACCESS_CREDIT_ALERT_READ', 'CAN_ACCESS_CREDIT_OVERRIDE_APPROVE',
    ];
    if (role.includes('CASHIER') || role.includes('SALE')) return [
      'CAN_ACCESS_CUSTOMER_CREATE', ...read, 'CAN_ACCESS_SALE_READ', 'CAN_ACCESS_SALE_CREATE',
      'CAN_ACCESS_SALE_UPDATE', 'CAN_ACCESS_SALE_RETURN_CREATE', 'CAN_ACCESS_PAYMENT_TRANSACTION_CREATE',
      'CAN_ACCESS_SERVICE_JOB_CREATE', 'CAN_ACCESS_SERVICE_JOB_UPDATE', 'CAN_ACCESS_SERVICE_JOB_SETTLE',
      'CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN', 'CAN_ACCESS_BOOKING_CREATE', 'CAN_ACCESS_BOOKING_UPDATE',
      'CAN_ACCESS_BOOKING_CONVERT_JOB',
    ];
    if (role.includes('TECHNICIAN') || role.includes('TECH')) return [
      'CAN_ACCESS_CUSTOMER_READ', 'CAN_ACCESS_PRODUCT_READ', 'CAN_ACCESS_SERVICE_READ',
      'CAN_ACCESS_SERVICE_JOB_READ', 'CAN_ACCESS_SERVICE_JOB_UPDATE', 'CAN_ACCESS_SERVICE_JOB_REWORK',
      'CAN_ACCESS_BOOKING_READ', 'CAN_ACCESS_BOOKING_UPDATE', 'CAN_ACCESS_STAFF_READ',
    ];
    if (role.includes('INVENTORY') || role.includes('STOCK')) return [
      'CAN_ACCESS_PRODUCT_READ', 'CAN_ACCESS_PRODUCT_CREATE', 'CAN_ACCESS_PRODUCT_UPDATE',
      'CAN_ACCESS_PRODUCT_SERIAL_READ', 'CAN_ACCESS_PRODUCT_SERIAL_UPDATE',
      'CAN_ACCESS_SUPPLIER_READ', 'CAN_ACCESS_PURCHASE_READ', 'CAN_ACCESS_STOCK_READ',
      'CAN_ACCESS_STOCK_UPDATE', 'CAN_ACCESS_STOCK_ADJUSTMENT_CREATE', 'CAN_ACCESS_STOCK_ADJUSTMENT_READ',
    ];
    if (role.includes('ACCOUNT') || role.includes('FINANCE')) return [
      'CAN_ACCESS_CUSTOMER_READ', 'CAN_ACCESS_SUPPLIER_READ', 'CAN_ACCESS_SALE_READ',
      'CAN_ACCESS_PURCHASE_READ', 'CAN_ACCESS_PAYMENT_TRANSACTION_READ', 'CAN_ACCESS_ACCOUNT_BALANCE_READ',
      'CAN_ACCESS_JOURNAL_READ', 'CAN_ACCESS_EXPENSE_READ', 'CAN_ACCESS_INCOME_READ',
      'CAN_ACCESS_CREDIT_ALERT_READ', 'CAN_ACCESS_CUSTOMER_PAYMENT_READ', 'CAN_ACCESS_REPORT_READ',
    ];
    return read;
  };

  const fetchData = useCallback(async () => {
    try {
      const [roleData, permData] = await Promise.all([
        roleService.getAll(),
        permissionService.getAll()
      ]);
      setRoles(roleData);
      setAllPermissions(permData);
    } catch (error) {
      console.error("Failed to load RBAC data", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);
  useRefreshOnTabActivate(fetchData);

  const handleWsMessage = useCallback(() => {
    setWsStatus('online');
    fetchData();
    const timer = setTimeout(() => setWsStatus('offline'), 5000);
    return () => clearTimeout(timer);
  }, [fetchData]);

  useWebsocket('/topic/role', handleWsMessage);

  const handleOpenRoleModal = (role?: RoleDTO) => {
    if (role) {
      setEditingRole(role);
      setFormData({ name: role.name, description: role.description || '' });
    } else {
      setEditingRole(null);
      setFormData({ name: 'ROLE_', description: '' });
    }
    setIsModalOpen(true);
  };

  const handleOpenAssignModal = (role: RoleDTO) => {
    setEditingRole(role);
    setSelectedPermissionIds(role.permissions.map(p => p.id));
    setPermSearchTerm('');
    setIsAssignModalOpen(true);
  };

  const handleSaveRole = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingRole) {
        await roleService.update(editingRole.id, formData);
      } else {
        await roleService.create(formData);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Role Saved', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Action failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Delete Role?',
      text: "Users assigned to this role might lose access.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'Yes, delete it'
    });

    if (result.isConfirmed) {
      try {
        await roleService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'Role Deleted', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message || 'Delete failed', 'error');
      }
    }
  };

  const handleAssignPermissions = async () => {
    if (!editingRole) return;
    setSaving(true);
    try {
      await roleService.assignPermissions(editingRole.id, selectedPermissionIds);
      Swal.fire({ icon: 'success', title: 'Permissions Synced', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      setIsAssignModalOpen(false);
      fetchData();
    } catch (error: any) {
      Swal.fire('Failed', error.message || 'Sync failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const togglePermission = (id: number) => {
    setSelectedPermissionIds(prev => 
      prev.includes(id) ? prev.filter(pid => pid !== id) : [...prev, id]
    );
  };

  const addCustomerHistoryPermissions = () => {
    const requiredNames = new Set([
      'CAN_ACCESS_CUSTOMER_READ',
      'CAN_ACCESS_SALE_READ',
      'CAN_ACCESS_BOOKING_READ',
      'CAN_ACCESS_SERVICE_JOB_READ'
    ]);
    const requiredIds = allPermissions.filter(permission => requiredNames.has(permission.name)).map(permission => permission.id);
    setSelectedPermissionIds(prev => Array.from(new Set([...prev, ...requiredIds])));
  };

  const addRecommendedPermissions = () => {
    if (!editingRole) return;
    const recommendedNames = new Set(recommendedPermissionNames(editingRole.name));
    const recommendedIds = allPermissions
      .filter(permission => recommendedNames.has(permission.name))
      .map(permission => permission.id);
    setSelectedPermissionIds(previous => Array.from(new Set([...previous, ...recommendedIds])));
  };

  const recommendedMissing = editingRole
    ? allPermissions.filter(permission =>
      recommendedPermissionNames(editingRole.name).includes(permission.name) &&
      !selectedPermissionIds.includes(permission.id)
    )
    : [];


  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  return (
    <div className="space-y-4 animate-in fade-in duration-400">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-left">
        <div>
          <h2 className="text-lg font-bold text-slate-800 tracking-tight">Security Roles</h2>
          <p className="text-slate-500 text-xs uppercase font-black tracking-widest mt-1">Access Group Management</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <input 
              type="text" placeholder="Search roles..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-lg outline-none text-xs w-48 focus:border-indigo-500 transition-all shadow-sm"
            />
          </div>
          <button 
            onClick={() => handleOpenRoleModal()}
            className="bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold shadow-md hover:bg-indigo-700 transition-all flex items-center gap-1.5"
          >
            <Plus size={14} /> Create Role
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
        {roles.filter(r => r.name.toLowerCase().includes(searchTerm.toLowerCase())).map(role => (
          <div key={role.id} className="bg-white rounded-xl p-5 border border-slate-100 shadow-sm hover:shadow-md transition-all relative group overflow-hidden">
            <div className="absolute top-0 right-0 p-3 opacity-0 group-hover:opacity-100 flex gap-1 bg-white/80 backdrop-blur-sm rounded-bl-xl border-l border-b border-slate-50">
              <button onClick={() => handleOpenRoleModal(role)} className="p-1.5 text-slate-400 hover:text-indigo-600 transition-colors"><Edit2 size={14} /></button>
              <button onClick={() => handleDelete(role.id)} className="p-1.5 text-slate-400 hover:text-rose-600 transition-colors"><Trash2 size={14} /></button>
            </div>
            
            <div className="flex items-center gap-3 mb-3 text-left">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 flex items-center justify-center text-indigo-600 shadow-inner"><ShieldCheck size={22} /></div>
              <div>
                <h3 className="text-sm font-black text-slate-800 tracking-tight">{role.name}</h3>
                <p className="text-[9px] text-slate-400 font-bold uppercase tracking-widest">ID: #{role.id}</p>
              </div>
            </div>

            <p className="text-[11px] text-slate-500 mb-4 line-clamp-2 min-h-[32px] font-medium leading-relaxed text-left">{role.description || "No specific description defined for this security role."}</p>

            <div className="space-y-3">
              <div className="flex items-center justify-between text-[9px] font-black text-slate-400 uppercase tracking-widest border-b border-slate-50 pb-2">
                <span>Access Scope</span>
                <span className="bg-indigo-600 text-white px-2 py-0.5 rounded-full shadow-sm">{role.permissions.length}</span>
              </div>
              
              <button 
                onClick={() => handleOpenAssignModal(role)}
                className="w-full py-2 bg-slate-50 hover:bg-indigo-600 text-slate-500 hover:text-white rounded-lg text-[10px] font-black uppercase tracking-widest transition-all border border-slate-100 flex items-center justify-center gap-1.5 active:scale-95"
              >
                <Settings2 size={12} />
                Manage Permissions
              </button>
            </div>
          </div>
        ))}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-[2rem] shadow-2xl overflow-hidden border border-slate-200 animate-in zoom-in-95">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-sm font-black text-slate-800 uppercase tracking-tight">{editingRole ? 'Update Role' : 'New Role'}</h3>
              <button onClick={() => setIsModalOpen(false)} className="p-2 hover:bg-slate-50 rounded-xl text-slate-400 transition-colors"><X size={18} /></button>
            </div>
            <form onSubmit={handleSaveRole} className="p-6 space-y-4 text-left">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Role Identifier</label>
                <input
                  type="text" required value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value.toUpperCase().replace(/\s/g, '_')})}
                  className="w-full px-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all"
                  placeholder="e.g. ROLE_MANAGER"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Description</label>
                <textarea
                  rows={3} value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  className="w-full px-5 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[11px] font-bold outline-none focus:border-indigo-500 transition-all resize-none"
                  placeholder="Define role scope..."
                />
              </div>
              <div className="flex gap-3 pt-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-3 border border-slate-200 rounded-2xl text-[10px] font-black uppercase bg-white text-slate-500 hover:bg-slate-50 transition-colors">Discard</button>
                <button type="submit" disabled={saving} className="flex-1 py-3 bg-indigo-600 text-white rounded-2xl text-[10px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center justify-center gap-2">
                  {saving && <Loader2 size={14} className="animate-spin" />}
                  Confirm
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {isAssignModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-xl rounded-[2rem] shadow-2xl overflow-hidden border border-slate-200 max-h-[80vh] flex flex-col animate-in zoom-in-95">
            <div className="p-6 border-b border-slate-100 flex items-center justify-between">
              <div className="text-left">
                <h3 className="text-sm font-black text-slate-800 uppercase tracking-tight">Permissions for {editingRole?.name}</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest mt-1">Assign Security Keys</p>
              </div>
              <button onClick={() => setIsAssignModalOpen(false)} className="p-2 hover:bg-slate-50 rounded-xl text-slate-400 transition-colors"><X size={20} /></button>
            </div>

            <div className="px-6 py-4 border-b border-slate-100 bg-slate-50/50 flex flex-col sm:flex-row items-center gap-3">
              <div className="relative flex-1 w-full">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
                <input 
                  type="text" placeholder="Search keys..." 
                  value={permSearchTerm}
                  onChange={(e) => setPermSearchTerm(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 bg-white border border-slate-200 rounded-xl outline-none text-[11px] font-bold shadow-sm focus:border-indigo-500 transition-all"
                />
              </div>
              <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
                <button onClick={addRecommendedPermissions} title="ဒီ Role အတွက် မရှိသေးသော သင့်တော်သည့် Permission များကိုသာ ထည့်မည်" className="flex-1 sm:flex-none px-3 py-2 text-[10px] font-black uppercase tracking-wider bg-amber-50 text-amber-700 rounded-xl border border-amber-200 hover:bg-amber-100 transition-colors">Recommended +</button>
                <button onClick={addCustomerHistoryPermissions} title="Customer, Sale, Booking နှင့် Service Job Read permissions ထည့်မည်" className="flex-1 sm:flex-none px-3 py-2 text-[10px] font-black uppercase tracking-wider bg-indigo-50 text-indigo-700 rounded-xl border border-indigo-200 hover:bg-indigo-100 transition-colors">Customer History</button>
                <button onClick={() => setSelectedPermissionIds(allPermissions.filter(p => p.name.toLowerCase().includes(permSearchTerm.toLowerCase())).map(p => p.id))} className="flex-1 sm:flex-none px-3 py-2 text-[10px] font-black uppercase tracking-wider bg-emerald-50 text-emerald-600 rounded-xl border border-emerald-100 hover:bg-emerald-100 transition-colors">All</button>
                <button onClick={() => setSelectedPermissionIds([])} className="flex-1 sm:flex-none px-3 py-2 text-[10px] font-black uppercase tracking-wider bg-rose-50 text-rose-600 rounded-xl border border-rose-100 hover:bg-rose-100 transition-colors">None</button>
              </div>
            </div>

            <div className="mx-6 mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-left">
              <div className="flex items-center justify-between gap-3">
                <p className="text-[10px] font-black uppercase tracking-widest text-amber-800">ထည့်သင့်သော Permission</p>
                <span className="rounded-full bg-white px-2 py-0.5 text-[10px] font-black text-amber-700">{recommendedMissing.length} ခု မရှိသေး</span>
              </div>
              <p className="mt-1 text-[10px] leading-5 text-amber-700">
                Recommended + ကိုနှိပ်ပါက မရှိသေးသော permission များကိုသာ ထပ်ပေါင်းမည်။ ရှိပြီးသား permission များ မဖယ်ပါ။
              </p>
              {recommendedMissing.length > 0 && (
                <p className="mt-1 line-clamp-2 text-[9px] font-medium text-amber-800">
                  {recommendedMissing.map(permission => permission.name).join(' · ')}
                </p>
              )}
              {editingRole && /TECH/i.test(editingRole.name) && (
                <p className="mt-2 text-[10px] leading-5 text-rose-700">
                  ပြုပြင်သူ Role တွင် <b>CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN</b> မထည့်ပါနှင့်။ ထည့်ရင် တခြား ပြုပြင်သူကိုပါ ရွေးနိုင်သွားမည်။
                </p>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-6 custom-scrollbar">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {allPermissions.filter(p => p.name.toLowerCase().includes(permSearchTerm.toLowerCase())).map(perm => {
                  const isSelected = selectedPermissionIds.includes(perm.id);
                  return (
                    <button 
                      key={perm.id}
                      onClick={() => togglePermission(perm.id)}
                      className={`flex items-center gap-3 p-4 rounded-2xl border transition-all text-left group ${
                        isSelected ? 'bg-indigo-50 border-indigo-200 text-indigo-700 shadow-sm' : 'bg-white border-slate-100 text-slate-600 hover:border-slate-300'
                      }`}
                    >
                      <div className={`p-2 rounded-xl border transition-colors ${isSelected ? 'bg-white border-indigo-200 text-indigo-600' : 'bg-slate-50 border-slate-100 text-slate-300 group-hover:text-slate-400'}`}>
                        {isSelected ? <CheckSquare size={16} /> : <Square size={16} />}
                      </div>
                      <div>
                        <p className="text-[11px] font-black uppercase tracking-tight">{perm.name}</p>
                        <p className={`text-[9px] font-medium mt-0.5 ${isSelected ? 'text-indigo-400' : 'text-slate-400'}`}>{perm.description || "System authority key"}</p>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 bg-white flex items-center justify-between shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
              <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">{selectedPermissionIds.length} Keys Active</span>
              <div className="flex gap-2">
                <button onClick={() => setIsAssignModalOpen(false)} className="px-5 py-2 rounded-xl border border-slate-200 text-[10px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-colors">Cancel</button>
                <button onClick={handleAssignPermissions} disabled={saving} className="px-6 py-2 rounded-xl bg-indigo-600 text-white text-[10px] font-black uppercase tracking-widest shadow-xl shadow-indigo-600/20 active:scale-95 transition-all">
                  Apply Updates
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RoleManagement;
