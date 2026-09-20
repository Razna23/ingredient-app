package com.ingredientapp.service;

import com.ingredientapp.model.Ingredient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps calls to the Clarifai Food Model API (see Chapter 3: API Integration Design).
 *
 * Real-call mode is used automatically once clarifai.api.key is set in application.properties
 * (or the CLARIFAI_API_KEY environment variable, e.g. on Render). Until then, the service runs
 * in mock mode so the rest of the system (session accumulation, recipe lookup, front-end flow)
 * can be built and tested without needing a Clarifai account yet.
 *
 * See README.md for exactly where to get a Clarifai API key and what to paste in.
 */
@Service
public class ClarifaiService {

    // Clarifai's public Food Model (general-availability food/ingredient recognition model).
    private static final String CLARIFAI_MODEL_ID = "bd367be194cf45149e75f01d59f77ba7";
    private static final String CLARIFAI_URL =
            "https://api.clarifai.com/v2/models/" + CLARIFAI_MODEL_ID + "/outputs";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${clarifai.api.key:}")
    private String apiKey;

    @Value("${clarifai.confidence.threshold:0.70}")
    private double confidenceThreshold;

    public boolean isMockMode() {
        return apiKey == null || apiKey.isBlank();
    }

    /**
     * Detects ingredients in a base64-encoded JPEG frame. Returns only concepts whose
     * confidence exceeds the configured threshold (Chapter 3: "Confidence-threshold filtering").
     */
    public List<Ingredient> detectIngredients(String base64Image) {
        if (isMockMode()) {
            return mockDetect();
        }
        return callClarifai(base64Image);
    }

    @SuppressWarnings("unchecked")
    private List<Ingredient> callClarifai(String base64Image) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Key " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "inputs", List.of(
                        Map.of("data", Map.of("image", Map.of("base64", base64Image)))
                )
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(CLARIFAI_URL, requestEntity, Map.class);

        List<Ingredient> results = new ArrayList<>();
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            return results;
        }

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) responseBody.get("outputs");
        Map<String, Object> data = (Map<String, Object>) outputs.get(0).get("data");
        List<Map<String, Object>> concepts = (List<Map<String, Object>>) data.get("concepts");

        for (Map<String, Object> concept : concepts) {
            String name = (String) concept.get("name");
            double value = ((Number) concept.get("value")).doubleValue();
            if (value >= confidenceThreshold) {
                results.add(new Ingredient(name, value));
            }
        }
        return results;
    }

    /**
     * Simulates a single camera scan detecting one or two plausible ingredients, so the
     * end-to-end flow (capture -> detect -> session -> recipes) can be exercised without a
     * real Clarifai key.
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
