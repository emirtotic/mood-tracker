package com.moodTracker.service;

import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Date;

public interface MoodEntryService {
    MoodEntryResponse create(String userEmail, MoodEntryRequest req); // 409 ako postoji
    MoodEntryResponse getEntryForDate(String email, LocalDate date);
    MoodEntryResponse getToday(String userEmail);
    Page<MoodEntryResponse> getEntryForDateRange(String email, LocalDate start, LocalDate end, Pageable pageable);
    String deleteById(String email, Long id);
}
