package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptFactoryTest {

    private final PromptFactory promptFactory = new PromptFactory(new ObjectMapper());

    @Test
    void shouldCreateAnalysisPromptFromEntries() {
        MoodEntryDto entry = MoodEntryDto.builder()
                .entryDate(LocalDate.of(2026, 7, 29))
                .moodScore(4)
                .note("  Productive day  ")
                .build();

        String prompt = promptFactory.analysisPrompt(List.of(entry));

        assertThat(prompt)
                .contains("Analyze the supplied mood logs")
                .contains("exactly five concrete suggestions")
                .contains("2026-07-29", "Productive day", "\"rating\":4");
    }

    @Test
    void shouldCreatePlanPromptFromSavedAnalysis() {
        AiAnalysis analysis = AiAnalysis.builder()
                .average(BigDecimal.valueOf(4.2))
                .summary("Mood is positive.")
                .suggestions(List.of(" Keep a regular sleep schedule "))
                .build();

        String prompt = promptFactory.planPrompt(analysis);

        assertThat(prompt)
                .contains("Horizon days: 7")
                .contains("Target: maintain")
                .contains("• Keep a regular sleep schedule")
                .contains("Day 1", "Day 7");
    }

    @Test
    void shouldRejectPlanWithoutUsableSuggestions() {
        AiAnalysis analysis = AiAnalysis.builder()
                .average(BigDecimal.valueOf(3.0))
                .summary("Mood needs support.")
                .suggestions(List.of(" "))
                .build();

        assertThatThrownBy(() -> promptFactory.planPrompt(analysis))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Saved AI analysis has no usable suggestions");
    }
}
