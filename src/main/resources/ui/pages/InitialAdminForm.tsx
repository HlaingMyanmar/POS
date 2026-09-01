import React, { useState } from 'react';
import { AlertCircle, Eye, EyeOff, Loader2, Lock, ShieldCheck } from 'lucide-react';
import Swal from 'sweetalert2';
import { setupService } from '../services/api';
import fallbackLogoSrc from '../img/logo.png';
import { CompanySettings } from '../utils/companySettings';

interface Props {
  company: CompanySettings | null;
  onCreated: (username: string) => void;
}

const InitialAdminForm: React.FC<Props> = ({ company, onCreated }) => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const cleanUsername = username.trim();
    if (cleanUsername.length < 3) return setError('Username must contain at least 3 characters.');
    if (password.length < 8) return setError('Password must contain at least 8 characters.');

    setLoading(true);
    try {
      const response = await setupService.createInitialAdmin({ username: cleanUsername, email: email.trim(), password });
      if (!response.success) throw new Error(response.message || 'Administrator account could not be created.');
      await Swal.fire({ icon: 'success', title: 'Administrator Created', text: 'Please sign in with your new account.', confirmButtonColor: '#4f46e5' });
      onCreated(cleanUsername);
    } catch (err: any) {
      setError(err.message || 'Administrator account could not be created.');
      try {
        const status = await setupService.getStatus();
        if (!status.needsInitialAdmin) onCreated(cleanUsername);
      } catch {}
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-100 px-4 py-8">
      <div className="w-full max-w-sm overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xl">
        <div className="border-b border-slate-100 px-8 pb-6 pt-8 text-center">
          <div className="mx-auto mb-4 flex h-20 w-20 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 p-2">
            <img src={company?.logoBase64 || fallbackLogoSrc} alt="Logo" className="h-full w-full object-contain" />
          </div>
          <div className="mb-3 inline-flex items-center gap-1.5 rounded-md border border-indigo-100 bg-indigo-50 px-2.5 py-1 text-[10px] font-bold uppercase text-indigo-700">
            <ShieldCheck size={11} /> First-time Setup
          </div>
          <h1 className="text-xl font-black text-slate-800">Create Administrator</h1>
          <p className="mt-1 text-xs font-medium text-slate-400">Create the first account before signing in</p>
        </div>
        <div className="px-8 py-7">
          {error && <div className="mb-4 flex gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2.5 text-rose-700"><AlertCircle size={15} /><p className="text-xs font-semibold">{error}</p></div>}
          <form onSubmit={submit} className="space-y-4">
            <label className="block text-[11px] font-bold uppercase text-slate-500">Username
              <input type="text" required minLength={3} autoFocus value={username} onChange={e => setUsername(e.target.value)} className="mt-1.5 block w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-semibold normal-case focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/10" />
            </label>
            <label className="block text-[11px] font-bold uppercase text-slate-500">Email
              <input type="email" required value={email} onChange={e => setEmail(e.target.value)} className="mt-1.5 block w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-semibold normal-case focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/10" />
            </label>
            <label className="block text-[11px] font-bold uppercase text-slate-500">Password
              <div className="relative mt-1.5">
                <Lock size={15} className="absolute left-3.5 top-3.5 text-slate-400" />
                <input type={showPassword ? 'text' : 'password'} required minLength={8} value={password} onChange={e => setPassword(e.target.value)} className="block w-full rounded-lg border border-slate-200 bg-slate-50 py-3 pl-10 pr-10 text-sm font-semibold normal-case focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-500/10" placeholder="At least 8 characters" />
                <button type="button" onClick={() => setShowPassword(v => !v)} className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400">{showPassword ? <EyeOff size={15} /> : <Eye size={15} />}</button>
              </div>
            </label>
            <button type="submit" disabled={loading} className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-3 text-sm font-bold text-white hover:bg-indigo-700 disabled:opacity-60">
              {loading ? <><Loader2 className="animate-spin" size={15} /> Creating...</> : 'Create Administrator'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default InitialAdminForm;