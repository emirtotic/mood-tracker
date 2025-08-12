package com.moodTracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Size(min = 2, max = 30) String firstName,
                              @NotBlank @Size(min = 2, max = 30) String lastName,
                              @NotBlank @Email @Size(max = 60) String email,
                              @NotBlank @Size(min = 8, max = 30) String password) {}

