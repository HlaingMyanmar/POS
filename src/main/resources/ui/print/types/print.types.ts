// ─── Paper / design enums ────────────────────────────────────────────────────

export type PaperSize = 'A4' | 'A5' | 'POS_58MM' | 'POS_80MM';
export type PrintDesign = 'STANDARD' | 'POS';
export type DocumentType = 'SALE' | 'BOOKING' | 'SERVICE_JOB' | 'SERVICE_DONE' | 'PURCHASE';

// ─── Page dimension constants ────────────────────────────────────────────────

export interface PageDimensions {
  /** Page width in mm */
  widthMm: number;
  /** Page height in mm (0 = auto for thermal) */
  heightMm: number;
  marginHorizMm: number;
  marginVertMm: number;
  /** Max table rows on page 1 (header + info blocks consume space) */
  rowsOnFirstPage: number;
  /** Max table rows on continuation pages */
  rowsOnContinuationPage: number;
  /** CSS @page size string */
  cssPageSize: string;
}

// ─── Invoice data model ──────────────────────────────────────────────────────

export interface PrintLineItem {
  rowNo: number;
  productName: string;
  serialInfo?: string;
  warrantyLabel?: string;
  qty: number;
  unitPrice: string;
  subtotal: string;
  discount?: string;
}

export interface PaymentEntry {
  date: string;
  method: string;
  amount: string;
}

export interface InvoiceData {
  // Company
  companyName: string;
  companyAddress?: string;
  companyPhone?: string;
  companyEmail?: string;
  logoBase64?: string;
  footerNote?: string;
  headerColor?: string;

  // Invoice meta
  invoiceTitle: string;
  invoiceNo: string;
  invoiceDate: string;
  dueDate?: string;
  paymentStatus?: string;
  creditStatus?: string;

  // Parties
  customerName: string;
  customerPhone?: string;
  customerAddress?: string;
  cashierName?: string;

  // Lines
  lineItems: PrintLineItem[];
  payments?: PaymentEntry[];

  // Totals
  subtotal: string;
  discount: string;
  netAmount: string;
  paid: string;
  balanceDue: string;

  // Extra
  remark?: string;
  customerNotice?: string;
}

// ─── Print options passed to hooks / components ──────────────────────────────

export interface PrintOptions {
  paperSize: PaperSize;
  design: PrintDesign;
  showLogo?: boolean;
  showSerial?: boolean;
  showPaymentHistory?: boolean;
  showSignatures?: boolean;
  showQrCode?: boolean;
  sign1Label?: string;
  sign2Label?: string;
  /** Override rows-per-page for unusual content density */
  rowsOverride?: number;
}

export const DEFAULT_PRINT_OPTIONS: Required<PrintOptions> = {
  paperSize: 'A4',
  design: 'STANDARD',
  showLogo: true,
  showSerial: true,
  showPaymentHistory: true,
  showSignatures: false,
  showQrCode: false,
  sign1Label: 'Prepared By',
  sign2Label: 'Received By',
  rowsOverride: 0,
};

// ─── Page pagination result ──────────────────────────────────────────────────

export interface InvoicePage {
  pageNumber: number;
  totalPages: number;
  items: PrintLineItem[];
  isFirst: boolean;
  isLast: boolean;
}

// ─── Print queue ─────────────────────────────────────────────────────────────

export type PrintJobStatus = 'pending' | 'generating' | 'ready' | 'printing' | 'done' | 'error';

export interface PrintJob {
  id: string;
  documentType: DocumentType;
  documentId: number;
  options: PrintOptions;
  status: PrintJobStatus;
  errorMessage?: string;
  pdfUrl?: string;
  createdAt: number;
}

// ─── API request / response ──────────────────────────────────────────────────

export interface PrintApiRequest {
  documentType: DocumentType;
  documentId: number;
  paperSize: PaperSize;
  showLogo: boolean;
  showSerial: boolean;
  showPaymentHistory: boolean;
  showSignatures: boolean;
  showQrCode: boolean;
  sign1Label: string;
  sign2Label: string;
}
