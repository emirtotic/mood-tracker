package com.moodTracker.service;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ResetPasswordRequest;

public interface UserService {

    void registerUser(RegisterRequest request);
    String login(LoginRequest request);
    String changePassword(ResetPasswordRequest request);
}
