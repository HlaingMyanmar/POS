
import { api } from './api';
import { ApiResponse, ManufacturingOrderDTO } from '../types';

export const manufacturingService = {
  getAll: (): Promise<ManufacturingOrderDTO[]> =>
    api.get<any, ApiResponse<ManufacturingOrderDTO[]>>('/v1/manufacturing').then(r => r.data),

  getById: (id: number): Promise<ManufacturingOrderDTO> =>
    api.get<any, ApiResponse<ManufacturingOrderDTO>>(`/v1/manufacturing/${id}`).then(r => r.data),

  create: (dto: ManufacturingOrderDTO): Promise<ManufacturingOrderDTO> =>
    api.post<any, ApiResponse<ManufacturingOrderDTO>>('/v1/manufacturing', dto).then(r => r.data),

  update: (id: number, dto: ManufacturingOrderDTO): Promise<ManufacturingOrderDTO> =>
    api.put<any, ApiResponse<ManufacturingOrderDTO>>(`/v1/manufacturing/${id}`, dto).then(r => r.data),

  complete: (id: number): Promise<ManufacturingOrderDTO> =>
    api.post<any, ApiResponse<ManufacturingOrderDTO>>(`/v1/manufacturing/${id}/complete`).then(r => r.data),

  cancel: (id: number): Promise<ManufacturingOrderDTO> =>
    api.post<any, ApiResponse<ManufacturingOrderDTO>>(`/v1/manufacturing/${id}/cancel`).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/v1/manufacturing/${id}`).then(() => undefined),
};
