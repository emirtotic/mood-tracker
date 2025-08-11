package com.moodTracker.controller;

import com.moodTracker.dto.AiPlan;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.entity.User;
import com.moodTracker.service.AiAdviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAnalyzerController {

    private final AiAdviceService aiAdviceService;

    @PostMapping("/plan")
    public AiPlan generatePlan(@AuthenticationPrincipal Object principal,
                               @RequestParam(name = "email", required = false) String email) {
        String resolved = resolveEmail(principal, email);
        if (resolved == null || resolved.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Authentication failed!");
        }
        return aiAdviceService.generatePlan(resolved);
    }


    @PostMapping("/analyze")
    public MoodEntryAiResponse analyzePost(@AuthenticationPrincipal Object principal,
                                           @RequestParam(name = "email", required = false) String email) {
        String resolved = resolveEmail(principal, email);
        if (resolved == null || resolved.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Authentication failed!");
        }
        return aiAdviceService.analyze(resolved);
    }

    private String resolveEmail(Object principal, String emailParam) {
        if (emailParam != null && !emailParam.isBlank()) return emailParam;

        if (principal instanceof User u && u.getEmail() != null) {
            return u.getEmail();
        }
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        return null;
    }
}
