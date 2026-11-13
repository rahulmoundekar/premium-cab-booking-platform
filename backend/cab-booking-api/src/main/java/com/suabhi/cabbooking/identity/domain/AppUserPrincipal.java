package com.suabhi.cabbooking.identity.domain;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AppUserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final String accountStatus;
    private final boolean emailVerified;
    private final Set<String> roleCodes;

    public AppUserPrincipal(UUID id, String email, String passwordHash, String fullName,
                            String accountStatus, boolean emailVerified, Set<String> roleCodes) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.fullName = fullName;
        this.accountStatus = accountStatus;
        this.emailVerified = emailVerified;
        this.roleCodes = Set.copyOf(roleCodes);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public boolean isEmailVerified() { return emailVerified; }
    public Set<String> getRoleCodes() { return roleCodes; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleCodes.stream()
                .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                .toList();
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() {
        return !Set.of("LOCKED", "SUSPENDED", "CLOSED").contains(accountStatus);
    }
    @Override public boolean isEnabled() { return "ACTIVE".equals(accountStatus); }
}
