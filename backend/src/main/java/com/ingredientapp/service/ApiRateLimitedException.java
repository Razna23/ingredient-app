package com.ingredientapp.service;

/* Thrown when an external API's free daily quota is used up (HTTP 429).
   Shared by GeminiService and SpoonacularService so the controller can send
   back a proper "quota used up" message instead of a generic failure. */
public class ApiRateLimitedException extends RuntimeException {

    public ApiRateLimitedException(String message) {
        super(message);
    }
}
