
import { api } from './api';
import { UnitDTO, ApiResponse } from '../types';

export const unitService = {
  getAll: async (): Promise<UnitDTO[]> => {
    const res = await api.get<any, ApiResponse<UnitDTO[]>>('/v1/units');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<UnitDTO>>(`/v1/units/${id}`).then((res: any) => res.data),
  
  create: (unit: Omit<UnitDTO, 'id'>) => 
    api.post<any, ApiResponse<UnitDTO>>('/v1/units', unit).then((res: any) => res.data),
  
  update: (id: number, unit: Partial<UnitDTO>) => 
    api.put<any, ApiResponse<UnitDTO>>(`/v1/units/${id}`, unit).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/units/${id}`).then((res: any) => res.data)
};
