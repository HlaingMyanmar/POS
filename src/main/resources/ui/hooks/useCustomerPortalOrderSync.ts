import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../services/api';
import { getCustomerSession } from '../services/customerPortalApi';

function sockJsUrl(url: string) {
  if (url.startsWith('ws://')) return `http://${url.slice(5)}`;
  if (url.startsWith('wss://')) return `https://${url.slice(6)}`;
  return url;
}

/** Customer-only authenticated order sync with visible-tab polling fallback. */
export function useCustomerPortalOrderSync(
  enabled: boolean,
  refresh: () => void | Promise<void>,
  pollMs = 15_000,
) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  const [connected, setConnected] = useState(false);
  const run = useCallback(() => { void refreshRef.current(); }, []);

  useEffect(() => {
    if (!enabled) {
      setConnected(false);
      return;
    }
    const token = getCustomerSession()?.accessToken;
    if (!token) return;
    const SockJSConstructor = (SockJS as any).default || SockJS;
    const client = new Client({
      webSocketFactory: () => new SockJSConstructor(sockJsUrl(WS_URL)),
      reconnectDelay: 5_000,
      heartbeatIncoming: 4_000,
      heartbeatOutgoing: 4_000,
      beforeConnect: () => {
        const latest = getCustomerSession()?.accessToken;
        client.connectHeaders = latest ? { Authorization: `Bearer ${latest}` } : {};
      },
      onConnect: () => {
        setConnected(true);
        client.subscribe('/user/topic/customer-orders', () => run());
        run();
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();
    return () => {
      setConnected(false);
      void client.deactivate();
    };
  }, [enabled, run]);

  useEffect(() => {
    if (!enabled) return;
    const visible = () => {
      if (document.visibilityState === 'visible') run();
    };
    document.addEventListener('visibilitychange', visible);
    return () => document.removeEventListener('visibilitychange', visible);
  }, [enabled, run]);

  useEffect(() => {
    if (!enabled || connected) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') run();
    }, pollMs);
    return () => window.clearInterval(timer);
  }, [enabled, connected, pollMs, run]);
}
