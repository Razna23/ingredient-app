package com.ingredientapp.dto;

/* Request body for POST /api/detect-ingredients - a base64 JPEG from the camera. */
public class ImageRequest {

    private String image;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
