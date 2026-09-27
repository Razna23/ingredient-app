package com.ingredientapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/* Entry point for the back-end. Exposes the REST API the front-end calls to
   scan ingredients and get recipes. */
@SpringBootApplication
public class IngredientAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngredientAppApplication.class, args);
    }
}
