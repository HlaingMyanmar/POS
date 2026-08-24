
import { api, BASE_URL, getAccessToken } from './api';
import { ProductDTO, ProductStockHistoryDTO, ApiResponse, PriceHistoryDTO, ReorderSuggestionDTO } from '../types';

type StockHistoryParams = { from?: string; to?: string; type?: string; search?: string; page?: number; size?: number };

export const productService = {
  getAll: async (): Promise<ProductDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductDTO[]>>('/v1/products');
    return res.data;
  },

  getLowStock: async (): Promise<ProductDTO[]> => {
    const res = await api.get<any, ApiResponse<ProductDTO[]>>('/v1/products/low-stock');
    return res.data;
  },

  getReorderSuggestions: (): Promise<ReorderSuggestionDTO[]> =>
    api.get<any, ApiResponse<ReorderSuggestionDTO[]>>('/v1/products/reorder-suggestions').then((res: any) => res.data || []),

  getPriceHistory: (id: number): Promise<PriceHistoryDTO[]> =>
    api.get<any, ApiResponse<PriceHistoryDTO[]>>(`/v1/products/${id}/price-history`).then((res: any) => res.data || []),

  getById: (id: number) => api.get<any, ApiResponse<ProductDTO>>(`/v1/products/${id}`).then((res: any) => res.data),

  getStockHistory: (id: number, params?: StockHistoryParams): Promise<ProductStockHistoryDTO> =>
    api.get<any, ApiResponse<ProductStockHistoryDTO>>(`/v1/products/${id}/stock-history`, { params }).then((res: any) => res.data),

  getAllStockHistory: (params?: StockHistoryParams): Promise<ProductStockHistoryDTO> =>
    api.get<any, ApiResponse<ProductStockHistoryDTO>>('/v1/products/stock-history', { params }).then((res: any) => res.data),
  
  create: (product: Partial<ProductDTO>) => 
    api.post<any, ApiResponse<ProductDTO>>('/v1/products', product).then((res: any) => res.data),
  
  update: (id: number, product: Partial<ProductDTO>) => 
    api.put<any, ApiResponse<ProductDTO>>(`/v1/products/${id}`, product).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/products/${id}`).then((res: any) => res.data),

  setArchived: (id: number, archived: boolean) =>
    api.put<any, ApiResponse<ProductDTO>>(`/v1/products/${id}/archive?archived=${archived}`, {}).then((res: any) => res.data),

  assignSerials: (id: number, data: { serialNumbers: string[]; warrantyMonths?: number }) =>
    api.post<any, ApiResponse<ProductDTO>>(`/v1/products/${id}/assign-serials`, data).then((res: any) => res.data),

  getNextSerialSeq: (id: number): Promise<number> =>
    api.get<any, ApiResponse<number>>(`/v1/products/${id}/next-serial-seq`).then((res: any) => res.data),

  updatePhoto: (id: number, photoBase64: string | null) =>
    api.put<any, ApiResponse<void>>(`/v1/products/${id}/photo`, { photoBase64 }).then((res: any) => res),

  exportExcel: async (): Promise<void> => {
    const token = getAccessToken();
    const res = await fetch(`${BASE_URL}/v1/products/export`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('Export failed');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `products_${new Date().toISOString().slice(0, 10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  },

  downloadTemplate: async (): Promise<void> => {
    const token = getAccessToken();
    const res = await fetch(`${BASE_URL}/v1/products/import-template`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('Template download failed');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'product_import_template.xlsx';
    a.click();
    URL.revokeObjectURL(url);
  },

  importExcel: async (file: File): Promise<ApiResponse<{ successCount: number; errorCount: number; errors: { row: number; message: string }[] }>> => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/v1/products/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }) as any;
  },
};
