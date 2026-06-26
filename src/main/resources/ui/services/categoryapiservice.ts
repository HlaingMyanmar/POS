
import { api } from './api';
import { CategoryDTO, ApiResponse } from '../types';

export const categoryService = {
  getAll: async (): Promise<CategoryDTO[]> => {
    const res = await api.get<any, ApiResponse<CategoryDTO[]>>('/v1/category');
    return res.data;
  },

  getTree: async (): Promise<CategoryDTO[]> => {
    const res = await api.get<any, ApiResponse<CategoryDTO[]>>('/v1/category/tree');
    return res.data;
  },

  getById: (id: number) => api.get<any, ApiResponse<CategoryDTO>>(`/v1/category/${id}`).then((res: any) => res.data),
  
  create: (category: Partial<CategoryDTO>) => 
    api.post<any, ApiResponse<CategoryDTO>>('/v1/category', category).then((res: any) => res.data),
  
  update: (id: number, category: Partial<CategoryDTO>) => 
    api.put<any, ApiResponse<CategoryDTO>>(`/v1/category/${id}`, category).then((res: any) => res.data),
  
  delete: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/category/${id}`).then((res: any) => res.data)
};
