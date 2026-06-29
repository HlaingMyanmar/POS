
import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react';
import { productSerialService } from '../services/productserialapiservice';
import { productService } from '../services/productapiservice';
import { ProductSerialDTO, ProductDTO, SerialStatus } from '../types';
import {
  Loader2, Plus, Search, Trash2, Edit2, X,
  Hash, ChevronLeft, ChevronRight, ChevronDown,
  Package, CheckCircle2, AlertTriangle, Settings, Save,
  SearchCode, Camera, FileText, Eye,
} from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import Swal from 'sweetalert2';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

const ProductSerialManagement: React.FC = () => {
  const [serials, setSerials] = useState<ProductSerialDTO[]>([]);
  const [products, setProducts] = useState<ProductDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSerial, setEditingSerial] = useState<ProductSerialDTO | null>(null);
  const [formData, setFormData] = useState<Partial<ProductSerialDTO>>({
    serialNumber: '',
    status: SerialStatus.AVAILABLE,
    productId: undefined,
    condition: '',
    photoBase64: undefined
  });
  const [saving, setSaving] = useState(false);

  // Warranty duration state
  const [wValue, setWValue] = useState(0);
  const [wUnit, setWUnit] = useState<'ရက်' | 'လ' | 'နှစ်'>('နှစ်');
  const [viewPhotoUrl, setViewPhotoUrl] = useState<string | null>(null);
  const photoInputRef = useRef<HTMLInputElement>(null);

  const fetchData = useCallback(async () => {
    try {
      const [sData, pData] = await Promise.all([
        productSerialService.getAll(),
        productService.getAll()
      ]);
      setSerials(sData);
      setProducts(pData);
    } catch (error) {
      console.error("Failed to load serial data", error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);
  useRefreshOnTabActivate(fetchData);

  useWebsocket('/topic/productSerial', fetchData);

  const filteredSerials = useMemo(() => {
    return serials.filter(s =>
      s.serialNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.productName?.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }, [serials, searchTerm]);

  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm]);

  const totalPages = Math.ceil(filteredSerials.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedSerials = filteredSerials.slice(startIndex, startIndex + itemsPerPage);

  const MS_PER_DAY = 24 * 60 * 60 * 1000;
  const toDateOnly = (value?: string) => value ? String(value).slice(0, 10) : '';
  const dateOnlyToUtcMs = (value?: string) => {
    const [year, month, day] = toDateOnly(value).split('-').map(Number);
    if (!year || !month || !day) return NaN;
    return Date.UTC(year, month - 1, day);
  };
  const utcMsToDateOnly = (value: number) => new Date(value).toISOString().slice(0, 10);
  const todayDateOnly = () => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };
  const addDays = (dateIso: string, days: number) => {
    const startTime = dateOnlyToUtcMs(dateIso);
    if (!Number.isFinite(startTime)) return toDateOnly(dateIso);
    return utcMsToDateOnly(startTime + (days * MS_PER_DAY));
  };
  const countDays = (start?: string, end?: string) => {
    if (!start || !end) return 0;
    const startTime = dateOnlyToUtcMs(start);
    const endTime = dateOnlyToUtcMs(end);
    if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) return 0;
    return Math.round((endTime - startTime) / MS_PER_DAY);
  };

  const monthsToDisplay = (serial: ProductSerialDTO): { value: number; unit: 'ရက်' | 'လ' | 'နှစ်' } => {
    const months = Number(serial.warrantyMonths ?? 0) || 0;
    if (months > 0 && months % 12 === 0) return { value: months / 12, unit: 'နှစ်' };
    if (months > 0) return { value: months, unit: 'လ' };
    const days = countDays(serial.warrantyStartDate, serial.warrantyEndDate);
    if (days > 0) return { value: days, unit: 'ရက်' };
    return { value: 0, unit: 'လ' };
  };

  const toMonths = (value: number, unit: 'ရက်' | 'လ' | 'နှစ်'): number => {
    if (unit === 'ရက်') return 0;
    if (unit === 'နှစ်') return value * 12;
    return value;
  };

  const buildWarrantyPayload = () => {
    const value = Math.max(0, Number(wValue) || 0);
    if (value <= 0) return { warrantyMonths: 0, warrantyStartDate: undefined, warrantyEndDate: undefined };
    const start = toDateOnly(formData.warrantyStartDate) || todayDateOnly();
    if (wUnit === 'ရက်') {
      return { warrantyMonths: 0, warrantyStartDate: start, warrantyEndDate: addDays(start, value) };
    }
    return { warrantyMonths: toMonths(value, wUnit), warrantyStartDate: start, warrantyEndDate: undefined };
  };

  const formatSerialWarranty = (serial: ProductSerialDTO) => {
    const months = Number(serial.warrantyMonths ?? 0) || 0;
    if (months > 0 && months % 12 === 0) return `${months / 12} နှစ်`;
    if (months > 0) return `${months} လ`;
    const days = countDays(serial.warrantyStartDate, serial.warrantyEndDate);
    return days > 0 ? `${days} ရက်` : '-';
  };

  const handleOpenModal = (serial?: ProductSerialDTO) => {
    if (serial) {
      setEditingSerial(serial);
      setFormData({
        serialNumber: serial.serialNumber,
        status: serial.status,
        productId: serial.productId,
        warrantyMonths: serial.warrantyMonths ?? 0,
        warrantyStartDate: serial.warrantyStartDate,
        condition: serial.condition ?? '',
        photoBase64: serial.photoBase64
      });
      const d = monthsToDisplay(serial);
      setWValue(d.value);
      setWUnit(d.unit);
    } else {
      setEditingSerial(null);
      setFormData({
        serialNumber: '',
        status: SerialStatus.AVAILABLE,
        productId: undefined,
        warrantyMonths: 0,
        warrantyStartDate: undefined,
        condition: '',
        photoBase64: undefined
      });
      setWValue(0);
      setWUnit('နှစ်');
    }
    setIsModalOpen(true);
  };

  const handlePhotoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const img = new Image();
    const objectUrl = URL.createObjectURL(file);
    img.onload = () => {
      const MAX = 400;
      const scale = Math.min(1, MAX / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width  = Math.round(img.width  * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height);
      const compressed = canvas.toDataURL('image/jpeg', 0.75);
      URL.revokeObjectURL(objectUrl);
      setFormData(prev => ({ ...prev, photoBase64: compressed }));
    };
    img.src = objectUrl;
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.productId) {
      Swal.fire('Required', 'Please select a target product', 'warning');
      return;
    }

    const payload = { ...formData, ...buildWarrantyPayload() };

    setSaving(true);
    try {
      if (editingSerial) {
        await productSerialService.update(editingSerial.id, payload);
      } else {
        await productSerialService.create(payload as Omit<ProductSerialDTO, 'id'>);
      }
      setIsModalOpen(false);
      fetchData();
      Swal.fire({ icon: 'success', title: 'စီရီယယ် သိမ်းဆည်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error.message || 'Action failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'စီရီယယ် ဖျက်လိုက်ပါသလား?',
      text: "ဤဆုံးဖြတ်ချက်သည် ထာဝရ ဖျက်ခြင်း ဖြစ်သည်။",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'ဟုတ်, ဖျက်လိုက်ပါ'
    });

    if (result.isConfirmed) {
      try {
        await productSerialService.delete(id);
        fetchData();
        Swal.fire({ icon: 'success', title: 'ဖျက်လိုက်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
      } catch (error: any) {
        Swal.fire('Error', error.message, 'error');
      }
    }
  };

  const getStatusStyle = (status: SerialStatus) => {
    switch (status) {
      case SerialStatus.AVAILABLE: return 'bg-emerald-50 text-emerald-600 border-emerald-100';
      case SerialStatus.SOLD: return 'bg-indigo-50 text-indigo-600 border-indigo-100';
      case SerialStatus.USED_IN_SERVICE: return 'bg-amber-50 text-amber-600 border-amber-100';
      case SerialStatus.DAMAGED: return 'bg-rose-50 text-rose-600 border-rose-100';
      default: return 'bg-slate-50 text-slate-500 border-slate-100';
    }
  };

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden text-left">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 bg-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-xl shadow-indigo-100">
            <SearchCode size={24} />
          </div>
          <div>
            <h2 className="text-base font-black text-slate-800 tracking-tight uppercase">စီရီယယ် မှတ်တမ်း</h2>
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-widest mt-0.5">ပိုင်ဆိုင်မှု ခြေရာခံသည် နှင့် စီရီယယ်ခွဲခြားခြင်း</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative group">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={16} />
            <input
              type="text" placeholder="စီရီယယ် သို့မဟုတ် ထုတ်ကုန် ရှာဖွေ..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-11 pr-3 py-2.5 bg-white border border-slate-200 rounded-2xl outline-none text-[11px] font-bold w-full md:w-64 focus:border-indigo-500 transition-all shadow-sm"
            />
          </div>
          <button
            onClick={() => handleOpenModal()}
            className="bg-indigo-600 text-white px-5 py-2.5 rounded-2xl text-[11px] font-black uppercase shadow-xl shadow-indigo-100 active:scale-95 transition-all flex items-center gap-1.5"
          >
            <Plus size={18} /> စီရီယယ် ထည့်ရန်
          </button>
        </div>
      </div>

      <div className="bg-white rounded-[1.5rem] shadow-xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden">
        <div className="flex-1 overflow-auto custom-scrollbar relative">
          <table className="w-full text-left border-collapse min-w-[1200px]">
            <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
              <tr>
                <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">စီရီယယ် အမှတ်</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">ထုတ်ကုန် ယူလုံ့စုံစုံဆိုင်ရာ</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">အခြအအဖြေ</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">အာမခံ</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">ဝယ်ယူခဲ့သည့် အရင်းအမြစ်</th>
                <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">အခြေအနေ</th>
                <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">လုပ်ဆောင်ချက်များ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {paginatedSerials.length > 0 ? paginatedSerials.map((serial) => (
                <tr key={serial.id} className="hover:bg-slate-50/50 transition-colors group">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      {serial.photoBase64 ? (
                        <img src={serial.photoBase64} alt="serial" className="w-9 h-9 rounded-xl object-cover border border-slate-200 shrink-0" />
                      ) : (
                        <div className="w-9 h-9 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-center text-indigo-600 group-hover:bg-indigo-600 group-hover:text-white transition-all shrink-0">
                          <Hash size={18} />
                        </div>
                      )}
                      <div>
                        <p className="text-xs font-black text-slate-800 tracking-tight">{serial.serialNumber}</p>
                        <p className="text-[9px] text-slate-400 font-bold uppercase tracking-widest italic">ID: #{serial.id}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <div className="flex items-center gap-2">
                      <Package size={14} className="text-slate-300" />
                      <span className="text-xs font-bold text-slate-600">{serial.productName || 'အမည်မသိ ထုတ်ကုန်'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    {serial.condition ? (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-amber-50 border border-amber-100 text-amber-700 text-[10px] font-bold rounded-lg">
                        <FileText size={10} />{serial.condition}
                      </span>
                    ) : (
                      <span className="text-[10px] text-slate-300">—</span>
                    )}
                  </td>
                  <td className="px-4 py-4">
                    <div className="text-xs text-slate-600 space-y-0.5">
                      <div className="font-semibold">
                        {formatSerialWarranty(serial)}
                      </div>
                      <div className="text-[10px] text-slate-400">
                        {serial.warrantyStartDate || '-'} → {serial.warrantyEndDate || '-'}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <div className="text-xs text-slate-600 space-y-0.5">
                      <div className="font-semibold">{serial.purchaseCode || '-'}</div>
                      <div className="text-[10px] text-slate-400">{serial.supplierName || '-'}</div>
                      <div className="text-[10px] text-slate-400">{serial.purchaseDate ? new Date(serial.purchaseDate).toLocaleDateString() : '-'}</div>
                    </div>
                  </td>
                  <td className="px-4 py-4 text-center">
                    <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider border ${getStatusStyle(serial.status)}`}>
                      {serial.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      {serial.photoBase64 && (
                        <button onClick={() => setViewPhotoUrl(serial.photoBase64!)} className="p-2 text-slate-400 hover:text-indigo-600 bg-white border border-slate-200 rounded-xl shadow-sm transition-all">
                          <Eye size={12} />
                        </button>
                      )}
                      <button onClick={() => handleOpenModal(serial)} className="p-2 text-slate-400 hover:text-indigo-600 bg-white border border-slate-200 rounded-xl shadow-sm transition-all">
                        <Edit2 size={12} />
                      </button>
                      <button onClick={() => handleDelete(serial.id)} className="p-2 text-slate-400 hover:text-rose-600 bg-white border border-slate-200 rounded-xl shadow-sm transition-all">
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </td>
                </tr>
              )) : (
                <tr><td colSpan={7} className="px-6 py-24 text-center text-slate-400 text-xs font-bold tracking-widest uppercase">မှတ်တမ်း မရှိ</td></tr>
              )}
            </tbody>
          </table>
        </div>

        {filteredSerials.length > 0 && (
          <div className="sticky bottom-0 z-30 px-8 py-5 bg-white border-t border-slate-200 flex flex-col md:flex-row items-center justify-between gap-6 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
            <div className="w-full md:w-auto text-center md:text-left order-2 md:order-1">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest">
                ပြ示 <span className="text-indigo-600">{startIndex + 1}</span> မှ <span className="text-indigo-600">{Math.min(startIndex + itemsPerPage, filteredSerials.length)}</span> of <span className="text-slate-800">{filteredSerials.length}</span> ပစ္စည်းများ
              </span>
            </div>

            <div className="w-full md:w-auto flex flex-col sm:flex-row items-center justify-center gap-4 order-1 md:order-2">
              <div className="relative group">
                <select
                  value={itemsPerPage} onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(1); }}
                  className="appearance-none bg-slate-50 border border-slate-200 rounded-xl px-4 py-1.5 pr-10 text-[10px] font-black text-slate-600 outline-none focus:bg-white focus:border-indigo-500 cursor-pointer transition-all"
                >
                  <option value={10}>စာမျက်နှာတစ်ခုလျှင် ၁၀</option>
                  <option value={25}>စာမျက်နှာတစ်ခုလျှင် ၂၅</option>
                  <option value={50}>စာမျက်နှာတစ်ခုလျှင် ၅၀</option>
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
                        className={`min-w-[40px] h-10 rounded-xl text-[11px] font-black transition-all ${
                          currentPage === pageNum ? 'bg-indigo-600 text-white shadow-lg border-indigo-600 scale-105' : 'bg-white border border-slate-200 text-slate-500 hover:bg-slate-50'
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

      {/* Photo viewer overlay */}
      {viewPhotoUrl && (
        <div
          className="fixed inset-0 z-[200] flex items-center justify-center bg-black/85 backdrop-blur-sm"
          onClick={() => setViewPhotoUrl(null)}
        >
          <div className="flex flex-col items-center gap-3" onClick={e => e.stopPropagation()}>
            <img
              src={viewPhotoUrl}
              alt="Serial photo"
              className="rounded-xl object-contain shadow-2xl"
              style={{ maxHeight: '50vh', maxWidth: '90vw' }}
            />
            <button
              onClick={() => setViewPhotoUrl(null)}
              className="px-5 py-2 bg-white/10 hover:bg-white/20 text-white text-xs font-bold rounded-full border border-white/20 transition-all"
            >
              ပိတ်
            </button>
          </div>
        </div>
      )}

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-md rounded-[2.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 overflow-hidden">
            <div className="p-8 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <div>
                <h3 className="text-[14px] font-black text-slate-800 uppercase tracking-tight">{editingSerial ? 'ပြင်ဆင်' : 'အသစ်'} စီရီယယ် ဝင်လျက်</h3>
                <p className="text-[10px] text-slate-400 font-bold uppercase mt-1 tracking-widest italic">စုံစုံတည်သည့် စားနည်း သတ်မှတ်ခြင်း</p>
              </div>
              <button onClick={() => setIsModalOpen(false)} className="p-2.5 hover:bg-white hover:shadow-md rounded-2xl transition-all border border-transparent hover:border-slate-100"><X size={20} className="text-slate-400" /></button>
            </div>

            <form onSubmit={handleSave} className="p-8 space-y-6 text-left">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">စီရီယယ် အမှတ်</label>
                <div className="relative group">
                  <Hash className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <input
                    type="text" required
                    autoFocus
                    value={formData.serialNumber}
                    onChange={(e) => setFormData(prev => ({...prev, serialNumber: e.target.value.toUpperCase()}))}
                    className="w-full pl-12 pr-5 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm"
                    placeholder="ဥပမာ SN-8829-XL"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ရည်မှန်းဒီမြောက် ထုတ်ကုန်</label>
                <div className="relative group">
                  <Package className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-indigo-500 transition-colors" size={16} />
                  <select
                    required
                    value={formData.productId ?? ""}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : undefined;
                      setFormData(prev => ({...prev, productId: val}));
                    }}
                    className="w-full pl-12 pr-10 py-4 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all appearance-none shadow-sm"
                  >
                    <option value="" disabled>ယူလုံ့စုံစုံဆိုင်ရာ ထုတ်ကုန် ရွေးချယ်</option>
                    {products.map(p => <option key={p.id} value={p.id}>{p.name} ({p.productCode})</option>)}
                  </select>
                  <ChevronDown size={16} className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1 text-left">လက်ရှိ အခြေအနေ</label>
                <div className="grid grid-cols-2 gap-2 p-1 bg-slate-50 rounded-2xl border border-slate-200">
                  {Object.values(SerialStatus).map((status) => (
                    <button
                      key={status}
                      type="button"
                      onClick={() => setFormData(prev => ({...prev, status}))}
                      className={`py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all border ${
                        formData.status === status
                        ? 'bg-white text-indigo-600 border-indigo-100 shadow-sm'
                        : 'text-slate-400 border-transparent hover:text-slate-600'
                      }`}
                    >
                      {status.replace(/_/g, ' ')}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အာမခံ ကာလ</label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min={0}
                    value={wValue}
                    onChange={e => setWValue(Math.max(0, Number(e.target.value) || 0))}
                    className="flex-1 px-3 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm"
                    placeholder="0"
                  />
                  <select
                    value={wUnit}
                    onChange={e => setWUnit(e.target.value as 'ရက်' | 'လ' | 'နှစ်')}
                    className="px-3 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm"
                  >
                    <option value="လ">လ</option>
                    <option value="ရက်">ရက်</option>
                    <option value="နှစ်">နှစ်</option>
                  </select>
                </div>
                {wValue > 0 && (
                  <p className="text-[10px] text-indigo-500 font-semibold ml-1">
                    = {wUnit === 'ရက်' ? wValue : toMonths(wValue, wUnit)} {wUnit === 'ရက်' ? 'ရက်' : 'လ'} (တွက်ချက်မှု)
                  </p>
                )}
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အာမခံ စတင်</label>
                <input
                  type="date"
                  value={formData.warrantyStartDate ? String(formData.warrantyStartDate).slice(0, 10) : ''}
                  onChange={(e) => setFormData(prev => ({ ...prev, warrantyStartDate: e.target.value || undefined }))}
                  className="w-full px-3 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-indigo-500 focus:bg-white transition-all shadow-sm"
                />
              </div>

              {/* Condition */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">အခြအအဖြေ</label>
                <div className="relative group">
                  <FileText className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-300 group-focus-within:text-amber-500 transition-colors" size={15} />
                  <input
                    type="text"
                    value={formData.condition ?? ''}
                    onChange={e => setFormData(prev => ({ ...prev, condition: e.target.value }))}
                    placeholder="ဥပမာ အသစ်, ကောင်း, ဆူးများ..."
                    className="w-full pl-12 pr-5 py-3.5 bg-slate-50 border border-slate-200 rounded-2xl text-[12px] font-bold outline-none focus:border-amber-400 focus:bg-white transition-all shadow-sm"
                  />
                </div>
              </div>

              {/* Photo */}
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">ကြည့်ကောင်း</label>
                <input ref={photoInputRef} type="file" accept="image/*" className="hidden" onChange={handlePhotoChange} />
                <div className="flex items-center gap-3">
                  {formData.photoBase64 ? (
                    <img src={formData.photoBase64} alt="preview" className="w-16 h-16 rounded-xl object-cover border border-slate-200" />
                  ) : (
                    <div className="w-16 h-16 rounded-xl bg-slate-50 border border-dashed border-slate-300 flex items-center justify-center text-slate-300">
                      <Camera size={22} />
                    </div>
                  )}
                  <div className="flex flex-col gap-1.5">
                    <button type="button" onClick={() => photoInputRef.current?.click()}
                      className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 text[10px] font-black uppercase rounded-xl transition-all">
                      {formData.photoBase64 ? 'ကြည့်ကောင်း ပြောင်းလဲ' : 'ကြည့်ကောင်း တင်သွင်း'}
                    </button>
                    {formData.photoBase64 && (
                      <button type="button" onClick={() => setViewPhotoUrl(formData.photoBase64!)}
                        className="flex items-center justify-center gap-1 px-4 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-600 text-[10px] font-black uppercase rounded-xl transition-all">
                        <Eye size={11} /> ကြည့်ကောင်း ကြည်ကြည့်
                      </button>
                    )}
                    {formData.photoBase64 && (
                      <button type="button" onClick={() => setFormData(prev => ({ ...prev, photoBase64: undefined }))}
                        className="px-4 py-2 bg-rose-50 hover:bg-rose-100 text-rose-500 text-[10px] font-black uppercase rounded-xl transition-all">
                        ဖယ်ရှား
                      </button>
                    )}
                  </div>
                </div>
              </div>

              <div className="flex gap-4 pt-4">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex-1 py-4 border border-slate-200 rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest bg-white text-slate-500 hover:bg-slate-50 transition-all shadow-sm"
                >
                  စွန့်ခွာ
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-[2] py-4 bg-indigo-600 text-white rounded-[1.25rem] text-[11px] font-black uppercase tracking-widest shadow-xl shadow-indigo-600/20 active:scale-[0.98] transition-all flex items-center justify-center gap-2"
                >
                  {saving ? <Loader2 size={16} className="animate-spin" /> : <Save size={16} />}
                  အတည်ပြု နှင့် သိမ်းဆည်း
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProductSerialManagement;
