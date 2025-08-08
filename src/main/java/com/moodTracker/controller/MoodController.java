package com.moodTracker.controller;

import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.entity.User;
import com.moodTracker.service.MoodEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Date;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/moods")
public class MoodController {

    private final MoodEntryService service;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public MoodEntryResponse create(@AuthenticationPrincipal UserDetails principal,
                                         @RequestBody MoodEntryRequest req) {
        return service.create(principal.getUsername(), req);
    }

    @GetMapping("/date")
    public MoodEntryResponse getByDate(
            @AuthenticationPrincipal(expression = "username") String email,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.getEntryForDate(email, date);
    }


    @GetMapping("/today")
    public MoodEntryResponse getToday(@AuthenticationPrincipal UserDetails principal) {
        return service.getToday(principal.getUsername());
    }
}
