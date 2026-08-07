package com.moodTracker.controller;

import com.moodTracker.dto.JwtResponse;
import com.moodTracker.dto.LoginRequest;
import com.moodTracker.dto.RegisterRequest;
import com.moodTracker.dto.ChangePasswordRequest;
import com.moodTracker.security.JwtService;
import com.moodTracker.security.TokenBlacklistService;
import com.moodTracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    @PostMapping(
            path = "/change-password",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserDetails principal,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
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
