package com.moodTracker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "mood_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","entry_date"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "mood_score", nullable = false)
    @Min(1)
    @Max(5)
    private int moodScore;

    @Column(name = "note")
    private String note;
}
