import { api, BASE_URL, getAccessToken } from './api';
import { ApiResponse, PurchaseDTO, PurchaseBudgetCheck, ReorderSuggestionDTO } from '../types';

export interface PurchasePage {
  content: PurchaseDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface PurchaseStats {
  count: number;
  totalAmount: number;
  paidAmount: number;
  dueAmount: number;
}

export interface PurchaseTrendPoint {
  date: string;
  purchaseAmount: number;
  paidAmount: number;
  payableAmount: number;
  count: number;
}

export interface TopSupplierPoint {
  supplierName: string;
  supplierCode: string;
  totalAmount: number;
  count: number;
}

export interface PurchaseImportRow {
  rowNumber:number; productCode:string; productId?:number; productName?:string; qty?:number; unitCost?:number;
  subtotal?:number; batchNumber?:string; expiryDate?:string; serialRequired?:boolean; valid:boolean; errors:string[];
}
export interface PurchaseImportPreview { totalRows:number; validRows:number; invalidRows:number; rows:PurchaseImportRow[]; errors:string[]; }
export interface PurchaseTimelineEvent { type:string; at?:string; title:string; detail?:string; refCode?:string; amount?:number }
export interface PurchaseAnalyticsNamed { name:string; count:number; amount:number }
export interface PurchaseAnalytics {
  voucherCount:number; totalSpent:number; paidAmount:number; dueAmount:number; taxAmount:number;
  withholdingTaxAmount:number; landedCostAmount:number; returnAmount:number; foreignAmount:number;
  fxVoucherCount:number; grnCount:number; grnVarianceCount:number;
  byCategory:PurchaseAnalyticsNamed[]; bySupplier:PurchaseAnalyticsNamed[]; byCurrency:PurchaseAnalyticsNamed[];
}

export interface PurchaseOcrPreviewLine {
  productHint?: string;
  qty?: number;
  unitCost?: number;
}

export interface PurchaseOcrPreview {
  supplierInvoiceNo?: string;
  supplierHint?: string;
  suggestedTotal?: number;
  suggestedTax?: number;
  rawText?: string;
  note?: string;
  lines?: PurchaseOcrPreviewLine[];
}

export const purchaseApiService = {
  getAll: async (): Promise<PurchaseDTO[]> => {
    const res = await api.get<any, ApiResponse<any>>('/v1/purchases?page=0&size=500');
    return res.data?.content ?? res.data ?? [];
  },

  getAllPaged: async (page = 0, size = 20, search = '', dateFrom = '', dateTo = ''): Promise<PurchasePage> => {
    const q = search.trim() ? `&search=${encodeURIComponent(search.trim())}` : '';
    const df = dateFrom ? `&dateFrom=${dateFrom}` : '';
    const dt = dateTo ? `&dateTo=${dateTo}` : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/purchases?page=${page}&size=${size}${q}${df}${dt}`);
    const d = res.data ?? {};
    return {
      content: Array.isArray(d.content) ? d.content : [],
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },

  getStats: async (dateFrom = '', dateTo = ''): Promise<PurchaseStats> => {
    const params: string[] = [];
    if (dateFrom) params.push(`dateFrom=${dateFrom}`);
    if (dateTo) params.push(`dateTo=${dateTo}`);
    const q = params.length ? `?${params.join('&')}` : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/purchases/stats${q}`);
    const d = res.data ?? {};
    return {
      count: Number(d.count) || 0,
      totalAmount: Number(d.totalAmount) || 0,
      paidAmount: Number(d.paidAmount) || 0,
      dueAmount: Number(d.dueAmount) || 0,
    };
  },

  getTrend: async (dateFrom = '', dateTo = ''): Promise<PurchaseTrendPoint[]> => {
    const params = new URLSearchParams();
    if (dateFrom) params.set('dateFrom', dateFrom);
    if (dateTo) params.set('dateTo', dateTo);
    const q = params.toString() ? `?${params.toString()}` : '';
    const res = await api.get<any, ApiResponse<PurchaseTrendPoint[]>>(`/v1/purchases/trend${q}`);
    return (res.data ?? []).map((point) => ({
      ...point,
      purchaseAmount: Number(point.purchaseAmount) || 0,
      paidAmount: Number(point.paidAmount) || 0,
      payableAmount: Number(point.payableAmount) || 0,
      count: Number(point.count) || 0,
    }));
  },

  getTopSuppliers: async (dateFrom = '', dateTo = ''): Promise<TopSupplierPoint[]> => {
    const params = new URLSearchParams();
    if (dateFrom) params.set('dateFrom', dateFrom);
    if (dateTo) params.set('dateTo', dateTo);
    const q = params.toString() ? `?${params.toString()}` : '';
    const res = await api.get<any, ApiResponse<TopSupplierPoint[]>>(`/v1/purchases/top-suppliers${q}`);
    return (res.data ?? []).map((point) => ({
      supplierName: point.supplierName,
      supplierCode: point.supplierCode,
      totalAmount: Number(point.totalAmount) || 0,
      count: Number(point.count) || 0,
    }));
  },

  getById: async (id: number): Promise<PurchaseDTO> => {
    const res = await api.get<any, ApiResponse<PurchaseDTO>>(`/v1/purchases/${id}`);
    return res.data;
  },

  create: async (data: PurchaseDTO): Promise<PurchaseDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseDTO>>('/v1/purchases', data);
    return res.data;
  },
  previewImport: async (file:File):Promise<PurchaseImportPreview> => {
    const form=new FormData(); form.append('file',file);
    const res=await api.post<any,ApiResponse<PurchaseImportPreview>>('/v1/purchases/import/preview',form);
    return res.data;
  },
  ocrPreview: async (file: File): Promise<PurchaseOcrPreview> => {
    const form = new FormData();
    form.append('file', file);
    const res = await api.post<any, ApiResponse<PurchaseOcrPreview>>('/v1/purchases/ocr/preview', form);
    return res.data;
  },
  downloadImportTemplate: async ():Promise<void> => {
    const token=getAccessToken();const res=await fetch(`${BASE_URL}/v1/purchases/import/template`,{headers:token?{Authorization:`Bearer ${token}`}:{}});
    if(!res.ok)throw new Error('Template download failed');const url=URL.createObjectURL(await res.blob());const a=document.createElement('a');a.href=url;a.download='purchase_import_template.xlsx';a.click();URL.revokeObjectURL(url);
  },
  checkBudget: async (data: PurchaseDTO): Promise<PurchaseBudgetCheck> => {
    const res = await api.post<any, ApiResponse<PurchaseBudgetCheck | string[]>>('/v1/purchases/budget-check', data);
    const d = res.data;
    if (Array.isArray(d)) return { warnings: d, blocks: [], blocked: false };
    return {
      warnings: d?.warnings ?? [],
      blocks: d?.blocks ?? [],
      blocked: !!d?.blocked
    };
  },
  getTimeline: async (id: number): Promise<PurchaseTimelineEvent[]> => {
    const res = await api.get<any, ApiResponse<PurchaseTimelineEvent[]>>(`/v1/purchases/${id}/timeline`);
    return res.data ?? [];
  },
  getAnalytics: async (dateFrom = '', dateTo = ''): Promise<PurchaseAnalytics> => {
    const params = new URLSearchParams();
    if (dateFrom) params.set('dateFrom', dateFrom);
    if (dateTo) params.set('dateTo', dateTo);
    const q = params.toString() ? `?${params.toString()}` : '';
    const res = await api.get<any, ApiResponse<PurchaseAnalytics>>(`/v1/purchases/analytics${q}`);
    const d = (res.data ?? {}) as PurchaseAnalytics;
    return {
      voucherCount: Number(d.voucherCount) || 0,
      totalSpent: Number(d.totalSpent) || 0,
      paidAmount: Number(d.paidAmount) || 0,
      dueAmount: Number(d.dueAmount) || 0,
      taxAmount: Number(d.taxAmount) || 0,
      withholdingTaxAmount: Number(d.withholdingTaxAmount) || 0,
      landedCostAmount: Number(d.landedCostAmount) || 0,
      returnAmount: Number(d.returnAmount) || 0,
      foreignAmount: Number(d.foreignAmount) || 0,
      fxVoucherCount: Number(d.fxVoucherCount) || 0,
      grnCount: Number(d.grnCount) || 0,
      grnVarianceCount: Number(d.grnVarianceCount) || 0,
      byCategory: (d.byCategory || []).map((x) => ({ name: x.name, count: Number(x.count) || 0, amount: Number(x.amount) || 0 })),
      bySupplier: (d.bySupplier || []).map((x) => ({ name: x.name, count: Number(x.count) || 0, amount: Number(x.amount) || 0 })),
      byCurrency: (d.byCurrency || []).map((x) => ({ name: x.name, count: Number(x.count) || 0, amount: Number(x.amount) || 0 })),
    };
  },

  confirmDraft: async (id: number, data?: Partial<PurchaseDTO>): Promise<PurchaseDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseDTO>>(`/v1/purchases/${id}/confirm`, data ?? {});
    return res.data;
  },

  cancel: async (id: number, reason: string, refundPaymentMethodId?: number): Promise<PurchaseDTO> => {
    const params = new URLSearchParams({ reason });
    if (refundPaymentMethodId && refundPaymentMethodId > 0) params.set('refundPaymentMethodId', String(refundPaymentMethodId));
    const res = await api.delete<any, ApiResponse<PurchaseDTO>>(`/v1/purchases/${id}?${params.toString()}`);
    return res.data;
  },

  updateAttachment: async (id: number, attachmentName?: string, attachmentData?: string): Promise<PurchaseDTO> => {
    const res = await api.put<any, ApiResponse<PurchaseDTO>>(`/v1/purchases/${id}/attachment`, {
      attachmentName: attachmentName ?? null,
      attachmentData: attachmentData ?? null,
    });
    return res.data;
  },

  getOverdue: async (): Promise<PurchaseDTO[]> => {
    const res = await api.get<any, ApiResponse<PurchaseDTO[]>>('/v1/purchases/overdue');
    return res.data ?? [];
  },

  getReorderSuggestions: async (): Promise<ReorderSuggestionDTO[]> => {
    const res = await api.get<any, ApiResponse<ReorderSuggestionDTO[]>>('/v1/purchases/reorder-suggestions');
    return res.data ?? [];
  },

  exportExcel: async (dateFrom = '', dateTo = ''): Promise<void> => {
    const token = getAccessToken();
    const params = new URLSearchParams();
    if (dateFrom) params.append('dateFrom', dateFrom);
    if (dateTo) params.append('dateTo', dateTo);
    const res = await fetch(`${BASE_URL}/v1/purchases/export/excel?${params.toString()}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('Export failed');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `purchases_${new Date().toISOString().slice(0, 10)}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  }
};
