import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { History, Mail, Pencil, RefreshCw, Smartphone, X } from 'lucide-react';
import Swal from 'sweetalert2';
import { api } from '../services/api';
import { AppRoute } from '../types';
import { useRefreshOnTabActivate } from '../hooks/useRefreshOnTabActivate';

type Account = {
  id: number;
  customerId?: number;
  customerName?: string;
  customerPhone?: string;
  phone?: string;
  email?: string;
  hasPassword?: boolean;
  hasGoogle?: boolean;
  profileComplete?: boolean;
  enabled?: boolean;
  lastLoginAt?: string;
  loginCount?: number;
  hasUsedApp?: boolean;
  createdAt?: string;
};

type Activity = {
  id: number;
  action?: string;
  detail?: string;
  createdAt?: string;
};

const ACTION_LABEL: Record<string, string> = {
  REGISTER: 'အကောင့်ဖွင့်',
  LOGIN: 'အကောင့်ဝင်',
  PROFILE_COMPLETED: 'Profile ဖြည့်',
  PASSWORD_CHANGED: 'စကားဝှက်ပြောင်း',
  EMAIL_UPDATED: 'Email ပြင်',
  ORDER_PLACED: 'ပစ္စည်းမှာ',
  SERVICE_REQUESTED: 'Service တောင်း',
};

const fmt = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '—');

const CustomerAppAccountsPage: React.FC = () => {
  const [rows, setRows] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Account | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [actLoading, setActLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<any>('/v1/customer-app-accounts');
      setRows(res.data ?? []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useRefreshOnTabActivate(load);

  const patchEnabled = async (id: number, enabled: boolean) => {
    await api.patch(`/v1/customer-app-accounts/${id}/enabled?enabled=${enabled}`);
    await load();
  };

  const editEmail = async (account: Account) => {
    const result = await Swal.fire({
      title: 'Email ပြင်မည်',
      html: `<p class="mb-2 text-left text-xs text-slate-500">${account.customerName || 'Customer'} · #${account.customerId ?? '—'}</p>`,
      input: 'email',
      inputValue: account.email || '',
      inputPlaceholder: 'name@example.com',
      showCancelButton: true,
      confirmButtonText: 'သိမ်းမည်',
      cancelButtonText: 'မလုပ်တော့ပါ',
      inputValidator: (value) => {
        const v = (value || '').trim();
        if (!v.includes('@') || v.length < 6) return 'Email မှန်ကန်စွာ ထည့်ပါ';
        return null;
      },
    });
    if (!result.isConfirmed) return;
    const email = String(result.value || '').trim();
    try {
      await api.patch(`/v1/customer-app-accounts/${account.id}/email`, { email });
      await Swal.fire({ icon: 'success', title: 'သိမ်းပြီး', text: 'Email ပြင်ဆင်ပြီးပါပြီ', timer: 1400, showConfirmButton: false });
      await load();
      if (selected?.id === account.id) {
        setSelected({ ...account, email });
      }
    } catch (e: any) {
      await Swal.fire({
        icon: 'error',
        title: 'မအောင်မြင်ပါ',
        text: e?.response?.data?.message || e?.message || 'Email ပြင်မရပါ',
      });
    }
  };

  const openActivity = async (account: Account) => {
    setSelected(account);
    setActLoading(true);
    setActivities([]);
    try {
      const res = await api.get<any>(`/v1/customer-app-accounts/${account.id}/activity`);
      setActivities(res.data ?? []);
    } catch (e) {
      console.error(e);
    } finally {
      setActLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
            <Smartphone size={16} className="text-indigo-600" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-slate-800">Customer App အကောင့်</h2>
            <p className="text-xs text-slate-500">
              App သို့မဟုတ် <Link to={AppRoute.CUSTOMER_SHOP} className="font-bold text-indigo-600">ဝဘ်ဆိုင်</Link> မှ ဖွင့်ထားသော အကောင့်များ —
              Email ပြင်ခြင်း / ဝင်သုံးမှု / လုပ်ဆောင်ချက် ကြည့်နိုင်သည်
            </p>
          </div>
        </div>
        <button onClick={() => load()} className="inline-flex items-center gap-2 px-3 py-1.5 bg-white border rounded-lg text-xs font-medium text-slate-600">
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
        </button>
      </div>
      <div className="bg-white border rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-[11px] uppercase text-slate-500">
            <tr>
              <th className="px-4 py-2 text-left">Customer</th>
              <th className="px-4 py-2 text-left">ဖုန်း</th>
              <th className="px-4 py-2 text-left">Email</th>
              <th className="px-4 py-2 text-left">ဝင်ပုံ</th>
              <th className="px-4 py-2 text-center">App သုံး</th>
              <th className="px-4 py-2 text-center">Login အကြိမ်</th>
              <th className="px-4 py-2 text-left">နောက်ဆုံးဝင်</th>
              <th className="px-4 py-2">အခြေအနေ</th>
              <th className="px-4 py-2 text-left">ဖွင့်သည့်ရက်</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={10} className="py-12 text-center text-slate-400">Loading...</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={10} className="py-12 text-center text-slate-400">App အကောင့် မရှိသေးပါ</td></tr>
            ) : rows.map((a) => (
              <tr key={a.id} className="border-t hover:bg-slate-50/60">
                <td className="px-4 py-3">
                  <b>{a.customerName || '—'}</b>
                  <div className="text-xs text-slate-500">#{a.customerId}</div>
                </td>
                <td className="px-4 py-3">{a.customerPhone || a.phone || '—'}</td>
                <td className="px-4 py-3 text-xs">
                  <div className="flex items-center gap-1.5">
                    <span className="min-w-0 break-all">{a.email || '—'}</span>
                    <button
                      type="button"
                      title="Email ပြင်မည်"
                      onClick={() => void editEmail(a)}
                      className="inline-flex shrink-0 items-center justify-center rounded-md p-1 text-indigo-600 hover:bg-indigo-50"
                    >
                      <Pencil size={13} />
                    </button>
                  </div>
                </td>
                <td className="px-4 py-3 text-xs">
                  {[a.hasPassword ? 'ဖုန်း' : null, a.hasGoogle ? 'Gmail' : null].filter(Boolean).join(' + ') || '—'}
                </td>
                <td className="px-4 py-3 text-center">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${a.hasUsedApp ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-500'}`}>
                    {a.hasUsedApp ? 'သုံးပြီး' : 'မဝင်သေး'}
                  </span>
                </td>
                <td className="px-4 py-3 text-center font-bold tabular-nums">{a.loginCount ?? 0}</td>
                <td className="px-4 py-3 text-xs text-slate-600">{fmt(a.lastLoginAt)}</td>
                <td className="px-4 py-3 text-center space-x-1">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-black ${a.enabled ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-700'}`}>
                    {a.enabled ? 'ဖွင့်' : 'ပိတ်'}
                  </span>
                  {!a.profileComplete && (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-black text-amber-800">Profile မပြည့်</span>
                  )}
                </td>
                <td className="px-4 py-3 text-xs text-slate-500">{fmt(a.createdAt)}</td>
                <td className="px-4 py-3 text-right whitespace-nowrap space-x-1">
                  <button
                    onClick={() => void editEmail(a)}
                    className="inline-flex items-center gap-1 rounded-lg bg-sky-50 px-2 py-1 text-[11px] font-bold text-sky-700"
                  >
                    <Mail size={12} /> Email
                  </button>
                  <button
                    onClick={() => openActivity(a)}
                    className="inline-flex items-center gap-1 rounded-lg bg-indigo-50 px-2 py-1 text-[11px] font-bold text-indigo-700"
                  >
                    <History size={12} /> လုပ်ဆောင်ချက်
                  </button>
                  <button
                    onClick={() => patchEnabled(a.id, !a.enabled)}
                    className={`rounded-lg px-2 py-1 text-[11px] font-bold ${a.enabled ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'}`}
                  >
                    {a.enabled ? 'ပိတ်မည်' : 'ဖွင့်မည်'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selected && (
        <div className="fixed inset-0 z-50 flex justify-end bg-slate-900/40" onClick={() => setSelected(null)}>
          <div
            className="flex h-full w-full max-w-md flex-col bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between border-b px-4 py-3">
              <div>
                <div className="text-sm font-black text-slate-800">{selected.customerName || 'Customer'}</div>
                <div className="text-xs text-slate-500">
                  Login {selected.loginCount ?? 0} ကြိမ် · နောက်ဆုံး {fmt(selected.lastLoginAt)}
                </div>
                <button
                  type="button"
                  onClick={() => void editEmail(selected)}
                  className="mt-1 inline-flex items-center gap-1 text-xs font-bold text-indigo-600"
                >
                  <Mail size={12} /> {selected.email || 'Email ထည့်မည်'}
                </button>
              </div>
              <button onClick={() => setSelected(null)} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100">
                <X size={18} />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-4">
              {actLoading ? (
                <div className="py-16 text-center text-sm text-slate-400">Loading...</div>
              ) : activities.length === 0 ? (
                <div className="py-16 text-center text-sm text-slate-400">
                  လုပ်ဆောင်ချက် မရှိသေးပါ<br />
                  <span className="text-xs">(deploy ပြီးနောက် အသစ်ဝင်/လုပ်မှ မှတ်တမ်းတင်မည်)</span>
                </div>
              ) : (
                <ul className="space-y-2">
                  {activities.map((row) => (
                    <li key={row.id} className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2.5">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-black text-indigo-700">
                          {ACTION_LABEL[row.action || ''] || row.action}
                        </span>
                        <span className="text-[10px] text-slate-400">{fmt(row.createdAt)}</span>
                      </div>
                      {row.detail && (
                        <div className="mt-1 text-xs text-slate-600">{row.detail}</div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CustomerAppAccountsPage;
