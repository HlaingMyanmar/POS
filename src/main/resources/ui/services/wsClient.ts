import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL, getAccessToken } from './api';

type MsgCallback = (body: string) => void;

interface SubEntry {
  callback: MsgCallback;
  sub: StompSubscription | null;
}

let client: Client | null = null;
const subs = new Map<string, SubEntry[]>();

function normalizeSockJsUrl(url: string) {
  if (url.startsWith('ws://')) return `http://${url.slice(5)}`;
  if (url.startsWith('wss://')) return `https://${url.slice(6)}`;
  return url;
}

function clearSubHandles() {
  subs.forEach(entries => entries.forEach(entry => { entry.sub = null; }));
}

function resubscribeAll() {
  if (!client?.connected) return;
  subs.forEach((entries, topic) => {
    entries.forEach(entry => {
      if (!entry.sub) {
        entry.sub = client!.subscribe(topic, (message) => entry.callback(message.body));
      }
    });
  });
}

export function isWsConnected() {
  return !!client?.connected;
}

export function ensureWsConnected() {
  if (client?.active) return;

  const SockJSConstructor = (SockJS as any).default || SockJS;
  const sockJsUrl = normalizeSockJsUrl(WS_URL);

  client = new Client({
    webSocketFactory: () => new SockJSConstructor(sockJsUrl),
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    debug: (msg) => {
      if (msg.includes('CONNECTED') || msg.includes('ERROR') || msg.includes('STOMP')) {
        console.debug(`[WS-STOMP] ${msg}`);
      }
    },
    beforeConnect: () => {
      const token = getAccessToken();
      if (client) {
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      }
    },
    onConnect: () => {
      console.info('[WS-READY] Shared connection established');
      clearSubHandles();
      resubscribeAll();
      window.dispatchEvent(new Event('ws-connected'));
    },
    onDisconnect: () => clearSubHandles(),
    onWebSocketClose: () => {
      clearSubHandles();
      window.dispatchEvent(new Event('ws-disconnected'));
    },
    onStompError: (frame) => {
      console.error('[WS-STOMP-ERROR]', frame.headers['message'] || frame.body);
    },
    onWebSocketError: () => {
      console.error(`[WS-NET-ERROR] WebSocket failed (${sockJsUrl})`);
    },
  });

  client.activate();
}

export function disconnectWs() {
  client?.deactivate();
  client = null;
  subs.forEach(entries => entries.forEach(entry => {
    entry.sub?.unsubscribe();
    entry.sub = null;
  }));
  subs.clear();
}

/** Reconnect so updated auth headers apply after token refresh. */
export function reconnectWs() {
  if (!client?.active) {
    ensureWsConnected();
    return;
  }
  void client.deactivate().then(() => {
    clearSubHandles();
    client?.activate();
  });
}

export function subscribeTopic(topic: string, callback: MsgCallback): () => void {
  ensureWsConnected();

  const entry: SubEntry = { callback, sub: null };
  const existing = subs.get(topic) ?? [];
  existing.push(entry);
  subs.set(topic, existing);

  if (client?.connected) {
    entry.sub = client.subscribe(topic, (message) => callback(message.body));
  }

  return () => {
    entry.sub?.unsubscribe();
    entry.sub = null;
    const remaining = (subs.get(topic) ?? []).filter(e => e !== entry);
    if (remaining.length === 0) subs.delete(topic);
    else subs.set(topic, remaining);
  };
}


