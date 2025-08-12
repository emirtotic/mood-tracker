package com.moodTracker.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {


    private final ConcurrentHashMap<String, Instant> revokedJti = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> revokedHash = new ConcurrentHashMap<>();

    public void revoke(String token, String jti, java.time.Instant exp) {
        if (jti != null) revokedJti.put(jti, exp);
        revokedHash.put(sha256(token), exp);
    }

    public boolean isRevoked(String token, String jti) {
        var now = java.time.Instant.now();
        // JTI check
        if (jti != null) {
            var e = revokedJti.get(jti);
            if (e != null && now.isBefore(e)) return true;
            if (e != null && now.isAfter(e)) revokedJti.remove(jti);
        }
        // hash check
        var h = sha256(token);
        var e2 = revokedHash.get(h);
        if (e2 != null && now.isBefore(e2)) return true;
        if (e2 != null && now.isAfter(e2)) revokedHash.remove(h);
        return false;
    }

    private String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
