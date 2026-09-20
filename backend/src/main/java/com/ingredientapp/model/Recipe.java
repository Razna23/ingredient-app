package com.ingredientapp.model;

/**
 * A recipe suggestion returned by the Spoonacular API. Recipe objects are never stored in
 * the session (see the "queried to retrieve" dependency in Figure 5) - they are built fresh
 * from the API response each time GET /api/recipes is called.
 */
public class Recipe {

    private int id;
    private String title;
    private String imageUrl;
    private int usedIngredientCount;
    private int missedIngredientCount;

    public Recipe() {
    }

    public Recipe(int id, String title, String imageUrl, int usedIngredientCount, int missedIngredientCount) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.usedIngredientCount = usedIngredientCount;
        this.missedIngredientCount = missedIngredientCount;
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

    public int getUsedIngredientCount() {
        return usedIngredientCount;
    }

    public void setUsedIngredientCount(int usedIngredientCount) {
        this.usedIngredientCount = usedIngredientCount;
    }

    public int getMissedIngredientCount() {
        return missedIngredientCount;
    }

    public void setMissedIngredientCount(int missedIngredientCount) {
        this.missedIngredientCount = missedIngredientCount;
    }
}
