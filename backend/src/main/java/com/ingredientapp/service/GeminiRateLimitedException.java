package com.ingredientapp.service;

/* Thrown when Gemini's free daily quota is used up (HTTP 429).
   Its own exception so the controller can send back a proper "quota used up"
   message instead of just saying no ingredients were found. */
public class GeminiRateLimitedException extends RuntimeException {

    public GeminiRateLimitedException(String message) {
        super(message);
    }
}
