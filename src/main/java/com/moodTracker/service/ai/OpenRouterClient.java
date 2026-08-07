package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenRouterClient {

    private static final String MODEL = "openrouter/free";
    private static final int ANALYSIS_MAX_TOKENS = 700;
    private static final int PLAN_MAX_TOKENS = 1_200;
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 750L;
    private static final long MAX_RETRY_DELAY_MS = 5_000L;
    private static final int MAX_LOGGED_ERROR_BODY_LENGTH = 2_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiResponseParser responseParser;

    @Value("${openrouter.base-url}")
    private String baseUrl;

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.referer:}")
    private String referer;

    @Value("${openrouter.title:Mood Tracker}")
    private String title;

    public void validateConfiguration() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("OpenRouter base URL is not available");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is not available");
        }
    }

    public ParsedAdvice generateAnalysis(String prompt) {
        long retryDelay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            double temperature = attempt == 1 ? 0.4 : attempt == 2 ? 0.5 : 0.6;
            log.info("Requesting AI analysis with model {}, attempt {}/{}", MODEL, attempt, MAX_ATTEMPTS);

            CallResult result = execute(analysisRequest(prompt, temperature), "analysis");
            ParsedAdvice advice = responseParser.parseAdvice(result.body());
            if (advice != null) {
                return advice;
            }
            if (!result.retryable() || attempt == MAX_ATTEMPTS) {
                return null;
            }

            retryDelay = waitBeforeRetry(result.retryAfterMillis(), retryDelay);
        }
        return null;
    }

    public String generatePlan(String prompt) {
        long retryDelay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            double temperature = attempt == 1 ? 0.6 : attempt == 2 ? 0.7 : 0.8;
            log.info("Requesting AI plan with model {}, attempt {}/{}", MODEL, attempt, MAX_ATTEMPTS);

            CallResult result = execute(planRequest(prompt, temperature), "plan");
            String plan = responseParser.parseText(result.body());
            if (plan != null) {
                return plan;
            }
            if (!result.retryable() || attempt == MAX_ATTEMPTS) {
                return null;
            }

            retryDelay = waitBeforeRetry(result.retryAfterMillis(), retryDelay);
        }
        return null;
    }

    private ObjectNode analysisRequest(String prompt, double temperature) {
        ObjectNode request = baseRequest(temperature, ANALYSIS_MAX_TOKENS);
        request.putObject("response_format").put("type", "json_object");
        addMessage(request, "system", """
                Return only one valid JSON object.

                The object must contain:
                - "summary": a non-empty string
                - "suggestions": an array containing exactly five non-empty strings

                Do not include Markdown.
                Do not include code fences.
                Do not include any text outside the JSON object.
                """);
        addMessage(request, "user", prompt);
        return request;
    }

    private ObjectNode planRequest(String prompt, double temperature) {
        ObjectNode request = baseRequest(temperature, PLAN_MAX_TOKENS);
        addMessage(request, "system", """
                You are a supportive wellbeing coach.
                Return plain text only.
                Do not return JSON.
                Do not use code fences.
                """);
        addMessage(request, "user", prompt);
        return request;
    }

    private ObjectNode baseRequest(double temperature, int maxTokens) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", MODEL);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);
        request.putArray("messages");
        return request;
    }

    private void addMessage(ObjectNode request, String role, String content) {
        ObjectNode message = ((ArrayNode) request.get("messages")).addObject();
        message.put("role", role);
        message.put("content", content);
    }

    private CallResult execute(JsonNode requestBody, String operation) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    chatCompletionsUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers()),
                    String.class
            );
            log.info("OpenRouter {} request completed with status {}", operation, response.getStatusCode().value());
            logSelectedModel(operation, response.getBody());
            return new CallResult(response.getBody(), true, 0L);
        } catch (HttpStatusCodeException e) {
            HttpStatusCode status = e.getStatusCode();
            boolean retryable = isRetryable(status.value());
            long retryAfter = retryAfterMillis(e.getResponseHeaders());
            log.error("OpenRouter {} request failed. Status: {}, retryable: {}, response: {}",
                    operation, status.value(), retryable, truncate(e.getResponseBodyAsString()));
            return new CallResult(null, retryable, retryAfter);
        } catch (ResourceAccessException e) {
            log.error("OpenRouter {} request failed because of a connection or timeout error: {}",
                    operation, e.getMessage());
            return new CallResult(null, true, 0L);
        } catch (Exception e) {
            log.error("Unexpected OpenRouter {} request failure", operation, e);
            return new CallResult(null, false, 0L);
        }
    }

    private HttpHeaders headers() {
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

    private String chatCompletionsUrl() {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }

    private boolean isRetryable(int status) {
        return status == 408 || status == 429 || status == 500 || status == 502
                || status == 503 || status == 504 || status == 529;
    }

    private long retryAfterMillis(HttpHeaders responseHeaders) {
        if (responseHeaders == null) {
            return 0L;
        }
        String retryAfter = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0L;
        }
        try {
            return Math.min(Long.parseLong(retryAfter.trim()) * 1_000L, MAX_RETRY_DELAY_MS);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long waitBeforeRetry(long retryAfter, long fallbackDelay) {
        long delay = retryAfter > 0 ? retryAfter : fallbackDelay;
        delay = Math.min(delay, MAX_RETRY_DELAY_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenRouter retry wait was interrupted");
        }
        return Math.min(delay * 2, MAX_RETRY_DELAY_MS);
    }

    private void logSelectedModel(String operation, String responseBody) {
        if (!log.isInfoEnabled() || responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            String model = objectMapper.readTree(responseBody).path("model").asText("");
            if (!model.isBlank()) {
                log.info("OpenRouter selected model {} for {}", model, operation);
            }
        } catch (Exception ignored) {
            log.debug("Could not read selected model from OpenRouter response");
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank() || value.length() <= MAX_LOGGED_ERROR_BODY_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_LOGGED_ERROR_BODY_LENGTH) + "...[truncated]";
    }

    private record CallResult(String body, boolean retryable, long retryAfterMillis) {
    }
}
