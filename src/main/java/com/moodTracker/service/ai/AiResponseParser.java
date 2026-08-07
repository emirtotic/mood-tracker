package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public ParsedAdvice parseAdvice(String responseBody) {
        String content = assistantContent(responseBody);
        String json = extractJson(content);

        if ("{}".equals(json)) {
            return null;
        }

        try {
            JsonNode result = objectMapper.readTree(json);
            String summary = result.path("summary").asText("").trim();
            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = result.path("suggestions");

            if (suggestionsNode.isArray()) {
                suggestionsNode.forEach(node -> suggestions.add(node.asText("")));
            }

            List<String> cleanedSuggestions = cleanSuggestions(suggestions);
            if (summary.isBlank() || cleanedSuggestions.isEmpty()) {
                return null;
            }

            return new ParsedAdvice(summary, cleanedSuggestions);
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter analysis response", e);
            return null;
        }
    }

    public String parseText(String responseBody) {
        String content = assistantContent(responseBody);
        return content.isBlank() ? null : content.trim();
    }

    private String assistantContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        try {
            JsonNode choices = objectMapper.readTree(responseBody).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }

            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText("").trim();
            }
            if (!content.isArray()) {
                return "";
            }

            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                String value = part.path("text").asText("");
                if (!value.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(value);
                }
            }
            return text.toString().trim();
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response envelope", e);
            return "";
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return "{}";
        }
        return content.substring(firstBrace, lastBrace + 1);
    }

    private List<String> cleanSuggestions(List<String> suggestions) {
        Set<String> unique = new LinkedHashSet<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && !suggestion.isBlank()) {
                unique.add(suggestion.trim());
            }
            if (unique.size() == 5) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }
}
