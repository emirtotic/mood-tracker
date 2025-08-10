package com.moodTracker.repository;

import com.moodTracker.entity.MoodEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoodEntryRepository extends JpaRepository<MoodEntry, Long> {

    Optional<MoodEntry> findByUserIdAndEntryDate(Long userId, LocalDate date);

    List<MoodEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
            Long userId, LocalDate from, LocalDate to);

    boolean existsByUserIdAndEntryDate(Long userId, LocalDate date);

    Page<MoodEntry> findByUserIdAndEntryDateBetween(Long userId, LocalDate start, LocalDate end, Pageable pageable);

}
