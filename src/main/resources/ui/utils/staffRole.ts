export const STAFF_JOB_ROLES = [
  { value: 'Technician', label: 'Technician — ပြုပြင်သူ' },
  { value: 'Helper', label: 'Helper — အကူပြုပြင်သူ' },
  { value: 'Cashier', label: 'Cashier — ငွေကိုင်' },
  { value: 'Receptionist', label: 'Receptionist — လက်ခံကောင်တာ' },
  { value: 'Manager', label: 'Manager — မန်နေဂျာ' },
  { value: 'Purchaser', label: 'Purchaser — ဝယ်ယူရေး' },
] as const;

export function isRepairTechnicianRole(role?: string | null): boolean {
  const value = String(role || '').toLowerCase();
  return value.includes('technician')
    || value.includes('tech')
    || value.includes('ပြုပြင်')
    || value.includes('နည်းပညာ');
}

export function isTechnicalUserRole(roleName?: string | null): boolean {
  const value = String(roleName || '').toUpperCase().replace(/^ROLE_/, '');
  return value.includes('TECH');
}
