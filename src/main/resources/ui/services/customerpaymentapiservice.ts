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
  },

  allocate: async (payload: {
    customerId: number;
    paymentMethodId: number;
    staffId?: number;
    amount: number;
    transactionNo?: string;
    remark?: string;
    allocations?: { saleId: number; amount: number }[];
  }): Promise<CustomerPaymentDTO> => {
    const res = await api.post<any, ApiResponse<CustomerPaymentDTO>>('/v1/customer-payments/allocate', payload);
    return res.data;
  },

  applyCredit: async (payload: {
    customerId: number;
    saleId: number;
    staffId?: number;
    amount: number;
    reason?: string;
  }): Promise<{ applicationNo?: string; amount?: number; remainingDue?: number; advanceBalance?: number }> => {
    const res = await api.post<any, ApiResponse<any>>('/v1/customer-payments/apply-credit', payload);
    return res.data ?? {};
  },

  voidPayment: async (id: number, reason: string, staffId?: number): Promise<CustomerPaymentDTO> => {
    const res = await api.post<any, ApiResponse<CustomerPaymentDTO>>(`/v1/customer-payments/${id}/void`, { reason, staffId });
    return res.data;
  },

  receivables: async (customerId: number): Promise<{ saleId: number; saleCode?: string; dueDate?: string; netAmount?: number; paidAmount?: number; dueAmount?: number }[]> => {
    const res = await api.get<any, ApiResponse<any[]>>(`/v1/customer-payments/customer/${customerId}/receivables`);
    return res.data ?? [];
  },

  creditSummary: async (customerId: number): Promise<{ advanceBalance?: number; availableCredit?: number }> => {
    const res = await api.get<any, ApiResponse<any>>(`/v1/customer-payments/customer/${customerId}/credit-summary`);
    return res.data ?? {};
  }
};
