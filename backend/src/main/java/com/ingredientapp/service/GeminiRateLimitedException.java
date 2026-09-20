package com.ingredientapp.service;

/**
 * Thrown when the Gemini API rejects a request with HTTP 429 (Too Many Requests) - in
 * practice, on the free tier, this almost always means the daily request quota (20/day at
 * the time of writing) has been exhausted rather than a genuine short-lived rate limit.
 *
 * This is kept as its own unchecked exception (rather than being folded into the generic
 * failure path in GeminiService.callGemini) so the controller can tell "Gemini is
 * temporarily unavailable/exhausted" apart from "Gemini looked and found nothing", and pass
 * that distinction on to the front-end as a distinct HTTP status instead of the two being
 * indistinguishable, misleading UI messages (see Chapter 6: robustness discussion).
 */
public class GeminiRateLimitedException extends RuntimeException {

    public GeminiRateLimitedException(String message) {
        super(message);
    }
}
