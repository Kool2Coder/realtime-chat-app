import React from 'react';
import './UserList.css';

const UserList = ({ users, currentUser, onSelectUser, selectedUserId }) => {
  const userList = Object.entries(users).filter(([id]) => Number(id) !== currentUser?.userId);

  return (
    <div className="user-list">
      <h3>Online Users</h3>
      <ul>
        {userList.map(([userId, username]) => (
          <li
            key={userId}
            className={selectedUserId === Number(userId) ? 'selected' : ''}
            onClick={() => onSelectUser(Number(userId), username)}
          >
            <span className="user-dot"></span>
            {username}
          </li>
        ))}
      </ul>
      {userList.length === 0 && <p className="no-users">No other users online</p>}
    </div>
  );
};

export default UserList;
