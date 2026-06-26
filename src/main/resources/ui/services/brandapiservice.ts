
import { api } from './api';
import { BrandDTO, ApiResponse } from '../types';

export const brandService = {
  getAll: async (): Promise<BrandDTO[]> => {
    const res = await api.get<any, ApiResponse<BrandDTO[]>>('/v1/brands');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<BrandDTO>>(`/v1/brands/${id}`).then((res: any) => res.data),
  
  create: (brand: Omit<BrandDTO, 'id'>) => 
    api.post<any, ApiResponse<BrandDTO>>('/v1/brands', brand).then((res: any) => res.data),
  
  update: (id: number, brand: Partial<BrandDTO>) => 
    api.put<any, ApiResponse<BrandDTO>>(`/v1/brands/${id}`, brand).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/brands/${id}`).then((res: any) => res.data)
};
