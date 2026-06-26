import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import {
  BadgeCheck,
  FileText,
  Printer,
  ReceiptText,
  RefreshCw,
  RotateCcw,
  Ruler,
  Save,
  Settings2,
  SlidersHorizontal,
  Type,
} from 'lucide-react';
import {
  DocumentType,
  VoucherSettingDto,
  voucherSettingService,
} from '../services/voucherSettingService';

const FONT_FAMILY_OPTIONS = [
  { label: 'Pyidaungsu', value: 'Pyidaungsu' },
  { label: 'System default', value: '' },
  { label: 'Segoe UI', value: 'Segoe UI' },
  { label: 'Arial', value: 'Arial' },
  { label: 'Tahoma', value: 'Tahoma' },
  { label: 'Calibri', value: 'Calibri' },
  { label: 'Times New Roman', value: 'Times New Roman' },
  { label: 'Courier New', value: 'Courier New' },
];

const TABS: { type: DocumentType; label: string; short: string }[] = [
  { type: 'SALE', label: 'Sales Invoice', short: 'Sale' },
  { type: 'SERVICE_JOB', label: 'Service Voucher', short: 'Service' },
  { type: 'SERVICE_DONE', label: 'Service Done', short: 'Done' },
  { type: 'BOOKING', label: 'Booking Receipt', short: 'Booking' },
  { type: 'PURCHASE', label: 'Purchase Voucher', short: 'Purchase' },
];

const PAPER_SIZES = [
  { value: 'A4', label: 'A4', hint: 'Office invoice' },
  { value: 'A5', label: 'A5', hint: 'Half page' },
  { value: 'POS_80MM', label: '80mm', hint: 'Thermal receipt' },
  { value: 'POS_58MM', label: '58mm', hint: 'Small receipt' },
];

const PRESETS: { label: string; description: string; patch: Partial<VoucherSettingDto> }[] = [
  {
    label: 'A4 Standard',
    description: 'Full invoice with header, totals, signatures',
    patch: { paperSize: 'A4', marginTopMm: 10, marginBottomMm: 10, marginLeftMm: 10, marginRightMm: 10, rowHeightPx: 30, showLogo: true, showSignatures: true, showPaymentHistory: true },
  },
  {
    label: 'A5 Counter',
    description: 'Compact shop counter voucher',
    patch: { paperSize: 'A5', marginTopMm: 8, marginBottomMm: 8, marginLeftMm: 8, marginRightMm: 8, rowHeightPx: 27, showLogo: true, showSignatures: true },
  },
  {
    label: 'POS 80mm',
    description: 'Thermal receipt for cashier printer',
    patch: { paperSize: 'POS_80MM', marginTopMm: 4, marginBottomMm: 4, marginLeftMm: 3, marginRightMm: 3, rowHeightPx: 24, showLogo: false, showQrCode: true, showSignatures: false, showPaymentHistory: false },
  },
];

const emptyMap: Record<DocumentType, VoucherSettingDto | null> = {
  SALE: null,
  SERVICE_JOB: null,
  SERVICE_DONE: null,
  BOOKING: null,
  PURCHASE: null,
};

const mm = (v: number | null) => (v == null ? 'Default' : `${v} mm`);
const px = (v: number | null) => (v == null ? 'Default' : `${v}px`);

const VoucherSettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<DocumentType>('SALE');
  const [settings, setSettings] = useState<Record<DocumentType, VoucherSettingDto | null>>(emptyMap);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const all = await voucherSettingService.getAll();
      const map: Record<DocumentType, VoucherSettingDto | null> = { ...emptyMap };
      all.forEach((setting) => { map[setting.documentType as DocumentType] = setting; });
      setSettings(map);
    } catch {
      Swal.fire('Error', 'Failed to load voucher settings', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const current = settings[activeTab];
  const activeMeta = TABS.find((tab) => tab.type === activeTab) || TABS[0];

  const update = (patch: Partial<VoucherSettingDto>) => {
    if (!current) return;
    setSettings((prev) => ({
      ...prev,
      [activeTab]: { ...prev[activeTab]!, ...patch },
    }));
  };

  const handleSave = async () => {
    if (!current) return;
    setSaving(true);
    try {
      const saved = await voucherSettingService.save(activeTab, current);
      setSettings((prev) => ({ ...prev, [activeTab]: saved }));
      Swal.fire({ icon: 'success', title: 'Voucher settings saved', timer: 1200, showConfirmButton: false });
    } catch {
      Swal.fire('Error', 'Failed to save settings', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    const { isConfirmed } = await Swal.fire({
      title: 'Reset this voucher design?',
      text: 'This document type will return to system defaults.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Reset',
      confirmButtonColor: '#dc2626',
    });
    if (!isConfirmed) return;
    setSaving(true);
    try {
      const fresh = await voucherSettingService.reset(activeTab);
      setSettings((prev) => ({ ...prev, [activeTab]: fresh }));
      Swal.fire({ icon: 'success', title: 'Defaults restored', timer: 1200, showConfirmButton: false });
    } catch {
      Swal.fire('Error', 'Failed to reset settings', 'error');
    } finally {
      setSaving(false);
    }
  };

  const capacityStatus = useMemo(() => {
    if (!current) return { label: '-', tone: 'slate' };
    const first = current.rowsOnFirstPage || 0;
    if (first >= 18) return { label: 'Good capacity', tone: 'emerald' };
    if (first >= 10) return { label: 'Normal capacity', tone: 'amber' };
    return { label: 'Low rows per page', tone: 'rose' };
  }, [current]);
  const capacityStatusClass =
    {
      emerald: 'bg-emerald-100 text-emerald-700',
      amber: 'bg-amber-100 text-amber-700',
      rose: 'bg-rose-100 text-rose-700',
      slate: 'bg-slate-100 text-slate-700',
    }[capacityStatus.tone] || 'bg-slate-100 text-slate-700';

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center">
            <Printer size={22} className="text-indigo-600" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-800">Voucher Print Settings</h1>
            <p className="text-sm text-slate-500">ဘောင်ချာ paper size, layout, font, footer, signature များကိုစီမံရန်</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <button onClick={load} disabled={loading} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50">
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh
          </button>
          <button onClick={handleReset} disabled={saving || loading || !current} className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-rose-200 bg-white text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50">
            <RotateCcw size={14} /> Reset
          </button>
          <button onClick={handleSave} disabled={saving || loading || !current} className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-bold hover:bg-indigo-700 disabled:opacity-50">
            {saving ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />} Save Design
          </button>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
        <div className="flex overflow-x-auto border-b border-slate-100">
          {TABS.map((tab) => (
            <button
              key={tab.type}
              onClick={() => setActiveTab(tab.type)}
              className={`px-4 py-3 text-sm font-semibold border-b-2 whitespace-nowrap transition-colors ${
                activeTab === tab.type ? 'border-indigo-600 text-indigo-700 bg-indigo-50/50' : 'border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {loading && (
        <div className="h-64 flex items-center justify-center text-slate-400">
          <RefreshCw size={22} className="animate-spin mr-2" /> Loading voucher settings...
        </div>
      )}

      {!loading && current && (
        <div className="grid grid-cols-1 2xl:grid-cols-[1fr_420px] gap-5">
          <div className="space-y-5">
            <Panel icon={<FileText size={16} />} title="Document Setup" subtitle="Real-life voucher type, title and paper size">
              <div className="grid grid-cols-1 lg:grid-cols-[1fr_1.2fr] gap-4">
                <div>
                  <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Voucher Title</label>
                  <input value={current.voucherTitle} onChange={(e) => update({ voucherTitle: e.target.value })} className="w-full px-3 py-2.5 rounded-lg border border-slate-200 bg-slate-50 text-sm font-semibold focus:outline-none focus:border-indigo-400" />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">Paper Size</label>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                    {PAPER_SIZES.map((paper) => (
                      <button
                        key={paper.value}
                        type="button"
                        onClick={() => update({ paperSize: paper.value })}
                        className={`px-3 py-2 rounded-lg border text-left transition-colors ${current.paperSize === paper.value ? 'border-indigo-500 bg-indigo-50 text-indigo-700' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}
                      >
                        <p className="text-sm font-bold">{paper.label}</p>
                        <p className="text-[10px] text-slate-400">{paper.hint}</p>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-2 pt-2">
                {PRESETS.map((preset) => (
                  <button key={preset.label} type="button" onClick={() => update(preset.patch)} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-left hover:bg-indigo-50 hover:border-indigo-200">
                    <p className="text-xs font-bold text-slate-700">{preset.label}</p>
                    <p className="text-[11px] text-slate-500 mt-0.5">{preset.description}</p>
                  </button>
                ))}
              </div>
            </Panel>

            <Panel icon={<Settings2 size={16} />} title="Printed Content" subtitle="What appears on the printed voucher">
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3">
                <SwitchCard label="Logo" checked={current.showLogo} onChange={(v) => update({ showLogo: v })} />
                <SwitchCard label="QR Code" checked={current.showQrCode} onChange={(v) => update({ showQrCode: v })} />
                <SwitchCard label="Signatures" checked={current.showSignatures} onChange={(v) => update({ showSignatures: v })} />
                <SwitchCard label="Payment History" checked={current.showPaymentHistory} onChange={(v) => update({ showPaymentHistory: v })} />
                <SwitchCard label="Serial Numbers" checked={current.showSerial} onChange={(v) => update({ showSerial: v })} />
              </div>
            </Panel>

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
              <Panel icon={<ReceiptText size={16} />} title="Footer & Signature" subtitle="Customer-facing notes and sign labels">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <TextField label="Signature 1" value={current.sign1Label} onChange={(v) => update({ sign1Label: v })} />
                  <TextField label="Signature 2" value={current.sign2Label} onChange={(v) => update({ sign2Label: v })} />
                </div>
                <TextArea label="Footer Note" value={current.footerNote} onChange={(v) => update({ footerNote: v })} rows={3} />
                <TextArea label="Customer Notice" value={current.customerNotice} onChange={(v) => update({ customerNotice: v })} rows={3} />
              </Panel>

              <Panel icon={<Ruler size={16} />} title="Margins" subtitle="Printer-safe spacing in millimeters">
                <div className="grid grid-cols-2 gap-3">
                  <NumberField label="Top" value={current.marginTopMm} onChange={(v) => update({ marginTopMm: v })} suffix="mm" />
                  <NumberField label="Bottom" value={current.marginBottomMm} onChange={(v) => update({ marginBottomMm: v })} suffix="mm" />
                  <NumberField label="Left" value={current.marginLeftMm} onChange={(v) => update({ marginLeftMm: v })} suffix="mm" />
                  <NumberField label="Right" value={current.marginRightMm} onChange={(v) => update({ marginRightMm: v })} suffix="mm" />
                </div>
              </Panel>
            </div>

            <Panel icon={<SlidersHorizontal size={16} />} title="Layout Heights" subtitle="Controls pagination, rows per page and bottom totals area">
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <NumberField label="Header" value={current.headerHeightPx} onChange={(v) => update({ headerHeightPx: v })} suffix="px" />
                <NumberField label="Continuation Header" value={current.contHeaderHeightPx} onChange={(v) => update({ contHeaderHeightPx: v })} suffix="px" />
                <NumberField label="Info Blocks" value={current.infoBlocksHeightPx} onChange={(v) => update({ infoBlocksHeightPx: v })} suffix="px" />
                <NumberField label="Table Header" value={current.tableHeaderHeightPx} onChange={(v) => update({ tableHeaderHeightPx: v })} suffix="px" />
                <NumberField label="Row Height" value={current.rowHeightPx} onChange={(v) => update({ rowHeightPx: v })} suffix="px" />
                <NumberField label="Totals Area" value={current.totalsAreaHeightPx} onChange={(v) => update({ totalsAreaHeightPx: v })} suffix="px" />
                <NumberField label="Footer" value={current.footerHeightPx} onChange={(v) => update({ footerHeightPx: v })} suffix="px" />
                <NumberField label="Safety Margin" value={current.safetyMarginPx} onChange={(v) => update({ safetyMarginPx: v })} suffix="px" />
              </div>
            </Panel>

            <Panel icon={<Type size={16} />} title="Typography" subtitle="Leave blank to use system defaults">
              <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
                <FontRow label="Header / Company" family={current.headerFontFamily} size={current.headerFontSizePx} onFamily={(v) => update({ headerFontFamily: v })} onSize={(v) => update({ headerFontSizePx: v })} />
                <FontRow label="Bill To / Info" family={current.infoFontFamily} size={current.infoFontSizePx} onFamily={(v) => update({ infoFontFamily: v })} onSize={(v) => update({ infoFontSizePx: v })} />
                <FontRow label="Table Header" family={current.tableHeaderFontFamily} size={current.tableHeaderFontSizePx} onFamily={(v) => update({ tableHeaderFontFamily: v })} onSize={(v) => update({ tableHeaderFontSizePx: v })} />
                <FontRow label="Table Data" family={current.tableDataFontFamily} size={current.tableDataFontSizePx} onFamily={(v) => update({ tableDataFontFamily: v })} onSize={(v) => update({ tableDataFontSizePx: v })} />
                <FontRow label="Footer" family={current.footerFontFamily} size={current.footerFontSizePx} onFamily={(v) => update({ footerFontFamily: v })} onSize={(v) => update({ footerFontSizePx: v })} />
                <FontRow label="Notice / Remark" family={current.noticeFontFamily} size={current.noticeFontSizePx} onFamily={(v) => update({ noticeFontFamily: v })} onSize={(v) => update({ noticeFontSizePx: v })} />
              </div>
            </Panel>
          </div>

          <aside className="space-y-5">
            <div className="bg-white border border-slate-200 rounded-xl shadow-sm p-5 sticky top-4 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-sm font-bold text-slate-800">Live Layout Preview</h2>
                  <p className="text-xs text-slate-500 mt-0.5">{activeMeta.label}</p>
                </div>
                <span className={`px-2 py-1 rounded-full text-[11px] font-bold ${capacityStatusClass}`}>
                  {capacityStatus.label}
                </span>
              </div>

              <VoucherPreview setting={current} />

              <div className="grid grid-cols-2 gap-2">
                <Metric label="Paper" value={current.paperSize} />
                <Metric label="First Page Rows" value={current.rowsOnFirstPage ?? '-'} />
                <Metric label="Next Page Rows" value={current.rowsOnContinuationPage ?? '-'} />
                <Metric label="Row Height" value={px(current.rowHeightPx)} />
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
                <p className="font-bold text-slate-700">Printer margins</p>
                <p>Top {mm(current.marginTopMm)} / Bottom {mm(current.marginBottomMm)}</p>
                <p>Left {mm(current.marginLeftMm)} / Right {mm(current.marginRightMm)}</p>
              </div>

              {current.updatedBy && (
                <div className="flex items-start gap-2 rounded-lg border border-emerald-100 bg-emerald-50 p-3">
                  <BadgeCheck size={15} className="text-emerald-600 mt-0.5" />
                  <p className="text-xs text-emerald-700">
                    Last saved by <b>{current.updatedBy}</b>
                    {current.updatedAt ? ` on ${new Date(current.updatedAt).toLocaleString()}` : ''}
                  </p>
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </div>
  );
};

const Panel: React.FC<{ icon: React.ReactNode; title: string; subtitle?: string; children: React.ReactNode }> = ({ icon, title, subtitle, children }) => (
  <section className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
    <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-3">
      <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">{icon}</div>
      <div>
        <h2 className="text-sm font-bold text-slate-800">{title}</h2>
        {subtitle && <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>}
      </div>
    </div>
    <div className="p-5 space-y-4">{children}</div>
  </section>
);

const TextField: React.FC<{ label: string; value: string; onChange: (v: string) => void }> = ({ label, value, onChange }) => (
  <div>
    <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">{label}</label>
    <input value={value || ''} onChange={(e) => onChange(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
  </div>
);

const TextArea: React.FC<{ label: string; value: string; rows?: number; onChange: (v: string) => void }> = ({ label, value, rows = 3, onChange }) => (
  <div>
    <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">{label}</label>
    <textarea value={value || ''} rows={rows} onChange={(e) => onChange(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm leading-5 focus:outline-none focus:border-indigo-400 resize-none" />
  </div>
);

const NumberField: React.FC<{ label: string; value: number | null; suffix: string; onChange: (v: number | null) => void }> = ({ label, value, suffix, onChange }) => (
  <div>
    <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wide mb-1.5">{label}</label>
    <div className="relative">
      <input type="number" value={value ?? ''} onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))} placeholder="Default" className="w-full px-3 py-2 pr-10 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-bold text-slate-400">{suffix}</span>
    </div>
  </div>
);

const SwitchCard: React.FC<{ label: string; checked: boolean; onChange: (v: boolean) => void }> = ({ label, checked, onChange }) => (
  <button type="button" onClick={() => onChange(!checked)} className={`rounded-lg border px-3 py-3 text-left transition-colors ${checked ? 'border-indigo-200 bg-indigo-50' : 'border-slate-200 bg-slate-50'}`}>
    <div className="flex items-center justify-between gap-2">
      <span className={`text-sm font-bold ${checked ? 'text-indigo-700' : 'text-slate-500'}`}>{label}</span>
      <span className={`relative w-9 h-5 rounded-full ${checked ? 'bg-indigo-600' : 'bg-slate-300'}`}>
        <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`} />
      </span>
    </div>
  </button>
);

const FontRow: React.FC<{
  label: string;
  family: string | null;
  size: number | null;
  onFamily: (v: string | null) => void;
  onSize: (v: number | null) => void;
}> = ({ label, family, size, onFamily, onSize }) => (
  <div className="grid grid-cols-[120px_1fr_72px] gap-2 items-center">
    <span className="text-xs font-semibold text-slate-600">{label}</span>
    <select value={family ?? ''} onChange={(e) => onFamily(e.target.value === '' ? null : e.target.value)} className="min-w-0 px-2 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400">
      {FONT_FAMILY_OPTIONS.map((font) => <option key={font.value} value={font.value}>{font.label}</option>)}
    </select>
    <input type="number" value={size ?? ''} onChange={(e) => onSize(e.target.value === '' ? null : Number(e.target.value))} placeholder="px" className="px-2 py-2 rounded-lg border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:border-indigo-400" />
  </div>
);

const Metric: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wide">{label}</p>
    <p className="text-sm font-bold text-slate-800 mt-1">{value}</p>
  </div>
);

const VoucherPreview: React.FC<{ setting: VoucherSettingDto }> = ({ setting }) => {
  const isPos = setting.paperSize?.startsWith('POS');
  return (
    <div className="rounded-xl bg-slate-100 border border-slate-200 p-4 flex justify-center">
      <div className={`bg-white shadow-sm border border-slate-200 ${isPos ? 'w-44 min-h-[360px]' : setting.paperSize === 'A5' ? 'w-72 min-h-[410px]' : 'w-80 min-h-[460px]'}`}>
        <div className="p-4 space-y-3">
          <div className="flex items-start gap-2">
            {setting.showLogo && <div className="w-9 h-9 rounded bg-slate-900" />}
            <div className="flex-1">
              <div className="h-3 w-28 bg-slate-800 rounded" />
              <div className="h-2 w-36 bg-slate-200 rounded mt-2" />
              <div className="h-2 w-24 bg-slate-200 rounded mt-1" />
            </div>
          </div>
          <div className="text-center">
            <p className="text-[11px] font-black text-slate-800 uppercase">{setting.voucherTitle || 'Voucher'}</p>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div className="rounded border border-slate-100 p-2 space-y-1">
              <div className="h-2 w-12 bg-slate-300 rounded" />
              <div className="h-2 w-20 bg-slate-200 rounded" />
            </div>
            <div className="rounded border border-slate-100 p-2 space-y-1">
              <div className="h-2 w-12 bg-slate-300 rounded" />
              <div className="h-2 w-20 bg-slate-200 rounded" />
            </div>
          </div>
          <div className="border border-slate-200 rounded overflow-hidden">
            <div className="h-6 bg-slate-100 grid grid-cols-4 gap-px p-1">
              <span className="bg-slate-300 rounded" />
              <span className="bg-slate-300 rounded" />
              <span className="bg-slate-300 rounded" />
              <span className="bg-slate-300 rounded" />
            </div>
            {Array.from({ length: isPos ? 5 : 7 }).map((_, i) => (
              <div key={i} className="h-7 border-t border-slate-100 grid grid-cols-4 gap-px p-1">
                <span className="bg-slate-100 rounded" />
                <span className="bg-slate-50 rounded" />
                <span className="bg-slate-50 rounded" />
                <span className="bg-slate-100 rounded" />
              </div>
            ))}
          </div>
          <div className="ml-auto w-32 space-y-1">
            <div className="h-2 bg-slate-200 rounded" />
            <div className="h-2 bg-slate-200 rounded" />
            <div className="h-3 bg-slate-800 rounded" />
          </div>
          {setting.showSignatures && (
            <div className="grid grid-cols-2 gap-4 pt-5">
              <div className="border-t border-slate-300 text-[9px] text-center pt-1">{setting.sign1Label || 'Prepared'}</div>
              <div className="border-t border-slate-300 text-[9px] text-center pt-1">{setting.sign2Label || 'Received'}</div>
            </div>
          )}
          {setting.footerNote && <div className="text-[9px] text-center text-slate-400 border-t border-slate-100 pt-2">{setting.footerNote.slice(0, 80)}</div>}
        </div>
      </div>
    </div>
  );
};

export default VoucherSettingsPage;
