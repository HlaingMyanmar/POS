
/**
 * Using sessionStorage instead of Cookies to support multi-tab independent user sessions.
 * Each tab will maintain its own token and user context.
 */

export const setCookie = (name: string, value: string, _days?: number) => {
  sessionStorage.setItem(name, value);
};

export const getCookie = (name: string): string | null => {
  return sessionStorage.getItem(name);
};

export const eraseCookie = (name: string) => {
  sessionStorage.removeItem(name);
};
