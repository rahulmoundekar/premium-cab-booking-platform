package com.suabhi.cabbooking.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserPrincipalTest {
    @Test
    void exposesOnlyRoleAuthoritiesForTheAccount() {
        AppUserPrincipal principal = new AppUserPrincipal(UUID.randomUUID(),
                "customer@example.com", "{bcrypt}hash", "Sample Customer",
                "ACTIVE", false, Set.of("CUSTOMER"));

        assertThat(principal.getAuthorities()).hasSize(1);
        assertThat(principal.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_CUSTOMER");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(principal.isEmailVerified()).isFalse();
    }

    @Test
    void disablesLockedAccounts() {
        AppUserPrincipal principal = new AppUserPrincipal(UUID.randomUUID(),
                "locked@example.com", "{bcrypt}hash", "Locked Customer",
                "LOCKED", false, Set.of("CUSTOMER"));

        assertThat(principal.isAccountNonLocked()).isFalse();
        assertThat(principal.isEnabled()).isFalse();
    }
}
