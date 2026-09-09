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
  notificationSoundBase64: '',
  voucherConfigJson: '',
  salePrefix: 'INV',
  saleDigits: 5,
  purchasePrefix: 'PUR',
  purchaseDigits: 5,
  poPrefix: 'PO',
  poDigits: 5,
  purchaseReturnPrefix: 'PRN',
  purchaseReturnDigits: 5,
  bookingPrefix: 'BK',
  bookingDigits: 6,
  poFinalApprovalThreshold: null,
  serviceSupervisorApprovalRequired: true,
  serviceAllowDeliveryWithDue: false,
  pickupDepositPercent: 30,
  mailSmtpHost: '',
  mailSmtpPort: 587,
  mailSmtpUsername: '',
  mailSmtpPassword: '',
  mailSmtpFrom: '',
  mailSmtpAuth: true,
  mailSmtpStartTls: true,
  mailSmtpConfigured: false,
};

const MAX_LOGO_BYTES = 500 * 1024;
const MAX_SOUND_BYTES = 2 * 1024 * 1024;

const fileToDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(String(reader.result || ''));
    reader.readAsDataURL(file);
  });

type Tab = 'company' | 'serial' | 'email';

const CompanySettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('company');
  const [settings, setSettings] = useState<CompanySettings>(emptySettings);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testingMail, setTestingMail] = useState(false);
  const [testTo, setTestTo] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const soundInputRef = useRef<HTMLInputElement>(null);

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
      const payload = { ...settings };
      if (!payload.mailSmtpPassword?.trim()) {
        delete (payload as { mailSmtpPassword?: string }).mailSmtpPassword;
      }
      const res = await companySettingsService.saveSettings(payload);
      if (res.success) {
        const merged = {
          ...emptySettings,
          ...(res.data || settings),
          mailSmtpPassword: '',
        };
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

  const handleTestMail = async () => {
    const to = (testTo || settings.mailSmtpUsername || settings.companyEmail || '').trim();
    if (!to || !to.includes('@')) {
      Swal.fire('Required', 'Test လက်ခံမည့် Gmail / email ထည့်ပါ', 'warning');
      return;
    }
    setTestingMail(true);
    try {
      const res = await companySettingsService.testMail(to);
      if (res.success) Swal.fire('Sent', res.message || 'Test email ပို့ပြီးပါပြီ', 'success');
      else Swal.fire('Error', res.message || 'Test mail failed', 'error');
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Test mail failed';
      Swal.fire('Error', msg, 'error');
    } finally {
      setTestingMail(false);
    }
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

  const handleSoundChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('audio/')) {
      Swal.fire('Invalid', 'MP3 / WAV / OGG အသံဖိုင်သာ တင်ပါ။', 'warning');
      return;
    }
    if (file.size > MAX_SOUND_BYTES) {
      Swal.fire('Too Large', 'အသံဖိုင် 2MB အောက် ဖြစ်ရပါမည်။', 'warning');
      return;
    }
    try {
      const dataUrl = await fileToDataUrl(file);
      setSettings(p => ({ ...p, notificationSoundBase64: dataUrl }));
    } catch {
      Swal.fire('Error', 'ဖိုင်ဖတ်မရပါ။', 'error');
    } finally {
      if (soundInputRef.current) soundInputRef.current.value = '';
    }
  };

  const playSoundPreview = async () => {
    if (!settings.notificationSoundBase64) {
      Swal.fire('Info', 'အသံ မတင်ရသေးပါ။', 'info');
      return;
    }
    try {
      const audio = new Audio(settings.notificationSoundBase64);
      await audio.play();
    } catch {
      Swal.fire('Error', 'အသံဖွင့်မရပါ။ ဘရောက်ဇာက အသံခွင့်ပြုထားပါ။', 'error');
    }
  };

  const set = <K extends keyof CompanySettings>(key: K, val: CompanySettings[K]) =>
    setSettings(p => ({ ...p, [key]: val }));

  const tabs: { id: Tab; label: string }[] = [
    { id: 'company', label: 'Company Info' },
    { id: 'serial',  label: 'Serial Numbers' },
    { id: 'email',   label: 'Email (Gmail)' },
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
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">
                  PO Final Approval Threshold <span className="text-slate-400 font-normal">(PO Final Approval Threshold)</span>
                </label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={settings.poFinalApprovalThreshold ?? ''}
                  onChange={e => {
                    const raw = e.target.value.trim();
                    set('poFinalApprovalThreshold', raw === '' ? null : Math.max(0, Number(raw) || 0));
                  }}
                  placeholder="Blank = no second approval"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
                />
                <p className="text-[10px] text-slate-400 mt-1">
                  PO စုစုပေါင်း ဤပမာဏနှင့် အထက်ဖြစ်ပါက ဒုတိယအဆင့် အတည်ပြုချက် လိုအပ်သည်။ ဗလာထားပါက second approval မလို။
                </p>
              </div>
              <div className="md:col-span-2 rounded-xl border border-purple-200 bg-purple-50 p-4">
                <label className="flex cursor-pointer items-start gap-3">
                  <input type="checkbox" checked={settings.serviceSupervisorApprovalRequired !== false}
                    onChange={e => set('serviceSupervisorApprovalRequired', e.target.checked)}
                    className="mt-1 h-4 w-4 rounded border-slate-300 text-purple-600" />
                  <span><b className="block text-sm text-purple-950">Service Job Supervisor Final Approval လိုအပ်သည်</b>
                    <span className="mt-1 block text-xs text-purple-700">ဖွင့်ထားလျှင် Lead Final Check ပြီးနောက် Supervisor က အတည်ပြု/ပြန်ပြင်ရန် ဆုံးဖြတ်ရမည်။ ပိတ်ထားလျှင် Lead Final Check ပြီးတာနဲ့ Job COMPLETED အလိုအလျောက်ဖြစ်မည်။</span>
                  </span>
                </label>
              </div>
              <div className="md:col-span-2 rounded-xl border border-amber-200 bg-amber-50 p-4">
                <label className="flex cursor-pointer items-start gap-3">
                  <input type="checkbox" checked={settings.serviceAllowDeliveryWithDue === true}
                    onChange={e => set('serviceAllowDeliveryWithDue', e.target.checked)}
                    className="mt-1 h-4 w-4 rounded border-slate-300 text-amber-600" />
                  <span><b className="block text-sm text-amber-950">အကြွေးကျန် Service Job ပေးအပ်ခွင့်</b>
                    <span className="mt-1 block text-xs text-amber-700">ဖွင့်ထားလျှင် Manager approval reason မှတ်တမ်းတင်ပြီးမှ Due ရှိသော completed job ကို ပေးအပ်နိုင်သည်။</span>
                  </span>
                </label>
              </div>
              <div className="md:col-span-2 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
                <label className="block text-sm font-bold text-emerald-950">စရံရာခိုင်နှုန်း (%)</label>
                <p className="mt-1 mb-2 text-xs text-emerald-800">
                  ဆိုင်မှာလာယူ နှင့် ပစ္စည်းလက်ခံချိန် ငွေရှင်း တို့တွင် ဤရာခိုင်နှုန်းကို စရံအဖြစ် ကြိုလွှဲရမည်။ ကျန်ငွေ လက်ခံချိန် ပေးချေပါမည်။
                </p>
                <input
                  type="number"
                  min={1}
                  max={100}
                  step="0.01"
                  value={settings.pickupDepositPercent ?? 30}
                  onChange={e => {
                    const n = Number(e.target.value);
                    const clamped = Number.isFinite(n) ? Math.min(100, Math.max(1, n)) : 30;
                    set('pickupDepositPercent', clamped);
                  }}
                  className="w-full max-w-xs border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-emerald-500"
                />
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

            <div className="border-t pt-5">
              <h3 className="text-sm font-medium text-slate-700 mb-2">Customer Order အသံ</h3>
              <p className="text-xs text-slate-500 mb-3">
                App အော်ဒါရောက်သည့်အခါ ဖွင့်မည့် notification အသံ။ မြန်မာ voice ရှိပါက စကားပြောအသံလည်း ထွက်ပါမည်။
                အသံမတင်ထားပါက မူရင်း beep သံ သုံးမည်။
              </p>
              <div className="flex flex-wrap items-start gap-4">
                <div className="min-w-[10rem] rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center">
                  {settings.notificationSoundBase64 ? (
                    <span className="text-xs font-bold text-emerald-700">အသံ တင်ပြီးပါပြီ</span>
                  ) : (
                    <span className="text-xs text-slate-400">အသံ မရှိသေး</span>
                  )}
                </div>
                <div className="flex flex-col gap-2">
                  <button type="button" onClick={() => soundInputRef.current?.click()}
                    className="px-4 py-1.5 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 w-fit">
                    {settings.notificationSoundBase64 ? '↑ အသံ ပြောင်းမည်' : '↑ အသံ Upload'}
                  </button>
                  {settings.notificationSoundBase64 && (
                    <>
                      <button type="button" onClick={() => void playSoundPreview()}
                        className="px-3 py-1.5 text-xs font-bold text-indigo-700 border border-indigo-200 rounded hover:bg-indigo-50 w-fit">
                        နားထောင်မည်
                      </button>
                      <button type="button" onClick={() => setSettings(p => ({ ...p, notificationSoundBase64: '' }))}
                        className="px-3 py-1.5 text-xs text-rose-600 border border-rose-200 rounded hover:bg-rose-50 w-fit">
                        အသံ ဖယ်မည်
                      </button>
                    </>
                  )}
                  <p className="text-[10px] text-slate-400">MP3 / WAV / OGG, max 2 MB။ Save နှိပ်မှ သိမ်းမည်။</p>
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
              label="Purchase"
              icon="🛒"
              prefix={settings.purchasePrefix ?? 'PUR'}
              digits={settings.purchaseDigits ?? 5}
              onPrefix={v => set('purchasePrefix', v)}
              onDigits={v => set('purchaseDigits', v)}
            />
            <SerialRow
              label="Purchase Order"
              icon="📦"
              prefix={settings.poPrefix ?? 'PO'}
              digits={settings.poDigits ?? 5}
              onPrefix={v => set('poPrefix', v)}
              onDigits={v => set('poDigits', v)}
            />
            <SerialRow
              label="Purchase Return"
              icon="↩️"
              prefix={settings.purchaseReturnPrefix ?? 'PRN'}
              digits={settings.purchaseReturnDigits ?? 5}
              onPrefix={v => set('purchaseReturnPrefix', v)}
              onDigits={v => set('purchaseReturnDigits', v)}
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

      {/* ─── Email / Gmail SMTP Tab ─── */}
      {activeTab === 'email' && (
        <div className="max-w-2xl space-y-4">
          <div className="bg-sky-50 border border-sky-200 rounded-lg px-4 py-3 text-xs text-sky-800 space-y-1">
            <p>
              Customer password-reset email အတွက် <b>Gmail App Password</b> ထည့်ပါ။
              Account password မဟုတ်ပါ — Google Account → Security → 2-Step Verification → App passwords မှ ၁၆ လုံးကုဒ်ယူပါ။
            </p>
            <p>
              Status:{' '}
              {settings.mailSmtpConfigured ? (
                <span className="font-semibold text-emerald-700">Configured</span>
              ) : (
                <span className="font-semibold text-amber-700">Not configured</span>
              )}
            </p>
          </div>

          <div className="bg-white rounded-xl shadow p-6 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
              <h2 className="font-semibold text-slate-700">SMTP (Gmail)</h2>
              <button
                type="button"
                onClick={() => setSettings(p => ({
                  ...p,
                  mailSmtpHost: 'smtp.gmail.com',
                  mailSmtpPort: 587,
                  mailSmtpAuth: true,
                  mailSmtpStartTls: true,
                }))}
                className="text-xs px-3 py-1.5 border border-slate-300 rounded-lg text-slate-600 hover:bg-slate-50"
              >
                Gmail defaults
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">SMTP Host</label>
                <input
                  value={settings.mailSmtpHost || ''}
                  onChange={e => set('mailSmtpHost', e.target.value)}
                  placeholder="smtp.gmail.com"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">Port</label>
                <input
                  type="number"
                  value={settings.mailSmtpPort ?? 587}
                  onChange={e => set('mailSmtpPort', Number(e.target.value) || 587)}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1">From (optional)</label>
                <input
                  value={settings.mailSmtpFrom || ''}
                  onChange={e => set('mailSmtpFrom', e.target.value)}
                  placeholder="same as Gmail"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">Gmail address</label>
                <input
                  type="email"
                  value={settings.mailSmtpUsername || ''}
                  onChange={e => set('mailSmtpUsername', e.target.value)}
                  placeholder="you@gmail.com"
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-600 mb-1">App Password</label>
                <input
                  type="password"
                  autoComplete="new-password"
                  value={settings.mailSmtpPassword || ''}
                  onChange={e => set('mailSmtpPassword', e.target.value)}
                  placeholder={settings.mailSmtpConfigured ? '******** (leave blank to keep)' : 'xxxx xxxx xxxx xxxx'}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500 font-mono"
                />
                <p className="text-[10px] text-slate-400 mt-1">
                  သိမ်းပြီးသား password ကို API က ပြန်မပေးပါ။ အသစ်ထည့်မှသာ ပြောင်းမည်။
                </p>
              </div>
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={settings.mailSmtpAuth !== false}
                  onChange={e => set('mailSmtpAuth', e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-indigo-600"
                />
                SMTP Auth
              </label>
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={settings.mailSmtpStartTls !== false}
                  onChange={e => set('mailSmtpStartTls', e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-indigo-600"
                />
                STARTTLS
              </label>
            </div>
          </div>

          <div className="bg-white rounded-xl shadow p-6 space-y-3">
            <h3 className="font-semibold text-slate-700">Test email</h3>
            <p className="text-xs text-slate-500">Save Settings ပြီးမှ စမ်းပါ။ Inbox / Spam စစ်ပါ။</p>
            <div className="flex flex-wrap gap-2">
              <input
                type="email"
                value={testTo}
                onChange={e => setTestTo(e.target.value)}
                placeholder={settings.mailSmtpUsername || 'you@gmail.com'}
                className="flex-1 min-w-[200px] border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-indigo-500"
              />
              <button
                type="button"
                onClick={handleTestMail}
                disabled={testingMail || saving}
                className="px-4 py-2 text-sm font-medium border border-indigo-300 text-indigo-700 rounded-lg hover:bg-indigo-50 disabled:opacity-50"
              >
                {testingMail ? 'Sending...' : 'Send test'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Hidden file inputs */}
      <input ref={fileInputRef} type="file" accept="image/*" onChange={handleLogoChange} className="hidden" aria-hidden="true" />
      <input ref={soundInputRef} type="file" accept="audio/*" onChange={handleSoundChange} className="hidden" aria-hidden="true" />

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
