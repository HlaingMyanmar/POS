import { DeliveryPolicyEditor } from '../components/ShippingSettings';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Building2,
  ChevronRight,
  Landmark,
  Loader2,
  MapPinned,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Truck,
  X,
} from 'lucide-react';
import Swal from 'sweetalert2';
import { api } from '../services/api';
import { AppRoute } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type Region = {
  id: number;
  name: string;
  kind: string;
  active: boolean;
  sortOrder: number;
};

type Township = {
  id: number;
  regionId: number;
  regionName?: string;
  regionKind?: string;
  name: string;
  deliveryCharge: number;
  active: boolean;
  sortOrder: number;
};

type Ward = {
  id: number;
  townshipId: number;
  townshipName?: string;
  regionId?: number;
  name: string;
  deliveryCharge: number;
  active: boolean;
  sortOrder: number;
};

const money = (n?: number | null) => Number(n || 0).toLocaleString();

const fieldClass =
  'w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-800 outline-none transition focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-50 disabled:cursor-not-allowed disabled:opacity-50';

const emptyRegionForm = () => ({
  id: undefined as number | undefined,
  name: '',
  kind: 'STATE',
  active: true,
  sortOrder: 0,
});

const emptyTownshipForm = (regionId?: number) => ({
  id: undefined as number | undefined,
  regionId: regionId ?? 0,
  name: '',
  active: true,
  sortOrder: 0,
});

const emptyWardForm = (townshipId?: number) => ({
  id: undefined as number | undefined,
  townshipId: townshipId ?? 0,
  name: '',
  deliveryCharge: '0',
  active: true,
  sortOrder: 0,
});

const StatusPill: React.FC<{ active: boolean }> = ({ active }) => (
  <span
    className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-black uppercase tracking-wide ${
      active ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-100' : 'bg-slate-100 text-slate-500 ring-1 ring-slate-200'
    }`}
  >
    {active ? 'Active' : 'Off'}
  </span>
);

const DeliveryChargesPage: React.FC = () => {
  const [regions, setRegions] = useState<Region[]>([]);
  const [townships, setTownships] = useState<Township[]>([]);
  const [wards, setWards] = useState<Ward[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'YANGON' | 'STATE'>('YANGON');
  const [query, setQuery] = useState('');
  const [selectedRegionId, setSelectedRegionId] = useState<number | null>(null);
  const [selectedTownshipId, setSelectedTownshipId] = useState<number | null>(null);
  const [regionForm, setRegionForm] = useState(emptyRegionForm());
  const [townshipForm, setTownshipForm] = useState(emptyTownshipForm());
  const [wardForm, setWardForm] = useState(emptyWardForm());
  const [regionEditorOpen, setRegionEditorOpen] = useState(false);
  const [townshipEditorOpen, setTownshipEditorOpen] = useState(false);
  const [wardEditorOpen, setWardEditorOpen] = useState(false);
  const [savingRegion, setSavingRegion] = useState(false);
  const [savingTownship, setSavingTownship] = useState(false);
  const [savingWard, setSavingWard] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [r, t, w] = await Promise.all([
        api.get<any>('/v1/customer-orders/regions'),
        api.get<any>('/v1/customer-orders/townships'),
        api.get<any>('/v1/customer-orders/wards'),
      ]);
      const regionRows = (r.data ?? []) as Region[];
      const townshipRows = (t.data ?? []) as Township[];
      const wardRows = (w.data ?? []) as Ward[];
      setRegions(regionRows);
      setTownships(townshipRows);
      setWards(wardRows);
      setSelectedRegionId((prev) => {
        if (prev && regionRows.some((x) => x.id === prev)) return prev;
        const preferred = regionRows.find((x) => x.kind === 'YANGON') ?? regionRows[0];
        return preferred?.id ?? null;
      });
    } catch (e) {
      console.error(e);
      void Swal.fire({ icon: 'error', title: 'စာရင်း မဖတ်နိုင်ပါ', text: 'Delivery locations ပြန်စစ်ပါ။' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);
  useRefreshOnTabActivate(load);

  const needle = query.trim().toLowerCase();

  const regionsForTab = useMemo(
    () => regions.filter((r) => (tab === 'YANGON' ? r.kind === 'YANGON' : r.kind === 'STATE')),
    [regions, tab]
  );
  const regionsInTab = useMemo(
    () => regionsForTab.filter((r) => !needle || r.name.toLowerCase().includes(needle)),
    [regionsForTab, needle]
  );

  useEffect(() => {
    if (!regionsForTab.length) {
      setSelectedRegionId(null);
      return;
    }
    if (!selectedRegionId || !regionsForTab.some((r) => r.id === selectedRegionId)) {
      setSelectedRegionId(regionsForTab[0].id);
    }
  }, [regionsForTab, selectedRegionId]);

  const selectedRegion = regions.find((r) => r.id === selectedRegionId) ?? null;
  const townshipsForRegion = useMemo(
    () => townships.filter((t) => t.regionId === selectedRegionId),
    [townships, selectedRegionId]
  );
  const townshipsInRegion = useMemo(
    () => townshipsForRegion.filter((t) => !needle || t.name.toLowerCase().includes(needle)),
    [townshipsForRegion, needle]
  );
  const selectedTownship = townships.find((t) => t.id === selectedTownshipId) ?? null;
  const wardsForTownship = useMemo(
    () => wards.filter((w) => w.townshipId === selectedTownshipId),
    [wards, selectedTownshipId]
  );
  const wardsInTownship = useMemo(
    () => wardsForTownship.filter((w) => !needle || w.name.toLowerCase().includes(needle)),
    [wardsForTownship, needle]
  );

  useEffect(() => {
    if (!townshipsForRegion.length) {
      setSelectedTownshipId(null);
      return;
    }
    if (!selectedTownshipId || !townshipsForRegion.some((t) => t.id === selectedTownshipId)) {
      setSelectedTownshipId(townshipsForRegion[0].id);
    }
  }, [townshipsForRegion, selectedTownshipId]);

  const stats = useMemo(() => {
    const regionCount = regions.filter((r) => (tab === 'YANGON' ? r.kind === 'YANGON' : r.kind === 'STATE')).length;
    const townshipCount = townships.filter((t) =>
      regions.some((r) => r.id === t.regionId && (tab === 'YANGON' ? r.kind === 'YANGON' : r.kind === 'STATE'))
    ).length;
    const tabWards = wards.filter((w) => {
      const tw = townships.find((t) => t.id === w.townshipId);
      if (!tw) return false;
      return regions.some((r) => r.id === tw.regionId && (tab === 'YANGON' ? r.kind === 'YANGON' : r.kind === 'STATE'));
    });
    const charges = tabWards.map((w) => Number(w.deliveryCharge) || 0);
    const min = charges.length ? Math.min(...charges) : 0;
    const max = charges.length ? Math.max(...charges) : 0;
    return { regionCount, townshipCount, wardCount: tabWards.length, min, max };
  }, [regions, townships, wards, tab]);

  const townshipChargeLabel = (townshipId: number) => {
    const list = wards.filter((w) => w.townshipId === townshipId);
    if (!list.length) return 'Charge မရှိသေး';
    const vals = list.map((w) => Number(w.deliveryCharge) || 0);
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    return min === max ? `${money(min)} Ks` : `${money(min)} – ${money(max)} Ks`;
  };

  const toastSaved = (title: string) =>
    Swal.fire({ icon: 'success', title, toast: true, position: 'top-end', showConfirmButton: false, timer: 1400 });

  const saveRegion = async () => {
    const name = regionForm.name.trim();
    if (!name) return;
    setSavingRegion(true);
    try {
      await api.post('/v1/customer-orders/regions', {
        id: regionForm.id,
        name,
        kind: tab === 'YANGON' ? 'YANGON' : regionForm.kind || 'STATE',
        active: regionForm.active,
        sortOrder: regionForm.sortOrder,
      });
      setRegionForm(emptyRegionForm());
      setRegionEditorOpen(false);
      await load();
      void toastSaved(regionForm.id ? 'တိုင်း/ပြည်နယ် ပြင်ပြီး' : 'တိုင်း/ပြည်နယ် ထည့်ပြီး');
    } catch (e: any) {
      void Swal.fire({ icon: 'error', title: 'သိမ်းမရပါ', text: e?.message || 'Region save failed' });
    } finally {
      setSavingRegion(false);
    }
  };

  const saveTownship = async () => {
    const name = townshipForm.name.trim();
    const regionId = townshipForm.regionId || selectedRegionId;
    if (!name || !regionId) return;
    setSavingTownship(true);
    try {
      await api.post('/v1/customer-orders/townships', {
        id: townshipForm.id,
        regionId,
        name,
        active: townshipForm.active,
        sortOrder: townshipForm.sortOrder,
      });
      setTownshipForm(emptyTownshipForm(regionId));
      setTownshipEditorOpen(false);
      await load();
      void toastSaved(townshipForm.id ? 'မြို့နယ် ပြင်ပြီး' : 'မြို့နယ် ထည့်ပြီး');
    } catch (e: any) {
      void Swal.fire({ icon: 'error', title: 'သိမ်းမရပါ', text: e?.message || 'Township save failed' });
    } finally {
      setSavingTownship(false);
    }
  };

  const saveWard = async () => {
    const name = wardForm.name.trim();
    const townshipId = wardForm.townshipId || selectedTownshipId;
    if (!name || !townshipId) return;
    setSavingWard(true);
    try {
      await api.post('/v1/customer-orders/wards', {
        id: wardForm.id,
        townshipId,
        name,
        deliveryCharge: Number(wardForm.deliveryCharge) || 0,
        active: wardForm.active,
        sortOrder: wardForm.sortOrder,
      });
      setWardForm(emptyWardForm(townshipId));
      setWardEditorOpen(false);
      await load();
      void toastSaved(wardForm.id ? 'ရပ်ကွက် ပြင်ပြီး' : 'ရပ်ကွက် ထည့်ပြီး');
    } catch (e: any) {
      void Swal.fire({ icon: 'error', title: 'သိမ်းမရပါ', text: e?.message || 'Ward save failed' });
    } finally {
      setSavingWard(false);
    }
  };

  const openNewRegion = () => {
    setRegionForm(emptyRegionForm());
    setRegionEditorOpen(true);
  };
  const openEditRegion = (r: Region) => {
    setRegionForm({
      id: r.id,
      name: r.name,
      kind: r.kind,
      active: r.active,
      sortOrder: r.sortOrder,
    });
    setRegionEditorOpen(true);
  };
  const openNewTownship = () => {
    if (!selectedRegionId) return;
    setTownshipForm(emptyTownshipForm(selectedRegionId));
    setTownshipEditorOpen(true);
  };
  const openEditTownship = (t: Township) => {
    setTownshipForm({
      id: t.id,
      regionId: t.regionId,
      name: t.name,
      active: t.active,
      sortOrder: t.sortOrder ?? 0,
    });
    setTownshipEditorOpen(true);
  };
  const openNewWard = () => {
    if (!selectedTownshipId) return;
    setWardForm(emptyWardForm(selectedTownshipId));
    setWardEditorOpen(true);
  };
  const openEditWard = (w: Ward) => {
    setWardForm({
      id: w.id,
      townshipId: w.townshipId,
      name: w.name,
      deliveryCharge: String(w.deliveryCharge ?? 0),
      active: w.active,
      sortOrder: w.sortOrder ?? 0,
    });
    setWardEditorOpen(true);
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-indigo-600 text-white shadow-xl shadow-indigo-100">
            <Truck size={22} />
          </div>
          <div className="min-w-0">
            <p className="text-[10px] font-black uppercase tracking-[0.2em] text-indigo-500">Customer app · Logistics</p>
            <h2 className="text-xl font-black tracking-tight text-slate-800">Delivery Charges</h2>
            <p className="mt-1 text-sm text-slate-500">
              တိုင်း → မြို့နယ် → ရပ်ကွက် အလိုက် ပို့ခ သတ်မှတ်ပါ။ ရပ်ကွက် မတူရင် စျေး ကွာနိုင်သည်။{' '}
              <Link to={AppRoute.CUSTOMER_APP_ORDERS} className="font-bold text-indigo-600 hover:underline">
                Customer App Orders
              </Link>
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          className="inline-flex items-center gap-2 self-start rounded-2xl border border-slate-200 bg-white px-4 py-2.5 text-xs font-black uppercase tracking-wide text-slate-600 shadow-sm transition hover:border-indigo-200 hover:text-indigo-700"
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {[
          { label: 'Regions', value: stats.regionCount, hint: tab === 'YANGON' ? 'ရန်ကုန်တိုင်း' : 'ပြည်နယ် / တိုင်း' },
          { label: 'Townships', value: stats.townshipCount, hint: 'ရွေးထားသော tab' },
          { label: 'Wards', value: stats.wardCount, hint: 'ပို့ခ သတ်မှတ်ပြီး' },
          {
            label: 'Charge range',
            value: stats.wardCount ? `${money(stats.min)}–${money(stats.max)}` : '—',
            hint: 'Ks · active tab',
          },
        ].map((card) => (
          <div key={card.label} className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">{card.label}</p>
            <p className="mt-1 text-lg font-black tracking-tight text-slate-800">{card.value}</p>
            <p className="text-[11px] text-slate-400">{card.hint}</p>
          </div>
        ))}
      </div>

      <DeliveryPolicyEditor />

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="inline-flex rounded-2xl border border-slate-200 bg-slate-50 p-1">
          {(
            [
              ['YANGON', 'ရန်ကုန်တိုင်း'],
              ['STATE', 'ပြည်နယ် / တိုင်းများ'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={`rounded-xl px-4 py-2 text-xs font-black transition ${
                tab === id ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="relative max-w-md flex-1 lg:max-w-sm">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="တိုင်း / မြို့နယ် / ရပ်ကွက် ရှာမည်"
            className="w-full rounded-2xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm font-semibold outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-50"
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-1.5 text-xs font-semibold text-slate-500">
        <Landmark size={13} className="text-indigo-500" />
        <span>{selectedRegion?.name || 'တိုင်း ရွေးပါ'}</span>
        <ChevronRight size={12} className="text-slate-300" />
        <Building2 size={13} className="text-indigo-500" />
        <span>{selectedTownship?.name || 'မြို့နယ် ရွေးပါ'}</span>
        <ChevronRight size={12} className="text-slate-300" />
        <MapPinned size={13} className="text-indigo-500" />
        <span>{wardsForTownship.length} ရပ်ကွက်</span>
      </div>

      <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)_minmax(0,1.15fr)]">
        <section className="flex min-h-[420px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">01 · Region</p>
              <p className="text-sm font-black text-slate-800">{tab === 'YANGON' ? 'ရန်ကုန်တိုင်း' : 'ပြည်နယ် / တိုင်း'}</p>
            </div>
            <button
              type="button"
              onClick={openNewRegion}
              className="inline-flex h-8 w-8 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm hover:bg-indigo-700"
              title="အသစ်ထည့်မည်"
            >
              <Plus size={16} />
            </button>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
            {loading ? (
              <div className="flex items-center justify-center gap-2 px-3 py-16 text-sm text-slate-400">
                <Loader2 size={16} className="animate-spin" /> Loading…
              </div>
            ) : regionsInTab.length === 0 ? (
              <div className="px-4 py-16 text-center text-sm text-slate-400">စာရင်း မရှိသေးပါ</div>
            ) : (
              regionsInTab.map((r) => (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => {
                    setSelectedRegionId(r.id);
                    setTownshipForm(emptyTownshipForm(r.id));
                    setTownshipEditorOpen(false);
                    setWardEditorOpen(false);
                    setSelectedTownshipId(null);
                  }}
                  className={`flex w-full items-center justify-between gap-2 border-b border-slate-50 px-4 py-3 text-left transition ${
                    selectedRegionId === r.id
                      ? 'border-l-4 border-l-indigo-600 bg-indigo-50/70'
                      : 'border-l-4 border-l-transparent hover:bg-slate-50'
                  }`}
                >
                  <span className="min-w-0 truncate text-sm font-bold text-slate-800">{r.name}</span>
                  <div className="flex shrink-0 items-center gap-2">
                    <StatusPill active={r.active} />
                    <span
                      role="button"
                      tabIndex={0}
                      onClick={(e) => {
                        e.stopPropagation();
                        openEditRegion(r);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.stopPropagation();
                          openEditRegion(r);
                        }
                      }}
                      className="rounded-lg p-1 text-slate-400 hover:bg-white hover:text-indigo-600"
                    >
                      <Pencil size={13} />
                    </span>
                  </div>
                </button>
              ))
            )}
          </div>
          {regionEditorOpen && (
            <div className="space-y-2 border-t border-slate-100 bg-slate-50/80 p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">
                  {regionForm.id ? 'တိုင်း/ပြည်နယ် ပြင်မည်' : 'တိုင်း/ပြည်နယ် အသစ်'}
                </p>
                <button type="button" onClick={() => setRegionEditorOpen(false)} className="text-slate-400 hover:text-slate-700">
                  <X size={14} />
                </button>
              </div>
              <input
                value={regionForm.name}
                onChange={(e) => setRegionForm((f) => ({ ...f, name: e.target.value }))}
                className={fieldClass}
                placeholder="အမည်"
              />
              {tab === 'STATE' && (
                <select
                  value={regionForm.kind}
                  onChange={(e) => setRegionForm((f) => ({ ...f, kind: e.target.value }))}
                  className={fieldClass}
                >
                  <option value="STATE">ပြည်နယ် / တိုင်း</option>
                  <option value="YANGON">ရန်ကုန်တိုင်း</option>
                </select>
              )}
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  value={regionForm.sortOrder}
                  onChange={(e) => setRegionForm((f) => ({ ...f, sortOrder: Number(e.target.value) || 0 }))}
                  className={`${fieldClass} w-24`}
                  placeholder="Sort"
                />
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <input
                    type="checkbox"
                    checked={regionForm.active}
                    onChange={(e) => setRegionForm((f) => ({ ...f, active: e.target.checked }))}
                  />
                  Active
                </label>
              </div>
              <button
                type="button"
                disabled={savingRegion || !regionForm.name.trim()}
                onClick={() => void saveRegion()}
                className="w-full rounded-xl bg-indigo-600 py-2.5 text-xs font-black uppercase tracking-wide text-white disabled:opacity-50"
              >
                {savingRegion ? 'Saving…' : 'သိမ်းမည်'}
              </button>
            </div>
          )}
        </section>

        <section className="flex min-h-[420px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div className="min-w-0">
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">02 · Township</p>
              <p className="truncate text-sm font-black text-slate-800">{selectedRegion?.name || 'မြို့နယ်များ'}</p>
            </div>
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-black text-indigo-700">
                {townshipsForRegion.length}
              </span>
              <button
                type="button"
                disabled={!selectedRegionId}
                onClick={openNewTownship}
                className="inline-flex h-8 w-8 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 disabled:opacity-40"
                title="မြို့နယ် အသစ်"
              >
                <Plus size={16} />
              </button>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-slate-50/95 text-[10px] font-black uppercase tracking-widest text-slate-400">
                <tr>
                  <th className="px-4 py-2.5 text-left">မြို့နယ်</th>
                  <th className="px-3 py-2.5 text-right">Charge</th>
                  <th className="px-3 py-2.5 text-center">Status</th>
                  <th className="px-3 py-2.5" />
                </tr>
              </thead>
              <tbody>
                {!selectedRegionId ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-16 text-center text-slate-400">
                      ဘယ်ဘက်မှ တိုင်း/ပြည်နယ် ရွေးပါ
                    </td>
                  </tr>
                ) : townshipsInRegion.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-16 text-center text-slate-400">
                      မြို့နယ် မရှိသေးပါ — + နှိပ်ပြီး ထည့်ပါ
                    </td>
                  </tr>
                ) : (
                  townshipsInRegion.map((t) => {
                    const wardCount = wards.filter((w) => w.townshipId === t.id).length;
                    return (
                      <tr
                        key={t.id}
                        className={`cursor-pointer border-t border-slate-50 transition ${
                          selectedTownshipId === t.id ? 'bg-indigo-50/80' : 'hover:bg-slate-50'
                        }`}
                        onClick={() => {
                          setSelectedTownshipId(t.id);
                          setWardForm(emptyWardForm(t.id));
                          setWardEditorOpen(false);
                        }}
                      >
                        <td className="px-4 py-3">
                          <p className="font-bold text-slate-800">{t.name}</p>
                          <p className="text-[11px] text-slate-400">{wardCount} ရပ်ကွက်</p>
                        </td>
                        <td className="whitespace-nowrap px-3 py-3 text-right text-xs font-bold text-indigo-700">
                          {townshipChargeLabel(t.id)}
                        </td>
                        <td className="px-3 py-3 text-center">
                          <StatusPill active={t.active} />
                        </td>
                        <td className="px-3 py-3 text-right">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              openEditTownship(t);
                            }}
                            className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-bold text-slate-500 hover:bg-white hover:text-indigo-600"
                          >
                            <Pencil size={12} /> Edit
                          </button>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {townshipEditorOpen && (
            <div className="space-y-2 border-t border-slate-100 bg-slate-50/80 p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">
                  {townshipForm.id ? 'မြို့နယ် ပြင်မည်' : 'မြို့နယ် အသစ်'}
                </p>
                <button type="button" onClick={() => setTownshipEditorOpen(false)} className="text-slate-400 hover:text-slate-700">
                  <X size={14} />
                </button>
              </div>
              <input
                value={townshipForm.name}
                onChange={(e) => setTownshipForm((f) => ({ ...f, name: e.target.value }))}
                className={fieldClass}
                placeholder="ဥပမာ — တောင်ဒဂုံ"
                disabled={!selectedRegionId}
              />
              <div className="flex flex-wrap items-center gap-2">
                <input
                  type="number"
                  value={townshipForm.sortOrder}
                  onChange={(e) => setTownshipForm((f) => ({ ...f, sortOrder: Number(e.target.value) || 0 }))}
                  className={`${fieldClass} w-24`}
                  disabled={!selectedRegionId}
                />
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <input
                    type="checkbox"
                    checked={townshipForm.active}
                    onChange={(e) => setTownshipForm((f) => ({ ...f, active: e.target.checked }))}
                    disabled={!selectedRegionId}
                  />
                  Active
                </label>
                <button
                  type="button"
                  disabled={savingTownship || !selectedRegionId || !townshipForm.name.trim()}
                  onClick={() => void saveTownship()}
                  className="ml-auto rounded-xl bg-indigo-600 px-4 py-2 text-xs font-black uppercase text-white disabled:opacity-50"
                >
                  {savingTownship ? 'Saving…' : townshipForm.id ? 'Update' : 'Add'}
                </button>
              </div>
            </div>
          )}
        </section>

        <section className="flex min-h-[420px] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div className="min-w-0">
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">03 · Ward · Charge</p>
              <p className="truncate text-sm font-black text-slate-800">
                {selectedTownship?.name || 'မြို့နယ် ရွေးပါ'}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[10px] font-black text-indigo-700">
                {wardsForTownship.length}
              </span>
              <button
                type="button"
                disabled={!selectedTownshipId}
                onClick={openNewWard}
                className="inline-flex h-8 w-8 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 disabled:opacity-40"
                title="ရပ်ကွက် + charge"
              >
                <Plus size={16} />
              </button>
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-slate-50/95 text-[10px] font-black uppercase tracking-widest text-slate-400">
                <tr>
                  <th className="px-4 py-2.5 text-left">ရပ်ကွက်</th>
                  <th className="px-3 py-2.5 text-right">Charge</th>
                  <th className="px-3 py-2.5 text-center">Status</th>
                  <th className="px-3 py-2.5" />
                </tr>
              </thead>
              <tbody>
                {!selectedTownshipId ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-16 text-center text-slate-400">
                      မြို့နယ် တစ်ခု ရွေးပါ
                    </td>
                  </tr>
                ) : wardsInTownship.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-16 text-center text-slate-400">
                      ရပ်ကွက် မရှိသေးပါ — + နှိပ်ပြီး ပို့ခ ထည့်ပါ
                    </td>
                  </tr>
                ) : (
                  wardsInTownship.map((w) => (
                    <tr key={w.id} className="border-t border-slate-50 hover:bg-slate-50">
                      <td className="px-4 py-3 font-bold text-slate-800">{w.name}</td>
                      <td className="whitespace-nowrap px-3 py-3 text-right">
                        <span className="rounded-lg bg-indigo-50 px-2 py-1 text-xs font-black text-indigo-700">
                          {money(w.deliveryCharge)} Ks
                        </span>
                      </td>
                      <td className="px-3 py-3 text-center">
                        <StatusPill active={w.active} />
                      </td>
                      <td className="px-3 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => openEditWard(w)}
                          className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-bold text-slate-500 hover:bg-white hover:text-indigo-600"
                        >
                          <Pencil size={12} /> Edit
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {wardEditorOpen && (
            <div className="space-y-2 border-t border-slate-100 bg-slate-50/80 p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-black uppercase tracking-wide text-slate-500">
                  {wardForm.id ? 'ရပ်ကွက် ပြင်မည်' : 'ရပ်ကွက် အသစ် + charge'}
                </p>
                <button type="button" onClick={() => setWardEditorOpen(false)} className="text-slate-400 hover:text-slate-700">
                  <X size={14} />
                </button>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                <input
                  value={wardForm.name}
                  onChange={(e) => setWardForm((f) => ({ ...f, name: e.target.value }))}
                  className={`${fieldClass} sm:col-span-2`}
                  placeholder="ဥပမာ — အမှတ် (၃) ရပ်ကွက်"
                  disabled={!selectedTownshipId}
                />
                <input
                  type="number"
                  min={0}
                  value={wardForm.deliveryCharge}
                  onChange={(e) => setWardForm((f) => ({ ...f, deliveryCharge: e.target.value }))}
                  className={fieldClass}
                  placeholder="Delivery charge"
                  disabled={!selectedTownshipId}
                />
                <input
                  type="number"
                  value={wardForm.sortOrder}
                  onChange={(e) => setWardForm((f) => ({ ...f, sortOrder: Number(e.target.value) || 0 }))}
                  className={fieldClass}
                  placeholder="Sort"
                  disabled={!selectedTownshipId}
                />
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <input
                    type="checkbox"
                    checked={wardForm.active}
                    onChange={(e) => setWardForm((f) => ({ ...f, active: e.target.checked }))}
                    disabled={!selectedTownshipId}
                  />
                  Active
                </label>
                <button
                  type="button"
                  disabled={savingWard || !selectedTownshipId || !wardForm.name.trim()}
                  onClick={() => void saveWard()}
                  className="ml-auto rounded-xl bg-indigo-600 px-4 py-2 text-xs font-black uppercase text-white disabled:opacity-50"
                >
                  {savingWard ? 'Saving…' : wardForm.id ? 'Update' : 'Add ward'}
                </button>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
};

export default DeliveryChargesPage;
