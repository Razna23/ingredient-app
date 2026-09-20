package com.ingredientapp.model;

/**
 * A single ingredient detected by the Clarifai Food Model API and held in the
 * user's session (see IngredientSession in the dissertation's Figure 5: Session Data Model).
 */
public class Ingredient {

    private String name;
    private double confidence;

    public Ingredient() {
        // required for JSON deserialization
    }

    public Ingredient(String name, double confidence) {
        this.name = name;
        this.confidence = confidence;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "Ingredient{name='" + name + "', confidence=" + confidence + "}";
    }
}
