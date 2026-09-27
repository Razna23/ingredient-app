package com.ingredientapp.dto;

/**
 * Request body for POST /api/ingredients - a user manually typing in an ingredient
 * name that Gemini failed to detect (or that they simply already have on hand),
 * rather than one coming from a camera scan (see ImageRequest for that path).
 */
public class ManualIngredientRequest {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
