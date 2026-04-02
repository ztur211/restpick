package com.ztur211.restpick;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


// Controller class to handle web requests for the restaurant picker application
@Controller // Marks the class as a REST controller
public class IndexController {
    @Value("${spring.application.name}")
    String appName;

    @Value("${google.places.api.key}")
    private String apiKey;

    private final RestaurantService restaurantService;
    private final AutocompleteService autocompleteService;

    public IndexController(RestaurantService restaurantService, AutocompleteService autocompleteService) {
        this.restaurantService = restaurantService;
        this.autocompleteService = autocompleteService;
    }
    

    @GetMapping("/")
    public String homePage(Model model) {
        model.addAttribute("appName", appName);
        model.addAttribute("apiKey", apiKey);
        model.addAttribute("cuisineTypes", RestaurantService.ALL_CUISINE_TYPES);
        return "index";
    }

    @PostMapping("/search")
    @ResponseBody
    public ResponseEntity<Void> search(@RequestBody SearchRequest searchRequest) {
        restaurantService.setSearchRequest(searchRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pick")
    @ResponseBody
    public ResponseEntity<?> pick() {
        try {
            Restaurant restaurant = restaurantService.getRandomNearbyRestaurant();
            return ResponseEntity.ok(restaurant);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/autocomplete")
    @ResponseBody
    public ResponseEntity<?> autocomplete(@RequestBody Map<String, Object> body) {
        String input = (String) body.get("input");
        if (input == null || input.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Input is required"));
        }
        
        Double biasLat = body.get("biasLat") != null ? ((Number) body.get("biasLat")).doubleValue() : null;
        Double biasLng = body.get("biasLng") != null ? ((Number) body.get("biasLng")).doubleValue() : null;

        try {
            return ResponseEntity.ok(autocompleteService.getSuggestions(input, biasLat, biasLng));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}