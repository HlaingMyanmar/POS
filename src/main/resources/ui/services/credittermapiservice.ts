import { api } from './api';
import { ApiResponse, CustomerCreditTermDTO, CustomerCreditTermHistoryDTO } from '../types';

export const creditTermService = {
  getAll: async (): Promise<CustomerCreditTermDTO[]> => {
    const res = await api.get<any, ApiResponse<CustomerCreditTermDTO[]>>('/v1/credit-terms');
    return res.data;
  },

  getByCustomer: async (customerId: number): Promise<CustomerCreditTermDTO> => {
    const res = await api.get<any, ApiResponse<CustomerCreditTermDTO>>(`/v1/credit-terms/customer/${customerId}`);
    return res.data;
  },

  create: async (data: CustomerCreditTermDTO): Promise<CustomerCreditTermDTO> => {
    const res = await api.post<any, ApiResponse<CustomerCreditTermDTO>>('/v1/credit-terms', data);
    return res.data;
  },

  update: async (data: CustomerCreditTermDTO): Promise<CustomerCreditTermDTO> => {
    const res = await api.put<any, ApiResponse<CustomerCreditTermDTO>>('/v1/credit-terms', data);
    return res.data;
  },

  getHistoryByCustomer: async (customerId: number): Promise<CustomerCreditTermHistoryDTO[]> => {
    const endpoints = [
      `/v1/credit-terms/customer/${customerId}/history`,
      `/v1/credit-terms/history/customer/${customerId}`,
      `/v1/customer-credit-terms/customer/${customerId}/history`
    ];

    for (const endpoint of endpoints) {
      try {
        const res = await api.get<any, ApiResponse<CustomerCreditTermHistoryDTO[]>>(endpoint);
        return Array.isArray(res.data) ? res.data : [];
      } catch {
        // Try next possible endpoint. Backend route may vary by module version.
      }
    }

    return [];
  }
};
