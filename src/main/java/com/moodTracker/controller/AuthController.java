package com.moodTracker.controller;

import com.moodTracker.dto.JwtResponse;
import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ResetPasswordRequest;
import com.moodTracker.security.JwtService;
import com.moodTracker.security.TokenBlacklistService;
import com.moodTracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request);
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ResetPasswordRequest request) {
        String token = userService.changePassword(request);
        return ResponseEntity.ok(new JwtResponse(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            var claims = jwtService.parseClaims(token);
            var exp = claims.getExpiration().toInstant();
            tokenBlacklistService.revoke(token, claims.getId(), exp);
        }
        return ResponseEntity.noContent().build();
    }

}

