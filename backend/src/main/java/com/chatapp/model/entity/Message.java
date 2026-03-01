package com.chatapp.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "room_id")
    private String roomId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "message_type", length = 20)
    private String messageType;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
