
import { api } from './api';
import { SupplierDTO, ApiResponse } from '../types';

export const supplierService = {
  getAll: async (): Promise<SupplierDTO[]> => {
    // Note: Use /all for full list if your paginated one is default
    const res = await api.get<any, ApiResponse<SupplierDTO[]>>('/v1/suppliers/all');
    return res.data;
  },

  getPaginated: async (page: number = 0, size: number = 10): Promise<any> => {
    const res = await api.get<any, ApiResponse<any>>(`/v1/suppliers?page=${page}&size=${size}`);
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<SupplierDTO>>(`/v1/suppliers/${id}`).then((res: any) => res.data),
  
  search: (keyword: string) => api.get<any, ApiResponse<SupplierDTO[]>>(`/v1/suppliers/search?keyword=${keyword}`).then((res: any) => res.data),

  create: (supplier: Partial<SupplierDTO>) => 
    api.post<any, ApiResponse<SupplierDTO>>('/v1/suppliers', supplier).then((res: any) => res.data),
  
  update: (id: number, supplier: Partial<SupplierDTO>) => 
    api.put<any, ApiResponse<SupplierDTO>>(`/v1/suppliers/${id}`, supplier).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/suppliers/${id}`).then((res: any) => res.data)
};
