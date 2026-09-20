export const fmtWarrantyDuration = (months?: number | null, startDate?: string | null, endDate?: string | null): string => {
  const m = Number(months) || 0;
  if (m > 0) {
    if (m % 12 === 0) return `${m / 12} နှစ်`;
    return `${m} လ`;
  }
  const start = String(startDate || '').slice(0, 10);
  const end = String(endDate || '').slice(0, 10);
  if (!start || !end) return '';
  const startTime = new Date(`${start}T00:00:00`).getTime();
  const endTime = new Date(`${end}T00:00:00`).getTime();
  if (!Number.isFinite(startTime) || !Number.isFinite(endTime)) return '';
  const days = Math.round((endTime - startTime) / (24 * 60 * 60 * 1000));
  return days > 0 ? `${days} ရက်` : '';
};

export const fmtProductWarranty = (warrantyTerms?: string | null, warrantyMonths?: number | null, startDate?: string | null, endDate?: string | null): string => {
  const terms = String(warrantyTerms || '').trim();
  if (terms) return terms;
  return fmtWarrantyDuration(warrantyMonths, startDate, endDate);
};

export const fmtWarrantyLabel = (months?: number | null, expiryDate?: string | null, startDate?: string | null): string => {
  const duration = fmtWarrantyDuration(months, startDate, expiryDate);
  if (!duration) return '';
  if (!expiryDate) return duration;
  const d = new Date(expiryDate);
  const expStr = Number.isNaN(d.getTime())
    ? String(expiryDate)
    : d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  return `${duration} · Exp: ${expStr}`;
};
