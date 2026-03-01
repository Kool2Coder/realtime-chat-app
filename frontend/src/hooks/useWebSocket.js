import { useState, useEffect, useCallback, useRef } from 'react';
import { createStompClient } from '../services/websocket';

export const useWebSocket = (token, onConnect, onError) => {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!token) return;

    const client = createStompClient(
      token,
      (frame) => {
        setConnected(true);
        onConnect?.(client);
      },
      (error) => {
        setConnected(false);
        onError?.(error);
      }
    );

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [token]);

  const send = useCallback((destination, body) => {
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      });
    }
  }, []);

  const subscribe = useCallback((destination, callback) => {
    if (!clientRef.current?.connected) return null;
    return clientRef.current.subscribe(destination, (message) => {
      const body = JSON.parse(message.body);
      callback(body);
    });
  }, []);

  return { client: clientRef.current, connected, send, subscribe };
};
