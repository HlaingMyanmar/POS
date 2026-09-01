import React, { useState } from 'react';
import { ShieldCheck, User, Mail, Lock, Loader2, ArrowRight, Eye, EyeOff } from 'lucide-react';
import { setupService } from '../services/api';

interface Props {
  onComplete: () => void;
}

const InitialAdminPage: React.FC<Props> = ({ onComplete }) => {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const canSubmit =
    username.trim().length >= 3
    && email.includes('@')
    && password.length >= 8
    && password === confirm
    && !saving;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await setupService.createInitialAdmin({
        username: username.trim(),
        email: email.trim(),
        password,
      });
      onComplete();
    } catch (err: any) {
      setError(err?.response?.data?.message || err?.message || 'Could not create administrator.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-indigo-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-indigo-500/20 border border-indigo-400/30 rounded-2xl mb-4">
            <ShieldCheck size={26} className="text-indigo-400" />
          </div>
          <h1 className="text-2xl font-bold text-white">Create Administrator</h1>
          <p className="text-slate-400 text-sm mt-1">
            No users yet. Create the first ADMINISTRATOR to open the software.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="bg-white rounded-2xl shadow-2xl p-6 space-y-4">
          <label className="block space-y-1">
            <span className="text-xs font-semibold text-slate-600 flex items-center gap-1">
              <User size={12} /> Username <span className="text-rose-500">*</span>
            </span>
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="e.g. HlaingHtun"
              autoComplete="username"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200"
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-semibold text-slate-600 flex items-center gap-1">
              <Mail size={12} /> Email <span className="text-rose-500">*</span>
            </span>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="admin@company.com"
              autoComplete="email"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200"
            />
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-semibold text-slate-600 flex items-center gap-1">
              <Lock size={12} /> Password <span className="text-rose-500">*</span>
            </span>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="At least 8 characters"
                autoComplete="new-password"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 pr-10 text-sm focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200"
              />
              <button
                type="button"
                onClick={() => setShowPassword(v => !v)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </label>

          <label className="block space-y-1">
            <span className="text-xs font-semibold text-slate-600 flex items-center gap-1">
              <Lock size={12} /> Confirm password <span className="text-rose-500">*</span>
            </span>
            <input
              type={showPassword ? 'text' : 'password'}
              value={confirm}
              onChange={e => setConfirm(e.target.value)}
              placeholder="Re-enter password"
              autoComplete="new-password"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200"
            />
          </label>

          {error && (
            <div className="bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg px-4 py-2">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={!canSubmit}
            className="w-full flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-bold px-5 py-2.5 rounded-lg disabled:opacity-40"
          >
            {saving ? <Loader2 size={14} className="animate-spin" /> : <ArrowRight size={14} />}
            {saving ? 'Creating...' : 'Create Administrator'}
          </button>

          <p className="text-[11px] text-slate-400 text-center">
            This screen appears only when the database has no users.
          </p>
        </form>
      </div>
    </div>
  );
};

export default InitialAdminPage;
