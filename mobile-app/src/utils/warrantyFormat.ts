/** Converts warranty months to a readable duration string. */
export const fmtWarrantyDuration = (months?: number | null): string => {
  const m = Number(months) || 0;
  if (m <= 0) return '';
  if (m % 12 === 0) { const y = m / 12; return `${y} Year${y > 1 ? 's' : ''}`; }
  return `${m} Month${m > 1 ? 's' : ''}`;
};

/** Full warranty label: duration + optional expiry date. */
export const fmtWarrantyLabel = (months?: number | null, expiryDate?: string | null): string => {
  const duration = fmtWarrantyDuration(months);
  if (!duration) return '';
  if (expiryDate) {
    const d = new Date(expiryDate);
    const expStr = isNaN(d.getTime()) ? expiryDate : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    return `${duration} · Exp: ${expStr}`;
  }
  return duration;
};

/** Serial-level warranty: duration only, Myanmar format. e.g. "2 နှစ်", "6 လ" */
export const fmtSerialWarranty = (months?: number | null): string => {
  const m = Number(months) || 0;
  if (m <= 0) return '';
  if (m % 12 === 0) return `${m / 12} နှစ်`;
  return `${m} လ`;
};

/** Display warranty from product fields (terms takes priority over months). */
export const fmtProductWarranty = (warrantyTerms?: string | null, warrantyMonths?: number | null): string => {
  const terms = (warrantyTerms || '').trim();
  if (terms) return terms;
  return fmtWarrantyDuration(warrantyMonths);
};
