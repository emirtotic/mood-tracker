package com.moodTracker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record MoodEntryRequest(int moodScore, @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, String note) {}
