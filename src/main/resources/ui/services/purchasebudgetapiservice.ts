import {api} from './api'; import {ApiResponse} from '../types';
export interface PurchaseBudgetDTO {id?:number;name:string;dateFrom:string;dateTo:string;categoryId?:number;categoryName?:string;limitAmount:number;enforcement:'WARN'|'BLOCK';active?:boolean;spentAmount?:number;remainingAmount?:number;usagePercent?:number}
export const purchaseBudgetApiService={
 list:async()=>{const r=await api.get<any,ApiResponse<PurchaseBudgetDTO[]>>('/v1/purchase-budgets');return r.data||[]},
 save:async(d:PurchaseBudgetDTO)=>{const r=await api.post<any,ApiResponse<PurchaseBudgetDTO>>('/v1/purchase-budgets',d);return r.data},
 active:async(id:number,value:boolean)=>{const r=await api.post<any,ApiResponse<PurchaseBudgetDTO>>(`/v1/purchase-budgets/${id}/active?value=${value}`,{});return r.data},
 remove:async(id:number)=>{await api.delete<any,ApiResponse<void>>(`/v1/purchase-budgets/${id}`)}
};
