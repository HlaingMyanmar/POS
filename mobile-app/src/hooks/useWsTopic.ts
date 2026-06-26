import { useEffect } from 'react';
import { subscribeTopic } from '../services/wsClient';

/**
 * Subscribe to a STOMP topic while the component is mounted.
 * Calls `onMessage` on every incoming message.
 */
export function useWsTopic(topic: string, onMessage: () => void) {
  useEffect(() => {
    const unsub = subscribeTopic(topic, onMessage);
    return unsub;
  }, [topic]);
}
