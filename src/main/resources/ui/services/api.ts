import axios from 'axios';
import Swal from 'sweetalert2';
import { AuthResponse, User, DashboardStats, ApiResponse, PagedData } from '../types';
import { saveToSession, removeFromSession } from '../utils/storageHelper';

const joinUrl = (base: string, path: string) =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

const toWsEndpointFromApiBase = (apiBase: string) => {
  const trimmed = apiBase.toString().trim();
  if (!trimmed) return '';

  // API base commonly ends with /api; derive websocket endpoint from the same origin.
  const withoutApiSuffix = trimmed.replace(/\/api\/?$/i, '');
  return joinUrl(withoutApiSuffix, 'ws-clinic');
};

const defaultApiBase = '/api';
const defaultWsUrl = '/ws-clinic';

export const BASE_URL = (import.meta.env.VITE_API_BASE_URL || defaultApiBase).toString();
const inferredWsUrlFromApiBase = import.meta.env.VITE_API_BASE_URL
  ? toWsEndpointFromApiBase(import.meta.env.VITE_API_BASE_URL)
  : '';
export const WS_URL = (import.meta.env.VITE_WS_URL || inferredWsUrlFromApiBase || defaultWsUrl).toString();

export const resolveAssetUrl = (value?: string | null) => {
  if (!value || value.startsWith('data:') || /^https?:\/\//i.test(value)) return value || '';
  try {
    const apiOrigin = new URL(BASE_URL, window.location.origin).origin;
    return new URL(value, apiOrigin).toString();
  } catch {
    return value;
  }
};

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
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'X-Client-Type': 'web',
  }
});

let refreshInFlight: Promise<ApiResponse<AuthResponse>> | null = null;

const isServiceJobRequest = (url?: string) => /\/v1\/service-jobs(?:\/|$)/i.test(String(url || ''));

/**
 * Request Interceptor
 * Injects token from JS Memory
 */
api.interceptors.request.use(async config => {
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    const headers = config.headers;
    if (headers && typeof headers.delete === 'function') {
      headers.delete('Content-Type');
    } else if (headers) {
      delete headers['Content-Type'];
      delete headers['content-type'];
    }
  }
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  if (needsStepUp(config.method, config.url) && !(config as any)._stepUpAttached) {
    const token = await ensureStepUpToken();
    if (!token) {
      return Promise.reject({
        success: false,
        message: 'Re-authentication cancelled',
        error: 'STEP_UP_REQUIRED',
      });
    }
    config.headers['X-Step-Up-Token'] = token;
    (config as any)._stepUpAttached = true;
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
      void authService.logout({ forceClearLocal: true }).finally(() => {
        Swal.fire({
          icon: 'warning',
          title: 'Session Ended',
          text: 'This account has been logged in from another device. You have been signed out.',
          confirmButtonText: 'OK'
        }).then(() => { window.location.href = '/pos/login'; });
      });
      return Promise.reject(errData);
    }

    if (error.response?.status === 401 && errData?.error === 'SESSION_ABSOLUTE') {
      clearStepUpToken();
      void authService.logout({ forceClearLocal: true }).finally(() => {
        Swal.fire({
          icon: 'info',
          title: 'Shift ended',
          text: 'Maximum session time reached. Please sign in again.',
          confirmButtonText: 'OK',
        }).then(() => { window.location.href = '/pos/login'; });
      });
      return Promise.reject(errData);
    }

    if (error.response?.status === 401 && errData?.error === 'SESSION_IDLE') {
      setAccessToken(null);
      clearStepUpToken();
      dispatchSessionLock();
      return Promise.reject(errData);
    }

    if (
      error.response?.status === 401
      && (errData?.error === 'STEP_UP_REQUIRED' || errData?.error === 'STEP_UP_INVALID')
      && originalRequest
      && !(originalRequest as any)._stepUpRetry
    ) {
      clearStepUpToken();
      (originalRequest as any)._stepUpRetry = true;
      try {
        const token = await ensureStepUpToken();
        if (token) {
          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers['X-Step-Up-Token'] = token;
          (originalRequest as any)._stepUpAttached = true;
          return api(originalRequest);
        }
      } catch {
        // fall through
      }
      return Promise.reject(errData || { success: false, message: 'Re-authentication required', error: 'STEP_UP_REQUIRED' });
    }

    const requestUrl = String(originalRequest?.url || '');
    const isPublicSetup = requestUrl.includes('/v1/setup/');
    const isAuthRequest = requestUrl.includes('/v1/auth/login')
      || requestUrl.includes('/v1/auth/refresh')
      || requestUrl.includes('/v1/auth/unlock');

    // If token expired (401) and we haven't retried yet
    if (!isPublicSetup && !isAuthRequest && error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        // Silent refresh via HttpOnly cookie (withCredentials)
        const refreshRes = await authService.refresh();
        if (refreshRes.success) {
          setAccessToken(refreshRes.data.accessToken);
          originalRequest.headers.Authorization = `Bearer ${refreshRes.data.accessToken}`;
          return api(originalRequest);
        }
      } catch (refreshError: any) {
        const refreshErr = refreshError?.response?.data || refreshError;
        if (refreshErr?.error === 'SESSION_IDLE') {
          setAccessToken(null);
          clearStepUpToken();
          dispatchSessionLock();
          return Promise.reject(refreshErr);
        }
        if (refreshErr?.error === 'SESSION_ABSOLUTE') {
          clearStepUpToken();
          await authService.logout({ forceClearLocal: true });
          window.location.href = '/pos/login';
          return Promise.reject(refreshErr);
        }
        await authService.logout({ forceClearLocal: true });
        window.location.href = '/pos/login';
      }
    }

    const responseData = error.response?.data;
    if (responseData && typeof responseData === 'object') {
      const validationMessage = !responseData.message
        ? Object.values(responseData).find(value => typeof value === 'string')
        : undefined;
      const normalizedError = {
        ...responseData,
        message: responseData.message || validationMessage || error.message || 'Operation failed',
        globalAlertShown: isServiceJobRequest(originalRequest?.url),
      };
      if (normalizedError.globalAlertShown) {
        void Swal.fire({
          icon: 'error',
          title: 'Service Job အမှား',
          text: normalizedError.message,
          confirmButtonText: 'OK',
        });
      }
      return Promise.reject(normalizedError);
    }
    const normalizedError = {
      success: false,
      message: error.message || 'Operation failed',
      globalAlertShown: isServiceJobRequest(originalRequest?.url),
    };
    if (normalizedError.globalAlertShown) {
      void Swal.fire({
        icon: 'error',
        title: 'Service Job အမှား',
        text: normalizedError.message,
        confirmButtonText: 'OK',
      });
    }
    return Promise.reject(normalizedError);
  }
);

export const SESSION_USER_EVENT = 'sspd:session-user';
export const SESSION_LOCK_EVENT = 'sspd:session-lock';
export const SESSION_UNLOCK_EVENT = 'sspd:session-unlock';

/** Client idle hint (ms) — server enforces the same via refresh/unlock (default 20m). */
export const CLIENT_IDLE_TIMEOUT_MS = 20 * 60 * 1000;

let _stepUpToken: string | null = null;
let _stepUpExpiresAt = 0;
let _stepUpPromptInFlight: Promise<string | null> | null = null;
let sessionLocked = false;

export const isSessionLocked = () => sessionLocked;

export const dispatchSessionLock = () => {
  sessionLocked = true;
  window.dispatchEvent(new CustomEvent(SESSION_LOCK_EVENT));
};

export const dispatchSessionUnlock = () => {
  sessionLocked = false;
  window.dispatchEvent(new CustomEvent(SESSION_UNLOCK_EVENT));
};

const clearStepUpToken = () => {
  _stepUpToken = null;
  _stepUpExpiresAt = 0;
};

const promptStepUpPassword = async (): Promise<string | null> => {
  const result = await Swal.fire({
    title: 'Confirm identity',
    text: 'This action requires re-entering your password.',
    input: 'password',
    inputPlaceholder: 'Password',
    showCancelButton: true,
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    inputAttributes: { autocapitalize: 'off', autocomplete: 'current-password' },
  });
  if (!result.isConfirmed) return null;
  const password = String(result.value || '');
  return password.trim() ? password : null;
};

const fetchStepUpToken = async (password: string): Promise<string> => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Client-Type': 'web',
  };
  if (_accessToken) {
    headers.Authorization = `Bearer ${_accessToken}`;
  }
  const response = await axios.post<ApiResponse<{ stepUpToken: string; expiresInSeconds: number }>>(
    joinUrl(BASE_URL, '/v1/auth/step-up'),
    { password },
    { withCredentials: true, headers },
  );
  const body = response.data;
  if (!body?.success || !body.data?.stepUpToken) {
    throw new Error(body?.message || 'Step-up failed');
  }
  _stepUpToken = body.data.stepUpToken;
  const ttlSec = Number(body.data.expiresInSeconds) || 300;
  _stepUpExpiresAt = Date.now() + Math.max(30, ttlSec - 15) * 1000;
  return _stepUpToken;
};

/** Prompts for password and caches a short-lived step-up token. */
export const ensureStepUpToken = async (): Promise<string | null> => {
  if (_stepUpToken && Date.now() < _stepUpExpiresAt) {
    return _stepUpToken;
  }
  if (_stepUpPromptInFlight) {
    return _stepUpPromptInFlight;
  }
  _stepUpPromptInFlight = (async () => {
    const password = await promptStepUpPassword();
    if (!password) return null;
    return fetchStepUpToken(password);
  })().finally(() => {
    _stepUpPromptInFlight = null;
  });
  return _stepUpPromptInFlight;
};

const needsStepUp = (method?: string, url?: string): boolean => {
  const m = String(method || 'get').toUpperCase();
  const path = String(url || '').replace(/^.*\/api/, '/api').split('?')[0];
  // Paths are relative to BASE_URL (/api) so often start with /v1/...
  const p = path.startsWith('/v1/') ? `/api${path}` : path.startsWith('/api/') ? path : `/api${path.startsWith('/') ? path : `/${path}`}`;

  if (m === 'DELETE' && /^\/api\/v1\/sales\/\d+$/.test(p)) return true;
  if (m === 'POST' && p === '/api/v1/sale-returns') return true;
  if (m === 'DELETE' && /^\/api\/v1\/sale-returns\/\d+$/.test(p)) return true;
  if (m === 'POST' && /^\/api\/v1\/sale-returns\/\d+\/void$/.test(p)) return true;
  if ((m === 'PUT' || m === 'DELETE') && /^\/api\/v1\/user\/\d+\/role(?:\/\d+)?$/.test(p)) return true;
  if ((m === 'POST' || m === 'PUT' || m === 'DELETE') && /^\/api\/v1\/roles(?:\/|$)/.test(p)) return true;
  if ((m === 'POST' || m === 'PUT' || m === 'DELETE') && /^\/api\/v1\/permissions(?:\/|$)/.test(p)) return true;
  return false;
};

export const userFromAuthResponse = (data: AuthResponse): User => ({
  username: data.username,
  name: data.name,
  phone: data.phone,
  staffId: data.staffId,
  roles: Array.isArray(data.roles) ? data.roles : [],
  permissions: Array.isArray(data.permissions) ? data.permissions : [],
});

const persistSessionUser = (data: AuthResponse) => {
  const user = userFromAuthResponse(data);
  saveToSession('sspd_user', JSON.stringify(user));
  // Notify App/Layout so menus & guards pick up role/permission changes mid-session.
  window.dispatchEvent(new CustomEvent<User>(SESSION_USER_EVENT, { detail: user }));
};

const clearLocalAuthSession = () => {
  setAccessToken(null);
  clearStepUpToken();
  sessionLocked = false;
  removeFromSession('sspd_refresh');
  removeFromSession('sspd_user');
  removeFromSession('sspd_token'); // Clean up old legacy keys
};

const postLogout = async (): Promise<void> => {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (_accessToken) {
    headers.Authorization = `Bearer ${_accessToken}`;
  }
  // Cookie-only logout for web (server clears HttpOnly refresh cookie).
  await axios.post(
    joinUrl(BASE_URL, '/v1/auth/logout'),
    {},
    { withCredentials: true, headers: { ...headers, 'X-Client-Type': 'web' } },
  );
};

export type LogoutOptions = {
  /** Always wipe local session (forced sign-out / session invalid). */
  forceClearLocal?: boolean;
};

export const authService = {
  login: async (usernameOremail: string, password: string): Promise<ApiResponse<AuthResponse>> => {
    const response = await api.post<any, ApiResponse<any>>('/v1/auth/login', { usernameOremail, password });
    if (response.success) {
      setAccessToken(response.data.accessToken);
      // Web refresh token is HttpOnly cookie-only (not in JSON body / not in sessionStorage).
      removeFromSession('sspd_refresh');
      persistSessionUser(response.data);
    }
    return response;
  },

  refresh: async (): Promise<ApiResponse<AuthResponse>> => {
    if (!refreshInFlight) {
      // Empty body → server uses HttpOnly refresh cookie; no refresh token in JS.
      refreshInFlight = axios
        .post<ApiResponse<AuthResponse>>(
          joinUrl(BASE_URL, '/v1/auth/refresh'),
          {},
          {
            withCredentials: true,
            headers: { 'Content-Type': 'application/json', 'X-Client-Type': 'web' },
          },
        )
        .then(response => {
          const result = response.data;
          if (result.success && result.data?.accessToken) {
            setAccessToken(result.data.accessToken);
            // Keep UI roles/permissions in sync after admin role changes.
            persistSessionUser(result.data);
          }
          return result;
        })
        .catch(error => {
          const errData = error?.response?.data;
          if (errData?.error === 'SESSION_IDLE' || errData?.error === 'SESSION_ABSOLUTE') {
            throw errData;
          }
          throw error;
        })
        .finally(() => {
          refreshInFlight = null;
        });
    }
    return refreshInFlight;
  },

  unlock: async (password: string): Promise<ApiResponse<AuthResponse>> => {
    const response = await axios.post<ApiResponse<AuthResponse>>(
      joinUrl(BASE_URL, '/v1/auth/unlock'),
      { password },
      {
        withCredentials: true,
        headers: { 'Content-Type': 'application/json', 'X-Client-Type': 'web' },
      },
    );
    const result = response.data;
    if (result.success && result.data?.accessToken) {
      setAccessToken(result.data.accessToken);
      persistSessionUser(result.data);
      dispatchSessionUnlock();
    }
    return result;
  },

  clearLocalSession: clearLocalAuthSession,

  /**
   * Revokes the server refresh session, then clears local auth state.
   * On network/server failure (user-initiated): offers Retry / local-only / stay signed in.
   * Forced paths always clear local state so the UI cannot keep a dead session.
   * @returns true when the local session was cleared
   */
  logout: async (options: LogoutOptions = {}): Promise<boolean> => {
    const { forceClearLocal = false } = options;

    try {
      await postLogout();
      clearLocalAuthSession();
      return true;
    } catch {
      if (forceClearLocal) {
        clearLocalAuthSession();
        return true;
      }

      const decision = await Swal.fire({
        icon: 'warning',
        title: 'Logout incomplete',
        text: 'Could not reach the server to end your session. The refresh token may still be active until logout succeeds.',
        showDenyButton: true,
        showCancelButton: true,
        confirmButtonText: 'Retry',
        denyButtonText: 'Sign out locally',
        cancelButtonText: 'Stay signed in',
      });

      if (decision.isConfirmed) {
        return authService.logout(options);
      }
      if (decision.isDenied) {
        clearLocalAuthSession();
        return true;
      }
      return false;
    }
  }
};

// ── Company Settings ──────────────────────────────────────
export const companySettingsService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/company-settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/company-settings', dto),
  testMail: (to: string) =>
    api.post<any, ApiResponse<void>>('/v1/company-settings/test-mail', { to }),
};

// ── Service & Booking Settings ─────────────────────────────
export const serviceBookingSettingsService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/service-booking-settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-booking-settings', dto),
  listWeekdayHours: () => api.get<any, ApiResponse<any[]>>('/v1/service-booking-settings/weekday-hours'),
  createWeekdayHours: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-booking-settings/weekday-hours', dto),
  updateWeekdayHours: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-booking-settings/weekday-hours/${id}`, dto),
  deleteWeekdayHours: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/service-booking-settings/weekday-hours/${id}`),
  listArrivalWindows: () => api.get<any, ApiResponse<any[]>>('/v1/service-booking-settings/arrival-windows'),
  createArrivalWindow: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-booking-settings/arrival-windows', dto),
  updateArrivalWindow: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-booking-settings/arrival-windows/${id}`, dto),
  deleteArrivalWindow: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/service-booking-settings/arrival-windows/${id}`),
  listDateExceptions: () => api.get<any, ApiResponse<any[]>>('/v1/service-booking-settings/date-exceptions'),
  createDateException: (dto: any) => api.post<any, ApiResponse<any>>('/v1/service-booking-settings/date-exceptions', dto),
  updateDateException: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-booking-settings/date-exceptions/${id}`, dto),
  deleteDateException: (id: number) => api.delete<any, ApiResponse<void>>(`/v1/service-booking-settings/date-exceptions/${id}`),
};

export type CustomerChatConversation = { customerId: number; customerName: string; phone?: string | null; lastMessage: string; lastAt: string; lastFromCustomer: boolean };
export type StaffChatMessage = { id: number; customerId: number; senderName?: string; senderRole?: string; content: string; sentAt: string };
export const customerChatService = {
  conversations: () => api.get<any, ApiResponse<CustomerChatConversation[]>>('/v1/chat/conversations'),
  messages: (customerId: number) => api.get<any, ApiResponse<StaffChatMessage[]>>(`/v1/chat/customers/${customerId}/messages`),
  send: (customerId: number, content: string) => api.post<any, ApiResponse<StaffChatMessage>>('/v1/chat/send', { customerId, content }),
};

// ── App Version Settings ───────────────────────────────────
export const appVersionSettingsService = {
  getSettings: () => api.get<any, ApiResponse<any>>('/v1/app-version-settings'),
  saveSettings: (dto: any) => api.post<any, ApiResponse<any>>('/v1/app-version-settings', dto),
  apkExists: () => api.get<any, ApiResponse<boolean>>('/v1/app-version-settings/apk-exists'),
  technicianApkExists: () => api.get<any, ApiResponse<boolean>>('/v1/app-version-settings/technician-apk-exists'),
  customerApkExists: () => api.get<any, ApiResponse<boolean>>('/v1/app-version-settings/customer-apk-exists'),
  uploadApk: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    // Let the browser generate the multipart boundary. Supplying a
    // Content-Type manually can result in a request without its boundary.
    return api.post<FormData, ApiResponse<string>>('/v1/app-version-settings/upload-apk', formData);
  },
  uploadTechnicianApk: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<FormData, ApiResponse<string>>('/v1/app-version-settings/upload-technician-apk', formData);
  },
  uploadCustomerApk: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post<FormData, ApiResponse<string>>('/v1/app-version-settings/upload-customer-apk', formData);
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
    return api.post<FormData, ApiResponse<any>>('/v1/backup/import', formData);
  },
};

export const adminQueryService = {
  status: () => api.get<any, ApiResponse<{ enabled: boolean }>>('/v1/admin-queries/status'),
  list: () => api.get<any, ApiResponse<any[]>>('/v1/admin-queries'),
  run: (id: string) => api.post<any, ApiResponse<any>>(`/v1/admin-queries/${encodeURIComponent(id)}/run`),
  execute: (sql: string, mode: 'READ' | 'WRITE') =>
    api.post<any, ApiResponse<any>>('/v1/admin-queries/execute', { sql, mode }),
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
  getPriceHistory: (id: number) => api.get<any, ApiResponse<any[]>>(`/v1/services/${id}/price-history`),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/services', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/services/${id}`, dto),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/services/${id}`),
};

// ── Bookings ──────────────────────────────────────────────
export const bookingService = {
  getAll: (
    page = 0,
    size = 20,
    search = '',
    dateFrom = '',
    dateTo = '',
    status = '',
    customerId: number | string | null = null,
    source = '',
  ) => {
    const q = search ? `&search=${encodeURIComponent(search)}` : '';
    const df = dateFrom ? `&dateFrom=${dateFrom}` : '';
    const dt = dateTo ? `&dateTo=${dateTo}` : '';
    const st = status ? `&status=${encodeURIComponent(status)}` : '';
    const cid = customerId != null && customerId !== '' ? `&customerId=${customerId}` : '';
    const src = source ? `&source=${encodeURIComponent(source)}` : '';
    return api.get<any, ApiResponse<PagedData<any>>>(`/v1/bookings?page=${page}&size=${size}${q}${df}${dt}${st}${cid}${src}`);
  },
  filterSummary: (
    search = '',
    dateFrom = '',
    dateTo = '',
    status = '',
    customerId: number | string | null = null,
    source = '',
  ) => {
    const params = new URLSearchParams();
    if (search) params.set('search', search);
    if (dateFrom) params.set('dateFrom', dateFrom);
    if (dateTo) params.set('dateTo', dateTo);
    if (status) params.set('status', status);
    if (customerId != null && customerId !== '') params.set('customerId', String(customerId));
    if (source) params.set('source', source);
    const qs = params.toString();
    return api.get<any, ApiResponse<any>>(`/v1/bookings/filter-summary${qs ? `?${qs}` : ''}`);
  },
  getById: (id: number) => api.get<any, ApiResponse<any>>(`/v1/bookings/${id}`),
  create: (dto: any) => api.post<any, ApiResponse<any>>('/v1/bookings', dto),
  update: (id: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/bookings/${id}`, dto),
  updateStatus: (id: number, status: string) =>
    api.patch<any, ApiResponse<any>>(`/v1/bookings/${id}/status?status=${status}`),
  reject: (id: number, reason: string) =>
    api.post<any, ApiResponse<any>>(`/v1/bookings/${id}/reject`, { reason }),
  addItems: (id: number, items: any[]) => api.post<any, ApiResponse<any>>(`/v1/bookings/${id}/items`, items),
  updateItem: (id: number, itemId: number, item: any) =>
    api.put<any, ApiResponse<any>>(`/v1/bookings/${id}/items/${itemId}`, item),
  removeItem: (id: number, itemId: number) => api.delete<any, ApiResponse<any>>(`/v1/bookings/${id}/items/${itemId}`),
  convertOutdoor: (id: number) => api.post<any, ApiResponse<any>>(`/v1/bookings/${id}/convert-outdoor`, {}),
  convertIndoor: (id: number) => api.post<any, ApiResponse<any>>(`/v1/bookings/${id}/convert-indoor`, {}),
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
  updateStatus: (id: number, status: string, holdReason?: string) =>
    api.patch<any, ApiResponse<any>>(`/v1/service-jobs/${id}/status?status=${status}${holdReason ? `&holdReason=${encodeURIComponent(holdReason)}` : ''}`),
  settle: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/settle`, dto),
  payDue: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/pay-due`, dto),
  deliver: (id: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/deliver`, {}),
  approveDueDelivery: (id: number, reason: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/approve-due-delivery`, { reason }),
  rework: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/rework`, dto),
  voidSettlement: (id: number, reason: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/void`, { reason }),
  approveEstimate: (id: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/approve-estimate`, {}),
  holdEstimate: (id: number, reason?: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/hold-estimate`, { reason }),
  rejectEstimate: (id: number, reason?: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/reject-estimate`, { reason }),
  approveFinal: (id: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/approve-final`, {}),
  leadFinalCheck: (id: number, note?: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/lead-final-check`, { note }),
  returnFinal: (id: number, reason: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/return-final`, { reason }),
  notify: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/notify`, dto),
  addAttachment: (id: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${id}/attachments`, dto),
  removeAttachment: (id: number, attachmentId: number) => api.delete<any, ApiResponse<any>>(`/v1/service-jobs/${id}/attachments/${attachmentId}`),
  getOverdue: () => api.get<any, ApiResponse<any[]>>('/v1/service-jobs/overdue'),
  remove: (id: number) => api.delete<any, ApiResponse<any>>(`/v1/service-jobs/${id}`),
  getUsedSerialNumbers: (excludeJobId?: number) => {
    const q = excludeJobId != null ? `?excludeJobId=${excludeJobId}` : '';
    return api.get<any, ApiResponse<string[]>>(`/v1/service-jobs/used-serial-numbers${q}`);
  },
  getUnpaid: () => api.get<any, ApiResponse<any[]>>('/v1/service-jobs/unpaid'),
  getPendingHandovers: () => api.get<any, ApiResponse<any[]>>('/v1/service-jobs/pending-handovers/mine'),
  getSentHandovers: () => api.get<any, ApiResponse<any[]>>('/v1/service-jobs/handovers/sent/mine'),
};

export const serviceJobTeamService = {
  getTeam: (jobId: number) => api.get<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team`),
  assign: (jobId: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments`, dto),
  updateAssignment: (jobId: number, assignmentId: number, dto: any) => api.put<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments/${assignmentId}`, dto),
  cancelAssignment: (jobId: number, assignmentId: number) => api.delete<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments/${assignmentId}`),
  acceptAssignment: (jobId: number, assignmentId: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments/${assignmentId}/accept`, {}),
  approveAssignment: (jobId: number, assignmentId: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments/${assignmentId}/approve`, {}),
  rejectAssignment: (jobId: number, assignmentId: number, reason: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/assignments/${assignmentId}/reject`, { reason }),
  recordWork: (jobId: number, assignmentId: number, action: string, details: any = {}) => api.post<any, ApiResponse<any>>(
    `/v1/service-jobs/${jobId}/team/assignments/${assignmentId}/work`,
    { action, ...(typeof details === 'string' ? { note: details } : details || {}) }),
  requestHandover: (jobId: number, dto: any) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/handovers`, dto),
  acceptHandover: (jobId: number, handoverId: number) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/handovers/${handoverId}/accept`, {}),
  rejectHandover: (jobId: number, handoverId: number, reason: string) => api.post<any, ApiResponse<any>>(`/v1/service-jobs/${jobId}/team/handovers/${handoverId}/reject`, { reason }),
};

// ── Excel Export ──────────────────────────────────────────
export const exportService = {
  bookings: () => `${BASE_URL}/v1/export/bookings`,
  services: () => `${BASE_URL}/v1/export/services`,
};

export const reportService = {
  openSalePdf: (saleId: number, pos = false) =>
    openPdfBlob(pos ? `/v1/reports/sale/${saleId}/pos` : `/v1/reports/sale/${saleId}`),
  openBookingPdf: (bookingId: number, paper = 'A5') =>
    openPdfBlob(`/v1/print/pdf/booking/${bookingId}?paper=${paper}`),
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
  hasAdministrator?: boolean;
  needsInitialAdmin?: boolean;
}

export interface SetupInitDTO {
  companyName: string;
  companyAddress: string;
  companyPhone: string;
  companyEmail: string;
  paymentMethods: string[];
}

export interface InitialAdminDTO {
  username: string;
  email: string;
  password: string;
}

export const setupService = {
  getStatus: async (): Promise<SetupStatusDTO> => {
    const res = await api.get<any, ApiResponse<SetupStatusDTO>>('/v1/setup/status');
    return res.data as SetupStatusDTO;
  },
  initialize: (dto: SetupInitDTO): Promise<ApiResponse<void>> =>
    api.post('/v1/setup/initialize', dto),
  createInitialAdmin: (dto: InitialAdminDTO, setupToken: string): Promise<ApiResponse<void>> =>
    api.post('/v1/setup/initial-admin', dto, {
      headers: { 'X-Setup-Token': setupToken.trim() },
    }),
};

async function openPdfBlob(path: string): Promise<void> {
  const res = await api.get(path, { responseType: 'blob' });
  const url = URL.createObjectURL(new Blob([res.data as BlobPart], { type: 'application/pdf' }));
  const w = window.open(url, '_blank');
  if (!w) { URL.revokeObjectURL(url); return; }
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}

export const dashboardService = {
  getStats: async (params?: { period?: string; from?: string; to?: string }): Promise<DashboardStats> => {
    const res = await api.get<any, ApiResponse<DashboardStats>>('/v1/dashboard/stats', { params });
    const d = (res?.data ?? {}) as any;
    return {
      totalSales:     Number(d.totalSales)     || 0,
      totalPurchases: Number(d.totalPurchases) || 0,
      totalServices:  Number(d.totalServices)  || 0,
      totalCustomers: Number(d.totalCustomers) || 0,
      todaySalesAmount:  Number(d.todaySalesAmount)  || 0,
      todaySalesCount:   Number(d.todaySalesCount)   || 0,
      periodServiceAmount: Number(d.periodServiceAmount) || 0,
      periodServiceCount: Number(d.periodServiceCount) || 0,
      periodPurchaseAmount: Number(d.periodPurchaseAmount) || 0,
      periodPurchaseCount: Number(d.periodPurchaseCount) || 0,
      totalOverdueAR:    Number(d.totalOverdueAR)    || 0,
      overdueARCount:    Number(d.overdueARCount)    || 0,
      totalPendingAR:    Number(d.totalPendingAR)    || 0,
      pendingARCount:    Number(d.pendingARCount)    || 0,
      pendingServiceJobs: Number(d.pendingServiceJobs) || 0,
      receivedJobCount: Number(d.receivedJobCount) || 0,
      inProgressJobCount: Number(d.inProgressJobCount) || 0,
      completedJobCount: Number(d.completedJobCount) || 0,
      pendingPaymentJobCount: Number(d.pendingPaymentJobCount) || 0,
      pendingDeliveryJobCount: Number(d.pendingDeliveryJobCount) || 0,
      lowStockCount:     Number(d.lowStockCount)     || 0,
      lowStockProducts:  Array.isArray(d.lowStockProducts) ? d.lowStockProducts : [],
      stockValue: Number(d.stockValue) || 0,
      supplierPayable: Number(d.supplierPayable) || 0,
      reworkCount: Number(d.reworkCount) || 0,
      upgradeCount: Number(d.upgradeCount) || 0,
      refundCount: Number(d.refundCount) || 0,
      refundAmount: Number(d.refundAmount) || 0,
      updatedAt: String(d.updatedAt || ''),
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
