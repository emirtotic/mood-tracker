package com.moodTracker.dto;

import java.time.Instant;

public record TokenValidationResponse(boolean valid, String email, Instant expiresAt) {
}
