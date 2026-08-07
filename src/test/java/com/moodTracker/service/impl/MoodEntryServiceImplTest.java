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
import com.moodTracker.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoodEntryServiceImplTest {

    private static final String EMAIL = "emir@example.com";
    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MoodEntryRepository moodEntryRepository;

    @Mock
    private MoodEntryMapper moodEntryMapper;

    private MoodEntryServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MoodEntryServiceImpl(userRepository, moodEntryRepository, moodEntryMapper);
        user = User.builder()
                .id(USER_ID)
                .firstName("Emir")
                .lastName("Totic")
                .email(EMAIL)
                .build();
    }

    @Nested
    class Create {

        @Test
        void shouldCreateEntryForRequestedDate() {
            LocalDate date = LocalDate.of(2026, 7, 20);
            MoodEntryRequest request = new MoodEntryRequest(4, date, "Today is a good day");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.existsByUserIdAndEntryDate(USER_ID, date)).thenReturn(false);
            when(moodEntryRepository.saveAndFlush(any(MoodEntry.class))).thenAnswer(invocation -> {
                MoodEntry entry = invocation.getArgument(0);
                entry.setId(10L);
                return entry;
            });

            MoodEntryResponse response = service.create(EMAIL, request);

            assertThat(response)
                    .isEqualTo(new MoodEntryResponse(10L, "2026-07-20", 4, "Today is a good day"));

            ArgumentCaptor<MoodEntry> captor = ArgumentCaptor.forClass(MoodEntry.class);
            verify(moodEntryRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue())
                    .extracting(MoodEntry::getUser, MoodEntry::getEntryDate,
                            MoodEntry::getMoodScore, MoodEntry::getNote)
                    .containsExactly(user, date, 4, "Today is a good day");
        }

        @Test
        void shouldUseTodayWhenDateIsNotProvided() {
            LocalDate beforeCall = LocalDate.now();
            MoodEntryRequest request = new MoodEntryRequest(3, null, "Usual day");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.existsByUserIdAndEntryDate(USER_ID, beforeCall)).thenReturn(false);
            when(moodEntryRepository.saveAndFlush(any(MoodEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

            MoodEntryResponse response = service.create(EMAIL, request);

            assertThat(LocalDate.parse(response.date()))
                    .isBetween(beforeCall, LocalDate.now());
            verify(moodEntryRepository).existsByUserIdAndEntryDate(USER_ID, LocalDate.parse(response.date()));
        }

        @Test
        void shouldRejectDuplicateEntry() {
            LocalDate date = LocalDate.of(2026, 7, 20);
            MoodEntryRequest request = new MoodEntryRequest(4, date, "Today is a good day");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.existsByUserIdAndEntryDate(USER_ID, date)).thenReturn(true);

            assertThatThrownBy(() -> service.create(EMAIL, request))
                    .isInstanceOf(MoodEntryAlreadyExistsException.class)
                    .hasMessageContaining(date.toString());

            verify(moodEntryRepository, never()).saveAndFlush(any());
        }

        @Test
        void shouldRejectDuplicateCreatedByConcurrentRequest() {
            LocalDate date = LocalDate.of(2026, 7, 20);
            MoodEntryRequest request = new MoodEntryRequest(4, date, "Today is a good day");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.existsByUserIdAndEntryDate(USER_ID, date)).thenReturn(false);
            when(moodEntryRepository.saveAndFlush(any(MoodEntry.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            assertThatThrownBy(() -> service.create(EMAIL, request))
                    .isInstanceOf(MoodEntryAlreadyExistsException.class)
                    .hasMessageContaining(date.toString());

            verify(moodEntryRepository).saveAndFlush(any(MoodEntry.class));
        }

        @Test
        void shouldFailWhenUserDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.create(EMAIL, new MoodEntryRequest(4, LocalDate.now(), "Today is a good day")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found: " + EMAIL);

            verifyNoInteractions(moodEntryRepository);
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateExistingEntry() {
            LocalDate date = LocalDate.of(2026, 7, 19);
            MoodEntry existing = entry(10L, date, 2, "Bad day");
            MoodEntryRequest request = new MoodEntryRequest(5, date, "Better");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, date))
                    .thenReturn(Optional.of(existing));

            MoodEntryResponse response = service.update(EMAIL, request);

            assertThat(response)
                    .isEqualTo(new MoodEntryResponse(10L, "2026-07-19", 5, "Better"));
            verify(moodEntryRepository).save(existing);
        }

        @Test
        void shouldRejectUpdateWhenEntryDoesNotExist() {
            LocalDate date = LocalDate.of(2026, 7, 19);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, date))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.update(EMAIL, new MoodEntryRequest(5, date, "Better")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining(date.toString());

            verify(moodEntryRepository, never()).save(any());
        }
    }

    @Nested
    class Read {

        @Test
        void shouldReturnEntryForDate() {
            LocalDate date = LocalDate.of(2026, 7, 18);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, date))
                    .thenReturn(Optional.of(entry(12L, date, 4, "Chill")));

            MoodEntryResponse response = service.getEntryForDate(EMAIL, date);

            assertThat(response)
                    .isEqualTo(new MoodEntryResponse(12L, "2026-07-18", 4, "Chill"));
        }

        @Test
        void shouldFailWhenEntryForDateDoesNotExist() {
            LocalDate date = LocalDate.of(2026, 7, 18);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, date))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEntryForDate(EMAIL, date))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(date.toString());
        }

        @Test
        void shouldReturnEntriesFromLastThirtyDays() {
            LocalDate beforeCall = LocalDate.now();
            List<MoodEntry> entries = List.of(entry(1L, beforeCall, 4, "Good"));
            List<MoodEntryDto> expected = List.of(MoodEntryDto.builder()
                    .id(1L)
                    .userId(USER_ID)
                    .entryDate(beforeCall)
                    .moodScore(4)
                    .note("Good")
                    .build());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                    USER_ID, beforeCall.minusDays(30), beforeCall)).thenReturn(entries);
            when(moodEntryMapper.toDto(entries)).thenReturn(expected);

            List<MoodEntryDto> result = service.getEntriesForDate(EMAIL);

            assertThat(result).isSameAs(expected);
            verify(moodEntryMapper).toDto(entries);
        }

        @Test
        void shouldFailWhenThereAreNoEntriesInLastThirtyDays() {
            LocalDate today = LocalDate.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                    USER_ID, today.minusDays(30), today)).thenReturn(List.of());

            assertThatThrownBy(() -> service.getEntriesForDate(EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(EMAIL);

            verifyNoInteractions(moodEntryMapper);
        }

        @Test
        void shouldReturnMappedPageForDateRange() {
            LocalDate start = LocalDate.of(2026, 7, 1);
            LocalDate end = LocalDate.of(2026, 7, 31);
            Pageable pageable = PageRequest.of(0, 10);
            Page<MoodEntry> page = new PageImpl<>(
                    List.of(entry(15L, LocalDate.of(2026, 7, 10), 5, "Awesome")),
                    pageable,
                    1);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDateBetween(USER_ID, start, end, pageable))
                    .thenReturn(page);

            Page<MoodEntryResponse> result =
                    service.getEntryForDateRange(EMAIL, start, end, pageable);

            assertThat(result.getTotalElements()).isOne();
            assertThat(result.getContent()).containsExactly(
                    new MoodEntryResponse(15L, "2026-07-10", 5, "Awesome"));
        }

        @Test
        void shouldRejectInvalidDateRange() {
            LocalDate start = LocalDate.of(2026, 7, 20);
            LocalDate end = LocalDate.of(2026, 7, 10);
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

            assertThatThrownBy(() ->
                    service.getEntryForDateRange(EMAIL, start, end, pageable))
                    .isInstanceOf(BadRequestException.class);

            verify(moodEntryRepository, never())
                    .findByUserIdAndEntryDateBetween(any(), any(), any(), any());
        }

        @Test
        void shouldReturnTodaysEntry() {
            LocalDate today = LocalDate.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, today))
                    .thenReturn(Optional.of(entry(20L, today, 3, "Good enough")));

            MoodEntryResponse response = service.getToday(EMAIL);

            assertThat(response)
                    .isEqualTo(new MoodEntryResponse(20L, today.toString(), 3, "Good enough"));
        }

        @Test
        void shouldFailWhenThereIsNoEntryForToday() {
            LocalDate today = LocalDate.now();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findByUserIdAndEntryDate(USER_ID, today))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getToday(EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("No entry for today");
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldDeleteExistingEntry() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findById(10L))
                    .thenReturn(Optional.of(entry(10L, LocalDate.now(), 3, "Ok")));

            String result = service.deleteById(EMAIL, 10L);

            assertThat(result).isEqualTo("Entry has been deleted.");
            verify(moodEntryRepository).deleteById(10L);
        }

        @Test
        void shouldRejectDeleteWhenEntryDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteById(EMAIL, 10L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Record with provided ID doesn't exist.");

            verify(moodEntryRepository, never()).deleteById(any());
        }
    }

    private MoodEntry entry(Long id, LocalDate date, int score, String note) {
        return MoodEntry.builder()
                .id(id)
                .user(user)
                .entryDate(date)
                .moodScore(score)
                .note(note)
                .build();
    }
}
