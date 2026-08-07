package com.moodTracker.repository;

import com.moodTracker.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM AuthToken token WHERE token.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}
