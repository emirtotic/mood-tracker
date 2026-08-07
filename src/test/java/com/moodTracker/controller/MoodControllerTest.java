package com.moodTracker.controller;

import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.exception.MoodEntryAlreadyExistsException;
import com.moodTracker.security.JwtAuthenticationFilter;
import com.moodTracker.service.MoodEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MoodController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(username = "emir@example.com")
class MoodControllerTest {

    private static final String EMAIL = "emir@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MoodEntryService moodEntryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldCreateMoodEntryForAuthenticatedUser() throws Exception {
        when(moodEntryService.create(eq(EMAIL), any()))
                .thenReturn(new MoodEntryResponse(10L, "2026-07-20", 4, "A productive day"));

        mockMvc.perform(post("/api/moods/create")
                        .with(user(EMAIL))
                        .contentType("application/json")
                        .content("""
                                {
                                  "moodScore": 4,
                                  "date": "2026-07-20",
                                  "note": "A productive day"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.date").value("2026-07-20"))
                .andExpect(jsonPath("$.moodScore").value(4))
                .andExpect(jsonPath("$.note").value("A productive day"));

        verify(moodEntryService).create(eq(EMAIL), any());
    }

    @Test
    void shouldRejectMoodEntryWithBlankNote() throws Exception {
        mockMvc.perform(post("/api/moods/create")
                        .with(user(EMAIL))
                        .contentType("application/json")
                        .content("""
                                {
                                  "moodScore": 4,
                                  "date": "2026-07-20",
                                  "note": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(moodEntryService);
    }

    @Test
    void shouldReturnConflictForDuplicateMoodEntry() throws Exception {
        when(moodEntryService.create(eq(EMAIL), any()))
                .thenThrow(new MoodEntryAlreadyExistsException(
                        "Mood entry for 2026-07-20 already exists"
                ));

        mockMvc.perform(post("/api/moods/create")
                        .with(user(EMAIL))
                        .contentType("application/json")
                        .content("""
                                {
                                  "moodScore": 4,
                                  "date": "2026-07-20",
                                  "note": "A productive day"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message")
                        .value("Mood entry for 2026-07-20 already exists"));
    }

    @Test
    void shouldUpdateMoodEntry() throws Exception {
        when(moodEntryService.update(eq(EMAIL), any()))
                .thenReturn(new MoodEntryResponse(10L, "2026-07-20", 5, "An excellent day"));

        mockMvc.perform(put("/api/moods/update")
                        .with(user(EMAIL))
                        .contentType("application/json")
                        .content("""
                                {
                                  "moodScore": 5,
                                  "date": "2026-07-20",
                                  "note": "An excellent day"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodScore").value(5))
                .andExpect(jsonPath("$.note").value("An excellent day"));
    }

    @Test
    void shouldReturnMoodEntryForDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 20);
        when(moodEntryService.getEntryForDate(EMAIL, date))
                .thenReturn(new MoodEntryResponse(10L, date.toString(), 4, "A productive day"));

        mockMvc.perform(get("/api/moods/date")
                        .with(user(EMAIL))
                        .param("date", "2026-07-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.date").value("2026-07-20"));
    }

    @Test
    void shouldReturnPagedMoodEntriesForDateRange() throws Exception {
        when(moodEntryService.getEntryForDateRange(
                eq(EMAIL),
                eq(LocalDate.of(2026, 7, 1)),
                eq(LocalDate.of(2026, 7, 31)),
                any()
        )).thenReturn(new PageImpl<>(List.of(
                new MoodEntryResponse(10L, "2026-07-20", 4, "A productive day")
        )));

        mockMvc.perform(get("/api/moods/range")
                        .with(user(EMAIL))
                        .param("start", "2026-07-01")
                        .param("end", "2026-07-31")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].note").value("A productive day"));
    }

    @Test
    void shouldDeleteMoodEntry() throws Exception {
        when(moodEntryService.deleteById(EMAIL, 10L))
                .thenReturn("Entry has been deleted.");

        mockMvc.perform(delete("/api/moods/delete")
                        .with(user(EMAIL))
                        .param("id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Entry has been deleted."));

        verify(moodEntryService).deleteById(EMAIL, 10L);
    }

    @Test
    void shouldReturnTodaysMoodEntry() throws Exception {
        when(moodEntryService.getToday(EMAIL))
                .thenReturn(new MoodEntryResponse(10L, "2026-07-29", 4, "A productive day"));

        mockMvc.perform(get("/api/moods/today").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-07-29"))
                .andExpect(jsonPath("$.moodScore").value(4));
    }
}
