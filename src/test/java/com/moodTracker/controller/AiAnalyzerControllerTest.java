package com.moodTracker.controller;

import com.moodTracker.dto.AiPlanResponse;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.security.JwtAuthenticationFilter;
import com.moodTracker.service.AiAdviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiAnalyzerController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(username = "emir@example.com")
class AiAnalyzerControllerTest {

    private static final String EMAIL = "emir@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAdviceService aiAdviceService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldAnalyzeMoodEntriesForAuthenticatedUser() throws Exception {
        when(aiAdviceService.analyze(EMAIL)).thenReturn(new MoodEntryAiResponse(
                4.2,
                "Your recent mood pattern is positive.",
                List.of("Maintain a regular sleep schedule")
        ));

        mockMvc.perform(post("/ai/analyze").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(4.2))
                .andExpect(jsonPath("$.summary")
                        .value("Your recent mood pattern is positive."))
                .andExpect(jsonPath("$.suggestions[0]")
                        .value("Maintain a regular sleep schedule"));

        verify(aiAdviceService).analyze(EMAIL);
    }

    @Test
    void shouldIgnoreExplicitEmailParameterForRegularUser() throws Exception {
        String requestedEmail = "requested@example.com";
        when(aiAdviceService.analyze(EMAIL)).thenReturn(new MoodEntryAiResponse(
                3.5,
                "Your recent mood pattern is stable.",
                List.of("Take a short walk every afternoon")
        ));

        mockMvc.perform(post("/ai/analyze")
                        .with(user(EMAIL))
                        .param("email", requestedEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(3.5));

        verify(aiAdviceService).analyze(EMAIL);
        verify(aiAdviceService, never()).analyze(requestedEmail);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void shouldAllowAdminToAnalyzeExplicitUserEmail() throws Exception {
        String requestedEmail = "requested@example.com";
        when(aiAdviceService.analyze(requestedEmail)).thenReturn(new MoodEntryAiResponse(
                3.5,
                "Your recent mood pattern is stable.",
                List.of("Take a short walk every afternoon")
        ));

        mockMvc.perform(post("/ai/analyze")
                        .param("email", requestedEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(3.5));

        verify(aiAdviceService).analyze(requestedEmail);
    }

    @Test
    void shouldGeneratePlanForAuthenticatedUser() throws Exception {
        when(aiAdviceService.generatePlan(EMAIL))
                .thenReturn(new AiPlanResponse("Day 1\n- Take a short walk."));

        mockMvc.perform(post("/ai/plan").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Day 1\n- Take a short walk."));

        verify(aiAdviceService).generatePlan(EMAIL);
    }
}
