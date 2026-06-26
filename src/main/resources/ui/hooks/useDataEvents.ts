import { useCallback, useEffect, useRef } from 'react';
import { useWebsocket } from './useWebsocket';

interface DataEvent {
  entity: string;
  action: string;
  resourceId?: string;
}

/**
 * Subscribes to /topic/data-events and calls [callback] (debounced) whenever
 * the backend broadcasts an event whose entity name contains any of the
 * given [keywords] (case-insensitive).
 *
 * The callback is stored in a ref so it always closes over the latest state
 * without being listed as a dependency — the same pattern as useWebsocket.
 *
 * Usage:
 *   useDataEvents(['Sale'], fetchSales);
 *   useDataEvents(['Product', 'Stock', 'Sale'], fetchProducts, 800);
 */
export const useDataEvents = (
  keywords: string[],
  callback: () => void,
  debounceMs = 600,
): void => {
  // Always call the latest version of the callback
  const callbackRef = useRef(callback);
  useEffect(() => { callbackRef.current = callback; });

  const timerRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const keyString = keywords.join('\x00'); // stable join for useCallback dep

  const handler = useCallback(
    (body: string) => {
      try {
        const event: DataEvent = JSON.parse(body);
        const keys    = keyString.split('\x00');
        const matches = keys.some(kw =>
          event.entity.toLowerCase().includes(kw.toLowerCase()),
        );
        if (!matches) return;

        if (timerRef.current) clearTimeout(timerRef.current);
        timerRef.current = setTimeout(() => callbackRef.current(), debounceMs);
      } catch {
        // ignore malformed frames
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [keyString, debounceMs],
  );

  useWebsocket('/topic/data-events', handler);
};
