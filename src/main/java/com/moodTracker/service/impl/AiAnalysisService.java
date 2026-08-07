package com.moodTracker.service.impl;

import com.moodTracker.dto.AiPlanResponse;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import com.moodTracker.entity.User;
import com.moodTracker.exception.ResourceNotFoundException;
import com.moodTracker.repository.AiAnalysisRepository;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.service.AiAdviceService;
import com.moodTracker.service.MoodEntryService;
import com.moodTracker.service.ai.OpenRouterClient;
import com.moodTracker.service.ai.ParsedAdvice;
import com.moodTracker.service.ai.PromptFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisService implements AiAdviceService {

    private final MoodEntryService moodEntryService;
    private final UserRepository userRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final PromptFactory promptFactory;
    private final OpenRouterClient openRouterClient;

    @Override
    public MoodEntryAiResponse analyze(String email) {
        openRouterClient.validateConfiguration();
        User user = findUserByEmail(email);
        List<MoodEntryDto> entries = recentEntries(user.getEmail());

        if (entries.isEmpty()) {
            return new MoodEntryAiResponse(0.0, "No entries in last 30 days.", List.of());
        }

        double average = roundedAverage(entries);
        log.info("Analyzing {} mood entries for user {}", entries.size(), user.getEmail());

        ParsedAdvice advice = openRouterClient.generateAnalysis(promptFactory.analysisPrompt(entries));
        if (advice == null) {
            log.error("Mood analysis could not be generated for user {}", user.getEmail());
            return new MoodEntryAiResponse(average, "", List.of());
        }

        saveOrUpdateAnalysis(user, average, advice);
        return new MoodEntryAiResponse(average, advice.summary(), advice.suggestions());
    }

    @Override
    public AiPlanResponse generatePlan(String email) {
        openRouterClient.validateConfiguration();
        User user = findUserByEmail(email);
        AiAnalysis analysis = aiAnalysisRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No saved AI analysis for user " + user.getId()
                ));

        validateSavedAnalysis(analysis);
        log.info("Generating AI plan for user {}", user.getEmail());

        String planText = openRouterClient.generatePlan(promptFactory.planPrompt(analysis));
        if (planText == null || planText.isBlank()) {
            throw new IllegalStateException(
                    "Plan generation temporarily unavailable. Please try again shortly."
            );
        }

        log.info("AI plan generated successfully for user {}", user.getEmail());
        return new AiPlanResponse(planText.trim());
    }

    private List<MoodEntryDto> recentEntries(String email) {
        return moodEntryService.getEntriesForDate(email).stream()
                .filter(entry -> entry != null && entry.getEntryDate() != null)
                .sorted(Comparator.comparing(MoodEntryDto::getEntryDate).reversed())
                .limit(30)
                .toList();
    }

    private double roundedAverage(List<MoodEntryDto> entries) {
        double average = entries.stream()
                .mapToInt(MoodEntryDto::getMoodScore)
                .average()
                .orElse(0.0);

        return BigDecimal.valueOf(average)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void saveOrUpdateAnalysis(User user, double average, ParsedAdvice advice) {
        Optional<AiAnalysis> previous = aiAnalysisRepository.findByUserId(user.getId());
        AiAnalysis analysis = previous.orElseGet(() -> AiAnalysis.builder().user(user).build());

        analysis.setAverage(BigDecimal.valueOf(average));
        analysis.setSummary(advice.summary());
        analysis.setSuggestions(new ArrayList<>(advice.suggestions()));
        analysis.setCreatedAt(LocalDateTime.now());
        aiAnalysisRepository.save(analysis);

        log.info("{} AI analysis for user {}",
                previous.isPresent() ? "Updated" : "Created", user.getEmail());
    }

    private User findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be empty");
        }

        String normalizedEmail = email.trim();
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + normalizedEmail
                ));
    }

    private void validateSavedAnalysis(AiAnalysis analysis) {
        if (analysis.getAverage() == null) {
            throw new IllegalStateException("Saved AI analysis has no average value");
        }
        if (analysis.getSummary() == null || analysis.getSummary().isBlank()) {
            throw new IllegalStateException("Saved AI analysis has no summary");
        }
        if (analysis.getSuggestions() == null || analysis.getSuggestions().isEmpty()) {
            throw new IllegalStateException("Saved AI analysis has no suggestions");
        }
    }
}
