package com.moodTracker.service;

import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface MoodEntryService {
    MoodEntryResponse create(String userEmail, MoodEntryRequest req); // 409 ako postoji
    MoodEntryResponse update(String userEmail, MoodEntryRequest req);
    MoodEntryResponse getEntryForDate(String email, LocalDate date);
    List<MoodEntryDto> getEntriesForDate(String email);
    MoodEntryResponse getToday(String userEmail);
    Page<MoodEntryResponse> getEntryForDateRange(String email, LocalDate start, LocalDate end, Pageable pageable);
    String deleteById(String email, Long id);
}
