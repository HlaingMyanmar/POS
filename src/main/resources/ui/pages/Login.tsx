import React, { useEffect, useState } from 'react';
import { User, AppLanguage } from '../types';
import { authService } from '../services/api';
import { Lock, User as UserIcon, Loader2, AlertCircle, Eye, EyeOff } from 'lucide-react';
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
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4" style={{ fontSize: '16px' }}>


      <div className="w-full max-w-sm bg-white border border-gray-200 rounded-lg shadow-sm p-8">
        <div className="text-center mb-7">
          <img
            src={company?.logoBase64 || fallbackLogoSrc}
            alt="Logo"
            className="w-16 h-16 object-contain mx-auto mb-3"
          />
          <h1 className="text-lg font-bold text-gray-800">
            {company?.companyName || 'SSPD IT Solution Center'}
          </h1>
          <p className="text-gray-400 text-xs mt-1">
            {company?.taglineMm || 'Inventory Management System'}
          </p>
        </div>

        {error && (
          <div className="mb-4 bg-red-50 border border-red-200 text-red-600 px-3 py-2.5 rounded flex items-start gap-2">
            <AlertCircle size={15} className="mt-0.5 shrink-0" />
            <p className="text-xs leading-tight">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Username or Email</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <UserIcon size={15} />
              </div>
              <input
                type="text"
                required
                autoFocus
                value={usernameOremail}
                onChange={(e) => setUsername(e.target.value)}
                className="block w-full pl-9 pr-3 py-2.5 border border-gray-300 rounded focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-sm text-gray-700 placeholder-gray-300"
                placeholder="Enter username or email"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Password</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <Lock size={15} />
              </div>
              <input
                type={showPassword ? 'text' : 'password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="block w-full pl-9 pr-9 py-2.5 border border-gray-300 rounded focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-sm text-gray-700 placeholder-gray-300"
                placeholder="••••••••"
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 text-white text-sm font-medium rounded hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 transition-colors disabled:opacity-60 disabled:pointer-events-none mt-1"
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

        <p className="text-center text-gray-300 text-[10px] mt-6">
          &copy; 2026 SSPD IT Solution &nbsp;&middot;&nbsp; {appVersion ? `v${appVersion}` : 'v1.2.0'}
        </p>
      </div>
    </div>
  );
};

export default Login;
