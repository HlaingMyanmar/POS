
import { api } from './api';
import { RoleDTO, ApiResponse } from '../types';

export const roleService = {
  getAll: async (): Promise<RoleDTO[]> => {
    const response = await api.get<any, ApiResponse<RoleDTO[]>>('/v1/roles');
    return response.data;
  },

  getById: (id: number) => 
    api.get<any, ApiResponse<RoleDTO>>(`/v1/roles/${id}`).then((res: any) => res.data),

  create: (role: Partial<RoleDTO>) => 
    api.post<any, ApiResponse<RoleDTO>>('/v1/roles', role).then((res: any) => res.data),

  update: (id: number, role: Partial<RoleDTO>) => 
    api.put<any, ApiResponse<RoleDTO>>(`/v1/roles/${id}`, role).then((res: any) => res.data),

  delete: (id: number) => 
    api.delete<any, ApiResponse<void>>(`/v1/roles/${id}`).then((res: any) => res.data),

  assignPermissions: (roleId: number, permissionIds: number[]) => 
    api.put<any, ApiResponse<void>>(`/v1/roles/${roleId}/permissions`, permissionIds).then((res: any) => res.data)
};
