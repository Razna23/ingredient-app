package com.ingredientapp.model;

import java.util.List;

/* Full recipe info (ingredient amounts + steps), fetched when a recipe card is tapped. */
public class RecipeDetail {

    private int id;
    private String title;
    private String imageUrl;
    private int servings;
    private int readyInMinutes;
    private List<String> ingredientLines;
    private List<String> instructionSteps;

    public RecipeDetail() {
    }

    public RecipeDetail(int id, String title, String imageUrl, int servings, int readyInMinutes,
                         List<String> ingredientLines, List<String> instructionSteps) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.servings = servings;
        this.readyInMinutes = readyInMinutes;
        this.ingredientLines = ingredientLines;
        this.instructionSteps = instructionSteps;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public int getReadyInMinutes() {
        return readyInMinutes;
    }

    public void setReadyInMinutes(int readyInMinutes) {
        this.readyInMinutes = readyInMinutes;
    }

    public List<String> getIngredientLines() {
        return ingredientLines;
    }

    public void setIngredientLines(List<String> ingredientLines) {
        this.ingredientLines = ingredientLines;
    }

    public List<String> getInstructionSteps() {
        return instructionSteps;
    }

    public void setInstructionSteps(List<String> instructionSteps) {
        this.instructionSteps = instructionSteps;
    }
}
