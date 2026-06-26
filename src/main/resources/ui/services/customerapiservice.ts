
import { api } from './api';
import { CustomerDTO, ApiResponse } from '../types';

export const customerService = {
  getAll: async (): Promise<CustomerDTO[]> => {
    const res = await api.get<any, ApiResponse<CustomerDTO[]>>('/v1/customers');
    return res.data;
  },

  getById: (id: number) => 
    api.get<any, ApiResponse<CustomerDTO>>(`/v1/customers/${id}`).then((res: any) => res.data),
  
  create: (customer: Partial<CustomerDTO>) => 
    api.post<any, ApiResponse<CustomerDTO>>('/v1/customers', customer).then((res: any) => res.data),
  
  update: (id: number, customer: Partial<CustomerDTO>) => 
    api.put<any, ApiResponse<CustomerDTO>>(`/v1/customers/${id}`, customer).then((res: any) => res.data),
  
  delete: (id: number) => 
    api.delete<any, ApiResponse<void>>(`/v1/customers/${id}`).then((res: any) => res.data)
};
