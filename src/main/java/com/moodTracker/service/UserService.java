package com.moodTracker.service;

import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.UserRegisterRequest;

public interface UserService {

    void registerUser(UserRegisterRequest request);
    String login(LoginRequest request);
}
