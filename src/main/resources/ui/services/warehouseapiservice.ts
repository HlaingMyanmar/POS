import { api } from './api';
import { ApiResponse } from '../types';

export interface WarehouseDTO {
  id?: number;
  code?: string;
  name: string;
  address?: string;
  active?: boolean;
}

export interface WarehouseTransferDTO {
  id?: number;
  transferNo?: string;
  productId: number;
  productName?: string;
  fromWarehouseId: number;
  fromWarehouseName?: string;
  toWarehouseId: number;
  toWarehouseName?: string;
  qty: number;
  transferredAt?: string;
  transferredBy?: string;
  remark?: string;
}

export const warehouseApiService = {
  list: async (activeOnly = true): Promise<WarehouseDTO[]> => {
    const res = await api.get<any, ApiResponse<WarehouseDTO[]>>(`/v1/warehouses?activeOnly=${activeOnly}`);
    return res.data ?? [];
  },
  create: async (dto: WarehouseDTO): Promise<WarehouseDTO> => {
    const res = await api.post<any, ApiResponse<WarehouseDTO>>('/v1/warehouses', dto);
    return res.data;
  },
  update: async (id: number, dto: WarehouseDTO): Promise<WarehouseDTO> => {
    const res = await api.put<any, ApiResponse<WarehouseDTO>>(`/v1/warehouses/${id}`, dto);
    return res.data;
  },
  transfer: async (dto: WarehouseTransferDTO): Promise<WarehouseTransferDTO> => {
    const res = await api.post<any, ApiResponse<WarehouseTransferDTO>>('/v1/warehouses/transfers', dto);
    return res.data;
  },
  transferHistory: async (): Promise<WarehouseTransferDTO[]> => {
    const res = await api.get<any, ApiResponse<WarehouseTransferDTO[]>>('/v1/warehouses/transfers');
    return res.data ?? [];
  }
};
