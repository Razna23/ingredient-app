package com.ingredientapp.controller;

import com.ingredientapp.dto.ImageRequest;
import com.ingredientapp.dto.ManualIngredientRequest;
import com.ingredientapp.model.Ingredient;
import com.ingredientapp.model.Recipe;
import com.ingredientapp.model.RecipeDetail;
import com.ingredientapp.service.GeminiRateLimitedException;
import com.ingredientapp.service.GeminiService;
import com.ingredientapp.service.SpoonacularService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller implementing the two endpoints described in Chapter 3 (Back-End Design),
 * plus a few small additions used by the front-end: GET /api/ingredients (so a page refresh can
 * redraw the list), DELETE /api/ingredients/{name} (FR5: manual removal of an incorrectly
 * detected ingredient), and POST /api/ingredients (manually typing in an ingredient Gemini
 * missed, or one the user already has on hand, without needing a camera scan at all).
 *
 * Ingredients are held per-session using Spring's built-in HttpSession (in-memory, no database
 * - see Figure 5: Session Data Model and the Design Decisions table in Chapter 3).
 */
@RestController
@RequestMapping("/api")
public class IngredientController {

    private static final String SESSION_ATTRIBUTE = "ingredients";

    private final GeminiService geminiService;
    private final SpoonacularService spoonacularService;

    public IngredientController(GeminiService geminiService, SpoonacularService spoonacularService) {
        this.geminiService = geminiService;
        this.spoonacularService = spoonacularService;
    }

    @PostMapping("/detect-ingredients")
    public ResponseEntity<?> detectIngredients(@RequestBody ImageRequest request, HttpSession session) {
        List<Ingredient> detected;
        try {
            detected = geminiService.detectIngredients(request.getImage());
        } catch (GeminiRateLimitedException e) {
            // Distinct HTTP status (429) so the front-end can tell "Gemini's quota is used up
            // for today" apart from a genuine "no ingredients recognized in this frame" result,
            // instead of both collapsing into the same misleading message.
            return ResponseEntity.status(429).body(Map.of(
                    "error", "gemini_rate_limited",
                    "message", e.getMessage()
            ));
        }
        List<Ingredient> sessionIngredients = getSessionIngredients(session);

        for (Ingredient newIngredient : detected) {
            boolean alreadyPresent = sessionIngredients.stream()
                    .anyMatch(existing -> existing.getName().equalsIgnoreCase(newIngredient.getName()));
            if (!alreadyPresent) {
                sessionIngredients.add(newIngredient);
            }
        }

        session.setAttribute(SESSION_ATTRIBUTE, sessionIngredients);
        return ResponseEntity.ok(sessionIngredients);
    }

    @GetMapping("/ingredients")
    public ResponseEntity<List<Ingredient>> getIngredients(HttpSession session) {
        return ResponseEntity.ok(getSessionIngredients(session));
    }

    @PostMapping("/ingredients")
    public ResponseEntity<?> addIngredient(@RequestBody ManualIngredientRequest request, HttpSession session) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Ingredient name cannot be empty."
            ));
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }

        List<Ingredient> sessionIngredients = getSessionIngredients(session);
        boolean alreadyPresent = sessionIngredients.stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(name));
        if (!alreadyPresent) {
            // Confidence 1.0: the user typed this themselves, so there's no detection
            // uncertainty to represent - it's simply above every confidence threshold.
            sessionIngredients.add(new Ingredient(name, 1.0));
        }

        session.setAttribute(SESSION_ATTRIBUTE, sessionIngredients);
        return ResponseEntity.ok(sessionIngredients);
    }

    @DeleteMapping("/ingredients/{name}")
    public ResponseEntity<List<Ingredient>> removeIngredient(@PathVariable String name, HttpSession session) {
        List<Ingredient> sessionIngredients = getSessionIngredients(session);
        sessionIngredients.removeIf(i -> i.getName().equalsIgnoreCase(name));
        session.setAttribute(SESSION_ATTRIBUTE, sessionIngredients);
        return ResponseEntity.ok(sessionIngredients);
    }

    @GetMapping("/recipes")
    public ResponseEntity<?> getRecipes(HttpSession session) {
        List<Ingredient> sessionIngredients = getSessionIngredients(session);
        if (sessionIngredients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No ingredients in this session yet. Scan at least one ingredient first."
            ));
        }
        List<Recipe> recipes = spoonacularService.findRecipes(sessionIngredients);
        return ResponseEntity.ok(recipes);
    }

    @GetMapping("/recipes/{id}")
    public ResponseEntity<RecipeDetail> getRecipeDetail(@PathVariable int id, HttpSession session) {
        List<Ingredient> sessionIngredients = getSessionIngredients(session);
        RecipeDetail detail = spoonacularService.getRecipeDetail(id, sessionIngredients);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    @SuppressWarnings("unchecked")
    private List<Ingredient> getSessionIngredients(HttpSession session) {
        Object attribute = session.getAttribute(SESSION_ATTRIBUTE);
        if (attribute == null) {
            List<Ingredient> fresh = new ArrayList<>();
            session.setAttribute(SESSION_ATTRIBUTE, fresh);
            return fresh;
        }
        return (List<Ingredient>) attribute;
    }
}
