package com.moodTracker.repository;

import com.moodTracker.entity.MoodEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {

    Optional<MoodEntry> findByUserIdAndEntryDate(Long userId, LocalDate date);
    boolean existsByUserIdAndEntryDate(Long userId, LocalDate date);
}
