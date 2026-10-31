package org.unsch.securityapi.service;

import org.unsch.securityapi.dto.*;
import org.unsch.securityapi.entity.AppUser;
import org.unsch.securityapi.entity.Role;
import org.unsch.securityapi.entity.UserSession;
import org.unsch.securityapi.repository.AppUserRepository;
import org.unsch.securityapi.repository.UserSessionRepository;
import org.unsch.securityapi.security.JwtProperties;
import org.unsch.securityapi.security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            AppUserRepository users,
            UserSessionRepository sessions,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        var user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        String email = normalize(request.email());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return issueTokens(user, ip, userAgent);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, String ip, String userAgent) {
        String hash = hash(request.refreshToken());
        UserSession oldSession = sessions.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        Instant now = Instant.now();
        if (oldSession.getRevokedAt() != null || oldSession.getExpiresAt().isBefore(now)) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        // Rotate refresh token: old token becomes unusable immediately.
        oldSession.setRevokedAt(now);
        oldSession.setLastUsedAt(now);

        return issueTokens(oldSession.getUser(), ip, userAgent);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        sessions.findByRefreshTokenHash(hash(refreshToken)).ifPresent(session -> {
            session.setRevokedAt(Instant.now());
            session.setLastUsedAt(Instant.now());
        });
    }

    private TokenResponse issueTokens(AppUser user, String ip, String userAgent) {
        var access = jwtService.createAccessToken(user);
        String refresh = randomToken();
        var session = new UserSession();
        session.setUser(user);
        session.setRefreshTokenHash(hash(refresh));
        session.setExpiresAt(Instant.now().plusSeconds(jwtProperties.refreshTokenDays() * 86400));
        session.setIpAddress(ip);
        session.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 1000)));
        sessions.save(session);
        return new TokenResponse("Bearer", access.token(), access.expiresIn(), refresh);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[64];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
