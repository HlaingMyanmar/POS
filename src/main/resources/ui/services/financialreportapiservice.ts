import { api } from './api';
import { ApiResponse, TrialBalanceDTO, BalanceSheetDTO, AgingReportDTO } from '../types';

export const financialReportService = {
  getTrialBalance: async (asOf: string): Promise<TrialBalanceDTO> => {
    const res = await api.get<any, ApiResponse<TrialBalanceDTO>>(`/v1/reports/trial-balance?asOf=${asOf}`);
    const d = res.data || {} as any;
    return {
      asOf:            d.asOf            || asOf,
      lines:           Array.isArray(d.lines) ? d.lines.map((l: any) => ({
        accountCode:  l.accountCode  || '',
        accountName:  l.accountName  || '',
        accountType:  l.accountType  || '',
        totalDebit:   Number(l.totalDebit)  || 0,
        totalCredit:  Number(l.totalCredit) || 0,
      })) : [],
      grandTotalDebit:  Number(d.grandTotalDebit)  || 0,
      grandTotalCredit: Number(d.grandTotalCredit) || 0,
      balanced:         Boolean(d.balanced),
    };
  },

  getBalanceSheet: async (asOf: string): Promise<BalanceSheetDTO> => {
    const res = await api.get<any, ApiResponse<BalanceSheetDTO>>(`/v1/reports/balance-sheet?asOf=${asOf}`);
    const d = res.data || {} as any;
    const mapItems = (arr: any[]) => (Array.isArray(arr) ? arr : []).map((i: any) => ({
      accountCode: i.accountCode || '',
      accountName: i.accountName || '',
      balance:     Number(i.balance) || 0,
    }));
    return {
      asOf:                    d.asOf || asOf,
      assets:                  mapItems(d.assets),
      totalAssets:             Number(d.totalAssets)             || 0,
      liabilities:             mapItems(d.liabilities),
      totalLiabilities:        Number(d.totalLiabilities)        || 0,
      equityItems:             mapItems(d.equityItems),
      currentYearPnL:          Number(d.currentYearPnL)          || 0,
      totalEquity:             Number(d.totalEquity)             || 0,
      totalLiabilitiesAndEquity: Number(d.totalLiabilitiesAndEquity) || 0,
      balanced:                Boolean(d.balanced),
    };
  },

  getArAging: async (asOf: string): Promise<AgingReportDTO> => {
    const res = await api.get<any, ApiResponse<AgingReportDTO>>(`/v1/reports/ar-aging?asOf=${asOf}`);
    return res.data as AgingReportDTO;
  },

  getApAging: async (asOf: string): Promise<AgingReportDTO> => {
    const res = await api.get<any, ApiResponse<AgingReportDTO>>(`/v1/reports/ap-aging?asOf=${asOf}`);
    return res.data as AgingReportDTO;
  },
};
