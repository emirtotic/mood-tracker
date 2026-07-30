package com.moodTracker.repository;

import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    private static final String EMAIL = "emir@example.com";

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserAndConfirmThatEmailExists() {
        User savedUser = userRepository.saveAndFlush(user(EMAIL));

        assertThat(userRepository.findByEmail(EMAIL)).contains(savedUser);
        assertThat(userRepository.findUserByEmail(EMAIL)).contains(savedUser);
        assertThat(userRepository.existsByEmail(EMAIL)).isTrue();
    }

    @Test
    void shouldReturnEmptyResultForUnknownEmail() {
        assertThat(userRepository.findByEmail("unknown@example.com")).isEmpty();
        assertThat(userRepository.findUserByEmail("unknown@example.com")).isEmpty();
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    void shouldEnforceUniqueEmailConstraint() {
        userRepository.saveAndFlush(user(EMAIL));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user(EMAIL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email) {
        return User.builder()
                .firstName("Emir")
                .lastName("Totic")
                .email(email)
                .password("encoded-password")
                .enabled(true)
                .role(Role.USER)
                .build();
    }
}
