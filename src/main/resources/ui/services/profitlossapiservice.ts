import { api } from './api';
import { ApiResponse, ProfitLossDTO } from '../types';

export const profitLossService = {
  getReport: async (from: string, to: string): Promise<ProfitLossDTO> => {
    const res = await api.get<any, ApiResponse<ProfitLossDTO>>(`/v1/reports/profit-loss?from=${from}&to=${to}`);
    const d = res.data || {} as any;
    return {
      from:             d.from             || from,
      to:               d.to               || to,
      grossSales:       Number(d.grossSales)       || 0,
      salesReturns:     Number(d.salesReturns)     || 0,
      netRevenue:       Number(d.netRevenue)       || 0,
      purchases:        Number(d.purchases)        || 0,
      purchaseReturns:  Number(d.purchaseReturns)  || 0,
      netPurchases:     Number(d.netPurchases)     || 0,
      grossProfit:      Number(d.grossProfit)      || 0,
      otherIncomeItems: Array.isArray(d.otherIncomeItems) ? d.otherIncomeItems : [],
      totalOtherIncome: Number(d.totalOtherIncome) || 0,
      expenseItems:     Array.isArray(d.expenseItems) ? d.expenseItems : [],
      totalExpenses:    Number(d.totalExpenses)    || 0,
      netProfit:        Number(d.netProfit)        || 0,
    };
  }
};
