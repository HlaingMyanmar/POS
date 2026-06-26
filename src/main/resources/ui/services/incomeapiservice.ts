import { api } from './api';
import { ApiResponse, IncomeDTO } from '../types';

type AnyRecord = Record<string, any>;

const normalizeIncome = (data: AnyRecord): IncomeDTO => ({
  ...data,
  id: Number(data?.id) || undefined,
  incomeCode: data?.incomeCode || undefined,
  incomeDate: data?.incomeDate || undefined,
  accountId: Number(data?.accountId) || 0,
  accountName: data?.accountName || undefined,
  paymentMethodId: Number(data?.paymentMethodId) || 0,
  paymentMethodName: data?.paymentMethodName || undefined,
  amount: Number(data?.amount) || 0,
  description: data?.description || undefined,
  staffId: Number(data?.staffId) || 0,
  staffName: data?.staffName || undefined
});

export const incomeApiService = {
  getAll: async (): Promise<IncomeDTO[]> => {
    const res = await api.get<any, ApiResponse<IncomeDTO[]>>('/v1/incomes');
    return Array.isArray(res.data) ? res.data.map((row) => normalizeIncome((row || {}) as AnyRecord)) : [];
  },

  getById: async (id: number): Promise<IncomeDTO> => {
    const res = await api.get<any, ApiResponse<IncomeDTO>>(`/v1/incomes/${id}`);
    return normalizeIncome((res.data || {}) as AnyRecord);
  },

  create: async (data: IncomeDTO): Promise<IncomeDTO> => {
    const res = await api.post<any, ApiResponse<IncomeDTO>>('/v1/incomes', data);
    return normalizeIncome((res.data || {}) as AnyRecord);
  }
};
