import React, { useEffect, useState } from 'react';
import { User, AppLanguage } from '../types';
import { authService } from '../services/api';
import { Lock, User as UserIcon, Loader2, AlertCircle, Eye, EyeOff, ShieldCheck, Languages } from 'lucide-react';
import Swal from 'sweetalert2';
import fallbackLogoSrc from '../img/logo.png';
import { companySettingsService } from '../services/api';
import { setCompanySettingsCache, CompanySettings } from '../utils/companySettings';
import { appVersionSettingsService } from '../services/api';

interface LoginProps {
  onLoginSuccess: (user: User, token: string) => void;
  language: AppLanguage;
  onLanguageChange: (language: AppLanguage) => void;
}

const Login: React.FC<LoginProps> = ({ onLoginSuccess, language, onLanguageChange }) => {
  const [usernameOremail, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [company, setCompany]       = useState<CompanySettings | null>(null);
  const [appVersion, setAppVersion] = useState('');

  useEffect(() => {
    companySettingsService.getSettings()
      .then(res => {
        if (res.success && res.data) {
          setCompanySettingsCache(res.data);
          setCompany(res.data);
        }
      })
      .catch(() => {});
    appVersionSettingsService.getSettings()
      .then(res => { if (res.success && res.data) setAppVersion(res.data.versionName); })
      .catch(() => {});
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const response = await authService.login(usernameOremail, password);

      if (response.success) {
        await Swal.fire({
          icon: 'success',
          title: 'Secure Access Granted',
          text: `Welcome back, ${response.data.username}`,
          timer: 1500,
          showConfirmButton: false,
          position: 'center',
          backdrop: 'rgba(15, 23, 42, 0.4)',
          customClass: {
            popup: 'rounded-2xl border-none shadow-2xl font-inter',
            title: 'font-bold text-slate-800'
          }
        });

        const authData = response.data;
        onLoginSuccess({
          username: authData.username,
          name: authData.name,
          phone: authData.phone,
          staffId: authData.staffId,
          roles: authData.roles,
          permissions: authData.permissions
        }, authData.accessToken);
      }
    } catch (err: any) {
      const errorMessage = err.message || 'Authentication failed. Please check your credentials.';
      setError(errorMessage);

      Swal.fire({
        icon: 'error',
        title: 'Access Denied',
        text: errorMessage,
        confirmButtonColor: '#6366f1',
        customClass: {
          popup: 'rounded-2xl border-none shadow-2xl',
          confirmButton: 'rounded-xl px-8 font-bold'
        }
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 px-4 py-8" style={{ fontSize: '16px' }}>
      <div className="absolute top-4 right-4 inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white/90 p-1 shadow-sm">
        <Languages size={14} className="ml-2 text-slate-400" />
        {(['en', 'my'] as AppLanguage[]).map((lng) => (
          <button
            key={lng}
            type="button"
            onClick={() => onLanguageChange(lng)}
            className={`px-2.5 py-1 rounded-md text-[11px] font-bold uppercase ${language === lng ? 'bg-indigo-600 text-white' : 'text-slate-500 hover:bg-slate-100'}`}
          >
            {lng}
          </button>
        ))}
      </div>

      <div className="w-full max-w-sm bg-white border border-slate-200 rounded-lg shadow-[0_20px_50px_rgba(15,23,42,0.12)] overflow-hidden">
        <div className="px-8 pt-8 pb-6 text-center border-b border-slate-100 bg-white">
          <div className="w-20 h-20 rounded-xl bg-slate-50 border border-slate-200 shadow-sm flex items-center justify-center mx-auto mb-4 p-2">
            <img
              src={company?.logoBase64 || fallbackLogoSrc}
              alt="Logo"
              className="w-full h-full object-contain"
            />
          </div>
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-100 text-[10px] font-bold uppercase mb-3">
            <ShieldCheck size={11} /> Secure Login
          </div>
          <h1 className="text-xl font-black text-slate-800 tracking-tight">
            {company?.companyName || 'SSPD IT Solution Center'}
          </h1>
          <p className="text-slate-400 text-xs mt-1 font-medium">
            {company?.taglineMm || 'Inventory Management System'}
          </p>
        </div>

        <div className="px-8 py-7">
          {error && (
            <div className="mb-4 bg-rose-50 border border-rose-200 text-rose-700 px-3 py-2.5 rounded-lg flex items-start gap-2">
              <AlertCircle size={15} className="mt-0.5 shrink-0" />
              <p className="text-xs leading-5 font-semibold">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Username or Email</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400 group-focus-within:text-indigo-600">
                  <UserIcon size={15} />
                </div>
                <input
                  type="text"
                  required
                  autoFocus
                  value={usernameOremail}
                  onChange={(e) => setUsername(e.target.value)}
                  className="block w-full pl-10 pr-3 py-3 border border-slate-200 bg-slate-50 rounded-lg focus:outline-none focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-500/10 text-sm font-semibold text-slate-700 placeholder-slate-300 transition-all"
                  placeholder="Enter username or email"
                />
              </div>
            </div>

            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Password</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400 group-focus-within:text-indigo-600">
                  <Lock size={15} />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full pl-10 pr-10 py-3 border border-slate-200 bg-slate-50 rounded-lg focus:outline-none focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-500/10 text-sm font-semibold text-slate-700 placeholder-slate-300 transition-all"
                  placeholder="Password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-700"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 py-3 px-4 bg-indigo-600 text-white text-sm font-bold rounded-lg hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-500/20 transition-all disabled:opacity-60 disabled:pointer-events-none mt-1 shadow-sm shadow-indigo-100"
            >
              {loading ? (
                <>
                  <Loader2 className="animate-spin" size={15} />
                  <span>Signing in...</span>
                </>
              ) : (
                'Sign In'
              )}
            </button>
          </form>
        </div>

        <div className="px-8 py-4 bg-slate-50 border-t border-slate-100 text-center">
          <p className="text-slate-400 text-[10px] font-semibold">
            &copy; 2026 SSPD IT Solution - {appVersion ? `v${appVersion}` : 'v1.2.0'}
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
