package com.chatapp.controller;

import com.chatapp.model.dto.ChatMessageDTO;
import com.chatapp.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatHistoryController {

    private final ChatService chatService;

    public ChatHistoryController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/history/public")
    public ResponseEntity<List<ChatMessageDTO>> getPublicHistory() {
        return ResponseEntity.ok(chatService.getPublicHistory());
    }

    @GetMapping("/history/room")
    public ResponseEntity<List<ChatMessageDTO>> getRoomHistory(@RequestParam String roomId) {
        return ResponseEntity.ok(chatService.getRoomHistory(roomId));
    }

    @GetMapping("/history/private")
    public ResponseEntity<List<ChatMessageDTO>> getPrivateHistory(@RequestParam Long userId,
                                                                  @RequestParam Long otherUserId) {
        return ResponseEntity.ok(chatService.getPrivateHistory(userId, otherUserId));
    }

    @GetMapping("/users/online")
    public ResponseEntity<Map<Long, String>> getOnlineUsers() {
        return ResponseEntity.ok(chatService.getOnlineUsers());
    }
}
