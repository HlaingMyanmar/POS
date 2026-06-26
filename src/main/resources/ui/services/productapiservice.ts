
import { api } from './api';
import { ProductDTO, ApiResponse } from '../types';

export const productService = {
  getAll: async (): Promise<ProductDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductDTO[]>>('/v1/products');
    return res.data;
  },

  getLowStock: async (): Promise<ProductDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductDTO[]>>('/v1/products/low-stock');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<ProductDTO>>(`/v1/products/${id}`).then((res: any) => res.data),
  
  create: (product: Partial<ProductDTO>) => 
    api.post<any, ApiResponse<ProductDTO>>('/v1/products', product).then((res: any) => res.data),
  
  update: (id: number, product: Partial<ProductDTO>) => 
    api.put<any, ApiResponse<ProductDTO>>(`/v1/products/${id}`, product).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/products/${id}`).then((res: any) => res.data),

  assignSerials: (id: number, data: { serialNumbers: string[]; warrantyMonths?: number }) =>
    api.post<any, ApiResponse<ProductDTO>>(`/v1/products/${id}/assign-serials`, data).then((res: any) => res.data),

  getNextSerialSeq: (id: number): Promise<number> =>
    api.get<any, ApiResponse<number>>(`/v1/products/${id}/next-serial-seq`).then((res: any) => res.data),

  updatePhoto: (id: number, photoBase64: string | null) =>
    api.put<any, ApiResponse<void>>(`/v1/products/${id}/photo`, { photoBase64 }).then((res: any) => res),
};
