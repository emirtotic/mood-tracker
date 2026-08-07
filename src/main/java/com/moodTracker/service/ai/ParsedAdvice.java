package com.moodTracker.service.ai;

import java.util.List;

public record ParsedAdvice(String summary, List<String> suggestions) {
}
