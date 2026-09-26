import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Printer, X, Download, RefreshCw, FileText, ZoomIn, ZoomOut, Receipt, Send } from 'lucide-react';
import { DocumentType, PaperSize, PrintOptions } from '../types/print.types';
import { useHtmlPreview, useIframePrint, usePdfDownload, useInvoiceSend } from '../hooks/usePrint';
import { voucherSettingService, VoucherSettingDto, DocumentType as VoucherDocType } from '../../services/voucherSettingService';

interface InvoicePrintPreviewProps {
  documentType: DocumentType;
  documentId: number;
  title?: string;
  defaultPaper?: PaperSize;
  onClose: () => void;
}

type PaperOption = { label: string; hint?: string; value: PaperSize };
type ZoomMode = 'fit-width' | 'fit-page' | 'manual';

const PAPER_OPTIONS_DEFAULT: PaperOption[] = [
  { label: 'A4', value: 'A4', hint: 'Full page' },
  { label: 'A5', value: 'A5', hint: 'Half page' },
  { label: '80mm', value: 'POS_80MM', hint: 'Thermal' },
  { label: '58mm', value: 'POS_58MM', hint: 'Thermal' },
];

const PAPER_OPTIONS_BOOKING: PaperOption[] = [
  { label: '80mm', value: 'POS_80MM', hint: 'Thermal' },
  { label: '58mm', value: 'POS_58MM', hint: 'Thermal' },
  { label: 'A5', value: 'A5', hint: 'Half page' },
  { label: 'A4', value: 'A4', hint: 'Full page' },
];

const PAPER_MAP: Record<string, PaperSize> = {
  A4: 'A4',
  A5: 'A5',
  POS_80MM: 'POS_80MM',
  POS_58MM: 'POS_58MM',
};

/** Physical page size in CSS mm — preview zoom only; print/@page sizes stay unchanged. */
function pageDimsMm(paper: PaperSize): { w: number; h: number } {
  switch (paper) {
    case 'POS_58MM': return { w: 58, h: 220 };
    case 'POS_80MM': return { w: 80, h: 240 };
    case 'A5': return { w: 148, h: 210 };
    default: return { w: 210, h: 297 };
  }
}

function previewWidthFor(paper: PaperSize): string {
  return `${pageDimsMm(paper).w}mm`;
}

/**
 * Full-screen print preview modal — voucher / invoice preview with paper switcher.
 */
export const InvoicePrintPreview: React.FC<InvoicePrintPreviewProps> = ({
  documentType,
  documentId,
  title = 'Print Preview',
  defaultPaper = 'A4',
  onClose,
}) => {
  const isBooking = documentType === 'BOOKING';
  const paperOptions = isBooking ? PAPER_OPTIONS_BOOKING : PAPER_OPTIONS_DEFAULT;
  const resolvedDefault = isBooking ? (defaultPaper || 'POS_80MM') : defaultPaper;

  const [paperSize, setPaperSize] = useState<PaperSize>(resolvedDefault);
  const [zoomMode, setZoomMode] = useState<ZoomMode>('fit-width');
  const [manualZoom, setManualZoom] = useState(100);
  const [fitZoom, setFitZoom] = useState(100);
  const [voucherSetting, setVoucherSetting] = useState<VoucherSettingDto | null>(null);
  const [settingsReady, setSettingsReady] = useState(false);
  const [copyType, setCopyType] = useState<'CUSTOMER' | 'SHOP' | 'BOTH'>('CUSTOMER');
  const workspaceRef = useRef<HTMLDivElement>(null);
  const pageWrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    voucherSettingService.getByType(documentType as VoucherDocType)
      .then(s => {
        setVoucherSetting(s);
        // Booking preview: keep toolbar default (80mm); DB paper is for admin defaults only.
        if (!isBooking) {
          const mapped = s.paperSize && PAPER_MAP[s.paperSize] ? PAPER_MAP[s.paperSize] : resolvedDefault;
          setPaperSize(mapped);
        }
      })
      .catch(() => {})
      .finally(() => setSettingsReady(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentType]);

  const options: PrintOptions = useMemo(() => ({
    paperSize,
    design: 'STANDARD',
    showLogo: voucherSetting?.showLogo ?? true,
    showSerial: voucherSetting?.showSerial ?? true,
    showWarranty: voucherSetting?.showColWarranty ?? true,
    showLineDiscount: voucherSetting?.showColLineDiscount ?? true,
    showPaymentHistory: voucherSetting?.showPaymentHistory ?? true,
    showSignatures: voucherSetting?.showSignatures ?? false,
    showQrCode: voucherSetting?.showQrCode ?? false,
    sign1Label: voucherSetting?.sign1Label || 'Prepared By',
    sign2Label: voucherSetting?.sign2Label || 'Received By',
    rowsOverride: 0,
    copyType,
  }), [paperSize, copyType, voucherSetting]);

  const { html, loading, error, load } = useHtmlPreview();
  const { iframeRef, print } = useIframePrint();
  const { execute: downloadPdf, loading: pdfLoading } = usePdfDownload();
  const { execute: sendPdf, loading: sendLoading } = useInvoiceSend();

  const recomputeFit = useCallback(() => {
    const workspace = workspaceRef.current;
    if (!workspace) return;
    const pad = 40;
    const availW = Math.max(120, workspace.clientWidth - pad);
    const availH = Math.max(120, workspace.clientHeight - pad);
    const { w, h } = pageDimsMm(paperSize);
    // CSS mm → px at 96dpi
    const pageWpx = w * 3.779527559;
    const pageHpx = h * 3.779527559;
    const widthScale = (availW / pageWpx) * 100;
    const pageScale = Math.min(availW / pageWpx, availH / pageHpx) * 100;
    const next = zoomMode === 'fit-page'
      ? Math.max(40, Math.min(160, Math.floor(pageScale)))
      : Math.max(50, Math.min(160, Math.floor(widthScale)));
    setFitZoom(next);
  }, [paperSize, zoomMode]);

  useEffect(() => {
    recomputeFit();
    const el = workspaceRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => recomputeFit());
    ro.observe(el);
    return () => ro.disconnect();
  }, [recomputeFit, html, loading]);

  useEffect(() => {
    if (!settingsReady) return;
    load(documentType, documentId, options);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentType, documentId, paperSize, copyType, settingsReady]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
        e.preventDefault();
        print();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose, print]);

  const handleDownload = () => {
    downloadPdf(documentType, documentId, options, 'download');
  };

  const handleSend = async () => {
    const to = window.prompt('Invoice ပို့မည့် email\n(ကွက်လပ်ထားရင် ဖောက်သည် email သို့ ပို့မည်)', '');
    if (to === null) return;
    try {
      const sent = await sendPdf(documentType, documentId, options, to.trim());
      window.alert('Invoice ပို့ပြီးပါပြီ\n' + sent);
    } catch (err: unknown) {
      window.alert(err instanceof Error ? err.message : 'Invoice မပို့နိုင်ပါ');
    }
  };

  const resizeIframeToContent = () => {
    const iframe = iframeRef.current;
    if (!iframe) return;
    try {
      const doc = iframe.contentDocument;
      if (!doc?.body) return;
      const height = Math.max(doc.body.scrollHeight, doc.documentElement?.scrollHeight || 0);
      if (height > 0) {
        iframe.style.height = `${height + 8}px`;
      }
    } catch {
      // cross-origin / empty — ignore
    }
  };

  const displayZoom = zoomMode === 'manual' ? manualZoom : fitZoom;
  const HeaderIcon = isBooking ? Receipt : FileText;
  const zoomLabel = zoomMode === 'fit-width' ? `Fit W · ${fitZoom}%`
    : zoomMode === 'fit-page' ? `Fit Page · ${fitZoom}%`
    : `${manualZoom}%`;

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-slate-950/90">
      {/* Toolbar — never printed (outside iframe) */}
      <header className="shrink-0 border-b border-slate-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center gap-3 px-4 py-3">
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-indigo-50 text-indigo-700">
              <HeaderIcon size={20} />
            </div>
            <div className="min-w-0">
              <h2 className="truncate text-base font-extrabold text-slate-900">{title}</h2>
              <p className="text-xs text-slate-500">
                {isBooking ? `Booking #${documentId} · လက်ခံဘောင်ချာ preview` : `Document #${documentId}`}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => load(documentType, documentId, options)}
              disabled={loading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
              title="Reload preview"
            >
              <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
              Refresh
            </button>
            <button
              type="button"
              onClick={handleDownload}
              disabled={pdfLoading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
            >
              <Download size={14} />
              {pdfLoading ? 'PDF…' : 'PDF'}
            </button>
            <button
              type="button"
              onClick={() => void handleSend()}
              disabled={sendLoading}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
            >
              <Send size={14} />
              {sendLoading ? 'ပို့နေသည်…' : 'Send'}
            </button>
            <button
              type="button"
              onClick={print}
              className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-indigo-700"
            >
              <Printer size={16} />
              ပရင့်ထုတ်မည်
            </button>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-10 w-10 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100"
              title="Close (Esc)"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3 border-t border-slate-100 bg-slate-50/80 px-4 py-3">
          <span className="text-[11px] font-bold uppercase tracking-wide text-slate-500">စက္ကူ</span>
          <div className="flex flex-wrap gap-2">
            {paperOptions.map(p => {
              const active = paperSize === p.value;
              return (
                <button
                  key={p.value}
                  type="button"
                  onClick={() => setPaperSize(p.value)}
                  className={`rounded-xl border px-3 py-2 text-left transition-colors ${
                    active
                      ? 'border-indigo-500 bg-indigo-50 text-indigo-800 shadow-sm ring-1 ring-indigo-200'
                      : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                  }`}
                >
                  <div className="text-sm font-bold leading-none">{p.label}</div>
                  {p.hint && <div className="mt-0.5 text-[10px] text-slate-500">{p.hint}</div>}
                </button>
              );
            })}
          </div>

          <div className="ml-auto flex flex-wrap items-center gap-1">
            <div className="flex items-center gap-0.5 rounded-lg border border-slate-200 bg-white p-0.5">
              {([
                ['fit-width', 'Fit Width'],
                ['fit-page', 'Fit Page'],
                ['manual', '100%'],
              ] as const).map(([mode, label]) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => {
                    if (mode === 'manual') {
                      setZoomMode('manual');
                      setManualZoom(100);
                    } else {
                      setZoomMode(mode);
                    }
                  }}
                  className={`rounded-md px-2 py-1.5 text-[11px] font-semibold ${
                    zoomMode === mode || (mode === 'manual' && zoomMode === 'manual' && manualZoom === 100)
                      ? 'bg-slate-800 text-white'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-1 py-1">
              <button
                type="button"
                onClick={() => {
                  setZoomMode('manual');
                  setManualZoom(z => Math.max(50, (zoomMode === 'manual' ? z : fitZoom) - 10));
                }}
                className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100"
                title="Zoom out"
              >
                <ZoomOut size={14} />
              </button>
              <span className="min-w-[5.5rem] text-center text-xs font-semibold text-slate-600">{zoomLabel}</span>
              <button
                type="button"
                onClick={() => {
                  setZoomMode('manual');
                  setManualZoom(z => Math.min(200, (zoomMode === 'manual' ? z : fitZoom) + 10));
                }}
                className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100"
                title="Zoom in"
              >
                <ZoomIn size={14} />
              </button>
            </div>
          </div>

          {documentType === 'SALE' && (
            <select
              value={copyType}
              onChange={e => setCopyType(e.target.value as typeof copyType)}
              className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs font-semibold text-slate-600"
              title="Print copies only — does not create extra sales"
            >
              <option value="CUSTOMER">Customer Copy</option>
              <option value="SHOP">Office / Merchant Copy</option>
              <option value="BOTH">Both Copies</option>
            </select>
          )}
        </div>
      </header>

      {/* Preview workspace */}
      <div
        ref={workspaceRef}
        className="flex flex-1 justify-center overflow-auto bg-[#E8EDF3] p-4 sm:p-6"
      >
        {loading && (
          <div className="flex min-h-[240px] flex-col items-center justify-center gap-3 self-center">
            <RefreshCw size={28} className="animate-spin text-indigo-600" />
            <p className="text-sm font-medium text-slate-500">Voucher ပြင်ဆင်နေသည်…</p>
          </div>
        )}

        {error && !loading && (
          <div className="flex min-h-[240px] flex-col items-center justify-center gap-3 self-center rounded-2xl border border-rose-200 bg-white px-8 py-10 shadow-sm">
            <p className="text-sm font-semibold text-rose-600">{error}</p>
            <button
              type="button"
              onClick={() => load(documentType, documentId, options)}
              className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-bold text-white hover:bg-indigo-700"
            >
              ထပ်မံ ကြိုးစားမည်
            </button>
          </div>
        )}

        {html && !loading && (
          <div
            ref={pageWrapRef}
            className="origin-top transition-transform"
            style={{ transform: `scale(${displayZoom / 100})` }}
          >
            <div className="overflow-hidden rounded-sm border border-slate-300 bg-white shadow-[0_8px_40px_rgba(15,23,42,0.18)]">
              <iframe
                ref={iframeRef}
                srcDoc={html}
                title={title}
                className="block border-0 bg-white"
                style={{
                  width: previewWidthFor(paperSize),
                  minHeight: `${pageDimsMm(paperSize).h}mm`,
                }}
                onLoad={resizeIframeToContent}
              />
            </div>
          </div>
        )}
      </div>

      <footer className="shrink-0 border-t border-slate-800 bg-slate-950/80 py-2 text-center text-[11px] text-slate-400">
        Ctrl+P ပရင့် · Esc ပိတ်ရန် · Fit Width / Fit Page သည် preview zoom သာဖြစ်ပြီး PDF အရွယ်အစား မပြောင်းပါ
        {' · '}Browser print dialog တွင် Headers and footers ကို ပိတ်ပါ (URL/date မပါစေရန်)
      </footer>
    </div>
  );
};
