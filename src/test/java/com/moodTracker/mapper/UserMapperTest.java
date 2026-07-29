package com.moodTracker.mapper;

import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void shouldMapRegisterRequestToUser() {
        RegisterRequest request = new RegisterRequest(
                "Emir",
                "Totic",
                "emir@example.com",
                "password123"
        );

        User result = mapper.toEntity(request);

        assertThat(result)
                .extracting(
                        User::getFirstName,
                        User::getLastName,
                        User::getEmail,
                        User::getPassword
                )
                .containsExactly(
                        "Emir",
                        "Totic",
                        "emir@example.com",
                        "password123"
                );
        assertThat(result.getId()).isNull();
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getRole()).isNull();
    }

    @Test
    void shouldReturnNullForNullRequest() {
        assertThat(mapper.toEntity(null)).isNull();
    }
}
