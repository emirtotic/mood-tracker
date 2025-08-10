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
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MoodEntryServiceImpl implements MoodEntryService {

    private final UserRepository userRepository;
    private final MoodEntryRepository moodRepo;
    private final MoodEntryMapper moodEntryMapper;

    @Override
    public MoodEntryResponse create(String userEmail, MoodEntryRequest req) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        LocalDate targetDate = req.date() != null
                ? req.date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        if (moodRepo.existsByUserIdAndEntryDate(user.getId(), targetDate)) {
            throw new MoodEntryAlreadyExistsException(
                    "Mood entry for " + targetDate + " already exists for user " + user.getEmail()
            );
        }

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
    public MoodEntryResponse getEntryForDate(String email, LocalDate date) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        var entry = moodRepo.findByUserIdAndEntryDate(user.getId(), date)
                .orElseThrow(() -> new ResourceNotFoundException("No mood entry found for date: " + date));

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
            throw new BadRequestException("Parametar 'end' ne sme biti pre 'start'.");
        }

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
            moodRepo.deleteById(id);
        } else {
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

        return new MoodEntryResponse(me.getId(), me.getEntryDate().toString(), me.getMoodScore(), me.getNote());
    }
}
