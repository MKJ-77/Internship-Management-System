package com.mkj.InternHub.auth;

import com.mkj.InternHub.entity.Role;
import com.mkj.InternHub.entity.UserEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 256-bit Base64-encoded secret for testing
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 900000; // 15 minutes

    private JwtService jwtService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
        user = new UserEntity(
                UUID.randomUUID(),
                "John Doe",
                "john@example.com",
                "$2a$10$hashedpasswordstring",
                Role.STUDENT,
                "ACTIVE",
                Instant.now()
        );
    }

    @Test
    @DisplayName("Should generate valid JWT token with user email subject and role claim")
    void testGenerateTokenAndExtractClaims() {
        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isBlank());

        String email = jwtService.extractEmail(token);
        assertEquals("john@example.com", email);

        String role = jwtService.extractRole(token);
        assertEquals("STUDENT", role);

        Claims claims = jwtService.extractAllClaims(token);
        assertEquals(user.getId().toString(), claims.get("userId"));
        assertEquals("John Doe", claims.get("name"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    @DisplayName("Should validate token against matching UserDetails")
    void testIsTokenValid() {
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isTokenValid(token, user));

        UserEntity otherUser = new UserEntity(
                UUID.randomUUID(), "Other", "other@example.com", "hash", Role.STUDENT, "ACTIVE", Instant.now()
        );
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    @DisplayName("Should reject expired token")
    void testExpiredTokenValidation() throws InterruptedException {
        // Create a JwtService with 1 millisecond expiration
        JwtService shortLivedService = new JwtService(TEST_SECRET, 1);
        String token = shortLivedService.generateToken(user);

        // Sleep briefly to ensure token expires
        Thread.sleep(10);

        assertFalse(shortLivedService.validateToken(token));
        assertFalse(shortLivedService.isTokenValid(token, user));
    }

    @Test
    @DisplayName("Should reject tampered or malformed token")
    void testTamperedTokenValidation() {
        String token = jwtService.generateToken(user);
        String tamperedToken = token + "corrupted";

        assertFalse(jwtService.validateToken(tamperedToken));
        assertFalse(jwtService.isTokenValid(tamperedToken, user));
        assertFalse(jwtService.validateToken("not.a.valid.jwt"));
    }
}
