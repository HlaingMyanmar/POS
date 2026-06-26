
import { api } from './api';
import { UserDTO, ApiResponse } from '../types';

export const userService = {
  getAll: async (): Promise<UserDTO[]> => {
    const response = await api.get<any, ApiResponse<UserDTO[]>>('/v1/user');
    return response.data;
  },

  getById: (id: number) => 
    api.get<any, ApiResponse<UserDTO>>(`/v1/user/${id}`).then((res: any) => res.data),

  create: (user: Partial<UserDTO & { password?: string }>) => 
    api.post<any, ApiResponse<UserDTO>>('/v1/user', user).then((res: any) => res.data),

  update: (id: number, user: Partial<UserDTO>) => 
    api.put<any, ApiResponse<UserDTO>>(`/v1/user/${id}`, user).then((res: any) => res.data),

  delete: (id: number) => 
    api.delete<any, ApiResponse<void>>(`/v1/user/${id}`).then((res: any) => res.data),

  assignRoles: (userId: number, roleIds: number[]) => 
    api.put<any, ApiResponse<void>>(`/v1/user/${userId}/role`, roleIds).then((res: any) => res.data)
};
