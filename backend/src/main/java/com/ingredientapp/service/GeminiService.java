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

/* Sends a photo to Gemini and asks it to identify the ingredients in it.
   Runs in mock mode until gemini.api.key (or GEMINI_API_KEY on Render) is set. */
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

    @Value("${gemini.model:gemini-3.1-flash-lite}")
    private String model;

    @Value("${gemini.confidence.threshold:0.70}")
    private double confidenceThreshold;

    public boolean isMockMode() {
        return apiKey == null || apiKey.isBlank();
    }

    /* Detects ingredients in a base64 JPEG. Only keeps ones above the confidence threshold. */
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
                // free tier daily quota hit, not a normal rate limit
                logger.warn("Gemini API quota/rate limit hit (model={}): {}", model, e.getMessage());
                throw new GeminiRateLimitedException(
                        "The Gemini API daily free-tier quota has been used up. Try again later.");
            } catch (RestClientException e) {
                // network error or bad API call, log it and return nothing
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
            // rethrow, don't let this get swallowed by the catch below
            throw e;
        } catch (Exception e) {
            // Gemini didn't return valid JSON, just log it and return nothing
            logger.error("Failed to parse Gemini response (model={}): {}", model, e.getMessage(), e);
            results.clear();
        }
        return results;
    }

    /* Digs the model's text output out of the API response. */
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

    /* Fakes a scan result so the app works without a real Gemini key. */
    private List<Ingredient> mockDetect() {
        List<Ingredient> pool = List.of(
                new Ingredient("onion", 0.93),
                new Ingredient("tomato", 0.89),
                new Ingredient("garlic", 0.85),
                new Ingredient("cheese", 0.81),
                new Ingredient("chicken breast", 0.78),
                new Ingredient("bell pepper", 0.74),
                new Ingredient("potato", 0.71),
                new Ingredient("carrot", 0.69) // below the default threshold on purpose
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
