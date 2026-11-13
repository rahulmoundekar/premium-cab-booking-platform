package com.suabhi.cabbooking.identity.service;

import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private static final String FIND_ACCOUNT = """
            SELECT id, email, password_hash, full_name, account_status, email_verified
            FROM app_users
            WHERE lower(email) = ?
            """;
    private static final String FIND_ROLES = """
            SELECT r.code
            FROM roles r
            JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = ?
            ORDER BY r.code
            """;

    private final JdbcTemplate jdbcTemplate;

    public AppUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        List<AccountRow> rows = jdbcTemplate.query(FIND_ACCOUNT, (rs, rowNum) ->
                new AccountRow(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("full_name"),
                        rs.getString("account_status"), rs.getBoolean("email_verified")), email);

        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("Invalid email or password");
        }

        AccountRow account = rows.getFirst();
        List<String> roleCodes = jdbcTemplate.query(FIND_ROLES,
                (rs, rowNum) -> rs.getString("code"), account.id());

        return new AppUserPrincipal(account.id(), account.email(), account.passwordHash(),
                account.fullName(), account.accountStatus(), account.emailVerified(),
                Set.copyOf(roleCodes));
    }

    private record AccountRow(UUID id, String email, String passwordHash, String fullName,
                              String accountStatus, boolean emailVerified) {}
}
