package com.chatapp.service;

import com.chatapp.model.dto.ChatMessageDTO;
import com.chatapp.model.entity.Message;
import com.chatapp.model.entity.User;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    private final Map<Long, String> onlineUsers = new ConcurrentHashMap<>();

    public ChatService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public void userConnected(Long userId, String username) {
        onlineUsers.put(userId, username);
        log.debug("User connected: {} (id: {})", username, userId);
    }

    public void userDisconnected(Long userId) {
        onlineUsers.remove(userId);
        log.debug("User disconnected: id={}", userId);
    }

    public Map<Long, String> getOnlineUsers() {
        return Map.copyOf(onlineUsers);
    }

    public String getUsernameById(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("Unknown");
    }

    public Long getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(0L);
    }

    public ChatMessageDTO saveAndConvertMessage(Message message) {
        Message saved = messageRepository.save(message);
        return toDTO(saved);
    }

    public List<ChatMessageDTO> getPublicHistory() {
        return messageRepository.findPublicMessages().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ChatMessageDTO> getRoomHistory(String roomId) {
        return messageRepository.findByRoomId(roomId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ChatMessageDTO> getPrivateHistory(Long userId, Long otherUserId) {
        return messageRepository.findPrivateMessages(userId, otherUserId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ChatMessageDTO toDTO(Message message) {
        String senderUsername = userRepository.findById(message.getSenderId())
                .map(User::getUsername)
                .orElse("Unknown");

        String receiverUsername = null;
        if (message.getReceiverId() != null) {
            receiverUsername = userRepository.findById(message.getReceiverId())
                    .map(User::getUsername)
                    .orElse("Unknown");
        }

        return ChatMessageDTO.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .senderUsername(senderUsername)
                .receiverId(message.getReceiverId())
                .receiverUsername(receiverUsername)
                .roomId(message.getRoomId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .messageType(message.getMessageType())
                .isTyping(false)
                .build();
    }
}
