package com.suabhi.cabbooking.identity.service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerAccountService {
    private static final String PROFILE_SQL = """
            SELECT id, email, full_name, phone_e164, email_verified, phone_verified, created_at
            FROM app_users
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CustomerProfile register(String emailInput, String fullNameInput,
                                    String phoneInput, String rawPassword) {
        String email = emailInput.strip().toLowerCase(Locale.ROOT);
        String fullName = fullNameInput.strip();
        String phone = blankToNull(phoneInput);

        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }

        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_users WHERE lower(email) = ?)",
                Boolean.class, email))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        if (phone != null && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_users WHERE phone_e164 = ?)",
                Boolean.class, phone))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This phone number is already registered.");
        }

        UUID userId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    INSERT INTO app_users
                    (id, email, phone_e164, password_hash, full_name, account_status,
                     email_verified, phone_verified, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', FALSE, FALSE, now(), now())
                    """, userId, email, phone, passwordEncoder.encode(rawPassword), fullName);

            int assignedRoles = jdbcTemplate.update("""
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT ?, id FROM roles WHERE code = 'CUSTOMER'
                    """, userId);

            if (assignedRoles != 1) {
                throw new IllegalStateException("The CUSTOMER role is missing from the database.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email or phone number already exists.", exception);
        }

        return findProfile(userId);
    }

    @Transactional(readOnly = true)
    public CustomerProfile findProfile(UUID userId) {
        List<CustomerProfile> profiles = jdbcTemplate.query(PROFILE_SQL, (rs, rowNum) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new CustomerProfile(
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone_e164"),
                    rs.getBoolean("email_verified"),
                    rs.getBoolean("phone_verified"),
                    createdAt == null ? null : createdAt.toInstant());
        }, userId);

        if (profiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account was not found.");
        }
        return profiles.getFirst();
    }

    @Transactional
    public CustomerProfile updateProfile(UUID userId, String fullNameInput, String phoneInput) {
        String fullName = fullNameInput.strip();
        String phone = blankToNull(phoneInput);
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE app_users
                    SET full_name = ?, phone_e164 = ?, updated_at = now()
                    WHERE id = ?
                    """, fullName, phone, userId);
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account was not found.");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This phone number is already registered.", exception);
        }
        return findProfile(userId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record CustomerProfile(UUID id, String email, String fullName, String phoneE164,
                                  boolean emailVerified, boolean phoneVerified, Instant createdAt) {}
}
