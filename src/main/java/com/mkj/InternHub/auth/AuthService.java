package com.mkj.InternHub.auth;

import com.mkj.InternHub.auth.dto.AuthResponse;
import com.mkj.InternHub.auth.dto.LoginRequest;
import com.mkj.InternHub.auth.dto.RegisterRequest;
import com.mkj.InternHub.entity.Role;
import com.mkj.InternHub.entity.UserEntity;
import com.mkj.InternHub.exception.AccountNotActiveException;
import com.mkj.InternHub.exception.EmailAlreadyExistsException;
import com.mkj.InternHub.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        UserEntity user = new UserEntity(
                null,
                request.name().trim(),
                normalizedEmail,
                hashedPassword,
                Role.STUDENT,
                "ACTIVE",
                null
        );

        UserEntity savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException("An account with this email already exists");
        }

        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationTimeSeconds(),
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        } catch (DisabledException | LockedException e) {
            throw new AccountNotActiveException("Account is inactive or suspended");
        }

        UserEntity user = (UserEntity) authentication.getPrincipal();

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AccountNotActiveException("Account is inactive or suspended");
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationTimeSeconds(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
