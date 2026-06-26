import { companySettingsService } from '../services/api';

export type CompanySettings = {
  id?: number;
  companyName: string;
  companyAddress?: string;
  companyPhone?: string;
  companyEmail?: string;
  invoiceTitle?: string;
  footerNote?: string;
  taglineMm?: string;
  logoBase64?: string;
  voucherConfigJson?: string;
  salePrefix?: string;
  saleDigits?: number;
  purchasePrefix?: string;
  purchaseDigits?: number;
  bookingPrefix?: string;
  bookingDigits?: number;
};

const DEFAULT_SETTINGS: CompanySettings = {
  companyName: 'SSPD IT Solution Center',
  companyAddress: 'No. 38/Kha, 56 Ward, Lhaw Kar Main Road, South Dagon, Yangon',
  companyPhone: '09-252425319',
  companyEmail: '',
  invoiceTitle: 'Sales Invoice',
  footerNote: 'Thank you for your business',
  taglineMm: 'ဝန်ဆောင်မှုဌာန',
  logoBase64: '',
  salePrefix: 'INV',
  saleDigits: 5,
  purchasePrefix: 'PUR',
  purchaseDigits: 5,
  bookingPrefix: 'BK',
  bookingDigits: 6,
};

let cached: CompanySettings | null = null;
let inflight: Promise<CompanySettings> | null = null;

export const buildCompanyContact = (s: CompanySettings) => {
  const parts: string[] = [];
  if (s.companyPhone) parts.push(`Phone: ${s.companyPhone}`);
  if (s.companyAddress) parts.push(`Address: ${s.companyAddress}`);
  if (s.companyEmail) parts.push(`Email: ${s.companyEmail}`);
  return parts.join(' | ');
};

export const getCompanySettings = async (force = false): Promise<CompanySettings> => {
  if (!force && cached) return cached;
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const res = await companySettingsService.getSettings();
      if (res.success && res.data) {
        cached = { ...DEFAULT_SETTINGS, ...res.data };
        return cached;
      }
    } catch {}
    cached = DEFAULT_SETTINGS;
    return cached;
  })().finally(() => { inflight = null; }) as Promise<CompanySettings>;
  return inflight;
};

export const setCompanySettingsCache = (s: CompanySettings) => {
  cached = { ...DEFAULT_SETTINGS, ...s };
  window.dispatchEvent(new CustomEvent('company-settings-updated', { detail: cached }));
};

export const clearCompanySettingsCache = () => {
  cached = null;
};

export const getCachedCompanySettings = (): CompanySettings => cached ?? DEFAULT_SETTINGS;
