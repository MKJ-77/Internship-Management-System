package com.mkj.InternHub.auth;

import com.mkj.InternHub.auth.dto.AuthResponse;
import com.mkj.InternHub.auth.dto.LoginRequest;
import com.mkj.InternHub.auth.dto.RegisterRequest;
import com.mkj.InternHub.entity.Role;
import com.mkj.InternHub.entity.UserEntity;
import com.mkj.InternHub.exception.AccountNotActiveException;
import com.mkj.InternHub.exception.EmailAlreadyExistsException;
import com.mkj.InternHub.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
    }

    @Test
    @DisplayName("Successful registration saves active STUDENT user with BCrypt password and returns AuthResponse")
    void testSuccessfulRegistration() {
        RegisterRequest request = new RegisterRequest("  John Doe  ", "  JOHN@EXAMPLE.COM  ", "Password@123");

        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("mock-jwt-token");
        when(jwtService.getExpirationTimeSeconds()).thenReturn(900L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
        assertEquals("John Doe", response.name());
        assertEquals("john@example.com", response.email());
        assertEquals("STUDENT", response.role());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("John Doe", saved.getName());
        assertEquals("john@example.com", saved.getEmail());
        assertEquals(Role.STUDENT, saved.getRole());
        assertEquals("ACTIVE", saved.getStatus());
        assertNotEquals("Password@123", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("Password@123", saved.getPasswordHash()));
    }

    @Test
    @DisplayName("Registration enforces BCrypt password hashing and never saves plaintext")
    void testBcryptPasswordHashing() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "Secret@987");

        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("mock-token");
        when(jwtService.getExpirationTimeSeconds()).thenReturn(900L);

        authService.register(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertTrue(saved.getPasswordHash().startsWith("$2a$"), "BCrypt hash must start with $2a$");
        assertTrue(passwordEncoder.matches("Secret@987", saved.getPasswordHash()));
        assertFalse(passwordEncoder.matches("WrongPassword", saved.getPasswordHash()));
    }

    @Test
    @DisplayName("Registration with duplicate email throws EmailAlreadyExistsException")
    void testDuplicateEmailRegistration() {
        RegisterRequest request = new RegisterRequest("Duplicate User", "existing@example.com", "Password@123");

        when(userRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );
        assertTrue(exception.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Registration catches DataIntegrityViolationException and throws EmailAlreadyExistsException")
    void testDuplicateEmailDataIntegrityViolation() {
        RegisterRequest request = new RegisterRequest("User", "concurrent@example.com", "Password@123");

        when(userRepository.existsByEmailIgnoreCase("concurrent@example.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("Successful login returns AuthResponse with token and profile info")
    void testSuccessfulLogin() {
        LoginRequest request = new LoginRequest("user@example.com", "Password@123");
        UserEntity authenticatedUser = new UserEntity(
                UUID.randomUUID(),
                "Jane Student",
                "user@example.com",
                passwordEncoder.encode("Password@123"),
                Role.STUDENT,
                "ACTIVE",
                Instant.now()
        );

        UsernamePasswordAuthenticationToken authResult = new UsernamePasswordAuthenticationToken(
                authenticatedUser, null, authenticatedUser.getAuthorities()
        );

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authResult);
        when(jwtService.generateToken(authenticatedUser)).thenReturn("login-jwt-token");
        when(jwtService.getExpirationTimeSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("login-jwt-token", response.accessToken());
        assertEquals("Jane Student", response.name());
        assertEquals("user@example.com", response.email());
        assertEquals("STUDENT", response.role());
    }

    @Test
    @DisplayName("Login with invalid credentials throws BadCredentialsException")
    void testLoginInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Login with suspended account throws AccountNotActiveException")
    void testLoginSuspendedAccount() {
        LoginRequest request = new LoginRequest("suspended@example.com", "Password@123");
        UserEntity suspendedUser = new UserEntity(
                UUID.randomUUID(),
                "Suspended User",
                "suspended@example.com",
                "hash",
                Role.STUDENT,
                "SUSPENDED",
                Instant.now()
        );

        UsernamePasswordAuthenticationToken authResult = new UsernamePasswordAuthenticationToken(
                suspendedUser, null, suspendedUser.getAuthorities()
        );

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authResult);

        assertThrows(AccountNotActiveException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Login with inactive account throws AccountNotActiveException")
    void testLoginInactiveAccount() {
        LoginRequest request = new LoginRequest("inactive@example.com", "Password@123");
        UserEntity inactiveUser = new UserEntity(
                UUID.randomUUID(),
                "Inactive User",
                "inactive@example.com",
                "hash",
                Role.STUDENT,
                "INACTIVE",
                Instant.now()
        );

        UsernamePasswordAuthenticationToken authResult = new UsernamePasswordAuthenticationToken(
                inactiveUser, null, inactiveUser.getAuthorities()
        );

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authResult);

        assertThrows(AccountNotActiveException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any());
    }
}
