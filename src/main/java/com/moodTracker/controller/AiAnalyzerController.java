package com.moodTracker.controller;

import com.moodTracker.dto.AiPlanResponse;
import com.moodTracker.dto.MoodEntryAiResponse;
import com.moodTracker.service.AiAdviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiAnalyzerController {

    private final AiAdviceService aiAdviceService;

    @PostMapping("/plan")
    public AiPlanResponse generatePlan(@AuthenticationPrincipal UserDetails principal,
                                       @RequestParam(name = "email", required = false) String email) {
        String resolved = resolveEmail(principal, email);
        if (resolved == null || resolved.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Authentication failed!");
        }
        return aiAdviceService.generatePlan(resolved);
    }


    @PostMapping("/analyze")
    public MoodEntryAiResponse analyzePost(@AuthenticationPrincipal UserDetails principal,
                                           @RequestParam(name = "email", required = false) String email) {
        String resolved = resolveEmail(principal, email);
        if (resolved == null || resolved.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Authentication failed!");
        }
        return aiAdviceService.analyze(resolved);
    }

    private String resolveEmail(UserDetails principal, String emailParam) {
        if (principal == null) {
            return null;
        }

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (isAdmin && emailParam != null && !emailParam.isBlank()) {
            return emailParam;
        }

        return principal.getUsername();
    }
}
