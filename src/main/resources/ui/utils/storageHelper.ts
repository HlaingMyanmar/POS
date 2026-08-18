
/**
 * sessionStorage is unique per browser tab. 
 * Storing the Refresh Token here allows the user to have different accounts 
 * logged into different tabs simultaneously without cross-tab session bleeding.
 */

export const saveToSession = (name: string, value: string) => {
  try {
    window.sessionStorage.setItem(name, value);
  } catch (_error) {
    // Storage can be disabled by browser privacy settings. Authentication can
    // still continue in memory for the current page.
  }
};

export const getFromSession = (name: string): string | null => {
  try {
    return window.sessionStorage.getItem(name);
  } catch (_error) {
    return null;
  }
};

export const removeFromSession = (name: string) => {
  try {
    window.sessionStorage.removeItem(name);
  } catch (_error) {
    // Nothing to remove when browser storage is unavailable.
  }
};
