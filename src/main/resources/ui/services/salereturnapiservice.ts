import { api } from './api';
import { ApiResponse, SaleReturnDTO, SaleReturnDetailDTO } from '../types';

type AnyRecord = Record<string, any>;

const extractList = (payload: any): AnyRecord[] => {
  if (Array.isArray(payload)) return payload as AnyRecord[];
  if (Array.isArray(payload?.data)) return payload.data as AnyRecord[];
  if (Array.isArray(payload?.content)) return payload.content as AnyRecord[];
  if (Array.isArray(payload?.items)) return payload.items as AnyRecord[];
  if (Array.isArray(payload?.rows)) return payload.rows as AnyRecord[];
  if (Array.isArray(payload?.data?.content)) return payload.data.content as AnyRecord[];
  return [];
};

const extractRecord = (payload: any): AnyRecord => {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    if (payload.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) {
      return payload.data as AnyRecord;
    }
    return payload as AnyRecord;
  }
  return {};
};

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

const normalizeDetail = (detail: AnyRecord): SaleReturnDetailDTO => {
  const serialNumbers = Array.from(new Set([
    ...toStringList(detail?.serialNumbers),
    ...toStringList(detail?.serialNos),
    ...toStringList(detail?.serialNoList),
    ...toStringList(detail?.serials),
    ...toStringList(detail?.serialNumber),
    ...toStringList(detail?.serialNo),
    ...toStringList(detail?.returnSerialNumbers),
    ...toStringList(detail?.saleReturnSerials),
    ...toStringList(detail?.saleReturnDetailSerials),
    ...toStringList(detail?.productSerials)
  ]));

  return {
    ...detail,
    id: Number(detail?.id) || undefined,
    returnId: Number(detail?.returnId) || undefined,
    productId: Number(detail?.productId) || 0,
    qty: Number(detail?.qty) || 0,
    unitPrice: Number(detail?.unitPrice) || 0,
    subtotal: Number(detail?.subtotal) || (Number(detail?.qty) || 0) * (Number(detail?.unitPrice) || 0),
    serialNumbers
  };
};

const normalizeSaleReturn = (data: AnyRecord): SaleReturnDTO => {
  const rawDetails = data?.details ?? data?.saleReturnDetails ?? data?.returnDetails ?? [];
  const details = Array.isArray(rawDetails) ? rawDetails.map((d) => normalizeDetail((d || {}) as AnyRecord)) : [];

  return {
    ...data,
    id: Number(data?.id) || undefined,
    saleId: Number(data?.saleId) || 0,
    customerId: Number(data?.customerId) || undefined,
    staffId: Number(data?.staffId) || undefined,
    saleCode: data?.saleCode || undefined,
    customerName: data?.customerName || undefined,
    returnCode: data?.returnCode || undefined,
    returnDate: data?.returnDate || data?.createdAt || undefined,
    totalReturnAmount: Number(data?.totalReturnAmount) || 0,
    refundAmount: data?.refundAmount === null || data?.refundAmount === undefined ? undefined : Number(data?.refundAmount),
    paymentMethodId: Number(data?.paymentMethodId) || undefined,
    transactionNo: data?.transactionNo || undefined,
    reason: data?.reason || undefined,
    details
  };
};

export interface SaleReturnPage {
  content: SaleReturnDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export const saleReturnApiService = {
  getAll: async (page = 0, size = 20, search = ''): Promise<SaleReturnPage> => {
    const q = search.trim() ? `&search=${encodeURIComponent(search.trim())}` : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/sale-returns?page=${page}&size=${size}${q}`);
    const d = res.data ?? {};
    const list = Array.isArray(d.content) ? d.content : [];
    return {
      content: list.map((row: any) => normalizeSaleReturn((row || {}) as AnyRecord)),
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },

  getBySaleId: async (saleId: number): Promise<SaleReturnDTO[]> => {
    const res = await api.get<any, ApiResponse<SaleReturnDTO[]>>(`/v1/sale-returns/by-sale/${saleId}`);
    return extractList(res).map((row) => normalizeSaleReturn((row || {}) as AnyRecord));
  },

  getById: async (id: number): Promise<SaleReturnDTO> => {
    const res = await api.get<any, ApiResponse<SaleReturnDTO>>(`/v1/sale-returns/${id}`);
    return normalizeSaleReturn(extractRecord(res));
  },

  create: async (data: SaleReturnDTO): Promise<SaleReturnDTO> => {
    const res = await api.post<any, ApiResponse<SaleReturnDTO>>('/v1/sale-returns', data);
    return normalizeSaleReturn(extractRecord(res));
  },

  update: async (id: number, data: SaleReturnDTO): Promise<SaleReturnDTO> => {
    const res = await api.put<any, ApiResponse<SaleReturnDTO>>(`/v1/sale-returns/${id}`, data);
    return normalizeSaleReturn(extractRecord(res));
  },

  delete: async (id: number): Promise<void> => {
    await api.delete<any, ApiResponse<void>>(`/v1/sale-returns/${id}`);
  }
};
