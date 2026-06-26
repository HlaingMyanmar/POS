
import { api } from './api';
import { ApiResponse, ManufacturingFormulaDTO } from '../types';

export const manufacturingFormulaService = {
  getAll: (): Promise<ManufacturingFormulaDTO[]> =>
    api.get<any, ApiResponse<ManufacturingFormulaDTO[]>>('/v1/manufacturing/formulas').then(r => r.data),

  getById: (id: number): Promise<ManufacturingFormulaDTO> =>
    api.get<any, ApiResponse<ManufacturingFormulaDTO>>(`/v1/manufacturing/formulas/${id}`).then(r => r.data),

  create: (dto: ManufacturingFormulaDTO): Promise<ManufacturingFormulaDTO> =>
    api.post<any, ApiResponse<ManufacturingFormulaDTO>>('/v1/manufacturing/formulas', dto).then(r => r.data),

  update: (id: number, dto: ManufacturingFormulaDTO): Promise<ManufacturingFormulaDTO> =>
    api.put<any, ApiResponse<ManufacturingFormulaDTO>>(`/v1/manufacturing/formulas/${id}`, dto).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/v1/manufacturing/formulas/${id}`).then(() => undefined),
};
