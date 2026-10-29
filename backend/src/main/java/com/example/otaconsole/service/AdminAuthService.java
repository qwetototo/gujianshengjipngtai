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
    private final AdminUserRepository users;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(@Value("${ota.auth.password:}") String initialPassword,
                            AdminUserRepository users) {
        this.initialPassword = initialPassword;
        this.users = users;
    }

    @PostConstruct
    void initializeAdmin() {
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("管理员初始密码未配置，请设置 OTA_ADMIN_PASSWORD");
        }
        users.insertAdminIfMissing(hashPassword(initialPassword));
    }

    public synchronized Map<String, String> login(String username, String password) {
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

    public synchronized void changePassword(String token, String currentPassword, String newPassword) {
        if (!isValid(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        String oldHash = users.findPasswordHash("admin").orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员账号不存在"));
        if (currentPassword == null || !verifyPassword(currentPassword, oldHash))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
        if (newPassword == null || newPassword.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空");
        if (!users.updatePassword(oldHash, hashPassword(newPassword)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "密码已发生变化，请重新登录");
        sessions.clear();
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
