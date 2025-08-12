package com.moodTracker.service;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;

public interface UserService {

    void registerUser(RegisterRequest request);
    String login(LoginRequest request);
}
