package com.moodTracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moodTracker.dto.AiPlan;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import com.moodTracker.entity.User;
import com.moodTracker.repository.AiAnalysisRepository;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.service.MoodEntryService;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAdviceServiceImplTest {

    private static final String EMAIL = "emir@example.com";
    private static final long USER_ID = 1L;
    private static final String BASE_URL = "https://openrouter.example/api/v1";
    private static final String API_KEY = "test-api-key";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MoodEntryService moodEntryService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiAnalysisRepository aiAnalysisRepository;

    private AiAdviceServiceImpl service;
    private ObjectMapper objectMapper;
    private User user;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AiAdviceServiceImpl(
                restTemplate,
                objectMapper,
                moodEntryService,
                userRepository,
                aiAnalysisRepository
        );
        ReflectionTestUtils.setField(service, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(service, "apiKey", API_KEY);
        ReflectionTestUtils.setField(service, "referer", "https://mood-tracker.example");
        ReflectionTestUtils.setField(service, "title", "Mood Tracker");

        user = User.builder()
                .id(USER_ID)
                .firstName("Emir")
                .lastName("Totic")
                .email(EMAIL)
                .build();
    }

    @Nested
    class Analyze {

        @Test
        void shouldAnalyzeEntriesAndSaveCleanedResult() throws Exception {
            List<MoodEntryDto> entries = List.of(
                    entry(1L, LocalDate.of(2026, 7, 27), 3, "A difficult day"),
                    entry(2L, LocalDate.of(2026, 7, 29), 4, "A productive day"),
                    entry(3L, LocalDate.of(2026, 7, 28), 5, "I felt calm")
            );
            String adviceJson = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("summary", "  Your mood was mostly positive.  ")
                    .set("suggestions", objectMapper.createArrayNode()
                            .add(" Take a short walk ")
                            .add("Write down one positive thought")
                            .add("Take a short walk")
                            .add("Call a close friend")
                            .add("Keep a consistent sleep schedule")
                            .add("Plan one relaxing activity")));

            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL)).thenReturn(entries);
            when(restTemplate.exchange(
                    eq(BASE_URL + "/chat/completions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(openRouterResponse(adviceJson)));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            MoodEntryAiResponse result = service.analyze(EMAIL);

            assertThat(result.average()).isEqualTo(4.0);
            assertThat(result.summary()).isEqualTo("Your mood was mostly positive.");
            assertThat(result.suggestions()).containsExactly(
                    "Take a short walk",
                    "Write down one positive thought",
                    "Call a close friend",
                    "Keep a consistent sleep schedule",
                    "Plan one relaxing activity"
            );

            ArgumentCaptor<AiAnalysis> analysisCaptor = ArgumentCaptor.forClass(AiAnalysis.class);
            verify(aiAnalysisRepository).save(analysisCaptor.capture());
            assertThat(analysisCaptor.getValue().getUser()).isSameAs(user);
            assertThat(analysisCaptor.getValue().getAverage()).isEqualByComparingTo("4.0");
            assertThat(analysisCaptor.getValue().getSummary())
                    .isEqualTo("Your mood was mostly positive.");
            assertThat(analysisCaptor.getValue().getCreatedAt()).isNotNull();

            ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(
                    eq(BASE_URL + "/chat/completions"),
                    eq(HttpMethod.POST),
                    requestCaptor.capture(),
                    eq(String.class)
            );
            assertThat(requestCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer " + API_KEY);
            assertThat(requestCaptor.getValue().getHeaders().getFirst("HTTP-Referer"))
                    .isEqualTo("https://mood-tracker.example");
            assertThat(requestCaptor.getValue().getHeaders().getFirst("X-Title"))
                    .isEqualTo("Mood Tracker");
            assertThat(requestCaptor.getValue().getBody().toString())
                    .contains("\"model\":\"openrouter/free\"")
                    .contains("2026-07-29", "2026-07-28", "2026-07-27");
        }

        @Test
        void shouldUpdateExistingAnalysis() throws Exception {
            AiAnalysis existingAnalysis = AiAnalysis.builder()
                    .id(10L)
                    .user(user)
                    .average(BigDecimal.valueOf(2.0))
                    .summary("Previous summary")
                    .suggestions(List.of("Previous suggestion"))
                    .build();
            String adviceJson = """
                    {
                      "summary": "Mood is improving.",
                      "suggestions": ["Continue your healthy morning routine"]
                    }
                    """;
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL))
                    .thenReturn(List.of(entry(1L, LocalDate.of(2026, 7, 29), 4, "Feeling better")));
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenReturn(ResponseEntity.ok(openRouterResponse(adviceJson)));
            when(aiAnalysisRepository.findByUserId(USER_ID))
                    .thenReturn(Optional.of(existingAnalysis));

            service.analyze(EMAIL);

            verify(aiAnalysisRepository).save(existingAnalysis);
            assertThat(existingAnalysis.getAverage()).isEqualByComparingTo("4.0");
            assertThat(existingAnalysis.getSummary()).isEqualTo("Mood is improving.");
            assertThat(existingAnalysis.getSuggestions())
                    .containsExactly("Continue your healthy morning routine");
        }

        @Test
        void shouldReturnEmptyAnalysisWhenThereAreNoValidEntries() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL)).thenReturn(List.of(
                    new MoodEntryDto(),
                    entry(2L, null, 4, "Missing date")
            ));

            MoodEntryAiResponse result = service.analyze(EMAIL);

            assertThat(result).isEqualTo(new MoodEntryAiResponse(
                    0.0,
                    "No entries in last 30 days.",
                    List.of()
            ));
            verifyNoInteractions(restTemplate, aiAnalysisRepository);
        }

        @Test
        void shouldReturnFallbackWhenOpenRouterCallFailsWithoutRetry() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(moodEntryService.getEntriesForDate(EMAIL))
                    .thenReturn(List.of(entry(1L, LocalDate.of(2026, 7, 29), 3, "An average day")));
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenThrow(new IllegalStateException("Unexpected client failure"));

            MoodEntryAiResponse result = service.analyze(EMAIL);

            assertThat(result).isEqualTo(new MoodEntryAiResponse(3.0, "", List.of()));
            verify(aiAnalysisRepository, never()).save(any());
        }

        @Test
        void shouldRejectBlankEmailBeforeLoadingEntries() {
            assertThatThrownBy(() -> service.analyze("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Email must not be empty");

            verifyNoInteractions(userRepository, moodEntryService, restTemplate, aiAnalysisRepository);
        }

        @Test
        void shouldFailWhenUserDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.analyze(EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found: " + EMAIL);

            verifyNoInteractions(moodEntryService, restTemplate, aiAnalysisRepository);
        }
    }

    @Nested
    class GeneratePlan {

        @Test
        void shouldGenerateTrimmedPlanFromSavedAnalysis() throws Exception {
            AiAnalysis analysis = validAnalysis();
            String plan = """
                    Day 1
                    - Take a twenty-minute walk.
                    Reflection: What improved your mood today?

                    Day 2
                    - Write down three positive moments.
                    """;
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(analysis));
            when(restTemplate.exchange(
                    eq(BASE_URL + "/chat/completions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok(openRouterResponse("  " + plan + "  ")));

            AiPlan result = service.generatePlan(EMAIL);

            assertThat(result.getResponse()).isEqualTo(plan.trim());

            ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(
                    eq(BASE_URL + "/chat/completions"),
                    eq(HttpMethod.POST),
                    requestCaptor.capture(),
                    eq(String.class)
            );
            assertThat(requestCaptor.getValue().getBody().toString())
                    .contains("Horizon days: 7")
                    .contains("Target: improve")
                    .contains("Maintain a regular sleep schedule")
                    .contains("Take a short walk every afternoon");
        }

        @Test
        void shouldFailWhenSavedAnalysisDoesNotExist() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No saved AI analysis for user " + USER_ID);

            verifyNoInteractions(restTemplate);
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

            verifyNoInteractions(restTemplate);
        }

        @Test
        void shouldFailWhenOpenRouterDoesNotReturnPlan() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(aiAnalysisRepository.findByUserId(USER_ID)).thenReturn(Optional.of(validAnalysis()));
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenThrow(new IllegalStateException("Unexpected client failure"));

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Plan generation temporarily unavailable. Please try again shortly.");
        }
    }

    @Nested
    class Configuration {

        @Test
        void shouldFailWhenBaseUrlIsMissing() {
            ReflectionTestUtils.setField(service, "baseUrl", " ");

            assertThatThrownBy(() -> service.analyze(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("OpenRouter base URL is not available");

            verifyNoInteractions(userRepository, moodEntryService, restTemplate, aiAnalysisRepository);
        }

        @Test
        void shouldFailWhenApiKeyIsMissing() {
            ReflectionTestUtils.setField(service, "apiKey", null);

            assertThatThrownBy(() -> service.generatePlan(EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("OpenRouter API key is not available");

            verifyNoInteractions(userRepository, restTemplate, aiAnalysisRepository);
        }
    }

    private MoodEntryDto entry(Long id, LocalDate date, int score, String note) {
        return MoodEntryDto.builder()
                .id(id)
                .userId(USER_ID)
                .entryDate(date)
                .moodScore(score)
                .note(note)
                .build();
    }

    private AiAnalysis validAnalysis() {
        return AiAnalysis.builder()
                .id(10L)
                .user(user)
                .average(BigDecimal.valueOf(3.5))
                .summary("The recent mood pattern is stable.")
                .suggestions(List.of(
                        "Maintain a regular sleep schedule",
                        "Take a short walk every afternoon"
                ))
                .build();
    }

    private String openRouterResponse(String content) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", "test/free-model");

        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        ObjectNode message = choice.putObject("message");
        message.put("content", content);

        return objectMapper.writeValueAsString(root);
    }
}
