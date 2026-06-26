import { api } from './api';
import { ApiResponse, CreditAlertDTO } from '../types';

export const creditAlertService = {
  getAllUnresolved: async (): Promise<CreditAlertDTO[]> => {
    const res = await api.get<any, ApiResponse<CreditAlertDTO[]>>('/v1/credit-alerts');
    return res.data;
  },

  getByCustomer: async (customerId: number): Promise<CreditAlertDTO[]> => {
    const res = await api.get<any, ApiResponse<CreditAlertDTO[]>>(`/v1/credit-alerts/customer/${customerId}`);
    return res.data;
  },

  resolve: async (alertId: number): Promise<CreditAlertDTO> => {
    const res = await api.post<any, ApiResponse<CreditAlertDTO>>(`/v1/credit-alerts/${alertId}/resolve`);
    return res.data;
  }
};
