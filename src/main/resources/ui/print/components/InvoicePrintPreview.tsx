import React, { useEffect, useRef, useState } from 'react';
import { Printer, X, Download, RefreshCw, FileText, ZoomIn, ZoomOut } from 'lucide-react';
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

const PAPER_OPTIONS: { label: string; value: PaperSize }[] = [
  { label: 'A4',      value: 'A4' },
  { label: 'A5',      value: 'A5' },
  { label: '80mm POS', value: 'POS_80MM' },
  { label: '58mm POS', value: 'POS_58MM' },
];


/**
 * Full-screen print preview modal.
 *
 * Features:
 *  - Paper size switcher (A4 / A5 / POS thermal)
 *  - Inline HTML preview in iframe (fast, no PDF needed)
 *  - Print button → triggers browser print dialog on iframe
 *  - PDF download button → fetches backend PDF
 *  - Zoom in / out for the iframe preview
 *  - Keyboard shortcut: Escape closes, Ctrl+P prints
 */
export const InvoicePrintPreview: React.FC<InvoicePrintPreviewProps> = ({
  documentType,
  documentId,
  title = 'Print Preview',
  defaultPaper = 'A4',
  onClose,
}) => {
  const [paperSize, setPaperSize]           = useState<PaperSize>(defaultPaper);
  const [zoom, setZoom]                     = useState(100);
  const [voucherSetting, setVoucherSetting] = useState<VoucherSettingDto | null>(null);
  const [settingsReady, setSettingsReady]   = useState(false);

  const PAPER_MAP: Record<string, PaperSize> = { A4: 'A4', A5: 'A5', POS_80MM: 'POS_80MM', POS_58MM: 'POS_58MM' };

  // Load saved voucher settings for this document type
  useEffect(() => {
    voucherSettingService.getByType(documentType as VoucherDocType)
      .then(s => {
        setVoucherSetting(s);
        const mapped = s.paperSize && PAPER_MAP[s.paperSize] ? PAPER_MAP[s.paperSize] : defaultPaper;
        setPaperSize(mapped);
      })
      .catch(() => {})
      .finally(() => setSettingsReady(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentType]);

  // Build options from saved settings (fallback to sensible defaults)
  const options: PrintOptions = {
    paperSize,
    design: 'STANDARD',
    showLogo:           voucherSetting?.showLogo           ?? true,
    showSerial:         voucherSetting?.showSerial         ?? true,
    showPaymentHistory: voucherSetting?.showPaymentHistory ?? true,
    showSignatures:     voucherSetting?.showSignatures     ?? false,
    showQrCode:         voucherSetting?.showQrCode         ?? false,
    sign1Label:         voucherSetting?.sign1Label        || 'Prepared By',
    sign2Label:         voucherSetting?.sign2Label        || 'Received By',
    rowsOverride: 0,
  };

  const { html, loading, error, load } = useHtmlPreview();
  const { iframeRef, print } = useIframePrint();
  const { execute: downloadPdf, loading: pdfLoading } = usePdfDownload();

  // Load preview only after settings are fetched (so options include saved values)
  useEffect(() => {
    if (!settingsReady) return;
    load(documentType, documentId, options);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [documentType, documentId, paperSize, settingsReady]);

  // Keyboard shortcuts
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

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 60,
        display: 'flex', flexDirection: 'column',
        background: 'rgba(15,23,42,0.9)',
      }}
    >
      {/* ── Toolbar ── */}
      <div
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '8px 16px',
          background: '#fff', borderBottom: '1px solid #e2e8f0',
          flexShrink: 0, gap: 12,
        }}
      >
        {/* Title */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileText size={16} style={{ color: '#6366f1' }} />
          <span style={{ fontSize: 14, fontWeight: 700, color: '#1e293b' }}>{title}</span>
        </div>

        {/* Paper switcher */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ fontSize: 11, color: '#64748b', marginRight: 4 }}>Paper:</span>
          {PAPER_OPTIONS.map((p) => (
            <button
              key={p.value}
              onClick={() => setPaperSize(p.value)}
              style={{
                padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 600,
                border: '1px solid',
                borderColor: paperSize === p.value ? '#6366f1' : '#e2e8f0',
                background: paperSize === p.value ? '#eef2ff' : '#fff',
                color: paperSize === p.value ? '#4338ca' : '#475569',
                cursor: 'pointer',
              }}
            >
              {p.label}
            </button>
          ))}
        </div>

        {/* Zoom */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button onClick={() => setZoom((z) => Math.max(50, z - 10))}
                  style={toolBtnStyle} title="Zoom out">
            <ZoomOut size={14} />
          </button>
          <span style={{ fontSize: 12, color: '#64748b', minWidth: 36, textAlign: 'center' }}>
            {zoom}%
          </span>
          <button onClick={() => setZoom((z) => Math.min(200, z + 10))}
                  style={toolBtnStyle} title="Zoom in">
            <ZoomIn size={14} />
          </button>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 6 }}>
          <button onClick={() => load(documentType, documentId, options)}
                  style={toolBtnStyle} title="Reload preview" disabled={loading}>
            <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            onClick={handleDownload}
            disabled={pdfLoading}
            style={{
              ...toolBtnStyle,
              padding: '6px 12px', background: '#f8fafc',
              border: '1px solid #e2e8f0', borderRadius: 8,
              display: 'flex', alignItems: 'center', gap: 5,
              fontSize: 12, fontWeight: 600, color: '#475569',
            }}
          >
            <Download size={13} />
            {pdfLoading ? 'Generating…' : 'PDF'}
          </button>
          <button
            onClick={print}
            style={{
              ...toolBtnStyle,
              padding: '6px 14px', background: '#6366f1',
              border: 'none', borderRadius: 8,
              display: 'flex', alignItems: 'center', gap: 5,
              fontSize: 13, fontWeight: 700, color: '#fff',
            }}
          >
            <Printer size={14} /> Print
          </button>
          <button onClick={onClose} style={{ ...toolBtnStyle, marginLeft: 4 }} title="Close (Esc)">
            <X size={16} />
          </button>
        </div>
      </div>

      {/* ── Preview area ── */}
      <div
        style={{
          flex: 1, overflow: 'auto', padding: 24,
          display: 'flex', justifyContent: 'center', alignItems: 'flex-start',
        }}
      >
        {loading && (
          <div style={centeredMsg}>
            <RefreshCw size={24} style={{ color: '#6366f1', animation: 'spin 1s linear infinite' }} />
            <span style={{ color: '#94a3b8', fontSize: 13, marginTop: 10 }}>Rendering preview…</span>
          </div>
        )}

        {error && !loading && (
          <div style={centeredMsg}>
            <span style={{ color: '#ef4444', fontSize: 14 }}>{error}</span>
            <button
              onClick={() => load(documentType, documentId, options)}
              style={{ marginTop: 12, padding: '6px 16px', borderRadius: 8, background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        )}

        {html && !loading && (
          <iframe
            ref={iframeRef}
            srcDoc={html}
            title={title}
            style={{
              border: 'none',
              width: paperSize === 'A5' ? '148mm' : paperSize.startsWith('POS') ? '100mm' : '210mm',
              minHeight: '80vh',
              boxShadow: '0 4px 32px rgba(0,0,0,0.25)',
              background: '#fff',
              transform: `scale(${zoom / 100})`,
              transformOrigin: 'top center',
              marginBottom: zoom < 100 ? `-${(100 - zoom) * 4}px` : 0,
            }}
          />
        )}
      </div>

      {/* Shortcut hint */}
      <div style={{
        textAlign: 'center', padding: '4px 0 8px',
        fontSize: 10.5, color: '#64748b',
      }}>
        Ctrl+P to print · Esc to close
      </div>
    </div>
  );
};

const toolBtnStyle: React.CSSProperties = {
  padding: 6, borderRadius: 6, border: 'none',
  background: 'transparent', cursor: 'pointer',
  display: 'flex', alignItems: 'center',
  color: '#475569', transition: 'background 0.15s',
};

const centeredMsg: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', alignItems: 'center',
  justifyContent: 'center', minHeight: 200, gap: 4,
};
