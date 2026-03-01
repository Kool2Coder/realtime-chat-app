package com.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Configuration
@ConditionalOnProperty(name = "websocket.redis-broker.enabled", havingValue = "true")
public class RedisMessageBrokerConfig {

    public static final String REDIS_WS_CHANNEL = "ws:broadcast";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter redisMessageListenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisMessageListenerAdapter, new ChannelTopic(REDIS_WS_CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter redisMessageListenerAdapter(RedisMessageForwarder forwarder) {
        return new MessageListenerAdapter(forwarder, "onMessage");
    }

    @Bean
    public RedisMessageForwarder redisMessageForwarder(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        return new RedisMessageForwarder(messagingTemplate, objectMapper);
    }

}
