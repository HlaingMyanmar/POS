
import { useEffect, useRef } from 'react';
import { subscribeTopic } from '../services/wsClient';

/**
 * Subscribe to a STOMP topic via the shared WebSocket connection.
 * One physical connection serves all hooks/pages.
 */
export const useWebsocket = (topic: string, onMessage: (message: string) => void) => {
  const messageHandlerRef = useRef(onMessage);

  useEffect(() => {
    messageHandlerRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    return subscribeTopic(topic, (body) => {
      if (messageHandlerRef.current) {
        messageHandlerRef.current(body);
      }
    });
  }, [topic]);
};
