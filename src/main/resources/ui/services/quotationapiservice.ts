import { api } from './api';
import { ApiResponse, QuotationDTO, SaleDTO } from '../types';

export const quotationApiService = {
  getAll: async (): Promise<QuotationDTO[]> => {
    const res = await api.get<any, ApiResponse<QuotationDTO[]>>('/v1/quotations');
    return res.data ?? [];
  },
  getById: async (id: number): Promise<QuotationDTO> => {
    const res = await api.get<any, ApiResponse<QuotationDTO>>(`/v1/quotations/${id}`);
    return res.data;
  },
  create: async (data: QuotationDTO): Promise<QuotationDTO> => {
    const res = await api.post<any, ApiResponse<QuotationDTO>>('/v1/quotations', data);
    return res.data;
  },
  update: async (id: number, data: QuotationDTO): Promise<QuotationDTO> => {
    const res = await api.put<any, ApiResponse<QuotationDTO>>(`/v1/quotations/${id}`, data);
    return res.data;
  },
  changeStatus: async (id: number, status: string): Promise<QuotationDTO> => {
    const res = await api.patch<any, ApiResponse<QuotationDTO>>(`/v1/quotations/${id}/status?status=${encodeURIComponent(status)}`);
    return res.data;
  },
  convertToSale: async (id: number, sale?: Partial<SaleDTO>): Promise<SaleDTO> => {
    const res = await api.post<any, ApiResponse<SaleDTO>>(`/v1/quotations/${id}/convert-to-sale`, sale || {});
    return res.data;
  }
};
