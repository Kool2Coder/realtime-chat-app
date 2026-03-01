package com.chatapp.service;

import com.chatapp.exception.UserAlreadyExistsException;
import com.chatapp.model.dto.AuthRequestDTO;
import com.chatapp.model.dto.AuthResponseDTO;
import com.chatapp.model.entity.User;
import com.chatapp.repository.UserRepository;
import com.chatapp.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponseDTO register(AuthRequestDTO request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getUsername());

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole());
        return AuthResponseDTO.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }

    public AuthResponseDTO login(AuthRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole());
        log.info("User logged in: {}", user.getUsername());

        return AuthResponseDTO.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .userId(user.getId())
                .build();
    }
}
