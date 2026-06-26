import React, { useEffect, useRef, useState } from 'react';
import Swal from 'sweetalert2';
import { companySettingsService } from '../services/api';
import { CompanySettings, setCompanySettingsCache } from '../utils/companySettings';

const emptySettings: CompanySettings = {
  companyName: '',
  companyAddress: '',
  companyPhone: '',
  companyEmail: '',
  invoiceTitle: 'Sales Invoice',
  footerNote: 'Thank you for your business',
  taglineMm: 'ဝန်ဆောင်မှုဌာန',
  logoBase64: '',
  voucherConfigJson: '',
  salePrefix: 'INV',
  saleDigits: 5,
  purchasePrefix: 'PUR',
  purchaseDigits: 5,
  bookingPrefix: 'BK',
  bookingDigits: 6,
};

const MAX_LOGO_BYTES = 500 * 1024;

const fileToDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(String(reader.result || ''));
    reader.readAsDataURL(file);
  });

type Tab = 'company' | 'serial';

const CompanySettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('company');
  const [settings, setSettings] = useState<CompanySettings>(emptySettings);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => { void load(); }, []);

  const load = async () => {
    setLoading(true);
    try {
      const res = await companySettingsService.getSettings();
      if (res.success && res.data) {
        setSettings({ ...emptySettings, ...res.data });
      }
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleSave = async () => {
    if (!settings.companyName.trim()) {
      Swal.fire('Required', 'Company name is required.', 'warning');
      return;
    }
    setSaving(true);
    try {
      const res = await companySettingsService.saveSettings(settings);
      if (res.success) {
        const merged = { ...emptySettings, ...(res.data || settings) };
        setSettings(merged);
        setCompanySettingsCache(merged);
        Swal.fire('Saved', 'Settings updated successfully.', 'success');
      } else {
        Swal.fire('Error', res.message || 'Save failed', 'error');
      }
    } catch (e: any) {
      Swal.fire('Error', e?.message || 'Save failed', 'error');
    } finally { setSaving(false); }
  };

  const handleLogoChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { Swal.fire('Invalid', 'Only image files are supported.', 'warning'); return; }
    if (file.size > MAX_LOGO_BYTES) { Swal.fire('Too Large', 'Logo must be smaller than 500KB.', 'warning'); return; }
    try {
      const dataUrl = await fileToDataUrl(file);
      setSettings(p => ({ ...p, logoBase64: dataUrl }));
    } catch { Swal.fire('Error', 'Failed to read file.', 'error'); }
    finally { if (fileInputRef.current) fileInputRef.current.value = ''; }
  };

  const set = <K extends keyof CompanySettings>(key: K, val: CompanySettings[K]) =>
    setSettings(p => ({ ...p, [key]: val }));

  const tabs: { id: Tab; label: string }[] = [
    { id: 'company', label: 'Company Info' },
    { id: 'serial',  label: 'Serial Numbers' },
  ];

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Company Settings</h1>
        <p className="text-sm text-slate-500 mt-1">
          Manage company information and document serial numbers. For voucher print layout, go to <b>Voucher Print Settings</b>.
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {tabs.map(t => (
          <button
            key={t.id}
            onClick={() => setActiveTab(t.id)}
            className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
              activeTab === t.id
                ? 'bg-white border border-b-white border-slate-200 -mb-px text-indigo-600'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* ─── Company Info Tab ─── */}
      {activeTab === 'company' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 bg-white rounded-xl shadow p-6 space-y-5">
            <h2 className="font-semibold text-slate-700 border-b pb-3">Company Information</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">Company Name <span className="text-rose-500">*</span></label>
                <input value={settings.companyName} onChange={e => set('companyName', e.target.value)} placeholder="e.g. SSPD IT Solution Center"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">Address</label>
                <textarea rows={2} value={settings.companyAddress || ''} onChange={e => set('companyAddress', e.target.value)} placeholder="Street, City, Country"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">Phone</label>
                <input value={settings.companyPhone || ''} onChange={e => set('companyPhone', e.target.value)} placeholder="09-xxxxxxxx"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">Email</label>
                <input value={settings.companyEmail || ''} onChange={e => set('companyEmail', e.target.value)} placeholder="contact@example.com"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">Invoice Title</label>
                <input value={settings.invoiceTitle || ''} onChange={e => set('invoiceTitle', e.target.value)} placeholder="Sales Invoice"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">Tagline (Myanmar)</label>
                <input value={settings.taglineMm || ''} onChange={e => set('taglineMm', e.target.value)} placeholder="ဝန်ဆောင်မှုဌာန"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">Footer Note</label>
                <input value={settings.footerNote || ''} onChange={e => set('footerNote', e.target.value)} placeholder="Thank you for your business"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500" />
              </div>
            </div>

            <div className="border-t pt-5">
              <h3 className="text-sm font-medium text-slate-700 mb-2">Logo</h3>
              <p className="text-xs text-slate-500 mb-3">Upload a PNG/JPG image (recommended: square, &lt; 500KB).</p>
              <div className="flex flex-wrap items-start gap-4">
                <div className="w-32 h-32 border-2 border-dashed border-slate-300 rounded-lg flex items-center justify-center bg-slate-50 overflow-hidden">
                  {settings.logoBase64 ? (
                    <img src={settings.logoBase64} alt="logo preview" className="object-contain w-full h-full" />
                  ) : (
                    <span className="text-xs text-slate-400">No logo</span>
                  )}
                </div>
                <div className="flex flex-col gap-2">
                  <button onClick={() => fileInputRef.current?.click()}
                    className="px-4 py-1.5 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 w-fit">
                    {settings.logoBase64 ? '↑ Change Logo' : '↑ Upload Logo'}
                  </button>
                  {settings.logoBase64 && (
                    <button onClick={() => setSettings(p => ({ ...p, logoBase64: '' }))}
                      className="px-3 py-1.5 text-xs text-rose-600 border border-rose-200 rounded hover:bg-rose-50 w-fit">
                      Remove Logo
                    </button>
                  )}
                  <p className="text-[10px] text-slate-400">PNG/JPG, max 500 KB. Square recommended.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Preview */}
          <div className="bg-white rounded-xl shadow p-6">
            <h2 className="font-semibold text-slate-700 border-b pb-3 mb-4">Preview</h2>
            <div className="border rounded-lg p-4 bg-gradient-to-b from-slate-50 to-white">
              <div className="text-center border-b border-slate-300 pb-3 mb-3">
                {settings.logoBase64 && <img src={settings.logoBase64} alt="logo" style={{ maxHeight: 50, maxWidth: 120, margin: '0 auto 6px', display: 'block' }} />}
                <div className="text-base font-bold text-slate-900">{settings.companyName || '—'}</div>
                {settings.taglineMm && <div className="text-[10px] text-slate-500 mt-0.5">{settings.taglineMm}</div>}
                <div className="text-[10px] text-slate-500 mt-1 leading-snug">
                  {settings.companyPhone && <div>Phone: {settings.companyPhone}</div>}
                  {settings.companyAddress && <div>{settings.companyAddress}</div>}
                  {settings.companyEmail && <div>{settings.companyEmail}</div>}
                </div>
              </div>
              <div className="text-center my-2">
                <div className="text-sm font-bold">{settings.invoiceTitle || 'Sales Invoice'}</div>
                <div className="text-[10px] text-slate-400">Sample Voucher</div>
              </div>
              <div className="space-y-1 text-[11px] text-slate-600">
                <div className="flex justify-between"><span>Invoice No</span><b>INV-0001</b></div>
                <div className="flex justify-between"><span>Date</span><b>—</b></div>
                <div className="flex justify-between"><span>Customer</span><b>Sample Customer</b></div>
              </div>
              <div className="border-t border-slate-300 mt-3 pt-2 text-center text-[10px] text-slate-500">
                {settings.footerNote || 'Thank you'}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ─── Serial Numbers Tab ─── */}
      {activeTab === 'serial' && (() => {
        const digitOptions = [3, 4, 5, 6, 7, 8];
        const preview = (prefix: string, digits: number) => {
          const p = prefix || '???';
          const n = '0'.repeat(digits - 1) + '1';
          return `${p}-${n}`;
        };
        const SerialRow: React.FC<{
          label: string;
          icon: string;
          prefix: string;
          digits: number;
          onPrefix: (v: string) => void;
          onDigits: (v: number) => void;
        }> = ({ label, icon, prefix, digits, onPrefix, onDigits }) => (
          <div className="bg-white rounded-xl shadow p-5 space-y-4">
            <div className="flex items-center gap-2 border-b pb-3">
              <span className="text-lg">{icon}</span>
              <h3 className="font-semibold text-slate-700">{label}</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Prefix</label>
                <input
                  value={prefix}
                  onChange={e => onPrefix(e.target.value.toUpperCase())}
                  maxLength={10}
                  placeholder="e.g. INV"
                  className="w-full border rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500 font-mono uppercase"
                />
                <p className="text-[10px] text-slate-400 mt-0.5">စာလုံး ၁-၁၀ လုံး (uppercase auto)</p>
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Number of Digits</label>
                <select
                  value={digits}
                  onChange={e => onDigits(Number(e.target.value))}
                  className="w-full border rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-indigo-500"
                >
                  {digitOptions.map(d => <option key={d} value={d}>{d} digits</option>)}
                </select>
                <p className="text-[10px] text-slate-400 mt-0.5">zero-padding အရေအတွက်</p>
              </div>
            </div>
            <div className="bg-slate-50 rounded-lg px-4 py-3 flex items-center gap-3">
              <span className="text-xs text-slate-500">Preview:</span>
              <span className="font-mono font-bold text-indigo-600 text-base tracking-wider">{preview(prefix, digits)}</span>
            </div>
          </div>
        );
        return (
          <div className="space-y-4 max-w-2xl">
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-xs text-amber-700">
              ပြောင်းလဲချက်များသည် <b>နောင်လာမည့် document အသစ်များ</b> တွင်သာ သက်ရောက်မည်ဖြစ်ပြီး၊ ရှိပြီးသား records များကို မပြောင်းပါ။
            </div>
            <SerialRow
              label="Sale Invoice"
              icon="🧾"
              prefix={settings.salePrefix ?? 'INV'}
              digits={settings.saleDigits ?? 5}
              onPrefix={v => set('salePrefix', v)}
              onDigits={v => set('saleDigits', v)}
            />
            <SerialRow
              label="Purchase Order"
              icon="📦"
              prefix={settings.purchasePrefix ?? 'PUR'}
              digits={settings.purchaseDigits ?? 5}
              onPrefix={v => set('purchasePrefix', v)}
              onDigits={v => set('purchaseDigits', v)}
            />
            <SerialRow
              label="Booking / Service Job"
              icon="🔧"
              prefix={settings.bookingPrefix ?? 'BK'}
              digits={settings.bookingDigits ?? 6}
              onPrefix={v => set('bookingPrefix', v)}
              onDigits={v => set('bookingDigits', v)}
            />
          </div>
        );
      })()}

      {/* Hidden file input */}
      <input ref={fileInputRef} type="file" accept="image/*" onChange={handleLogoChange} className="hidden" aria-hidden="true" />

      {/* ─── Save / Reload buttons ─── */}
      <div className="flex gap-3">
        <button
          onClick={handleSave}
          disabled={saving || loading}
          className="px-5 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50"
        >
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
        <button
          onClick={load}
          disabled={loading || saving}
          className="px-5 py-2 border border-slate-300 text-slate-600 rounded-lg text-sm font-medium hover:bg-slate-50 disabled:opacity-50"
        >
          Reload
        </button>
      </div>
    </div>
  );
};

export default CompanySettingsPage;
