import { api } from './api';
import { ApiResponse, PurchaseReturnDTO, PurchaseReturnDetailDTO, PurchaseReturnReasonDTO } from '../types';

type AnyRecord = Record<string, any>;

const toStringList = (value: any): string[] => {
  if (value === null || value === undefined) return [];

  if (Array.isArray(value)) {
    return value.flatMap((item) => {
      if (item === null || item === undefined) return [];
      if (typeof item === 'string' || typeof item === 'number') {
        const text = String(item).trim();
        return text ? [text] : [];
      }
      if (typeof item === 'object') {
        const serialText = item.serialNumber ?? item.serialNo ?? item.serial ?? item.value ?? item.code;
        if (serialText === null || serialText === undefined) return [];
        const normalized = String(serialText).trim();
        return normalized ? [normalized] : [];
      }
      return [];
    });
  }

  if (typeof value === 'string') {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }

  if (typeof value === 'number') {
    return [String(value)];
  }

  return [];
};

const normalizeDetail = (detail: AnyRecord): PurchaseReturnDetailDTO => {
  const serialNumbers = Array.from(new Set([
    ...toStringList(detail?.serialNumbers),
    ...toStringList(detail?.serialNos),
    ...toStringList(detail?.serialNoList),
    ...toStringList(detail?.serials),
    ...toStringList(detail?.serialNumber),
    ...toStringList(detail?.serialNo),
    ...toStringList(detail?.returnSerialNumbers),
    ...toStringList(detail?.returnSerials),
    ...toStringList(detail?.purchaseReturnSerials),
    ...toStringList(detail?.purchaseReturnDetailSerials),
    ...toStringList(detail?.productSerials)
  ]));

  return {
    ...detail,
    returnId: Number(detail?.returnId) || undefined,
    productId: Number(detail?.productId) || 0,
    qty: Number(detail?.qty) || 0,
    unitPrice: Number(detail?.unitPrice ?? detail?.unitCost) || 0,
    subtotal: Number(detail?.subtotal) || (Number(detail?.qty) || 0) * (Number(detail?.unitPrice ?? detail?.unitCost) || 0),
    allocatedShippingCost: Number(detail?.allocatedShippingCost) || 0,
    serialNumbers
  };
};

const normalizePurchaseReturn = (data: AnyRecord): PurchaseReturnDTO => {
  const rawDetails = data?.details ?? data?.purchaseReturnDetails ?? data?.returnDetails ?? [];
  const details = Array.isArray(rawDetails) ? rawDetails.map((d) => normalizeDetail((d || {}) as AnyRecord)) : [];

  return {
    ...data,
    purchaseId: Number(data?.purchaseId) || 0,
    shippingCostAmount: Number(data?.shippingCostAmount) || 0,
    companyShippingPortion: Number(data?.companyShippingPortion) || 0,
    supplierShippingPortion: Number(data?.supplierShippingPortion) || 0,
    details
  };
};

export interface PurchaseReturnPage {
  content: PurchaseReturnDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export const purchaseReturnApiService = {
  getAll: async (page = 0, size = 20, search = '', filters: Record<string,string|number|undefined> = {}): Promise<PurchaseReturnPage> => {
    const params=new URLSearchParams({page:String(page),size:String(size)}); if(search.trim())params.set('search',search.trim()); Object.entries(filters).forEach(([k,v])=>{if(v!==undefined&&v!=='')params.set(k,String(v));});
    const res = await api.get<any, ApiResponse<any>>(`/v1/purchase-returns?${params.toString()}`);
    const d = res.data ?? {};
    const content = Array.isArray(d.content) ? d.content.map((row: any) => normalizePurchaseReturn((row || {}) as AnyRecord)) : [];
    return {
      content,
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },

  getByPurchaseId: async (purchaseId: number): Promise<PurchaseReturnDTO[]> => {
    const res = await api.get<any, ApiResponse<PurchaseReturnDTO[]>>(`/v1/purchase-returns/by-purchase/${purchaseId}`);
    return Array.isArray(res.data) ? res.data.map((row) => normalizePurchaseReturn((row || {}) as AnyRecord)) : [];
  },

  getById: async (id: number): Promise<PurchaseReturnDTO> => {
    const res = await api.get<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}`);
    return normalizePurchaseReturn((res.data || {}) as AnyRecord);
  },

  create: async (data: PurchaseReturnDTO): Promise<PurchaseReturnDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseReturnDTO>>('/v1/purchase-returns', data);
    return normalizePurchaseReturn((res.data || {}) as AnyRecord);
  },

  update: async (id: number, data: PurchaseReturnDTO): Promise<PurchaseReturnDTO> => {
    const res = await api.put<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}`, data);
    return normalizePurchaseReturn((res.data || {}) as AnyRecord);
  },

  voidReturn: async (id: number, reason: string): Promise<PurchaseReturnDTO> => {
    const res = await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/void`, {
      voidReason: reason,
      reason
    });
    return normalizePurchaseReturn((res.data || {}) as AnyRecord);
  },

  submit: async (id: number) => normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/submit`, {})).data || {}),
  approve: async (id: number, approvalNote?: string) => normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/approve`, { approvalNote })).data || {}),
  reject: async (id: number, rejectionReason: string) => normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/reject`, { rejectionReason })).data || {}),
  addAttachment: async (id: number, data: { attachmentType: string; fileName: string; contentType?: string; dataUrl: string }) => normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/attachments`, data)).data || {}),
  deleteAttachment: async (id: number, attachmentId: number) => normalizePurchaseReturn((await api.delete<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/attachments/${attachmentId}`)).data || {}),
  dispatch: async (id: number, data: Pick<PurchaseReturnDTO, 'carrier' | 'trackingNo' | 'dispatchedAt' | 'deliveryProof'>) =>
    normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/dispatch`, data)).data || {}),
  supplierReceived: async (id: number, data: Pick<PurchaseReturnDTO, 'supplierReceivedAt' | 'deliveryProof'> = {}) =>
    normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/supplier-received`, data)).data || {}),
  settle: async (id: number, data: PurchaseReturnDTO) =>
    normalizePurchaseReturn((await api.post<any, ApiResponse<PurchaseReturnDTO>>(`/v1/purchase-returns/${id}/settle`, data)).data || {}),

  getReasons: async (activeOnly = false): Promise<PurchaseReturnReasonDTO[]> => {
    const res = await api.get<any, ApiResponse<PurchaseReturnReasonDTO[]>>(`/v1/purchase-return-reasons?activeOnly=${activeOnly}`);
    return res.data || [];
  },
  createReason: async (data: PurchaseReturnReasonDTO): Promise<PurchaseReturnReasonDTO> =>
    (await api.post<any, ApiResponse<PurchaseReturnReasonDTO>>('/v1/purchase-return-reasons', data)).data!,
  updateReason: async (id: number, data: PurchaseReturnReasonDTO): Promise<PurchaseReturnReasonDTO> =>
    (await api.put<any, ApiResponse<PurchaseReturnReasonDTO>>(`/v1/purchase-return-reasons/${id}`, data)).data!,

  delete: async (id: number): Promise<void> => {
    await api.delete<any, ApiResponse<void>>(`/v1/purchase-returns/${id}`);
  }
};
