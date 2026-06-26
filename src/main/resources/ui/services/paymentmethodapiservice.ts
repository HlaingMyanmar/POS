
import { api } from './api';
import { PaymentMethodDTO, ApiResponse } from '../types';

export const paymentMethodService = {
  getAll: async (): Promise<PaymentMethodDTO[]> => {
    const res = await api.get<any, ApiResponse<PaymentMethodDTO[]>>('/v1/payment-methods');
    return res.data;
  },

  getAllActive: async (): Promise<PaymentMethodDTO[]> => {
    const res = await api.get<any, ApiResponse<PaymentMethodDTO[]>>('/v1/payment-methods/active');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<PaymentMethodDTO>>(`/v1/payment-methods/${id}`).then((res: any) => res.data),
  
  create: (dto: Partial<PaymentMethodDTO>) => 
    api.post<any, ApiResponse<PaymentMethodDTO>>('/v1/payment-methods', dto).then((res: any) => res.data),
  
  update: (id: number, dto: Partial<PaymentMethodDTO>) => 
    api.put<any, ApiResponse<PaymentMethodDTO>>(`/v1/payment-methods/${id}`, dto).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/payment-methods/${id}`).then((res: any) => res.data)
};
