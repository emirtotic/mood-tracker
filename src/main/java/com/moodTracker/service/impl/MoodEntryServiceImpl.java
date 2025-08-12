package com.moodTracker.service.impl;

import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.entity.MoodEntry;
import com.moodTracker.entity.User;
import com.moodTracker.exception.BadRequestException;
import com.moodTracker.exception.MoodEntryAlreadyExistsException;
import com.moodTracker.mapper.MoodEntryMapper;
import com.moodTracker.repository.MoodEntryRepository;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.service.MoodEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoodEntryServiceImpl implements MoodEntryService {

    private final UserRepository userRepository;
    private final MoodEntryRepository moodRepo;
    private final MoodEntryMapper moodEntryMapper;

    @Override
    public MoodEntryResponse create(String userEmail, MoodEntryRequest req) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LocalDate targetDate = req.date() != null
                ? req.date()
                : LocalDate.now();

        if (moodRepo.existsByUserIdAndEntryDate(user.getId(), targetDate)) {
            throw new MoodEntryAlreadyExistsException(
                    "Mood entry for " + targetDate + " already exists for user " + user.getEmail()
            );
        }

        log.info("Creating new entry for {} {}", user.getFirstName(), user.getLastName());

        MoodEntry me = MoodEntry.builder()
                .user(user)
                .entryDate(targetDate)
                .moodScore(req.moodScore())
                .note(req.note())
                .build();

        me = moodRepo.save(me);
        return new MoodEntryResponse(me.getId(), me.getEntryDate().toString(), me.getMoodScore(), me.getNote());
    }

    @Override
    public MoodEntryResponse update(String userEmail, MoodEntryRequest req) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Optional<MoodEntry> existingEntry = moodRepo.findByUserIdAndEntryDate(user.getId(), req.date());

        if (existingEntry.isPresent()) {
            log.info("Updating entry for {} {} for date {}", user.getFirstName(), user.getLastName(), existingEntry.get().getEntryDate());
            existingEntry.get().setMoodScore(req.moodScore());
            existingEntry.get().setNote(req.note());
            moodRepo.save(existingEntry.get());
        } else {
            log.error("There is no entry for this date.");
            throw new BadRequestException("Entry for date " + req.date() + " is not found");
        }

        return MoodEntryResponse.builder()
                .id(existingEntry.get().getId())
                .date(existingEntry.get().getEntryDate().toString())
                .moodScore(existingEntry.get().getMoodScore())
                .note(existingEntry.get().getNote())
                .build();
    }

    @Override
    public MoodEntryResponse getEntryForDate(String email, LocalDate date) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        var entry = moodRepo.findByUserIdAndEntryDate(user.getId(), date)
                .orElseThrow(() -> new ResourceNotFoundException("No mood entry found for date: " + date));

        log.info("Entry for {} is found", entry.getEntryDate());

        return new MoodEntryResponse(entry.getId(), date.toString(), entry.getMoodScore(), entry.getNote());
    }

    @Override
    public List<MoodEntryDto> getEntriesForDate(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<MoodEntry> entries = moodRepo.findAllByUserIdAndEntryDateBetweenOrderByEntryDateDesc(user.getId(), LocalDate.now().minusDays(30), LocalDate.now());

        List<MoodEntryDto> result = new ArrayList<>();

        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("No mood entries found for user: " + email);
        } else {
            result = moodEntryMapper.toDto(entries);
        }

        return result;
    }

    @Override
    public Page<MoodEntryResponse> getEntryForDateRange(String email, LocalDate start, LocalDate end, Pageable pageable) {

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (end.isBefore(start)) {
            throw new BadRequestException("Parameter 'end' should be before 'start'.");
        }

        log.info("Retrieving records from {} to {}", start, end);

        return moodRepo
                .findByUserIdAndEntryDateBetween(user.getId(), start, end, pageable)
                .map(e -> new MoodEntryResponse(
                        e.getId(),
                        e.getEntryDate().toString(),
                        e.getMoodScore(),
                        e.getNote()
                ));
    }

    @Override
    public String deleteById(String email, Long id) {

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        Optional<MoodEntry> mood = moodRepo.findById(id);

        if (mood.isPresent()) {
            log.info("Deleting the entry with id {}", id);
            moodRepo.deleteById(id);
        } else {
            log.error("Record with provided ID doesn't exist.");
            throw new BadRequestException("Record with provided ID doesn't exist.");
        }

        return "Entry has been deleted.";
    }



    @Override
    public MoodEntryResponse getToday(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        var today = LocalDate.now();

        MoodEntry me = moodRepo.findByUserIdAndEntryDate(user.getId(), today)
                .orElseThrow(() -> new IllegalStateException("No entry for today"));

        log.info("Getting today's entry...");

        return new MoodEntryResponse(me.getId(), me.getEntryDate().toString(), me.getMoodScore(), me.getNote());
    }
}
