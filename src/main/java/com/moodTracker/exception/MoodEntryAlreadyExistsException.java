package com.moodTracker.exception;

public class MoodEntryAlreadyExistsException extends RuntimeException {
    public MoodEntryAlreadyExistsException(String message) {
        super(message);
    }
}
