import { api } from './api';
import { ApiResponse, PurchaseDTO } from '../types';

export interface PurchasePage {
  content: PurchaseDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export const purchaseApiService = {
  getAll: async (): Promise<PurchaseDTO[]> => {
    const res = await api.get<any, ApiResponse<any>>('/v1/purchases?page=0&size=500');
    return res.data?.content ?? res.data ?? [];
  },

  getAllPaged: async (page = 0, size = 20, search = ''): Promise<PurchasePage> => {
    const q = search.trim() ? `&search=${encodeURIComponent(search.trim())}` : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/purchases?page=${page}&size=${size}${q}`);
    const d = res.data ?? {};
    return {
      content: Array.isArray(d.content) ? d.content : [],
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },
  
  getById: async (id: number): Promise<PurchaseDTO> => {
    const res = await api.get<any, ApiResponse<PurchaseDTO>>(`/v1/purchases/${id}`);
    return res.data;
  },

  create: async (data: PurchaseDTO): Promise<PurchaseDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseDTO>>('/v1/purchases', data);
    return res.data;
  }
};
