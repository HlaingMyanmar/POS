import { api } from './api';
import { ApiResponse, ShelfLocationDTO } from '../types';

export const shelfLocationService = {
  getAll: () =>
    api.get<any, ApiResponse<ShelfLocationDTO[]>>('/v1/shelf-locations').then(r => r.data ?? []),

  getActive: () =>
    api.get<any, ApiResponse<ShelfLocationDTO[]>>('/v1/shelf-locations/active').then(r => r.data ?? []),

  create: (dto: Omit<ShelfLocationDTO, 'id'>) =>
    api.post<any, ApiResponse<ShelfLocationDTO>>('/v1/shelf-locations', dto),

  update: (id: number, dto: Partial<ShelfLocationDTO>) =>
    api.put<any, ApiResponse<ShelfLocationDTO>>(`/v1/shelf-locations/${id}`, dto),

  delete: (id: number) =>
    api.delete<any, ApiResponse<void>>(`/v1/shelf-locations/${id}`),
};
