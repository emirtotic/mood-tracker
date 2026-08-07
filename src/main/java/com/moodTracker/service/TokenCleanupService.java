package com.moodTracker.service;

import com.moodTracker.repository.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final AuthTokenRepository authTokenRepository;

    // scheduled for every 4 days
    @Scheduled(fixedRate = 4, timeUnit = java.util.concurrent.TimeUnit.DAYS)
    @Transactional
    public void deleteExpiredTokens() {
        authTokenRepository.deleteExpiredTokens(Instant.now());
    }
}
