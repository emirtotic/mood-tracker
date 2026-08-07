package com.moodTracker.exception;

public class MoodEntryAlreadyExistsException extends ConflictException {
    public MoodEntryAlreadyExistsException(String message) {
        super(message);
    }
}
