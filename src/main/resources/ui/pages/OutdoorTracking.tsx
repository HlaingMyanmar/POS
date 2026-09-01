import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Clock3, MapPin, Navigation, RefreshCw } from 'lucide-react';
import { useWebsocket } from '../hooks/useWebsocket';
import { addCartoBaseLayers, addCartoTileLayer } from '../config/mapTiles';
import {
  LocationPingDTO,
  TechnicianVisitDTO,
  TechnicianVisitReportDTO,
  VisitEventDTO,
  technicianVisitService
} from '../services/technicianVisitService';

declare global {
  interface Window {
    L?: any;
  }
}
const OSRM_URL = (
  import.meta.env.VITE_OSRM_URL || 'https://router.project-osrm.org'
).replace(/\/+$/, '');

interface RouteSummary {
  distanceMeters: number;
  durationSeconds: number;
}

interface StopSummary {
  event: VisitEventDTO;
  endedAt?: string;
  durationMinutes: number;
  longStop: boolean;
  reasonCode?: string;
  note?: string;
}

const haversineMeters = (lat1: number, lng1: number, lat2: number, lng2: number) => {
  const radians = (value: number) => value * Math.PI / 180;
  const dLat = radians(lat2 - lat1);
  const dLng = radians(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(radians(lat1)) * Math.cos(radians(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

const buildStopSummaries = (events: VisitEventDTO[], visitEndedAt?: string): StopSummary[] => {
  const ordered = [...events].sort(
    (a, b) => new Date(a.occurredAt || 0).getTime() - new Date(b.occurredAt || 0).getTime()
  );
  const result: StopSummary[] = [];
  let active: {
    event: VisitEventDTO;
    longStop: boolean;
    reasonCode?: string;
    note?: string;
  } | null = null;

  ordered.forEach((event) => {
    if (event.eventType === 'STOPPED') {
      if (!active) active = { event, longStop: false };
      return;
    }
    if (event.eventType === 'LONG_STOP') {
      active = active ? { ...active, longStop: true } : { event, longStop: true };
      return;
    }
    if (event.eventType === 'REASON_ADDED' && active) {
      active = {
        ...active,
        reasonCode: event.reasonCode,
        note: event.note
      };
      return;
    }
    if (active && ['RESUMED', 'ARRIVED', 'CUSTOMER_DEPARTED', 'ENDED', 'CANCELLED'].includes(event.eventType)) {
      const start = new Date(active.event.occurredAt || 0).getTime();
      const end = new Date(event.occurredAt || 0).getTime();
      result.push({
        ...active,
        endedAt: event.occurredAt,
        durationMinutes: Math.max(0, Math.round((end - start) / 60000))
      });
      active = null;
    }
  });

  if (active) {
    const start = new Date(active.event.occurredAt || 0).getTime();
    const end = new Date(visitEndedAt || Date.now()).getTime();
    result.push({
      ...active,
      endedAt: visitEndedAt,
      durationMinutes: Math.max(0, Math.round((end - start) / 60000))
    });
  }
  return result;
};

const isStale = (recordedAt?: string) => {
  if (!recordedAt) return true;
  const ts = new Date(recordedAt).getTime();
  if (Number.isNaN(ts)) return true;
  return Date.now() - ts > 2 * 60 * 1000;
};

const dateInputValue = (date = new Date()) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const statusLabel = (visit: TechnicianVisitDTO) =>
  [visit.status, visit.motionStatus].filter(Boolean).join(' · ');

const timeLabel = (value?: string) => {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
};

const durationLabel = (from?: string, to?: string) => {
  if (!from) return '—';
  const start = new Date(from).getTime();
  const finish = to ? new Date(to).getTime() : Date.now();
  if (Number.isNaN(start) || Number.isNaN(finish) || finish < start) return '—';
  const minutes = Math.floor((finish - start) / 60000);
  const hours = Math.floor(minutes / 60);
  return hours ? `${hours} နာရီ ${minutes % 60} မိနစ်` : `${minutes} မိနစ်`;
};

const minutesLabel = (value?: number) => {
  if (value == null) return '—';
  const hours = Math.floor(value / 60);
  return hours ? `${hours} နာရီ ${value % 60} မိနစ်` : `${value} မိနစ်`;
};

const downloadText = (content: string, type: string, fileName: string) => {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
};

const csvCell = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`;
const xmlEscape = (value: unknown) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&apos;');

const eventLabel: Record<string, string> = {
  STARTED: 'ထွက်ခွာ',
  STOPPED: 'ခဏရပ်',
  LONG_STOP: 'ကြာမြင့်စွာရပ်',
  RESUMED: 'ခရီးဆက်',
  REASON_ADDED: 'အကြောင်းရင်းပေး',
  NEAR_CUSTOMER: 'Customer အနီးရောက်',
  ARRIVED: 'Customer ဆီရောက်',
  CUSTOMER_DEPARTED: 'Customer ဆီမှပြန်ထွက်',
  ENDED: 'ပြန်ရောက် / Visit ပိတ်',
  CANCELLED: 'Visit ပယ်ဖျက်',
  GPS_HISTORY_DELETED: 'Raw GPS history ဖျက်ခဲ့သည်'
};

const OutdoorTracking: React.FC = () => {
  const [visits, setVisits] = useState<TechnicianVisitDTO[]>([]);
  const [todayVisits, setTodayVisits] = useState<TechnicianVisitDTO[]>([]);
  const [reportRows, setReportRows] = useState<TechnicianVisitReportDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [mapMessage, setMapMessage] = useState('');
  const [routeSummary, setRouteSummary] = useState<RouteSummary | null>(null);
  const [routeLoading, setRouteLoading] = useState(false);
  const [routeMessage, setRouteMessage] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [historyFrom, setHistoryFrom] = useState(() => dateInputValue());
  const [historyTo, setHistoryTo] = useState(() => dateInputValue());
  const [historySearch, setHistorySearch] = useState('');
  const [appliedHistoryRange, setAppliedHistoryRange] = useState(() => ({
    from: dateInputValue(),
    to: dateInputValue()
  }));
  const [replayVisit, setReplayVisit] = useState<TechnicianVisitDTO | null>(null);
  const [replayPings, setReplayPings] = useState<LocationPingDTO[]>([]);
  const [replayLoading, setReplayLoading] = useState(false);
  const [replayError, setReplayError] = useState('');
  const mapRef = useRef<HTMLDivElement>(null);
  const replayMapRef = useRef<HTMLDivElement>(null);
  const mapApi = useRef<any>(null);
  const markers = useRef<Map<number, any>>(new Map());
  const routeLayer = useRef<any>(null);

  const load = useCallback(async () => {
    try {
      const [liveResult, historyResult, reportResult] = await Promise.allSettled([
        technicianVisitService.live(),
        technicianVisitService.history(
          `${appliedHistoryRange.from}T00:00:00`,
          `${appliedHistoryRange.to}T23:59:59`
        ),
        technicianVisitService.report(
          `${appliedHistoryRange.from}T00:00:00`,
          `${appliedHistoryRange.to}T23:59:59`
        )
      ]);
      if (liveResult.status === 'fulfilled') setVisits(liveResult.value);
      if (historyResult.status === 'fulfilled') setTodayVisits(historyResult.value);
      if (reportResult.status === 'fulfilled') setReportRows(reportResult.value);
      if (liveResult.status === 'rejected') {
        setError('Live location မရပါ။');
      } else if (historyResult.status === 'rejected' || reportResult.status === 'rejected') {
        setError('History filter အတွက် CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ permission လိုအပ်ပါသည်။');
      } else {
        setError('');
      }
    } finally {
      setLoading(false);
    }
  }, [appliedHistoryRange]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const refresh = window.setInterval(() => { void load(); }, 30_000);
    return () => window.clearInterval(refresh);
  }, [load]);

  useWebsocket('/topic/technician-location', (body) => {
    try {
      const visit = JSON.parse(body) as TechnicianVisitDTO;
      setVisits((prev) => {
        const without = prev.filter((row) => row.id !== visit.id && row.staffId !== visit.staffId);
        if (visit.status === 'COMPLETED' || visit.status === 'CANCELLED') return without;
        return [visit, ...without];
      });
      void load();
    } catch {
      void load();
    }
  });

  useEffect(() => {
    if (!mapRef.current || !window.L || mapApi.current) return;
    const L = window.L;
    const map = L.map(mapRef.current).setView([16.8409, 96.1735], 12);
    mapApi.current = map;

    addCartoBaseLayers(L, map, setMapMessage);

    return () => {
      markers.current.clear();
      map.remove();
      mapApi.current = null;
    };
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
  const filteredHistoryVisits = useMemo(() => {
    const query = historySearch.trim().toLocaleLowerCase();
    if (!query) return todayVisits;
    return todayVisits.filter((visit) =>
      visit.jobNo?.toLocaleLowerCase().includes(query) ||
      visit.customerName?.toLocaleLowerCase().includes(query)
    );
  }, [todayVisits, historySearch]);
  const filteredReportRows = useMemo(() => {
    const query = historySearch.trim().toLocaleLowerCase();
    if (!query) return reportRows;
    return reportRows.filter((row) =>
      row.jobNo?.toLocaleLowerCase().includes(query) ||
      row.customerName?.toLocaleLowerCase().includes(query)
    );
  }, [reportRows, historySearch]);
  const reportSummary = useMemo(() => ({
    visits: filteredReportRows.length,
    completed: filteredReportRows.filter((row) => row.status === 'COMPLETED').length,
    verified: filteredReportRows.filter((row) => row.arrivalVerified === true).length,
    distanceKm: filteredReportRows.reduce((sum, row) => sum + (row.actualDistanceMeters || 0), 0) / 1000,
    stopMinutes: filteredReportRows.reduce((sum, row) => sum + (row.stopMinutes || 0), 0),
    gpsExceptions: filteredReportRows.filter((row) => Boolean(row.gpsException)).length
  }), [filteredReportRows]);

  useEffect(() => {
    if (!selected || selected.latitude == null || selected.longitude == null || !mapApi.current) return;
    mapApi.current.setView([selected.latitude, selected.longitude], 14);
  }, [selected?.id]);

  useEffect(() => {
    const map = mapApi.current;
    const L = window.L;
    if (!map || !L) return;

    if (routeLayer.current) {
      map.removeLayer(routeLayer.current);
      routeLayer.current = null;
    }
    setRouteSummary(null);
    setRouteMessage('');

    if (!selected) return;
    if (selected.latitude == null || selected.longitude == null) {
      setRouteMessage('Technician GPS မရသေးပါ။');
      return;
    }
    if (selected.customerLatitude == null || selected.customerLongitude == null) {
      setRouteMessage('Customer GPS မရှိသဖြင့် လမ်းကြောင်းမတွက်နိုင်ပါ။');
      return;
    }

    const controller = new AbortController();
    const coordinates =
      `${selected.longitude},${selected.latitude};` +
      `${selected.customerLongitude},${selected.customerLatitude}`;
    const url =
      `${OSRM_URL}/route/v1/driving/${coordinates}` +
      '?overview=full&geometries=geojson&steps=false';

    setRouteLoading(true);
    fetch(url, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error(`OSRM ${response.status}`);
        return response.json();
      })
      .then((payload) => {
        const route = payload?.routes?.[0];
        if (!route?.geometry) throw new Error('Route not found');
        routeLayer.current = L.geoJSON(route.geometry, {
          style: { color: '#4F46E5', weight: 5, opacity: 0.85 }
        }).addTo(map);
        const customerMarker = L.circleMarker(
          [selected.customerLatitude, selected.customerLongitude],
          {
            radius: 8,
            color: '#DC2626',
            fillColor: '#EF4444',
            fillOpacity: 0.9,
            weight: 2
          }
        ).bindPopup(`<strong>${selected.customerName}</strong><br/>Customer location`);
        customerMarker.addTo(routeLayer.current);
        map.fitBounds(routeLayer.current.getBounds(), { padding: [30, 30] });
        setRouteSummary({
          distanceMeters: Number(route.distance) || 0,
          durationSeconds: Number(route.duration) || 0
        });
      })
      .catch((error) => {
        if (error?.name !== 'AbortError') {
          setRouteMessage('OSRM လမ်းကြောင်းမရပါ။ Internet သို့မဟုတ် routing server ကို စစ်ပါ။');
        }
      })
      .finally(() => setRouteLoading(false));

    return () => controller.abort();
  }, [
    selected?.id,
    selected?.latitude,
    selected?.longitude,
    selected?.customerLatitude,
    selected?.customerLongitude
  ]);

  const openReplay = useCallback(async (visit: TechnicianVisitDTO) => {
    setReplayLoading(true);
    setReplayError('');
    setReplayVisit(visit);
    setReplayPings([]);
    try {
      const [detail, pings] = await Promise.all([
        technicianVisitService.detail(visit.id),
        technicianVisitService.historyPings(visit.id)
      ]);
      setReplayVisit(detail);
      setReplayPings(pings);
      window.setTimeout(() => {
        replayMapRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 100);
    } catch (err: any) {
      setReplayError(
        err?.response?.status === 403
          ? 'History map ကြည့်ရန် CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ permission လိုအပ်ပါသည်။'
          : err?.response?.data?.message || 'Visit GPS history မရပါ။'
      );
    } finally {
      setReplayLoading(false);
    }
  }, []);

  const replayStops = useMemo(
    () => buildStopSummaries(replayVisit?.events || [], replayVisit?.endedAt),
    [replayVisit]
  );
  const replayArrival = useMemo(
    () => replayVisit?.events?.find((event) => event.eventType === 'ARRIVED') || null,
    [replayVisit]
  );
  const replayDeparture = useMemo(
    () => replayVisit?.events?.find((event) => event.eventType === 'CUSTOMER_DEPARTED') || null,
    [replayVisit]
  );
  const arrivalDistance = useMemo(() => {
    if (
      replayArrival?.latitude == null || replayArrival.longitude == null ||
      replayVisit?.customerLatitude == null || replayVisit.customerLongitude == null
    ) return null;
    return haversineMeters(
      replayArrival.latitude,
      replayArrival.longitude,
      replayVisit.customerLatitude,
      replayVisit.customerLongitude
    );
  }, [replayArrival, replayVisit]);

  useEffect(() => {
    if (replayLoading || !replayVisit || !replayMapRef.current || !window.L) return;
    const L = window.L;
    const map = L.map(replayMapRef.current).setView([16.8409, 96.1735], 12);
    addCartoTileLayer(L, map);

    const points = replayPings
      .filter((ping) => Number.isFinite(ping.latitude) && Number.isFinite(ping.longitude))
      .map((ping) => [ping.latitude, ping.longitude]);
    const boundsPoints: number[][] = [...points];

    if (points.length > 1) {
      L.polyline(points, { color: '#4F46E5', weight: 5, opacity: 0.85 })
        .bindPopup(`မှတ်တမ်းတင်ထားသော GPS လမ်းကြောင်း · ${points.length} points`)
        .addTo(map);
    }
    if (points.length > 0) {
      L.circleMarker(points[0], {
        radius: 7, color: '#047857', fillColor: '#10B981', fillOpacity: 1
      }).bindPopup(`စထွက်ချိန် ${timeLabel(replayVisit.startedAt)}`).addTo(map);
      L.circleMarker(points[points.length - 1], {
        radius: 7, color: '#0F172A', fillColor: '#334155', fillOpacity: 1
      }).bindPopup(`နောက်ဆုံး GPS ${timeLabel(replayPings[replayPings.length - 1]?.recordedAt)}`).addTo(map);
    }

    if (replayVisit.customerLatitude != null && replayVisit.customerLongitude != null) {
      const customerPoint = [replayVisit.customerLatitude, replayVisit.customerLongitude];
      boundsPoints.push(customerPoint);
      L.circleMarker(customerPoint, {
        radius: 10, color: '#B91C1C', fillColor: '#EF4444', fillOpacity: 1, weight: 3
      }).bindPopup(`<strong>${replayVisit.customerName}</strong><br/>သတ်မှတ်ထားသော Customer GPS`)
        .addTo(map);
    }

    if (replayArrival?.latitude != null && replayArrival.longitude != null) {
      const arrivalPoint = [replayArrival.latitude, replayArrival.longitude];
      boundsPoints.push(arrivalPoint);
      L.circleMarker(arrivalPoint, {
        radius: 8, color: '#1D4ED8', fillColor: '#3B82F6', fillOpacity: 1, weight: 3
      }).bindPopup(
        `<strong>ရောက်ပြီ နှိပ်ခဲ့သောနေရာ</strong><br/>${timeLabel(replayArrival.occurredAt)}` +
        (arrivalDistance != null ? `<br/>Customer GPS မှ ${Math.round(arrivalDistance)} m` : '')
      ).addTo(map);
    }

    if (replayDeparture?.latitude != null && replayDeparture.longitude != null) {
      const departurePoint = [replayDeparture.latitude, replayDeparture.longitude];
      boundsPoints.push(departurePoint);
      L.circleMarker(departurePoint, {
        radius: 8, color: '#6D28D9', fillColor: '#8B5CF6', fillOpacity: 1, weight: 3
      }).bindPopup(
        `<strong>Customer ဆီမှ ပြန်ထွက်ခဲ့သောနေရာ</strong><br/>${timeLabel(replayDeparture.occurredAt)}`
      ).addTo(map);
    }

    (replayVisit.events || [])
      .filter((event) =>
        event.eventType === 'REASON_ADDED' &&
        event.latitude != null &&
        event.longitude != null
      )
      .forEach((event) => {
        const point = [event.latitude, event.longitude];
        boundsPoints.push(point as number[]);
        L.circleMarker(point, {
          radius: 9,
          color: '#0E7490',
          fillColor: '#06B6D4',
          fillOpacity: 1,
          weight: 3
        }).bindPopup(
          `<strong>အကြောင်းပြချက်ပေးခဲ့သောနေရာ</strong>` +
          `<br/>${timeLabel(event.occurredAt)}` +
          (event.reasonCode ? `<br/>${event.reasonCode}` : '') +
          (event.note ? `<br/>${event.note}` : '')
        ).addTo(map);
      });

    replayStops.forEach((stop) => {
      if (stop.event.latitude == null || stop.event.longitude == null) return;
      const point = [stop.event.latitude, stop.event.longitude];
      boundsPoints.push(point);
      L.circleMarker(point, {
        radius: 8,
        color: stop.longStop ? '#9A3412' : '#B45309',
        fillColor: stop.longStop ? '#EA580C' : '#F59E0B',
        fillOpacity: 1,
        weight: 2
      }).bindPopup(
        `<strong>${stop.longStop ? 'ကြာမြင့်စွာ ရပ်နား' : 'ရပ်နား'}</strong>` +
        `<br/>${timeLabel(stop.event.occurredAt)} · ${stop.durationMinutes} မိနစ်` +
        (stop.note ? `<br/>${stop.note}` : '')
      ).addTo(map);
    });

    if (boundsPoints.length > 1) {
      map.fitBounds(boundsPoints, { padding: [30, 30] });
    } else if (boundsPoints.length === 1) {
      map.setView(boundsPoints[0], 16);
    }
    window.setTimeout(() => map.invalidateSize(), 0);
    return () => map.remove();
  }, [
    replayLoading,
    replayVisit,
    replayPings,
    replayStops,
    replayArrival,
    replayDeparture,
    arrivalDistance
  ]);

  const exportReportCsv = () => {
    const headers = [
      'Technician', 'Job No', 'Customer', 'Status', 'Started At', 'Arrived At',
      'Left Customer At', 'Ended At', 'Outbound Minutes', 'On-site Minutes',
      'Return Minutes', 'Total Minutes', 'Actual Distance Km',
      'Arrival Distance M', 'Arrival Verified', 'Stop Count', 'Stop Minutes',
      'Stop Reasons', 'GPS Points', 'Max GPS Gap Minutes', 'GPS Exception'
    ];
    const rows = filteredReportRows.map((row) => [
      row.staffName, row.jobNo, row.customerName, row.status, row.startedAt,
      row.arrivedAt, row.leftCustomerAt, row.endedAt, row.outboundMinutes,
      row.onSiteMinutes, row.returnMinutes, row.totalMinutes,
      ((row.actualDistanceMeters || 0) / 1000).toFixed(2),
      row.arrivalDistanceMeters?.toFixed(1),
      row.arrivalVerified == null ? '' : row.arrivalVerified ? 'YES' : 'NO',
      row.stopCount, row.stopMinutes, row.stopReasons?.join(' | '),
      row.gpsPointCount, row.maxGpsGapMinutes, row.gpsException
    ]);
    const csv = '\uFEFF' + [headers, ...rows]
      .map((row) => row.map(csvCell).join(','))
      .join('\r\n');
    downloadText(
      csv,
      'text/csv;charset=utf-8',
      `outdoor-tracking-${appliedHistoryRange.from}-${appliedHistoryRange.to}.csv`
    );
  };

  const printReport = () => {
    const reportWindow = window.open('', '_blank');
    if (!reportWindow) {
      setError('Print window ဖွင့်မရပါ။ Browser popup ကို allow လုပ်ပါ။');
      return;
    }
    reportWindow.opener = null;
    const rows = filteredReportRows.map((row) => `
      <tr>
        <td>${xmlEscape(row.staffName)}</td><td>${xmlEscape(row.jobNo)}</td>
        <td>${xmlEscape(row.customerName)}</td><td>${xmlEscape(row.status)}</td>
        <td>${xmlEscape(row.startedAt || '')}</td><td>${xmlEscape(row.arrivedAt || '')}</td>
        <td>${xmlEscape(row.leftCustomerAt || '')}</td><td>${xmlEscape(row.endedAt || '')}</td>
        <td>${xmlEscape(minutesLabel(row.outboundMinutes))}</td>
        <td>${xmlEscape(minutesLabel(row.onSiteMinutes))}</td>
        <td>${xmlEscape(minutesLabel(row.returnMinutes))}</td>
        <td>${xmlEscape(minutesLabel(row.totalMinutes))}</td>
        <td>${((row.actualDistanceMeters || 0) / 1000).toFixed(2)}</td>
        <td>${row.arrivalDistanceMeters == null ? '—' : `${Math.round(row.arrivalDistanceMeters)} m`}</td>
        <td>${row.stopCount} / ${row.stopMinutes} min</td>
        <td>${xmlEscape(row.gpsException || 'OK')}</td>
      </tr>`).join('');
    reportWindow.document.write(`<!doctype html><html><head><meta charset="utf-8">
      <title>Outdoor Tracking Report</title>
      <style>
        body{font-family:Arial,sans-serif;margin:20px;color:#172033}h1{font-size:20px}
        .summary{display:flex;gap:18px;margin:12px 0;font-size:12px}
        table{border-collapse:collapse;width:100%;font-size:9px}th,td{border:1px solid #cbd5e1;padding:5px;text-align:left}
        th{background:#eef2ff}@page{size:landscape;margin:10mm}
      </style></head><body>
      <h1>Outdoor Tracking Report</h1>
      <p>${xmlEscape(appliedHistoryRange.from)} to ${xmlEscape(appliedHistoryRange.to)}
      ${historySearch ? ` · Filter: ${xmlEscape(historySearch)}` : ''}</p>
      <div class="summary"><b>Visits: ${reportSummary.visits}</b><b>Completed: ${reportSummary.completed}</b>
      <b>Verified: ${reportSummary.verified}</b><b>Distance: ${reportSummary.distanceKm.toFixed(1)} km</b>
      <b>Stop: ${reportSummary.stopMinutes} min</b><b>GPS exceptions: ${reportSummary.gpsExceptions}</b></div>
      <table><thead><tr><th>Technician</th><th>Job</th><th>Customer</th><th>Status</th>
      <th>Start</th><th>Arrive</th><th>Leave</th><th>Return</th><th>Outbound</th>
      <th>On-site</th><th>Return trip</th><th>Total</th><th>Distance km</th>
      <th>Arrival check</th><th>Stops</th><th>GPS</th></tr></thead><tbody>${rows}</tbody></table>
      <script>window.onload=()=>setTimeout(()=>window.print(),150)</script></body></html>`);
    reportWindow.document.close();
  };

  const exportReplayKml = () => {
    if (!replayVisit) return;
    const coordinates = (pings: LocationPingDTO[]) =>
      pings.map((ping) => `${ping.longitude},${ping.latitude},0`).join(' ');
    const routePlacemark = (name: string, style: string, pings: LocationPingDTO[]) =>
      pings.length >= 2
        ? `<Placemark><name>${xmlEscape(name)}</name><styleUrl>#${style}</styleUrl>
             <LineString><tessellate>1</tessellate><altitudeMode>clampToGround</altitudeMode>
               <coordinates>${coordinates(pings)}</coordinates>
             </LineString>
           </Placemark>`
        : '';
    const arrivedAt = replayVisit.arrivedAt ? new Date(replayVisit.arrivedAt).getTime() : null;
    const leftCustomerAt = replayVisit.leftCustomerAt
      ? new Date(replayVisit.leftCustomerAt).getTime()
      : null;
    const pingTime = (ping: LocationPingDTO) => new Date(ping.recordedAt).getTime();
    const outboundPings = arrivedAt == null
      ? []
      : replayPings.filter((ping) => pingTime(ping) <= arrivedAt);
    const onSitePings = arrivedAt == null || leftCustomerAt == null
      ? []
      : replayPings.filter((ping) => {
          const time = pingTime(ping);
          return time >= arrivedAt && time <= leftCustomerAt;
        });
    const returnPings = leftCustomerAt == null
      ? []
      : replayPings.filter((ping) => pingTime(ping) >= leftCustomerAt);
    const eventStyle = (eventType: string) => {
      if (eventType === 'STARTED') return 'pointStart';
      if (eventType === 'ARRIVED') return 'pointArrival';
      if (eventType === 'CUSTOMER_DEPARTED') return 'pointDeparture';
      if (eventType === 'ENDED') return 'pointEnd';
      if (eventType === 'STOPPED' || eventType === 'LONG_STOP') return 'pointStop';
      if (eventType === 'REASON_ADDED') return 'pointReason';
      return 'pointEvent';
    };
    const eventPlacemarks = (replayVisit.events || [])
      .filter((event) => event.latitude != null && event.longitude != null)
      .map((event) => `
        <Placemark>
          <name>${xmlEscape(eventLabel[event.eventType] || event.eventType)}</name>
          <styleUrl>#${eventStyle(event.eventType)}</styleUrl>
          <description>${xmlEscape(
            [event.occurredAt, event.reasonCode, event.note].filter(Boolean).join(' · ')
          )}</description>
          <Point><coordinates>${event.longitude},${event.latitude},0</coordinates></Point>
        </Placemark>`)
      .join('');
    const customerPlacemark =
      replayVisit.customerLatitude != null && replayVisit.customerLongitude != null
        ? `<Placemark><name>${xmlEscape(replayVisit.customerName)} - Customer GPS</name>
             <styleUrl>#pointCustomer</styleUrl>
             <Point><coordinates>${replayVisit.customerLongitude},${replayVisit.customerLatitude},0</coordinates></Point>
           </Placemark>`
        : '';
    const kml = `<?xml version="1.0" encoding="UTF-8"?>
      <kml xmlns="http://www.opengis.net/kml/2.2"><Document>
        <name>${xmlEscape(`${replayVisit.jobNo} - ${replayVisit.customerName}`)}</name>
        <Style id="routeFull"><LineStyle><color>ff8b7464</color><width>3</width></LineStyle></Style>
        <Style id="routeOutbound"><LineStyle><color>ffeb6325</color><width>7</width></LineStyle></Style>
        <Style id="routeOnSite"><LineStyle><color>ff4aa316</color><width>7</width></LineStyle></Style>
        <Style id="routeReturn"><LineStyle><color>ffed3a7c</color><width>7</width></LineStyle></Style>
        <Style id="pointCustomer"><IconStyle><color>ff0000ff</color><scale>1.3</scale></IconStyle></Style>
        <Style id="pointStart"><IconStyle><color>ff00a000</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointArrival"><IconStyle><color>ffff6600</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointDeparture"><IconStyle><color>ffff00aa</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointEnd"><IconStyle><color>ff222222</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointStop"><IconStyle><color>ff00aaff</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointReason"><IconStyle><color>ffffff00</color><scale>1.1</scale></IconStyle></Style>
        <Style id="pointEvent"><IconStyle><color>ffffffff</color></IconStyle></Style>
        <Folder><name>ခရီးစဉ်လမ်းကြောင်းများ</name>
          ${routePlacemark('GPS လမ်းကြောင်းအပြည့်အစုံ', 'routeFull', replayPings)}
          ${routePlacemark('Customer ဆီသွားလမ်း', 'routeOutbound', outboundPings)}
          ${routePlacemark('Customer နေရာတွင်ရှိသော လမ်းကြောင်း', 'routeOnSite', onSitePings)}
          ${routePlacemark('Customer ဆီမှပြန်လာလမ်း', 'routeReturn', returnPings)}
        </Folder>
        <Folder><name>နေရာနှင့်ဖြစ်ရပ်များ</name>
          ${customerPlacemark}
          ${eventPlacemarks}
        </Folder>
      </Document></kml>`;
    downloadText(kml, 'application/vnd.google-earth.kml+xml', `${replayVisit.jobNo}-route.kml`);
  };

  const openGoogleMapsPoint = (latitude?: number, longitude?: number) => {
    if (latitude == null || longitude == null) return;
    window.open(
      `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`,
      '_blank',
      'noopener,noreferrer'
    );
  };

  const openGoogleMapsTrip = () => {
    if (replayPings.length < 2) {
      const event = replayVisit?.events?.find((row) => row.latitude != null && row.longitude != null);
      openGoogleMapsPoint(event?.latitude, event?.longitude);
      return;
    }
    const origin = replayPings[0];
    const destination = replayPings[replayPings.length - 1];
    const waypointCount = Math.min(8, Math.max(0, replayPings.length - 2));
    const waypoints = Array.from({ length: waypointCount }, (_, index) => {
      const pingIndex = Math.round(((index + 1) * (replayPings.length - 1)) / (waypointCount + 1));
      const ping = replayPings[pingIndex];
      return `${ping.latitude},${ping.longitude}`;
    });
    const params = new URLSearchParams({
      api: '1',
      origin: `${origin.latitude},${origin.longitude}`,
      destination: `${destination.latitude},${destination.longitude}`,
      travelmode: 'driving'
    });
    if (waypoints.length) params.set('waypoints', waypoints.join('|'));
    window.open(`https://www.google.com/maps/dir/?${params}`, '_blank', 'noopener,noreferrer');
  };

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
          {mapMessage && (
            <div className="px-4 py-2 text-xs font-semibold text-amber-700 bg-amber-50 border-t border-amber-100">
              {mapMessage}
            </div>
          )}
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
            {routeLoading && <p className="text-indigo-600">OSRM လမ်းကြောင်းတွက်နေသည်…</p>}
            {routeSummary && (
              <p className="text-indigo-700 font-semibold">
                လမ်းအကွာအဝေး {(routeSummary.distanceMeters / 1000).toFixed(1)} km ·
                ခန့်မှန်း {Math.max(1, Math.round(routeSummary.durationSeconds / 60))} မိနစ်
              </p>
            )}
            {routeMessage && <p className="text-amber-600">{routeMessage}</p>}
            {selected.needsReason && <p className="text-amber-600 font-semibold">Long stop — technician အကြောင်းရင်း မပေးသေး</p>}
          </div>
        </div>
      )}

      {replayError && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {replayError}
        </div>
      )}

      {replayVisit && (
        <section className="rounded-xl border border-indigo-200 bg-white overflow-hidden">
          <div className="px-4 py-3 border-b border-indigo-100 flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="font-black text-slate-800">
                ခရီးစဉ် ပြန်လည်စစ်ဆေးခြင်း · {replayVisit.staffName}
              </h2>
              <p className="text-xs text-slate-500">
                {replayVisit.jobNo} · {replayVisit.customerName} · GPS {replayPings.length} points
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={openGoogleMapsTrip}
                className="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-bold"
              >
                Google Maps တွင်ဖွင့်မည်
              </button>
              <button
                type="button"
                onClick={exportReplayKml}
                className="px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-xs font-bold"
              >
                KML ထုတ်မည်
              </button>
              <button
                type="button"
                onClick={() => setReplayVisit(null)}
                className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-bold text-slate-600"
              >
                ပိတ်မည်
              </button>
            </div>
          </div>
          {replayLoading ? (
            <p className="p-6 text-sm text-indigo-600 font-semibold">GPS ခရီးစဉ်ယူနေသည်…</p>
          ) : (
            <>
              <div ref={replayMapRef} className="w-full h-[480px]" />
              <div className="p-4 grid grid-cols-1 md:grid-cols-4 gap-3">
                <div className={`rounded-lg border px-3 py-2 ${
                  arrivalDistance != null && arrivalDistance <= 100
                    ? 'border-emerald-200 bg-emerald-50'
                    : 'border-amber-200 bg-amber-50'
                }`}>
                  <p className="text-[11px] text-slate-500">Customer ရောက်ရှိမှု စစ်ဆေးချက်</p>
                  <p className="font-black text-slate-800 mt-1">
                    {arrivalDistance == null
                      ? 'Arrival GPS မရှိသဖြင့် မစစ်နိုင်ပါ'
                      : arrivalDistance <= 100
                        ? `သေချာရောက်ခဲ့သည် · ${Math.round(arrivalDistance)} m`
                        : `Customer GPS မှ ${Math.round(arrivalDistance)} m ဝေးသည်`}
                  </p>
                </div>
                <TimeBox label="စထွက်ချိန်" value={timeLabel(replayVisit.startedAt)} />
                <TimeBox label="Customer ဆီရောက်ချိန်" value={timeLabel(replayVisit.arrivedAt)} />
                <TimeBox label="Customer ဆီမှပြန်ထွက်ချိန်" value={timeLabel(replayVisit.leftCustomerAt)} />
                <TimeBox label="ပြန်ရောက်ချိန်" value={timeLabel(replayVisit.endedAt)} />
                <TimeBox
                  label="သွားချိန်စုစုပေါင်း"
                  value={replayVisit.arrivedAt ? durationLabel(replayVisit.startedAt, replayVisit.arrivedAt) : '—'}
                />
                <TimeBox
                  label="ပြန်ချိန်စုစုပေါင်း"
                  value={replayVisit.endedAt ? durationLabel(replayVisit.leftCustomerAt, replayVisit.endedAt) : '—'}
                />
                <TimeBox
                  label="စုစုပေါင်းကြာချိန်"
                  value={durationLabel(replayVisit.startedAt, replayVisit.endedAt)}
                />
              </div>
              <div className="px-4 pb-4">
                <p className="text-xs font-bold text-slate-600 mb-2">
                  ရပ်နားမှုမှတ်တမ်း ({replayStops.length})
                </p>
                {replayStops.length === 0 ? (
                  <p className="text-xs text-slate-400">မှတ်တမ်းတင်ထားသော ရပ်နားမှု မရှိပါ။</p>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    {replayStops.map((stop, index) => (
                      <div key={`${stop.event.id}-${index}`} className="rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-xs">
                        <p className="font-black text-amber-800">
                          {stop.longStop ? 'ကြာမြင့်စွာရပ်' : 'ခဏရပ်'} · {stop.durationMinutes} မိနစ်
                        </p>
                        <p className="text-slate-500">
                          {timeLabel(stop.event.occurredAt)} – {timeLabel(stop.endedAt)}
                        </p>
                        {(stop.reasonCode || stop.note) && (
                          <p className="text-slate-600">
                            {[stop.reasonCode, stop.note].filter(Boolean).join(' · ')}
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div className="px-4 pb-4 flex flex-wrap gap-3 text-[11px] font-semibold text-slate-500">
                <span>🔴 Customer GPS</span>
                <span>🔵 ရောက်ပြီနှိပ်သည့်နေရာ</span>
                <span>🟣 Customer ဆီမှပြန်ထွက်သည့်နေရာ</span>
                <span>🟠 ရပ်နားရာနေရာ</span>
                <span>🔷 အကြောင်းပြချက်ပေးခဲ့သောနေရာ</span>
                <span>🟣 အမှန်တကယ်သွားခဲ့သော GPS လမ်းကြောင်း</span>
              </div>
              <div className="mx-4 mb-4 rounded-lg border border-slate-100 bg-slate-50 px-3 py-2 text-[11px] font-semibold text-slate-600">
                KML လမ်းကြောင်းအရောင် — 🔵 Customer ဆီသွားလမ်း · 🟢 Customer နေရာတွင်ရှိချိန် · 🟣 ပြန်လာလမ်း · ⚪ GPS လမ်းကြောင်းအပြည့်
              </div>
            </>
          )}
        </section>
      )}

      <section className="rounded-xl border border-slate-200 bg-white overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center gap-2">
          <Clock3 size={17} className="text-indigo-600" />
          <div>
            <h2 className="font-black text-slate-800">Visit အချိန်မှတ်တမ်း</h2>
            <p className="text-xs text-slate-500">ရက်စွဲအလိုက် ခရီးစဉ်နှင့် GPS map ကို ပြန်စစ်နိုင်သည်</p>
          </div>
        </div>
        <div className="px-4 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-end gap-3">
          <label className="text-xs font-bold text-slate-600">
            <span className="block mb-1">စတင်ရက်</span>
            <input
              type="date"
              value={historyFrom}
              onChange={(event) => setHistoryFrom(event.target.value)}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-normal"
            />
          </label>
          <label className="text-xs font-bold text-slate-600">
            <span className="block mb-1">ပြီးဆုံးရက်</span>
            <input
              type="date"
              value={historyTo}
              onChange={(event) => setHistoryTo(event.target.value)}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-normal"
            />
          </label>
          <label className="text-xs font-bold text-slate-600 flex-1 min-w-[220px]">
            <span className="block mb-1">Job No / Customer Name</span>
            <input
              type="search"
              value={historySearch}
              onChange={(event) => setHistorySearch(event.target.value)}
              placeholder="ဥပမာ SJ-000123 သို့ Customer အမည်"
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-normal"
            />
          </label>
          <button
            type="button"
            onClick={() => {
              if (!historyFrom || !historyTo || historyFrom > historyTo) {
                setError('History ရက်စွဲအပိုင်းအခြား မမှန်ပါ။');
                return;
              }
              setAppliedHistoryRange({ from: historyFrom, to: historyTo });
            }}
            className="px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-bold hover:bg-indigo-700"
          >
            Data ရှာမည်
          </button>
          <button
            type="button"
            onClick={exportReportCsv}
            disabled={filteredReportRows.length === 0}
            className="px-4 py-2 rounded-lg border border-emerald-300 bg-white text-emerald-700 text-sm font-bold disabled:opacity-40"
          >
            Excel CSV
          </button>
          <button
            type="button"
            onClick={printReport}
            disabled={filteredReportRows.length === 0}
            className="px-4 py-2 rounded-lg border border-slate-300 bg-white text-slate-700 text-sm font-bold disabled:opacity-40"
          >
            Print / PDF
          </button>
        </div>

        <div className="p-4 border-b border-slate-100">
          <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2">
            <ReportMetric label="Visit စုစုပေါင်း" value={String(reportSummary.visits)} />
            <ReportMetric label="ပြီးဆုံး" value={String(reportSummary.completed)} />
            <ReportMetric label="ရောက်ရှိမှုအတည်ပြု" value={String(reportSummary.verified)} />
            <ReportMetric label="GPS ခရီးအကွာအဝေး" value={`${reportSummary.distanceKm.toFixed(1)} km`} />
            <ReportMetric label="ရပ်နားချိန်" value={minutesLabel(reportSummary.stopMinutes)} />
            <ReportMetric label="GPS ပြဿနာ" value={String(reportSummary.gpsExceptions)} />
          </div>
          {filteredReportRows.length > 0 && (
            <details className="mt-3">
              <summary className="cursor-pointer text-sm font-bold text-indigo-700">
                အသေးစိတ် Report ဇယားကြည့်မည်
              </summary>
              <div className="mt-3 overflow-x-auto">
                <table className="min-w-[1200px] w-full text-xs">
                  <thead className="bg-slate-50 text-slate-500">
                    <tr>
                      <th className="p-2 text-left">Technician</th>
                      <th className="p-2 text-left">Job / Customer</th>
                      <th className="p-2 text-left">သွားချိန်</th>
                      <th className="p-2 text-left">Customer ဆီကြာချိန်</th>
                      <th className="p-2 text-left">ပြန်ချိန်</th>
                      <th className="p-2 text-left">စုစုပေါင်း</th>
                      <th className="p-2 text-left">Distance</th>
                      <th className="p-2 text-left">Arrival</th>
                      <th className="p-2 text-left">Stops</th>
                      <th className="p-2 text-left">GPS Quality</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {filteredReportRows.map((row) => (
                      <tr key={row.visitId}>
                        <td className="p-2 font-bold">{row.staffName}</td>
                        <td className="p-2">{row.jobNo}<br/><span className="text-slate-500">{row.customerName}</span></td>
                        <td className="p-2">{minutesLabel(row.outboundMinutes)}</td>
                        <td className="p-2">{minutesLabel(row.onSiteMinutes)}</td>
                        <td className="p-2">{minutesLabel(row.returnMinutes)}</td>
                        <td className="p-2">{minutesLabel(row.totalMinutes)}</td>
                        <td className="p-2">{((row.actualDistanceMeters || 0) / 1000).toFixed(1)} km</td>
                        <td className="p-2">
                          {row.arrivalVerified == null
                            ? 'မစစ်နိုင်'
                            : row.arrivalVerified
                              ? `ရောက် · ${Math.round(row.arrivalDistanceMeters || 0)} m`
                              : `ဝေး · ${Math.round(row.arrivalDistanceMeters || 0)} m`}
                        </td>
                        <td className="p-2">{row.stopCount} ကြိမ် · {minutesLabel(row.stopMinutes)}</td>
                        <td className={`p-2 font-bold ${row.gpsException ? 'text-amber-700' : 'text-emerald-700'}`}>
                          {row.gpsException || 'OK'}<br/>
                          <span className="font-normal text-slate-500">{row.gpsPointCount} points</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          )}
        </div>

        {filteredHistoryVisits.length === 0 ? (
          <p className="p-4 text-sm text-slate-500">ရွေးထားသည့်ရက်အတွင်း Visit မှတ်တမ်း မရှိပါ။</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {filteredHistoryVisits.map((visit) => (
              <article key={visit.id} className="p-4 space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="font-black text-slate-800">
                      {visit.staffName} · {visit.jobNo}
                    </p>
                    <p className="text-sm text-slate-500">{visit.customerName}</p>
                  </div>
                  <span className="text-xs font-bold text-indigo-600">{visit.status}</span>
                </div>

                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => void openReplay(visit)}
                    disabled={replayLoading && replayVisit?.id === visit.id}
                    className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700 disabled:opacity-50"
                  >
                    <MapPin size={14} />
                    {replayLoading && replayVisit?.id === visit.id ? 'GPS ယူနေသည်…' : 'Map ဖြင့်ပြန်စစ်မည်'}
                  </button>
                  {visit.customerLatitude != null && visit.customerLongitude != null && (
                    <button
                      type="button"
                      onClick={() => openGoogleMapsPoint(visit.customerLatitude, visit.customerLongitude)}
                      className="px-3 py-2 rounded-lg border border-emerald-300 text-emerald-700 text-xs font-bold"
                    >
                      Customer ကို Google Maps တွင်ဖွင့်မည်
                    </button>
                  )}
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-7 gap-2">
                  <TimeBox label="စထွက်ချိန်" value={timeLabel(visit.startedAt)} />
                  <TimeBox label="Customer ဆီရောက်" value={timeLabel(visit.arrivedAt)} />
                  <TimeBox label="Customer ဆီမှပြန်ထွက်" value={timeLabel(visit.leftCustomerAt)} />
                  <TimeBox label="ပြန်ရောက်ချိန်" value={timeLabel(visit.endedAt)} />
                  <TimeBox
                    label="သွားချိန်စုစုပေါင်း"
                    value={visit.arrivedAt ? durationLabel(visit.startedAt, visit.arrivedAt) : '—'}
                  />
                  <TimeBox
                    label="ပြန်ချိန်စုစုပေါင်း"
                    value={visit.endedAt ? durationLabel(visit.leftCustomerAt, visit.endedAt) : '—'}
                  />
                  <TimeBox
                    label="စုစုပေါင်းကြာချိန်"
                    value={durationLabel(visit.startedAt, visit.endedAt)}
                  />
                </div>

                {(visit.events || []).length > 0 && (
                  <div className="border-l-2 border-indigo-100 pl-3 space-y-2">
                    {(visit.events || []).map((event) => (
                      <div key={event.id} className="flex items-start gap-3 text-xs">
                        <span className="w-12 shrink-0 font-mono font-bold text-slate-500">
                          {timeLabel(event.occurredAt)}
                        </span>
                        <div className="flex-1">
                          <span className="font-bold text-slate-700">
                            {eventLabel[event.eventType] || event.eventType}
                          </span>
                          {event.reasonCode && (
                            <span className="ml-2 text-amber-700">{event.reasonCode}</span>
                          )}
                          {event.note && <p className="text-slate-500 mt-0.5">{event.note}</p>}
                          {event.latitude != null && event.longitude != null && (
                            <button
                              type="button"
                              onClick={() => openGoogleMapsPoint(event.latitude, event.longitude)}
                              className="mt-1 text-[11px] font-bold text-emerald-700 hover:underline"
                            >
                              Google Maps တွင်ကြည့်မည်
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

const TimeBox: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="rounded-lg bg-slate-50 border border-slate-100 px-3 py-2">
    <p className="text-[11px] text-slate-500">{label}</p>
    <p className="text-sm font-black text-slate-800 mt-0.5">{value}</p>
  </div>
);

const ReportMetric: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="rounded-lg border border-indigo-100 bg-indigo-50 px-3 py-2">
    <p className="text-[11px] text-slate-500">{label}</p>
    <p className="mt-1 text-base font-black text-indigo-800">{value}</p>
  </div>
);

export default OutdoorTracking;
