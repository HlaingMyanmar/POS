import { AppLanguage } from '../types';

export const LANGUAGE_STORAGE_KEY = 'sspd_language';
export const DEFAULT_LANGUAGE: AppLanguage = 'my';

const isAppLanguage = (value: string | null): value is AppLanguage => value === 'en' || value === 'my';

export const resolveInitialLanguage = (): AppLanguage => {
  if (typeof window === 'undefined') return DEFAULT_LANGUAGE;

  try {
    const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY);
    if (isAppLanguage(stored)) return stored;
  } catch (_error) {
    // Ignore blocked localStorage access and continue with browser language fallback.
  }

  const browserLanguage = window.navigator.language?.toLowerCase() || DEFAULT_LANGUAGE;
  return browserLanguage.startsWith('my') ? 'my' : DEFAULT_LANGUAGE;
};

export const applyDocumentLanguage = (language: AppLanguage) => {
  if (typeof document === 'undefined') return;

  document.documentElement.lang = language === 'my' ? 'my' : 'en';
  document.body.classList.remove('lang-en', 'lang-my');
  document.body.classList.add(language === 'my' ? 'lang-my' : 'lang-en');
};

export const saveLanguagePreference = (language: AppLanguage) => {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  } catch (_error) {
    // Ignore blocked localStorage access.
  }
};
