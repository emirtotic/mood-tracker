package com.moodTracker.repository;

import com.moodTracker.entity.AiAnalysis;
import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AiAnalysisRepositoryTest {

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .firstName("Emir")
                .lastName("Totic")
                .email("emir@example.com")
                .password("encoded-password")
                .enabled(true)
                .role(Role.USER)
                .build());
    }

    @Test
    void shouldFindAnalysisByUserIdAndRestoreSuggestions() {
        AiAnalysis savedAnalysis = aiAnalysisRepository.saveAndFlush(analysis(
                "Your mood remained stable.",
                List.of(
                        "Maintain a regular sleep schedule",
                        "Take a short walk every afternoon"
                )
        ));

        AiAnalysis result = aiAnalysisRepository.findByUserId(user.getId()).orElseThrow();

        assertThat(result.getId()).isEqualTo(savedAnalysis.getId());
        assertThat(result.getAverage()).isEqualByComparingTo("3.8");
        assertThat(result.getSummary()).isEqualTo("Your mood remained stable.");
        assertThat(result.getSuggestions()).containsExactly(
                "Maintain a regular sleep schedule",
                "Take a short walk every afternoon"
        );
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReturnEmptyResultWhenUserHasNoAnalysis() {
        assertThat(aiAnalysisRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void shouldEnforceOneAnalysisPerUser() {
        aiAnalysisRepository.saveAndFlush(analysis(
                "First analysis.",
                List.of("Maintain a regular sleep schedule")
        ));

        assertThatThrownBy(() -> aiAnalysisRepository.saveAndFlush(analysis(
                "Second analysis.",
                List.of("Take a short walk every afternoon")
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private AiAnalysis analysis(String summary, List<String> suggestions) {
        return AiAnalysis.builder()
                .user(user)
                .average(BigDecimal.valueOf(3.8))
                .summary(summary)
                .suggestions(suggestions)
                .build();
    }
}
