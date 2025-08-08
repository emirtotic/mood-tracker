package com.moodTracker.controller;

import com.moodTracker.dto.JwtResponse;
import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.UserRegisterRequest;
import com.moodTracker.entity.User;
import com.moodTracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String token = userService.login(request);
        return ResponseEntity.ok(new JwtResponse(token));
    }


    // login ćemo dodati odmah nakon JWT-a
}

