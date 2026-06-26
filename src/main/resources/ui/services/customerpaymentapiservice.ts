import { api } from './api';
import { ApiResponse, CustomerPaymentDTO, SaleDTO } from '../types';

export const customerPaymentService = {
  create: async (data: CustomerPaymentDTO): Promise<CustomerPaymentDTO | SaleDTO> => {
    const res = await api.post<any, ApiResponse<CustomerPaymentDTO | SaleDTO>>('/v1/customer-payments', data);
    return res.data;
  },

  getByCustomer: async (customerId: number): Promise<CustomerPaymentDTO[]> => {
    const res = await api.get<any, ApiResponse<CustomerPaymentDTO[]>>(`/v1/customer-payments/customer/${customerId}`);
    return res.data;
  },

  getBySale: async (saleId: number): Promise<CustomerPaymentDTO[]> => {
    const res = await api.get<any, ApiResponse<CustomerPaymentDTO[]>>(`/v1/customer-payments/sale/${saleId}`);
    return res.data;
  }
};
