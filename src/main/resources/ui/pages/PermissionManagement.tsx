
import React, { useEffect, useState, useCallback } from 'react';
import { permissionService } from '../services/permissionapiservice';
import { PermissionDTO } from '../types';
import { Loader2, Key, Search, Trash2, Edit2, Wifi, Plus, X, Save } from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';

const PermissionManagement: React.FC = () => {
  const [permissions, setPermissions] = useState<PermissionDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [wsStatus, setWsStatus] = useState<'online' | 'offline'>('offline');
  const [searchTerm, setSearchTerm] = useState('');
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPermission, setEditingPermission] = useState<PermissionDTO | null>(null);
  const [formData, setFormData] = useState({ name: '', description: '' });
  const [saving, setSaving] = useState(false);

  const fetchPermissions = useCallback(async () => {
    try {
      const data = await permissionService.getAll();
      setPermissions(data);
    } catch (error) {
      console.error("Failed to load permissions", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPermissions();
  }, [fetchPermissions]);

  const handleWsMessage = useCallback((message: string) => {
    setWsStatus('online');
    fetchPermissions();
    const timer = setTimeout(() => setWsStatus('offline'), 5000);
    return () => clearTimeout(timer);
  }, [fetchPermissions]);

  useWebsocket('/topic/permissions', handleWsMessage);

  const handleOpenModal = (permission?: PermissionDTO) => {
    if (permission) {
      setEditingPermission(permission);
      setFormData({ name: permission.name, description: permission.description || '' });
    } else {
      setEditingPermission(null);
      setFormData({ name: '', description: '' });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingPermission) {
        await permissionService.update(editingPermission.id, formData);
      } else {
        await permissionService.create(formData);
      }
      setIsModalOpen(false);
      fetchPermissions();
      Swal.fire({ icon: 'success', title: 'Done', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const filteredPermissions = permissions.filter(p => 
    p.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) return (
    <div className="h-full flex items-center justify-center">
      <Loader2 className="animate-spin text-indigo-600" size={32} />
    </div>
  );

  return (
    <div className="space-y-4 animate-in fade-in duration-400 relative">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-left">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-slate-800 tracking-tight">Access Repo</h2>
            <span className="bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full text-[10px] font-bold">
              {permissions.length} Total
            </span>
          </div>
          <p className="text-slate-500 text-xs">Manage system authorization keys.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
            <input 
              type="text" placeholder="Search..." 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-lg outline-none text-xs w-48 transition-all"
            />
          </div>
          <button 
            onClick={() => handleOpenModal()}
            className="bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold shadow-md flex items-center gap-1.5"
          >
            <Plus size={14} /> Add Key
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-3">
        {filteredPermissions.map(permission => (
          <div key={permission.id} className="bg-white p-4 rounded-xl border border-slate-100 shadow-sm hover:border-indigo-200 transition-all group relative overflow-hidden">
            <div className="flex items-start justify-between mb-3 text-left">
              <div className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                <Key size={16} />
              </div>
              <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-all">
                <button onClick={() => handleOpenModal(permission)} className="p-1 text-slate-400 hover:text-indigo-600"><Edit2 size={12} /></button>
              </div>
            </div>
            
            <h4 className="font-bold text-slate-800 text-xs mb-1 tracking-tight truncate text-left">{permission.name}</h4>
            <p className="text-[10px] text-slate-500 leading-tight line-clamp-2 min-h-[24px] text-left">
              {permission.description || "System authority key."}
            </p>
            
            <div className="mt-3 pt-3 border-t border-slate-50 flex items-center justify-between">
              <span className="text-[8px] font-bold text-slate-300 uppercase">ID: #{permission.id}</span>
            </div>
          </div>
        ))}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-sm rounded-xl shadow-2xl overflow-hidden border border-slate-200">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">{editingPermission ? 'Update Key' : 'New Key'}</h3>
              <button onClick={() => setIsModalOpen(false)} className="p-1 hover:bg-slate-100 rounded-full"><X size={16} className="text-slate-400" /></button>
            </div>
            <form onSubmit={handleSave} className="p-4 space-y-3 text-left">
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-500 uppercase">Key Name</label>
                <input
                  type="text" required value={formData.name}
                  onChange={(e) => setFormData({...formData, name: e.target.value.toUpperCase().replace(/\s/g, '_')})}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs outline-none"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-500 uppercase">Description</label>
                <textarea
                  rows={2} value={formData.description}
                  onChange={(e) => setFormData({...formData, description: e.target.value})}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-xs outline-none resize-none"
                />
              </div>
              <div className="flex gap-2 pt-2">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-2 border border-slate-200 rounded-lg text-xs font-bold">Cancel</button>
                <button type="submit" disabled={saving} className="flex-1 py-2 bg-indigo-600 text-white rounded-lg text-xs font-bold shadow-md">Confirm</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PermissionManagement;
