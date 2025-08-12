package com.moodTracker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MoodEntryRequest(int moodScore,
                               @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
                               @NotBlank @Size(min = 3, max = 1000) String note) {}
