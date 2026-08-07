package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouterClientTest {

    private static final String BASE_URL = "https://openrouter.example/api/v1";

    @Mock private RestTemplate restTemplate;
    @Mock private AiResponseParser responseParser;

    private OpenRouterClient client;

    @BeforeEach
    void setUp() {
        client = new OpenRouterClient(restTemplate, new ObjectMapper(), responseParser);
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL + "/");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "referer", "https://mood-tracker.example");
        ReflectionTestUtils.setField(client, "title", "Mood Tracker");
    }

    @Test
    void shouldSendAnalysisRequestWithOpenRouterHeaders() {
        ParsedAdvice advice = new ParsedAdvice("Stable mood", List.of("Take a walk"));
        when(restTemplate.exchange(
                eq(BASE_URL + "/chat/completions"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("response body"));
        when(responseParser.parseAdvice("response body")).thenReturn(advice);

        assertThat(client.generateAnalysis("prompt")).isSameAs(advice);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(BASE_URL + "/chat/completions"), eq(HttpMethod.POST),
                captor.capture(), eq(String.class)
        );
        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer test-api-key");
        assertThat(captor.getValue().getHeaders().getFirst("HTTP-Referer"))
                .isEqualTo("https://mood-tracker.example");
        assertThat(captor.getValue().getBody().toString())
                .contains("\"model\":\"openrouter/free\"")
                .contains("\"response_format\"")
                .contains("prompt");
    }

    @Test
    void shouldReturnPlanParsedFromResponse() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("response body"));
        when(responseParser.parseText("response body")).thenReturn("Day 1\n- Take a walk");

        assertThat(client.generatePlan("plan prompt")).isEqualTo("Day 1\n- Take a walk");
    }

    @Test
    void shouldRejectMissingConfiguration() {
        ReflectionTestUtils.setField(client, "apiKey", " ");

        assertThatThrownBy(client::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenRouter API key is not available");
    }
}
