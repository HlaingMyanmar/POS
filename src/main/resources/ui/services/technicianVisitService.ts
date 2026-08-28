import { api } from './api';
import { ApiResponse } from '../types';

export interface TechnicianVisitDTO {
  id: number;
  staffId: number;
  staffName: string;
  jobId: number;
  jobNo: string;
  customerId: number;
  customerName: string;
  status: string;
  motionStatus?: string;
  needsReason?: boolean;
  startedAt?: string;
  arrivedAt?: string;
  leftCustomerAt?: string;
  endedAt?: string;
  latitude?: number;
  longitude?: number;
  accuracy?: number;
  recordedAt?: string;
  customerLatitude?: number;
  customerLongitude?: number;
  distanceMeters?: number;
  events?: VisitEventDTO[];
}

export interface VisitEventDTO {
  id: number;
  eventType: string;
  latitude?: number;
  longitude?: number;
  reasonCode?: string;
  note?: string;
  occurredAt?: string;
}

export interface LocationPingDTO {
  id: number;
  latitude: number;
  longitude: number;
  accuracy?: number;
  recordedAt: string;
}

export interface TechnicianVisitReportDTO {
  visitId: number;
  staffId: number;
  staffName: string;
  jobId: number;
  jobNo: string;
  customerId: number;
  customerName: string;
  status: string;
  startedAt?: string;
  arrivedAt?: string;
  leftCustomerAt?: string;
  endedAt?: string;
  outboundMinutes?: number;
  onSiteMinutes?: number;
  returnMinutes?: number;
  totalMinutes?: number;
  actualDistanceMeters: number;
  arrivalDistanceMeters?: number;
  arrivalVerified?: boolean;
  stopCount: number;
  stopMinutes: number;
  stopReasons: string[];
  gpsPointCount: number;
  maxGpsGapMinutes: number;
  gpsException?: string;
}

export const technicianVisitService = {
  live: async (): Promise<TechnicianVisitDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO[]>>('/v1/technician-visits/live');
    return res.data || [];
  },
  today: async (): Promise<TechnicianVisitDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO[]>>('/v1/technician-visits/today');
    return res.data || [];
  },
  history: async (from?: string, to?: string): Promise<TechnicianVisitDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO[]>>(
      '/v1/technician-visits/history',
      { params: { from, to } }
    );
    return res.data || [];
  },
  detail: async (id: number): Promise<TechnicianVisitDTO> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO>>(`/v1/technician-visits/${id}`);
    return res.data;
  },
  historyPings: async (id: number): Promise<LocationPingDTO[]> => {
    const res = await api.get<any, ApiResponse<LocationPingDTO[]>>(
      `/v1/technician-visits/${id}/history-pings`
    );
    return res.data || [];
  },
  report: async (
    from?: string,
    to?: string,
    job?: string,
    customer?: string
  ): Promise<TechnicianVisitReportDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitReportDTO[]>>(
      '/v1/technician-visits/report',
      { params: { from, to, job: job || undefined, customer: customer || undefined } }
    );
    return res.data || [];
  }
};
