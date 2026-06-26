import { api } from './api';
import { AdjustmentType, ApiResponse, StockAdjustmentDTO } from '../types';

export interface StockAdjustmentPage {
  content: StockAdjustmentDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

type AnyRecord = Record<string, any>;

const normalizeType = (value: any): AdjustmentType => {
  const text = String(value || '').toUpperCase();
  if (text === AdjustmentType.DAMAGE) return AdjustmentType.DAMAGE;
  if (text === AdjustmentType.LOSS) return AdjustmentType.LOSS;
  if (text === AdjustmentType.FOUND) return AdjustmentType.FOUND;
  return AdjustmentType.CORRECTION;
};

const normalizeStockAdjustment = (data: AnyRecord): StockAdjustmentDTO => ({
  ...data,
  id: Number(data?.id) || undefined,
  productId: Number(data?.productId) || 0,
  productName: data?.productName || undefined,
  productCode: data?.productCode || undefined,
  adjustmentType: normalizeType(data?.adjustmentType),
  qtyChange: Number(data?.qtyChange) || 0,
  qtyBefore: data?.qtyBefore === null || data?.qtyBefore === undefined ? undefined : Number(data?.qtyBefore),
  qtyAfter: data?.qtyAfter === null || data?.qtyAfter === undefined ? undefined : Number(data?.qtyAfter),
  serialNumbers: data?.serialNumbers || undefined,
  reason: data?.reason || undefined,
  staffId: Number(data?.staffId) || 0,
  staffName: data?.staffName || undefined,
  createdAt: data?.createdAt || undefined
});

export const stockAdjustmentApiService = {
  getAll: async (page = 0, size = 20, search = ''): Promise<StockAdjustmentPage> => {
    const res = await api.get<any, ApiResponse<StockAdjustmentPage>>(
      `/v1/stock-adjustments?page=${page}&size=${size}&search=${encodeURIComponent(search)}`
    );
    const d = res.data as any;
    return {
      content: Array.isArray(d?.content) ? d.content.map((row: AnyRecord) => normalizeStockAdjustment(row)) : [],
      totalElements: d?.totalElements ?? 0,
      totalPages: d?.totalPages ?? 0,
      pageNumber: d?.pageNumber ?? 0,
      pageSize: d?.pageSize ?? size,
    };
  },

  getById: async (id: number): Promise<StockAdjustmentDTO> => {
    const res = await api.get<any, ApiResponse<StockAdjustmentDTO>>(`/v1/stock-adjustments/${id}`);
    return normalizeStockAdjustment((res.data || {}) as AnyRecord);
  },

  create: async (data: StockAdjustmentDTO): Promise<StockAdjustmentDTO> => {
    const res = await api.post<any, ApiResponse<StockAdjustmentDTO>>('/v1/stock-adjustments', data);
    return normalizeStockAdjustment((res.data || {}) as AnyRecord);
  }
};
