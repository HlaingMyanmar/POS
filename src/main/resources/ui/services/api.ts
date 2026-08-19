
import axios from 'axios';
import Swal from 'sweetalert2';
import { AuthResponse, User, DashboardStats, ApiResponse, PagedData } from '../types';
import { getFromSession, saveToSession, removeFromSession } from '../utils/storageHelper';

const joinUrl = (base: string, path: string) =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

const toWsEndpointFromApiBase = (apiBase: string) => {
  const trimmed = apiBase.toString().trim();
  if (!trimmed) return '';

  // API base commonly ends with /api; derive websocket endpoint from the same origin.
  const withoutApiSuffix = trimmed.replace(/\/api\/?$/i, '');
  return joinUrl(withoutApiSuffix, 'ws-clinic');
};

const inferBackendOrigin = () => {
  if (typeof window === 'undefined') return 'http://localhost:8080';

  const protocol = window.location.protocol;
  const hostname = window.location.hostname || 'localhost';
  const backendPort = (import.meta.env.VITE_BACKEND_PORT || '8080').toString();

  return `${protocol}//${hostname}:${backendPort}`;
};

const backendOrigin = inferBackendOrigin();
const defaultApiBase = import.meta.env.DEV ? '/api' : joinUrl(backendOrigin, 'api');
const defaultWsUrl = import.meta.env.DEV ? '/ws-clinic' : joinUrl(backendOrigin, 'ws-clinic');

export const BASE_URL = (import.meta.env.VITE_API_BASE_URL || defaultApiBase).toString();
const inferredWsUrlFromApiBase = import.meta.env.VITE_API_BASE_URL
  ? toWsEndpointFromApiBase(import.meta.env.VITE_API_BASE_URL)
  : '';
export const WS_URL = (import.meta.env.VITE_WS_URL || inferredWsUrlFromApiBase || defaultWsUrl).toString();

/**
 * Access Token is stored ONLY in JS memory (variable) to prevent XSS.
 * It will be lost on page reload.
 */
let _accessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  _accessToken = token;
};

export const getAccessToken = () => _accessToken;

/**
 * Shared Axios Instance
 */
export const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' }
});

/**
 * Request Interceptor
 * Injects token from JS Memory
 */
api.interceptors.request.use(config => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

/**
 * Response Interceptor
 */
api.interceptors.response.use(
  response => response.data,
  async (error) => {
    const originalRequest = error.config;
    const errData = error.response?.data;

    // Session invalidated — another device logged in, no refresh attempt
    if (error.response?.status === 401 && errData?.error === 'SESSION_INVALIDATED') {
      authService.logout();
      Swal.fire({
        icon: 'warning',
        title: 'Session Ended',
        text: 'This account has been logged in from another device. You have been signed out.',
        confirmButtonText: 'OK'
      }).then(() => { window.location.href = '#/login'; });
      return Promise.reject(errData);
    }

    // If token expired (401) and we haven't retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        // Attempt silent refresh using the refreshToken from sessionStorage (tab-isolated)
        const refreshRes = await authService.refresh();
        if (refreshRes.success) {
          setAccessToken(refreshRes.data.accessToken);
          originalRequest.headers.Authorization = `Bearer ${refreshRes.data.accessToken}`;
          return api(originalRequest);
        }
      } catch (refreshError) {
        authService.logout();
        window.location.href = '#/login';
      }
    }

    return Promise.reject(error.response?.data || { success: false, message: error.message });
  }
);

export const authService = {
  login: async (usernameOremail: string, password: string): Promise<ApiResponse<AuthResponse>> => {
    const response = await api.post<any, ApiResponse<any>>('/v1/auth/login', { usernameOremail, password });
    if (response.success) {
      setAccessToken(response.data.accessToken);
      // We only store refreshToken and user info in sessionStorage for tab-level persistence
      saveToSession('sspd_refresh', response.data.refreshToken);
      saveToSession('sspd_user', JSON.stringify({
        username: response.data.username,
        roles: response.data.roles,
        permissions: response.data.permissions
      }));
    }
    return response;
  },

  refresh: async (): Promise<ApiResponse<AuthResponse>> => {
    const refreshToken = getFromSession('sspd_refresh');
    if (!refreshToken) throw new Error('No refresh token available');
    
    // Usually backend expects refresh token in a cookie or body
    return api.post('/v1/auth/refresh', { refreshToken });
  },

  logout: () => {
    setAccessToken(null);
    removeFromSession('sspd_refresh');
    removeFromSession('sspd_user');
    removeFromSession('sspd_token'); // Clean up old legacy keys
  }
};

// ── Company Settings ──────────────────────────────────────
export const companySettingsService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/company-settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/company-settings', dto),
};

// ── App Version Settings ───────────────────────────────────
export const appVersionSettingsService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/app-version-settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/app-version-settings', dto),
  apkExists: () => api.get<any, ApiResponse<boolean>>('/v1/app-version-settings/apk-exists'),
  uploadApk: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<FormData, ApiResponse<string>>('/v1/app-version-settings/upload-apk', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

// ── Backup ────────────────────────────────────────────────
export const backupService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/backup/settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/backup/settings', dto),
  runNow: () => api.post<any, ApiResponse<any>>('/v1/backup/run-now'),
  listBackups: () => api.get<any, ApiResponse<any[]>>('/v1/backup/list'),
  importBackup: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<FormData, ApiResponse<any>>('/v1/backup/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

// ── ServiceType ───────────────────────────────────────────
export const serviceTypeService = {
  getAll: () => api.get<any, ApiResponse<any[]>>('/v1/service-types'),
  getActive: () => api.get<any, ApiResponse<any[]>>('/v1/service-types/active'),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-types', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-types/${id}`, dto),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/service-types/${id}`),
};

// ── SubServiceType ────────────────────────────────────────
export const subServiceTypeService = {
  getByType: (typeId: number) => api.get<any, ApiResponse<any[]>>(`/v1/sub-service-types/by-type/${typeId}`),
  getActiveByType: (typeId: number) => api.get<any, ApiResponse<any[]>>(`/v1/sub-service-types/active/by-type/${typeId}`),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/sub-service-types', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/sub-service-types/${id}`, dto),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/sub-service-types/${id}`),
};

// ── ServiceItem ───────────────────────────────────────────
export const serviceItemService = {
  getAll: () => api.get<any, ApiResponse<any[]>>('/v1/services'),
  getActive: () => api.get<any, ApiResponse<any[]>>('/v1/services/active'),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/services', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/services/${id}`, dto),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/services/${id}`),
};

// ── Bookings ──────────────────────────────────────────────
export const bookingService = {
  getAll: (page = 0, size = 20, search = '', dateFrom = '', dateTo = '') => {
    const q = search ? `&search=${encodeURIComponent(search)}` : '';
    const df = dateFrom ? `&dateFrom=${dateFrom}` : '';
    const dt = dateTo ? `&dateTo=${dateTo}` : '';
    return api.get<any, ApiResponse<PagedData<any>>>(`/v1/bookings?page=${page}&size=${size}${q}${df}${dt}`);
  },
  getById: (id: number) => api.get<any, ApiResponse<any>>(`/v1/bookings/${id}`),
  getUpcoming: (mins = 60) => api.get<any, ApiResponse<any[]>>(`/v1/bookings/upcoming?minutesAhead=${mins}`),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/bookings', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/bookings/${id}`, dto),
  updateStatus: (id: number, status: string) =>
    api.patch<any, ApiResponse<any>>(`/v1/bookings/${id}/status?status=${status}`),
  convertToJob: (id: number) => api.post<any, ApiResponse<any>>(`/v1/bookings/${id}/convert-to-job`, {}),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/bookings/${id}`),
};

// ── Service Jobs ──────────────────────────────────────────
export const serviceJobService = {
  getAll: (page = 0, size = 20, search = '', dateFrom = '', dateTo = '') => {
    const q = search ? `&search=${encodeURIComponent(search)}` : '';
    const df = dateFrom ? `&dateFrom=${dateFrom}` : '';
    const dt = dateTo ? `&dateTo=${dateTo}` : '';
    return api.get<any, ApiResponse<PagedData<any>>>(`/v1/service-jobs?page=${page}&size=${size}${q}${df}${dt}`);
  },
  getById: (id: number) => api.get<any, ApiResponse<any>>(`/v1/service-jobs/${id}`),
  getByBooking: (bookingId: number) => api.get<any, ApiResponse<any[]>>(`/v1/service-jobs/by-booking/${bookingId}`),
  getByStatus: (status: string) => api.get<any, ApiResponse<any[]>>(`/v1/service-jobs/status/${status}`),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-jobs', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-jobs/${id}`, dto),
  updateStatus: (id: number, status: string) =>
    api.patch<any, ApiResponse<any>>(`/v1/service-jobs/${id}/status?status=${status}`),
  settle: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/settle`, dto),
  payDue: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/pay-due`, dto),
  deliver: (id: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/deliver`, {}),
  rework: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/rework`, dto),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/service-jobs/${id}`),
  getUsedSerialNumbers: (excludeJobId?: number) => {
    const q = excludeJobId != null ? `?excludeJobId=${excludeJobId}` : '';
    return api.get<any, ApiResponse<string[]>>(`/v1/service-jobs/used-serial-numbers${q}`);
  },
  getUnpaid: () => api.get<any, ApiResponse<any[]>>('/v1/service-jobs/unpaid'),
};

// ── Excel Export ──────────────────────────────────────────
export const exportService = {
  bookings: () => `${BASE_URL}/v1/export/bookings`,
  services: () => `${BASE_URL}/v1/export/services`,
};

export const reportService = {
  openSalePdf: (saleId: number, pos = false) =>
    openPdfBlob(pos ? `/v1/reports/sale/${saleId}/pos` : `/v1/reports/sale/${saleId}`),
  openBookingPdf: (bookingId: number) =>
    openPdfBlob(`/v1/reports/booking/${bookingId}`),
  openServiceJobPdf: (jobId: number) =>
    openPdfBlob(`/v1/reports/service-job/${jobId}`),
};

export const salesRankingService = {
  topProducts: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to)   params.set('to', to);
    return api.get<any, ApiResponse<any[]>>(`/v1/reports/sales-ranking/products?${params}`);
  },
  monthly: () => api.get<any, ApiResponse<any[]>>('/v1/reports/sales-ranking/monthly'),
};

const summaryParams = (from?: string, to?: string) => {
  const p = new URLSearchParams();
  if (from) p.set('from', from);
  if (to)   p.set('to', to);
  return p.toString();
};

export const summaryReportService = {
  sales:    (from?: string, to?: string) => api.get<any, ApiResponse<any>>(`/v1/reports/sales-summary?${summaryParams(from, to)}`),
  purchase: (from?: string, to?: string) => api.get<any, ApiResponse<any>>(`/v1/reports/purchase-summary?${summaryParams(from, to)}`),
  service:  (from?: string, to?: string) => api.get<any, ApiResponse<any>>(`/v1/reports/service-summary?${summaryParams(from, to)}`),
  daily:    (from?: string, to?: string) => api.get<any, ApiResponse<any>>(`/v1/reports/daily-summary?${summaryParams(from, to)}`),
  yearly:   (year?: number)              => api.get<any, ApiResponse<any>>(`/v1/reports/yearly-summary?year=${year ?? 0}`),
  staffPerformance: (from?: string, to?: string) => api.get<any, ApiResponse<any>>(`/v1/reports/staff/performance?${summaryParams(from, to)}`),
};

export const adminService = {
  backfillJournals: (): Promise<ApiResponse<Record<string, number>>> =>
    api.post('/v1/admin/backfill-journals'),
};

export interface SetupStatusDTO {
  complete: boolean;
  hasPaymentMethods: boolean;
  companyConfigured: boolean;
}

export interface SetupInitDTO {
  companyName: string;
  companyAddress: string;
  companyPhone: string;
  companyEmail: string;
  paymentMethods: string[];
}

export const setupService = {
  getStatus: async (): Promise<SetupStatusDTO> => {
    const res = await api.get<any, ApiResponse<SetupStatusDTO>>('/v1/setup/status');
    return res.data as SetupStatusDTO;
  },
  initialize: (dto: SetupInitDTO): Promise<ApiResponse<void>> =>
    api.post('/v1/setup/initialize', dto),
};

async function openPdfBlob(path: string): Promise<void> {
  const res = await api.get(path, { responseType: 'blob' });
  const url = URL.createObjectURL(new Blob([res.data as BlobPart], { type: 'application/pdf' }));
  const w = window.open(url, '_blank');
  if (!w) { URL.revokeObjectURL(url); return; }
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export const dashboardService = {
  getStats: async (): Promise<DashboardStats> => {
    const res = await api.get<any, ApiResponse<DashboardStats>>('/v1/dashboard/stats');
    const d = (res?.data ?? {}) as any;
    return {
      totalSales:     Number(d.totalSales)     || 0,
      totalPurchases: Number(d.totalPurchases) || 0,
      totalServices:  Number(d.totalServices)  || 0,
      totalCustomers: Number(d.totalCustomers) || 0,
      todaySalesAmount:  Number(d.todaySalesAmount)  || 0,
      todaySalesCount:   Number(d.todaySalesCount)   || 0,
      totalOverdueAR:    Number(d.totalOverdueAR)    || 0,
      overdueARCount:    Number(d.overdueARCount)    || 0,
      totalPendingAR:    Number(d.totalPendingAR)    || 0,
      pendingARCount:    Number(d.pendingARCount)    || 0,
      pendingServiceJobs: Number(d.pendingServiceJobs) || 0,
      lowStockCount:     Number(d.lowStockCount)     || 0,
      lowStockProducts:  Array.isArray(d.lowStockProducts) ? d.lowStockProducts : [],
      hasJournalEntries: Boolean(d.hasJournalEntries),
      recentSales: Array.isArray(d.recentSales)
        ? d.recentSales.map((s: any) => ({
            id:           Number(s.id)           || 0,
            saleCode:     String(s.saleCode      || `#${s.id}`),
            customerName: String(s.customerName  || 'Unknown'),
            amount:       Number(s.amount)       || 0,
            date:         String(s.date          || ''),
            status:       s.status               || 'Pending',
          }))
        : [],
    };
  }
};
