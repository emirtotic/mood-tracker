package com.moodTracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Email @Size(max = 60) String email,
                           @NotBlank @Size(min = 8, max = 30) String password) {}
