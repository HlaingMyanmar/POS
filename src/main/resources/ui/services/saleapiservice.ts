import { api } from './api';
import { ApiResponse, SaleDTO, SaleDetailDTO, SalePaymentDTO } from '../types';

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

const normalizeDetail = (detail: AnyRecord): SaleDetailDTO => {
  const serialNumbers = Array.from(new Set([
    ...toStringList(detail?.serialNumbers),
    ...toStringList(detail?.serialNos),
    ...toStringList(detail?.serialNoList),
    ...toStringList(detail?.serials),
    ...toStringList(detail?.serialNumber),
    ...toStringList(detail?.serialNo),
    ...toStringList(detail?.saleSerialNumbers),
    ...toStringList(detail?.productSerials)
  ]));

  const qty = Number(detail?.qty) || 0;
  const unitPrice = Number(detail?.unitPrice ?? detail?.sellingPrice) || 0;

  return {
    ...detail,
    productId: Number(detail?.productId) || 0,
    qty,
    unitPrice,
    subtotal: Number(detail?.subtotal) || qty * unitPrice,
    serialNumbers
  };
};

const normalizeSale = (data: AnyRecord): SaleDTO => {
  const rawDetails = data?.details ?? data?.saleDetails ?? [];
  const details = Array.isArray(rawDetails) ? rawDetails.map((d) => normalizeDetail((d || {}) as AnyRecord)) : [];

  const detailsSum = details.reduce((s, d) => s + (Number(d.subtotal) || 0), 0);

  const round2 = (v: number) => Math.round(v * 100) / 100;

  const totalAmount    = round2(Number(data?.totalAmount    ?? data?.total        ?? data?.grandTotal    ?? data?.saleTotal    ?? data?.totalPrice)    || detailsSum);
  const discountAmount = round2(Number(data?.discountAmount ?? data?.discount     ?? data?.totalDiscount ?? data?.discountTotal ?? data?.discAmt)       || 0);
  const netAmount      = round2(Number(data?.netAmount      ?? data?.net          ?? data?.netTotal      ?? data?.subTotal      ?? data?.netSale       ?? data?.finalAmount ?? data?.payable) || (totalAmount - discountAmount) || 0);
  const paidAmount     = round2(Number(data?.paidAmount     ?? data?.paid         ?? data?.totalPaid     ?? data?.amountPaid    ?? data?.paidTotal)     || 0);
  const rawDue         = Number(data?.dueAmount      ?? data?.due          ?? data?.balance       ?? data?.dueBalance    ?? data?.remaining      ?? data?.outstanding ?? data?.balanceDue);
  const dueAmount      = round2(rawDue > 0 ? rawDue : Math.max(0, netAmount - paidAmount));

  return {
    ...data,
    customerId: Number(data?.customerId) || 0,
    staffId: Number(data?.staffId) || 0,
    dueDate: data?.dueDate || undefined,
    totalAmount,
    discountAmount,
    netAmount,
    paidAmount,
    dueAmount,
    creditStatus: data?.creditStatus || undefined,
    paymentMethodId: Number(data?.paymentMethodId) || undefined,
    paymentAccountId: Number(data?.paymentAccountId) || undefined,
    arAccountId: Number(data?.arAccountId) || undefined,
    details
  };
};

export interface SalePage {
  content: SaleDTO[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export const saleApiService = {
  getAll: async (): Promise<SaleDTO[]> => {
    const res = await api.get<any, ApiResponse<any>>('/v1/sales?page=0&size=500');
    const data = res.data;
    const list = data?.content ?? (Array.isArray(data) ? data : []);
    return list.map((row: any) => normalizeSale((row || {}) as AnyRecord));
  },

  getAllPaged: async (page = 0, size = 20, search = '', dateFrom = '', dateTo = ''): Promise<SalePage> => {
    const q  = search.trim() ? `&search=${encodeURIComponent(search.trim())}` : '';
    const df = dateFrom ? `&dateFrom=${dateFrom}` : '';
    const dt = dateTo   ? `&dateTo=${dateTo}`     : '';
    const res = await api.get<any, ApiResponse<any>>(`/v1/sales?page=${page}&size=${size}${q}${df}${dt}`);
    const d = res.data ?? {};
    const list = Array.isArray(d.content) ? d.content : [];
    return {
      content: list.map((row: any) => normalizeSale((row || {}) as AnyRecord)),
      totalElements: Number(d.totalElements) || 0,
      totalPages: Number(d.totalPages) || 0,
      pageNumber: Number(d.pageNumber) || 0,
      pageSize: Number(d.pageSize) || size,
    };
  },

  getById: async (id: number): Promise<SaleDTO> => {
    const res = await api.get<any, ApiResponse<SaleDTO>>(`/v1/sales/${id}`);
    if (import.meta.env.DEV) console.log('[sale getById raw]', res.data);
    return normalizeSale((res.data || {}) as AnyRecord);
  },

  create: async (data: SaleDTO): Promise<SaleDTO> => {
    const res = await api.post<any, ApiResponse<SaleDTO>>('/v1/sales', data);
    return normalizeSale((res.data || {}) as AnyRecord);
  },

  update: async (id: number, data: SaleDTO): Promise<SaleDTO> => {
    const res = await api.put<any, ApiResponse<SaleDTO>>(`/v1/sales/${id}`, data);
    return normalizeSale((res.data || {}) as AnyRecord);
  },

  payDue: async (id: number, data: SalePaymentDTO): Promise<SaleDTO> => {
    const res = await api.post<any, ApiResponse<SaleDTO>>(`/v1/sales/${id}/pay`, data);
    return normalizeSale((res.data || {}) as AnyRecord);
  },

  delete: async (id: number): Promise<void> => {
    await api.delete<any, ApiResponse<void>>(`/v1/sales/${id}`);
  }
};
