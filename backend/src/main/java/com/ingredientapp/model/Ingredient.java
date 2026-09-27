package com.ingredientapp.model;

/* One ingredient detected in a scan, held in the user's session. */
public class Ingredient {

    private String name;
    private double confidence;

    public Ingredient() {
        // needed for JSON
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
