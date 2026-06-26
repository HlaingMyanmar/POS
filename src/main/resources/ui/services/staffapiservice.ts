
import { api } from './api';
import { StaffDTO, ApiResponse } from '../types';

export const staffService = {
  getAll: async (): Promise<StaffDTO[]> => {
    const res = await api.get<any, ApiResponse<StaffDTO[]>>('/v1/staffs');
    return res.data;
  },

  getAllActive: async (): Promise<StaffDTO[]> => {
    const res = await api.get<any, ApiResponse<StaffDTO[]>>('/v1/staffs/active');
    return res.data;
  },

  getById: (id: number) => 
    api.get<any, ApiResponse<StaffDTO>>(`/v1/staffs/${id}`).then((res: any) => res.data),
  
  create: (staff: Partial<StaffDTO>) => 
    api.post<any, ApiResponse<StaffDTO>>('/v1/staffs', staff).then((res: any) => res.data),
  
  update: (id: number, staff: Partial<StaffDTO>) => 
    api.put<any, ApiResponse<StaffDTO>>(`/v1/staffs/${id}`, staff).then((res: any) => res.data),
  
  delete: (id: number) => 
    api.delete<any, ApiResponse<void>>(`/v1/staffs/${id}`).then((res: any) => res.data)
};
