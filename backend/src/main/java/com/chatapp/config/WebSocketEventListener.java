package com.chatapp.config;

import com.chatapp.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatService chatService;

    public WebSocketEventListener(SimpMessageSendingOperations messagingTemplate, ChatService chatService) {
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = (Long) headerAccessor.getHeader("userId");
        String username = (String) headerAccessor.getHeader("username");
        if (userId == null && headerAccessor.getSessionAttributes() != null) {
            userId = (Long) headerAccessor.getSessionAttributes().get("userId");
            username = (String) headerAccessor.getSessionAttributes().get("username");
        }

        if (userId != null && username != null) {
            chatService.userConnected(userId, username);
            log.info("User connected: {} (id: {})", username, userId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = (Long) headerAccessor.getHeader("userId");
        if (userId == null && headerAccessor.getSessionAttributes() != null) {
            userId = (Long) headerAccessor.getSessionAttributes().get("userId");
        }

        if (userId != null) {
            chatService.userDisconnected(userId);
            log.info("User disconnected: id={}", userId);
            java.util.Map<String, String> online = new java.util.HashMap<>();
            chatService.getOnlineUsers().forEach((id, name) -> online.put(String.valueOf(id), name));
            messagingTemplate.convertAndSend("/topic/online.users", online);
        }
    }
}
