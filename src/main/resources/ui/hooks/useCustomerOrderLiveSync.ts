import { useCallback, useEffect, useRef, useState } from 'react';
import { useWebsocket } from './useWebsocket';
import { isWsConnected } from '../services/wsClient';

export type CustomerOrderLiveEvent = {
  type?: string;
  orderId?: number;
};

export function parseCustomerOrderEvent(body: string): CustomerOrderLiveEvent {
  const trimmed = (body || '').trim();
  if (!trimmed) return {};
  try {
    const parsed = JSON.parse(trimmed) as { type?: string; orderId?: number };
    if (parsed && typeof parsed === 'object') {
      const id = Number(parsed.orderId);
      return {
        type: parsed.type || undefined,
        orderId: Number.isFinite(id) && id > 0 ? id : undefined,
      };
    }
  } catch {
    /* plain string payload from older servers */
  }
  return { type: trimmed };
}

/**
 * Staff web: authenticated STOMP /topic/customer-order.
 * Re-fetches via the callback (API is source of truth).
 * Syncs on reconnect and when the browser tab becomes visible.
 * Polls only while this page is mounted, the tab is visible, and the socket is down.
 */
export function useCustomerOrderLiveSync(
  refresh: (orderId?: number) => void | Promise<void>,
  pollMs = 15_000
) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  const [socketUp, setSocketUp] = useState(isWsConnected);

  const run = useCallback((orderId?: number) => {
    void refreshRef.current(orderId);
  }, []);

  useWebsocket('/topic/customer-order', (body) => {
    run(parseCustomerOrderEvent(body).orderId);
  });

  useEffect(() => {
    const onConnected = () => {
      setSocketUp(true);
      run();
    };
    const onDisconnected = () => setSocketUp(false);
    const onVisibility = () => {
      if (document.visibilityState === 'visible') run();
    };
    window.addEventListener('ws-connected', onConnected);
    window.addEventListener('ws-disconnected', onDisconnected);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.removeEventListener('ws-connected', onConnected);
      window.removeEventListener('ws-disconnected', onDisconnected);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [run]);

  useEffect(() => {
    if (socketUp) return;
    const tick = () => {
      if (document.visibilityState !== 'visible') return;
      run();
    };
    const timer = window.setInterval(tick, pollMs);
    return () => window.clearInterval(timer);
  }, [socketUp, pollMs, run]);
}
