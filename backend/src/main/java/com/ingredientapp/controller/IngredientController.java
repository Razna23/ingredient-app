package com.ingredientapp.controller;

import com.ingredientapp.dto.ImageRequest;
import com.ingredientapp.model.Ingredient;
import com.ingredientapp.model.Recipe;
import com.ingredientapp.model.RecipeDetail;
import com.ingredientapp.service.ClarifaiService;
import com.ingredientapp.service.SpoonacularService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST controller implementing the two endpoints described in Chapter 3 (Back-End Design),
 * plus two small additions used by the front-end: GET /api/ingredients (so a page refresh can
 * redraw the list) and DELETE /api/ingredients/{name} (FR5: manual removal of an incorrectly
 * detected ingredient).
 *
 * Ingredients are held per-session using Spring's built-in HttpSession (in-memory, no database
 * - see Figure 5: Session Data Model and the Design Decisions table in Chapter 3).
 */
@RestController
@RequestMapping("/api")
public class IngredientController {

    private static final String SESSION_ATTRIBUTE = "ingredients";

    private final ClarifaiService clarifaiService;
    private final SpoonacularService spoonacularService;

    public IngredientController(ClarifaiService clarifaiService, SpoonacularService spoonacularService) {
        this.clarifaiService = clarifaiService;
        this.spoonacularService = spoonacularService;
    }

    @PostMapping("/detect-ingredients")
    public ResponseEntity<List<Ingredient>> detectIngredients(@RequestBody ImageRequest request, HttpSession session) {
        List<Ingredient> detected = clarifaiService.detectIngredients(request.getImage());
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
