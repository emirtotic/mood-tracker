package com.moodTracker.service.impl;

import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import com.moodTracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceImplTest {

    private static final String EMAIL = "emir@example.com";
    private static final String PASSWORD = "encoded-password";

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsServiceImpl(userRepository);
    }

    @Test
    void shouldLoadUserDetailsByEmail() {
        User user = User.builder()
                .id(1L)
                .firstName("Emir")
                .lastName("Totic")
                .email(EMAIL)
                .password(PASSWORD)
                .enabled(true)
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername(EMAIL);

        assertThat(result.getUsername()).isEqualTo(EMAIL);
        assertThat(result.getPassword()).isEqualTo(PASSWORD);
        assertThat(result.getAuthorities()).isEmpty();
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonExpired()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.isCredentialsNonExpired()).isTrue();
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void shouldPassProvidedEmailToRepositoryWithoutModification() {
        String providedEmail = " Emir@Example.com ";
        User user = User.builder()
                .email(providedEmail)
                .password(PASSWORD)
                .build();
        when(userRepository.findByEmail(providedEmail)).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername(providedEmail);

        assertThat(result.getUsername()).isEqualTo(providedEmail);
        verify(userRepository).findByEmail(providedEmail);
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");

        verify(userRepository).findByEmail(EMAIL);
    }
}
