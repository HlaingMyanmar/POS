import { api } from './api';
import { ApiResponse } from '../types';

export interface BarcodeLabelSettingsDTO {
  id?: number;
  codeType: string;
  labelPreset: string;
  customW: number;
  customH: number;
  customCols: number;
  codeBarcodeH: number;
  codeBarcodeW: number;
  codeQrPx: number;
  labelFontSize: number;
  subLabelFontSize: number;
  showPrice: boolean;
  showProductCode: boolean;
  showWarranty: boolean;
  marginTop: number;
  marginBottom: number;
  marginLeft: number;
  marginRight: number;
  paperSizeKey: string;
  customPaperW: number;
  customPaperH: number;
}

export interface BarcodeLabelPresetDTO extends BarcodeLabelSettingsDTO {
  id?: number;
  name: string;
}

export const barcodeSettingsService = {
  get: (): Promise<BarcodeLabelSettingsDTO> =>
    api.get<any, ApiResponse<BarcodeLabelSettingsDTO>>('/v1/barcode-label-settings').then((r) => r.data),

  save: (dto: BarcodeLabelSettingsDTO): Promise<BarcodeLabelSettingsDTO> =>
    api.post<any, ApiResponse<BarcodeLabelSettingsDTO>>('/v1/barcode-label-settings', dto).then((r) => r.data),
};

export const barcodePresetService = {
  getAll: (): Promise<BarcodeLabelPresetDTO[]> =>
    api.get<any, ApiResponse<BarcodeLabelPresetDTO[]>>('/v1/barcode-label-presets').then((r) => r.data),

  create: (dto: BarcodeLabelPresetDTO): Promise<BarcodeLabelPresetDTO> =>
    api.post<any, ApiResponse<BarcodeLabelPresetDTO>>('/v1/barcode-label-presets', dto).then((r) => r.data),

  update: (id: number, dto: BarcodeLabelPresetDTO): Promise<BarcodeLabelPresetDTO> =>
    api.put<any, ApiResponse<BarcodeLabelPresetDTO>>(`/v1/barcode-label-presets/${id}`, dto).then((r) => r.data),

  delete: (id: number): Promise<void> =>
    api.delete<any, ApiResponse<void>>(`/v1/barcode-label-presets/${id}`).then(() => undefined),
};
