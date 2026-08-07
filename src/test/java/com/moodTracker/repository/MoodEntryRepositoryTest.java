package com.moodTracker.repository;

import com.moodTracker.entity.MoodEntry;
import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class MoodEntryRepositoryTest {

    @Autowired
    private MoodEntryRepository moodEntryRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .firstName("Emir")
                .lastName("Totic")
                .email("emir@example.com")
                .password("encoded-password")
                .enabled(true)
                .role(Role.USER)
                .build());
    }

    @Test
    void shouldFindEntryByUserAndDate() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        MoodEntry savedEntry = moodEntryRepository.saveAndFlush(entry(date, 4, "A productive day"));

        assertThat(moodEntryRepository.findByUserIdAndEntryDate(user.getId(), date))
                .contains(savedEntry);
        assertThat(moodEntryRepository.existsByUserIdAndEntryDate(user.getId(), date))
                .isTrue();
    }

    @Test
    void shouldReturnEntriesInDateRangeOrderedByDateDescending() {
        moodEntryRepository.saveAllAndFlush(List.of(
                entry(LocalDate.of(2026, 7, 10), 3, "An ordinary day"),
                entry(LocalDate.of(2026, 7, 20), 5, "An excellent day"),
                entry(LocalDate.of(2026, 7, 15), 4, "A good day"),
                entry(LocalDate.of(2026, 6, 30), 2, "A difficult day")
        ));

        List<MoodEntry> result =
                moodEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        user.getId(),
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31)
                );

        assertThat(result)
                .extracting(MoodEntry::getEntryDate)
                .containsExactly(
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 7, 10)
                );
    }

    @Test
    void shouldReturnPagedEntriesInDateRange() {
        moodEntryRepository.saveAllAndFlush(List.of(
                entry(LocalDate.of(2026, 7, 10), 3, "An ordinary day"),
                entry(LocalDate.of(2026, 7, 15), 4, "A good day"),
                entry(LocalDate.of(2026, 7, 20), 5, "An excellent day")
        ));

        Page<MoodEntry> result = moodEntryRepository.findByUserIdAndEntryDateBetween(
                user.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                PageRequest.of(0, 2)
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void shouldNotReturnEntryOwnedByAnotherUser() {
        User anotherUser = userRepository.save(User.builder()
                .firstName("John")
                .lastName("Smith")
                .email("john@example.com")
                .password("encoded-password")
                .enabled(true)
                .role(Role.USER)
                .build());
        LocalDate date = LocalDate.of(2026, 7, 20);
        MoodEntry savedEntry = moodEntryRepository.saveAndFlush(entry(date, 4, "A productive day"));

        assertThat(moodEntryRepository.findByUserIdAndEntryDate(anotherUser.getId(), date))
                .isEmpty();
        assertThat(moodEntryRepository.findByIdAndUserId(savedEntry.getId(), anotherUser.getId()))
                .isEmpty();
        assertThat(moodEntryRepository.findByIdAndUserId(savedEntry.getId(), user.getId()))
                .contains(savedEntry);
    }

    @Test
    void shouldEnforceOneEntryPerUserAndDate() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        moodEntryRepository.saveAndFlush(entry(date, 4, "A productive day"));

        assertThatThrownBy(() ->
                moodEntryRepository.saveAndFlush(entry(date, 5, "An excellent day")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MoodEntry entry(LocalDate date, int score, String note) {
        return MoodEntry.builder()
                .user(user)
                .entryDate(date)
                .moodScore(score)
                .note(note)
                .build();
    }
}
