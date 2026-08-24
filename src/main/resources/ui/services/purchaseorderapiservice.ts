import { api } from './api';
import { ApiResponse, PurchaseOrderDTO, PurchaseOrderReceivePayload, PurchaseDTO } from '../types';

export interface PurchaseOrderPage {
  content: PurchaseOrderDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface PurchaseOrderReceiveResult {
  order?: PurchaseOrderDTO;
  purchase?: PurchaseDTO;
}

export const purchaseOrderApiService = {
  getAllPaged: async (page = 0, size = 20, search = ''): Promise<PurchaseOrderPage> => {
    const q = search.trim() ? `&search=${encodeURIComponent(search.trim())}` : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/purchase-orders?page=${page}&size=${size}${q}`);
    const d = res.data ?? {};
    return {
      content: Array.isArray(d.content) ? d.content : [],
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },

  getById: async (id: number): Promise<PurchaseOrderDTO> => {
    const res = await api.get<any, ApiResponse<PurchaseOrderDTO>>(`/v1/purchase-orders/${id}`);
    return res.data;
  },

  create: async (data: PurchaseOrderDTO): Promise<PurchaseOrderDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseOrderDTO>>('/v1/purchase-orders', data);
    return res.data;
  },

  update: async (id: number, data: PurchaseOrderDTO): Promise<PurchaseOrderDTO> => {
    const res = await api.put<any, ApiResponse<PurchaseOrderDTO>>(`/v1/purchase-orders/${id}`, data);
    return res.data;
  },

  cancel: async (id: number): Promise<void> => {
    await api.delete<any, ApiResponse<void>>(`/v1/purchase-orders/${id}`);
  },

  receive: async (id: number, payload: PurchaseOrderReceivePayload): Promise<PurchaseOrderReceiveResult> => {
    const res = await api.post<any, ApiResponse<PurchaseOrderReceiveResult>>(`/v1/purchase-orders/${id}/receive`, payload);
    return res.data;
  }
};
