package com.ingredientapp.service;

import com.ingredientapp.model.Ingredient;
import com.ingredientapp.model.Recipe;
import com.ingredientapp.model.RecipeDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wraps calls to Spoonacular's "find by ingredients" endpoint (Chapter 3: API Integration Design).
 *
 * Real-call mode is used automatically once spoonacular.api.key is set (or the
 * SPOONACULAR_API_KEY environment variable). Until then, the service returns mock recipes
 * derived from whatever ingredients are currently in the session, so the front-end's Recipe
 * Results screen can be built and demonstrated before a Spoonacular account exists.
 */
@Service
public class SpoonacularService {

    private static final String SPOONACULAR_URL = "https://api.spoonacular.com/recipes/findByIngredients";
    private static final String SPOONACULAR_INFO_URL = "https://api.spoonacular.com/recipes/{id}/information";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spoonacular.api.key:}")
    private String apiKey;

    public boolean isMockMode() {
        return apiKey == null || apiKey.isBlank();
    }

    /**
     * Finds recipes ranked to maximise use of the supplied ingredients (ranking=2 favours
     * recipes that use more of the supplied ingredients and require fewer extra ones -
     * see Chapter 3's discussion of Spoonacular's ranking option).
     */
    public List<Recipe> findRecipes(List<Ingredient> ingredients) {
        if (isMockMode()) {
            return mockRecipes(ingredients);
        }
        return callSpoonacular(ingredients);
    }

    @SuppressWarnings("unchecked")
    private List<Recipe> callSpoonacular(List<Ingredient> ingredients) {
        String ingredientParam = ingredients.stream()
                .map(Ingredient::getName)
                .collect(Collectors.joining(","));

        String url = UriComponentsBuilder.fromHttpUrl(SPOONACULAR_URL)
                .queryParam("ingredients", ingredientParam)
                .queryParam("number", 5)
                .queryParam("ranking", 2)
                .queryParam("ignorePantry", true)
                .queryParam("apiKey", apiKey)
                .toUriString();

        Map[] response = restTemplate.getForObject(url, Map[].class);
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

    /**
     * Fetches full recipe detail (ingredient quantities + step-by-step instructions) for a
     * single recipe, called when a user taps a card on the Recipe Results screen. In mock
     * mode, a generic detail is synthesised from whatever ingredients are currently in the
     * session, since there is no real recipe ID to look up yet.
     */
    @SuppressWarnings("unchecked")
    public RecipeDetail getRecipeDetail(int id, List<Ingredient> sessionIngredients) {
        if (isMockMode()) {
            return mockRecipeDetail(id, sessionIngredients);
        }

        String url = UriComponentsBuilder.fromHttpUrl(SPOONACULAR_INFO_URL)
                .queryParam("includeNutrition", false)
                .queryParam("apiKey", apiKey)
                .buildAndExpand(id)
                .toUriString();

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
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
        // Fall back to the raw instructions field (plain-ish HTML) if Spoonacular didn't
        // return a step-by-step breakdown for this particular recipe.
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

    private RecipeDetail mockRecipeDetail(int id, List<Ingredient> sessionIngredients) {
        List<String> names = sessionIngredients.stream().map(Ingredient::getName).collect(Collectors.toList());
        String joined = String.join(" & ", names.stream().limit(2).collect(Collectors.toList()));

        List<String> ingredientLines = names.isEmpty()
                ? List.of("1 mystery ingredient")
                : names.stream().map(n -> "1 portion " + n).collect(Collectors.toList());

        List<String> instructionSteps = List.of(
                "Prepare and wash all your ingredients (" + (names.isEmpty() ? "whatever you've scanned" : String.join(", ", names)) + ").",
                "Combine everything in a pan or oven dish and season to taste.",
                "Cook until done, plate up, and enjoy your " + capitalise(joined) + "."
        );

        return new RecipeDetail(id, capitalise(joined) + " Bake", "https://via.placeholder.com/300x200?text=Recipe",
                2, 25, ingredientLines, instructionSteps);
    }

    private List<Recipe> mockRecipes(List<Ingredient> ingredients) {
        int have = ingredients.size();
        List<String> names = ingredients.stream().map(Ingredient::getName).collect(Collectors.toList());
        String joined = String.join(" & ", names.stream().limit(2).collect(Collectors.toList()));

        List<Recipe> recipes = new ArrayList<>();
        recipes.add(new Recipe(1, capitalise(joined) + " Bake", "https://via.placeholder.com/300x200?text=Recipe+1",
                Math.min(have, 4), Math.max(0, 5 - have)));
        recipes.add(new Recipe(2, capitalise(joined) + " Soup", "https://via.placeholder.com/300x200?text=Recipe+2",
                Math.min(have, 3), Math.max(0, 4 - have)));
        recipes.add(new Recipe(3, capitalise(joined) + " Stir Fry", "https://via.placeholder.com/300x200?text=Recipe+3",
                Math.min(have, 3), Math.max(0, 5 - have)));
        return recipes;
    }

    private String capitalise(String text) {
        if (text == null || text.isBlank()) {
            return "Mystery";
        }
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isBlank()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
