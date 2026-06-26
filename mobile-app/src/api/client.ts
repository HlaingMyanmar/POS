import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiResponse, AuthResponse } from '../types';

const KEY_TOKEN  = 'sspd_token';
const KEY_SERVER = 'sspd_server';

let _token: string | null = null;

export const getToken  = () => _token;
export const setToken  = (t: string | null) => { _token = t; };

export const saveServerUrl = (url: string) => AsyncStorage.setItem(KEY_SERVER, url.trim().replace(/\/+$/, ''));
export const loadServerUrl = () => AsyncStorage.getItem(KEY_SERVER);
export const saveToken     = (t: string)   => AsyncStorage.setItem(KEY_TOKEN, t);
export const clearStorage  = () => Promise.all([
  AsyncStorage.removeItem(KEY_TOKEN),
  AsyncStorage.removeItem(KEY_SERVER),
]);

// Build axios instance with dynamic baseURL
export const buildClient = (serverUrl: string) => {
  const instance = axios.create({
    baseURL: `${serverUrl}/api/v1`,
    headers: { 'Content-Type': 'application/json' },
    timeout: 10_000,
  });
  instance.interceptors.request.use(cfg => {
    if (_token) cfg.headers.Authorization = `Bearer ${_token}`;
    return cfg;
  });
  instance.interceptors.response.use(
    r => r.data,
    e => Promise.reject(e.response?.data || { success: false, message: e.message }),
  );
  return instance;
};

let _client = axios.create();

export const initClient = async (): Promise<string | null> => {
  const url = await loadServerUrl();
  if (url) _client = buildClient(url);
  const stored = await AsyncStorage.getItem(KEY_TOKEN);
  if (stored) _token = stored;
  return url;
};

export const setServerUrl = (url: string) => {
  _client = buildClient(url);
};

export const api = {
  get:    <T>(path: string, cfg?: any) => _client.get<any, T>(path, cfg),
  post:   <T>(path: string, body?: any) => _client.post<any, T>(path, body),
  put:    <T>(path: string, body?: any) => _client.put<any, T>(path, body),
  patch:  <T>(path: string, body?: any) => _client.patch<any, T>(path, body),
  delete: <T>(path: string) => _client.delete<any, T>(path),
};

export const authApi = {
  login: async (usernameOremail: string, password: string): Promise<ApiResponse<AuthResponse>> => {
    _token = null; // clear stale token so login request has no Bearer header
    const res = await api.post<ApiResponse<AuthResponse>>('/auth/login', { usernameOremail, password });
    if (res.success && res.data?.accessToken) {
      _token = res.data.accessToken;
      await saveToken(res.data.accessToken);
    }
    return res;
  },
  logout: async () => {
    _token = null;
    await AsyncStorage.removeItem(KEY_TOKEN);
  },
};
