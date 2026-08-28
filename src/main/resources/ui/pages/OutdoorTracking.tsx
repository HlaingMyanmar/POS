import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MapPin, Navigation, RefreshCw } from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import { TechnicianVisitDTO, technicianVisitService } from '../services/technicianVisitService';

declare global {
  interface Window {
    L?: any;
  }
}

const OSM_TILE = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
const OSM_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

const isStale = (recordedAt?: string) => {
  if (!recordedAt) return true;
  const ts = new Date(recordedAt).getTime();
  if (Number.isNaN(ts)) return true;
  return Date.now() - ts > 2 * 60 * 1000;
};

const statusLabel = (visit: TechnicianVisitDTO) =>
  [visit.status, visit.motionStatus].filter(Boolean).join(' · ');

const OutdoorTracking: React.FC = () => {
  const [visits, setVisits] = useState<TechnicianVisitDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const mapRef = useRef<HTMLDivElement>(null);
  const mapApi = useRef<any>(null);
  const markers = useRef<Map<number, any>>(new Map());

  const load = useCallback(async () => {
    try {
      const rows = await technicianVisitService.live();
      setVisits(rows);
      setError('');
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Live location မရပါ');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useWebsocket('/topic/technician-location', (body) => {
    try {
      const visit = JSON.parse(body) as TechnicianVisitDTO;
      setVisits((prev) => {
        const without = prev.filter((row) => row.id !== visit.id && row.staffId !== visit.staffId);
        if (visit.status === 'COMPLETED' || visit.status === 'CANCELLED') return without;
        return [visit, ...without];
      });
    } catch {
      void load();
    }
  });

  useEffect(() => {
    if (!mapRef.current || !window.L || mapApi.current) return;
    mapApi.current = window.L.map(mapRef.current).setView([16.8409, 96.1735], 12);
    window.L.tileLayer(OSM_TILE, { attribution: OSM_ATTR, maxZoom: 19 }).addTo(mapApi.current);
  }, []);

  useEffect(() => {
    const map = mapApi.current;
    const L = window.L;
    if (!map || !L) return;
    const seen = new Set<number>();
    visits.forEach((visit) => {
      if (visit.latitude == null || visit.longitude == null) return;
      seen.add(visit.id);
      const stale = isStale(visit.recordedAt);
      const html = `<div style="background:${stale ? '#94a3b8' : '#059669'};color:white;border-radius:999px;padding:4px 8px;font-size:11px;font-weight:700;white-space:nowrap">${visit.staffName}</div>`;
      const icon = L.divIcon({ html, className: '' });
      let marker = markers.current.get(visit.id);
      if (!marker) {
        marker = L.marker([visit.latitude, visit.longitude], { icon }).addTo(map);
        markers.current.set(visit.id, marker);
      } else {
        marker.setLatLng([visit.latitude, visit.longitude]);
        marker.setIcon(icon);
      }
      marker.bindPopup(
        `<strong>${visit.staffName}</strong><br/>${visit.jobNo} · ${visit.customerName}<br/>${statusLabel(visit)}`
      );
    });
    markers.current.forEach((marker, id) => {
      if (!seen.has(id)) {
        map.removeLayer(marker);
        markers.current.delete(id);
      }
    });
  }, [visits]);

  const selected = useMemo(
    () => visits.find((row) => row.id === selectedId) || visits[0] || null,
    [visits, selectedId]
  );

  useEffect(() => {
    if (!selected || selected.latitude == null || selected.longitude == null || !mapApi.current) return;
    mapApi.current.setView([selected.latitude, selected.longitude], 14);
  }, [selected?.id]);

  return (
    <div className="p-4 md:p-6 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-black text-slate-800">Outdoor Tracking</h1>
          <p className="text-sm text-slate-500">Active technician visits · Leaflet + OpenStreetMap</p>
        </div>
        <button
          onClick={() => void load()}
          className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm font-semibold text-slate-700 hover:bg-slate-50"
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {error && <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</div>}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="xl:col-span-1 rounded-xl border border-slate-200 bg-white overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
            Live list ({visits.length})
          </div>
          {loading ? (
            <p className="p-4 text-sm text-slate-500">Loading…</p>
          ) : visits.length === 0 ? (
            <p className="p-4 text-sm text-slate-500">Active outdoor visit မရှိသေးပါ။</p>
          ) : (
            <div className="divide-y divide-slate-100 max-h-[70vh] overflow-auto">
              {visits.map((visit) => {
                const stale = isStale(visit.recordedAt);
                return (
                  <button
                    key={visit.id}
                    onClick={() => setSelectedId(visit.id)}
                    className={`w-full text-left px-4 py-3 hover:bg-slate-50 ${selectedId === visit.id ? 'bg-emerald-50' : ''}`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-bold text-slate-800">{visit.staffName}</p>
                      <span className={`text-[10px] font-black uppercase ${stale ? 'text-slate-400' : 'text-emerald-600'}`}>
                        {stale ? 'STALE' : visit.motionStatus || visit.status}
                      </span>
                    </div>
                    <p className="text-sm text-slate-600">{visit.jobNo} · {visit.customerName}</p>
                    <p className="text-xs text-slate-400">{visit.recordedAt ? new Date(visit.recordedAt).toLocaleTimeString() : '—'}</p>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="xl:col-span-2 rounded-xl border border-slate-200 bg-white overflow-hidden min-h-[420px]">
          <div ref={mapRef} className="w-full h-[420px] xl:h-full min-h-[420px]" />
          {!window.L && (
            <div className="p-4 text-sm text-slate-500 flex items-center gap-2">
              <MapPin size={16} /> Map မတင်နိုင်ပါ။ List ကို ဆက်သုံးနိုင်သည်။
            </div>
          )}
        </div>
      </div>

      {selected && (
        <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600 flex items-start gap-3">
          <Navigation size={16} className="mt-0.5 text-emerald-600" />
          <div>
            <p className="font-bold text-slate-800">{selected.staffName} · {selected.jobNo}</p>
            <p>{selected.customerName} · {statusLabel(selected)}</p>
            {selected.distanceMeters != null && <p>Customer နှင့် {Math.round(selected.distanceMeters)} m</p>}
            {selected.needsReason && <p className="text-amber-600 font-semibold">Long stop — technician အကြောင်းရင်း မပေးသေး</p>}
          </div>
        </div>
      )}
    </div>
  );
};

export default OutdoorTracking;
