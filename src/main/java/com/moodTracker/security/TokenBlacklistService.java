package com.moodTracker.security;

import com.moodTracker.entity.AuthToken;
import com.moodTracker.entity.User;
import com.moodTracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final AuthTokenRepository authTokenRepository;

    @Transactional
    public void registerIssuedToken(String token, String jti, Instant expiresAt, User user) {
        authTokenRepository.save(AuthToken.builder()
                .tokenHash(sha256(token))
                .jti(jti)
                .user(user)
                .expiresAt(expiresAt)
                .revoked(false)
                .build());
    }

    @Transactional
    public void revoke(String token, String jti, java.time.Instant exp) {
        authTokenRepository.findByTokenHash(sha256(token))
                .ifPresent(storedToken -> {
                    storedToken.setRevoked(true);
                    authTokenRepository.save(storedToken);
                });
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token, String jti) {
        return authTokenRepository.findByTokenHash(sha256(token))
                .map(storedToken -> storedToken.isRevoked()
                        || !storedToken.getJti().equals(jti)
                        || !Instant.now().isBefore(storedToken.getExpiresAt()))
                .orElse(true);
    }

    private String sha256(String s) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash authentication token", e);
        }
    }
}
