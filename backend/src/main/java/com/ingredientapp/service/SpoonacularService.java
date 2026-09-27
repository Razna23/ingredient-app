package com.ingredientapp.service;

import com.ingredientapp.model.Ingredient;
import com.ingredientapp.model.Recipe;
import com.ingredientapp.model.RecipeDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/* Calls Spoonacular's "find by ingredients" API to get recipe suggestions. */
@Service
public class SpoonacularService {

    private static final String SPOONACULAR_URL = "https://api.spoonacular.com/recipes/findByIngredients";
    private static final String SPOONACULAR_INFO_URL = "https://api.spoonacular.com/recipes/{id}/information";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spoonacular.api.key:}")
    private String apiKey;

    /* Finds recipes that use as many of the given ingredients as possible. */
    public List<Recipe> findRecipes(List<Ingredient> ingredients) {
        return callSpoonacular(ingredients);
    }

    @SuppressWarnings("unchecked")
    private List<Recipe> callSpoonacular(List<Ingredient> ingredients) {
        String ingredientParam = ingredients.stream()
                .map(Ingredient::getName)
                .collect(Collectors.joining(","));

        String url = UriComponentsBuilder.fromHttpUrl(SPOONACULAR_URL)
                .queryParam("ingredients", ingredientParam)
                .queryParam("number", 30)
                .queryParam("ranking", 2)
                .queryParam("ignorePantry", true)
                .queryParam("apiKey", apiKey)
                .toUriString();

        Map[] response;
        try {
            response = restTemplate.getForObject(url, Map[].class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // free tier daily quota hit, same as Gemini's 429 handling
            throw new ApiRateLimitedException(
                    "The recipe search API's daily free-tier quota has been used up. Try again later.");
        }
        List<Recipe> recipes = new ArrayList<>();
        if (response == null) {
            return recipes;
        }
        for (Map<String, Object> item : response) {
            recipes.add(new Recipe(
                    ((Number) item.get("id")).intValue(),
                    (String) item.get("title"),
                    (String) item.get("image"),
                    ((Number) item.get("usedIngredientCount")).intValue(),
                    ((Number) item.get("missedIngredientCount")).intValue()
            ));
        }
        return recipes;
    }

    /* Gets full recipe info (ingredient amounts + steps) for one recipe. */
    @SuppressWarnings("unchecked")
    public RecipeDetail getRecipeDetail(int id, List<Ingredient> sessionIngredients) {
        String url = UriComponentsBuilder.fromHttpUrl(SPOONACULAR_INFO_URL)
                .queryParam("includeNutrition", false)
                .queryParam("apiKey", apiKey)
                .buildAndExpand(id)
                .toUriString();

        Map<String, Object> response;
        try {
            response = restTemplate.getForObject(url, Map.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ApiRateLimitedException(
                    "The recipe search API's daily free-tier quota has been used up. Try again later.");
        }
        if (response == null) {
            return null;
        }

        List<Map<String, Object>> extendedIngredients =
                (List<Map<String, Object>>) response.getOrDefault("extendedIngredients", List.of());
        List<String> ingredientLines = extendedIngredients.stream()
                .map(i -> (String) i.get("original"))
                .collect(Collectors.toList());

        List<String> instructionSteps = new ArrayList<>();
        List<Map<String, Object>> analyzedInstructions =
                (List<Map<String, Object>>) response.getOrDefault("analyzedInstructions", List.of());
        for (Map<String, Object> block : analyzedInstructions) {
            List<Map<String, Object>> steps = (List<Map<String, Object>>) block.getOrDefault("steps", List.of());
            for (Map<String, Object> step : steps) {
                instructionSteps.add((String) step.get("step"));
            }
        }
        // fallback if Spoonacular didn't give step-by-step instructions
        if (instructionSteps.isEmpty() && response.get("instructions") instanceof String raw && !raw.isBlank()) {
            instructionSteps.add(raw.replaceAll("<[^>]*>", ""));
        }

        Number servings = (Number) response.getOrDefault("servings", 0);
        Number readyInMinutes = (Number) response.getOrDefault("readyInMinutes", 0);

        return new RecipeDetail(
                id,
                (String) response.get("title"),
                (String) response.get("image"),
                servings.intValue(),
                readyInMinutes.intValue(),
                ingredientLines,
                instructionSteps
        );
    }

}
