package com.moodTracker.entity;

import com.moodTracker.config.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_analysis",
        uniqueConstraints = @UniqueConstraint(name = "uniq_ai_user", columnNames = "user_id"))
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ai_analysis_user"))
    private User user;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal average;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "json")
    private List<String> suggestions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        var now = LocalDateTime.now();
        createdAt = now;
    }
}
