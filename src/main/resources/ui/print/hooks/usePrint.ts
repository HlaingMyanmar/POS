import { useCallback, useRef, useState } from 'react';
import { DocumentType, PrintJob, PrintOptions } from '../types/print.types';
import { fetchHtmlPreview, fetchPdfObjectUrl } from '../utils/htmlPdfClient';
import { printQueue } from '../utils/printQueue';

// ─── useHtmlPreview ───────────────────────────────────────────────────────────

/**
 * Loads an HTML preview string from the backend for injection into an iframe.
 *
 * Usage:
 *   const { html, loading, load } = useHtmlPreview();
 *   <button onClick={() => load('SALE', saleId, options)}>Preview</button>
 *   {html && <iframe srcDoc={html} />}
 */
export function useHtmlPreview() {
  const [html, setHtml] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (type: DocumentType, id: number, options: PrintOptions) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchHtmlPreview(type, id, options);
        setHtml(result);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Preview failed');
      } finally {
        setLoading(false);
      }
    },
    []
  );

  const clear = useCallback(() => setHtml(null), []);

  return { html, loading, error, load, clear };
}

// ─── usePdfDownload ───────────────────────────────────────────────────────────

/**
 * Fetches a PDF from the backend and triggers browser download or opens in new tab.
 *
 * Usage:
 *   const { download, loading } = usePdfDownload();
 *   <button onClick={() => download('SALE', id, options, 'open')}>Open PDF</button>
 */
export function usePdfDownload() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const urlRef = useRef<string | null>(null);

  const execute = useCallback(
    async (
      type: DocumentType,
      id: number,
      options: PrintOptions,
      action: 'download' | 'open' = 'open'
    ) => {
      setLoading(true);
      setError(null);
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);

      try {
        const url = await fetchPdfObjectUrl(type, id, options);
        urlRef.current = url;

        if (action === 'download') {
          const a = document.createElement('a');
          a.href = url;
          a.download = `invoice-${id}.pdf`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 5000);
          urlRef.current = null;
        } else {
          window.open(url, '_blank');
        }
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'PDF generation failed');
      } finally {
        setLoading(false);
      }
    },
    []
  );

  return { execute, loading, error };
}

// ─── usePrintQueue ────────────────────────────────────────────────────────────

/**
 * Subscribes to the global print queue and exposes its job list.
 *
 * Usage:
 *   const { jobs, addJob, removeJob } = usePrintQueue();
 */
export function usePrintQueue() {
  const [jobs, setJobs] = useState<PrintJob[]>([]);

  // Subscribe on mount; unsubscribe on unmount
  const subscribed = useRef(false);
  if (!subscribed.current) {
    subscribed.current = true;
    printQueue.subscribe(setJobs);
  }

  const addJob = useCallback(
    (type: DocumentType, id: number, options: PrintOptions) =>
      printQueue.add(type, id, options),
    []
  );

  const removeJob = useCallback((jobId: string) => printQueue.remove(jobId), []);

  const clearFinished = useCallback(() => printQueue.clearFinished(), []);

  return { jobs, addJob, removeJob, clearFinished };
}

// ─── useIframePrint ───────────────────────────────────────────────────────────

/**
 * Triggers the browser print dialog on an iframe containing HTML.
 * Used with the print preview modal.
 */
export function useIframePrint() {
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const print = useCallback(() => {
    const win = iframeRef.current?.contentWindow;
    if (!win) return;
    win.focus();
    win.print();
  }, []);

  return { iframeRef, print };
}
