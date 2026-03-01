package com.chatapp.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private Long id;
    private Long senderId;
    private String senderUsername;
    private Long receiverId;
    private String receiverUsername;
    private String roomId;
    private String content;
    private LocalDateTime timestamp;
    private String messageType;
    private Boolean isTyping;
}
