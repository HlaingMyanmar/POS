import React, { useEffect, useMemo, useState } from 'react';
import { Printer, X, Download, RefreshCw, FileText, ZoomIn, ZoomOut, Receipt } from 'lucide-react';
import { DocumentType, PaperSize, PrintOptions } from '../types/print.types';
import { useHtmlPreview, useIframePrint, usePdfDownload } from '../hooks/usePrint';
import { voucherSettingService, VoucherSettingDto, DocumentType as VoucherDocType } from '../../services/voucherSettingService';

interface InvoicePrintPreviewProps {
  documentType: DocumentType;
  documentId: number;
  title?: string;
  defaultPaper?: PaperSize;
  onClose: () => void;
}

type PaperOption = { label: string; hint?: string; value: PaperSize };

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

function previewWidthFor(paper: PaperSize): string {
  switch (paper) {
    case 'POS_58MM': return '58mm';
    case 'POS_80MM': return '80mm';
    case 'A5': return '148mm';
    default: return '210mm';
  }
}

function previewHeightFor(paper: PaperSize): string {
  switch (paper) {
    case 'POS_58MM':
    case 'POS_80MM':
      return '70vh';
    case 'A5':
      return '210mm';
    default:
      return '297mm';
  }
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
  const [zoom, setZoom] = useState(100);
  const [voucherSetting, setVoucherSetting] = useState<VoucherSettingDto | null>(null);
  const [settingsReady, setSettingsReady] = useState(false);
  const [copyType, setCopyType] = useState<'CUSTOMER' | 'SHOP' | 'BOTH'>('CUSTOMER');

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

  const HeaderIcon = isBooking ? Receipt : FileText;

  return (
    <div className="fixed inset-0 z-[60] flex flex-col bg-slate-950/90">
      {/* Toolbar */}
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
                      ? 'border-indigo-500 bg-indigo-50 text-indigo-800 shadow-sm'
                      : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                  }`}
                >
                  <div className="text-sm font-bold leading-none">{p.label}</div>
                  {p.hint && <div className="mt-0.5 text-[10px] text-slate-500">{p.hint}</div>}
                </button>
              );
            })}
          </div>

          <div className="ml-auto flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-1 py-1">
            <button
              type="button"
              onClick={() => setZoom(z => Math.max(50, z - 10))}
              className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100"
              title="Zoom out"
            >
              <ZoomOut size={14} />
            </button>
            <span className="min-w-[3rem] text-center text-xs font-semibold text-slate-600">{zoom}%</span>
            <button
              type="button"
              onClick={() => setZoom(z => Math.min(200, z + 10))}
              className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100"
              title="Zoom in"
            >
              <ZoomIn size={14} />
            </button>
          </div>

          {documentType === 'SALE' && (
            <select
              value={copyType}
              onChange={e => setCopyType(e.target.value as typeof copyType)}
              className="rounded-lg border border-slate-200 bg-white px-2 py-2 text-xs font-semibold text-slate-600"
            >
              <option value="CUSTOMER">Customer Copy</option>
              <option value="SHOP">Shop Copy</option>
              <option value="BOTH">Both Copies</option>
            </select>
          )}
        </div>
      </header>

      {/* Preview */}
      <div className="flex flex-1 justify-center overflow-auto bg-[#F4F7FA] p-4 sm:p-8">
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
            className="origin-top transition-transform"
            style={{ transform: `scale(${zoom / 100})` }}
          >
            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
              <iframe
                ref={iframeRef}
                srcDoc={html}
                title={title}
                className="block border-0 bg-white"
                style={{
                  width: previewWidthFor(paperSize),
                  minHeight: previewHeightFor(paperSize),
                }}
              />
            </div>
          </div>
        )}
      </div>

      <footer className="shrink-0 border-t border-slate-800 bg-slate-950/80 py-2 text-center text-[11px] text-slate-400">
        Ctrl+P ပရင့် · Esc ပိတ်ရန် · Pinch/Zoom slider ဖြင့် ကြည့်နိုင်ပါသည်
      </footer>
    </div>
  );
};
