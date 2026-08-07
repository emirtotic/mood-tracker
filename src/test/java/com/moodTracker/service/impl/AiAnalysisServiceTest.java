package com.moodTracker.service.impl;

import com.moodTracker.dto.AiPlanResponse;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import com.moodTracker.entity.User;
import com.moodTracker.exception.ResourceNotFoundException;
import com.moodTracker.repository.AiAnalysisRepository;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.service.MoodEntryService;
import com.moodTracker.service.ai.OpenRouterClient;
import com.moodTracker.service.ai.ParsedAdvice;
import com.moodTracker.service.ai.PromptFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    private static final String EMAIL = "emir@example.com";
    private static final long USER_ID = 1L;

    @Mock private MoodEntryService moodEntryService;
    @Mock private UserRepository userRepository;
    @Mock private AiAnalysisRepository aiAnalysisRepository;
    @Mock private PromptFactory promptFactory;
    @Mock private OpenRouterClient openRouterClient;

    private AiAnalysisService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(
                moodEntryService, userRepository, aiAnalysisRepository,
                promptFactory, openRouterClient
        );
        user = User.builder().id(USER_ID).email(EMAIL).build();
    }

    @Nested
    class Analyze {

        @Test
        void shouldAnalyzeEntriesAndSaveCleanedResult() {
            List<MoodEntryDto> entries = List.of(
                    entry(1L, LocalDate.of(2026, 7, 27), 3),
                    entry(2L, LocalDate.of(2026, 7, 29), 4),
                    entry(3L, LocalDate.of(2026, 7, 28), 5)
            );
            ParsedAdvice advice = new ParsedAdvice(
                    "Your mood was mostly positive.",
                    List.of("Take a short walk", "Write down one positive thought")
            );
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL)).thenReturn(entries);
            when(promptFactory.analysisPrompt(any())).thenReturn("analysis prompt");
            when(openRouterClient.generateAnalysis("analysis prompt")).thenReturn(advice);
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            MoodEntryAiResponse result = service.analyze(EMAIL);

            assertThat(result).isEqualTo(new MoodEntryAiResponse(
                    4.0, advice.summary(), advice.suggestions()
            ));
            ArgumentCaptor<AiAnalysis> captor = ArgumentCaptor.forClass(AiAnalysis.class);
            verify(aiAnalysisRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(user);
            assertThat(captor.getValue().getAverage()).isEqualByComparingTo("4.0");
            assertThat(captor.getValue().getCreatedAt()).isNotNull();

            ArgumentCaptor<List<MoodEntryDto>> entriesCaptor = ArgumentCaptor.forClass(List.class);
            verify(promptFactory).analysisPrompt(entriesCaptor.capture());
            assertThat(entriesCaptor.getValue())
                    .extracting(MoodEntryDto::getEntryDate)
                    .containsExactly(
                            LocalDate.of(2026, 7, 29),
                            LocalDate.of(2026, 7, 28),
                            LocalDate.of(2026, 7, 27)
                    );
        }

        @Test
        void shouldUpdateExistingAnalysis() {
            AiAnalysis existing = validAnalysis();
            ParsedAdvice advice = new ParsedAdvice("Mood is improving.", List.of("Keep the routine"));
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL))
                    .thenReturn(List.of(entry(1L, LocalDate.of(2026, 7, 29), 4)));
            when(promptFactory.analysisPrompt(any())).thenReturn("prompt");
            when(openRouterClient.generateAnalysis("prompt")).thenReturn(advice);
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

            service.analyze(EMAIL);

            verify(aiAnalysisRepository).save(existing);
            assertThat(existing.getSummary()).isEqualTo("Mood is improving.");
            assertThat(existing.getSuggestions()).containsExactly("Keep the routine");
        }

        @Test
        void shouldReturnEmptyAnalysisWhenThereAreNoValidEntries() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL)).thenReturn(List.of(new MoodEntryDto()));

            MoodEntryAiResponse result = service.analyze(EMAIL);

            assertThat(result).isEqualTo(new MoodEntryAiResponse(
                    0.0, "No entries in last 30 days.", List.of()
            ));
            verify(openRouterClient, never()).generateAnalysis(any());
            verifyNoInteractions(promptFactory, aiAnalysisRepository);
        }

        @Test
        void shouldReturnFallbackWhenAiCallFails() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL))
                    .thenReturn(List.of(entry(1L, LocalDate.of(2026, 7, 29), 3)));
            when(promptFactory.analysisPrompt(any())).thenReturn("prompt");
            when(openRouterClient.generateAnalysis("prompt")).thenReturn(null);

            assertThat(service.analyze(EMAIL))
                    .isEqualTo(new MoodEntryAiResponse(3.0, "", List.of()));
            verify(aiAnalysisRepository, never()).save(any());
        }

        @Test
        void shouldRejectBlankEmail() {
            assertThatThrownBy(() -> service.analyze("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email must not be empty");
            verifyNoInteractions(userRepository, moodEntryService, promptFactory, aiAnalysisRepository);
        }

        @Test
        void shouldFailWhenUserDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.analyze(EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found: " + EMAIL);
            verifyNoInteractions(moodEntryService, promptFactory, aiAnalysisRepository);
        }
    }

    @Nested
    class GeneratePlan {

        @Test
        void shouldGenerateTrimmedPlanFromSavedAnalysis() {
            AiAnalysis analysis = validAnalysis();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(analysis));
            when(promptFactory.planPrompt(analysis)).thenReturn("plan prompt");
            when(openRouterClient.generatePlan("plan prompt")).thenReturn("  Day 1\n- Take a walk.  ");

            AiPlanResponse result = service.generatePlan(EMAIL);

            assertThat(result.getResponse()).isEqualTo("Day 1\n- Take a walk.");
        }

        @Test
        void shouldFailWhenSavedAnalysisDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No saved AI analysis for user " + USER_ID);
            verifyNoInteractions(promptFactory);
        }

        @Test
        void shouldFailWhenSavedAnalysisHasNoSummary() {
            AiAnalysis analysis = validAnalysis();
            analysis.setSummary(" ");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(analysis));

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Saved AI analysis has no summary");
            verifyNoInteractions(promptFactory);
        }

        @Test
        void shouldFailWhenOpenRouterDoesNotReturnPlan() {
            AiAnalysis analysis = validAnalysis();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(analysis));
            when(promptFactory.planPrompt(analysis)).thenReturn("prompt");

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Plan generation temporarily unavailable. Please try again shortly.");
        }
    }

    @Test
    void shouldStopWhenOpenRouterConfigurationIsInvalid() {
        doThrow(new IllegalStateException("OpenRouter API key is not available"))
                .when(openRouterClient).validateConfiguration();

        assertThatThrownBy(() -> service.analyze(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenRouter API key is not available");
        verifyNoInteractions(userRepository, moodEntryService, promptFactory, aiAnalysisRepository);
    }

    private MoodEntryDto entry(Long id, LocalDate date, int score) {
        return MoodEntryDto.builder()
                .id(id).userId(USER_ID).entryDate(date).moodScore(score).note("Note")
                .build();
    }

    private AiAnalysis validAnalysis() {
        return AiAnalysis.builder()
                .id(10L).user(user).average(BigDecimal.valueOf(3.5))
                .summary("The recent mood pattern is stable.")
                .suggestions(List.of("Maintain a regular sleep schedule"))
                .build();
    }
}
