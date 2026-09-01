import { api } from './api';
import { ApiResponse, VideoAppType, VideoAudience, VideoDTO } from '../types';

export interface VideoListFilters {
  audience?: VideoAudience | '';
  category?: string;
  active?: boolean | '';
}

export interface VideoArrangementFilters {
  appType: VideoAppType;
  category?: string;
  active?: boolean | '';
}

export interface VideoArrangementItem {
  videoId: number;
  featured: boolean;
}

export const videoService = {
  getAll: async (filters: VideoListFilters = {}): Promise<VideoDTO[]> => {
    const params: Record<string, string> = {};
    if (filters.audience) params.audience = filters.audience;
    if (filters.category) params.category = filters.category;
    if (filters.active === true || filters.active === false) params.active = String(filters.active);
    const res = await api.get<any, ApiResponse<VideoDTO[]>>('/v1/videos', { params });
    return res.data;
  },

  getArrangement: async (filters: VideoArrangementFilters): Promise<VideoDTO[]> => {
    const params: Record<string, string> = { appType: filters.appType };
    if (filters.category) params.category = filters.category;
    if (filters.active === true || filters.active === false) params.active = String(filters.active);
    const res = await api.get<any, ApiResponse<VideoDTO[]>>('/v1/videos/arrangement', { params });
    return res.data;
  },

  saveArrangement: async (appType: VideoAppType, items: VideoArrangementItem[], filters: Omit<VideoArrangementFilters, 'appType'> = {}): Promise<VideoDTO[]> => {
    const params: Record<string, string> = {};
    if (filters.category) params.category = filters.category;
    if (filters.active === true || filters.active === false) params.active = String(filters.active);
    const res = await api.put<any, ApiResponse<VideoDTO[]>>(`/v1/videos/arrangement/${appType}`, { items }, { params });
    return res.data;
  },

  create: (video: Omit<VideoDTO, 'id'>) =>
    api.post<any, ApiResponse<VideoDTO>>('/v1/videos', video).then((res: any) => res.data),

  update: (id: number, video: Partial<VideoDTO>) =>
    api.put<any, ApiResponse<VideoDTO>>(`/v1/videos/${id}`, video).then((res: any) => res.data),

  delete: (id: number) =>
    api.delete<any, ApiResponse<void>>(`/v1/videos/${id}`).then((res: any) => res.data)
};
