package com.ingredientapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingredientapp.model.Ingredient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps calls to Google's Gemini API (multimodal image understanding) for ingredient
 * detection. This replaces the originally planned Clarifai Food Model integration
 * (see Chapter 3 / Chapter 4: this substitution is documented as an implementation
 * challenge - Clarifai's API became unreachable from multiple independent networks during
 * development, and its sign-up flow hit an unrelated third-party billing (Stripe) error).
 *
 * Gemini has no dedicated "food model" the way Clarifai did, so this service instead sends
 * the captured frame to a general-purpose Gemini model with a prompt asking it to identify
 * cooking ingredients and return them as structured JSON, then parses that JSON into the same
 * Ingredient shape the rest of the system already expects. This keeps the Controller, session
 * model, and front-end completely unchanged - only this service's internals differ.
 *
 * Real-call mode is used automatically once gemini.api.key is set (or the GEMINI_API_KEY
 * environment variable, e.g. on Render). Until then, the service runs in mock mode so the rest
 * of the system can be built and tested without a Gemini account yet. See README.md for where
 * to get a free key (no credit card required, unlike Clarifai/Google Cloud Vision/Rekognition).
 */
@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions";

    private static final String PROMPT = "You are a food recognition assistant. Look at the attached photo and "
            + "identify every distinct raw or packaged cooking ingredient you can clearly see (for example: "
            + "\"onion\", \"tomato\", \"chicken breast\", \"cheese\"). Ignore prepared/cooked dishes, plates, "
            + "cutlery, and anything that is not a food ingredient.\n\n"
            + "Respond with ONLY a JSON array (no markdown, no code fences, no commentary) in exactly this "
            + "shape: [{\"name\": \"onion\", \"confidence\": 0.93}, {\"name\": \"tomato\", \"confidence\": 0.81}]\n\n"
            + "\"confidence\" is your own estimate, between 0 and 1, of how certain you are that ingredient is "
            + "genuinely present in the image. If you cannot identify any ingredients, respond with an empty "
            + "JSON array: []";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String model;

    @Value("${gemini.confidence.threshold:0.70}")
    private double confidenceThreshold;

    public boolean isMockMode() {
        return apiKey == null || apiKey.isBlank();
    }

    /**
     * Detects ingredients in a base64-encoded JPEG frame. Returns only ingredients whose
     * (self-reported) confidence exceeds the configured threshold, mirroring the
     * confidence-threshold filtering originally designed around Clarifai's concept scores.
     */
    public List<Ingredient> detectIngredients(String base64Image) {
        if (isMockMode()) {
            return mockDetect();
        }
        return callGemini(base64Image);
    }

    private List<Ingredient> callGemini(String base64Image) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-goog-api-key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image");
        imagePart.put("mime_type", "image/jpeg");
        imagePart.put("data", base64Image);

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", PROMPT);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("input", List.of(imagePart, textPart));
        requestBody.put("response_format", List.of(responseFormat));

        List<Ingredient> results = new ArrayList<>();
        try {
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);
            String responseJson;
            try {
                responseJson = restTemplate.postForObject(GEMINI_URL, requestEntity, String.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                // On the free tier this is almost always the 20-requests/day quota being
                // exhausted, not a transient rate limit. Surface this distinctly (instead of
                // returning an empty list) so the controller/front-end can tell the user the
                // real reason instead of "try a clearer angle".
                logger.warn("Gemini API quota/rate limit hit (model={}): {}", model, e.getMessage());
                throw new GeminiRateLimitedException(
                        "The Gemini API daily free-tier quota has been used up. Try again later.");
            } catch (RestClientException e) {
                // Network failure, timeout, or another non-2xx HTTP response (bad/expired API
                // key, wrong model name, etc). Logged so a "no ingredients detected" scan can
                // be told apart from a genuinely failed API call in Render's logs.
                logger.error("Gemini API call failed (model={}): {}", model, e.getMessage(), e);
                return results;
            }
            if (responseJson == null) {
                logger.warn("Gemini API returned a null response body (model={})", model);
                return results;
            }

            String modelText = extractModelText(responseJson);
            if (modelText == null || modelText.isBlank()) {
                logger.warn("Gemini response had no usable model_output text. Raw response: {}", responseJson);
                return results;
            }

            JsonNode ingredientsNode = objectMapper.readTree(stripCodeFences(modelText));
            if (ingredientsNode.isArray()) {
                for (JsonNode item : ingredientsNode) {
                    String name = item.path("name").asText(null);
                    double confidence = item.path("confidence").asDouble(0.0);
                    if (name != null && !name.isBlank() && confidence >= confidenceThreshold) {
                        results.add(new Ingredient(name, confidence));
                    } else if (name != null && !name.isBlank()) {
                        logger.info("Gemini saw '{}' but below confidence threshold ({} < {})",
                                name, confidence, confidenceThreshold);
                    }
                }
                if (results.isEmpty()) {
                    logger.info("Gemini returned {} candidate(s), none met the confidence threshold. Model text: {}",
                            ingredientsNode.size(), modelText);
                }
            } else {
                logger.warn("Gemini model_output was not a JSON array as requested. Model text: {}", modelText);
            }
        } catch (GeminiRateLimitedException e) {
            // Let this propagate up to the controller unchanged - it must NOT be swallowed by
            // the generic catch below, or the quota-exhausted case would silently collapse
            // back into an empty "no ingredients detected" result.
            throw e;
        } catch (Exception e) {
            // Gemini occasionally deviates from the requested JSON-only format (malformed JSON,
            // unexpected shape); fail closed (no ingredients detected for this scan) rather than
            // crashing the request, but log the real cause so it's visible in Render's logs
            // instead of being indistinguishable from a genuine "nothing recognized" result.
            logger.error("Failed to parse Gemini response (model={}): {}", model, e.getMessage(), e);
            results.clear();
        }
        return results;
    }

    /**
     * Navigates the Interactions API response (an "interaction" resource with a "steps"
     * timeline) to find the model's final text output.
     */
    private String extractModelText(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode steps = root.path("steps");
        String lastText = null;
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText())) {
                    continue;
                }
                for (JsonNode contentItem : step.path("content")) {
                    if ("text".equals(contentItem.path("type").asText())) {
                        lastText = contentItem.path("text").asText(null);
                    }
                }
            }
        }
        return lastText;
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    /**
     * Simulates a single camera scan detecting one or two plausible ingredients, so the
     * end-to-end flow (capture -> detect -> session -> recipes) can be exercised without a
     * real Gemini key.
     */
    private List<Ingredient> mockDetect() {
        List<Ingredient> pool = List.of(
                new Ingredient("onion", 0.93),
                new Ingredient("tomato", 0.89),
                new Ingredient("garlic", 0.85),
                new Ingredient("cheese", 0.81),
                new Ingredient("chicken breast", 0.78),
                new Ingredient("bell pepper", 0.74),
                new Ingredient("potato", 0.71),
                new Ingredient("carrot", 0.69) // deliberately below the default 0.70 threshold
        );
        List<Ingredient> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
        int count = 1 + ThreadLocalRandom.current().nextInt(2); // 1 or 2 per scan
        List<Ingredient> detected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Ingredient candidate = shuffled.get(i);
            if (candidate.getConfidence() >= confidenceThreshold) {
                detected.add(candidate);
            }
        }
        return detected;
    }
}
