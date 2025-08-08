package com.moodTracker.dto;

import java.util.Date;

public record MoodEntryRequest(int moodScore, Date date, String note) {}
