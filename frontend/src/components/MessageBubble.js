import React from 'react';
import './MessageBubble.css';

const MessageBubble = ({ message, isOwn }) => {
  const { content, senderUsername, timestamp, isTyping } = message;
  const time = timestamp ? new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

  if (isTyping) {
    return (
      <div className={`message-bubble typing ${isOwn ? 'own' : ''}`}>
        <span className="typing-indicator">{senderUsername} is typing...</span>
      </div>
    );
  }

  return (
    <div className={`message-bubble ${isOwn ? 'own' : ''}`}>
      {!isOwn && <span className="sender-name">{senderUsername}</span>}
      <div className="message-content">{content}</div>
      <span className="message-time">{time}</span>
    </div>
  );
};

export default MessageBubble;
