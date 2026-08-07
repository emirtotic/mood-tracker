package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PromptFactory {

    private final ObjectMapper objectMapper;

    public String analysisPrompt(List<MoodEntryDto> entries) {
        return """
                Analyze the supplied mood logs.

                Instructions:
                - Detect the dominant language used in the notes.
                - Write the summary and every suggestion in that language.
                - Do not mix languages.
                - Base the analysis only on the supplied entries.
                - Do not make medical diagnoses.
                - The summary must contain no more than 120 words.
                - Return exactly five concrete suggestions.
                - Each suggestion must contain between 6 and 14 words.
                - Avoid generic, duplicated, or empty suggestions.

                Return only this JSON structure:

                {
                  "summary": "non-empty summary",
                  "suggestions": [
                    "suggestion one",
                    "suggestion two",
                    "suggestion three",
                    "suggestion four",
                    "suggestion five"
                  ]
                }

                Mood logs:
                %s
                """.formatted(entriesJson(entries));
    }

    public String planPrompt(AiAnalysis analysis) {
        String language = "en";
        int days = 7;
        String target = analysis.getAverage().doubleValue() >= 4.0 ? "maintain" : "improve";
        String suggestions = analysis.getSuggestions().stream()
                .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                .map(String::trim)
                .map(suggestion -> "• " + suggestion)
                .collect(Collectors.joining("\n"));

        if (suggestions.isBlank()) {
            throw new IllegalStateException("Saved AI analysis has no usable suggestions");
        }

        String dayLabel = "en".equalsIgnoreCase(language) ? "Day" : "Dan";

        return """
                Language: %s
                Horizon days: %d
                Target: %s

                Analysis:
                average = %.1f
                summary = %s
                suggestions:
                %s

                Task:
                Create a %d-day wellbeing plan as plain text in %s.
                Label sections from "%s 1" through "%s %d".

                Each day must contain:
                - 3 to 5 concrete and actionable bullet points
                - no more than 15 words per bullet point
                - one short reflection question

                Constraints:
                - Use the supplied analysis and suggestions as the backbone.
                - Keep the tone practical and supportive.
                - Do not provide medical diagnoses.
                - Do not use alarming or fear-based language.
                - When target is "maintain", focus on preserving good habits.
                - When target is "improve", focus on gentle recovery steps.
                - Return plain text only.
                - Do not return JSON.
                - Do not use code fences.
                - Do not include meta commentary.
                """.formatted(
                language, days, target, analysis.getAverage().doubleValue(),
                analysis.getSummary().trim(), suggestions, days, language,
                dayLabel, dayLabel, days
        );
    }

    private String entriesJson(List<MoodEntryDto> entries) {
        ArrayNode entriesArray = objectMapper.createArrayNode();

        for (MoodEntryDto entry : entries) {
            ObjectNode entryNode = entriesArray.addObject();
            entryNode.put("date", entry.getEntryDate().toString());
            entryNode.put("rating", entry.getMoodScore());

            if (entry.getNote() == null || entry.getNote().isBlank()) {
                entryNode.putNull("note");
            } else {
                entryNode.put("note", entry.getNote().trim());
            }
        }

        return entriesArray.toString();
    }
}
