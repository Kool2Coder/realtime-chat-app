package com.chatapp.controller;

import com.chatapp.config.RedisMessageBrokerConfig;
import com.chatapp.model.dto.ChatMessageDTO;
import com.chatapp.model.entity.Message;
import com.chatapp.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisStringTemplate;

    @Value("${websocket.redis-broker.enabled:false}")
    private boolean redisBrokerEnabled;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/presence.join")
    public void presenceJoin(Principal principal,
                             @Header("userId") Optional<Long> userId,
                             @Header("username") Optional<String> username) {
        if (principal == null) return;
        String uname = username.orElse(principal.getName());
        Long uid = userId.orElse(0L);
        if (uid == 0L) uid = chatService.getUserIdByUsername(principal.getName());
        if (uid != 0L) {
            chatService.userConnected(uid, uname);
            Map<String, String> online = new HashMap<>();
            chatService.getOnlineUsers().forEach((id, name) -> online.put(String.valueOf(id), name));
            messagingTemplate.convertAndSend("/topic/online.users", online);
        }
    }

    @MessageMapping("/chat.public")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/public")
    public ChatMessageDTO sendPublicMessage(@Payload ChatMessageDTO message,
                                            @Header("userId") Optional<Long> userId,
                                            @Header("username") Optional<String> username,
                                            Principal principal) {
        Long senderId = userId.orElse(0L);
        String senderUsername = username.orElse(principal != null ? principal.getName() : "Anonymous");
        if (senderId == 0L && principal != null) {
            senderId = chatService.getUserIdByUsername(principal.getName());
        }

        Message entity = Message.builder()
                .senderId(senderId)
                .content(message.getContent())
                .messageType("PUBLIC")
                .build();

        ChatMessageDTO dto = chatService.saveAndConvertMessage(entity);
        dto.setSenderId(senderId);
        dto.setSenderUsername(senderUsername);

        broadcastViaRedis("/topic/public", dto);
        return dto;
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessageDTO message,
                                   @Header("userId") Optional<Long> senderId,
                                   @Header("username") Optional<String> senderUsername,
                                   Principal principal) {
        Long fromId = senderId.orElse(0L);
        String fromUsername = senderUsername.orElse(principal != null ? principal.getName() : "Anonymous");
        if (fromId == 0L && principal != null) {
            fromId = chatService.getUserIdByUsername(principal.getName());
        }
        Long toId = message.getReceiverId();

        if (toId == null) {
            log.warn("Private message missing receiverId");
            return;
        }

        Message entity = Message.builder()
                .senderId(fromId)
                .receiverId(toId)
                .content(message.getContent())
                .messageType("PRIVATE")
                .build();

        ChatMessageDTO dto = chatService.saveAndConvertMessage(entity);
        dto.setSenderId(fromId);
        dto.setSenderUsername(fromUsername);
        dto.setReceiverId(toId);

        String receiverUsername = chatService.getUsernameById(toId);
        messagingTemplate.convertAndSendToUser(receiverUsername, "/queue/private", dto);
        broadcastViaRedis("/user/" + receiverUsername + "/queue/private", dto);
    }

    @MessageMapping("/chat.room")
    public void sendRoomMessage(@Payload ChatMessageDTO message,
                                @Header("userId") Optional<Long> userId,
                                @Header("username") Optional<String> username,
                                Principal principal) {
        String roomId = message.getRoomId();
        if (roomId == null || roomId.isBlank()) {
            log.warn("Room message missing roomId");
            return;
        }
        Long senderId = userId.orElse(0L);
        String senderUsername = username.orElse(principal != null ? principal.getName() : "Anonymous");
        if (senderId == 0L && principal != null) {
            senderId = chatService.getUserIdByUsername(principal.getName());
        }

        Message entity = Message.builder()
                .senderId(senderId)
                .roomId(roomId)
                .content(message.getContent())
                .messageType("ROOM")
                .build();

        ChatMessageDTO dto = chatService.saveAndConvertMessage(entity);
        dto.setSenderId(senderId);
        dto.setSenderUsername(senderUsername);
        dto.setRoomId(roomId);

        String destination = "/topic/room." + roomId;
        messagingTemplate.convertAndSend(destination, dto);
        broadcastViaRedis(destination, dto);
    }

    @MessageMapping("/typing.public")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/public.typing")
    public ChatMessageDTO typingPublic(@Payload ChatMessageDTO message,
                                       @Header("userId") Optional<Long> userId,
                                       @Header("username") Optional<String> username,
                                       Principal principal) {
        ChatMessageDTO dto = new ChatMessageDTO();
        Long uid = userId.orElse(0L);
        if (uid == 0L && principal != null) uid = chatService.getUserIdByUsername(principal.getName());
        dto.setSenderId(uid);
        dto.setSenderUsername(username.orElse(principal != null ? principal.getName() : "Anonymous"));
        dto.setIsTyping(message.getIsTyping() != null ? message.getIsTyping() : true);
        return dto;
    }

    @MessageMapping("/typing.room")
    public void typingRoom(@Payload ChatMessageDTO message,
                           @Header("userId") Optional<Long> userId,
                           @Header("username") Optional<String> username,
                           Principal principal) {
        String roomId = message.getRoomId();
        if (roomId == null || roomId.isBlank()) return;
        ChatMessageDTO dto = new ChatMessageDTO();
        Long uid = userId.orElse(0L);
        if (uid == 0L && principal != null) uid = chatService.getUserIdByUsername(principal.getName());
        dto.setSenderId(uid);
        dto.setSenderUsername(username.orElse(principal != null ? principal.getName() : "Anonymous"));
        dto.setRoomId(roomId);
        dto.setIsTyping(message.getIsTyping() != null ? message.getIsTyping() : true);
        messagingTemplate.convertAndSend("/topic/room." + roomId + ".typing", dto);
    }

    @MessageMapping("/typing.private")
    public void typingPrivate(@Payload ChatMessageDTO message,
                              @Header("userId") Optional<Long> userId,
                              @Header("username") Optional<String> username,
                              Principal principal) {
        Long receiverId = message.getReceiverId();
        if (receiverId == null) return;
        Long uid = userId.orElse(0L);
        if (uid == 0L && principal != null) uid = chatService.getUserIdByUsername(principal.getName());
        String senderUsername = username.orElse(principal != null ? principal.getName() : "Anonymous");
        String receiverUsername = chatService.getUsernameById(receiverId);

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setSenderId(uid);
        dto.setSenderUsername(senderUsername);
        dto.setReceiverId(receiverId);
        dto.setIsTyping(message.getIsTyping() != null ? message.getIsTyping() : true);

        messagingTemplate.convertAndSendToUser(receiverUsername, "/queue/private.typing", dto);
    }

    private void broadcastViaRedis(String destination, Object payload) {
        if (redisBrokerEnabled && redisStringTemplate != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String envelope = mapper.writeValueAsString(Map.of("destination", destination, "payload", payload));
                redisStringTemplate.convertAndSend(RedisMessageBrokerConfig.REDIS_WS_CHANNEL, envelope);
            } catch (Exception e) {
                log.warn("Failed to publish to Redis: {}", e.getMessage());
            }
        }
    }
}
