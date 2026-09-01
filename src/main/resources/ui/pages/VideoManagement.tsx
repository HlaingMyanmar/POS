import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ChevronDown, ChevronLeft, ChevronRight, Edit2, GripVertical, LayoutList, ListOrdered, Loader2, Play, Plus, Search, Star, Trash2, X } from 'lucide-react';
import Swal from 'sweetalert2';
import { videoService } from '../services/videoapiservice';
import { VideoAppType, VideoAudience, VideoDTO } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type AudienceFilter = '' | VideoAudience;
type ActiveFilter = '' | 'true' | 'false';
type ViewMode = 'list' | 'arrange';

const AUDIENCE_LABELS: Record<VideoAudience, string> = {
  TECHNICIAN: 'Technician App',
  CLIENT: 'Client App',
  BOTH: 'Both Apps'
};

const youtubeThumb = (url: string) => {
  const match = url.trim().match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/))([A-Za-z0-9_-]{11})/);
  return match ? `https://img.youtube.com/vi/${match[1]}/hqdefault.jpg` : '';
};

const placementOf = (video: VideoDTO, appType: VideoAppType) =>
  video.placements?.find(placement => placement.appType === appType);

const VideoManagement: React.FC = () => {
  const [view, setView] = useState<ViewMode>('list');
  const [videos, setVideos] = useState<VideoDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [audienceFilter, setAudienceFilter] = useState<AudienceFilter>('');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState<VideoDTO | null>(null);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    youtubeUrl: '',
    description: '',
    category: '',
    targetAudience: 'TECHNICIAN' as VideoAudience,
    active: true
  });

  const [appType, setAppType] = useState<VideoAppType>('TECHNICIAN');
  const [arrangeCategory, setArrangeCategory] = useState('');
  const [arrangeActive, setArrangeActive] = useState<ActiveFilter>('');
  const [arranged, setArranged] = useState<VideoDTO[]>([]);
  const [dirty, setDirty] = useState(false);
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  const fetchList = useCallback(async () => {
    try {
      const data = await videoService.getAll({ audience: audienceFilter });
      setVideos(data);
    } catch (error) {
      console.error('Load error', error);
    } finally {
      setLoading(false);
    }
  }, [audienceFilter]);

  const fetchArrangement = useCallback(async () => {
    try {
      const data = await videoService.getArrangement({
        appType,
        category: arrangeCategory.trim() || undefined,
        active: arrangeActive === '' ? '' : arrangeActive === 'true'
      });
      setArranged(data);
      setDirty(false);
    } catch (error) {
      console.error('Arrangement load error', error);
    } finally {
      setLoading(false);
    }
  }, [appType, arrangeCategory, arrangeActive]);

  useEffect(() => {
    setLoading(true);
    if (view === 'list') fetchList();
    else fetchArrangement();
  }, [view, fetchList, fetchArrangement]);
  useRefreshOnTabActivate(view === 'list' ? fetchList : fetchArrangement);

  const filtered = useMemo(() => {
    const q = searchTerm.toLowerCase().trim();
    return videos.filter(video => {
      if (!q) return true;
      return video.title.toLowerCase().includes(q)
        || (video.category || '').toLowerCase().includes(q)
        || (video.description || '').toLowerCase().includes(q);
    });
  }, [videos, searchTerm]);

  useEffect(() => { setCurrentPage(1); }, [searchTerm, audienceFilter]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / itemsPerPage));
  const startIndex = (currentPage - 1) * itemsPerPage;
  const pageItems = filtered.slice(startIndex, startIndex + itemsPerPage);

  const handleOpenModal = (video?: VideoDTO) => {
    if (video) {
      setEditing(video);
      setFormData({
        title: video.title,
        youtubeUrl: video.youtubeUrl || video.sourceUrl || '',
        description: video.description || '',
        category: video.category || '',
        targetAudience: video.targetAudience,
        active: video.active
      });
    } else {
      setEditing(null);
      setFormData({
        title: '',
        youtubeUrl: '',
        description: '',
        category: '',
        targetAudience: 'TECHNICIAN',
        active: true
      });
    }
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.title.trim() || !formData.youtubeUrl.trim() || !formData.targetAudience) {
      Swal.fire('Required', 'Title, YouTube URL and Target App are required', 'warning');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        title: formData.title.trim(),
        youtubeUrl: formData.youtubeUrl.trim(),
        description: formData.description.trim() || undefined,
        category: formData.category.trim() || undefined,
        targetAudience: formData.targetAudience,
        active: formData.active
      };
      if (editing) await videoService.update(editing.id, payload);
      else await videoService.create(payload);
      setIsModalOpen(false);
      if (view === 'list') fetchList();
      else fetchArrangement();
      Swal.fire({ icon: 'success', title: 'သိမ်းဆည်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error?.response?.data?.message || error?.message || 'Operation failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    const result = await Swal.fire({
      title: 'Video ဖျက်မလား?',
      text: 'Mobile app catalog ထဲမှ ပျောက်သွားမည်။',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#ef4444',
      confirmButtonText: 'ဖျက်ပါ'
    });
    if (!result.isConfirmed) return;
    try {
      await videoService.delete(id);
      if (view === 'list') fetchList();
      else fetchArrangement();
      Swal.fire({ icon: 'success', title: 'ဖျက်ပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (err: any) {
      Swal.fire('အမှားအယွင်း', err?.response?.data?.message || err.message || 'ဖျက်ခြင်း မအောင်မြင်', 'error');
    }
  };

  const moveItem = (from: number, to: number) => {
    if (to < 0 || to >= arranged.length || from === to) return;
    setArranged(current => {
      const next = [...current];
      const [item] = next.splice(from, 1);
      next.splice(to, 0, item);
      return next;
    });
    setDirty(true);
  };

  const toggleFeatured = (id: number) => {
    setArranged(current => current.map(video => video.id === id ? { ...video, featured: !video.featured } : video));
    setDirty(true);
  };

  const handleSaveOrder = async () => {
    setSaving(true);
    try {
      const saved = await videoService.saveArrangement(
        appType,
        arranged.map(video => ({ videoId: video.id, featured: !!video.featured })),
        { category: arrangeCategory.trim() || undefined, active: arrangeActive === '' ? '' : arrangeActive === 'true' }
      );
      setArranged(saved);
      setDirty(false);
      Swal.fire({ icon: 'success', title: 'အစီအစဉ် သိမ်းပြီး', toast: true, position: 'top-end', showConfirmButton: false, timer: 1500 });
    } catch (error: any) {
      Swal.fire('Error', error?.response?.data?.message || error?.message || 'Order save failed', 'error');
    } finally {
      setSaving(false);
    }
  };

  const previewThumb = youtubeThumb(formData.youtubeUrl);

  if (loading) return <div className="h-full flex items-center justify-center"><Loader2 className="animate-spin text-indigo-600" size={32} /></div>;

  return (
    <div className="space-y-4 animate-in fade-in duration-400 h-full flex flex-col overflow-hidden">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 text-left shrink-0">
        <div>
          <h2 className="text-lg font-bold text-slate-800 tracking-tight uppercase">Video Management</h2>
          <p className="text-slate-500 text-xs">
            {view === 'list'
              ? 'YouTube Unlisted URL ကို Technician / Client / Both Apps သို့ ခွဲသတ်မှတ်ပါ။'
              : 'App တစ်ခုချင်းစီအတွက် ပြသရမည့် အစီအစဉ်ကို ဆွဲရွှေ့၍ သတ်မှတ်ပါ။ Featured videos သည် အပေါ်ဆုံးတွင် ပေါ်သည်။'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="bg-slate-100 rounded-lg p-0.5 flex">
            <button onClick={() => setView('list')} className={`px-3 py-1.5 rounded-md text-xs font-bold flex items-center gap-1 ${view === 'list' ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-500'}`}>
              <LayoutList size={14} /> List
            </button>
            <button onClick={() => setView('arrange')} className={`px-3 py-1.5 rounded-md text-xs font-bold flex items-center gap-1 ${view === 'arrange' ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-500'}`}>
              <ListOrdered size={14} /> Arrangement
            </button>
          </div>
          {view === 'list' && (
            <>
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" size={14} />
                <input
                  type="text"
                  placeholder="ခေါင်းစဉ် / အမျိုးအစား..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-8 pr-3 py-1.5 bg-white border border-slate-200 rounded-lg outline-none text-xs w-48 focus:border-indigo-500 transition-all shadow-sm"
                />
              </div>
              <select
                value={audienceFilter}
                onChange={(e) => setAudienceFilter(e.target.value as AudienceFilter)}
                className="bg-white border border-slate-200 rounded-lg px-3 py-1.5 text-xs font-bold text-slate-600 outline-none focus:border-indigo-500"
              >
                <option value="">All</option>
                <option value="TECHNICIAN">Technician</option>
                <option value="CLIENT">Client</option>
                <option value="BOTH">Both</option>
              </select>
              <button onClick={() => handleOpenModal()} className="bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold shadow-md flex items-center gap-1.5 hover:bg-indigo-700 transition-all active:scale-95">
                <Plus size={14} /> Video ထည့်ရန်
              </button>
            </>
          )}
        </div>
      </div>

      {view === 'arrange' ? (
        <div className="bg-white rounded-xl shadow-xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-100 flex flex-wrap items-center gap-2">
            <select value={appType} onChange={(e) => setAppType(e.target.value as VideoAppType)} className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs font-bold text-slate-700 outline-none focus:border-indigo-500">
              <option value="TECHNICIAN">Technician App</option>
              <option value="CLIENT">Client App</option>
            </select>
            <input
              value={arrangeCategory}
              onChange={(e) => setArrangeCategory(e.target.value)}
              placeholder="Category"
              className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs outline-none focus:border-indigo-500 w-36"
            />
            <select value={arrangeActive} onChange={(e) => setArrangeActive(e.target.value as ActiveFilter)} className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-xs font-bold text-slate-600 outline-none">
              <option value="">Active + Inactive</option>
              <option value="true">Active only</option>
              <option value="false">Inactive only</option>
            </select>
            <button disabled={!dirty || saving} onClick={handleSaveOrder} className="ml-auto bg-indigo-600 text-white px-3 py-1.5 rounded-lg text-xs font-bold disabled:opacity-40">
              {saving ? 'သိမ်းနေသည်...' : 'Save Order'}
            </button>
          </div>
          <div className="flex-1 overflow-auto custom-scrollbar p-3 space-y-2">
            {arranged.length === 0 && <p className="px-4 py-16 text-center text-slate-400 text-xs font-bold tracking-widest">ဤ app အတွက် video မရှိသေးပါ</p>}
            {arranged.map((video, index) => (
              <div
                key={video.id}
                draggable
                onDragStart={() => setDragIndex(index)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => {
                  if (dragIndex !== null) moveItem(dragIndex, index);
                  setDragIndex(null);
                }}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl border bg-white ${dragIndex === index ? 'border-indigo-300 bg-indigo-50' : 'border-slate-200'}`}
              >
                <GripVertical size={16} className="text-slate-300 shrink-0 cursor-grab" />
                <span className="w-6 text-[11px] font-black text-slate-400">{index + 1}</span>
                {video.thumbnailUrl ? (
                  <img src={video.thumbnailUrl} alt="" className="w-14 h-9 rounded-md object-cover border border-slate-100 shrink-0" />
                ) : (
                  <div className="w-14 h-9 rounded-md bg-slate-50 border border-slate-100 flex items-center justify-center text-slate-400 shrink-0"><Play size={12} /></div>
                )}
                <div className="min-w-0 flex-1 text-left">
                  <p className="text-xs font-bold text-slate-700 truncate">{video.title}</p>
                  <p className="text-[10px] text-slate-400">{AUDIENCE_LABELS[video.targetAudience]}{video.category ? ` · ${video.category}` : ''}{video.active ? '' : ' · Inactive'}</p>
                </div>
                <button type="button" onClick={() => toggleFeatured(video.id)} className={`p-1.5 rounded-lg ${video.featured ? 'text-amber-500' : 'text-slate-300 hover:text-amber-400'}`} title="Featured">
                  <Star size={16} fill={video.featured ? 'currentColor' : 'none'} />
                </button>
                <button type="button" disabled={index === 0} onClick={() => moveItem(index, index - 1)} className="p-1.5 text-slate-400 hover:text-indigo-600 disabled:opacity-30" title="Move up">
                  <ArrowUp size={14} />
                </button>
                <button type="button" disabled={index === arranged.length - 1} onClick={() => moveItem(index, index + 1)} className="p-1.5 text-slate-400 hover:text-indigo-600 disabled:opacity-30" title="Move down">
                  <ArrowDown size={14} />
                </button>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-xl shadow-slate-200/50 border border-slate-200 flex flex-col flex-1 overflow-hidden">
          <div className="flex-1 overflow-auto custom-scrollbar relative">
            <table className="w-full text-left border-collapse min-w-[880px]">
              <thead className="sticky top-0 z-30 bg-slate-50/95 backdrop-blur-sm border-b border-slate-200 shadow-sm">
                <tr>
                  <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Thumbnail</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Title</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Category</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Target App</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Tech #</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Client #</th>
                  <th className="px-4 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-center">Status</th>
                  <th className="px-6 py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {pageItems.length > 0 ? pageItems.map((video) => {
                  const tech = placementOf(video, 'TECHNICIAN');
                  const client = placementOf(video, 'CLIENT');
                  return (
                    <tr key={video.id} className="hover:bg-slate-50/50 transition-colors group">
                      <td className="px-6 py-3">
                        {video.thumbnailUrl ? (
                          <img src={video.thumbnailUrl} alt="" className="w-16 h-10 rounded-md object-cover border border-slate-100" />
                        ) : (
                          <div className="w-16 h-10 rounded-md bg-slate-50 border border-slate-100 flex items-center justify-center text-slate-400">
                            <Play size={14} />
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <p className="text-xs font-bold text-slate-700 flex items-center gap-1">
                          {(tech?.featured || client?.featured) && <Star size={12} className="text-amber-500" fill="currentColor" />}
                          {video.title}
                        </p>
                        {video.description && <p className="text-[10px] text-slate-400 mt-0.5 line-clamp-1">{video.description}</p>}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500">{video.category || '—'}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider border ${
                          video.targetAudience === 'TECHNICIAN' ? 'bg-sky-50 text-sky-700 border-sky-100'
                            : video.targetAudience === 'CLIENT' ? 'bg-amber-50 text-amber-700 border-amber-100'
                            : 'bg-violet-50 text-violet-700 border-violet-100'
                        }`}>
                          {AUDIENCE_LABELS[video.targetAudience]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center text-xs font-bold text-slate-500">{tech ? tech.sortOrder : '—'}</td>
                      <td className="px-4 py-3 text-center text-xs font-bold text-slate-500">{client ? client.sortOrder : '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex justify-center">
                          <span className={`px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider border ${
                            video.active ? 'bg-emerald-50 text-emerald-600 border-emerald-100' : 'bg-slate-50 text-slate-500 border-slate-200'
                          }`}>
                            {video.active ? 'Active' : 'Inactive'}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-3 text-right">
                        <div className="flex justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => handleOpenModal(video)} className="p-2 text-slate-400 hover:text-indigo-600 transition-colors"><Edit2 size={12} /></button>
                          <button onClick={() => handleDelete(video.id)} className="p-2 text-slate-400 hover:text-rose-600 transition-colors"><Trash2 size={12} /></button>
                        </div>
                      </td>
                    </tr>
                  );
                }) : (
                  <tr><td colSpan={8} className="px-6 py-20 text-center text-slate-400 text-xs font-bold tracking-widest">မှတ်တမ်းများမတွေ့ရှိ</td></tr>
                )}
              </tbody>
            </table>
          </div>

          {filtered.length > 0 && (
            <div className="sticky bottom-0 z-30 px-6 py-4 bg-white border-t border-slate-200 flex flex-col md:flex-row items-center justify-between gap-4 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
              <span className="text-[11px] font-black text-slate-400 uppercase tracking-widest">
                ပြသ <span className="text-indigo-600">{startIndex + 1}</span> မှ <span className="text-indigo-600">{Math.min(startIndex + itemsPerPage, filtered.length)}</span> အထိ <span className="text-slate-800">{filtered.length}</span>
              </span>
              <div className="flex items-center gap-4">
                <div className="relative group">
                  <select
                    value={itemsPerPage}
                    onChange={(e) => { setItemsPerPage(Number(e.target.value)); setCurrentPage(1); }}
                    className="appearance-none bg-slate-50 border border-slate-200 rounded-xl px-4 py-1.5 pr-10 text-[10px] font-black text-slate-600 outline-none focus:bg-white focus:border-indigo-500"
                  >
                    <option value={10}>စာမျက်နှာလျှင် ၁၀</option>
                    <option value={20}>စာမျက်နှာလျှင် ၂၀</option>
                    <option value={50}>စာမျက်နှာလျှင် ၅၀</option>
                  </select>
                  <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                </div>
                <div className="flex items-center gap-1.5">
                  <button disabled={currentPage === 1} onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))} className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 disabled:opacity-30">
                    <ChevronLeft size={16} />
                  </button>
                  <span className="min-w-[38px] h-10 rounded-xl text-[11px] font-black flex items-center justify-center bg-indigo-600 text-white">{currentPage}</span>
                  <button disabled={currentPage === totalPages} onClick={() => setCurrentPage(prev => Math.min(prev + 1, totalPages))} className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-500 disabled:opacity-30">
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {isModalOpen && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white w-full max-w-lg rounded-[1.5rem] shadow-2xl border border-slate-200 animate-in zoom-in-95 max-h-[90vh] overflow-y-auto">
            <div className="p-5 border-b border-slate-100 flex items-center justify-between text-left">
              <h3 className="text-sm font-bold text-slate-800 uppercase tracking-tight">{editing ? 'Video ပြင်ဆင်ရန်' : 'Video အသစ်'}</h3>
              <button onClick={() => setIsModalOpen(false)} className="p-2 hover:bg-slate-100 rounded-xl transition-colors"><X size={18} className="text-slate-400" /></button>
            </div>
            <form onSubmit={handleSave} className="p-6 space-y-4 text-left">
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Title</label>
                <input required value={formData.title} onChange={(e) => setFormData({ ...formData, title: e.target.value })} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500" placeholder="CCTV Installation Guide" />
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">YouTube URL</label>
                <input required value={formData.youtubeUrl} onChange={(e) => setFormData({ ...formData, youtubeUrl: e.target.value })} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500" placeholder="https://youtu.be/xxxxxx" />
                {previewThumb && <img src={previewThumb} alt="" className="mt-2 w-full h-36 object-cover rounded-xl border border-slate-100" />}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Target App</label>
                  <select required value={formData.targetAudience} onChange={(e) => setFormData({ ...formData, targetAudience: e.target.value as VideoAudience })} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500">
                    <option value="TECHNICIAN">Technician App</option>
                    <option value="CLIENT">Client App</option>
                    <option value="BOTH">Both Apps</option>
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Category</label>
                  <input value={formData.category} onChange={(e) => setFormData({ ...formData, category: e.target.value })} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500" placeholder="Diagnosis" />
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="text-[10px] font-black text-slate-500 uppercase tracking-widest ml-1">Description</label>
                <textarea value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })} rows={3} className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-2xl text-xs outline-none focus:border-indigo-500 resize-none" />
              </div>
              <label className="flex items-center gap-2 text-xs font-bold text-slate-600">
                <input type="checkbox" checked={formData.active} onChange={(e) => setFormData({ ...formData, active: e.target.checked })} />
                Active
              </label>
              <p className="text-[10px] text-slate-400 leading-relaxed">
                Video အသစ်သည် ရွေးထားသော app စာရင်း၏ နောက်ဆုံးနေရာတွင် ပေါ်မည်။ အစီအစဉ်ကို Arrangement tab မှ ပြင်နိုင်သည်။ Featured အလိုအလျောက် မဟုတ်ပါ။
              </p>
              <div className="flex gap-2 pt-2">
                <button type="button" onClick={() => setIsModalOpen(false)} className="flex-1 py-3 border border-slate-200 rounded-2xl text-[10px] font-black bg-white text-slate-500">ပယ်ဖျက်ရန်</button>
                <button type="submit" disabled={saving} className="flex-1 py-3 bg-indigo-600 text-white rounded-2xl text-[10px] font-black uppercase shadow-xl shadow-indigo-100 disabled:opacity-50">
                  {saving ? 'သိမ်းနေသည်...' : 'အတည်ပြုရန်'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default VideoManagement;
