import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import { createStompClient } from '../services/websocket';
import MessageBubble from './MessageBubble';
import UserList from './UserList';
import './ChatRoom.css';

const ChatRoom = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [publicMessages, setPublicMessages] = useState([]);
  const [roomMessages, setRoomMessages] = useState({});
  const [privateHistory, setPrivateHistory] = useState({});
  const [typingUsers, setTypingUsers] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [chatMode, setChatMode] = useState('public');
  const [roomId, setRoomId] = useState('');
  const [selectedUser, setSelectedUser] = useState({ id: null, username: '' });
  const [onlineUsers, setOnlineUsers] = useState({});
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionsRef = useRef([]);
  const messagesEndRef = useRef(null);
  const typingTimeoutRef = useRef(null);
  const lastTypingSentRef = useRef(0);
  const selectedUserRef = useRef(selectedUser);
  selectedUserRef.current = selectedUser;

  const scrollToBottom = () => messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });

  const loadPublicHistory = useCallback(async () => {
    try {
      const response = await api.get('/chat/history/public');
      setPublicMessages(response.data || []);
    } catch (e) {
      console.error('Failed to load public history', e);
    }
  }, []);

  const loadRoomHistory = useCallback(async (rid) => {
    if (!rid) return;
    try {
      const response = await api.get(`/chat/history/room?roomId=${rid}`);
      setRoomMessages((prev) => ({ ...prev, [rid]: response.data || [] }));
    } catch (e) {
      console.error('Failed to load room history', e);
    }
  }, []);

  const loadPrivateHistory = useCallback(async (otherUserId) => {
    if (!otherUserId || !user?.userId) return;
    try {
      const response = await api.get(
        `/chat/history/private?userId=${user.userId}&otherUserId=${otherUserId}`
      );
      const data = response.data || [];
      setPrivateHistory((prev) => {
        const existing = prev[otherUserId];
        const merged = data.length > 0 ? data : (existing || []);
        return { ...prev, [otherUserId]: merged };
      });
    } catch (e) {
      console.error('Failed to load private history', e);
    }
  }, [user?.userId]);

  const loadOnlineUsers = useCallback(async () => {
    try {
      const response = await api.get('/chat/users/online');
      setOnlineUsers(response.data || {});
    } catch (e) {
      console.error('Failed to load online users', e);
    }
  }, []);

  useEffect(() => {
    if (!user?.token) return;

    const client = createStompClient(
      user.token,
      (frame) => {
        setConnected(true);
        loadPublicHistory();

        const subs = [];

        subs.push(
          client.subscribe('/topic/online.users', (msg) => {
            const body = JSON.parse(msg.body);
            const users = Object.entries(body || {}).reduce(
              (acc, [id, name]) => ({ ...acc, [Number(id)]: name }),
              {}
            );
            setOnlineUsers(users);
          })
        );
        subs.push(
          client.subscribe('/topic/public', (msg) => {
            const body = JSON.parse(msg.body);
            setPublicMessages((prev) => [...prev, body]);
          })
        );
        subs.push(
          client.subscribe('/topic/public.typing', (msg) => {
            const body = JSON.parse(msg.body);
            setTypingUsers((prev) =>
              body.isTyping
                ? [...prev.filter((u) => u.id !== body.senderId), { id: body.senderId, username: body.senderUsername }]
                : prev.filter((u) => u.id !== body.senderId)
            );
          })
        );
        subs.push(
          client.subscribe('/user/queue/private', (msg) => {
            const body = JSON.parse(msg.body);
            setPrivateHistory((prev) => ({
              ...prev,
              [body.senderId]: [...(prev[body.senderId] || []), body],
            }));
          })
        );
        subs.push(
          client.subscribe('/user/queue/private.typing', (msg) => {
            const body = JSON.parse(msg.body);
            const isCurrentPrivatePartner = selectedUserRef.current?.id === body.senderId;
            setTypingUsers((prev) => {
              if (body.isTyping && !isCurrentPrivatePartner) return prev;
              return body.isTyping
                ? [...prev.filter((u) => u.id !== body.senderId), { id: body.senderId, username: body.senderUsername }]
                : prev.filter((u) => u.id !== body.senderId);
            });
          })
        );

        subscriptionsRef.current = subs;

        setTimeout(() => {
          client.publish({
            destination: '/app/presence.join',
            body: JSON.stringify({}),
          });
        }, 150);

        setTimeout(() => loadOnlineUsers(), 800);
      },
      (err) => {
        setConnected(false);
        console.error('STOMP error', err);
      }
    );

    clientRef.current = client;
    client.activate();

    return () => {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
      subscriptionsRef.current.forEach((s) => s?.unsubscribe?.());
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [user?.token]);

  useEffect(() => {
    if (!connected) return;
    const interval = setInterval(loadOnlineUsers, 5000);
    return () => clearInterval(interval);
  }, [connected, loadOnlineUsers]);

  useEffect(() => {
    if (!clientRef.current?.connected || !roomId) return;

    const client = clientRef.current;
    const roomSub = client.subscribe(`/topic/room.${roomId}`, (msg) => {
      const body = JSON.parse(msg.body);
      setRoomMessages((prev) => ({
        ...prev,
        [roomId]: [...(prev[roomId] || []), body],
      }));
    });
    const typingSub = client.subscribe(`/topic/room.${roomId}.typing`, (msg) => {
      const body = JSON.parse(msg.body);
      setTypingUsers((prev) =>
        body.isTyping
          ? [...prev.filter((u) => u.id !== body.senderId), { id: body.senderId, username: body.senderUsername }]
          : prev.filter((u) => u.id !== body.senderId)
      );
    });

    subscriptionsRef.current.push(roomSub, typingSub);

    return () => {
      roomSub.unsubscribe();
      typingSub.unsubscribe();
      subscriptionsRef.current = subscriptionsRef.current.filter((s) => s !== roomSub && s !== typingSub);
    };
  }, [connected, roomId]);

  useEffect(() => {
    if (chatMode === 'public') loadPublicHistory();
    if (chatMode === 'room' && roomId) loadRoomHistory(roomId);
    if (chatMode === 'private' && selectedUser.id) loadPrivateHistory(selectedUser.id);
  }, [chatMode, roomId, selectedUser.id]);


  const sendMessage = () => {
    if (!inputMessage.trim() || !clientRef.current?.connected) return;

    sendTyping(false);
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
      typingTimeoutRef.current = null;
    }

    const content = inputMessage.trim();
    const payload = { content };

    if (chatMode === 'public') {
      clientRef.current.publish({
        destination: '/app/chat.public',
        body: JSON.stringify(payload),
      });
    } else if (chatMode === 'room' && roomId) {
      clientRef.current.publish({
        destination: `/app/chat.room`,
        body: JSON.stringify({ ...payload, roomId }),
      });
    } else if (chatMode === 'private' && selectedUser.id) {
      clientRef.current.publish({
        destination: '/app/chat.private',
        body: JSON.stringify({ ...payload, receiverId: selectedUser.id }),
      });
      const optimisticMsg = {
        id: null,
        senderId: user.userId,
        senderUsername: user.username,
        receiverId: selectedUser.id,
        receiverUsername: selectedUser.username,
        content,
        timestamp: new Date().toISOString(),
        messageType: 'PRIVATE',
      };
      setPrivateHistory((prev) => ({
        ...prev,
        [selectedUser.id]: [...(prev[selectedUser.id] || []), optimisticMsg],
      }));
    }

    setInputMessage('');
  };

  const sendTyping = (isTyping = true) => {
    if (!clientRef.current?.connected) return;
    const now = Date.now();
    if (isTyping && now - lastTypingSentRef.current < 800) return;
    lastTypingSentRef.current = now;

    if (chatMode === 'public') {
      clientRef.current.publish({
        destination: '/app/typing.public',
        body: JSON.stringify({ content: '', isTyping }),
      });
    } else if (chatMode === 'room' && roomId) {
      clientRef.current.publish({
        destination: '/app/typing.room',
        body: JSON.stringify({ content: '', roomId, isTyping }),
      });
    } else if (chatMode === 'private' && selectedUser.id) {
      clientRef.current.publish({
        destination: '/app/typing.private',
        body: JSON.stringify({ content: '', receiverId: selectedUser.id, isTyping }),
      });
    }
  };

  const scheduleStopTyping = () => {
    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      sendTyping(false);
      typingTimeoutRef.current = null;
    }, 1500);
  };

  const displayMessages = () => {
    if (chatMode === 'public') return publicMessages;
    if (chatMode === 'room' && roomId) return roomMessages[roomId] || [];
    if (chatMode === 'private' && selectedUser.id) {
      const msgs = privateHistory[selectedUser.id] || [];
      return msgs.sort((a, b) => new Date(a.timestamp || 0) - new Date(b.timestamp || 0));
    }
    return [];
  };

  const recentPrivateChats = () => {
    const byId = {};
    Object.keys(privateHistory).forEach((id) => {
      const msgs = privateHistory[id];
      if (msgs && msgs.length > 0) byId[id] = true;
    });
    if (selectedUser.id && !byId[selectedUser.id]) {
      byId[selectedUser.id] = true;
    }
    return Object.keys(byId).map((id) => {
      const numId = Number(id);
      const msgs = privateHistory[numId];
      const msgWithOther = msgs?.find((m) => m.senderId === numId || m.receiverId === numId);
      const otherUsername =
        msgWithOther?.senderId === numId
          ? msgWithOther?.senderUsername
          : msgWithOther?.receiverUsername;
      return {
        id: numId,
        username: otherUsername || (selectedUser.id === numId ? selectedUser.username : `User ${numId}`),
      };
    });
  };

  const handleSelectUser = (id, username) => {
    setSelectedUser({ id, username });
    setChatMode('private');
    loadPrivateHistory(id);
  };

  const handleJoinRoom = () => {
    if (roomId.trim()) {
      setChatMode('room');
      loadRoomHistory(roomId.trim());
    }
  };

  const currentMessages = displayMessages();

  return (
    <div className="chat-room">
      <header className="chat-header">
        <h1>Chat App {user?.username && <span className="user-badge">({user.username})</span>}</h1>
        <div className="header-actions">
          <span className={`status ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? 'Connected' : 'Disconnected'}
          </span>
          <button
            onClick={() => {
              logout();
              navigate('/login');
            }}
            className="logout-btn"
          >
            Logout
          </button>
        </div>
      </header>

      <div className="chat-layout">
        <aside className="sidebar">
          <div className="sidebar-section">
            <h3>You: {user?.username || 'Unknown'}</h3>
          </div>
          <UserList
            users={onlineUsers}
            currentUser={user}
            onSelectUser={handleSelectUser}
            selectedUserId={selectedUser.id}
          />
          {recentPrivateChats().length > 0 && (
            <div className="recent-chats">
              <h3>Recent Chats</h3>
              <ul>
                {recentPrivateChats().map(({ id, username }) => (
                  <li
                    key={id}
                    className={selectedUser.id === id ? 'selected' : ''}
                    onClick={() => handleSelectUser(id, username)}
                  >
                    {username}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="room-join">
            <h3>Join Room</h3>
            <input
              type="text"
              placeholder="Room ID"
              value={roomId}
              onChange={(e) => setRoomId(e.target.value)}
            />
            <button onClick={handleJoinRoom}>Join</button>
          </div>
        </aside>

        <main className="chat-main">
          <div className="chat-mode">
            <button
              className={chatMode === 'public' ? 'active' : ''}
              onClick={() => setChatMode('public')}
            >
              Public
            </button>
            <button
              className={chatMode === 'room' ? 'active' : ''}
              onClick={() => roomId && setChatMode('room')}
            >
              Room: {roomId || '-'}
            </button>
            <button
              className={chatMode === 'private' ? 'active' : ''}
              onClick={() => selectedUser.id && setChatMode('private')}
              title={selectedUser.id ? `Private: ${selectedUser.username}` : 'Select a user for private chat'}
            >
              Private: {selectedUser.username || 'Select user'}
            </button>
          </div>

          <div className="messages-container">
            {chatMode === 'private' && !selectedUser.id && (
              <div className="chat-placeholder">
                Select a user from <strong>Online Users</strong> or <strong>Recent Chats</strong> to start a private conversation.
              </div>
            )}
            {currentMessages.map((msg) => (
              <MessageBubble
                key={msg.id || `${msg.timestamp}-${msg.senderId}`}
                message={msg}
                isOwn={msg.senderId === user?.userId}
              />
            ))}
            {(chatMode === 'private' && selectedUser.id
              ? typingUsers.filter((u) => u.id === selectedUser.id)
              : typingUsers
            ).map((u) => (
              <MessageBubble
                key={`typing-${u.id}`}
                message={{ isTyping: true, senderUsername: u.username }}
                isOwn={false}
              />
            ))}
            <div ref={messagesEndRef} />
          </div>

          <div className="input-area">
            <input
              type="text"
              placeholder={
                chatMode === 'private' && !selectedUser.id
                  ? 'Select a user to message...'
                  : 'Type a message...'
              }
              value={inputMessage}
              onChange={(e) => {
                setInputMessage(e.target.value);
                sendTyping(true);
                scheduleStopTyping();
              }}
              onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
              onBlur={() => {
                sendTyping(false);
                if (typingTimeoutRef.current) {
                  clearTimeout(typingTimeoutRef.current);
                  typingTimeoutRef.current = null;
                }
              }}
              disabled={chatMode === 'private' && !selectedUser.id}
            />
            <button
              onClick={sendMessage}
              disabled={!connected || (chatMode === 'private' && !selectedUser.id)}
            >
              Send
            </button>
          </div>
        </main>
      </div>
    </div>
  );
};

export default ChatRoom;
