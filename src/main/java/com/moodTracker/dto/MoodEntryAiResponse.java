package com.moodTracker.dto;

import java.util.List;

public record MoodEntryAiResponse(double average, String summary, List<String> suggestions) {
}
