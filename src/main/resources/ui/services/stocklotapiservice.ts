import { api } from './api';
import { ApiResponse } from '../types';

export interface StockLotDTO {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  purchaseId?: number;
  purchaseCode?: string;
  batchNumber?: string;
  expiryDate?: string;
  warehouseName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  sourceType?: string;
  status?: string;
  receivedQty: number;
  remainingQty: number;
  soldQty?: number;
  daysToExpiry?: number;
  alertLevel?: 'EXPIRED' | 'CRITICAL' | 'WARNING' | 'UPCOMING';
}

export interface WarehouseBalanceDTO {
  warehouseName: string;
  productId: number;
  productCode: string;
  productName: string;
  remainingQty: number;
  receivedQty: number;
  lotCount: number;
}

export const stockLotApiService = {
  expiring: async (days = 90) => {
    const r = await api.get<any, ApiResponse<StockLotDTO[]>>(`/v1/stock-lots/expiring?days=${days}`);
    return r.data || [];
  },
  warehouseBalances: async () => {
    const r = await api.get<any, ApiResponse<WarehouseBalanceDTO[]>>('/v1/stock-lots/warehouse-balances');
    return r.data || [];
  },
  createOpening: async (body: { productId: number; warehouseId?: number; warehouseName?: string; qty: number; batchNumber?: string; expiryDate?: string; reason?: string }) => {
    const r = await api.post<any, ApiResponse<StockLotDTO>>('/v1/stock/opening', body);
    return r.data;
  },
  updateLot: async (id: number, body: { batchNumber?: string; expiryDate?: string | null; warehouseId?: number }) => {
    const r = await api.put<any, ApiResponse<StockLotDTO>>(`/v1/stock/lots/${id}`, body);
    return r.data;
  }
};
