import React, { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, FileUp, Info, RefreshCw, Save, ShieldAlert, Smartphone, Upload } from 'lucide-react';
import Swal from 'sweetalert2';
import { appVersionSettingsService } from '../services/api';

interface VersionSettings {
  versionCode: number;
  versionName: string;
  forceUpdate: boolean;
  changelog: string;
}

const defaultSettings: VersionSettings = {
  versionCode: 5,
  versionName: '1.2.0',
  forceUpdate: false,
  changelog: '',
};

const changelogTemplates = [
  {
    label: 'Split Payment',
    text: [
      '- Cash, KPay, Bank တို့ကို ခွဲ၍ ငွေပေးချေနိုင်ပါပြီ။',
      '- Sale / Purchase / Return / Service Job payment များကို ပိုမိုမှန်ကန်အောင် ပြင်ဆင်ထားသည်။',
      '- Payment transaction နှင့် journal မှတ်တမ်းချိတ်ဆက်မှုများကို တိုးတက်အောင်ပြင်ထားသည်။'
    ].join('\n')
  },
  {
    label: 'Bug Fix',
    text: [
      '- အသုံးပြုနေစဉ်တွေ့ရသော bug များကို ပြင်ဆင်ထားသည်။',
      '- Data save / sync လုပ်ငန်းစဉ်များကို ပိုမိုတည်ငြိမ်အောင် ပြင်ဆင်ထားသည်။',
      '- UI အသုံးပြုရလွယ်ကူစေရန် အချို့နေရာများကို ပြင်ဆင်ထားသည်။'
    ].join('\n')
  },
  {
    label: 'Full Note',
    text: [
      'ဗားရှင်းအသစ်တွင် Cash, KPay, Bank တို့ကို ခွဲ၍ ငွေပေးချေနိုင်ပါပြီ။',
      'Sale/Purchase/Return/Service Job payment များနှင့် Customer Credit Collection ကို ပိုမိုမှန်ကန်အောင် ပြင်ဆင်ထားသည်။',
      'ငွေလွှဲပြောင်းမှုနှင့် Journal မှတ်တမ်းချိတ်ဆက်မှုများကိုလည်း တိုးတက်အောင်ပြင်ထားသည်။'
    ].join('\n')
  }
];

const nextVersionName = (value: string) => {
  const parts = value.trim().split('.').map((p) => Number(p));
  if (parts.length === 0 || parts.some((n) => Number.isNaN(n))) return value;
  const lastIndex = parts.length - 1;
  parts[lastIndex] += 1;
  return parts.join('.');
};

const AppVersionSettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<VersionSettings>(defaultSettings);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [apkExists, setApkExists] = useState(false);
  const [apkFile, setApkFile] = useState<File | null>(null);

  const releaseReady = useMemo(() => (
    settings.versionCode > 0 &&
    settings.versionName.trim().length > 0 &&
    settings.changelog.trim().length > 0 &&
    apkExists
  ), [apkExists, settings]);

  const load = async () => {
    setLoading(true);
    try {
      const [res, apkRes] = await Promise.all([
        appVersionSettingsService.getSettings(),
        appVersionSettingsService.apkExists()
      ]);
      if (res.success && res.data) setSettings(res.data);
      setApkExists(Boolean(apkRes.data));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const handleUploadApk = async () => {
    if (!apkFile) {
      Swal.fire({ icon: 'warning', title: 'APK file ရွေးပါ', timer: 1600, showConfirmButton: false });
      return;
    }
    if (!apkFile.name.toLowerCase().endsWith('.apk')) {
      Swal.fire({ icon: 'warning', title: '.apk file သာ upload လုပ်ပါ', timer: 1600, showConfirmButton: false });
      return;
    }

    setUploading(true);
    try {
      const res = await appVersionSettingsService.uploadApk(apkFile);
      if (res.success) {
        setApkExists(true);
        setApkFile(null);
        Swal.fire({ icon: 'success', title: 'APK upload ပြီးပါပြီ', timer: 1400, showConfirmButton: false });
      } else {
        Swal.fire({ icon: 'error', title: res.message || 'APK upload မအောင်မြင်ပါ' });
      }
    } catch (e: any) {
      Swal.fire({ icon: 'error', title: e?.message || 'APK upload မအောင်မြင်ပါ' });
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!settings.versionName.trim()) {
      Swal.fire({ icon: 'warning', title: 'Version Name ထည့်ပါ', timer: 1600, showConfirmButton: false });
      return;
    }
    if (settings.versionCode < 1) {
      Swal.fire({ icon: 'warning', title: 'Version Code သည် 1 အထက် ဖြစ်ရမည်', timer: 1600, showConfirmButton: false });
      return;
    }
    setSaving(true);
    try {
      const res = await appVersionSettingsService.saveSettings(settings);
      if (res.success) {
        setSettings(res.data);
        Swal.fire({ icon: 'success', title: 'Version settings သိမ်းပြီးပါပြီ', timer: 1400, showConfirmButton: false });
      }
    } catch {
      Swal.fire({ icon: 'error', title: 'သိမ်းမရပါ' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw size={24} className="animate-spin text-indigo-500" />
      </div>
    );
  }

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center">
            <Smartphone size={22} className="text-indigo-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-800">Android App Version</h1>
            <p className="text-sm text-slate-500">Mobile APK release, update dialog, changelog ကိုစီမံရန်</p>
          </div>
        </div>
        <div className="flex gap-2">
          <button onClick={load} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50">
            <RefreshCw size={14} /> Refresh
          </button>
          <button onClick={handleSave} disabled={saving} className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700 disabled:opacity-60">
            {saving ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />}
            Save Version
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[1fr_360px] gap-5">
        <div className="space-y-5">
          <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold text-slate-800">Release Information</h2>
                <p className="text-xs text-slate-500 mt-0.5">Version Code တိုးမှ app က update ရှိသည်ဟုသိပါမည်။</p>
              </div>
              <button
                type="button"
                onClick={() => setSettings((s) => ({ ...s, versionCode: Math.max(1, s.versionCode + 1), versionName: nextVersionName(s.versionName) }))}
                className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800 text-white text-xs font-bold hover:bg-slate-900"
              >
                <RefreshCw size={13} /> Next Version
              </button>
            </div>

            <div className="p-5 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Version Name</label>
                <input
                  type="text"
                  value={settings.versionName}
                  onChange={(e) => setSettings((s) => ({ ...s, versionName: e.target.value }))}
                  placeholder="1.0.1"
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-200 bg-slate-50 text-sm font-semibold focus:outline-none focus:border-indigo-400"
                />
                <p className="text-[11px] text-slate-400 mt-1">User မြင်မည့် version ဖြစ်သည်။ ဥပမာ 1.0.1</p>
              </div>
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Version Code</label>
                <input
                  type="number"
                  min={1}
                  value={settings.versionCode}
                  onChange={(e) => setSettings((s) => ({ ...s, versionCode: Math.max(1, Number(e.target.value) || 1) }))}
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-200 bg-slate-50 text-sm font-semibold focus:outline-none focus:border-indigo-400"
                />
                <p className="text-[11px] text-slate-400 mt-1">App ထဲက BuildConfig.VERSION_CODE ထက်ကြီးရမည်။</p>
              </div>
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold text-slate-800">Release Notes</h2>
                <p className="text-xs text-slate-500 mt-0.5">Mobile update dialog ထဲတွင်ပြမည့် changelog ဖြစ်သည်။</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {changelogTemplates.map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    onClick={() => setSettings((s) => ({ ...s, changelog: item.text }))}
                    className="px-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs font-semibold text-slate-600 hover:bg-indigo-50 hover:text-indigo-700"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <textarea
              rows={8}
              value={settings.changelog}
              onChange={(e) => setSettings((s) => ({ ...s, changelog: e.target.value }))}
              placeholder="- Cash, KPay, Bank တို့ကို ခွဲ၍ ငွေပေးချေနိုင်ပါပြီ။&#10;- Minor bug fixes and performance improvements."
              className="w-full px-3 py-3 rounded-lg border border-slate-200 bg-slate-50 text-sm leading-6 focus:outline-none focus:border-indigo-400 resize-none"
            />
          </div>

          <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5 space-y-4">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold text-slate-800">APK Upload</h2>
                <p className="text-xs text-slate-500 mt-0.5">Upload လုပ်ပြီးသော APK ကို mobile app က download လုပ်မည်။</p>
              </div>
              <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-bold ${apkExists ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                {apkExists ? <CheckCircle2 size={13} /> : <Info size={13} />}
                {apkExists ? 'APK Ready' : 'No APK'}
              </span>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-3">
              <label className="flex items-center gap-3 px-3 py-3 rounded-lg border border-dashed border-slate-300 bg-slate-50 cursor-pointer hover:bg-slate-100">
                <FileUp size={18} className="text-slate-500" />
                <span className="text-sm text-slate-600 truncate">{apkFile ? apkFile.name : 'Choose release .apk file'}</span>
                <input
                  type="file"
                  accept=".apk,application/vnd.android.package-archive"
                  onChange={(e) => setApkFile(e.target.files?.[0] || null)}
                  className="hidden"
                />
              </label>
              <button
                type="button"
                onClick={handleUploadApk}
                disabled={uploading || !apkFile}
                className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-slate-800 text-white text-sm font-bold hover:bg-slate-900 disabled:opacity-50"
              >
                {uploading ? <RefreshCw size={15} className="animate-spin" /> : <Upload size={15} />}
                Upload APK
              </button>
            </div>
            <p className="text-[11px] text-slate-500">
              Server ထဲတွင် <span className="font-mono font-semibold">C:/sspd-apk/servicemgmt.apk</span> အဖြစ်သိမ်းမည်။ APK file name ဘာဖြစ်ဖြစ် upload ပြီးပါက app က <span className="font-mono">/app/servicemgmt.apk</span> မှ download လုပ်ပါမည်။
            </p>
          </div>
        </div>

        <div className="space-y-5">
          <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5">
            <h2 className="text-sm font-bold text-slate-800 mb-3">Mobile Preview</h2>
            <div className="rounded-2xl border border-slate-200 bg-slate-950 p-3">
              <div className="rounded-xl bg-white overflow-hidden">
                <div className={`px-4 py-4 ${settings.forceUpdate ? 'bg-rose-50' : 'bg-indigo-50'}`}>
                  <div className="flex items-center gap-2">
                    <Smartphone size={18} className={settings.forceUpdate ? 'text-rose-600' : 'text-indigo-600'} />
                    <p className={`text-sm font-bold ${settings.forceUpdate ? 'text-rose-800' : 'text-indigo-800'}`}>
                      {settings.forceUpdate ? 'Update လုပ်ရန်လိုအပ်ပါသည်' : 'Update ရှိပါသည်'}
                    </p>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">Version {settings.versionName} (code {settings.versionCode})</p>
                </div>
                <div className="p-4 space-y-3">
                  <pre className="whitespace-pre-wrap text-xs leading-5 text-slate-600 font-sans bg-slate-50 rounded-lg p-3 min-h-[120px]">
                    {settings.changelog || 'Changelog မရေးရသေးပါ'}
                  </pre>
                  <button className={`w-full py-2 rounded-lg text-white text-sm font-bold ${settings.forceUpdate ? 'bg-rose-600' : 'bg-indigo-600'}`}>
                    Download APK
                  </button>
                  {!settings.forceUpdate && <p className="text-center text-xs text-slate-400">နောက်မှလုပ်မည်</p>}
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5 space-y-3">
            <h2 className="text-sm font-bold text-slate-800">Release Checklist</h2>
            <ChecklistRow done={settings.versionName.trim().length > 0} label="Version Name ဖြည့်ပြီး" />
            <ChecklistRow done={settings.versionCode > 0} label="Version Code တိုးပြီး" />
            <ChecklistRow done={settings.changelog.trim().length > 0} label="Changelog ရေးပြီး" />
            <ChecklistRow done={apkExists} label="APK upload ပြီး" />
            <div className={`mt-3 rounded-lg px-3 py-2 flex items-start gap-2 ${releaseReady ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
              {releaseReady ? <CheckCircle2 size={16} className="mt-0.5" /> : <AlertTriangle size={16} className="mt-0.5" />}
              <p className="text-xs font-semibold">
                {releaseReady ? 'Release အတွက်အဆင်သင့်ဖြစ်ပါပြီ။' : 'Release မလုပ်ခင် checklist ကိုပြီးအောင်စစ်ပါ။'}
              </p>
            </div>
          </div>

          <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ShieldAlert size={16} className={settings.forceUpdate ? 'text-rose-600' : 'text-slate-400'} />
                <h2 className="text-sm font-bold text-slate-800">Force Update</h2>
              </div>
              <button
                type="button"
                onClick={() => setSettings((s) => ({ ...s, forceUpdate: !s.forceUpdate }))}
                className={`relative w-12 h-6 rounded-full transition-colors ${settings.forceUpdate ? 'bg-rose-500' : 'bg-slate-300'}`}
              >
                <span className={`absolute top-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${settings.forceUpdate ? 'translate-x-6' : 'translate-x-0.5'}`} />
              </button>
            </div>
            <p className="text-xs text-slate-500 leading-5">
              Force update ဖွင့်ထားလျှင် mobile user က update dialog ကိုပိတ်လို့မရပါ။ အရေးကြီး bug fix, data format change, security update များတွင်သာသုံးပါ။
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

const ChecklistRow: React.FC<{ done: boolean; label: string }> = ({ done, label }) => (
  <div className="flex items-center gap-2 text-sm">
    <span className={`w-5 h-5 rounded-full flex items-center justify-center ${done ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'}`}>
      <CheckCircle2 size={13} />
    </span>
    <span className={done ? 'text-slate-700 font-semibold' : 'text-slate-400'}>{label}</span>
  </div>
);

export default AppVersionSettingsPage;
