package com.chatapp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 98)
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtil jwtUtil;

    public JwtChannelInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            String token = null;

            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && jwtUtil.validateToken(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                Long userId = jwtUtil.getUserIdFromToken(token);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
                accessor.setUser(auth);
                accessor.setHeader("userId", userId);
                accessor.setHeader("username", username);
                java.util.Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs == null) {
                    sessionAttrs = new java.util.HashMap<>();
                    accessor.setSessionAttributes(sessionAttrs);
                }
                sessionAttrs.put("userId", userId);
                sessionAttrs.put("username", username);
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("WebSocket authenticated: {}", username);
            } else {
                log.warn("WebSocket connection rejected: invalid or missing JWT");
                return null;
            }
        } else {
            java.util.Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                Object userId = sessionAttrs.get("userId");
                Object username = sessionAttrs.get("username");
                if (userId != null) {
                    accessor.setHeader("userId", userId);
                }
                if (username != null) {
                    accessor.setHeader("username", username);
                }
            }
        }

        return message;
    }
}
