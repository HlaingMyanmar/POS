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

export const technicianVisitService = {
  live: async (): Promise<TechnicianVisitDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO[]>>('/v1/technician-visits/live');
    return res.data || [];
  },
  history: async (): Promise<TechnicianVisitDTO[]> => {
    const res = await api.get<any, ApiResponse<TechnicianVisitDTO[]>>('/v1/technician-visits/history');
    return res.data || [];
  }
};
