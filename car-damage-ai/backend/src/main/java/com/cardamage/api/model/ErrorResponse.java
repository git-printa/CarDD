package com.cardamage.api.model;

import java.time.Instant;

public class ErrorResponse {

    private String error;
    private String message;
    private Instant timestamp;

    public ErrorResponse(String error, String message, Instant timestamp) {
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getError() { return error; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
