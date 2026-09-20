package com.ingredientapp.dto;

/**
 * Request body for POST /api/detect-ingredients.
 * `image` is a base64-encoded JPEG frame captured by the front-end's HTML canvas
 * (data URL prefix already stripped by the client - see frontend/app.js).
 */
public class ImageRequest {

    private String image;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
