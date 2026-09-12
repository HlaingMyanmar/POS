import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, Shield } from 'lucide-react';
import { AppRoute } from '../types';
import { saleApiService } from '../services/saleapiservice';

type WarrantyRow = {
  saleDetailId?: number;
  saleId?: number;
  saleCode?: string;
  saleDate?: string;
  customerId?: number;
  customerName?: string;
  productName?: string;
  serialNumber?: string;
  qty?: number;
  warrantyMonths?: number;
  warrantyStartDate?: string;
  warrantyEndDate?: string;
  status?: string;
  daysRemaining?: number;
};

const statusLabel = (s?: string) => {
  if (s === 'ACTIVE') return { text: 'သက်တမ်းရှိ', cls: 'bg-emerald-50 text-emerald-700' };
  if (s === 'EXPIRED') return { text: 'သက်တမ်းကုန်', cls: 'bg-rose-50 text-rose-700' };
  return { text: 'Warranty မရှိ', cls: 'bg-slate-100 text-slate-500' };
};

const WarrantyLookupPage: React.FC = () => {
  const [serial, setSerial] = useState('');
  const [status, setStatus] = useState('');
  const [rows, setRows] = useState<WarrantyRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = async (nextSerial = serial, nextStatus = status) => {
    setBusy(true);
    setError('');
    try {
      setRows(await saleApiService.lookupWarranties({
        serial: nextSerial.trim() || undefined,
        status: nextStatus || undefined,
      }));
    } catch (e: any) {
      setError(e?.message || 'Warranty ဖတ်မရပါ');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => { void load('', ''); }, []);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-black text-slate-900">
          <Shield size={20} className="text-indigo-600" />
          Customer Warranty
        </h1>
        <p className="text-sm text-slate-500">
          ရောင်းချိန် snapshot — Product master ပြောင်းလဲမှုက အဟောင်း warranties ကို မပြောင်းပါ။
        </p>
      </div>

      <form
        className="flex flex-wrap items-end gap-2 rounded-2xl border bg-white p-4"
        onSubmit={e => { e.preventDefault(); void load(); }}
      >
        <label className="min-w-[220px] flex-1 text-xs font-semibold text-slate-500">
          Serial
          <input
            value={serial}
            onChange={e => setSerial(e.target.value)}
            placeholder="KS512-A00192"
            className="mt-1 w-full rounded-xl border px-3 py-2 text-sm text-slate-800"
          />
        </label>
        <label className="text-xs font-semibold text-slate-500">
          အခြေအနေ
          <select
            value={status}
            onChange={e => setStatus(e.target.value)}
            className="mt-1 block rounded-xl border px-3 py-2 text-sm"
          >
            <option value="">အားလုံး</option>
            <option value="ACTIVE">သက်တမ်းရှိ</option>
            <option value="EXPIRED">သက်တမ်းကုန်</option>
          </select>
        </label>
        <button
          type="submit"
          disabled={busy}
          className="inline-flex h-10 items-center gap-1.5 rounded-xl bg-indigo-600 px-4 text-sm font-bold text-white disabled:opacity-50"
        >
          <Search size={16} />
          ရှာမည်
        </button>
      </form>

      {error && <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}

      <div className="overflow-auto rounded-2xl border bg-white">
        <table className="w-full min-w-[980px] text-sm">
          <thead className="bg-slate-50 text-left text-[11px] font-bold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-3 py-2">Invoice</th>
              <th className="px-3 py-2">ဖောက်သည်</th>
              <th className="px-3 py-2">ပစ္စည်း</th>
              <th className="px-3 py-2">Serial</th>
              <th className="px-3 py-2">စတင်</th>
              <th className="px-3 py-2">ကုန်ဆုံး</th>
              <th className="px-3 py-2">ကျန်</th>
              <th className="px-3 py-2">အခြေအနေ</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(r => {
              const st = statusLabel(r.status);
              return (
                <tr key={r.saleDetailId} className="border-t">
                  <td className="px-3 py-2">
                    {r.saleId ? (
                      <Link className="font-semibold text-indigo-700 hover:underline" to={`${AppRoute.SALES}?saleId=${r.saleId}`}>
                        {r.saleCode || `#${r.saleId}`}
                      </Link>
                    ) : '—'}
                    <div className="text-[11px] text-slate-400">{r.saleDate?.replace('T', ' ').slice(0, 16)}</div>
                  </td>
                  <td className="px-3 py-2">{r.customerName || '—'}</td>
                  <td className="px-3 py-2">
                    {r.productName || '—'}
                    <div className="text-[11px] text-slate-400">{r.warrantyMonths || 0} လ · qty {r.qty || 1}</div>
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">{r.serialNumber || '—'}</td>
                  <td className="px-3 py-2">{r.warrantyStartDate || '—'}</td>
                  <td className="px-3 py-2">{r.warrantyEndDate || '—'}</td>
                  <td className="px-3 py-2">{r.status === 'NONE' ? '—' : `${r.daysRemaining ?? 0} ရက်`}</td>
                  <td className="px-3 py-2">
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${st.cls}`}>{st.text}</span>
                  </td>
                </tr>
              );
            })}
            {!busy && rows.length === 0 && (
              <tr><td colSpan={8} className="px-3 py-10 text-center text-slate-400">Warranty မတွေ့ပါ</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default WarrantyLookupPage;
