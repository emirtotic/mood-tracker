package com.moodTracker.dto;

import lombok.Builder;

@Builder
public record MoodEntryResponse(Long id, String date, int moodScore, String note) {}
