package com.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisMessageForwarder implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageForwarder.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessageForwarder(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = objectMapper.readValue(payload, Map.class);
            String destination = (String) envelope.get("destination");
            Object msg = envelope.get("payload");

            if (destination != null && msg != null) {
                messagingTemplate.convertAndSend(destination, msg);
                log.debug("Forwarded Redis message to {}", destination);
            }
        } catch (Exception e) {
            log.error("Error forwarding Redis message", e);
        }
    }
}
