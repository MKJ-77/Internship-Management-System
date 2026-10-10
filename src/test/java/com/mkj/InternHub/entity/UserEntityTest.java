package com.mkj.InternHub.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTest {

    @Test
    @DisplayName("UserEntity correctly implements UserDetails methods")
    void testUserDetailsImplementation() {
        UUID id = UUID.randomUUID();
        UserEntity user = new UserEntity(
                id,
                "Jane Doe",
                "jane@example.com",
                "$2a$10$hashedpasswordstring",
                Role.STUDENT,
                "ACTIVE",
                Instant.now()
        );

        assertEquals("jane@example.com", user.getUsername());
        assertEquals("$2a$10$hashedpasswordstring", user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isCredentialsNonExpired());

        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        assertEquals(1, authorities.size());
        assertEquals("ROLE_STUDENT", authorities.iterator().next().getAuthority());
    }

    @Test
    @DisplayName("UserEntity maps COMPANY and ADMIN roles with ROLE_ prefix")
    void testRoleAuthorities() {
        UserEntity companyUser = new UserEntity(
                UUID.randomUUID(), "Company", "comp@example.com", "hash", Role.COMPANY, "ACTIVE", Instant.now()
        );
        assertEquals("ROLE_COMPANY", companyUser.getAuthorities().iterator().next().getAuthority());

        UserEntity adminUser = new UserEntity(
                UUID.randomUUID(), "Admin", "admin@example.com", "hash", Role.ADMIN, "ACTIVE", Instant.now()
        );
        assertEquals("ROLE_ADMIN", adminUser.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("Inactive or suspended accounts report isEnabled as false or isAccountNonLocked as false")
    void testInactiveAndSuspendedAccounts() {
        UserEntity suspendedUser = new UserEntity(
                UUID.randomUUID(), "Suspended User", "suspended@example.com", "hash", Role.STUDENT, "SUSPENDED", Instant.now()
        );
        assertFalse(suspendedUser.isEnabled(), "Suspended user must not be enabled");
        assertFalse(suspendedUser.isAccountNonLocked(), "Suspended user account must be locked");

        UserEntity inactiveUser = new UserEntity(
                UUID.randomUUID(), "Inactive User", "inactive@example.com", "hash", Role.STUDENT, "INACTIVE", Instant.now()
        );
        assertFalse(inactiveUser.isEnabled(), "Inactive user must not be enabled");
        assertTrue(inactiveUser.isAccountNonLocked(), "Inactive user is disabled rather than locked");
    }
}
