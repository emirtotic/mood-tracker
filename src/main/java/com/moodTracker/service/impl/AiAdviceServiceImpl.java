package com.moodTracker.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodTracker.dto.AiPlan;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.dto.MoodEntryDto;
import com.moodTracker.entity.AiAnalysis;
import com.moodTracker.entity.User;
import com.moodTracker.repository.AiAnalysisRepository;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.service.AiAdviceService;
import com.moodTracker.service.MoodEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdviceServiceImpl implements AiAdviceService {

    private final RestTemplate restTemplate;
    private final ObjectMapper om;
    private final MoodEntryService moodEntryService;
    private final UserRepository userRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    @Value("${openrouter.base-url}")
    private String baseUrl;

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.referer}")
    private String referer;

    @Value("${openrouter.title}")
    private String title;

    @Value("${openrouter.model}")
    private String model;

    @Value("${openrouter.fallback-model}")
    private String fallbackModel;

    @Override
    public MoodEntryAiResponse analyze(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<MoodEntryDto> entries = moodEntryService.getEntriesForDate(email).stream()
                .filter(e -> e.getEntryDate() != null)
                .sorted(Comparator.comparing(MoodEntryDto::getEntryDate).reversed())
                .limit(30)
                .toList();

        double avg = entries.stream().mapToInt(MoodEntryDto::getMoodScore).average().orElse(0.0);
        double avgRounded = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();

        if (entries.isEmpty()) {
            return new MoodEntryAiResponse(0.0, "No entries in last 30 days.", List.of());
        }

        log.info("Analyzing condition of the user: {} {}", user.getFirstName(), user.getLastName());

        // JSON payload for AI model
        String payload = entries.stream()
                .map(e -> String.format(Locale.ROOT,
                        "{\"date\":\"%s\",\"rating\":%d,\"note\":%s}",
                        e.getEntryDate(), e.getMoodScore(),
                        e.getNote() == null ? "null" : "\"" + e.getNote().replace("\"", "\\\"") + "\""))
                .reduce((a, b) -> a + ",\n" + b)
                .orElse("");

        // prompt + mini example (few-shot) + demand the STRICT JSON
        String prompt = """
                You are an assistant that outputs STRICT JSON only.
                Analyze the mood logs (array of {"date","rating","note"}). Output Serbian.
                Return ONLY a valid JSON object:
                {
                  "summary": "<summary in english language, max 120 words, without generic phrases>",
                  "suggestions": ["three concrete seps, 6–14 words each, without empty strings"]
                }
                
                Example input:
                [{"date":"2025-08-01","rating":2,"note":"Stress at work, not enough sleep"},
                 {"date":"2025-08-02","rating":4,"note":"Walk and hanging out with friends"}]
                Example output:
                        {
                          "summary": "Mood fluctuates; sleep and physical activity improve overall tone.",
                          "suggestions": [
                            "Set a fixed bedtime for 7–8 hours of sleep",
                            "Take a 10–15 minute walk after work",
                            "Record daily stress triggers and responses",
                            "Schedule a brief social activity twice a week",
                            "Practice a 5-minute breathing exercise each morning"
                          ]
                        }
                Now analyze these logs and produce JSON only:
                [%s]
                """.formatted(payload);

        // 1. attempt – better free model
        Advice adv = callOpenRouterOnce("meta-llama/llama-3.1-8b-instruct:free", prompt, 0.6, 500);
        // 2. fallback – second free model, if the first didn't respond well
        if (adv == null || adv.summary.isBlank() || adv.suggestions.stream().allMatch(String::isBlank)) {
            adv = callOpenRouterOnce("mistralai/mistral-7b-instruct:free", prompt, 0.7, 600);
        }
        // 3. Last shot with higher temperature of the same model to eliminate empty strings
        if (adv == null || adv.summary.isBlank() || adv.suggestions.stream().allMatch(String::isBlank)) {
            adv = callOpenRouterOnce("meta-llama/llama-3.1-8b-instruct:free", prompt, 0.9, 650);
        }

        if (adv == null) {
            log.error("Could not generate summary from Open AI");
            return new MoodEntryAiResponse(avgRounded, "", List.of());
        }

        // Remove empty or duplicated suggestions and trim it to 5
        List<String> cleaned = adv.suggestions.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(5)
                .toList();

        MoodEntryAiResponse moodEntryAiResponse = new MoodEntryAiResponse(avgRounded, adv.summary.trim(), cleaned);

        Optional<AiAnalysis> previousAdvice = aiAnalysisRepository.findByUserId(user.getId());

        if (previousAdvice.isEmpty()) {
            AiAnalysis response = AiAnalysis.builder()
                    .user(user)
                    .average(BigDecimal.valueOf(avgRounded))
                    .summary(adv.summary)
                    .suggestions(List.of(String.valueOf(cleaned)))
                    .createdAt(LocalDateTime.now())
                    .build();
            log.info("Creating the AI response for {}", user.getEmail());

            aiAnalysisRepository.save(response);
        } else {
            AiAnalysis existing = previousAdvice.get();
            existing.setAverage(BigDecimal.valueOf(avgRounded));
            existing.setSummary(adv.summary.trim());
            existing.setSuggestions(cleaned);
            existing.setCreatedAt(LocalDateTime.now());

            log.info("Updating AI response for {}", user.getEmail());
            aiAnalysisRepository.save(existing);
        }

        return moodEntryAiResponse;
    }

    @Override
    public AiPlan generatePlan(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        AiAnalysis a = aiAnalysisRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No saved AI analysis for user " + user.getId()));

        log.info("Generating the plan from Open AI for {} {}", user.getFirstName(), user.getLastName());

        String language = "en";
        int days = 7;

        String target = a.getAverage().doubleValue() >= 4.0 ? "maintain" : "improve";

        String bullets = a.getSuggestions().stream()
                .map(s -> "• " + s)
                .collect(java.util.stream.Collectors.joining("\n"));

        String dayLabel = "en".equalsIgnoreCase(language) ? "Day" : "Dan";

        String prompt = """
                Language: %s
                Horizon days: %d
                Target: %s  // maintain | improve
                
                Analysis:
                average = %.1f
                summary = %s
                suggestions:
                %s
                
                Task:
                Create a %d-day plan as PLAIN TEXT in %s, labeled "%s 1" to "%s %d".
                Each day must have 3–5 actionable bullet points (<= 15 words each) and ONE short reflection question.
                Constraints:
                - Use the suggestions above as backbone.
                - Practical, supportive tone. No medical diagnoses or alarms.
                - If target=maintain: focus on sustaining good habits. If improve: gentle recovery steps.
                - Output PLAIN TEXT only (no JSON, no code fences, no extra meta text).
                """.formatted(
                language, days, target,
                a.getAverage().doubleValue(), a.getSummary(), bullets,
                days, language, dayLabel, dayLabel, days
        );

        // candidates: primary + fallback from properties + extra :free models
        List<String> candidates = new ArrayList<>();
        if (model != null && !model.isBlank()) candidates.add(model.trim());
        if (fallbackModel != null && !fallbackModel.isBlank()) candidates.add(fallbackModel.trim());
        candidates.addAll(List.of(
                "meta-llama/llama-3.2-3b-instruct:free",
                "mistralai/mistral-7b-instruct:free",
                "qwen/qwen2.5-7b-instruct:free"
        ));

        // remove duplicates and empty spaces
        candidates = candidates.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();

        String planText = null;
        for (String m : candidates) {
            try {
                planText = callOpenRouterText(m, prompt, 0.8, 1100);

                if (planText != null && !planText.isBlank()) {
                    log.warn("Plan from AI is generated successfully...");
                } else {
                    break;
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                int sc = e.getStatusCode().value();
                if (sc == 404 || sc == 400 || sc == 429) {
                    log.warn("Response Status Code from AI is not 200. Trying with different model...");
                    continue;
                }
                throw e;
            } catch (org.springframework.web.client.ResourceAccessException timeout) {
                log.warn("Timeout for AI Response. Trying with different model...");
                continue;
            }
        }

        if (planText == null || planText.isBlank()) {
            log.error("Plan generation temporarily unavailable. Please try again shortly.");
            throw new IllegalStateException("Plan generation temporarily unavailable. Please try again shortly.");
        }

        AiPlan plan = new AiPlan();
        plan.setResponse(planText.trim());
        log.info("\nPlan generated: \n{}", plan.getResponse());
        return plan;
    }

    /* ===================== Helpers ===================== */

    private static class Advice {
        final String summary;
        final List<String> suggestions;

        Advice(String summary, List<String> suggestions) {
            this.summary = summary;
            this.suggestions = suggestions;
        }
    }

    @SuppressWarnings("unchecked") // I know I’m doing an unchecked conversion/cast here so don’t report a warning :)
    private Advice callOpenRouterOnce(String model, String prompt, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "Respond only with valid JSON object, without text outsife of JSON."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.add("HTTP-Referer", referer);
        headers.add("X-Title", title);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            String assistant = extractAssistantContent(resp.getBody());
            String json = extractJson(assistant);
            if (json == null || json.isBlank() || "{}".equals(json)) return null;

            JsonNode node = om.readTree(json);
            String summary = node.path("summary").asText("");
            List<String> suggestions = new ArrayList<>();
            JsonNode s = node.path("suggestions");
            if (s.isArray()) s.forEach(n -> {
                String v = n.asText("").trim();
                if (!v.isEmpty()) suggestions.add(v);
            });
            if (summary.isBlank() || suggestions.isEmpty()) return null;
            return new Advice(summary, suggestions);
        } catch (Exception e) {
            return null;
        }
    }

    private String callOpenRouterText(String model, String prompt, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are a supportive wellbeing coach. Output PLAIN TEXT only, no JSON, no code fences."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);
        h.add("HTTP-Referer", referer);
        h.add("X-Title", title);

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body, h),
                String.class
        );
        return extractAssistantContent(resp.getBody());
    }

    /**
     * Retrieve assistant message content from OpenRouter/OpenAI response.
     */
    private String extractAssistantContent(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            Map<String, Object> root = om.readValue(body, new TypeReference<>() {
            });
            List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
            if (choices == null || choices.isEmpty()) return "";
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return message == null ? "" : String.valueOf(message.getOrDefault("content", ""));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Remove ```json fence-ove and return contend of JSON object if exist.
     */
    private static String extractJson(String content) {
        if (content == null) return "{}";
        String c = content.trim();
        if (c.startsWith("```")) {
            int first = c.indexOf('{');
            int last = c.lastIndexOf('}');
            if (first >= 0 && last > first) return c.substring(first, last + 1);
        }
        int first = c.indexOf('{');
        int last = c.lastIndexOf('}');
        return (first >= 0 && last > first) ? c.substring(first, last + 1) : "{}";
    }
}
