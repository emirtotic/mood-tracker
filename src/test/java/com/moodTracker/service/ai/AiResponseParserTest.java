package com.moodTracker.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiResponseParser parser = new AiResponseParser(objectMapper);

    @Test
    void shouldParseAndCleanAnalysisResponse() throws Exception {
        String content = """
                ```json
                {
                  "summary": "  Mood is improving.  ",
                  "suggestions": [" Take a walk ", "Take a walk", "Call a friend"]
                }
                ```
                """;

        ParsedAdvice result = parser.parseAdvice(openRouterResponse(content));

        assertThat(result.summary()).isEqualTo("Mood is improving.");
        assertThat(result.suggestions()).containsExactly("Take a walk", "Call a friend");
    }

    @Test
    void shouldReturnNullForInvalidAnalysis() throws Exception {
        assertThat(parser.parseAdvice(openRouterResponse("not json"))).isNull();
        assertThat(parser.parseAdvice(openRouterResponse("{\"summary\":\"\",\"suggestions\":[]}")))
                .isNull();
    }

    @Test
    void shouldReadTextContentParts() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode content = root.putArray("choices").addObject()
                .putObject("message").putArray("content");
        content.addObject().put("text", "Day 1");
        content.addObject().put("text", "- Take a walk");

        assertThat(parser.parseText(objectMapper.writeValueAsString(root)))
                .isEqualTo("Day 1\n- Take a walk");
    }

    private String openRouterResponse(String content) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("choices").addObject().putObject("message").put("content", content);
        return objectMapper.writeValueAsString(root);
    }
}
