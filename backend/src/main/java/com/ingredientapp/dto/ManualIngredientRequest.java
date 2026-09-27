package com.ingredientapp.dto;

/* Request body for POST /api/ingredients - an ingredient typed in by hand
   instead of scanned with the camera. */
public class ManualIngredientRequest {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
