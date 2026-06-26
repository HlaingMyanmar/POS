
import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL, getAccessToken } from '../services/api';

/**
 * Custom hook to manage STOMP over WebSocket connection.
 * Includes Authorization headers from JS Memory for secure communication.
 */
export const useWebsocket = (topic: string, onMessage: (message: string) => void) => {
  const messageHandlerRef = useRef(onMessage);

  const normalizeSockJsUrl = (url: string) => {
    if (url.startsWith('ws://')) return `http://${url.slice(5)}`;
    if (url.startsWith('wss://')) return `https://${url.slice(6)}`;
    return url;
  };

  useEffect(() => {
    messageHandlerRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    const SockJSConstructor = (SockJS as any).default || SockJS;
    const sockJsUrl = normalizeSockJsUrl(WS_URL);

    const client = new Client({
      webSocketFactory: () => {
        return new SockJSConstructor(sockJsUrl);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg) => {
        if (msg.includes('CONNECTED') || msg.includes('ERROR') || msg.includes('STOMP')) {
          console.debug(`[WS-STOMP] ${msg}`);
        }
      },
    });

    client.beforeConnect = () => {
      const token = getAccessToken();
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    };

    client.onConnect = (frame) => {
      console.info(`[WS-READY] Connected successfully to ${topic}`);
      client.subscribe(topic, (message) => {
        if (messageHandlerRef.current) {
          messageHandlerRef.current(message.body);
        }
      });
    };

    client.onStompError = (frame) => {
      console.error('[WS-STOMP-ERROR] Protocol error:', frame.headers['message']);
    };

    client.onWebSocketError = (event) => {
      console.error(`[WS-NET-ERROR] WebSocket connection failed for ${topic} (${sockJsUrl}).`);
    };

    try {
      client.activate();
    } catch (err) {
      console.error("[WS-FATAL] Client activation failed:", err);
    }

    return () => {
      if (client.active) {
        client.deactivate();
      }
    };
  }, [topic]); 
};
