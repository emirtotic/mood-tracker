package com.moodTracker.controller;

import com.moodTracker.security.JwtService;
import com.moodTracker.security.JwtAuthenticationFilter;
import com.moodTracker.security.TokenBlacklistService;
import com.moodTracker.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldRegisterUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "Emir",
                                  "lastName": "Totic",
                                  "email": "emir@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("User registered successfully"));

        verify(userService).registerUser(any());
    }

    @Test
    void shouldRejectInvalidRegistrationRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "firstName": "E",
                                  "lastName": "",
                                  "email": "invalid-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void shouldLoginAndReturnJwt() throws Exception {
        when(userService.login(any())).thenReturn("generated-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "emir@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("generated-jwt"));
    }

    @Test
    @WithMockUser(username = "emir@example.com")
    void shouldChangePassword() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "new-password"
                                }
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).changePassword(eq("emir@example.com"), any());
    }

    @Test
    @WithMockUser(username = "emir@example.com")
    void shouldRejectInvalidChangePasswordRequest() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "currentPassword": "",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any());
    }

    @Test
    void shouldRevokeJwtOnLogout() throws Exception {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        Instant expiration = Instant.now()
                .plusSeconds(3600)
                .truncatedTo(ChronoUnit.MILLIS);
        when(jwtService.parseClaims("generated-jwt")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(Date.from(expiration));
        when(claims.getId()).thenReturn("token-id");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer generated-jwt"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(tokenBlacklistService).revoke("generated-jwt", "token-id", expiration);
    }

    @Test
    void shouldIgnoreMissingBearerTokenOnLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());

        verifyNoInteractions(jwtService, tokenBlacklistService);
    }
}
