package com.moodTracker.service;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ChangePasswordRequest;

public interface UserService {

    void registerUser(RegisterRequest request);
    String login(LoginRequest request);
    void changePassword(String authenticatedEmail, ChangePasswordRequest request);
}
