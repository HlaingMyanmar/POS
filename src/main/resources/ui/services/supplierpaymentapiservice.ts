import { api } from './api';
import { ApiResponse } from '../types';

export interface SupplierPayable {
  purchaseId: number; purchaseCode: string; purchaseDate?: string; dueDate?: string;
  netAmount: number; paidAmount: number; dueAmount: number;
}
export interface SupplierPayment {
  id: number; paymentNo: string; supplierId: number; supplierName: string;
  paymentMethodId: number; paymentMethodName: string; totalAmount: number;
  allocatedAmount: number; advanceAmount: number; paymentDate: string;
  transactionNo?: string; paidBy?: string; remark?: string;
  allocations: Array<{ purchaseId: number; purchaseCode: string; amount: number; remainingDue: number }>;
}
export interface SupplierPaymentRequest {
  supplierId: number; staffId: number; paymentMethodId: number; amount: number;
  transactionNo?: string; remark?: string;
  allocations?: Array<{ purchaseId: number; amount: number }>;
}
export const supplierPaymentApiService = {
  payables: async (supplierId: number): Promise<SupplierPayable[]> => {
    const res = await api.get<any, ApiResponse<SupplierPayable[]>>(`/v1/supplier-payments/supplier/${supplierId}/payables`);
    return res.data ?? [];
  },
  history: async (supplierId: number): Promise<SupplierPayment[]> => {
    const res = await api.get<any, ApiResponse<SupplierPayment[]>>(`/v1/supplier-payments/supplier/${supplierId}`);
    return res.data ?? [];
  },
  create: async (payload: SupplierPaymentRequest): Promise<SupplierPayment> => {
    const res = await api.post<any, ApiResponse<SupplierPayment>>('/v1/supplier-payments', payload);
    return res.data;
  },
  creditSummary: async (supplierId: number): Promise<{ advanceBalance: number; returnCreditBalance: number; availableCredit: number }> => {
    const res = await api.get<any, ApiResponse<any>>(`/v1/supplier-payments/supplier/${supplierId}/credit-summary`);
    return {
      advanceBalance: Number(res.data?.advanceBalance) || 0,
      returnCreditBalance: Number(res.data?.returnCreditBalance) || 0,
      availableCredit: Number(res.data?.availableCredit) || 0
    };
  },
  applyCredit: async (payload: { supplierId: number; purchaseId: number; staffId: number; amount: number; reason?: string }) => {
    const res = await api.post<any, ApiResponse<any>>('/v1/supplier-payments/apply-credit', payload);
    return res.data;
  }
};
