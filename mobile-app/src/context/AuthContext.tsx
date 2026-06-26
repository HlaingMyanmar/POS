import React, { createContext, useContext, useState, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { authApi, initClient, setServerUrl, saveServerUrl } from '../api/client';
import { initWs, disconnectWs } from '../services/wsClient';

const KEY_PERMS  = 'sspd_permissions';
const KEY_ROLES  = 'sspd_roles';
const KEY_NAME   = 'sspd_name';
const KEY_PHONE  = 'sspd_phone';

interface AuthState {
  isReady: boolean;
  isLoggedIn: boolean;
  username: string;
  name: string;
  phone: string;
  serverUrl: string;
  permissions: string[];
  roles: string[];
  hasPermission: (perm: string) => boolean;
  login: (server: string, username: string, password: string) => Promise<string | null>;
  logout: () => Promise<void>;
  updateProfile: (name: string, phone: string) => void;
}

const AuthContext = createContext<AuthState>({} as AuthState);
export const useAuth = () => useContext(AuthContext);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isReady,     setReady]       = useState(false);
  const [isLoggedIn,  setLoggedIn]    = useState(false);
  const [username,    setUsername]    = useState('');
  const [name,        setName]        = useState('');
  const [phone,       setPhone]       = useState('');
  const [serverUrl,   setServerState] = useState('');
  const [permissions, setPermissions] = useState<string[]>([]);
  const [roles,       setRoles]       = useState<string[]>([]);

  useEffect(() => {
    (async () => {
      const url = await initClient();
      if (url) setServerState(url);
      const storedPerms = await AsyncStorage.getItem(KEY_PERMS);
      const storedRoles = await AsyncStorage.getItem(KEY_ROLES);
      const storedName  = await AsyncStorage.getItem(KEY_NAME);
      const storedPhone = await AsyncStorage.getItem(KEY_PHONE);
      if (storedPerms) setPermissions(JSON.parse(storedPerms));
      if (storedRoles) setRoles(JSON.parse(storedRoles));
      if (storedName)  setName(storedName);
      if (storedPhone) setPhone(storedPhone);
      setReady(true);
    })();
  }, []);

  const hasPermission = (perm: string) => {
    if (roles.includes('ADMIN') || roles.includes('ROLE_ADMIN')) return true;
    return permissions.includes(perm);
  };

  const login = async (server: string, user: string, pass: string): Promise<string | null> => {
    const trimmed = server.trim().replace(/\/+$/, '');
    setServerUrl(trimmed);
    await saveServerUrl(trimmed);
    setServerState(trimmed);
    try {
      const res = await authApi.login(user, pass);
      if (res.success) {
        setUsername(res.data.username);
        const perms  = res.data.permissions ?? [];
        const rols   = res.data.roles ?? [];
        const nm     = res.data.name  ?? '';
        const ph     = res.data.phone ?? '';
        setPermissions(perms);
        setRoles(rols);
        setName(nm);
        setPhone(ph);
        await AsyncStorage.setItem(KEY_PERMS, JSON.stringify(perms));
        await AsyncStorage.setItem(KEY_ROLES, JSON.stringify(rols));
        await AsyncStorage.setItem(KEY_NAME,  nm);
        await AsyncStorage.setItem(KEY_PHONE, ph);
        setLoggedIn(true);
        initWs(trimmed); // start WebSocket after login
        return null;
      }
      return res.message || 'Login failed';
    } catch (e: any) {
      return e?.message || 'Cannot connect to server';
    }
  };

  const logout = async () => {
    disconnectWs();
    await authApi.logout();
    await AsyncStorage.multiRemove([KEY_PERMS, KEY_ROLES, KEY_NAME, KEY_PHONE]);
    setLoggedIn(false);
    setUsername('');
    setName('');
    setPhone('');
    setPermissions([]);
    setRoles([]);
  };

  const updateProfile = (newName: string, newPhone: string) => {
    setName(newName);
    setPhone(newPhone);
    AsyncStorage.setItem(KEY_NAME,  newName);
    AsyncStorage.setItem(KEY_PHONE, newPhone);
  };

  return (
    <AuthContext.Provider value={{ isReady, isLoggedIn, username, name, phone, serverUrl, permissions, roles, hasPermission, login, logout, updateProfile }}>
      {children}
    </AuthContext.Provider>
  );
};
