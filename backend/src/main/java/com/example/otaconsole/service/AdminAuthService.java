package com.example.otaconsole.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {
    private static final long SESSION_TTL_MILLIS = 12L * 60L * 60L * 1000L;

    private final String configuredUsername;
    private final String configuredPassword;
    private final boolean requireStrongPassword;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(@Value("${ota.auth.username:}") String configuredUsername,
                            @Value("${ota.auth.password:}") String configuredPassword,
                            @Value("${ota.auth.require-strong-password:false}") boolean requireStrongPassword) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
        this.requireStrongPassword = requireStrongPassword;
    }

    @PostConstruct
    void validateCredentials() {
        if (configuredUsername == null || configuredUsername.trim().isEmpty()
                || configuredPassword == null || configuredPassword.trim().isEmpty()) {
            throw new IllegalStateException("管理员账号未配置，请设置 OTA_ADMIN_USERNAME 和 OTA_ADMIN_PASSWORD");
        }
        if (requireStrongPassword && (configuredPassword.length() < 16
                || configuredPassword.equalsIgnoreCase(configuredUsername)
                || configuredPassword.equals("123456"))) {
            throw new IllegalStateException("生产管理员密码至少需要 16 个字符，且不得与用户名或测试密码相同");
        }
    }

    public Map<String, String> login(String username, String password) {
        if (!constantTimeEquals(configuredUsername, username) || !constantTimeEquals(configuredPassword, password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(username, System.currentTimeMillis() + SESSION_TTL_MILLIS));

        Map<String, String> response = new java.util.HashMap<>();
        response.put("token", token);
        response.put("username", username);
        return response;
    }

    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Session {
        private final String username;
        private final long expiresAt;

        private Session(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
