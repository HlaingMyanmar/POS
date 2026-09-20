import { api } from './api';
import { ApiResponse, CashDrawerMovementDTO, CashDrawerSessionDTO } from '../types';

export const cashDrawerService = {
  getAll: async (): Promise<CashDrawerSessionDTO[]> => {
    const res = await api.get<any, ApiResponse<CashDrawerSessionDTO[]>>('/v1/cash-drawers');
    return res.data || [];
  },

  movements: async (id: number): Promise<CashDrawerMovementDTO[]> => {
    const res = await api.get<any, ApiResponse<CashDrawerMovementDTO[]>>(`/v1/cash-drawers/${id}/movements`);
    return res.data || [];
  },

  open: (amount: number, note?: string) =>
    api.post<any, ApiResponse<CashDrawerSessionDTO>>('/v1/cash-drawers/open', { amount, note }).then((res: any) => res.data),

  cashIn: (id: number, amount: number, reason: string) =>
    api.post<any, ApiResponse<CashDrawerSessionDTO>>(`/v1/cash-drawers/${id}/cash-in`, { amount, reason }).then((res: any) => res.data),

  cashOut: (id: number, amount: number, reason: string) =>
    api.post<any, ApiResponse<CashDrawerSessionDTO>>(`/v1/cash-drawers/${id}/cash-out`, { amount, reason }).then((res: any) => res.data),

  close: (id: number, amount: number, note?: string) =>
    api.post<any, ApiResponse<CashDrawerSessionDTO>>(`/v1/cash-drawers/${id}/close`, { amount, note }).then((res: any) => res.data),
};
