package com.moodTracker.dto;

public record MoodEntryAiRequest(String date, int score, String note) {
}
