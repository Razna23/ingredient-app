package com.ingredientapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AI-Powered Ingredient Detection and Recipe Generation back-end.
 *
 * This Spring Boot application exposes a small REST API used by the mobile front-end
 * (see /frontend) to:
 *   1. submit a captured camera frame for ingredient detection (Clarifai Food Model API), and
 *   2. request recipe suggestions based on the ingredients accumulated in the user's session
 *      (Spoonacular API).
 *
 * See README.md at the project root for how to configure real API keys and deploy to Render.
 */
@SpringBootApplication
public class IngredientAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngredientAppApplication.class, args);
    }
}
