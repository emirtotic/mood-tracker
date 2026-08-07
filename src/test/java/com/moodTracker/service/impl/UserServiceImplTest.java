package com.moodTracker.service.impl;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ChangePasswordRequest;
import com.moodTracker.exception.BadRequestException;
import com.moodTracker.entity.Role;
import com.moodTracker.entity.User;
import com.moodTracker.mapper.UserMapper;
import com.moodTracker.repository.UserRepository;
import com.moodTracker.security.JwtService;
import com.moodTracker.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Date;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String EMAIL = "emir@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userRepository,
                userMapper,
                passwordEncoder,
                authenticationManager,
                jwtService,
                tokenBlacklistService
        );
    }

    @Nested
    class RegisterUser {

        @Test
        void shouldRegisterNewUser() {
            RegisterRequest request = new RegisterRequest(
                    "Emir",
                    "Totic",
                    EMAIL,
                    RAW_PASSWORD
            );
            User mappedUser = new User();
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(userMapper.toEntity(request)).thenReturn(mappedUser);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

            service.registerUser(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue()).isSameAs(mappedUser);
            assertThat(userCaptor.getValue())
                    .extracting(
                            User::getFirstName,
                            User::getLastName,
                            User::getEmail,
                            User::getPassword,
                            User::isEnabled,
                            User::getRole
                    )
                    .containsExactly(
                            "Emir",
                            "Totic",
                            EMAIL,
                            ENCODED_PASSWORD,
                            true,
                            Role.USER
                    );
            verify(passwordEncoder).encode(RAW_PASSWORD);
        }

        @Test
        void shouldRejectEmailThatIsAlreadyInUse() {
            RegisterRequest request = new RegisterRequest(
                    "Emir",
                    "Totic",
                    EMAIL,
                    RAW_PASSWORD
            );
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> service.registerUser(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email already in use");

            verifyNoInteractions(userMapper, passwordEncoder);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Login {

        @Test
        void shouldAuthenticateUserAndReturnJwt() {
            LoginRequest request = new LoginRequest(EMAIL, RAW_PASSWORD);
            org.springframework.security.core.userdetails.User principal =
                    new org.springframework.security.core.userdetails.User(
                            EMAIL,
                            ENCODED_PASSWORD,
                            List.of()
                    );
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(jwtService.generateToken(principal)).thenReturn("generated-jwt");
            User user = existingUser();
            Instant expiration = Instant.now().plusSeconds(3600);
            Date expirationDate = Date.from(expiration);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(jwtService.extractJti("generated-jwt")).thenReturn("token-id");
            when(jwtService.extractExpiration("generated-jwt")).thenReturn(expirationDate);

            String result = service.login(request);

            assertThat(result).isEqualTo("generated-jwt");

            ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().getPrincipal()).isEqualTo(EMAIL);
            assertThat(tokenCaptor.getValue().getCredentials()).isEqualTo(RAW_PASSWORD);
            verify(jwtService).generateToken(principal);
            verify(tokenBlacklistService).registerIssuedToken(
                    "generated-jwt", "token-id", expirationDate.toInstant(), user);
        }

        @Test
        void shouldPropagateAuthenticationFailure() {
            LoginRequest request = new LoginRequest(EMAIL, "wrong-password");
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> service.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Bad credentials");

            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void shouldChangePasswordWhenCurrentPasswordIsCorrect() {
            ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, "new-password");
            User user = existingUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(passwordEncoder.matches("new-password", ENCODED_PASSWORD)).thenReturn(false);
            when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");

            service.changePassword(EMAIL, request);

            assertThat(user.getPassword()).isEqualTo("new-encoded-password");
            verify(passwordEncoder).encode("new-password");
            verify(userRepository).save(user);
        }

        @Test
        void shouldThrowWhenChangingPasswordForUnknownUser() {
            ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, "new-password");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changePassword(EMAIL, request))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessage("User not found");

            verifyNoInteractions(passwordEncoder);
            verify(userRepository, never()).save(any());
        }

        @Test
        void shouldRejectIncorrectCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest("wrong-password", "new-password");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
            when(passwordEncoder.matches("wrong-password", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(EMAIL, request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Current password is incorrect");

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        void shouldRejectReusingCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest(RAW_PASSWORD, RAW_PASSWORD);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(EMAIL, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("New password must be different from current password");

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any());
        }

    }

    private User existingUser() {
        return User.builder()
                .id(1L)
                .firstName("Emir")
                .lastName("Totic")
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .enabled(true)
                .role(Role.USER)
                .build();
    }
}
