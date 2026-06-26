import axios from 'axios';
import { DocumentType, PaperSize, PrintApiRequest, PrintOptions } from '../types/print.types';
import { getAccessToken } from '../../services/api';

const BASE = '/api/v1/print';

function authHeaders(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * Fetches an HTML preview string from the backend (Thymeleaf rendered).
 * Inject the result into an iframe srcDoc for print preview.
 */
export async function fetchHtmlPreview(
  documentType: DocumentType,
  documentId: number,
  options: PrintOptions
): Promise<string> {
  const body = buildRequest(documentType, documentId, options);
  const { data } = await axios.post<string>(`${BASE}/preview`, body, {
    responseType: 'text',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
  });
  return data;
}

/**
 * Requests a PDF from the backend and returns an object URL suitable
 * for <a href> downloads or an <iframe src>.
 *
 * Remember to call URL.revokeObjectURL(url) when done to avoid memory leaks.
 */
export async function fetchPdfObjectUrl(
  documentType: DocumentType,
  documentId: number,
  options: PrintOptions
): Promise<string> {
  const body = buildRequest(documentType, documentId, options);
  const { data } = await axios.post(`${BASE}/pdf`, body, {
    responseType: 'blob',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
  });
  return URL.createObjectURL(new Blob([data], { type: 'application/pdf' }));
}

/**
 * Convenience GET for a sale PDF: opens inline in the browser.
 */
export function buildSalePdfUrl(saleId: number, paper: PaperSize = 'A4'): string {
  return `${BASE}/pdf/sale/${saleId}?paper=${paper}`;
}

export function buildServiceJobPdfUrl(jobId: number, paper: PaperSize = 'A4'): string {
  return `${BASE}/pdf/service-job/${jobId}?paper=${paper}`;
}

export function buildBookingPdfUrl(bookingId: number, paper: PaperSize = 'A4'): string {
  return `${BASE}/pdf/booking/${bookingId}?paper=${paper}`;
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function buildRequest(
  documentType: DocumentType,
  documentId: number,
  options: PrintOptions
): PrintApiRequest {
  return {
    documentType,
    documentId,
    paperSize: options.paperSize,
    showLogo: options.showLogo ?? true,
    showSerial: options.showSerial ?? true,
    showPaymentHistory: options.showPaymentHistory ?? true,
    showSignatures: options.showSignatures ?? false,
    showQrCode: options.showQrCode ?? false,
    sign1Label: options.sign1Label ?? 'Prepared By',
    sign2Label: options.sign2Label ?? 'Received By',
  };
}
