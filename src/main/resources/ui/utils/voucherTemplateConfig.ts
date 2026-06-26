// Customizable text labels and toggles for each voucher type.
// Stored as JSON in CompanySettings.voucherConfigJson.

export type VoucherSaleConfig = {
  title: string;
  // Column headers
  colItem: string;
  colSerial: string;
  colQty: string;
  colUnitPrice: string;
  colAmount: string;
  // Section labels
  secBillTo: string;
  secPreparedBy: string;
  secPaymentHistory: string;
  secRemark: string;
  // Signature lines
  sign1: string;
  sign2: string;
  // Visibility toggles
  showSerial: boolean;
  showPaymentHistory: boolean;
  showSignatures: boolean;
  // Style
  headerColor: string;
  // Customer notice (printed above footer)
  customerNotice: string;
};

export type VoucherPurchaseConfig = {
  title: string;
  // Column headers
  colItem: string;
  colQty: string;
  colUnitCost: string;
  colAmount: string;
  // Section labels
  secSupplier: string;
  secPreparedBy: string;
  secRemark: string;
  // Signature lines
  sign1: string;
  sign2: string;
  // Visibility toggles
  showSignatures: boolean;
  // Style
  headerColor: string;
};

export type VoucherServiceConfig = {
  title: string;
  // Section labels
  secDevice: string;
  secDiagnosis: string;
  secServices: string;
  secParts: string;
  secPayment: string;
  secRemark: string;
  // Signature lines
  sign1: string;
  sign2: string;
  // Visibility toggles
  showSignatures: boolean;
  // Style
  headerColor: string;
  // Customer notice (printed above footer)
  customerNotice: string;
};

export type VoucherTemplateConfig = {
  sale: VoucherSaleConfig;
  purchase: VoucherPurchaseConfig;
  service: VoucherServiceConfig;
};

export const DEFAULT_SALE_CONFIG: VoucherSaleConfig = {
  title: 'Sales Invoice',
  colItem: 'Item Description',
  colSerial: 'Serial / Mode',
  colQty: 'Qty',
  colUnitPrice: 'Unit Price',
  colAmount: 'Amount',
  secBillTo: 'Bill To',
  secPreparedBy: 'Prepared By',
  secPaymentHistory: 'Payment History',
  secRemark: 'Remark / Notes',
  sign1: 'Prepared By',
  sign2: 'Customer Signature',
  showSerial: true,
  showPaymentHistory: true,
  showSignatures: true,
  headerColor: '#0f172a',
  customerNotice: '',
};

export const DEFAULT_PURCHASE_CONFIG: VoucherPurchaseConfig = {
  title: 'Purchase Invoice',
  colItem: 'Item Description',
  colQty: 'Qty',
  colUnitCost: 'Unit Cost',
  colAmount: 'Amount',
  secSupplier: 'Supplier Info',
  secPreparedBy: 'Prepared By',
  secRemark: 'Remark / Notes',
  sign1: 'Prepared By',
  sign2: 'Supplier Signature',
  showSignatures: true,
  headerColor: '#0f172a',
};

export const DEFAULT_SERVICE_CONFIG: VoucherServiceConfig = {
  title: 'Service Voucher',
  secDevice: 'Device Info',
  secDiagnosis: 'Diagnosis / Notes',
  secServices: 'Services Performed',
  secParts: 'Parts Used',
  secPayment: 'Payment Summary',
  secRemark: 'Remark',
  sign1: 'Technician',
  sign2: 'Customer Signature',
  showSignatures: true,
  headerColor: '#0f172a',
  customerNotice: '',
};

export const DEFAULT_VOUCHER_CONFIG: VoucherTemplateConfig = {
  sale: DEFAULT_SALE_CONFIG,
  purchase: DEFAULT_PURCHASE_CONFIG,
  service: DEFAULT_SERVICE_CONFIG,
};

export const parseVoucherConfig = (json?: string | null): VoucherTemplateConfig => {
  if (!json) return DEFAULT_VOUCHER_CONFIG;
  try {
    const parsed = JSON.parse(json) as Partial<VoucherTemplateConfig>;
    return {
      sale: { ...DEFAULT_SALE_CONFIG, ...(parsed.sale ?? {}) },
      purchase: { ...DEFAULT_PURCHASE_CONFIG, ...(parsed.purchase ?? {}) },
      service: { ...DEFAULT_SERVICE_CONFIG, ...(parsed.service ?? {}) },
    };
  } catch {
    return DEFAULT_VOUCHER_CONFIG;
  }
};
