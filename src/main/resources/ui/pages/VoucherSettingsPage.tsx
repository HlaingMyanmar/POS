import React, { useCallback, useEffect, useMemo, useState } from 'react';
import Swal from 'sweetalert2';
import { BadgeCheck, ChevronDown, Eye, FileText, LayoutTemplate, Printer, ReceiptText, RefreshCw, RotateCcw, Ruler, Save, Settings2, SlidersHorizontal, Type } from 'lucide-react';
import { DocumentType, VoucherSettingDto, voucherSettingService } from '../services/voucherSettingService';
import { CompanySettings, getCachedCompanySettings, getCompanySettings } from '../utils/companySettings';

type SectionKey = 'basic' | 'content' | 'layout' | 'text';
type TabMeta = { type: DocumentType; label: string; short: string; description: string; defaultTitle: string; recommendedPaper: string };

const TABS: TabMeta[] = [
  { type: 'SALE', label: 'အရောင်းဘောင်ချာ', short: 'Sale', description: 'Customer ကိုပေးမည့် အရောင်း invoice', defaultTitle: 'SALES INVOICE', recommendedPaper: 'A4 / 80mm' },
  { type: 'SERVICE_JOB', label: 'Service လက်ခံဘောင်ချာ', short: 'Intake', description: 'ဖုန်း/Computer လက်ခံချိန် ပေးသော receipt', defaultTitle: 'DEVICE INTAKE RECEIPT', recommendedPaper: 'A5' },
  { type: 'SERVICE_DONE', label: 'Service ပြီးဘောင်ချာ', short: 'Done', description: 'ပြင်ပြီးချိန် ငွေရှင်း/အပ်နှံသော voucher', defaultTitle: 'SERVICE DONE VOUCHER', recommendedPaper: 'A5' },
  { type: 'BOOKING', label: 'Booking Receipt', short: 'Booking', description: 'ကြိုတင်လက်ခံ/Booking receipt', defaultTitle: 'BOOKING RECEIPT', recommendedPaper: 'A5' },
  { type: 'PURCHASE', label: 'ဝယ်ယူမှုဘောင်ချာ', short: 'Purchase', description: 'Supplier ဝယ်ယူမှု မှတ်တမ်း', defaultTitle: 'PURCHASE VOUCHER', recommendedPaper: 'A4' },
];

const PAPER_SIZES = [
  { value: 'A4', label: 'A4', hint: 'စာရင်းရှင်း / အပြည့်အစုံ', use: 'ရုံးသုံး printer' },
  { value: 'A5', label: 'A5', hint: 'ဆိုင်ကောင်တာသုံး', use: 'ဝက်စာရွက်' },
  { value: 'POS_80MM', label: '80mm', hint: 'Thermal receipt', use: 'POS printer' },
  { value: 'POS_58MM', label: '58mm', hint: 'သေးသော receipt', use: 'Mini printer' },
];

const FONT_FAMILY_OPTIONS = [
  { label: 'မူရင်း Font', value: '' },
  { label: 'Pyidaungsu', value: 'Pyidaungsu' },
  { label: 'Segoe UI', value: 'Segoe UI' },
  { label: 'Arial', value: 'Arial' },
  { label: 'Tahoma', value: 'Tahoma' },
  { label: 'Calibri', value: 'Calibri' },
];

const emptyMap: Record<DocumentType, VoucherSettingDto | null> = { SALE: null, SERVICE_JOB: null, SERVICE_DONE: null, BOOKING: null, PURCHASE: null };

const PRESETS: { label: string; description: string; badge: string; patch: Partial<VoucherSettingDto> }[] = [
  { label: 'A4 Invoice', description: 'Logo, payment history, signature ပါတဲ့ အပြည့်အစုံ ဘောင်ချာ', badge: 'Office', patch: { paperSize: 'A4', marginTopMm: 10, marginBottomMm: 10, marginLeftMm: 10, marginRightMm: 10, rowHeightPx: 30, showLogo: true, showQrCode: false, showSignatures: true, showPaymentHistory: true, showSerial: true } },
  { label: 'A5 Counter', description: 'ဆိုင်ကောင်တာမှာမြန်မြန်ထုတ်ရန် compact ပုံစံ', badge: 'Shop', patch: { paperSize: 'A5', marginTopMm: 8, marginBottomMm: 8, marginLeftMm: 8, marginRightMm: 8, rowHeightPx: 27, showLogo: true, showQrCode: false, showSignatures: true, showPaymentHistory: true } },
  { label: '80mm POS', description: 'Thermal printer အတွက် logo/signature လျှော့ထားသော receipt', badge: 'Thermal', patch: { paperSize: 'POS_80MM', marginTopMm: 4, marginBottomMm: 4, marginLeftMm: 3, marginRightMm: 3, rowHeightPx: 24, showLogo: false, showQrCode: true, showSignatures: false, showPaymentHistory: false } },
];

const mm = (v: number | null) => (v == null ? '-' : `${v} mm`);
const px = (v: number | null) => (v == null ? '-' : `${v}px`);

const VoucherSettingsPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<DocumentType>('SALE');
  const [activeSection, setActiveSection] = useState<SectionKey>('basic');
  const [settings, setSettings] = useState<Record<DocumentType, VoucherSettingDto | null>>(emptyMap);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [company, setCompany] = useState<CompanySettings>(() => getCachedCompanySettings());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [all] = await Promise.all([
        voucherSettingService.getAll(),
        getCompanySettings().catch(() => getCachedCompanySettings()),
      ]);
      const map: Record<DocumentType, VoucherSettingDto | null> = { ...emptyMap };
      all.forEach((setting) => { map[setting.documentType as DocumentType] = setting; });
      setSettings(map);
      setCompany(getCachedCompanySettings());
    } catch {
      Swal.fire('Error', 'Voucher settings ဖတ်လို့မရပါ', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const current = settings[activeTab];
  const activeMeta = TABS.find((tab) => tab.type === activeTab) || TABS[0];
  const update = (patch: Partial<VoucherSettingDto>) => {
    if (!current) return;
    setSettings((prev) => ({ ...prev, [activeTab]: { ...prev[activeTab]!, ...patch } }));
  };
  const applyPreset = (patch: Partial<VoucherSettingDto>) => { update(patch); setActiveSection('content'); };

  const handleSave = async () => {
    if (!current) return;
    setSaving(true);
    try {
      const saved = await voucherSettingService.save(activeTab, current);
      setSettings((prev) => ({ ...prev, [activeTab]: saved }));
      Swal.fire({ icon: 'success', title: 'Voucher design သိမ်းပြီးပါပြီ', timer: 1300, showConfirmButton: false });
    } catch {
      Swal.fire('Error', 'Settings သိမ်းလို့မရပါ', 'error');
    } finally { setSaving(false); }
  };

  const handleReset = async () => {
    const { isConfirmed } = await Swal.fire({ title: 'ဒီ voucher design ကို reset လုပ်မလား', text: 'System default ပုံစံသို့ ပြန်သွားပါမည်။', icon: 'warning', showCancelButton: true, confirmButtonText: 'Reset လုပ်မည်', cancelButtonText: 'မလုပ်တော့ပါ', confirmButtonColor: '#dc2626' });
    if (!isConfirmed) return;
    setSaving(true);
    try {
      const fresh = await voucherSettingService.reset(activeTab);
      setSettings((prev) => ({ ...prev, [activeTab]: fresh }));
      Swal.fire({ icon: 'success', title: 'Default ပြန်ထားပြီးပါပြီ', timer: 1300, showConfirmButton: false });
    } catch {
      Swal.fire('Error', 'Reset လုပ်လို့မရပါ', 'error');
    } finally { setSaving(false); }
  };

  const capacity = useMemo(() => {
    if (!current) return { label: '-', className: 'bg-slate-100 text-slate-600', note: '' };
    if (current.paperSize?.startsWith('POS')) return { label: 'Thermal receipt', className: 'bg-cyan-100 text-cyan-700', note: 'စာရွက်ရှည်အလိုက် ဆက်ထွက်မည်' };
    const first = current.rowsOnFirstPage || 0;
    if (first >= 18) return { label: 'တစ်မျက်နှာ item များများဝင်', className: 'bg-emerald-100 text-emerald-700', note: `${first} rows ခန့် ဝင်နိုင်သည်` };
    if (first >= 10) return { label: 'သာမန်အသုံးပြုရန်ကောင်း', className: 'bg-amber-100 text-amber-700', note: `${first} rows ခန့် ဝင်နိုင်သည်` };
    return { label: 'စာမျက်နှာများနိုင်', className: 'bg-rose-100 text-rose-700', note: 'Row height/margins လျှော့နိုင်သည်' };
  }, [current]);

  return (
    <div className="w-full max-w-none space-y-5">
      <div className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="flex flex-col gap-4 p-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg border border-indigo-100 bg-indigo-50 text-indigo-600"><Printer size={22} /></div>
            <div className="min-w-0">
              <h1 className="text-xl font-bold text-slate-800">Voucher Print Settings</h1>
              <p className="mt-1 text-sm text-slate-500">ဆိုင်မှာ တကယ် print ထုတ်မည့် ဘောင်ချာပုံစံ၊ စက္ကူ၊ စာသား၊ လက်မှတ်နှင့် layout ကိုပြင်ရန်</p>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:flex sm:items-center">
            <button onClick={load} disabled={loading} className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"><RefreshCw size={14} className={loading ? 'animate-spin' : ''} /> Refresh</button>
            <button onClick={handleReset} disabled={saving || loading || !current} className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-rose-200 bg-white px-3 text-xs font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50"><RotateCcw size={14} /> Reset</button>
            <button onClick={handleSave} disabled={saving || loading || !current} className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 text-xs font-bold text-white hover:bg-indigo-700 disabled:opacity-50">{saving ? <RefreshCw size={14} className="animate-spin" /> : <Save size={14} />} သိမ်းမည်</button>
          </div>
        </div>
        <div className="flex gap-2 overflow-x-auto border-t border-slate-100 bg-slate-50/70 p-2">
          {TABS.map((tab) => (
            <button key={tab.type} onClick={() => { setActiveTab(tab.type); setActiveSection('basic'); }} className={`min-w-[150px] rounded-lg border px-3 py-2 text-left transition-colors ${activeTab === tab.type ? 'border-indigo-200 bg-white text-indigo-700 shadow-sm' : 'border-transparent text-slate-500 hover:bg-white hover:text-slate-700'}`}>
              <p className="text-sm font-bold">{tab.label}</p>
              <p className="mt-0.5 text-[10px] font-semibold uppercase tracking-wide opacity-70">{tab.short}</p>
            </button>
          ))}
        </div>
      </div>

      {loading && <div className="flex h-64 items-center justify-center rounded-xl border border-slate-200 bg-white text-slate-400"><RefreshCw size={22} className="mr-2 animate-spin" /> Settings ဖတ်နေသည်...</div>}

      {!loading && current && (
        <div className="grid grid-cols-1 2xl:grid-cols-[minmax(0,1fr)_440px] gap-5">
          <main className="min-w-0 space-y-5">
            <div className="grid grid-cols-1 lg:grid-cols-[260px_minmax(0,1fr)] gap-5">
              <nav className="rounded-xl border border-slate-200 bg-white p-2 shadow-sm h-fit">
                <SectionButton active={activeSection === 'basic'} icon={<LayoutTemplate size={15} />} label="အခြေခံပုံစံ" hint="Voucher type, title, paper" onClick={() => setActiveSection('basic')} />
                <SectionButton active={activeSection === 'content'} icon={<Eye size={15} />} label="Print မှာပြမည့်အရာ" hint="Logo, serial, payment, signature" onClick={() => setActiveSection('content')} />
                <SectionButton active={activeSection === 'layout'} icon={<Ruler size={15} />} label="စာမျက်နှာအရွယ်အစား" hint="Margins, rows, spacing" onClick={() => setActiveSection('layout')} />
                <SectionButton active={activeSection === 'text'} icon={<Type size={15} />} label="စာသားနှင့် Font" hint="Footer, notice, font" onClick={() => setActiveSection('text')} />
              </nav>
              <div className="min-w-0 space-y-5">
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-900">
                  <b>Live Preview</b> — ဘယ်ဘက်မှာ ပြင်တာများကို ညာဘက် Preview မှာ ချက်ချင်းမြင်ရပါမယ်။
                  Settings <b>သိမ်းမည်</b> နှိပ်ပြီးမှ printer မှာ သုံးနိုင်ပါမယ် (WAR ပြန်တင်စရာ မလိုပါ — voucher settings က DB ထဲသိမ်းသည်)။
                </div>
                {activeSection === 'basic' && <>
                  <Panel icon={<FileText size={16} />} title="Voucher အမျိုးအစား" subtitle={activeMeta.description}>
                    <div className="grid grid-cols-1 xl:grid-cols-[1fr_220px] gap-4">
                      <TextField label="Voucher ခေါင်းစဉ်" value={current.voucherTitle} onChange={(v) => update({ voucherTitle: v })} placeholder={activeMeta.defaultTitle} />
                      <ReadOnlyInfo label="အသုံးများသော စက္ကူ" value={activeMeta.recommendedPaper} />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                      {PRESETS.map((preset) => <button key={preset.label} type="button" onClick={() => applyPreset(preset.patch)} className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-left hover:border-indigo-200 hover:bg-indigo-50"><div className="flex items-center justify-between gap-2"><p className="text-sm font-black text-slate-800">{preset.label}</p><span className="rounded-md bg-white px-2 py-0.5 text-[10px] font-bold text-slate-500">{preset.badge}</span></div><p className="mt-1 text-xs leading-5 text-slate-500">{preset.description}</p></button>)}
                    </div>
                  </Panel>
                  <Panel icon={<Printer size={16} />} title="စက္ကူ / Printer" subtitle="ပုံမှန်ဆိုင်သုံး printer အလိုက်ရွေးပါ">
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3">
                      {PAPER_SIZES.map((paper) => <button key={paper.value} type="button" onClick={() => update({ paperSize: paper.value })} className={`rounded-lg border p-3 text-left transition-colors ${current.paperSize === paper.value ? 'border-indigo-500 bg-indigo-50 text-indigo-700' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'}`}><p className="text-base font-black">{paper.label}</p><p className="mt-1 text-xs font-semibold">{paper.hint}</p><p className="mt-1 text-[11px] text-slate-400">{paper.use}</p></button>)}
                    </div>
                  </Panel>
                </>}

                {activeSection === 'content' && <Panel icon={<Settings2 size={16} />} title="Print ထဲမှာ ပြမည့်အရာ" subtitle="တကယ်လိုအပ်တာကိုပဲဖွင့်ထားပါ">
                  <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                    <SwitchCard label="Logo ပြမည်" description="Company logo ကို header မှာထည့်မည်" checked={current.showLogo} onChange={(v) => update({ showLogo: v })} />
                    <SwitchCard label="QR Code ပြမည်" description="ဘောင်ချာကို scan/check လုပ်ရန်" checked={current.showQrCode} onChange={(v) => update({ showQrCode: v })} />
                    <SwitchCard label="Serial နံပါတ်ပြမည်" description="Serial column ပြမည်" checked={current.showSerial} onChange={(v) => update({ showSerial: v })} />
                    <SwitchCard label="Warranty column ပြမည်" description="Warranty သီးခြား column" checked={current.showColWarranty !== false} onChange={(v) => update({ showColWarranty: v })} />
                    <SwitchCard label="Dis column ပြမည်" description="Line discount သီးခြား column" checked={current.showColLineDiscount !== false} onChange={(v) => update({ showColLineDiscount: v })} />
                    <SwitchCard label="Payment History ပြမည်" description="Paid / partial payment မှတ်တမ်း" checked={current.showPaymentHistory} onChange={(v) => update({ showPaymentHistory: v })} />
                    <SwitchCard label="လက်မှတ်နေရာ ပြမည်" description="Prepared / Customer signature" checked={current.showSignatures} onChange={(v) => update({ showSignatures: v })} />
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-2">
                    <TextField label="လက်မှတ် ၁" value={current.sign1Label} onChange={(v) => update({ sign1Label: v })} placeholder="Prepared By" />
                    <TextField label="လက်မှတ် ၂" value={current.sign2Label} onChange={(v) => update({ sign2Label: v })} placeholder="Customer Signature" />
                  </div>
                </Panel>}

                {activeSection === 'layout' && <>
                  <Panel icon={<Ruler size={16} />} title="Printer margin" subtitle="စာရွက်အစွန်းနဲ့ မကပ်အောင်ချိန်ပါ">
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                      <NumberField label="အပေါ်" value={current.marginTopMm} onChange={(v) => update({ marginTopMm: v })} suffix="mm" />
                      <NumberField label="အောက်" value={current.marginBottomMm} onChange={(v) => update({ marginBottomMm: v })} suffix="mm" />
                      <NumberField label="ဘယ်" value={current.marginLeftMm} onChange={(v) => update({ marginLeftMm: v })} suffix="mm" />
                      <NumberField label="ညာ" value={current.marginRightMm} onChange={(v) => update({ marginRightMm: v })} suffix="mm" />
                    </div>
                  </Panel>
                  <Panel icon={<SlidersHorizontal size={16} />} title="Rows / Spacing" subtitle="Item row များများဝင်ချင်ရင် Row height ကိုလျှော့ပါ">
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                      <NumberField label="Row Height" value={current.rowHeightPx} onChange={(v) => update({ rowHeightPx: v })} suffix="px" />
                      <NumberField label="Header" value={current.headerHeightPx} onChange={(v) => update({ headerHeightPx: v })} suffix="px" />
                      <NumberField label="Info Box" value={current.infoBlocksHeightPx} onChange={(v) => update({ infoBlocksHeightPx: v })} suffix="px" />
                      <NumberField label="Totals Area" value={current.totalsAreaHeightPx} onChange={(v) => update({ totalsAreaHeightPx: v })} suffix="px" />
                    </div>
                    <button type="button" onClick={() => setShowAdvanced((v) => !v)} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100"><ChevronDown size={14} className={showAdvanced ? 'rotate-180 transition-transform' : 'transition-transform'} /> Advanced spacing</button>
                    {showAdvanced && <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <NumberField label="Cont. Header" value={current.contHeaderHeightPx} onChange={(v) => update({ contHeaderHeightPx: v })} suffix="px" />
                      <NumberField label="Table Header" value={current.tableHeaderHeightPx} onChange={(v) => update({ tableHeaderHeightPx: v })} suffix="px" />
                      <NumberField label="Footer" value={current.footerHeightPx} onChange={(v) => update({ footerHeightPx: v })} suffix="px" />
                      <NumberField label="Safety" value={current.safetyMarginPx} onChange={(v) => update({ safetyMarginPx: v })} suffix="px" />
                    </div>}
                  </Panel>
                </>}

                {activeSection === 'text' && <>
                  <Panel icon={<ReceiptText size={16} />} title="Footer / Customer Notice" subtitle="ဘောင်ချာအောက်ဆုံးမှာပါတဲ့ customer-facing စာသားများ">
                    <TextArea label="Footer note" value={current.footerNote} onChange={(v) => update({ footerNote: v })} rows={3} placeholder="Thank you / Warranty policy / Return policy" />
                    <TextArea label="Customer notice" value={current.customerNotice} onChange={(v) => update({ customerNotice: v })} rows={4} placeholder="ဥပမာ - ပစ္စည်းပြန်လဲရန် ဘောင်ချာယူဆောင်လာပါ။" />
                  </Panel>
                  <Panel icon={<Type size={16} />} title="Font" subtitle="မြန်မာစာမမှန်ရင် Pyidaungsu ကိုရွေးပါ">
                    <div className="grid grid-cols-1 xl:grid-cols-2 gap-3">
                      <FontRow label="Header" family={current.headerFontFamily} size={current.headerFontSizePx} onFamily={(v) => update({ headerFontFamily: v })} onSize={(v) => update({ headerFontSizePx: v })} />
                      <FontRow label="Info" family={current.infoFontFamily} size={current.infoFontSizePx} onFamily={(v) => update({ infoFontFamily: v })} onSize={(v) => update({ infoFontSizePx: v })} />
                      <FontRow label="Table Head" family={current.tableHeaderFontFamily} size={current.tableHeaderFontSizePx} onFamily={(v) => update({ tableHeaderFontFamily: v })} onSize={(v) => update({ tableHeaderFontSizePx: v })} />
                      <FontRow label="Table Data" family={current.tableDataFontFamily} size={current.tableDataFontSizePx} onFamily={(v) => update({ tableDataFontFamily: v })} onSize={(v) => update({ tableDataFontSizePx: v })} />
                      <FontRow label="Footer" family={current.footerFontFamily} size={current.footerFontSizePx} onFamily={(v) => update({ footerFontFamily: v })} onSize={(v) => update({ footerFontSizePx: v })} />
                      <FontRow label="Notice" family={current.noticeFontFamily} size={current.noticeFontSizePx} onFamily={(v) => update({ noticeFontFamily: v })} onSize={(v) => update({ noticeFontSizePx: v })} />
                    </div>
                  </Panel>
                </>}
              </div>
            </div>
          </main>

          <aside className="space-y-5">
            <div className="sticky top-4 space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-[11px] font-bold uppercase tracking-wider text-indigo-500">Live Preview</p>
                  <h2 className="text-sm font-bold text-slate-800">{activeMeta.label}</h2>
                  <p className="mt-1 text-xs text-slate-500">Compose Preview လို — ပြင်ပြီးချက်ချင်းမြင်ရမည်</p>
                </div>
                <span className={`shrink-0 rounded-full px-2 py-1 text-[10px] font-bold ${capacity.className}`}>{capacity.label}</span>
              </div>
              <VoucherPreview setting={current} company={company} defaultTitle={activeMeta.defaultTitle} />
              <div className="grid grid-cols-2 gap-2">
                <Metric label="စက္ကူ" value={current.paperSize} />
                <Metric label="ပထမစာမျက်နှာ" value={current.rowsOnFirstPage ?? '-'} />
                <Metric label="နောက်စာမျက်နှာ" value={current.rowsOnContinuationPage ?? '-'} />
                <Metric label="Row" value={px(current.rowHeightPx)} />
              </div>
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
                <p className="font-bold text-slate-700">Print အကျဉ်းချုပ်</p>
                <p>{capacity.note}</p>
                <p>Margin: {mm(current.marginTopMm)} / {mm(current.marginBottomMm)} / {mm(current.marginLeftMm)} / {mm(current.marginRightMm)}</p>
                <p className="text-[11px] text-slate-500">အစစ်အမှန် Sale voucher ကို Sale → Print Preview မှာလည်း ထပ်ကြည့်နိုင်ပါသည်။</p>
              </div>
              {current.updatedBy && <div className="flex items-start gap-2 rounded-lg border border-emerald-100 bg-emerald-50 p-3"><BadgeCheck size={15} className="mt-0.5 text-emerald-600" /><p className="text-xs text-emerald-700">နောက်ဆုံးသိမ်းသူ <b>{current.updatedBy}</b>{current.updatedAt ? ` · ${new Date(current.updatedAt).toLocaleString()}` : ''}</p></div>}
            </div>
          </aside>
        </div>
      )}
    </div>
  );
};

const SectionButton: React.FC<{ active: boolean; icon: React.ReactNode; label: string; hint: string; onClick: () => void }> = ({ active, icon, label, hint, onClick }) => (
  <button type="button" onClick={onClick} className={`mb-1 flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left transition-colors ${active ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50'}`}>
    <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${active ? 'bg-white text-indigo-600' : 'bg-slate-100 text-slate-500'}`}>{icon}</span>
    <span className="min-w-0"><span className="block text-sm font-bold">{label}</span><span className="block truncate text-[11px] opacity-70">{hint}</span></span>
  </button>
);

const Panel: React.FC<{ icon: React.ReactNode; title: string; subtitle?: string; children: React.ReactNode }> = ({ icon, title, subtitle, children }) => (
  <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"><div className="flex items-center gap-3 border-b border-slate-100 px-5 py-4"><div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">{icon}</div><div className="min-w-0"><h2 className="text-sm font-bold text-slate-800">{title}</h2>{subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}</div></div><div className="space-y-4 p-5">{children}</div></section>
);

const TextField: React.FC<{ label: string; value: string; placeholder?: string; onChange: (v: string) => void }> = ({ label, value, placeholder, onChange }) => <div><label className="mb-1.5 block text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</label><input value={value || ''} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-semibold focus:border-indigo-400 focus:outline-none" /></div>;
const ReadOnlyInfo: React.FC<{ label: string; value: string }> = ({ label, value }) => <div><p className="mb-1.5 text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</p><div className="flex h-10 items-center rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-bold text-slate-700">{value}</div></div>;
const TextArea: React.FC<{ label: string; value: string; rows?: number; placeholder?: string; onChange: (v: string) => void }> = ({ label, value, rows = 3, placeholder, onChange }) => <div><label className="mb-1.5 block text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</label><textarea value={value || ''} rows={rows} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} className="w-full resize-none rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm leading-5 focus:border-indigo-400 focus:outline-none" /></div>;
const NumberField: React.FC<{ label: string; value: number | null; suffix: string; onChange: (v: number | null) => void }> = ({ label, value, suffix, onChange }) => <div><label className="mb-1.5 block text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</label><div className="relative"><input type="number" value={value ?? ''} onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))} placeholder="Default" className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 pr-10 text-sm focus:border-indigo-400 focus:outline-none" /><span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] font-bold text-slate-400">{suffix}</span></div></div>;
const SwitchCard: React.FC<{ label: string; description: string; checked: boolean; onChange: (v: boolean) => void }> = ({ label, description, checked, onChange }) => (
  <button type="button" onClick={() => onChange(!checked)} className={`flex min-h-[86px] items-start justify-between gap-3 rounded-lg border p-3 text-left transition-colors ${checked ? 'border-indigo-200 bg-indigo-50' : 'border-slate-200 bg-white hover:bg-slate-50'}`}>
    <span className="min-w-0"><span className="block text-sm font-bold text-slate-800">{label}</span><span className="mt-1 block text-xs leading-5 text-slate-500">{description}</span></span>
    <span className={`mt-0.5 flex h-5 w-9 shrink-0 items-center rounded-full p-0.5 transition-colors ${checked ? 'bg-indigo-600' : 'bg-slate-300'}`}><span className={`h-4 w-4 rounded-full bg-white shadow-sm transition-transform ${checked ? 'translate-x-4' : ''}`} /></span>
  </button>
);

const FontRow: React.FC<{ label: string; family: string; size: number | null; onFamily: (v: string) => void; onSize: (v: number | null) => void }> = ({ label, family, size, onFamily, onSize }) => (
  <div className="grid grid-cols-[minmax(92px,120px)_1fr_96px] items-end gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3">
    <div><p className="text-[11px] font-bold uppercase tracking-wide text-slate-500">{label}</p><p className="mt-1 truncate text-xs text-slate-400">စာလုံးပုံစံ</p></div>
    <div><label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wide text-slate-400">Font family</label><input value={family || ''} onChange={(e) => onFamily(e.target.value)} placeholder="Pyidaungsu, Arial" className="h-9 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm focus:border-indigo-400 focus:outline-none" /></div>
    <div><label className="mb-1.5 block text-[10px] font-bold uppercase tracking-wide text-slate-400">Size</label><input type="number" value={size ?? ''} onChange={(e) => onSize(e.target.value === '' ? null : Number(e.target.value))} className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2 text-sm focus:border-indigo-400 focus:outline-none" /></div>
  </div>
);

const Metric: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div className="rounded-lg border border-slate-200 bg-slate-50 p-2.5"><p className="text-[10px] font-bold uppercase tracking-wide text-slate-400">{label}</p><p className="mt-1 text-sm font-bold text-slate-800">{value}</p></div>
);

/** Fake QR pattern for live preview (not a real scannable code). */
const PreviewQr: React.FC<{ size?: number }> = ({ size = 56 }) => (
  <div
    className="shrink-0 rounded border border-slate-200 bg-white p-0.5 shadow-sm"
    style={{ width: size, height: size }}
    title="QR preview"
  >
    <svg viewBox="0 0 40 40" width="100%" height="100%" aria-hidden>
      <rect width="40" height="40" fill="#fff" />
      <rect x="2" y="2" width="12" height="12" fill="#0f172a" />
      <rect x="4" y="4" width="8" height="8" fill="#fff" />
      <rect x="6" y="6" width="4" height="4" fill="#0f172a" />
      <rect x="26" y="2" width="12" height="12" fill="#0f172a" />
      <rect x="28" y="4" width="8" height="8" fill="#fff" />
      <rect x="30" y="6" width="4" height="4" fill="#0f172a" />
      <rect x="2" y="26" width="12" height="12" fill="#0f172a" />
      <rect x="4" y="28" width="8" height="8" fill="#fff" />
      <rect x="6" y="30" width="4" height="4" fill="#0f172a" />
      <rect x="18" y="18" width="3" height="3" fill="#0f172a" />
      <rect x="24" y="18" width="3" height="3" fill="#0f172a" />
      <rect x="18" y="24" width="3" height="3" fill="#0f172a" />
      <rect x="30" y="24" width="3" height="3" fill="#0f172a" />
      <rect x="24" y="30" width="3" height="3" fill="#0f172a" />
      <rect x="34" y="34" width="4" height="4" fill="#0f172a" />
    </svg>
  </div>
);

const VoucherPreview: React.FC<{
  setting: VoucherSettingDto;
  company: CompanySettings;
  defaultTitle: string;
}> = ({ setting, company, defaultTitle }) => {
  const isPos = String(setting.paperSize || '').startsWith('POS');
  const is58 = setting.paperSize === 'POS_58MM';
  const isA5 = setting.paperSize === 'A5';
  const title = setting.voucherTitle || defaultTitle || company.invoiceTitle || 'SALES INVOICE';
  const companyName = company.companyName || 'SSPD Store';
  const contact = [company.companyAddress, company.companyPhone].filter(Boolean).join(' · ');
  const logoSrc = company.logoBase64 || '';
  const headerFont = setting.headerFontFamily || 'Segoe UI, Arial, sans-serif';
  const headerSize = Math.max(setting.headerFontSizePx || (isPos ? 13 : 16), 11);
  const rowCount = Math.min(Math.max(setting.rowsOnFirstPage || 5, 3), isPos ? 5 : 7);
  const rows = Array.from({ length: rowCount });
  const paperWidth = is58 ? 200 : isPos ? 260 : isA5 ? 320 : 360;
  const padX = isPos ? 10 : 14;

  if (isPos) {
    return (
      <div className="rounded-xl border border-slate-200 bg-slate-100 p-3">
        <div
          className="mx-auto overflow-hidden rounded-lg bg-white shadow-sm"
          style={{ width: paperWidth, fontFamily: setting.tableDataFontFamily || 'Arial' }}
        >
          <div className="border-b border-dashed border-slate-300 px-3 py-3 text-center">
            {setting.showLogo && logoSrc && (
              <img src={logoSrc} alt="" className="mx-auto mb-2 max-h-10 max-w-[72px] object-contain" />
            )}
            {setting.showLogo && !logoSrc && (
              <div className="mx-auto mb-2 flex h-9 w-9 items-center justify-center rounded border border-slate-200 text-[9px] font-black text-slate-400">LOGO</div>
            )}
            <p className="font-black text-slate-900" style={{ fontFamily: headerFont, fontSize: headerSize }}>{companyName}</p>
            {contact && <p className="mt-1 text-[9px] leading-4 text-slate-500">{contact}</p>}
            <p className="mt-2 text-[10px] font-black uppercase tracking-wide text-slate-700">{title}</p>
            <p className="text-[9px] font-bold text-slate-400">CUSTOMER COPY</p>
          </div>
          <div className="space-y-1 border-b border-dashed border-slate-300 px-3 py-2 text-[10px] text-slate-600">
            <div className="flex justify-between"><span>No</span><b>INV-000123</b></div>
            <div className="flex justify-between"><span>Date</span><span>04 Sep 2026 14:30</span></div>
            <div className="flex justify-between"><span>Customer</span><span>Walk-in</span></div>
          </div>
          <div className="px-3 py-2">
            {rows.map((_, idx) => (
              <div key={idx} className="border-b border-dashed border-slate-100 py-1.5 text-[10px] text-slate-700">
                <div className="font-semibold">Product {idx + 1}</div>
                {setting.showSerial && idx === 0 && (
                  <div className="text-[9px] text-slate-400">SN:ABC-2048,Warranty:1Year,Dis 5,000</div>
                )}
                {idx === 0 && !setting.showSerial && (
                  <div className="text-[9px] text-slate-400">Warranty:1Year,Dis 5,000</div>
                )}
                <div className="mt-0.5 flex justify-between text-slate-500">
                  <span>1 × 25,000</span>
                  <b className="text-slate-800">25,000</b>
                </div>
              </div>
            ))}
          </div>
          <div className="space-y-1 border-t border-dashed border-slate-300 px-3 py-2 text-[10px]">
            <div className="flex justify-between"><span>Total</span><b>125,000</b></div>
            <div className="flex justify-between"><span>Paid</span><b>100,000</b></div>
            <div className="flex justify-between font-black"><span>Due</span><span>25,000</span></div>
          </div>
          {(setting.showQrCode || setting.footerNote) && (
            <div className="border-t border-dashed border-slate-300 px-3 py-3 text-center">
              {setting.showQrCode && <div className="mb-2 flex justify-center"><PreviewQr size={52} /></div>}
              {setting.footerNote && <p className="text-[9px] text-slate-500">{setting.footerNote}</p>}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-100 p-3">
      <div
        className="mx-auto overflow-hidden rounded-lg bg-white shadow-sm"
        style={{ width: paperWidth, fontFamily: setting.tableDataFontFamily || 'Arial' }}
      >
        {/* Compact white header: brand left · meta text + QR side-by-side */}
        <div
          className="border-b border-slate-200 bg-white"
          style={{
            minHeight: Math.max(setting.headerHeightPx || 52, 48),
            padding: `8px ${padX}px 7px`,
            fontFamily: headerFont,
          }}
        >
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 flex-1 items-center gap-2">
              {setting.showLogo && logoSrc && (
                <img src={logoSrc} alt="" className="h-9 max-w-[56px] shrink-0 rounded border border-slate-200 object-contain" />
              )}
              {setting.showLogo && !logoSrc && (
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded border border-slate-200 text-[8px] font-black text-slate-400">LOGO</div>
              )}
              <div className="min-w-0">
                <p className="truncate font-extrabold leading-tight text-slate-900" style={{ fontSize: Math.min(headerSize, 14) }}>{companyName}</p>
                {contact && <p className="mt-0.5 line-clamp-1 text-[8px] leading-3 text-slate-500">{contact}</p>}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2 border-l border-slate-200 pl-2">
              {setting.showQrCode && <PreviewQr size={isA5 ? 34 : 40} />}
              <div className="text-right">
                <p className="text-[7px] font-black uppercase tracking-wide text-slate-500">{title}</p>
                <p className="text-[7px] font-bold text-slate-400">CUSTOMER COPY</p>
                <p className="text-[12px] font-black leading-tight text-slate-900">INV-000123</p>
                <p className="text-[8px] text-slate-500">04/09/2026 14:30</p>
              </div>
            </div>
          </div>
        </div>
        <div className="h-0.5 bg-indigo-600" />

        <div
          className="grid grid-cols-2 gap-2 border-b border-slate-100 text-[10px] text-slate-600"
          style={{
            minHeight: Math.max(setting.infoBlocksHeightPx || 56, 46),
            padding: `10px ${padX}px`,
            fontFamily: setting.infoFontFamily || 'Arial',
            fontSize: Math.max(setting.infoFontSizePx || 11, 9),
          }}
        >
          <div><b className="text-slate-800">Customer</b><br />Walk-in Customer</div>
          <div><b className="text-slate-800">Cashier</b><br />Admin</div>
          <div><b className="text-slate-800">Phone</b><br />09-123456789</div>
          <div><b className="text-slate-800">Status</b><br />PAID</div>
        </div>

        <div style={{ padding: `8px ${padX}px 10px` }}>
          {(() => {
            const showWar = setting.showColWarranty !== false;
            const showDis = setting.showColLineDiscount !== false;
            const cols = [
              '22px',
              'minmax(0,1.2fr)',
              setting.showSerial ? 'minmax(0,0.9fr)' : null,
              showWar ? '52px' : null,
              showDis ? '48px' : null,
              '28px',
              '48px',
              '48px',
            ].filter(Boolean).join(' ');
            return (
              <>
                <div
                  className="gap-1 border-b border-slate-300 pb-1 text-[8px] font-black uppercase tracking-wide text-slate-700"
                  style={{
                    display: 'grid',
                    gridTemplateColumns: cols,
                    fontFamily: setting.tableHeaderFontFamily || 'Arial',
                    fontSize: Math.max((setting.tableHeaderFontSizePx || 10) - 1, 8),
                    minHeight: Math.max(setting.tableHeaderHeightPx || 22, 18),
                  }}
                >
                  <span className="text-center">#</span>
                  <span>Service</span>
                  {setting.showSerial && <span>Serial</span>}
                  {showWar && <span>Warranty</span>}
                  {showDis && <span className="text-right">Dis</span>}
                  <span className="text-center">Qty</span>
                  <span className="text-right">Unit</span>
                  <span className="text-right">Amt</span>
                </div>
                {rows.map((_, idx) => {
                  const sample = idx === 0
                    ? { name: 'iPhone 13 Display', sn: 'SN:ABC-2048', war: '1Year', disc: '5,000', qty: '1', unit: '85,000', amt: '80,000' }
                    : idx === 1
                      ? { name: 'Battery Replacement', sn: '—', war: '6Months', disc: '—', qty: '1', unit: '45,000', amt: '45,000' }
                      : { name: `Product ${idx + 1}`, sn: '—', war: '—', disc: '—', qty: '1', unit: '25,000', amt: '25,000' };
                  return (
                    <div
                      key={idx}
                      className="items-start gap-1 border-b border-dashed border-slate-100 py-1.5 text-[9px] text-slate-600"
                      style={{
                        display: 'grid',
                        gridTemplateColumns: cols,
                        minHeight: Math.max(setting.rowHeightPx || 26, 20),
                        fontSize: Math.max(setting.tableDataFontSizePx || 10, 8),
                      }}
                    >
                      <span className="text-center text-slate-400">{idx + 1}</span>
                      <div className="min-w-0 truncate font-medium text-slate-800">{sample.name}</div>
                      {setting.showSerial && <span className="min-w-0 truncate text-[8px] text-slate-500">{sample.sn}</span>}
                      {showWar && <span className="text-[8px] text-slate-500">{sample.war}</span>}
                      {showDis && <span className="text-right text-[8px] text-slate-500">{sample.disc}</span>}
                      <span className="text-center font-semibold">{sample.qty}</span>
                      <span className="text-right">{sample.unit}</span>
                      <span className="text-right font-semibold text-slate-800">{sample.amt}</span>
                    </div>
                  );
                })}
              </>
            );
          })()}
        </div>

        <div
          className="border-t border-slate-200 text-[10px] text-slate-700"
          style={{ minHeight: Math.max(setting.totalsAreaHeightPx || 72, 54), padding: `10px ${padX}px` }}
        >
          <div className="ml-auto w-36 space-y-1">
            <div className="flex justify-between"><span>Subtotal</span><b>125,000</b></div>
            {setting.showPaymentHistory && <div className="flex justify-between text-slate-500"><span>Paid</span><b>100,000</b></div>}
            <div className="flex justify-between border-t border-slate-200 pt-1 text-slate-900"><span>Balance</span><b>25,000</b></div>
          </div>
        </div>

        {(setting.footerNote || setting.customerNotice || setting.showSignatures) && (
          <div
            className="border-t border-slate-100 text-center text-[10px] leading-4 text-slate-500"
            style={{
              minHeight: Math.max(setting.footerHeightPx || 44, 32),
              padding: `10px ${padX}px`,
              fontFamily: setting.footerFontFamily || 'Arial',
              fontSize: Math.max(setting.footerFontSizePx || 10, 8),
            }}
          >
            {setting.footerNote && <p className="font-semibold text-slate-600">{setting.footerNote}</p>}
            {setting.customerNotice && (
              <p className="mt-1" style={{ fontFamily: setting.noticeFontFamily || 'Arial', fontSize: Math.max(setting.noticeFontSizePx || 9, 8) }}>
                {setting.customerNotice}
              </p>
            )}
            {setting.showSignatures && (
              <div className="mt-4 grid grid-cols-2 gap-6 text-slate-500">
                <span className="border-t border-slate-300 pt-1">{setting.sign1Label || 'Prepared By'}</span>
                <span className="border-t border-slate-300 pt-1">{setting.sign2Label || 'Customer'}</span>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default VoucherSettingsPage;
