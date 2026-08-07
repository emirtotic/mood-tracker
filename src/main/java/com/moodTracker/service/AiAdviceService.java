package com.moodTracker.service;

import com.moodTracker.dto.AiPlanResponse;
import com.moodTracker.dto.MoodEntryAiResponse;

public interface AiAdviceService {

    MoodEntryAiResponse analyze(String email);
    AiPlanResponse generatePlan(String email);
}
