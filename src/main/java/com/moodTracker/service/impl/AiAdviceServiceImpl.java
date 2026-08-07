package com.moodTracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moodTracker.dto.AiPlanResponse;
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
import com.moodTracker.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAdviceServiceImpl implements AiAdviceService {

    private static final String FREE_MODEL = "openrouter/free";

    private static final int ANALYSIS_MAX_TOKENS = 700;
    private static final int PLAN_MAX_TOKENS = 1_200;
    private static final int MAX_ATTEMPTS = 3;

    private static final long INITIAL_RETRY_DELAY_MS = 750L;
    private static final long MAX_RETRY_DELAY_MS = 5_000L;
    private static final int MAX_LOGGED_ERROR_BODY_LENGTH = 2_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MoodEntryService moodEntryService;
    private final UserRepository userRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    @Value("${openrouter.base-url}")
    private String baseUrl;

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.referer:}")
    private String referer;

    @Value("${openrouter.title:Mood Tracker}")
    private String title;

    @Override
    public MoodEntryAiResponse analyze(String email) {
        validateRequiredFields();

        User user = findUserByEmail(email);

        List<MoodEntryDto> entries = moodEntryService.getEntriesForDate(user.getEmail())
                .stream()
                .filter(entry -> entry != null && entry.getEntryDate() != null)
                .sorted(Comparator.comparing(MoodEntryDto::getEntryDate).reversed())
                .limit(30)
                .toList();

        if (entries.isEmpty()) {
            return new MoodEntryAiResponse(
                    0.0,
                    "No entries in last 30 days.",
                    List.of()
            );
        }

        double average = entries.stream()
                .mapToInt(MoodEntryDto::getMoodScore)
                .average()
                .orElse(0.0);

        double roundedAverage = BigDecimal.valueOf(average)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        log.info(
                "Analyzing {} mood entries for user {}",
                entries.size(),
                user.getEmail()
        );

        String entriesJson = createEntriesJson(entries);
        String prompt = createAnalysisPrompt(entriesJson);

        Advice advice = generateAdviceWithRetry(prompt);

        if (!isValidAdvice(advice)) {
            log.error(
                    "Mood analysis could not be generated for user {} after {} attempts",
                    user.getEmail(),
                    MAX_ATTEMPTS
            );

            return new MoodEntryAiResponse(
                    roundedAverage,
                    "",
                    List.of()
            );
        }

        String cleanedSummary = advice.summary().trim();
        List<String> cleanedSuggestions = cleanSuggestions(advice.suggestions());

        if (cleanedSuggestions.isEmpty()) {
            log.error(
                    "Mood analysis contained no usable suggestions for user {}",
                    user.getEmail()
            );

            return new MoodEntryAiResponse(
                    roundedAverage,
                    cleanedSummary,
                    List.of()
            );
        }

        saveOrUpdateAnalysis(
                user,
                roundedAverage,
                cleanedSummary,
                cleanedSuggestions
        );

        return new MoodEntryAiResponse(
                roundedAverage,
                cleanedSummary,
                cleanedSuggestions
        );
    }

    @Override
    public AiPlanResponse generatePlan(String email) {
        validateRequiredFields();

        User user = findUserByEmail(email);

        AiAnalysis analysis = aiAnalysisRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No saved AI analysis for user " + user.getId()
                ));

        validateSavedAnalysis(analysis);

        log.info("Generating AI plan for user {}", user.getEmail());

        String language = "en";
        int days = 7;
        String target = analysis.getAverage().doubleValue() >= 4.0
                ? "maintain"
                : "improve";

        String suggestions = analysis.getSuggestions()
                .stream()
                .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                .map(String::trim)
                .map(suggestion -> "• " + suggestion)
                .collect(Collectors.joining("\n"));

        if (suggestions.isBlank()) {
            throw new IllegalStateException(
                    "Saved AI analysis has no usable suggestions"
            );
        }

        String dayLabel = "en".equalsIgnoreCase(language) ? "Day" : "Dan";

        String prompt = """
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
                language,
                days,
                target,
                analysis.getAverage().doubleValue(),
                analysis.getSummary().trim(),
                suggestions,
                days,
                language,
                dayLabel,
                dayLabel,
                days
        );

        String planText = generatePlanWithRetry(prompt);

        if (planText == null || planText.isBlank()) {
            log.error(
                    "Plan generation failed for user {} after {} attempts",
                    user.getEmail(),
                    MAX_ATTEMPTS
            );

            throw new IllegalStateException(
                    "Plan generation temporarily unavailable. Please try again shortly."
            );
        }

        AiPlanResponse plan = new AiPlanResponse();
        plan.setResponse(planText.trim());

        log.info("AI plan generated successfully for user {}", user.getEmail());

        return plan;
    }

    private Advice generateAdviceWithRetry(String prompt) {
        long retryDelayMs = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            double temperature = switch (attempt) {
                case 1 -> 0.4;
                case 2 -> 0.5;
                default -> 0.6;
            };

            log.info(
                    "Requesting AI analysis with model {}, attempt {}/{}",
                    FREE_MODEL,
                    attempt,
                    MAX_ATTEMPTS
            );

            OpenRouterResult result = callOpenRouterForAnalysis(
                    prompt,
                    temperature,
                    ANALYSIS_MAX_TOKENS
            );

            if (result.advice() != null && isValidAdvice(result.advice())) {
                return result.advice();
            }

            if (!result.retryable() || attempt == MAX_ATTEMPTS) {
                return null;
            }

            retryDelayMs = resolveRetryDelay(result.retryAfterMillis(), retryDelayMs);
            sleepBeforeRetry(retryDelayMs);
            retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_DELAY_MS);
        }

        return null;
    }

    private OpenRouterResult callOpenRouterForAnalysis(
            String prompt,
            double temperature,
            int maxTokens
    ) {
        ObjectNode requestBody = createBaseRequestBody(
                temperature,
                maxTokens
        );

        ObjectNode responseFormat = requestBody.putObject("response_format");
        responseFormat.put("type", "json_object");

        addMessage(
                requestBody,
                "system",
                """
                Return only one valid JSON object.

                The object must contain:
                - "summary": a non-empty string
                - "suggestions": an array containing exactly five non-empty strings

                Do not include Markdown.
                Do not include code fences.
                Do not include any text outside the JSON object.
                """
        );

        addMessage(requestBody, "user", prompt);

        HttpCallResult httpResult = executeOpenRouterRequest(
                requestBody,
                "analysis"
        );

        if (httpResult.body() == null || httpResult.body().isBlank()) {
            return new OpenRouterResult(
                    null,
                    httpResult.retryable(),
                    httpResult.retryAfterMillis()
            );
        }

        try {
            String assistantContent = extractAssistantContent(httpResult.body());

            if (assistantContent.isBlank()) {
                log.warn("OpenRouter returned empty assistant content for analysis");

                return new OpenRouterResult(
                        null,
                        true,
                        httpResult.retryAfterMillis()
                );
            }

            String json = extractJson(assistantContent);

            if ("{}".equals(json)) {
                log.warn("OpenRouter analysis response did not contain a JSON object");

                return new OpenRouterResult(
                        null,
                        true,
                        httpResult.retryAfterMillis()
                );
            }

            JsonNode result = objectMapper.readTree(json);

            String summary = result.path("summary")
                    .asText("")
                    .trim();

            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = result.path("suggestions");

            if (suggestionsNode.isArray()) {
                suggestionsNode.forEach(node -> {
                    String suggestion = node.asText("").trim();

                    if (!suggestion.isBlank()) {
                        suggestions.add(suggestion);
                    }
                });
            }

            Advice advice = new Advice(summary, suggestions);

            return new OpenRouterResult(
                    isValidAdvice(advice) ? advice : null,
                    true,
                    httpResult.retryAfterMillis()
            );

        } catch (Exception e) {
            log.error("Failed to parse OpenRouter analysis response", e);

            return new OpenRouterResult(
                    null,
                    true,
                    httpResult.retryAfterMillis()
            );
        }
    }

    private String generatePlanWithRetry(String prompt) {
        long retryDelayMs = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            double temperature = switch (attempt) {
                case 1 -> 0.6;
                case 2 -> 0.7;
                default -> 0.8;
            };

            log.info(
                    "Requesting AI plan with model {}, attempt {}/{}",
                    FREE_MODEL,
                    attempt,
                    MAX_ATTEMPTS
            );

            TextResult result = callOpenRouterForText(
                    prompt,
                    temperature,
                    PLAN_MAX_TOKENS
            );

            if (result.text() != null && !result.text().isBlank()) {
                return result.text().trim();
            }

            if (!result.retryable() || attempt == MAX_ATTEMPTS) {
                return null;
            }

            retryDelayMs = resolveRetryDelay(result.retryAfterMillis(), retryDelayMs);
            sleepBeforeRetry(retryDelayMs);
            retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_DELAY_MS);
        }

        return null;
    }

    private TextResult callOpenRouterForText(
            String prompt,
            double temperature,
            int maxTokens
    ) {
        ObjectNode requestBody = createBaseRequestBody(
                temperature,
                maxTokens
        );

        addMessage(
                requestBody,
                "system",
                """
                You are a supportive wellbeing coach.
                Return plain text only.
                Do not return JSON.
                Do not use code fences.
                """
        );

        addMessage(requestBody, "user", prompt);

        HttpCallResult httpResult = executeOpenRouterRequest(
                requestBody,
                "plan"
        );

        if (httpResult.body() == null || httpResult.body().isBlank()) {
            return new TextResult(
                    null,
                    httpResult.retryable(),
                    httpResult.retryAfterMillis()
            );
        }

        String assistantContent = extractAssistantContent(httpResult.body());

        if (assistantContent.isBlank()) {
            log.warn("OpenRouter returned empty assistant content for plan");

            return new TextResult(
                    null,
                    true,
                    httpResult.retryAfterMillis()
            );
        }

        return new TextResult(
                assistantContent.trim(),
                false,
                0L
        );
    }

    private ObjectNode createBaseRequestBody(
            double temperature,
            int maxTokens
    ) {
        ObjectNode requestBody = objectMapper.createObjectNode();

        requestBody.put("model", FREE_MODEL);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.putArray("messages");

        return requestBody;
    }

    private void addMessage(
            ObjectNode requestBody,
            String role,
            String content
    ) {
        ArrayNode messages = (ArrayNode) requestBody.get("messages");

        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", content);
    }

    private HttpCallResult executeOpenRouterRequest(
            JsonNode requestBody,
            String operation
    ) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    createChatCompletionsUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, createHeaders()),
                    String.class
            );

            log.info(
                    "OpenRouter {} request completed with status {}",
                    operation,
                    response.getStatusCode().value()
            );

            logSelectedModel(operation, response.getBody());

            return new HttpCallResult(
                    response.getBody(),
                    false,
                    0L
            );

        } catch (HttpStatusCodeException e) {
            HttpStatusCode statusCode = e.getStatusCode();
            boolean retryable = isRetryableStatus(statusCode.value());
            long retryAfterMillis = extractRetryAfterMillis(e.getResponseHeaders());

            log.error(
                    "OpenRouter {} request failed. Status: {}, retryable: {}, response: {}",
                    operation,
                    statusCode.value(),
                    retryable,
                    truncateForLog(e.getResponseBodyAsString())
            );

            return new HttpCallResult(
                    null,
                    retryable,
                    retryAfterMillis
            );

        } catch (ResourceAccessException e) {
            log.error(
                    "OpenRouter {} request failed because of a connection or timeout error: {}",
                    operation,
                    e.getMessage()
            );

            return new HttpCallResult(
                    null,
                    true,
                    0L
            );

        } catch (Exception e) {
            log.error(
                    "Unexpected OpenRouter {} request failure",
                    operation,
                    e
            );

            return new HttpCallResult(
                    null,
                    false,
                    0L
            );
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        if (referer != null && !referer.isBlank()) {
            headers.add("HTTP-Referer", referer.trim());
        }

        if (title != null && !title.isBlank()) {
            headers.add("X-Title", title.trim());
        }

        return headers;
    }

    private String createChatCompletionsUrl() {
        String normalizedBaseUrl = baseUrl.trim();

        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(
                    0,
                    normalizedBaseUrl.length() - 1
            );
        }

        return normalizedBaseUrl + "/chat/completions";
    }

    private void saveOrUpdateAnalysis(
            User user,
            double roundedAverage,
            String summary,
            List<String> suggestions
    ) {
        Optional<AiAnalysis> previousAnalysis =
                aiAnalysisRepository.findByUserId(user.getId());

        AiAnalysis analysis = previousAnalysis.orElseGet(
                () -> AiAnalysis.builder()
                        .user(user)
                        .build()
        );

        analysis.setAverage(BigDecimal.valueOf(roundedAverage));
        analysis.setSummary(summary);
        analysis.setSuggestions(new ArrayList<>(suggestions));
        analysis.setCreatedAt(LocalDateTime.now());

        aiAnalysisRepository.save(analysis);

        log.info(
                "{} AI analysis for user {}",
                previousAnalysis.isPresent() ? "Updated" : "Created",
                user.getEmail()
        );
    }

    private String createEntriesJson(List<MoodEntryDto> entries) {
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

    private String createAnalysisPrompt(String entriesJson) {
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
                """.formatted(entriesJson);
    }

    private String extractAssistantContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("OpenRouter response does not contain choices");
                return "";
            }

            JsonNode content = choices.get(0)
                    .path("message")
                    .path("content");

            if (content.isTextual()) {
                return content.asText("").trim();
            }

            if (content.isArray()) {
                StringBuilder result = new StringBuilder();

                for (JsonNode part : content) {
                    String text = part.path("text").asText("");

                    if (!text.isBlank()) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }

                        result.append(text);
                    }
                }

                return result.toString().trim();
            }

            return "";

        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response envelope", e);
            return "";
        }
    }

    private static String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        String cleaned = content.trim();
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return "{}";
        }

        return cleaned.substring(firstBrace, lastBrace + 1);
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

    private void validateRequiredFields() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "OpenRouter base URL is not available"
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenRouter API key is not available"
            );
        }
    }

    private void validateSavedAnalysis(AiAnalysis analysis) {
        if (analysis.getAverage() == null) {
            throw new IllegalStateException(
                    "Saved AI analysis has no average value"
            );
        }

        if (analysis.getSummary() == null || analysis.getSummary().isBlank()) {
            throw new IllegalStateException(
                    "Saved AI analysis has no summary"
            );
        }

        if (analysis.getSuggestions() == null || analysis.getSuggestions().isEmpty()) {
            throw new IllegalStateException(
                    "Saved AI analysis has no suggestions"
            );
        }
    }

    private boolean isValidAdvice(Advice advice) {
        return advice != null
                && advice.summary() != null
                && !advice.summary().isBlank()
                && advice.suggestions() != null
                && advice.suggestions()
                .stream()
                .anyMatch(suggestion ->
                        suggestion != null && !suggestion.isBlank()
                );
    }

    private List<String> cleanSuggestions(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueSuggestions = new LinkedHashSet<>();

        for (String suggestion : suggestions) {
            if (suggestion == null) {
                continue;
            }

            String cleaned = suggestion.trim();

            if (!cleaned.isBlank()) {
                uniqueSuggestions.add(cleaned);
            }

            if (uniqueSuggestions.size() == 5) {
                break;
            }
        }

        return new ArrayList<>(uniqueSuggestions);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504
                || statusCode == 529;
    }

    private long extractRetryAfterMillis(HttpHeaders responseHeaders) {
        if (responseHeaders == null) {
            return 0L;
        }

        String retryAfter = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);

        if (retryAfter == null || retryAfter.isBlank()) {
            return 0L;
        }

        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.min(seconds * 1_000L, MAX_RETRY_DELAY_MS);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long resolveRetryDelay(
            long retryAfterMillis,
            long fallbackDelayMillis
    ) {
        if (retryAfterMillis > 0L) {
            return Math.min(retryAfterMillis, MAX_RETRY_DELAY_MS);
        }

        return Math.min(fallbackDelayMillis, MAX_RETRY_DELAY_MS);
    }

    private void sleepBeforeRetry(long delayMilliseconds) {
        try {
            Thread.sleep(delayMilliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenRouter retry wait was interrupted");
        }
    }

    private void logSelectedModel(
            String operation,
            String responseBody
    ) {
        if (!log.isInfoEnabled()
                || responseBody == null
                || responseBody.isBlank()) {
            return;
        }

        try {
            String selectedModel = objectMapper.readTree(responseBody)
                    .path("model")
                    .asText("");

            if (!selectedModel.isBlank()) {
                log.info(
                        "OpenRouter selected model {} for {}",
                        selectedModel,
                        operation
                );
            }
        } catch (Exception ignored) {
            log.debug(
                    "Could not read selected model from OpenRouter response"
            );
        }
    }

    private String truncateForLog(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (value.length() <= MAX_LOGGED_ERROR_BODY_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_LOGGED_ERROR_BODY_LENGTH)
                + "...[truncated]";
    }

    private record Advice(
            String summary,
            List<String> suggestions
    ) {
    }

    private record HttpCallResult(
            String body,
            boolean retryable,
            long retryAfterMillis
    ) {
    }

    private record OpenRouterResult(
            Advice advice,
            boolean retryable,
            long retryAfterMillis
    ) {
    }

    private record TextResult(
            String text,
            boolean retryable,
            long retryAfterMillis
    ) {
    }
}
