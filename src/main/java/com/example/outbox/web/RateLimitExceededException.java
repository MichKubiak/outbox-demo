package com.example.outbox.web;

public class RateLimitExceededException extends RuntimeException {

    private final long secondsToWait;

    public RateLimitExceededException(long secondsToWait) {
        super("Rate limit exceeded");
        this.secondsToWait = secondsToWait;
    }

    public long getSecondsToWait() {
        return secondsToWait;
    }
}
