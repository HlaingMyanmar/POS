import React, { useState } from 'react';
import { Printer, FileDown, Eye, Loader } from 'lucide-react';
import { DocumentType, PaperSize, PrintOptions } from '../types/print.types';
import { usePdfDownload } from '../hooks/usePrint';

interface PrintButtonProps {
  documentType: DocumentType;
  documentId: number;
  options?: Partial<PrintOptions>;
  label?: string;
  variant?: 'primary' | 'secondary' | 'ghost';
  size?: 'sm' | 'md';
  onPreview?: () => void;
}

const VARIANT_CLASSES: Record<string, string> = {
  primary:   'inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 transition-colors disabled:opacity-60',
  secondary: 'inline-flex items-center gap-1.5 px-4 py-2 rounded-lg border border-slate-300 text-slate-700 bg-white text-sm font-semibold hover:bg-slate-50 transition-colors disabled:opacity-60',
  ghost:     'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-slate-600 text-sm font-medium hover:bg-slate-100 transition-colors disabled:opacity-60',
};

/**
 * Unified print action button with three modes:
 *  1. "Print" — fetches PDF and opens in new tab (browser's own print dialog)
 *  2. "Download" — fetches PDF and triggers file download
 *  3. "Preview" — calls onPreview callback (caller opens the preview modal)
 *
 * Usage:
 *   <PrintButton
 *     documentType="SALE"
 *     documentId={sale.id}
 *     options={{ paperSize: 'A4' }}
 *     onPreview={() => setShowPreview(true)}
 *   />
 */
export const PrintButton: React.FC<PrintButtonProps> = ({
  documentType,
  documentId,
  options = {},
  label = 'Print',
  variant = 'primary',
  size = 'md',
  onPreview,
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const { execute, loading } = usePdfDownload();

  const resolvedOptions: PrintOptions = {
    paperSize: 'A4',
    design: 'STANDARD',
    showSerial: true,
    showPaymentHistory: true,
    showSignatures: false,
    sign1Label: 'Prepared By',
    sign2Label: 'Received By',
    rowsOverride: 0,
    ...options,
  };

  const handlePrint = () => {
    execute(documentType, documentId, resolvedOptions, 'open');
    setMenuOpen(false);
  };

  const handleDownload = () => {
    execute(documentType, documentId, resolvedOptions, 'download');
    setMenuOpen(false);
  };

  const iconSize = size === 'sm' ? 13 : 15;
  const btnClass = VARIANT_CLASSES[variant];

  if (!onPreview) {
    return (
      <button onClick={handlePrint} disabled={loading} className={btnClass}>
        {loading ? <Loader size={iconSize} className="animate-spin" /> : <Printer size={iconSize} />}
        {label}
      </button>
    );
  }

  return (
    <div style={{ position: 'relative', display: 'inline-block' }}>
      <div style={{ display: 'flex', gap: 1 }}>
        <button onClick={handlePrint} disabled={loading} className={btnClass}>
          {loading ? <Loader size={iconSize} className="animate-spin" /> : <Printer size={iconSize} />}
          {label}
        </button>
        <button
          onClick={() => setMenuOpen((o) => !o)}
          disabled={loading}
          className={`${VARIANT_CLASSES.secondary} px-2`}
          style={{ borderLeft: '1px solid #e2e8f0' }}
          title="More options"
        >
          <span style={{ fontSize: 10 }}>▾</span>
        </button>
      </div>

      {menuOpen && (
        <>
          {/* backdrop */}
          <div
            style={{ position: 'fixed', inset: 0, zIndex: 40 }}
            onClick={() => setMenuOpen(false)}
          />
          <div
            style={{
              position: 'absolute', top: '100%', right: 0, zIndex: 50,
              background: '#fff', border: '1px solid #e2e8f0',
              borderRadius: 8, boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
              minWidth: 170, marginTop: 4, overflow: 'hidden',
            }}
          >
            <button
              onClick={handlePrint}
              style={menuItemStyle}
            >
              <Printer size={13} /> Open PDF
            </button>
            <button
              onClick={handleDownload}
              style={menuItemStyle}
            >
              <FileDown size={13} /> Download PDF
            </button>
            {onPreview && (
              <button
                onClick={() => { onPreview(); setMenuOpen(false); }}
                style={menuItemStyle}
              >
                <Eye size={13} /> HTML Preview
              </button>
            )}
            {/* Paper size submenu */}
            {(['A4', 'A5'] as PaperSize[]).map((p) => (
              <button
                key={p}
                onClick={() => {
                  execute(documentType, documentId, { ...resolvedOptions, paperSize: p }, 'open');
                  setMenuOpen(false);
                }}
                style={{ ...menuItemStyle, color: '#6b7280', fontSize: 12 }}
              >
                <Printer size={12} /> Print {p}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
};

const menuItemStyle: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8,
  padding: '8px 14px', width: '100%', textAlign: 'left',
  background: 'transparent', border: 'none', cursor: 'pointer',
  fontSize: 13, color: '#1e293b',
  borderBottom: '1px solid #f1f5f9',
};
