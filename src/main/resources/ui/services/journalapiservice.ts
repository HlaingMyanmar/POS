import { api } from './api';
import { ApiResponse, JournalEntryDTO } from '../types';

export const journalApiService = {
  getAll: async (): Promise<JournalEntryDTO[]> => {
    const res = await api.get<any, ApiResponse<JournalEntryDTO[]>>('/v1/journal-entries');
    return res.data;
  },
  
  getById: async (id: number): Promise<JournalEntryDTO> => {
    const res = await api.get<any, ApiResponse<JournalEntryDTO>>(`/v1/journal-entries/${id}`);
    return res.data;
  },

  create: async (data: JournalEntryDTO): Promise<JournalEntryDTO> => {
    const res = await api.post<any, ApiResponse<JournalEntryDTO>>('/v1/journal-entries', data);
    return res.data;
  }
};
