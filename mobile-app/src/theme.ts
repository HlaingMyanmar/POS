export const C = {
  primary:      '#4F46E5',
  primaryDark:  '#4338CA',
  primaryLight: '#EEF2FF',
  bg:           '#F8FAFC',
  card:         '#FFFFFF',
  text:         '#1E293B',
  textMuted:    '#64748B',
  border:       '#E2E8F0',
  success:      '#059669',
  successBg:    '#ECFDF5',
  warning:      '#D97706',
  warningBg:    '#FFFBEB',
  danger:       '#DC2626',
  dangerBg:     '#FFF1F2',
  violet:       '#7C3AED',
  violetBg:     '#F5F3FF',
} as const;

export const S = {
  card: {
    backgroundColor: C.card,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: C.border,
    padding: 16,
  },
  row: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
  },
  label: {
    fontSize: 11,
    fontWeight: '700' as const,
    color: C.textMuted,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.5,
    marginBottom: 4,
  },
  value: {
    fontSize: 15,
    fontWeight: '600' as const,
    color: C.text,
  },
};
