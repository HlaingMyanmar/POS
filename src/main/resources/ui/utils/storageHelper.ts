
/**
 * sessionStorage is unique per browser tab. 
 * Storing the Refresh Token here allows the user to have different accounts 
 * logged into different tabs simultaneously without cross-tab session bleeding.
 */

export const saveToSession = (name: string, value: string) => {
  sessionStorage.setItem(name, value);
};

export const getFromSession = (name: string): string | null => {
  return sessionStorage.getItem(name);
};

export const removeFromSession = (name: string) => {
  sessionStorage.removeItem(name);
};
