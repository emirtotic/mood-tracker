package com.moodTracker.controller;

import com.moodTracker.dto.MoodEntryRequest;
import com.moodTracker.dto.MoodEntryResponse;
import com.moodTracker.exception.BadRequestException;
import com.moodTracker.service.MoodEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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

    @PutMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    public MoodEntryResponse update(@AuthenticationPrincipal UserDetails principal,
                                    @RequestBody MoodEntryRequest req) {
        return service.update(principal.getUsername(), req);
    }

    @GetMapping("/date")
    public MoodEntryResponse getByDate(
            @AuthenticationPrincipal(expression = "username") String email,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.getEntryForDate(email, date);
    }

    @GetMapping("/range")
    public Page<MoodEntryResponse> getByDateRange(
            @AuthenticationPrincipal(expression = "username") String email,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @PageableDefault(size = 10, sort = "entryDate", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {

        if (start == null || end == null) {
            throw new BadRequestException("Parameters 'start' and 'end' are required.");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("'end' date must be the same as or after 'start' date.");
        }

        // limit range to prevent retrieving a lot of data in a bucket
        if (start.isBefore(LocalDate.now().minusYears(1))) {
            throw new BadRequestException("Date range too large. Max one year back.");
        }

        return service.getEntryForDateRange(email, start, end, pageable);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteById(
            @AuthenticationPrincipal(expression = "username") String email,
            @RequestParam("id") Long id
    ) {

        String result = service.deleteById(email, id);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }



    @GetMapping("/today")
    public MoodEntryResponse getToday(@AuthenticationPrincipal UserDetails principal) {
        return service.getToday(principal.getUsername());
    }
}
