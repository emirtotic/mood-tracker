package com.moodTracker.dto;

public record MoodEntryResponse(Long id, String date, int moodScore, String note) {}
