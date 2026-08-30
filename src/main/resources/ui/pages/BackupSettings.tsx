import React, { useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import {
  AlertTriangle,
  Archive,
  CalendarClock,
  CheckCircle2,
  Clock3,
  DatabaseBackup,
  FileClock,
  FolderCheck,
  FolderX,
  HardDriveDownload,
  RefreshCw,
  RotateCcw,
  Save,
  ShieldCheck,
  Upload,
} from 'lucide-react';
import { backupService } from '../services/api';

const DAYS_OF_WEEK = ['တနင်္လာ', 'အင်္ဂါ', 'ဗုဒ္ဓဟူး', 'ကြာသပတေး', 'သောကြာ', 'စနေ', 'တနင်္ဂနွေ'];
const MONTHS = ['ဇန်နဝါရီ', 'ဖေဖော်ဝါရီ', 'မတ်', 'ဧပြီ', 'မေ', 'ဇွန်', 'ဇူလိုင်', 'ဩဂုတ်', 'စက်တင်ဘာ', 'အောက်တိုဘာ', 'နိုဝင်ဘာ', 'ဒီဇင်ဘာ'];

type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

type BackupFile = {
  fileName: string;
  sizeBytes?: number;
  modifiedAt?: string;
  ageDays?: number;
};

type BackupSettingsDTO = {
  id?: number;
  frequency: Frequency;
  dayValue: number;
  monthValue: number;
  backupTime: string;
  backupDir: string;
  enabled: boolean;
  keepDays: number;
  mysqldumpPath: string;
  nextRunAt?: string | null;
  backupDirExists?: boolean;
  backupDirWritable?: boolean;
  lastBackupFile?: string | null;
  lastBackupAt?: string | null;
  lastBackupSizeBytes?: number | null;
  backupCount?: number;
  dailyEnabled: boolean;
  dailyTime: string;
  weeklyEnabled: boolean;
  weeklyDay: number;
  weeklyTime: string;
  monthlyEnabled: boolean;
  monthlyDay: number;
  monthlyTime: string;
};

const defaultSettings: BackupSettingsDTO = {
  frequency: 'DAILY',
  dayValue: 1,
  monthValue: 1,
  backupTime: '02:00',
  backupDir: './backups',
  enabled: true,
  keepDays: 30,
  mysqldumpPath: '',
  dailyEnabled: true,
  dailyTime: '05:30',
  weeklyEnabled: true,
  weeklyDay: 7,
  weeklyTime: '05:40',
  monthlyEnabled: true,
  monthlyDay: 1,
  monthlyTime: '05:50',
};

const frequencyLabels: Record<Frequency, string> = {
  DAILY: 'နေ့စဉ်',
  WEEKLY: 'အပတ်စဉ်',
  MONTHLY: 'လစဉ်',
  YEARLY: 'နှစ်စဉ်',
};

const normalizeFile = (file: BackupFile | string): BackupFile =>
  typeof file === 'string' ? { fileName: file } : file;

const formatBytes = (bytes?: number | null) => {
  if (!bytes || bytes <= 0) return '-';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
};

const formatDateTime = (value?: string | null) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('my-MM', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
};

const scheduleSummary = (settings: BackupSettingsDTO) => {
  if (!settings.enabled) return 'Auto backup ပိတ်ထားသည်';
  const daily = settings.dailyEnabled ? `နေ့စဉ် ${settings.dailyTime}` : 'နေ့စဉ် ပိတ်ထားသည်';
  const weekly = settings.weeklyEnabled ? `${DAYS_OF_WEEK[(settings.weeklyDay || 7) - 1]}နေ့ ${settings.weeklyTime}` : 'အပတ်စဉ် ပိတ်ထားသည်';
  const monthly = settings.monthlyEnabled ? `လတိုင်း ${settings.monthlyDay || 1} ရက်နေ့ ${settings.monthlyTime}` : 'လစဉ် ပိတ်ထားသည်';
  return `${daily} · ${weekly} · ${monthly}`;
};

const StatusTile = ({
  icon,
  label,
  value,
  tone = 'slate',
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  tone?: 'slate' | 'green' | 'amber' | 'red' | 'blue';
}) => {
  const tones = {
    slate: 'border-slate-200 bg-white text-slate-700',
    green: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    amber: 'border-amber-200 bg-amber-50 text-amber-800',
    red: 'border-rose-200 bg-rose-50 text-rose-800',
    blue: 'border-sky-200 bg-sky-50 text-sky-800',
  };
  return (
    <div className={`min-h-[104px] rounded-lg border p-4 ${tones[tone]}`}>
      <div className="flex items-center gap-2 text-xs font-medium opacity-80">
        {icon}
        <span>{label}</span>
      </div>
      <div className="mt-3 text-sm font-semibold leading-6 text-slate-900">{value}</div>
    </div>
  );
};

const BackupSettings: React.FC = () => {
  const [settings, setSettings] = useState<BackupSettingsDTO>(defaultSettings);
  const [backups, setBackups] = useState<BackupFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importFilePath, setImportFilePath] = useState('');

  useEffect(() => {
    loadSettings();
    loadBackups();
  }, []);

  const loadSettings = async () => {
    try {
      const res = await backupService.getSettings();
      if (res.success) setSettings({ ...defaultSettings, ...(res.data ?? {}) });
    } catch {
      Swal.fire('ဖတ်ယူမရပါ', 'Backup settings ကိုဖတ်ယူရာတွင် အမှားရှိသည်', 'error');
    }
  };

  const loadBackups = async () => {
    try {
      const res = await backupService.listBackups();
      if (res.success) setBackups((res.data ?? []).map(normalizeFile));
    } catch {
      setBackups([]);
    }
  };

  const refreshAll = async () => {
    await Promise.all([loadSettings(), loadBackups()]);
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      const res = await backupService.saveSettings(settings);
      if (res.success) {
        setSettings({ ...defaultSettings, ...(res.data ?? {}) });
        Swal.fire('သိမ်းပြီးပါပြီ', 'Auto backup အချိန်ဇယားကို သိမ်းဆည်းပြီးပါပြီ', 'success');
        refreshAll();
      } else {
        Swal.fire('မအောင်မြင်ပါ', res.message, 'error');
      }
    } catch {
      Swal.fire('မအောင်မြင်ပါ', 'Settings သိမ်းရာတွင် အမှားရှိသည်', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleRunNow = async () => {
    setRunning(true);
    try {
      const res = await backupService.runNow();
      Swal.fire(res.success ? 'Backup ပြီးပါပြီ' : 'Backup မအောင်မြင်ပါ', res.message, res.success ? 'success' : 'error');
      if (res.success) refreshAll();
    } catch {
      Swal.fire('Backup မအောင်မြင်ပါ', 'mysqldump path သို့မဟုတ် database permission ကိုစစ်ဆေးပါ', 'error');
    } finally {
      setRunning(false);
    }
  };

  const handleImport = async () => {
    if (!importFile) {
      Swal.fire('File ရွေးပါ', '.sql.gz backup file တစ်ခုရွေးရန်လိုသည်', 'error');
      return;
    }

    const confirmed = await Swal.fire({
      title: 'Database ပြန်တင်မည်လား',
      text: 'လက်ရှိ database ကိုရွေးထားသော SQL file ထဲက data ဖြင့် အစားထိုးနိုင်ပါသည်။ ပြန်မလုပ်နိုင်သောလုပ်ဆောင်ချက်ဖြစ်နိုင်သည်။',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'ပြန်တင်မည်',
      cancelButtonText: 'မလုပ်တော့ပါ',
      confirmButtonColor: '#b45309',
    });
    if (!confirmed.isConfirmed) return;

    setImporting(true);
    try {
      const res = await backupService.importBackup(importFile);
      Swal.fire(res.success ? 'Restore ပြီးပါပြီ' : 'Restore မအောင်မြင်ပါ', res.message, res.success ? 'success' : 'error');
      if (res.success) {
        setImportFile(null);
        setImportFilePath('');
      }
    } catch {
      Swal.fire('Restore မအောင်မြင်ပါ', 'SQL file, mysql path, database permission တို့ကိုစစ်ဆေးပါ', 'error');
    } finally {
      setImporting(false);
    }
  };

  const set = <K extends keyof BackupSettingsDTO>(key: K, value: BackupSettingsDTO[K]) => {
    setSettings((previous) => ({ ...previous, [key]: value }));
  };

  const folderTone = settings.backupDirWritable ? 'green' : settings.backupDirExists ? 'amber' : 'red';
  const folderText = settings.backupDirWritable
    ? 'Folder အသုံးပြုနိုင်သည်'
    : settings.backupDirExists
      ? 'Folder ရှိသော်လည်း write permission စစ်ရန်လိုသည်'
      : 'Folder မရှိသေးပါ၊ backup ယူချိန်တွင် ဖန်တီးရန်ကြိုးစားမည်';

  const normalizedBackups = useMemo(() => backups.map(normalizeFile), [backups]);

  return (
    <div className="ml-0 mr-0 min-h-screen w-full bg-slate-50 py-4 pr-4 sm:py-6 sm:pr-6">
      <div className="ml-0 mr-0 w-full space-y-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">အလိုအလျောက် Backup Settings</h1>
            <p className="mt-1 text-sm text-slate-600">{scheduleSummary(settings)}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={refreshAll}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              <RefreshCw size={16} />
              ပြန်ဖတ်ရန်
            </button>
            <button
              type="button"
              onClick={handleRunNow}
              disabled={running}
              className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            >
              <DatabaseBackup size={16} />
              {running ? 'Backup ယူနေသည်...' : 'ယခု Backup ယူမည်'}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
          <StatusTile
            icon={settings.enabled ? <ShieldCheck size={16} /> : <AlertTriangle size={16} />}
            label="Auto Backup"
            value={settings.enabled ? 'ဖွင့်ထားသည်' : 'ပိတ်ထားသည်'}
            tone={settings.enabled ? 'green' : 'amber'}
          />
          <StatusTile
            icon={<CalendarClock size={16} />}
            label="နောက်တစ်ကြိမ် Backup"
            value={settings.nextRunAt ? formatDateTime(settings.nextRunAt) : '-'}
            tone="blue"
          />
          <StatusTile
            icon={settings.backupDirWritable ? <FolderCheck size={16} /> : <FolderX size={16} />}
            label="Backup Folder"
            value={folderText}
            tone={folderTone}
          />
          <StatusTile
            icon={<Archive size={16} />}
            label="Backup File"
            value={`${settings.backupCount ?? normalizedBackups.length} ခု သိမ်းထားသည်`}
            tone="slate"
          />
        </div>

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.3fr_0.9fr]">
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-200 pb-4">
              <div>
                <h2 className="text-lg font-semibold text-slate-900">Backup အချိန်ဇယား</h2>
                <p className="mt-1 text-sm text-slate-500">လုပ်ငန်းပိတ်ချိန်တွင် backup ယူရန်အတွက် အချိန်နှင့် retention ကိုသတ်မှတ်ပါ</p>
              </div>
              <label className="inline-flex cursor-pointer items-center gap-3">
                <input
                  type="checkbox"
                  checked={settings.enabled}
                  onChange={(event) => set('enabled', event.target.checked)}
                  className="h-5 w-5 rounded border-slate-300 accent-emerald-600"
                />
                <span className="text-sm font-medium text-slate-700">Auto Backup ဖွင့်မည်</span>
              </label>
            </div>

            <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="md:col-span-2 rounded-lg border border-indigo-100 bg-indigo-50 p-4">
                <p className="text-sm font-bold text-indigo-900">Backup အချိန်ဇယား အသေးစိတ်</p>
                <p className="mt-1 text-xs text-indigo-700">Schedule တစ်ခုချင်းစီကို ဖွင့်/ပိတ်ပြီး ရက်နှင့်အချိန် သီးခြားသတ်မှတ်နိုင်သည်။</p>
              </div>

              <label className="flex items-center gap-3 rounded-lg border border-slate-200 p-3 md:col-span-2">
                <input type="checkbox" checked={settings.dailyEnabled} onChange={(event) => set('dailyEnabled', event.target.checked)} className="h-5 w-5 accent-emerald-600" />
                <span className="w-32 text-sm font-bold text-slate-700">နေ့စဉ် Backup</span>
                <input type="time" value={settings.dailyTime} onChange={(event) => set('dailyTime', event.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
                <span className="text-xs text-slate-500">နေ့တိုင်း</span>
              </label>

              <label className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 p-3 md:col-span-2">
                <input type="checkbox" checked={settings.weeklyEnabled} onChange={(event) => set('weeklyEnabled', event.target.checked)} className="h-5 w-5 accent-emerald-600" />
                <span className="w-32 text-sm font-bold text-slate-700">အပတ်စဉ် Backup</span>
                <select value={settings.weeklyDay} onChange={(event) => set('weeklyDay', Number(event.target.value))} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
                  {DAYS_OF_WEEK.map((day, index) => <option key={day} value={index + 1}>{day}</option>)}
                </select>
                <input type="time" value={settings.weeklyTime} onChange={(event) => set('weeklyTime', event.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
              </label>

              <label className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 p-3 md:col-span-2">
                <input type="checkbox" checked={settings.monthlyEnabled} onChange={(event) => set('monthlyEnabled', event.target.checked)} className="h-5 w-5 accent-emerald-600" />
                <span className="w-32 text-sm font-bold text-slate-700">လစဉ် Backup</span>
                <select value={settings.monthlyDay} onChange={(event) => set('monthlyDay', Number(event.target.value))} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
                  {Array.from({ length: 28 }, (_, index) => index + 1).map((day) => <option key={day} value={day}>{day} ရက်နေ့</option>)}
                </select>
                <input type="time" value={settings.monthlyTime} onChange={(event) => set('monthlyTime', event.target.value)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
              </label>

              <label className="space-y-1 md:col-span-2">
                <span className="text-sm font-medium text-slate-700">Backup သိမ်းမည့် Folder</span>
                <input
                  value={settings.backupDir}
                  onChange={(event) => set('backupDir', event.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                  placeholder="./backups"
                />
                <span className="block text-xs text-slate-500">Server machine ပေါ်ရှိ folder path ဖြစ်ရမည်။ External drive သို့ NAS path သုံးပါက write permission ရှိရန်လိုသည်။</span>
              </label>

              <label className="space-y-1">
                <span className="text-sm font-medium text-slate-700">သိမ်းထားမည့်ရက်</span>
                <input
                  type="number"
                  min={1}
                  max={3650}
                  value={settings.keepDays}
                  onChange={(event) => set('keepDays', Number(event.target.value))}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                />
              </label>

              <label className="space-y-1">
                <span className="text-sm font-medium text-slate-700">mysqldump Path</span>
                <input
                  value={settings.mysqldumpPath ?? ''}
                  onChange={(event) => set('mysqldumpPath', event.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none focus:ring-2 focus:ring-emerald-100"
                  placeholder="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"
                />
                <span className="block text-xs text-slate-500">Blank ထားပါက server PATH ထဲမှ အလိုအလျောက်ရှာမည်။</span>
              </label>
            </div>

            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={handleSave}
                disabled={loading}
                className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
              >
                <Save size={16} />
                {loading ? 'သိမ်းနေသည်...' : 'Settings သိမ်းမည်'}
              </button>
            </div>
          </section>

          <section className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
              <h2 className="text-lg font-semibold text-slate-900">နောက်ဆုံး Backup</h2>
              <div className="mt-4 space-y-4 text-sm">
                <div className="flex items-start gap-3">
                  <FileClock className="mt-0.5 text-slate-500" size={18} />
                  <div>
                    <p className="font-medium text-slate-900">{settings.lastBackupFile || 'Backup file မရှိသေးပါ'}</p>
                    <p className="text-slate-500">{formatDateTime(settings.lastBackupAt)} · {formatBytes(settings.lastBackupSizeBytes)}</p>
                  </div>
                </div>
                <div className="flex items-start gap-3">
                  <Clock3 className="mt-0.5 text-slate-500" size={18} />
                  <div>
                    <p className="font-medium text-slate-900">Retention</p>
                    <p className="text-slate-500">{settings.keepDays} ရက်ကျော်သော .sql files များကို backup အောင်မြင်ပြီးနောက်ရှင်းမည်</p>
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-lg border border-amber-200 bg-amber-50 p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <AlertTriangle className="mt-0.5 text-amber-700" size={20} />
                <div>
                  <h2 className="text-lg font-semibold text-amber-950">Database Restore</h2>
                  <p className="mt-1 text-sm text-amber-900">SQL file ပြန်တင်ခြင်းသည် လက်ရှိ data ကိုပြောင်းလဲနိုင်သည်။ လုပ်ငန်းမစခင် backup အသစ်တစ်ခုယူထားပါ။</p>
                </div>
              </div>
              <div className="mt-4 space-y-3">
                <input
                  type="file"
                  accept=".sql.gz,application/gzip"
                  onChange={(event) => {
                    const file = event.target.files?.[0] ?? null;
                    setImportFile(file);
                    setImportFilePath(file ? event.target.value || file.name : '');
                  }}
                  className="block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-white file:px-4 file:py-2 file:text-sm file:font-medium file:text-slate-700"
                />
                <div>
                  <label htmlFor="restore-file-path" className="mb-1.5 block text-sm font-medium text-amber-950">
                    ရွေးထားသော SQL file path
                  </label>
                  <input
                    id="restore-file-path"
                    type="text"
                    value={importFilePath}
                    readOnly
                    placeholder="SQL file မရွေးရသေးပါ"
                    title={importFilePath}
                    className="block w-full rounded-lg border border-amber-300 bg-white px-3 py-2.5 text-sm text-slate-700 outline-none placeholder:text-slate-400"
                  />
                  {importFile && importFilePath.includes('fakepath') && (
                    <p className="mt-1 text-xs text-amber-800">Browser လုံခြုံရေးကြောင့် full local path အစား ရွေးထားသော file name ကိုသာ ပြနိုင်ပါသည်။</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={handleImport}
                  disabled={importing || !importFile}
                  className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-amber-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-amber-800 disabled:opacity-50"
                >
                  <Upload size={16} />
                  {importing ? 'Restore လုပ်နေသည်...' : 'SQL File ပြန်တင်မည်'}
                </button>
              </div>
            </div>
          </section>
        </div>

        <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-3 border-b border-slate-200 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Backup History</h2>
              <p className="mt-1 text-sm text-slate-500">Daily, Weekly, Monthly နှင့် Safety folder များထဲရှိ .sql.gz files များ</p>
            </div>
            <button
              type="button"
              onClick={loadBackups}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              <RefreshCw size={16} />
              List ပြန်ဖတ်ရန်
            </button>
          </div>

          {normalizedBackups.length === 0 ? (
            <div className="flex flex-col items-center justify-center px-5 py-12 text-center">
              <HardDriveDownload className="text-slate-300" size={44} />
              <p className="mt-3 text-sm font-medium text-slate-700">Backup file မတွေ့သေးပါ</p>
              <p className="mt-1 text-sm text-slate-500">ယခု Backup ယူမည်ကိုနှိပ်ပြီး ပထမဆုံး backup စမ်းနိုင်သည်။</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-100 text-left text-xs font-semibold uppercase text-slate-600">
                  <tr>
                    <th className="px-5 py-3">File</th>
                    <th className="px-5 py-3">Size</th>
                    <th className="px-5 py-3">သိမ်းထားသည့်အချိန်</th>
                    <th className="px-5 py-3">အသက်</th>
                    <th className="px-5 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {normalizedBackups.map((file, index) => (
                    <tr key={`${file.fileName}-${file.modifiedAt ?? index}`} className="hover:bg-slate-50">
                      <td className="px-5 py-3 font-medium text-slate-900">{file.fileName}</td>
                      <td className="px-5 py-3 text-slate-600">{formatBytes(file.sizeBytes)}</td>
                      <td className="px-5 py-3 text-slate-600">{formatDateTime(file.modifiedAt)}</td>
                      <td className="px-5 py-3 text-slate-600">{file.ageDays != null ? `${file.ageDays} ရက်` : '-'}</td>
                      <td className="px-5 py-3">
                        {index === 0 ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-700">
                            <CheckCircle2 size={13} />
                            နောက်ဆုံး
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">
                            <RotateCcw size={13} />
                            သိမ်းထားသည်
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
};

export default BackupSettings;
