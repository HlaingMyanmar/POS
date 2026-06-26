import { api } from './api';
import { ApiResponse } from '../types';

export interface VoucherSettingDto {
  id: number | null;
  documentType: string;
  paperSize: string;

  marginTopMm: number | null;
  marginBottomMm: number | null;
  marginLeftMm: number | null;
  marginRightMm: number | null;

  headerHeightPx: number | null;
  contHeaderHeightPx: number | null;
  infoBlocksHeightPx: number | null;
  tableHeaderHeightPx: number | null;
  rowHeightPx: number | null;
  totalsAreaHeightPx: number | null;
  footerHeightPx: number | null;
  safetyMarginPx: number | null;

  showLogo: boolean;
  showQrCode: boolean;
  showSignatures: boolean;
  showPaymentHistory: boolean;
  showSerial: boolean;

  sign1Label: string;
  sign2Label: string;
  voucherTitle: string;
  footerNote: string;
  customerNotice: string;

  headerFontFamily: string | null;
  headerFontSizePx: number | null;
  infoFontFamily: string | null;
  infoFontSizePx: number | null;
  tableHeaderFontFamily: string | null;
  tableHeaderFontSizePx: number | null;
  tableDataFontFamily: string | null;
  tableDataFontSizePx: number | null;
  footerFontFamily: string | null;
  footerFontSizePx: number | null;
  noticeFontFamily: string | null;
  noticeFontSizePx: number | null;

  rowsOnFirstPage: number | null;
  rowsOnContinuationPage: number | null;

  updatedAt: string | null;
  updatedBy: string | null;
}

export type DocumentType = 'SALE' | 'SERVICE_JOB' | 'SERVICE_DONE' | 'BOOKING' | 'PURCHASE';

export const voucherSettingService = {
  getAll(): Promise<VoucherSettingDto[]> {
    return api.get<any, ApiResponse<VoucherSettingDto[]>>('/v1/voucher-settings')
      .then(r => r.data);
  },

  getByType(type: DocumentType): Promise<VoucherSettingDto> {
    return api.get<any, ApiResponse<VoucherSettingDto>>(`/v1/voucher-settings/${type}`)
      .then(r => r.data);
  },

  save(type: DocumentType, dto: VoucherSettingDto): Promise<VoucherSettingDto> {
    return api.put<any, ApiResponse<VoucherSettingDto>>(`/v1/voucher-settings/${type}`, dto)
      .then(r => r.data);
  },

  reset(type: DocumentType): Promise<VoucherSettingDto> {
    return api.post<any, ApiResponse<VoucherSettingDto>>(`/v1/voucher-settings/${type}/reset`)
      .then(r => r.data);
  },
};
