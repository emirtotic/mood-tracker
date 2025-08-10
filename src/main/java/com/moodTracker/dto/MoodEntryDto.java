package com.moodTracker.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoodEntryDto {

    private Long id;
    private Long userId;
    private LocalDate entryDate;
    private int moodScore;
    private String note;
}
