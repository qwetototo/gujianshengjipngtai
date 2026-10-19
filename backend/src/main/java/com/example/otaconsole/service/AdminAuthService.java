package com.example.otaconsole.service;

import com.example.otaconsole.repository.AdminUserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {
    private static final int HASH_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final long SESSION_TTL_MILLIS = 12L * 60L * 60L * 1000L;

    private final String initialPassword;
    private final boolean requireStrongPassword;
    private final AdminUserRepository users;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(@Value("${ota.auth.password:}") String initialPassword,
                            @Value("${ota.auth.require-strong-password:false}") boolean requireStrongPassword,
                            AdminUserRepository users) {
        this.initialPassword = initialPassword;
        this.requireStrongPassword = requireStrongPassword;
        this.users = users;
    }

    @PostConstruct
    void initializeAdmin() {
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("管理员初始密码未配置，请设置 OTA_ADMIN_PASSWORD");
        }
        if (requireStrongPassword && (initialPassword.length() < 16 || initialPassword.equals("123456"))) {
            throw new IllegalStateException("生产管理员初始密码至少需要 16 个字符，且不得使用测试密码");
        }
        users.insertAdminIfMissing(hashPassword(initialPassword));
    }

    public Map<String, String> login(String username, String password) {
        String passwordHash = users.findPasswordHash(username == null ? "" : username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (password == null || !verifyPassword(password, passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(System.currentTimeMillis() + SESSION_TTL_MILLIS));
        return Map.of("token", token, "username", "admin");
    }

    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) return false;
        Session session = sessions.get(token);
        if (session == null) return false;
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password, salt, HASH_ITERATIONS);
        return "pbkdf2-sha256$" + HASH_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法安全处理管理员密码", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private record Session(long expiresAt) { }
}
