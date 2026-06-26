
import { api } from './api';
import { PermissionDTO, ApiResponse } from '../types';

export const permissionService = {
  getAll: async (): Promise<PermissionDTO[]> => {
    const res = await api.get<any, ApiResponse<PermissionDTO[]>>('/v1/permissions');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<PermissionDTO>>(`/v1/permissions/${id}`).then((res: any) => res.data),
  
  create: (permission: Omit<PermissionDTO, 'id'>) => 
    api.post<any, ApiResponse<PermissionDTO>>('/v1/permissions', permission).then((res: any) => res.data),
  
  update: (id: number, permission: Partial<PermissionDTO>) => 
    api.put<any, ApiResponse<PermissionDTO>>(`/v1/permissions/${id}`, permission).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/permissions/${id}`).then((res: any) => res.data)
};
