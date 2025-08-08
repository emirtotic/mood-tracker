package com.moodTracker.service;

import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.entity.User;

import java.time.LocalDate;
import java.util.Date;

public interface MoodEntryService {
    MoodEntryResponse create(String userEmail, MoodEntryRequest req); // 409 ako postoji
    MoodEntryResponse getEntryForDate(String email, LocalDate date);
    MoodEntryResponse getToday(String userEmail);
}
