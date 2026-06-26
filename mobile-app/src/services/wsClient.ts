import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { getToken } from '../api/client';

type MsgCallback = (msg: IMessage) => void;

interface SubEntry {
  callback: MsgCallback;
  sub: StompSubscription | null;
}

let _client: Client | null = null;
let _wsUrl: string = '';
const _subs = new Map<string, SubEntry[]>();

function resubscribeAll() {
  if (!_client?.connected) return;
  _subs.forEach((entries, topic) => {
    entries.forEach(entry => {
      if (!entry.sub) {
        entry.sub = _client!.subscribe(topic, entry.callback);
      }
    });
  });
}

function clearSubHandles() {
  _subs.forEach(entries => entries.forEach(e => { e.sub = null; }));
}

export function initWs(serverUrl: string) {
  const wsUrl = serverUrl.replace(/^https:\/\//, 'wss://').replace(/^http:\/\//, 'ws://') + '/ws-native';
  if (_client?.active && _wsUrl === wsUrl) return;

  _wsUrl = wsUrl;
  _client?.deactivate();
  clearSubHandles();

  _client = new Client({
    webSocketFactory: () => new WebSocket(wsUrl),
    connectHeaders: () => {
      const token = getToken();
      return token ? { Authorization: `Bearer ${token}` } : {};
    },
    reconnectDelay: 5000,
    onConnect: () => { resubscribeAll(); },
    onDisconnect: () => { clearSubHandles(); },
    onStompError: () => {},
  });

  _client.activate();
}

export function disconnectWs() {
  _client?.deactivate();
  _client = null;
  _subs.clear();
}

export function subscribeTopic(topic: string, callback: MsgCallback): () => void {
  const existing = _subs.get(topic) ?? [];
  const entry: SubEntry = { callback, sub: null };

  if (_client?.connected) {
    entry.sub = _client.subscribe(topic, callback);
  }

  existing.push(entry);
  _subs.set(topic, existing);

  return () => {
    entry.sub?.unsubscribe();
    entry.sub = null;
    const remaining = (_subs.get(topic) ?? []).filter(e => e !== entry);
    if (remaining.length === 0) {
      _subs.delete(topic);
    } else {
      _subs.set(topic, remaining);
    }
  };
}
