import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { customerPortalService } from '../../services/customerPortalApi';

const HINT = 'အနည်းဆုံး ၈ လုံး — အက္ခရာ၊ ဂဏန်း နှင့် အထူးအက္ခရာ (!@#$…) ရောထည့်ပါ';

const isStrongPassword = (password: string) =>
  password.length >= 8 &&
  /[A-Za-z]/.test(password) &&
  /\d/.test(password) &&
  /[^A-Za-z0-9\s]/.test(password);

const tokenFromLocation = (): string => {
  const hash = window.location.hash || '';
  const hashQuery = hash.includes('?') ? hash.slice(hash.indexOf('?') + 1) : '';
  const fromHash = new URLSearchParams(hashQuery).get('token');
  if (fromHash) return fromHash;
  return new URLSearchParams(window.location.search).get('token') || '';
};

declare global {
  interface Window {
    google?: any;
  }
}

const CustomerPasswordResetPage: React.FC = () => {
  const [params] = useSearchParams();
  const token = useMemo(() => params.get('token') || tokenFromLocation(), [params]);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [googleIdToken, setGoogleIdToken] = useState('');
  const [googleEmail, setGoogleEmail] = useState('');
  const [clientId, setClientId] = useState('');
  const btnRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    void (async () => {
      try {
        const res = await fetch('/api/v1/customer-portal/auth/public-config');
        const body = await res.json();
        setClientId(body?.data?.googleClientId || '');
      } catch {
        setError('Google config မရယူနိုင်ပါ');
      }
    })();
  }, []);

  useEffect(() => {
    if (!clientId || !btnRef.current) return;
    const scriptId = 'google-gis';
    const boot = () => {
      if (!window.google?.accounts?.id || !btnRef.current) return;
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response: { credential?: string }) => {
          const cred = response?.credential || '';
          setGoogleIdToken(cred);
          try {
            const payload = JSON.parse(atob(cred.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
            setGoogleEmail(String(payload.email || '').toLowerCase());
          } catch {
            setGoogleEmail('');
          }
          setError(null);
        },
        auto_select: false
      });
      btnRef.current.innerHTML = '';
      window.google.accounts.id.renderButton(btnRef.current, {
        theme: 'outline',
        size: 'large',
        text: 'signin_with',
        width: 320
      });
    };
    if (!document.getElementById(scriptId)) {
      const s = document.createElement('script');
      s.id = scriptId;
      s.src = 'https://accounts.google.com/gsi/client';
      s.async = true;
      s.onload = boot;
      document.head.appendChild(s);
    } else {
      boot();
    }
  }, [clientId]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!googleIdToken) {
      setError('Reset email နဲ့ တူသော Google အကောင့်ဖြင့် အရင် ဝင်ပါ');
      return;
    }
    if (!isStrongPassword(password)) {
      setError(HINT);
      return;
    }
    if (password !== confirm) {
      setError('စကားဝှက် နှစ်ခု မတူပါ');
      return;
    }
    if (!token) {
      setError('Reset link မမှန်ကန်ပါ');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await customerPortalService.resetPassword({ token, password, googleIdToken });
      if (res.success) setDone(true);
      else setError(res.message || 'ပြောင်းမရပါ');
    } catch (err: any) {
      setError(err?.message || 'ပြောင်းမရပါ');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-16 text-slate-800">
      <div className="mx-auto w-full max-w-md rounded-2xl border bg-white p-6 shadow-sm">
        <h1 className="text-lg font-black">Password ပြန်သတ်မှတ်ရန်</h1>
        <p className="mt-1 text-xs text-slate-500">
          Reset email ရောက်တဲ့ Gmail နှင့် တူညီသော Google အကောင့်ဖြင့် ဝင်မှ ပြောင်းနိုင်ပါသည်။
        </p>
        {done ? (
          <div className="mt-6 space-y-3">
            <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800">
              Password ပြောင်းပြီးပါပြီ။
            </p>
            <div className="rounded-xl border border-dashed border-indigo-200 bg-indigo-50 px-3 py-3 text-sm leading-relaxed text-indigo-950">
              <p className="font-black">နောက်တစ်ဆင့် — Mobile App</p>
              <p className="mt-1 text-xs">
                ဖုန်းထဲက <b>SSPD Customer</b> APK ဖွင့်ပြီး စကားဝှက်အသစ်ဖြင့် ဝင်ပါ။
              </p>
            </div>
          </div>
        ) : (
          <form onSubmit={submit} className="mt-5 space-y-3">
            {!token && <p className="text-sm text-rose-600">Reset token မရှိပါ။ Email လင့်ခ်ကို ထပ်ဖွင့်ပါ။</p>}
            <div className="rounded-xl border bg-slate-50 p-3">
              <div ref={btnRef} className="flex justify-center" />
              {googleEmail && (
                <p className="mt-2 text-center text-xs font-bold text-emerald-700">ဝင်ထားသည် — {googleEmail}</p>
              )}
            </div>
            {googleIdToken && (
              <>
                <input
                  required
                  type="password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="ဥပမာ MyPass@12"
                  className="w-full rounded-xl border px-3 py-2.5 text-sm"
                />
                <input
                  required
                  type="password"
                  value={confirm}
                  onChange={e => setConfirm(e.target.value)}
                  placeholder="စကားဝှက်အသစ် ထပ်ရိုက်ပါ"
                  className="w-full rounded-xl border px-3 py-2.5 text-sm"
                />
                <p className="rounded-lg bg-indigo-50 px-3 py-2 text-[11px] leading-relaxed text-slate-600">{HINT}</p>
              </>
            )}
            {error && <p className="text-sm text-rose-600">{error}</p>}
            <button
              disabled={busy || !token || !googleIdToken}
              type="submit"
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 py-3 text-sm font-bold text-white disabled:opacity-60"
            >
              {busy && <Loader2 size={16} className="animate-spin" />}
              Password ပြောင်းမည်
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default CustomerPasswordResetPage;
