import { api } from './api';
import { ApiResponse } from '../types';

export interface StockLotDTO {
  id: number;
  productId: number;
  productCode: string;
  productName: string;
  receivedQty: number;
  remainingQty: number;
}

export const stockLotApiService = {
  createOpening: async (body: { productId: number; qty: number; reason?: string }) => {
    const r = await api.post<any, ApiResponse<StockLotDTO>>('/v1/stock/opening', body);
    return r.data;
  },
};
