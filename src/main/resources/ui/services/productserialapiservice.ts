
import { api } from './api';
import { ProductSerialDTO, ApiResponse } from '../types';

export const productSerialService = {
  getAll: async (): Promise<ProductSerialDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductSerialDTO[]>>('/v1/product-serials');
    return res.data;
  },

  getByProductId: async (productId: number): Promise<ProductSerialDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductSerialDTO[]>>(`/v1/product-serials/by-product/${productId}`);
    return res.data;
  },

  getById: (id: number) =>
    api.get<any, ApiResponse<ProductSerialDTO>>(`/v1/product-serials/${id}`).then((res: any) => res.data),
  
  create: (serial: Omit<ProductSerialDTO, 'id'>) => 
    api.post<any, ApiResponse<ProductSerialDTO>>('/v1/product-serials', serial).then((res: any) => res.data),
  
  update: (id: number, serial: Partial<ProductSerialDTO>) => 
    api.put<any, ApiResponse<ProductSerialDTO>>(`/v1/product-serials/${id}`, serial).then((res: any) => res.data),
  
  delete: (id: number) => 
    api.delete<any, ApiResponse<void>>(`/v1/product-serials/${id}`).then((res: any) => res.data),

  deleteBySerialNumber: (serialNumber: string) => 
    api.delete<any, ApiResponse<void>>(`/v1/product-serials/by-serial/${serialNumber}`).then((res: any) => res.data)
};
