import React, { useRef } from 'react';
import { Printer, X } from 'lucide-react';

type PrintPreviewModalProps = {
  html: string;
  title?: string;
  onClose: () => void;
};

const PrintPreviewModal: React.FC<PrintPreviewModalProps> = ({ html, title = 'Print Preview', onClose }) => {
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const handlePrint = () => {
    const iframe = iframeRef.current;
    if (!iframe?.contentWindow) return;
    iframe.contentWindow.focus();
    iframe.contentWindow.print();
  };

  return (
    <div className="fixed inset-0 z-[60] flex flex-col" style={{ background: 'rgba(15,23,42,0.88)' }}>
      <div className="flex items-center justify-between px-4 py-3 bg-white border-b border-slate-200 flex-shrink-0 shadow-sm">
        <h3 className="text-sm font-bold text-slate-800">{title}</h3>
        <div className="flex items-center gap-2">
          <button
            onClick={handlePrint}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 transition-colors"
          >
            <Printer size={14} /> Print
          </button>
          <button
            onClick={onClose}
            className="p-2 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-700 transition-colors"
          >
            <X size={16} />
          </button>
        </div>
      </div>
      <div className="flex-1 overflow-auto bg-slate-300 p-4 flex justify-center">
        <iframe
          ref={iframeRef}
          srcDoc={html}
          title={title}
          className="w-full max-w-4xl bg-white shadow-2xl"
          style={{ minHeight: 'calc(100vh - 100px)', border: 'none' }}
        />
      </div>
    </div>
  );
};

export default PrintPreviewModal;
