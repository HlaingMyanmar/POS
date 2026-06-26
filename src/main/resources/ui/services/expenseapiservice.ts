import { api } from './api';
import { ApiResponse, ExpenseDTO } from '../types';

type AnyRecord = Record<string, any>;

const normalizeExpense = (data: AnyRecord): ExpenseDTO => ({
  ...data,
  id: Number(data?.id) || undefined,
  expenseCode: data?.expenseCode || undefined,
  expenseDate: data?.expenseDate || undefined,
  accountId: Number(data?.accountId) || 0,
  accountName: data?.accountName || undefined,
  paymentMethodId: Number(data?.paymentMethodId) || 0,
  paymentMethodName: data?.paymentMethodName || undefined,
  amount: Number(data?.amount) || 0,
  description: data?.description || undefined,
  staffId: Number(data?.staffId) || 0,
  staffName: data?.staffName || undefined
});

export const expenseApiService = {
  getAll: async (): Promise<ExpenseDTO[]> => {
    const res = await api.get<any, ApiResponse<ExpenseDTO[]>>('/v1/expenses');
    return Array.isArray(res.data) ? res.data.map((row) => normalizeExpense((row || {}) as AnyRecord)) : [];
  },

  getById: async (id: number): Promise<ExpenseDTO> => {
    const res = await api.get<any, ApiResponse<ExpenseDTO>>(`/v1/expenses/${id}`);
    return normalizeExpense((res.data || {}) as AnyRecord);
  },

  create: async (data: ExpenseDTO): Promise<ExpenseDTO> => {
    const res = await api.post<any, ApiResponse<ExpenseDTO>>('/v1/expenses', data);
    return normalizeExpense((res.data || {}) as AnyRecord);
  }
};
