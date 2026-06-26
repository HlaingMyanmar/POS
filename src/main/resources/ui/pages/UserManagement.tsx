
import React, { useEffect, useState, useCallback } from 'react';
import { userService } from '../services/userapiservice';
import { roleService } from '../services/roleapiservice';
import { UserDTO, RoleDTO } from '../types';
import { 
  Loader2, Plus, Search, Shield, Mail, CheckCircle, XCircle, 
  Trash2, Edit2, Wifi, Save, X, MoreHorizontal, UserCheck, 
  UserPlus, Key, Eye, EyeOff, ShieldCheck
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

const UserManagement: React.FC = () => {
  const [users, setUsers] = useState<UserDTO[]>([]);
  const [availableRoles, setAvailableRoles] = useState<RoleDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isRoleModalOpen, setIsRoleModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserDTO | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [formData, setFormData] = useState({ username: '', email: '', password: '', isActive: true });
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);
  const [roleSearchTerm, setRoleSearchTerm] = useState('');
  const [saving, setSaving] = useState(false);

  const fetchData = useCallback(async () => {
    try {
      const [userData, rolesData] = await Promise.all([
        userService.getAll(),
        roleService.getAll()
      ]);
      setUsers(userData);
      setAvailableRoles(rolesData);
    } catch (error) {
      console.error("Failed to load user data", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleWsMessage = useCallback((message: string) => {
    setWsStatus('online');
    fetchData();
    const timer = setTimeout(() => setWsStatus('offline'), 5000);
    return () => clearTimeout(timer);
  }, [fetchData]);

  useWebsocket('/topic/user', handleWsMessage);

  const handleOpenUserModal = (user?: UserDTO) => {
    if (user) {
      setEditingUser(user);
      setFormData({ username: user.username, email: user.email, password: '', isActive: user.isActive });
    } else {
      setEditingUser(null);
      setFormData({ username: '', email: '', password: '', isActive: true });
    }
    setShowPassword(false);
    setIsModalOpen(true);
  };

  const handleOpenRoleModal = (user: UserDTO) => {
    setEditingUser(user);
    const currentIds = availableRoles
      .filter(r => user.roles.includes(r.name))
      .map(r => r.id);
    setSelectedRoleIds(currentIds);
    setRoleSearchTerm('');
    setIsRoleModalOpen(true);
  };

  const handleSaveUser = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    const payload = { ...formData };
    if (editingUser && !payload.password) delete (payload as any).password;

    try {
      if (editingUser) {
        await userService.update(editingUser.id, payload);
      } else {
        await userService.create(payload);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Action Successful', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Failed', error.message || 'Error', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteUser = async (id: number) => {
    const result = await Swal.fire({
      title: 'Delete User Account?',
      text: "This user will lose all system access.",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      cancelButtonColor: '#94a3b8',
      confirmButtonText: 'Yes, delete'
    });

    if (result.isConfirmed) {
      try {
        await userService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'User Deleted', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message || 'Failed to delete user', 'error');
      }
    }
  };

  const handleAssignRoles = async () => {
    if (!editingUser) return;
    setSaving(true);
    try {
      await userService.assignRoles(editingUser.id, selectedRoleIds);
      setIsRoleModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'Roles Updated', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', 'Role assignment failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  return (
    <div className="space-y-4 animate-in fade-in duration-400">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-slate-800 tracking-tight text-left">Identity Directory</h2>
          <p className="text-slate-500 text-xs text-left">Manage system access accounts.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <input 
              type="text" placeholder="Search..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-lg focus:ring-2 focus:ring-indigo-500/20 outline-none text-xs w-48 transition-all"
            />
          </div>
          <button 
            onClick={() => handleOpenUserModal()}
            className="bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold shadow-md hover:bg-indigo-700 transition-all flex items-center gap-1.5"
          >
            <UserPlus size={14} /> New Account
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
        {users.filter(u => u.username.toLowerCase().includes(searchTerm.toLowerCase())).map(user => (
          <div key={user.id} className="bg-white rounded-xl p-4 border border-slate-100 shadow-sm hover:shadow-md transition-all relative group overflow-hidden">
            <div className={`absolute top-0 left-0 w-full h-0.5 ${user.isActive ? 'bg-indigo-500' : 'bg-slate-300'}`}></div>
            <div className="flex justify-between items-start mb-4">
              <div className="w-10 h-10 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-center text-sm font-black text-slate-400 uppercase">
                {user.username.charAt(0)}
              </div>
              <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                <button onClick={() => handleOpenUserModal(user)} className="p-1.5 text-slate-400 hover:text-indigo-600 transition-colors"><Edit2 size={14} /></button>
                <button onClick={() => handleDeleteUser(user.id)} className="p-1.5 text-slate-400 hover:text-rose-600 transition-colors"><Trash2 size={14} /></button>
              </div>
            </div>
            <h3 className="text-sm font-bold text-slate-800 flex items-center gap-1.5 text-left">{user.username} {user.isActive && <CheckCircle size={12} className="text-emerald-500" />}</h3>
            <p className="text-[10px] text-slate-400 mb-4 truncate text-left">{user.email}</p>
            <div className="space-y-3">
              <div className="flex flex-wrap gap-1 min-h-[32px]">
                {user.roles.map(role => (
                  <span key={role} className="px-1.5 py-0.5 rounded-md bg-indigo-50 text-indigo-600 text-[9px] font-bold border border-indigo-100">
                    {role.replace('ROLE_', '')}
                  </span>
                ))}
              </div>
              <button 
                onClick={() => handleOpenRoleModal(user)}
                className="w-full py-1.5 bg-slate-50 hover:bg-indigo-600 text-slate-500 hover:text-white rounded-lg text-[10px] font-bold transition-all border border-slate-100 flex items-center justify-center gap-1.5"
              >
                <Key size={12} /> Access Control
              </button>
            </div>
          </div>
        ))}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-xl shadow-2xl overflow-hidden border border-slate-200 animate-in zoom-in-95">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">{editingUser ? 'Edit Account' : 'Create Account'}</h3>
              <button onClick={() => setIsModalOpen(false)} className="p-1 hover:bg-slate-100 rounded-full"><X size={16} className="text-slate-400" /></button>
            </div>
            <form onSubmit={handleSaveUser} className="p-4 space-y-3 text-left">
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-500 uppercase">Username</label>
                <input type="text" required value={formData.username} onChange={(e) => setFormData({...formData, username: e.target.value})} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg outline-none text-xs" />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-500 uppercase">Email</label>
                <input type="email" required value={formData.email} onChange={(e) => setFormData({...formData, email: e.target.value})} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg outline-none text-xs" />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Password {editingUser && '(Optional)'}</label>
                <input type="password" required={!editingUser} value={formData.password} onChange={(e) => setFormData({...formData, password: e.target.value})} className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg outline-none text-xs" placeholder={editingUser ? "Unchanged" : "••••••••"} />
              </div>
              <div className="flex gap-2 pt-2">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-2 border border-slate-200 rounded-lg text-xs font-bold">Cancel</button>
                <button type="submit" disabled={saving} className="flex-1 py-2 bg-indigo-600 text-white rounded-lg text-xs font-bold shadow-md">Confirm</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {isRoleModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-xl shadow-2xl overflow-hidden border border-slate-200 max-h-[80vh] flex flex-col animate-in zoom-in-95">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldCheck size={16} className="text-indigo-600" />
                <h3 className="text-sm font-bold text-slate-800">Assign Roles</h3>
              </div>
              <button onClick={() => setIsRoleModalOpen(false)} className="p-1 hover:bg-slate-100 rounded-full"><X size={16} className="text-slate-400" /></button>
            </div>

            <div className="px-4 py-2 border-b border-slate-100">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={12} />
                <input 
                  type="text" 
                  placeholder="Filter roles..." 
                  value={roleSearchTerm}
                  onChange={(e) => setRoleSearchTerm(e.target.value)}
                  className="w-full pl-8 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg outline-none text-[10px]"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-1.5 custom-scrollbar">
              {availableRoles.filter(r => r.name.toLowerCase().includes(roleSearchTerm.toLowerCase())).map(role => {
                const isSelected = selectedRoleIds.includes(role.id);
                return (
                  <button 
                    key={role.id}
                    onClick={() => setSelectedRoleIds(prev => isSelected ? prev.filter(id => id !== role.id) : [...prev, role.id])}
                    className={`w-full flex items-center justify-between p-3 rounded-lg border transition-all text-left ${
                      isSelected ? 'bg-indigo-50 border-indigo-100 text-indigo-700' : 'bg-white border-slate-100 text-slate-600'
                    }`}
                  >
                    <span className="font-bold text-xs">{role.name}</span>
                    {isSelected && <CheckCircle size={16} className="text-indigo-600" />}
                  </button>
                );
              })}
            </div>

            <div className="p-4 border-t border-slate-100 flex items-center justify-between bg-slate-50">
              <span className="text-[10px] font-bold text-slate-400">{selectedRoleIds.length} Selected</span>
              <div className="flex gap-2">
                <button onClick={() => setIsRoleModalOpen(false)} className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-bold bg-white">Cancel</button>
                <button onClick={handleAssignRoles} disabled={saving} className="px-4 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-bold shadow-md">Apply</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserManagement;
