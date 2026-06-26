import { api } from './api';
import { ApiResponse, AccountBalanceDTO, AccountTransferDTO, PaymentTransactionDTO } from '../types';

export const accountingApiService = {
  getAllBalances: async (): Promise<AccountBalanceDTO[]> => {
    const res = await api.get<any, ApiResponse<AccountBalanceDTO[]>>('/v1/account-balances');
    return res.data;
  },
  
  getAccountBalance: async (accountId: number): Promise<AccountBalanceDTO> => {
    const res = await api.get<any, ApiResponse<AccountBalanceDTO>>(`/v1/account-balances/account/${accountId}`);
    return res.data;
  },

  getBalanceByAccountAndYear: async (accountId: number, fiscalYear: string): Promise<AccountBalanceDTO> => {
    const normalizedYear = fiscalYear.trim().toLowerCase();
    const balances = await accountingApiService.getAllBalances();
    const match = balances.find(
      (b) => b.accountId === accountId && (b.fiscalYear || '').trim().toLowerCase() === normalizedYear
    );
    if (!match) throw new Error('No balance found for this account and fiscal year');
    return match;
  },

  setOpeningBalance: async (accountId: number, amount: number, staffId: number, paymentMethodId: number): Promise<AccountBalanceDTO> => {
    const res = await api.post<any, ApiResponse<AccountBalanceDTO>>(
      `/v1/account-balances/set-opening?accountId=${accountId}&amount=${amount}&staffId=${staffId}&paymentMethodId=${paymentMethodId}`
    );
    return res.data;
  },

  getAllTransactions: async (): Promise<PaymentTransactionDTO[]> => {
    const res = await api.get<any, ApiResponse<PaymentTransactionDTO[]>>('/v1/payment-transactions');
    return res.data;
  },

  getDetailedReport: async (): Promise<PaymentTransactionDTO[]> => {
    const res = await api.get<any, ApiResponse<PaymentTransactionDTO[]>>('/v1/payment-transactions/report');
    return res.data;
  },

  getTransactionsByRef: async (refId: number, type: string): Promise<PaymentTransactionDTO[]> => {
    const res = await api.get<any, ApiResponse<PaymentTransactionDTO[]>>(`/v1/payment-transactions/reference/${refId}?type=${type}`);
    return res.data;
  },

  createPaymentTransaction: async (payload: {
    referenceId: number;
    referenceType: string;
    paymentMethodId: number;
    amount: number;
    transactionNo?: string;
  }): Promise<PaymentTransactionDTO> => {
    const res = await api.post<any, ApiResponse<PaymentTransactionDTO>>('/v1/payment-transactions/pay-purchase-debt', payload);
    return res.data;
  },

  transferPaymentMethodBalance: async (payload: AccountTransferDTO): Promise<PaymentTransactionDTO> => {
    const res = await api.post<any, ApiResponse<PaymentTransactionDTO>>('/v1/payment-transactions/transfer', payload);
    return res.data;
  }
};
