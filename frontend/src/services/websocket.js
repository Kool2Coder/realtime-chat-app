import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const getWsBase = () => {
  if (process.env.REACT_APP_WS_URL) return process.env.REACT_APP_WS_URL;
  if (typeof window !== 'undefined') return `${window.location.origin}/api`;
  return 'http://localhost:8080/api';
};

const WS_BASE = getWsBase();

export const createStompClient = (token, onConnect, onError) => {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${getWsBase()}/ws`),
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: onConnect,
    onStompError: onError,
  });
  return client;
};

export const subscribeToTopic = (client, destination, callback) => {
  return client.subscribe(destination, (message) => {
    const body = JSON.parse(message.body);
    callback(body);
  });
};

export const sendToDestination = (client, destination, body) => {
  client.publish({
    destination,
    body: JSON.stringify(body),
  });
};
