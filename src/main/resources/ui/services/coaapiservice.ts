
import { api } from './api';
import { ChartOfAccountDTO, ApiResponse } from '../types';

export const coaService = {
  getAll: async (): Promise<ChartOfAccountDTO[]> => {
    const res = await api.get<any, ApiResponse<ChartOfAccountDTO[]>>('/v1/chart-of-accounts');
    return res.data;
  },

  getTree: async (): Promise<ChartOfAccountDTO[]> => {
    const res = await api.get<any, ApiResponse<ChartOfAccountDTO[]>>('/v1/chart-of-accounts/tree');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<ChartOfAccountDTO>>(`/v1/chart-of-accounts/${id}`).then((res: any) => res.data),
  
  create: (coa: Partial<ChartOfAccountDTO>) => 
    api.post<any, ApiResponse<ChartOfAccountDTO>>('/v1/chart-of-accounts', coa).then((res: any) => res.data),
  
  update: (id: number, coa: Partial<ChartOfAccountDTO>) => 
    api.put<any, ApiResponse<ChartOfAccountDTO>>(`/v1/chart-of-accounts/${id}`, coa).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/chart-of-accounts/${id}`).then((res: any) => res.data)
};
