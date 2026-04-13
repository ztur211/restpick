package com.ztur211.restpick;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;

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
            System.out.println("Calling autocomplete service with input: " + input);
            return ResponseEntity.ok(autocompleteService.getSuggestions(input, biasLat, biasLng));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
/* 
    @PostMapping("/search")
    @ResponseBody
    public ResponseEntity<Void> search(@RequestBody SearchRequest searchRequest) {
        restaurantService.setSearchRequest(searchRequest);
        return ResponseEntity.ok().build();
    } */

    @PostMapping("/pick")
    @ResponseBody
    public ResponseEntity<?> pick(@RequestBody SearchRequest searchRequest) {
        try {
            restaurantService.setSearchRequest(searchRequest);
            Restaurant restaurant = restaurantService.getRandomNearbyRestaurant();
            return ResponseEntity.ok(restaurant);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/resolve-location")
    @ResponseBody
    public ResponseEntity<?> resolveLocation(@RequestBody Map<String, String> body) {
        String placeId = body.get("placeId");
        if (placeId == null || placeId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "placeId is required"));
        }
        try {
            return ResponseEntity.ok(autocompleteService.getLocation(placeId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/map-image")
    public ResponseEntity<byte[]> getMapImage(@RequestParam Double latitude, @RequestParam Double longitude) {
        String mapUrl = "https://maps.googleapis.com/maps/api/staticmap"
                + "?center=" + latitude + "," + longitude
                + "&zoom=18"
                + "&size=300x600"
                + "&maptype=satellite"
                + "&markers=color:red%7C" + latitude + "," + longitude
                + "&key=" + apiKey;

        RestTemplate restTemplate = new RestTemplate();
        byte[] imageBytes = restTemplate.getForObject(mapUrl, byte[].class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
    }
}