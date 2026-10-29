package com.example.otaconsole.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdminUserRepository {
    private final JdbcTemplate jdbc;

    public AdminUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertAdminIfMissing(String passwordHash) {
        jdbc.update("""
                INSERT IGNORE INTO admin_user (username, password_hash, created_at, updated_at)
                VALUES ('admin', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, passwordHash);
    }

    public boolean updatePassword(String oldHash, String newHash) {
        return jdbc.update("UPDATE admin_user SET password_hash = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE username = 'admin' AND password_hash = ?",
                newHash, oldHash) == 1;
    }

    public Optional<String> findPasswordHash(String username) {
        return jdbc.query("SELECT password_hash FROM admin_user WHERE username = ?",
                (result, row) -> result.getString("password_hash"), username).stream().findFirst();
    }
}
