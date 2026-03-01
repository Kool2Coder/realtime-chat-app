# Real-Time Chat Application

A production-ready real-time chat application with Spring Boot backend and React frontend.

## Tech Stack

**Backend:** Java 17, Spring Boot 3, WebSocket (STOMP), JWT, Redis, H2/MySQL  
**Frontend:** React 18, SockJS, STOMP, Axios

## Features

- **Authentication:** JWT-based registration and login
- **Public Chat:** Global chat room
- **Private Messaging:** Direct messages between users
- **Room Chat:** Join rooms by ID
- **Online Users:** Real-time user presence
- **Typing Indicators:** See when others are typing
- **Message History:** Persisted messages in database

---

## Run Locally

### Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+
- Redis (optional for dev; required for Redis broker)

### Steps

1. **Start Redis** (required for default dev profile):

   ```bash
   docker run -d -p 6379:6379 redis:7-alpine
   ```

   To run **without Redis**, use the `local` profile (excludes Redis):

   ```bash
   cd chatapp/backend
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. **Backend** (with Redis):

   ```bash
   cd chatapp/backend
   mvn spring-boot:run
   ```

   Backend runs at `http://localhost:8080`. API base: `http://localhost:8080/api`.

3. **Frontend:**

   ```bash
   cd chatapp/frontend
   npm install
   npm start
   ```

   Frontend runs at `http://localhost:3000`. Proxy is configured to forward `/api` to backend.

4. **Test:**

   - Open `http://localhost:3000`
   - Register a new user
   - Open another browser/incognito and register another user
   - Chat in public, create a room, or send private messages

---

## Run with Docker

### Prerequisites

- Docker and Docker Compose

### Steps

```bash
cd chatapp
docker-compose up --build
```

- **Frontend:** http://localhost:3000  
- **Backend API:** http://localhost:8080/api  
- **Redis:** localhost:6379  
- **MySQL:** localhost:3306 (user: chatapp, password: chatapp)

---

## Configuration

### Backend (`application.yml`)

| Property | Description | Default |
|----------|-------------|---------|
| `spring.datasource.url` | DB URL | H2 in-memory |
| `spring.data.redis.host` | Redis host | localhost |
| `jwt.secret` | JWT signing key | (dev default) |
| `websocket.redis-broker.enabled` | Enable Redis pub/sub for scaling | true |


### Database (MySQL)

For production, set:

```yaml
spring:
  datasource:
    url: jdbc:mysql://host:3306/chatapp
    username: chatapp
    password: your_password
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

---

## Architecture Overview

### STOMP (Simple Text-Oriented Messaging Protocol)

- STOMP runs over WebSocket for real-time messaging
- Client connects to `/ws` (SockJS)
- Messages use destinations: `/app/*` for sending, `/topic/*` and `/queue/*` for receiving
- Subscriptions are managed by the broker

### Message Routing

| Client Sends To | Server Receives | Server Sends To |
|-----------------|-----------------|-----------------|
| `/app/chat.public` | `@MessageMapping("/chat.public")` | `/topic/public` |
| `/app/chat.private` | `@MessageMapping("/chat.private")` | `/user/{username}/queue/private` |
| `/app/chat.room` | `@MessageMapping("/chat.room")` | `/topic/room.{roomId}` |

### Redis Broker

- **SimpleBroker:** In-memory; single instance only
- **Redis Pub/Sub:** Messages published to Redis are broadcast to all app instances
- Enables horizontal scaling across multiple backend pods
- Set `websocket.redis-broker.enabled: true` for multi-instance

### JWT Authentication

- **HTTP:** `Authorization: Bearer <token>` header
- **WebSocket:** Same header in STOMP CONNECT frame
- `JwtFilter` validates HTTP requests
- `JwtChannelInterceptor` validates WebSocket connections

---

## Project Structure

### Backend

```
com.chatapp
├── config/          (WebSocket, Redis, Security)
├── controller/      (Auth, Chat, ChatHistory)
├── service/         (Auth, Chat)
├── repository/      (User, Message)
├── model/           (entity, dto)
├── security/        (JWT, JwtFilter, JwtChannelInterceptor)
└── exception/       (GlobalExceptionHandler)
```

### Frontend

```
src/
├── components/      (ChatRoom, MessageBubble, UserList, Login)
├── services/        (api, websocket)
├── context/         (AuthContext)
├── hooks/           (useWebSocket)
└── utils/
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register user |
| POST | `/api/auth/login` | Login user |
| GET | `/api/chat/history/public` | Public chat history |
| GET | `/api/chat/history/room?roomId=` | Room history |
| GET | `/api/chat/history/private?userId=&otherUserId=` | Private history |
| GET | `/api/chat/users/online` | Online users |

*All chat endpoints except auth require `Authorization: Bearer <token>`.*
