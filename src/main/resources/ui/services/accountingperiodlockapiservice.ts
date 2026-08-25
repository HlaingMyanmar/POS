import { api } from './api';
import { ApiResponse } from '../types';

export interface AccountingPeriodLock {
  id?: number;
  dateFrom: string;
  dateTo: string;
  active?: boolean;
  reason: string;
  lockedBy?: string;
  lockedAt?: string;
  unlockedBy?: string;
  unlockedAt?: string;
}

export const accountingPeriodLockApiService = {
  list: async (): Promise<AccountingPeriodLock[]> => {
    const res = await api.get<any, ApiResponse<AccountingPeriodLock[]>>('/v1/accounting-period-locks');
    return res.data || [];
  },
  lock: async (data: Pick<AccountingPeriodLock, 'dateFrom' | 'dateTo' | 'reason'>): Promise<AccountingPeriodLock> => {
    const res = await api.post<any, ApiResponse<AccountingPeriodLock>>('/v1/accounting-period-locks', data);
    return res.data;
  },
  unlock: async (id: number): Promise<AccountingPeriodLock> => {
    const res = await api.post<any, ApiResponse<AccountingPeriodLock>>(`/v1/accounting-period-locks/${id}/unlock`, {});
    return res.data;
  }
};
